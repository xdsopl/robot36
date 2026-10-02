"""Generate synthetic FLAC fixtures with soundfile 0.13.1 and NumPy.

No recordings are used. The reader tests reproduce the integer samples to
check both 16/24-bit precision and the exact first/last decoded frames.
"""
from pathlib import Path

import numpy as np
import soundfile as sf


def sample(frame, channel, bits):
    if frame == 0:
        return -(1 << (bits - 1))
    if frame == 1:
        return (1 << (bits - 1)) - 1
    phase = frame * (31 if channel == 0 else 47) % 2048 - 1024
    return (phase << (bits - 12)) + (frame % 251 - 125 if bits == 24 else 0)


def generate(name, rate, channels, bits, frames, silence=False):
    pcm = np.zeros((frames, channels), dtype=np.int32)
    if not silence:
        for frame in range(frames):
            for channel in range(channels):
                # libsndfile expects integer samples left-aligned in int32.
                pcm[frame, channel] = sample(frame, channel, bits) << (32 - bits)
    destination = Path(__file__).resolve().parents[1] / "assets" / "flac"
    destination.mkdir(parents=True, exist_ok=True)
    sf.write(destination / name, pcm, rate, format="FLAC", subtype=f"PCM_{bits}")
    print(name, (destination / name).stat().st_size, "bytes")


generate("mono-16.flac", 32000, 1, 16, 10037)
generate("stereo-24.flac", 48000, 2, 24, 15013)
generate("silence.flac", 48000, 2, 16, 24017, silence=True)
