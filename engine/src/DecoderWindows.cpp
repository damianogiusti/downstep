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
#include <cstdio>

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
        // 100 ns units. Capped at a day, so a corrupt header cannot ask for gigabytes up front.
        const double seconds = std::min(double(duration.uhVal.QuadPart) / 1e7, 86400.0);
        frames = int64_t(seconds * sampleRate);
    }
    PropVariantClear(&duration);
    return frames;
}

} // namespace

bool decodeFile(const std::string &path, PcmBuffer &out, std::string &error) {
    MediaFoundationScope mediaFoundation;
    if (FAILED(mediaFoundation.result())) {
        error = hresultText("MFStartup", mediaFoundation.result());
        return false;
    }

    ComPtr<IMFSourceReader> reader;
    HRESULT hr = MFCreateSourceReaderFromURL(widen(path).c_str(), nullptr, &reader);
    if (FAILED(hr)) {
        error = hresultText("MFCreateSourceReaderFromURL", hr);
        return false;
    }
    reader->SetStreamSelection(DWORD(MF_SOURCE_READER_ALL_STREAMS), FALSE);
    hr = reader->SetStreamSelection(kAudioStream, TRUE);
    if (FAILED(hr)) {
        error = hresultText("no audio stream: SetStreamSelection", hr);
        return false;
    }

    // Interleaved 32-bit float at the file's own rate and channel count: the engine resamples.
    ComPtr<IMFMediaType> wanted;
    MFCreateMediaType(&wanted);
    wanted->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Audio);
    wanted->SetGUID(MF_MT_SUBTYPE, MFAudioFormat_Float);
    hr = reader->SetCurrentMediaType(kAudioStream, nullptr, wanted.Get());
    if (FAILED(hr)) {
        error = hresultText("SetCurrentMediaType (float PCM)", hr);
        return false;
    }
    ComPtr<IMFMediaType> actual;
    hr = reader->GetCurrentMediaType(kAudioStream, &actual);
    if (FAILED(hr)) {
        error = hresultText("GetCurrentMediaType", hr);
        return false;
    }
    UINT32 channels = 0;
    UINT32 sampleRate = 0;
    actual->GetUINT32(MF_MT_AUDIO_NUM_CHANNELS, &channels);
    actual->GetUINT32(MF_MT_AUDIO_SAMPLES_PER_SECOND, &sampleRate);
    if (channels < 1 || channels > 2) {
        error = "only mono or stereo files are supported, got " + std::to_string(channels) + " channels";
        return false;
    }
    if (sampleRate == 0) {
        error = "the decoder reported no sample rate";
        return false;
    }

    out = PcmBuffer{};
    out.sampleRate = int(sampleRate);
    out.channels = int(channels);
    out.data.assign(channels, {});
    // Size the channels once from the container's duration: growing them block by block copied
    // everything decoded so far each time with MinGW's exact-size reserve (minutes for a long M4A).
    // The duration is only a hint: a missing or short one falls back to the vector's own growth.
    const int64_t expected = expectedFrames(reader.Get(), sampleRate);
    if (expected > 0) {
        for (auto &channel : out.data) channel.reserve(size_t(expected + expected / 64 + sampleRate));
    }

    while (true) {
        DWORD flags = 0;
        ComPtr<IMFSample> sample;
        hr = reader->ReadSample(kAudioStream, 0, nullptr, &flags, nullptr, &sample);
        if (FAILED(hr)) {
            error = hresultText("ReadSample", hr);
            return false;
        }
        if (flags & MF_SOURCE_READERF_CURRENTMEDIATYPECHANGED) {
            error = "the audio format changed in the middle of the file";
            return false;
        }
        if (sample) {
            ComPtr<IMFMediaBuffer> buffer;
            hr = sample->ConvertToContiguousBuffer(&buffer);
            if (FAILED(hr)) {
                error = hresultText("ConvertToContiguousBuffer", hr);
                return false;
            }
            BYTE *bytes = nullptr;
            DWORD length = 0;
            hr = buffer->Lock(&bytes, nullptr, &length);
            if (FAILED(hr)) {
                error = hresultText("IMFMediaBuffer::Lock", hr);
                return false;
            }
            const auto *samples = reinterpret_cast<const float *>(bytes);
            const size_t frames = length / (sizeof(float) * channels);
            for (UINT32 c = 0; c < channels; ++c) {
                auto &channel = out.data[c];
                const size_t start = channel.size();
                channel.resize(start + frames);
                float *to = channel.data() + start;
                for (size_t f = 0; f < frames; ++f) to[f] = samples[f * channels + c];
            }
            buffer->Unlock();
        }
        if (flags & MF_SOURCE_READERF_ENDOFSTREAM) break;
    }
    out.frames = int64_t(out.data[0].size());
    // An overstated duration would keep its excess for as long as the file is open.
    for (auto &channel : out.data) {
        if (channel.capacity() - channel.size() > channel.size() / 32 + sampleRate) channel.shrink_to_fit();
    }
    if (out.frames == 0) {
        error = "no audio decoded";
        return false;
    }
    return true;
}
