/*
 * Engine tests through the C API in manual mode: no device, the test pulls the audio.
 * Plain asserts with a summary, so they run with `ctest` and need no framework.
 */
#include "Denormals.h"
#include "Equalizer.h"
#include "Pcm.h"
#include "Player.h"
#include "sdengine.h"

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
        sd_engine_set_playing(e, 1);
        std::vector<float> block(512 * 2);
        for (int i = 0; i < 40; ++i) sd_engine_render(e, block.data(), 512);
        // Like slider drags: new values every 512-frame block, plus a seek and a loop.
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
            if (i == 200) sd_engine_set_loop_and_seek(e, 0.3, 0.5, 1, 0.3);
            sd_engine_render(e, block.data(), 512);
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
