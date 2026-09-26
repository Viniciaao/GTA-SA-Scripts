#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_script.py - pre-checagem estatica do VisualPathEditor.sc (gta3script)

O compilador de verdade deste mod e' o gta3sc (linguagem gta3script, NAO Sanny
Builder) - veja BUILD.md / tools/build.sh. Este script e' so' um quebra-galho
para rodar sem instalar nada; ele confere:

  * balanceamento de blocos: { }, IF/ENDIF, WHILE/ENDWHILE, REPEAT/ENDREPEAT,
    SWITCH/ENDSWITCH;
  * labels usados por GOSUB/GOTO/CLEO_CALL (precisam existir no arquivo);
  * variaveis usadas mas nao declaradas (LVAR_INT/LVAR_FLOAT/CONST_*);
  * limite de 32 variaveis locais por bloco;
  * comentarios /* */ e strings "" nao terminadas;
  * a regra do gta3script que so' deixa UMA operacao por expressao
    (`size += 14 * naviCount` e' erro de sintaxe no gta3sc);
  * as duas armadilhas de memoria que causavam crash no mod (v1.1 -> v1.2):
      - usar o endereco de um slot da tabela `lists` como se fosse o dado
        (falta de READ_MEMORY antes de READ/WRITE_STRUCT_OFFSET);
      - DELETE_LIST duas vezes no mesmo ponteiro (double free no CLEO+).

Uso:
    python3 check_script.py "caminho/arquivo.sc"

Codigo de saida 0 = sem erros, 1 = encontrou problemas.
"""

import io
import re
import sys

STRUCT = {
    "IF", "ELSE", "ENDIF", "WHILE", "ENDWHILE", "REPEAT", "ENDREPEAT",
    "SWITCH", "ENDSWITCH", "CASE", "BREAK", "DEFAULT", "GOTO", "GOSUB",
    "RETURN", "AND", "OR", "NOT", "TRUE", "FALSE", "LVAR_INT", "LVAR_FLOAT",
    "LVAR_TEXT_LABEL", "LVAR_TEXT_LABEL16", "CONST_INT", "CONST_FLOAT",
    "SCRIPT_START", "SCRIPT_END", "SCRIPT_NAME", "NOP", "CLEO_CALL",
    "CLEO_RETURN", "DUMP", "ENDDUMP", "MISSION_START", "MISSION_END",
}

# identificadores que sao texto (nao variaveis) em parametros de opcodes CLEO+
TEXT_PARAMS = {"VPEV", "AUTO",
               # nomes de sprite/textura passados como texto em LOAD_SPRITE
               "booloff", "boolon", "typeped", "typecar", "typeboat"}

IDENT = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")

# getListPointer(tipo, n) devolve o ENDERECO do slot na tabela "lists" do
# script. Tipos 0..2 sao listas e o tipo 3 sao os buffers dos nodes*.dat: os
# dois guardam PONTEIRO, entao precisam de READ_MEMORY antes de usar o valor
# com READ/WRITE_STRUCT_OFFSET. Tipos 4..7 sao contadores inteiros, e neles o
# endereco do slot E' o dado (usar direto esta' certo).
GP_CALL = re.compile(r"CLEO_CALL\s+getListPointer\s+\d+\s*\((\w+)\s+(\w+)\)\s*\((\w+)\)")
READ_MEM = re.compile(r"^READ_MEMORY\s+(\w+)\s")
STRUCT_OP = re.compile(r"^(?:READ|WRITE)_STRUCT_OFFSET\s+(\w+)\b")
DELETE_LIST = re.compile(r"^DELETE_LIST\s+(\w+)\b")
CREATE_LIST = re.compile(r"^CREATE_LIST\b.*\((\w+)\)")
PLAIN_ASSIGN = re.compile(r"^(\w+)\s*(?:=|\+=|-=|\*=|/=|=#)")
OUT_END = re.compile(r"\((\w+)\)\s*$")          # ... (var) no fim da linha
BARE_END = re.compile(r"\s([A-Za-z_]\w*)\s*$")   # ... var  (sem parenteses)
HEXLIT = re.compile(r"0[xX][0-9A-Fa-f]+")
LABEL_DEF = re.compile(r"^\s*([A-Za-z_][A-Za-z0-9_]*)\s*:\s*(//.*)?$")
ASSIGN = re.compile(r"^\s*([A-Za-z_][A-Za-z0-9_]*)\s*(?:=|\+=|-=|\*=|/=|=#)\s*(.*)$")
CALL_REF = re.compile(r"\b(?:GOSUB|GOTO|CLEO_CALL|STREAM_CUSTOM_SCRIPT_FROM_LABEL)\s+"
                      r"@?([A-Za-z_][A-Za-z0-9_]*)")


def is_all_caps(ident):
    return re.fullmatch(r"[A-Z0-9_]+", ident) is not None


def strip_comments(line, state):
    out = []
    i = 0
    n = len(line)
    in_str = False
    while i < n:
        c = line[i]
        if state["block_comment"]:
            if c == "*" and i + 1 < n and line[i + 1] == "/":
                state["block_comment"] = False
                i += 2
                continue
            i += 1
            continue
        if in_str:
            if c == "\\":
                i += 2
                continue
            if c == '"':
                in_str = False
            i += 1
            continue
        if c == '"':
            in_str = True
            i += 1
            continue
        if c == "/" and i + 1 < n and line[i + 1] == "*":
            state["block_comment"] = True
            i += 2
            continue
        if c == "/" and i + 1 < n and line[i + 1] == "/":
            break
        out.append(c)
        i += 1
    if in_str:
        state["unterminated"].append(True)
    return "".join(out)


def check(path):
    errors = []
    warnings = []
    with io.open(path, encoding="utf-8", errors="replace") as f:
        raw_lines = f.read().splitlines()

    state = {"block_comment": False, "unterminated": []}
    lines = [strip_comments(l, state) for l in raw_lines]

    if state["unterminated"]:
        errors.append("arquivo: string \" nao terminada (em %d linha(s))" % len(state["unterminated"]))
    if state["block_comment"]:
        errors.append("arquivo: comentario /* sem fechamento */")

    # ------------------------------------------------------------------
    # 1a passada: declaracoes, labels
    # ------------------------------------------------------------------
    declared = set()
    labels = {}
    block_locals = {}   # indice do bloco -> set()

    for num, line in enumerate(lines, start=1):
        s = line.strip()
        if not s:
            continue
        first = s.split()[0]

        if first in ("LVAR_INT", "LVAR_FLOAT", "LVAR_TEXT_LABEL", "LVAR_TEXT_LABEL16",
                     "CONST_INT", "CONST_FLOAT"):
            names = IDENT.findall(HEXLIT.sub(" ", s[len(first):]))
            declared.update(n.lower() for n in names)
            for name in names:
                block_locals.setdefault("atual", set()).add(name) \
                    if False else None
            continue

        m = LABEL_DEF.match(s)
        if m and first not in STRUCT:
            labels[m.group(1).lower()] = num

    # ------------------------------------------------------------------
    # 2a passada: blocos, estruturas, variaveis
    # ------------------------------------------------------------------
    depth = {"if": 0, "while": 0, "repeat": 0, "switch": 0, "brace": 0}
    stacks = {"if": [], "while": [], "switch": [], "repeat": []}
    brace_stack = []
    local_count = {}
    # rastreio dos ponteiros de lista (ver GP_CALL acima)
    addr_key = {}   # variavel -> (tipo, n): guarda o ENDERECO do slot
    ptr_key = {}    # variavel -> (tipo, n): guarda o PONTEIRO que veio do slot
    freed = {}      # variavel -> {(tipo, n)}: ponteiros ja' deletados

    for num, line in enumerate(lines, start=1):
        s = line.strip()
        if not s:
            continue
        first = s.split()[0]

        if s.startswith("{"):
            depth["brace"] += 1
            brace_stack.append((num, len(declared)))
            local_count[depth["brace"]] = set()
            continue
        if s.startswith("}"):
            if depth["brace"] <= 0:
                errors.append("%d: '}' sem '{' correspondente" % num)
            else:
                opened, _ = brace_stack.pop()
                depth["brace"] -= 1
            continue

        if first in ("LVAR_INT", "LVAR_FLOAT", "LVAR_TEXT_LABEL", "LVAR_TEXT_LABEL16"):
            if depth["brace"] > 0:
                local_count[depth["brace"]].update(IDENT.findall(s[len(first):]))
            continue

        # ---- ponteiros de lista: getListPointer / READ_MEMORY / DELETE_LIST ----
        # O mod guarda nos slots da tabela "lists" (e nas proprias variaveis)
        # ponteiros de listas CLEO+ e de buffers de arquivo. Estes tres erros
        # ja' causaram crash neste mod:
        #   * READ/WRITE_STRUCT_OFFSET usando o ENDERECO do slot;
        #   * DELETE_LIST duas vezes no mesmo ponteiro;
        #   * (aviso) DELETE_LIST/FREE em ponteiro que pode estar zerado.
        escritos = []
        mo = OUT_END.search(s)
        if mo:
            escritos.append(mo.group(1).lower())
        else:
            mb = BARE_END.search(s)
            if mb and not is_all_caps(mb.group(1)):
                escritos.append(mb.group(1).lower())
        mp = PLAIN_ASSIGN.match(s)
        if mp:
            escritos.append(mp.group(1).lower())

        m = GP_CALL.search(s)
        if m:
            tipo, nb, out = m.group(1), m.group(2), m.group(3).lower()
            addr_key[out] = (tipo, nb)
            ptr_key.pop(out, None)
            continue

        if READ_MEM.match(s):
            partes = s.split()
            origem = partes[1].lower()
            destino = escritos[-1] if escritos else origem
            if origem in addr_key:
                ptr_key[destino] = addr_key[origem]
            addr_key.pop(destino, None)
            continue

        m = STRUCT_OP.match(s)
        if m:
            var = m.group(1).lower()
            chave = addr_key.get(var)
            if chave and chave[0].isdigit() and int(chave[0]) <= 3:
                errors.append(
                    "%d: %s usa '%s', que e' o ENDERECO de um slot de "
                    "getListPointer(%s, %s), e nao o dado - falta "
                    "'READ_MEMORY %s ...' antes. Ler/gravar 16 bytes depois do "
                    "slot acerta o slot vizinho (foi assim que o ponteiro de "
                    "outro nodes*.dat ficou torto e o FREE_MEMORY estourou)"
                    % (num, first, m.group(1), chave[0], chave[1], m.group(1)))
            addr_key.pop(var, None)
            ptr_key.pop(var, None)

        m = DELETE_LIST.match(s)
        if m:
            var = m.group(1).lower()
            chave = ptr_key.get(var)
            if chave is not None:
                if chave in freed.get(var, set()):
                    errors.append(
                        "%d: DELETE_LIST %s: MESMO ponteiro de novo "
                        "(getListPointer(%s, %s)) - no CLEO+ o DELETE_LIST faz "
                        "'delete' sem validar, entao sao dois 'delete' no mesmo "
                        "bloco: double free = heap corrompido"
                        % (num, m.group(1), chave[0], chave[1]))
                freed.setdefault(var, set()).add(chave)
                ptr_key.pop(var, None)
            continue

        m = CREATE_LIST.search(s)
        if m:
            var = m.group(1).lower()
            addr_key.pop(var, None)
            ptr_key.pop(var, None)
            freed.pop(var, None)
            continue

        for var in escritos:
            addr_key.pop(var, None)
            ptr_key.pop(var, None)

        if first == "IF":
            depth["if"] += 1
            stacks["if"].append(num)
            continue
        if first == "ENDIF":
            if depth["if"] <= 0:
                errors.append("%d: ENDIF sem IF" % num)
            else:
                depth["if"] -= 1
                stacks["if"].pop()
            continue
        if first == "WHILE":
            depth["while"] += 1
            stacks["while"].append(num)
            continue
        if first == "ENDWHILE":
            if depth["while"] <= 0:
                errors.append("%d: ENDWHILE sem WHILE" % num)
            else:
                depth["while"] -= 1
                stacks["while"].pop()
            continue
        if first == "REPEAT":
            depth["repeat"] += 1
            stacks["repeat"].append(num)
            continue
        if first == "ENDREPEAT":
            if depth["repeat"] <= 0:
                errors.append("%d: ENDREPEAT sem REPEAT" % num)
            else:
                depth["repeat"] -= 1
                stacks["repeat"].pop()
            continue
        if first == "SWITCH":
            depth["switch"] += 1
            stacks["switch"].append(num)
            continue
        if first == "ENDSWITCH":
            if depth["switch"] <= 0:
                errors.append("%d: ENDSWITCH sem SWITCH" % num)
            else:
                depth["switch"] -= 1
                stacks["switch"].pop()
            continue

        # ---- linha de codigo: atribuicao?
        m = ASSIGN.match(s)
        if m:
            lhs = m.group(1)
            if not is_all_caps(lhs) and lhs.lower() not in declared:
                errors.append("%d: variavel nao declarada: %s" % (num, lhs))
            for ident in IDENT.findall(HEXLIT.sub(" ", m.group(2))):
                if is_all_caps(ident) or ident in TEXT_PARAMS:
                    continue
                if ident.lower() not in declared:
                    errors.append("%d: variavel nao declarada: %s" % (num, ident))
            continue

        # ---- linha de opcode (primeiro token todo em maiusculas)
        if is_all_caps(first) or first in STRUCT:
            tokens = IDENT.findall(HEXLIT.sub(" ", s))
            # em GOSUB/GOTO/CLEO_CALL/STREAM_... o token seguinte e um label
            skip = 2 if first in ("GOSUB", "GOTO", "CLEO_CALL",
                                  "STREAM_CUSTOM_SCRIPT_FROM_LABEL") else 1
            for ident in tokens[skip:]:
                if is_all_caps(ident) or ident in TEXT_PARAMS:
                    continue
                if ident.lower() in labels or ident.lower() in declared:
                    continue
                errors.append("%d: variavel nao declarada: %s" % (num, ident))
            continue

        # ---- qualquer outra coisa (bytes de DUMP, etc)
        warnings.append("%d: linha nao reconhecida: %s" % (num, s[:50]))

    # ------------------------------------------------------------------
    # labels referenciados
    # ------------------------------------------------------------------
    for num, line in enumerate(lines, start=1):
        for ref in CALL_REF.findall(line):
            if ref.lower() not in labels:
                errors.append("%d: label inexistente: %s" % (num, ref))

    # ------------------------------------------------------------------
    # gta3script: uma unica operacao por expressao
    # (o gta3sc para com "expected newline after this token" quando aparece
    #  algo como `size += 14 * naviCount`)
    # ------------------------------------------------------------------
    OPERATORS = {"+", "-", "*", "/", "+=", "-=", "*=", "/="}
    for num, line in enumerate(lines, start=1):
        s = re.sub(r'"[^"]*"', '""', line)      # ignora dentro de strings
        ops = [t for t in s.split() if t in OPERATORS]
        if len(ops) > 1:
            errors.append("%d: mais de uma operacao na mesma expressao (%s) - "
                          "o gta3script so' aceita uma; quebre em duas linhas"
                          % (num, " ".join(ops)))

    # ------------------------------------------------------------------
    # limite de 32 locais por bloco
    # ------------------------------------------------------------------
    for idx, names in sorted(local_count.items()):
        if len(names) > 32:
            errors.append("bloco %d: %d variaveis locais (limite 32)" % (idx, len(names)))

    for name, stack in (("IF", stacks["if"]), ("WHILE", stacks["while"]),
                        ("REPEAT", stacks["repeat"]), ("SWITCH", stacks["switch"])):
        for ln in stack:
            errors.append("%d: bloco %s nao fechado" % (ln, name))
    for ln, _ in brace_stack:
        errors.append("%d: bloco '{' nao fechado" % ln)

    return errors, warnings


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    total = 0
    for path in sys.argv[1:]:
        errors, warnings = check(path)
        print("=== %s" % path)
        for e in errors:
            print("  ERRO  %s" % e)
        if "-v" in sys.argv:
            for w in warnings:
                print("  aviso %s" % w)
        print("  %d erro(s), %d aviso(s)" % (len(errors), len(warnings)))
        total += len(errors)
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main())
