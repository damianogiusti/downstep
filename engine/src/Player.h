#pragma once

#include "Equalizer.h"
#include "Pcm.h"
#include "TimeStretcher.h"

#include <array>
#include <atomic>
#include <memory>
#include <mutex>
#include <string>

/**
 * Everything between the decoded file and the output: mix → time stretch → EQ → preamp → fades.
 *
 * Controls are atomics written by the control thread. render() runs on the audio thread (or the
 * caller's thread in manual mode); it applies the controls at the start of each block and never
 * blocks: while open() or close() hold the lock it outputs silence.
 */
class Player {
public:
    /** Sound settings, to copy one player's sound onto another (export). */
    struct Settings {
        double speed = 1.0;
        double pitch = 0.0;
        double mix = 0.5;
        bool eqEnabled = false;
        std::array<double, Equalizer::kBands> eqGainsDb{};
        double preampDb = 0.0;
    };

    explicit Player(int outputRate, int maxBlockFrames);

    bool open(const std::string &path);
    /** Plays audio decoded elsewhere, shared read-only: the export reuses the player's buffer this way. */
    bool open(std::shared_ptr<const PcmBuffer> decoded, const std::string &path);
    void close();
    bool isOpen() const { return isOpen_.load(); }
    double durationSeconds() const { return durationSeconds_.load(); }
    /** Control thread only. */
    const std::string &path() const { return path_; }
    /** Control thread only. Null when no file is open. */
    std::shared_ptr<const PcmBuffer> source() const { return source_; }
    int sourceSampleRate() const { return sourceSampleRate_.load(); }
    Settings settings() const;
    void applySettings(const Settings &settings);

    void setPlaying(bool playing);
    bool isPlaying() const { return wantsPlaying_.load(); }
    /** 0..1 of the file. */
    double position() const;
    void seek(double position);

    void setSpeed(double ratio) { speed_.store(ratio); }
    void setPitch(double semitones) { pitch_.store(semitones); }
    /** `seek` >= 0 also moves playback there, in the same block as the new loop. */
    void setLoop(double start, double end, bool enabled, double seek = -1.0);
    void setMix(double mixRight) { mix_.store(mixRight); }
    void setEqEnabled(bool enabled);
    void setEqBand(int band, double gainDb);
    void setPreamp(double gainDb);
    void setOutputGain(double gain) { outputGain_.store(gain); }

    unsigned takeEvents() { return events_.exchange(0); }
    int outputRate() const { return outputRate_; }

    /**
     * Planar stereo. Always fills `frames` frames, with silence when stopped. Returns how many frames
     * carry playback: `frames`, fewer in the block that reaches the end, 0 when stopped.
     */
    int render(float *const *out, int frames);

private:
    /** Returns the seek that came with a new loop, or -1. */
    double applyControls();
    void applyFade(float *const *out, int frames, bool fadeIn);

    const int outputRate_;
    const int maxBlockFrames_;

    std::mutex lock_; // structure: source and stretcher
    // Written only by the control thread, under the lock; the audio thread reads it under the lock.
    std::shared_ptr<const PcmBuffer> source_;
    std::string path_;
    std::atomic<int> sourceSampleRate_{0};
    TimeStretcher stretcher_;
    Equalizer equalizer_;

    std::atomic<bool> isOpen_{false};
    std::atomic<double> durationSeconds_{0};
    std::atomic<int64_t> sourceFrames_{0};

    std::atomic<bool> wantsPlaying_{false};
    std::atomic<double> speed_{1.0};
    std::atomic<double> pitch_{0.0};
    std::atomic<double> mix_{0.5};
    // Loop points travel together: a seqlock (odd version = being written) lets the audio thread
    // take a consistent snapshot, or keep the previous one, without locking.
    std::atomic<unsigned> loopVersion_{0};
    std::atomic<double> loopStart_{0.0}, loopEnd_{1.0}, loopSeek_{-1.0};
    std::atomic<bool> loopOn_{false};
    unsigned appliedLoopVersion_ = ~0u;
    std::atomic<double> seekTo_{-1.0};
    std::atomic<double> positionFrames_{0.0};
    std::atomic<double> outputGain_{1.0};
    std::atomic<unsigned> events_{0};

    // EQ settings: copied into the filters on the audio thread when the version changes.
    std::atomic<bool> eqEnabled_{false};
    std::array<std::atomic<double>, Equalizer::kBands> eqGainsDb_{};
    std::atomic<double> preampDb_{0.0};
    std::atomic<unsigned> eqVersion_{1};
    unsigned appliedEqVersion_ = 0;
    float preampGain_ = 1.f;

    // Audio-thread state.
    bool isRunning_ = false; // producing audio, including a fade-out block
    bool needsFadeIn_ = false;
};
