#include "sdengine.h"

#include "Player.h"

#include <miniaudio.h>

#include <algorithm>
#include <memory>
#include <vector>

namespace {

constexpr int kMaxBlock = 4096;

} // namespace

struct SdEngine {
    std::unique_ptr<Player> player;
    std::unique_ptr<ma_device> device;
    std::vector<float> left, right;

    ~SdEngine() {
        if (device) ma_device_uninit(device.get());
    }
};

namespace {

void onAudio(ma_device *device, void *output, const void *, ma_uint32 frameCount) {
    auto &engine = *static_cast<SdEngine *>(device->pUserData);
    auto *out = static_cast<float *>(output);
    float *planar[2] = {engine.left.data(), engine.right.data()};
    for (ma_uint32 done = 0; done < frameCount;) {
        const int n = int(std::min<ma_uint32>(frameCount - done, kMaxBlock));
        engine.player->render(planar, n);
        for (int i = 0; i < n; ++i) {
            out[(done + ma_uint32(i)) * 2] = planar[0][i];
            out[(done + ma_uint32(i)) * 2 + 1] = planar[1][i];
        }
        done += ma_uint32(n);
    }
}

} // namespace

Player *sdEnginePlayer(SdEngine *engine) { return engine->player.get(); }

extern "C" {

SdEngine *sd_engine_create(int withDevice, int manualSampleRate) {
    auto engine = std::make_unique<SdEngine>();
    engine->left.assign(kMaxBlock, 0.f);
    engine->right.assign(kMaxBlock, 0.f);
    if (!withDevice) {
        if (manualSampleRate <= 0) return nullptr;
        engine->player = std::make_unique<Player>(manualSampleRate, kMaxBlock);
        return engine.release();
    }

    engine->device = std::make_unique<ma_device>();
    ma_device_config config = ma_device_config_init(ma_device_type_playback);
    config.playback.format = ma_format_f32;
    config.playback.channels = 2;
    config.sampleRate = 0; // the device's own rate: Bungee resamples, the OS does not have to
    config.dataCallback = onAudio;
    config.pUserData = engine.get();
    if (ma_device_init(nullptr, &config, engine->device.get()) != MA_SUCCESS) {
        engine->device.reset();
        return nullptr;
    }
    // The callback only runs after ma_device_start, so the player can be created now.
    engine->player = std::make_unique<Player>(int(engine->device->sampleRate), kMaxBlock);
    if (ma_device_start(engine->device.get()) != MA_SUCCESS) return nullptr;
    return engine.release();
}

void sd_engine_destroy(SdEngine *engine) { delete engine; }

int sd_engine_open(SdEngine *engine, const char *utf8Path) {
    // A decode can run out of memory (a long file, a corrupt header): an exception crossing into the
    // JVM would end the whole app. A file that cannot be decoded is all it is.
    try {
        return engine->player->open(utf8Path) ? 1 : 0;
    } catch (...) {
        return 0;
    }
}

void sd_engine_close(SdEngine *engine) { engine->player->close(); }

int sd_engine_is_open(const SdEngine *engine) { return engine->player->isOpen() ? 1 : 0; }

double sd_engine_duration_seconds(const SdEngine *engine) { return engine->player->durationSeconds(); }

void sd_engine_set_playing(SdEngine *engine, int playing) { engine->player->setPlaying(playing != 0); }

int sd_engine_is_playing(const SdEngine *engine) { return engine->player->isPlaying() ? 1 : 0; }

double sd_engine_position(const SdEngine *engine) { return engine->player->position(); }

void sd_engine_seek(SdEngine *engine, double position) { engine->player->seek(position); }

void sd_engine_set_speed(SdEngine *engine, double ratio) { engine->player->setSpeed(ratio); }

void sd_engine_set_pitch(SdEngine *engine, double semitones) { engine->player->setPitch(semitones); }

void sd_engine_set_loop(SdEngine *engine, double start, double end, int enabled) {
    engine->player->setLoop(start, end, enabled != 0);
}

void sd_engine_set_loop_and_seek(SdEngine *engine, double start, double end, int enabled, double position) {
    engine->player->setLoop(start, end, enabled != 0, position);
}

void sd_engine_set_mix(SdEngine *engine, double mixRight) { engine->player->setMix(mixRight); }

void sd_engine_set_eq_enabled(SdEngine *engine, int enabled) { engine->player->setEqEnabled(enabled != 0); }

void sd_engine_set_eq_band(SdEngine *engine, int band, double gainDb) { engine->player->setEqBand(band, gainDb); }

void sd_engine_set_preamp(SdEngine *engine, double gainDb) { engine->player->setPreamp(gainDb); }

void sd_engine_set_output_gain(SdEngine *engine, double gain) { engine->player->setOutputGain(gain); }

void sd_engine_set_normalization(SdEngine *engine, int enabled) { engine->player->setNormalization(enabled != 0); }

double sd_engine_loudness_lufs(const SdEngine *engine) { return engine->player->loudnessLufs(); }

double sd_engine_normalization_gain_db(const SdEngine *engine) { return engine->player->normalizationGainDb(); }

unsigned sd_engine_take_events(SdEngine *engine) { return engine->player->takeEvents(); }

int sd_engine_sample_rate(const SdEngine *engine) { return engine->player->outputRate(); }

int sd_engine_render(SdEngine *engine, float *interleaved, int frames) {
    if (engine->device) return 0;
    float *planar[2] = {engine->left.data(), engine->right.data()};
    for (int done = 0; done < frames;) {
        const int n = std::min(frames - done, kMaxBlock);
        engine->player->render(planar, n);
        for (int i = 0; i < n; ++i) {
            interleaved[(done + i) * 2] = planar[0][i];
            interleaved[(done + i) * 2 + 1] = planar[1][i];
        }
        done += n;
    }
    return 1;
}

} // extern "C"
