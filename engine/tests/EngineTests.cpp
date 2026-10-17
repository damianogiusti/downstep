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

void sliceHoldsTheRangeAndNothingElse() {
    // Silence for 5 s, then a tone for 5 s: the slice 0.4..0.6 is 1 s of silence, then 1 s of tone.
    PcmBuffer pcm;
    pcm.sampleRate = kRate;
    pcm.channels = 2;
    pcm.frames = 10 * kRate;
    pcm.data.assign(2, std::vector<float>(size_t(pcm.frames), 0.f));
    for (int64_t i = 5 * kRate; i < pcm.frames; ++i) pcm.data[0][size_t(i)] = float(0.5 * std::sin(2 * M_PI * 441 * double(i) / kRate));
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
        {"exports a loop slice at the current speed", [&] { exportsALoopSliceAtTheCurrentSpeed(path); }},
        {"slice progress rises within the range", [&] { sliceProgressRisesWithinTheRange(path); }},
        {"a slice holds the range and nothing else", [] { sliceHoldsTheRangeAndNothingElse(); }},
        {"slice exports share the decoded audio", [&] { sliceExportsShareTheDecodedAudio(path); }},
        {"an empty range exports nothing", [&] { anEmptyRangeExportsNothing(path); }},
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
