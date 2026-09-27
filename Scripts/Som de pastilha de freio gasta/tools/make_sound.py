#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
make_sound.py - gera o chiado de pastilha de freio do mod "Som de pastilha de
freio gasta" (CLEO/BrakePadSound/brakepad.wav).

O som do mod original e' do autor (Amilton/Fabio/Junior_Djjr) e nao pode ser
redistribuido aqui, entao este script SINTETIZA um chiado equivalente: uma
ressonancia aguda (~2-4 kHz) com Harmonicos inarmonicos, vibracao de tom
(wobble), tremolo de ~33 Hz, uma camada de ruido de atrito e tres "chirp"s,
que e' a textura tipica de pastilha gasta.

Nao depende de nada: so a biblioteca padrao do Python 3.

    python3 tools/make_sound.py
    python3 tools/make_sound.py --out /tmp/outro.wav --seed 1234
"""

import argparse
import math
import os
import random
import struct
import wave

# taxa baixa de proposito: o jogo tambem roda o som a 22050 Hz, e o arquivo
# fica pequeno (~50 KB) sem perder o brilho do chiado
SAMPLE_RATE = 22050
DURATION = 1.20          # segundos - o suficiente para cobrir um Cooldown de 1100 ms
DEFAULT_SEED = 0xB2A5    # fixo: a mesma build sempre gera o mesmo wav

# os tres chirps: inicio e fim de cada um (segundos)
CHIRPS = ((0.000, 0.300), (0.360, 0.660), (0.720, 1.020))

# harmonicos inarmonicos: (multiplicador, ganho) - o desalinhamento proposital
# (1.0, 2.0, 3.0, 4.02, 5.9) e' o que da o "metal" caracteristico
HARMONICS = ((1.0, 1.00), (2.0, 0.52), (3.0, 0.33), (4.02, 0.20), (5.9, 0.12))


def build_samples(seed=DEFAULT_SEED, sample_rate=SAMPLE_RATE, duration=DURATION):
    rnd = random.Random(seed)
    total = int(sample_rate * duration)
    out = [0.0] * total

    # --- ruido de atrito (branco, depois high-pass por diferenca) ---
    noise = [rnd.uniform(-1.0, 1.0) for _ in range(total + 1)]

    phase = 0.0
    prev_noise = 0.0
    for i in range(total):
        t = i / sample_rate

        # --- tremolo: o "chirp-chirp" da pastilha ---
        tremolo = 0.55 + 0.45 * math.sin(2.0 * math.pi * 33.0 * t + 0.7)
        # --- envelope geral: tres pulsos com folga entre eles ---
        gate = 0.0
        for start, end in CHIRPS:
            if start <= t < end:
                d = t - start
                span = end - start
                # ataque curtinho, decaimento com curva
                attack = min(1.0, d / 0.012)
                decay = max(0.0, 1.0 - d / span) ** 1.5
                gate = max(gate, attack * decay)
        # a fracao final some de verdade (fadeout) para nao estalar no fim
        tail = min(1.0, (duration - t) / 0.06)
        env = tremolo * gate * tail
        if env <= 0.0:
            continue

        # --- tom: wobble de ~7 Hz sobre a ressonancia ---
        f0 = 3200.0 + 1100.0 * math.sin(2.0 * math.pi * 7.0 * t) \
                 + 500.0 * math.sin(2.0 * math.pi * 1.3 * t + 1.0)
        phase += 2.0 * math.pi * f0 / sample_rate

        tone = 0.0
        for mult, gain in HARMONICS:
            tone += gain * math.sin(phase * mult)

        # --- atrito: ruido com um pouco mais de corpo ---
        hiss = noise[i + 1] - prev_noise
        prev_noise = noise[i + 1]
        hiss = hiss * 0.5 + noise[i + 1] * 0.12

        out[i] = (tone * 0.72 + hiss * 0.55) * env

    # normaliza e aplica um fade-in curtinho
    peak = max(abs(v) for v in out) or 1.0
    gain = 0.92 / peak
    fade = max(1, int(sample_rate * 0.005))
    for i, v in enumerate(out):
        v *= gain
        if i < fade:
            v *= i / fade
        out[i] = v
    return out


def write_wav(path, samples, sample_rate=SAMPLE_RATE):
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    frames = bytearray()
    for v in samples:
        v = max(-1.0, min(1.0, v))
        frames += struct.pack("<h", int(v * 32767.0))
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sample_rate)
        w.writeframes(bytes(frames))
    return len(frames)


def main():
    here = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    parser = argparse.ArgumentParser(description="gera o chiado de pastilha de freio")
    parser.add_argument("--out", default=os.path.join(here, "CLEO", "BrakePadSound", "brakepad.wav"))
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    args = parser.parse_args()

    samples = build_samples(seed=args.seed)
    size = write_wav(args.out, samples)
    print("%s (%d bytes, %.2f s, %d Hz, mono)" % (args.out, size, DURATION, SAMPLE_RATE))


if __name__ == "__main__":
    main()
