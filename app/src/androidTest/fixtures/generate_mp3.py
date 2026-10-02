import math
import struct
from pathlib import Path

import lameenc


def generate(name, rate, frequencies, vbr=False, silence=False):
    encoder = lameenc.Encoder()
    encoder.set_in_sample_rate(rate)
    encoder.set_out_sample_rate(rate)
    encoder.set_channels(len(frequencies))
    encoder.set_quality(2)
    encoder.silence()
    if vbr:
        encoder.set_vbr(lameenc.VBR_MTRH)
        encoder.set_vbr_quality(4)
    else:
        encoder.set_bit_rate(64)
    pcm = bytearray()
    for frame in range(round(rate * 0.6)):
        for frequency in frequencies:
            value = 0 if silence else round(8192 * math.sin(2 * math.pi * frequency * frame / rate))
            pcm.extend(struct.pack("<h", value))
    destination = Path(__file__).resolve().parents[1] / "assets" / "mp3"
    destination.mkdir(parents=True, exist_ok=True)
    encoded = encoder.encode(bytes(pcm)) + encoder.flush()
    (destination / name).write_bytes(encoded)
    print(name, len(encoded), "bytes")


generate("mono-cbr.mp3", 32000, (1500,))
generate("stereo-vbr.mp3", 44100, (1200, 2300), vbr=True)
generate("silence.mp3", 32000, (1500,), silence=True)
