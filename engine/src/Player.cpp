#include "Player.h"

#include "sdengine.h"

#include <algorithm>
#include <cmath>
#include <utility>

namespace {

// A fixed ~10 ms fade: about one output buffer, but independent of the device.
constexpr double kFadeSeconds = 0.010;

} // namespace

Player::Player(int outputRate, int maxBlockFrames) : outputRate_(outputRate), maxBlockFrames_(maxBlockFrames) {
    for (int b = 0; b < Equalizer::kMaxBands; ++b) {
        eqFrequencyHz_[size_t(b)].store(1000.0);
        eqGainDb_[size_t(b)].store(0.0);
        eqQ_[size_t(b)].store(1.0);
    }
    equalizer_.setSampleRate(outputRate);
    isolation_.setSampleRate(outputRate);
}

bool Player::open(const std::string &path) {
    // Decode outside the lock: it can take a second, and playback of the old file can go on meanwhile.
    auto decoded = std::make_shared<PcmBuffer>();
    std::string error;
    if (!decodeFile(path, *decoded, error)) return false;
    // Also outside the lock, and only here: the export's player never draws a waveform.
    auto summary = PeakSummary::of(*decoded);
    if (!open(std::move(decoded), path)) return false;
    std::lock_guard<std::mutex> guard(peaksLock_);
    peaks_ = std::move(summary);
    return true;
}

int Player::peaks(double from, double to, float *out, int buckets) const {
    std::shared_ptr<const PeakSummary> summary;
    {
        std::lock_guard<std::mutex> guard(peaksLock_);
        summary = peaks_;
    }
    return summary ? summary->read(from, to, out, buckets) : 0;
}

bool Player::open(std::shared_ptr<const PcmBuffer> decoded, const std::string &path) {
    if (!decoded || decoded->frames <= 0 || decoded->channels < 1) return false;
    {
        std::lock_guard<std::mutex> guard(peaksLock_);
        peaks_.reset();
    }
    std::shared_ptr<const PcmBuffer> previous;
    {
        std::lock_guard<std::mutex> guard(lock_);
        wantsPlaying_.store(false);
        isRunning_ = false;
        previous = std::exchange(source_, std::move(decoded));
        path_ = path;
        sourceSampleRate_.store(source_->sampleRate);
        stretcher_.prepare(*source_, outputRate_, maxBlockFrames_);
        equalizer_.reset();
        isolation_.reset();
        sourceFrames_.store(source_->frames);
        durationSeconds_.store(source_->seconds());
        positionFrames_.store(0.0);
        seekTo_.store(-1.0);
        setLoop(0.0, 1.0, false);
        isOpen_.store(true);
    }
    // `previous` (maybe the last owner of a 100 MB buffer) is freed here, outside the lock.
    return true;
}

void Player::close() {
    {
        std::lock_guard<std::mutex> peaksGuard(peaksLock_);
        peaks_.reset();
    }
    std::shared_ptr<const PcmBuffer> previous; // declared before the guard: freed after unlocking
    std::lock_guard<std::mutex> guard(lock_);
    wantsPlaying_.store(false);
    isRunning_ = false;
    isOpen_.store(false);
    previous = std::move(source_);
    path_.clear();
    sourceFrames_.store(0);
    durationSeconds_.store(0.0);
    positionFrames_.store(0.0);
}

Player::Settings Player::settings() const {
    Settings s;
    s.speed = speed_.load();
    s.pitch = pitch_.load();
    s.mix = mix_.load();
    s.eqEnabled = eqEnabled_.load();
    s.eqBandCount = eqBandCount_.load();
    for (int b = 0; b < Equalizer::kMaxBands; ++b)
        s.eqBands[size_t(b)] = {eqFrequencyHz_[size_t(b)].load(), eqGainDb_[size_t(b)].load(), eqQ_[size_t(b)].load()};
    s.preampDb = preampDb_.load();
    return s;
}

void Player::applySettings(const Settings &settings) {
    setSpeed(settings.speed);
    setPitch(settings.pitch);
    setMix(settings.mix);
    for (int b = 0; b < Equalizer::kMaxBands; ++b) {
        const EqBandParams &band = settings.eqBands[size_t(b)];
        setEqBandParams(b, band.frequencyHz, band.gainDb, band.q);
    }
    setEqBandCount(settings.eqBandCount);
    setPreamp(settings.preampDb);
    setEqEnabled(settings.eqEnabled);
}

void Player::setPlaying(bool playing) {
    if (playing && !isOpen_.load()) return;
    wantsPlaying_.store(playing);
}

double Player::position() const {
    const int64_t frames = sourceFrames_.load();
    return frames > 0 ? std::clamp(positionFrames_.load() / double(frames), 0.0, 1.0) : 0.0;
}

void Player::seek(double position) {
    const double clamped = std::clamp(position, 0.0, 1.0);
    seekTo_.store(clamped);
    // Report the new position right away, before the audio thread picks it up.
    positionFrames_.store(clamped * double(sourceFrames_.load()));
}

void Player::setLoop(double start, double end, bool enabled, double seek) {
    loopVersion_.fetch_add(1, std::memory_order_acq_rel); // odd: being written
    loopStart_.store(std::clamp(start, 0.0, 1.0), std::memory_order_relaxed);
    loopEnd_.store(std::clamp(end, 0.0, 1.0), std::memory_order_relaxed);
    loopOn_.store(enabled, std::memory_order_relaxed);
    loopSeek_.store(seek >= 0 ? std::clamp(seek, 0.0, 1.0) : -1.0, std::memory_order_relaxed);
    loopVersion_.fetch_add(1, std::memory_order_release);
    if (seek >= 0) positionFrames_.store(std::clamp(seek, 0.0, 1.0) * double(sourceFrames_.load()));
}

template <typename Write> void Player::writeEq(Write write) {
    eqVersion_.fetch_add(1, std::memory_order_relaxed); // odd: being written
    // Keeps the writes below after the odd version: a reader that sees any of them must also see it
    // when it checks the version again (pairs with the acquire fence in applyControls).
    std::atomic_thread_fence(std::memory_order_release);
    write();
    eqVersion_.fetch_add(1, std::memory_order_release);
}

void Player::setEqEnabled(bool enabled) {
    writeEq([&] { eqEnabled_.store(enabled, std::memory_order_relaxed); });
}

void Player::setEqBandParams(int band, double frequencyHz, double gainDb, double q) {
    if (band < 0 || band >= Equalizer::kMaxBands) return;
    if (!std::isfinite(frequencyHz) || !std::isfinite(gainDb) || !std::isfinite(q)) return;
    writeEq([&] {
        eqFrequencyHz_[size_t(band)].store(std::clamp(frequencyHz, 20.0, 20000.0), std::memory_order_relaxed);
        eqGainDb_[size_t(band)].store(std::clamp(gainDb, -12.0, 12.0), std::memory_order_relaxed);
        eqQ_[size_t(band)].store(std::clamp(q, 0.3, 10.0), std::memory_order_relaxed);
    });
}

void Player::setEqBandCount(int count) {
    writeEq([&] { eqBandCount_.store(std::clamp(count, 0, Equalizer::kMaxBands), std::memory_order_relaxed); });
}

void Player::setPreamp(double gainDb) {
    if (!std::isfinite(gainDb)) return;
    writeEq([&] { preampDb_.store(gainDb, std::memory_order_relaxed); });
}

void Player::setIsolation(int mask) {
    writeEq([&] { isolationMask_.store(mask & 7, std::memory_order_relaxed); });
}

double Player::applyControls() {
    const double frames = double(source_->frames);
    stretcher_.setSpeed(speed_.load());
    stretcher_.setPitch(pitch_.load());
    stretcher_.setMix(mix_.load());

    double loopSeek = -1.0;
    const unsigned before = loopVersion_.load(std::memory_order_acquire);
    if ((before & 1u) == 0 && before != appliedLoopVersion_) {
        const double start = loopStart_.load(std::memory_order_relaxed);
        const double end = loopEnd_.load(std::memory_order_relaxed);
        const bool on = loopOn_.load(std::memory_order_relaxed);
        const double seek = loopSeek_.load(std::memory_order_relaxed);
        std::atomic_thread_fence(std::memory_order_acquire);
        if (loopVersion_.load(std::memory_order_relaxed) == before) { // else: retry next block
            appliedLoopVersion_ = before;
            stretcher_.setLoop(int64_t(start * frames), int64_t(end * frames), on);
            loopSeek = seek;
        }
    }

    const unsigned eqBefore = eqVersion_.load(std::memory_order_acquire);
    if ((eqBefore & 1u) == 0 && eqBefore != appliedEqVersion_) {
        EqParams eq;
        eq.enabled = eqEnabled_.load(std::memory_order_relaxed);
        eq.preampDb = preampDb_.load(std::memory_order_relaxed);
        eq.bandCount = eqBandCount_.load(std::memory_order_relaxed);
        for (int b = 0; b < Equalizer::kMaxBands; ++b)
            eq.bands[size_t(b)] = {eqFrequencyHz_[size_t(b)].load(std::memory_order_relaxed),
                                   eqGainDb_[size_t(b)].load(std::memory_order_relaxed),
                                   eqQ_[size_t(b)].load(std::memory_order_relaxed)};
        const int mask = isolationMask_.load(std::memory_order_relaxed);
        std::atomic_thread_fence(std::memory_order_acquire);
        if (eqVersion_.load(std::memory_order_relaxed) == eqBefore) { // else: retry next block
            appliedEqVersion_ = eqBefore;
            // Glide only what is heard: while stopped (a new player, the export's, or a file just
            // opened) the settings are simply in place when playback starts.
            equalizer_.setTarget(eq, !isRunning_);
            isolation_.setMask(mask, !isRunning_);
        }
    }
    return loopSeek;
}

int Player::render(float *const *out, int frames) {
    std::unique_lock<std::mutex> guard(lock_, std::try_to_lock);
    if (!guard.owns_lock() || !isOpen_.load()) {
        std::fill(out[0], out[0] + frames, 0.f);
        std::fill(out[1], out[1] + frames, 0.f);
        return 0;
    }
    const double loopSeek = applyControls();

    const double plainSeek = seekTo_.exchange(-1.0);
    const double seek = loopSeek >= 0 ? loopSeek : plainSeek;
    const bool wantsPlaying = wantsPlaying_.load();
    if (seek >= 0) {
        stretcher_.seek(seek * double(source_->frames));
        equalizer_.reset();
        isolation_.reset();
        needsFadeIn_ = true;
    }
    if (wantsPlaying && !isRunning_) {
        // Start from where the user is, with a fresh grain history.
        if (seek < 0) stretcher_.seek(positionFrames_.load());
        equalizer_.reset();
        isolation_.reset();
        isRunning_ = true;
        needsFadeIn_ = true;
    }
    if (!isRunning_) {
        std::fill(out[0], out[0] + frames, 0.f);
        std::fill(out[1], out[1] + frames, 0.f);
        return 0;
    }

    const int rendered = stretcher_.render(out, frames);
    equalizer_.process(out, frames); // with the preamp
    isolation_.process(out, frames);
    const float gain = float(outputGain_.load());
    if (gain != 1.f) {
        for (int c = 0; c < 2; ++c)
            for (int i = 0; i < frames; ++i) out[c][i] *= gain;
    }
    if (needsFadeIn_) {
        applyFade(out, frames, true);
        needsFadeIn_ = false;
    }
    positionFrames_.store(stretcher_.position());

    if (rendered < frames) {
        // End of the file with the loop off: stop and tell the control side.
        isRunning_ = false;
        wantsPlaying_.store(false);
        positionFrames_.store(double(source_->frames));
        events_.fetch_or(SD_EVENT_REACHED_END);
    } else if (!wantsPlaying) {
        applyFade(out, frames, false);
        isRunning_ = false;
    }
    return rendered;
}

void Player::applyFade(float *const *out, int frames, bool fadeIn) {
    const int length = std::min(frames, std::max(1, int(outputRate_ * kFadeSeconds)));
    for (int i = 0; i < frames; ++i) {
        float g;
        if (fadeIn) g = i < length ? float(i) / length : 1.f;
        else {
            const int fromEnd = frames - i;
            g = fromEnd <= length ? float(fromEnd - 1) / length : 1.f;
        }
        out[0][i] *= g;
        out[1][i] *= g;
    }
}
