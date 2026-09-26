#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
tests.py - testes do nodes_tool.py (e demonstracao dos bugs da versao 1.0)

Roda sem dependencias:
    python3 tests.py

Os testes provam que o validador pega:
  1. arquivo valido = 0 erros
  2. arquivo salvo no formato antigo (sem a secao 7 e sem os 192 bytes extras
     da secao 6) = erro de "arquivo INCOMPLETO"
  3. arquivo salvo com o bug do Z (-2 a cada save) = comparador aponta o
     deslocamento uniforme de Z
"""

import io
import math
import os
import struct
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
TOOL = os.path.join(HERE, "nodes_tool.py")

sys.path.insert(0, HERE)
import nodes_tool  # noqa: E402


def build_sample(nodes=40, navis=8, area=8):
    """Cria um nodesN.dat valido em memoria (mesmo formato do jogo)."""
    links = []
    nodes_bin = []
    veh = int(nodes * 0.7)
    for i in range(nodes):
        link_id = len(links)
        links.append((area, i))
        links.append((area, (i + 1) % nodes))
        x = int((-100.0 + i * 10.0) * 8)
        y = int(-100.0 * 8)
        z = int(20.0 * 8)
        flags = 2 | (0x1000 if i < veh else 0) | (0xF << 16)
        nodes_bin.append(struct.pack("<IIhhhHhhhBBI", 0, 0, x, y, z,
                                     nodes_tool.HEURISTIC_DEFAULT, link_id,
                                     area, i, 16 if i >= veh else 0,
                                     1 if i < veh else 5, flags))
    navis_bin = []
    for j in range(navis):
        x = int((-100.0 + j * 20.0 + 5.0) * 8)
        y = int(-100.0 * 8)
        navis_bin.append(struct.pack("<hhHHbbBBH", x, y, area, j % nodes,
                                     100, 0, 16, 0b00001000, 0))

    out = struct.pack("<5I", nodes, veh, nodes - veh, navis, len(links))
    out += b"".join(nodes_bin)
    out += b"".join(navis_bin)
    for (a, n) in links:
        out += struct.pack("<HH", a, n)
    out += nodes_tool.SENTINEL * nodes_tool.EXTRA_LINKS
    for i in range(len(links)):
        v = (area << 10) | i if i < navis else 0
        out += struct.pack("<H", v)
    # comprimentos = distancia real entre os nodes (10 m)
    for i, (a, n) in enumerate(links):
        if a == area:
            xa = -100.0 + i // 2 * 10.0
            xb = -100.0 + n * 10.0
            dist = math.hypot(xb - xa, 0.0)
            out += bytes([max(0, min(255, int(dist)))])
        else:
            out += bytes([0])
    out += bytes(nodes_tool.EXTRA_LINKS)
    for i in range(len(links)):
        out += bytes([1 if i % 7 == 0 else 0])
    out += bytes(nodes_tool.EXTRA_LINKS)
    return out


def emulate_old_writer(data):
    """Simula o que a versao 1.0 gravava: sem a secao 7, sem os 192 bytes
    extras da secao 6, Z deslocado em -2 (0.25 m) e sem os bytes de
    interseccao (que ficavam no lixo da memoria do jogo)."""
    a = nodes_tool.Area("memoria.dat", data=data, area_id=8)
    out = struct.pack("<5I", a.num_nodes, a.num_veh, a.num_ped, a.num_navis, a.num_links)
    for n in a.nodes:
        out += struct.pack("<IIhhhHhhhBBI", 27788808, 0, n["rx"], n["ry"], n["rz"] - 2,
                           nodes_tool.HEURISTIC_DEFAULT, n["link_id"], n["area_id"],
                           n["node_id"], n["path_width"], n["flood_fill"], n["flags"])
    for v in a.navis:
        out += struct.pack("<hhHHbbBBH", v["rx"], v["ry"], v["attached_area"],
                           v["attached_node"], v["dx"], v["dy"], v["path_width"],
                           v["lane_flags"], v["traffic_flags"])
    for l in a.links:
        out += struct.pack("<HH", l["area"], l["node"])
    out += nodes_tool.SENTINEL * nodes_tool.EXTRA_LINKS
    for nl in a.navi_links:
        out += struct.pack("<H", nl["raw"])
    for i in range(a.num_links):
        out += bytes([a.lengths[i]])
    return out


def lines_of(text):
    return text.splitlines()


def run_tool(args):
    p = subprocess.Popen([sys.executable, TOOL] + args,
                         stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    out = p.communicate()[0].decode("utf-8", "replace")
    return p.returncode, out


def main():
    ok = True
    tmp = tempfile.mkdtemp(prefix="nodes_tool_test_")
    good = os.path.join(tmp, "nodes8.dat")
    old = os.path.join(tmp, "nodes8_old.dat")

    with io.open(good, "wb") as f:
        f.write(build_sample())

    # 1) arquivo valido
    rc, out = run_tool(["validar", good])
    print("--- 1) arquivo no formato completo")
    print(out.strip())
    if "0 erro" not in out:
        print("FALHOU: esperava 0 erros")
        ok = False

    # 2) arquivo "salvo pela versao 1.0"
    with io.open(old, "wb") as f:
        f.write(emulate_old_writer(build_sample()))
    rc, out = run_tool(["validar", old])
    print("\n--- 2) arquivo no formato antigo (incompleto)")
    print(out.strip())
    if "INCOMPLETO" not in out:
        print("FALHOU: esperava detectar o arquivo incompleto")
        ok = False

    # 3) comparacao: o bug do Z
    rc, out = run_tool(["comparar", good, old])
    print("\n--- 3) comparando o original com o arquivo salvo pela versao 1.0")
    print(out.strip())
    if "0.25 m" not in out:
        print("FALHOU: esperava detectar o deslocamento de Z")
        ok = False

    print("\n%s" % ("TODOS OS TESTES PASSARAM" if ok else "HOUVE FALHA"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
