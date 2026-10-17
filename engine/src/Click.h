#pragma once

#include <algorithm>
#include <array>
#include <cmath>
#include <vector>

/**
 * The metronome's click: two short blips synthesised once at the output rate (the accent, for the
 * downbeat, higher and louder), and one voice that plays them over the music. A new click restarts
 * the voice. Made at the output rate and mixed after the time stretch, so it is never stretched,
 * pitched or equalised.
 *
 * Audio thread only, after prepare(): trigger() and mix() never allocate or lock.
 */
class ClickTrack {
public:
    /** More than a block can ever hold: 400 BPM at 4× speed is 27 clicks a second. */
    static constexpr int kMaxTriggers = 64;

    void prepare(int outputRate) {
        synthesise(accent_, outputRate, kAccentHz, 1.0f);
        synthesise(normal_, outputRate, kNormalHz, 0.6f);
        stop();
    }

    /** A click at `frame` of the block being built; triggers come in frame order. */
    void trigger(int frame, bool accent) {
        if (count_ < kMaxTriggers) triggers_[size_t(count_++)] = {frame, accent};
    }

    /** Adds the click to out[0..frames) at `gain`, then forgets this block's triggers. */
    void mix(float *const *out, int frames, float gain) {
        int next = 0;
        for (int i = 0; i < frames; ++i) {
            while (next < count_ && triggers_[size_t(next)].frame <= i) {
                voice_ = triggers_[size_t(next)].accent ? &accent_ : &normal_;
                voicePosition_ = 0;
                ++next;
            }
            if (!voice_) continue;
            const float sample = (*voice_)[size_t(voicePosition_)] * gain;
            out[0][i] += sample;
            out[1][i] += sample;
            if (++voicePosition_ >= int(voice_->size())) voice_ = nullptr;
        }
        count_ = 0;
    }

    /** Silences the voice and drops pending triggers (stop, a new file). */
    void stop() {
        voice_ = nullptr;
        voicePosition_ = 0;
        count_ = 0;
    }

private:
    static constexpr double kAccentHz = 1760.0;
    static constexpr double kNormalHz = 1100.0;
    static constexpr double kSeconds = 0.030;
    static constexpr double kAttackSeconds = 0.001;
    static constexpr double kDecaySeconds = 0.006;

    /** A cosine with a 1 ms ramp up and an exponential decay: the first sample is already audible, so the onset is exact. */
    static void synthesise(std::vector<float> &out, int rate, double hz, float level) {
        const int frames = std::max(1, int(rate * kSeconds));
        const double attack = std::max(1.0, rate * kAttackSeconds);
        out.assign(size_t(frames), 0.f);
        for (int i = 0; i < frames; ++i) {
            const double envelope = std::min(1.0, (i + 1) / attack) * std::exp(-i / (rate * kDecaySeconds));
            out[size_t(i)] = float(level * 0.8 * envelope * std::cos(2 * M_PI * hz * i / rate));
        }
    }

    struct Trigger {
        int frame = 0;
        bool accent = false;
    };

    std::vector<float> accent_, normal_;
    const std::vector<float> *voice_ = nullptr;
    int voicePosition_ = 0;
    std::array<Trigger, kMaxTriggers> triggers_{};
    int count_ = 0;
};
