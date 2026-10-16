#!/usr/bin/env python3
"""Synthesizes the built-in Rift UI sounds (no third-party audio, so no licence questions).

Usage: tools/rift/gen_ui_sounds.py  -> writes app/src/main/res/raw/rift_ui_*.ogg (needs numpy and ffmpeg).
Sounds are short, soft sine tones with fast attack and exponential decay.
"""
import subprocess
import tempfile
import wave
from pathlib import Path

import numpy as np

SR = 44100
OUT = Path(__file__).resolve().parents[2] / "app/src/main/res/raw"


def tone(freq, dur, gain=1.0, glide_to=None, harmonics=((1, 1.0), (2, 0.18))):
    t = np.arange(int(SR * dur)) / SR
    f = np.linspace(freq, glide_to, t.size) if glide_to else np.full(t.size, freq)
    phase = 2 * np.pi * np.cumsum(f) / SR
    sig = sum(a * np.sin(h * phase) for h, a in harmonics)
    attack = np.minimum(1.0, t / 0.004)
    decay = np.exp(-t * (5.0 / dur))
    return gain * sig * attack * decay


def mix(*parts):
    n = max(off + p.size for off, p in parts)
    out = np.zeros(n)
    for off, p in parts:
        out[off:off + p.size] += p
    return out


def at(sec):
    return int(SR * sec)


SOUNDS = {
    "move": tone(1500, 0.045, 0.5),
    "select": mix((0, tone(660, 0.09, 0.6)), (at(0.045), tone(990, 0.14, 0.6))),
    "back": mix((0, tone(600, 0.08, 0.6)), (at(0.045), tone(420, 0.13, 0.6))),
    "tab": tone(520, 0.14, 0.55, glide_to=780),
    "open": tone(330, 0.2, 0.6, glide_to=620),
    "close": tone(620, 0.17, 0.6, glide_to=330),
    "launch": mix(
        (0, tone(523, 0.35, 0.5)),
        (at(0.07), tone(659, 0.35, 0.5)),
        (at(0.14), tone(784, 0.45, 0.5)),
    ),
}

for name, sig in SOUNDS.items():
    sig = sig / max(1e-9, np.abs(sig).max()) * 0.8
    fade = min(sig.size, at(0.01))
    sig[-fade:] *= np.linspace(1, 0, fade)
    pcm = (sig * 32767).astype("<i2")
    with tempfile.NamedTemporaryFile(suffix=".wav") as tmp:
        with wave.open(tmp.name, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-i", tmp.name, "-c:a", "libvorbis", "-q:a", "3",
             str(OUT / f"rift_ui_{name}.ogg")],
            check=True,
        )
    print("wrote", name)
