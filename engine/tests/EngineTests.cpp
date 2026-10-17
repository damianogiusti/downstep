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
            sd_engine_set_eq_band(e, i % 7, (i % 25) - 12);
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
    for (auto *pcm : {&noise}) {
        SdEngine *e = openInMemory(*pcm);
        double bpm = 0, offset = 0, confidence = 0;
        CHECK(sd_engine_detect_tempo(e, &bpm, &offset, &confidence) == 0, "noise: %.2f BPM (confidence %.3f)", bpm,
              confidence);
        CHECK(bpm == 0 && offset == 0 && confidence == 0, "outputs not cleared");
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
        {"decoder keeps every frame", [] { decoderKeepsEveryFrame(); }},
        {"an unreadable file fails cleanly", [] { unreadableFileFailsCleanly(); }},
        {"mono is kept as one channel and plays on both sides", [] { monoIsKeptAsOneChannelAndPlaysOnBothSides(); }},
        {"export shares the decoded audio", [&] { exportSharesTheDecodedAudio(path); }},
        {"flushes denormals in scope", [] { flushesDenormalsInScope(); }},
        {"rendering does not allocate", [&] { renderingDoesNotAllocate(path); }},
        {"peaks outline the open file", [] { peaksOutlineTheOpenFile(); }},
        {"a corrupt length reserves only what the file can hold", [] { aCorruptLengthReservesOnlyWhatTheFileCanHold(); }},
        {"export begins while another file opens", [&] { exportBeginsWhileAnotherFileOpens(path); }},
        {"metronome clicks on the beat at any speed", [] { metronomeClicksOnTheBeatAtAnySpeed(); }},
        {"metronome lines up with the music", [] { metronomeLinesUpWithTheMusic(); }},
        {"metronome follows the loop wrap", [] { metronomeFollowsTheLoopWrap(); }},
        {"count-in starts the music on the beat", [] { countInStartsTheMusicOnTheBeat(); }},
        {"count-in every loop leaves a gap at each wrap", [] { countInEveryLoopLeavesAGapAtEachWrap(); }},
        {"detects the tempo of a click track", [] { detectsTheTempoOfAClickTrack(); }},
        {"no tempo in silence or noise", [] { noTempoInSilenceOrNoise(); }},
        {"export has no clicks", [] { exportHasNoClicks(); }},
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
