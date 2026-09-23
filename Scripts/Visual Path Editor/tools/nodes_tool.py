#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
nodes_tool.py - leitor, validador e comparador de arquivos nodes*.dat do GTA SA

Formato (o mesmo que o jogo usa em data\\paths\\nodesN.dat):
    cabecalho 20 bytes : 5 x uint32 (nodes, veh nodes, ped nodes, navi nodes, links)
    secao 1            : nodes   - 28 bytes cada
    secao 2            : navis   - 14 bytes cada
    secao 3            : links   - 4 bytes cada x (links + 192)
    secao 5            : navi links - 2 bytes cada x links
    secao 6            : comprimentos - 1 byte cada x (links + 192)
    secao 7            : flags de interseccao - 1 byte cada x (links + 192)

As secoes 3, 6 e 7 tem 192 entradas extras no fim (768 bytes na secao 3) -
sao os "dynamic links" que o jogo usa em tempo de execucao. Muitos editores
antigos (e a versao 1.0 deste mod) nao gravam esses bytes extras: nesse caso
o jogo le lixo depois do fim da secao 6 e as flags de interseccao inteiras
ficam com memoria nao inicializada.

Uso:
    python3 nodes_tool.py info   arquivo.dat [arquivo.dat ...]
    python3 nodes_tool.py validar arquivo.dat [arquivo.dat ...]
    python3 nodes_tool.py comparar original.dat salvo.dat
    python3 nodes_tool.py criar-simples saida.dat [--nodes N] [--navis M]

Sem dependencias (Python 3.6+).
"""

import argparse
import io
import math
import os
import struct
import sys

HEADER_SIZE = 20
NODE_SIZE = 28
NAVI_SIZE = 14
LINK_SIZE = 4
EXTRA_LINKS = 192          # 16 * 12 slots dinamicos
EXTRA_LINK_BYTES = EXTRA_LINKS * LINK_SIZE   # 768

HEURISTIC_DEFAULT = 0x7FFE  # SHRT_MAX - 1 (distancia inicial do Dijkstra)
SENTINEL = b"\xff\xff\x00\x00"  # area 0xFFFF + node 0x0000 = slot livre
COORD_LIMIT = 32767 / 8.0


class Area(object):
    """Um arquivo nodesN.dat lido do disco."""

    def __init__(self, path, data=None, area_id=None):
        self.path = path
        self.raw = data if data is not None else io.open(path, "rb").read()
        if area_id is None:
            base = os.path.basename(path)
            digits = "".join(c for c in base if c.isdigit())
            area_id = int(digits) if digits else -1
        self.area_id = area_id
        self.errors = []
        self.warnings = []
        self.parse()

    # ------------------------------------------------------------------ parse
    def parse(self):
        d = self.raw
        self.valid_header = False
        if len(d) < HEADER_SIZE:
            self.errors.append("arquivo com %d bytes (nem o cabecalho de 20 bytes cabe)" % len(d))
            self.header = (0, 0, 0, 0, 0)
            self.nodes, self.navis, self.links = [], [], []
            self.navi_links, self.lengths, self.intersections = [], [], []
            return

        (self.num_nodes, self.num_veh, self.num_ped,
         self.num_navis, self.num_links) = struct.unpack_from("<5I", d, 0)
        self.header = (self.num_nodes, self.num_veh, self.num_ped,
                       self.num_navis, self.num_links)
        self.valid_header = True

        self.expected_size = expected_size(self.num_nodes, self.num_navis, self.num_links)
        self.size = len(d)

        off = HEADER_SIZE
        self.nodes = []
        for i in range(self.num_nodes):
            if off + NODE_SIZE > len(d):
                self.errors.append("secao 1 truncada no node %d" % i)
                break
            (mem_next, mem_prev, x, y, z, heur, link_id,
             area_id, node_id, width, flood, flags) = struct.unpack_from("<IIhhhHhhhBB I", d, off)
            self.nodes.append({
                "index": i,
                "mem_next": mem_next, "mem_prev": mem_prev,
                "x": x / 8.0, "y": y / 8.0, "z": z / 8.0,
                "rx": x, "ry": y, "rz": z,
                "heuristic": heur, "link_id": link_id,
                "area_id": area_id, "node_id": node_id,
                "path_width": width, "flood_fill": flood, "flags": flags,
                "num_links": flags & 0xF,
            })
            off += NODE_SIZE

        self.navis = []
        for i in range(self.num_navis):
            if off + NAVI_SIZE > len(d):
                self.errors.append("secao 2 truncada na navi %d" % i)
                break
            (x, y, area_id, node_id, dx, dy, width, lane_flags,
             flags2) = struct.unpack_from("<hhHHbbBBH", d, off)
            if dx > 127:
                dx -= 256
            if dy > 127:
                dy -= 256
            self.navis.append({
                "index": i,
                "x": x / 8.0, "y": y / 8.0, "rx": x, "ry": y,
                "attached_area": area_id, "attached_node": node_id,
                "dx": dx, "dy": dy,
                "path_width": width,
                "lane_flags": lane_flags,
                "traffic_flags": flags2,
                "same_lanes": (lane_flags >> 3) & 7,
                "opposite_lanes": lane_flags & 7,
                "light_direction": (lane_flags >> 6) & 1,
                "light_state": flags2 & 3,
                "bridge_lights": (flags2 >> 2) & 1,
            })
            off += NAVI_SIZE

        self.links_offset = off
        n_all = self.num_links + EXTRA_LINKS
        self.links = []
        for i in range(n_all):
            if off + LINK_SIZE > len(d):
                break
            area_id, node_id = struct.unpack_from("<HH", d, off)
            if i < self.num_links:
                self.links.append({"index": i, "area": area_id, "node": node_id})
            off += LINK_SIZE
        self.extra_links_present = off <= len(d) and len(self.links) == self.num_links and \
            self.links_offset + 4 * n_all <= len(d)
        self.extra_links_offset = self.links_offset + self.num_links * LINK_SIZE

        self.navi_links_offset = self.links_offset + 4 * n_all
        self.navi_links = []
        for i in range(self.num_links):
            if self.navi_links_offset + i * 2 + 2 > len(d):
                break
            (v,) = struct.unpack_from("<H", d, self.navi_links_offset + i * 2)
            self.navi_links.append({"raw": v, "navi": v & 0x3FF, "area": v >> 10})

        self.lengths_offset = self.navi_links_offset + 2 * self.num_links
        self.lengths = []
        for i in range(self.num_links + EXTRA_LINKS):
            p = self.lengths_offset + i
            if p >= len(d):
                break
            self.lengths.append(d[p])
        self.extra_lengths_present = len(self.lengths) >= self.num_links + EXTRA_LINKS

        self.intersections_offset = self.lengths_offset + (self.num_links + EXTRA_LINKS)
        self.intersections = []
        for i in range(self.num_links + EXTRA_LINKS):
            p = self.intersections_offset + i
            if p >= len(d):
                break
            self.intersections.append(d[p])
        self.intersections_present = len(self.intersections) >= self.num_links + EXTRA_LINKS

    # -------------------------------------------------------------- node links
    def node_link_indexes(self, node):
        return list(range(node["link_id"], node["link_id"] + node["num_links"]))

    def link(self, idx):
        if 0 <= idx < len(self.links):
            return self.links[idx]
        return None

    def node(self, area_id, node_id):
        if area_id == self.area_id and 0 <= node_id < len(self.nodes):
            return self.nodes[node_id]
        return None

    # ---------------------------------------------------------------- validate
    def validate(self):
        errs, warns = list(self.errors), list(self.warnings)

        if not self.valid_header:
            return errs, warns

        # tamanhos / secoes
        if self.size != self.expected_size:
            if self.size < self.expected_size:
                faltando = []
                if not self.extra_links_present:
                    faltando.append("192 slots extras da secao 3 (768 bytes)")
                if not self.extra_lengths_present:
                    faltando.append("192 bytes extras da secao 6")
                if not self.intersections_present:
                    faltando.append("secao 7 inteira (flags de interseccao)")
                errs.append("arquivo INCOMPLETO: %d bytes (o formato pede %d). Faltando: %s"
                            % (self.size, self.expected_size, "; ".join(faltando) or "bytes no fim"))
            else:
                warns.append("arquivo com %d bytes (o formato pede %d): sobrando %d bytes no fim"
                             % (self.size, self.expected_size, self.size - self.expected_size))

        if self.num_nodes != self.num_veh + self.num_ped:
            errs.append("cabecalho inconsistente: nodes (%d) != veh (%d) + ped (%d)"
                        % (self.num_nodes, self.num_veh, self.num_ped))
        if self.num_nodes > 65535:
            errs.append("nodes demais: %d (maximo 65535)" % self.num_nodes)
        if self.num_links > 65535:
            errs.append("links demais: %d (maximo 65535)" % self.num_links)

        # nodes
        heur_ruins = 0
        mem_zerados = 0
        largura_ruim = 0
        sem_link = 0
        poucos_links = 0
        for n in self.nodes:
            if n["node_id"] != n["index"]:
                errs.append("node %d: NodeID gravado = %d (deveria ser o indice)"
                            % (n["index"], n["node_id"]))
            if n["area_id"] != self.area_id:
                errs.append("node %d: AreaID gravado = %d (o arquivo e a area %d)"
                            % (n["index"], n["area_id"], self.area_id))
            if abs(n["x"]) > COORD_LIMIT or abs(n["y"]) > COORD_LIMIT or abs(n["z"]) > COORD_LIMIT:
                errs.append("node %d: coordenada fora do limite do formato" % n["index"])
            if n["heuristic"] != HEURISTIC_DEFAULT:
                heur_ruins += 1
            if n["mem_next"] == 0 and n["mem_prev"] == 0:
                mem_zerados += 1
            if n["path_width"] > 255:
                largura_ruim += 1
            if n["num_links"] == 0:
                sem_link += 1
            elif n["num_links"] < 2:
                poucos_links += 1
            fim = n["link_id"] + n["num_links"]
            if fim > self.num_links:
                errs.append("node %d: links %d..%d passam do total do arquivo (%d)"
                            % (n["index"], n["link_id"], fim - 1, self.num_links))
        if heur_ruins:
            warns.append("%d node(s) com heuristica diferente de 0x7FFE (o jogo espera 0x7FFE)"
                         % heur_ruins)
        if largura_ruim:
            warns.append("%d node(s) com path width acima de 255" % largura_ruim)
        if sem_link:
            errs.append("%d node(s) SEM NENHUM link (o jogo pode travar)" % sem_link)
        if poucos_links:
            warns.append("%d node(s) com apenas 1 link" % poucos_links)

        # links (secao 3)
        soltos = 0
        fora_da_area = 0
        for l in self.links:
            if l["area"] > 63:
                errs.append("link %d: area %d invalida" % (l["index"], l["area"]))
                continue
            if l["area"] == self.area_id:
                if l["node"] >= self.num_nodes:
                    fora_da_area += 1
                    errs.append("link %d: aponta pro node %d da area %d, mas ela tem %d nodes"
                                % (l["index"], l["node"], l["area"], self.num_nodes))
            elif l["node"] == 0xFFFF:
                errs.append("link %d: node 0xFFFF (link nao apontado)" % l["index"])
            else:
                soltos += 1
        if soltos:
            warns.append("%d link(s) apontam pra outras areas (confira se elas foram carregadas)"
                         % soltos)

        # ids de link duplicados/fora de ordem
        esperado = 0
        for n in self.nodes:
            if n["link_id"] != esperado:
                warns.append("node %d: LinkID %d (esperado %d): a lista de links nao esta na ordem dos nodes"
                             % (n["index"], n["link_id"], esperado))
                break
            esperado += n["num_links"]
        if esperado != self.num_links:
            errs.append("soma dos links dos nodes = %d, mas o cabecalho diz %d"
                        % (esperado, self.num_links))

        # navi links (secao 5)
        if len(self.navi_links) == self.num_links:
            ruins = 0
            for i, nl in enumerate(self.navi_links):
                if nl["raw"] in (0xFFFF, 0x0000):
                    continue
                if nl["area"] == self.area_id and nl["navi"] >= self.num_navis:
                    ruins += 1
                    if ruins <= 5:
                        errs.append("link %d: aponta pra navi %d, mas a area tem %d navis"
                                    % (i, nl["navi"], self.num_navis))
            if ruins > 5:
                errs.append("... mais %d links com navi inexistente" % (ruins - 5))

        # navis (secao 2)
        dirs_ruins = 0
        for v in self.navis:
            if v["attached_node"] == 0xFFFF and v["attached_area"] == 0xFFFF:
                errs.append("navi %d: nao esta conectada a nenhum node" % v["index"])
            elif v["attached_area"] == self.area_id and v["attached_node"] >= self.num_nodes:
                errs.append("navi %d: conectada ao node %d, mas a area tem %d nodes"
                            % (v["index"], v["attached_node"], self.num_nodes))
            if abs(v["x"]) > COORD_LIMIT or abs(v["y"]) > COORD_LIMIT:
                errs.append("navi %d: coordenada fora do limite do formato" % v["index"])
            if v["dx"] or v["dy"]:
                comp = math.sqrt(v["dx"] ** 2 + v["dy"] ** 2)
                if abs(comp - 100.0) > 5.0:
                    dirs_ruins += 1
        if dirs_ruins:
            warns.append("%d navi(s) com direcao que nao e um vetor unitario (esperado ~100)"
                         % dirs_ruins)

        # comprimentos (secao 6) x distancia real
        desatualizados = 0
        comparados = 0
        if len(self.lengths) >= self.num_links:
            for i, l in enumerate(self.links):
                a = self.node(l["area"], l["node"]) if l["area"] == self.area_id else None
                if a is None:
                    continue
                # o link i pertence ao node dono desse link_id
                dono = None
                for n in self.nodes:
                    if n["link_id"] <= i < n["link_id"] + n["num_links"]:
                        dono = n
                        break
                if dono is None:
                    continue
                d = math.hypot(a["x"] - dono["x"], a["y"] - dono["y"])
                esperado_len = max(0, min(255, int(d)))
                comparados += 1
                if abs(int(self.lengths[i]) - esperado_len) > 1:
                    desatualizados += 1
        if desatualizados:
            warns.append("%d de %d comprimentos de link nao batem com a distancia real dos nodes"
                         % (desatualizados, comparados))

        # flags de interseccao
        if self.intersections_present:
            nao_zero = sum(1 for b in self.intersections[:self.num_links] if b)
            if nao_zero == 0:
                warns.append("todas as flags de interseccao estao zeradas (cruzamentos e "
                             "semafaros de pedestre serao ignorados)")
            else:
                pass

        return errs, warns


def expected_size(nodes, navis, links):
    return HEADER_SIZE + NODE_SIZE * nodes + NAVI_SIZE * navis + \
        LINK_SIZE * (links + EXTRA_LINKS) + 2 * links + 2 * (links + EXTRA_LINKS)


# ---------------------------------------------------------------------- info
def cmd_info(args):
    for path in args.files:
        try:
            a = Area(path)
        except IOError as e:
            print("%s: %s" % (path, e))
            continue
        print("=== %s" % path)
        print("  area: %d   tamanho: %d bytes (esperado %d)" % (a.area_id, a.size, a.expected_size))
        print("  nodes: %d (veh %d / ped %d)   navis: %d   links: %d"
              % (a.num_nodes, a.num_veh, a.num_ped, a.num_navis, a.num_links))
        print("  secoes: nodes@%d navis@%d links@%d navi_links@%d comprimentos@%d interseccoes@%d"
              % (HEADER_SIZE, HEADER_SIZE + NODE_SIZE * a.num_nodes, a.links_offset,
                 a.navi_links_offset, a.lengths_offset, a.intersections_offset))
        if a.nodes:
            zs = [n["z"] for n in a.nodes]
            print("  Z dos nodes: min %.3f / max %.3f" % (min(zs), max(zs)))
        print("  slots extras das secoes 3/6/7: %s / %s / %s"
              % (a.extra_links_present, a.extra_lengths_present, a.intersections_present))


# ------------------------------------------------------------------- validar
def cmd_validar(args):
    total_err = 0
    for path in args.files:
        try:
            a = Area(path)
        except IOError as e:
            print("%s: %s" % (path, e))
            total_err += 1
            continue
        errs, warns = a.validate()
        print("=== %s" % path)
        for e in errs:
            print("  ERRO  %s" % e)
        for w in warns:
            print("  aviso %s" % w)
        print("  %d erro(s), %d aviso(s)" % (len(errs), len(warns)))
        total_err += len(errs)
    return 1 if total_err else 0


# ------------------------------------------------------------------ comparar
def cmd_comparar(args):
    a = Area(args.original, area_id=None)
    b = Area(args.salvo, area_id=a.area_id)
    print("original: %s (%d bytes, %d nodes, %d navis, %d links)"
          % (args.original, a.size, a.num_nodes, a.num_navis, a.num_links))
    print("salvo   : %s (%d bytes, %d nodes, %d navis, %d links)"
          % (args.salvo, b.size, b.num_nodes, b.num_navis, b.num_links))

    if b.size < a.expected_size:
        print("\n! O arquivo salvo tem %d bytes a menos que o esperado pelo formato (%d)"
              % (a.expected_size - b.size, a.expected_size))
        print("  Faltando: secao 7 (interseccoes)%s"
              % ("" if b.extra_links_present else ", 192 slots extras da secao 3"))

    comuns = min(a.num_nodes, b.num_nodes)
    dz = dy = dx = 0
    pior = None
    for i in range(comuns):
        x, y, z = a.nodes[i]["rx"] - b.nodes[i]["rx"], a.nodes[i]["ry"] - b.nodes[i]["ry"], \
            a.nodes[i]["rz"] - b.nodes[i]["rz"]
        dx += x
        dy += y
        dz += z
        if pior is None or abs(z) > abs(pior[1]):
            pior = (i, z, a.nodes[i]["z"], b.nodes[i]["z"])
    if comuns:
        print("\nDiferenca media (original - salvo), em unidades de 1/8:")
        print("  X: %.2f   Y: %.2f   Z: %.2f" % (dx / comuns, dy / comuns, dz / comuns))
        if pior and pior[1] != 0:
            print("  Maior diferenca de Z no node %d: %.4f -> %.4f (%.3f m)"
                  % (pior[0], pior[2], pior[3], pior[1] / 8.0))
        if dz != 0 and all(a.nodes[i]["rz"] - b.nodes[i]["rz"] == a.nodes[0]["rz"] - b.nodes[0]["rz"]
                           for i in range(comuns)):
            print("  >>> TODOS os nodes foram deslocados no mesmo valor de Z: "
                  "isso e o bug do 'iz -= 2' (cada save afunda os nodes em 0.25 m)")

    mobj = {}
    for n in b.nodes:
        mobj[(n["area_id"], n["index"])] = n
    mudos = 0
    for i in range(comuns):
        if a.nodes[i]["flags"] != b.nodes[i]["flags"]:
            mudos += 1
    if mudos:
        print("\n%d node(s) com flags diferentes" % mudos)

    if b.intersections_present and a.intersections_present:
        difs = sum(1 for i in range(min(len(a.intersections), len(b.intersections)))
                   if a.intersections[i] != b.intersections[i])
        print("%d byte(s) de interseccao diferentes" % difs)
    return 0


# --------------------------------------------------------------- criar-simples
def cmd_criar(args):
    """Cria um nodesN.dat valido e simples (util pra testar o validador)."""
    nodes, navis = args.nodes, args.navis
    links = []
    nodes_bin = []
    for i in range(nodes):
        # cada node tem 2 links: pra si mesmo e pro proximo
        link_id = len(links)
        links.append((args.area, i))
        links.append((args.area, (i + 1) % nodes))
        x = int((args.x + i * 10.0) * 8)
        y = int(args.y * 8)
        z = int(20.0 * 8)
        is_veh = i < int(nodes * 0.7)
        flags = 2 | (0x1000 if is_veh else 0)   # 2 links + "nao rodovia"
        flags |= 0xF << 16                      # chance de spawn maxima
        nodes_bin.append(struct.pack("<IIhhhHhhhBBI", 0, 0, x, y, z,
                                     HEURISTIC_DEFAULT, link_id,
                                     args.area, i, 16 if not is_veh else 0,
                                     1 if is_veh else 5, flags))
    navis_bin = []
    for j in range(navis):
        x = int((args.x + j * 20.0 + 5.0) * 8)
        y = int(args.y * 8)
        navis_bin.append(struct.pack("<hhHHbbBBH", x, y, args.area,
                                     j % max(1, nodes), 100, 0, 16, 0b00001000, 0))
    out = struct.pack("<5I", nodes, int(nodes * 0.7), nodes - int(nodes * 0.7), navis, len(links))
    out += b"".join(nodes_bin)
    out += b"".join(navis_bin)
    for (a, n) in links:
        out += struct.pack("<HH", a, n)
    out += SENTINEL * EXTRA_LINKS
    for i in range(len(links)):
        v = 0
        if i < navis:
            v = (args.area << 10) | i
        out += struct.pack("<H", v)
    for i in range(len(links)):
        if i < navis and i < nodes:
            pass
        out += bytes([0])
    out += bytes(EXTRA_LINKS)
    for i in range(len(links)):
        out += bytes([1 if i % 7 == 0 else 0])
    out += bytes(EXTRA_LINKS)
    with io.open(args.out, "wb") as f:
        f.write(out)
    print("criado %s: %d bytes (nodes=%d, navis=%d, links=%d)"
          % (args.out, len(out), nodes, navis, len(links)))
    return 0


def main():
    ap = argparse.ArgumentParser(description="Leitor/validador de nodes*.dat do GTA SA")
    sub = ap.add_subparsers(dest="cmd")

    p = sub.add_parser("info", help="resumo do arquivo")
    p.add_argument("files", nargs="+")
    p.set_defaults(func=cmd_info)

    p = sub.add_parser("validar", help="confere o arquivo contra o formato do jogo")
    p.add_argument("files", nargs="+")
    p.set_defaults(func=cmd_validar)

    p = sub.add_parser("comparar", help="compara o original com o arquivo salvo")
    p.add_argument("original")
    p.add_argument("salvo")
    p.set_defaults(func=cmd_comparar)

    p = sub.add_parser("criar-simples", help="gera um arquivo de exemplo valido")
    p.add_argument("out")
    p.add_argument("--area", type=int, default=8)
    p.add_argument("--nodes", type=int, default=40)
    p.add_argument("--navis", type=int, default=8)
    p.add_argument("--x", type=float, default=-100.0)
    p.add_argument("--y", type=float, default=-100.0)
    p.set_defaults(func=cmd_criar)

    args = ap.parse_args()
    if not args.cmd:
        ap.print_help()
        return 2
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
