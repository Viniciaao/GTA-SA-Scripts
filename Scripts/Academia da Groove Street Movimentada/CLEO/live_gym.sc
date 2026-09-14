/*
    ============================================================================
    Academia da Ganton / Grove Street Movimentada (Remastered & Optimized)
    ----------------------------------------------------------------------------
    Autor original: SanKing (2013)
    Refatoração & Otimização: Padrão GTA3script para CLEO 4.4.4 e CLEO+ (2026)
    ----------------------------------------------------------------------------
    Novas funcionalidades e correções aplicadas:
    1. Chance aleatória individual para cada pedestre:
       - Cada frequentador (corredores, halterofilistas, boxeadores e cliente)
         possui probabilidade independente de aparecer a cada visita.
       - A academia nunca fica 100% deserta, mas nunca repete a mesma
         combinação exata em todas as idas.
       - Halteres e animações são carregados somente quando os respectivos
         atletas forem sorteados.
    2. Sistema dinâmico de clima / chuva para o exterior:
       - Se estiver chovendo lá fora, o pedestre fumante externo NÃO é gerado.
       - Se começar a chover enquanto ele já estiver na calçada, o mod cancela
         a animação de cigarro, atribui a tarefa de perambular (TASK_WANDER_STANDARD)
         e o libera imediatamente para a IA do jogo sair andando pela rua.
    3. Reescrita completa em GTA3script moderno (sintaxe oficial da Rockstar).
    4. Correção do gatilho de interior com validação 3D de Ganton (evita falsos
       disparos na Academia Cobra em SF, Atrium e apartamento do B Dup).
    5. Correção do objeto 1@ (banco fora do mapa em Y=755.388) e alinhamento
       perfeito dos 4 bancos de bar ao longo do balcão.
    6. Correção do memory leak do ped 17@ (que nunca era deletado no mod antigo).
    7. Carregamento assíncrono via streaming nativo (sem freeze de LOAD_ALL_MODELS_NOW).
    8. Remoção da tranca forçada de 10 segundos na porta (switch_entry_exit).
    9. Sistema dinâmico de pânico: halteres caem com física dinâmica caso haja
       tiros, agressão ou mira, e pedestres fogem de forma inteligente.
    10. Limpeza completa em caso de morte, prisão ou saída da academia.
    ============================================================================
*/

SCRIPT_START
{
    NOP

    SCRIPT_NAME livegym

    // IDs dos modelos de objetos da academia (prop models)
    CONST_INT MODEL_KMB_DUMBBELL_R  3071
    CONST_INT MODEL_CJ_BARSTOOL     1805
    CONST_INT MODEL_GYM_TREADMILL   2627
    CONST_INT MODEL_GYM_BENCH1      2629
    CONST_INT MODEL_CJ_4WAY_CLOTHES 2390

    // Variáveis de controle de estado
    LVAR_INT scplayer interior bGymSpawned bPanicked
    LVAR_INT targetChar tempVar
    LVAR_FLOAT fRain

    // Variáveis dos Pedestres (Chars)
    LVAR_INT pedJogger1 pedJogger2
    LVAR_INT pedLifter1 pedLifter2
    LVAR_INT pedBoxer1 pedBoxer2
    LVAR_INT pedShopper pedSmoker

    // Variáveis dos Objetos (Props)
    LVAR_INT objStool1 objStool2 objStool3 objStool4
    LVAR_INT objTreadmill1 objTreadmill2
    LVAR_INT objBench1 objBench2
    LVAR_INT objRack
    LVAR_INT objDumbbell1 objDumbbell2

    GET_PLAYER_CHAR 0 scplayer
    bGymSpawned = 0
    bPanicked = 0
    pedSmoker = 0
    pedJogger1 = 0
    pedJogger2 = 0
    pedLifter1 = 0
    pedLifter2 = 0
    pedBoxer1 = 0
    pedBoxer2 = 0
    pedShopper = 0
    objDumbbell1 = 0
    objDumbbell2 = 0

main_loop:
    WAIT 250

    // Limpeza de segurança se o CJ morrer, for preso ou desativado
    IF NOT IS_PLAYER_PLAYING 0
        IF bGymSpawned = 1
            GOSUB CleanupGym
        ENDIF
        GOTO main_loop
    ENDIF

    // Monitoramento do pedestre externo em relação à chuva
    IF DOES_CHAR_EXIST pedSmoker
        IF NOT IS_CHAR_DEAD pedSmoker
            READ_MEMORY 0xC81324 4 FALSE (fRain)
            READ_MEMORY 0xC81318 2 FALSE (tempVar)
            IF fRain > 0.05
            OR tempVar = 8
            OR tempVar = 16
                // Começou a chover enquanto ele estava lá!
                // Libera ele do mod para sair andando pela rua
                CLEAR_CHAR_TASKS pedSmoker
                TASK_WANDER_STANDARD pedSmoker
                MARK_CHAR_AS_NO_LONGER_NEEDED pedSmoker
                pedSmoker = 0
            ELSE
                // Se o jogador se afastar muito da academia, libera o ped da memória
                IF NOT LOCATE_CHAR_ANY_MEANS_3D scplayer 2228.751 -1720.5699 13.5531 70.0 70.0 30.0 FALSE
                    MARK_CHAR_AS_NO_LONGER_NEEDED pedSmoker
                    pedSmoker = 0
                ENDIF
            ENDIF
        ELSE
            pedSmoker = 0
        ENDIF
    ENDIF

    GET_AREA_VISIBLE interior

    // Verifica se o jogador está especificamente na Academia de Ganton
    // (Interior 5 e dentro do raio de coordenadas da academia)
    IF interior = 5
    AND LOCATE_CHAR_ANY_MEANS_3D scplayer 765.0 5.0 1000.0 35.0 35.0 20.0 FALSE
        IF bGymSpawned = 0
            GOSUB SpawnGym
        ELSE
            GOSUB UpdateGym
        ENDIF
    ELSE
        // Jogador saiu da academia
        IF bGymSpawned = 1
            GOSUB CleanupGym
            GOSUB SpawnOutsideSmoker
        ENDIF
    ENDIF

    GOTO main_loop

/* ========================================================================= */
/* SPAWN DOS OCUPANTES E EQUIPAMENTOS DA ACADEMIA                            */
/* ========================================================================= */
SpawnGym:
    // Reseta identificadores dos peds e halteres
    pedJogger1 = 0
    pedJogger2 = 0
    pedLifter1 = 0
    pedLifter2 = 0
    pedBoxer1 = 0
    pedBoxer2 = 0
    pedShopper = 0
    objDumbbell1 = 0
    objDumbbell2 = 0

    // Sorteio de chance aleatória individual para cada pedestre
    // (Marcamos com -1 temporariamente os pedestres sorteados para spawnar)
    GENERATE_RANDOM_INT_IN_RANGE 0 100 tempVar
    IF tempVar < 65
        pedJogger1 = -1
    ENDIF

    GENERATE_RANDOM_INT_IN_RANGE 0 100 tempVar
    IF tempVar < 60
        pedJogger2 = -1
    ENDIF

    GENERATE_RANDOM_INT_IN_RANGE 0 100 tempVar
    IF tempVar < 70
        pedLifter1 = -1
    ENDIF

    GENERATE_RANDOM_INT_IN_RANGE 0 100 tempVar
    IF tempVar < 65
        pedLifter2 = -1
    ENDIF

    GENERATE_RANDOM_INT_IN_RANGE 0 100 tempVar
    IF tempVar < 65
        pedBoxer1 = -1
    ENDIF

    GENERATE_RANDOM_INT_IN_RANGE 0 100 tempVar
    IF tempVar < 60
        pedBoxer2 = -1
    ENDIF

    GENERATE_RANDOM_INT_IN_RANGE 0 100 tempVar
    IF tempVar < 55
        pedShopper = -1
    ENDIF

    // Garante que a academia nunca esteja completamente vazia
    IF pedJogger1 = 0
    AND pedJogger2 = 0
    AND pedLifter1 = 0
    AND pedLifter2 = 0
    AND pedBoxer1 = 0
    AND pedBoxer2 = 0
    AND pedShopper = 0
        pedLifter1 = -1
    ENDIF

    // Requisita modelos dos objetos fixos do cenário
    REQUEST_MODEL MODEL_CJ_BARSTOOL
    REQUEST_MODEL MODEL_GYM_TREADMILL
    REQUEST_MODEL MODEL_GYM_BENCH1
    REQUEST_MODEL MODEL_CJ_4WAY_CLOTHES

    // Requisita modelos e animações condicionados ao sorteio desta visita
    IF pedLifter1 = -1
    OR pedLifter2 = -1
        REQUEST_ANIMATION "FREEWEIGHTS"
        REQUEST_MODEL MODEL_KMB_DUMBBELL_R
    ENDIF

    IF pedJogger1 = -1
    OR pedJogger2 = -1
    OR pedBoxer1 = -1
    OR pedBoxer2 = -1
        REQUEST_ANIMATION "GYMNASIUM"
    ENDIF

    IF pedShopper = -1
        REQUEST_ANIMATION "COP_AMBIENT"
        REQUEST_MODEL BMYST
    ENDIF

    IF pedJogger1 = -1
        REQUEST_MODEL WMYJG
    ENDIF
    IF pedJogger2 = -1
        REQUEST_MODEL BMYCR
    ENDIF
    IF pedLifter1 = -1
        REQUEST_MODEL BMYDRUG
    ENDIF
    IF pedLifter2 = -1
        REQUEST_MODEL BMYPOL2
    ENDIF
    IF pedBoxer1 = -1
        REQUEST_MODEL BMYDJ
    ENDIF
    IF pedBoxer2 = -1
        REQUEST_MODEL HMYDRUG
    ENDIF

    // Aguarda carregamento dos modelos fixos de objetos
    WHILE NOT HAS_MODEL_LOADED MODEL_CJ_BARSTOOL
       OR NOT HAS_MODEL_LOADED MODEL_GYM_TREADMILL
       OR NOT HAS_MODEL_LOADED MODEL_GYM_BENCH1
       OR NOT HAS_MODEL_LOADED MODEL_CJ_4WAY_CLOTHES
        WAIT 0
        IF NOT IS_PLAYER_PLAYING 0
            RETURN
        ENDIF
        GET_AREA_VISIBLE interior
        IF NOT interior = 5
            RETURN
        ENDIF
    ENDWHILE

    // Aguarda carregamento condicional dos halteres e animação freeweights
    IF pedLifter1 = -1
    OR pedLifter2 = -1
        WHILE NOT HAS_MODEL_LOADED MODEL_KMB_DUMBBELL_R
           OR NOT HAS_ANIMATION_LOADED "FREEWEIGHTS"
            WAIT 0
            IF NOT IS_PLAYER_PLAYING 0
                RETURN
            ENDIF
            GET_AREA_VISIBLE interior
            IF NOT interior = 5
                RETURN
            ENDIF
        ENDWHILE
    ENDIF

    // Aguarda animação gymnasium se necessário
    IF pedJogger1 = -1
    OR pedJogger2 = -1
    OR pedBoxer1 = -1
    OR pedBoxer2 = -1
        WHILE NOT HAS_ANIMATION_LOADED "GYMNASIUM"
            WAIT 0
            IF NOT IS_PLAYER_PLAYING 0
                RETURN
            ENDIF
            GET_AREA_VISIBLE interior
            IF NOT interior = 5
                RETURN
            ENDIF
        ENDWHILE
    ENDIF

    // Aguarda animação cop ambient e modelo BMYST se necessário
    IF pedShopper = -1
        WHILE NOT HAS_MODEL_LOADED BMYST
           OR NOT HAS_ANIMATION_LOADED "COP_AMBIENT"
            WAIT 0
            IF NOT IS_PLAYER_PLAYING 0
                RETURN
            ENDIF
            GET_AREA_VISIBLE interior
            IF NOT interior = 5
                RETURN
            ENDIF
        ENDWHILE
    ENDIF

    // Aguarda modelos dos peds que foram sorteados
    IF pedJogger1 = -1
        WHILE NOT HAS_MODEL_LOADED WMYJG
            WAIT 0
            IF NOT IS_PLAYER_PLAYING 0
                RETURN
            ENDIF
            GET_AREA_VISIBLE interior
            IF NOT interior = 5
                RETURN
            ENDIF
        ENDWHILE
    ENDIF

    IF pedJogger2 = -1
        WHILE NOT HAS_MODEL_LOADED BMYCR
            WAIT 0
            IF NOT IS_PLAYER_PLAYING 0
                RETURN
            ENDIF
            GET_AREA_VISIBLE interior
            IF NOT interior = 5
                RETURN
            ENDIF
        ENDWHILE
    ENDIF

    IF pedLifter1 = -1
        WHILE NOT HAS_MODEL_LOADED BMYDRUG
            WAIT 0
            IF NOT IS_PLAYER_PLAYING 0
                RETURN
            ENDIF
            GET_AREA_VISIBLE interior
            IF NOT interior = 5
                RETURN
            ENDIF
        ENDWHILE
    ENDIF

    IF pedLifter2 = -1
        WHILE NOT HAS_MODEL_LOADED BMYPOL2
            WAIT 0
            IF NOT IS_PLAYER_PLAYING 0
                RETURN
            ENDIF
            GET_AREA_VISIBLE interior
            IF NOT interior = 5
                RETURN
            ENDIF
        ENDWHILE
    ENDIF

    IF pedBoxer1 = -1
        WHILE NOT HAS_MODEL_LOADED BMYDJ
            WAIT 0
            IF NOT IS_PLAYER_PLAYING 0
                RETURN
            ENDIF
            GET_AREA_VISIBLE interior
            IF NOT interior = 5
                RETURN
            ENDIF
        ENDWHILE
    ENDIF

    IF pedBoxer2 = -1
        WHILE NOT HAS_MODEL_LOADED HMYDRUG
            WAIT 0
            IF NOT IS_PLAYER_PLAYING 0
                RETURN
            ENDIF
            GET_AREA_VISIBLE interior
            IF NOT interior = 5
                RETURN
            ENDIF
        ENDWHILE
    ENDIF

    // ---------------------------------------------------------------------
    // 1. Bancos do balcão (Objetos fixos do cenário)
    // ---------------------------------------------------------------------
    CREATE_OBJECT MODEL_CJ_BARSTOOL 756.129 4.8 999.8 objStool1
    SET_OBJECT_AREA_VISIBLE objStool1 5

    CREATE_OBJECT MODEL_CJ_BARSTOOL 756.129 5.95 999.8 objStool2
    SET_OBJECT_AREA_VISIBLE objStool2 5

    CREATE_OBJECT MODEL_CJ_BARSTOOL 756.129 7.1 999.8 objStool3
    SET_OBJECT_AREA_VISIBLE objStool3 5

    CREATE_OBJECT MODEL_CJ_BARSTOOL 756.129 8.25 999.8 objStool4
    SET_OBJECT_AREA_VISIBLE objStool4 5

    // ---------------------------------------------------------------------
    // 2. Esteiras e Corredores (Joggers)
    // ---------------------------------------------------------------------
    CREATE_OBJECT MODEL_GYM_TREADMILL 773.115 10.9414 999.672 objTreadmill1
    SET_OBJECT_HEADING objTreadmill1 270.0
    SET_OBJECT_AREA_VISIBLE objTreadmill1 5

    CREATE_OBJECT MODEL_GYM_TREADMILL 773.115 12.3276 999.672 objTreadmill2
    SET_OBJECT_HEADING objTreadmill2 270.0
    SET_OBJECT_AREA_VISIBLE objTreadmill2 5

    IF pedJogger1 = -1
        CREATE_CHAR PEDTYPE_CIVMALE WMYJG 771.4 10.9204 1000.849 pedJogger1
        SET_CHAR_HEADING pedJogger1 270.0
        SET_CHAR_AREA_VISIBLE pedJogger1 5
        SET_CHAR_STAY_IN_SAME_PLACE pedJogger1 TRUE
        TASK_PLAY_ANIM pedJogger1 "GYM_TREAD_JOG" "GYMNASIUM" 4.0 TRUE FALSE FALSE FALSE -1
    ELSE
        pedJogger1 = 0
    ENDIF

    IF pedJogger2 = -1
        CREATE_CHAR PEDTYPE_CIVMALE BMYCR 771.4 12.3027 1000.849 pedJogger2
        SET_CHAR_HEADING pedJogger2 270.0
        SET_CHAR_AREA_VISIBLE pedJogger2 5
        SET_CHAR_STAY_IN_SAME_PLACE pedJogger2 TRUE
        TASK_PLAY_ANIM pedJogger2 "GYM_TREAD_JOG" "GYMNASIUM" 4.0 TRUE FALSE FALSE FALSE -1
    ELSE
        pedJogger2 = 0
    ENDIF

    // ---------------------------------------------------------------------
    // 3. Supinos, Halteres e Atletas de Musculação
    // ---------------------------------------------------------------------
    CREATE_OBJECT MODEL_GYM_BENCH1 771.823 7.4149 999.672 objBench1
    SET_OBJECT_AREA_VISIBLE objBench1 5

    CREATE_OBJECT MODEL_GYM_BENCH1 773.95 7.4149 999.672 objBench2
    SET_OBJECT_AREA_VISIBLE objBench2 5

    IF pedLifter1 = -1
        CREATE_OBJECT MODEL_KMB_DUMBBELL_R 0.0 0.0 0.0 objDumbbell1
        SET_OBJECT_AREA_VISIBLE objDumbbell1 5
        SET_OBJECT_HEADING objDumbbell1 90.0

        CREATE_CHAR PEDTYPE_CIVMALE BMYDRUG 771.4566 7.3739 1000.71 pedLifter1
        SET_CHAR_HEADING pedLifter1 0.0
        SET_CHAR_AREA_VISIBLE pedLifter1 5
        SET_CHAR_STAY_IN_SAME_PLACE pedLifter1 TRUE
        TASK_PICK_UP_OBJECT pedLifter1 objDumbbell1 0.04 0.0 -0.02 PED_HANDR HOLD_ORIENTATE_BONE_FULL "NULL" "NULL" -1
        TASK_PLAY_ANIM pedLifter1 "GYM_BARBELL" "FREEWEIGHTS" 4.0 TRUE FALSE FALSE FALSE -1
    ELSE
        pedLifter1 = 0
        objDumbbell1 = 0
    ENDIF

    IF pedLifter2 = -1
        CREATE_OBJECT MODEL_KMB_DUMBBELL_R 0.0 0.0 0.0 objDumbbell2
        SET_OBJECT_AREA_VISIBLE objDumbbell2 5
        SET_OBJECT_HEADING objDumbbell2 90.0

        CREATE_CHAR PEDTYPE_CIVMALE BMYPOL2 773.6576 7.4052 1000.709 pedLifter2
        SET_CHAR_HEADING pedLifter2 0.0
        SET_CHAR_AREA_VISIBLE pedLifter2 5
        SET_CHAR_STAY_IN_SAME_PLACE pedLifter2 TRUE
        TASK_PICK_UP_OBJECT pedLifter2 objDumbbell2 0.04 0.0 -0.02 PED_HANDR HOLD_ORIENTATE_BONE_FULL "NULL" "NULL" -1
        TASK_PLAY_ANIM pedLifter2 "GYM_BARBELL" "FREEWEIGHTS" 4.0 TRUE FALSE FALSE FALSE -1
    ELSE
        pedLifter2 = 0
        objDumbbell2 = 0
    ENDIF

    // ---------------------------------------------------------------------
    // 4. Boxeadores / Sparring
    // ---------------------------------------------------------------------
    IF pedBoxer1 = -1
        CREATE_CHAR PEDTYPE_CIVMALE BMYDJ 767.2722 -2.4724 1000.719 pedBoxer1
        SET_CHAR_HEADING pedBoxer1 185.9322
        SET_CHAR_AREA_VISIBLE pedBoxer1 5
        SET_CHAR_STAY_IN_SAME_PLACE pedBoxer1 TRUE
        TASK_PLAY_ANIM pedBoxer1 "GYM_SHADOWBOX" "GYMNASIUM" 4.0 TRUE FALSE FALSE FALSE -1
    ELSE
        pedBoxer1 = 0
    ENDIF

    IF pedBoxer2 = -1
        CREATE_CHAR PEDTYPE_CIVMALE HMYDRUG 769.6318 -2.4529 1000.724 pedBoxer2
        SET_CHAR_HEADING pedBoxer2 158.0452
        SET_CHAR_AREA_VISIBLE pedBoxer2 5
        SET_CHAR_STAY_IN_SAME_PLACE pedBoxer2 TRUE
        TASK_PLAY_ANIM pedBoxer2 "GYM_SHADOWBOX" "GYMNASIUM" 4.0 TRUE FALSE FALSE FALSE -1
    ELSE
        pedBoxer2 = 0
    ENDIF

    // ---------------------------------------------------------------------
    // 5. Arara de Roupas e Cliente
    // ---------------------------------------------------------------------
    CREATE_OBJECT MODEL_CJ_4WAY_CLOTHES 755.392 9.191 1000.6 objRack
    SET_OBJECT_HEADING objRack 270.0
    SET_OBJECT_AREA_VISIBLE objRack 5

    IF pedShopper = -1
        CREATE_CHAR PEDTYPE_CIVMALE BMYST 756.2289 8.8118 1000.7 pedShopper
        SET_CHAR_HEADING pedShopper 67.2502
        SET_CHAR_AREA_VISIBLE pedShopper 5
        SET_CHAR_STAY_IN_SAME_PLACE pedShopper TRUE
        TASK_PLAY_ANIM pedShopper "COPBROWSE_NOD" "COP_AMBIENT" 4.0 TRUE FALSE FALSE FALSE -1
    ELSE
        pedShopper = 0
    ENDIF

    // Libera os modelos da memória de streaming
    MARK_MODEL_AS_NO_LONGER_NEEDED MODEL_CJ_BARSTOOL
    MARK_MODEL_AS_NO_LONGER_NEEDED MODEL_GYM_TREADMILL
    MARK_MODEL_AS_NO_LONGER_NEEDED MODEL_GYM_BENCH1
    MARK_MODEL_AS_NO_LONGER_NEEDED MODEL_CJ_4WAY_CLOTHES
    IF DOES_OBJECT_EXIST objDumbbell1
    OR DOES_OBJECT_EXIST objDumbbell2
        MARK_MODEL_AS_NO_LONGER_NEEDED MODEL_KMB_DUMBBELL_R
    ENDIF
    IF DOES_CHAR_EXIST pedJogger1
        MARK_MODEL_AS_NO_LONGER_NEEDED WMYJG
    ENDIF
    IF DOES_CHAR_EXIST pedJogger2
        MARK_MODEL_AS_NO_LONGER_NEEDED BMYCR
    ENDIF
    IF DOES_CHAR_EXIST pedLifter1
        MARK_MODEL_AS_NO_LONGER_NEEDED BMYDRUG
    ENDIF
    IF DOES_CHAR_EXIST pedLifter2
        MARK_MODEL_AS_NO_LONGER_NEEDED BMYPOL2
    ENDIF
    IF DOES_CHAR_EXIST pedBoxer1
        MARK_MODEL_AS_NO_LONGER_NEEDED BMYDJ
    ENDIF
    IF DOES_CHAR_EXIST pedBoxer2
        MARK_MODEL_AS_NO_LONGER_NEEDED HMYDRUG
    ENDIF
    IF DOES_CHAR_EXIST pedShopper
        MARK_MODEL_AS_NO_LONGER_NEEDED BMYST
    ENDIF

    bGymSpawned = 1
    bPanicked = 0
    RETURN

/* ========================================================================= */
/* MONITORAMENTO DE COMBATE / AMEAÇA                                         */
/* ========================================================================= */
UpdateGym:
    IF bPanicked = 1
        RETURN
    ENDIF

    // Detecta se CJ atirou dentro da academia
    IF IS_CHAR_SHOOTING scplayer
        GOSUB TriggerPanic
        RETURN
    ENDIF

    // Detecta se CJ está mirando em algum dos pedestres da academia
    IF GET_CHAR_PLAYER_IS_TARGETING 0 targetChar
        IF DOES_CHAR_EXIST pedJogger1
        AND targetChar = pedJogger1
            GOSUB TriggerPanic
            RETURN
        ENDIF
        IF DOES_CHAR_EXIST pedJogger2
        AND targetChar = pedJogger2
            GOSUB TriggerPanic
            RETURN
        ENDIF
        IF DOES_CHAR_EXIST pedLifter1
        AND targetChar = pedLifter1
            GOSUB TriggerPanic
            RETURN
        ENDIF
        IF DOES_CHAR_EXIST pedLifter2
        AND targetChar = pedLifter2
            GOSUB TriggerPanic
            RETURN
        ENDIF
        IF DOES_CHAR_EXIST pedBoxer1
        AND targetChar = pedBoxer1
            GOSUB TriggerPanic
            RETURN
        ENDIF
        IF DOES_CHAR_EXIST pedBoxer2
        AND targetChar = pedBoxer2
            GOSUB TriggerPanic
            RETURN
        ENDIF
        IF DOES_CHAR_EXIST pedShopper
        AND targetChar = pedShopper
            GOSUB TriggerPanic
            RETURN
        ENDIF
    ENDIF

    // Detecta se algum pedestre foi atacado ou ferido por CJ
    IF DOES_CHAR_EXIST pedJogger1
        IF HAS_CHAR_BEEN_DAMAGED_BY_CHAR pedJogger1 scplayer
            GOSUB TriggerPanic
            RETURN
        ENDIF
    ENDIF
    IF DOES_CHAR_EXIST pedJogger2
        IF HAS_CHAR_BEEN_DAMAGED_BY_CHAR pedJogger2 scplayer
            GOSUB TriggerPanic
            RETURN
        ENDIF
    ENDIF
    IF DOES_CHAR_EXIST pedLifter1
        IF HAS_CHAR_BEEN_DAMAGED_BY_CHAR pedLifter1 scplayer
            GOSUB TriggerPanic
            RETURN
        ENDIF
    ENDIF
    IF DOES_CHAR_EXIST pedLifter2
        IF HAS_CHAR_BEEN_DAMAGED_BY_CHAR pedLifter2 scplayer
            GOSUB TriggerPanic
            RETURN
        ENDIF
    ENDIF
    IF DOES_CHAR_EXIST pedBoxer1
        IF HAS_CHAR_BEEN_DAMAGED_BY_CHAR pedBoxer1 scplayer
            GOSUB TriggerPanic
            RETURN
        ENDIF
    ENDIF
    IF DOES_CHAR_EXIST pedBoxer2
        IF HAS_CHAR_BEEN_DAMAGED_BY_CHAR pedBoxer2 scplayer
            GOSUB TriggerPanic
            RETURN
        ENDIF
    ENDIF
    IF DOES_CHAR_EXIST pedShopper
        IF HAS_CHAR_BEEN_DAMAGED_BY_CHAR pedShopper scplayer
            GOSUB TriggerPanic
            RETURN
        ENDIF
    ENDIF

    RETURN

/* ========================================================================= */
/* RESPOSTA DINÂMICA AO PÂNICO                                               */
/* ========================================================================= */
TriggerPanic:
    bPanicked = 1

    // Solta e destaca os halteres com física dinâmica
    IF DOES_OBJECT_EXIST objDumbbell1
        IF DOES_CHAR_EXIST pedLifter1
            DROP_OBJECT pedLifter1 TRUE
        ENDIF
        DETACH_OBJECT objDumbbell1 0.0 0.0 0.0 FALSE
        SET_OBJECT_DYNAMIC objDumbbell1 TRUE
    ENDIF

    IF DOES_OBJECT_EXIST objDumbbell2
        IF DOES_CHAR_EXIST pedLifter2
            DROP_OBJECT pedLifter2 TRUE
        ENDIF
        DETACH_OBJECT objDumbbell2 0.0 0.0 0.0 FALSE
        SET_OBJECT_DYNAMIC objDumbbell2 TRUE
    ENDIF

    // Libera os peds para fugir de forma inteligente e passa o controle para a IA
    IF DOES_CHAR_EXIST pedJogger1
        SET_CHAR_STAY_IN_SAME_PLACE pedJogger1 FALSE
        IF NOT IS_CHAR_DEAD pedJogger1
            CLEAR_CHAR_TASKS pedJogger1
            TASK_SMART_FLEE_CHAR pedJogger1 scplayer 45.0 15000
        ENDIF
        MARK_CHAR_AS_NO_LONGER_NEEDED pedJogger1
    ENDIF

    IF DOES_CHAR_EXIST pedJogger2
        SET_CHAR_STAY_IN_SAME_PLACE pedJogger2 FALSE
        IF NOT IS_CHAR_DEAD pedJogger2
            CLEAR_CHAR_TASKS pedJogger2
            TASK_SMART_FLEE_CHAR pedJogger2 scplayer 45.0 15000
        ENDIF
        MARK_CHAR_AS_NO_LONGER_NEEDED pedJogger2
    ENDIF

    IF DOES_CHAR_EXIST pedLifter1
        SET_CHAR_STAY_IN_SAME_PLACE pedLifter1 FALSE
        IF NOT IS_CHAR_DEAD pedLifter1
            CLEAR_CHAR_TASKS pedLifter1
            TASK_SAY pedLifter1 CONTEXT_GLOBAL_GUN_RUN
            TASK_SMART_FLEE_CHAR pedLifter1 scplayer 45.0 15000
        ENDIF
        MARK_CHAR_AS_NO_LONGER_NEEDED pedLifter1
    ENDIF

    IF DOES_CHAR_EXIST pedLifter2
        SET_CHAR_STAY_IN_SAME_PLACE pedLifter2 FALSE
        IF NOT IS_CHAR_DEAD pedLifter2
            CLEAR_CHAR_TASKS pedLifter2
            TASK_SMART_FLEE_CHAR pedLifter2 scplayer 45.0 15000
        ENDIF
        MARK_CHAR_AS_NO_LONGER_NEEDED pedLifter2
    ENDIF

    IF DOES_CHAR_EXIST pedBoxer1
        SET_CHAR_STAY_IN_SAME_PLACE pedBoxer1 FALSE
        IF NOT IS_CHAR_DEAD pedBoxer1
            CLEAR_CHAR_TASKS pedBoxer1
            TASK_SAY pedBoxer1 CONTEXT_GLOBAL_GUN_RUN
            TASK_SMART_FLEE_CHAR pedBoxer1 scplayer 45.0 15000
        ENDIF
        MARK_CHAR_AS_NO_LONGER_NEEDED pedBoxer1
    ENDIF

    IF DOES_CHAR_EXIST pedBoxer2
        SET_CHAR_STAY_IN_SAME_PLACE pedBoxer2 FALSE
        IF NOT IS_CHAR_DEAD pedBoxer2
            CLEAR_CHAR_TASKS pedBoxer2
            TASK_SMART_FLEE_CHAR pedBoxer2 scplayer 45.0 15000
        ENDIF
        MARK_CHAR_AS_NO_LONGER_NEEDED pedBoxer2
    ENDIF

    IF DOES_CHAR_EXIST pedShopper
        SET_CHAR_STAY_IN_SAME_PLACE pedShopper FALSE
        IF NOT IS_CHAR_DEAD pedShopper
            CLEAR_CHAR_TASKS pedShopper
            TASK_SMART_FLEE_CHAR pedShopper scplayer 45.0 15000
        ENDIF
        MARK_CHAR_AS_NO_LONGER_NEEDED pedShopper
    ENDIF

    RETURN

/* ========================================================================= */
/* PEDESTRE FUMANDO NA CALÇADA EXTERNA                                       */
/* ========================================================================= */
SpawnOutsideSmoker:
    // Evita duplicar o fumante caso já exista um vivo no local
    IF DOES_CHAR_EXIST pedSmoker
        IF NOT IS_CHAR_DEAD pedSmoker
            RETURN
        ENDIF
    ENDIF

    // Verifica se está chovendo lá fora; se estiver chovendo, NÃO cria o pedestre externo
    READ_MEMORY 0xC81324 4 FALSE (fRain)
    READ_MEMORY 0xC81318 2 FALSE (tempVar)
    IF fRain > 0.05
    OR tempVar = 8
    OR tempVar = 16
        RETURN
    ENDIF

    // Spawna apenas se o jogador estiver no exterior próximo à porta da academia
    IF LOCATE_CHAR_ANY_MEANS_3D scplayer 2228.751 -1720.5699 13.5531 30.0 30.0 15.0 FALSE
        REQUEST_MODEL HMYCR
        REQUEST_ANIMATION "SMOKING"
        WHILE NOT HAS_MODEL_LOADED HMYCR
           OR NOT HAS_ANIMATION_LOADED "SMOKING"
            WAIT 0
            IF NOT IS_PLAYER_PLAYING 0
                RETURN
            ENDIF
        ENDWHILE

        CREATE_CHAR PEDTYPE_CIVMALE HMYCR 2228.751 -1720.5699 13.5531 pedSmoker
        SET_CHAR_HEADING pedSmoker 137.4583
        SET_CHAR_AREA_VISIBLE pedSmoker 0
        TASK_PLAY_ANIM pedSmoker "M_SMKSTND_LOOP" "SMOKING" 4.0 TRUE FALSE FALSE FALSE -1
        MARK_MODEL_AS_NO_LONGER_NEEDED HMYCR
        REMOVE_ANIMATION "SMOKING"
    ENDIF
    RETURN

/* ========================================================================= */
/* LIMPEZA SEGURA DE ENTIDADES E MEMÓRIA                                     */
/* ========================================================================= */
CleanupGym:
    // Desvincula e deleta os halteres
    IF DOES_OBJECT_EXIST objDumbbell1
        IF DOES_CHAR_EXIST pedLifter1
            DROP_OBJECT pedLifter1 TRUE
        ENDIF
        DETACH_OBJECT objDumbbell1 0.0 0.0 0.0 FALSE
        DELETE_OBJECT objDumbbell1
        objDumbbell1 = 0
    ENDIF

    IF DOES_OBJECT_EXIST objDumbbell2
        IF DOES_CHAR_EXIST pedLifter2
            DROP_OBJECT pedLifter2 TRUE
        ENDIF
        DETACH_OBJECT objDumbbell2 0.0 0.0 0.0 FALSE
        DELETE_OBJECT objDumbbell2
        objDumbbell2 = 0
    ENDIF

    // Deleta os objetos do cenário
    IF DOES_OBJECT_EXIST objStool1
        DELETE_OBJECT objStool1
    ENDIF
    IF DOES_OBJECT_EXIST objStool2
        DELETE_OBJECT objStool2
    ENDIF
    IF DOES_OBJECT_EXIST objStool3
        DELETE_OBJECT objStool3
    ENDIF
    IF DOES_OBJECT_EXIST objStool4
        DELETE_OBJECT objStool4
    ENDIF
    IF DOES_OBJECT_EXIST objTreadmill1
        DELETE_OBJECT objTreadmill1
    ENDIF
    IF DOES_OBJECT_EXIST objTreadmill2
        DELETE_OBJECT objTreadmill2
    ENDIF
    IF DOES_OBJECT_EXIST objBench1
        DELETE_OBJECT objBench1
    ENDIF
    IF DOES_OBJECT_EXIST objBench2
        DELETE_OBJECT objBench2
    ENDIF
    IF DOES_OBJECT_EXIST objRack
        DELETE_OBJECT objRack
    ENDIF

    // Deleta peds caso ainda existam na academia
    IF DOES_CHAR_EXIST pedJogger1
        DELETE_CHAR pedJogger1
        pedJogger1 = 0
    ENDIF
    IF DOES_CHAR_EXIST pedJogger2
        DELETE_CHAR pedJogger2
        pedJogger2 = 0
    ENDIF
    IF DOES_CHAR_EXIST pedLifter1
        DELETE_CHAR pedLifter1
        pedLifter1 = 0
    ENDIF
    IF DOES_CHAR_EXIST pedLifter2
        DELETE_CHAR pedLifter2
        pedLifter2 = 0
    ENDIF
    IF DOES_CHAR_EXIST pedBoxer1
        DELETE_CHAR pedBoxer1
        pedBoxer1 = 0
    ENDIF
    IF DOES_CHAR_EXIST pedBoxer2
        DELETE_CHAR pedBoxer2
        pedBoxer2 = 0
    ENDIF
    IF DOES_CHAR_EXIST pedShopper
        DELETE_CHAR pedShopper
        pedShopper = 0
    ENDIF

    // Libera animações da memória se foram carregadas
    IF HAS_ANIMATION_LOADED "FREEWEIGHTS"
        REMOVE_ANIMATION "FREEWEIGHTS"
    ENDIF
    IF HAS_ANIMATION_LOADED "GYMNASIUM"
        REMOVE_ANIMATION "GYMNASIUM"
    ENDIF
    IF HAS_ANIMATION_LOADED "COP_AMBIENT"
        REMOVE_ANIMATION "COP_AMBIENT"
    ENDIF

    bGymSpawned = 0
    bPanicked = 0
    RETURN

}
SCRIPT_END
