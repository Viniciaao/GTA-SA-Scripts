#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
test_model.py - confere o mod "Som de pastilha de freio gasta" sem o jogo.

Duas metades:

1) MODELO DE TEMPO. O UpdateCar do BrakePadSound.sc e' transcrito aqui linha
   por linha (pressao que sobe/desce, gatilho, MinSpeed, cooldown/latch e o
   volume) e o comportamento prometido e' verificado em ~70 casos: um som por
   aperto (Cooldown = 0) em qualquer FPS, repeticao enquanto o freio esta
   apertado, o corte de velocidade, o BrakeThreshold, a escala de volume (pedal,
   velocidade, volume do menu, teto), PressureRate/TriggerPressure em
   milissegundos, e o pedaidinho da IA sem virar metralhadeira.

2) CONFERENCIA DO SOURCE (secao 10). O modelo acima passou por anos com o mod
   MUDO no jogo, porque a conversao int<->float dos opcodes CSET estava
   invertida e nenhum teste de mesa enxerga um opcode. A secao 10 trava a
   ordem dos operandos do CSET, o $ do ponteiro do buffer, a contagem de slots
   e a promessa de "o .ini e' lido so no comeco" lendo o .sc de verdade.

    python3 tools/test_model.py

Nao substitui o teste dentro do jogo (nada aqui emula o pool de veiculos, os
opcodes ou o mixer), mas trava o modelo e o source: se voce mexer no UpdateCar,
nos defaults do .ini ou nas conversoes, rode isso antes de brigar com o jogo.
"""

import os
import re
import sys

CFG = dict(threshold=0.15, trigger=0.08, rate=5.0, refspeed=110.0,
           minspeed=10.0, volume=0.7, cooldown=1500, sfx=0.8)


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
        # ---- velocidade minima (MinSpeed, em km/h) ----
        if kmh < c['minspeed']:
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
                # o pedal entra no volume com piso de 0.5 (ver PlaySqueal)
                vol = max(0.0, min(1.0, sfx * c['volume'] * (0.5 + 0.5 * brake) * f))
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


def check_src(desc, ok, detail=""):
    print(("  ok    " if ok else "  FALHA ") + "%-56s %s" % (desc, detail))
    if not ok:
        fails.append(desc)


print("1) Cooldown = 0 (modo v2.5.1): UM som por aperto, em qualquer FPS/duracao")
for fps in (30, 60, 144, 240):
    for dur in (0.2, 1.0, 4.0, 12.0):
        n, _, _ = run(fps, dur, 60.0, cfg=dict(cooldown=0))
        check("fps %3d, aperto de %.1f s" % (fps, dur), n, 1)

print("2) Cooldown = 1500 (padrao): repete enquanto o freio estiver apertado")
for fps in (30, 60, 144, 240):
    n, _, _ = run(fps, 3.6, 60.0)
    check("fps %3d, aperto de 3.6 s -> 3 sons" % fps, n, 3)
    n, _, _ = run(fps, 1.0, 60.0)
    check("fps %3d, aperto de 1.0 s -> 1 som" % fps, n, 1)
    n, _, _ = run(fps, 2.0, 60.0)
    check("fps %3d, aperto de 2.0 s -> 2 sons" % fps, n, 2)
    n, _, _ = run(fps, 5.0, 60.0)
    check("fps %3d, aperto de 5.0 s -> 4 sons" % fps, n, 4)

print("3) MinSpeed: parado/capitando devagar nao canta")
n, _, _ = run(60, 2.0, 5.0)
check("5 km/h", n, 0)
n, _, _ = run(60, 2.0, 9.9)
check("9.9 km/h (logo abaixo do MinSpeed = 10)", n, 0)
n, _, _ = run(60, 2.0, 10.1)
check("10.1 km/h (logo acima do MinSpeed)", n, 2)
n, _, _ = run(60, 2.0, 300.0)
check("300 km/h (acima de RefSpeed, nao estoura)", n, 2)
n, _, _ = run(60, 2.0, 0.0, cfg=dict(cooldown=0, minspeed=0.0))
check("MinSpeed = 0 canta parado (modo de teste)", n, 1)

print("4) BrakeThreshold: pedal fraco nao acumula pressao")
n, _, _ = run(60, 3.0, 60.0, pedal=0.10)
check("pedal 0.10 (abaixo do threshold 0.15) por 3 s", n, 0)
n, _, _ = run(60, 3.0, 60.0, pedal=0.20)
check("pedal 0.20 por 3 s -> 2 sons (o 3ro morre no decaimento)", n, 2)

print("5) volume: escala com pedal e velocidade, respeita o menu, tem teto")
_, p1, _ = run(60, 1.0, 60.0, pedal=1.0, cfg=dict(cooldown=0))
_, p2, _ = run(60, 1.0, 60.0, pedal=0.5, cfg=dict(cooldown=0))
check("pedal 1.0 (1.00) / pedal 0.5 (0.75) = 1.33x", round(p1 / p2, 2), 1.33, tol=0.02)
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
_, light, _ = run(60, 1.0, 60.0, pedal=0.2, cfg=dict(cooldown=0, sfx=1.0, volume=1.0))
_, hard, _ = run(60, 1.0, 60.0, pedal=1.0, cfg=dict(cooldown=0, sfx=1.0, volume=1.0))
check("toque leve (pedal 0.2) fica em >= 50% do pedal cheio",
      1.0 if light / hard >= 0.5 else round(light / hard, 3), 1.0)

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
# 2.5 s de aperto: o 2o chiado cai em 1.5 s, longe da fronteira. Numa aperto
# que termisse exatamente em 3.0 s (0 s, 1.5 s, 3.0 s) a contagem poderia
# diferir em 1 entre 30 e 60 fps por causa do arredondamento para ms inteiro do
# GET_GAME_TIMER - e' um som a mais ou a menos numa frenagem de 3 s, nao uma
# diferenca perceptivel.
ref = run(60, 2.5, 70.0)
for fps in (25, 30, 50, 144, 240):
    n, peak, avg = run(fps, 2.5, 70.0)
    check("fps %3d: n de sons" % fps, n, ref[0])
    check("fps %3d: volume" % fps, round(peak, 4), round(ref[1], 4), tol=0.001)

print("8) solavanco do pedal no meio da frenagem (Cooldown = 0)")
n, _, _ = run(60, 1.0, 60.0, cfg=dict(cooldown=0), gaps=((0.5, 0.02),))
check("soltou por 1 frame (20 ms) -> o piso de 250 ms segura (1 som)", n, 1)
n, _, _ = run(60, 2.0, 60.0, cfg=dict(cooldown=0), gaps=((0.5, 0.4),))
check("soltou por 400 ms -> 2 apertos = 2 sons", n, 2)
n, _, _ = run(60, 4.0, 60.0, cfg=dict(cooldown=0), gaps=((0.5, 0.05), (1.0, 0.05), (1.5, 0.05), (2.0, 0.05)))
check("IA pisando 5x (50 ms cada) -> 1 som so (o latch segura)", n, 1)
n, _, _ = run(60, 4.0, 60.0, cfg=dict(cooldown=0), gaps=((0.5, 0.4), (1.5, 0.4), (2.5, 0.4)))
check("IA pisando 3x (400 ms cada) -> 4 sons (um por aperto de verdade)", n, 4)
n, _, avg1 = run(60, 1.0, 60.0, cfg=dict(cooldown=0), gaps=((0.5, 0.02),))
print("       (volume medio do caso acima: %.3f)" % avg1)

# ---------------------------------------------------------------------------
# 10) CONFERENCIA DO SOURCE
# O modelo acima roda em Python e nao enxerga opcode nenhum: foi exatamente por
# isso que o mod ficou mudo no jogo (o CSET invertido mantinha fStep = 0.0) e o
# modelo continuava verde. Estas verificacoes leem o .sc compilado de verdade.
# ---------------------------------------------------------------------------
print("9) source: ordem dos operandos do CSET (o bug que deixou o mod mudo)")
HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "CLEO", "BrakePadSound.sc")
INI = os.path.join(HERE, "..", "CLEO", "BrakePadSound.ini")

if not os.path.isfile(SRC):
    print("  FALHA %-56s %s" % ("BrakePadSound.sc nao encontrado", SRC))
    fails.append("source ausente")
else:
    src = open(SRC, encoding="utf-8").read()
    code = "\n".join(l for l in src.split("\n")
                     if not l.strip().startswith(("//", "*", "/*")))

    # 0092 = CSET_LVAR_INT_TO_LVAR_FLOAT  -> NA VERDADE float -> int
    # 0093 = CSET_LVAR_FLOAT_TO_LVAR_INT  -> NA VERDADE int  -> float
    # Nos dois o DESTINO vem primeiro. Destravado pela documentacao do Sanny
    # (0092: 22@ = float 17@ to_integer) e confirmado pelo SCRLog do usuario.
    cset = re.findall(r"CSET_\w+\s+\S+\s+\S+", code)
    check_src("3 conversoes CSET no source", len(cset) == 3, " ".join(cset))
    check_src("fStep = (float)dt   -> 0093 destino primeiro",
              "CSET_LVAR_FLOAT_TO_LVAR_INT fStep iValue" in code)
    check_src("fPress = (float)var2 -> 0093 destino primeiro",
              "CSET_LVAR_FLOAT_TO_LVAR_INT fPress iPress" in code)
    check_src("var2 = (int)pressao -> 0092 destino primeiro",
              "CSET_LVAR_INT_TO_LVAR_FLOAT iPress f" in code)
    check_src("nenhuma conversao CSET no sentido invertido (o bug do v2.6)",
              not re.search(r"CSET_LVAR_INT_TO_LVAR_FLOAT\s+(iValue|fPress)\s+", code))

    # slots de variavel local (o limite do script CLEO e' 32)
    decl = re.search(r"^LVAR_INT(.*?)$", code, re.M)
    names = []
    for line in re.findall(r"^LVAR_(?:INT|FLOAT)\s+(.*)$", code, re.M):
        names += line.split()
    check_src("slots de variavel local <= 32", len(names) <= 32,
              "%d usados: %s" % (len(names), " ".join(names)))

    # o ponteiro do buffer sempre com $ (sem $, vira o literal "pBuffer")
    check_src("buffer de som usado com $pBuffer (sem $, vira o texto 'pBuffer')",
              "LOAD_3D_AUDIO_STREAM $pBuffer" in code
              and "DOES_FILE_EXIST $pBuffer" in code)
    check_src("GET_LABEL_POINTER escreve no pBuffer",
              "GET_LABEL_POINTER txtSoundBuffer (pBuffer)" in code)

    # promessa do cabecalho: o .ini e' lido uma vez so
    loop = src.split("// =================================================================== laco")[-1]
    check_src("nenhuma leitura de .ini dentro do laco principal",
              "READ_" not in loop.split("OnCarCreate:")[0],
              "lidas so na inicializacao")
    check_src("MinSpeed vem do .ini", '"MinSpeed"' in code)

    if os.path.isfile(INI):
        ini = open(INI, encoding="utf-8").read()
        for key, val in (("MinSpeed", "10.0"), ("Cooldown", "1500"),
                         ("TriggerPressure", "0.08"), ("BrakeThreshold", "0.15")):
            m = re.search(r"^%s\s*=\s*(\S+)" % key, ini, re.M)
            check_src("ini: %s = %s" % (key, val), m is not None and m.group(1) == val,
                      "" if m is None else m.group(1))
        check_src("ini: arquivo de som e' wav ou mp3 documentado",
                  re.search(r"^SoundFile\s*=\s*\S+\.(wav|mp3)", ini, re.M) is not None)
    else:
        check_src("BrakePadSound.ini nao encontrado", False, INI)

print()
if fails:
    print("FALHARAM %d verificacoes: %s" % (len(fails), fails))
    sys.exit(1)
print("todas as verificacoes do modelo passaram")
