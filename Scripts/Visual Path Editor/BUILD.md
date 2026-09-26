# Compilando o Visual Path Editor (gta3script / gta3sc)

> **Resumo:** este mod é escrito em **gta3script** e é compilado com o
> [`gta3sc`](https://github.com/thelink2012/gta3sc) — **não** com o Sanny
> Builder. As duas linguagens se parecem (têm `IF/ENDIF`, `LVAR_INT`, labels),
> mas são diferentes: um `.sc` de gta3script não compila no Sanny, e o `.txt`
> do Sanny não compila no gta3sc.

O `VisualPathEditor.cs` que está nesta pasta foi gerado com a receita abaixo.
Todo o processo está automatizado em [`tools/build.sh`](tools/build.sh)
(seção 4).

---

## 1. O compilador

**Windows (mais rápido):** baixe o `gta3sc` pronto na página de releases
<https://github.com/thelink2012/gta3sc/releases> — o pacote traz o `gta3sc.exe`
e a pasta `config/`.

**Linux / macOS / MSYS2 / WSL — compilando a fonte:**

```bash
git clone https://github.com/thelink2012/gta3sc
cd gta3sc && mkdir build && cd build
cmake .. -DCMAKE_BUILD_TYPE=Release     # em CMake 4.x: + -DCMAKE_POLICY_VERSION_MINIMUM=3.5
make -j
```

O binário é `build/gta3sc`. Atenção: **o compilador lê a pasta `config/` que
fica ao lado do executável** (num build por CMake é `build/config/`, não
`gta3sc/config/`) — é lá que os arquivos do passo 2 precisam ficar.

## 2. A config do CLEO+ (obrigatória — e tem que ser a versão certa)

O `config/gtasa/cleo.xml` que vem no gta3sc **não tem** os comandos do CLEO+
que o mod usa (`CREATE_LIST`, `LIST_ADD`, `LIST_REMOVE_INDEX`,
`GET_LIST_VALUE_BY_INDEX`, `GET_LIST_SIZE`, `DELETE_LIST`, `RESET_LIST`,
`SET_THREAD_VAR`, `GET_THREAD_VAR`, `READ_STRUCT_OFFSET`, `WRITE_STRUCT_OFFSET`,
`INIT_EXTENDED_OBJECT_VARS`, `GET_EXTENDED_OBJECT_VAR`,
`SET_EXTENDED_OBJECT_VAR`, `DRAW_TEXTURE_PLUS`, `GET_TEXTURE_FROM_SPRITE`,
`CONVERT_3D_TO_SCREEN_2D`, `DRAW_STRING`, `CREATE_OBJECT_NO_SAVE`,
`STREAM_CUSTOM_SCRIPT_FROM_LABEL`, `GET_LAST_CREATED_CUSTOM_SCRIPT`, ...).
Sem ele o compile morre com centenas de `error: unknown command`.

O repositório do CLEO+ publica um `cleo.xml` já pronto para o gta3sc:

* arquivo pronto (tag do mod): `(for developers)/gta3script/cleo.xml`
  → <https://github.com/JuniorDjjr/CLEOPlus/blob/v1.0.7/(for%20developers)/gta3script/cleo.xml>

**Use a tag `v1.0.7`** (a mesma versão de CLEO+ que o mod pede). Duas mudanças
posteriores **quebram este source**:

| Versão | O que mudou | Efeito aqui |
|---|---|---|
| `v1.2.0` | `SET_THREAD_VAR`/`GET_THREAD_VAR` viraram `SET_SCRIPT_VAR`/`GET_SCRIPT_VAR` | `unknown command` em 71 linhas |
| commit `9cd0e1f` (dez/2024) | o 2º parâmetro de `OPEN_FILE` passou de `INT` para `STRING` | o mod passa `0x6272`/`0x6277` (que é `"rb"`/`"wb"` compactado — exatamente como o CLEO lê esse parâmetro) e o compilador reclama `expected string literal` |

Copie por cima da config do compilador:

```bash
# instalação/binário pronto:
cp cleo.xml /caminho/do/gta3sc/config/gtasa/cleo.xml
# build por CMake:
cp cleo.xml /caminho/do/gta3sc/build/config/gtasa/cleo.xml
```

## 3. Compilar

```bash
gta3sc compile VisualPathEditor.sc \
    --config=gtasa --guesser -fno-entity-tracking -fbreak-continue -fcleo --cs \
    -o VisualPathEditor.cs
```

O que cada flag faz aqui:

| Flag | Por quê |
|---|---|
| `--config=gtasa` | lê `config/gtasa/` (comandos, constantes e flags padrão do jogo) |
| `--guesser` | **obrigatório**: o source usa `SWITCH` (`-fswitch`); sem o guesser o compilador aborta com *"-fswitch only available in guesser mode"* |
| `-fno-entity-tracking` | **obrigatório**: o source guarda handles de objeto em variáveis sem tipo (`curobject`, retornos de `CLEO_CALL`); com o tracking ligado dá *"expected variable of type OBJECT but got NONE"* |
| `-fbreak-continue` | vem da receita do autor (libera `BREAK`/`CONTINUE` em `WHILE`/`REPEAT`); neste source não muda o bytecode |
| `--cs` | script CLEO (`.cs`) — também liga o `-fcleo`, que é o que traz os comandos de CLEO/CLEO+ |

Saída esperada: **nenhuma mensagem** (nem aviso) e um `.cs` de ~37 KB.

**Pelo VSCode**, com a extensão
[GTA3script](https://marketplace.visualstudio.com/items?itemName=thelink2012.gta3script):
`gta3script.compiler` = caminho do `gta3sc`, `gta3script.config` = `gtasa` e
`gta3script.buildflags.gtasa` =
`["--guesser","-fbreak-continue","-fno-entity-tracking","-fcleo","--cs"]`
(mesmo lembrete do passo 2 sobre o `cleo.xml`).

## 4. Script automatizado

```bash
bash tools/build.sh                        # Linux/macOS/WSL/MSYS2
GTA3SC=/caminho/gta3sc bash tools/build.sh # usando um compilador já pronto
```

Ele clona e compila o gta3sc (no commit com que este repositório foi validado),
pega o `cleo.xml` da tag `v1.0.7`, compila o `.sc`, e mostra o tamanho e o
SHA-256 do `.cs` gerado.

## 5. Como saber que o seu toolchain está certo

Compile o **source original 1.0** (o que o autor distribuiu em
[`TaylorNAlbarnaz/VisualPathEditor`](https://github.com/TaylorNAlbarnaz/VisualPathEditor))
com esta mesma receita: o `.cs` gerado sai **byte a byte igual** ao
`VisualPathEditor.cs` publicado pelo autor:

```
29837 bytes   SHA-256 43b6bd944c9e93a94c06737ed2a61cfdac9034d41b21247f2c2bcf659d45a84b
```

Foi assim que este toolchain foi validado. Se o seu gta3sc + `cleo.xml`
reproduzem esse arquivo, então qualquer diferença no `.cs` da v1.2 vem
só das correções do `CORRECOES.md`.

O `.cs` que está neste repositório (v1.2 — as correções de crash da seção 11
do `CORRECOES.md`):

```
37614 bytes   SHA-256 e43181e7f8d9cf6cb59480b0d372e1ee8de2f25dfe052226d9fae580e983217c
```

Era este o da v1.1 (antes das correções de memória):

```
37051 bytes   SHA-256 6af889f20b6503f3244435f1e6199c3236280ec5119437b934038c5f379532fd
```

Também testamos com o binário da **release 0.9.7** (o `.exe` da página de
releases, que é o caminho mais fácil no Windows): os dois arquivos saem com
**exatamente os mesmos bytes/hashes** — ou seja, dá pra usar o `gta3sc.exe`
pronto, não precisa compilar o compilador.

## 6. Detalhe da linguagem que mordeu a gente

Em gta3script cada **expressão aceita uma única operação**. Isto é erro de
sintaxe (`expected newline after this token`):

```
size += 14 * naviCount
```

E isto é o certo:

```
iz = 14 * naviCount
size += iz
```

A mesma regra vale para `a = b + c * d`, `(a + b) * 2`, `b + c + d`, etc.
Foi a única correção de sintaxe necessária para o source da v1.1 compilar —
o `tools/check_script.py` agora também avisa quando alguma linha tem duas
operações (ele não substitui o compilador, mas roda sem instalar nada).

## 7. Instalar o resultado

Veja a seção *"Como compilar e instalar"* do [`CORRECOES.md`](CORRECOES.md).
Resumo: `VisualPathEditor.cs` e `VisualPathEditor.ini` vão para a pasta `CLEO`
do jogo, `models/txd/pathtxd.txd` vai para `models/txd/`, e os
`nodes0.dat`…`nodes63.dat` extraídos do `gta3.img` precisam existir **em duas
cópias**: `CLEO/gta3img/` (leitura) e `CLEO/gta3img/compiled/` (gravação).
