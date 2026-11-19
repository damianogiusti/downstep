#include "Pcm.h"

#include "miniaudio.h"

#include <cstdio>
#include <memory>

/**
 * Decodes the MP3 stems that StemWriter produces with miniaudio's own decoder (dr_mp3), the same code on
 * every platform. Media Foundation ignores the Xing/LAME tag and returns the encoder's padding and delay
 * as audio, so the OS decoders cannot give back exactly the frames that were written; dr_mp3 reads the
 * tag and drops them.
 *
 * ma_decoder over our own file callbacks, not ma_decoder_init_file: the file is opened with
 * openFileUtf8, so a UTF-8 path works on Windows too, and no wide-character variant is needed. dr_mp3
 * directly would give the same frames but would repeat what ma_decoder already does (float output,
 * native channels and rate, read loop).
 */
namespace {

struct FileCloser {
    void operator()(FILE *file) const { std::fclose(file); }
};

ma_result onRead(ma_decoder *decoder, void *out, size_t bytesToRead, size_t *bytesRead) {
    FILE *file = static_cast<FILE *>(decoder->pUserData);
    *bytesRead = std::fread(out, 1, bytesToRead, file);
    if (*bytesRead == 0 && bytesToRead > 0) return std::feof(file) ? MA_AT_END : MA_ERROR;
    return MA_SUCCESS;
}

ma_result onSeek(ma_decoder *decoder, ma_int64 offset, ma_seek_origin origin) {
    const int whence = origin == ma_seek_origin_start ? SEEK_SET : origin == ma_seek_origin_current ? SEEK_CUR : SEEK_END;
    return std::fseek(static_cast<FILE *>(decoder->pUserData), long(offset), whence) == 0 ? MA_SUCCESS : MA_ERROR;
}

uint64_t sizeOf(FILE *file) {
    if (std::fseek(file, 0, SEEK_END) != 0) return 0;
    const long size = std::ftell(file);
    std::fseek(file, 0, SEEK_SET);
    return size > 0 ? uint64_t(size) : 0;
}

} // namespace

DecodeStatus decodeStemMp3(const std::string &path, PcmBuffer &out, std::string &error, int64_t memoryBudgetBytes) {
    std::unique_ptr<FILE, FileCloser> file(openFileUtf8(path, "rb"));
    if (!file) {
        error = "cannot open the file";
        return DecodeStatus::Failed;
    }
    const uint64_t bytes = sizeOf(file.get());

    // f32 at the file's own channels (0) and rate (0): no conversion, no resampling.
    ma_decoder_config config = ma_decoder_config_init(ma_format_f32, 0, 0);
    config.encodingFormat = ma_encoding_format_mp3;
    ma_decoder decoder;
    if (ma_decoder_init(onRead, onSeek, file.get(), &config, &decoder) != MA_SUCCESS) {
        error = "not a decodable MP3";
        return DecodeStatus::Failed;
    }
    struct DecoderCloser {
        ma_decoder *decoder;
        ~DecoderCloser() { ma_decoder_uninit(decoder); }
    } closer{&decoder};

    const int channels = int(decoder.outputChannels);
    if (channels < 1 || channels > 2) {
        error = "only mono or stereo files are supported, got " + std::to_string(channels) + " channels";
        return DecodeStatus::Failed;
    }

    // With the Xing/LAME tag this is the exact length; without it dr_mp3 scans the file. A hint either way.
    ma_uint64 lengthFrames = 0;
    if (ma_decoder_get_length_in_pcm_frames(&decoder, &lengthFrames) != MA_SUCCESS) lengthFrames = 0;
    const uint64_t budget = memoryBudgetBytes > 0 ? uint64_t(memoryBudgetBytes) : availableMemoryBytes();
    if (!pcmFitsInMemory(int64_t(lengthFrames), channels, bytes, budget)) {
        error = "the decoded audio would not fit in memory";
        return DecodeStatus::TooLong;
    }

    out = PcmBuffer{};
    out.sampleRate = int(decoder.outputSampleRate);
    out.channels = channels;
    out.data.assign(size_t(channels), {});
    reservePcm(out, int64_t(lengthFrames), bytes);

    try {
        constexpr ma_uint64 chunkFrames = 32768;
        std::vector<float> chunk(size_t(chunkFrames) * size_t(channels));
        while (true) {
            ma_uint64 frames = 0;
            const ma_result result = ma_decoder_read_pcm_frames(&decoder, chunk.data(), chunkFrames, &frames);
            if (result != MA_SUCCESS && result != MA_AT_END) {
                error = "decoding failed (miniaudio result " + std::to_string(int(result)) + ")";
                out = PcmBuffer{};
                return DecodeStatus::Failed;
            }
            for (ma_uint64 i = 0; i < frames; ++i)
                for (int c = 0; c < channels; ++c) out.data[size_t(c)].push_back(chunk[size_t(i) * size_t(channels) + size_t(c)]);
            if (result == MA_AT_END || frames == 0) break;
        }
    } catch (const std::bad_alloc &) {
        out = PcmBuffer{};
        error = "out of memory while decoding";
        return DecodeStatus::TooLong;
    }
    out.frames = int64_t(out.data[0].size());
    return DecodeStatus::Ok;
}
