#include "Pcm.h"

#include <algorithm>
#include <cstdio>
#include <memory>

#ifdef _WIN32
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>
#endif

FILE *openFileUtf8(const std::string &path, const char *mode) {
#ifdef _WIN32
    auto widen = [](const std::string &utf8) {
        const int length = MultiByteToWideChar(CP_UTF8, 0, utf8.data(), int(utf8.size()), nullptr, 0);
        std::wstring wide(size_t(length), L'\0');
        MultiByteToWideChar(CP_UTF8, 0, utf8.data(), int(utf8.size()), wide.data(), length);
        return wide;
    };
    return _wfopen(widen(path).c_str(), widen(mode).c_str());
#else
    return std::fopen(path.c_str(), mode);
#endif
}

bool writeWavFloat(const std::string &path, const PcmBuffer &pcm, std::string &error) {
    // A deleter type rather than decltype(&fclose): glibc's fclose carries attributes GCC warns about.
    struct FileCloser {
        void operator()(FILE *file) const { std::fclose(file); }
    };
    std::unique_ptr<FILE, FileCloser> f(openFileUtf8(path, "wb"));
    if (!f) {
        error = "cannot write " + path;
        return false;
    }
    auto u32 = [&](uint32_t v) { std::fwrite(&v, 4, 1, f.get()); };
    auto u16 = [&](uint16_t v) { std::fwrite(&v, 2, 1, f.get()); };
    const uint32_t dataBytes = uint32_t(pcm.frames * pcm.channels * int64_t(sizeof(float)));
    std::fwrite("RIFF", 1, 4, f.get());
    u32(36 + dataBytes);
    std::fwrite("WAVEfmt ", 1, 8, f.get());
    u32(16);
    u16(3); // IEEE float
    u16(uint16_t(pcm.channels));
    u32(uint32_t(pcm.sampleRate));
    u32(uint32_t(pcm.sampleRate * pcm.channels * int(sizeof(float))));
    u16(uint16_t(pcm.channels * int(sizeof(float))));
    u16(32);
    std::fwrite("data", 1, 4, f.get());
    u32(dataBytes);
    std::vector<float> interleaved(size_t(pcm.channels) * 4096);
    for (int64_t start = 0; start < pcm.frames; start += 4096) {
        const int64_t n = std::min<int64_t>(4096, pcm.frames - start);
        for (int64_t i = 0; i < n; ++i)
            for (int c = 0; c < pcm.channels; ++c)
                interleaved[size_t(i * pcm.channels + c)] = pcm.data[size_t(c)][size_t(start + i)];
        std::fwrite(interleaved.data(), sizeof(float), size_t(n * pcm.channels), f.get());
    }
    return true;
}
