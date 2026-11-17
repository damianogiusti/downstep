#pragma once

#include <cstddef>
#include <cstdint>
#include <optional>

/**
 * The edit list of the sound track of a fragmented MP4, when it starts the track later than its first
 * sample (media_time > 0). YouTube's audio (itag 140) has one, media_time 1600: the AAC encoder's
 * delay. Media Foundation decodes no audio at all from such a file, without an error, while it reads
 * the same file without the edit list, or with media_time 0. Seen on Windows Server 2025 in CI, and
 * as "no audio decoded" on users' Windows.
 */
struct FragmentedEditList {
    /** Where the track's edts box starts in the file. */
    size_t edtsOffset = 0;
    /** First sample the edit plays, in [timescale] units. */
    int64_t mediaTime = 0;
    /** The track's own timescale (mdhd). */
    uint32_t timescale = 0;
};

/**
 * Looks into the moov box of the [size] bytes at [data] (a whole file). Nothing for a file that is
 * not a fragmented MP4 (no mvex), whose sound track has no edit list or one that starts at 0, or that
 * cannot be read as boxes.
 */
std::optional<FragmentedEditList> findFragmentedEditList(const uint8_t *data, size_t size);
