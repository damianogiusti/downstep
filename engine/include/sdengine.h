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

#define SD_EQ_BANDS 7

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
SD_API void sd_engine_set_eq_enabled(SdEngine *engine, int enabled);
/** Bands 0..6 at 60, 150, 400, 1k, 2.4k, 6k, 15k Hz. */
SD_API void sd_engine_set_eq_band(SdEngine *engine, int band, double gainDb);
/** Applied only while the EQ is on. */
SD_API void sd_engine_set_preamp(SdEngine *engine, double gainDb);
/** Linear output gain, 1 by default. 0 mutes a device engine (scripted checks). */
SD_API void sd_engine_set_output_gain(SdEngine *engine, double gain);

/**
 * Loudness normalization, off by default. On: every file plays at -16 LUFS (ITU-R BS.1770-4 integrated
 * loudness, measured by sd_engine_open), raised by at most 12 dB, with a peak limiter at -1 dBFS after
 * the gain. Playback only: an export keeps the original level. Switching mid-play dips for about 25 ms.
 */
SD_API void sd_engine_set_normalization(SdEngine *engine, int enabled);
/** Integrated loudness of the open file in LUFS. -INFINITY if it is silent or no file is open. */
SD_API double sd_engine_loudness_lufs(const SdEngine *engine);
/** The gain normalization gives the open file, in dB, clamped. 0 if it is silent or no file is open. */
SD_API double sd_engine_normalization_gain_db(const SdEngine *engine);

/** Returns and clears the SD_EVENT_* bits raised since the previous call. */
SD_API unsigned sd_engine_take_events(SdEngine *engine);

/** Output sample rate: the device rate, or `manualSampleRate`. Output is always stereo. */
SD_API int sd_engine_sample_rate(const SdEngine *engine);
/** Manual mode only: fills `frames` interleaved stereo frames. Returns 0 on a device engine. */
SD_API int sd_engine_render(SdEngine *engine, float *interleaved, int frames);

typedef struct SdExport SdExport;

/**
 * Starts an MP3 export of the open file with the engine's current sound (speed, pitch, mix, EQ),
 * from the start to the end, loop ignored. It decodes the file again into its own engine: playback
 * is not disturbed. NULL if no file is open or the output cannot be written.
 */
SD_API SdExport *sd_export_begin(SdEngine *engine, const char *utf8OutputPath, int bitrateKbps);
/** Encodes the next chunk. Returns the progress 0..1, or -1 once everything is encoded. */
SD_API double sd_export_next(SdExport *exporter);
/** Finishes the file and frees the exporter. 1 = complete file, 0 = failed or cut short. */
SD_API int sd_export_end(SdExport *exporter);

#ifdef __cplusplus
}
#endif

#endif
