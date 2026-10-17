#include "sdengine.h"

#include "Denormals.h"
#include "Pcm.h"
#include "Player.h"

#include <lame.h>

#include <cstdio>
#include <memory>
#include <vector>

// Defined in CApi.cpp: the export only needs the source player.
Player *sdEnginePlayer(SdEngine *engine);

namespace {

constexpr int kChunkFrames = 16384;

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

SdExport *sd_export_begin(SdEngine *engine, const char *utf8OutputPath, int bitrateKbps) try {
    Player *source = sdEnginePlayer(engine);
    // The open file's decoded audio, shared read-only: no second decode, no second copy in memory,
    // and it stays alive for the export even if the player moves on to another file. Buffer, path
    // and rate come from one locked read: a file opening on another thread cannot mix two files.
    auto open = source->snapshot();
    if (!open.source) return nullptr;
    const int rate = open.source->sampleRate;

    auto exporter = std::make_unique<SdExport>();
    exporter->player = std::make_unique<Player>(rate, kChunkFrames);
    if (!exporter->player->open(std::move(open.source), open.path)) return nullptr;
    // The sound only: the metronome and count-in are practice aids, not part of the music, so the
    // export's player never gets them (they are not in Player::Settings).
    exporter->player->applySettings(source->settings());

    exporter->file = openFileUtf8(utf8OutputPath, "wb");
    if (!exporter->file) return nullptr;
    exporter->lame = lame_init();
    lame_set_in_samplerate(exporter->lame, rate);
    lame_set_num_channels(exporter->lame, 2);
    lame_set_brate(exporter->lame, bitrateKbps);
    lame_set_quality(exporter->lame, 2); // near-best; encoding is much faster than real time anyway
    if (lame_init_params(exporter->lame) < 0) return nullptr;

    exporter->player->setPlaying(true);
    return exporter.release();
} catch (...) {
    // Out of memory, most likely: nothing may cross into the JVM.
    return nullptr;
}

double sd_export_next(SdExport *exporter) {
    if (exporter->isComplete || exporter->hasFailed) return -1.0;
    float *planar[2] = {exporter->left.data(), exporter->right.data()};
    int frames = 0;
    {
        const ScopedFlushDenormals flushDenormals;
        frames = exporter->player->render(planar, kChunkFrames);
    }
    const bool reachedEnd = (exporter->player->takeEvents() & SD_EVENT_REACHED_END) != 0;
    const int bytes = lame_encode_buffer_ieee_float(exporter->lame, planar[0], planar[1], frames,
                                                    exporter->mp3.data(), int(exporter->mp3.size()));
    if (!exporter->write(bytes)) return -1.0;
    if (!reachedEnd) return exporter->player->position();

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
