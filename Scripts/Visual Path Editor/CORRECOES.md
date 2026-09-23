# Visual Path Editor — correções no salvamento (v1.0 → v1.1)

Este documento lista o que estava errado na gravação dos `nodes*.dat` da versão
1.0 do mod, como cada problema foi comprovado e o que mudou.
A referência do formato está em [`PATHS-FORMAT.md`](PATHS-FORMAT.md).

> Resumo: **sim, o mod salvava errado.** O arquivo saía incompleto (faltando a
> seção de flags de interseção e 192 bytes no fim das seções 6 e 7), todos os
> nodes desciam 0,25 m a cada salvamento, links inválidos eram gravados sem
> nenhum aviso e o arquivo de destino era truncado antes de qualquer checagem.

---

## 1. O arquivo salvo ficava incompleto

**O que a versão 1.0 gravava**: cabeçalho + nodes + navis + links + os 768 bytes
de slots extras + navi links + comprimentos (`numLinks` bytes). Fim.

**O que o jogo lê** (carregador `CPathFind::LoadPathFindData`, 0x4529F0):

```
links            4 * (numLinks + 192)
navi links       2 * numLinks
comprimentos        (numLinks + 192)
interseções         (numLinks + 192)
```

Ou seja, faltavam **`numLinks + 384` bytes** no fim do arquivo:
* 192 bytes de comprimentos (slots dinâmicos) e
* a **seção 7 inteira** (as flags de interseção, 1 byte por link + 192).

Como o arquivo era aberto em modo `"wb"` (truncando), o resultado no disco era
um arquivo curto de verdade — o jogo lia lixo depois do fim da seção 6 (a busca
por interseções e os semáforos de pedestre ficavam com memória não inicializada)
e os 192 slots dinâmicos ficavam com valores errados.

**Correção**: `writeAreaData` grava o arquivo completo, na ordem do jogo, com
os 192 slots extras das seções 3, 6 e 7. Os bytes originais dessas seções são
**preservados** (copiados do arquivo carregado) e só o que muda é recalculado:

* flags de interseção: preservadas por índice (ao inserir/remover links os
  índices mudam, então o que não tem origem vira `0`);
* comprimentos: o valor original é mantido, **mas se a distância real entre os
  dois nodes divergir mais de 1 unidade o valor é corrigido** (isso conserta os
  comprimentos que ficavam velhos quando você movia um node);
* slots extras (seção 3): copiados como estavam; se o arquivo de origem for um
  dos arquivos antigos (sem esses bytes), é gravado o padrão `FF FF 00 00`
  (endereço inválido) usado pelo próprio jogo.

Verificação: `tools/nodes_tool.py validar` mostra `arquivo INCOMPLETO` em
qualquer arquivo salvo pela versão 1.0.

---

## 2. Todos os nodes desciam 0,25 m a cada save

No `writeFile` antigo, depois de converter o Z do objeto para o formato do
arquivo, tinha um `iz -= 2` (2 unidades = 0,25 m). A leitura não compensava
isso, então **cada ciclo carregar → salvar afundava todos os nodes** — 0,25 m
por save, 5 m depois de 20 salvamentos. Era isso que fazia os carros andarem
"enterrados" depois de editar a mesma área algumas vezes.

Além disso a conversão truncava (`value =# var`) em vez de arredondar, e tinha
dois erros de sinal no caminho inverso:
* `IF value > 32766` (deveria ser `> 32767`) — só afeta o valor exato 32767;
* `IF ix > 128` (deveria ser `>= 128`) para os bytes de direção das navis.

**Correção**: o `-2` foi removido, `convertToSignedCoord` agora **arredonda pro
múltiplo de 1/8 mais próximo** e os limites de sinal foram corrigidos.

Verificação: `tools/nodes_tool.py comparar original.dat salvo.dat` — com arquivos
da versão 1.0 ele imprime:
`>>> TODOS os nodes foram deslocados no mesmo valor de Z: isso e o bug do 'iz -= 2'`.

---

## 3. Nenhuma checagem antes de gravar (links inválidos iam pro disco)

A seção 3 tinha esta checagem:

```c
iy = 0
GET_LIST_VALUE_BY_INDEX list number (iy)   // node do link
IF number = -1                             // <-- testa o CONTADOR do loop, não o node!
    PRINT_FORMATTED_NOW "~r~Ha nodes com links invalidos!" 3000
    GOTO main
ENDIF
```

`number` é o índice do link (0, 1, 2…), então a condição **nunca** era
verdadeira. Consequência prática: o botão de inserir link (`INSERT`) cria um
link "vazio" (`area 0`, `node -1`) e, se você salvasse sem apontá-lo pra um node,
o arquivo ia com `node = 0xFFFF` na seção 3. O jogo faz
`m_pNodeLinks[area][nodeId]` sem checar o índice — leitura fora da memória.

**Correção**: antes de tocar no arquivo de destino, `checkAreaFile` confere
* tudo: contagens do cabeçalho, limite de 15 links por node, coordenadas,
  cada link (área e node existindo **dentro do arquivo** e, quando a área
  vizinha também está carregada, dentro dela), referências de navi e navis sem
  node conectado. Se achar problema, **o arquivo não é aberto**: aparece uma
  mensagem dizendo exatamente qual node/link está errado e o mod continua com
  as áreas carregadas pra você corrigir e salvar de novo.

---

## 4. Arquivo de destino truncado antes da validação (risco de perder o anterior)

O `writeFile` antigo fazia `OPEN_FILE ... 0x6277` (modo `"wb"`, que **trunca o
arquivo na hora**) e só depois ia checando as coisas. Os caminhos de erro
faziam `GOTO main`, ou seja: deixavam o arquivo aberto, meio escrito, truncado e
sem `CLOSE_FILE`.

**Correção**:
1. valida tudo **antes** de abrir (`checkAreaFile`);
2. faz backup do arquivo compilado anterior em `nodesN.dat.bak`;
3. grava;
4. confere o tamanho final e fecha o arquivo em todos os caminhos.

Se algum arquivo falhar, o mod **não descarrega as áreas** e avisa
("X arquivo(s) nao foram salvos") — o trabalho da sessão não é perdido.

---

## 5. Path width e flags trocados ao criar node novo

Ao criar um node, o mod tentava reaproveitar as definições do último node
selecionado e fazia:

```c
READ_STRUCT_OFFSET listptr 36 4 (count)   // Flags
IF NOT count = 0
    SET_EXTENDED_OBJECT_VAR object VPEV 6 count   // Flags (ok)
    WRITE_STRUCT_OFFSET listptr 32 4 (count)      // <-- gravou as FLAGS no slot do PathWidth
    IF count < 1024
        SET_EXTENDED_OBJECT_VAR object VPEV 4 count  // <-- path width virou o valor das flags
    ENDIF
ENDIF
```

**Correção**: cada valor é lido do seu próprio slot (32 = PathWidth,
36 = Flags) e o path width só é aplicado se estiver na faixa 0–255.

---

## 6. Navi criada "conectada" ao node 0

`createNewNode` criava a navi com `VPEV 2 = 0`, ou seja, uma navi nova já nascia
apontando pro **node 0** do arquivo (um node qualquer, longe dali) até o usuário
conectá-la — e se ele não conectasse, ia assim mesmo pro arquivo: direção,
faixa e posição calculadas a partir de um node errado.

**Correção**: a navi nasce com `NodeID = -1` (sem conexão) e o save **recusa**
arquivos com navi sem node conectado, dizendo qual é — mesma política que o mod
já usava pra quando o node alvo era apagado.

Pra conectar: selecione a navi (ande até ela e aperte `O`), fique a menos de 2 m
do node desejado e aperte `P` (no menu de manipulação, aba das navis) — o mod
mostra um `N` em cima do node que vai ser conectado. O painel da navi mostra o
node conectado na primeira linha (`área-node`, ou `---` quando não há nenhum).

---

## 7. Seção 5 apontava pra área errada

O "navi link" de um link (2 bytes: 10 bits da navi + 6 bits da área) era
montado com a **área do node conectado** à navi, e não com a **área onde a navi
mora** (que é o que o jogo usa pra procurar a navi em `m_pNaviNodes[area]`).
Quando os dois coincidem (caso comum) não dava diferença; quando não coincidem,
o jogo lê a navi errada — ou fora da área.

**Correção**: cada navi agora guarda a própria área (`VPEV 8`, definida na
criação/carregamento) e é ela que vai pro pacote da seção 5.

Aproveitando: quando um link **não tem navi**, o valor gravado agora é
`0xFFFF` (`CCarPathLinkAddress` inválido, o mesmo que o próprio jogo usa) em vez
de `0`, que significa "navi 0 da área 0" — um endereço que existe de verdade e
que o jogo consultaria. Links de nodes de pedestre continuam com `0`, como nos
arquivos originais (pra eles a seção 5 não é usada).

---

## 8. Pequenos erros de UI que corrompiam flags

| Problema | Efeito | Correção |
|----------|--------|----------|
| "Left/Right lanes" iam até 15 | campo tem 3 bits: valor 8 invade as flags vizinhas (semáforo, trem) | limitado a 7 |
| Ligar "Highway" não limpava o bit 12 ("não rodovia") | `11` = combinação que não existe no arquivo original | ligar/desligar agora mexe nos dois bits |
| Ciclo do "Traffic Level" travava em `00` | não dava pra voltar de FULL | ciclo de 2 bits: FULL → HIGH → MEDIUM → LOW |
| Path width sem limite | valor > 255 era cortado em silêncio na gravação | limitado a 255 (e o save também limita) |
| Modelo da navi fixo em 1318 | mudar `navinode` no .ini não fazia nada (nem a detecção de "isto é uma navi") | tudo lê a seção `[MODELS]` do ini |
| "Path Width" da navi era desenhado mas não editável | — | agora é editável (L/J com a linha selecionada) |
| Lista de links mostrava "0-0" para link sem navi | confuso | mostra `---` |

---

## 9. Carregamento mais seguro

* O arquivo é carregado com verificação de tamanho mínimo e de sanidade do
  cabeçalho (contagens absurdas são recusadas em vez de alocar gigabytes).
* Se o arquivo for **menor** que o formato (por exemplo, salvo pela versão 1.0),
  ele é completado com zeros em memória e o mod avisa
  (`nodesN.dat incompleto (X de Y bytes), sera completado ao salvar`) — ao
  salvar de novo, o arquivo fica correto. **Arquivos salvos pela 1.0 são
  recuperáveis** por esse caminho (as flags de interseção perdidas viram `0`).
* A cópia do arquivo pra memória não escreve mais 4 bytes quando faltam 1–3
  (antes isso podia passar do fim do bloco alocado).
* Os 8 bytes de "endereço de memória" de cada node são preservados quando o node
  já existia no arquivo (nodes novos gravam zero, que é o que o jogo espera —
  esse campo é ignorado pelo jogo).

---

## Como compilar e instalar

1. Abra `VisualPathEditor.sc` no **Sanny Builder 4** (com os comandos do
   **CLEO+** instalados — o mod usa `0E1A/0E1B/0E1C`, listas e `VPEV`) e
   compile para `VisualPathEditor.cs`.
2. `VisualPathEditor.cs` + `VisualPathEditor.ini` vão pra pasta `CLEO` do jogo.
3. `gta3img/txd/pathtxd.txd` → `models/txd/`.
4. Extraia `nodes0.dat` … `nodes63.dat` do `gta3.img` e coloque **duas cópias**:
   `CLEO/gta3img/nodesN.dat` (de onde o mod lê) e
   `CLEO/gta3img/compiled/nodesN.dat` (onde o mod grava).
5. Requisitos: CLEO 4.4+, CLEO+ 1.0.7+, e os ajustes de limite
   (OpenLimitAdjuster com objetos ≥ 250.000 + LargeAddress).
6. Para jogar com o que você salvou: copie `CLEO/gta3img/compiled/nodesN.dat`
   pro `gta3.img` (ou use ModLoader, veja `PATHS-FORMAT.md` §7).

> O compilador não roda neste ambiente de correção: o `.sc` foi revisado linha
> por linha e passou por um verificador próprio (`tools/check_script.py`), que
> confere blocos, labels e declaração de variáveis — mas **compile antes de
> usar** e salve em uma área de teste primeiro, como sempre.

## Como conferir se o save ficou correto

```bash
# 1) o arquivo tem o layout completo?
python3 tools/nodes_tool.py validar "CLEO/gta3img/compiled/nodes8.dat"

# 2) o que mudou em relação ao original (drift de Z, links, comprimentos)?
python3 tools/nodes_tool.py comparar "CLEO/gta3img/nodes8.dat" "CLEO/gta3img/compiled/nodes8.dat"
```

O comando `validar` acusa: arquivo incompleto, contagens erradas, NodeID/AreaID
fora do padrão, links apontando pra nodes inexistentes, navis soltas,
coordenadas fora do limite, comprimentos desatualizados, nodes sem link e
heurística diferente de 0x7FFE.

## Limitações conhecidas (não mudou)

* Só é possível **criar e apagar** nodes/navis na região central carregada (a
  que o mod chama de `curfile = 8`); nas outras o salvamento preserva o que
  estava lá.
* O mod não faz undo nem versionamento além do `.bak`.
* Formato fastman92 (FLA paths) não é suportado — veja `PATHS-FORMAT.md` §7.
* O jogo original não relê um `nodesN.dat` enquanto a área está na memória:
  depois de salvar, recarregue/unload no mod e reimporte o arquivo no IMG.
