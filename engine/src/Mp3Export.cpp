#include "sdengine.h"

#include "Denormals.h"
#include "Pcm.h"
#include "Player.h"

#include <lame.h>

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <memory>
#include <vector>

// Defined in CApi.cpp: the export only needs the source player.
Player *sdEnginePlayer(SdEngine *engine);

namespace {

constexpr int kChunkFrames = 16384;
// A part starts and ends mid-sound: short fades avoid the click of a hard cut.
constexpr double kPartFadeSeconds = 0.01;
// After a seek the stretcher plays its preroll, audio from before the target, while it reports the
// target: a part skips it in blocks this small, then starts with a fade-in.
constexpr int kPrerollBlockFrames = 64;
// Bounds the skip if the position never moves (a stuck or silent stretcher): 2 s of output.
constexpr int kMaxPrerollBlocks = 2 * 48000 / kPrerollBlockFrames;

} // namespace

struct SdExport {
    std::unique_ptr<Player> player;
    lame_t lame = nullptr;
    FILE *file = nullptr;
    std::vector<float> left = std::vector<float>(kChunkFrames), right = std::vector<float>(kChunkFrames);
    // LAME's worst case: 1.25 × samples + 7200 bytes.
    std::vector<unsigned char> mp3 = std::vector<unsigned char>(kChunkFrames * 5 / 4 + 7200);
    // The part of the file to export, 0..1.
    double start = 0.0, end = 1.0;
    bool hasSkippedPreroll = false;
    bool isComplete = false;
    bool hasFailed = false;

    ~SdExport() {
        if (lame) lame_close(lame);
        if (file) std::fclose(file);
    }

    bool write(int bytes) {
        if (bytes < 0 || std::fwrite(mp3.data(), 1, size_t(bytes), file) != size_t(bytes)) hasFailed = true;
        return !hasFailed;
    }
};

namespace {

SdExport *beginExport(SdEngine *engine, const char *utf8OutputPath, int bitrateKbps, double start, double end,
                      bool isOriginalSound) try {
    if (!(start >= 0.0 && end <= 1.0 && start < end)) return nullptr;
    Player *source = sdEnginePlayer(engine);
    // The open file's decoded audio, shared read-only: no second decode, no second copy in memory,
    // and it stays alive for the export even if the player moves on to another file. Buffer, path
    // and rate come from one locked read: a file opening on another thread cannot mix two files.
    auto open = source->snapshot();
    if (!open.source) return nullptr;
    const int rate = open.source->sampleRate;

    auto exporter = std::make_unique<SdExport>();
    exporter->start = start;
    exporter->end = end;
    exporter->player = std::make_unique<Player>(rate, kChunkFrames);
    if (!exporter->player->open(std::move(open.source), open.path)) return nullptr;
    // Default settings are the file as it is: speed 1, pitch 0, centered mix, EQ off.
    exporter->player->applySettings(isOriginalSound ? Player::Settings{} : source->settings());

    exporter->file = openFileUtf8(utf8OutputPath, "wb");
    if (!exporter->file) return nullptr;
    exporter->lame = lame_init();
    lame_set_in_samplerate(exporter->lame, rate);
    lame_set_num_channels(exporter->lame, 2);
    lame_set_brate(exporter->lame, bitrateKbps);
    lame_set_quality(exporter->lame, 2); // near-best; encoding is much faster than real time anyway
    if (lame_init_params(exporter->lame) < 0) return nullptr;

    if (start > 0.0) exporter->player->seek(start);
    exporter->player->setPlaying(true);
    return exporter.release();
} catch (...) {
    // Out of memory, most likely: nothing may cross into the JVM.
    return nullptr;
}

void fadeIn(float *const *planar, int frames, int rate) {
    const int length = std::min(frames, std::max(1, int(rate * kPartFadeSeconds)));
    for (int i = 0; i < length; ++i) {
        const float gain = float(i) / float(length);
        planar[0][i] *= gain;
        planar[1][i] *= gain;
    }
}

/** Renders and drops the preroll; true once the output has reached the start of the part. */
bool skipPreroll(SdExport &exporter) {
    float *planar[2] = {exporter.left.data(), exporter.right.data()};
    for (int block = 0; block < kMaxPrerollBlocks; ++block) {
        exporter.player->render(planar, kPrerollBlockFrames);
        if (exporter.player->position() > exporter.start) return true;
    }
    return false;
}

void fadeOut(float *const *planar, int frames, int rate) {
    const int length = std::min(frames, std::max(1, int(rate * kPartFadeSeconds)));
    for (int i = frames - length; i < frames; ++i) {
        const float gain = float(frames - 1 - i) / float(length);
        planar[0][i] *= gain;
        planar[1][i] *= gain;
    }
}

} // namespace

extern "C" {

SdExport *sd_export_begin(SdEngine *engine, const char *utf8OutputPath, int bitrateKbps) {
    return beginExport(engine, utf8OutputPath, bitrateKbps, 0.0, 1.0, false);
}

SdExport *sd_export_begin_part(SdEngine *engine, const char *utf8OutputPath, int bitrateKbps, double start,
                               double end, int sound) {
    return beginExport(engine, utf8OutputPath, bitrateKbps, start, end, sound == SD_EXPORT_SOUND_ORIGINAL);
}

double sd_export_next(SdExport *exporter) {
    if (exporter->isComplete || exporter->hasFailed) return -1.0;
    float *planar[2] = {exporter->left.data(), exporter->right.data()};
    Player &player = *exporter->player;
    const bool isFirstChunk = !exporter->hasSkippedPreroll;
    if (isFirstChunk) {
        exporter->hasSkippedPreroll = true;
        const ScopedFlushDenormals flushDenormals;
        if (exporter->start > 0.0 && !skipPreroll(*exporter)) {
            exporter->hasFailed = true;
            return -1.0;
        }
    }
    const double before = player.position();
    int frames = 0;
    {
        const ScopedFlushDenormals flushDenormals;
        frames = player.render(planar, kChunkFrames);
    }
    bool reachedEnd = (player.takeEvents() & SD_EVENT_REACHED_END) != 0;
    const double after = player.position();
    if (isFirstChunk && exporter->start > 0.0) fadeIn(planar, frames, player.outputRate());
    if (!reachedEnd && exporter->end < 1.0 && after >= exporter->end) {
        // The chunk crosses the end of the part: keep the frames before it. The source moves at a
        // steady rate within a chunk, so the share of source covered is the share of output to keep.
        const double share = after > before ? (exporter->end - before) / (after - before) : 0.0;
        frames = std::clamp(int(std::lround(share * frames)), 0, frames);
        fadeOut(planar, frames, player.outputRate());
        reachedEnd = true;
    }
    const int bytes = lame_encode_buffer_ieee_float(exporter->lame, planar[0], planar[1], frames,
                                                    exporter->mp3.data(), int(exporter->mp3.size()));
    if (!exporter->write(bytes)) return -1.0;
    if (!reachedEnd) {
        const double progress = (after - exporter->start) / (exporter->end - exporter->start);
        return std::clamp(progress, 0.0, 1.0);
    }

    if (!exporter->write(lame_encode_flush(exporter->lame, exporter->mp3.data(), int(exporter->mp3.size()))))
        return -1.0;
    exporter->isComplete = true;
    return -1.0;
}

int sd_export_end(SdExport *exporter) {
    const bool ok = exporter->isComplete && !exporter->hasFailed;
    delete exporter;
    return ok ? 1 : 0;
}

} // extern "C"
