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
    (`size += 14 * naviCount` e' erro de sintaxe no gta3sc).

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
