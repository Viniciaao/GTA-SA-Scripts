#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
test_model.py - confere o MODELO DE TEMPO do mod "Som de pastilha de freio
gasta" sem precisar do jogo.

O UpdateCar do BrakePadSound.sc e' transcrito aqui linha por linha (pressao que
sobe/desce, gatilho, porta de velocidade, cooldown/latch e o volume), e o
comportamento que o mod promete e' verificado em 60 casos: um som por aperto
(Cooldown = 0) em qualquer FPS, repeticao enquanto o freio esta apertado
(Cooldown = 1100), o corte de velocidade, o BrakeThreshold, a escala de volume
(pedal, velocidade, volume do menu, teto), PressureRate/TriggerPressure em
milissegundos, e o pedaidinho da IA sem virar metralhadeira.

    python3 tools/test_model.py

Nao substitui o teste dentro do jogo (nada aqui emula o pool de veiculos, os
opcodes ou o mixer), mas trava o modelo de tempo: se voce mexer no
UpdateCar ou nos defaults do .ini, rode isso antes de brigar com o jogo.
"""

import sys

CFG = dict(threshold=0.15, trigger=0.08, rate=5.0, refspeed=110.0,
           volume=0.7, cooldown=1100, sfx=0.8)


class Car:
    def __init__(self, cfg):
        self.c = cfg
        self.press = 0.0      # var 2 / 1000
        self.next = 0         # var 3 (instante do ultimo som)
        self.reg = 1          # var 1 (1 = armado, 3 = travado)

    def update(self, now, brake, kmh, step, sfx, frame_sounds):
        """espelha o UpdateCar do BrakePadSound.sc (iReg = 1 armado, 3 travado)"""
        c = self.c
        fstep = step * 0.001 * c['rate']
        # ---- pressao ----
        if brake > c['threshold']:
            self.press += fstep * brake
        else:
            self.press -= fstep * 2
        self.press = max(0.0, min(1.0, self.press))
        # ---- velocidade ----
        if kmh / c['refspeed'] < 0.12:
            self.press = 0.0
        # ---- destrava (so com Cooldown = 0) ----
        if self.reg == 3 and self.press <= c['trigger'] and (now - self.next) >= 250:
            self.reg = 1
            self.press = 0.0
        vol = None
        if self.press > c['trigger'] and self.reg == 1 and now > self.next:
            self.next = now
            if c['cooldown'] == 0:
                self.reg = 3
            else:
                self.next += c['cooldown']
            if frame_sounds[0] < 4:      # teto de 4 sons por frame
                frame_sounds[0] += 1
                f = kmh / c['refspeed']
                f = max(0.25, min(1.0, f))
                vol = max(0.0, min(1.0, sfx * c['volume'] * brake * f))
        return vol


def run(fps, press_time, kmh, pedal=1.0, cfg=None, gaps=()):
    """press_time: duracao total apertando. gaps: ((t0, dur), ...) solavancos
    dentro do aperto (o pedal solta por 'dur' segundos e volta)."""
    cfg = dict(CFG, **(cfg or {}))
    car = Car(cfg)
    now, frame_sounds, vols = 0.0, [0], []
    step = 1000.0 / fps
    frames = int((press_time + 2.0) * fps)
    for i in range(frames):
        now += step
        t = i / fps
        on = pedal if t < press_time else 0.0
        for t0, dur in gaps:
            if t0 <= t < t0 + dur:
                on = 0.0
        frame_sounds[0] = 0
        v = car.update(int(now), on, kmh, step, cfg['sfx'], frame_sounds)
        if v is not None:
            vols.append(v)
    return len(vols), (max(vols) if vols else 0.0), (sum(vols) / len(vols) if vols else 0.0)


fails = []


def check(desc, got, want, tol=None):
    ok = (abs(got - want) <= tol) if tol is not None else (got == want)
    print(("  ok    " if ok else "  FALHA ") + "%-56s got=%s want=%s" % (desc, got, want))
    if not ok:
        fails.append(desc)


print("1) Cooldown = 0 (modo v2.5.1): UM som por aperto, em qualquer FPS/duracao")
for fps in (30, 60, 144, 240):
    for dur in (0.2, 1.0, 4.0, 12.0):
        n, _, _ = run(fps, dur, 60.0, cfg=dict(cooldown=0))
        check("fps %3d, aperto de %.1f s" % (fps, dur), n, 1)

print("2) Cooldown = 1100 (padrao): repete enquanto o freio estiver apertado")
for fps in (30, 60, 144, 240):
    n, _, _ = run(fps, 3.6, 60.0)
    check("fps %3d, aperto de 3.6 s -> 4 sons" % fps, n, 4)
    n, _, _ = run(fps, 1.0, 60.0)
    check("fps %3d, aperto de 1.0 s -> 1 som" % fps, n, 1)
    n, _, _ = run(fps, 2.0, 60.0)
    check("fps %3d, aperto de 2.0 s -> 2 sons" % fps, n, 2)

print("3) porta de velocidade: parado/capitando devagar nao canta")
n, _, _ = run(60, 2.0, 5.0)
check("5 km/h", n, 0)
n, _, _ = run(60, 2.0, 12.0)
check("12 km/h (corte e' 13.2)", n, 0)
n, _, _ = run(60, 2.0, 14.0)
check("14 km/h (logo acima do corte)", n, 2)
n, _, _ = run(60, 2.0, 300.0)
check("300 km/h (acima de RefSpeed, nao estoura)", n, 2)

print("4) BrakeThreshold: pedal fraco nao acumula pressao")
n, _, _ = run(60, 3.0, 60.0, pedal=0.10)
check("pedal 0.10 (abaixo do threshold 0.15) por 3 s", n, 0)
n, _, _ = run(60, 3.0, 60.0, pedal=0.20)
check("pedal 0.20 por 3 s -> 3 sons", n, 3)

print("5) volume: escala com pedal e velocidade, respeita o menu, tem teto")
_, p1, _ = run(60, 1.0, 60.0, pedal=1.0, cfg=dict(cooldown=0))
_, p2, _ = run(60, 1.0, 60.0, pedal=0.5, cfg=dict(cooldown=0))
check("pedal 1.0 = 2x o volume do pedal 0.5", round(p1 / p2, 2), 2.0, tol=0.05)
_, low, _ = run(60, 1.0, 20.0, cfg=dict(cooldown=0))
_, high, _ = run(60, 1.0, 110.0, cfg=dict(cooldown=0))
check("110 km/h (fator 1.0) / 20 km/h (piso 0.25) = 4x", round(high / low, 2), 4.0, tol=0.05)
_, full, _ = run(60, 1.0, 200.0, cfg=dict(cooldown=0, volume=1.0, sfx=1.0))
check("volume 1.0 + menu 1.0 nao passa de 1.0", full, 1.0, tol=0.001)
_, quiet, _ = run(60, 1.0, 110.0, cfg=dict(cooldown=0, volume=0.0))
check("Volume = 0 silencia", quiet, 0.0, tol=0.001)
_, menu, _ = run(60, 1.0, 110.0, cfg=dict(cooldown=0, sfx=0.5))
_, full_, _ = run(60, 1.0, 110.0, cfg=dict(cooldown=0, sfx=1.0))
check("volume do menu pela metade = metade do volume", round(menu / full_, 2), 0.5, tol=0.02)

print("6) PressureRate/TriggerPressure: o tempo e' em ms, nao em frames")
n, _, _ = run(240, 0.010, 60.0, cfg=dict(cooldown=0))
check("toque de 10 ms a 240 fps nao cruza o gatilho (0.08)", n, 0)
n, _, _ = run(240, 0.030, 60.0, cfg=dict(cooldown=0))
check("toque de 30 ms a 240 fps cruza", n, 1)
n, _, _ = run(60, 0.200, 60.0, cfg=dict(cooldown=0))
check("toque de 200 ms com pedal 1.0 (gatilho em 16 ms)", n, 1)
n, _, _ = run(60, 0.050, 60.0, cfg=dict(cooldown=0, rate=1.0))
check("toque de 50 ms com PressureRate = 1.0 nao chega (precisa 80 ms)", n, 0)
n, _, _ = run(60, 0.100, 60.0, cfg=dict(cooldown=0, rate=1.0))
check("toque de 100 ms com PressureRate = 1.0 chega", n, 1)
n, _, _ = run(60, 0.100, 60.0, cfg=dict(cooldown=0, trigger=0.95))
check("toque de 100 ms com TriggerPressure = 0.95 nao chega (0.5 acumulado)", n, 0)
n, _, _ = run(60, 0.300, 60.0, cfg=dict(cooldown=0, trigger=0.95))
check("toque de 300 ms com TriggerPressure = 0.95 chega", n, 1)

print("7) FPS nao muda nada (nem contagem, nem volume)")
ref = run(60, 3.0, 70.0)
for fps in (25, 30, 50, 144, 240):
    n, peak, avg = run(fps, 3.0, 70.0)
    check("fps %3d: n de sons" % fps, n, ref[0])
    check("fps %3d: volume" % fps, round(peak, 4), round(ref[1], 4), tol=0.001)

print("8) solavanco do pedal no meio da frenagem (Cooldown = 0)")
n, _, _ = run(60, 1.0, 60.0, cfg=dict(cooldown=0), gaps=((0.5, 0.02),))
check("soltou por 1 frame (20 ms) -> o piso de 250 ms segura (1 som)", n, 1)
n, _, _ = run(60, 2.0, 60.0, cfg=dict(cooldown=0), gaps=((0.5, 0.4),))
check("soltou por 400 ms -> 2 apertos = 2 sons", n, 2)
n, _, _ = run(60, 4.0, 60.0, cfg=dict(cooldown=0), gaps=((0.5, 0.05), (1.0, 0.05), (1.5, 0.05), (2.0, 0.05)))
check("IA pisando 5x (50 ms cada) -> 1 som so (o latch segura, sem metralhadeira)", n, 1)
n, _, _ = run(60, 4.0, 60.0, cfg=dict(cooldown=0), gaps=((0.5, 0.4), (1.5, 0.4), (2.5, 0.4)))
check("IA pisando 3x (400 ms cada) -> 4 sons (um por aperto de verdade)", n, 4)
n, _, _ = run(60, 1.0, 60.0, cfg=dict(cooldown=0), gaps=((0.5, 0.02),))
_, _, avg1 = run(60, 1.0, 60.0, cfg=dict(cooldown=0), gaps=((0.5, 0.02),))
print("       (volume medio do caso acima: %.3f)" % avg1)

print()
if fails:
    print("FALHARAM %d verificacoes: %s" % (len(fails), fails))
    sys.exit(1)
print("todas as verificacoes do modelo passaram")
