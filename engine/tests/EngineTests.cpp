/*
 * Engine tests through the C API in manual mode: no device, the test pulls the audio.
 * Plain asserts with a summary, so they run with `ctest` and need no framework.
 */
#include "Denormals.h"
#include "Equalizer.h"
#include "Limiter.h"
#include "Loudness.h"
#include "Mp4EditList.h"
#include "Pcm.h"
#include "Player.h"
#include "sdengine.h"

#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <algorithm>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <functional>
#include <initializer_list>
#include <new>
#include <random>
#include <string>
#include <thread>
#include <tuple>
#include <vector>

// Allocation counter for the render path. operator new is replaced for the whole binary; with GNU
// ld malloc is wrapped too (see CMakeLists.txt), since Bungee's Eigen arrays use malloc.
namespace {
std::atomic<long> allocationCount{0}; // every allocation since the start
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
    allocationCount.fetch_add(1, std::memory_order_relaxed);
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
    CHECK(decodeFile(output, decoded, error, 0) == DecodeStatus::Ok, "cannot read the MP3: %s", error.c_str());
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

/** Hours of audio are measured on several threads, each warming its filters up: the same result as one pass. */
void loudnessOnThreadsMatchesOnePass() {
    // Noise whose level changes every few seconds, so the gates have work to do; stereo and mono.
    for (const bool stereo : {true, false}) {
        PcmBuffer pcm = sine(kRate, 600, 1000, 0.0, true);
        std::mt19937 random(11);
        std::uniform_real_distribution<float> noise(-1.f, 1.f);
        for (int64_t i = 0; i < pcm.frames; ++i) {
            const float level = 0.02f + 0.3f * float((i / (3 * kRate)) % 7) / 7.f;
            pcm.data[0][size_t(i)] = level * noise(random);
            pcm.data[1][size_t(i)] = level * noise(random);
        }
        if (!stereo) {
            pcm.channels = 1;
            pcm.data.pop_back();
        }
        const double one = loudness::integratedLoudness(pcm, 1);
        const double many = loudness::integratedLoudness(pcm, loudness::kMaxLoudnessThreads);
        CHECK(std::isfinite(one) && std::fabs(one - many) < 1e-3, "%s: one pass %f LUFS, threads %f",
              stereo ? "stereo" : "mono", one, many);
    }
    CHECK(loudness::loudnessThreads(sine(kRate, 50, 1000, 0.1, true)) == 1, "threads for 50 s");
    CHECK(loudness::loudnessThreads(sine(kRate, 600, 1000, 0.1, true)) ==
              int(std::min<unsigned>(std::max(std::thread::hardware_concurrency(), 1u), unsigned(loudness::kMaxLoudnessThreads))),
          "threads for 10 min: %d", loudness::loudnessThreads(sine(kRate, 600, 1000, 0.1, true)));
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
    CHECK(decodeFile(output, decoded, error, 0) == DecodeStatus::Ok, "cannot read the MP3: %s", error.c_str());
    const double level = rms(decoded.data[0], size_t(kRate));
    CHECK(std::fabs(level - 0.5 / std::sqrt(2.0)) < 0.02, "export level %f", level);
    std::filesystem::remove(output);
    sd_engine_destroy(e);
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
        CHECK(decodeFile(path, decoded, error, 0) == DecodeStatus::Ok, "cannot decode: %s", error.c_str());
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

/** A 16-bit PCM WAV of [pcm] (rounded), announcing [announcedFrames] frames (default: all of them). */
void writeWav16(const std::string &path, const PcmBuffer &pcm, int64_t announcedFrames = -1) {
    FILE *f = openFileUtf8(path, "wb");
    if (!f) return;
    auto u32 = [&](uint32_t v) { std::fwrite(&v, 4, 1, f); };
    auto u16 = [&](uint16_t v) { std::fwrite(&v, 2, 1, f); };
    const int64_t announced = announcedFrames < 0 ? pcm.frames : announcedFrames;
    const auto dataBytes = uint32_t(announced * pcm.channels * 2);
    std::fwrite("RIFF", 1, 4, f);
    u32(36 + dataBytes);
    std::fwrite("WAVEfmt ", 1, 8, f);
    u32(16);
    u16(1); // integer PCM
    u16(uint16_t(pcm.channels));
    u32(uint32_t(pcm.sampleRate));
    u32(uint32_t(pcm.sampleRate * pcm.channels * 2));
    u16(uint16_t(pcm.channels * 2));
    u16(16);
    std::fwrite("data", 1, 4, f);
    u32(dataBytes);
    for (int64_t i = 0; i < pcm.frames; ++i)
        for (int c = 0; c < pcm.channels; ++c) u16(uint16_t(int16_t(std::lround(pcm.data[size_t(c)][size_t(i)] * 32767.0))));
    std::fclose(f);
}

/**
 * Long PCM files are read in slices on several threads: every frame lands where it belongs, also at the
 * seams between slices, in 16-bit as in float (decoderKeepsEveryFrame).
 */
void decoderReadsLongPcmInSlicesExactly() {
    for (const auto &[channels, seconds] : {std::pair{2, 97.31}, std::pair{1, 61.7}}) {
        const PcmBuffer written = rampBuffer(channels, seconds);
        const auto path = tempPath("sdengine-ramp16.wav");
        writeWav16(path, written);
        PcmBuffer decoded;
        std::string error;
        CHECK(decodeFile(path, decoded, error, 0) == DecodeStatus::Ok, "cannot decode: %s", error.c_str());
        std::filesystem::remove(path);
        CHECK(decoded.frames == written.frames, "%lld frames, wrote %lld", (long long)decoded.frames,
              (long long)written.frames);
        if (decoded.channels != channels || decoded.frames != written.frames) continue;
        for (int c = 0; c < channels; ++c) {
            int64_t wrong = 0;
            for (int64_t i = 0; i < written.frames; ++i) {
                const float expected = float(int16_t(std::lround(written.data[size_t(c)][size_t(i)] * 32767.0))) / 32768.f;
                if (decoded.data[size_t(c)][size_t(i)] != expected) ++wrong;
            }
            CHECK(wrong == 0, "channel %d: %lld frames differ", c, (long long)wrong);
        }
    }
}

/** A WAV cut short of the length its header announces: the frames that are there, as before. */
void aCutShortPcmFileKeepsTheFramesItHas() {
    const PcmBuffer written = rampBuffer(2, 60.0);
    const auto path = tempPath("sdengine-cut16.wav");
    writeWav16(path, written, written.frames * 2);
    PcmBuffer decoded;
    std::string error;
    const DecodeStatus status = decodeFile(path, decoded, error, 0);
    std::filesystem::remove(path);
    CHECK(status == DecodeStatus::Ok, "status %d: %s", int(status), error.c_str());
    CHECK(decoded.frames == written.frames, "%lld frames, wrote %lld", (long long)decoded.frames,
          (long long)written.frames);
}

void unreadableFileFailsCleanly() {
    // Not audio at all.
    const auto path = tempPath("sdengine-not-audio.m4a");
    if (FILE *f = openFileUtf8(path, "wb")) {
        std::fputs("this is not an audio file, just some text that no decoder should accept", f);
        std::fclose(f);
    }
    PcmBuffer decoded;
    std::string error;
    CHECK(decodeFile(path, decoded, error, 0) == DecodeStatus::Failed, "decoded a text file");
    CHECK(!error.empty(), "no error message");
    SdEngine *e = sd_engine_create(0, kRate);
    CHECK(sd_engine_open(e, path.c_str()) == 0 && !sd_engine_is_open(e), "opened a text file");
    CHECK(sd_engine_open_error(e) == SD_OPEN_FAILED, "error code %d", sd_engine_open_error(e));
    char text[256];
    const int length = sd_engine_open_error_text(e, text, int(sizeof(text)));
    CHECK(length > 0 && std::string(text).find(path) == std::string::npos, "error text: %s", text);
    sd_engine_destroy(e);
    std::filesystem::remove(path);
}

void tooLongForTheBudgetIsReportedWithoutDecoding() {
    const PcmBuffer written = rampBuffer(2, 5.0);
    const auto path = tempPath("sdengine-budget.wav");
    std::string error;
    CHECK(writeWavFloat(path, written, error), "cannot write: %s", error.c_str());
    SdEngine *e = sd_engine_create(0, kRate);
    sd_engine_set_memory_budget(e, 1000);
    CHECK(sd_engine_open(e, path.c_str()) == 0, "opened over the budget");
    CHECK(sd_engine_open_error(e) == SD_OPEN_TOO_LONG, "error code %d", sd_engine_open_error(e));
    sd_engine_set_memory_budget(e, 0);
    CHECK(sd_engine_open(e, path.c_str()) == 1, "did not open with the automatic budget");
    CHECK(sd_engine_open_error(e) == SD_OPEN_OK, "error code %d", sd_engine_open_error(e));
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

/** One channel in memory, two on the speakers: the measure counts it on both, as for the same file in stereo. */
void monoLoudnessEqualsTheSameFileInStereo() {
    PcmBuffer mono;
    mono.sampleRate = kRate;
    mono.channels = 1;
    mono.frames = 3 * kRate;
    mono.data.assign(1, std::vector<float>(size_t(mono.frames)));
    for (int64_t i = 0; i < mono.frames; ++i) mono.data[0][size_t(i)] = float(0.3 * std::sin(2 * M_PI * 441 * double(i) / kRate));
    PcmBuffer stereo = mono;
    stereo.channels = 2;
    stereo.data.push_back(mono.data[0]);
    const double a = loudness::integratedLoudness(mono), b = loudness::integratedLoudness(stereo);
    CHECK(std::fabs(a - b) < 1e-9, "mono %f LUFS, stereo %f", a, b);
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
    CHECK(decodeFile(output, decoded, error, 0) == DecodeStatus::Ok, "cannot read the MP3: %s", error.c_str());
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
            sd_engine_set_eq_band(e, i % 7, (i % 25) - 12);
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

using Bytes = std::vector<uint8_t>;

Bytes u32(uint32_t v) { return {uint8_t(v >> 24), uint8_t(v >> 16), uint8_t(v >> 8), uint8_t(v)}; }

Bytes u64(uint64_t v) {
    Bytes high = u32(uint32_t(v >> 32));
    const Bytes low = u32(uint32_t(v));
    high.insert(high.end(), low.begin(), low.end());
    return high;
}

Bytes join(std::initializer_list<Bytes> parts) {
    Bytes all;
    for (const auto &part : parts) all.insert(all.end(), part.begin(), part.end());
    return all;
}

Bytes box(const char *type, const Bytes &payload) {
    return join({u32(uint32_t(8 + payload.size())), Bytes(type, type + 4), payload});
}

Bytes elst0(int32_t mediaTime) { return box("elst", join({u32(0), u32(1), u32(133324), u32(uint32_t(mediaTime)), u32(0x00010000)})); }

Bytes soundTrack(const Bytes &edts, const char *handler = "soun") {
    const Bytes mdhd = box("mdhd", join({u32(0), u32(0), u32(0), u32(44100), u32(133324), u32(0)}));
    const Bytes hdlr = box("hdlr", join({u32(0), u32(0), Bytes(handler, handler + 4), u32(0), u32(0), u32(0)}));
    return box("trak", join({box("tkhd", Bytes(84)), edts, box("mdia", join({mdhd, hdlr}))}));
}

/** The layout of YouTube's itag 140, without the audio: ftyp, moov with mvex and an edited track, a fragment. */
Bytes youTubeLike(const Bytes &tracks, bool isFragmented = true) {
    const Bytes mvex = isFragmented ? box("mvex", box("trex", Bytes(24))) : Bytes{};
    return join({box("ftyp", Bytes(16)), box("moov", join({box("mvhd", Bytes(100)), tracks, mvex})),
                 box("moof", Bytes(40)), box("mdat", Bytes(64))});
}

void anEditListPastZeroIsFoundInAFragmentedMp4() {
    const Bytes file = youTubeLike(soundTrack(box("edts", elst0(1600))));
    const auto edit = findFragmentedEditList(file.data(), file.size());
    CHECK(edit && edit->mediaTime == 1600 && edit->timescale == 44100, "not found or wrong values");
    if (edit) CHECK(std::memcmp(file.data() + edit->edtsOffset + 4, "edts", 4) == 0, "offset %zu", edit->edtsOffset);

    const Bytes version1 = youTubeLike(soundTrack(box("edts", box("elst", join({u32(0x01000000), u32(1), u64(133324), u64(2112), u32(0x00010000)})))));
    const auto wide = findFragmentedEditList(version1.data(), version1.size());
    CHECK(wide && wide->mediaTime == 2112, "64-bit edit list not read");

    // A video track first (itag 18 has one): only the sound track counts.
    const Bytes muxed = youTubeLike(join({soundTrack(box("edts", elst0(512)), "vide"), soundTrack(box("edts", elst0(1600)))}));
    const auto sound = findFragmentedEditList(muxed.data(), muxed.size());
    CHECK(sound && sound->mediaTime == 1600, "took the video track's edit");
}

void otherMp4sHaveNoEditListToHide() {
    for (const auto &[name, file] : {
             std::pair{"plain MP4", youTubeLike(soundTrack(box("edts", elst0(1600))), false)},
             std::pair{"edit at 0", youTubeLike(soundTrack(box("edts", elst0(0))))},
             std::pair{"empty edit", youTubeLike(soundTrack(box("edts", elst0(-1))))},
             std::pair{"no edit list", youTubeLike(soundTrack(Bytes{}))},
             std::pair{"video only", youTubeLike(soundTrack(box("edts", elst0(1600)), "vide"))},
         }) {
        CHECK(!findFragmentedEditList(file.data(), file.size()), "%s: found an edit list", name);
    }
}

void aCutOrCorruptMp4IsReadWithinItsBytes() {
    const Bytes file = youTubeLike(soundTrack(box("edts", elst0(1600))));
    // A file cut anywhere, as a download that stopped: an edit list is found only if all of it is there.
    for (size_t size = 0; size <= file.size(); ++size) {
        const Bytes cut(file.begin(), file.begin() + std::ptrdiff_t(size));
        const auto edit = findFragmentedEditList(cut.data(), cut.size());
        CHECK(!edit || edit->edtsOffset + 8 <= size, "cut at %zu: offset %zu", size, edit->edtsOffset);
    }
    Bytes corrupt = file;
    const size_t moov = 24; // after the 24-byte ftyp
    CHECK(std::memcmp(corrupt.data() + moov + 4, "moov", 4) == 0, "no moov at %zu", moov);
    corrupt[moov + 2] = 0xff; // its size, far past the end
    CHECK(!findFragmentedEditList(corrupt.data(), corrupt.size()), "read a moov larger than the file");
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

/** Writes `pcm` (planar) through the stem writer in blocks of `blockFrames`, like the pipeline will. */
bool writeStem(const std::string &path, const PcmBuffer &pcm, int bitrateKbps, int64_t blockFrames) {
    int status = -1;
    SdStemWriter *writer = sd_stem_writer_open(path.c_str(), pcm.sampleRate, pcm.channels, bitrateKbps, &status);
    if (!writer) {
        std::printf("  cannot open the stem writer: status %d\n", status);
        return false;
    }
    std::vector<float> block(size_t(blockFrames) * size_t(pcm.channels));
    bool isOk = true;
    for (int64_t start = 0; start < pcm.frames && isOk; start += blockFrames) {
        const int64_t n = std::min(blockFrames, pcm.frames - start);
        for (int64_t i = 0; i < n; ++i)
            for (int c = 0; c < pcm.channels; ++c) block[size_t(i * pcm.channels + c)] = pcm.data[size_t(c)][size_t(start + i)];
        isOk = sd_stem_writer_write(writer, block.data(), n) == SD_STEM_OK;
    }
    return sd_stem_writer_close(writer) == 1 && isOk;
}

/**
 * The format decision for stems: an MP3 written by the stem writer must decode, with the platform's own
 * decoder, to exactly the frames written and with the audio at the same place. Without the Xing/LAME tag
 * AudioToolbox returns 2560 extra frames and everything 1728 samples late. If this fails on a platform,
 * stems on it must be written as WAV.
 */
void stemMp3DecodesToTheSameFramesAtTheSamePlace() {
    constexpr int64_t kFrames = 20 * int64_t(kRate) + 777; // not a multiple of 1152
    PcmBuffer pcm;
    pcm.sampleRate = kRate;
    pcm.channels = 2;
    pcm.frames = kFrames;
    pcm.data.assign(2, std::vector<float>(size_t(kFrames), 0.f));
    const int64_t clicks[] = {kRate, 5 * int64_t(kRate) + 123, 12 * int64_t(kRate) + 1000, kFrames - 5000};
    for (const int64_t click : clicks) pcm.data[0][size_t(click)] = pcm.data[1][size_t(click)] = 0.8f;

    const auto path = tempPath("sdengine-stem-align.mp3");
    CHECK(writeStem(path, pcm, 192, 5000), "cannot write the stem");
    PcmBuffer decoded;
    std::string error;
    CHECK(decodeFile(path, decoded, error, 0) == DecodeStatus::Ok, "cannot decode: %s", error.c_str());
    std::filesystem::remove(path);
    std::printf("     decoded %lld frames, wrote %lld\n", (long long)decoded.frames, (long long)kFrames);
    CHECK(decoded.channels == 2 && decoded.sampleRate == kRate, "%d channels at %d Hz", decoded.channels, decoded.sampleRate);
    CHECK(decoded.frames == kFrames,
          "the decoder returned %lld frames for %lld written: it does not honour the Xing/LAME tag, so stems on "
          "this platform must be WAV",
          (long long)decoded.frames, (long long)kFrames);
    if (decoded.channels != 2) return;
    for (const int64_t click : clicks) {
        // The loudest sample within 300 frames of where the click was written.
        int64_t peakAt = -1;
        float peak = 0.f;
        for (int64_t i = std::max<int64_t>(0, click - 300); i < std::min(decoded.frames, click + 300); ++i) {
            const float value = std::fabs(decoded.data[0][size_t(i)]);
            if (value > peak) {
                peak = value;
                peakAt = i;
            }
        }
        std::printf("     click at %lld: peak %.3f at offset %lld\n", (long long)click, peak, (long long)(peakAt - click));
        CHECK(peak > 0.3f, "no click found near %lld", (long long)click);
        CHECK(peakAt == click, "the click at %lld came back at offset %lld: the decoded audio is not aligned",
              (long long)click, (long long)(peakAt - click));
    }
}

void stemWriterTakesMonoAnyRateAndAnyBlockSize() {
    PcmBuffer pcm;
    pcm.sampleRate = 48000;
    pcm.channels = 1;
    pcm.frames = 3 * 48000 + 101;
    pcm.data.assign(1, std::vector<float>(size_t(pcm.frames)));
    for (int64_t i = 0; i < pcm.frames; ++i) pcm.data[0][size_t(i)] = float(0.4 * std::sin(2 * M_PI * 440 * double(i) / 48000));
    const auto path = tempPath("sdengine-stem-mono.mp3");
    // A block bigger than the writer's internal chunk, and one that is not a divisor of the length.
    CHECK(writeStem(path, pcm, 128, 40000), "cannot write the stem");
    PcmBuffer decoded;
    std::string error;
    CHECK(decodeFile(path, decoded, error, 0) == DecodeStatus::Ok, "cannot decode: %s", error.c_str());
    std::filesystem::remove(path);
    CHECK(decoded.channels == 1 && decoded.sampleRate == 48000, "%d channels at %d Hz", decoded.channels, decoded.sampleRate);
    CHECK(decoded.frames == pcm.frames, "%lld frames, wrote %lld", (long long)decoded.frames, (long long)pcm.frames);
}

void stemWriterReportsItsErrorsAsStatusCodes() {
    int status = -1;
    const auto path = tempPath("sdengine-stem-bad.mp3");
    CHECK(sd_stem_writer_open(path.c_str(), 44100, 3, 192, &status) == nullptr && status == SD_STEM_BAD_FORMAT, "3 channels: %d", status);
    CHECK(sd_stem_writer_open(path.c_str(), 44100, 0, 192, &status) == nullptr && status == SD_STEM_BAD_FORMAT, "0 channels: %d", status);
    CHECK(sd_stem_writer_open(path.c_str(), 96000, 2, 192, &status) == nullptr && status == SD_STEM_BAD_FORMAT, "96 kHz: %d", status);
    CHECK(sd_stem_writer_open(path.c_str(), 44100, 2, 0, &status) == nullptr && status == SD_STEM_BAD_FORMAT, "0 kbps: %d", status);
    CHECK(!std::filesystem::exists(path), "a rejected format created a file");
    const auto missing = (std::filesystem::temp_directory_path() / "sdengine-no-such-folder" / "stem.mp3").string();
    CHECK(sd_stem_writer_open(missing.c_str(), 44100, 2, 192, &status) == nullptr && status == SD_STEM_OPEN_FAILED, "no folder: %d", status);
    CHECK(sd_stem_writer_open(missing.c_str(), 44100, 2, 192, nullptr) == nullptr, "a NULL status crashed or opened");
    // Nothing written: closing still gives a valid, empty file and reports it complete.
    SdStemWriter *writer = sd_stem_writer_open(path.c_str(), 44100, 2, 192, &status);
    CHECK(writer != nullptr && status == SD_STEM_OK, "open: %d", status);
    if (writer) {
        const float silence[8] = {};
        CHECK(sd_stem_writer_write(writer, silence, 0) == SD_STEM_OK, "zero frames");
        CHECK(sd_stem_writer_close(writer) == 1, "an empty stem did not close");
    }
    std::filesystem::remove(path);
}

void pcmDecodeGivesTheFramesAndCopiesRanges() {
    for (const int channels : {2, 1}) {
        const PcmBuffer written = rampBuffer(channels, 3.3);
        const auto path = tempPath("sdengine-pcm-api.wav");
        std::string error;
        CHECK(writeWavFloat(path, written, error), "cannot write: %s", error.c_str());
        int status = -1;
        SdPcm *pcm = sd_pcm_decode(path.c_str(), 0, &status);
        std::filesystem::remove(path);
        CHECK(pcm != nullptr && status == SD_PCM_OK, "status %d", status);
        if (!pcm) continue;
        CHECK(sd_pcm_sample_rate(pcm) == kRate, "rate %d", sd_pcm_sample_rate(pcm));
        CHECK(sd_pcm_channels(pcm) == channels, "%d channels", sd_pcm_channels(pcm));
        CHECK(sd_pcm_frames(pcm) == written.frames, "%lld frames", (long long)sd_pcm_frames(pcm));
        std::vector<float> out(1000, -9.f);
        for (int c = 0; c < channels; ++c) {
            CHECK(sd_pcm_copy(pcm, c, 777, out.data(), 1000) == 1000, "copy of channel %d", c);
            CHECK(std::equal(out.begin(), out.end(), written.data[size_t(c)].begin() + 777), "channel %d differs", c);
        }
        // Past the last channel reads the last one: how a mono file gives both sides.
        CHECK(sd_pcm_copy(pcm, 1, 0, out.data(), 1000) == 1000, "channel 1");
        CHECK(std::equal(out.begin(), out.end(), written.data[size_t(channels - 1)].begin()), "channel 1 is not the last channel");
        // The end of the audio: fewer than asked, and nothing written past what is returned.
        std::fill(out.begin(), out.end(), -9.f);
        CHECK(sd_pcm_copy(pcm, 0, written.frames - 10, out.data(), 1000) == 10, "tail");
        CHECK(out[9] == written.data[0].back() && out[10] == -9.f, "tail content");
        CHECK(sd_pcm_copy(pcm, 0, written.frames, out.data(), 10) == 0, "at the end");
        CHECK(sd_pcm_copy(pcm, 0, -1, out.data(), 10) == 0, "negative start");
        CHECK(sd_pcm_copy(pcm, -1, 0, out.data(), 10) == 0, "negative channel");
        CHECK(sd_pcm_copy(pcm, 0, 0, out.data(), 0) == 0, "no frames");
        sd_pcm_free(pcm);
    }
    sd_pcm_free(nullptr);
}

void pcmDecodeReportsFailuresAsStatusCodes() {
    const auto text = tempPath("sdengine-pcm-not-audio.m4a");
    if (FILE *f = openFileUtf8(text, "wb")) {
        std::fputs("this is not an audio file, just some text that no decoder should accept", f);
        std::fclose(f);
    }
    int status = -1;
    CHECK(sd_pcm_decode(text.c_str(), 0, &status) == nullptr && status == SD_PCM_FAILED, "text file: status %d", status);
    CHECK(sd_pcm_decode(text.c_str(), 0, nullptr) == nullptr, "a NULL status crashed or decoded");
    std::filesystem::remove(text);
    CHECK(sd_pcm_decode(text.c_str(), 0, &status) == nullptr && status == SD_PCM_FAILED, "missing file: status %d", status);

    const auto wav = tempPath("sdengine-pcm-budget.wav");
    std::string error;
    CHECK(writeWavFloat(wav, rampBuffer(2, 2.0), error), "cannot write: %s", error.c_str());
    CHECK(sd_pcm_decode(wav.c_str(), 1000, &status) == nullptr && status == SD_PCM_TOO_LONG, "budget: status %d", status);
    std::filesystem::remove(wav);
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
        {"too long for the budget is reported", [] { tooLongForTheBudgetIsReportedWithoutDecoding(); }},
        {"mix fades the far side", [&] { mixFadesTheFarSide(path); }},
        {"EQ boosts its band and flat is transparent", [] { eqBoostsItsBandAndFlatIsTransparent(); }},
        {"preamp only with the EQ on", [&] { preampOnlyWithEqOn(path); }},
        {"K-weighting matches the recommendation at 48 kHz", [] { kWeightingMatchesTheRecommendationAt48k(); }},
        {"loudness of reference sines", [] { loudnessOfReferenceSines(); }},
        {"loudness ignores silence and quiet passages", [] { loudnessIgnoresSilenceAndQuietPassages(); }},
        {"normalization gain is clamped", [] { normalizationGainIsClamped(); }},
        {"measures five minutes quickly", [] { measuresFiveMinutesQuickly(); }},
        {"loudness on threads matches one pass", [] { loudnessOnThreadsMatchesOnePass(); }},
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
        {"decoder keeps every frame", [] { decoderKeepsEveryFrame(); }},
#ifdef __APPLE__
        // Only the AudioToolbox decoder reads PCM in slices.
        {"decoder reads long PCM in slices exactly", [] { decoderReadsLongPcmInSlicesExactly(); }},
        {"a cut-short PCM file keeps the frames it has", [] { aCutShortPcmFileKeepsTheFramesItHas(); }},
#endif
        {"an unreadable file fails cleanly", [] { unreadableFileFailsCleanly(); }},
        {"mono is kept as one channel and plays on both sides", [] { monoIsKeptAsOneChannelAndPlaysOnBothSides(); }},
        {"mono loudness equals the same file in stereo", [] { monoLoudnessEqualsTheSameFileInStereo(); }},
        {"export shares the decoded audio", [&] { exportSharesTheDecodedAudio(path); }},
        {"flushes denormals in scope", [] { flushesDenormalsInScope(); }},
        {"rendering does not allocate", [&] { renderingDoesNotAllocate(path); }},
        {"a corrupt length reserves only what the file can hold", [] { aCorruptLengthReservesOnlyWhatTheFileCanHold(); }},
        {"an edit list past zero is found in a fragmented MP4", [] { anEditListPastZeroIsFoundInAFragmentedMp4(); }},
        {"other MP4s have no edit list to hide", [] { otherMp4sHaveNoEditListToHide(); }},
        {"a cut or corrupt MP4 is read within its bytes", [] { aCutOrCorruptMp4IsReadWithinItsBytes(); }},
        {"export begins while another file opens", [&] { exportBeginsWhileAnotherFileOpens(path); }},
        {"peaks outline the open file", [] { peaksOutlineTheOpenFile(); }},
        {"stem MP3 decodes to the same frames at the same place", [] { stemMp3DecodesToTheSameFramesAtTheSamePlace(); }},
        {"stem writer takes mono, any rate and any block size", [] { stemWriterTakesMonoAnyRateAndAnyBlockSize(); }},
        {"stem writer reports its errors as status codes", [] { stemWriterReportsItsErrorsAsStatusCodes(); }},
        {"pcm decode gives the frames and copies ranges", [] { pcmDecodeGivesTheFramesAndCopiesRanges(); }},
        {"pcm decode reports failures as status codes", [] { pcmDecodeReportsFailuresAsStatusCodes(); }},
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
