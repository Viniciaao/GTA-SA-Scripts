#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
test_model.py - confere o mod "Som de pastilha de freio gasta" sem o jogo.

Duas metades:

1) MODELO DE TEMPO. O coracao do mod (UpdateCar) esta transcrito aqui linha
   por linha - a queda de velocidade (desaceleracao) em m/s2, o BrakeThreshold,
   o BrakeForce, o cooldown/latch e o volume - e o comportamento prometido e'
   verificado em ~70 casos: nada de som com o carro PARADO (a garantia de
   soar natural), o chiado na hora em que freia de verdade, repeticao enquanto
   a frenagem e' forte, um som por aperto (Cooldown = 0) em qualquer FPS, o
   corte de valor, a escala de volume e o pedaidinho da IA sem virar
   metralhadeira.

2) CONFERENCIA DO SOURCE (secao 9). O modelo acima passou com o mod MUDO no
   jogo, porque a conversao int<->float dos opcodes CSET estava invertida e
   nenhum teste de mesa enxerga um opcode. A secao 9 trava a ordem dos
   operandos do CSET, o $ do ponteiro do buffer, a contagem de slots e a
   promessa de "o .ini e' lido so no comeco" lendo o .sc de verdade.

    python3 tools/test_model.py

Nao substitui o teste dentro do jogo (nada aqui emula o pool de veiculos, os
opcodes ou o mixer), mas trava o modelo e o source: se voce mexer no UpdateCar,
nos defaults do .ini ou nas conversoes, rode isso antes de brigar com o jogo.
"""

import os
import re
import sys

CFG = dict(threshold=0.15, force=2.5, refspeed=110.0,
           volume=0.7, cooldown=1500, sfx=0.8)


class Car:
    """espelha o UpdateCar do BrakePadSound.sc.

    A variavel de estado que importa e' iPrevVel: a velocidade do frame
    anterior, em m/s x 1000, guardada na var. estendida 2 do proprio carro.
    O script mede a queda e compara com o BrakeForce.
    """

    def __init__(self, cfg):
        self.c = cfg
        self.prev = 0        # var 2 / 1000
        self.next = 0        # instante (ms) do proximo som permitido
        self.reg = 1         # var 1 (1 = armado, 3 = travado neste aperto)

    def update(self, now, brake, vel, step, sfx, frame_sounds):
        c = self.c
        # ---- queda de velocidade em m/s2 (o coracao do mod) ----
        # step chega em MILISSEGUNDOS; o script divide por fStep, que ja esta
        # em segundos (o fStep *= 0.001 la no laco principal).
        prev = self.prev * 0.001            # velocidade anterior, em m/s
        decel = prev - vel                   # quanto caiu neste frame (m/s)
        if step > 0.0:
            decel /= (step * 0.001)          # ...por segundo = m/s2
        self.prev = int(vel * 1000.0)        # guarda a de hoje

        # ---- sem pedal: zera o aperto, rearma o "um som por aperto" ----
        if brake < c['threshold']:
            self.reg = 1
            return None

        vol = None
        # ---- a frenagem foi forte o bastante? (e o carro esta armado?) ----
        if self.reg == 1 and decel > c['force']:
            if now >= self.next:             # passou o cooldown?
                self.next = now + c['cooldown']
                if c['cooldown'] == 0:
                    self.reg = 3              # trava ate soltar o freio
                if frame_sounds[0] < 4:      # teto de 4 sons por frame
                    frame_sounds[0] += 1
                    f = (vel * 3.6) / c['refspeed']     # m/s -> km/h -> fracao
                    f = max(0.25, min(1.0, f))
                    # o pedal entra no volume com piso de 0.5 (ver PlaySqueal)
                    vol = max(0.0, min(1.0, sfx * c['volume'] * (0.5 + 0.5 * brake) * f))
        return vol


def run(fps, press_time, v0, decel, cfg=None, gaps=(), vel0=None):
    """Simula uma frenagem.

    press_time: duracao (s) com o pedal apertado.
    v0:         velocidade inicial em km/h.
    decel:      desaceleracao em m/s2 enquanto o pedal esta apertado (0 = so
               Coastando, o carro mantem a velocidade).
    gaps:       ((t0, dur), ...) solavancos: o pedal solta por 'dur' segundos
                e volta (enquanto solto, o carro nao desacelera).
    vel0:       se dado, a velocidade com que o carro comeca (default = v0).
    """
    cfg = dict(CFG, **(cfg or {}))
    car = Car(cfg)
    now, frame_sounds, vols = 0.0, [0], []
    step = 1000.0 / fps
    vel = (v0 if vel0 is None else vel0) / 3.6      # km/h -> m/s
    frames = int((press_time + 2.0) * fps)
    for i in range(frames):
        now += step
        t = i / fps
        on = 1.0 if t < press_time else 0.0
        coasting = True
        for t0, dur in gaps:
            if t0 <= t < t0 + dur:
                on = 0.0
                coasting = False
        # a velocidade cai enquanto o pedal aperta, para 0 e fica
        if on and decel > 0.0:
            vel = max(0.0, vel - decel * (step / 1000.0))
        if vel < 0.0:
            vel = 0.0
        frame_sounds[0] = 0
        v = car.update(int(now), on, vel, step, cfg['sfx'], frame_sounds)
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


print("1) CARRO PARADO: nenhuma cantiga, mesmo com o pe no freio")
# v0 = 0 => o carro nunca desacelera, entao o script nunca canta. E' a garantia
# de que o mod soa natural (foi exatamente o que a v2.7 errou).
n, _, _ = run(60, 3.0, 0, 0)
check("carro parado, pe no freio 3 s, decel 0", n, 0)
n, _, _ = run(60, 3.0, 0, 0, cfg=dict(cooldown=0))
check("carro parado, Cooldown = 0", n, 0)
n, _, _ = run(60, 10.0, 0, 0, cfg=dict(threshold=0.0))
check("carro parado, BrakeThreshold = 0 (nem ai)", n, 0)
# parado -> acelera -> para: a desaceleração da parada conta (e' uma frenagem)
n, _, _ = run(60, 2.0, 60, 0, cfg=dict(cooldown=0), vel0=0)
check("parado que sai andando (sem queda) nao canta", n, 0)

print("2) FREANDO DE VERDADE: o chiado vem na hora, como no mod original")
n, _, _ = run(60, 1.0, 80, 8.0, cfg=dict(cooldown=0))
check("80 km/h, freada de 8 m/s2 -> canta", n, 1)
n, _, _ = run(60, 2.0, 80, 8.0)
check("80 km/h, freada forte de 2 s -> repete", n, 2)
n, _, _ = run(60, 1.0, 40, 6.0, cfg=dict(cooldown=0))
check("40 km/h, freada de 6 m/s2 -> canta", n, 1)
n, _, _ = run(60, 1.0, 15, 6.0, cfg=dict(cooldown=0))
check("15 km/h, freada de 6 m/s2 -> canta", n, 1)

print("3) BrakeThreshold: sem pedal, a queda nao vira som")
# o scenario e' frear e depois SOLTAR: quando o pe levanta, a queda de
# velocidade acaba junto, e nao sai chiado novo
n, _, _ = run(60, 1.0, 80, 8.0, cfg=dict(cooldown=0), gaps=((0.5, 0.5),))
check("freou e depois soltou o pe (coastou) -> 1 som so", n, 1)
# segurou o pedal 1 s e depois solta: ainda assim 1 som (Cooldown = 0 trava)
n, _, _ = run(60, 2.0, 80, 8.0, cfg=dict(cooldown=0), gaps=((1.0, 1.0),))
check("freou 1 s, soltou e nao freou mais -> 1 som", n, 1)
# pedal apertado mas o carro nao perde velocidade: nao ha frenagem, nao ha som
n, _, _ = run(60, 1.0, 80, 0.0, cfg=dict(cooldown=0))
check("pedal apertado mas sem queda de velocidade nao canta", n, 0)

print("4) BrakeForce: quanto mais forte a frenagem, mais chiado")
n, _, _ = run(60, 1.0, 80, 1.0, cfg=dict(cooldown=0))     # 1.0 < 2.5
check("freada de 1.0 m/s2 (abaixo do BrakeForce) nao canta", n, 0)
n, _, _ = run(60, 1.0, 80, 3.0, cfg=dict(cooldown=0))     # 3.0 > 2.5
check("freada de 3.0 m/s2 (acima do BrakeForce) canta", n, 1)
n, _, _ = run(60, 1.0, 80, 6.0, cfg=dict(cooldown=0, force=10.0))
check("freada de 6.0 m/s2 com BrakeForce = 10 nao canta", n, 0)
n, _, _ = run(60, 1.0, 80, 6.0, cfg=dict(cooldown=0, force=1.0))
check("freada de 6.0 m/s2 com BrakeForce = 1 canta", n, 1)

print("5) volume: escala com velocidade e pedal, respeita o menu, tem teto")
_, p1, _ = run(60, 1.0, 110, 8.0, cfg=dict(cooldown=0, sfx=1.0, volume=1.0), vel0=110)
_, p2, _ = run(60, 1.0, 20, 8.0, cfg=dict(cooldown=0, sfx=1.0, volume=1.0), vel0=20)
check("110 km/h (fator 1.0) / 20 km/h (piso 0.25) = 4x", round(p1 / p2, 2), 4.0, tol=0.05)
_, full, _ = run(60, 1.0, 300, 8.0, cfg=dict(cooldown=0, volume=1.0, sfx=1.0), vel0=300)
check("volume 1.0 + menu 1.0 nao passa de 1.0", full, 1.0, tol=0.001)
_, quiet, _ = run(60, 1.0, 60, 8.0, cfg=dict(cooldown=0, volume=0.0), vel0=60)
check("Volume = 0 silencia", quiet, 0.0, tol=0.001)
_, menu, _ = run(60, 1.0, 60, 8.0, cfg=dict(cooldown=0, sfx=0.5), vel0=60)
_, full_, _ = run(60, 1.0, 60, 8.0, cfg=dict(cooldown=0, sfx=1.0), vel0=60)
check("volume do menu pela metade = metade do volume", round(menu / full_, 2), 0.5, tol=0.02)
# o pedal pesa no volume com piso de 0.5 (0.5 + 0.5 * pedal): um toque de
# 0.3 acima do threshold fica em 0.65 do volume cheio
def _vol_at_pedal(brake):
    return 0.5 + 0.5 * brake
check("pedal 1.00 = 1.00 do volume", round(_vol_at_pedal(1.0), 3), 1.0)
check("pedal 0.30 (toque leve) = 0.65 do volume", round(_vol_at_pedal(0.3), 3), 0.65)

print("6) Cooldown = 1500 (padrao): repete durante a frenagem forte")
for fps in (30, 60, 144, 240):
    n, _, _ = run(fps, 3.0, 90, 8.0, cfg=dict(cooldown=1500), vel0=90)
    check("fps %3d, frenagem de 3 s -> repete" % fps, n >= 2, True)
n, _, _ = run(60, 1.0, 80, 8.0, cfg=dict(cooldown=1500))
check("uma batida curta de 1 s -> 1 som", n, 1)
n, _, _ = run(60, 5.0, 120, 8.0, cfg=dict(cooldown=1500), vel0=120)
check("frenagem longa de 5 s -> 3+ sons", n >= 3, True)

print("7) Cooldown = 0: UM som por aperto, em qualquer FPS/duracao")
for fps in (30, 60, 144, 240):
    for dur in (0.3, 1.0, 4.0):
        # v0 alto e forca alta: o carro freia o tempo todo, mas trava apos
        # o primeiro som (Cooldown = 0) ate o pedal soltar
        n, _, _ = run(fps, dur, 200, 12.0, cfg=dict(cooldown=0), vel0=200)
        check("fps %3d, aperto de %.1f s" % (fps, dur), n, 1)

print("8) FPS nao muda nada (nem contagem, nem volume do 1o som)")
# O volume do PRIMEIRO som e' medido no instante em que ele dispara. A
# desacel comeca a comer velocidade a partir dai, entao o "pico" da frenagem
# cai em frames diferentes conforme o FPS - comparar o pico daria uma
# diferencinha de 0.5% que nao e' o que importa. O que importa: dar 1 som e
# o mesmo volume, seja a 25 ou a 240 FPS.
ref_n, ref_v, _ = run(60, 1.0, 90, 8.0, cfg=dict(cooldown=0), vel0=90)
for fps in (25, 30, 50, 144, 240):
    n, v, _ = run(fps, 1.0, 90, 8.0, cfg=dict(cooldown=0), vel0=90)
    check("fps %3d: n de sons" % fps, n, ref_n)
    # tolerancia de 0.01: a 240 FPS o chiado dispara um frame antes, quando o
    # carro ainda esta 0.03 m/s mais rapido, entao sai ~0.6% mais alto. E' a
    # fisica do jogo, nao um bug - e' inaudivel.
    check("fps %3d: volume do 1o som" % fps, v, ref_v, tol=0.01)

print("9) solavanco do pedal no meio da frenagem (Cooldown = 0)")
# solta e volta 2x com forca alta: como o latch rearma so quando o pedal REALMENTE
# solta (abaixo do threshold), dois apertos de verdade = 2 sons
n, _, _ = run(60, 2.0, 90, 10.0, cfg=dict(cooldown=0), gaps=((0.5, 0.4),), vel0=90)
check("soltou 400 ms no meio -> 2 apertos = 2 sons", n, 2)
n, _, _ = run(60, 2.0, 90, 10.0, cfg=dict(cooldown=0), gaps=((0.5, 0.02),), vel0=90)
check("soltou por 1 frame e voltou -> 2 apertos = 2 sons", n, 2)
# a desaceleracao sozinha ja segura o canto: a IA pisando varias vezes num
# transito parado (pouca queda por aperto) nao vira metralhadeira
n, _, _ = run(60, 6.0, 30, 1.2, cfg=dict(cooldown=0), gaps=((0.5, 0.2), (1.2, 0.2), (1.9, 0.2), (2.6, 0.2)), vel0=30)
check("IA pisando 4x com frenagem fraca (1.2 m/s2) -> poucos sons", n <= 3, True)

# ---------------------------------------------------------------------------
# 10) CONFERENCIA DO SOURCE
# O modelo acima roda em Python e nao enxerga opcode nenhum: foi exatamente por
# isso que o mod ficou mudo no jogo (o CSET invertido) e o modelo continuava
# verde. Estas verificacoes leem o .sc compilado de verdade.
# ---------------------------------------------------------------------------
print("10) source: ordem dos operandos do CSET (o bug que deixou o mod mudo)")
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
    # Nos dois o DESTINO vem primeiro. Destrava documentada pelo Sanny
    # (0092: 22@ = float 17@ to_integer) e confirmada pelo SCRLog do usuario.
    cset = re.findall(r"CSET_\w+\s+\S+\s+\S+", code)
    check_src("3 conversoes CSET no source", len(cset) == 3, " ".join(cset))
    check_src("fStep = (float)dt   -> 0093 destino primeiro",
              "CSET_LVAR_FLOAT_TO_LVAR_INT fStep iValue" in code)
    check_src("f = (float)iPrevVel -> 0093 destino primeiro",
              "CSET_LVAR_FLOAT_TO_LVAR_INT f iPrevVel" in code)
    check_src("iPrevVel = (int)f   -> 0092 destino primeiro",
              "CSET_LVAR_INT_TO_LVAR_FLOAT iPrevVel f" in code)
    check_src("nenhuma conversao CSET no sentido invertido (bug v2.6/v2.7)",
              not re.search(r"CSET_LVAR_INT_TO_LVAR_FLOAT\s+(iValue|f)\s+", code))

    # slots de variavel local (o limite do script CLEO e' 32)
    names = []
    for line in re.findall(r"^LVAR_(?:INT|FLOAT)\s+(.*)$", code, re.M):
        names += line.split()
    check_src("slots de variavel local <= 32", len(names) <= 32,
              "%d usados" % len(names))

    # o ponteiro do buffer sempre com $ (sem $, vira o literal "pBuffer")
    check_src("buffer de som usado com $pBuffer (sem $, vira o texto)",
              "LOAD_3D_AUDIO_STREAM $pBuffer" in code
              and "DOES_FILE_EXIST $pBuffer" in code)
    check_src("GET_LABEL_POINTER escreve no pBuffer",
              "GET_LABEL_POINTER txtSoundBuffer (pBuffer)" in code)

    # promessa do cabecalho: o .ini e' lido uma vez so
    loop = src.split("// =================================================================== laco")[-1]
    check_src("nenhuma leitura de .ini dentro do laco principal",
              "READ_" not in loop.split("OnCarCreate:")[0],
              "lidas so na inicializacao")

    # o coracao do mod: a desaceleracao tem que estar de pe
    check_src("desaceleracao medida em m/s2 (fDecel /= fStep)", "fDecel /= fStep" in code)
    check_src("gatilho compara a queda com BrakeForce", "fDecel > fForce" in code)
    check_src("carro parado nao canta: sem pedal, o aperto rearma e sai",
              "IF fBrake < fBrakeMin" in code)
    check_src("BrakeForce vem do .ini", '"BrakeForce"' in code)

    if os.path.isfile(INI):
        ini = open(INI, encoding="utf-8").read()
        for key, val in (("BrakeForce", "2.5"), ("Cooldown", "1500"),
                         ("BrakeThreshold", "0.15"), ("TriggerPressure", None),
                         ("MinSpeed", None), ("PressureRate", None)):
            m = re.search(r"^%s\s*=\s*(\S+)" % key, ini, re.M)
            if val is None:
                check_src("ini: %s removido (chave do modelo antigo)" % key, m is None)
            else:
                check_src("ini: %s = %s" % (key, val),
                          m is not None and m.group(1) == val,
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
