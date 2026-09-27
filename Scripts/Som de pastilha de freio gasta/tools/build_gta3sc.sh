#!/usr/bin/env bash
#
# build_gta3sc.sh - compila o gta3sc (thelink2012/gta3sc) SEM cmake.
#
# Por que existe: o build.sh usa o cmake, que e' o caminho oficial e o
# recomendado. Em maquinas sem cmake (ou sem rede para instalar) este script
# faz a mesma coisa chamando o g++ na mao - a lista de arquivos e as flags sao
# as mesmas do CMakeLists.txt do projeto, entao o binario sai equivalente.
#
# Uso:
#   bash tools/build_gta3sc.sh                # compila e devolve o caminho
#   WORKDIR=/tmp/x bash tools/build_gta3sc.sh # outro diretorio de trabalho
#   GTA3SC_REF=master bash tools/build_gta3sc.sh
#
set -euo pipefail

GTA3SC_REPO="${GTA3SC_REPO:-https://github.com/thelink2012/gta3sc.git}"
GTA3SC_REF="${GTA3SC_REF:-e9b4c3035c77b013f57af8595bc76b777acf73f6}"
WORKDIR="${WORKDIR:-${TMPDIR:-/tmp}/brakepad-gta3sc-nocmake}"

log() { printf '>> %s\n' "$*"; }
die() { printf 'erro: %s\n' "$*" >&2; exit 1; }

command -v git    >/dev/null 2>&1 || die "git nao encontrado no PATH"
command -v g++    >/dev/null 2>&1 || die "g++ nao encontrado no PATH (instale build-essential)"

mkdir -p "$WORKDIR"
SRC="$WORKDIR/gta3sc"
BUILD="$SRC/build"

if [ ! -d "$SRC/.git" ]; then
    log "clonando o gta3sc em $SRC"
    git clone --quiet --depth 1 "$GTA3SC_REPO" "$SRC"
fi

if ! git -C "$SRC" cat-file -e "${GTA3SC_REF}^{commit}" 2>/dev/null; then
    git -C "$SRC" fetch --quiet --depth 1 origin "$GTA3SC_REF" 2>/dev/null || true
fi
if git -C "$SRC" cat-file -e "${GTA3SC_REF}^{commit}" 2>/dev/null; then
    git -C "$SRC" checkout --quiet "$GTA3SC_REF"
    log "gta3sc no commit $GTA3SC_REF"
else
    log "aviso: commit $GTA3SC_REF nao encontrado - compilando o master atual"
fi

# ---- git-sha1.cpp (o CMakeLists gera esse arquivo na hora) -----------------
SHA1="$(git -C "$SRC" rev-parse HEAD)"
BRANCH="$(git -C "$SRC" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
mkdir -p "$BUILD"
sed -e "s/@GIT_SHA1@/$SHA1/" \
    -e "s/@GIT_BRANCH@/$BRANCH/" \
    -e "s/@GIT_DESCRIBE_TAG@//" \
    "$SRC/git-sha1.cpp.in" > "$BUILD/git-sha1.cpp"

# ---- compila --------------------------------------------------------------
# Mesmos Includes e mesmas definicoes do CMakeLists.txt (C++17, sem PCH no GCC).
INCLUDES=(
    -I"$SRC/src" -I"$SRC/deps" -I"$SRC/deps/rapidxml" -I"$SRC/deps/cppformat"
    -I"$SRC/deps/optional/include" -I"$SRC/deps/expected/include"
    -I"$SRC/deps/any/include" -I"$SRC/deps/variant/include"
)

SOURCES=(
    "$BUILD/git-sha1.cpp"
    "$SRC/src/stdinc.cpp"
    "$SRC/src/codegen.cpp"
    "$SRC/src/config.cpp"
    "$SRC/src/commands.cpp"
    "$SRC/src/compiler.cpp"
    "$SRC/src/disassembler.cpp"
    "$SRC/src/main.cpp"
    "$SRC/src/main_compile.cpp"
    "$SRC/src/main_decompile.cpp"
    "$SRC/src/parser_lexer.cpp"
    "$SRC/src/parser_syntax.cpp"
    "$SRC/src/program.cpp"
    "$SRC/src/script.cpp"
    "$SRC/src/symtable.cpp"
    "$SRC/src/system.cpp"
    "$SRC/deps/cppformat/cppformat/format.cc"
)

log "compilando o gta3sc com g++ (sem cmake)"
g++ -std=c++17 -O2 -w -Wno-placement-new -Wno-parentheses-equality \
    -Wno-tautological-compare \
    -DGTA3SC_USING_GIT_DESCRIBE \
    "${INCLUDES[@]}" \
    "${SOURCES[@]}" \
    -o "$BUILD/gta3sc"

[ -x "$BUILD/gta3sc" ] || die "nao gerou $BUILD/gta3sc"

# o gta3sc le' a pasta config/ AO LADO do binario
rm -rf "$BUILD/config"
cp -r "$SRC/config" "$BUILD/config"
log "pronto: $BUILD/gta3sc"
"$BUILD/gta3sc" version 2>/dev/null | head -2 || true
printf '%s\n' "$BUILD/gta3sc"
