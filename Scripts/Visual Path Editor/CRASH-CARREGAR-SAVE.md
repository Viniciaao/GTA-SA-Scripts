# Por que o crash só aparece ao CARREGAR UM SAVE (e não em jogo novo)

Investigação feita no código-fonte do CLEO 4.4 e do CLEO+ 1.0.7 (o mod exige o
CLEO+). O sintoma relatado é: **jogo novo = tudo funciona; carregar save =
crash no heap dentro do `CLEO.asi`**. Isto é o que a engine faz de diferente
nesse caminho.

## 1. A ordem dos acontecimentos num restart de sessão

Em QUALQUER entrada de sessão (jogo novo ou save carregado) o CLEO faz:

```
CGame::Initialise                       <- CLEO+ hook: restartEvent.before
  -> ClearScriptLists()                 <- CLEO+ APAGA TODAS as listas dos scripts
  -> ScriptEvent::ClearAllScriptEvents()
  ...
  CTheScripts::Init  -> OnInitScm1/2/3  <- CLEO: RemoveAllCustomScripts() + InitScm()
                                            + LoadCustomScripts() re-le tudo de cleo/
```

Dois pontos importantes (ambos conferidos no fonte):

* O comentário do próprio CLEO+ no `restartEvent` diz: *"Scripts are executed
  once before restart, so we need to re-execute some events"* — ou seja, os
  scripts da sessão anterior **executam mais uma vez** no meio dessa transição,
  ANTES de o `InitScm()` destruí-los. Entre o `ClearScriptLists()` e a morte do
  thread existe então uma "janela" em que o mod ainda está vivo **com os
  ponteiros das listas já inválidos**.
* `InitScm()` + `LoadCustomScripts()` recriam os `.cs` do zero: as variáveis
  locais voltam a 0 e o mapa de DUMPs é recarregado do arquivo. Ou seja, na
  sessão NOVA o mod começa limpo; o problema é só a execução extra da janela.

## 2. O que o mod faz nessa janela

No jogo novo "de teste" o editor normalmente está fechado (`loaded = FALSE`), e
aí o loop principal não toca em nenhuma lista. Já quem acabou de editar paths e
salvou o jogo tem `loaded = TRUE`, `curfile`, `loadedregions` e os 9 buffers de
`nodes*.dat` na memória. Na janela do restart o loop executa mais uma vez e:

* `IF NOT temporary = region AND loaded = TRUE` → `updateCurfile` →
  `GET_LIST_VALUE_BY_INDEX loadedregions ...` — **lista já apagada pelo CLEO+**;
* ou a `functionz` pendente (ação da última tecla) → `CASE` que use as listas ou
  os buffers;
* ao sair, `deleteObjects`/`unloadFiles` → `DELETE_LIST`/`FREE_MEMORY` em
  ponteiro morto.

Isso explica por que o crash aparece **ao carregar um save** com o mod
instalado, e não em jogo novo.

## 3. Os bugs do mod que transformavam isso em crash

Os dois bugs corrigidos na v1.2 (ver `CORRECOES.md` §11) são exatamente os que
transformam "ponteiro morto" em "heap corrompido":

1. `deleteObjects` chamava `DELETE_LIST` três vezes no MESMO ponteiro (as três
   listas eram buscadas com o tipo 0) — o CLEO+ faz `delete` sem validar, então
   a segunda chamada já é um *invalid free*;
2. ao criar/apagar link faltava o `READ_MEMORY`, e o código lia/gravava 16 bytes
   depois do slot, torcendo o ponteiro do buffer de outro `nodesN.dat`; o
   `FREE_MEMORY` do unload liberava esse ponteiro torto;
3. `unloadFiles` liberava os 9 slots sem checar se estavam carregados (double
   free quando uma área não carregava).

## 4. O que ainda falta (se o crash persistir)

Fechar a janela do restart no lado do mod. O plano é o mod **re-armar o próprio
estado** e nunca usar ponteiro guardado sem uma checagem de "o jogo reiniciou?".
Duas maneiras de detectar o restart de dentro da execução extra:

* `0A9F current_thread_pointer` + uma marca no DUMP (o DUMP é recarregado do
  arquivo a cada restart, então marca ausente = sessão nova);
* o relógio do jogo / `TIMERA` voltando no tempo (indica restart).

Teste que separa as hipóteses (a fazer com o usuário):

1. carregar o mesmo save com o `VisualPathEditor.cs` FORA da pasta `CLEO`
   (se não crashar, é o mod; se crashar, é o save/outro mod);
2. carregar o save **sem ter aberto o editor** naquela sessão.

## 5. Referências de código (fontes conferidos)

* CLEO 4.4 `source/CScriptEngine.cpp`: `ThreadSavingInfo` (linha ~315) —
  guarda `SCRIPT_VAR tls[32]`, `timers`, `ip_diff` e aplica de volta em
  `Apply()`; `OnInitScm1/2/3` (`RemoveAllCustomScripts()` + `InitScm()` +
  `LoadCustomScripts()`), `OnNewGame`, `OnLoadScmData`, `SaveState()`.
* CLEO 4.4 `source/CCustomOpcodeSystem.cpp`: `0A95 enable_thread_saving`
  (desligado por padrão — `bSaveEnabled(false)` no construtor do
  `CCustomScript`; por isso o VPE NÃO tem estado restaurado do save),
  `0A9F current_thread_pointer`, `0AAA thread pointer by name`.
* CLEO+ 1.0.7 `CLEOPlus/CLEOPlus.cpp`: `restartEvent.before` (endereço
  `0x53C6DB`) → `ClearScriptLists()` + `ClearAllScriptEvents()`; `loadingEvent`
  (`0x5D19CE`), `newGameFirstStartEvent` (`0x748E1C`), `startSaveGame`
  (`0x618F51`).
* CLEO+ 1.0.7 `CLEOPlus/List.cpp`: `DELETE_LIST` faz `delete scriptList` sem
  validar; `GET_LIST_VALUE_BY_INDEX`/`LIST_ADD`/`RESET_LIST` dereferenciam o
  ponteiro guardado (crash com ponteiro morto); `ClearScriptLists()` no restart.
