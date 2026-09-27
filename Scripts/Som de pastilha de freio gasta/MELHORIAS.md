# v2.5.1 → v2.9: o que mudou, e por quê

Este documento é o detalhe técnico da reescrita. O resumo está no
[`leiame (ou morra).txt`](leiame%20(ou%20morra).txt) (pt) e no
[`Readme (or die).txt`](Readme%20(or%20die).txt) (en).

**A pergunta que motivou tudo:** *"tem como melhorar esse mod colocando pra o
som acontecer com NPCs também, igual com player?"* — sim, e é exatamente isso que
a v2.6 faz: o som deixou de ser "o carro do jogador freia" e passou a ser
"qualquer carro do jogo freia", com o jogador apenas sendo um carro a mais.

---

## 1. Como a v2.5.1 resolvia (e por que não dá pra esticar)

O mod original é um script CLEO comum (Sanny/gta3script) que pegava o carro do
jogador, comparava a velocidade com a do frame anterior, detectava a
desaceleração e tocava um som quando a queda era grande. Funcionava, mas:

- o laço era `IS_CHAR_IN_CAR $PLAYER_ACTOR hVeh` — só o carro do jogador
  entrava;
- o "valor do carro" vinha de uma **lista no `.ini`**, que não conhece carro
  added nem carro substituído;
- o volume era fixo e o `.ini` era lido durante o jogo.

Nada disso impede o NPC — o problema é que **o script nunca olhava os outros
carros**. E não dá para simplesmente "perguntar" ao jogo pelo pedal de freio de
outro carro nos opcodes do SCM: `GET_CAR_PEDALS` (0xEFD) é opcode do **CLEO+**,
e ele lê exatamente o campo `CVehicle::m_fBreakPedal`, que o jogo preenche tanto
para o jogador quanto para a IA. É esse opcode que destrava a v2.6.

## 2. A virada: a mesma arquitetura do Air Brake Sound v3

O `Air Brake Sound v3` (Junior_Djjr, 2022) já resolvia o mesmo problema para o
som de freio a ar: em vez de checar "o carro do jogador", ele percorre o pool de
veículos, lê o pedal de todo mundo e toca o som 3D no carro que freou. O script
dele (gta3script, com o `cleo.xml` do CLEO+) é a referência usada aqui — o que
muda é o **gatilho** (pastilha em vez de escape de freio) e a **filtro de valor**.

O esqueleto do laço, então:

```
GET_ANY_CAR_NO_SAVE_RECURSIVE <cursor> (<cursor> <car>)   // um carro do pool
  GET_EXTENDED_CAR_VAR <car> AUTO 1 <estado>              // filtro barato
    IS_CAR_ENGINE_ON <car>
      GET_CAR_COORDINATES + LOCATE_CAMERA_DISTANCE_TO_COORDINATES   // raio
        GET_CAR_PEDALS <car> <gas> <freio>                 // o que o NPC fez
        GET_CAR_SPEED <car> <velocidade>
          ... volume, som 3D no carro ...
```

O jogador **não tem nenhum caso especial**: o carro dele aparece no pool como
qualquer outro, tem pedal lido do mesmo jeito e é filtrado pelo mesmo valor. É
por isso que "funciona igual ao player" por construção, e não por duplicação de
código.

## 3. O filtro de valor: da lista do `.ini` para a handling

O mod original filtrava pelo **valor monetário do carro** (só carro velho/barato
chia). A v2.6 usa o opcode do CLEO+:

```
GET_CAR_VALUE <car> <valor>     // 0xEFC -> CHandlingData::m_nMonetaryValue
```

Esse é o mesmo valor que o jogo usa na missão de exportar veículos (é a coluna
`Monetary Value` do `data/handling.cfg`), então:

| | v2.5.1 | v2.6 |
|---|---|---|
| origem do valor | lista no `.ini` (chave por carro) | handling do jogo |
| carro added | não lista → sem som | valor da handling dele (0 se não tiver) |
| carro substituído | precisa editar o `.ini` | funciona sozinho |
| trocar o valor de um carro | editar o `.ini` do mod | editar o `handling.cfg` |

O `.ini` continua mandando no **corte** (`MinValue` / `MaxValue`), que é a parte
que o usuário realmente ajusta. Com `Debug = 1` o script imprime o valor de
cada carro que passa por ele, então descobrir o corte certo é uma volta no
cidade com o print ligado.

> **Nota sobre carro added:** muitos `handling.cfg` de carro added deixam o
> valor monetário em 0. Com o padrão `MinValue = 0`, esses carros **chiam**.
> Para um added específico não chiar, suba o `MinValue` ou arrume o valor na
> handling dele.

## 4. Som 3D e volume

Cada chiado é tocado **no carro que freou** (`SET_PLAY_3D_AUDIO_STREAM_AT_CAR`),
e não "na sua cara". É isso que faz o som vir da rua, de trás, do carro parked
que você empurrou, e o próprio attenuate do jogo cuida da distância.

O volume final é:

```
volume = volumeDeEfeitosDoMenu (0xB5FCCC, via GET_AUDIO_SFX_VOLUME)
       × Volume do .ini
       × pedal (0..1)
       × fatorDeVelocidade (0.25 em 13 km/h → 1.0 em RefSpeed)
       , com tudo preso entre 0.0 e 1.0
```

O `0xB5FCCC` é o endereço que a própria MixMods indicou em 2019 ("agora o volume
do som segue a configuração do menu") — na v2.5.1 isso valia para o jogador, e
aqui vale para **todos** os carros. O `GET_AUDIO_SFX_VOLUME` do CLEO+ lê
exatamente esse endereço.

## 5. O modelo de pressão (e por que é independente de FPS)

Detalhar só o que muda em relação ao script original (que era "cai mais que X de
velocidade → toca"):

- **Pressão acumulada por carro.** Cada carro registrado guarda uma pressão
  `0..1` que **sobe** enquanto o pedal está apertado (proporcional ao pedal) e
  **desce** quando ele solta. Ela é a mesma ideia do `fBrakePressure` do Air
  Brake v3, mas guardada em **tempo** (`GET_GAME_TIMER`), não em frames: a
  rampa é `PressureRate` por segundo, com um teto de 200 ms por passo para
  segurar o caso de hitch. Sem isso, o mesmo `.ini` cantava diferente a 30 e a
  240 FPS.
- **Gatilho.** O som só sai quando a pressão passa de `TriggerPressure`. É o que
  separa "freou" de "encostou no freio".
- **Portão de velocidade.** Abaixo de 12% de `RefSpeed` (13 km/h no padrão) a
  pressão zera: pastilha gasta não canta com o carro parado.
- **`Cooldown = 0` (modo v2.5.1).** Depois de tocar, o carro fica
  **travado** (o estado dele passa a ser 3) e só volta a cantar quando a
  pressão cai de verdade abaixo do gatilho — isto é, quando o freio solta.
  A trava é o que garante um som por aperto: travar a pressão *no* gatilho
  não bastaria, porque no frame seguinte a subida de um frame já a repassa
  (e o som saía a cada frame). O destravamento ainda tem um **piso de 250 ms**
  desde o último som, para o pedal da IA ficar pisando não virar
  metralhadeira.
- **`Cooldown > 0` (padrão).** A pressão continua subindo e o som se repete a
  cada `Cooldown` ms enquanto o freio estiver apertado — a pastilha canta
  durante a frenagem inteira.

### Como isso foi conferido sem o jogo

O `UpdateCar` foi transcrito linha por linha em
[`tools/test_model.py`](tools/test_model.py), que roda 60 casos do modelo de
tempo (contagem de sons e volume) contra o comportamento documentado:

```bash
python3 tools/test_model.py
```

Foi esse simulador que pegou os dois bugs reais do modelo antes de qualquer
teste in-game: a pressão gravada sem escala ×1000 (que zerava o estado a cada
frame) e o "um som por aperto" disparando a cada frame. O piso de 250 ms também
nasceu dele, do caso "IA pisando 5 vezes em 4 s", que rendia dezenas de sons
por segundo.

### O terceiro bug: o caminho do som era curto demais (achado por quem instalou)

A primeira build publicada avisava "arquivo de som não encontrado" mesmo com o
arquivo no lugar. A causa era o limite do `LVAR_TEXT_LABEL16`: **text label
guarda 7 (`TEXT_LABEL`) ou 15 (`TEXT_LABEL16`) caracteres** — o último byte é o
terminador nulo — e o caminho `CLEO\BrakePadSound\brakepad.wav` tem 31. O
caminho era truncado, o `DOES_FILE_EXIST` respondia não, e o script parava com
aquela mensagem.

A correção é o caminho idiomático do gta3script para string comprida: um
**buffer de 128 bytes na área de dados** (`txtSoundBuffer`, alcançado por
`GET_LABEL_POINTER` num `LVAR_INT`), que cabe 127 caracteres, custa **1 slot
só** e ainda libera os 4 slots do text label. O IR2 do compilador confirma o
que cada forma significa (`GET_LABEL_POINTER %MAIN 3@`,
`READ_STRING_FROM_INI_FILE "CLEO\BrakePadSound.ini" "Config" "SoundFile" 3@`,
`DOES_FILE_EXIST 3@`). A mensagem de erro agora mostra o caminho que ele
tentou, e os readmes ganharam uma seção de problemas com a regra do ModLoader
(ele copia a pasta `cleo` do mod para a `CLEO` do jogo, e só enxerga `.ini`
dentro de uma pasta `CLEO`).

## 6. Desempenho: como o "todo mundo" cabe no frame

O laço visita até ~200 slots do pool de veículos por frame. Se cada visita
custasse leitura de pedal + posição + volume, isso seria caro. O truque é o
**estado por carro em variáveis estendidas do CLEO+** (`INIT/GET/SET_EXTENDED_CAR_VAR`),
que ficam **no próprio veículo** (o CLEO+ cria e libera junto com o carro):

| var | significado |
|---|---|
| 1 | estado: 0 = ainda não avaliado · 1 = registrado e armado · 2 = descartado · 3 = registrado e travado (já cantou neste aperto, modo `Cooldown = 0`) |
| 2 | pressão acumulada (×1000) |
| 3 | instante (ms) do último chiado (dele saem o cooldown e o piso de 250 ms) |

Assim, para todo carro do pool o único opcode é o `GET_EXTENDED_CAR_VAR` da
var 1, e um `IF iReg = 0` / `ELSE IF NOT iReg = 2` decide o resto. Carro caro,
avião, barco e moto fora do padrão saem **ali mesmo**, sem nenhuma outra leitura.

O registro das 3 variáveis acontece **uma vez por carro**, no evento de criação
do CLEO+ (`SET_SCRIPT_EVENT_CAR_CREATE`), e a primeira varredura do pool no
início do script cobre os carros que já existiam. A avaliação (tipo + valor)
roda uma vez por carro também — a primeira vez que o laço o vê.

Detalhes que só apareceram aqui:

- **O evento roda no meio do laço.** O handler do CLEO+ executa no **mesmo
  thread**, só redirecionando o instruction pointer (e ele recebe a variável do
  carro por parâmetro). Por isso o handler só mexe em `hNewCar`: se ele
  mexesse em `hVeh`/`iSearch`/`iReg`, o laço continuaria com o carro errado na
  hora de voltar.
- **Ext var só guarda inteiro.** `SET_EXTENDED_CAR_VAR` grava 32 bits; a pressão
  (float) é guardada ×1000 e convertida com `CSET_LVAR_*`. Passar o float
  direto (como o Air Brake v3 faz com `fBrakePressure`) grava os bits do float
  truncados para int e lê de volta um número sem sentido.
- **Teto de 4 sons por frame.** Numa batida de 15 carros, no máximo 4 chiados
  começam no mesmo frame — evita despejar 15 streams no mixer de uma vez.
- **Cooldown por carro, não global.** Cada carro tem o seu próprio instante do
  último chiado (a var 3), então dois carros nunca se atrapalham. A comparação
  usa o `GET_GAME_TIMER` de 32 bits, que só dá a volta depois de 49,7 dias
  de jogo contínuo na mesma sessão (e nesse caso o carro calaria até o contador
  passar o valor guardado) — não é caso de se preocupar.

## 7. O `.ini`

Uma chave a mais, uma a menos, e o resto mais extenso que a v2.5.1:

- o `.ini` é lido **uma vez** no início (a v2.5.1 lia a lista durante o jogo);
- se faltar uma chave, o script escreve o padrão e segue (ele se autocria);
- valores fora de faixa são corrigidos no start-up (divisão por zero em
  `RefSpeed`, raio negativo, volume > 1...);
- o caminho do som é configurável (`SoundFile`), e se o arquivo não existir o
  script avisa na tela e **não roda** (melhor do que ficar gastando cpu calado).

## 8. O som deste pacote

O `.wav` aqui é **sintetizado** por [`tools/make_sound.py`](tools/make_sound.py)
(ressonância em ~3 kHz com harmônicos inarmônicos, vibrato de tom, tremolo de
~33 Hz, ruído de atrito e três "chirps"), porque o som do mod original é dos
autores dele e não é nosso para redistribuir. Para usar o som da v2.5.1, copie o
arquivo para `CLEO\BrakePadSound\brakepad.wav` ou aponte `SoundFile` para ele.

## 9. Limites conhecidos (leia antes de reclamar)

- **Exige CLEO+ 1.0.7+.** A v2.5.1 roda no CLEO 4 puro; esta não (o
  `GET_CAR_VALUE`, o `GET_CAR_PEDALS` e as variáveis estendidas são do CLEO+).
  Em troca, é exatamente o que permite ler o pedal e o valor de qualquer carro.
- **Não roda no MoonLoader.** É um script CLEO (`.cs`), não Lua.
- **Ônibus/caminhão tem pedal de freio normal**, então chia como qualquer
  carro — inclusive quando o "freio a ar" do truck mod também estiver instalado.
  Dá para separar pelo `MaxValue`/handling, não por tipo.
- **`Debug = 1` enche a tela**: ele imprime na primeira avaliação de cada carro.
  É de propósito (é o jeito de achar o valor dos carros), mas use só na hora de
  ajustar o corte.
- **Status de teste:** o source compila limpo com o toolchain do repositório
  (gta3sc + `cleo.xml` do CLEO+ 1.0.7, 3.944 bytes, SHA-256
  `9da5c164...`) e a lógica foi conferida opcode a opcode contra o código-fonte
  do CLEO+ (semântica de `GET_CAR_PEDALS`, `GET_CAR_VALUE`, das variáveis
  estendidas, do evento de criação e dos streams de áudio). A v2.6 **rodou
  dentro do jogo** e foi exatamente o `SCRLog` do jogador que revelou o quarto
  bug (seção 10). A v2.7 só precisa do teste de rolagem com o carro em
  movimento — que é o que o bug anterior impedia de fazer.

## 10. v2.6 → v2.7: o quarto bug, e por que ele custou caro

O quarto bug é o mais instrutivo do mod, porque **não dava erro nenhum**:
compilava limpo, sem warning, e o jogo rodava o script inteiro sem reclamar.
O sintoma era um só — *nenhum som, nem no carro do jogador, nem no dos NPCs* —
e nenhuma verificação de mesa pegava ele, porque verificação de mesa não é
opcode.

### O que acontecia

O script converte o `dt` (inteiro, milissegundos) para float toda vez que o
frame vira. Isso é feito com `CSET`, e os opcodes `CSET` do gta3sc **têm os
nomes trocados em relação ao que fazem**:

| opcode | nome no `cleo.xml` do gta3sc | o que ele faz de verdade |
|---|---|---|
| 0092 | `CSET_LVAR_INT_TO_LVAR_FLOAT` | **float → int** (o Sanny chama de `22@ = float 17@ to_integer`) |
| 0093 | `CSET_LVAR_FLOAT_TO_LVAR_INT` | **int → float** |

Nos dois o **destino vem primeiro**. A v2.6 escreveu as três conversões no
sentido inverso do que o opcode faz, então `fStep` (o quanto de pressão o frame
soma) valia **0.0 sempre**. Com `fStep = 0`, a pressão nunca subia, nunca
passava do `TriggerPressure = 0.08`, e o `PlaySqueal` — que fica *depois* desse
teste — nunca era chamado. Um único erro de operandos matava o mod inteiro, e
funcionava igual para o jogador e para os NPCs (o caminho é o mesmo, o que até
fazia o bug parecer "problema de NPC").

O `SCRLog` do jogo mostrou a prova:

```
iValue  = 46                       // dt em ms, correto
[0092] l11(46) =# l22(0.0)         // a conversao
l22(0.0) *= 0.001                   // fStep continua 0.0
l22(0.0) *= l23(5.0)
```

### O que a v2.7 faz

- as três conversões na ordem certa (`CSET_LVAR_FLOAT_TO_LVAR_INT fStep
  iValue`, `CSET_LVAR_FLOAT_TO_LVAR_INT fPress iPress`, `CSET_LVAR_INT_TO_LVAR_FLOAT
  iPress f`);
- **`MinSpeed`** no `.ini` (10 km/h por padrão): a pastilha não canta com o
  carro parado, e `MinSpeed = 0` libera isso para teste;
- o pedal entra no volume com **piso de 50%** (`0.5 + 0.5 * pedal`): antes, um
  toque leve de freio (0.2) dava 20% do volume e o chiado sumia no barulho da
  rua;
- `Cooldown` padrão 1500 ms (o `.wav` dura 1,2 s, então os chiados não se
  sobrepõem);
- `Vehicles` e `Debug` passam a ser lidos **só na inicialização** (antes eram
  lidos do `.ini` a cada carro novo avaliado, o que contrariava a promessa do
  cabeçalho);
- a mensagem de inicialização passou a mostrar o **caminho do arquivo de som que
  o script está usando de fato** — é a primeira coisa a olhar quando alguém
  relata "não ouvi som";
- e, no `tools/test_model.py`, uma seção nova que **lê o `.sc` compilado de
  verdade** e trava a ordem dos operandos do `CSET`, o `$` do ponteiro do
  buffer, a contagem de slots (32 de 32) e a promessa de "o `.ini` é lido só no
  começo". Reintroduzir o bug da v2.6 faz o teste falhar em duas verificações —
  foi testado justamente assim.


## 11. v2.7 → v2.8: voltar a ser natural (desaceleração, não pressão)

O teste de rolagem do jogador na v2.7 levantou dois problemas de *natureza*,
não de crash:

1. **O som cantava com o carro parado.** Além de ser irreal (pastilha gasta
   não canta com o carro parado), era resultado direto do modelo da v2.7: uma
   "pressão" que ia acumulando enquanto o pé ficasse no freio, e que só o corte
   de velocidade zerava - corte que a própria v2.7 desativava quando o
   `MinSpeed` era baixo. Um carro parado, com o pé no freio, acumulava
   pressão até disparar a cantiga.

2. **Não soava "como o original".** O mod v2.5.1 detectava **desaceleração**
   (comparava a velocidade do frame com a do frame anterior). A v2.7 trocou
   isso por um modelo de pressão/tempo que, embora mais estável em FPS, mudava
   o *caráter* do som: ele "decidia" chiar por um contador interno, não pela
   física da frenagem.

A v2.8 volta ao gatilho do mod original: **a queda de velocidade**, medida em
**m/s²** (metros por segundo ao quadrado, o popular "g"). O script compara a
velocidade de cada carro com a do frame anterior e só canta quando a perda
passa de `BrakeForce` (2.5 por padrão). É a mesma lógica do v2.5.1, com dois
detalhes a mais:

- A desaceleração é **por segundo** (via `GET_GAME_TIMER`), não por frame, então
  o mesmo freio é reconhecido igual a 30, 60, 144 ou 240 FPS.
- Cada carro guarda a **própria velocidade do frame anterior** na sua variável
  estendida 2, então o script não mantém nenhuma tabela paralela.

Consequências diretas, todas desejadas:

- **Carro parado nunca canta** (um carro parado não desacelera) - a cantiga só
  aparece quando o carro está mesmo freando. O `BrakeThreshold` (pedal mínimo)
  continua como trava contra falso positivo, mas o peso da decisão agora é da
  física, não de um contador.
- **Freio leve quase não canta**; batida forte canta alto e longo - a
  intensidade da frenagem é proporcional à intensidade da cantiga, como na
  vida real.
- Acelera e depois freia: a primeira frenagem é a mais forte e é a que canta.

O que saiu do `.ini`: `TriggerPressure`, `PressureRate` e `MinSpeed` (chaves
do modelo de pressão). O que entrou: `BrakeForce` (queda mínima, em m/s²). O
`Cooldown` e o `Volume` continuam como antes, e o volume ainda escala com a
velocidade (com a conversão m/s → km/h de 3.6, como o mod original), o pedal
(com piso de 50%) e o volume de efeitos do menu.

O `tools/test_model.py` foi reescrito em torno do novo núcleo e agora tem uma
seção dedicada a provar o que o jogador pediu: **carro parado não canta, mesmo
com o pé no freio, mesmo com `BrakeThreshold = 0`** (seção 1), mais a
independência de FPS medida no volume do primeiro som (seção 8).

---

## 12. v2.8 → v2.9: o som tem fim

**O problema relatado:** *"ao frear até o carro parar e soltar o freio, o som
continua por alguns segundos"*.

### O que acontecia

A v2.8 (e a v2.5.1) tratavam o som como um **evento**: quando o gatilho
disparava, o script carregava o stream, ligava no carro e seguia em frente. O
handle do áudio nunca era guardado, então ninguém mais mexia naquele som. O
arquivo tocava até o fim — e o do mod original dura ~6,165 s.

Por que ninguém percebia antes: o `brakepad.wav` deste pacote dura 1,2 s, e
1,2 s de chiado parece "o freio". Com o arquivo de 6 s (ou com uma frenagem de
montanha), o chiado continuava com o carro já parado e o pé fora do pedal.

### O que a v2.9 faz

O som deixou de ser evento e passou a ser **estado do carro**. Cada carro guarda
o handle do seu stream, e uma máquina de estados o mantém vivo enquanto a
frenagem dura:

- **nasce** quando a frenagem começa (desaceleração real + pé no freio);
- **acompanha** — o volume é reavaliado todo frame a partir da frenagem atual;
- **morre** quando a frenagem acaba: o volume desce linearmente até zero durante
  `Fade` segundos, e só então o stream é *desligado e removido* de verdade
  (`SET_AUDIO_STREAM_STATE 0` + `REMOVE_AUDIO_STREAM`), não apenas abaixado.

"Acabou a frenagem" cobre todos os casos que deixam o carro mudo: pé fora do
freio, o carro deixou de desacelerar, o carro parou, o carro saiu do raio da
câmera, o motor foi desligado. Nenhum desses deixa som órfão no mundo.

### O que cada carro guarda

A v2.8 já reservava 4 variáveis estendidas por carro; a v2.9 usa as quatro.
Isso não consumiu nenhum slot novo do script: o total de **variáveis locais**
continua em 31 das 32 disponíveis (é esse número que o `tools/test_model.py`
verifica).

| var. | conteúdo |
| --- | --- |
| 1 | estado: `0` = ainda não avaliado · `1` = armado, pode chiar · `2` = descartado (avião/barco, tipo fora do `Vehicles`) · `3` = com som tocando |
| 2 | velocidade do frame anterior (m/s × 1000) — é dela que sai a desaceleração |
| 3 | handle do stream de áudio deste carro (`0` = nenhum som) |
| 4 | volume atual do som (× 1000) — é esta que o *fade* move |

Não existe um estado "desvanecendo": quando a frenagem acaba, o **alvo** de
volume (`BrakeVolume`) simplesmente vira 0.0, e o `AdjustSound` caminha o volume
atual até esse alvo no passo do `Fade`. Ao chegar em zero — e só aí — o stream é
desligado e removido, e o carro volta ao estado `1` (armado), pronto para
cantar de novo na próxima frenagem.

O passo do *fade* é `(volume por segundo) × dt`, com `dt` em segundos — por isso
o desvanecimento dura o mesmo em 30 ou 240 FPS (e o `.sc` calcula `1/Fade` uma
vez só, na leitura do `.ini`).

Uma sutileza que custou um bug: o `AdjustSound` **não** roda no mesmo frame em
que o stream é criado. Um stream recém-ligado pode responder "parado" ao
`GET_AUDIO_STREAM_STATE` naquele frame, e o script mataria o som que acabou de
nascer. Por isso o volume só começa a subir no frame seguinte (~16 ms de
diferença, inaudível).

### Arquivo de qualquer duração

O script não conhece a duração do som: ele não "lê o tamanho do arquivo". O
que existe é **detecção de término natural** — a cada frame, o
`GET_AUDIO_STREAM_STATE` diz se o stream ainda está tocando; se já acabou
(retorna 0) e a frenagem ainda valia, o stream é removido, o carro rearma
(estado `1`) e pode cantar de novo. Foi isso que permitiu validar o mesmo código com o
placeholder de 1,2 s e com o `brakepad.mp3` de 6,165 s:

- com 1,2 s e frenagem longa → toca, acaba, rearma, toca de novo;
- com 6,165 s e frenagem curta → toca, é cortado no `Fade`, nunca sobrepõe.

Como nunca existe mais de um stream por carro, **é impossível empilhar duas
cópias do mesmo chiado** — a sobreposição que o `Cooldown` existia para evitar
sumiu junto com a chave.

### O `.ini`

Saiu o `Cooldown`. Entrou o `Fade` (segundos, não milissegundos), que faz o
outro trabalho: *quando o som some*. O mesmo tempo vale para a entrada — o
chiado sobe devagar em vez de estalar no primeiro frame.

### O que o modelo prova

O `tools/test_model.py` tem agora **117 verificações** (eram 81), incluindo a
que reproduce exatamente o relato do usuário:

- frear até parar, soltar o freio → o som desaparece (`Fade`, ~0,2 s);
- término natural com áudio de 6,165 s → rearma e reinicia, sem sobreposição;
- 10 carros no pool → teto de 6 sons, sem estouro;
- FPS 25/30/50/60/144/240 → mesmo comportamento, inclusive a curva do fade;
- o volume do menu é respeitado, e `Volume = 1.0` chega a 100% (a v2.8 tinha um
  teto extra de 0,7 que segurava o valor).

### Curva de volume

O volume instantâneo é `fDecel / (2 × BrakeForce)`, limitado a 1.0. Ou seja:
**frear com o dobro da força mínima = volume máximo** — no próprio limite do
gatilho o som já aparece a ~50%. Como o `BrakeForce` é o mesmo número que
dispara o som, mexer nele muda o gatilho e a curva de volume juntas (faz
sentido: frenagem mais forte exigida = chiado mais alto).

Esse valor ainda é multiplicado por dois fatores, como o mod original:

- a **velocidade** (`velocidade / RefSpeed`, com piso de 25% e teto de 1.0 — o
  `× 3.6` é a conversão de m/s para km/h);
- o **pedal** (`pedal × 0.5 + 0.5`, ou seja, piso de 50%).

O resultado é limitado a 1.0. A v2.8 ainda tinha um teto extra de 0.7 acima
disso, que impedia o `Volume = 1.0` do `.ini` de chegar ao máximo; foi removido.
