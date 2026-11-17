#include "Mp4EditList.h"

#include <cstring>

namespace {

uint32_t readU32(const uint8_t *p) { return uint32_t(p[0]) << 24 | uint32_t(p[1]) << 16 | uint32_t(p[2]) << 8 | p[3]; }

uint64_t readU64(const uint8_t *p) { return uint64_t(readU32(p)) << 32 | readU32(p + 4); }

/** One ISO BMFF box: its type, and its payload as [begin, end) offsets in the file. */
struct Box {
    char type[4];
    size_t start = 0;
    size_t begin = 0;
    size_t end = 0;
};

/** The boxes in [begin, end). Stops at the first header that does not fit, so a corrupt size ends the walk. */
template <typename Visit> void forEachBox(const uint8_t *data, size_t begin, size_t end, Visit visit) {
    size_t at = begin;
    while (end - at >= 8) {
        uint64_t size = readU32(data + at);
        size_t header = 8;
        if (size == 1) {
            if (end - at < 16) return;
            size = readU64(data + at + 8);
            header = 16;
        } else if (size == 0) {
            size = end - at;
        }
        if (size < header || size > end - at) return;
        Box box;
        std::memcpy(box.type, data + at + 4, 4);
        box.start = at;
        box.begin = at + header;
        box.end = at + size_t(size);
        if (!visit(box)) return;
        at = box.end;
    }
}

bool is(const Box &box, const char *type) { return std::memcmp(box.type, type, 4) == 0; }

/** The first box of [type] directly inside [parent]. */
std::optional<Box> child(const uint8_t *data, const Box &parent, const char *type) {
    std::optional<Box> found;
    forEachBox(data, parent.begin, parent.end, [&](const Box &box) {
        if (is(box, type)) found = box;
        return !found;
    });
    return found;
}

/** media_time of the first edit, or nothing if the elst cannot be read. */
std::optional<int64_t> firstMediaTime(const uint8_t *data, const Box &elst) {
    // version(1) flags(3) entry_count(4), then per entry: duration and media_time, 32 or 64 bits.
    if (elst.end - elst.begin < 8) return std::nullopt;
    const uint8_t version = data[elst.begin];
    if (readU32(data + elst.begin + 4) == 0) return std::nullopt;
    const size_t entry = elst.begin + 8;
    if (version == 1) {
        if (elst.end - entry < 16) return std::nullopt;
        return int64_t(readU64(data + entry + 8));
    }
    if (elst.end - entry < 8) return std::nullopt;
    return int64_t(int32_t(readU32(data + entry + 4)));
}

/** mdhd's timescale, 0 if it cannot be read. */
uint32_t timescale(const uint8_t *data, const Box &mdhd) {
    // version 1: version/flags(4) creation(8) modification(8) timescale(4); version 0: 4 + 4 + 4 + 4.
    if (mdhd.end - mdhd.begin < 24) return 0;
    const size_t at = mdhd.begin + (data[mdhd.begin] == 1 ? 20 : 12);
    return mdhd.end - at >= 4 ? readU32(data + at) : 0;
}

bool isSoundTrack(const uint8_t *data, const Box &mdia) {
    // hdlr: version/flags(4) pre_defined(4) handler_type(4).
    const auto hdlr = child(data, mdia, "hdlr");
    return hdlr && hdlr->end - hdlr->begin >= 12 && std::memcmp(data + hdlr->begin + 8, "soun", 4) == 0;
}

} // namespace

std::optional<FragmentedEditList> findFragmentedEditList(const uint8_t *data, size_t size) {
    Box file;
    file.begin = 0;
    file.end = size;
    const auto moov = child(data, file, "moov");
    // Only fragmented files: Media Foundation plays the edit lists of plain MP4 (iTunes AAC has them).
    if (!moov || !child(data, *moov, "mvex")) return std::nullopt;

    std::optional<FragmentedEditList> found;
    forEachBox(data, moov->begin, moov->end, [&](const Box &trak) {
        if (!is(trak, "trak")) return true;
        const auto edts = child(data, trak, "edts");
        const auto mdia = child(data, trak, "mdia");
        if (!edts || !mdia || !isSoundTrack(data, *mdia)) return true;
        const auto elst = child(data, *edts, "elst");
        const auto mdhd = child(data, *mdia, "mdhd");
        const auto mediaTime = elst ? firstMediaTime(data, *elst) : std::nullopt;
        const uint32_t scale = mdhd ? timescale(data, *mdhd) : 0;
        // -1 is an empty edit (a pause before the track), not a start inside it.
        if (mediaTime && *mediaTime > 0 && scale > 0) found = FragmentedEditList{edts->start, *mediaTime, scale};
        return !found;
    });
    return found;
}
