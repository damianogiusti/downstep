#pragma once

#include "Click.h"
#include "Equalizer.h"
#include "Isolation.h"
#include "Pcm.h"
#include "Peaks.h"
#include "TimeStretcher.h"

#include <array>
#include <atomic>
#include <memory>
#include <mutex>
#include <string>

/**
 * Everything between the decoded file and the output: mix → time stretch → EQ → preamp → isolation
 * → fades, then the metronome and count-in clicks on top (never stretched, pitched or equalised).
 *
 * Controls are atomics written by the control thread. render() runs on the audio thread (or the
 * caller's thread in manual mode); it applies the controls at the start of each block and never
 * blocks: while open() or close() hold the lock it outputs silence.
 */
class Player {
public:
    /**
     * Sound settings, to copy one player's sound onto another (export). Isolate, the metronome and
     * the count-in are not here on purpose: they are practice aids, like the loop, and an export never
     * contains them.
     */
    struct Settings {
        double speed = 1.0;
        double pitch = 0.0;
        double mix = 0.5;
        bool eqEnabled = false;
        int eqBandCount = 0;
        std::array<EqBandParams, Equalizer::kMaxBands> eqBands{};
        double preampDb = 0.0;
    };

    explicit Player(int outputRate, int maxBlockFrames);

    bool open(const std::string &path);
    /** Plays audio decoded elsewhere, shared read-only: the export reuses the player's buffer this way. */
    bool open(std::shared_ptr<const PcmBuffer> decoded, const std::string &path);
    void close();
    bool isOpen() const { return isOpen_.load(); }
    double durationSeconds() const { return durationSeconds_.load(); }

    /** The open file's decoded audio and path, read together; a null source when nothing is open. */
    struct Snapshot {
        std::shared_ptr<const PcmBuffer> source;
        std::string path;
    };
    /**
     * Any thread: takes the lock, so an open() or close() on another thread (the app decodes off the
     * UI thread, and exports on yet another) cannot swap the buffer while it is copied. The audio
     * thread only try-locks: at worst it plays one block of silence.
     */
    Snapshot snapshot() const {
        std::lock_guard<std::mutex> guard(lock_);
        return {source_, path_};
    }
    /** As snapshot(), the source alone. */
    std::shared_ptr<const PcmBuffer> source() const { return snapshot().source; }
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
    /** A peaking bell: 20 Hz..20 kHz, ±12 dB, Q 0.3..10, the three set together. */
    void setEqBandParams(int band, double frequencyHz, double gainDb, double q);
    void setEqBandCount(int count);
    void setPreamp(double gainDb);
    /** Isolate's mask: bit 0 low, bit 1 mid, bit 2 high; 0 = the full mix. */
    void setIsolation(int mask);
    void setOutputGain(double gain) { outputGain_.store(gain); }

    /**
     * The open file's beat grid at 100% speed: beats at beatOffsetSeconds + k·60/bpm of the file, the
     * first of every `beatsPerBar` accented. bpm <= 0 clears it: no metronome, and the count-in counts
     * at 120 BPM. It stays across open(), like the speed: the app sets it for each track.
     */
    void setTempo(double bpm, double beatOffsetSeconds, int beatsPerBar);
    /** The click over playback, on the beat grid; `volume` 0..1 is the click's own level (count-in too). */
    void setMetronome(bool enabled, double volume);
    /**
     * `beats` clicks at the effective tempo (bpm × speed) before playback starts, the music then
     * starting on the beat after the last one; 0 = off. `everyLoop`: also before each restart of the
     * loop (playback stops at its end, counts in, and goes on from its start; no end event).
     */
    void setCountIn(int beats, bool everyLoop);
    /** Count-in clicks still to play; 0 when not counting in. Any thread. */
    int countInRemaining() const { return countInRemaining_.load(); }
    static constexpr int kMaxCountInBeats = 64;

    /**
     * Waveform outline of the open file (see PeakSummary::read). Any thread: it never touches the
     * audio thread's lock. 0 when no file is open, or one was opened from shared audio (export).
     */
    int peaks(double from, double to, float *out, int buckets) const;

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
    /** Music into out[from..frames): stretch, EQ, gain, fade-in. Returns the frames that carry it. */
    int renderMusic(float *const *out, int from, int frames);
    void armCountIn(int beats);
    /** Count-in clicks for out[from..from+frames), which stay silent otherwise. */
    void countIn(float *const *out, int from, int frames);
    /** Metronome clicks on the beats crossed by sourcePositions_[from..from+frames). */
    void placeBeats(int from, int frames);
    /** Fades out[from..end) to silence at `end`, over at most the usual fade length. */
    void fadeOutBefore(float *const *out, int end, int from);
    /** Audio-thread click state back to rest (open, close, stop). */
    void resetClicks();

    const int outputRate_;
    const int maxBlockFrames_;

    mutable std::mutex lock_; // structure: source and stretcher
    // Written only by the control thread, under the lock; the audio thread reads it under the lock.
    std::shared_ptr<const PcmBuffer> source_;
    std::string path_;
    std::atomic<int> sourceSampleRate_{0};
    TimeStretcher stretcher_;
    Equalizer equalizer_;
    IsolationFilter isolation_;

    // Its own lock: a waveform read must never make the audio thread skip a block.
    mutable std::mutex peaksLock_;
    std::shared_ptr<const PeakSummary> peaks_;

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

    // EQ and isolation settings: a seqlock like the loop's, so a band's frequency, gain and Q always
    // arrive together. The audio thread hands them to the filters, which glide to them.
    template <typename Write> void writeEq(Write write);
    std::atomic<unsigned> eqVersion_{0};
    std::atomic<bool> eqEnabled_{false};
    std::atomic<int> eqBandCount_{0};
    std::array<std::atomic<double>, Equalizer::kMaxBands> eqFrequencyHz_{}, eqGainDb_{}, eqQ_{};
    std::atomic<double> preampDb_{0.0};
    std::atomic<int> isolationMask_{0};
    unsigned appliedEqVersion_ = ~0u;

    // Tempo: travels together, by seqlock like the loop.
    std::atomic<unsigned> tempoVersion_{0};
    std::atomic<double> tempoBpm_{0.0}, tempoOffsetSeconds_{0.0};
    std::atomic<int> tempoBeatsPerBar_{4};
    unsigned appliedTempoVersion_ = ~0u;
    std::atomic<bool> metronomeOn_{false};
    std::atomic<double> clickVolume_{0.7};
    std::atomic<int> countInBeats_{0};
    std::atomic<bool> countInEveryLoop_{false};
    std::atomic<int> countInRemaining_{0};

    // Audio-thread state.
    bool isRunning_ = false; // producing audio, including a fade-out block
    bool needsFadeIn_ = false;
    ClickTrack click_;
    std::vector<double> sourcePositions_; // per output frame of the block, from the stretcher
    bool hasPositions_ = false;
    double bpm_ = 0, beatOffsetSeconds_ = 0;
    int beatsPerBar_ = 4;
    double lastSourceFrame_ = -1; // < 0: no previous frame (start, seek)
    // Count-in in progress: clicks at round(k·interval) frames from its start, music at round(beats·interval).
    bool isCountingIn_ = false;
    int countInTotalBeats_ = 0, countInNextBeat_ = 0;
    double countInInterval_ = 0;
    int64_t countInElapsed_ = 0, countInLength_ = 0;
};
