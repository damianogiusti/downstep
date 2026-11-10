#include "Pcm.h"

#include <AudioToolbox/AudioToolbox.h>
#include <sys/stat.h>
#include <sys/sysctl.h>

#include <cstdio>
#include <cstdlib>
#include <memory>

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

} // namespace

DecodeStatus decodeFile(const std::string &path, PcmBuffer &out, std::string &error, int64_t memoryBudgetBytes) {
    CFURLRef url = CFURLCreateFromFileSystemRepresentation(
        nullptr, reinterpret_cast<const UInt8 *>(path.c_str()), CFIndex(path.size()), false);
    ExtAudioFileRef raw = nullptr;
    OSStatus status = ExtAudioFileOpenURL(url, &raw);
    CFRelease(url);
    if (status != noErr) {
        error = statusText("ExtAudioFileOpenURL", status);
        return DecodeStatus::Failed;
    }
    std::unique_ptr<OpaqueExtAudioFile, FileCloser> file(raw);

    AudioStreamBasicDescription fileFormat{};
    UInt32 size = sizeof(fileFormat);
    status = ExtAudioFileGetProperty(file.get(), kExtAudioFileProperty_FileDataFormat, &size, &fileFormat);
    if (status != noErr) {
        error = statusText("FileDataFormat", status);
        return DecodeStatus::Failed;
    }
    const int channels = int(fileFormat.mChannelsPerFrame);
    if (channels < 1 || channels > 2) {
        error = "only mono or stereo files are supported, got " + std::to_string(channels) + " channels";
        return DecodeStatus::Failed;
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
        return DecodeStatus::Failed;
    }

    SInt64 lengthFrames = 0;
    size = sizeof(lengthFrames);
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
    // The header's length is a hint only: reservePcm caps it.
    reservePcm(out, int64_t(lengthFrames), bytes);

    // The duration can be missing or wrong, so the vectors may still grow past what is available.
    try {
        constexpr UInt32 chunkFrames = 32768;
        std::vector<std::vector<float>> chunk(static_cast<size_t>(channels), std::vector<float>(chunkFrames));
        const size_t listBytes = offsetof(AudioBufferList, mBuffers) + sizeof(AudioBuffer) * size_t(channels);
        std::unique_ptr<AudioBufferList, decltype(&std::free)> list(
            static_cast<AudioBufferList *>(std::malloc(listBytes)), &std::free);
        list->mNumberBuffers = UInt32(channels);

        while (true) {
            for (int c = 0; c < channels; ++c) {
                list->mBuffers[c].mNumberChannels = 1;
                list->mBuffers[c].mDataByteSize = chunkFrames * sizeof(float);
                list->mBuffers[c].mData = chunk[size_t(c)].data();
            }
            UInt32 frames = chunkFrames;
            status = ExtAudioFileRead(file.get(), &frames, list.get());
            if (status != noErr) {
                error = statusText("ExtAudioFileRead", status);
                return DecodeStatus::Failed;
            }
            if (frames == 0) break;
            for (int c = 0; c < channels; ++c) {
                out.data[size_t(c)].insert(out.data[size_t(c)].end(), chunk[size_t(c)].begin(),
                                           chunk[size_t(c)].begin() + frames);
            }
        }
    } catch (const std::bad_alloc &) {
        out = PcmBuffer{};
        error = "out of memory while decoding";
        return DecodeStatus::TooLong;
    }
    out.frames = int64_t(out.data[0].size());
    return DecodeStatus::Ok;
}
