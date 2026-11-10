#pragma once

#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <new>
#include <string>
#include <vector>

/**
 * Decoded audio, planar: data[channel][frame]. A mono file stays one channel (half the memory):
 * readers ask for channels 0 and 1 and both get channel 0.
 */
struct PcmBuffer {
    int sampleRate = 0;
    int channels = 0;
    int64_t frames = 0;
    std::vector<std::vector<float>> data;

    const float *channelData(int channel) const { return data[size_t(std::min(channel, channels - 1))].data(); }

    /** Silence outside the buffer, so stretchers can read before the start and past the end. */
    float at(int channel, int64_t frame) const {
        return (frame >= 0 && frame < frames) ? channelData(channel)[frame] : 0.f;
    }

    double seconds() const { return sampleRate ? double(frames) / sampleRate : 0.0; }
};

/**
 * Reserves about [expectedFrames] per channel, from the length a container announces. Only a hint: a
 * corrupt header can announce anything, so it is capped at three hours and at what [fileBytes] of
 * compressed audio can hold (0 = unknown size), and a failed allocation leaves the vectors to grow on
 * their own. A std::bad_alloc here would otherwise cross the C API and end the JVM.
 */
inline void reservePcm(PcmBuffer &out, int64_t expectedFrames, uint64_t fileBytes) {
    // 128 frames per byte is about 3 kbps at 48 kHz: below any real codec's bitrate.
    constexpr uint64_t kMaxFramesPerFileByte = 128;
    constexpr int64_t kMaxSeconds = 3 * 60 * 60;
    if (expectedFrames <= 0) return;
    int64_t frames = std::min<int64_t>(expectedFrames, int64_t(out.sampleRate) * kMaxSeconds);
    if (fileBytes > 0) frames = int64_t(std::min<uint64_t>(uint64_t(frames), fileBytes * kMaxFramesPerFileByte));
    try {
        for (auto &channel : out.data) channel.reserve(size_t(frames));
    } catch (const std::bad_alloc &) {
        for (auto &channel : out.data) {
            channel.clear();
            channel.shrink_to_fit();
        }
    }
}

enum class DecodeStatus { Ok, Failed, TooLong };

/**
 * What the rest of the engine needs besides the PCM itself. Nothing in the engine grows with the
 * track: the stretcher and its FIFOs are fixed-size, the loudness energies are one double per 100 ms
 * (about 40 KB an hour), export shares the decoded buffer and encodes in chunks. So this is room for
 * the JVM, the UI and the audio device, not for the track.
 */
constexpr uint64_t kMemoryMarginBytes = 512ull * 1024 * 1024;

/**
 * Whether the PCM of a track that announces [expectedFrames] fits in [budgetBytes]. An unknown
 * duration (<= 0) fits: the decoder then relies on catching std::bad_alloc. The announced length is
 * bounded by what [fileBytes] of compressed audio can hold (as in reservePcm), so a corrupt header is
 * not reported as a long track. There is no three-hour cap here: a real long file must be refused.
 */
inline bool pcmFitsInMemory(int64_t expectedFrames, int channels, uint64_t fileBytes, uint64_t budgetBytes) {
    if (expectedFrames <= 0) return true;
    uint64_t frames = uint64_t(expectedFrames);
    // Same bound as reservePcm: 128 frames per byte is below any real codec's bitrate.
    if (fileBytes > 0) frames = std::min<uint64_t>(frames, fileBytes * 128);
    const uint64_t bytesPerFrame = uint64_t(channels) * sizeof(float);
    return frames <= budgetBytes / bytesPerFrame;
}

/**
 * Only the decoder depends on the OS: AudioToolbox on macOS, Media Foundation on Windows.
 * [memoryBudgetBytes] bounds the decoded PCM: 0 = what the OS can still give, minus a margin. On
 * TooLong or Failed, [error] says why, without the path.
 */
DecodeStatus decodeFile(const std::string &path, PcmBuffer &out, std::string &error, int64_t memoryBudgetBytes);

/** fopen for a UTF-8 path: on Windows the narrow fopen reads paths in the ANSI code page. */
FILE *openFileUtf8(const std::string &path, const char *mode);

bool writeWavFloat(const std::string &path, const PcmBuffer &pcm, std::string &error);
