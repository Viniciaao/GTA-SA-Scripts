# Como funcionam os paths do GTA San Andreas

Referência do sistema de paths (rotas) do GTA SA, dos arquivos `nodes*.dat` e de
como o jogo lê/usa cada campo. Escrito para documentar as correções feitas no
**Visual Path Editor** (veja `CORRECOES.md`), mas serve de referência para
qualquer ferramenta de paths.

Fontes principais: o carregador do próprio jogo (`CPathFind::LoadPathFindData`,
sub_0x4529F0 — usado como referência via projeto *gta-reversed*), o editor
*SAPaths* do euryopa, a wiki do GTAMods (`Paths (GTA SA)`) e a documentação do
open.mp (`Path Nodes`). Onde havia divergência, vale o que o jogo faz.

---

## 1. A grade de 64 áreas

O mapa é dividido em **64 arquivos** `nodes0.dat` … `nodes63.dat`. Cada arquivo
cobre um quadrado de **750 × 750 unidades**, começando no canto **sudoeste**
(-3000, -3000) e seguindo em ordem de linha (row-major):

```
area = linha * 8 + coluna          (-3000,-3000) é a area 0
x = -3000 + coluna * 750           (coluna = area % 8)
y = -3000 + linha  * 750           (linha  = area / 8 = area >> 3)
```

Ou seja: `area + 1` é a área a **leste**, `area + 8` é a área ao **norte**.

O jogo carrega as áreas conforme o jogador (e missões) se movem: a cada quadro o
`CPathFind::UpdateStreaming` marca as áreas num raio de ~350 m
(`MarkRegionsForCoors`) e pede o modelo correspondente — por isso as 9 áreas em
volta do jogador ficam na memória e as outras não (`m_pPathNodes[area]` nulo).

Os arquivos ficam **dentro do `gta3.img`** no caminho `data\paths\nodesN.dat`.
Os `nodes*.dat` soltos em `data\paths\` (fora do IMG) são **ignorados** pelo jogo.

---

## 2. Estrutura do arquivo

Tudo little-endian, sem alinhamento extra. O arquivo tem 7 seções:

| Seção | Conteúdo | Tamanho |
|-------|----------|---------|
| Cabeçalho | 5 × `uint32` | 20 bytes |
| 1 | Nodes (veículos primeiro, depois pedestres) | `28 × numNodes` |
| 2 | Navi nodes (segmentos de faixa p/ carros) | `14 × numNavis` |
| 3 | Links (vizinhos de cada node) | `4 × (numLinks + 192)` |
| 5 | Navi links (qual navi cada link usa) | `2 × numLinks` |
| 6 | Comprimento dos links | `1 × (numLinks + 192)` |
| 7 | Flags de interseção | `1 × (numLinks + 192)` |

### Cabeçalho (20 bytes)

```
0x00 uint32  numNodes       * total (veículos + pedestres)
0x04 uint32  numVehicleNodes
0x08 uint32  numPedNodes    * precisa ser numNodes - numVehicleNodes
0x0C uint32  numNaviNodes   * máximo 1024 (por causa da seção 5)
0x10 uint32  numLinks
```

### Os "192 extras" (o famoso "filler" de 768 bytes)

O jogo reserva **192 links dinâmicos por área**:
`numToAdd = numLinks + 16 * 12`. E é assim que ele lê o arquivo:

```
sec 3: sizeof(CPathNodeLink) * (numLinks + 192)   // = 4 * (numLinks + 192)
sec 5: sizeof(CCarPathLinkAddress) * numLinks      // = 2 * numLinks
sec 6: sizeof(uint8) * (numLinks + 192)
sec 7: sizeof(CPathIntersectionInfo) * (numLinks + 192)
```

Logo: **a seção 3 termina com 192 slots livres** (768 bytes, normalmente
`FF FF 00 00`, ou seja área `0xFFFF` = endereço inválido), e **as seções 6 e 7
terminam com 192 bytes extras** cada uma. Não existe "cauda" separada: os
bytes que a wiki chama de "192 bytes desconhecidos depois da seção 7" são os 192
últimos bytes da própria seção 7.

Tamanho total esperado:

```
20 + 28*numNodes + 14*numNavis + 8*numLinks + 1172
       (o 1172 = 20 do cabeçalho + 768 + 192 + 192)
```

Se uma ferramenta grava o arquivo sem esses bytes, o jogo lê **além do fim do
arquivo**: os comprimentos dos 192 slots dinâmicos ficam com lixo e a seção 7
inteira (flags de interseção) fica com memória não inicializada.

---

## 3. Seção 1 — Nodes (28 bytes cada)

```
0x00 uint32  pNext      } endereços de memória do compilador da Rockstar.
0x04 uint32  pPrevious  } são ignorados pelo jogo; pode ser 0
0x08 int16   X          }
0x0A int16   Y          } posição no mundo = valor / 8.0
0x0C int16   Z          } (limite ±4095.875)
0x0E int16   heurística = 0x7FFE sempre
0x10 int16   LinkID     primeiro link deste node (índice na seção 3)
0x12 int16   AreaID     = número do arquivo
0x14 int16   NodeID     = índice do node dentro do arquivo (0, 1, 2, …)
0x16 uint8   PathWidth
0x17 uint8   FloodFill
0x18 uint32  Flags
```

* **heurística (0x7FFE)**: é o `m_totalDistFromOrigin` inicial da busca de
  caminho (`SHRT_MAX - 1`). O jogo usa esse campo como "distância acumulada" no
  algoritmo estilo Dijkstra e **restaura o valor** quando termina a busca — se
  você gravar outro valor, a busca começa corrompida.
* **LinkID**: os links de todos os nodes ficam **em sequência**, na mesma ordem
  dos nodes. O node 0 tem `LinkID = 0`, o node 1 tem `LinkID = 0 + links do
  node 0`, e assim por diante. `LinkID`+`numLinks` não pode passar do total.
* **NodeID**: o jogo endereça um node por `(AreaID, NodeID)` = `(area, índice)`,
  então esse campo **tem** que ser o índice. Áreas diferentes se ligam por
  links que apontam pra `(outra area, NodeID)`.
* **Nodes de veículo vêm sempre primeiro**: os primeiros `numVehicleNodes` são
  de carros/barco/trilha e os restantes são de pedestre. É esse corte que diz ao
  jogo onde termina uma lista e começa a outra.

### Flags do node (32 bits)

| Bits | Significado |
|------|-------------|
| 0–3 | **número de links** (é desse campo que o jogo tira `m_nNumLinks`) |
| 4–5 | nível de tráfego: `00` = FULL, `10` = HIGH, `01` = MEDIUM, `11` = LOW |
| 6 | road blocks (rua bloqueada) |
| 7 | node de água (**barcos**) |
| 8 | só veículos de emergência |
| 9 | (usado em tempo de execução: "já visitado") |
| 10 | não vagar aqui (usado por script) |
| 12 | **não é rodovia** |
| 13 | **é rodovia** |
| 16–19 | chance de spawn (0–15) |
| 20 | (usado em tempo de execução) |
| 21 | estacionamento |
| 22–23 | (livre) |
| 24–31 | (livre) |

Os bits 12 e 13 são **mutuamente exclusivos** nos arquivos originais: `10` =
rua normal, `01` = rodovia (nunca `00` nem `11` em nodes de veículo).

> Cuidado: o campo de número de links tem só 4 bits. Um node com 16 links faz o
> contador "voltar" a zero (e ainda invade o nível de tráfego) — o máximo real é
> **15 links por node**.

---

## 4. Seção 2 — Navi nodes (14 bytes cada)

Navi nodes são "pontos de faixa": ficam no meio do caminho entre dois nodes e
dizem ao jogo **como o tráfego anda** ali (direção, número de faixas, semáforo).
Nodes de pedestre **não** usam navi (a seção 5 fica zerada pra eles).

```
0x00 int16   X           } posição = valor / 8.0
0x02 int16   Y           } (Z não existe: navi é só X/Y)
0x04 uint16  AreaID      } node "conectado" (o node ao qual essa navi pertence)
0x06 uint16  NodeID      }
0x08 int8    dirX        } direção do fluxo, normalizada: -100..100
0x09 int8    dirY        }
0x0A uint8   PathWidth   } largura da via (1/16 de unidade por unidade do byte)
0x0B uint8   flags       } faixas/semáforo (bits 0-9, veja abaixo)
0x0C uint16  flags2      } resto das flags
```

Juntando os 4 bytes a partir do 0x0A num `uint32` (é assim que várias
ferramentas, incluindo este mod, trabalham), a máscara fica:

| Bits | Significado |
|------|-------------|
| 0–7 | largura da via (path width) |
| 8–10 | número de faixas no sentido oposto (as "da esquerda") |
| 11–13 | número de faixas no mesmo sentido (as "da direita") |
| 14 | direção do semáforo |
| 16–17 | estado do semáforo: `0` desligado, `1` norte-sul, `2` leste-oeste |
| 18 | luz de passagem de trem / ponte (`m_bridgeLights`) |

O jogo usa a navi pra posicionar o carro na faixa correta, pra descobrir sentido
único (`m_numOppositeDirLanes`) e pra parar no semáforo quando o estado da pista
combina com o estado da luz (`m_nTrafficLightState`).

`dirX`/`dirY` devem ser um vetor **unitário escalado por 100**
(ex.: indo pro leste = `(100, 0)`; pro sul = `(0, -100)` — no GTA o eixo Y
cresce pra cima/norte). Valores fora de ±100 confundem o cálculo de faixa.

---

## 5. Seções 3, 5, 6 e 7

### Seção 3 — Links (4 bytes cada)

```
0x00 uint16  AreaID do node vizinho
0x02 uint16  NodeID do node vizinho
```

Um link é uma aresta do grafo: o caminho é bidirecional na prática, então o
normal é que A tenha um link pra B **e** B tenha um link pra A.

### Seção 5 — Navi links (2 bytes cada)

Cada link aponta pra uma navi (o "segmento de faixa" daquele trecho):

```
bits 0–9   número da navi (índice na seção 2)
bits 10–15 área da navi  (o arquivo onde ela está)
```

`0xFFFF` (`area 63`, `navi 1023`) é o valor **inválido/sem navi** usado pelo
próprio jogo (`CCarPathLinkAddress::IsValid()` testa exatamente isso). Nos links
de nodes de pedestre o valor é `0` (unused).
Como só existem 10 bits pra navi, **cada área pode ter no máximo 1024 navis**.

### Seção 6 — Comprimento dos links (1 byte cada)

Distância entre os dois nodes **em unidades do mundo** (1 unidade ≈ 1 m),
arredondada e limitada a 255. É o peso que o algoritmo de busca usa pra escolher
a rota — se estiver errado, o tráfego pega rotas estranhas.
(Os 192 bytes extras dessa seção valem para os slots dinâmicos.)

### Seção 7 — Flags de interseção (1 byte cada)

```
bit 0: m_bRoadCross       (cruzamento com outra rua)
bit 1: m_bPedTrafficLight (semáforo de pedestre)
```

Um byte por link (mais os 192 dos slots dinâmicos). O jogo consulta isso em
`CPathFind::FindIntersection`, usado por pedestres/scripts. Se a seção estiver
faltando, o jogo lê lixo.

---

## 6. Resumo: o que uma ferramenta precisa gravar

1. Cabeçalho com as 5 contagens batendo com o que foi escrito de verdade
   (`numPed = numNodes - numVehicleNodes`).
2. Nodes de veículo primeiro, com `NodeID` = índice e `AreaID` = número do
   arquivo; `LinkID` contíguo na ordem dos nodes; `heurística = 0x7FFE`.
3. Navi nodes com `AreaID`/`NodeID` apontando pra um node **existente** e
   direções em -100..100.
4. Seção 3 com `numLinks` links **+ 192 slots** (`FF FF 00 00` por padrão).
5. Seção 5 com 2 bytes por link (`0xFFFF` = sem navi).
6. Seção 6 com `numLinks` comprimentos **+ 192 bytes**.
7. Seção 7 com `numLinks` bytes **+ 192 bytes**.
8. Nada de link apontando pra node que não existe (área carregada) e no máximo
   15 links por node.

## 7. Compatibilidade

* **fastman92 Limit Adjuster (paths)**: o FLA pode trocar o formato dos paths
  por um formato próprio (`VER2`/`VER3`, nodes de 40/42 bytes, coordenadas
  extendidas, cabeçalho diferente). Esse formato **não** é o daqui, e o FLA
  recusa/converte. Se você usa o FLA com "path format" ligado, desligue-o (ou
  converta depois) antes de editar os arquivos com este mod — e as coordenadas
  além de ±4095 unidades também exigem esse formato estendido.
* **ModLoader**: em vez de reimportar o `nodesN.dat` no `gta3.img`, você pode
  jogar o arquivo compilado em `modloader/<pasta>/gta3.img/nodesN.dat`; o
  ModLoader substitui a entrada do IMG sem precisar mexer no arquivo original.
* **Arquivos de áreas não carregadas**: um link pode apontar pra um node de
  outra área (isso existe no jogo original, nas bordas). Só que o alvo precisa
  existir naquela área — o editor só consegue conferir isso quando a área em
  questão também está carregada.

## 8. Ferramentas deste repositório

* `tools/nodes_tool.py` — lê, valida e compara `nodes*.dat`
  (`validar`, `info`, `comparar`, `criar-simples`).
* `tools/tests.py` — testes do validador e demonstração dos bugs da versão 1.0.
* `tools/check_script.py` — pré-checagem estática do `.sc` (blocos, labels,
  variáveis, limite de 32 locais e a regra de uma operação por expressão do
  gta3script) — roda sem instalar nada, mas **não substitui** o compilador.
* `tools/build.sh` + `BUILD.md` — compilam de verdade o `.sc` com o **gta3sc**
  (linguagem gta3script, não Sanny Builder) e geram o `.cs`.

Exemplo de uso depois de salvar com o mod:

```bash
python3 tools/nodes_tool.py validar "CLEO/gta3img/compiled/nodes8.dat"
python3 tools/nodes_tool.py comparar "CLEO/gta3img/nodes8.dat" "CLEO/gta3img/compiled/nodes8.dat"
```
