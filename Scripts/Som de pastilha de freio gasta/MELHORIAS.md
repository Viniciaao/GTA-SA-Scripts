# v2.5.1 → v2.6: o que mudou, e por quê

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
  (gta3sc + `cleo.xml` do CLEO+ 1.0.7, 3.360 bytes, SHA-256
  `204fb0b3...`) e a lógica foi conferida opcode a opcode contra o código-fonte
  do CLEO+ (semântica de `GET_CAR_PEDALS`, `GET_CAR_VALUE`, das variáveis
  estendidas, do evento de criação e dos streams de áudio), mas **ainda não foi
  rodado dentro do jogo** — quem instalar, comece com `Debug = 1` e `Radius`
  alto, e ajuste daí.
