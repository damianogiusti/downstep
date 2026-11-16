"""Reference vectors for the DSP tests (StftParityTest). Re-run with:

    python -I generate_reference.py <output dir>

Needs only numpy and torch. Fixed seed. Files are raw little-endian float32; spectra are interleaved
(re, im) per bin, frame after frame, bins = fftSize / 2 + 1.

  signal.bin           input, 2 * 4096 + 123 samples
  spleeter_spec.bin    numpy: prepend (N - hop) zeros, periodic Hann, rfft, no scaling
  spleeter_istft.bin   numpy: weighted overlap-add / sum of squared windows, leading N - hop zeros removed
  demucs_spec.bin      torch.stft(center=True, pad_mode="reflect", normalized=True, periodic Hann)
  demucs_istft.bin     torch.istft of demucs_spec (normalized=True, center=True), no length argument
"""
import sys
from pathlib import Path

import numpy as np
import torch

N, HOP = 4096, 1024
LENGTH = 2 * N + 123


def write(directory, name, array):
    np.ascontiguousarray(array, dtype="<f4").tofile(directory / name)


def interleave(complex_frames):
    return np.stack([complex_frames.real, complex_frames.imag], axis=-1).astype(np.float32)


def main():
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    rng = np.random.default_rng(2025)
    signal = rng.uniform(-1.0, 1.0, LENGTH).astype(np.float32)
    window = (0.5 - 0.5 * np.cos(2 * np.pi * np.arange(N) / N)).astype(np.float32)

    padded = np.concatenate([np.zeros(N - HOP, dtype=np.float32), signal])
    frames = (len(padded) - N) // HOP + 1
    spectra = np.stack([np.fft.rfft(padded[k * HOP:k * HOP + N].astype(np.float64) * window) for k in range(frames)])
    length = (frames - 1) * HOP + N
    acc = np.zeros(length)
    env = np.zeros(length)
    for k in range(frames):
        acc[k * HOP:k * HOP + N] += np.fft.irfft(spectra[k], N) * window
        env[k * HOP:k * HOP + N] += window.astype(np.float64) ** 2
    rebuilt = np.where(env > 1e-8, acc / np.maximum(env, 1e-8), 0.0)[N - HOP:]
    write(out, "signal.bin", signal)
    write(out, "spleeter_spec.bin", interleave(spectra))
    write(out, "spleeter_istft.bin", rebuilt)

    t = torch.from_numpy(signal)
    w = torch.from_numpy(window)
    spec = torch.stft(t, N, HOP, window=w, center=True, pad_mode="reflect", normalized=True, return_complex=True)
    back = torch.istft(spec, N, HOP, window=w, center=True, normalized=True)
    write(out, "demucs_spec.bin", interleave(spec.T.numpy()))
    write(out, "demucs_istft.bin", back.numpy())


if __name__ == "__main__":
    main()
