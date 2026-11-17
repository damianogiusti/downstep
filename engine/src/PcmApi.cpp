#include "sdengine.h"

#include "Pcm.h"

#include <algorithm>
#include <memory>
#include <new>
#include <string>

struct SdPcm {
    PcmBuffer buffer;
};

extern "C" {

SdPcm *sd_pcm_decode(const char *utf8Path, int64_t memoryBudgetBytes, int *status) try {
    auto pcm = std::make_unique<SdPcm>();
    std::string error; // decoders explain themselves for logs; this API only reports the class of failure
    const DecodeStatus result = decodeFile(utf8Path, pcm->buffer, error, memoryBudgetBytes);
    if (status) *status = result == DecodeStatus::Ok ? SD_PCM_OK : result == DecodeStatus::TooLong ? SD_PCM_TOO_LONG : SD_PCM_FAILED;
    return result == DecodeStatus::Ok ? pcm.release() : nullptr;
} catch (const std::bad_alloc &) {
    // Nothing may cross into the JVM.
    if (status) *status = SD_PCM_TOO_LONG;
    return nullptr;
} catch (...) {
    if (status) *status = SD_PCM_FAILED;
    return nullptr;
}

int sd_pcm_sample_rate(const SdPcm *pcm) { return pcm->buffer.sampleRate; }

int sd_pcm_channels(const SdPcm *pcm) { return pcm->buffer.channels; }

int64_t sd_pcm_frames(const SdPcm *pcm) { return pcm->buffer.frames; }

int64_t sd_pcm_copy(const SdPcm *pcm, int channel, int64_t firstFrame, float *out, int64_t frames) {
    const PcmBuffer &buffer = pcm->buffer;
    if (channel < 0 || firstFrame < 0 || frames <= 0 || firstFrame >= buffer.frames) return 0;
    const int64_t count = std::min(frames, buffer.frames - firstFrame);
    std::copy_n(buffer.channelData(channel) + firstFrame, size_t(count), out);
    return count;
}

void sd_pcm_free(SdPcm *pcm) { delete pcm; }

} // extern "C"
