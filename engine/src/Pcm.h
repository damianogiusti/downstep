#pragma once

#include <cstdint>
#include <cstdio>
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

/** Only the decoder depends on the OS: AudioToolbox on macOS, Media Foundation on Windows. */
bool decodeFile(const std::string &path, PcmBuffer &out, std::string &error);

/** fopen for a UTF-8 path: on Windows the narrow fopen reads paths in the ANSI code page. */
FILE *openFileUtf8(const std::string &path, const char *mode);

bool writeWavFloat(const std::string &path, const PcmBuffer &pcm, std::string &error);
