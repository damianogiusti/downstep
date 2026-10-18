/*
 * SlowDowner audio engine: C API for the JVM (FFM) and, later, Kotlin/Native (cinterop).
 *
 * Threading: controls come from one control thread at a time. Getters (position, playing, events)
 * may also be read from a polling thread. With an output device the audio callback runs on its own
 * thread; controls reach it through atomics and never block it.
 * Positions are fractions 0..1 of the whole file, as in the Kotlin `PlaybackEngine`.
 */
#ifndef SDENGINE_H
#define SDENGINE_H

#ifdef __cplusplus
extern "C" {
#endif

#if defined(_WIN32)
#define SD_API __declspec(dllexport)
#else
#define SD_API __attribute__((visibility("default")))
#endif

typedef struct SdEngine SdEngine;

/** Playback reached the end of the file with the loop off. Playback is already stopped. */
#define SD_EVENT_REACHED_END 1u

/** How many bells the EQ has room for. */
#define SD_EQ_MAX_BANDS 8

/**
 * `withDevice` = 1 plays on the default output device. 0 = manual mode: nothing plays, the caller
 * pulls audio with `sd_engine_render` at `manualSampleRate` (offline export, tests).
 * Returns NULL if the device cannot be opened.
 */
SD_API SdEngine *sd_engine_create(int withDevice, int manualSampleRate);
SD_API void sd_engine_destroy(SdEngine *engine);

/** Decodes the whole file into memory. Blocking: call it off the UI thread. 1 = ok, 0 = failed. */
SD_API int sd_engine_open(SdEngine *engine, const char *utf8Path);
SD_API void sd_engine_close(SdEngine *engine);
SD_API int sd_engine_is_open(const SdEngine *engine);
SD_API double sd_engine_duration_seconds(const SdEngine *engine);

/** Play with a short fade in, or stop with a short fade out. The position is kept. */
SD_API void sd_engine_set_playing(SdEngine *engine, int playing);
SD_API int sd_engine_is_playing(const SdEngine *engine);
SD_API double sd_engine_position(const SdEngine *engine);
SD_API void sd_engine_seek(SdEngine *engine, double position);

/** 1.0 = original speed, 0.5 = half speed. */
SD_API void sd_engine_set_speed(SdEngine *engine, double ratio);
SD_API void sd_engine_set_pitch(SdEngine *engine, double semitones);
/** Points are kept while the loop is off; playback is bounded by them only when it is on. */
SD_API void sd_engine_set_loop(SdEngine *engine, double start, double end, int enabled);
/** Same, and moves playback to `position` in the same audio block: no block plays with only half applied. */
SD_API void sd_engine_set_loop_and_seek(SdEngine *engine, double start, double end, int enabled, double position);

/** 0 = left channel on both outputs, 0.5 = unchanged, 1 = right channel on both. */
SD_API void sd_engine_set_mix(SdEngine *engine, double mixRight);
/**
 * The EQ: up to SD_EQ_MAX_BANDS peaking (bell) filters, then the preamp. Every change glides over
 * ~20 ms (each band's frequency, gain and Q, the preamp, on and off): no zipper noise, no clicks.
 */
SD_API void sd_engine_set_eq_enabled(SdEngine *engine, int enabled);
/**
 * Band `band` (0..SD_EQ_MAX_BANDS-1): a bell at frequencyHz (20..20k, kept below 0.45 × the output
 * rate), gainDb (±12) and q (0.3..10), the three taking effect together.
 */
SD_API void sd_engine_set_eq_band_params(SdEngine *engine, int band, double frequencyHz, double gainDb, double q);
/** How many bands are in use (0..SD_EQ_MAX_BANDS), from band 0; the others fade to 0 dB. */
SD_API void sd_engine_set_eq_band_count(SdEngine *engine, int count);
/** Applied only while the EQ is on. */
SD_API void sd_engine_set_preamp(SdEngine *engine, double gainDb);
/**
 * Isolate: brings the selected range forward by cutting the rest 12 dB, after the EQ and whether it
 * is on or off. `mask`: bit 0 low, bit 1 mid, bit 2 high; 0 = the full mix. A practice aid: an
 * export never carries it.
 */
SD_API void sd_engine_set_isolation(SdEngine *engine, int mask);
/** Linear output gain, 1 by default. 0 mutes a device engine (scripted checks). */
SD_API void sd_engine_set_output_gain(SdEngine *engine, double gain);

/**
 * The open file's beat grid at 100% speed: beats at beatOffsetSeconds + k·60/bpm seconds of the file,
 * the first of every `beatsPerBar` accented. bpm <= 0 = no tempo: no metronome clicks, and the
 * count-in counts at 120 BPM. The engine follows the speed and the loop itself; the grid stays
 * across sd_engine_open (set it for each track).
 */
SD_API void sd_engine_set_tempo(SdEngine *engine, double bpm, double beatOffsetSeconds, int beatsPerBar);
/**
 * A click on every beat of the grid while playing, mixed after the time stretch, EQ, Isolate and fades: it is
 * never stretched, pitched or equalised, and follows loop wraps and seeks. `volume` 0..1 is the
 * click's own level (the count-in's too); the output gain still applies.
 */
SD_API void sd_engine_set_metronome(SdEngine *engine, int enabled, double volume);
/**
 * `beats` > 0: that many clicks at the effective tempo (bpm × speed) before playback starts, the
 * music starting from the requested position one beat after the last click; 0 = off. `everyLoop` = 1:
 * also at each loop restart (playback stops at the loop end, counts in, and goes on from the loop
 * start; the position stays at the loop start meanwhile, and there is no SD_EVENT_REACHED_END). A
 * speed change while counting in re-times the clicks still to come, and the music after them.
 */
SD_API void sd_engine_set_count_in(SdEngine *engine, int beats, int everyLoop);
/** Count-in clicks still to play; 0 when not counting in. For the UI, from any thread. */
SD_API int sd_engine_count_in_remaining(const SdEngine *engine);
/**
 * Estimates the open file's tempo from its onsets (see TempoDetector.h), over the audio already
 * decoded. Blocking (a fraction of a second for a song): call it off the UI thread. `*bpm` on entry:
 * 0 to detect the tempo, or a tempo to keep, of which only the phase is found. Writes the bpm, the
 * first beat in seconds (0 <= offset < 60/bpm) and a confidence 0..1. Returns 1 when found, 0 when
 * nothing is open or no steady pulse was found (the outputs are then 0).
 */
SD_API int sd_engine_detect_tempo(SdEngine *engine, double *bpm, double *beatOffsetSeconds, double *confidence);

/**
 * Waveform of the open file for drawing: fills `buckets` values 0..1, the peak level of each equal
 * slice of from..to (fractions 0..1 of the file). Cheap (read from a summary made at open) and safe
 * from any thread. Returns the number of values written: `buckets`, or 0 when no file is open.
 */
SD_API int sd_engine_peaks(SdEngine *engine, double from, double to, float *out, int buckets);

/** Returns and clears the SD_EVENT_* bits raised since the previous call. */
SD_API unsigned sd_engine_take_events(SdEngine *engine);

/** Output sample rate: the device rate, or `manualSampleRate`. Output is always stereo. */
SD_API int sd_engine_sample_rate(const SdEngine *engine);
/** Manual mode only: fills `frames` interleaved stereo frames. Returns 0 on a device engine. */
SD_API int sd_engine_render(SdEngine *engine, float *interleaved, int frames);

typedef struct SdExport SdExport;

/**
 * Starts an MP3 export of the open file with the engine's current sound (speed, pitch, mix, EQ),
 * from the start to the end, loop ignored. It renders in its own engine over the audio already
 * decoded (shared, not decoded again): playback is not disturbed, and closing or replacing the
 * open file meanwhile does not affect the export. NULL if no file is open or the output cannot be
 * written.
 */
SD_API SdExport *sd_export_begin(SdEngine *engine, const char *utf8OutputPath, int bitrateKbps);
/**
 * As sd_export_begin, but only `start`..`end` (fractions 0..1 of the file, end > start): a loop's
 * slice. The file lasts (end - start) × duration / speed, starts with the usual short fade-in and
 * ends with a short fade-out. sd_export_begin is the range 0..1. Each call shares the same decoded
 * audio, so a batch of slices never decodes again. Exports carry only the sound settings: never the
 * loop, the metronome or the count-in. NULL if nothing is open, the range is empty, or the output
 * cannot be written.
 */
SD_API SdExport *sd_export_begin_range(SdEngine *engine, const char *utf8OutputPath, int bitrateKbps, double start,
                                       double end);
/**
 * The engine's current sound settings (speed, pitch, mix, EQ, preamp), frozen: a batch of exports
 * takes them once and passes them to each file, so a change made while the batch runs reaches none
 * of its files. NULL if out of memory. Free it with sd_export_settings_free.
 */
typedef struct SdExportSettings SdExportSettings;
SD_API SdExportSettings *sd_export_capture_settings(SdEngine *engine);
SD_API void sd_export_settings_free(SdExportSettings *settings);
/** As sd_export_begin_range, with `settings` (from sd_export_capture_settings) instead of the engine's own. */
SD_API SdExport *sd_export_begin_range_with(SdEngine *engine, const char *utf8OutputPath, int bitrateKbps,
                                            double start, double end, const SdExportSettings *settings);
/** Encodes the next chunk. Returns the progress 0..1 within the range, or -1 once everything is encoded. */
SD_API double sd_export_next(SdExport *exporter);
/** Finishes the file and frees the exporter. 1 = complete file, 0 = failed or cut short. */
SD_API int sd_export_end(SdExport *exporter);

#ifdef __cplusplus
}
#endif

#endif
