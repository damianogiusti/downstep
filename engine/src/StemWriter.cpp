#include "sdengine.h"

#include "Pcm.h"

#include <lame.h>

#include <algorithm>
#include <cstdio>
#include <memory>
#include <new>
#include <vector>

namespace {

constexpr int kChunkFrames = 16384;

} // namespace

struct SdStemWriter {
    lame_t lame = nullptr;
    FILE *file = nullptr;
    int channels = 0;
    // LAME's worst case: 1.25 × samples + 7200 bytes.
    std::vector<unsigned char> mp3 = std::vector<unsigned char>(kChunkFrames * 5 / 4 + 7200);
    bool hasFailed = false;

    ~SdStemWriter() {
        if (lame) lame_close(lame);
        if (file) std::fclose(file);
    }

    bool write(int bytes) {
        if (bytes < 0 || std::fwrite(mp3.data(), 1, size_t(bytes), file) != size_t(bytes)) hasFailed = true;
        return !hasFailed;
    }
};

extern "C" {

SdStemWriter *sd_stem_writer_open(const char *utf8Path, int sampleRate, int channels, int bitrateKbps,
                                  int *status) try {
    auto fail = [&](int reason) -> SdStemWriter * {
        if (status) *status = reason;
        return nullptr;
    };
    if (channels < 1 || channels > 2 || sampleRate < 8000 || sampleRate > 48000 || bitrateKbps <= 0)
        return fail(SD_STEM_BAD_FORMAT);

    auto writer = std::make_unique<SdStemWriter>();
    writer->channels = channels;
    writer->lame = lame_init();
    if (!writer->lame) return fail(SD_STEM_BAD_FORMAT);
    lame_set_in_samplerate(writer->lame, sampleRate);
    lame_set_out_samplerate(writer->lame, sampleRate); // no resampling inside LAME: the file keeps the rate
    lame_set_num_channels(writer->lame, channels);
    lame_set_mode(writer->lame, channels == 1 ? MONO : JOINT_STEREO);
    // ABR around the requested bitrate, not CBR: LAME tags a CBR file "Info" and AudioToolbox then
    // returns 1152 frames too many for some lengths (measured: 768 and 409 MP3 frames, among others).
    // The same file tagged "Xing" (ABR and VBR) is read exactly for every length tried.
    lame_set_VBR(writer->lame, vbr_abr);
    lame_set_VBR_mean_bitrate_kbps(writer->lame, bitrateKbps);
    lame_set_quality(writer->lame, 2);
    lame_set_bWriteVbrTag(writer->lame, 1);
    if (lame_init_params(writer->lame) < 0) return fail(SD_STEM_BAD_FORMAT);

    // "w+b": lame_mp3_tags_fid reads and rewrites the first frame, so the file must be readable too.
    writer->file = openFileUtf8(utf8Path, "w+b");
    if (!writer->file) return fail(SD_STEM_OPEN_FAILED);
    if (status) *status = SD_STEM_OK;
    return writer.release();
} catch (...) {
    if (status) *status = SD_STEM_OPEN_FAILED;
    return nullptr;
}

int sd_stem_writer_write(SdStemWriter *writer, const float *interleaved, int64_t frames) try {
    if (writer->hasFailed) return SD_STEM_WRITE_FAILED;
    for (int64_t done = 0; done < frames;) {
        const int n = int(std::min<int64_t>(frames - done, kChunkFrames));
        const float *block = interleaved + done * writer->channels;
        const int bytes = writer->channels == 1
            ? lame_encode_buffer_ieee_float(writer->lame, block, nullptr, n, writer->mp3.data(), int(writer->mp3.size()))
            : lame_encode_buffer_interleaved_ieee_float(writer->lame, block, n, writer->mp3.data(), int(writer->mp3.size()));
        if (!writer->write(bytes)) return SD_STEM_WRITE_FAILED;
        done += n;
    }
    return SD_STEM_OK;
} catch (...) {
    writer->hasFailed = true;
    return SD_STEM_WRITE_FAILED;
}

int sd_stem_writer_close(SdStemWriter *writer) try {
    bool isComplete = !writer->hasFailed &&
                      writer->write(lame_encode_flush(writer->lame, writer->mp3.data(), int(writer->mp3.size())));
    // The tag frame is the first one of the file: it carries the length and the encoder delay and
    // padding that make a decoder return exactly the frames that were written.
    if (isComplete) lame_mp3_tags_fid(writer->lame, writer->file);
    // A failed flush to disk is a failed file.
    if (std::fflush(writer->file) != 0) isComplete = false;
    delete writer;
    return isComplete ? 1 : 0;
} catch (...) {
    delete writer;
    return 0;
}

} // extern "C"
