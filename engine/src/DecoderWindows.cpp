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
#include <atomic>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <new>
#include <optional>
#include <string>
#include <vector>

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
 * starts past 0, as YouTube's audio does (Mp4EditList.h). For such a file, [make] writes a copy to the
 * temp folder with the edts box renamed to "free": same size, so no offset in the file moves, and
 * Media Foundation skips it as padding. The decoder reads that copy, and applies the edit itself.
 * A file, not a stream in memory: MinGW's headers lack MFCreateMFByteStreamOnStream. The copy is
 * deleted with this object; one left by a crash stays in the temp folder for the system to clean.
 */
class EditListHiddenCopy {
public:
    EditListHiddenCopy() = default;
    EditListHiddenCopy(const EditListHiddenCopy &) = delete;
    EditListHiddenCopy &operator=(const EditListHiddenCopy &) = delete;
    ~EditListHiddenCopy() {
        if (!path_.empty()) DeleteFileW(path_.c_str());
    }

    /** The edit of [path], with [path()] to read instead; nothing for every other file, or if the copy fails. */
    std::optional<FragmentedEditList> make(const std::string &path, uint64_t size) {
        if (size == 0 || !startsLikeMp4(path)) return std::nullopt;
        std::vector<uint8_t> bytes;
        try {
            bytes.resize(size_t(size));
        } catch (const std::bad_alloc &) {
            return std::nullopt;
        }
        FILE *file = openFileUtf8(path, "rb");
        if (!file) return std::nullopt;
        const bool isRead = std::fread(bytes.data(), 1, bytes.size(), file) == bytes.size();
        std::fclose(file);
        if (!isRead) return std::nullopt;
        const auto edit = findFragmentedEditList(bytes.data(), bytes.size());
        if (!edit) return std::nullopt;
        std::memcpy(bytes.data() + edit->edtsOffset + 4, "free", 4);
        std::wstring target = tempName();
        if (target.empty() || !write(target, bytes)) return std::nullopt;
        path_ = std::move(target);
        return edit;
    }

    const std::wstring &path() const { return path_; }

private:
    /** In the temp folder, with the .m4a extension Media Foundation picks the container by. */
    static std::wstring tempName() {
        static std::atomic<unsigned> counter{0};
        wchar_t folder[MAX_PATH + 1];
        const DWORD length = GetTempPathW(MAX_PATH + 1, folder);
        if (length == 0 || length > MAX_PATH) return {};
        return std::wstring(folder) + L"sdengine-" + std::to_wstring(GetCurrentProcessId()) + L"-" +
               std::to_wstring(GetTickCount64()) + L"-" + std::to_wstring(counter++) + L".m4a";
    }

    static bool write(const std::wstring &target, const std::vector<uint8_t> &bytes) {
        // CREATE_NEW: never over a file that is already there.
        HANDLE handle = CreateFileW(target.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_NEW, FILE_ATTRIBUTE_TEMPORARY, nullptr);
        if (handle == INVALID_HANDLE_VALUE) return false;
        bool isWritten = true;
        size_t at = 0;
        while (isWritten && at < bytes.size()) {
            const DWORD chunk = DWORD(std::min<size_t>(bytes.size() - at, size_t(1) << 30));
            DWORD written = 0;
            isWritten = WriteFile(handle, bytes.data() + at, chunk, &written, nullptr) && written == chunk;
            at += written;
        }
        CloseHandle(handle);
        if (!isWritten) DeleteFileW(target.c_str());
        return isWritten;
    }

    std::wstring path_;
};

} // namespace

DecodeStatus decodeFile(const std::string &path, PcmBuffer &out, std::string &error, int64_t memoryBudgetBytes) {
    MediaFoundationScope mediaFoundation;
    if (FAILED(mediaFoundation.result())) {
        error = hresultText("MFStartup", mediaFoundation.result());
        return DecodeStatus::Failed;
    }

    const uint64_t size = fileBytes(path);
    // Before the reader: it lets go of the copy before the copy is deleted.
    EditListHiddenCopy copy;
    const std::optional<FragmentedEditList> edit = copy.make(path, size);
    ComPtr<IMFSourceReader> reader;
    HRESULT hr = MFCreateSourceReaderFromURL((edit ? copy.path() : widen(path)).c_str(), nullptr, &reader);
    if (FAILED(hr)) {
        error = hresultText("MFCreateSourceReaderFromURL", hr);
        return DecodeStatus::Failed;
    }
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
