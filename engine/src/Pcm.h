#pragma once

#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <new>
#include <string>
#include <vector>

/** Decoded audio, planar: data[channel][frame]. */
struct PcmBuffer {
    int sampleRate = 0;
    int channels = 0;
    int64_t frames = 0;
    std::vector<std::vector<float>> data;

    /** Silence outside the buffer, so stretchers can read before the start and past the end. */
    float at(int channel, int64_t frame) const {
        return (frame >= 0 && frame < frames) ? data[channel][size_t(frame)] : 0.f;
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

/** Only the decoder depends on the OS: AudioToolbox on macOS, Media Foundation on Windows. */
bool decodeFile(const std::string &path, PcmBuffer &out, std::string &error);

/** fopen for a UTF-8 path: on Windows the narrow fopen reads paths in the ANSI code page. */
FILE *openFileUtf8(const std::string &path, const char *mode);

bool writeWavFloat(const std::string &path, const PcmBuffer &pcm, std::string &error);
