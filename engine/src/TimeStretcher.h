#pragma once

#include "Pcm.h"

#include <array>
#include <memory>

namespace Bungee {
template <class Edition> struct Stretcher;
struct Basic;
struct OutputChunk;
} // namespace Bungee

/**
 * Bungee's granular API over a stereo source (a mono one feeds both sides). Every grain names the
 * source position it wants, so seek and loop are just a new position: no splice, no reset. Grains
 * produce output in chunks of varying size, kept in a FIFO together with the source positions they
 * cover.
 *
 * Single-threaded: only the render path calls it.
 */
class TimeStretcher {
public:
    TimeStretcher();
    ~TimeStretcher();

    /**
     * Allocates every buffer, ours and Bungee's. Bungee reallocates a grain's input copy only when
     * the input chunk size changes, which resampleMode_autoOut never does: speed, pitch, seek and
     * loop changes render without allocating (the "rendering does not allocate" engine test).
     */
    void prepare(const PcmBuffer &source, int outputRate, int maxBlockFrames);
    void seek(double frame);
    void setSpeed(double ratio);
    void setPitch(double semitones);
    void setLoop(int64_t start, int64_t end, bool enabled);
    /** 0 = left on both channels, 0.5 = unchanged, 1 = right on both. Applied before the stretch. */
    void setMix(double mixRight);

    /** Fills `frames` planar stereo frames. Returns how many carry audio: fewer only at the end. */
    int render(float *const *out, int frames);
    /** Source frame being output at the FIFO read head. */
    double position() const { return position_; }

private:
    void produceGrain();
    void appendToFifo(const Bungee::OutputChunk &output);
    void updatePosition();
    void updateChunkPosition();

    struct ChunkInfo {
        int64_t begin = 0; // absolute FIFO frame index
        int frames = 0;
        double positionBegin = 0;
        double positionEnd = 0;
    };
    static constexpr int kMaxChunks = 64;

    const PcmBuffer *source_ = nullptr;
    std::unique_ptr<Bungee::Stretcher<Bungee::Basic>> stretcher_;
    struct RequestState;
    std::unique_ptr<RequestState> request_;
    int64_t loopStart_ = 0, loopEnd_ = 0;
    bool loopOn_ = false;
    float mixLeftToRight_ = 0.f, mixRightToLeft_ = 0.f, keepLeft_ = 1.f, keepRight_ = 1.f;
    std::vector<float> grainInput_;
    std::array<std::vector<float>, 2> fifo_;
    int fifoCapacity_ = 0;
    int fifoRead_ = 0, fifoWrite_ = 0;
    int64_t readCount_ = 0, writeCount_ = 0; // absolute, for the chunk table
    std::array<ChunkInfo, kMaxChunks> chunks_{};
    int chunkHead_ = 0, chunkCount_ = 0;
    bool finished_ = false;
    double position_ = 0;
    double seekTarget_ = -1; // after a seek the preroll grains sit before it: hold the target until passed
};
