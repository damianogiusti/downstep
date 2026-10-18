#include "Pcm.h"

#include <miniaudio.h>

#include <sys/stat.h>

#include <algorithm>
#include <memory>

namespace {

struct DecoderCloser {
    void operator()(ma_decoder *decoder) const {
        ma_decoder_uninit(decoder);
        delete decoder;
    }
};

std::string resultText(const char *what, ma_result result) {
    return std::string(what) + " failed (" + ma_result_description(result) + ")";
}

} // namespace

/**
 * miniaudio's built-in decoders: WAV, FLAC and MP3. Linux has no system decoder every distribution
 * ships, and AAC (M4A) would mean FFmpeg or GStreamer: those files fail here with a clear message.
 */
bool decodeFile(const std::string &path, PcmBuffer &out, std::string &error) {
    // f32 at the file's own rate and channel count (0 = keep): the engine resamples.
    const ma_decoder_config config = ma_decoder_config_init(ma_format_f32, 0, 0);
    std::unique_ptr<ma_decoder, DecoderCloser> decoder;
    {
        auto raw = std::make_unique<ma_decoder>();
        const ma_result result = ma_decoder_init_file(path.c_str(), &config, raw.get());
        if (result != MA_SUCCESS) {
            error = result == MA_NO_BACKEND || result == MA_INVALID_FILE
                        ? "unsupported audio format: Linux reads WAV, FLAC and MP3 (no M4A/AAC)"
                        : resultText("ma_decoder_init_file", result);
            return false;
        }
        decoder.reset(raw.release());
    }

    ma_format format;
    ma_uint32 channels = 0, sampleRate = 0;
    ma_decoder_get_data_format(decoder.get(), &format, &channels, &sampleRate, nullptr, 0);
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
    // A hint only (MP3 lengths are estimates): the vectors grow on their own past it, and an absurd
    // one is capped (reservePcm).
    ma_uint64 expected = 0;
    if (ma_decoder_get_length_in_pcm_frames(decoder.get(), &expected) == MA_SUCCESS && expected > 0) {
        struct stat file {};
        const uint64_t bytes = stat(path.c_str(), &file) == 0 ? uint64_t(file.st_size) : 0;
        expected = std::min<ma_uint64>(expected, ma_uint64(sampleRate) * 86400);
        reservePcm(out, int64_t(expected), bytes);
    }

    constexpr ma_uint64 kChunkFrames = 32768;
    std::vector<float> interleaved(size_t(kChunkFrames) * channels);
    while (true) {
        ma_uint64 frames = 0;
        const ma_result result = ma_decoder_read_pcm_frames(decoder.get(), interleaved.data(), kChunkFrames, &frames);
        if (result != MA_SUCCESS && result != MA_AT_END) {
            error = resultText("ma_decoder_read_pcm_frames", result);
            return false;
        }
        for (ma_uint32 c = 0; c < channels; ++c) {
            auto &channel = out.data[c];
            const size_t start = channel.size();
            channel.resize(start + size_t(frames));
            float *to = channel.data() + start;
            for (size_t f = 0; f < size_t(frames); ++f) to[f] = interleaved[f * channels + c];
        }
        if (result == MA_AT_END || frames == 0) break;
    }
    out.frames = int64_t(out.data[0].size());
    if (out.frames == 0) {
        error = "no audio decoded";
        return false;
    }
    return true;
}
