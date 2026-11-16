#include "Mp4EditList.h"
#include "Pcm.h"

#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>

#include <mfapi.h>
#include <mferror.h>
#include <mfidl.h>
#include <mfreadwrite.h>
#include <wrl/client.h>

#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <new>
#include <optional>

using Microsoft::WRL::ComPtr;

namespace {

std::string hresultText(const char *what, HRESULT hr) {
    char hex[16];
    std::snprintf(hex, sizeof(hex), "0x%08lX", static_cast<unsigned long>(hr));
    return std::string(what) + " failed (HRESULT " + hex + ")";
}

std::wstring widen(const std::string &utf8) {
    const int length = MultiByteToWideChar(CP_UTF8, 0, utf8.data(), int(utf8.size()), nullptr, 0);
    std::wstring wide(size_t(length), L'\0');
    MultiByteToWideChar(CP_UTF8, 0, utf8.data(), int(utf8.size()), wide.data(), length);
    return wide;
}

/** COM and Media Foundation for the calling thread, released on every return path. */
class MediaFoundationScope {
public:
    MediaFoundationScope() {
        // The JVM may have set up COM on this thread already, in either model: use it as it is.
        const HRESULT com = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
        ownsCom_ = SUCCEEDED(com);
        result_ = MFStartup(MF_VERSION, MFSTARTUP_NOSOCKET);
    }
    ~MediaFoundationScope() {
        if (SUCCEEDED(result_)) MFShutdown();
        if (ownsCom_) CoUninitialize();
    }
    MediaFoundationScope(const MediaFoundationScope &) = delete;
    MediaFoundationScope &operator=(const MediaFoundationScope &) = delete;

    HRESULT result() const { return result_; }

private:
    bool ownsCom_ = false;
    HRESULT result_ = E_FAIL;
};

constexpr DWORD kAudioStream = DWORD(MF_SOURCE_READER_FIRST_AUDIO_STREAM);

/** Frames the container announces, or 0 when it does not say. */
int64_t expectedFrames(IMFSourceReader *reader, UINT32 sampleRate) {
    PROPVARIANT duration;
    PropVariantInit(&duration);
    int64_t frames = 0;
    if (SUCCEEDED(reader->GetPresentationAttribute(DWORD(MF_SOURCE_READER_MEDIASOURCE), MF_PD_DURATION, &duration)) &&
        duration.vt == VT_UI8) {
        // 100 ns units. Capped at a day only to keep the arithmetic in range: reservePcm bounds it.
        const double seconds = std::min(double(duration.uhVal.QuadPart) / 1e7, 86400.0);
        frames = int64_t(seconds * sampleRate);
    }
    PropVariantClear(&duration);
    return frames;
}

/** 0 when the size cannot be read: reservePcm then keeps only its time cap. */
uint64_t fileBytes(const std::string &path) {
    WIN32_FILE_ATTRIBUTE_DATA data;
    if (!GetFileAttributesExW(widen(path).c_str(), GetFileExInfoStandard, &data)) return 0;
    return (uint64_t(data.nFileSizeHigh) << 32) | data.nFileSizeLow;
}

/**
 * What the process can still commit (physical memory plus page file), not what is free in RAM: on
 * Windows an allocation fails when the commit limit is reached, even with the disk half empty.
 */
uint64_t availableMemoryBytes() {
    MEMORYSTATUSEX status;
    status.dwLength = sizeof(status);
    if (!GlobalMemoryStatusEx(&status)) return UINT64_MAX;
    // A 32-bit process also has its address space as a limit.
    const uint64_t commit = std::min<uint64_t>(status.ullAvailPageFile, status.ullAvailVirtual);
    return commit > kMemoryMarginBytes ? commit - kMemoryMarginBytes : 0;
}

/** Whether the file starts as an MP4 does (an ftyp box), without reading the rest. */
bool startsLikeMp4(const std::string &path) {
    FILE *file = openFileUtf8(path, "rb");
    if (!file) return false;
    char header[8];
    const bool isMp4 = std::fread(header, 1, sizeof(header), file) == sizeof(header) && std::memcmp(header + 4, "ftyp", 4) == 0;
    std::fclose(file);
    return isMp4;
}

/**
 * Media Foundation decodes no audio from a fragmented MP4 whose sound track has an edit list that
 * starts past 0, as YouTube's audio does (Mp4EditList.h). For such a file this returns a stream over a
 * copy in memory where the edts box is renamed to "free": same size, so no offset in the file moves,
 * and Media Foundation skips it as padding. [edit] is what the edit said, for the caller to apply.
 * Null for every other file, which is read from its path as before. The copy is the compressed file,
 * a few MB for a song: small next to the PCM it decodes to.
 */
ComPtr<IStream> withoutEditList(const std::string &path, uint64_t size, std::optional<FragmentedEditList> &edit) {
    if (size == 0 || !startsLikeMp4(path)) return nullptr;
    HGLOBAL memory = GlobalAlloc(GMEM_MOVEABLE, SIZE_T(size));
    if (!memory) return nullptr;
    auto *bytes = static_cast<uint8_t *>(GlobalLock(memory));
    bool isRead = false;
    if (bytes) {
        if (FILE *file = openFileUtf8(path, "rb")) {
            isRead = std::fread(bytes, 1, size_t(size), file) == size_t(size);
            std::fclose(file);
        }
        if (isRead) edit = findFragmentedEditList(bytes, size_t(size));
        if (edit) std::memcpy(bytes + edit->edtsOffset + 4, "free", 4);
        GlobalUnlock(memory);
    }
    ComPtr<IStream> stream;
    // TRUE: the stream frees the memory when it is released.
    if (!edit || FAILED(CreateStreamOnHGlobal(memory, TRUE, &stream))) {
        GlobalFree(memory);
        edit.reset();
        return nullptr;
    }
    return stream;
}

HRESULT createReader(const std::string &path, IStream *memory, ComPtr<IMFSourceReader> &reader, std::string &error) {
    if (!memory) {
        const HRESULT hr = MFCreateSourceReaderFromURL(widen(path).c_str(), nullptr, &reader);
        if (FAILED(hr)) error = hresultText("MFCreateSourceReaderFromURL", hr);
        return hr;
    }
    ComPtr<IMFByteStream> byteStream;
    HRESULT hr = MFCreateMFByteStreamOnStream(memory, &byteStream);
    if (FAILED(hr)) {
        error = hresultText("MFCreateMFByteStreamOnStream", hr);
        return hr;
    }
    // Without a URL there is no extension to tell the container from.
    ComPtr<IMFAttributes> attributes;
    if (SUCCEEDED(byteStream.As(&attributes))) attributes->SetString(MF_BYTESTREAM_CONTENT_TYPE, L"audio/mp4");
    hr = MFCreateSourceReaderFromByteStream(byteStream.Get(), nullptr, &reader);
    if (FAILED(hr)) error = hresultText("MFCreateSourceReaderFromByteStream", hr);
    return hr;
}

} // namespace

DecodeStatus decodeFile(const std::string &path, PcmBuffer &out, std::string &error, int64_t memoryBudgetBytes) {
    MediaFoundationScope mediaFoundation;
    if (FAILED(mediaFoundation.result())) {
        error = hresultText("MFStartup", mediaFoundation.result());
        return DecodeStatus::Failed;
    }

    const uint64_t size = fileBytes(path);
    std::optional<FragmentedEditList> edit;
    const ComPtr<IStream> memory = withoutEditList(path, size, edit);
    ComPtr<IMFSourceReader> reader;
    HRESULT hr = createReader(path, memory.Get(), reader, error);
    if (FAILED(hr)) return DecodeStatus::Failed;
    reader->SetStreamSelection(DWORD(MF_SOURCE_READER_ALL_STREAMS), FALSE);
    hr = reader->SetStreamSelection(kAudioStream, TRUE);
    if (FAILED(hr)) {
        error = hresultText("no audio stream: SetStreamSelection", hr);
        return DecodeStatus::Failed;
    }

    // Interleaved 32-bit float at the file's own rate and channel count: the engine resamples.
    ComPtr<IMFMediaType> wanted;
    MFCreateMediaType(&wanted);
    wanted->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Audio);
    wanted->SetGUID(MF_MT_SUBTYPE, MFAudioFormat_Float);
    hr = reader->SetCurrentMediaType(kAudioStream, nullptr, wanted.Get());
    if (FAILED(hr)) {
        error = hresultText("SetCurrentMediaType (float PCM)", hr);
        return DecodeStatus::Failed;
    }
    ComPtr<IMFMediaType> actual;
    hr = reader->GetCurrentMediaType(kAudioStream, &actual);
    if (FAILED(hr)) {
        error = hresultText("GetCurrentMediaType", hr);
        return DecodeStatus::Failed;
    }
    UINT32 channels = 0;
    UINT32 sampleRate = 0;
    actual->GetUINT32(MF_MT_AUDIO_NUM_CHANNELS, &channels);
    actual->GetUINT32(MF_MT_AUDIO_SAMPLES_PER_SECOND, &sampleRate);
    if (channels < 1 || channels > 2) {
        error = "only mono or stereo files are supported, got " + std::to_string(channels) + " channels";
        return DecodeStatus::Failed;
    }
    if (sampleRate == 0) {
        error = "the decoder reported no sample rate";
        return DecodeStatus::Failed;
    }

    out = PcmBuffer{};
    out.sampleRate = int(sampleRate);
    out.channels = int(channels);
    out.data.assign(channels, {});
    // Size the channels once from the container's duration: growing them block by block copied
    // everything decoded so far each time with MinGW's exact-size reserve (minutes for a long M4A).
    // The duration is only a hint: a missing or short one falls back to the vector's own growth, and
    // an absurd one is capped (reservePcm).
    const int64_t expected = expectedFrames(reader.Get(), sampleRate);
    const uint64_t budget = memoryBudgetBytes > 0 ? uint64_t(memoryBudgetBytes) : availableMemoryBytes();
    if (!pcmFitsInMemory(expected, int(channels), size, budget)) {
        out = PcmBuffer{};
        error = "the decoded audio would not fit in memory";
        return DecodeStatus::TooLong;
    }
    if (expected > 0) reservePcm(out, expected + expected / 64 + sampleRate, size);

    while (true) {
        DWORD flags = 0;
        ComPtr<IMFSample> sample;
        hr = reader->ReadSample(kAudioStream, 0, nullptr, &flags, nullptr, &sample);
        if (FAILED(hr)) {
            error = hresultText("ReadSample", hr);
            return DecodeStatus::Failed;
        }
        if (flags & MF_SOURCE_READERF_CURRENTMEDIATYPECHANGED) {
            error = "the audio format changed in the middle of the file";
            return DecodeStatus::Failed;
        }
        if (sample) {
            ComPtr<IMFMediaBuffer> buffer;
            hr = sample->ConvertToContiguousBuffer(&buffer);
            if (FAILED(hr)) {
                error = hresultText("ConvertToContiguousBuffer", hr);
                return DecodeStatus::Failed;
            }
            BYTE *bytes = nullptr;
            DWORD length = 0;
            hr = buffer->Lock(&bytes, nullptr, &length);
            if (FAILED(hr)) {
                error = hresultText("IMFMediaBuffer::Lock", hr);
                return DecodeStatus::Failed;
            }
            const auto *samples = reinterpret_cast<const float *>(bytes);
            const size_t frames = length / (sizeof(float) * channels);
            try {
                for (UINT32 c = 0; c < channels; ++c) {
                    auto &channel = out.data[c];
                    const size_t start = channel.size();
                    channel.resize(start + frames);
                    float *to = channel.data() + start;
                    for (size_t f = 0; f < frames; ++f) to[f] = samples[f * channels + c];
                }
            } catch (const std::bad_alloc &) {
                // No duration (fragmented MP4) or a wrong one: the vector ran out of commit while growing.
                buffer->Unlock();
                out = PcmBuffer{};
                error = "out of memory while decoding";
                return DecodeStatus::TooLong;
            }
            buffer->Unlock();
        }
        if (flags & MF_SOURCE_READERF_ENDOFSTREAM) break;
    }
    if (edit) {
        // The edit starts the track after the encoder's delay: what comes before it is not the song.
        const double seconds = double(edit->mediaTime) / edit->timescale;
        const auto skip = size_t(std::min<double>(seconds * sampleRate, double(out.data[0].size())));
        for (auto &channel : out.data) channel.erase(channel.begin(), channel.begin() + std::ptrdiff_t(skip));
    }
    out.frames = int64_t(out.data[0].size());
    // An overstated duration would keep its excess for as long as the file is open.
    for (auto &channel : out.data) {
        if (channel.capacity() - channel.size() > channel.size() / 32 + sampleRate) channel.shrink_to_fit();
    }
    if (out.frames == 0) {
        error = "no audio decoded";
        return DecodeStatus::Failed;
    }
    return DecodeStatus::Ok;
}
