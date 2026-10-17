/*
 * Engine tests through the C API in manual mode: no device, the test pulls the audio.
 * Plain asserts with a summary, so they run with `ctest` and need no framework.
 */
#include "Denormals.h"
#include "Equalizer.h"
#include "Pcm.h"
#include "Player.h"
#include "sdengine.h"
#include <lame.h>

#include <atomic>
#include <cmath>
#include <complex>
#include <cstdio>
#include <cstdlib>
#include <filesystem>
#include <functional>
#include <new>
#include <string>
#include <thread>
#include <vector>

// Allocation counter for the render path. operator new is replaced for the whole binary; with GNU
// ld malloc is wrapped too (see CMakeLists.txt), since Bungee's Eigen arrays use malloc.
namespace {
std::atomic<bool> countAllocations{false};
std::atomic<long> news{0}, mallocs{0}; // a new also counts as a malloc where malloc is wrapped
void noteAllocation(std::atomic<long> &counter) {
    if (countAllocations.load(std::memory_order_relaxed)) counter.fetch_add(1, std::memory_order_relaxed);
}
} // namespace

// GCC sees free() behind a delete and warns, not knowing this new is malloc too.
#pragma GCC diagnostic ignored "-Wpragmas"
#pragma GCC diagnostic ignored "-Wmismatched-new-delete"
void *operator new(std::size_t size) {
    noteAllocation(news);
    if (void *p = std::malloc(size ? size : 1)) return p;
    throw std::bad_alloc();
}
void *operator new[](std::size_t size) { return operator new(size); }
void operator delete(void *p) noexcept { std::free(p); }
void operator delete[](void *p) noexcept { std::free(p); }
void operator delete(void *p, std::size_t) noexcept { std::free(p); }
void operator delete[](void *p, std::size_t) noexcept { std::free(p); }

#ifdef SDENGINE_TESTS_WRAP_MALLOC
extern "C" {
void *__real_malloc(std::size_t size);
void *__real_calloc(std::size_t count, std::size_t size);
void *__real_realloc(void *p, std::size_t size);
void *__wrap_malloc(std::size_t size) {
    noteAllocation(mallocs);
    return __real_malloc(size);
}
void *__wrap_calloc(std::size_t count, std::size_t size) {
    noteAllocation(mallocs);
    return __real_calloc(count, size);
}
void *__wrap_realloc(void *p, std::size_t size) {
    noteAllocation(mallocs);
    return __real_realloc(p, size);
}
}
#endif

// Defined in CApi.cpp.
Player *sdEnginePlayer(SdEngine *engine);

namespace {

constexpr int kRate = 44100;
int failures = 0;

#define CHECK(condition, ...)                                                                                          \
    do {                                                                                                               \
        if (!(condition)) {                                                                                            \
            ++failures;                                                                                                \
            std::printf("  FAIL %s:%d: %s — ", __FILE__, __LINE__, #condition);                                        \
            std::printf(__VA_ARGS__);                                                                                  \
            std::printf("\n");                                                                                         \
        }                                                                                                              \
    } while (0)

/** 10 s stereo file: a 441 Hz tone on the left, silence on the right. */
std::string writeTestFile() {
    PcmBuffer pcm;
    pcm.sampleRate = kRate;
    pcm.channels = 2;
    pcm.frames = 10 * kRate;
    pcm.data.assign(2, std::vector<float>(size_t(pcm.frames), 0.f));
    for (int64_t i = 0; i < pcm.frames; ++i) pcm.data[0][size_t(i)] = float(0.5 * std::sin(2 * M_PI * 441 * double(i) / kRate));
    const auto path = (std::filesystem::temp_directory_path() / "sdengine-test.wav").string();
    std::string error;
    if (!writeWavFloat(path, pcm, error)) std::printf("cannot write test file: %s\n", error.c_str());
    return path;
}

struct Rendered {
    std::vector<float> left, right;
};

Rendered render(SdEngine *engine, double seconds) {
    const int frames = int(seconds * kRate);
    std::vector<float> interleaved(size_t(frames) * 2);
    sd_engine_render(engine, interleaved.data(), frames);
    Rendered r;
    for (int i = 0; i < frames; ++i) {
        r.left.push_back(interleaved[size_t(i) * 2]);
        r.right.push_back(interleaved[size_t(i) * 2 + 1]);
    }
    return r;
}

double rms(const std::vector<float> &x, size_t from = 0) {
    double sum = 0;
    for (size_t i = from; i < x.size(); ++i) sum += double(x[i]) * x[i];
    return x.size() > from ? std::sqrt(sum / double(x.size() - from)) : 0;
}

SdEngine *openEngine(const std::string &path) {
    SdEngine *engine = sd_engine_create(0, kRate);
    if (!sd_engine_open(engine, path.c_str())) std::printf("cannot open %s\n", path.c_str());
    return engine;
}

void playsAtHalfSpeed(const std::string &path) {
    SdEngine *e = openEngine(path);
    CHECK(std::fabs(sd_engine_duration_seconds(e) - 10.0) < 0.01, "duration %f", sd_engine_duration_seconds(e));
    sd_engine_set_speed(e, 0.5);
    sd_engine_set_playing(e, 1);
    render(e, 4.0);
    // 4 s of output at half speed = 2 s of the file = 0.2.
    CHECK(std::fabs(sd_engine_position(e) - 0.2) < 0.01, "position %f", sd_engine_position(e));
    sd_engine_destroy(e);
}

void stopsWithFadeAndKeepsPosition(const std::string &path) {
    SdEngine *e = openEngine(path);
    sd_engine_set_playing(e, 1);
    render(e, 1.0);
    sd_engine_set_playing(e, 0);
    CHECK(!sd_engine_is_playing(e), "still playing");
    const Rendered fadeBlock = render(e, 0.05);
    CHECK(std::fabs(fadeBlock.left.back()) < 1e-3, "no fade out, last sample %f", fadeBlock.left.back());
    const double stoppedAt = sd_engine_position(e);
    const Rendered silent = render(e, 0.5);
    CHECK(rms(silent.left) == 0.0, "sound after stop");
    CHECK(std::fabs(sd_engine_position(e) - stoppedAt) < 1e-9, "position moved while stopped");
    CHECK(std::fabs(stoppedAt - 0.105) < 0.01, "stopped at %f", stoppedAt);
    sd_engine_destroy(e);
}

void seekWhileStoppedIsReportedAtOnce(const std::string &path) {
    SdEngine *e = openEngine(path);
    sd_engine_seek(e, 0.6);
    CHECK(std::fabs(sd_engine_position(e) - 0.6) < 1e-9, "position %f", sd_engine_position(e));
    sd_engine_set_playing(e, 1);
    render(e, 1.0);
    CHECK(std::fabs(sd_engine_position(e) - 0.7) < 0.01, "position %f", sd_engine_position(e));
    sd_engine_destroy(e);
}

void positionNeverGoesBackAfterSeek(const std::string &path) {
    SdEngine *e = openEngine(path);
    sd_engine_set_playing(e, 1);
    render(e, 0.5);
    sd_engine_seek(e, 0.5);
    double previous = sd_engine_position(e);
    for (int block = 0; block < 40; ++block) { // 512-frame blocks, like a device callback
        render(e, 512.0 / kRate);
        const double now = sd_engine_position(e);
        CHECK(now >= previous - 1e-9, "went back from %f to %f", previous, now);
        previous = now;
    }
    CHECK(previous > 0.5, "did not move: %f", previous);
    sd_engine_destroy(e);
}

void loopAndSeekLandTogether(const std::string &path) {
    SdEngine *e = openEngine(path);
    sd_engine_set_playing(e, 1);
    render(e, 0.5);
    sd_engine_set_loop_and_seek(e, 0.6, 0.7, 1, 0.6);
    CHECK(std::fabs(sd_engine_position(e) - 0.6) < 1e-9, "position %f", sd_engine_position(e));
    render(e, 512.0 / kRate);
    CHECK(sd_engine_position(e) >= 0.6 && sd_engine_position(e) < 0.61, "position %f", sd_engine_position(e));
    sd_engine_destroy(e);
}

void exportsTheWholeFileAtTheCurrentSpeed(const std::string &path) {
    SdEngine *e = openEngine(path);
    sd_engine_set_speed(e, 0.5);
    sd_engine_set_loop(e, 0.2, 0.3, 1); // ignored by the export
    const auto output = (std::filesystem::temp_directory_path() / "sdengine-export.mp3").string();
    SdExport *exporter = sd_export_begin(e, output.c_str(), 192);
    CHECK(exporter != nullptr, "cannot start export");
    if (!exporter) return;
    double progress = 0, last = 0;
    while ((progress = sd_export_next(exporter)) >= 0) {
        CHECK(progress >= last - 1e-9, "progress went back");
        last = progress;
    }
    CHECK(sd_export_end(exporter) == 1, "export incomplete");
    PcmBuffer decoded;
    std::string error;
    CHECK(decodeFile(output, decoded, error), "cannot read the MP3: %s", error.c_str());
    CHECK(std::fabs(decoded.seconds() - 20.0) < 0.3, "MP3 lasts %f s", decoded.seconds());
    CHECK(rms(decoded.data[0], size_t(kRate)) > 0.2, "MP3 is silent");
    std::filesystem::remove(output);
    sd_engine_destroy(e);
}

void loopStaysInside(const std::string &path) {
    SdEngine *e = openEngine(path);
    sd_engine_set_loop(e, 0.2, 0.3, 1);
    sd_engine_seek(e, 0.2);
    sd_engine_set_playing(e, 1);
    double lowest = 1, highest = 0;
    for (int block = 0; block < 60; ++block) { // 6 s = 6 passes of the 1 s loop
        render(e, 0.1);
        lowest = std::min(lowest, sd_engine_position(e));
        highest = std::max(highest, sd_engine_position(e));
    }
    CHECK(lowest >= 0.199 && highest <= 0.301, "positions %f..%f", lowest, highest);
    CHECK(sd_engine_is_playing(e), "loop stopped");
    sd_engine_destroy(e);
}

void reachesEndWithLoopOff(const std::string &path) {
    SdEngine *e = openEngine(path);
    // Points kept but loop off: playback must not be bounded by them.
    sd_engine_set_loop(e, 0.2, 0.3, 0);
    sd_engine_seek(e, 0.9);
    sd_engine_set_playing(e, 1);
    render(e, 1.5);
    CHECK(sd_engine_take_events(e) & SD_EVENT_REACHED_END, "no end event");
    CHECK(!sd_engine_is_playing(e), "still playing at the end");
    CHECK(sd_engine_position(e) == 1.0, "position %f", sd_engine_position(e));
    CHECK(sd_engine_take_events(e) == 0, "events not cleared");
    sd_engine_destroy(e);
}

void mixFadesTheFarSide(const std::string &path) {
    // Left = tone, right = silence.
    const auto levels = [&](double mix) {
        SdEngine *e = openEngine(path);
        sd_engine_set_mix(e, mix);
        sd_engine_set_playing(e, 1);
        const Rendered r = render(e, 1.0);
        sd_engine_destroy(e);
        return std::make_pair(rms(r.left, kRate / 2), rms(r.right, kRate / 2));
    };
    const double tone = 0.5 / std::sqrt(2.0);
    const auto neutral = levels(0.5);
    CHECK(std::fabs(neutral.first - tone) < 0.03 && neutral.second < 1e-3, "0.5: %f %f", neutral.first, neutral.second);
    const auto leftOnly = levels(0.0);
    CHECK(std::fabs(leftOnly.first - tone) < 0.03 && std::fabs(leftOnly.second - tone) < 0.03, "0.0: %f %f",
          leftOnly.first, leftOnly.second);
    const auto rightOnly = levels(1.0);
    CHECK(rightOnly.first < 1e-3 && rightOnly.second < 1e-3, "1.0: %f %f", rightOnly.first, rightOnly.second);
}

/** Amplitude of the `frequency` component of x[from..to), by correlation: deaf to everything else. */
double toneAmplitude(const std::vector<float> &x, size_t from, size_t to, double frequency, int rate = kRate) {
    double s = 0, c = 0;
    for (size_t i = from; i < to; ++i) {
        const double w = 2 * M_PI * frequency * double(i) / rate;
        s += x[i] * std::sin(w);
        c += x[i] * std::cos(w);
    }
    return 2 * std::hypot(s, c) / double(to - from);
}

std::vector<float> sine(double frequency, double amplitude, int frames, int rate = kRate) {
    std::vector<float> x(static_cast<size_t>(frames));
    for (int i = 0; i < frames; ++i) x[size_t(i)] = float(amplitude * std::sin(2 * M_PI * frequency * i / rate));
    return x;
}

/**
 * The cookbook's peaking EQ (R. Bristow-Johnson), written out again here on purpose, evaluated as
 * |H(e^jw)|: what the engine's bells must measure.
 */
double cookbookPeakingDb(double centre, double gainDb, double q, double rate, double at) {
    const double a = std::pow(10.0, gainDb / 40), w0 = 2 * M_PI * centre / rate, alpha = std::sin(w0) / (2 * q);
    const std::complex<double> z = std::polar(1.0, -2 * M_PI * at / rate);
    const std::complex<double> num = (1 + alpha * a) - 2 * std::cos(w0) * z + (1 - alpha * a) * z * z;
    const std::complex<double> den = (1 + alpha / a) - 2 * std::cos(w0) * z + (1 - alpha / a) * z * z;
    return 20 * std::log10(std::abs(num / den));
}

EqParams oneBand(double frequency, double gainDb, double q) {
    EqParams p;
    p.enabled = true;
    p.bandCount = 1;
    p.bands[0] = {frequency, gainDb, q};
    return p;
}

/** Steady-state gain of an EQ at `frequency`, measured on a sine through it. */
double measuredGainDb(const EqParams &params, double frequency) {
    Equalizer eq;
    eq.setSampleRate(kRate);
    eq.setTarget(params, true);
    std::vector<float> l = sine(frequency, 0.25, kRate), r = l;
    float *channels[2] = {l.data(), r.data()};
    eq.process(channels, kRate);
    return 20 * std::log10(toneAmplitude(l, kRate / 2, size_t(kRate), frequency) / 0.25);
}

void peakingBandsMatchTheCookbook() {
    struct Case {
        double centre, gainDb, q;
    };
    for (const Case band : {Case{1000, 6, 1}, Case{100, -9, 0.7}, Case{4000, 12, 4}, Case{250, -12, 0.3},
                            Case{12000, 3, 10}}) {
        for (const double ratio : {1.0, 0.5, 2.0, 1.1, 0.25, 3.0}) {
            const double at = band.centre * ratio;
            if (at < 20 || at > kRate * 0.45) continue;
            const double measured = measuredGainDb(oneBand(band.centre, band.gainDb, band.q), at);
            const double expected = cookbookPeakingDb(band.centre, band.gainDb, band.q, kRate, at);
            CHECK(std::fabs(measured - expected) < 0.05, "%g Hz %+g dB Q %g at %g Hz: %f dB, the formula %f dB",
                  band.centre, band.gainDb, band.q, at, measured, expected);
        }
        const double centre = measuredGainDb(oneBand(band.centre, band.gainDb, band.q), band.centre);
        CHECK(std::fabs(centre - band.gainDb) < 0.05, "%g Hz centre gave %f dB for %+g", band.centre, centre,
              band.gainDb);
    }
    // A wider Q reaches further: an octave away, Q 0.5 keeps more of the boost than Q 4.
    const double wide = measuredGainDb(oneBand(1000, 12, 0.5), 2000), narrow = measuredGainDb(oneBand(1000, 12, 4), 2000);
    CHECK(wide > narrow + 3, "Q 0.5 gave %f dB an octave away, Q 4 %f dB", wide, narrow);
    // Bands add up in dB.
    EqParams two = oneBand(200, 6, 1);
    two.bandCount = 2;
    two.bands[1] = {5000, -6, 2};
    const double both = measuredGainDb(two, 1000);
    const double sum = cookbookPeakingDb(200, 6, 1, kRate, 1000) + cookbookPeakingDb(5000, -6, 2, kRate, 1000);
    CHECK(std::fabs(both - sum) < 0.05, "two bands gave %f dB, their sum %f dB", both, sum);
    // Off, the bands are kept but not heard.
    EqParams off = oneBand(1000, 12, 1);
    off.enabled = false;
    CHECK(std::fabs(measuredGainDb(off, 1000)) < 1e-6, "a disabled EQ still shapes the sound");
}

void flatEqIsBitTransparent() {
    const std::vector<float> input = sine(997, 0.5, kRate / 2);
    const auto through = [&](Equalizer &eq) {
        std::vector<float> l = input, r = input;
        float *channels[2] = {l.data(), r.data()};
        eq.process(channels, int(l.size()));
        return l;
    };
    Equalizer eq;
    eq.setSampleRate(kRate);
    EqParams flat;
    flat.enabled = true;
    flat.bandCount = Equalizer::kMaxBands;
    for (int b = 0; b < Equalizer::kMaxBands; ++b) flat.bands[size_t(b)] = {100.0 * (b + 1), 0, 1};
    eq.setTarget(flat, true);
    CHECK(eq.isFlat() && through(eq) == input, "eight bands at 0 dB changed the signal");

    // Back to flat after a boost, once the glide has ended: exactly the input again.
    EqParams boost = flat;
    boost.bands[3].gainDb = 9;
    boost.preampDb = -3;
    eq.setTarget(boost, false);
    through(eq);
    eq.setTarget(flat, false);
    through(eq); // half a second: long past the glide
    CHECK(eq.isFlat() && through(eq) == input, "the EQ did not settle back to an exact flat");

    // The whole engine: a flat EQ that is on sounds bit for bit like no EQ.
    const auto rendered = [](bool eqOn) {
        Player player(kRate, 512);
        PcmBuffer pcm;
        pcm.sampleRate = kRate;
        pcm.channels = 1;
        pcm.frames = kRate;
        pcm.data.assign(1, sine(441, 0.5, kRate));
        player.open(std::make_shared<PcmBuffer>(std::move(pcm)), "tone");
        player.setEqBandCount(3);
        for (int b = 0; b < 3; ++b) player.setEqBandParams(b, 300.0 * (b + 1), 0, 2);
        player.setEqEnabled(eqOn);
        player.setPlaying(true);
        std::vector<float> l(4096), r(4096);
        float *out[2] = {l.data(), r.data()};
        player.render(out, 4096);
        return l;
    };
    CHECK(rendered(true) == rendered(false), "a flat EQ that is on changed the sound");
}

void eqChangesGlideWithoutSteps() {
    // A steady 1 kHz tone while its band jumps from -12 to +12 dB, sub-block by sub-block.
    constexpr double kTone = 1000, kAmplitude = 0.2;
    Equalizer eq;
    eq.setSampleRate(kRate);
    eq.setTarget(oneBand(kTone, -12, 1), true);
    const int frames = kRate / 32 * 32; // whole sub-blocks
    std::vector<float> l = sine(kTone, kAmplitude, frames), r = l;
    float *channels[2] = {l.data(), r.data()};
    constexpr int kSub = 32;
    const int changeAt = kRate / 4 / kSub * kSub;
    double previousGain = eq.currentGainDb(0), largestGainStep = 0;
    int settledAt = -1;
    for (int offset = 0; offset < frames; offset += kSub) {
        if (offset == changeAt) eq.setTarget(oneBand(kTone, 12, 1), false);
        float *block[2] = {channels[0] + offset, channels[1] + offset};
        eq.process(block, kSub);
        largestGainStep = std::max(largestGainStep, std::fabs(eq.currentGainDb(0) - previousGain));
        previousGain = eq.currentGainDb(0);
        if (settledAt < 0 && offset >= changeAt && eq.currentGainDb(0) == 12) settledAt = offset + kSub - changeAt;
    }
    CHECK(largestGainStep < 1.0, "the gain moved %f dB in one 32-frame step", largestGainStep);
    CHECK(settledAt > 0 && settledAt <= kRate / 10, "reached +12 dB %d frames after the change", settledAt);
    // No sample jumps further than the loudest steady tone ever does between two samples.
    const double loudest = kAmplitude * std::pow(10.0, 12 / 20.0) * 2 * std::sin(M_PI * kTone / kRate);
    double largestJump = 0;
    for (int i = 1; i < frames; ++i) largestJump = std::max(largestJump, double(std::fabs(l[size_t(i)] - l[size_t(i - 1)])));
    CHECK(largestJump <= loudest * 1.02, "a %f jump between samples, the +12 dB tone moves at most %f", largestJump,
          loudest);
    // Nor any click: the third difference (a steep high-pass) of a tone is tiny, a step is not.
    const double tick = std::pow(2 * std::sin(M_PI * kTone / kRate), 3) * kAmplitude * std::pow(10.0, 12 / 20.0);
    double largestTick = 0;
    for (int i = 3; i < frames; ++i) {
        const size_t n = size_t(i);
        largestTick = std::max(largestTick, double(std::fabs(l[n] - 3 * l[n - 1] + 3 * l[n - 2] - l[n - 3])));
    }
    // An instant switch measures about ten times the tone's own.
    CHECK(largestTick <= tick * 1.5, "a %f click, the +12 dB tone alone %f", largestTick, tick);
    const double after = 20 * std::log10(toneAmplitude(l, size_t(changeAt + kRate / 10), size_t(frames), kTone) / kAmplitude);
    CHECK(std::fabs(after - 12) < 0.05, "100 ms after the change the tone is at %f dB", after);

    // The preamp ramps sample by sample: a 12 dB jump on a constant signal never steps.
    Equalizer pre;
    pre.setSampleRate(kRate);
    EqParams quiet;
    quiet.enabled = true;
    quiet.preampDb = -12;
    pre.setTarget(quiet, true);
    std::vector<float> dc(size_t(kRate / 4), 0.5f), dcRight = dc;
    float *dcChannels[2] = {dc.data(), dcRight.data()};
    pre.process(dcChannels, 1024);
    quiet.preampDb = 0;
    pre.setTarget(quiet, false);
    float *rest[2] = {dc.data() + 1024, dcRight.data() + 1024};
    pre.process(rest, int(dc.size()) - 1024);
    double largestStep = 0;
    for (size_t i = 1; i < dc.size(); ++i) largestStep = std::max(largestStep, double(std::fabs(dc[i] - dc[i - 1])));
    CHECK(largestStep < 0.002, "the preamp stepped %f at once", largestStep);
    CHECK(pre.currentPreampDb() == 0 && dc.back() == 0.5f, "the preamp ended at %f dB", pre.currentPreampDb());
}

void eqFrequencyAndWidthGlide() {
    // A boosted band thrown from 100 Hz to 6.4 kHz (six octaves) and from Q 0.5 to Q 8 at once: both
    // travel there in small steps, settle on the target exactly, and the sound stays finite.
    Equalizer eq;
    eq.setSampleRate(kRate);
    eq.setTarget(oneBand(100, 9, 0.5), true);
    constexpr int kSub = 32;
    const int frames = kRate / 2 / kSub * kSub;
    std::vector<float> l = sine(1000, 0.2, frames), r = l;
    eq.setTarget(oneBand(6400, 9, 8), false);
    double previousOctave = std::log2(eq.currentFrequencyHz(0)), previousLogQ = std::log(eq.currentQ(0));
    double largestOctaveStep = 0, largestLogQStep = 0;
    int settledAt = -1;
    for (int offset = 0; offset < frames; offset += kSub) {
        float *block[2] = {l.data() + offset, r.data() + offset};
        eq.process(block, kSub);
        const double octave = std::log2(eq.currentFrequencyHz(0)), logQ = std::log(eq.currentQ(0));
        largestOctaveStep = std::max(largestOctaveStep, std::fabs(octave - previousOctave));
        largestLogQStep = std::max(largestLogQStep, std::fabs(logQ - previousLogQ));
        previousOctave = octave;
        previousLogQ = logQ;
        if (settledAt < 0 && std::fabs(eq.currentFrequencyHz(0) - 6400) < 1e-9 && std::fabs(eq.currentQ(0) - 8) < 1e-9)
            settledAt = offset + kSub;
    }
    // Six octaves in one jump would be a click; a glide moves a small fraction per 32-frame step.
    CHECK(largestOctaveStep < 0.25, "the frequency moved %f octaves in one step", largestOctaveStep);
    CHECK(largestLogQStep < 0.15, "Q moved by a factor %f in one step", std::exp(largestLogQStep));
    CHECK(settledAt > 0 && settledAt <= kRate * 3 / 20, "the band settled %d frames after the change", settledAt);
    bool finite = true;
    for (float x : l) finite = finite && std::isfinite(x);
    CHECK(finite, "the glide produced a non-finite sample");
    const double centre = measuredGainDb(oneBand(6400, 9, 8), 6400);
    CHECK(std::fabs(centre - 9) < 0.05, "after the glide the centre measures %f dB", centre);
}

void isolateCutsOutsideTheSelection() {
    // The test file's tone is 441 Hz: inside the mid range.
    const auto level = [](int mask, bool eqOn, bool throughSettings = false) {
        PcmBuffer pcm;
        pcm.sampleRate = kRate;
        pcm.channels = 1;
        pcm.frames = kRate;
        pcm.data.assign(1, sine(441, 0.25, kRate));
        auto shared = std::make_shared<const PcmBuffer>(std::move(pcm));
        Player player(kRate, 4096);
        player.open(shared, "tone");
        player.setEqEnabled(eqOn);
        player.setIsolation(mask);
        Player copy(kRate, 4096); // the export copies a player's sound through its settings
        copy.open(shared, "tone");
        copy.applySettings(player.settings());
        Player &heard = throughSettings ? copy : player;
        heard.setPlaying(true);
        std::vector<float> l(kRate / 2), r(kRate / 2);
        float *out[2] = {l.data(), r.data()};
        for (int offset = 0; offset < kRate / 2; offset += 4096) {
            float *block[2] = {out[0] + offset, out[1] + offset};
            heard.render(block, std::min(4096, kRate / 2 - offset));
        }
        return 20 * std::log10(toneAmplitude(l, kRate / 8, kRate / 2, 441) / 0.25);
    };
    const double full = level(0, false);
    const auto relative = [&](int mask, bool eqOn) { return level(mask, eqOn) - full; };
    const double low = relative(1, false), mid = relative(2, true), high = relative(4, false);
    const double lowAndHigh = relative(1 | 4, true), everything = relative(7, false);
    CHECK(std::fabs(full) < 0.5, "the full mix is at %f dB", full);
    CHECK(low < -9, "isolating low left the mid tone at %f dB", low);
    CHECK(high < -9, "isolating high left the mid tone at %f dB", high);
    CHECK(lowAndHigh < -9, "isolating low and high left the mid tone at %f dB", lowAndHigh);
    CHECK(std::fabs(mid) < 2.5, "isolating mid moved the mid tone by %f dB", mid); // the neighbours' skirts
    CHECK(std::fabs(everything) < 0.01, "isolating every range moved the tone by %f dB", everything);
    const double exported = level(1, false, true) - full;
    CHECK(std::fabs(exported) < 0.01, "the settings carried Isolate: %f dB", exported);
}

void firstSettingsApplyAtOnce() {
    // A new player (as the export's) plays its EQ from the first block, without a glide from flat.
    const auto firstBlock = [](bool eqOn) {
        PcmBuffer pcm;
        pcm.sampleRate = kRate;
        pcm.channels = 1;
        pcm.frames = kRate;
        pcm.data.assign(1, sine(1000, 0.25, kRate));
        Player player(kRate, 4096);
        player.open(std::make_shared<PcmBuffer>(std::move(pcm)), "tone");
        player.setEqBandParams(0, 1000, 6, 1);
        player.setEqBandCount(1);
        player.setEqEnabled(eqOn);
        player.setPlaying(true);
        std::vector<float> l(4096), r(4096);
        float *out[2] = {l.data(), r.data()};
        player.render(out, 4096);
        return toneAmplitude(l, 882, 4096, 1000); // past the 10 ms fade-in
    };
    const double early = 20 * std::log10(firstBlock(true) / firstBlock(false));
    CHECK(std::fabs(early - 6) < 0.1, "the first block played %f dB", early);
}

void preampOnlyWithEqOn(const std::string &path) {
    const auto level = [&](bool eqOn) {
        SdEngine *e = openEngine(path);
        sd_engine_set_preamp(e, -6);
        sd_engine_set_eq_enabled(e, eqOn ? 1 : 0);
        sd_engine_set_playing(e, 1);
        const Rendered r = render(e, 1.0);
        sd_engine_destroy(e);
        return rms(r.left, kRate / 2);
    };
    const double off = level(false), on = level(true);
    CHECK(std::fabs(20 * std::log10(on / off) + 6) < 0.3, "preamp gave %f dB", 20 * std::log10(on / off));
}

std::string tempPath(const char *name) { return (std::filesystem::temp_directory_path() / name).string(); }

/** `seconds` of a distinct ramp per channel: any lost, doubled or swapped block shows. */
PcmBuffer rampBuffer(int channels, double seconds) {
    PcmBuffer pcm;
    pcm.sampleRate = kRate;
    pcm.channels = channels;
    pcm.frames = int64_t(seconds * kRate);
    pcm.data.assign(size_t(channels), std::vector<float>(size_t(pcm.frames)));
    for (int c = 0; c < channels; ++c)
        for (int64_t i = 0; i < pcm.frames; ++i)
            pcm.data[size_t(c)][size_t(i)] = float((c ? -1 : 1) * double(i % 50000) / 50000.0);
    return pcm;
}

void decoderKeepsEveryFrame() {
    // Odd lengths, so the last block is partial; stereo and mono.
    for (const auto &[channels, seconds] : {std::pair{2, 73.3}, std::pair{1, 7.77}}) {
        const PcmBuffer written = rampBuffer(channels, seconds);
        const auto path = tempPath("sdengine-ramp.wav");
        std::string error;
        CHECK(writeWavFloat(path, written, error), "cannot write: %s", error.c_str());
        PcmBuffer decoded;
        CHECK(decodeFile(path, decoded, error), "cannot decode: %s", error.c_str());
        std::filesystem::remove(path);
        CHECK(decoded.channels == channels, "%d channels", decoded.channels);
        CHECK(decoded.frames == written.frames, "%lld frames, wrote %lld", (long long)decoded.frames,
              (long long)written.frames);
        if (decoded.channels != channels || decoded.frames != written.frames) continue;
        for (int c = 0; c < channels; ++c) {
            CHECK(int64_t(decoded.data[size_t(c)].size()) == decoded.frames, "channel %d size", c);
            CHECK(decoded.data[size_t(c)] == written.data[size_t(c)], "channel %d differs", c);
        }
    }
}

void unreadableFileFailsCleanly() {
    // Not audio at all (on Linux an M4A ends up here too: no AAC decoder).
    const auto path = tempPath("sdengine-not-audio.m4a");
    if (FILE *f = openFileUtf8(path, "wb")) {
        std::fputs("this is not an audio file, just some text that no decoder should accept", f);
        std::fclose(f);
    }
    PcmBuffer decoded;
    std::string error;
    CHECK(!decodeFile(path, decoded, error), "decoded a text file");
    CHECK(!error.empty(), "no error message");
    SdEngine *e = sd_engine_create(0, kRate);
    CHECK(sd_engine_open(e, path.c_str()) == 0 && !sd_engine_is_open(e), "opened a text file");
    sd_engine_destroy(e);
    std::filesystem::remove(path);
}

void monoIsKeptAsOneChannelAndPlaysOnBothSides() {
    PcmBuffer pcm;
    pcm.sampleRate = kRate;
    pcm.channels = 1;
    pcm.frames = 5 * kRate;
    pcm.data.assign(1, std::vector<float>(size_t(pcm.frames)));
    for (int64_t i = 0; i < pcm.frames; ++i) pcm.data[0][size_t(i)] = float(0.5 * std::sin(2 * M_PI * 441 * double(i) / kRate));
    const auto path = tempPath("sdengine-mono.wav");
    std::string error;
    CHECK(writeWavFloat(path, pcm, error), "cannot write: %s", error.c_str());
    const double tone = 0.5 / std::sqrt(2.0);
    for (const double mix : {0.0, 0.5, 1.0}) {
        SdEngine *e = openEngine(path);
        CHECK(sdEnginePlayer(e)->source()->channels == 1, "mono copied into %d channels",
              sdEnginePlayer(e)->source()->channels);
        sd_engine_set_mix(e, mix);
        sd_engine_set_pitch(e, 2); // through the resampler too
        sd_engine_set_playing(e, 1);
        const Rendered r = render(e, 1.0);
        sd_engine_destroy(e);
        CHECK(r.left == r.right, "mix %.1f: the sides differ", mix);
        CHECK(std::fabs(rms(r.left, kRate / 2) - tone) < 0.03, "mix %.1f: level %f", mix, rms(r.left, kRate / 2));
    }
    std::filesystem::remove(path);
}

void exportSharesTheDecodedAudio(const std::string &path) {
    // Open a copy, then delete it: the export can only succeed from the audio already in memory.
    const auto copy = tempPath("sdengine-export-source.wav");
    std::filesystem::copy_file(path, copy, std::filesystem::copy_options::overwrite_existing);
    SdEngine *e = openEngine(copy);
    std::filesystem::remove(copy);
    const auto shared = sdEnginePlayer(e)->source();
    const long ownersBefore = shared.use_count();

    const auto output = tempPath("sdengine-export-shared.mp3");
    SdExport *exporter = sd_export_begin(e, output.c_str(), 128);
    CHECK(exporter != nullptr, "cannot start export from memory");
    if (!exporter) {
        sd_engine_destroy(e);
        return;
    }
    CHECK(shared.use_count() == ownersBefore + 1, "export did not share the buffer (%ld owners)", shared.use_count());
    sd_engine_close(e); // the export keeps the audio alive
    while (sd_export_next(exporter) >= 0) {
    }
    CHECK(sd_export_end(exporter) == 1, "export incomplete");
    PcmBuffer decoded;
    std::string error;
    CHECK(decodeFile(output, decoded, error), "cannot read the MP3: %s", error.c_str());
    CHECK(std::fabs(decoded.seconds() - 10.0) < 0.3, "MP3 lasts %f s", decoded.seconds());
    CHECK(rms(decoded.data[0], size_t(kRate)) > 0.2, "MP3 is silent");
    std::filesystem::remove(output);
    sd_engine_destroy(e);

    // Same sound from a shared buffer as from a fresh decode, sample for sample.
    const auto renderAll = [](Player &player) {
        std::vector<float> l(4096), r(4096), all;
        float *planar[2] = {l.data(), r.data()};
        player.setSpeed(0.7);
        player.setPitch(-3);
        player.setPlaying(true);
        for (int block = 0; block < 100; ++block) {
            player.render(planar, 4096);
            all.insert(all.end(), l.begin(), l.end());
            all.insert(all.end(), r.begin(), r.end());
        }
        return all;
    };
    Player fresh(kRate, 4096), reused(kRate, 4096);
    CHECK(fresh.open(path), "cannot open");
    CHECK(reused.open(fresh.source(), path), "cannot open the shared buffer");
    CHECK(renderAll(fresh) == renderAll(reused), "shared buffer sounds different");
}

void flushesDenormalsInScope() {
#if defined(__x86_64__) || defined(__SSE__) || defined(__aarch64__)
    volatile float tiny = 1e-38f, scale = 1e-3f; // the product is a denormal
    {
        const ScopedFlushDenormals flush;
        CHECK(tiny * scale == 0.f, "denormal kept inside the scope");
    }
    CHECK(tiny * scale != 0.f, "flush mode leaked out of the scope");
#endif
}

void renderingDoesNotAllocate(const std::string &path) {
    // The hooks must see a new and, where wrapped, Bungee's own mallocs (opening a file builds its
    // Eigen arrays), or the zero below would prove nothing. Through a volatile pointer: the
    // compiler may otherwise drop an unused new/delete pair.
    countAllocations.store(true);
    int *volatile number = new int(1);
    delete number;
    {
        Player probe(kRate, 512);
        auto pcm = std::make_shared<PcmBuffer>(rampBuffer(2, 1.0));
        probe.open(std::move(pcm), "probe");
    }
    countAllocations.store(false);
    const long probeNews = news.exchange(0), probeMallocs = mallocs.exchange(0);
    CHECK(probeNews >= 1, "operator new hook saw nothing");
#ifdef SDENGINE_TESTS_WRAP_MALLOC
    // Every new is a malloc too: more mallocs means direct ones (Eigen's) were seen.
    CHECK(probeMallocs > probeNews, "malloc hook saw %ld for %ld new", probeMallocs, probeNews);
#else
    (void)probeMallocs;
#endif

    // Device rates equal to and different from the file's: the second always resamples.
    for (const int rate : {kRate, 48000}) {
        SdEngine *e = sd_engine_create(0, rate);
        sd_engine_open(e, path.c_str());
        sd_engine_set_eq_enabled(e, 1);
        sd_engine_set_metronome(e, 1, 0.8);
        sd_engine_set_count_in(e, 2, 1);
        sd_engine_set_playing(e, 1);
        std::vector<float> block(512 * 2);
        for (int i = 0; i < 40; ++i) sd_engine_render(e, block.data(), 512);
        // Like slider drags: new values every 512-frame block, plus a seek and a loop. The metronome
        // clicks throughout, a stop and start counts in, and the loop restarts count in too.
        countAllocations.store(true);
        for (int i = 0; i < 400; ++i) {
            const double t = i / 400.0;
            sd_engine_set_speed(e, 0.05 + 3.95 * t); // the stretcher's whole range
            sd_engine_set_pitch(e, -12 + 24 * t); // not snapped: the grain sizes move as much as they can
            // Bands dragged around while they glide: frequency, gain and Q, their number, the
            // preamp, the switch and Isolate.
            sd_engine_set_eq_band_params(e, i % SD_EQ_MAX_BANDS, 20 * std::pow(1000.0, t), (i % 25) - 12,
                                         0.3 + 9.7 * t);
            sd_engine_set_eq_band_count(e, i % (SD_EQ_MAX_BANDS + 1));
            sd_engine_set_preamp(e, (i % 13) - 6);
            if (i % 50 == 0) sd_engine_set_eq_enabled(e, (i / 50) % 2 == 0);
            sd_engine_set_isolation(e, (i / 10) % 8);
            sd_engine_set_tempo(e, 200 + 100 * t, 0.01 * (i % 7), 3 + i % 2);
            if (i == 100) sd_engine_set_playing(e, 0);
            if (i == 102) sd_engine_set_playing(e, 1);
            if (i == 200) sd_engine_set_loop_and_seek(e, 0.3, 0.32, 1, 0.3);
            if (i == 300) sd_engine_set_metronome(e, 1, 0.5);
            sd_engine_render(e, block.data(), 512);
            (void)sd_engine_count_in_remaining(e);
        }
        countAllocations.store(false);
        const long newCount = news.exchange(0), mallocCount = mallocs.exchange(0);
        CHECK(newCount == 0 && mallocCount == 0, "%d Hz: %ld new, %ld malloc while rendering with moving controls",
              rate, newCount, mallocCount);
        sd_engine_destroy(e);
    }
}

/** Loud first half, silent second half: the outline follows it at any zoom, and goes with the file. */
void peaksOutlineTheOpenFile() {
    PcmBuffer pcm;
    pcm.sampleRate = kRate;
    pcm.channels = 1;
    pcm.frames = 4 * kRate;
    pcm.data.assign(1, std::vector<float>(size_t(pcm.frames), 0.f));
    for (int64_t i = 0; i < pcm.frames / 2; ++i) pcm.data[0][size_t(i)] = float(0.8 * std::sin(2 * M_PI * 441 * double(i) / kRate));
    const auto path = (std::filesystem::temp_directory_path() / "sdengine-peaks.wav").string();
    std::string error;
    CHECK(writeWavFloat(path, pcm, error), "cannot write: %s", error.c_str());

    SdEngine *e = sd_engine_create(0, kRate);
    std::vector<float> out(64, -1.f);
    CHECK(sd_engine_peaks(e, 0, 1, out.data(), 64) == 0, "peaks with nothing open");
    sd_engine_open(e, path.c_str());
    CHECK(sd_engine_peaks(e, 0, 1, out.data(), 64) == 64, "whole file not read");
    CHECK(std::fabs(out[0] - 0.8f) < 0.01f && std::fabs(out[31] - 0.8f) < 0.01f, "loud half %f %f", out[0], out[31]);
    CHECK(out[33] == 0.f && out[63] == 0.f, "silent half %f %f", out[33], out[63]);
    // Zoomed into 10 ms around the middle: much narrower than one bucket per summary block.
    CHECK(sd_engine_peaks(e, 0.4975, 0.5025, out.data(), 64) == 64, "zoomed read");
    CHECK(out[0] > 0.5f && out[63] == 0.f, "zoomed edges %f %f", out[0], out[63]);
    sd_engine_close(e);
    CHECK(sd_engine_peaks(e, 0, 1, out.data(), 64) == 0, "peaks after close");
    sd_engine_destroy(e);
    std::filesystem::remove(path);
}

/** A container's length is a hint: a corrupt one must not reserve gigabytes, nor throw out of the decode. */
void aCorruptLengthReservesOnlyWhatTheFileCanHold() {
    const auto buffer = [](int sampleRate) {
        PcmBuffer pcm;
        pcm.sampleRate = sampleRate;
        pcm.channels = 2;
        pcm.data.assign(2, {});
        return pcm;
    };
    // 4 billion frames announced by a 1000-byte file: 128 frames per byte at most.
    PcmBuffer small = buffer(48000);
    reservePcm(small, 4'000'000'000LL, 1000);
    CHECK(small.data[0].capacity() == 128'000 && small.data[1].capacity() == 128'000, "reserved %ld frames",
          long(small.data[0].capacity()));
    // Size unknown: three hours at most.
    PcmBuffer unknown = buffer(10);
    reservePcm(unknown, 1'000'000'000'000LL, 0);
    CHECK(unknown.data[0].capacity() == 10 * 3 * 60 * 60, "reserved %ld frames", long(unknown.data[0].capacity()));
    // More than any machine has: the reserve fails quietly and the decode grows the vectors itself.
    PcmBuffer huge = buffer(2'000'000'000);
    reservePcm(huge, 4'000'000'000'000'000LL, 0);
    CHECK(huge.data[0].capacity() == 0 && huge.data[1].capacity() == 0, "kept %ld frames", long(huge.data[0].capacity()));
}

/** The app opens files on one thread and exports on another: the export must read one file whole. */
void exportBeginsWhileAnotherFileOpens(const std::string &path) {
    SdEngine *e = openEngine(path);
    const auto output = tempPath("sdengine-export-race.mp3");
    std::atomic<bool> done{false};
    std::thread opener([&] {
        for (int i = 0; i < 20; ++i) {
            sd_engine_close(e);
            sd_engine_open(e, path.c_str());
        }
        done.store(true);
    });
    int started = 0;
    while (!done.load()) {
        if (SdExport *exporter = sd_export_begin(e, output.c_str(), 128)) {
            ++started;
            sd_export_next(exporter);
            sd_export_end(exporter);
        }
    }
    opener.join();
    CHECK(sd_engine_is_open(e) == 1, "the last open did not land");
    std::printf("     %d exports started during the opens\n", started);
    std::filesystem::remove(output);
    sd_engine_destroy(e);
}

/** Runs a range export to the end: the decoded MP3 (empty if it failed) and every progress value. */
struct SliceExport {
    PcmBuffer decoded;
    std::vector<double> progress;
};

SliceExport exportSlice(SdEngine *e, const std::string &output, double start, double end) {
    SliceExport result;
    SdExport *exporter = sd_export_begin_range(e, output.c_str(), 192, start, end);
    CHECK(exporter != nullptr, "cannot start the slice %.2f..%.2f", start, end);
    if (!exporter) return result;
    double progress = 0;
    while ((progress = sd_export_next(exporter)) >= 0) result.progress.push_back(progress);
    CHECK(sd_export_end(exporter) == 1, "slice export incomplete");
    std::string error;
    CHECK(decodeFile(output, result.decoded, error), "cannot read the MP3: %s", error.c_str());
    std::filesystem::remove(output);
    return result;
}

void exportsALoopSliceAtTheCurrentSpeed(const std::string &path) {
    SdEngine *e = openEngine(path);
    const auto output = tempPath("sdengine-slice.mp3");
    // 0.2..0.5 of 10 s = 3 s of the file. LAME's delay and padding add about 50 ms.
    for (const double speed : {1.0, 0.5, 1.5}) {
        sd_engine_set_speed(e, speed);
        const SliceExport slice = exportSlice(e, output, 0.2, 0.5);
        const double expected = 3.0 / speed;
        CHECK(std::fabs(slice.decoded.seconds() - expected) < 0.1, "speed %.1f: slice lasts %f s, not %f",
              speed, slice.decoded.seconds(), expected);
        if (slice.decoded.frames > kRate)
            CHECK(rms(slice.decoded.data[0], size_t(kRate / 2)) > 0.2, "speed %.1f: slice is silent", speed);
    }
    sd_engine_destroy(e);
}

void sliceProgressRisesWithinTheRange(const std::string &path) {
    SdEngine *e = openEngine(path);
    sd_engine_set_speed(e, 0.5);
    const SliceExport slice = exportSlice(e, tempPath("sdengine-slice-progress.mp3"), 0.6, 0.9);
    // 3 s at half speed: 6 s of output, about 16 chunks.
    CHECK(slice.progress.size() > 10, "%zu progress steps", slice.progress.size());
    if (!slice.progress.empty()) {
        CHECK(slice.progress.front() > 0 && slice.progress.front() < 0.1, "first step %f", slice.progress.front());
        CHECK(slice.progress.back() > 0.9 && slice.progress.back() <= 1.0, "last step %f", slice.progress.back());
    }
    for (size_t i = 1; i < slice.progress.size(); ++i)
        CHECK(slice.progress[i] >= slice.progress[i - 1], "progress went back at step %zu", i);
    sd_engine_destroy(e);
}

/** 10 s of silence with a 441 Hz tone on the left from `toneFrom` seconds to the end. */
PcmBuffer toneFrom(double toneFrom) {
    PcmBuffer pcm;
    pcm.sampleRate = kRate;
    pcm.channels = 2;
    pcm.frames = 10 * kRate;
    pcm.data.assign(2, std::vector<float>(size_t(pcm.frames), 0.f));
    for (int64_t i = int64_t(toneFrom * kRate); i < pcm.frames; ++i)
        pcm.data[0][size_t(i)] = float(0.5 * std::sin(2 * M_PI * 441 * double(i) / kRate));
    return pcm;
}

/** The first frame of `x` louder than `threshold`, or -1. */
long firstAbove(const std::vector<float> &x, float threshold) {
    for (size_t i = 0; i < x.size(); ++i)
        if (std::fabs(x[i]) > threshold) return long(i);
    return -1;
}

/**
 * The MP3 round trip's own delay in frames (encoder and decoder): `pcm` encoded straight with LAME, as
 * the export does but with no player in between, then decoded; -1 if it fails. The tone of
 * toneFrom(1.0) then starts this many frames after 1 s.
 */
long mp3Delay() {
    const PcmBuffer pcm = toneFrom(1.0);
    const auto output = tempPath("sdengine-mp3-delay.mp3");
    lame_global_flags *lame = lame_init();
    lame_set_in_samplerate(lame, kRate);
    lame_set_num_channels(lame, 2);
    lame_set_brate(lame, 192);
    lame_set_quality(lame, 2);
    if (lame_init_params(lame) < 0) return -1;
    std::vector<unsigned char> mp3(size_t(pcm.frames) * 2 + 7200);
    int bytes = lame_encode_buffer_ieee_float(lame, pcm.data[0].data(), pcm.data[1].data(), int(pcm.frames), mp3.data(),
                                              int(mp3.size()));
    bytes += lame_encode_flush(lame, mp3.data() + bytes, int(mp3.size()) - bytes);
    lame_close(lame);
    if (FILE *file = std::fopen(output.c_str(), "wb")) {
        std::fwrite(mp3.data(), 1, size_t(bytes), file);
        std::fclose(file);
    }
    PcmBuffer decoded;
    std::string error;
    const bool ok = decodeFile(output, decoded, error);
    std::filesystem::remove(output);
    return ok && !decoded.data.empty() ? firstAbove(decoded.data[0], 0.05f) - kRate : -1;
}

SdEngine *openInMemory(PcmBuffer pcm);

void aSliceStartsAtAItself() {
    // Silence up to 5.1 s, then a tone. The player starts at 0.5 (5 s) exactly: the tone comes
    // 0.1 s of the file later, not after the stretcher's run-in (about 35 ms of the audio before A).
    // (Within a few ms: the stretcher smears the tone's onset a little, more at low speeds.)
    for (const double speed : {1.0, 0.5}) {
        SdEngine *e = openInMemory(toneFrom(5.1));
        sd_engine_set_speed(e, speed);
        sdEnginePlayer(e)->seekExact(0.5);
        sd_engine_set_playing(e, 1);
        const Rendered r = render(e, 0.5 / speed);
        const long onset = firstAbove(r.left, 0.05f);
        const double expected = 0.1 / speed * kRate;
        CHECK(onset >= 0 && std::fabs(double(onset) - expected) < 0.006 * kRate,
              "speed %.1f: the tone starts at frame %ld, A + 0.1 s is at %.0f", speed, onset, expected);
        sd_engine_destroy(e);
    }
}

void sliceHoldsTheRangeAndNothingElse() {
    // Silence for 5 s, then a tone for 5 s: the slice 0.4..0.6 is 1 s of silence, then 1 s of tone.
    const PcmBuffer pcm = toneFrom(5.0);
    const long codecDelay = mp3Delay();
    CHECK(codecDelay >= 0 && codecDelay < kRate / 10, "MP3 delay of %ld frames", codecDelay);
    const auto source = tempPath("sdengine-slice-source.wav");
    std::string error;
    CHECK(writeWavFloat(source, pcm, error), "cannot write: %s", error.c_str());
    SdEngine *e = openEngine(source);
    // Loop and playback state of the live player never reach the export.
    sd_engine_set_loop(e, 0.1, 0.2, 1);
    // At half speed too: the slice still starts at A, so the tone still comes half way through.
    for (const double speed : {1.0, 0.5}) {
        sd_engine_set_speed(e, speed);
        const SliceExport slice = exportSlice(e, tempPath("sdengine-slice-range.mp3"), 0.4, 0.6);
        const auto &left = slice.decoded.data.empty() ? std::vector<float>() : slice.decoded.data[0];
        const size_t second = size_t(double(kRate) / speed); // one second of the file, in output frames
        CHECK(std::fabs(slice.decoded.seconds() - 2.0 / speed) < 0.1, "speed %.1f: slice lasts %f s", speed,
              slice.decoded.seconds());
        if (left.size() < 2 * second - second / 10) continue;
        const std::vector<float> before(left.begin(), left.begin() + long(second * 9 / 10));
        const std::vector<float> after(left.begin() + long(second * 11 / 10), left.begin() + long(second * 19 / 10));
        CHECK(rms(before) < 0.01, "speed %.1f: audio before the tone starts: %f", speed, rms(before));
        CHECK(rms(after) > 0.3, "speed %.1f: the tone is missing: %f", speed, rms(after));
        // To the frame, give or take the stretcher's smear: the slice starts at A, not before it, so
        // the tone comes exactly one second of the file in (and the slice's fixed length ends at B).
        const long onset = firstAbove(left, 0.05f) - codecDelay;
        CHECK(std::fabs(double(onset) - double(second)) < 0.006 * kRate,
              "speed %.1f: the tone starts %.1f ms into the slice, expected %.1f ms", speed,
              1000.0 * double(onset) / kRate, 1000.0 * double(second) / kRate);
        // Only the music: the silent right channel stays silent (no click, no count-in).
        CHECK(rms(slice.decoded.data[1]) < 0.01, "speed %.1f: sound on the silent side: %f", speed,
              rms(slice.decoded.data[1]));
        // The cut fades out: the last 2 ms of sound (before the MP3's padding) are already quiet.
        size_t end = left.size();
        while (end > 0 && std::fabs(left[end - 1]) < 0.01f) --end;
        if (end > size_t(kRate / 500)) {
            const std::vector<float> tail(left.begin() + long(end - kRate / 500), left.begin() + long(end));
            CHECK(rms(tail) < 0.15, "speed %.1f: no fade-out at the cut: %f", speed, rms(tail));
        }
    }
    sd_engine_destroy(e);
    std::filesystem::remove(source);
}

void aBatchKeepsTheSettingsItTook(const std::string &path) {
    SdEngine *e = openEngine(path);
    sd_engine_set_speed(e, 1.0);
    SdExportSettings *frozen = sd_export_capture_settings(e);
    CHECK(frozen != nullptr, "cannot take the settings");
    // Changed while the batch runs: no file of the batch hears it.
    sd_engine_set_speed(e, 0.5);
    sd_engine_set_mix(e, 1.0);
    const auto output = tempPath("sdengine-slice-frozen.mp3");
    SdExport *exporter = sd_export_begin_range_with(e, output.c_str(), 192, 0.2, 0.5, frozen);
    CHECK(exporter != nullptr, "cannot start the slice");
    if (exporter) {
        while (sd_export_next(exporter) >= 0) {
        }
        CHECK(sd_export_end(exporter) == 1, "slice export incomplete");
        PcmBuffer decoded;
        std::string error;
        CHECK(decodeFile(output, decoded, error), "cannot read the MP3: %s", error.c_str());
        CHECK(std::fabs(decoded.seconds() - 3.0) < 0.1, "slice lasts %f s, not 3 s at the speed taken", decoded.seconds());
        if (decoded.data.size() == 2)
            CHECK(rms(decoded.data[0]) > 0.1, "the left side is gone: the mix changed after the settings were taken");
    }
    CHECK(sd_export_begin_range_with(e, output.c_str(), 192, 0.2, 0.5, nullptr) == nullptr, "began with no settings");
    sd_export_settings_free(frozen);
    std::filesystem::remove(output);
    sd_engine_destroy(e);
}

void sliceExportsShareTheDecodedAudio(const std::string &path) {
    SdEngine *e = openEngine(path);
    const auto shared = sdEnginePlayer(e)->source();
    const long ownersBefore = shared.use_count();
    const auto first = tempPath("sdengine-slice-a.mp3"), second = tempPath("sdengine-slice-b.mp3");
    SdExport *a = sd_export_begin_range(e, first.c_str(), 128, 0.0, 0.3);
    SdExport *b = sd_export_begin_range(e, second.c_str(), 128, 0.5, 0.7);
    CHECK(a && b, "cannot start two slices");
    CHECK(shared.use_count() == ownersBefore + 2, "the slices did not share the buffer (%ld owners)", shared.use_count());
    if (a) sd_export_end(a);
    if (b) sd_export_end(b);
    CHECK(shared.use_count() == ownersBefore, "a slice kept the buffer (%ld owners)", shared.use_count());
    std::filesystem::remove(first);
    std::filesystem::remove(second);
    sd_engine_destroy(e);
}

void anEmptyRangeExportsNothing(const std::string &path) {
    SdEngine *e = openEngine(path);
    const auto output = tempPath("sdengine-slice-empty.mp3");
    CHECK(sd_export_begin_range(e, output.c_str(), 128, 0.5, 0.5) == nullptr, "empty range started");
    CHECK(sd_export_begin_range(e, output.c_str(), 128, 0.6, 0.4) == nullptr, "reversed range started");
    CHECK(sd_export_begin_range(e, output.c_str(), 128, 1.2, 1.5) == nullptr, "range past the end started");
    sd_engine_close(e);
    CHECK(sd_export_begin_range(e, output.c_str(), 128, 0.1, 0.2) == nullptr, "export with nothing open");
    std::filesystem::remove(output);
    sd_engine_destroy(e);
}

// --- Metronome, count-in, tempo detection -----------------------------------------------------------

PcmBuffer silence(double seconds) {
    PcmBuffer pcm;
    pcm.sampleRate = kRate;
    pcm.channels = 2;
    pcm.frames = int64_t(seconds * kRate);
    pcm.data.assign(2, std::vector<float>(size_t(pcm.frames), 0.f));
    return pcm;
}

/** A 441 Hz tone on the left only: the right channel of the output then carries the clicks alone. */
PcmBuffer toneOnTheLeft(double seconds) {
    PcmBuffer pcm = silence(seconds);
    for (int64_t i = 0; i < pcm.frames; ++i) pcm.data[0][size_t(i)] = float(0.5 * std::sin(2 * M_PI * 441 * double(i) / kRate));
    return pcm;
}

/** A 5 ms burst at every beat, on the given channels (both when `channels` is 2). */
PcmBuffer clickTrack(double bpm, double offsetSeconds, double seconds, bool leftOnly, double background = 0) {
    PcmBuffer pcm = silence(seconds);
    for (int64_t i = 0; i < pcm.frames; ++i) {
        const float hum = float(background * std::sin(2 * M_PI * 110 * double(i) / kRate));
        pcm.data[0][size_t(i)] = hum;
        if (!leftOnly) pcm.data[1][size_t(i)] = hum;
    }
    const int burst = kRate / 200;
    for (double t = offsetSeconds; t < seconds; t += 60.0 / bpm) {
        const auto start = int64_t(std::llround(t * kRate));
        for (int i = 0; i < burst && start + i < pcm.frames; ++i) {
            const float x = float(0.6 * std::exp(-i / (0.001 * kRate)) * std::sin(2 * M_PI * 3000 * i / kRate));
            pcm.data[0][size_t(start + i)] += x;
            if (!leftOnly) pcm.data[1][size_t(start + i)] += x;
        }
    }
    return pcm;
}

SdEngine *openInMemory(PcmBuffer pcm) {
    SdEngine *engine = sd_engine_create(0, kRate);
    sdEnginePlayer(engine)->open(std::make_shared<PcmBuffer>(std::move(pcm)), "memory");
    return engine;
}

/** Where `x` first rises above `threshold` after at least `quietFrames` below it. */
std::vector<int> onsets(const std::vector<float> &x, float threshold, int quietFrames = kRate / 20) {
    std::vector<int> found;
    int quiet = quietFrames;
    for (size_t i = 0; i < x.size(); ++i) {
        if (std::fabs(x[i]) > threshold) {
            if (quiet >= quietFrames) found.push_back(int(i));
            quiet = 0;
        } else {
            ++quiet;
        }
    }
    return found;
}

float peakAfter(const std::vector<float> &x, int from, int frames) {
    float peak = 0;
    for (int i = from; i < from + frames && i < int(x.size()); ++i) peak = std::max(peak, std::fabs(x[size_t(i)]));
    return peak;
}

std::vector<float> difference(const Rendered &r) {
    std::vector<float> d(r.left.size());
    for (size_t i = 0; i < d.size(); ++i) d[i] = r.left[i] - r.right[i];
    return d;
}

void metronomeClicksOnTheBeatAtAnySpeed() {
    for (const double speed : {1.0, 0.75, 0.5}) {
        SdEngine *e = openInMemory(silence(20));
        sd_engine_set_tempo(e, 120, 0.25, 4);
        sd_engine_set_metronome(e, 1, 1.0);
        sd_engine_set_speed(e, speed);
        sd_engine_set_playing(e, 1);
        const Rendered r = render(e, 4.0 / speed);
        const auto found = onsets(r.right, 1e-3f);
        CHECK(r.left == r.right, "speed %.2f: the click is not on both sides", speed);
        CHECK(found.size() == 8, "speed %.2f: %d clicks", speed, int(found.size()));
        // After a start the music itself comes a fixed run-in later (Bungee's preroll, about 35 ms):
        // the clicks follow the music (see the next test), so they keep that same lag, exactly.
        const double lag = found.empty() ? 0 : found[0] - 0.25 / speed * kRate;
        CHECK(lag >= 0 && lag < 0.05 * kRate, "speed %.2f: first click %.1f frames after its beat", speed, lag);
        for (size_t k = 0; k < found.size(); ++k) {
            const double expected = (0.25 + 0.5 * double(k)) / speed * kRate + lag;
            CHECK(std::fabs(found[k] - expected) <= 1, "speed %.2f: click %d at frame %d, beat at %.1f", speed, int(k),
                  found[k], expected);
        }
        for (size_t k = 0; k + 1 < found.size(); ++k) {
            const bool accent = k % 4 == 0;
            const float level = peakAfter(r.right, found[k], 200), next = peakAfter(r.right, found[k + 1], 200);
            if (accent) CHECK(level > next * 1.4f, "speed %.2f: beat %d not accented (%f vs %f)", speed, int(k), level, next);
            else if ((k + 1) % 4 != 0) CHECK(std::fabs(level - next) < 1e-3f, "speed %.2f: beat %d level %f vs %f", speed, int(k), level, next);
        }
        sd_engine_destroy(e);
    }
}

void metronomeLinesUpWithTheMusic() {
    // The music's own beats on the left only: left - right is the stretched music, right the click.
    for (const double speed : {1.0, 0.75}) {
        SdEngine *e = openInMemory(clickTrack(96, 0.4, 20, true));
        sd_engine_set_tempo(e, 96, 0.4, 4);
        sd_engine_set_metronome(e, 1, 1.0);
        sd_engine_set_speed(e, speed);
        sd_engine_seek(e, 0.1);
        sd_engine_set_playing(e, 1);
        const Rendered r = render(e, 6.0);
        const auto clicks = onsets(r.right, 1e-3f);
        const auto music = onsets(difference(r), 0.05f);
        CHECK(clicks.size() >= 6 && music.size() >= 6, "speed %.2f: %d clicks, %d music beats", speed,
              int(clicks.size()), int(music.size()));
        double worst = 0;
        for (size_t k = 0; k < std::min(clicks.size(), music.size()); ++k)
            worst = std::max(worst, std::fabs(double(clicks[k] - music[k])) / kRate);
        CHECK(worst < 0.002, "speed %.2f: click and music beat up to %.2f ms apart", speed, worst * 1000);
        sd_engine_destroy(e);
    }
}

void metronomeFollowsTheLoopWrap() {
    // 120 BPM from 0: A on a beat clicks at every wrap, A off the beat never does.
    const auto clicksFor = [](double a, double b) {
        SdEngine *e = openInMemory(silence(10));
        sd_engine_set_tempo(e, 120, 0, 4);
        sd_engine_set_metronome(e, 1, 1.0);
        sd_engine_set_loop_and_seek(e, a / 10, b / 10, 1, a / 10);
        sd_engine_set_playing(e, 1);
        const Rendered r = render(e, 4.0);
        CHECK(sd_engine_take_events(e) == 0, "loop %.2f-%.2f raised an event", a, b);
        sd_engine_destroy(e);
        std::vector<double> seconds;
        for (const int frame : onsets(r.right, 1e-3f)) seconds.push_back(double(frame) / kRate);
        return seconds;
    };
    // Times from the first beat heard (the music's run-in comes first, as in the test above).
    const std::vector<double> onBeat = {0, 0.5, 1.0, 1.25, 1.75, 2.25, 2.5, 3.0, 3.5, 3.75};
    const auto got = clicksFor(1.0, 2.25);
    const double lag = got.empty() ? 0 : got[0];
    CHECK(lag < 0.05, "first click %.4f s after the start", lag);
    CHECK(got.size() == onBeat.size(), "A on the beat: %d clicks, expected %d", int(got.size()), int(onBeat.size()));
    for (size_t k = 0; k < std::min(got.size(), onBeat.size()); ++k)
        CHECK(std::fabs(got[k] - lag - onBeat[k]) < 2.0 / kRate, "A on the beat: click %d at %.5f s, expected %.5f",
              int(k), got[k] - lag, onBeat[k]);
    // 1.1..2.35: beats at 1.5 and 2.0, so 0.4 and 0.9 s into each 1.25 s pass; never at A.
    const std::vector<double> offBeat = {0.4, 0.9, 1.65, 2.15, 2.9, 3.4};
    const auto got2 = clicksFor(1.1, 2.35);
    CHECK(got2.size() == offBeat.size(), "A off the beat: %d clicks, expected %d", int(got2.size()), int(offBeat.size()));
    for (size_t k = 0; k < std::min(got2.size(), offBeat.size()); ++k)
        CHECK(std::fabs(got2[k] - lag - offBeat[k]) < 2.0 / kRate, "A off the beat: click %d at %.5f s, expected %.5f",
              int(k), got2[k] - lag, offBeat[k]);
}

void countInStartsTheMusicOnTheBeat() {
    SdEngine *e = openInMemory(toneOnTheLeft(20));
    sd_engine_set_tempo(e, 100, 0.123, 4);
    sd_engine_set_metronome(e, 0, 0.8);
    sd_engine_set_count_in(e, 4, 0);
    sd_engine_set_speed(e, 0.8);
    sd_engine_seek(e, 0.25);
    sd_engine_set_playing(e, 1);
    CHECK(sd_engine_count_in_remaining(e) == 0, "counting before playing");
    Rendered r = render(e, 100.0 / kRate);
    CHECK(sd_engine_count_in_remaining(e) == 3, "%d clicks left after the first", sd_engine_count_in_remaining(e));
    const Rendered rest = render(e, 4.0);
    r.left.insert(r.left.end(), rest.left.begin(), rest.left.end());
    r.right.insert(r.right.end(), rest.right.begin(), rest.right.end());

    const double interval = kRate * 60.0 / (100 * 0.8); // 33075 frames
    const auto clicks = onsets(r.right, 1e-3f);
    CHECK(clicks.size() == 4, "%d count-in clicks", int(clicks.size()));
    for (size_t k = 0; k < clicks.size(); ++k)
        CHECK(clicks[k] == int(std::llround(double(k) * interval)), "click %d at %d", int(k), clicks[k]);
    if (clicks.size() == 4)
        CHECK(peakAfter(r.right, clicks[0], 200) > 1.4f * peakAfter(r.right, clicks[1], 200), "first click not accented");
    const auto music = difference(r);
    const int onset = int(std::llround(4 * interval));
    int first = -1;
    for (size_t i = 0; i < music.size(); ++i)
        if (std::fabs(music[i]) > 1e-6f) {
            first = int(i);
            break;
        }
    CHECK(first >= onset && first <= onset + 2, "music starts at frame %d, the beat is at %d", first, onset);
    CHECK(rms(std::vector<float>(music.begin() + onset + kRate / 50, music.begin() + onset + kRate / 2)) > 0.2,
          "music too quiet after the count-in");
    CHECK(sd_engine_count_in_remaining(e) == 0, "still counting");
    sd_engine_destroy(e);

    // The position stays at the start while counting.
    SdEngine *e2 = openInMemory(toneOnTheLeft(20));
    sd_engine_set_count_in(e2, 4, 0); // no tempo: 120 BPM, 2 s
    sd_engine_seek(e2, 0.5);
    sd_engine_set_playing(e2, 1);
    render(e2, 1.5);
    CHECK(sd_engine_position(e2) == 0.5, "position %f during the count-in", sd_engine_position(e2));
    CHECK(sd_engine_count_in_remaining(e2) == 1, "%d left at 1.5 s of 2", sd_engine_count_in_remaining(e2));
    sd_engine_set_playing(e2, 0); // stopping cancels it
    render(e2, 0.1);
    CHECK(sd_engine_count_in_remaining(e2) == 0, "count-in survived the stop");
    CHECK(rms(render(e2, 0.5).right) == 0.0, "clicks after the stop");
    sd_engine_destroy(e2);
}

void countInEveryLoopLeavesAGapAtEachWrap() {
    // Loop 1..2 s, 2 beats at 120 BPM: 1 s of clicks, 1 s of music, again and again.
    SdEngine *e = openInMemory(toneOnTheLeft(10));
    sd_engine_set_tempo(e, 120, 0, 4);
    sd_engine_set_count_in(e, 2, 1);
    sd_engine_set_loop_and_seek(e, 0.1, 0.2, 1, 0.1);
    sd_engine_set_playing(e, 1);
    Rendered r;
    double positionInGap = -1;
    int remainingInGap = -1;
    for (int block = 0; block < 6 * kRate / 441; ++block) { // 6 s in 10 ms blocks
        const Rendered part = render(e, 441.0 / kRate);
        r.left.insert(r.left.end(), part.left.begin(), part.left.end());
        r.right.insert(r.right.end(), part.right.begin(), part.right.end());
        if (block == 230) { // 2.3 s: in the second gap, its second click to come at 2.5 s
            positionInGap = sd_engine_position(e);
            remainingInGap = sd_engine_count_in_remaining(e);
        }
    }
    CHECK(sd_engine_take_events(e) == 0, "an event at the loop end");
    CHECK(sd_engine_is_playing(e), "stopped at the loop end");
    CHECK(positionInGap == 0.1, "position %f in the gap", positionInGap);
    CHECK(remainingInGap == 1, "%d clicks left in the gap", remainingInGap);
    const auto music = difference(r);
    const auto level = [&](double from, double to) {
        return rms(std::vector<float>(music.begin() + long(from * kRate), music.begin() + long(to * kRate)));
    };
    for (int pass = 0; pass < 3; ++pass) {
        const double start = 2.0 * pass;
        CHECK(level(start + 0.01, start + 0.98) == 0.0, "pass %d: music during the count-in (%f)", pass,
              level(start + 0.01, start + 0.98));
        CHECK(level(start + 1.05, start + 1.9) > 0.2, "pass %d: no music after the count-in (%f)", pass,
              level(start + 1.05, start + 1.9));
    }
    const auto clicks = onsets(r.right, 1e-3f);
    CHECK(clicks.size() == 6, "%d clicks in three count-ins", int(clicks.size()));
    sd_engine_destroy(e);
}

double circularDistance(double a, double b, double period) {
    const double d = std::fmod(std::fabs(a - b), period);
    return std::min(d, period - d);
}

void detectsTheTempoOfAClickTrack() {
    struct Case {
        double bpm, offset;
    };
    for (const Case c : {Case{92, 0.3}, Case{128, 0.123}, Case{174, 0.05}, Case{140, 0.71}}) {
        SdEngine *e = openInMemory(clickTrack(c.bpm, c.offset, 30, false, 0.1));
        double bpm = 0, offset = -1, confidence = -1;
        CHECK(sd_engine_detect_tempo(e, &bpm, &offset, &confidence) == 1, "%.0f BPM not found", c.bpm);
        const double period = 60.0 / c.bpm;
        CHECK(std::fabs(bpm - c.bpm) <= 0.5, "%.0f BPM found as %.3f", c.bpm, bpm);
        CHECK(offset >= 0 && offset < 60.0 / bpm, "%.0f BPM: offset %.4f outside a beat", c.bpm, offset);
        CHECK(circularDistance(offset, c.offset, period) <= 0.010, "%.0f BPM: offset %.4f, beats at %.4f", c.bpm, offset,
              std::fmod(c.offset, period));
        CHECK(confidence > 0.3, "%.0f BPM: confidence %.3f", c.bpm, confidence);
        std::printf("     %.0f BPM: %.3f BPM, offset %+.1f ms, confidence %.2f\n", c.bpm, bpm,
                    1000 * (std::fmod(offset - c.offset + 10 * period + period / 2, period) - period / 2), confidence);
        // A typed tempo keeps its value: only the phase is found.
        double typed = c.bpm;
        CHECK(sd_engine_detect_tempo(e, &typed, &offset, &confidence) == 1 && typed == c.bpm, "typed %.0f gave %.3f",
              c.bpm, typed);
        CHECK(circularDistance(offset, c.offset, period) <= 0.010, "typed %.0f: offset %.4f", c.bpm, offset);
        sd_engine_destroy(e);
    }
}

void noTempoInSilenceOrNoise() {
    PcmBuffer noise = silence(20);
    uint32_t seed = 12345;
    for (auto &channel : noise.data)
        for (auto &x : channel) {
            seed = seed * 1664525u + 1013904223u;
            x = float(0.3 * (double(seed >> 8) / double(1u << 24) - 0.5));
        }
    const PcmBuffer tone = toneOnTheLeft(20);
    // As a 16-bit file decodes: both sides, quantised.
    PcmBuffer quantised = silence(10);
    for (int64_t i = 0; i < quantised.frames; ++i)
        quantised.data[0][size_t(i)] = quantised.data[1][size_t(i)] =
            float(int(16000 * std::sin(2 * M_PI * 441 * double(i) / kRate))) / 32768.f;
    for (const auto &[name, pcm] : {std::pair<const char *, const PcmBuffer *>{"noise", &noise}, {"a steady tone", &tone},
                                    {"a 16-bit tone", &quantised}}) {
        SdEngine *e = openInMemory(*pcm);
        double bpm = 0, offset = 0, confidence = 0;
        CHECK(sd_engine_detect_tempo(e, &bpm, &offset, &confidence) == 0, "%s: %.2f BPM (confidence %.3f)", name, bpm,
              confidence);
        CHECK(bpm == 0 && offset == 0 && confidence == 0, "%s: outputs not cleared", name);
        sd_engine_destroy(e);
    }
    SdEngine *quiet = openInMemory(silence(20));
    double bpm = 0, offset = 0, confidence = 0;
    CHECK(sd_engine_detect_tempo(quiet, &bpm, &offset, &confidence) == 0, "silence: %.2f BPM", bpm);
    sd_engine_destroy(quiet);
    SdEngine *empty = sd_engine_create(0, kRate);
    CHECK(sd_engine_detect_tempo(empty, &bpm, &offset, &confidence) == 0, "nothing open: %.2f BPM", bpm);
    sd_engine_destroy(empty);
}

void exportHasNoClicks() {
    SdEngine *e = openInMemory(silence(5));
    sd_engine_set_tempo(e, 120, 0, 4);
    sd_engine_set_metronome(e, 1, 1.0);
    sd_engine_set_count_in(e, 4, 1);
    const auto output = tempPath("sdengine-export-clicks.mp3");
    SdExport *exporter = sd_export_begin(e, output.c_str(), 128);
    CHECK(exporter != nullptr, "cannot start export");
    if (exporter) {
        while (sd_export_next(exporter) >= 0) {
        }
        CHECK(sd_export_end(exporter) == 1, "export incomplete");
        PcmBuffer decoded;
        std::string error;
        CHECK(decodeFile(output, decoded, error), "cannot read the MP3: %s", error.c_str());
        CHECK(std::fabs(decoded.seconds() - 5.0) < 0.3, "MP3 lasts %f s (a count-in would add 2)", decoded.seconds());
        float peak = 0;
        for (const auto &channel : decoded.data) peak = std::max(peak, peakAfter(channel, 0, int(channel.size())));
        CHECK(peak < 1e-3f, "the export clicks (peak %f)", peak);
    }
    std::filesystem::remove(output);
    sd_engine_destroy(e);
}


/** A drum pattern from 0.1 s: kick on beats 1 and 3, snare on 2 and 4, a hi-hat `hatsPerBeat` times a beat, over a chord pad. */
PcmBuffer drumPattern(double bpm, double seconds, int hatsPerBeat) {
    PcmBuffer pcm = silence(seconds);
    uint32_t seed = 7;
    const auto noise = [&] {
        seed = seed * 1664525u + 1013904223u;
        return double(seed >> 8) / double(1u << 24) - 0.5;
    };
    const double beat = 60.0 / bpm;
    for (int64_t i = 0; i < pcm.frames; ++i) {
        const double t = double(i) / kRate;
        const double f = int(t / (4 * beat)) % 2 ? 220.0 : 196.0;
        const double pad = 0.08 * (std::sin(2 * M_PI * f * t) + std::sin(2 * M_PI * f * 1.26 * t) + std::sin(2 * M_PI * f * 1.5 * t));
        pcm.data[0][size_t(i)] = pcm.data[1][size_t(i)] = float(pad);
    }
    const auto add = [&](double at, int frames, const std::function<double(int)> &sound) {
        const auto start = int64_t(at * kRate);
        for (int i = 0; i < frames && start + i < pcm.frames; ++i) {
            const float x = float(sound(i));
            pcm.data[0][size_t(start + i)] += x;
            pcm.data[1][size_t(start + i)] += x;
        }
    };
    for (int b = 0; b < int(seconds / beat); ++b) {
        for (int h = 0; h < hatsPerBeat; ++h)
            add(0.1 + b * beat + h * beat / hatsPerBeat, kRate / 30, [&](int i) { return 0.4 * noise() * std::exp(-i / (0.008 * kRate)); });
        if (b % 2 == 0)
            add(0.1 + b * beat, kRate / 5, [](int i) {
                return 0.8 * std::exp(-i / (0.05 * kRate)) * std::sin(2 * M_PI * (50 + 80 * std::exp(-i / (0.01 * kRate))) * i / kRate);
            });
        else
            add(0.1 + b * beat, kRate / 5, [&](int i) { return 0.5 * noise() * std::exp(-i / (0.04 * kRate)); });
    }
    return pcm;
}

/** Piano-like notes from 0.1 s, `notesPerBeat` a beat (the beat's own louder), over a low drone. */
PcmBuffer pianoPattern(double bpm, double seconds, int notesPerBeat) {
    PcmBuffer pcm = silence(seconds);
    const double beat = 60.0 / bpm;
    for (int64_t i = 0; i < pcm.frames; ++i) {
        const double t = double(i) / kRate;
        pcm.data[0][size_t(i)] = pcm.data[1][size_t(i)] = float(0.3 * (std::sin(2 * M_PI * 55 * t) + 0.5 * std::sin(2 * M_PI * 82.5 * t)));
    }
    const double scale[] = {261.6, 293.7, 329.6, 349.2, 392.0, 440.0, 493.9};
    for (int k = 0; k < int(seconds / beat * notesPerBeat); ++k) {
        const double f = scale[(k * 3 + k / 5) % 7];
        const double amp = (k % notesPerBeat == 0 ? 0.35 : 0.2) * (k % (4 * notesPerBeat) == 0 ? 1.3 : 1.0);
        const auto start = int64_t((0.1 + k * beat / notesPerBeat) * kRate);
        for (int i = 0; i < kRate && start + i < pcm.frames; ++i) {
            const double env = std::min(1.0, i / (0.003 * kRate)) * std::exp(-i / (0.3 * kRate));
            const double x = amp * env *
                             (std::sin(2 * M_PI * f * i / kRate) + 0.4 * std::sin(4 * M_PI * f * i / kRate) +
                              0.2 * std::sin(6 * M_PI * f * i / kRate));
            pcm.data[0][size_t(start + i)] += float(x);
            pcm.data[1][size_t(start + i)] += float(x);
        }
    }
    return pcm;
}

void detectsTheBeatOfMusicLikePatterns() {
    // Beats at 0.1 s + k·60/bpm. Twice or half the tempo is the usual ambiguity and still clicks on
    // the beats; anything else (three sixteenths of a busy hi-hat, say) would drift against the music.
    const auto check = [](const char *what, double bpm, int perBeat, const PcmBuffer &pcm, bool octaveAllowed) {
        SdEngine *e = openInMemory(pcm);
        double found = 0, offset = 0, confidence = 0;
        CHECK(sd_engine_detect_tempo(e, &found, &offset, &confidence) == 1, "%s %.0f/%d: no tempo", what, bpm, perBeat);
        const double ratio = found / bpm;
        const bool same = std::fabs(found - bpm) <= 0.5;
        const bool octave = std::fabs(ratio - 2) < 0.01 || std::fabs(ratio - 0.5) < 0.005;
        CHECK(same || (octaveAllowed && octave), "%s %.0f/%d: found %.3f BPM", what, bpm, perBeat, found);
        if (found > 0)
            CHECK(circularDistance(offset, 0.1, 60.0 / found) <= 0.010, "%s %.0f/%d: first beat at %.4f", what, bpm,
                  perBeat, offset);
        sd_engine_destroy(e);
    };
    for (const double bpm : {72.0, 100.0, 140.0, 165.0})
        for (const int hats : {1, 2, 4}) check("drums", bpm, hats, drumPattern(bpm, 30, hats), true);
    for (const double bpm : {66.0, 105.0, 150.0})
        for (const int notes : {1, 2}) check("piano", bpm, notes, pianoPattern(bpm, 30, notes), false);
}

void seekDuringTheCountInMovesWhereTheMusicStarts() {
    SdEngine *e = openInMemory(toneOnTheLeft(20));
    sd_engine_set_tempo(e, 120, 0, 4);
    sd_engine_set_count_in(e, 4, 0);
    sd_engine_set_speed(e, 0.5); // 1 s a beat: 4 s of count-in
    sd_engine_seek(e, 0.25);
    sd_engine_set_playing(e, 1);
    Rendered r = render(e, 1.0);
    sd_engine_seek(e, 0.6);
    const Rendered second = render(e, 1.0);
    CHECK(sd_engine_position(e) == 0.6, "position %f during the count-in after a seek", sd_engine_position(e));
    CHECK(sd_engine_count_in_remaining(e) == 2, "%d clicks left after 2 s of 4", sd_engine_count_in_remaining(e));
    const Rendered rest = render(e, 3.0);
    for (const Rendered *part : {&second, &rest}) {
        r.left.insert(r.left.end(), part->left.begin(), part->left.end());
        r.right.insert(r.right.end(), part->right.begin(), part->right.end());
    }
    const auto clicks = onsets(r.right, 1e-3f);
    CHECK(clicks.size() == 4, "%d count-in clicks", int(clicks.size()));
    for (size_t k = 0; k < clicks.size(); ++k) CHECK(clicks[k] == int(k) * kRate, "click %d at %d", int(k), clicks[k]);
    const auto music = difference(r);
    int first = -1;
    for (size_t i = 0; i < music.size(); ++i)
        if (std::fabs(music[i]) > 1e-6f) {
            first = int(i);
            break;
        }
    CHECK(first >= 4 * kRate && first <= 4 * kRate + 2, "music starts at frame %d, the beat is at %d", first, 4 * kRate);
    CHECK(sd_engine_position(e) > 0.6, "the music did not go on from the seek (%f)", sd_engine_position(e));
    sd_engine_destroy(e);
}

/** The first frame where the left channel differs from the right (the music, with clicks on both), or -1. */
int musicStart(const Rendered &r, int from = 0) {
    const auto music = difference(r);
    for (size_t i = size_t(from); i < music.size(); ++i)
        if (std::fabs(music[i]) > 1e-6f) return int(i);
    return -1;
}

void aSpeedChangeDuringTheCountInRetimesTheClicksToCome() {
    // The click already heard stays; the clicks still to come, and the music after them, follow the
    // new tempo.
    SdEngine *e = openInMemory(toneOnTheLeft(20));
    sd_engine_set_tempo(e, 120, 0, 4);
    sd_engine_set_count_in(e, 4, 0);
    sd_engine_set_speed(e, 0.5); // 1 s a beat
    sd_engine_seek(e, 0.25);
    sd_engine_set_playing(e, 1);
    Rendered r = render(e, 0.3);
    sd_engine_set_speed(e, 1.0); // 0.5 s a beat, from the click at 0
    const Rendered rest = render(e, 2.5);
    r.left.insert(r.left.end(), rest.left.begin(), rest.left.end());
    r.right.insert(r.right.end(), rest.right.begin(), rest.right.end());
    const auto clicks = onsets(r.right, 1e-3f, 1000);
    const std::vector<int> expected = {0, kRate / 2, kRate, 3 * kRate / 2};
    CHECK(clicks.size() == expected.size(), "%d count-in clicks", int(clicks.size()));
    for (size_t k = 0; k < std::min(clicks.size(), expected.size()); ++k)
        CHECK(std::abs(clicks[k] - expected[k]) <= 1, "click %d at %d, expected %d", int(k), clicks[k], expected[k]);
    const int first = musicStart(r);
    CHECK(first >= 2 * kRate && first <= 2 * kRate + 2, "music starts at frame %d, the beat is at %d", first, 2 * kRate);
    sd_engine_destroy(e);

    // Count-in every loop with Progression: the step to the next pass's speed lands just after the
    // loop restarts, while it counts in again. Loop 5..5.5 s, 2 beats at 120 BPM: clicks at 0 and
    // 0.5 s, music 1..1.5 s, then clicks from 1.5 s; the speed halves at 1.6 s, after the first one,
    // so the second comes 1 s after it and the music 1 s later still.
    SdEngine *loop = openInMemory(toneOnTheLeft(20));
    sd_engine_set_tempo(loop, 120, 0, 4);
    sd_engine_set_count_in(loop, 2, 1);
    sd_engine_set_loop_and_seek(loop, 0.25, 0.275, 1, 0.25);
    sd_engine_set_playing(loop, 1);
    Rendered passes = render(loop, 1.6);
    sd_engine_set_speed(loop, 0.5); // 1 s a beat, from the click at 1.5 s
    const Rendered next = render(loop, 2.5);
    passes.left.insert(passes.left.end(), next.left.begin(), next.left.end());
    passes.right.insert(passes.right.end(), next.right.begin(), next.right.end());
    const auto wrapClicks = onsets(passes.right, 1e-3f, 1000);
    CHECK(wrapClicks.size() == 4, "%d clicks over two count-ins", int(wrapClicks.size()));
    if (wrapClicks.size() == 4) {
        // The loop stops where the stretcher reaches B, about 1.5 s in: times from that restart.
        const int restart = wrapClicks[2];
        CHECK(std::abs(restart - 3 * kRate / 2) < kRate / 20, "the loop restarts at frame %d", restart);
        CHECK(std::abs(wrapClicks[3] - restart - kRate) <= 1, "the second click %d frames after the first, not 1 s",
              wrapClicks[3] - restart);
        const int again = musicStart(passes, restart + kRate / 10);
        CHECK(again >= restart + 2 * kRate && again <= restart + 2 * kRate + 2,
              "the second pass starts %d frames after its count-in, not 2 s", again - restart);
    }
    sd_engine_destroy(loop);
}

void countInFlowsIntoTheMetronome() {
    // Starting on a beat with the metronome on: the count-in's clicks and the metronome's are one even
    // pulse, the music's first beat the accent after the last count-in click.
    SdEngine *e = openInMemory(silence(20));
    sd_engine_set_tempo(e, 120, 0.5, 4);
    sd_engine_set_metronome(e, 1, 1.0);
    sd_engine_set_count_in(e, 4, 0);
    sd_engine_set_speed(e, 0.5);
    sd_engine_seek(e, 0.5 / 20);
    sd_engine_set_playing(e, 1);
    const Rendered r = render(e, 6.0);
    const auto clicks = onsets(r.right, 1e-3f, 1000);
    CHECK(clicks.size() == 6, "%d clicks in 6 s", int(clicks.size()));
    for (size_t k = 0; k < clicks.size(); ++k)
        CHECK(std::abs(clicks[k] - int(k) * kRate) <= 1, "click %d at %d, expected %d", int(k), clicks[k], int(k) * kRate);
    if (clicks.size() == 6)
        CHECK(peakAfter(r.right, clicks[4], 200) > 1.4f * peakAfter(r.right, clicks[3], 200), "the music's downbeat not accented");
    sd_engine_destroy(e);
}

} // namespace

int main() {
    const std::string path = writeTestFile();
    const std::pair<const char *, std::function<void()>> tests[] = {
        {"plays at half speed", [&] { playsAtHalfSpeed(path); }},
        {"stops with a fade and keeps the position", [&] { stopsWithFadeAndKeepsPosition(path); }},
        {"seek while stopped is reported at once", [&] { seekWhileStoppedIsReportedAtOnce(path); }},
        {"position never goes back after a seek", [&] { positionNeverGoesBackAfterSeek(path); }},
        {"loop and seek land together", [&] { loopAndSeekLandTogether(path); }},
        {"exports the whole file at the current speed", [&] { exportsTheWholeFileAtTheCurrentSpeed(path); }},
        {"loop stays inside its points", [&] { loopStaysInside(path); }},
        {"reaches the end with the loop off", [&] { reachesEndWithLoopOff(path); }},
        {"mix fades the far side", [&] { mixFadesTheFarSide(path); }},
        {"peaking bands match the cookbook", [] { peakingBandsMatchTheCookbook(); }},
        {"a flat EQ is bit transparent", [] { flatEqIsBitTransparent(); }},
        {"EQ changes glide without steps", [] { eqChangesGlideWithoutSteps(); }},
        {"EQ frequency and width glide", [] { eqFrequencyAndWidthGlide(); }},
        {"isolate cuts outside the selection", [] { isolateCutsOutsideTheSelection(); }},
        {"the first settings apply at once", [] { firstSettingsApplyAtOnce(); }},
        {"preamp only with the EQ on", [&] { preampOnlyWithEqOn(path); }},
        {"decoder keeps every frame", [] { decoderKeepsEveryFrame(); }},
        {"an unreadable file fails cleanly", [] { unreadableFileFailsCleanly(); }},
        {"mono is kept as one channel and plays on both sides", [] { monoIsKeptAsOneChannelAndPlaysOnBothSides(); }},
        {"export shares the decoded audio", [&] { exportSharesTheDecodedAudio(path); }},
        {"flushes denormals in scope", [] { flushesDenormalsInScope(); }},
        {"rendering does not allocate", [&] { renderingDoesNotAllocate(path); }},
        {"peaks outline the open file", [] { peaksOutlineTheOpenFile(); }},
        {"a corrupt length reserves only what the file can hold", [] { aCorruptLengthReservesOnlyWhatTheFileCanHold(); }},
        {"export begins while another file opens", [&] { exportBeginsWhileAnotherFileOpens(path); }},
        {"exports a loop slice at the current speed", [&] { exportsALoopSliceAtTheCurrentSpeed(path); }},
        {"slice progress rises within the range", [&] { sliceProgressRisesWithinTheRange(path); }},
        {"a slice holds the range and nothing else", [] { sliceHoldsTheRangeAndNothingElse(); }},
        {"a slice starts at A itself", [] { aSliceStartsAtAItself(); }},
        {"a batch keeps the settings it took", [&] { aBatchKeepsTheSettingsItTook(path); }},
        {"slice exports share the decoded audio", [&] { sliceExportsShareTheDecodedAudio(path); }},
        {"an empty range exports nothing", [&] { anEmptyRangeExportsNothing(path); }},
        {"metronome clicks on the beat at any speed", [] { metronomeClicksOnTheBeatAtAnySpeed(); }},
        {"metronome lines up with the music", [] { metronomeLinesUpWithTheMusic(); }},
        {"metronome follows the loop wrap", [] { metronomeFollowsTheLoopWrap(); }},
        {"count-in starts the music on the beat", [] { countInStartsTheMusicOnTheBeat(); }},
        {"count-in every loop leaves a gap at each wrap", [] { countInEveryLoopLeavesAGapAtEachWrap(); }},
        {"detects the tempo of a click track", [] { detectsTheTempoOfAClickTrack(); }},
        {"no tempo in silence or noise", [] { noTempoInSilenceOrNoise(); }},
        {"export has no clicks", [] { exportHasNoClicks(); }},
        {"detects the beat of drums and piano", [] { detectsTheBeatOfMusicLikePatterns(); }},
        {"a seek during the count-in moves where the music starts", [] { seekDuringTheCountInMovesWhereTheMusicStarts(); }},
        {"a speed change during the count-in retimes the clicks to come", [] { aSpeedChangeDuringTheCountInRetimesTheClicksToCome(); }},
        {"the count-in flows into the metronome", [] { countInFlowsIntoTheMetronome(); }},
    };
    for (const auto &[name, test] : tests) {
        const int before = failures;
        test();
        std::printf("%s %s\n", failures == before ? "ok  " : "FAIL", name);
    }
    std::filesystem::remove(path);
    std::printf("%d failure(s)\n", failures);
    return failures == 0 ? 0 : 1;
}
