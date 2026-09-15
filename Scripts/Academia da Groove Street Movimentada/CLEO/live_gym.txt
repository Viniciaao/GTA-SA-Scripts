/*
    ============================================================================
    Academia de Ganton / Grove Street Movimentada
    ----------------------------------------------------------------------------
    Script original: SanKing (2013)
    Reformulacao do sistema de reacao: CLEO 4.4.4+ / CLEO+ / ModLoader
    ============================================================================
    O QUE MUDOU NESTA VERSAO (reacao individual por pedestre):

    1. O PEDESTRE QUE FUMA NA CALCADA VOLTA A APARECER.
       Antes, a rotina que criava o fumante exigia o jogador DENTRO da
       academia, entao ela nunca passava e ele nunca nascia. Agora ele nasce
       na rua, perto da porta, desde que nao esteja chovendo. Ele tambem
       reage: se apanhar ou levar uma arma na cara, larga o cigarro e vaza.

    2. FIM DO PANICO COLETIVO.
       Antes existia uma unica flag (bPanicked): socar UM pedestre fazia os
       outros seis pararem de treinar e sairem andando sem rumo. Agora cada
       pedestre tem seu proprio estado e reage sozinho -- quem nao foi
       incomodado continua treinando normalmente.

    3. SO EXISTE REACAO QUANDO EXISTE AGRESSAO DE VERDADE:
         - soco, cassetete, faca, bengala... (qualquer dano no pedestre);
         - tiro, inclusive errando, se o disparo for por perto;
         - granada, molotov, bomba ou qualquer explosao (pelo dano);
         - arma apontada para o pedestre (e preciso ter arma na mao).
       Mira de punhos nao assusta mais ninguem, e trombada NAO cancela o
       exercicio.

    4. "TELEPORTE" PELA PORTA.
       Quem entra em panico corre ate a porta por onde o jogador entrou e, ao
       chegar, o script apaga o pedestre. Ninguem fica batendo na parede
       tentando fugir de um lugar fechado.

    5. QUEM REVIDA.
       Ao apanhar, cada pedestre tem sua chance de partir para a briga:
       boxeadores revidam quase sempre, os marombeiros as vezes e os
       corredores raramente. Quem leva tiro, explosao ou fica muito
       machucado foge.

    6. TROMBADA NAO TRAVA MAIS O TREINO.
       Se a animacao for cancelada (empurrao, queda, IA do jogo), o script
       espera um pouco, reposiciona o pedestre no aparelho e reinicia o
       exercicio -- sem teleporte instantaneo e sem ficar com o halter
       pendurado na mao.

    7. HALTERES NUNCA MAIS GRUDAM NA MAO.
       Ao parar de treinar, o halter e solto (DROP_OBJECT + DETACH_OBJECT) e
       volta a ser um objeto fisico do mundo. Se o atleta voltar a treinar,
       ele pega o halter de novo.

    8. MORTOS SAO LIMPOS NA SAIDA.
       Ao sair da academia (ou morrer), tudo -- pedestres, corpos, objetos e
       animacoes -- e apagado, sem acumular nada na memoria.

    ----------------------------------------------------------------------------
    COMPILACAO (gta3sc / GTA3script):
        gta3sc compile --config=gtasa --guesser --cs -fno-entity-tracking -O \
            live_gym.sc -o live_gym.cs
    OBS: o jogo so permite 32 variaveis locais por script e este arquivo usa
    exatamente 32. Nao adicione variaveis sem tirar outra.
    ============================================================================
*/

SCRIPT_START
{
    NOP

    SCRIPT_NAME livegym

    /* IDs dos modelos usados pelo script */
    CONST_INT MODEL_KMB_DUMBBELL_R  3071
    CONST_INT MODEL_CJ_BARSTOOL     1805
    CONST_INT MODEL_GYM_TREADMILL   2627
    CONST_INT MODEL_GYM_BENCH1      2629
    CONST_INT MODEL_CJ_4WAY_CLOTHES 2390

    /* Estados possiveis de cada pedestre.
       Valores de 1 a 7: contador de espera antes de reposicionar o atleta.
       Valores de 11 a 14: contador de "insistir no caminho ate a porta". */
    CONST_INT ST_CALM      0
    CONST_INT ST_FIXED     7
    CONST_INT ST_MARK      8
    CONST_INT ST_FLEE      10
    CONST_INT ST_REISSUE   15
    CONST_INT ST_FIGHT     40
    CONST_INT ST_DEAD      50

    /* Faixas de WEAPONTYPE (arma na mao do jogador) */
    CONST_INT WT_HAND_FIRST 1     /* soco ingles: primeira arma "de mao" */
    CONST_INT WT_HAND_LAST  40    /* detonador: ultimo item ameacador     */
    CONST_INT WT_GUN_FIRST  22    /* pistola: primeira arma de fogo      */
    CONST_INT WT_GUN_LAST   38    /* minigun: ultima arma de fogo        */
    CONST_INT WT_BOMB_FIRST 16    /* granada: primeiro explosivo         */
    CONST_INT WT_BOMB_LAST  20    /* rocket hs: ultimo explosivo         */

    /* Tempo (ms) que o pedestre tem para se recuperar sozinho antes de ser
       reposicionado no aparelho */
    CONST_INT FIX_DELAY     1500

    /* Tempo (ms) sem ninguem fumando na calcada depois de espantar o fumante */
    CONST_INT SMOKE_DELAY   15000

    /* Variaveis de controle */
    LVAR_INT scplayer
    LVAR_INT smokerCooldown

    /* Pedestres da academia + fumante da calcada */
    LVAR_INT pedJogger1 pedJogger2
    LVAR_INT pedLifter1 pedLifter2
    LVAR_INT pedBoxer1 pedBoxer2
    LVAR_INT pedShopper pedSmoker

    /* Objetos do cenario */
    LVAR_INT objStool1 objStool2 objStool3 objStool4
    LVAR_INT objTreadmill1 objTreadmill2
    LVAR_INT objBench1 objBench2
    LVAR_INT objRack

    /* Halteres */
    LVAR_INT objDumbbell1 objDumbbell2

    /* Auxiliares */
    LVAR_INT curPed

    /* Porta de saida da academia (posicao onde o jogador entrou) */
    LVAR_FLOAT doorX doorY fTmpZ

    /* Estado individual de cada pedestre */
    LVAR_INT stJogger1 stJogger2
    LVAR_INT stLifter1 stLifter2
    LVAR_INT stBoxer1 stBoxer2
    LVAR_INT stShopper

    /* --- total: 32 variaveis locais (limite do jogo) --- */

    GET_PLAYER_CHAR 0 scplayer

    smokerCooldown = 0

    pedJogger1 = 0
    pedJogger2 = 0
    pedLifter1 = 0
    pedLifter2 = 0
    pedBoxer1 = 0
    pedBoxer2 = 0
    pedShopper = 0
    pedSmoker = 0

    objDumbbell1 = 0
    objDumbbell2 = 0

    stJogger1 = ST_CALM
    stJogger2 = ST_CALM
    stLifter1 = ST_CALM
    stLifter2 = ST_CALM
    stBoxer1 = ST_CALM
    stBoxer2 = ST_CALM
    stShopper = ST_CALM

    doorX = 0.0
    doorY = 0.0

main_loop:
    WAIT 250

    /* Jogador morreu, foi preso ou o jogo esta em transicao: limpa tudo */
    IF NOT IS_PLAYER_PLAYING 0
        GOSUB CleanupGym
        IF DOES_CHAR_EXIST pedSmoker
            MARK_CHAR_AS_NO_LONGER_NEEDED pedSmoker
            GOSUB FreeSmokingAnim
            pedSmoker = 0
        ENDIF
        GOTO main_loop
    ENDIF

    /* Pedestre fumante da calcada (independente de estar dentro ou fora) */
    GOSUB UpdateOutside

    /* Jogador dentro da academia de Ganton?
       Valida o interior 5 E a caixa 3D exata da academia (sem isso, outros
       interiores poderiam disparar o mod). */
    GET_AREA_VISIBLE curPed
    IF curPed = 5
    AND LOCATE_CHAR_ANY_MEANS_3D scplayer 765.0 5.0 1000.0 35.0 35.0 20.0 FALSE
        /* O banco do supino e criado junto com todo mundo: se ele existe,
           a academia ja esta montada */
        IF DOES_OBJECT_EXIST objBench1
            GOSUB UpdateGym
        ELSE
            GOSUB SpawnGym
        ENDIF
    ELSE
        GOSUB CleanupGym
    ENDIF

    GOTO main_loop

/* ========================================================================= */
/* SPAWN DOS OCUPANTES E EQUIPAMENTOS DA ACADEMIA                            */
/* ========================================================================= */
SpawnGym:
    /* Reseta handles e estados */
    pedJogger1 = 0
    stJogger1 = ST_CALM
    pedJogger2 = 0
    stJogger2 = ST_CALM
    pedLifter1 = 0
    stLifter1 = ST_CALM
    pedLifter2 = 0
    stLifter2 = ST_CALM
    pedBoxer1 = 0
    stBoxer1 = ST_CALM
    pedBoxer2 = 0
    stBoxer2 = ST_CALM
    pedShopper = 0
    stShopper = ST_CALM
    objDumbbell1 = 0
    objDumbbell2 = 0
    /* A porta de saida e exatamente onde o jogador entrou na academia.
       Guarda so na primeira vez: depois a porta nao muda de lugar. */
    IF doorX = 0.0
        GET_CHAR_COORDINATES scplayer doorX doorY fTmpZ
    ENDIF

    /* Sorteio individual de cada frequentador */
    GENERATE_RANDOM_INT_IN_RANGE 0 100 curPed
    IF curPed < 65
        pedJogger1 = -1
    ENDIF

    GENERATE_RANDOM_INT_IN_RANGE 0 100 curPed
    IF curPed < 60
        pedJogger2 = -1
    ENDIF

    GENERATE_RANDOM_INT_IN_RANGE 0 100 curPed
    IF curPed < 70
        pedLifter1 = -1
    ENDIF

    GENERATE_RANDOM_INT_IN_RANGE 0 100 curPed
    IF curPed < 65
        pedLifter2 = -1
    ENDIF

    GENERATE_RANDOM_INT_IN_RANGE 0 100 curPed
    IF curPed < 65
        pedBoxer1 = -1
    ENDIF

    GENERATE_RANDOM_INT_IN_RANGE 0 100 curPed
    IF curPed < 60
        pedBoxer2 = -1
    ENDIF

    GENERATE_RANDOM_INT_IN_RANGE 0 100 curPed
    IF curPed < 55
        pedShopper = -1
    ENDIF
    /* Garante que a academia nunca fique completamente vazia */
    IF pedJogger1 = 0
    AND pedJogger2 = 0
    AND pedLifter1 = 0
    AND pedLifter2 = 0
    AND pedBoxer1 = 0
    AND pedBoxer2 = 0
    AND pedShopper = 0
        pedLifter1 = -1
    ENDIF

    /* Modelos fixos do cenario */
    REQUEST_MODEL MODEL_CJ_BARSTOOL
    REQUEST_MODEL MODEL_GYM_TREADMILL
    REQUEST_MODEL MODEL_GYM_BENCH1
    REQUEST_MODEL MODEL_CJ_4WAY_CLOTHES

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
    /* Espera o carregamento dos modelos do cenario (aborta se o jogador sair) */
    WHILE NOT HAS_MODEL_LOADED MODEL_CJ_BARSTOOL
       OR NOT HAS_MODEL_LOADED MODEL_GYM_TREADMILL
       OR NOT HAS_MODEL_LOADED MODEL_GYM_BENCH1
       OR NOT HAS_MODEL_LOADED MODEL_CJ_4WAY_CLOTHES
        WAIT 0
        IF NOT IS_PLAYER_PLAYING 0
            RETURN
        ENDIF
        IF NOT LOCATE_CHAR_ANY_MEANS_3D scplayer 765.0 5.0 1000.0 35.0 35.0 20.0 FALSE
            RETURN
        ENDIF
    ENDWHILE

    IF pedLifter1 = -1
    OR pedLifter2 = -1
        WHILE NOT HAS_MODEL_LOADED MODEL_KMB_DUMBBELL_R
           OR NOT HAS_ANIMATION_LOADED "FREEWEIGHTS"
            WAIT 0
            IF NOT IS_PLAYER_PLAYING 0
                RETURN
            ENDIF
            IF NOT LOCATE_CHAR_ANY_MEANS_3D scplayer 765.0 5.0 1000.0 35.0 35.0 20.0 FALSE
                RETURN
            ENDIF
        ENDWHILE
    ENDIF

    IF pedJogger1 = -1
    OR pedJogger2 = -1
    OR pedBoxer1 = -1
    OR pedBoxer2 = -1
        WHILE NOT HAS_ANIMATION_LOADED "GYMNASIUM"
            WAIT 0
            IF NOT IS_PLAYER_PLAYING 0
                RETURN
            ENDIF
            IF NOT LOCATE_CHAR_ANY_MEANS_3D scplayer 765.0 5.0 1000.0 35.0 35.0 20.0 FALSE
                RETURN
            ENDIF
        ENDWHILE
    ENDIF

    IF pedShopper = -1
        WHILE NOT HAS_MODEL_LOADED BMYST
           OR NOT HAS_ANIMATION_LOADED "COP_AMBIENT"
            WAIT 0
            IF NOT IS_PLAYER_PLAYING 0
                RETURN
            ENDIF
            IF NOT LOCATE_CHAR_ANY_MEANS_3D scplayer 765.0 5.0 1000.0 35.0 35.0 20.0 FALSE
                RETURN
            ENDIF
        ENDWHILE
    ENDIF

    IF pedJogger1 = -1
        WHILE NOT HAS_MODEL_LOADED WMYJG
            WAIT 0
            IF NOT IS_PLAYER_PLAYING 0
                RETURN
            ENDIF
            IF NOT LOCATE_CHAR_ANY_MEANS_3D scplayer 765.0 5.0 1000.0 35.0 35.0 20.0 FALSE
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
            IF NOT LOCATE_CHAR_ANY_MEANS_3D scplayer 765.0 5.0 1000.0 35.0 35.0 20.0 FALSE
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
            IF NOT LOCATE_CHAR_ANY_MEANS_3D scplayer 765.0 5.0 1000.0 35.0 35.0 20.0 FALSE
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
            IF NOT LOCATE_CHAR_ANY_MEANS_3D scplayer 765.0 5.0 1000.0 35.0 35.0 20.0 FALSE
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
            IF NOT LOCATE_CHAR_ANY_MEANS_3D scplayer 765.0 5.0 1000.0 35.0 35.0 20.0 FALSE
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
            IF NOT LOCATE_CHAR_ANY_MEANS_3D scplayer 765.0 5.0 1000.0 35.0 35.0 20.0 FALSE
                RETURN
            ENDIF
        ENDWHILE
    ENDIF


    /* ---------------------------------------------------------------------
       1. Bancos do balcao (objetos fixos do cenario)
       --------------------------------------------------------------------- */
    CREATE_OBJECT MODEL_CJ_BARSTOOL 756.129 4.8 999.8 objStool1
    SET_OBJECT_AREA_VISIBLE objStool1 5

    CREATE_OBJECT MODEL_CJ_BARSTOOL 756.129 5.95 999.8 objStool2
    SET_OBJECT_AREA_VISIBLE objStool2 5

    CREATE_OBJECT MODEL_CJ_BARSTOOL 756.129 7.1 999.8 objStool3
    SET_OBJECT_AREA_VISIBLE objStool3 5

    CREATE_OBJECT MODEL_CJ_BARSTOOL 756.129 8.25 999.8 objStool4
    SET_OBJECT_AREA_VISIBLE objStool4 5

    /* ---------------------------------------------------------------------
       2. Esteiras e corredores
       --------------------------------------------------------------------- */
    CREATE_OBJECT MODEL_GYM_TREADMILL 773.115 10.9414 999.672 objTreadmill1
    SET_OBJECT_HEADING objTreadmill1 270.0
    SET_OBJECT_AREA_VISIBLE objTreadmill1 5

    CREATE_OBJECT MODEL_GYM_TREADMILL 773.115 12.3276 999.672 objTreadmill2
    SET_OBJECT_HEADING objTreadmill2 270.0
    SET_OBJECT_AREA_VISIBLE objTreadmill2 5

    /* ---------------------------------------------------------------------
       3. Supinos, halteres e atletas
       --------------------------------------------------------------------- */
    CREATE_OBJECT MODEL_GYM_BENCH1 771.823 7.4149 999.672 objBench1
    SET_OBJECT_AREA_VISIBLE objBench1 5

    CREATE_OBJECT MODEL_GYM_BENCH1 773.95 7.4149 999.672 objBench2
    SET_OBJECT_AREA_VISIBLE objBench2 5

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

    /* ---------------------------------------------------------------------
       4. Arara de roupas e cliente
       --------------------------------------------------------------------- */
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

    /* Libera os modelos da memoria de streaming */
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

    RETURN

/* ========================================================================= */
/* ATUALIZACAO DOS PEDESTRES DA ACADEMIA                                     */
/* ========================================================================= */
UpdateGym:
    /* ---------------------------------------------------------------------
       ALGUMA COISA EXPLODIU AQUI DENTRO?
       (granada, C4/bomba, molotov, foguete, barril, carro...)
       fTmpZ fica em 1.0 durante este ciclo e todo mundo reage: alguns
       correm para a porta e outros partem para a briga.
       --------------------------------------------------------------------- */
    fTmpZ = 0.0
    IF IS_EXPLOSION_IN_AREA EXPLOSION_GRENADE 748.0 -12.0 998.0 782.0 22.0 1008.0
        fTmpZ = 1.0
    ENDIF
    IF IS_EXPLOSION_IN_AREA EXPLOSION_MOLOTOV 748.0 -12.0 998.0 782.0 22.0 1008.0
        fTmpZ = 1.0
    ENDIF
    IF IS_EXPLOSION_IN_AREA EXPLOSION_ROCKET 748.0 -12.0 998.0 782.0 22.0 1008.0
        fTmpZ = 1.0
    ENDIF
    IF IS_EXPLOSION_IN_AREA EXPLOSION_ROCKET_WEAK 748.0 -12.0 998.0 782.0 22.0 1008.0
        fTmpZ = 1.0
    ENDIF
    IF IS_EXPLOSION_IN_AREA EXPLOSION_CAR 748.0 -12.0 998.0 782.0 22.0 1008.0
        fTmpZ = 1.0
    ENDIF
    IF IS_EXPLOSION_IN_AREA EXPLOSION_CAR_QUICK 748.0 -12.0 998.0 782.0 22.0 1008.0
        fTmpZ = 1.0
    ENDIF
    IF IS_EXPLOSION_IN_AREA EXPLOSION_MINE 748.0 -12.0 998.0 782.0 22.0 1008.0
        fTmpZ = 1.0
    ENDIF
    IF IS_EXPLOSION_IN_AREA EXPLOSION_OBJECT 748.0 -12.0 998.0 782.0 22.0 1008.0
        fTmpZ = 1.0
    ENDIF
    IF IS_EXPLOSION_IN_AREA EXPLOSION_TANK_GRENADE 748.0 -12.0 998.0 782.0 22.0 1008.0
        fTmpZ = 1.0
    ENDIF
    IF IS_EXPLOSION_IN_AREA EXPLOSION_SMALL 748.0 -12.0 998.0 782.0 22.0 1008.0
        fTmpZ = 1.0
    ENDIF

    /* O jogador acabou de armar/arremessar/detonar uma bomba? */
    IF IS_CHAR_SHOOTING scplayer
        GET_CURRENT_CHAR_WEAPON scplayer curPed
        IF curPed >= WEAPONTYPE_REMOTE_SATCHEL_CHARGE
        AND curPed <= WEAPONTYPE_DETONATOR
            fTmpZ = 1.0
        ENDIF
    ENDIF

    /* ---------------------------------------------------------------------
       Arma apontada: somente o pedestre mirado reage, e somente se o jogador
       estiver de fato com uma arma na mao (mira de punhos nao assusta).
       A marca (ST_MARK) e consumida no mesmo ciclo, logo abaixo.
       --------------------------------------------------------------------- */
    curPed = 0
    GET_CHAR_PLAYER_IS_TARGETING 0 curPed
    IF DOES_CHAR_EXIST curPed
        IF curPed = pedJogger1
        AND stJogger1 < ST_FLEE
            GET_CURRENT_CHAR_WEAPON scplayer stJogger1
            IF stJogger1 >= WT_HAND_FIRST
            AND stJogger1 <= WT_HAND_LAST
                stJogger1 = ST_MARK
            ELSE
                stJogger1 = ST_CALM
            ENDIF
        ENDIF
        IF curPed = pedJogger2
        AND stJogger2 < ST_FLEE
            GET_CURRENT_CHAR_WEAPON scplayer stJogger2
            IF stJogger2 >= WT_HAND_FIRST
            AND stJogger2 <= WT_HAND_LAST
                stJogger2 = ST_MARK
            ELSE
                stJogger2 = ST_CALM
            ENDIF
        ENDIF
        IF curPed = pedLifter1
        AND stLifter1 < ST_FLEE
            GET_CURRENT_CHAR_WEAPON scplayer stLifter1
            IF stLifter1 >= WT_HAND_FIRST
            AND stLifter1 <= WT_HAND_LAST
                stLifter1 = ST_MARK
            ELSE
                stLifter1 = ST_CALM
            ENDIF
        ENDIF
        IF curPed = pedLifter2
        AND stLifter2 < ST_FLEE
            GET_CURRENT_CHAR_WEAPON scplayer stLifter2
            IF stLifter2 >= WT_HAND_FIRST
            AND stLifter2 <= WT_HAND_LAST
                stLifter2 = ST_MARK
            ELSE
                stLifter2 = ST_CALM
            ENDIF
        ENDIF
        IF curPed = pedBoxer1
        AND stBoxer1 < ST_FLEE
            GET_CURRENT_CHAR_WEAPON scplayer stBoxer1
            IF stBoxer1 >= WT_HAND_FIRST
            AND stBoxer1 <= WT_HAND_LAST
                stBoxer1 = ST_MARK
            ELSE
                stBoxer1 = ST_CALM
            ENDIF
        ENDIF
        IF curPed = pedBoxer2
        AND stBoxer2 < ST_FLEE
            GET_CURRENT_CHAR_WEAPON scplayer stBoxer2
            IF stBoxer2 >= WT_HAND_FIRST
            AND stBoxer2 <= WT_HAND_LAST
                stBoxer2 = ST_MARK
            ELSE
                stBoxer2 = ST_CALM
            ENDIF
        ENDIF
        IF curPed = pedShopper
        AND stShopper < ST_FLEE
            GET_CURRENT_CHAR_WEAPON scplayer stShopper
            IF stShopper >= WT_HAND_FIRST
            AND stShopper <= WT_HAND_LAST
                stShopper = ST_MARK
            ELSE
                stShopper = ST_CALM
            ENDIF
        ENDIF
    ENDIF

    /* ---------------------------------------------------------------------
       Estado individual de cada pedestre
       --------------------------------------------------------------------- */

        /* ---------------- CORREDOR 1 (esteira 1) ---------------- */
        IF DOES_CHAR_EXIST pedJogger1
            curPed = pedJogger1
            IF IS_CHAR_DEAD pedJogger1
                /* morreu: solta o que estiver na mao e deixa o corpo para
                   ser removido na saida da academia */
                IF stJogger1 < ST_DEAD

                    stJogger1 = ST_DEAD
                ENDIF
            ELSE
                IF stJogger1 >= ST_DEAD
                    /* handle reaproveitado pelo jogo: esquece */
                    pedJogger1 = 0
                    stJogger1 = ST_CALM
                ELSE
                    IF stJogger1 >= ST_FIGHT
                        /* brigando: se o jogador se afastar ele desiste e foge */
                        IF NOT LOCATE_CHAR_ANY_MEANS_CHAR_3D pedJogger1 scplayer 18.0 18.0 8.0 FALSE
                            GOSUB FleeToExit
                            stJogger1 = ST_FLEE
                        ENDIF
                    ELSE
                        IF stJogger1 >= ST_FLEE
                            /* Fugindo: corre ate a porta e o script apaga ele */
                            GOSUB FleeTick
                            IF NOT DOES_CHAR_EXIST pedJogger1
                                pedJogger1 = 0
                                stJogger1 = ST_CALM
                            ELSE
                                IF stJogger1 >= ST_REISSUE
                                    /* Demorou demais: insiste no caminho ate a porta */
                                    GOSUB FleeToExit
                                    stJogger1 = ST_FLEE
                                ELSE
                                    stJogger1 += 1
                                ENDIF
                            ENDIF
                        ELSE
                            /* -------- calmo: treinando -------- */
                            IF stJogger1 = ST_MARK
                                /* arma apontada / tiros por perto */

                                GOSUB FleeToExit
                                stJogger1 = ST_FLEE
                            ELSE
                                /* tiros por perto? */
                                IF IS_CHAR_SHOOTING scplayer
                                AND LOCATE_CHAR_ANY_MEANS_CHAR_3D pedJogger1 scplayer 12.0 12.0 8.0 FALSE
                                    GET_CURRENT_CHAR_WEAPON scplayer stJogger1
                                    IF stJogger1 >= WT_GUN_FIRST
                                    AND stJogger1 <= WT_GUN_LAST
                                        stJogger1 = ST_MARK
                                    ELSE
                                        stJogger1 = ST_CALM
                                    ENDIF
                                ENDIF
                                IF stJogger1 = ST_MARK
                                    /* levou tiro / ouviu tiro de perto */

                                    GOSUB FleeToExit
                                    stJogger1 = ST_FLEE
                                ELSE
                                    IF HAS_CHAR_BEEN_DAMAGED_BY_WEAPON pedJogger1 WEAPONTYPE_ANYMELEE
                                    OR NOT IS_CHAR_HEALTH_GREATER pedJogger1 95
                                    OR fTmpZ = 1.0
                                        /* Foi agredido de verdade (soco, arma branca,
                                           tiro, bomba, atropelamento): foge ou revida.
                                           Trombada nao passa por aqui. */

                                        IF NOT IS_CHAR_HEALTH_GREATER pedJogger1 45
                                            GOSUB FleeToExit
                                            stJogger1 = ST_FLEE
                                        ELSE
                                            GET_CURRENT_CHAR_WEAPON scplayer stJogger1
                                            IF stJogger1 >= WT_BOMB_FIRST
                                            AND stJogger1 <= WT_BOMB_LAST
                                            AND fTmpZ = 0.0
                                                GOSUB FleeToExit
                                                stJogger1 = ST_FLEE
                                            ELSE
                                                GENERATE_RANDOM_INT_IN_RANGE 0 100 stJogger1
                                                IF stJogger1 < 25
                                                    GOSUB FightPlayer
                                                    stJogger1 = ST_FIGHT
                                                ELSE
                                                    GOSUB FleeToExit
                                                    stJogger1 = ST_FLEE
                                                ENDIF
                                            ENDIF
                                        ENDIF
                                    ELSE
                                        /* ninguem encostou nele: mantem o treino
                                           (conserta trombadas e travamentos) */
                                        IF IS_CHAR_PLAYING_ANIM pedJogger1 "GYM_TREAD_JOG"
                                        AND LOCATE_CHAR_ANY_MEANS_3D pedJogger1 771.4 10.9204 1000.849 1.5 1.5 2.0 FALSE
                                            IF stJogger1 < ST_FIXED
                                                stJogger1 += 1
                                            ELSE
                                                GOSUB RestoreJogger1
                                                stJogger1 = ST_CALM
                                            ENDIF
                                        ELSE
                                            IF stJogger1 < ST_FIXED
                                                stJogger1 += 1
                                            ELSE
                                                GOSUB RestoreJogger1
                                                stJogger1 = ST_CALM
                                            ENDIF
                                        ENDIF
                                    ENDIF
                                ENDIF
                            ENDIF
                        ENDIF
                    ENDIF
                ENDIF
            ENDIF
        ENDIF


        /* ---------------- CORREDOR 2 (esteira 2) ---------------- */
        IF DOES_CHAR_EXIST pedJogger2
            curPed = pedJogger2
            IF IS_CHAR_DEAD pedJogger2
                /* morreu: solta o que estiver na mao e deixa o corpo para
                   ser removido na saida da academia */
                IF stJogger2 < ST_DEAD

                    stJogger2 = ST_DEAD
                ENDIF
            ELSE
                IF stJogger2 >= ST_DEAD
                    /* handle reaproveitado pelo jogo: esquece */
                    pedJogger2 = 0
                    stJogger2 = ST_CALM
                ELSE
                    IF stJogger2 >= ST_FIGHT
                        /* brigando: se o jogador se afastar ele desiste e foge */
                        IF NOT LOCATE_CHAR_ANY_MEANS_CHAR_3D pedJogger2 scplayer 18.0 18.0 8.0 FALSE
                            GOSUB FleeToExit
                            stJogger2 = ST_FLEE
                        ENDIF
                    ELSE
                        IF stJogger2 >= ST_FLEE
                            /* Fugindo: corre ate a porta e o script apaga ele */
                            GOSUB FleeTick
                            IF NOT DOES_CHAR_EXIST pedJogger2
                                pedJogger2 = 0
                                stJogger2 = ST_CALM
                            ELSE
                                IF stJogger2 >= ST_REISSUE
                                    /* Demorou demais: insiste no caminho ate a porta */
                                    GOSUB FleeToExit
                                    stJogger2 = ST_FLEE
                                ELSE
                                    stJogger2 += 1
                                ENDIF
                            ENDIF
                        ELSE
                            /* -------- calmo: treinando -------- */
                            IF stJogger2 = ST_MARK
                                /* arma apontada / tiros por perto */

                                GOSUB FleeToExit
                                stJogger2 = ST_FLEE
                            ELSE
                                /* tiros por perto? */
                                IF IS_CHAR_SHOOTING scplayer
                                AND LOCATE_CHAR_ANY_MEANS_CHAR_3D pedJogger2 scplayer 12.0 12.0 8.0 FALSE
                                    GET_CURRENT_CHAR_WEAPON scplayer stJogger2
                                    IF stJogger2 >= WT_GUN_FIRST
                                    AND stJogger2 <= WT_GUN_LAST
                                        stJogger2 = ST_MARK
                                    ELSE
                                        stJogger2 = ST_CALM
                                    ENDIF
                                ENDIF
                                IF stJogger2 = ST_MARK
                                    /* levou tiro / ouviu tiro de perto */

                                    GOSUB FleeToExit
                                    stJogger2 = ST_FLEE
                                ELSE
                                    IF HAS_CHAR_BEEN_DAMAGED_BY_WEAPON pedJogger2 WEAPONTYPE_ANYMELEE
                                    OR NOT IS_CHAR_HEALTH_GREATER pedJogger2 95
                                    OR fTmpZ = 1.0
                                        /* Foi agredido de verdade (soco, arma branca,
                                           tiro, bomba, atropelamento): foge ou revida.
                                           Trombada nao passa por aqui. */

                                        IF NOT IS_CHAR_HEALTH_GREATER pedJogger2 45
                                            GOSUB FleeToExit
                                            stJogger2 = ST_FLEE
                                        ELSE
                                            GET_CURRENT_CHAR_WEAPON scplayer stJogger2
                                            IF stJogger2 >= WT_BOMB_FIRST
                                            AND stJogger2 <= WT_BOMB_LAST
                                            AND fTmpZ = 0.0
                                                GOSUB FleeToExit
                                                stJogger2 = ST_FLEE
                                            ELSE
                                                GENERATE_RANDOM_INT_IN_RANGE 0 100 stJogger2
                                                IF stJogger2 < 25
                                                    GOSUB FightPlayer
                                                    stJogger2 = ST_FIGHT
                                                ELSE
                                                    GOSUB FleeToExit
                                                    stJogger2 = ST_FLEE
                                                ENDIF
                                            ENDIF
                                        ENDIF
                                    ELSE
                                        /* ninguem encostou nele: mantem o treino
                                           (conserta trombadas e travamentos) */
                                        IF IS_CHAR_PLAYING_ANIM pedJogger2 "GYM_TREAD_JOG"
                                        AND LOCATE_CHAR_ANY_MEANS_3D pedJogger2 771.4 12.3027 1000.849 1.5 1.5 2.0 FALSE
                                            IF stJogger2 < ST_FIXED
                                                stJogger2 += 1
                                            ELSE
                                                GOSUB RestoreJogger2
                                                stJogger2 = ST_CALM
                                            ENDIF
                                        ELSE
                                            IF stJogger2 < ST_FIXED
                                                stJogger2 += 1
                                            ELSE
                                                GOSUB RestoreJogger2
                                                stJogger2 = ST_CALM
                                            ENDIF
                                        ENDIF
                                    ENDIF
                                ENDIF
                            ENDIF
                        ENDIF
                    ENDIF
                ENDIF
            ENDIF
        ENDIF


        /* ---------------- MUSCULOSO 1 (supino 1) ---------------- */
        IF DOES_CHAR_EXIST pedLifter1
            curPed = pedLifter1
            IF IS_CHAR_DEAD pedLifter1
                /* morreu: solta o que estiver na mao e deixa o corpo para
                   ser removido na saida da academia */
                IF stLifter1 < ST_DEAD
GOSUB DropDumbbell1
                    stLifter1 = ST_DEAD
                ENDIF
            ELSE
                IF stLifter1 >= ST_DEAD
                    /* handle reaproveitado pelo jogo: esquece */
                    pedLifter1 = 0
                    stLifter1 = ST_CALM
                ELSE
                    IF stLifter1 >= ST_FIGHT
                        /* brigando: se o jogador se afastar ele desiste e foge */
                        IF NOT LOCATE_CHAR_ANY_MEANS_CHAR_3D pedLifter1 scplayer 18.0 18.0 8.0 FALSE
                            GOSUB FleeToExit
                            stLifter1 = ST_FLEE
                        ENDIF
                    ELSE
                        IF stLifter1 >= ST_FLEE
                            /* Fugindo: corre ate a porta e o script apaga ele */
                            GOSUB FleeTick
                            IF NOT DOES_CHAR_EXIST pedLifter1
                                pedLifter1 = 0
                                stLifter1 = ST_CALM
                            ELSE
                                IF stLifter1 >= ST_REISSUE
                                    /* Demorou demais: insiste no caminho ate a porta */
                                    GOSUB FleeToExit
                                    stLifter1 = ST_FLEE
                                ELSE
                                    stLifter1 += 1
                                ENDIF
                            ENDIF
                        ELSE
                            /* -------- calmo: treinando -------- */
                            IF stLifter1 = ST_MARK
                                /* arma apontada / tiros por perto */
GOSUB DropDumbbell1
                                GOSUB FleeToExit
                                stLifter1 = ST_FLEE
                            ELSE
                                /* tiros por perto? */
                                IF IS_CHAR_SHOOTING scplayer
                                AND LOCATE_CHAR_ANY_MEANS_CHAR_3D pedLifter1 scplayer 12.0 12.0 8.0 FALSE
                                    GET_CURRENT_CHAR_WEAPON scplayer stLifter1
                                    IF stLifter1 >= WT_GUN_FIRST
                                    AND stLifter1 <= WT_GUN_LAST
                                        stLifter1 = ST_MARK
                                    ELSE
                                        stLifter1 = ST_CALM
                                    ENDIF
                                ENDIF
                                IF stLifter1 = ST_MARK
                                    /* levou tiro / ouviu tiro de perto */
GOSUB DropDumbbell1
                                    GOSUB FleeToExit
                                    stLifter1 = ST_FLEE
                                ELSE
                                    IF HAS_CHAR_BEEN_DAMAGED_BY_WEAPON pedLifter1 WEAPONTYPE_ANYMELEE
                                    OR NOT IS_CHAR_HEALTH_GREATER pedLifter1 95
                                    OR fTmpZ = 1.0
                                        /* Foi agredido de verdade (soco, arma branca,
                                           tiro, bomba, atropelamento): foge ou revida.
                                           Trombada nao passa por aqui. */
GOSUB DropDumbbell1
                                        IF NOT IS_CHAR_HEALTH_GREATER pedLifter1 45
                                            GOSUB FleeToExit
                                            stLifter1 = ST_FLEE
                                        ELSE
                                            GET_CURRENT_CHAR_WEAPON scplayer stLifter1
                                            IF stLifter1 >= WT_BOMB_FIRST
                                            AND stLifter1 <= WT_BOMB_LAST
                                            AND fTmpZ = 0.0
                                                GOSUB FleeToExit
                                                stLifter1 = ST_FLEE
                                            ELSE
                                                GENERATE_RANDOM_INT_IN_RANGE 0 100 stLifter1
                                                IF stLifter1 < 40
                                                    GOSUB FightPlayer
                                                    stLifter1 = ST_FIGHT
                                                ELSE
                                                    GOSUB FleeToExit
                                                    stLifter1 = ST_FLEE
                                                ENDIF
                                            ENDIF
                                        ENDIF
                                    ELSE
                                        /* ninguem encostou nele: mantem o treino
                                           (conserta trombadas e travamentos) */
                                        IF IS_CHAR_PLAYING_ANIM pedLifter1 "GYM_BARBELL"
                                        AND LOCATE_CHAR_ANY_MEANS_3D pedLifter1 771.4566 7.3739 1000.71 1.5 1.5 2.0 FALSE
                                            IF DOES_OBJECT_EXIST objDumbbell1
                                                IF IS_CHAR_HOLDING_OBJECT pedLifter1 objDumbbell1
                                                    stLifter1 = ST_CALM
                                                ELSE
                                                    IF stLifter1 < ST_FIXED
                                                        stLifter1 += 1
                                                    ELSE
                                                        GOSUB RestoreLifter1
                                                        stLifter1 = ST_CALM
                                                    ENDIF
                                                ENDIF
                                            ELSE
                                                /* halter foi removido do mundo:
                                                   deixa ele treinar sem peso */
                                                stLifter1 = ST_CALM
                                            ENDIF
                                        ELSE
                                            IF stLifter1 < ST_FIXED
                                                stLifter1 += 1
                                            ELSE
                                                GOSUB RestoreLifter1
                                                stLifter1 = ST_CALM
                                            ENDIF
                                        ENDIF
                                    ENDIF
                                ENDIF
                            ENDIF
                        ENDIF
                    ENDIF
                ENDIF
            ENDIF
        ENDIF


        /* ---------------- MUSCULOSO 2 (supino 2) ---------------- */
        IF DOES_CHAR_EXIST pedLifter2
            curPed = pedLifter2
            IF IS_CHAR_DEAD pedLifter2
                /* morreu: solta o que estiver na mao e deixa o corpo para
                   ser removido na saida da academia */
                IF stLifter2 < ST_DEAD
GOSUB DropDumbbell2
                    stLifter2 = ST_DEAD
                ENDIF
            ELSE
                IF stLifter2 >= ST_DEAD
                    /* handle reaproveitado pelo jogo: esquece */
                    pedLifter2 = 0
                    stLifter2 = ST_CALM
                ELSE
                    IF stLifter2 >= ST_FIGHT
                        /* brigando: se o jogador se afastar ele desiste e foge */
                        IF NOT LOCATE_CHAR_ANY_MEANS_CHAR_3D pedLifter2 scplayer 18.0 18.0 8.0 FALSE
                            GOSUB FleeToExit
                            stLifter2 = ST_FLEE
                        ENDIF
                    ELSE
                        IF stLifter2 >= ST_FLEE
                            /* Fugindo: corre ate a porta e o script apaga ele */
                            GOSUB FleeTick
                            IF NOT DOES_CHAR_EXIST pedLifter2
                                pedLifter2 = 0
                                stLifter2 = ST_CALM
                            ELSE
                                IF stLifter2 >= ST_REISSUE
                                    /* Demorou demais: insiste no caminho ate a porta */
                                    GOSUB FleeToExit
                                    stLifter2 = ST_FLEE
                                ELSE
                                    stLifter2 += 1
                                ENDIF
                            ENDIF
                        ELSE
                            /* -------- calmo: treinando -------- */
                            IF stLifter2 = ST_MARK
                                /* arma apontada / tiros por perto */
GOSUB DropDumbbell2
                                GOSUB FleeToExit
                                stLifter2 = ST_FLEE
                            ELSE
                                /* tiros por perto? */
                                IF IS_CHAR_SHOOTING scplayer
                                AND LOCATE_CHAR_ANY_MEANS_CHAR_3D pedLifter2 scplayer 12.0 12.0 8.0 FALSE
                                    GET_CURRENT_CHAR_WEAPON scplayer stLifter2
                                    IF stLifter2 >= WT_GUN_FIRST
                                    AND stLifter2 <= WT_GUN_LAST
                                        stLifter2 = ST_MARK
                                    ELSE
                                        stLifter2 = ST_CALM
                                    ENDIF
                                ENDIF
                                IF stLifter2 = ST_MARK
                                    /* levou tiro / ouviu tiro de perto */
GOSUB DropDumbbell2
                                    GOSUB FleeToExit
                                    stLifter2 = ST_FLEE
                                ELSE
                                    IF HAS_CHAR_BEEN_DAMAGED_BY_WEAPON pedLifter2 WEAPONTYPE_ANYMELEE
                                    OR NOT IS_CHAR_HEALTH_GREATER pedLifter2 95
                                    OR fTmpZ = 1.0
                                        /* Foi agredido de verdade (soco, arma branca,
                                           tiro, bomba, atropelamento): foge ou revida.
                                           Trombada nao passa por aqui. */
GOSUB DropDumbbell2
                                        IF NOT IS_CHAR_HEALTH_GREATER pedLifter2 45
                                            GOSUB FleeToExit
                                            stLifter2 = ST_FLEE
                                        ELSE
                                            GET_CURRENT_CHAR_WEAPON scplayer stLifter2
                                            IF stLifter2 >= WT_BOMB_FIRST
                                            AND stLifter2 <= WT_BOMB_LAST
                                            AND fTmpZ = 0.0
                                                GOSUB FleeToExit
                                                stLifter2 = ST_FLEE
                                            ELSE
                                                GENERATE_RANDOM_INT_IN_RANGE 0 100 stLifter2
                                                IF stLifter2 < 40
                                                    GOSUB FightPlayer
                                                    stLifter2 = ST_FIGHT
                                                ELSE
                                                    GOSUB FleeToExit
                                                    stLifter2 = ST_FLEE
                                                ENDIF
                                            ENDIF
                                        ENDIF
                                    ELSE
                                        /* ninguem encostou nele: mantem o treino
                                           (conserta trombadas e travamentos) */
                                        IF IS_CHAR_PLAYING_ANIM pedLifter2 "GYM_BARBELL"
                                        AND LOCATE_CHAR_ANY_MEANS_3D pedLifter2 773.6576 7.4052 1000.709 1.5 1.5 2.0 FALSE
                                            IF DOES_OBJECT_EXIST objDumbbell2
                                                IF IS_CHAR_HOLDING_OBJECT pedLifter2 objDumbbell2
                                                    stLifter2 = ST_CALM
                                                ELSE
                                                    IF stLifter2 < ST_FIXED
                                                        stLifter2 += 1
                                                    ELSE
                                                        GOSUB RestoreLifter2
                                                        stLifter2 = ST_CALM
                                                    ENDIF
                                                ENDIF
                                            ELSE
                                                /* halter foi removido do mundo:
                                                   deixa ele treinar sem peso */
                                                stLifter2 = ST_CALM
                                            ENDIF
                                        ELSE
                                            IF stLifter2 < ST_FIXED
                                                stLifter2 += 1
                                            ELSE
                                                GOSUB RestoreLifter2
                                                stLifter2 = ST_CALM
                                            ENDIF
                                        ENDIF
                                    ENDIF
                                ENDIF
                            ENDIF
                        ENDIF
                    ENDIF
                ENDIF
            ENDIF
        ENDIF


        /* ---------------- BOXEADOR 1 (sombra) ---------------- */
        IF DOES_CHAR_EXIST pedBoxer1
            curPed = pedBoxer1
            IF IS_CHAR_DEAD pedBoxer1
                /* morreu: solta o que estiver na mao e deixa o corpo para
                   ser removido na saida da academia */
                IF stBoxer1 < ST_DEAD

                    stBoxer1 = ST_DEAD
                ENDIF
            ELSE
                IF stBoxer1 >= ST_DEAD
                    /* handle reaproveitado pelo jogo: esquece */
                    pedBoxer1 = 0
                    stBoxer1 = ST_CALM
                ELSE
                    IF stBoxer1 >= ST_FIGHT
                        /* brigando: se o jogador se afastar ele desiste e foge */
                        IF NOT LOCATE_CHAR_ANY_MEANS_CHAR_3D pedBoxer1 scplayer 18.0 18.0 8.0 FALSE
                            GOSUB FleeToExit
                            stBoxer1 = ST_FLEE
                        ENDIF
                    ELSE
                        IF stBoxer1 >= ST_FLEE
                            /* Fugindo: corre ate a porta e o script apaga ele */
                            GOSUB FleeTick
                            IF NOT DOES_CHAR_EXIST pedBoxer1
                                pedBoxer1 = 0
                                stBoxer1 = ST_CALM
                            ELSE
                                IF stBoxer1 >= ST_REISSUE
                                    /* Demorou demais: insiste no caminho ate a porta */
                                    GOSUB FleeToExit
                                    stBoxer1 = ST_FLEE
                                ELSE
                                    stBoxer1 += 1
                                ENDIF
                            ENDIF
                        ELSE
                            /* -------- calmo: treinando -------- */
                            IF stBoxer1 = ST_MARK
                                /* arma apontada / tiros por perto */

                                GOSUB FleeToExit
                                stBoxer1 = ST_FLEE
                            ELSE
                                /* tiros por perto? */
                                IF IS_CHAR_SHOOTING scplayer
                                AND LOCATE_CHAR_ANY_MEANS_CHAR_3D pedBoxer1 scplayer 12.0 12.0 8.0 FALSE
                                    GET_CURRENT_CHAR_WEAPON scplayer stBoxer1
                                    IF stBoxer1 >= WT_GUN_FIRST
                                    AND stBoxer1 <= WT_GUN_LAST
                                        stBoxer1 = ST_MARK
                                    ELSE
                                        stBoxer1 = ST_CALM
                                    ENDIF
                                ENDIF
                                IF stBoxer1 = ST_MARK
                                    /* levou tiro / ouviu tiro de perto */

                                    GOSUB FleeToExit
                                    stBoxer1 = ST_FLEE
                                ELSE
                                    IF HAS_CHAR_BEEN_DAMAGED_BY_WEAPON pedBoxer1 WEAPONTYPE_ANYMELEE
                                    OR NOT IS_CHAR_HEALTH_GREATER pedBoxer1 95
                                    OR fTmpZ = 1.0
                                        /* Foi agredido de verdade (soco, arma branca,
                                           tiro, bomba, atropelamento): foge ou revida.
                                           Trombada nao passa por aqui. */

                                        IF NOT IS_CHAR_HEALTH_GREATER pedBoxer1 45
                                            GOSUB FleeToExit
                                            stBoxer1 = ST_FLEE
                                        ELSE
                                            GET_CURRENT_CHAR_WEAPON scplayer stBoxer1
                                            IF stBoxer1 >= WT_BOMB_FIRST
                                            AND stBoxer1 <= WT_BOMB_LAST
                                            AND fTmpZ = 0.0
                                                GOSUB FleeToExit
                                                stBoxer1 = ST_FLEE
                                            ELSE
                                                GENERATE_RANDOM_INT_IN_RANGE 0 100 stBoxer1
                                                IF stBoxer1 < 90
                                                    GOSUB FightPlayer
                                                    stBoxer1 = ST_FIGHT
                                                ELSE
                                                    GOSUB FleeToExit
                                                    stBoxer1 = ST_FLEE
                                                ENDIF
                                            ENDIF
                                        ENDIF
                                    ELSE
                                        /* ninguem encostou nele: mantem o treino
                                           (conserta trombadas e travamentos) */
                                        IF IS_CHAR_PLAYING_ANIM pedBoxer1 "GYM_SHADOWBOX"
                                        AND LOCATE_CHAR_ANY_MEANS_3D pedBoxer1 767.2722 -2.4724 1000.719 1.5 1.5 2.0 FALSE
                                            IF stBoxer1 < ST_FIXED
                                                stBoxer1 += 1
                                            ELSE
                                                GOSUB RestoreBoxer1
                                                stBoxer1 = ST_CALM
                                            ENDIF
                                        ELSE
                                            IF stBoxer1 < ST_FIXED
                                                stBoxer1 += 1
                                            ELSE
                                                GOSUB RestoreBoxer1
                                                stBoxer1 = ST_CALM
                                            ENDIF
                                        ENDIF
                                    ENDIF
                                ENDIF
                            ENDIF
                        ENDIF
                    ENDIF
                ENDIF
            ENDIF
        ENDIF


        /* ---------------- BOXEADOR 2 (sombra) ---------------- */
        IF DOES_CHAR_EXIST pedBoxer2
            curPed = pedBoxer2
            IF IS_CHAR_DEAD pedBoxer2
                /* morreu: solta o que estiver na mao e deixa o corpo para
                   ser removido na saida da academia */
                IF stBoxer2 < ST_DEAD

                    stBoxer2 = ST_DEAD
                ENDIF
            ELSE
                IF stBoxer2 >= ST_DEAD
                    /* handle reaproveitado pelo jogo: esquece */
                    pedBoxer2 = 0
                    stBoxer2 = ST_CALM
                ELSE
                    IF stBoxer2 >= ST_FIGHT
                        /* brigando: se o jogador se afastar ele desiste e foge */
                        IF NOT LOCATE_CHAR_ANY_MEANS_CHAR_3D pedBoxer2 scplayer 18.0 18.0 8.0 FALSE
                            GOSUB FleeToExit
                            stBoxer2 = ST_FLEE
                        ENDIF
                    ELSE
                        IF stBoxer2 >= ST_FLEE
                            /* Fugindo: corre ate a porta e o script apaga ele */
                            GOSUB FleeTick
                            IF NOT DOES_CHAR_EXIST pedBoxer2
                                pedBoxer2 = 0
                                stBoxer2 = ST_CALM
                            ELSE
                                IF stBoxer2 >= ST_REISSUE
                                    /* Demorou demais: insiste no caminho ate a porta */
                                    GOSUB FleeToExit
                                    stBoxer2 = ST_FLEE
                                ELSE
                                    stBoxer2 += 1
                                ENDIF
                            ENDIF
                        ELSE
                            /* -------- calmo: treinando -------- */
                            IF stBoxer2 = ST_MARK
                                /* arma apontada / tiros por perto */

                                GOSUB FleeToExit
                                stBoxer2 = ST_FLEE
                            ELSE
                                /* tiros por perto? */
                                IF IS_CHAR_SHOOTING scplayer
                                AND LOCATE_CHAR_ANY_MEANS_CHAR_3D pedBoxer2 scplayer 12.0 12.0 8.0 FALSE
                                    GET_CURRENT_CHAR_WEAPON scplayer stBoxer2
                                    IF stBoxer2 >= WT_GUN_FIRST
                                    AND stBoxer2 <= WT_GUN_LAST
                                        stBoxer2 = ST_MARK
                                    ELSE
                                        stBoxer2 = ST_CALM
                                    ENDIF
                                ENDIF
                                IF stBoxer2 = ST_MARK
                                    /* levou tiro / ouviu tiro de perto */

                                    GOSUB FleeToExit
                                    stBoxer2 = ST_FLEE
                                ELSE
                                    IF HAS_CHAR_BEEN_DAMAGED_BY_WEAPON pedBoxer2 WEAPONTYPE_ANYMELEE
                                    OR NOT IS_CHAR_HEALTH_GREATER pedBoxer2 95
                                    OR fTmpZ = 1.0
                                        /* Foi agredido de verdade (soco, arma branca,
                                           tiro, bomba, atropelamento): foge ou revida.
                                           Trombada nao passa por aqui. */

                                        IF NOT IS_CHAR_HEALTH_GREATER pedBoxer2 45
                                            GOSUB FleeToExit
                                            stBoxer2 = ST_FLEE
                                        ELSE
                                            GET_CURRENT_CHAR_WEAPON scplayer stBoxer2
                                            IF stBoxer2 >= WT_BOMB_FIRST
                                            AND stBoxer2 <= WT_BOMB_LAST
                                            AND fTmpZ = 0.0
                                                GOSUB FleeToExit
                                                stBoxer2 = ST_FLEE
                                            ELSE
                                                GENERATE_RANDOM_INT_IN_RANGE 0 100 stBoxer2
                                                IF stBoxer2 < 90
                                                    GOSUB FightPlayer
                                                    stBoxer2 = ST_FIGHT
                                                ELSE
                                                    GOSUB FleeToExit
                                                    stBoxer2 = ST_FLEE
                                                ENDIF
                                            ENDIF
                                        ENDIF
                                    ELSE
                                        /* ninguem encostou nele: mantem o treino
                                           (conserta trombadas e travamentos) */
                                        IF IS_CHAR_PLAYING_ANIM pedBoxer2 "GYM_SHADOWBOX"
                                        AND LOCATE_CHAR_ANY_MEANS_3D pedBoxer2 769.6318 -2.4529 1000.724 1.5 1.5 2.0 FALSE
                                            IF stBoxer2 < ST_FIXED
                                                stBoxer2 += 1
                                            ELSE
                                                GOSUB RestoreBoxer2
                                                stBoxer2 = ST_CALM
                                            ENDIF
                                        ELSE
                                            IF stBoxer2 < ST_FIXED
                                                stBoxer2 += 1
                                            ELSE
                                                GOSUB RestoreBoxer2
                                                stBoxer2 = ST_CALM
                                            ENDIF
                                        ENDIF
                                    ENDIF
                                ENDIF
                            ENDIF
                        ENDIF
                    ENDIF
                ENDIF
            ENDIF
        ENDIF


        /* ---------------- CLIENTE (arara de roupas) ---------------- */
        IF DOES_CHAR_EXIST pedShopper
            curPed = pedShopper
            IF IS_CHAR_DEAD pedShopper
                /* morreu: solta o que estiver na mao e deixa o corpo para
                   ser removido na saida da academia */
                IF stShopper < ST_DEAD

                    stShopper = ST_DEAD
                ENDIF
            ELSE
                IF stShopper >= ST_DEAD
                    /* handle reaproveitado pelo jogo: esquece */
                    pedShopper = 0
                    stShopper = ST_CALM
                ELSE
                    IF stShopper >= ST_FIGHT
                        /* brigando: se o jogador se afastar ele desiste e foge */
                        IF NOT LOCATE_CHAR_ANY_MEANS_CHAR_3D pedShopper scplayer 18.0 18.0 8.0 FALSE
                            GOSUB FleeToExit
                            stShopper = ST_FLEE
                        ENDIF
                    ELSE
                        IF stShopper >= ST_FLEE
                            /* Fugindo: corre ate a porta e o script apaga ele */
                            GOSUB FleeTick
                            IF NOT DOES_CHAR_EXIST pedShopper
                                pedShopper = 0
                                stShopper = ST_CALM
                            ELSE
                                IF stShopper >= ST_REISSUE
                                    /* Demorou demais: insiste no caminho ate a porta */
                                    GOSUB FleeToExit
                                    stShopper = ST_FLEE
                                ELSE
                                    stShopper += 1
                                ENDIF
                            ENDIF
                        ELSE
                            /* -------- calmo: treinando -------- */
                            IF stShopper = ST_MARK
                                /* arma apontada / tiros por perto */

                                GOSUB FleeToExit
                                stShopper = ST_FLEE
                            ELSE
                                /* tiros por perto? */
                                IF IS_CHAR_SHOOTING scplayer
                                AND LOCATE_CHAR_ANY_MEANS_CHAR_3D pedShopper scplayer 12.0 12.0 8.0 FALSE
                                    GET_CURRENT_CHAR_WEAPON scplayer stShopper
                                    IF stShopper >= WT_GUN_FIRST
                                    AND stShopper <= WT_GUN_LAST
                                        stShopper = ST_MARK
                                    ELSE
                                        stShopper = ST_CALM
                                    ENDIF
                                ENDIF
                                IF stShopper = ST_MARK
                                    /* levou tiro / ouviu tiro de perto */

                                    GOSUB FleeToExit
                                    stShopper = ST_FLEE
                                ELSE
                                    IF HAS_CHAR_BEEN_DAMAGED_BY_WEAPON pedShopper WEAPONTYPE_ANYMELEE
                                    OR NOT IS_CHAR_HEALTH_GREATER pedShopper 95
                                    OR fTmpZ = 1.0
                                        /* Foi agredido de verdade (soco, arma branca,
                                           tiro, bomba, atropelamento): foge ou revida.
                                           Trombada nao passa por aqui. */

                                        IF NOT IS_CHAR_HEALTH_GREATER pedShopper 45
                                            GOSUB FleeToExit
                                            stShopper = ST_FLEE
                                        ELSE
                                            GET_CURRENT_CHAR_WEAPON scplayer stShopper
                                            IF stShopper >= WT_BOMB_FIRST
                                            AND stShopper <= WT_BOMB_LAST
                                            AND fTmpZ = 0.0
                                                GOSUB FleeToExit
                                                stShopper = ST_FLEE
                                            ELSE
                                                GENERATE_RANDOM_INT_IN_RANGE 0 100 stShopper
                                                IF stShopper < 20
                                                    GOSUB FightPlayer
                                                    stShopper = ST_FIGHT
                                                ELSE
                                                    GOSUB FleeToExit
                                                    stShopper = ST_FLEE
                                                ENDIF
                                            ENDIF
                                        ENDIF
                                    ELSE
                                        /* ninguem encostou nele: mantem o treino
                                           (conserta trombadas e travamentos) */
                                        IF IS_CHAR_PLAYING_ANIM pedShopper "COPBROWSE_NOD"
                                        AND LOCATE_CHAR_ANY_MEANS_3D pedShopper 756.2289 8.8118 1000.7 1.5 1.5 2.0 FALSE
                                            IF stShopper < ST_FIXED
                                                stShopper += 1
                                            ELSE
                                                GOSUB RestoreShopper
                                                stShopper = ST_CALM
                                            ENDIF
                                        ELSE
                                            IF stShopper < ST_FIXED
                                                stShopper += 1
                                            ELSE
                                                GOSUB RestoreShopper
                                                stShopper = ST_CALM
                                            ENDIF
                                        ENDIF
                                    ENDIF
                                ENDIF
                            ENDIF
                        ENDIF
                    ENDIF
                ENDIF
            ENDIF
        ENDIF

    RETURN

/* ========================================================================= */
/* PEDESTRE FUMANTE NA CALCADA EXTERNA                                       */
/* ------------------------------------------------------------------------- */
/* Este pedestre nao depende da academia estar carregada: ele nasce na rua,   */
/* perto da porta, sempre que o tempo permitir. Ele NAO usa estado salvo,     */
/* para nao gastar variavel local (o limite do jogo sao 32).                  */
/* ========================================================================= */
UpdateOutside:
    IF DOES_CHAR_EXIST pedSmoker
        /* Morreu: deixa o corpo para o jogo recolher e nao cria outro no lugar */
        IF IS_CHAR_DEAD pedSmoker
            MARK_CHAR_AS_NO_LONGER_NEEDED pedSmoker
            GOSUB FreeSmokingAnim
            RETURN
        ENDIF

        /* Foi agredido de verdade (soco, arma branca, tiro, bomba, carro)?
           Entao foge e o mod solta ele. Trombada nao conta. */
        IF HAS_CHAR_BEEN_DAMAGED_BY_WEAPON pedSmoker WEAPONTYPE_ANYMELEE
        OR NOT IS_CHAR_HEALTH_GREATER pedSmoker 95
            GOSUB ReleaseSmoker
            RETURN
        ENDIF

        /* Arma apontada para ele (mira de punhos nao conta) */
        curPed = 0
        GET_CHAR_PLAYER_IS_TARGETING 0 curPed
        IF curPed = pedSmoker
            GET_CURRENT_CHAR_WEAPON scplayer curPed
            IF curPed >= WT_HAND_FIRST
            AND curPed <= WT_HAND_LAST
                GOSUB ReleaseSmoker
                RETURN
            ENDIF
        ENDIF

        /* Tiro por perto? (precisa ser arma de fogo) */
        IF IS_CHAR_SHOOTING scplayer
        AND LOCATE_CHAR_ANY_MEANS_CHAR_3D pedSmoker scplayer 12.0 12.0 8.0 FALSE
            GET_CURRENT_CHAR_WEAPON scplayer curPed
            IF curPed >= WT_GUN_FIRST
            AND curPed <= WT_GUN_LAST
                GOSUB ReleaseSmoker
                RETURN
            ENDIF
        ENDIF

        /* Comecou a chover: ele sai da calcada andando (busca abrigo) */
        READ_MEMORY 0xC8131C 2 FALSE curPed
        IF curPed = 8
        OR curPed = 16
            GOSUB SendSmokerAway
            RETURN
        ENDIF
        READ_MEMORY 0xC81320 2 FALSE curPed
        IF curPed = 8
        OR curPed = 16
            GOSUB SendSmokerAway
            RETURN
        ENDIF

        /* Jogador foi embora (e nao esta na academia): solta o pedestre */
        IF NOT LOCATE_CHAR_ANY_MEANS_3D scplayer 2228.751 -1720.5699 13.5531 60.0 60.0 40.0 FALSE
        AND NOT LOCATE_CHAR_ANY_MEANS_3D scplayer 765.0 5.0 1000.0 35.0 35.0 20.0 FALSE
            GOSUB SendSmokerAway
            RETURN
        ENDIF

        /* Trombada/empurrao cancelou a animacao? Volta a fumar no lugar certo */
        IF IS_CHAR_PLAYING_ANIM pedSmoker "M_SMKSTND_LOOP"
            IF NOT LOCATE_CHAR_ANY_MEANS_3D pedSmoker 2228.751 -1720.5699 13.5531 1.5 1.5 2.0 FALSE
                GOSUB RestoreSmoker
            ENDIF
        ELSE
            GOSUB RestoreSmoker
        ENDIF
        RETURN
    ENDIF

    /* ---------------------------------------------------------------------
       Ninguem esta fumando ali: cria um novo pedestre? (spawn)
       --------------------------------------------------------------------- */

    /* O fumante anterior foi espantado agora? espera um pouco */
    IF smokerCooldown > 0
        GET_GAME_TIMER curPed
        IF curPed < smokerCooldown
            RETURN
        ENDIF
    ENDIF

    /* Chovendo: nao cria */
    READ_MEMORY 0xC8131C 2 FALSE curPed
    IF curPed = 8
    OR curPed = 16
        RETURN
    ENDIF
    READ_MEMORY 0xC81320 2 FALSE curPed
    IF curPed = 8
    OR curPed = 16
        RETURN
    ENDIF

    /* So nasce se o jogador estiver NA RUA, perto da porta da academia */
    IF NOT LOCATE_CHAR_ANY_MEANS_3D scplayer 2228.751 -1720.5699 13.5531 40.0 40.0 25.0 FALSE
        RETURN
    ENDIF

    REQUEST_MODEL HMYCR
    REQUEST_ANIMATION "SMOKING"
    WHILE NOT HAS_MODEL_LOADED HMYCR
       OR NOT HAS_ANIMATION_LOADED "SMOKING"
        WAIT 0
        IF NOT IS_PLAYER_PLAYING 0
            RETURN
        ENDIF
        /* Se o jogador entrou na academia ou se afastou, desiste deste spawn */
        IF NOT LOCATE_CHAR_ANY_MEANS_3D scplayer 2228.751 -1720.5699 13.5531 60.0 60.0 40.0 FALSE
            RETURN
        ENDIF
        IF LOCATE_CHAR_ANY_MEANS_3D scplayer 765.0 5.0 1000.0 35.0 35.0 20.0 FALSE
            RETURN
        ENDIF
    ENDWHILE

    CREATE_CHAR PEDTYPE_CIVMALE HMYCR 2228.751 -1720.5699 13.5531 pedSmoker
    SET_CHAR_HEADING pedSmoker 137.4583
    SET_CHAR_AREA_VISIBLE pedSmoker 0
    SET_CHAR_STAY_IN_SAME_PLACE pedSmoker TRUE
    TASK_PLAY_ANIM pedSmoker "M_SMKSTND_LOOP" "SMOKING" 4.0 TRUE FALSE FALSE FALSE -1
    MARK_MODEL_AS_NO_LONGER_NEEDED HMYCR
    RETURN

/* ========================================================================= */
/* ROTINAS COMPARTILHADAS (usam sempre curPed)                               */
/* ========================================================================= */

/* Corre ate a porta da academia e sera apagado ao chegar */
FleeToExit:
    SET_CHAR_STAY_IN_SAME_PLACE curPed FALSE
    CLEAR_CHAR_TASKS curPed
    TASK_SAY curPed CONTEXT_GLOBAL_GUN_RUN
    TASK_GO_STRAIGHT_TO_COORD curPed doorX doorY 1000.7 PEDMOVE_RUN 60000
    RETURN

/* Revida o jogador (briga de mao limpa) */
FightPlayer:
    SET_CHAR_STAY_IN_SAME_PLACE curPed FALSE
    CLEAR_CHAR_TASKS curPed
    TASK_SAY curPed CONTEXT_GLOBAL_FIGHT
    TASK_KILL_CHAR_ON_FOOT curPed scplayer
    RETURN

/* Chegou na porta? Sai da academia (apagado) */
FleeTick:
    IF LOCATE_CHAR_ANY_MEANS_3D curPed doorX doorY 1000.7 3.0 3.0 3.5 FALSE
        DELETE_CHAR curPed
        RETURN
    ENDIF
    /* Se por algum motivo ele saiu do predio/area, tambem e apagado */
    IF NOT LOCATE_CHAR_ANY_MEANS_3D curPed 765.0 5.0 1000.0 60.0 60.0 30.0 FALSE
        DELETE_CHAR curPed
    ENDIF
    RETURN

/* Solta o fumante para a IA do jogo (foge e some) */
ReleaseSmoker:
    SET_CHAR_STAY_IN_SAME_PLACE pedSmoker FALSE
    CLEAR_CHAR_TASKS pedSmoker
    TASK_SAY pedSmoker CONTEXT_GLOBAL_PAIN_PANIC
    TASK_SMART_FLEE_CHAR pedSmoker scplayer 40.0 30000
    MARK_CHAR_AS_NO_LONGER_NEEDED pedSmoker
    GOSUB FreeSmokingAnim
    pedSmoker = 0
    /* Ninguem volta a fumar ali por um tempo */
    GET_GAME_TIMER smokerCooldown
    smokerCooldown += SMOKE_DELAY
    RETURN

/* Descarrega o IFP do cigarro (usado quando ninguem mais esta fumando) */
FreeSmokingAnim:
    IF HAS_ANIMATION_LOADED "SMOKING"
        REMOVE_ANIMATION "SMOKING"
    ENDIF
    RETURN

/* Manda o fumante embora andando (usado quando comeca a chover ou quando o
   jogador vai embora): ele sai da calcada e volta para a IA do jogo */
SendSmokerAway:
    SET_CHAR_STAY_IN_SAME_PLACE pedSmoker FALSE
    CLEAR_CHAR_TASKS pedSmoker
    TASK_WANDER_STANDARD pedSmoker
    MARK_CHAR_AS_NO_LONGER_NEEDED pedSmoker
    GOSUB FreeSmokingAnim
    pedSmoker = 0
    GET_GAME_TIMER smokerCooldown
    smokerCooldown += SMOKE_DELAY
    RETURN

/* Solta o halter do musculoso 1 */
DropDumbbell1:
    IF DOES_OBJECT_EXIST objDumbbell1
        IF DOES_CHAR_EXIST curPed
            DROP_OBJECT curPed TRUE
        ENDIF
        DETACH_OBJECT objDumbbell1 0.0 0.0 0.0 FALSE
        SET_OBJECT_DYNAMIC objDumbbell1 TRUE
    ENDIF
    RETURN

/* Solta o halter do musculoso 2 */
DropDumbbell2:
    IF DOES_OBJECT_EXIST objDumbbell2
        IF DOES_CHAR_EXIST curPed
            DROP_OBJECT curPed TRUE
        ENDIF
        DETACH_OBJECT objDumbbell2 0.0 0.0 0.0 FALSE
        SET_OBJECT_DYNAMIC objDumbbell2 TRUE
    ENDIF
    RETURN

/* Reposiciona e reinicia a animacao (usado quando o jogo cancela o treino) */
RestoreJogger1:
    SET_CHAR_COORDINATES pedJogger1 771.4 10.9204 1000.849
    SET_CHAR_HEADING pedJogger1 270.0
    SET_CHAR_STAY_IN_SAME_PLACE pedJogger1 TRUE
    TASK_PLAY_ANIM pedJogger1 "GYM_TREAD_JOG" "GYMNASIUM" 4.0 TRUE FALSE FALSE FALSE -1
    RETURN

RestoreJogger2:
    SET_CHAR_COORDINATES pedJogger2 771.4 12.3027 1000.849
    SET_CHAR_HEADING pedJogger2 270.0
    SET_CHAR_STAY_IN_SAME_PLACE pedJogger2 TRUE
    TASK_PLAY_ANIM pedJogger2 "GYM_TREAD_JOG" "GYMNASIUM" 4.0 TRUE FALSE FALSE FALSE -1
    RETURN

RestoreLifter1:
    SET_CHAR_COORDINATES pedLifter1 771.4566 7.3739 1000.71
    SET_CHAR_HEADING pedLifter1 0.0
    SET_CHAR_STAY_IN_SAME_PLACE pedLifter1 TRUE
    IF DOES_OBJECT_EXIST objDumbbell1
        IF NOT IS_CHAR_HOLDING_OBJECT pedLifter1 objDumbbell1
            /* O halter caiu no chao (empurrao, queda, reacao): devolve ele
               para a mao. Primeiro volta a ser um objeto parado, na altura
               do banco, e so depois o atleta pega de novo. */
            SET_OBJECT_DYNAMIC objDumbbell1 FALSE
            SET_OBJECT_COORDINATES objDumbbell1 771.4566 7.3739 1000.76
            SET_OBJECT_HEADING objDumbbell1 90.0
            TASK_PICK_UP_OBJECT pedLifter1 objDumbbell1 0.04 0.0 -0.02 PED_HANDR HOLD_ORIENTATE_BONE_FULL "NULL" "NULL" -1
        ENDIF
    ENDIF
    TASK_PLAY_ANIM pedLifter1 "GYM_BARBELL" "FREEWEIGHTS" 4.0 TRUE FALSE FALSE FALSE -1
    RETURN

RestoreLifter2:
    SET_CHAR_COORDINATES pedLifter2 773.6576 7.4052 1000.709
    SET_CHAR_HEADING pedLifter2 0.0
    SET_CHAR_STAY_IN_SAME_PLACE pedLifter2 TRUE
    IF DOES_OBJECT_EXIST objDumbbell2
        IF NOT IS_CHAR_HOLDING_OBJECT pedLifter2 objDumbbell2
            /* O halter caiu no chao (empurrao, queda, reacao): devolve ele
               para a mao. Primeiro volta a ser um objeto parado, na altura
               do banco, e so depois o atleta pega de novo. */
            SET_OBJECT_DYNAMIC objDumbbell2 FALSE
            SET_OBJECT_COORDINATES objDumbbell2 773.6576 7.4052 1000.76
            SET_OBJECT_HEADING objDumbbell2 90.0
            TASK_PICK_UP_OBJECT pedLifter2 objDumbbell2 0.04 0.0 -0.02 PED_HANDR HOLD_ORIENTATE_BONE_FULL "NULL" "NULL" -1
        ENDIF
    ENDIF
    TASK_PLAY_ANIM pedLifter2 "GYM_BARBELL" "FREEWEIGHTS" 4.0 TRUE FALSE FALSE FALSE -1
    RETURN

RestoreBoxer1:
    SET_CHAR_COORDINATES pedBoxer1 767.2722 -2.4724 1000.719
    SET_CHAR_HEADING pedBoxer1 185.9322
    SET_CHAR_STAY_IN_SAME_PLACE pedBoxer1 TRUE
    TASK_PLAY_ANIM pedBoxer1 "GYM_SHADOWBOX" "GYMNASIUM" 4.0 TRUE FALSE FALSE FALSE -1
    RETURN

RestoreBoxer2:
    SET_CHAR_COORDINATES pedBoxer2 769.6318 -2.4529 1000.724
    SET_CHAR_HEADING pedBoxer2 158.0452
    SET_CHAR_STAY_IN_SAME_PLACE pedBoxer2 TRUE
    TASK_PLAY_ANIM pedBoxer2 "GYM_SHADOWBOX" "GYMNASIUM" 4.0 TRUE FALSE FALSE FALSE -1
    RETURN

RestoreShopper:
    SET_CHAR_COORDINATES pedShopper 756.2289 8.8118 1000.7
    SET_CHAR_HEADING pedShopper 67.2502
    SET_CHAR_STAY_IN_SAME_PLACE pedShopper TRUE
    TASK_PLAY_ANIM pedShopper "COPBROWSE_NOD" "COP_AMBIENT" 4.0 TRUE FALSE FALSE FALSE -1
    RETURN

RestoreSmoker:
    SET_CHAR_COORDINATES pedSmoker 2228.751 -1720.5699 13.5531
    SET_CHAR_HEADING pedSmoker 137.4583
    TASK_PLAY_ANIM pedSmoker "M_SMKSTND_LOOP" "SMOKING" 4.0 TRUE FALSE FALSE FALSE -1
    RETURN

/* ========================================================================= */
/* LIMPEZA SEGURA DE ENTIDADES E MEMORIA                                     */
/* ========================================================================= */
CleanupGym:
    /* Halteres */
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

    /* Objetos do cenario */
    IF DOES_OBJECT_EXIST objStool1
        DELETE_OBJECT objStool1
    ENDIF
    objStool1 = 0
    IF DOES_OBJECT_EXIST objStool2
        DELETE_OBJECT objStool2
    ENDIF
    objStool2 = 0
    IF DOES_OBJECT_EXIST objStool3
        DELETE_OBJECT objStool3
    ENDIF
    objStool3 = 0
    IF DOES_OBJECT_EXIST objStool4
        DELETE_OBJECT objStool4
    ENDIF
    objStool4 = 0
    IF DOES_OBJECT_EXIST objTreadmill1
        DELETE_OBJECT objTreadmill1
    ENDIF
    objTreadmill1 = 0
    IF DOES_OBJECT_EXIST objTreadmill2
        DELETE_OBJECT objTreadmill2
    ENDIF
    objTreadmill2 = 0
    IF DOES_OBJECT_EXIST objBench1
        DELETE_OBJECT objBench1
    ENDIF
    objBench1 = 0
    IF DOES_OBJECT_EXIST objBench2
        DELETE_OBJECT objBench2
    ENDIF
    objBench2 = 0
    IF DOES_OBJECT_EXIST objRack
        DELETE_OBJECT objRack
    ENDIF
    objRack = 0

    /* Peds (inclusive os corpos e os que estavam fugindo) */
    IF DOES_CHAR_EXIST pedJogger1
        SET_CHAR_STAY_IN_SAME_PLACE pedJogger1 FALSE
        DELETE_CHAR pedJogger1
    ENDIF
    pedJogger1 = 0
    stJogger1 = ST_CALM

    IF DOES_CHAR_EXIST pedJogger2
        SET_CHAR_STAY_IN_SAME_PLACE pedJogger2 FALSE
        DELETE_CHAR pedJogger2
    ENDIF
    pedJogger2 = 0
    stJogger2 = ST_CALM

    IF DOES_CHAR_EXIST pedLifter1
        SET_CHAR_STAY_IN_SAME_PLACE pedLifter1 FALSE
        DELETE_CHAR pedLifter1
    ENDIF
    pedLifter1 = 0
    stLifter1 = ST_CALM

    IF DOES_CHAR_EXIST pedLifter2
        SET_CHAR_STAY_IN_SAME_PLACE pedLifter2 FALSE
        DELETE_CHAR pedLifter2
    ENDIF
    pedLifter2 = 0
    stLifter2 = ST_CALM

    IF DOES_CHAR_EXIST pedBoxer1
        SET_CHAR_STAY_IN_SAME_PLACE pedBoxer1 FALSE
        DELETE_CHAR pedBoxer1
    ENDIF
    pedBoxer1 = 0
    stBoxer1 = ST_CALM

    IF DOES_CHAR_EXIST pedBoxer2
        SET_CHAR_STAY_IN_SAME_PLACE pedBoxer2 FALSE
        DELETE_CHAR pedBoxer2
    ENDIF
    pedBoxer2 = 0
    stBoxer2 = ST_CALM

    IF DOES_CHAR_EXIST pedShopper
        SET_CHAR_STAY_IN_SAME_PLACE pedShopper FALSE
        DELETE_CHAR pedShopper
    ENDIF
    pedShopper = 0
    stShopper = ST_CALM

    /* Animacoes carregadas pelo script */
    IF HAS_ANIMATION_LOADED "FREEWEIGHTS"
        REMOVE_ANIMATION "FREEWEIGHTS"
    ENDIF
    IF HAS_ANIMATION_LOADED "GYMNASIUM"
        REMOVE_ANIMATION "GYMNASIUM"
    ENDIF
    IF HAS_ANIMATION_LOADED "COP_AMBIENT"
        REMOVE_ANIMATION "COP_AMBIENT"
    ENDIF

    RETURN

}
SCRIPT_END
