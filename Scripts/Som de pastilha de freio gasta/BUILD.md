# Compilando o "Som de pastilha de freio gasta" (gta3script / gta3sc)

> **Resumo:** este mod é escrito em **gta3script** e compilado com o
> [`gta3sc`](https://github.com/thelink2012/gta3sc) — **não** com o Sanny
> Builder. As duas linguagens se parecem (têm `IF/ENDIF`, `LVAR_INT`, labels),
> mas um `.sc` de gta3script não compila no Sanny, e vice-versa.

O `BrakePadSound.cs` que está na pasta `CLEO/` foi gerado com a receita abaixo,
automatizada em [`tools/build.sh`](tools/build.sh).

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

O mod usa opcodes que só o **CLEO+** tem: `GET_CAR_VALUE` (0xEFC),
`GET_CAR_PEDALS` (0xEFD), `GET_VEHICLE_SUBCLASS` (0xE12),
`GET_ANY_CAR_NO_SAVE_RECURSIVE` (0xEA8), `INIT/GET/SET_EXTENDED_CAR_VAR`
(0xE17–0xE19), `SET_SCRIPT_EVENT_CAR_CREATE` (0xED5), `GET_AUDIO_SFX_VOLUME`
(0xE21) e os de audio stream (0xAC0–0xABC). O `cleo.xml` que vem no gta3sc não
tem nenhum deles.

O repositório do CLEO+ publica um `cleo.xml` pronto para o gta3sc:

* arquivo pronto (tag do mod): `(for developers)/gta3script/cleo.xml`
  → <https://github.com/JuniorDjjr/CLEOPlus/blob/v1.0.7/(for%20developers)/gta3script/cleo.xml>

**Use a tag `v1.0.7`.** A partir da `v1.2.0` os nomes/assinaturas mudam
(`SET_THREAD_VAR` virou `SET_SCRIPT_VAR`, o `GET_*_EXTENDED_*_VAR` passou a ter
3 argumentos em vez de 4, o `SET_SCRIPT_EVENT_*` passou a aceitar `0/1` no
lugar de `ON/OFF`) e este source deixa de compilar.

```bash
# instalação/binário pronto:
cp cleo.xml /caminho/do/gta3sc/config/gtasa/cleo.xml
# build por CMake:
cp cleo.xml /caminho/do/gta3sc/build/config/gtasa/cleo.xml
```

## 3. Compilar

```bash
gta3sc compile BrakePadSound.sc \
    --config=gtasa --guesser -fno-entity-tracking -fbreak-continue -fcleo --cs \
    -o BrakePadSound.cs
```

| Flag | Por quê |
|---|---|
| `--config=gtasa` | lê `config/gtasa/` (comandos, constantes e flags padrão do jogo) |
| `--guesser` | vem da receita validada do repositório (o `commandline.txt` do gtasa usa `-fswitch`, que só funciona no guesser) |
| `-fno-entity-tracking` | **obrigatório**: handles e retornos de `CLEO_CALL`/out params ficam em variáveis sem tipo |
| `-fbreak-continue` | libera `BREAK`/`CONTINUE`; neste source não muda o bytecode |
| `--cs` | script CLEO (`.cs`) — também liga o `-fcleo`, que traz os comandos de CLEO/CLEO+ |

Saída esperada: **nenhuma mensagem** (nem warning) e um `.cs` de ~3,1 KB.

**Pelo VSCode**, com a extensão
[GTA3script](https://marketplace.visualstudio.com/items?itemName=thelink2012.gta3script):
`gta3script.compiler` = caminho do `gta3sc`, `gta3script.config` = `gtasa` e
`gta3script.buildflags.gtasa` =
`["--guesser","-fbreak-continue","-fno-entity-tracking","-fcleo","--cs"]`.

## 4. Script automatizado

```bash
bash tools/build.sh                        # Linux/macOS/WSL/MSYS2
GTA3SC=/caminho/gta3sc bash tools/build.sh # usando um compilador já pronto
```

Ele usa o `gta3sc` já compilado (ou clona e compila na commit testada), instala
o `cleo.xml` do CLEO+ `v1.0.7`, compila o `.sc` e mostra tamanho + SHA-256 do
`.cs` gerado. O `.cs` que está no repositório:

```
3360 bytes   SHA-256 204fb0b3f44331439aa625b2950cff18ccebbf6ec4ed38aaf7ecfa4a1dd379f9
```

## 5. As cinco armadilhas do gta3script que aparecem neste source

1. **Uma operação por expressão.** Isto é erro de sintaxe
   (`expected newline after this token`):

   ```
   fPress += fStep * fBrake
   ```

   E isto é o certo:

   ```
   f = fStep
   f *= fBrake
   fPress += f
   ```

2. **32 slots de variável local, e o preço não é igual.** `LVAR_INT` e
   `LVAR_FLOAT` custam 1 slot; `LVAR_TEXT_LABEL16` (8 bytes) custa **4**; e
   `LVAR_TEXT_LABEL` (16 bytes) custa **2**. Não existe escopo por label: um
   `GOSUB` não abre um conjunto novo de variáveis, então tudo conta junto. É
   por isso que o `.ini` aparece como literal nos comandos de leitura e que o
   script reaproveita `iValue`/`iNext` em trechos comentados.

3. **String literal em text label só na variante de 8 bytes.**
   `LVAR_TEXT_LABEL16 txt` aceita `txt = "CLEO\algo.ini"`, mas
   `LVAR_TEXT_LABEL txt` (16 bytes) **não** — e é justamente a de 16 bytes que
   o `GET_NAME_OF_VEHICLE_MODEL` exige na saída. Erro:
   `variable type does not match argument type`.

4. **Argumento string quer `$` na frente da variável.** `LOAD_3D_AUDIO_STREAM
   txtSound` é lido pelo compilador como *referência a text label* (e ainda
   dispara o warning *"text label collides with some variable name"*);
   `LOAD_3D_AUDIO_STREAM $txtSound` é lido como *variável* — que é o que a gente
   quer. Parâmetro de **saída** nunca leva `$`.

5. **`NOP` logo depois do `SCRIPT_START`.** Sem ele o script começa com uma
   referência a label no offset zero e o compilador aborta com
   `compiled script references a label at the zero offset`.

Extras que só aparecem aqui: conversão int↔float é com
`CSET_LVAR_INT_TO_LVAR_FLOAT` / `CSET_LVAR_FLOAT_TO_LVAR_INT` (misturar `int`
e `float` na mesma expressão **não** compila), o operador `<>` **não existe**
(`IF iReg <> 2` dá `expected command`; escreva `IF NOT iReg = 2`) e o evento de
criação de carro é `SET_SCRIPT_EVENT_CAR_CREATE ON <label> <var>` na 1.0.7 (a
1.2.0 virou `0/1`).

## 6. O modelo de tempo (o que dá para testar sem o jogo)

A parte que **não** é opcode puro é o modelo de pressão/cooldown do
`UpdateCar`, e é justamente onde mora a diferença entre "um som por aperto" e
metralhadeira. Ele está transcrito linha por linha em
[`tools/test_model.py`](tools/test_model.py), que roda 60 casos sem precisar do
jogo:

```bash
python3 tools/test_model.py
```

Cobre: um som por aperto com `Cooldown = 0` em 30/60/144/240 FPS e apertos de
0,2 s a 12 s; repetição enquanto o freio está apertado com `Cooldown = 1100`; o
corte de velocidade; `BrakeThreshold`; a escala de volume (pedal, velocidade,
volume do menu, teto em 1,0); `PressureRate`/`TriggerPressure` em milissegundos
(30 FPS e 240 FPS dão o mesmo resultado); e o pedal da IA pisando sem virar
metralhadeira. Se você mexer no `UpdateCar` ou nos defaults do `.ini`, rode
isso antes de brigar com o jogo.

## 7. O som (`brakepad.wav`)

O `.wav` que vem no mod é **sintetizado** por
[`tools/make_sound.py`](tools/make_sound.py) (só biblioteca padrão do Python),
porque o som do mod original é dos autores dele e não é nosso para
redistribuir. Para usar o som do mod v2.5.1, copie o `.wav` dele para
`CLEO\BrakePadSound\brakepad.wav` ou aponte o `.ini` para o caminho dele:

```bash
python3 tools/make_sound.py                 # recria o wav padrão
python3 tools/make_sound.py --seed 1234     # outra variação
```

## 8. Instalar

`CLEO/BrakePadSound.cs` e `CLEO/BrakePadSound.ini` vão para a pasta `CLEO` do
jogo e `CLEO/BrakePadSound/brakepad.wav` para a subpasta. Se você usa o
ModLoader do MixMods, extraia a pasta do mod inteira para
`ModLoader/Scripts/`. Requer **CLEO+ 1.0.7+** e o **CLEO Redux** (ou o
ModLoader, que já traz o CLEO).
