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
3402 bytes   SHA-256 431fa120f1e6656595bf45069fe2359718c7c907deabbc02035f9bc6348c2070
```

**Sem cmake?** O `build.sh` exige o `cmake` para compilar o `gta3sc` da fonte.
Em máquina sem cmake (ou sem rede para instalar) use o
[`tools/build_gta3sc.sh`](tools/build_gta3sc.sh), que faz a mesma compilação
chamando o `g++` na mão, com as mesmas flags e a mesma lista de arquivos do
`CMakeLists.txt`:

```bash
GTA3SC=$(bash tools/build_gta3sc.sh | tail -1)   # imprime o caminho do gta3sc
GTA3SC="$GTA3SC" bash tools/build.sh              # e compila o mod
```

## 5. As seis armadilhas do gta3script que aparecem neste source

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
   `LVAR_FLOAT` custam 1 slot; `LVAR_TEXT_LABEL` (8 bytes) custa **2**; e
   `LVAR_TEXT_LABEL16` (16 bytes) custa **4**. Não existe escopo por label: um
   `GOSUB` não abre um conjunto novo de variáveis, então tudo conta junto. É
   por isso que o `.ini` aparece como literal nos comandos de leitura e que o
   script reaproveita `iValue`/`iNext` em trechos comentados.

3. **Text label é curto demais para qualquer caminho de arquivo.**
   `LVAR_TEXT_LABEL` segura **7 caracteres** e `LVAR_TEXT_LABEL16` segura **15**
   (o último byte de cada um é o terminador nulo) — é por isso que eles
   custam 2 e 4 slots. Um caminho de som ("CLEO\BrakePadSound\brakepad.wav")
   tem 31, então **nenhum** dos dois serve: o caminho era truncado e o
   `DOES_FILE_EXIST` respondia não mesmo com o arquivo no lugar certo
   (foi exatamente o bug da primeira build publicada).

   A forma certa é um **buffer na área de dados**, com um `LVAR_INT` de ponteiro:

   ```
   // na area de dados, depois do SCRIPT_END (128 bytes zerados):
   txtSoundBuffer:
   DUMP
   00 00 ... (128 x 00)
   ENDDUMP

   // no script:
   GET_LABEL_POINTER txtSoundBuffer (pBuffer)
   READ_STRING_FROM_INI_FILE "CLEO\BrakePadSound.ini" "Config" "SoundFile" pBuffer
   IF NOT DOES_FILE_EXIST $pBuffer
       ...
   ENDIF
   ...
   LOAD_3D_AUDIO_STREAM $pBuffer (hStream)
   ```

   São 127 caracteres de capacidade por **1 slot só** (o buffer vive nos dados
   do script, não nas variáveis locais). E repare que o out param de
   `READ_STRING_FROM_INI_FILE` aqui é o **ponteiro** (`pBuffer`), não a
   variável de texto.

4. **Argumento string quer `$` na frente da variável** — e isso é literal, não
   convenção. Sem o `$`, o identificador vira uma *text label* com o **nome da
   variável** como conteúdo (o IR2 mostra `DOES_FILE_EXIST "TXT16"` e o
   compilador avisa *"text label collides with some variable name"*; foi assim
   que `TAMANHO_DA_CAMISA` apareceu na tela na vez em que o `$` faltava).
   Com `$`:
   * variável de texto → os 7/15 bytes do slot viram o endereço da string;
   * `LVAR_INT` com um `GET_LABEL_POINTER` → o valor da variável **é** o
     endereço do buffer, e o opcode lê a string de lá.

   Parâmetro de **saída** nunca leva `$` — e `READ_STRING_FROM_INI_FILE` com
   text label na saída é justamente o caso quebrado do item 3.

5. **`NOP` logo depois do `SCRIPT_START`.** Sem ele o script começa com uma
   referência a label no offset zero e o compilador aborta com
   `compiled script references a label at the zero offset`.

6. **Os opcodes `CSET` têm os NOMOS trocados em relação ao que fazem.** Este é
   o quarto bug deste mod e o mais caro: não dá erro de compilação, não dá
   warning, e o script simplesmente nunca dispara som. O opcode **0092**,
   chamado no `cleo.xml` do gta3sc de `CSET_LVAR_INT_TO_LVAR_FLOAT`, na
   verdade faz **float → int** (é o mesmo 0092 do Sanny, documentado como
   `22@ = float 17@ to_integer`); o **0093**, chamado de
   `CSET_LVAR_FLOAT_TO_LVAR_INT`, faz **int → float**. Nos dois o **destino vem
   primeiro**. Ou seja:

   | o que você quer | o que escrever |
   |---|---|
   | `fStep = (float)iValue` (int → float) | `CSET_LVAR_FLOAT_TO_LVAR_INT fStep iValue` |
   | `iPrevVel = (int)f` (float → int) | `CSET_LVAR_INT_TO_LVAR_FLOAT iPrevVel f` |

   A v2.6/v2.7 escreveram ao contrário, o
   `dt` virou `0.0`, a pressão nunca passou do gatilho e o mod ficou
   **mudo no carro do jogador e no dos NPCs** — mesmo com o pedal lido
   corretamente. A confirmação veio do `SCRLog` do jogo: o `dt` chegava valendo
   46 ms na linha `CSET` e a variável `fStep` continuava `0.0` na linha
   seguinte.

   Esse bug esc fugiu de todos os testes de mesa justamente porque eles rodam em
   Python e não em opcode — por isso a seção 9 do `test_model.py` agora lê o
   `.sc` e trava a ordem dos operandos.

Extras que só aparecem aqui: conversão int↔float é com os opcodes acima
(misturar `int` e `float` na mesma expressão **não** compila), o operador `<>`
**não existe** (`IF iReg <> 2` dá `expected command`; escreva `IF NOT iReg = 2`)
e o evento de criação de carro é `SET_SCRIPT_EVENT_CAR_CREATE ON <label> <var>`
na 1.0.7 (a 1.2.0 virou `0/1`).

## 6. O modelo de tempo (o que dá para testar sem o jogo)

A parte que **não** é opcode puro é o modelo de desaceleração e o ciclo de
vida do som (`KeepSound`/`AdjustSound`/`StopSound`), e é justamente onde mora a
diferença entre um chiado que acompanha o freio e um chiado que fica tocando no
vazio. Está transcrito linha por linha em
[`tools/test_model.py`](tools/test_model.py), que roda **117 verificações** sem
precisar do jogo:

```bash
python3 tools/test_model.py
```

Cobre: **carro parado não canta** (com o pé no freio, mesmo com
`BrakeThreshold = 0`); o chiado chegando na hora em que o carro freia de
verdade; `BrakeThreshold`; `BrakeForce` (a queda em m/s² que separa freada de
encostada no freio); **o caso relatado na v2.8** (frear até o carro parar e soltar
o freio tem de encerrar o som em ~`Fade`); o *fade* em segundos, idêntico de 25
a 240 FPS; o término natural do arquivo e o rearme (6,165 s recomeçando sozinho
numa frenagem de 12 s); **ausência de sobreposição** com áudio de 1,2 s e de
6,165 s; o teto de 6 sons com 10 carros no pool; a escala de volume (pedal com
piso de 50%, velocidade com a conversão 3.6, volume do menu, teto em 1,0); o
pedal da IA pisando sem virar metralhadeira; e a **conferência do source**
(ordem dos operandos do `CSET`, o `$` do ponteiro do buffer, a contagem de slots
≤ 32, a desaceleração em m/s², o `Fade` em vez do `Cooldown`, e a promessa de
"o `.ini` é lido só no começo"). Se você mexer no `UpdateCar`, no `AdjustSound`,
nos defaults do `.ini` ou nas conversões, rode isso antes de brigar com o jogo.

## 7. O som (`brakepad.wav`)

O `.wav` que vem no mod é **sintetizado** por
[`tools/make_sound.py`](tools/make_sound.py) (só biblioteca padrão do Python),
porque o som do mod original é dos autores dele e não é nosso para
redistribuir. Para usar o som do mod v2.5.1, copie o `.wav` dele (ou o `.mp3`) para
`CLEO\BrakePadSound\brakepad.wav` ou aponte o `.ini` para o caminho dele. O
script não depende da duração do arquivo: os ~6 s do mod original funcionam
igual ao placeholder de 1,2 s deste pacote.

```bash
python3 tools/make_sound.py                 # recria o wav padrão
python3 tools/make_sound.py --seed 1234     # outra variação
```

Os opcodes de audio stream (`0xAC1` `LOAD_3D_AUDIO_STREAM`, `0xAC5`
`SET_PLAY_3D_AUDIO_STREAM_AT_CAR`, e `0xAC0`/`0xABC`/`0xAAD` para loop, volume
e estado) são **nativos do San Andreas** — funcionam com CLEO+ sem o CLEO 4.
Eles aceitam `.wav` (PCM) e `.mp3`; para tocar, prefira **mono a 22050 Hz**, que
é a taxa do motor de áudio do jogo. O arquivo pode ser outro: é só apontar a
chave `SoundFile` do `.ini` para ele (o caminho tem que caber em 127
caracteres).

## 8. Instalar

`CLEO/BrakePadSound.cs` e `CLEO/BrakePadSound.ini` vão para a pasta `CLEO` do
jogo e `CLEO/BrakePadSound/brakepad.wav` para a subpasta. Se você usa o
ModLoader do MixMods, extraia a pasta do mod inteira para
`ModLoader/Scripts/`. Requer **CLEO+ 1.0.7+** e o **CLEO Redux** (ou o
ModLoader, que já traz o CLEO).
