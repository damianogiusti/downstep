/*
 * Engine tests through the C API in manual mode: no device, the test pulls the audio.
 * Plain asserts with a summary, so they run with `ctest` and need no framework.
 */
#include "Equalizer.h"
#include "Limiter.h"
#include "Loudness.h"
#include "Pcm.h"
#include "sdengine.h"

#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <filesystem>
#include <functional>
#include <new>
#include <random>
#include <string>
#include <tuple>
#include <vector>

// Counts every allocation, so a test can check that a render path never allocates.
static std::atomic<long> allocationCount{0};

void *operator new(std::size_t size) {
    allocationCount.fetch_add(1, std::memory_order_relaxed);
    if (void *p = std::malloc(size ? size : 1)) return p;
    throw std::bad_alloc();
}
void operator delete(void *p) noexcept { std::free(p); }
void operator delete(void *p, std::size_t) noexcept { std::free(p); }

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

void eqBoostsItsBandAndFlatIsTransparent() {
    const auto gainAt = [](double frequency, const std::array<double, Equalizer::kBands> &gains) {
        Equalizer eq;
        eq.configure(kRate, gains);
        std::vector<float> l(static_cast<size_t>(kRate)), r(static_cast<size_t>(kRate));
        for (int i = 0; i < kRate; ++i) l[size_t(i)] = r[size_t(i)] = float(std::sin(2 * M_PI * frequency * i / kRate));
        const double before = rms(l, kRate / 2);
        float *channels[2] = {l.data(), r.data()};
        eq.process(channels, kRate);
        return 20 * std::log10(rms(l, kRate / 2) / before);
    };
    CHECK(std::fabs(gainAt(1000, {})) < 1e-6, "flat EQ changed the signal");
    const double boost = gainAt(1000, {0, 0, 0, 6, 0, 0, 0});
    CHECK(std::fabs(boost - 6) < 0.5, "1 kHz +6 dB gave %f dB", boost);
    const double cut = gainAt(30, {-6, 0, 0, 0, 0, 0, 0});
    CHECK(std::fabs(cut + 6) < 0.7, "low shelf -6 dB at 30 Hz gave %f dB", cut);
    const double neighbour = gainAt(6000, {0, 0, 0, 6, 0, 0, 0});
    CHECK(std::fabs(neighbour) < 1.0, "1 kHz boost leaked %f dB at 6 kHz", neighbour);
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

/** `amplitude` sine at `frequency` on the left, and on the right too if `stereo`. */
PcmBuffer sine(int rate, double seconds, double frequency, double amplitude, bool stereo) {
    PcmBuffer pcm;
    pcm.sampleRate = rate;
    pcm.channels = 2;
    pcm.frames = int64_t(seconds * rate);
    pcm.data.assign(2, std::vector<float>(size_t(pcm.frames), 0.f));
    for (int64_t i = 0; i < pcm.frames; ++i) {
        const float x = float(amplitude * std::sin(2 * M_PI * frequency * double(i) / rate));
        pcm.data[0][size_t(i)] = x;
        if (stereo) pcm.data[1][size_t(i)] = x;
    }
    return pcm;
}

std::string writeTemp(const PcmBuffer &pcm, const char *name) {
    const auto path = (std::filesystem::temp_directory_path() / name).string();
    std::string error;
    if (!writeWavFloat(path, pcm, error)) std::printf("cannot write %s: %s\n", name, error.c_str());
    return path;
}

double peak(const std::vector<float> &x) {
    double p = 0;
    for (const float v : x) p = std::max(p, double(std::fabs(v)));
    return p;
}

double db(double ratio) { return 20 * std::log10(ratio); }

std::vector<float> slice(const std::vector<float> &x, size_t from, size_t to) {
    return std::vector<float>(x.begin() + long(from), x.begin() + long(to));
}

void kWeightingMatchesTheRecommendationAt48k() {
    // ITU-R BS.1770-4, tables 1 and 2.
    const auto filters = loudness::kWeighting(48000);
    const loudness::Biquad &shelf = filters[0], &highPass = filters[1];
    CHECK(std::fabs(shelf.b0 - 1.53512485958697) < 1e-6, "shelf b0 %.12f", shelf.b0);
    CHECK(std::fabs(shelf.b1 + 2.69169618940638) < 1e-6, "shelf b1 %.12f", shelf.b1);
    CHECK(std::fabs(shelf.b2 - 1.19839281085285) < 1e-6, "shelf b2 %.12f", shelf.b2);
    CHECK(std::fabs(shelf.a1 + 1.69065929318241) < 1e-6, "shelf a1 %.12f", shelf.a1);
    CHECK(std::fabs(shelf.a2 - 0.73248077421585) < 1e-6, "shelf a2 %.12f", shelf.a2);
    CHECK(highPass.b0 == 1.0 && highPass.b1 == -2.0 && highPass.b2 == 1.0, "high-pass numerator");
    CHECK(std::fabs(highPass.a1 + 1.99004745483398) < 1e-6, "high-pass a1 %.12f", highPass.a1);
    CHECK(std::fabs(highPass.a2 - 0.99007225036621) < 1e-6, "high-pass a2 %.12f", highPass.a2);
}

void loudnessOfReferenceSines() {
    // BS.1770: a 0 dBFS 1 kHz sine in one channel reads -3.01 LKFS, so -20 dBFS reads -23.01.
    for (const int rate : {44100, 48000}) {
        const double oneChannel = loudness::integratedLoudness(sine(rate, 5, 1000, 0.1, false));
        CHECK(std::fabs(oneChannel + 23.01) < 0.05, "%d Hz, one channel: %f LUFS", rate, oneChannel);
        const double bothChannels = loudness::integratedLoudness(sine(rate, 5, 1000, 0.1, true));
        CHECK(std::fabs(bothChannels + 20.0) < 0.05, "%d Hz, two channels: %f LUFS", rate, bothChannels);
    }
}

void loudnessIgnoresSilenceAndQuietPassages() {
    // 10 s at -20 dBFS, then 10 s of silence (absolute gate) and 10 s at -50 dBFS (relative gate).
    PcmBuffer pcm = sine(kRate, 30, 1000, 0.1, false);
    for (int64_t i = 10 * kRate; i < pcm.frames; ++i) pcm.data[0][size_t(i)] *= i < 20 * kRate ? 0.f : 0.0316f;
    const double lufs = loudness::integratedLoudness(pcm);
    CHECK(std::fabs(lufs + 23.01) < 0.1, "%f LUFS", lufs);

    CHECK(std::isinf(loudness::integratedLoudness(sine(kRate, 5, 1000, 0.0, true))), "silence has a loudness");
    CHECK(std::isinf(loudness::integratedLoudness(sine(kRate, 0.3, 1000, 0.1, true))), "shorter than one block");
}

void normalizationGainIsClamped() {
    CHECK(loudness::normalizationGainDb(-16) == 0, "at target");
    CHECK(loudness::normalizationGainDb(-6) == -10, "loud master");
    CHECK(loudness::normalizationGainDb(-22) == 6, "quiet master");
    CHECK(loudness::normalizationGainDb(-40) == loudness::kMaxBoostDb, "boost not clamped");
    CHECK(loudness::normalizationGainDb(10) == -loudness::kMaxCutDb, "cut not clamped");
    CHECK(loudness::normalizationGainDb(-HUGE_VAL) == 0, "silence gets a gain");
}

void measuresFiveMinutesQuickly() {
    // Noise rather than a tone, so nothing is special about the input. The time is printed for the record.
    PcmBuffer pcm = sine(kRate, 300, 1000, 0.0, true);
    std::mt19937 random(7);
    std::uniform_real_distribution<float> noise(-0.3f, 0.3f);
    for (auto &channel : pcm.data)
        for (auto &x : channel) x = noise(random);
    const auto start = std::chrono::steady_clock::now();
    const double lufs = loudness::integratedLoudness(pcm);
    const double ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - start).count();
    std::printf("  5 min of 44.1 kHz stereo measured in %.1f ms (%.1f LUFS)\n", ms, lufs);
    CHECK(std::isfinite(lufs), "no loudness");
    CHECK(ms < 2000, "took %.0f ms", ms);
}

void limiterKeepsPeaksUnderTheCeiling() {
    Limiter limiter;
    limiter.prepare(kRate);
    // 1 s of a +12 dBFS tone on the left, in 512-frame blocks. On the right, bursts of noise up to
    // +18 dBFS in the first half, then the tone 20 dB lower.
    std::vector<float> l(static_cast<size_t>(kRate)), r(static_cast<size_t>(kRate));
    std::mt19937 random(3);
    std::uniform_real_distribution<float> noise(-8.f, 8.f);
    for (int i = 0; i < kRate; ++i) {
        l[size_t(i)] = float(4 * std::sin(2 * M_PI * 441 * i / kRate));
        r[size_t(i)] = i < kRate / 2 && (i / 2000) % 3 == 0 ? noise(random) : 0.1f * l[size_t(i)];
    }
    const long before = allocationCount.load();
    for (int done = 0; done < kRate; done += 512) {
        float *block[2] = {l.data() + done, r.data() + done};
        limiter.process(block, std::min(512, kRate - done));
    }
    CHECK(allocationCount.load() == before, "process() allocated");
    CHECK(peak(l) <= Limiter::kCeiling && peak(r) <= Limiter::kCeiling, "peaks %f %f", peak(l), peak(r));
    // Once the bursts are over, the steady tone settles right under the ceiling.
    const double settled = peak(slice(l, size_t(kRate) * 3 / 4, size_t(kRate)));
    CHECK(settled > Limiter::kCeiling * 0.98, "over-limited: %f", settled);
}

void limiterNeverRoundsPastTheCeiling() {
    // Peaks where peak × (ceiling / peak) rounds to one float step above the ceiling. Each is held long
    // enough for the gain to settle on it, from the highest down, so the gain drops at once to each.
    const float peaks[] = {0x1.005f42p+0f, 0x1.005512p+0f, 0x1.005106p+0f, 0x1.0041cp+0f,
                           0x1.004102p+0f, 0x1.002b24p+0f, 0x1.001ef8p+0f, 0x1.00177ap+0f};
    Limiter limiter;
    limiter.prepare(kRate);
    std::vector<float> l, r;
    for (const float p : peaks) {
        l.insert(l.end(), 2000, p);
        r.insert(r.end(), 2000, -p);
    }
    float *block[2] = {l.data(), r.data()};
    limiter.process(block, int(l.size()));
    CHECK(peak(l) <= Limiter::kCeiling && peak(r) <= Limiter::kCeiling, "peak %.9g over %.9g", peak(l),
          double(Limiter::kCeiling));
}

void limiterLeavesQuietAudioAlone() {
    Limiter limiter;
    limiter.prepare(kRate);
    const PcmBuffer pcm = sine(kRate, 1, 441, 0.5, true);
    std::vector<float> l = pcm.data[0], r = pcm.data[1];
    float *block[2] = {l.data(), r.data()};
    limiter.process(block, int(l.size()));
    const size_t latency = size_t(limiter.latency());
    double error = 0;
    for (size_t i = latency; i < l.size(); ++i) error = std::max(error, double(std::fabs(l[i] - pcm.data[0][i - latency])));
    CHECK(error == 0, "changed a signal under the ceiling by %g", error);
}

void normalizationBringsFilesToTheTarget(const std::string &path) {
    // The test file: 441 Hz at -6 dBFS on the left only, about -9.7 LUFS.
    const auto level = [&](bool normalize) {
        SdEngine *e = openEngine(path);
        sd_engine_set_normalization(e, normalize ? 1 : 0);
        sd_engine_set_playing(e, 1);
        const Rendered r = render(e, 1.0);
        const double gainDb = sd_engine_normalization_gain_db(e);
        const double lufs = sd_engine_loudness_lufs(e);
        sd_engine_destroy(e);
        return std::make_tuple(rms(r.left, kRate / 2), gainDb, lufs);
    };
    const auto [off, gainDb, lufs] = level(false);
    const double on = std::get<0>(level(true));
    CHECK(std::fabs(lufs + 9.7) < 0.1, "measured %f LUFS", lufs);
    CHECK(std::fabs(gainDb - (-16 - lufs)) < 1e-9, "gain %f dB for %f LUFS", gainDb, lufs);
    CHECK(std::fabs(db(on / off) - gainDb) < 0.2, "played %f dB, wanted %f dB", db(on / off), gainDb);
}

void quietFileIsRaisedAndItsPeaksLimited() {
    // -40 dBFS for 5 s with a 5 ms burst at -6 dBFS: about -31 LUFS, so raised by the full 12 dB. The
    // burst would reach +6 dBFS: the limiter holds it under the ceiling.
    PcmBuffer pcm = sine(kRate, 5, 441, 0.01, true);
    for (int64_t i = 2 * kRate; i < 2 * kRate + kRate / 200; ++i)
        for (auto &channel : pcm.data) channel[size_t(i)] *= 50.f;
    const std::string quiet = writeTemp(pcm, "sdengine-quiet.wav");
    SdEngine *e = openEngine(quiet);
    CHECK(sd_engine_normalization_gain_db(e) == 12.0, "gain %f", sd_engine_normalization_gain_db(e));
    sd_engine_set_normalization(e, 1);
    sd_engine_set_playing(e, 1);
    const Rendered r = render(e, 4.0);
    CHECK(peak(r.left) <= Limiter::kCeiling && peak(r.right) <= Limiter::kCeiling, "peak %f", peak(r.left));
    const double body = rms(slice(r.left, kRate / 2, kRate));
    CHECK(std::fabs(db(body / (0.01 / std::sqrt(2.0))) - 12) < 0.2, "body raised by %f dB", db(body / (0.01 / std::sqrt(2.0))));
    sd_engine_destroy(e);
    std::filesystem::remove(quiet);
}

void silentFileGetsNoGain() {
    const std::string silent = writeTemp(sine(kRate, 2, 441, 0.0, true), "sdengine-silent.wav");
    SdEngine *e = openEngine(silent);
    CHECK(std::isinf(sd_engine_loudness_lufs(e)) && sd_engine_loudness_lufs(e) < 0, "loudness %f", sd_engine_loudness_lufs(e));
    CHECK(sd_engine_normalization_gain_db(e) == 0, "gain %f", sd_engine_normalization_gain_db(e));
    sd_engine_close(e);
    CHECK(sd_engine_normalization_gain_db(e) == 0 && std::isinf(sd_engine_loudness_lufs(e)), "kept after close");
    sd_engine_destroy(e);
    std::filesystem::remove(silent);
}

/** Largest jump between neighbouring samples: a click shows up as a step the tone never makes. */
double maxStep(const std::vector<float> &x) {
    double step = 0;
    for (size_t i = 1; i < x.size(); ++i) step = std::max(step, double(std::fabs(x[i] - x[i - 1])));
    return step;
}

/** `seconds` of output in blocks of `blockFrames`, like a device callback, left channel only. */
std::vector<float> renderBlocks(SdEngine *engine, double seconds, int blockFrames) {
    std::vector<float> left;
    for (int done = 0; done < int(seconds * kRate); done += blockFrames) {
        const Rendered r = render(engine, double(blockFrames) / kRate);
        left.insert(left.end(), r.left.begin(), r.left.end());
    }
    return left;
}

// The 441 Hz tone at -6 dBFS never steps more than 0.5 × 2π × 441 / 44100 ≈ 0.031 between samples, plus a
// little margin. The switch and the seek happen at 20 phases of the tone (its period is 100 frames), in
// 128- and 512-frame blocks: a fade spent on the limiter's empty delay lets the tone in with a jump of
// 0.1-0.25 at most of them.
constexpr double kToneStep = 0.034;

void togglingWhilePlayingDoesNotClick(const std::string &path) {
    double worst = 0;
    for (const int blockFrames : {128, 512}) {
        for (int phase = 0; phase < 20; ++phase) {
            SdEngine *e = openEngine(path);
            sd_engine_set_playing(e, 1);
            render(e, (0.5 * kRate + phase * 5) / kRate);
            std::vector<float> left = renderBlocks(e, 0.05, blockFrames);
            sd_engine_set_normalization(e, 1);
            const std::vector<float> on = renderBlocks(e, 0.3, blockFrames);
            sd_engine_set_normalization(e, 0);
            const std::vector<float> off = renderBlocks(e, 0.2, blockFrames);
            left.insert(left.end(), on.begin(), on.end());
            left.insert(left.end(), off.begin(), off.end());
            worst = std::max(worst, maxStep(left));
            if (blockFrames == 512 && phase == 0) {
                const double gain =
                    db(rms(slice(on, on.size() / 2, on.size())) / rms(slice(off, off.size() / 2, off.size())));
                CHECK(std::fabs(gain - sd_engine_normalization_gain_db(e)) < 0.2, "on/off differ by %f dB", gain);
            }
            sd_engine_destroy(e);
        }
    }
    std::printf("  largest step around a switch: %.4f\n", worst);
    CHECK(worst < kToneStep, "step of %f", worst);
}

void seekWhileNormalizedDoesNotClick(const std::string &path) {
    double worst = 0, gainDb = 0;
    for (const int blockFrames : {128, 512}) {
        for (int phase = 0; phase < 20; ++phase) {
            SdEngine *e = openEngine(path);
            sd_engine_set_normalization(e, 1);
            sd_engine_set_playing(e, 1);
            renderBlocks(e, 0.5, blockFrames);
            sd_engine_seek(e, 0.3 + phase * 5.0 / (10 * kRate));
            // From the first sample after the seek: the cut of the old audio is a known limit (README).
            worst = std::max(worst, maxStep(renderBlocks(e, 0.1, blockFrames)));
            gainDb = sd_engine_normalization_gain_db(e);
            sd_engine_destroy(e);
        }
    }
    std::printf("  largest step after a seek: %.4f\n", worst);
    // Only normalized audio here: the tone is about 6.3 dB down, so its own step is about 0.0153.
    const double toneStep = 0.0314 * std::pow(10.0, gainDb / 20);
    CHECK(worst < toneStep * 1.1, "step of %f, tone %f", worst, toneStep);
}

// Counts operator new only: a malloc inside Bungee's Eigen or miniaudio would not show here.
void normalizedRenderDoesNotCallNew(const std::string &path) {
    SdEngine *e = openEngine(path);
    sd_engine_set_normalization(e, 1);
    sd_engine_set_playing(e, 1);
    std::vector<float> buffer(512 * 2);
    for (int block = 0; block < 100; ++block) sd_engine_render(e, buffer.data(), 512); // past the first grains
    const long before = allocationCount.load();
    for (int block = 0; block < 200; ++block) sd_engine_render(e, buffer.data(), 512);
    CHECK(allocationCount.load() == before, "%ld allocations", allocationCount.load() - before);
    sd_engine_destroy(e);
}

void exportKeepsTheOriginalLevel(const std::string &path) {
    SdEngine *e = openEngine(path);
    sd_engine_set_normalization(e, 1);
    const auto output = (std::filesystem::temp_directory_path() / "sdengine-export-level.mp3").string();
    SdExport *exporter = sd_export_begin(e, output.c_str(), 192);
    CHECK(exporter != nullptr, "cannot start export");
    if (!exporter) return;
    while (sd_export_next(exporter) >= 0) {
    }
    CHECK(sd_export_end(exporter) == 1, "export incomplete");
    PcmBuffer decoded;
    std::string error;
    CHECK(decodeFile(output, decoded, error), "cannot read the MP3: %s", error.c_str());
    const double level = rms(decoded.data[0], size_t(kRate));
    CHECK(std::fabs(level - 0.5 / std::sqrt(2.0)) < 0.02, "export level %f", level);
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
        {"EQ boosts its band and flat is transparent", [] { eqBoostsItsBandAndFlatIsTransparent(); }},
        {"preamp only with the EQ on", [&] { preampOnlyWithEqOn(path); }},
        {"K-weighting matches the recommendation at 48 kHz", [] { kWeightingMatchesTheRecommendationAt48k(); }},
        {"loudness of reference sines", [] { loudnessOfReferenceSines(); }},
        {"loudness ignores silence and quiet passages", [] { loudnessIgnoresSilenceAndQuietPassages(); }},
        {"normalization gain is clamped", [] { normalizationGainIsClamped(); }},
        {"measures five minutes quickly", [] { measuresFiveMinutesQuickly(); }},
        {"limiter keeps peaks under the ceiling", [] { limiterKeepsPeaksUnderTheCeiling(); }},
        {"limiter never rounds past the ceiling", [] { limiterNeverRoundsPastTheCeiling(); }},
        {"limiter leaves quiet audio alone", [] { limiterLeavesQuietAudioAlone(); }},
        {"normalization brings files to the target", [&] { normalizationBringsFilesToTheTarget(path); }},
        {"a quiet file is raised and its peaks limited", [] { quietFileIsRaisedAndItsPeaksLimited(); }},
        {"a silent file gets no gain", [] { silentFileGetsNoGain(); }},
        {"toggling while playing does not click", [&] { togglingWhilePlayingDoesNotClick(path); }},
        {"seek while normalized does not click", [&] { seekWhileNormalizedDoesNotClick(path); }},
        {"normalized render does not call operator new", [&] { normalizedRenderDoesNotCallNew(path); }},
        {"export keeps the original level", [&] { exportKeepsTheOriginalLevel(path); }},
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
