#!/usr/bin/env bash
#
# build.sh - compila o Visual Path Editor (.sc) com o compilador gta3sc.
#
# O source e' linguagem **gta3script** (nao Sanny Builder!) - veja BUILD.md.
#
# Uso:
#   bash tools/build.sh                        # gera VisualPathEditor.cs ao lado do .sc
#   bash tools/build.sh /tmp/saida.cs          # outro arquivo de saida
#   GTA3SC=/caminho/gta3sc bash tools/build.sh # usa um gta3sc que voce ja tem
#   GTA3SC_REF=master bash tools/build.sh      # usa o master em vez do commit testado
#
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MOD_DIR="$(cd "$HERE/.." && pwd)"
SOURCE="$MOD_DIR/VisualPathEditor.sc"
OUTPUT="${1:-$MOD_DIR/VisualPathEditor.cs}"

GTA3SC_REPO="${GTA3SC_REPO:-https://github.com/thelink2012/gta3sc.git}"
GTA3SC_REF="${GTA3SC_REF:-e9b4c3035c77b013f57af8595bc76b777acf73f6}"
CLEO_PLUS_REPO="${CLEO_PLUS_REPO:-https://github.com/JuniorDjjr/CLEOPlus.git}"
# O mod foi escrito para o CLEO+ 1.0.7. Tags mais novas mudam nomes/assinaturas
# de comandos e este source deixa de compilar (veja BUILD.md secao 2).
CLEO_PLUS_TAG="${CLEO_PLUS_TAG:-v1.0.7}"
WORKDIR="${WORKDIR:-${TMPDIR:-/tmp}/vpe-gta3sc}"

log()  { printf '>> %s\n' "$*"; }
die()  { printf 'erro: %s\n' "$*" >&2; exit 1; }

command -v git >/dev/null 2>&1 || die "git nao encontrado no PATH"

# --------------------------------------------------------------- 1. gta3sc
if [ -n "${GTA3SC:-}" ]; then
    COMPILER="$GTA3SC"
    [ -x "$COMPILER" ] || die "GTA3SC=$COMPILER nao e' um executavel"
    log "usando o compilador informado: $COMPILER"
else
    mkdir -p "$WORKDIR"
    SRC_DIR="$WORKDIR/gta3sc"
    BUILD_DIR="$SRC_DIR/build"

    if [ ! -d "$SRC_DIR/.git" ]; then
        log "clonando o gta3sc em $SRC_DIR"
        git clone --quiet --depth 1 "$GTA3SC_REPO" "$SRC_DIR"
    fi

    if ! git -C "$SRC_DIR" cat-file -e "${GTA3SC_REF}^{commit}" 2>/dev/null; then
        git -C "$SRC_DIR" fetch --quiet --depth 1 origin "$GTA3SC_REF" 2>/dev/null || true
    fi

    if git -C "$SRC_DIR" cat-file -e "${GTA3SC_REF}^{commit}" 2>/dev/null; then
        git -C "$SRC_DIR" checkout --quiet "$GTA3SC_REF"
        log "gta3sc no commit $GTA3SC_REF"
    else
        log "aviso: nao achei o commit $GTA3SC_REF - compilando o master atual"
    fi

    command -v cmake >/dev/null 2>&1 || die "cmake nao encontrado (pip install cmake, ou use GTA3SC=...)"
    command -v make  >/dev/null 2>&1 || die "make nao encontrado"

    mkdir -p "$BUILD_DIR"
    log "configurando o cmake"
    # -DCMAKE_POLICY_VERSION_MINIMUM=3.5 e' necessario em CMake >= 4.x
    ( cd "$BUILD_DIR" && cmake .. -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_POLICY_VERSION_MINIMUM=3.5 >/dev/null )

    JOBS="$( (nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 2) )"
    log "compilando o gta3sc (make -j$JOBS)"
    ( cd "$BUILD_DIR" && make -j"$JOBS" >/dev/null )

    COMPILER="$BUILD_DIR/gta3sc"
    [ -x "$COMPILER" ] || die "o build do gta3sc nao gerou $COMPILER"
fi

# o gta3sc le' a pasta config/ que fica AO LADO do binario
CONFIG_DIR="$(dirname "$COMPILER")/config"
[ -d "$CONFIG_DIR/gtasa" ] || die "nao achei $CONFIG_DIR/gtasa (config do jogo)"

# --------------------------------------------------- 2. config do CLEO+ 1.0.7
CLEO_XML="$WORKDIR/cleo-plus-$CLEO_PLUS_TAG.xml"
if [ ! -s "$CLEO_XML" ]; then
    mkdir -p "$WORKDIR"
    log "baixando o cleo.xml do CLEO+ $CLEO_PLUS_TAG"
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL -o "$CLEO_XML" \
            "https://raw.githubusercontent.com/JuniorDjjr/CLEOPlus/$CLEO_PLUS_TAG/(for%20developers)/gta3script/cleo.xml" \
            2>/dev/null || true
    fi
    if [ ! -s "$CLEO_XML" ]; then
        log "download direto falhou - clonando o repositorio do CLEO+"
        rm -rf "$WORKDIR/CLEOPlus"
        git -c advice.detachedHead=false clone --quiet --depth 1 \
            --branch "$CLEO_PLUS_TAG" "$CLEO_PLUS_REPO" "$WORKDIR/CLEOPlus"
        cp "$WORKDIR/CLEOPlus/(for developers)/gta3script/cleo.xml" "$CLEO_XML"
    fi
fi
[ -s "$CLEO_XML" ] || die "nao consegui obter o cleo.xml do CLEO+ $CLEO_PLUS_TAG"
cp "$CLEO_XML" "$CONFIG_DIR/gtasa/cleo.xml"
log "cleo.xml do CLEO+ $CLEO_PLUS_TAG instalado em $CONFIG_DIR/gtasa/"

# ------------------------------------------------------------- 3. compilar
log "compilando $(basename "$SOURCE")"
rm -f "$OUTPUT"
"$COMPILER" compile "$SOURCE" \
    --config=gtasa --guesser -fno-entity-tracking -fbreak-continue -fcleo --cs \
    -o "$OUTPUT"

[ -s "$OUTPUT" ] || die "o compilador nao gerou $OUTPUT"
SIZE="$(wc -c < "$OUTPUT" | tr -d ' ')"
log "pronto: $OUTPUT ($SIZE bytes)"
if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$OUTPUT"
elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$OUTPUT"
fi

# 37051 bytes / 6af889f2... = o .cs da v1.1 que esta no repositorio
if [ "$SIZE" != "37051" ]; then
    log "aviso: o .cs do repositorio (v1.1) tem 37051 bytes - o seu tem $SIZE."
    log "        Se voce mexeu no source, tudo bem; se nao mexeu, veja BUILD.md secao 5."
fi
