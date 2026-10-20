#include "Player.h"

#include "Loudness.h"
#include "sdengine.h"

#include <algorithm>
#include <cmath>
#include <limits>

namespace {

// A fixed ~10 ms fade: about one output buffer, but independent of the device.
constexpr double kFadeSeconds = 0.010;

} // namespace

Player::Player(int outputRate, int maxBlockFrames)
    : outputRate_(outputRate), maxBlockFrames_(maxBlockFrames),
      loudnessLufs_(-std::numeric_limits<double>::infinity()) {
    for (auto &gain : eqGainsDb_) gain.store(0.0);
    limiter_.prepare(outputRate);
}

bool Player::open(const std::string &path) {
    // Decode outside the lock: it can take a second, and playback of the old file can go on meanwhile.
    PcmBuffer decoded;
    std::string error;
    if (!decodeFile(path, decoded, error)) return false;
    if (decoded.channels == 1) {
        decoded.data.push_back(decoded.data[0]);
        decoded.channels = 2;
    }
    // Measured on what plays: a mono file counts on both speakers, like a stereo one. On the caller's
    // thread, after the decode: about 75 ms for 5 minutes (M1 Pro), so it is not worth storing.
    const double lufs = loudness::integratedLoudness(decoded);
    const double gainDb = loudness::normalizationGainDb(lufs);

    std::lock_guard<std::mutex> guard(lock_);
    wantsPlaying_.store(false);
    isRunning_ = false;
    source_ = std::move(decoded);
    path_ = path;
    sourceSampleRate_.store(source_.sampleRate);
    stretcher_.prepare(source_, outputRate_, maxBlockFrames_);
    equalizer_.reset();
    sourceFrames_.store(source_.frames);
    durationSeconds_.store(source_.seconds());
    positionFrames_.store(0.0);
    seekTo_.store(-1.0);
    setLoop(0.0, 1.0, false);
    loudnessLufs_.store(lufs);
    normalizationGainDb_.store(gainDb);
    normalizationGain_.store(float(std::pow(10.0, gainDb / 20.0)));
    isOpen_.store(true);
    return true;
}

void Player::close() {
    std::lock_guard<std::mutex> guard(lock_);
    wantsPlaying_.store(false);
    isRunning_ = false;
    isOpen_.store(false);
    source_ = PcmBuffer{};
    path_.clear();
    sourceFrames_.store(0);
    durationSeconds_.store(0.0);
    positionFrames_.store(0.0);
    loudnessLufs_.store(-std::numeric_limits<double>::infinity());
    normalizationGainDb_.store(0.0);
    normalizationGain_.store(1.f);
}

Player::Settings Player::settings() const {
    Settings s;
    s.speed = speed_.load();
    s.pitch = pitch_.load();
    s.mix = mix_.load();
    s.eqEnabled = eqEnabled_.load();
    for (int b = 0; b < Equalizer::kBands; ++b) s.eqGainsDb[size_t(b)] = eqGainsDb_[size_t(b)].load();
    s.preampDb = preampDb_.load();
    return s;
}

void Player::applySettings(const Settings &settings) {
    setSpeed(settings.speed);
    setPitch(settings.pitch);
    setMix(settings.mix);
    for (int b = 0; b < Equalizer::kBands; ++b) setEqBand(b, settings.eqGainsDb[size_t(b)]);
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

void Player::setEqEnabled(bool enabled) {
    eqEnabled_.store(enabled);
    eqVersion_.fetch_add(1);
}

void Player::setEqBand(int band, double gainDb) {
    if (band < 0 || band >= Equalizer::kBands) return;
    eqGainsDb_[size_t(band)].store(std::clamp(gainDb, -12.0, 12.0));
    eqVersion_.fetch_add(1);
}

void Player::setPreamp(double gainDb) {
    preampDb_.store(gainDb);
    eqVersion_.fetch_add(1);
}

double Player::applyControls() {
    const double frames = double(source_.frames);
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

    const unsigned version = eqVersion_.load();
    if (version != appliedEqVersion_) {
        appliedEqVersion_ = version;
        std::array<double, Equalizer::kBands> gains{};
        const bool enabled = eqEnabled_.load();
        for (int b = 0; b < Equalizer::kBands; ++b) gains[size_t(b)] = enabled ? eqGainsDb_[size_t(b)].load() : 0.0;
        equalizer_.configure(outputRate_, gains);
        preampGain_ = enabled ? float(std::pow(10.0, preampDb_.load() / 20.0)) : 1.f;
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
        stretcher_.seek(seek * double(source_.frames));
        equalizer_.reset();
        limiter_.reset();
        needsFadeIn_ = true;
    }
    if (wantsPlaying && !isRunning_) {
        // Start from where the user is, with a fresh grain history.
        if (seek < 0) stretcher_.seek(positionFrames_.load());
        equalizer_.reset();
        limiter_.reset();
        isRunning_ = true;
        needsFadeIn_ = true;
    }
    // The limiter delays the audio by 5 ms, so switching it in or out mid-sound would jump. It switches
    // where the output is silent: now if stopped or about to fade in, else after this block fades out,
    // and the next one fades in. No click, a dip of about 25 ms instead (fade-out, the limiter's empty
    // delay, fade-in), only when the setting changes.
    const bool wantsNormalizing = normalizationOn_.load();
    bool switchesNormalizing = false;
    if (wantsNormalizing != isNormalizing_) {
        if (!isRunning_ || needsFadeIn_) {
            isNormalizing_ = wantsNormalizing;
            limiter_.reset();
        } else {
            switchesNormalizing = true;
        }
    }
    if (!isRunning_) {
        std::fill(out[0], out[0] + frames, 0.f);
        std::fill(out[1], out[1] + frames, 0.f);
        return 0;
    }

    const int rendered = stretcher_.render(out, frames);
    equalizer_.process(out, frames);
    if (isNormalizing_) {
        // The gain changes only in open(), which stops playback: no ramp needed. The limiter then catches
        // what the boost, the EQ or the preamp push over the ceiling.
        scale(out, frames, preampGain_ * normalizationGain_.load());
        // Fade in before the limiter: after a reset its delay line is silence, and a fade applied to the
        // output would be spent on that silence, letting the audio in with a jump.
        if (needsFadeIn_) applyFade(out, frames, true);
        limiter_.process(out, frames);
        scale(out, frames, float(outputGain_.load()));
    } else {
        scale(out, frames, preampGain_ * float(outputGain_.load()));
        if (needsFadeIn_) applyFade(out, frames, true);
    }
    needsFadeIn_ = false;
    // While normalizing, this is about 5 ms (the limiter's delay) ahead of what is heard: too little to
    // matter, like the device latency, which the position does not count either.
    positionFrames_.store(stretcher_.position());

    if (rendered < frames) {
        // End of the file with the loop off: stop and tell the control side. While normalizing, the last
        // 5 ms can still sit in the limiter's delay and are cut, if the end falls near the end of the
        // block. Accepted: files end in silence or a fade almost always.
        isRunning_ = false;
        wantsPlaying_.store(false);
        positionFrames_.store(double(source_.frames));
        events_.fetch_or(SD_EVENT_REACHED_END);
    } else if (!wantsPlaying) {
        applyFade(out, frames, false);
        isRunning_ = false;
    } else if (switchesNormalizing) {
        applyFade(out, frames, false);
        isNormalizing_ = wantsNormalizing;
        limiter_.reset();
        needsFadeIn_ = true;
    }
    return rendered;
}

void Player::scale(float *const *out, int frames, float gain) {
    if (gain == 1.f) return;
    for (int c = 0; c < 2; ++c)
        for (int i = 0; i < frames; ++i) out[c][i] *= gain;
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
