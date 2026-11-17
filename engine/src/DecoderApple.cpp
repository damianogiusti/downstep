#include "Pcm.h"

#include <AudioToolbox/AudioToolbox.h>
#include <sys/stat.h>
#include <sys/sysctl.h>

#include <algorithm>
#include <atomic>
#include <cstddef>
#include <cstdio>
#include <cstdlib>
#include <exception>
#include <memory>
#include <new>
#include <thread>
#include <vector>

namespace {

std::string statusText(const char *what, OSStatus status) {
    return std::string(what) + " failed (OSStatus " + std::to_string(int(status)) + ")";
}

/** macOS compresses and swaps instead of failing an allocation, so physical memory is the bound. */
uint64_t availableMemoryBytes() {
    uint64_t physical = 0;
    size_t length = sizeof(physical);
    if (sysctlbyname("hw.memsize", &physical, &length, nullptr, 0) != 0) return UINT64_MAX;
    return physical > kMemoryMarginBytes ? physical - kMemoryMarginBytes : 0;
}

struct FileCloser {
    void operator()(OpaqueExtAudioFile *file) const { ExtAudioFileDispose(file); }
};
using AudioFile = std::unique_ptr<OpaqueExtAudioFile, FileCloser>;

/**
 * Below this many frames per slice (about 22 s at 48 kHz) a file is read on one thread: opening the
 * file again and starting a thread would cost more than the slice saves.
 */
constexpr int64_t kMinSliceFrames = int64_t(1) << 20;
/** Reading is bound by memory and the disk past a few threads. */
constexpr int64_t kMaxSlices = 8;
constexpr UInt32 kReadFrames = 65536;

/**
 * Opens [path] for reading as 32-bit float, one buffer per channel, mono or stereo. Null on failure,
 * with [error] set. [fileFormat] is the format inside the file.
 */
AudioFile openAsFloat(const std::string &path, AudioStreamBasicDescription &fileFormat, std::string &error) {
    CFURLRef url = CFURLCreateFromFileSystemRepresentation(
        nullptr, reinterpret_cast<const UInt8 *>(path.c_str()), CFIndex(path.size()), false);
    ExtAudioFileRef raw = nullptr;
    OSStatus status = ExtAudioFileOpenURL(url, &raw);
    CFRelease(url);
    if (status != noErr) {
        error = statusText("ExtAudioFileOpenURL", status);
        return nullptr;
    }
    AudioFile file(raw);

    UInt32 size = sizeof(fileFormat);
    status = ExtAudioFileGetProperty(file.get(), kExtAudioFileProperty_FileDataFormat, &size, &fileFormat);
    if (status != noErr) {
        error = statusText("FileDataFormat", status);
        return nullptr;
    }
    const int channels = int(fileFormat.mChannelsPerFrame);
    if (channels < 1 || channels > 2) {
        error = "only mono or stereo files are supported, got " + std::to_string(channels) + " channels";
        return nullptr;
    }

    AudioStreamBasicDescription client{};
    client.mSampleRate = fileFormat.mSampleRate;
    client.mFormatID = kAudioFormatLinearPCM;
    client.mFormatFlags = kAudioFormatFlagIsFloat | kAudioFormatFlagIsPacked | kAudioFormatFlagIsNonInterleaved |
                          kAudioFormatFlagsNativeEndian;
    client.mBitsPerChannel = 32;
    client.mChannelsPerFrame = UInt32(channels);
    client.mFramesPerPacket = 1;
    client.mBytesPerFrame = sizeof(float);
    client.mBytesPerPacket = sizeof(float);
    status = ExtAudioFileSetProperty(file.get(), kExtAudioFileProperty_ClientDataFormat, sizeof(client), &client);
    if (status != noErr) {
        error = statusText("ClientDataFormat", status);
        return nullptr;
    }
    return file;
}

/** One AudioBuffer per channel of [out], pointed at the frames to fill. */
class ChannelBuffers {
public:
    explicit ChannelBuffers(int channels)
        : list_(static_cast<AudioBufferList *>(
                    std::malloc(offsetof(AudioBufferList, mBuffers) + sizeof(AudioBuffer) * size_t(channels))),
                &std::free) {
        if (!list_) throw std::bad_alloc();
        list_->mNumberBuffers = UInt32(channels);
    }

    /** Reads up to [frames] frames into [out] from frame [offset] on, which must exist. */
    OSStatus read(ExtAudioFileRef file, PcmBuffer &out, int64_t offset, UInt32 &frames) {
        for (UInt32 c = 0; c < list_->mNumberBuffers; ++c) {
            list_->mBuffers[c].mNumberChannels = 1;
            list_->mBuffers[c].mDataByteSize = frames * UInt32(sizeof(float));
            list_->mBuffers[c].mData = out.data[c].data() + offset;
        }
        return ExtAudioFileRead(file, &frames, list_.get());
    }

private:
    std::unique_ptr<AudioBufferList, decltype(&std::free)> list_;
};

/**
 * Reads from the file's current position to its end, appending to [out]. Through a chunk rather than
 * straight into the vectors: growing a vector past its capacity copies it, which costs more than the
 * chunk's copy, and after the parallel slices this usually reads nothing at all.
 */
DecodeStatus readToEnd(ExtAudioFileRef file, PcmBuffer &out, std::string &error) {
    PcmBuffer chunk;
    chunk.channels = out.channels;
    chunk.data.assign(size_t(out.channels), std::vector<float>(kReadFrames));
    ChannelBuffers buffers(out.channels);
    while (true) {
        UInt32 read = kReadFrames;
        const OSStatus status = buffers.read(file, chunk, 0, read);
        if (status != noErr) {
            error = statusText("ExtAudioFileRead", status);
            return DecodeStatus::Failed;
        }
        if (read == 0) break;
        for (size_t c = 0; c < out.data.size(); ++c) {
            out.data[c].insert(out.data[c].end(), chunk.data[c].begin(), chunk.data[c].begin() + read);
        }
    }
    out.frames = int64_t(out.data[0].size());
    return DecodeStatus::Ok;
}

/** Reads frames [first, last) of [path] into [out], whose vectors already hold them. */
bool readSlice(const std::string &path, PcmBuffer &out, int64_t first, int64_t last) {
    try {
        AudioStreamBasicDescription fileFormat{};
        std::string error;
        AudioFile file = openAsFloat(path, fileFormat, error);
        if (!file || int(fileFormat.mChannelsPerFrame) != out.channels) return false;
        if (ExtAudioFileSeek(file.get(), first) != noErr) return false;
        ChannelBuffers buffers(out.channels);
        for (int64_t frame = first; frame < last;) {
            UInt32 read = UInt32(std::min<int64_t>(kReadFrames, last - frame));
            if (buffers.read(file.get(), out, frame, read) != noErr || read == 0) return false;
            frame += read;
        }
        return true;
    } catch (const std::exception &) {
        // Out of memory for the buffer list: the caller reads the file on one thread instead.
        return false;
    }
}

/**
 * Uncompressed PCM can be read anywhere without decoding what comes before, so a long file is read
 * in slices on several threads, each with its own handle: 2.5 hours of 48 kHz 16-bit stereo in well
 * under a second instead of about 3 s (M1 Pro). Compressed formats are read on one thread: a seek there lands on a
 * packet and needs the decoder's priming, which this does not try to match. False if a slice failed:
 * [out] then holds garbage and the caller reads the whole file again.
 */
bool readSlicesInParallel(const std::string &path, ExtAudioFileRef file, PcmBuffer &out, int64_t lengthFrames) {
    const int64_t hardware = std::max<int64_t>(std::thread::hardware_concurrency(), 1);
    const int64_t slices = std::min({hardware, kMaxSlices, lengthFrames / kMinSliceFrames});
    // Everything that can throw before the first thread starts: a thread left unjoined ends the process.
    for (auto &channel : out.data) channel.resize(size_t(lengthFrames));
    ChannelBuffers buffers(out.channels);
    std::atomic<bool> ok{true};
    const auto sliceStart = [&](int64_t k) { return lengthFrames * k / slices; };
    std::vector<std::thread> workers;
    int64_t next = 1;
    try {
        workers.reserve(size_t(slices - 1));
        for (; next < slices; ++next) {
            workers.emplace_back([&, next] {
                if (!readSlice(path, out, sliceStart(next), sliceStart(next + 1))) ok = false;
            });
        }
    } catch (const std::exception &) {
        // No thread or memory to spare (std::system_error, std::bad_alloc): the slices not handed out
        // are read here instead.
    }
    for (int64_t k = next; k < slices; ++k) {
        if (!readSlice(path, out, sliceStart(k), sliceStart(k + 1))) ok = false;
    }
    // The first slice on the handle already open, at frame 0.
    for (int64_t frame = 0; ok && frame < sliceStart(1);) {
        UInt32 read = UInt32(std::min<int64_t>(kReadFrames, sliceStart(1) - frame));
        if (buffers.read(file, out, frame, read) != noErr || read == 0) ok = false;
        frame += read;
    }
    for (auto &worker : workers) worker.join();
    return ok;
}

} // namespace

DecodeStatus decodeFile(const std::string &path, PcmBuffer &out, std::string &error, int64_t memoryBudgetBytes) {
    AudioStreamBasicDescription fileFormat{};
    AudioFile file = openAsFloat(path, fileFormat, error);
    if (!file) return DecodeStatus::Failed;
    const int channels = int(fileFormat.mChannelsPerFrame);

    SInt64 lengthFrames = 0;
    UInt32 size = sizeof(lengthFrames);
    ExtAudioFileGetProperty(file.get(), kExtAudioFileProperty_FileLengthFrames, &size, &lengthFrames);

    struct stat stats {};
    const uint64_t bytes = stat(path.c_str(), &stats) == 0 ? uint64_t(stats.st_size) : 0;
    const uint64_t budget = memoryBudgetBytes > 0 ? uint64_t(memoryBudgetBytes) : availableMemoryBytes();
    if (!pcmFitsInMemory(int64_t(lengthFrames), channels, bytes, budget)) {
        error = "the decoded audio would not fit in memory";
        return DecodeStatus::TooLong;
    }

    out = PcmBuffer{};
    out.sampleRate = int(fileFormat.mSampleRate);
    out.channels = channels;
    out.data.assign(size_t(channels), {});

    // The duration can be missing or wrong, so the vectors may still grow past what is available.
    try {
        // For PCM the header's length is exact, as long as the file is not cut short or corrupt: then a
        // slice fails and the file is read again on one thread. Whatever follows the announced length is
        // read on one thread too.
        const bool isPcm = fileFormat.mFormatID == kAudioFormatLinearPCM;
        bool isSliced = false;
        if (isPcm && lengthFrames >= 2 * kMinSliceFrames &&
            uint64_t(lengthFrames) <= bytes / std::max<uint64_t>(fileFormat.mBytesPerFrame, 1)) {
            isSliced = readSlicesInParallel(path, file.get(), out, int64_t(lengthFrames));
            if (!isSliced) {
                for (auto &channel : out.data) channel.clear();
            }
            const OSStatus status = ExtAudioFileSeek(file.get(), isSliced ? lengthFrames : 0);
            if (status != noErr) {
                out = PcmBuffer{};
                error = statusText("ExtAudioFileSeek", status);
                return DecodeStatus::Failed;
            }
        } else {
            // The header's length is a hint only: reservePcm caps it.
            reservePcm(out, int64_t(lengthFrames), bytes);
        }
        const DecodeStatus status = readToEnd(file.get(), out, error);
        if (status != DecodeStatus::Ok) out = PcmBuffer{};
        return status;
    } catch (const std::bad_alloc &) {
        out = PcmBuffer{};
        error = "out of memory while decoding";
        return DecodeStatus::TooLong;
    }
}
