#pragma once

#include <cstdint>

#if defined(__x86_64__) || defined(__SSE__)
#include <xmmintrin.h>
#endif

/**
 * Flushes denormal floats to zero while in scope. Decaying EQ filter states and FFT tails pass
 * through denormals, which many cores handle far slower than normal floats: a CPU spike on the
 * audio thread for nothing audible. Restored on exit, since manual-mode rendering runs on the
 * caller's thread (a JVM thread, where Java expects IEEE denormals). Two register writes.
 */
class ScopedFlushDenormals {
public:
#if defined(__x86_64__) || defined(__SSE__)
    // MXCSR: FTZ (bit 15) flushes results, DAZ (bit 6) treats denormal inputs as zero.
    ScopedFlushDenormals() : saved_(_mm_getcsr()) { _mm_setcsr(saved_ | 0x8040u); }
    ~ScopedFlushDenormals() { _mm_setcsr(saved_); }

private:
    unsigned saved_;
#elif defined(__aarch64__)
    // FPCR.FZ (bit 24) flushes both inputs and results.
    ScopedFlushDenormals() {
        asm volatile("mrs %0, fpcr" : "=r"(saved_));
        asm volatile("msr fpcr, %0" : : "r"(saved_ | (uint64_t(1) << 24)));
    }
    ~ScopedFlushDenormals() { asm volatile("msr fpcr, %0" : : "r"(saved_)); }

private:
    uint64_t saved_ = 0;
#else
    ScopedFlushDenormals() = default;
#endif

public:
    ScopedFlushDenormals(const ScopedFlushDenormals &) = delete;
    ScopedFlushDenormals &operator=(const ScopedFlushDenormals &) = delete;
};
