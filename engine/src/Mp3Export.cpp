#include "sdengine.h"

#include "Denormals.h"
#include "Pcm.h"
#include "Player.h"

#include <lame.h>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <limits>
#include <memory>
#include <vector>

// Defined in CApi.cpp: the export only needs the source player.
Player *sdEnginePlayer(SdEngine *engine);

namespace {

constexpr int kChunkFrames = 16384;
// The fade-out at a slice's end: as short as the player's own fades, so it only hides the cut.
constexpr double kSliceFadeSeconds = 0.010;
// Mirrors TimeStretcher::setSpeed's clamp: a slice lasts what it really plays for.
constexpr double kMinSpeed = 0.05, kMaxSpeed = 4.0;

/** Ramps the last `length` frames of the block down to silence. */
void fadeOut(float *const *out, int frames, int length) {
    length = std::min(frames, length);
    for (int i = frames - length; i < frames; ++i) {
        const float g = float(frames - 1 - i) / float(length);
        out[0][i] *= g;
        out[1][i] *= g;
    }
}

} // namespace

struct SdExport {
    std::unique_ptr<Player> player;
    lame_t lame = nullptr;
    FILE *file = nullptr;
    std::vector<float> left = std::vector<float>(kChunkFrames), right = std::vector<float>(kChunkFrames);
    // LAME's worst case: 1.25 × samples + 7200 bytes.
    std::vector<unsigned char> mp3 = std::vector<unsigned char>(kChunkFrames * 5 / 4 + 7200);
    bool isComplete = false;
    bool hasFailed = false;
    // Output frames to write. The whole file is open-ended: it stops at the end-of-file event.
    int64_t targetFrames = std::numeric_limits<int64_t>::max();
    // The range's expected length in output frames, for the progress (also when open-ended).
    int64_t expectedFrames = 1;
    int64_t written = 0;
    int fadeFrames = 0; // 0 = no fade-out: the file's own end

    ~SdExport() {
        if (lame) lame_close(lame);
        if (file) std::fclose(file);
    }

    bool write(int bytes) {
        if (bytes < 0 || std::fwrite(mp3.data(), 1, size_t(bytes), file) != size_t(bytes)) hasFailed = true;
        return !hasFailed;
    }
};

extern "C" {

SdExport *sd_export_begin(SdEngine *engine, const char *utf8OutputPath, int bitrateKbps) {
    return sd_export_begin_range(engine, utf8OutputPath, bitrateKbps, 0.0, 1.0);
}

SdExport *sd_export_begin_range(SdEngine *engine, const char *utf8OutputPath, int bitrateKbps, double start,
                                double end) try {
    start = std::clamp(start, 0.0, 1.0);
    end = std::clamp(end, 0.0, 1.0);
    if (!(end > start)) return nullptr;
    Player *source = sdEnginePlayer(engine);
    // The open file's decoded audio, shared read-only: no second decode, no second copy in memory,
    // and it stays alive for the export even if the player moves on to another file. Buffer, path
    // and rate come from one locked read: a file opening on another thread cannot mix two files.
    // Each slice of a batch takes its own reference to the same buffer.
    auto open = source->snapshot();
    if (!open.source) return nullptr;
    const int rate = open.source->sampleRate;
    const double rangeFrames = (end - start) * double(open.source->frames);

    auto exporter = std::make_unique<SdExport>();
    exporter->player = std::make_unique<Player>(rate, kChunkFrames);
    if (!exporter->player->open(std::move(open.source), open.path)) return nullptr;
    // The sound settings only: the loop and the practice aids the live player plays on top of the
    // music (metronome, count-in) never reach the export's player.
    const Player::Settings settings = source->settings();
    exporter->player->applySettings(settings);

    // The export's player runs at the file's rate, so the output lasts the range's frames over the
    // speed. Pitch does not change the length.
    const double speed = std::clamp(settings.speed, kMinSpeed, kMaxSpeed);
    const int64_t expected = std::llround(rangeFrames / speed);
    if (expected < 1) return nullptr;
    exporter->expectedFrames = expected;
    if (end < 1.0) {
        // A slice: cut at its length, with a short fade-out so the cut does not click.
        exporter->targetFrames = expected;
        exporter->fadeFrames = std::max(1, int(rate * kSliceFadeSeconds));
    }

    exporter->file = openFileUtf8(utf8OutputPath, "wb");
    if (!exporter->file) return nullptr;
    exporter->lame = lame_init();
    lame_set_in_samplerate(exporter->lame, rate);
    lame_set_num_channels(exporter->lame, 2);
    lame_set_brate(exporter->lame, bitrateKbps);
    lame_set_quality(exporter->lame, 2); // near-best; encoding is much faster than real time anyway
    if (lame_init_params(exporter->lame) < 0) return nullptr;

    // The seek lands in the first block, with the start: the slice fades in as playback does.
    if (start > 0.0) exporter->player->seek(start);
    exporter->player->setPlaying(true);
    return exporter.release();
} catch (...) {
    // Out of memory, most likely: nothing may cross into the JVM.
    return nullptr;
}

double sd_export_next(SdExport *exporter) {
    if (exporter->isComplete || exporter->hasFailed) return -1.0;
    float *planar[2] = {exporter->left.data(), exporter->right.data()};
    const int64_t remaining = exporter->targetFrames - exporter->written;
    int chunk = int(std::min<int64_t>(kChunkFrames, remaining));
    // The fade-out must fall in one chunk: stop this one short of it rather than split it.
    if (remaining > chunk && remaining - chunk < exporter->fadeFrames) chunk = int(remaining - exporter->fadeFrames);
    int frames = 0;
    {
        const ScopedFlushDenormals flushDenormals;
        frames = exporter->player->render(planar, chunk);
    }
    exporter->written += frames;
    const bool reachedEnd = (exporter->player->takeEvents() & SD_EVENT_REACHED_END) != 0;
    const bool isLast = reachedEnd || exporter->written >= exporter->targetFrames;
    if (isLast && exporter->fadeFrames > 0) fadeOut(planar, frames, exporter->fadeFrames);
    const int bytes = lame_encode_buffer_ieee_float(exporter->lame, planar[0], planar[1], frames,
                                                    exporter->mp3.data(), int(exporter->mp3.size()));
    if (!exporter->write(bytes)) return -1.0;
    // Frames written over the range's length: it only grows, whatever the stretcher's preroll does.
    if (!isLast) return std::min(1.0, double(exporter->written) / double(exporter->expectedFrames));

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
