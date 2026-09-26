/*
    ============================================================================
    Jesus Enfermeiro - GTA3script (CLEO 4.4.4 + CLEO+)
    ============================================================================
    Mod original: Junior_Djjr (MixMods)
    Codigo antigo: "Jesus Enfermeiro (Junior_Djjr).txt" (decompilado, Sanny)
    Reescrita e otimizacao: 2026
    Proper Shaders API: 2026 (luz per-pixel deferred)

    ----------------------------------------------------------------------------
    O QUE O MOD FAZ
    ----------------------------------------------------------------------------
    Acende uma luz em cima de cada pedestre cujo MODELO esteja listado em
    CLEO\Jesus Enfermeiro.ini (lido uma vez, ao entrar no jogo). A lista usa
    NOME de modelo (nunca ID), entao
    funciona tambem com peds adicionados que nao substituem nenhum original:
    basta o nome do modelo existir no jogo (.ide ou ModLoader).

    A luz some na hora em que o NPC morre, e varios NPCs podem ser iluminados
    ao mesmo tempo - veja o "MaxLights" no INI se voce usa algum limit
    adjuster de searchlights.

    Com o Proper Shaders instalado (versao com API, 09-26+), o mod cria
    luzes dinamicas per-pixel de verdade (PS_LightCreate): iluminam parede,
    chao, CJ, fog volumetrica etc. Sem o Proper Shaders, cai no comportamento
    antigo (searchlight / corona). Documentacao da API:
        https://github.com/MixMods/ProperShadersApiExample

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
    6.  A searchlight fica estatica em cima do NPC (como no mod original) e e
        refeita quando ele se afasta mais de ~1.5 m: o jogo nao tem opcode
        para mover a ORIGEM dela. Como a searchlight nao tem fade, refazer a
        luz no mesmo quadro nao pisca. Pedestre parado (CPR) nao custa nada.
    7.  Dois tipos de luz, escolhidos no INI: facho de searchlight (como no
        original) e/ou brilho (corona com cor/size configuravel). O brilho
        nao gasta vaga de searchlight, entao serve para iluminar muito mais
        NPCs ao mesmo tempo.
    8.  "OnlyWhenReviving = 1" reproduz o comportamento original (luz somente
        enquanto o ped faz a animacao de reanimacao/CPR).
    9.  "Type = 1" acende so o brilho (corona) e NAO usa searchlight nenhuma:
        serve tambem para testar se algum outro mod esta estourando o array de
        searchlights do jogo (que tem apenas 8 vagas).
   10.  Integracao opcional com a API do Proper Shaders (UseProperShaders=1):
        luz ponto/spot per-pixel que segue o NPC a cada quadro via
        PS_LightSetPosition. Sem o .asi, o mod continua normal.

    ----------------------------------------------------------------------------
    REQUISITOS
    ----------------------------------------------------------------------------
    - CLEO 4.4.4 ou superior
    - CLEO+ 1.2.0 ou superior (cleo.li) - usa GET_MODEL_BY_NAME e
      GET_ANY_CHAR_NO_SAVE_RECURSIVE. Sem o CLEO+ o script avisa e se desliga.
    - Proper Shaders (opcional, mas recomendado) com a API publica
      (ProperShaders.asi exportando PS_LightCreate etc.). Sem ele o mod
      usa searchlight/corona como antes.

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
CONST_INT   JE_MAX_CHARS          16      // vagas de NPC iluminado por vez
CONST_INT   JE_MAX_MODELS         32      // maximo de modelos lidos do INI
CONST_INT   JE_MEM_SIZE           896     // bloco de memoria do script
CONST_INT   JE_OFF_MODELS         0       // 32 IDs de modelo   (128 bytes)
CONST_INT   JE_OFF_TABLE          128     // 16 vagas x 24 bytes (384 bytes)
CONST_INT   JE_OFF_LINE           512     // buffer de linha   (256 bytes)
CONST_INT   JE_OFF_DESC           768     // PS_LightDesc       (88 bytes)
CONST_INT   JE_OFF_PS             856     // ponteiros da API   (40 bytes)
// layout de cada vaga da tabela (24 bytes):
//   +0  handle do ped
//   +4  handle da luz (searchlight OU PS_LightHandle)
//   +8  origem X (so searchlight - teste de movimento)
//  +12  origem Y
//  +16  origem Z
//  +20  kind: 0=sem luz, 1=searchlight, 2=ProperShaders
CONST_INT   JE_ENTRY_SIZE         24
CONST_INT   JE_ENTRY_LIGHT        4
CONST_INT   JE_ENTRY_X            8
CONST_INT   JE_ENTRY_Y            12
CONST_INT   JE_ENTRY_Z            16
CONST_INT   JE_ENTRY_KIND         20
CONST_INT   JE_KIND_NONE          0
CONST_INT   JE_KIND_SEARCHLIGHT   1
CONST_INT   JE_KIND_PS            2
CONST_INT   JE_LINE_SIZE          256
CONST_INT   JE_READ_SIZE          240
CONST_INT   JE_SCAN_EVERY         6
CONST_INT   JE_DEF_MAXLIGHTS      7
CONST_INT   JE_LIGHT_COUNT_ADDR   0xA90830 // CTheScripts::NumberOfScriptSearchLights

// Offsets dentro do bloco JE_OFF_PS (ponteiros / config da API)
CONST_INT   JE_PS_CREATE          0       // PS_LightCreate*
CONST_INT   JE_PS_DESTROY         4       // PS_LightDestroy*
CONST_INT   JE_PS_SETPOS          8       // PS_LightSetPosition*
CONST_INT   JE_PS_ACTIVE          12      // 1 se a API carregou
CONST_INT   JE_PS_USE             16      // UseProperShaders do INI
CONST_INT   JE_PS_RADIUS          20      // float
CONST_INT   JE_PS_INTENSITY       24      // float
CONST_INT   JE_PS_OFFSETZ         28      // float (altura da luz no corpo)
CONST_INT   JE_PS_FOG             32      // int fogMode
CONST_INT   JE_PS_TYPE            36      // 0=point 1=spot

// PS_LightDesc (88 bytes) - ver ProperShadersAPI.h
CONST_INT   JE_DESC_SIZE          88
CONST_INT   JE_DESC_STRUCTSIZE    0
CONST_INT   JE_DESC_TYPE          4
CONST_INT   JE_DESC_FLAGS         8
CONST_INT   JE_DESC_POSX          12
CONST_INT   JE_DESC_POSY          16
CONST_INT   JE_DESC_POSZ          20
CONST_INT   JE_DESC_DIRX          24
CONST_INT   JE_DESC_DIRY          28
CONST_INT   JE_DESC_DIRZ          32
CONST_INT   JE_DESC_RADIUS        36
CONST_INT   JE_DESC_COLORR        40
CONST_INT   JE_DESC_COLORG        44
CONST_INT   JE_DESC_COLORB        48
CONST_INT   JE_DESC_INTENSITY     52
CONST_INT   JE_DESC_SPOTANGLE     56
CONST_INT   JE_DESC_FOGMODE       60
CONST_INT   JE_DESC_FOGINT        64
CONST_INT   JE_DESC_BEAMMODE      68
CONST_INT   JE_DESC_BEAMLEN       72
CONST_INT   JE_DESC_BEAMINT       76
CONST_INT   JE_DESC_LIFE          80
CONST_INT   JE_DESC_FADE          84

CONST_FLOAT JE_FOLLOW_DIST_SQ     2.25    // 1.5 m ao quadrado
CONST_FLOAT JE_DEF_HEIGHT         30.0    // altura padrao do foco (searchlight)
CONST_FLOAT JE_DEF_RADIUS1        0.0
CONST_FLOAT JE_DEF_RADIUS2        2.5
CONST_FLOAT JE_DEF_GLOWSIZE       0.9
CONST_FLOAT JE_DEF_PS_RADIUS      12.0
CONST_FLOAT JE_DEF_PS_INTENSITY   2.0
CONST_FLOAT JE_DEF_PS_OFFSETZ     1.2
CONST_FLOAT JE_FLOAT_ONE          1.0
CONST_FLOAT JE_FLOAT_ZERO         0.0
CONST_FLOAT JE_FLOAT_NEG1         -1.0
CONST_FLOAT JE_COLOR_DIV          255.0
CONST_FLOAT JE_DEF_SPOTANGLE      55.0

SCRIPT_START
{
    NOP
    SCRIPT_NAME jesus

    // -----------------------------------------------------------------------
    // Variaveis (limite 32 com -flocal-var-limit=32)
    // Ponteiros da API do Proper Shaders ficam no bloco de memoria (JE_OFF_PS)
    // para nao estourar o limite de LVAR.
    // -----------------------------------------------------------------------
    LVAR_INT   base
    LVAR_INT   i
    LVAR_INT   prog
    LVAR_INT   ch
    LVAR_INT   lh                      // searchlight OU PS_LightHandle
    LVAR_INT   entry
    LVAR_INT   line
    LVAR_INT   len
    LVAR_INT   tmp
    LVAR_INT   ptr
    LVAR_INT   model
    LVAR_INT   id
    LVAR_INT   nModels
    LVAR_INT   section
    LVAR_INT   lightType               // 0=facho 1=brilho 2=ambos
    LVAR_INT   onlyCpr
    LVAR_INT   maxLights
    LVAR_INT   colR
    LVAR_INT   colG
    LVAR_INT   colB
    LVAR_INT   hFile

    LVAR_FLOAT height
    LVAR_FLOAT rad1
    LVAR_FLOAT rad2
    LVAR_FLOAT glowSize
    LVAR_FLOAT px
    LVAR_FLOAT py
    LVAR_FLOAT pz
    LVAR_FLOAT sz
    LVAR_FLOAT dx
    LVAR_FLOAT dist
    LVAR_FLOAT fTmp                    // scratch float (cor 0-1, configs PS)

    // -----------------------------------------------------------------------
    // Tipos das variaveis de entidade (CHAR / SEARCHLIGHT)
    // -----------------------------------------------------------------------
    IF base < 0
        GET_PLAYER_CHAR 0 (ch)
        READ_MEMORY JE_LIGHT_COUNT_ADDR 2 0 (tmp)
        IF tmp < JE_DEF_MAXLIGHTS
            CREATE_SEARCHLIGHT 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 (lh)
            IF lh > -1
                IF DOES_SEARCHLIGHT_EXIST lh
                    DELETE_SEARCHLIGHT lh
                ENDIF
            ENDIF
        ENDIF
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

    // zera a tabela e o bloco da API
    entry = base
    entry += JE_OFF_TABLE
    i = 0
    WHILE i < JE_MAX_CHARS
        WRITE_MEMORY entry 4 0 0
        tmp = entry
        tmp += JE_ENTRY_KIND
        WRITE_MEMORY tmp 4 0 0
        entry += JE_ENTRY_SIZE
        i += 1
    ENDWHILE

    ptr = base
    ptr += JE_OFF_PS
    i = 0
    WHILE i < 40
        tmp = ptr
        tmp += i
        WRITE_MEMORY tmp 1 0 0
        i += 1
    ENDWHILE

    GOSUB LoadIni
    GOSUB TryLoadProperShaders

    // -----------------------------------------------------------------------
    // Loop principal
    // -----------------------------------------------------------------------
main_loop:
    WAIT 0

    IF NOT IS_PLAYER_PLAYING 0
        GOTO main_loop
    ENDIF

    // tenta de novo a API umas vezes (ASI pode carregar depois do CLEO)
    IF FRAME_MOD 120
        ptr = base
        ptr += JE_OFF_PS
        ptr += JE_PS_ACTIVE
        READ_MEMORY ptr 4 0 (tmp)
        IF tmp = 0
            ptr = base
            ptr += JE_OFF_PS
            ptr += JE_PS_USE
            READ_MEMORY ptr 4 0 (tmp)
            IF tmp = 1
                GOSUB TryLoadProperShaders
            ENDIF
        ENDIF
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
    // TryLoadProperShaders - resolve PS_Light* em ProperShaders.asi
    // =======================================================================
TryLoadProperShaders:
    ptr = base
    ptr += JE_OFF_PS
    ptr += JE_PS_USE
    READ_MEMORY ptr 4 0 (tmp)
    IF tmp = 0
        RETURN
    ENDIF

    // ja carregou?
    ptr = base
    ptr += JE_OFF_PS
    ptr += JE_PS_ACTIVE
    READ_MEMORY ptr 4 0 (tmp)
    IF tmp = 1
        RETURN
    ENDIF

    // GET_LOADED_LIBRARY so acha se o ASI ja esta mapeado (ASI Loader).
    // Usa `model` (INT puro) como handle da DLL - NAO use `lh` (SEARCHLIGHT).
    IF NOT GET_LOADED_LIBRARY "ProperShaders.asi" (model)
        RETURN
    ENDIF

    GET_DYNAMIC_LIBRARY_PROCEDURE "PS_LightCreate" model (tmp)
    IF tmp = 0
        RETURN
    ENDIF
    ptr = base
    ptr += JE_OFF_PS
    ptr += JE_PS_CREATE
    WRITE_MEMORY ptr 4 tmp 0

    GET_DYNAMIC_LIBRARY_PROCEDURE "PS_LightDestroy" model (tmp)
    IF tmp = 0
        RETURN
    ENDIF
    ptr = base
    ptr += JE_OFF_PS
    ptr += JE_PS_DESTROY
    WRITE_MEMORY ptr 4 tmp 0

    GET_DYNAMIC_LIBRARY_PROCEDURE "PS_LightSetPosition" model (tmp)
    IF tmp = 0
        RETURN
    ENDIF
    ptr = base
    ptr += JE_OFF_PS
    ptr += JE_PS_SETPOS
    WRITE_MEMORY ptr 4 tmp 0

    ptr = base
    ptr += JE_OFF_PS
    ptr += JE_PS_ACTIVE
    WRITE_MEMORY ptr 4 1 0

    // aviso so uma vez: luz per-pixel ligada
    PRINT_STRING_NOW "~g~Jesus Enfermeiro:~w~ Proper Shaders API ativa (luz per-pixel)." 5000
    RETURN

    // =======================================================================
    // LoadIni
    // =======================================================================
LoadIni:
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

    // defaults Proper Shaders (gravados no bloco)
    ptr = base
    ptr += JE_OFF_PS
    tmp = ptr
    tmp += JE_PS_USE
    WRITE_MEMORY tmp 4 1 0
    tmp = ptr
    tmp += JE_PS_RADIUS
    fTmp = JE_DEF_PS_RADIUS
    WRITE_MEMORY tmp 4 fTmp 0
    tmp = ptr
    tmp += JE_PS_INTENSITY
    fTmp = JE_DEF_PS_INTENSITY
    WRITE_MEMORY tmp 4 fTmp 0
    tmp = ptr
    tmp += JE_PS_OFFSETZ
    fTmp = JE_DEF_PS_OFFSETZ
    WRITE_MEMORY tmp 4 fTmp 0
    tmp = ptr
    tmp += JE_PS_FOG
    WRITE_MEMORY tmp 4 1 0
    tmp = ptr
    tmp += JE_PS_TYPE
    WRITE_MEMORY tmp 4 0 0

    IF NOT DOES_FILE_EXIST "CLEO\Jesus Enfermeiro.ini"
        GOSUB DefaultModels
        PRINT_STRING_NOW "~y~Jesus Enfermeiro:~w~ crie o CLEO\Jesus Enfermeiro.ini (por enquanto so os medicos originais)." 9000
        RETURN
    ENDIF

    IF OPEN_FILE "CLEO\Jesus Enfermeiro.ini" "r" (hFile)
        section = 0
        tmp = 1
        WHILE tmp = 1
            line = base
            line += JE_OFF_LINE
            IF READ_STRING_FROM_FILE hFile line JE_READ_SIZE
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
    RETURN

ParseLine:
    GET_STRING_LENGTH $line (len)

    IF len > 2
        READ_MEMORY line 1 0 (tmp)
        IF tmp = 239
            line += 3
            len -= 3
        ENDIF
    ENDIF

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

    READ_MEMORY line 1 0 (tmp)
    IF tmp = 59
        RETURN
    ENDIF
    IF tmp = 35
        RETURN
    ENDIF

    IF tmp = 91
        GOSUB ParseSection
        RETURN
    ENDIF

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
        IF section = 2
            GOSUB AddModel
        ENDIF
        RETURN
    ENDIF

    IF section = 2
        CUT_STRING_AT line i
        GOSUB AddModel
        RETURN
    ENDIF

    ptr = line
    ptr += i
    ptr += 1
    CUT_STRING_AT line i
    GOSUB ParseSetting
    RETURN

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
    section = 2
    GOSUB AddModel
    RETURN

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

    // --- Proper Shaders ---
    IF IS_STRING_EQUAL $line "USEPROPERSHADERS" 32 0 ""
        SCAN_STRING $ptr "%d" (tmp) (id)
        ptr = base
        ptr += JE_OFF_PS
        ptr += JE_PS_USE
        WRITE_MEMORY ptr 4 id 0
    ENDIF
    IF IS_STRING_EQUAL $line "PSRADIUS" 32 0 ""
        SCAN_STRING $ptr "%f" (tmp) (fTmp)
        ptr = base
        ptr += JE_OFF_PS
        ptr += JE_PS_RADIUS
        WRITE_MEMORY ptr 4 fTmp 0
    ENDIF
    IF IS_STRING_EQUAL $line "PSINTENSITY" 32 0 ""
        SCAN_STRING $ptr "%f" (tmp) (fTmp)
        ptr = base
        ptr += JE_OFF_PS
        ptr += JE_PS_INTENSITY
        WRITE_MEMORY ptr 4 fTmp 0
    ENDIF
    IF IS_STRING_EQUAL $line "PSOFFSETZ" 32 0 ""
        SCAN_STRING $ptr "%f" (tmp) (fTmp)
        ptr = base
        ptr += JE_OFF_PS
        ptr += JE_PS_OFFSETZ
        WRITE_MEMORY ptr 4 fTmp 0
    ENDIF
    IF IS_STRING_EQUAL $line "PSFOG" 32 0 ""
        SCAN_STRING $ptr "%d" (tmp) (id)
        ptr = base
        ptr += JE_OFF_PS
        ptr += JE_PS_FOG
        WRITE_MEMORY ptr 4 id 0
    ENDIF
    IF IS_STRING_EQUAL $line "PSTYPE" 32 0 ""
        SCAN_STRING $ptr "%d" (tmp) (id)
        ptr = base
        ptr += JE_OFF_PS
        ptr += JE_PS_TYPE
        WRITE_MEMORY ptr 4 id 0
    ENDIF
    RETURN

AddModel:
    GOSUB CountLen
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
        PRINT_FORMATTED_NOW "~y~Jesus Enfermeiro:~w~ modelo desconhecido no INI: %s" (line)
    ENDIF
    RETURN

CountLen:
    GET_STRING_LENGTH $line (len)
    RETURN

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
    // ScanPeds
    // =======================================================================
ScanPeds:
    prog = 0
    WHILE GET_ANY_CHAR_NO_SAVE_RECURSIVE prog (prog ch)
        GOSUB CheckPed
    ENDWHILE
    RETURN

CheckPed:
    i = 0
    WHILE i < JE_MAX_CHARS
        GOSUB GetEntry
        READ_MEMORY entry 4 0 (tmp)
        IF tmp = ch
            RETURN
        ENDIF
        i += 1
    ENDWHILE

    GET_CHAR_MODEL ch (model)
    GOSUB ModelInList
    IF ptr = 0
        RETURN
    ENDIF

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
    // UpdateEntry
    // =======================================================================
UpdateEntry:
    GOSUB GetEntry
    READ_MEMORY entry 4 0 (ch)
    IF ch = 0
        RETURN
    ENDIF

    IF NOT DOES_CHAR_EXIST ch
        GOSUB DropEntry
        RETURN
    ENDIF
    IF IS_CHAR_DEAD ch
        GOSUB DropEntry
        RETURN
    ENDIF

    IF onlyCpr = 1
        IF NOT IS_CHAR_PLAYING_ANIM ch "CPR"
            GOSUB KillLight
            RETURN
        ENDIF
    ENDIF

    GET_CHAR_COORDINATES ch (px py pz)

    // Proper Shaders tem prioridade quando a API esta ativa
    ptr = base
    ptr += JE_OFF_PS
    ptr += JE_PS_ACTIVE
    READ_MEMORY ptr 4 0 (tmp)
    IF tmp = 1
        GOSUB UpdatePsLight
        // se a PS criou luz nesta vaga, corona opcional (Type 1/2) e pronto
        tmp = entry
        tmp += JE_ENTRY_KIND
        READ_MEMORY tmp 4 0 (id)
        IF id = JE_KIND_PS
            IF lightType = 1
            OR lightType = 2
                IF IS_CHAR_ON_SCREEN ch
                    ptr = base
                    ptr += JE_OFF_PS
                    ptr += JE_PS_OFFSETZ
                    READ_MEMORY ptr 4 0 (sz)
                    sz += pz
                    DRAW_CORONA px py sz glowSize CORONATYPE_SHINYSTAR FLARETYPE_NONE colR colG colB
                ENDIF
            ENDIF
            RETURN
        ENDIF
        // PS falhou (registry cheia etc.): cai no fallback vanilla abaixo
    ENDIF

    // fallback vanilla: searchlight +/ou corona
    IF lightType = 1
        GOSUB KillLight
    ELSE
        GOSUB UpdateSearchLight
    ENDIF

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
    // UpdatePsLight - cria/move a luz per-pixel do Proper Shaders
    // -----------------------------------------------------------------------
UpdatePsLight:
    tmp = entry
    tmp += JE_ENTRY_KIND
    READ_MEMORY tmp 4 0 (id)
    IF id = JE_KIND_PS
        // move todo frame (barato: PS_LightSetPosition)
        // handle PS fica em `model` (INT puro) - `lh` e tipado SEARCHLIGHT
        tmp = entry
        tmp += JE_ENTRY_LIGHT
        READ_MEMORY tmp 4 0 (model)
        IF model = 0
            GOSUB CreatePsLight
            RETURN
        ENDIF
        ptr = base
        ptr += JE_OFF_PS
        ptr += JE_PS_OFFSETZ
        READ_MEMORY ptr 4 0 (sz)
        sz += pz
        ptr = base
        ptr += JE_OFF_PS
        ptr += JE_PS_SETPOS
        READ_MEMORY ptr 4 0 (tmp)
        // PS_LightSetPosition(handle, x, y, z) __cdecl
        CALL_FUNCTION_RETURN tmp 4 4 (model px py sz) (id)
        IF id = 0
            // handle ficou invalido (registry limpou / reload): recria
            tmp = entry
            tmp += JE_ENTRY_KIND
            WRITE_MEMORY tmp 4 0 0
            GOSUB CreatePsLight
        ENDIF
        RETURN
    ENDIF

    // se tinha searchlight antiga, apaga antes de criar PS
    IF id = JE_KIND_SEARCHLIGHT
        GOSUB KillSearchLightOnly
    ENDIF
    GOSUB CreatePsLight
    RETURN

CreatePsLight:
    // monta PS_LightDesc em JE_OFF_DESC
    line = base
    line += JE_OFF_DESC

    // zera os 88 bytes
    i = 0
    WHILE i < JE_DESC_SIZE
        ptr = line
        ptr += i
        WRITE_MEMORY ptr 1 0 0
        i += 1
    ENDWHILE

    // structSize
    ptr = line
    ptr += JE_DESC_STRUCTSIZE
    WRITE_MEMORY ptr 4 JE_DESC_SIZE 0

    // type (0 point / 1 spot)
    tmp = base
    tmp += JE_OFF_PS
    tmp += JE_PS_TYPE
    READ_MEMORY tmp 4 0 (id)
    ptr = line
    ptr += JE_DESC_TYPE
    WRITE_MEMORY ptr 4 id 0

    // position
    ptr = base
    ptr += JE_OFF_PS
    ptr += JE_PS_OFFSETZ
    READ_MEMORY ptr 4 0 (sz)
    sz += pz
    ptr = line
    ptr += JE_DESC_POSX
    WRITE_MEMORY ptr 4 px 0
    ptr += 4
    WRITE_MEMORY ptr 4 py 0
    ptr += 4
    WRITE_MEMORY ptr 4 sz 0

    // direction (0,0,-1) - util pro spot
    ptr = line
    ptr += JE_DESC_DIRZ
    fTmp = JE_FLOAT_NEG1
    WRITE_MEMORY ptr 4 fTmp 0

    // radius
    ptr = base
    ptr += JE_OFF_PS
    ptr += JE_PS_RADIUS
    READ_MEMORY ptr 4 0 (fTmp)
    ptr = line
    ptr += JE_DESC_RADIUS
    WRITE_MEMORY ptr 4 fTmp 0

    // color 0..1 a partir de colR/G/B
    fTmp =# colR
    fTmp /= JE_COLOR_DIV
    ptr = line
    ptr += JE_DESC_COLORR
    WRITE_MEMORY ptr 4 fTmp 0
    fTmp =# colG
    fTmp /= JE_COLOR_DIV
    ptr = line
    ptr += JE_DESC_COLORG
    WRITE_MEMORY ptr 4 fTmp 0
    fTmp =# colB
    fTmp /= JE_COLOR_DIV
    ptr = line
    ptr += JE_DESC_COLORB
    WRITE_MEMORY ptr 4 fTmp 0

    // intensity
    ptr = base
    ptr += JE_OFF_PS
    ptr += JE_PS_INTENSITY
    READ_MEMORY ptr 4 0 (fTmp)
    ptr = line
    ptr += JE_DESC_INTENSITY
    WRITE_MEMORY ptr 4 fTmp 0

    // spotAngle default
    ptr = line
    ptr += JE_DESC_SPOTANGLE
    fTmp = JE_DEF_SPOTANGLE
    WRITE_MEMORY ptr 4 fTmp 0

    // fogMode + fogIntensity=1
    ptr = base
    ptr += JE_OFF_PS
    ptr += JE_PS_FOG
    READ_MEMORY ptr 4 0 (id)
    ptr = line
    ptr += JE_DESC_FOGMODE
    WRITE_MEMORY ptr 4 id 0
    ptr = line
    ptr += JE_DESC_FOGINT
    fTmp = JE_FLOAT_ONE
    WRITE_MEMORY ptr 4 fTmp 0

    // beamIntensity = 1 (resto ja zero)
    ptr = line
    ptr += JE_DESC_BEAMINT
    WRITE_MEMORY ptr 4 fTmp 0

    // chama PS_LightCreate(&desc) - retorno em `id` (INT puro; `lh` e SEARCHLIGHT)
    ptr = base
    ptr += JE_OFF_PS
    ptr += JE_PS_CREATE
    READ_MEMORY ptr 4 0 (tmp)
    CALL_FUNCTION_RETURN tmp 1 1 (line) (id)
    IF id = 0
        // registry cheia (64 luzes) ou desc rejeitada
        RETURN
    ENDIF

    ptr = entry
    ptr += JE_ENTRY_LIGHT
    WRITE_MEMORY ptr 4 id 0
    ptr = entry
    ptr += JE_ENTRY_KIND
    WRITE_MEMORY ptr 4 JE_KIND_PS 0
    RETURN

    // -----------------------------------------------------------------------
    // UpdateSearchLight (fallback vanilla)
    // -----------------------------------------------------------------------
UpdateSearchLight:
    tmp = entry
    tmp += JE_ENTRY_KIND
    READ_MEMORY tmp 4 0 (id)
    IF id = JE_KIND_NONE
        GOSUB CreateSearchLight
        RETURN
    ENDIF
    IF id = JE_KIND_PS
        // nao misturar: se a API caiu no meio do jogo, mata PS e cria SL
        GOSUB KillLight
        GOSUB CreateSearchLight
        RETURN
    ENDIF

    tmp = entry
    tmp += JE_ENTRY_LIGHT
    READ_MEMORY tmp 4 0 (lh)
    IF NOT DOES_SEARCHLIGHT_EXIST lh
        GOSUB CreateSearchLight
        RETURN
    ENDIF

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
        GOSUB CreateSearchLight
    ENDIF
    RETURN

CreateSearchLight:
    READ_MEMORY JE_LIGHT_COUNT_ADDR 2 0 (tmp)
    IF tmp < 0
        RETURN
    ENDIF
    IF tmp >= maxLights
        RETURN
    ENDIF

    sz = pz
    sz += height
    CREATE_SEARCHLIGHT px py sz px py pz rad1 rad2 (lh)
    IF lh < 0
        RETURN
    ENDIF

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
    WRITE_MEMORY ptr 4 JE_KIND_SEARCHLIGHT 0
    RETURN

    // -----------------------------------------------------------------------
    // KillLight - apaga searchlight OU luz PS desta vaga
    // -----------------------------------------------------------------------
KillLight:
    ptr = entry
    ptr += JE_ENTRY_KIND
    READ_MEMORY ptr 4 0 (id)
    IF id = JE_KIND_NONE
        RETURN
    ENDIF
    WRITE_MEMORY ptr 4 0 0

    IF id = JE_KIND_PS
        // handle PS e INT opaco - le direto em `model` (nao em `lh`/SEARCHLIGHT)
        ptr = entry
        ptr += JE_ENTRY_LIGHT
        READ_MEMORY ptr 4 0 (model)
        IF model > 0
            tmp = base
            tmp += JE_OFF_PS
            tmp += JE_PS_DESTROY
            READ_MEMORY tmp 4 0 (ptr)
            IF ptr > 0
                CALL_FUNCTION_RETURN ptr 1 1 (model) (tmp)
            ENDIF
        ENDIF
        RETURN
    ENDIF

    // searchlight
    ptr = entry
    ptr += JE_ENTRY_LIGHT
    READ_MEMORY ptr 4 0 (lh)
    IF DOES_SEARCHLIGHT_EXIST lh
        DELETE_SEARCHLIGHT lh
    ENDIF
    RETURN

KillSearchLightOnly:
    ptr = entry
    ptr += JE_ENTRY_KIND
    READ_MEMORY ptr 4 0 (id)
    IF id = JE_KIND_SEARCHLIGHT
        WRITE_MEMORY ptr 4 0 0
        ptr = entry
        ptr += JE_ENTRY_LIGHT
        READ_MEMORY ptr 4 0 (lh)
        IF DOES_SEARCHLIGHT_EXIST lh
            DELETE_SEARCHLIGHT lh
        ENDIF
    ENDIF
    RETURN

DropEntry:
    GOSUB KillLight
    WRITE_MEMORY entry 4 0 0
    RETURN

GetEntry:
    tmp = i
    tmp = tmp * JE_ENTRY_SIZE
    entry = base
    entry += JE_OFF_TABLE
    entry += tmp
    RETURN
}
SCRIPT_END
