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

#include <stdint.h>

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

/** Reasons of the last `sd_engine_open`, from `sd_engine_open_error`. */
#define SD_OPEN_OK 0
/** The file cannot be decoded: unreadable, unsupported, no audio. */
#define SD_OPEN_FAILED 1
/** The decoded audio does not fit in memory. */
#define SD_OPEN_TOO_LONG 2

/** Decodes the whole file into memory. Blocking: call it off the UI thread. 1 = ok, 0 = failed. */
SD_API int sd_engine_open(SdEngine *engine, const char *utf8Path);
/**
 * Opens the stems of a track and plays them together, summed on the fly, each with its own gain.
 * `utf8Paths` are `count` MP3 files written by `sd_stem_writer_*` (1 to SD_MAX_STEMS), decoded like
 * `sd_stem_decode`: sample-exact, the same on every OS. All must have the same sample rate (else
 * SD_OPEN_FAILED); a mono stem plays on both sides; a stem shorter than the others is padded with
 * silence. The memory limit (`sd_engine_set_memory_budget`) covers all the stems together
 * (SD_OPEN_TOO_LONG). Blocking: call it off the UI thread. Status and error text as `sd_engine_open`
 * (the text names the stem by number, never a path). 1 = ok, 0 = failed; on failure what was open stays.
 *
 * Speed, pitch, loop, mix, EQ, normalization and export work as for a single file, on the sum. The
 * loudness for normalization is measured once, here, on the sum of the stems at gain 1: gain changes
 * do not measure it again. `sd_engine_open` goes back to a single file.
 */
SD_API int sd_engine_open_stems(SdEngine *engine, const char *const *utf8Paths, int count);
/** Most stems a source can have. */
#define SD_MAX_STEMS 8
/**
 * Linear gain of stem `index` (the order of the paths in `sd_engine_open_stems`), 0 = muted. The audio
 * thread follows it with a ramp over about one grain (tens of ms): a slider does not click. The engine
 * applies the gain exactly as given and does not compensate the headroom of the stem files: the stem
 * writer leaves -3 dB (gain 0.7079), so the caller passes `gain / 0.7079` to get the original level back
 * with every stem at 1. An index outside 0..SD_MAX_STEMS-1 is ignored. Every successful open (stems or
 * single file) resets all gains to 1, so set them after the open returns; they then persist until the
 * next successful open. Playback after an open starts at the gains in place, with no ramp. Gains are
 * limited to 0..8.
 */
SD_API void sd_engine_set_stem_gain(SdEngine *engine, int index, double gain);
/** Number of stems of the open source; 0 when a single file is open (or nothing). */
SD_API int sd_engine_stem_count(const SdEngine *engine);

/**
 * Limit for the decoded audio of the next `sd_engine_open`, in bytes (32-bit float per channel per
 * frame). 0 = automatic: what the OS can still give (commit on Windows, physical memory on macOS)
 * minus a margin. A positive value is used as is: tests set a tiny one to get SD_OPEN_TOO_LONG.
 */
SD_API void sd_engine_set_memory_budget(SdEngine *engine, int64_t bytes);
/** SD_OPEN_* of the last `sd_engine_open`. SD_OPEN_OK before any call and after a successful one. */
SD_API int sd_engine_open_error(const SdEngine *engine);
/**
 * Detail of the last failure, for logs: a decoder step and its system code, never the path. Copies a
 * NUL-terminated text, cut to `size` - 1 bytes, into `buffer`. Returns the full length, so 0 means
 * no text; `buffer` may be NULL to only ask for it.
 */
SD_API int sd_engine_open_error_text(const SdEngine *engine, char *buffer, int size);
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
 * Starts an MP3 export of the open file with the engine's current sound (speed, pitch, mix, EQ, and the
 * stem gains when stems are open),
 * from the start to the end, loop ignored. It renders in its own engine over the audio already
 * decoded (shared, not decoded again): playback is not disturbed, and closing or replacing the
 * open file meanwhile does not affect the export. NULL if no file is open or the output cannot be
 * written.
 */
SD_API SdExport *sd_export_begin(SdEngine *engine, const char *utf8OutputPath, int bitrateKbps);
/** Encodes the next chunk. Returns the progress 0..1, or -1 once everything is encoded. */
SD_API double sd_export_next(SdExport *exporter);
/** Finishes the file and frees the exporter. 1 = complete file, 0 = failed or cut short. */
SD_API int sd_export_end(SdExport *exporter);

/*
 * Decoded audio without a player: the stem pipeline decodes a track, separates it, writes the stems.
 * The same decoders as sd_engine_open (AudioToolbox, Media Foundation).
 */
typedef struct SdPcm SdPcm;

/** Results of `sd_pcm_decode`, in `*status`. */
#define SD_PCM_OK 0
/** The file cannot be decoded: unreadable, unsupported, no audio, more than two channels. */
#define SD_PCM_FAILED 1
/** The decoded audio does not fit in memory. */
#define SD_PCM_TOO_LONG 2

/**
 * Decodes the whole file into memory at its own sample rate and channel count (mono or stereo): no
 * resampling here. Blocking: call it off the UI thread. `memoryBudgetBytes` as in
 * `sd_engine_set_memory_budget`: 0 = automatic. Returns NULL on failure, with the reason in `*status`
 * (may be NULL if not wanted).
 */
SD_API SdPcm *sd_pcm_decode(const char *utf8Path, int64_t memoryBudgetBytes, int *status);
SD_API int sd_pcm_sample_rate(const SdPcm *pcm);
/** 1 or 2. A mono file stays mono: the caller duplicates the channel. */
SD_API int sd_pcm_channels(const SdPcm *pcm);
SD_API int64_t sd_pcm_frames(const SdPcm *pcm);
/**
 * Copies up to `frames` samples of `channel`, from `firstFrame`, into `out`. Returns how many were
 * copied: fewer than asked at the end of the audio, 0 for a bad range. A channel past the last one
 * reads the last one (channel 1 of a mono file is channel 0).
 */
SD_API int64_t sd_pcm_copy(const SdPcm *pcm, int channel, int64_t firstFrame, float *out, int64_t frames);
SD_API void sd_pcm_free(SdPcm *pcm);

/**
 * Decodes an MP3 stem written by `sd_stem_writer_*` into the same SdPcm, with the engine's own decoder
 * (miniaudio's dr_mp3) instead of the OS one: the same on every platform, and it honours the Xing/LAME
 * tag, so `sd_pcm_frames` is exactly the number of frames written and the audio is at the same place.
 * Only for files the stem writer made: other MP3s should go through `sd_pcm_decode`. Budget, statuses
 * (SD_PCM_*) and the rest as `sd_pcm_decode`; free with `sd_pcm_free`.
 */
SD_API SdPcm *sd_stem_decode(const char *utf8Path, int64_t memoryBudgetBytes, int *status);

typedef struct SdStemWriter SdStemWriter;

/** Results of `sd_stem_writer_open` (in `*status`) and `sd_stem_writer_write`. */
#define SD_STEM_OK 0
/** The file cannot be created. */
#define SD_STEM_OPEN_FAILED 1
/** Rate, channel count or bitrate that the MP3 encoder does not take. */
#define SD_STEM_BAD_FORMAT 2
/** The encoder or the disk failed, or the writer already failed: the file is not complete. */
#define SD_STEM_WRITE_FAILED 3

/**
 * Starts an MP3 stem file around `bitrateKbps` (192 is the default for stems), `channels` 1 or 2, the
 * rates MP3 allows (8000 to 48000). LAME is called directly and the file is opened "w+b" so that
 * `sd_stem_writer_close` can write the Xing/LAME tag: with it a decoder gives back exactly the frames
 * written, without it AudioToolbox adds 2560 frames and the audio is late. The bitrate is an average
 * (ABR), not constant: a constant-bitrate file gets an "Info" tag, which AudioToolbox misreads by 1152
 * frames for some lengths. Returns NULL on failure, with the reason in `*status` (may be NULL).
 */
SD_API SdStemWriter *sd_stem_writer_open(const char *utf8Path, int sampleRate, int channels, int bitrateKbps,
                                         int *status);
/**
 * Encodes `frames` frames of interleaved float samples (-1..1; `channels` values per frame). Call it
 * as often as needed, with any block size. Returns SD_STEM_OK or an error; after an error every write
 * fails and the file is not complete.
 */
SD_API int sd_stem_writer_write(SdStemWriter *writer, const float *interleaved, int64_t frames);
/** Flushes the encoder, writes the tag, closes the file and frees the writer. 1 = complete file, 0 = not. */
SD_API int sd_stem_writer_close(SdStemWriter *writer);

#ifdef __cplusplus
}
#endif

#endif
