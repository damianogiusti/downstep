#include "Pcm.h"

#include <AudioToolbox/AudioToolbox.h>

#include <cstdio>
#include <cstdlib>
#include <memory>

namespace {

std::string statusText(const char *what, OSStatus status) {
    return std::string(what) + " failed (OSStatus " + std::to_string(int(status)) + ")";
}

struct FileCloser {
    void operator()(OpaqueExtAudioFile *file) const { ExtAudioFileDispose(file); }
};

} // namespace

bool decodeFile(const std::string &path, PcmBuffer &out, std::string &error) {
    CFURLRef url = CFURLCreateFromFileSystemRepresentation(
        nullptr, reinterpret_cast<const UInt8 *>(path.c_str()), CFIndex(path.size()), false);
    ExtAudioFileRef raw = nullptr;
    OSStatus status = ExtAudioFileOpenURL(url, &raw);
    CFRelease(url);
    if (status != noErr) {
        error = statusText("ExtAudioFileOpenURL", status);
        return false;
    }
    std::unique_ptr<OpaqueExtAudioFile, FileCloser> file(raw);

    AudioStreamBasicDescription fileFormat{};
    UInt32 size = sizeof(fileFormat);
    status = ExtAudioFileGetProperty(file.get(), kExtAudioFileProperty_FileDataFormat, &size, &fileFormat);
    if (status != noErr) {
        error = statusText("FileDataFormat", status);
        return false;
    }
    const int channels = int(fileFormat.mChannelsPerFrame);
    if (channels < 1 || channels > 2) {
        error = "only mono or stereo files are supported, got " + std::to_string(channels) + " channels";
        return false;
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
        return false;
    }

    SInt64 lengthFrames = 0;
    size = sizeof(lengthFrames);
    ExtAudioFileGetProperty(file.get(), kExtAudioFileProperty_FileLengthFrames, &size, &lengthFrames);

    out = PcmBuffer{};
    out.sampleRate = int(fileFormat.mSampleRate);
    out.channels = channels;
    out.data.assign(size_t(channels), {});
    for (auto &channel : out.data) channel.reserve(size_t(std::max<SInt64>(lengthFrames, 0)));

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
            return false;
        }
        if (frames == 0) break;
        for (int c = 0; c < channels; ++c) {
            out.data[size_t(c)].insert(out.data[size_t(c)].end(), chunk[size_t(c)].begin(),
                                       chunk[size_t(c)].begin() + frames);
        }
    }
    out.frames = int64_t(out.data[0].size());
    return true;
}
