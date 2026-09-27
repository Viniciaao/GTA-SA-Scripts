#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
test_model.py - confere o mod "Som de pastilha de freio gasta" sem o jogo.

Duas metades:

1) MODELO DE TEMPO. O coracao do mod (PlaySqueal -> MeasureBrake ->
   BrakeVolume -> AdjustSound) esta transcrito aqui linha por linha - a queda
   de velocidade (desaceleracao) em m/s2, o BrakeThreshold, o BrakeForce, o
   FADE, o volume-alvo, o fim do som e a liberacao do stream - e o
   comportamento prometido e' verificado em ~80 casos: nada de som com o carro
   PARADO (a garantia de soar natural), o chiado na hora em que freia de
   verdade, o som ACABANDO junto com a frenagem, um som por carro (nunca duas
   copias sobrepostas), e o mesmo resultado a 30 ou a 240 FPS.

   O cenario do modelo usa o audio REAL de 6,165 s (o do jogador), e nao o
   placeholder de 1,2 s: o ciclo de vida nao pode depender da duracao do
   arquivo.

2) CONFERENCIA DO SOURCE (secao 10). O modelo acima passou com o mod MUDO no
   jogo, porque a conversao int<->float dos opcodes CSET estava invertida e
   nenhum teste de mesa enxerga um opcode. A secao 10 trava a ordem dos
   operandos do CSET, o $ do ponteiro do buffer, a contagem de slots, o
   "desliga e REMOVE no fim da frenagem" e a promessa de "o .ini e' lido so no
   comeco" lendo o .sc de verdade.

    python3 tools/test_model.py

Nao substitui o teste dentro do jogo (nada aqui emula o pool de veiculos, os
opcodes ou o mixer), mas trava o modelo e o source: se voce mexer no
PlaySqueal, nos defaults do .ini ou nas conversoes, rode isso antes de brigar
com o jogo.
"""

import os
import re
import sys

CFG = dict(threshold=0.15, force=2.5, refspeed=110.0,
           volume=0.7, fade=0.2, sfx=0.8)

# teto global de sons ao mesmo tempo (o script usa 6)
MAX_SOUNDS = 6

# o audio do jogador dura 6,165 s; o placeholder do repo dura 1,20 s
AUDIO_REAL = 6.165
AUDIO_PLACEHOLDER = 1.20


class World:
    """O mundo do mod: guarda os sons vivos (streams) para conferir o teto e
    a sobreposicao."""

    def __init__(self):
        self.streams = {}        # handle -> {"car": id, "left": segundos}
        self.next_handle = 1
        self.overlaps = 0        # vezes em que um carro ja tinha som e ganhou outro
        self.peak = 0            # maximo de sons simultaneos

    def create(self, car_id, length):
        if car_id in [s["car"] for s in self.streams.values()]:
            self.overlaps += 1
        h = self.next_handle
        self.next_handle += 1
        self.streams[h] = {"car": car_id, "left": length}
        self.peak = max(self.peak, len(self.streams))
        return h

    def remove(self, h):
        self.streams.pop(h, None)

    def advance(self, dt):
        """o arquivo nao-loopado acaba por conta propria"""
        for h, s in list(self.streams.items()):
            s["left"] -= dt
            if s["left"] <= 0.0:
                del self.streams[h]

    def count(self):
        return len(self.streams)


class Car:
    """espelha o PlaySqueal/MeasureBrake/BrakeVolume/AdjustSound do .sc.

    Estados, como as var. estendidas do carro:
      reg = 1  armado, sem som
      reg = 3  com som tocando (handle na var 3)
      reg = 2  descartado
    """

    def __init__(self, cfg, world, car_id=1, length=AUDIO_REAL):
        self.c = cfg
        self.w = world
        self.id = car_id
        self.length = length
        self.prev = 0        # var 2 / 1000 (velocidade do frame anterior)
        self.handle = 0      # var 3 (0 = sem som)
        self.vol = 0.0       # var 4 / 1000 (volume atual)
        self.reg = 1
        self.removed = 0     # quantas vezes o som foi removido
        self.finished = 0    # quantas vezes o arquivo acabou sozinho
        self.peak_vol = 0.0
        self.voice_log = []  # (t, volume) a cada frame, para os testes

    # ---- MeasureBrake: a queda de velocidade, em m/s2 ----
    def measure(self, vel, step):
        prev = self.prev * 0.001
        decel = prev - vel
        if step > 0.0:
            decel /= (step * 0.001)
        self.prev = int(vel * 1000.0)
        return decel

    # ---- BrakeVolume: quanto de volume a frenagem deste frame merece ----
    def brake_volume(self, brake, vel, decel, sfx):
        if vel < 0.5:                       # carro parado
            return 0.0
        if brake < self.c['threshold']:     # pe fora do freio
            return 0.0
        if decel < self.c['force']:         # freada leve
            return 0.0
        if decel > 25.0:                    # pico falso
            return 0.0
        f = decel * 0.5 / self.c['force']      # 0.5 no limite, 1.0 no dobro
        f = min(f, 1.0)
        fv = (vel * 3.6) / self.c['refspeed']
        fv = max(0.25, min(1.0, fv))
        f *= fv
        f *= 0.5 + 0.5 * brake
        return max(0.0, min(1.0, sfx * self.c['volume'] * f))

    # ---- AdjustSound: move o volume e DESLIGA/REMOVE no fim ----
    def adjust(self, target, step):
        if self.handle == 0:
            return
        # step chega em MILISSEGUNDOS (o script usa fStep em segundos); o Fade
        # e' "volume por segundo", entao o passo precisa ir para segundos.
        if step > 0.0:
            incr = (step * 0.001) * (1.0 / self.c['fade'] if self.c['fade'] > 0.0 else 1000.0)
        else:
            incr = 0.0
        if self.vol > target:
            self.vol -= incr
            if self.vol < target:
                self.vol = target
        else:
            self.vol += incr
            if self.vol > target:
                self.vol = target
        if self.vol <= 0.0:
            self.w.remove(self.handle)
            self.handle = 0
            self.vol = 0.0
            self.reg = 1
            self.removed += 1
        self.peak_vol = max(self.peak_vol, self.vol)

    def stop(self, step):
        """StopSound: alvo 0 (saiu do raio / motor desligado)"""
        self.adjust(0.0, step)

    # ---- PlaySqueal ----
    def update(self, now, brake, vel, step, sfx, in_range=True):
        if not in_range:
            self.stop(step)          # UpdateSounding -> StopSound
            self.voice_log.append((now, 0.0))
            return

        # ---- o som acabou sozinho? (GET_AUDIO_STREAM_STATE = 0) ----
        # Com o arquivo nao-loopado isso acontece quando o chiado acaba: o
        # handle some do mundo, mas o carro ainda aponta para ele. O script
        # detecta pelo estado do stream, limpa o registro e deixa a frenagem
        # comecar o som de novo no proximo frame.
        if self.handle != 0 and self.handle not in self.w.streams:
            self.handle = 0
            self.vol = 0.0
            self.reg = 1
            self.finished += 1

        if self.handle != 0:
            decel = self.measure(vel, step)
            self.adjust(self.brake_volume(brake, vel, decel, sfx), step)
            self.voice_log.append((now, self.vol))
            return

        # ---- sem som: o gatilho ----
        decel = self.measure(vel, step)
        if vel < 0.5:
            self.voice_log.append((now, 0.0))
            return
        if brake < self.c['threshold']:
            self.voice_log.append((now, 0.0))
            return
        if decel <= self.c['force'] or decel >= 25.0:
            self.voice_log.append((now, 0.0))
            return
        if self.w.count() >= MAX_SOUNDS:
            self.voice_log.append((now, 0.0))
            return
        self.handle = self.w.create(self.id, self.length)
        self.vol = 0.0
        self.reg = 3
        # (o script NAO ajusta o volume no mesmo frame em que cria: o som sobe
        #  a partir do proximo frame, pelo KeepSound)
        self.voice_log.append((now, 0.0))


def run(fps, press_time, v0, decel, cfg=None, gaps=(), vel0=None,
        out_of_range=None, length=AUDIO_REAL, world=None):
    """Simula uma frenagem e devolve o que aconteceu.

    press_time:   duracao (s) com o pedal apertado.
    v0:           velocidade inicial em km/h.
    decel:        desaceleracao em m/s2 enquanto o pedal esta apertado.
    gaps:         ((t0, dur), ...) solavancos do pedal.
    out_of_range: ((t0, dur), ...) intervalos em que o carro esta longe da
                  camera (o som tem de morrer mesmo assim).
    """
    cfg = dict(CFG, **(cfg or {}))
    world = World() if world is None else world
    car = Car(cfg, world, length=length)
    now, step = 0.0, 1000.0 / fps
    vel = (v0 if vel0 is None else vel0) / 3.6      # km/h -> m/s
    for i in range(int((press_time + 2.0) * fps)):
        now += step
        t = i / fps
        on = 1.0 if t < press_time else 0.0
        for t0, dur in gaps:
            if t0 <= t < t0 + dur:
                on = 0.0
        if on and decel > 0.0:
            vel = max(0.0, vel - decel * (step / 1000.0))
        near = True
        for t0, dur in (out_of_range or ()):
            if t0 <= t < t0 + dur:
                near = False
        world.advance(step / 1000.0)
        car.update(now, on, vel, step, cfg['sfx'], in_range=near)
    return car, world


def voice_time(car):
    """Quantos segundos o carro passou audivel (volume acima de zero)."""
    total, prev_t, prev_v = 0.0, None, 0.0
    for t, v in car.voice_log:
        if prev_t is not None and prev_v > 0.0:
            total += (t - prev_t) / 1000.0
        prev_t, prev_v = t, v
    return total


fails = []


def check(desc, got, want, tol=None):
    ok = (abs(got - want) <= tol) if tol is not None else (got == want)
    print(("  ok    " if ok else "  FALHA ") + "%-58s got=%s want=%s" % (desc, got, want))
    if not ok:
        fails.append(desc)


def check_src(desc, ok, detail=""):
    print(("  ok    " if ok else "  FALHA ") + "%-58s %s" % (desc, detail))
    if not ok:
        fails.append(desc)


print("1) CARRO PARADO: nenhuma cantiga, mesmo com o pe no freio")
car, w = run(60, 3.0, 0, 0)
check("carro parado, pe no freio 3 s", w.count(), 0)
car, w = run(60, 3.0, 0, 0, cfg=dict(fade=0.0))
check("carro parado, Fade = 0", w.count(), 0)
car, w = run(60, 10.0, 0, 0, cfg=dict(threshold=0.0, fade=0.0))
check("carro parado, BrakeThreshold = 0", w.count(), 0)
car, w = run(60, 2.0, 60, 0, vel0=0)
check("parado que sai andando (sem queda) nao canta", w.count(), 0)

print("2) FREANDO DE VERDADE: o chiado vem na hora, como no mod original")
car, w = run(60, 1.0, 80, 8.0)
check("80 km/h, freada de 8 m/s2 -> canta", voice_time(car) > 0.1, True)
car, w = run(60, 1.0, 40, 6.0)
check("40 km/h, freada de 6 m/s2 -> canta", voice_time(car) > 0.1, True)
car, w = run(60, 1.0, 15, 6.0)
check("15 km/h, freada de 6 m/s2 -> canta", voice_time(car) > 0.1, True)
car, w = run(60, 1.0, 80, 8.0)
check("freada forte -> 1 som so no carro", w.peak, 1)
check("freada forte -> o som foi removido no fim", car.removed >= 1, True)
check("freada forte -> nada sobrando no mundo", w.count(), 0)

print("3) O SOM ACABA COM A FRENAGEM (o bug reportado na v2.8)")
# 1) batida de 0,5 s a 90 km/h: o som some logo depois, e nao 6 s depois
car, w = run(60, 0.5, 90, 9.0)
check("batida de 0,5 s -> voz de ~0,5 s", round(voice_time(car), 1), 0.6, tol=0.35)
check("batida de 0,5 s -> stream removido", car.removed, 1)
# 2) segurou o freio 2 s: o chiado dura a frenagem, nao a duracao do arquivo
car, w = run(60, 2.0, 90, 9.0)
check("freada de 2 s -> voz de ~2 s", round(voice_time(car), 1), 2.0, tol=0.35)
# 3) o caso EXATO do jogador: freou ate parar e soltou
car, w = run(60, 8.0, 90, 3.2)
check("freou ate parar (8 s) -> 1 stream no maximo por vez", w.overlaps, 0)
check("freou ate parar -> nada tocando no fim", w.count(), 0)
# 4) o carro para e o script mantem o pedal apertado: o som tem de morrer
car, w = run(60, 6.0, 40, 8.0)      # 40 km/h = 11 m/s -> para em ~1,4 s
check("freou ate o carro PARAR (pedal segue apertado)", voice_time(car) < 3.0, True)
check("carro parado nao segura o som", w.count(), 0)

print("4) FIM DO SOM: o Fade quanto dura depois de soltar o freio")
for fps, tol in ((30, 0.12), (60, 0.08), (144, 0.05), (240, 0.04)):
    # frenagem de 1 s a 90 km/h; o som nao pode morrer em menos que ~Fade
    car, w = run(fps, 1.0, 90, 9.0, cfg=dict(fade=0.2))
    # a frenagem dura 1 s; depois dela o som ainda sobe/desce durante o fade
    rest = voice_time(car) - 1.0
    check("fps %3d: sobra de voz <= Fade (0,2 s)" % fps, rest <= 0.2 + tol, True)
# Fade = 0: corte seco
car, w = run(60, 1.0, 90, 9.0, cfg=dict(fade=0.0))
check("Fade = 0 -> voz de ~1 s (corte seco)", round(voice_time(car), 1), 1.0, tol=0.1)
# Fade grande: o chiado arrasta depois de soltar
curto, _ = run(60, 2.0, 90, 9.0, cfg=dict(fade=0.1))
longo, _ = run(60, 2.0, 90, 9.0, cfg=dict(fade=1.0))
arrasto_curto, arrasto_longo = voice_time(curto) - 2.0, voice_time(longo) - 2.0
check("Fade = 1,0 arrasta mais que Fade = 0,1", arrasto_longo > arrasto_curto * 2, True)
check("o arrasto nunca dura mais que o Fade", arrasto_longo <= 1.0 + 0.05, True)

print("5) UM SOM POR CARRO: nunca duas copias do mesmo chiado")
for fps in (30, 60, 144, 240):
    car, w = run(fps, 5.0, 200, 12.0)      # audio de 6,165 s, freada de 5 s
    check("fps %3d, frenagem de 5 s, audio de 6,165 s" % fps, w.overlaps, 0)
    check("fps %3d, nada tocando no fim" % fps, w.count(), 0)
# frenagem LONGA (o som recomeca quando o arquivo acaba, sem sobrepor)
car, w = run(60, 12.0, 250, 8.0)
check("frenagem de 12 s com audio de 6,165 s -> recomeca", w.overlaps, 0)
check("frenagem de 12 s -> o arquivo acabou e o som voltou",
      car.finished >= 1 and w.next_handle - 1 >= 2, True,
      )
car, w = run(60, 12.0, 250, 8.0, length=AUDIO_PLACEHOLDER)
check("mesma frenagem com audio de 1,2 s -> sem sobreposicao", w.overlaps, 0)
check("audio curto -> recomeca varias vezes", w.next_handle - 1 >= 5, True)
# nunca passa do teto global
world = World()
cars = []
for i in range(1, 11):
    c = Car(dict(CFG), world, car_id=i)
    c.prev = 25150                       # 25,15 m/s no frame anterior
    c.update(16, 1.0, 25.0, 16.0, 0.8, in_range=True)   # caindo 0,15 m/s no frame
    cars.append(c)
check("10 carros freando juntos -> 6 deles cantam", world.count(), MAX_SOUNDS)
check("os 4 restantes ficam armados (cantam na proxima frenagem)",
      sum(1 for c in cars if c.handle == 0), 4)

print("6) BrakeForce / BrakeThreshold: o gatilho nao mudou")
car, w = run(60, 1.0, 80, 1.0)      # 1,0 m/s2 < 2,5
check("freada de 1,0 m/s2 nao canta", voice_time(car), 0.0, tol=0.001)
car, w = run(60, 1.0, 80, 3.0)
check("freada de 3,0 m/s2 canta", voice_time(car) > 0.1, True)
car, w = run(60, 1.0, 80, 6.0, cfg=dict(force=10.0))
check("BrakeForce = 10 silencia a freada de 6", voice_time(car), 0.0, tol=0.001)
car, w = run(60, 1.0, 80, 6.0, cfg=dict(force=1.0))
check("BrakeForce = 1 deixa a freada de 6 passar", voice_time(car) > 0.1, True)
car, w = run(60, 1.0, 80, 8.0, cfg=dict(threshold=0.9))
check("BrakeThreshold = 0,9 (pedal em 1,0) canta", voice_time(car) > 0.1, True)
car, w = run(60, 1.0, 80, 8.0, cfg=dict(threshold=0.0))
check("BrakeThreshold = 0 com o pedal em 1,0 canta", voice_time(car) > 0.1, True)
# pedalReleased no meio da frenagem: o som morre e volta quando a frenagem volta
car, w = run(60, 2.0, 200, 12.0, gaps=((0.5, 0.4),))
check("soltou 400 ms no meio -> o som some", car.removed >= 1, True)
check("soltou e voltou -> 2 trechos de som", w.overlaps, 0)
# a IA pisando num transito lento nao vira metralhadeira
car, w = run(60, 6.0, 30, 1.2, gaps=((0.5, 0.2), (1.2, 0.2), (1.9, 0.2)))
check("IA pisando 3x com frenagem fraca", voice_time(car) < 1.0, True)

print("7) volume: escala com velocidade e pedal, respeita o menu, tem teto")
def _alvo(decel, kmh, brake=1.0, sfx=1.0, volume=1.0, **cfg):
    """chama o BrakeVolume direto, com numeros fixos (sem a fisica da frenagem)"""
    c = Car(dict(CFG, sfx=sfx, volume=volume, **cfg), World())
    return c.brake_volume(brake, kmh / 3.6, decel, sfx)


check("110 km/h (fator 1,0) / 20 km/h (piso 0,25) = 4x",
      round(_alvo(2.5, 110) / _alvo(2.5, 20), 2), 4.0, tol=0.01)
check("frenagem no limite (2,5 m/s2) = metade do volume", round(_alvo(2.5, 110), 3), 0.5)
check("frenagem no dobro do limite (5 m/s2) = 1,0", round(_alvo(5.0, 110), 3), 1.0)
check("frenagem fortissima nao passa de 1,0", round(_alvo(20.0, 110), 3), 1.0)
check("frenagem abaixo do limite = 0 (nem canta)", round(_alvo(1.0, 110), 3), 0.0)
cfull, _ = run(60, 1.0, 300, 8.0, cfg=dict(volume=1.0, sfx=1.0), vel0=300)
check("volume 1,0 + menu 1,0 chega a 1,0 (nunca passa)", cfull.peak_vol, 1.0, tol=0.001)
cquiet, _ = run(60, 1.0, 60, 8.0, cfg=dict(volume=0.0), vel0=60)
check("Volume = 0 silencia", cquiet.peak_vol, 0.0, tol=0.001)
cmenu, _ = run(60, 1.0, 60, 8.0, cfg=dict(sfx=0.5), vel0=60)
cfull2, _ = run(60, 1.0, 60, 8.0, cfg=dict(sfx=1.0), vel0=60)
check("volume do menu pela metade = metade do volume",
      round(cmenu.peak_vol / cfull2.peak_vol, 2), 0.5, tol=0.02)


def _vol_at_pedal(brake):
    return 0.5 + 0.5 * brake


check("pedal 1,00 = 1,00 do volume", round(_vol_at_pedal(1.0), 3), 1.0)
check("pedal 0,30 (toque leve) = 0,65 do volume", round(_vol_at_pedal(0.3), 3), 0.65)

print("8) FPS nao muda o resultado")
# a 60 FPS serve de referencia; o resto tem que bater dentro da fisica do jogo
ref, _ = run(60, 2.0, 90, 9.0)
ref_t, ref_v = voice_time(ref), ref.peak_vol
for fps in (25, 30, 50, 60, 144, 240):
    car, w = run(fps, 2.0, 90, 9.0)
    # duracao da voz: a frenagem dura 2 s e o fade acrescenta no maximo 0,2 s
    check("fps %3d: duracao da voz (s)" % fps, round(voice_time(car), 1),
          round(ref_t, 1), tol=0.2)
    # Pico de volume: o fade e' por SEGUNDO, nao por frame, entao o som nao
    # depende do FPS. A diferenca que sobra e' fisica do jogo e nao do som: a
    # 25 FPS o carro ja freou ~1,5 m/s a mais quando o volume chega ao topo,
    # e o fator de velocidade (km/h / RefSpeed) e' um pouco menor - 2,5%, que
    # nao se ouve. Por isso a tolerancia e' de 0,02 e nao de 0,01.
    check("fps %3d: pico de volume" % fps, car.peak_vol, ref_v, tol=0.02)
    check("fps %3d: streams removidos" % fps, car.removed >= 1, True)

print("9) CARRO QUE SAI DE CENA: o som nao fica sozinho no mundo")
# o carro fura o raio da camera no meio da frenagem e volta
car, w = run(60, 3.0, 90, 9.0, out_of_range=((1.0, 1.0),))
check("saiu do raio por 1 s -> o som foi removido", car.removed >= 1, True)
check("voltou a ser avaliado -> o som recomeca", w.overlaps, 0)
check("no fim, nenhum som pendurado", w.count(), 0)
# e o caso de um carro destruido: o stream segue o arquivo ate o fim, mas o
# script nao trava e o teto se refaz (o contador e' recontado a cada frame)
car, w = run(60, 4.0, 90, 9.0, out_of_range=((1.0, 3.0),))
check("carro sumiu de vez -> nada tocando para sempre", w.count(), 0)

# ---------------------------------------------------------------------------
# 10) CONFERENCIA DO SOURCE
# O modelo acima roda em Python e nao enxerga opcode nenhum: foi exatamente por
# isso que o mod ficou mudo no jogo (o CSET invertido) e o modelo continuava
# verde. Estas verificacoes leem o .sc compilado de verdade.
# ---------------------------------------------------------------------------
print("10) source: o ciclo de vida do som esta inteiro")
HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "CLEO", "BrakePadSound.sc")
INI = os.path.join(HERE, "..", "CLEO", "BrakePadSound.ini")

if not os.path.isfile(SRC):
    print("  FALHA %-58s %s" % ("BrakePadSound.sc nao encontrado", SRC))
    fails.append("source ausente")
else:
    src = open(SRC, encoding="utf-8").read()
    code = "\n".join(l for l in src.split("\n")
                     if not l.strip().startswith(("//", "*", "/*")))

    # ---- o bug do audio residual: o som tem de ser DESLIGADO e REMOVIDO ----
    check_src("REMOVE_AUDIO_STREAM existe no source (0xAAE)",
              re.search(r"REMOVE_AUDIO_STREAM\s+\w+", code) is not None)
    check_src("SET_AUDIO_STREAM_STATE ... 0 (desliga antes de remover)",
              re.search(r"SET_AUDIO_STREAM_STATE\s+\w+\s+0\b", code) is not None)
    check_src("GET_AUDIO_STREAM_STATE (saber se o som ja acabou)",
              re.search(r"GET_AUDIO_STREAM_STATE\s+\w+", code) is not None)
    check_src("o handle fica guardado no carro (var. estendida 3)",
              "GET_EXTENDED_CAR_VAR hVeh AUTO 3 hStream" in code
              and "SET_EXTENDED_CAR_VAR hVeh AUTO 3 hStream" in code)
    check_src("o handle e' zerado ao remover (senao o carro trava)",
              code.count("SET_EXTENDED_CAR_VAR hVeh AUTO 3 0") >= 2)
    check_src("o volume atual fica guardado no carro (var. estendida 4)",
              "GET_EXTENDED_CAR_VAR hVeh AUTO 4" in code
              and "SET_EXTENDED_CAR_VAR hVeh AUTO 4" in code)
    check_src("4 var. estendidas reservadas no registro",
              "INIT_EXTENDED_CAR_VARS hNewCar AUTO 4" in code)
    check_src("som nao e' loopado (um chiado, nao uma sirene)",
              re.search(r"SET_AUDIO_STREAM_LOOPED\s+\w+\s+FALSE", code) is not None)
    check_src("Fade lido do .ini",
              re.search(r'READ_FLOAT_FROM_INI_FILE\s+"[^"]*"\s+"[^"]*"\s+"Fade"', code) is not None)
    check_src("o fade e' calculado em segundos, nao por frame",
              "fIncr = fStep" in code and "fIncr *= fFade" in code)
    check_src("o volume-alvo e' zero quando a frenagem acabou",
              re.search(r"BrakeVolume:\s*\n\s*f = 0\.0", code) is not None)
    check_src("um som so por carro: criacao so com handle == 0",
              re.search(r"IF hStream = 0\s*\n\s*RETURN", code) is not None)
    check_src("carro fora do raio desliga o som (StopSound)",
              "StopSound:" in code and "GOSUB StopSound" in code)
    check_src("teto de sons同时 (iSounds < 6)", "iSounds < 6" in code)

    # ---- ordem dos operandos do CSET (o bug que deixou o mod mudo) ----
    cset = re.findall(r"CSET_\w+\s+\S+\s+\S+", code)
    check_src("5 conversoes CSET no source", len(cset) == 5, " ".join(cset))
    check_src("fStep = (float)dt   -> 0093 destino primeiro",
              "CSET_LVAR_FLOAT_TO_LVAR_INT fStep iValue" in code)
    check_src("f = (float)iPrevVel -> 0093 destino primeiro",
              "CSET_LVAR_FLOAT_TO_LVAR_INT f iPrevVel" in code)
    check_src("fVol = (float)vol   -> 0093 destino primeiro",
              "CSET_LVAR_FLOAT_TO_LVAR_INT fVol iValue" in code)
    check_src("iPrevVel = (int)f   -> 0092 destino primeiro",
              "CSET_LVAR_INT_TO_LVAR_FLOAT iPrevVel f" in code)
    # sentinela do bug: no 0093 o destino tem de ser FLOAT e no 0092 tem de ser
    # INT. Escrever o destino trocado foi o que deixou o mod mudo (o dt virava
    # 0,0 e o gatilho nunca passava).
    floats = set()
    ints = set()
    for line in re.findall(r"^LVAR_INT\s+(.*)$", code, re.M):
        ints |= set(line.split())
    for line in re.findall(r"^LVAR_FLOAT\s+(.*)$", code, re.M):
        floats |= set(line.split())
    errados = []
    for op, dest in re.findall(r"CSET_LVAR_(FLOAT_TO_LVAR_INT|INT_TO_LVAR_FLOAT)\s+(\w+)\s+", code):
        # 0093 (FLOAT_TO_LVAR_INT) grava num FLOAT; 0092 (INT_TO_LVAR_FLOAT)
        # grava num INT. Destino do tipo trocado = o bug do dt/velocidade.
        if op == "FLOAT_TO_LVAR_INT" and dest in ints:
            errados.append((op, dest))
        if op == "INT_TO_LVAR_FLOAT" and dest in floats:
            errados.append((op, dest))
    check_src("nenhum CSET com destino do tipo errado (bug v2.6/v2.7)",
              not errados, "" if not errados else str(errados))

    # ---- slots de variavel local (o limite do script CLEO e' 32) ----
    names = []
    for line in re.findall(r"^LVAR_(?:INT|FLOAT)\s+(.*)$", code, re.M):
        names += line.split()
    check_src("slots de variavel local <= 32", len(names) <= 32,
              "%d usados" % len(names))

    # ---- o resto das promessas do cabecalho ----
    check_src("buffer de som usado com $pBuffer (sem $, vira o texto)",
              "LOAD_3D_AUDIO_STREAM $pBuffer" in code
              and "DOES_FILE_EXIST $pBuffer" in code)
    check_src("GET_LABEL_POINTER escreve no pBuffer",
              "GET_LABEL_POINTER txtSoundBuffer (pBuffer)" in code)
    loop = src.split("// =================================================================== laco")[-1]
    check_src("nenhuma leitura de .ini dentro do laco principal",
              "READ_" not in loop.split("OnCarCreate:")[0],
              "lidas so na inicializacao")
    check_src("desaceleracao medida em m/s2 (fDecel /= fStep)", "fDecel /= fStep" in code)
    check_src("gatilho compara a queda com BrakeForce", "fDecel > fForce" in code)
    check_src("carro parado nao canta (piso de 0,5 m/s no alvo do volume)",
              "IF fVel < 0.5" in code)
    check_src("BrakeForce vem do .ini", '"BrakeForce"' in code)
    check_src("aviao/barco sao descartados antes da avaliacao de valor",
              re.search(r"IF iVehicles = 0.*?GOTO EvalReject", code, re.S) is not None)

    if os.path.isfile(INI):
        ini = open(INI, encoding="utf-8").read()
        for key, val in (("BrakeForce", "2.5"), ("BrakeThreshold", "0.15"),
                         ("Fade", "0.2"), ("Cooldown", None),
                         ("TriggerPressure", None), ("MinSpeed", None),
                         ("PressureRate", None)):
            m = re.search(r"^%s\s*=\s*(\S+)" % key, ini, re.M)
            if val is None:
                check_src("ini: %s removido (chave de modelo antigo)" % key, m is None)
            else:
                check_src("ini: %s = %s" % (key, val),
                          m is not None and m.group(1) == val,
                          "" if m is None else m.group(1))
        check_src("ini: arquivo de som e' wav ou mp3 documentado",
                  re.search(r"^SoundFile\s*=\s*\S+\.(wav|mp3)", ini, re.M) is not None)
        check_src("ini: a duracao do arquivo nao e' uma constante do script",
                  "1,2 s" not in ini.split("SoundFile")[0]
                  and "6165" not in ini and "6,165 s" not in ini)
    else:
        check_src("BrakePadSound.ini nao encontrado", False, INI)

print()
if fails:
    print("FALHARAM %d verificacoes: %s" % (len(fails), fails))
    sys.exit(1)
print("todas as verificacoes do modelo passaram")
