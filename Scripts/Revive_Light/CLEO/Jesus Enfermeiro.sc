/*
    ============================================================================
    Jesus Enfermeiro - GTA3script (CLEO 4.4.4 + CLEO+)
    ============================================================================
    Mod original: Junior_Djjr (MixMods)
    Codigo antigo: "Jesus Enfermeiro (Junior_Djjr).txt" (decompilado, Sanny)
    Reescrita e otimizacao: 2026

    ----------------------------------------------------------------------------
    O QUE O MOD FAZ
    ----------------------------------------------------------------------------
    Acende uma luz em cima de cada pedestre cujo MODELO esteja listado em
    CLEO\Jesus Enfermeiro.ini. A lista usa NOME de modelo (nunca ID), entao
    funciona tambem com peds adicionados que nao substituem nenhum original:
    basta o nome do modelo existir no jogo (.ide ou ModLoader).

    A luz some na hora em que o NPC morre, e varios NPCs podem ser iluminados
    ao mesmo tempo - veja o "MaxLights" no INI se voce usa algum limit
    adjuster de searchlights.

    ----------------------------------------------------------------------------
    O QUE MELHOROU EM RELACAO AO SCRIPT ANTIGO
    ----------------------------------------------------------------------------
    1.  Os modelos vem do INI, por nome. O script antigo conhecia 3 IDs fixos
        (LAEMT1/SFEMT1/LVEMT1) - nao funcionava em ped nenhum, nem em peds
        adicionados.
    2.  Varios NPCs ao mesmo tempo. O antigo iluminava um pedestre por vez.
    3.  A luz desaparece na hora em que o NPC morre, e tambem quando o ped e
        deletado ou sai da pool (streaming). O antigo nao checava nada disso.
    4.  Nada de varrer a heap na mao (0xB74490 + byte map) em todo quadro: a
        enumeracao usa a pool oficial (CLEO+ GET_ANY_CHAR_NO_SAVE_RECURSIVE) e
        a comparacao de modelo usa o ID resolvido pelo nome
        (CLEO+ GET_MODEL_BY_NAME). A varredura roda a cada N quadros.
    5.  Guarda contra o bug do jogo que escreve FORA do array de searchlights
        quando acaba a vaga (CTheScripts::AddScriptSearchLight): o script le
        NumberOfScriptSearchLights antes de criar luz e respeita o "MaxLights"
        configurado no INI (7 no jogo original, 120 com limit adjuster).
    6.  A mira da searchlight e re-apontada para o NPC a cada quadro (1
        opcode), e a luz so e desmontada/refeita quando o NPC se afasta mais
        de ~5 m - o jogo nao tem opcode para mover a ORIGEM da searchlight.
        Como o jogo nao usa fade na searchlight, refazer a luz no mesmo
        quadro nao pisca. Pedestre parado (CPR) nao custa nada.
    7.  Dois tipos de luz, escolhidos no INI: facho de searchlight (como no
        original) e/ou brilho (corona com cor/size configuravel). O brilho
        nao gasta vaga de searchlight, entao serve para iluminar muito mais
        NPCs ao mesmo tempo.
    8.  O INI e relido sozinho a cada ~10 segundos: da para testar mudancas
        sem reiniciar o jogo.
    9.  "OnlyWhenReviving = 1" reproduz o comportamento original (luz somente
        enquanto o ped faz a animacao de reanimacao/CPR).

    ----------------------------------------------------------------------------
    REQUISITOS
    ----------------------------------------------------------------------------
    - CLEO 4.4.4 ou superior
    - CLEO+ 1.2.0 ou superior (cleo.li) - usa GET_MODEL_BY_NAME e
      GET_ANY_CHAR_NO_SAVE_RECURSIVE. Sem o CLEO+ o script avisa e se desliga.

    Arquivos:
        CLEO\Jesus Enfermeiro.cs    script compilado
        CLEO\Jesus Enfermeiro.ini   lista de modelos + ajustes

    Compilar (gta3sc):
        gta3sc "Jesus Enfermeiro.sc" --config=gtasa --guesser -fconst -farrays
               -fbreak-continue -fifnot -flocal-var-limit=32 --cs
               -o "Jesus Enfermeiro.cs"
    ============================================================================
*/

// ---------------------------------------------------------------------------
// Constantes de compilacao (nao ocupam variavel nem memoria no script)
// ---------------------------------------------------------------------------
CONST_INT   JE_MAX_CHARS          8       // vagas de NPC iluminado por vez
CONST_INT   JE_MAX_MODELS         32      // maximo de modelos lidos do INI
CONST_INT   JE_MEM_SIZE           576     // bloco de memoria do script
CONST_INT   JE_OFF_MODELS         0       // 32 IDs de modelo   (128 bytes)
CONST_INT   JE_OFF_TABLE          128     // 8 vagas x 24 bytes (192 bytes)
CONST_INT   JE_OFF_LINE           320     // buffer de linha   (256 bytes)
CONST_INT   JE_ENTRY_SIZE         24      // tamanho de cada vaga da tabela
CONST_INT   JE_ENTRY_LIGHT        4       //       +4 = handle da searchlight
CONST_INT   JE_ENTRY_X            8       //       +8 = origem X  (para o
CONST_INT   JE_ENTRY_Y            12      //      +12 = origem Y   teste de
CONST_INT   JE_ENTRY_Z            16      //      +16 = origem Z   movimento)
CONST_INT   JE_ENTRY_HAS          20      //      +20 = 1 se a searchlight existe
CONST_INT   JE_LINE_SIZE          256     // tamanho maximo de uma linha do INI
CONST_INT   JE_SCAN_EVERY         6       // varredura de peds a cada N quadros
CONST_INT   JE_RELOAD_EVERY       600     // recarrega o INI a cada N quadros
CONST_INT   JE_DEF_MAXLIGHTS      7       // o array do jogo tem 8 searchlights
CONST_INT   JE_LIGHT_COUNT_ADDR   0xA90830 // CTheScripts::NumberOfScriptSearchLights

CONST_FLOAT JE_FOLLOW_DIST_SQ     25.0    // 5 m ao quadrado
CONST_FLOAT JE_AIM_SPEED          5.0     // velocidade do ajuste da mira
CONST_FLOAT JE_DEF_HEIGHT         30.0    // altura padrao do foco
CONST_FLOAT JE_DEF_RADIUS1        0.0     // 1o raio do opcode 06B1
CONST_FLOAT JE_DEF_RADIUS2        2.5     // 2o raio do opcode 06B1
CONST_FLOAT JE_DEF_GLOWSIZE       0.9     // tamanho do brilho (corona)

SCRIPT_START
{
    NOP
    SCRIPT_NAME jesus

    // -----------------------------------------------------------------------
    // Variaveis
    // -----------------------------------------------------------------------
    LVAR_INT   base                    // bloco de memoria do script
    LVAR_INT   i
    LVAR_INT   prog                    // progresso da varredura da pool
    LVAR_INT   ch                      // pedestre   (scratch, sempre recarregado)
    LVAR_INT   lh                      // searchlight (scratch)
    LVAR_INT   entry                   // endereco da vaga atual da tabela
    LVAR_INT   line                    // endereco da linha atual do INI
    LVAR_INT   len                     // tamanho da linha / indice do '='
    LVAR_INT   tmp
    LVAR_INT   ptr
    LVAR_INT   model                   // ID do modelo do pedestre
    LVAR_INT   id                      // ID de um modelo da lista do INI
    LVAR_INT   nModels                 // quantos modelos vieram do INI
    LVAR_INT   section                 // 0 = nenhuma, 1 = [Settings], 2 = [Models]
    LVAR_INT   lightType               // 0 = facho, 1 = brilho, 2 = os dois
    LVAR_INT   onlyCpr                 // 1 = so durante a reanimacao (CPR)
    LVAR_INT   maxLights               // searchlights que o jogo aguenta
    LVAR_INT   colR
    LVAR_INT   colG
    LVAR_INT   colB
    LVAR_INT   hFile                   // arquivo do INI
    LVAR_INT   firstRun                // 1 = ainda nao avisou o jogador

    LVAR_FLOAT height                  // altura do foco acima do ped
    LVAR_FLOAT rad1
    LVAR_FLOAT rad2
    LVAR_FLOAT glowSize
    LVAR_FLOAT px
    LVAR_FLOAT py
    LVAR_FLOAT pz
    LVAR_FLOAT sz
    LVAR_FLOAT dx
    LVAR_FLOAT dist

    // -----------------------------------------------------------------------
    // Dicas de tipo para o compilador
    // -----------------------------------------------------------------------
    // O gta3script exige que variavel de entidade nasça de um comando que a
    // devolva. As duas linhas abaixo somente anotam os tipos de `ch` (CHAR) e
    // de `lh` (SEARCHLIGHT) e nunca sao executadas: `base` e um endereco de
    // memoria valido e jamais fica negativo.
    // -----------------------------------------------------------------------
    IF base < 0
        GET_PLAYER_CHAR 0 (ch)
        CREATE_SEARCHLIGHT 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 (lh)
    ENDIF

    // -----------------------------------------------------------------------
    // Preparacao
    // -----------------------------------------------------------------------
    IF NOT DOES_FILE_EXIST "CLEO\CLEO+.cleo"
        IF NOT DOES_FILE_EXIST "CLEO\core\CLEO+.cleo"
            PRINT_STRING_NOW "~r~Jesus Enfermeiro:~w~ instale o CLEO+ (cleo.li) para usar este mod." 9000
            TERMINATE_THIS_CUSTOM_SCRIPT
        ENDIF
    ENDIF

    ALLOCATE_MEMORY JE_MEM_SIZE (base)
    IF base = 0
        TERMINATE_THIS_CUSTOM_SCRIPT
    ENDIF

    // zera a tabela (ped handle 0 = vaga livre, JE_ENTRY_HAS 0 = sem luz)
    entry = base
    entry += JE_OFF_TABLE
    i = 0
    WHILE i < JE_MAX_CHARS
        WRITE_MEMORY entry 4 0 0
        tmp = entry
        tmp += JE_ENTRY_HAS
        WRITE_MEMORY tmp 4 0 0
        entry += JE_ENTRY_SIZE
        i += 1
    ENDWHILE

    firstRun = 1
    GOSUB LoadIni

    // -----------------------------------------------------------------------
    // Loop principal
    // -----------------------------------------------------------------------
main_loop:
    WAIT 0

    IF NOT IS_PLAYER_PLAYING 0
        GOTO main_loop
    ENDIF

    IF FRAME_MOD JE_RELOAD_EVERY
        GOSUB LoadIni
    ENDIF

    IF FRAME_MOD JE_SCAN_EVERY
        GOSUB ScanPeds
    ENDIF

    i = 0
    WHILE i < JE_MAX_CHARS
        GOSUB UpdateEntry
        i += 1
    ENDWHILE

    GOTO main_loop

    // =======================================================================
    // LoadIni - le CLEO\Jesus Enfermeiro.ini (ajustes + lista de modelos)
    // =======================================================================
LoadIni:
    // valores padrao, usados se o INI nao existir ou nao definir a chave
    lightType = 0
    height = JE_DEF_HEIGHT
    rad1 = JE_DEF_RADIUS1
    rad2 = JE_DEF_RADIUS2
    glowSize = JE_DEF_GLOWSIZE
    colR = 255
    colG = 240
    colB = 180
    onlyCpr = 0
    maxLights = JE_DEF_MAXLIGHTS
    nModels = 0

    IF NOT DOES_FILE_EXIST "CLEO\Jesus Enfermeiro.ini"
        GOSUB DefaultModels
        GOSUB ValidateEntries
        IF firstRun = 1
            PRINT_STRING_NOW "~y~Jesus Enfermeiro:~w~ crie o CLEO\Jesus Enfermeiro.ini (por enquanto so os medicos originais)." 9000
        ENDIF
        firstRun = 0
        RETURN
    ENDIF

    IF OPEN_FILE "CLEO\Jesus Enfermeiro.ini" "r" (hFile)
        line = base
        line += JE_OFF_LINE
        section = 0
        tmp = 1
        WHILE tmp = 1
            IF READ_STRING_FROM_FILE hFile line JE_LINE_SIZE
                GOSUB ParseLine
            ELSE
                tmp = 0
            ENDIF
        ENDWHILE
        CLOSE_FILE hFile
    ENDIF

    IF nModels = 0
        GOSUB DefaultModels
    ENDIF

    GOSUB ValidateEntries
    firstRun = 0
    RETURN

    // -----------------------------------------------------------------------
    // ParseLine - interpreta uma linha do INI
    // -----------------------------------------------------------------------
ParseLine:
    GET_STRING_LENGTH $line (len)

    // pula o BOM (alguns editores do Windows gravam UTF-8 com BOM)
    IF len > 2
        READ_MEMORY line 1 0 (tmp)
        IF tmp = 239
            line += 3
            len -= 3
        ENDIF
    ENDIF

    // tira espacos, tabs e CR/LF do comeco
    i = 0
    WHILE i < len
        ptr = line
        ptr += i
        READ_MEMORY ptr 1 0 (tmp)
        IF tmp < 33
            i += 1
        ELSE
            BREAK
        ENDIF
    ENDWHILE
    line += i
    len -= i

    // ... e do fim
    WHILE len > 0
        i = len
        i -= 1
        ptr = line
        ptr += i
        READ_MEMORY ptr 1 0 (tmp)
        IF tmp < 33
            len -= 1
        ELSE
            BREAK
        ENDIF
    ENDWHILE
    IF len = 0
        RETURN
    ENDIF

    // comentarios: ; ou #
    READ_MEMORY line 1 0 (tmp)
    IF tmp = 59
        RETURN
    ENDIF
    IF tmp = 35
        RETURN
    ENDIF

    // cabecalho de secao: [Nome]
    IF tmp = 91
        GOSUB ParseSection
        RETURN
    ENDIF

    // procura o '=' da linha
    i = 0
    WHILE i < len
        ptr = line
        ptr += i
        READ_MEMORY ptr 1 0 (tmp)
        IF tmp = 61
            BREAK
        ENDIF
        i += 1
    ENDWHILE

    IF i >= len
        // linha sem '=': dentro de [Models] a linha toda e um nome de modelo
        IF section = 2
            GOSUB AddModel
        ENDIF
        RETURN
    ENDIF

    IF section = 2
        // "nome = valor" em [Models]: o valor e ignorado, o nome vale
        CUT_STRING_AT line i
        GOSUB AddModel
        RETURN
    ENDIF

    // chave = valor (antes de qualquer secao ou em [Settings])
    ptr = line
    ptr += i
    ptr += 1
    CUT_STRING_AT line i
    GOSUB ParseSetting
    RETURN

    // -----------------------------------------------------------------------
    // ParseSection - [Settings] ou [Models]
    // -----------------------------------------------------------------------
ParseSection:
    i = 1
    WHILE i < len
        ptr = line
        ptr += i
        READ_MEMORY ptr 1 0 (tmp)
        IF tmp = 93
            BREAK
        ENDIF
        i += 1
    ENDWHILE
    IF i >= len
        RETURN
    ENDIF
    line += 1
    len = i
    len -= 1
    CUT_STRING_AT line len
    section = 0
    IF IS_STRING_EQUAL $line "Settings" 16 0 ""
        section = 1
        RETURN
    ENDIF
    IF IS_STRING_EQUAL $line "Models" 16 0 ""
        section = 2
        RETURN
    ENDIF
    // a comparacao acima deixou o texto em MAIUSCULAS: qualquer outra secao
    // vale como nome de modelo
    section = 2
    GOSUB AddModel
    RETURN

    // -----------------------------------------------------------------------
    // ParseSetting - aplica uma chave de [Settings]
    // -----------------------------------------------------------------------
ParseSetting:
    IF IS_STRING_EQUAL $line "TYPE" 32 0 ""
        SCAN_STRING $ptr "%d" (tmp) (lightType)
    ENDIF
    IF IS_STRING_EQUAL $line "HEIGHT" 32 0 ""
        SCAN_STRING $ptr "%f" (tmp) (height)
    ENDIF
    IF IS_STRING_EQUAL $line "RADIUS1" 32 0 ""
        SCAN_STRING $ptr "%f" (tmp) (rad1)
    ENDIF
    IF IS_STRING_EQUAL $line "RADIUS2" 32 0 ""
        SCAN_STRING $ptr "%f" (tmp) (rad2)
    ENDIF
    IF IS_STRING_EQUAL $line "GLOWSIZE" 32 0 ""
        SCAN_STRING $ptr "%f" (tmp) (glowSize)
    ENDIF
    IF IS_STRING_EQUAL $line "COLOR" 32 0 ""
        SCAN_STRING $ptr "%d %d %d" (tmp) (colR) (colG) (colB)
    ENDIF
    IF IS_STRING_EQUAL $line "ONLYWHENREVIVING" 32 0 ""
        SCAN_STRING $ptr "%d" (tmp) (onlyCpr)
    ENDIF
    IF IS_STRING_EQUAL $line "MAXLIGHTS" 32 0 ""
        SCAN_STRING $ptr "%d" (tmp) (maxLights)
    ENDIF
    RETURN

    // -----------------------------------------------------------------------
    // AddModel - guarda o ID do modelo cujo nome esta em `line`
    // -----------------------------------------------------------------------
AddModel:
    GOSUB CountLen
    // a linha e so ate o primeiro espaco, ';' ou '#' (aceita comentario junto)
    i = 0
    WHILE i < len
        ptr = line
        ptr += i
        READ_MEMORY ptr 1 0 (tmp)
        IF tmp < 33
            BREAK
        ENDIF
        IF tmp = 59
            BREAK
        ENDIF
        IF tmp = 35
            BREAK
        ENDIF
        i += 1
    ENDWHILE
    CUT_STRING_AT line i
    SET_STRING_UPPER line
    IF nModels >= JE_MAX_MODELS
        RETURN
    ENDIF
    IF GET_MODEL_BY_NAME $line (model)
        tmp = nModels
        tmp = tmp * 4
        entry = base
        entry += JE_OFF_MODELS
        entry += tmp
        WRITE_MEMORY entry 4 model 0
        nModels += 1
    ELSE
        IF firstRun = 1
            PRINT_FORMATTED_NOW "~y~Jesus Enfermeiro:~w~ modelo desconhecido no INI: %s" (line)
        ENDIF
    ENDIF
    RETURN

    // -----------------------------------------------------------------------
    // CountLen - len = tamanho da string em `line`
    // -----------------------------------------------------------------------
CountLen:
    GET_STRING_LENGTH $line (len)
    RETURN

    // -----------------------------------------------------------------------
    // DefaultModels - a lista do mod original (medicos de LS/SF/LV)
    // -----------------------------------------------------------------------
DefaultModels:
    entry = base
    entry += JE_OFF_MODELS
    nModels = 0
    IF GET_MODEL_BY_NAME "LAEMT1" (model)
        WRITE_MEMORY entry 4 model 0
        nModels += 1
    ENDIF
    entry += 4
    IF GET_MODEL_BY_NAME "SFEMT1" (model)
        WRITE_MEMORY entry 4 model 0
        nModels += 1
    ENDIF
    entry += 4
    IF GET_MODEL_BY_NAME "LVEMT1" (model)
        WRITE_MEMORY entry 4 model 0
        nModels += 1
    ENDIF
    RETURN

    // -----------------------------------------------------------------------
    // ValidateEntries - solta quem deixou de usar um modelo da lista
    // -----------------------------------------------------------------------
ValidateEntries:
    i = 0
    WHILE i < JE_MAX_CHARS
        GOSUB GetEntry
        READ_MEMORY entry 4 0 (ch)
        IF ch > 0
            GET_CHAR_MODEL ch (model)
            GOSUB ModelInList
            IF ptr = 0
                GOSUB DropEntry
            ENDIF
        ENDIF
        i += 1
    ENDWHILE
    RETURN

    // -----------------------------------------------------------------------
    // ModelInList - ptr = 1 se o ID em `model` esta na lista do INI
    // -----------------------------------------------------------------------
ModelInList:
    ptr = 0
    entry = base
    entry += JE_OFF_MODELS
    tmp = 0
    WHILE tmp < nModels
        READ_MEMORY entry 4 0 (id)
        IF id = model
            ptr = 1
            BREAK
        ENDIF
        entry += 4
        tmp += 1
    ENDWHILE
    RETURN

    // =======================================================================
    // ScanPeds - procura na pool de pedestres quem usa um modelo do INI
    // =======================================================================
ScanPeds:
    prog = 0
    WHILE GET_ANY_CHAR_NO_SAVE_RECURSIVE prog (prog ch)
        GOSUB CheckPed
    ENDWHILE
    RETURN

CheckPed:
    // ja esta sendo acompanhado?
    i = 0
    WHILE i < JE_MAX_CHARS
        GOSUB GetEntry
        READ_MEMORY entry 4 0 (tmp)
        IF tmp = ch
            RETURN
        ENDIF
        i += 1
    ENDWHILE

    // o modelo dele esta na lista do INI?
    GET_CHAR_MODEL ch (model)
    GOSUB ModelInList
    IF ptr = 0
        RETURN
    ENDIF

    // guarda na primeira vaga livre
    i = 0
    WHILE i < JE_MAX_CHARS
        GOSUB GetEntry
        READ_MEMORY entry 4 0 (tmp)
        IF tmp = 0
            WRITE_MEMORY entry 4 ch 0
            RETURN
        ENDIF
        i += 1
    ENDWHILE
    RETURN

    // =======================================================================
    // UpdateEntry - mantem (ou nao) a luz do NPC da vaga `i`
    // =======================================================================
UpdateEntry:
    GOSUB GetEntry
    READ_MEMORY entry 4 0 (ch)
    IF ch = 0
        RETURN
    ENDIF

    // morreu, ou foi deletado (streaming): a luz vai embora
    IF NOT DOES_CHAR_EXIST ch
        GOSUB DropEntry
        RETURN
    ENDIF
    IF IS_CHAR_DEAD ch
        GOSUB DropEntry
        RETURN
    ENDIF

    // opcional: so enquanto ele reanima alguem (comportamento do mod original)
    IF onlyCpr = 1
        IF NOT IS_CHAR_PLAYING_ANIM ch "CPR"
            GOSUB KillLight
            RETURN
        ENDIF
    ENDIF

    GET_CHAR_COORDINATES ch (px py pz)

    // facho de searchlight (recriado apenas quando o NPC sai do lugar)
    IF lightType = 1
        GOSUB KillLight
    ELSE
        GOSUB UpdateLight
    ENDIF

    // brilho (corona com cor configuravel)
    IF lightType = 1
    OR lightType = 2
        IF IS_CHAR_ON_SCREEN ch
            sz = pz
            sz += height
            DRAW_CORONA px py sz glowSize CORONATYPE_SHINYSTAR FLARETYPE_NONE colR colG colB
        ENDIF
    ENDIF
    RETURN

    // -----------------------------------------------------------------------
    // UpdateLight - cria/move a searchlight da vaga atual
    // -----------------------------------------------------------------------
UpdateLight:
    tmp = entry
    tmp += JE_ENTRY_HAS
    READ_MEMORY tmp 4 0 (id)
    IF id = 0
        GOSUB CreateLight
        RETURN
    ENDIF

    // a luz ainda e a nossa? (missao ou script podem limpar o array do jogo)
    tmp = entry
    tmp += JE_ENTRY_LIGHT
    READ_MEMORY tmp 4 0 (lh)
    IF NOT DOES_SEARCHLIGHT_EXIST lh
        GOSUB CreateLight
        RETURN
    ENDIF

    // a searchlight fica no ceu e a mira e re-apontada para o NPC (1 opcode)
    POINT_SEARCHLIGHT_AT_COORD lh px py pz JE_AIM_SPEED

    // o jogo nao tem opcode para mover a ORIGEM da searchlight: quando o NPC
    // se afasta muito, a luz e refeita em cima dele
    sz = pz
    sz += height
    dist = 0.0
    ptr = entry
    ptr += JE_ENTRY_X
    READ_MEMORY ptr 4 0 (dx)
    dx -= px
    dx = dx * dx
    dist += dx
    ptr += 4
    READ_MEMORY ptr 4 0 (dx)
    dx -= py
    dx = dx * dx
    dist += dx
    ptr += 4
    READ_MEMORY ptr 4 0 (dx)
    dx -= sz
    dx = dx * dx
    dist += dx

    IF dist > JE_FOLLOW_DIST_SQ
        DELETE_SEARCHLIGHT lh
        GOSUB CreateLight
    ENDIF
    RETURN

    // -----------------------------------------------------------------------
    // CreateLight - cria a searchlight no ponto atual
    // -----------------------------------------------------------------------
CreateLight:
    // O handler de 06B1 escreve FORA do array quando nao ha vaga, entao nunca
    // crie uma luz sem confirmar que ainda tem espaco (MaxLights no INI).
    READ_MEMORY JE_LIGHT_COUNT_ADDR 2 0 (tmp)
    IF tmp >= maxLights
        RETURN
    ENDIF

    sz = pz
    sz += height
    CREATE_SEARCHLIGHT px py sz px py pz rad1 rad2 (lh)

    // guarda o handle, de onde a luz nasceu e a marca de "temos luz"
    ptr = entry
    ptr += JE_ENTRY_LIGHT
    WRITE_MEMORY ptr 4 lh 0
    ptr += 4
    WRITE_MEMORY ptr 4 px 0
    ptr += 4
    WRITE_MEMORY ptr 4 py 0
    ptr += 4
    WRITE_MEMORY ptr 4 sz 0
    ptr += 4
    WRITE_MEMORY ptr 4 1 0
    RETURN

    // -----------------------------------------------------------------------
    // KillLight - apaga a searchlight desta vaga (sem perder o NPC)
    // -----------------------------------------------------------------------
KillLight:
    ptr = entry
    ptr += JE_ENTRY_HAS
    READ_MEMORY ptr 4 0 (id)
    IF id = 0
        RETURN
    ENDIF
    WRITE_MEMORY ptr 4 0 0

    ptr = entry
    ptr += JE_ENTRY_LIGHT
    READ_MEMORY ptr 4 0 (lh)
    IF DOES_SEARCHLIGHT_EXIST lh
        DELETE_SEARCHLIGHT lh
    ENDIF
    RETURN

    // -----------------------------------------------------------------------
    // DropEntry - apaga a luz e libera a vaga da tabela
    // -----------------------------------------------------------------------
DropEntry:
    GOSUB KillLight
    WRITE_MEMORY entry 4 0 0
    RETURN

    // -----------------------------------------------------------------------
    // GetEntry - entry = endereco da vaga `i` da tabela
    // -----------------------------------------------------------------------
GetEntry:
    tmp = i
    tmp = tmp * JE_ENTRY_SIZE
    entry = base
    entry += JE_OFF_TABLE
    entry += tmp
    RETURN
}
SCRIPT_END
