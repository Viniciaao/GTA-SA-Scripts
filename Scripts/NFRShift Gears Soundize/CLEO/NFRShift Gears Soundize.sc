SCRIPT_START
{
NOP

/*
    ============================================================================
    NFRSHIFT GEARS SOUNDIZE - cambio manual para GTA SA (recriacao do
    "NFRShift Gears Mod") com integracao ao mod Soundize (Junior_Djjr)
    ----------------------------------------------------------------------------
    O QUE E ISSO:

    Recriacao do cambio manual do NFRShift Gears Mod (marcha neutra, 1 a 6,
    re, motor que morre, embreagem no Shift, alavanca em H no mouse para
    carros e botoes para motos/quadriciclos), reconstruido em cima do sistema
    de som do mod Soundize:

        https://www.mixmods.com.br/2026/06/soundize/
        https://github.com/JuniorDjjr/soundize/wiki/

    COMO A INTEGRACAO FUNCIONA:

    1. O script pega a API oficial do Soundize pela biblioteca
       "Soundize (Junior_Djjr).asi" (Ext_IsVehicleUsingAnyBank,
       Ext_GetVehicleRPM, Ext_GetVehicleMaxRPM, Ext_GetVehicleGear).

    2. O Soundize USA AS MARCHAS DO JOGO e nao muda a fisica (palavra do
       autor, Djjr). Entao o cambio manual manda no jogo, e o som segue:
         - A marcha escolhida e ESCRITA NO PROPRIO JOGO
           (CVehicle+0x4B4, m_nCurrentGear) todo frame, junto do contador de
           troca (CVehicle+0x4B8) zerado. Fisica e som passam a seguir o
           cambio manual: largada parado na 5a tem a fisica fraca de 5a e o
           som toca na 5a baixa, sem passar marchas sozinho
           (opcao WriteGearToGame).
         - O acelerador e cortado no corte-giro (aGears[gear]
           .fChangeUpVelocity x ShiftThreshold 0.97): a velocidade nunca
           cruza o ponto de troca, entao nem o jogo nem o som sobem de
           marcha sozinhos: quem sobe e o jogador.
         - A marcha tambem e escrita no byte de marcha da entidade de audio
           do jogo (CAEVehicleAudioEntity+0xAA), igual o mod original fazia
           (opcao WriteGearToAudio) - cobre o som vanilla e o neutral/re.
         - Nos carros SEM som do Soundize (banco nativo), o comportamento do
           mod original e mantido (SwitchCarGearAudio + efeito de suspensao).
         - Sair parado em marcha alta: a marcha fica CRAVADA na sua - a
           fisica fraca da marcha alta segura o carro (largada lenta e
           arrastada de verdade) e o som fica na SUA marcha girando baixo
           (afogando), sem o jogo passar marcha nenhuma. Acelerando muito
           tempo fora da faixa da marcha o motor morre (potentes aguentam
           mais, pela forca do motor em GetGearMinSpeedLimit).
         - Abuso de partida realista: cada tranco falhado (ligar em marcha
           sem embreagem) causa dano leve no motor (StarterDamage).

    3. HUD: o GearHelper mostra a marcha selecionada, a marcha que o SOM
       esta tocando (API do Soundize quando houver banco ativo, senao a
       marcha real do jogo) e uma barra de RPM em tempo real.

    NOVIDADES DESSA VERSAO:

    - [Soundize] Enabled ................ integração com a API do Soundize
    - [Soundize] WriteGearToAudio ...... crava a marcha no audio do jogo
    - [Soundize] InhibitShiftFxVanilla . tira o efeito de troca de marcha do
                                         audio vanilla (igual mod original)
    - [config] DisableShiftAnim ........ 1 = SEM a animacao de "pular marcha"
                                         (TASK_PLAY_ANIM changegear). Use 1
                                         se voce usa VEHIK do zzpuma (o VehIK
                                         ja anima a mao no cambio sozinho e a
                                         animacao antiga briga com ele).
    - [config] ShowRPMBar .............. barra de RPM via API do Soundize
    - [config] GearLimitMode ........... 0 = janela da handling (recomendado
                                         com Soundize), 1 = proporcional ao
                                         numero de marchas (estilo antigo),
                                         2 = tabela fixa de km/h
    - [config] ShiftThreshold .......... onde fica o corte-giro da marcha
                                         (0.97 = vai NO TALO igual na vida
                                         real; o RPM fica cravado no limite
                                         em vez de hesitar/cortar antes)
    - Acelerar PARADO (ponto morto ou embreagem pisada) = rev de verdade:
      o gas real chega no motor e o carro fica segurado no lugar - o RPM
      sobe rapido ate o talo, sem as rodas girarem (nativo, Soundize ok)
    - Sair PARADO numa marcha alta (ex.: 5a) agora sai fraco/afogando, em
      vez de acelerar normal enquanto o som passa as marchas sozinho
    - [config] StallSpeedFactor ........ 0.5: se a velocidade cair abaixo de
                                         50% da faixa da marcha (embreagem
                                         solta), o motor labuta e MORRE
                                         (falta velocidade pra marcha)
    - [config] RealisticStart .......... partida realista (simulada e pedida
                                         pelo povo):
                                           * parado EM MARCHA + E (sem embre-
                                             agem) = o carro da o tranco com a
                                             forca dele proprio e o motor
                                             MORRE (afogou), como na vida real
                                           * PONTO MORTO + E = fica ligado
                                           * rolando em marcha (>= ~7 km/h) +
                                             E = pega no tranco (bump start)
                                           * pisar a embreagem durante o
                                             tranco salva o engate
                                         Funciona com som do Soundize e
                                         vanilla (e fisica do jogo).

    REQUERIMENTOS:

    - CLEO 4.4.4+ (ou CLEO 5) e CLEO+
    - Soundize (opcional, mas o script foi feito para ele). Sem o Soundize o
      script roda em modo vanilla (audio original do jogo).
    - Os arquivos visuais do mod original: gbox.txd (texturas gearbox, gear,
      gcenter, gup, gdown, tail, tail2) na pasta CLEO, e opcionalmente a
      pasta cleo/NFR Shift Gear/gears_sound com 1.mp3 2.mp3 3.mp3 4.mp3
      5.mp3 gas.mp3 (sons de engate).
    - As chaves de texto (MMSG1..MMSG4, SNDSOFF, SNDSOLD) estao no arquivo
      NFRShift Gears Soundize.fxt (colocar em CLEO/CLEO_TEXT).

    COMO USAR (igual mod original):

    - Segure Shift (ManualClutchKey) por um instante para pisar na embreagem;
      o alavanca em H aparece (mouse) para carros/caminhoes. Motos e quads
      trocam com botao esquerdo (sobe) e direito (desce) do mouse.
    - Toques rapidos na embreagem (< 50 ms) nao entram no cambio manual.
    - Na barra central (ponto morto) o carro fica em ponto morto.
    - Segure o botao Circle (controle) no ponto morto para engatar a re.
    - Se soltar o acelerador com o RPM muito baixo, o motor morre (segura E
      para ligar de novo).

    CREDITOS:
    - NFRShift Gears Mod (base dessa recriacao)
    - Junior_Djjr - Soundize (sistema de som e API)
    - zzpuma - VehIK (motivo da opcao DisableShiftAnim)
    ============================================================================
*/

// ============================ VARIAVEIS ============================
LVAR_INT scplayer iCar pVeh iSubclass gear pGasPedal clutchKey
LVAR_INT fps_set iniGearHelper iniNoAnim iniShowRPM iniGearLimitMode iniRealStart clutchRevSim iniWriteGearGame
LVAR_INT sndEnabled sndWriteGear iniInhibitVanillaFx
LVAR_INT sndLoaded pSoundize pIsBank pGetRPM pGetMaxRPM pGetGear
LVAR_FLOAT mouseX mouseY gear_posX gear_posY gear_pointer fThresh

GET_PLAYER_CHAR 0 scplayer

LOAD_TEXTURE_DICTIONARY gbox
LOAD_SPRITE 1 "gearbox"
LOAD_SPRITE 2 "gear"
LOAD_SPRITE 3 "gcenter"
LOAD_SPRITE 4 "gup"
LOAD_SPRITE 5 "gdown"
LOAD_SPRITE 6 "tail"
LOAD_SPRITE 7 "tail2"

// ============================ INI ============================
    IF NOT READ_INT_FROM_INI_FILE "cleo/NFRShift Gears Soundize.ini" "Manual" "ManualClutchKey" clutchKey
        WRITE_INT_TO_INI_FILE 160 "cleo/NFRShift Gears Soundize.ini" "Manual" "ManualClutchKey"
    ENDIF

    IF NOT READ_FLOAT_FROM_INI_FILE "cleo/NFRShift Gears Soundize.ini" "Manual" "gPointerWidth" gear_pointer
        WRITE_FLOAT_TO_INI_FILE 49.0 "cleo/NFRShift Gears Soundize.ini" "Manual" "gPointerWidth"
    ENDIF

    IF NOT READ_INT_FROM_INI_FILE "cleo/NFRShift Gears Soundize.ini" "config" "FPSstableMode" fps_set
        WRITE_INT_TO_INI_FILE 0 "cleo/NFRShift Gears Soundize.ini" "config" "FPSstableMode"
    ENDIF

    IF NOT READ_INT_FROM_INI_FILE "cleo/NFRShift Gears Soundize.ini" "config" "GearHelper" iniGearHelper
        WRITE_INT_TO_INI_FILE 1 "cleo/NFRShift Gears Soundize.ini" "config" "GearHelper"
    ENDIF

    // NOVO: barra de RPM usando o RPM do Soundize
    IF NOT READ_INT_FROM_INI_FILE "cleo/NFRShift Gears Soundize.ini" "config" "ShowRPMBar" iniShowRPM
        WRITE_INT_TO_INI_FILE 1 "cleo/NFRShift Gears Soundize.ini" "config" "ShowRPMBar"
    ENDIF

    // NOVO: 1 = desativa a animacao de pular marcha (compatibilidade com VEHIK)
    IF NOT READ_INT_FROM_INI_FILE "cleo/NFRShift Gears Soundize.ini" "config" "DisableShiftAnim" iniNoAnim
        WRITE_INT_TO_INI_FILE 0 "cleo/NFRShift Gears Soundize.ini" "config" "DisableShiftAnim"
    ENDIF

    // NOVO: como calcular o limite de velocidade de cada marcha
    // 0 = janela da handling (aGears) | 1 = proporcional | 2 = tabela fixa km/h
    IF NOT READ_INT_FROM_INI_FILE "cleo/NFRShift Gears Soundize.ini" "config" "GearLimitMode" iniGearLimitMode
        WRITE_INT_TO_INI_FILE 0 "cleo/NFRShift Gears Soundize.ini" "config" "GearLimitMode"
    ENDIF

    // NOVO: onde fica o corte-giro da marcha (0.97 = vai no talo)
    IF NOT READ_FLOAT_FROM_INI_FILE "cleo/NFRShift Gears Soundize.ini" "config" "ShiftThreshold" fThresh
        WRITE_FLOAT_TO_INI_FILE 0.97 "cleo/NFRShift Gears Soundize.ini" "config" "ShiftThreshold"
    ENDIF

    // NOVO: integração com o Soundize
    IF NOT READ_INT_FROM_INI_FILE "cleo/NFRShift Gears Soundize.ini" "Soundize" "Enabled" sndEnabled
        WRITE_INT_TO_INI_FILE 1 "cleo/NFRShift Gears Soundize.ini" "Soundize" "Enabled"
    ENDIF

    // NOVO: cravar a marcha escolhida no byte de marcha do audio do jogo
    IF NOT READ_INT_FROM_INI_FILE "cleo/NFRShift Gears Soundize.ini" "Soundize" "WriteGearToAudio" sndWriteGear
        WRITE_INT_TO_INI_FILE 1 "cleo/NFRShift Gears Soundize.ini" "Soundize" "WriteGearToAudio"
    ENDIF

    // NOVO: zerar o efeito de troca de marcha do audio vanilla (fora do Soundize)
    IF NOT READ_INT_FROM_INI_FILE "cleo/NFRShift Gears Soundize.ini" "Soundize" "InhibitShiftFxVanilla" iniInhibitVanillaFx
        WRITE_INT_TO_INI_FILE 1 "cleo/NFRShift Gears Soundize.ini" "Soundize" "InhibitShiftFxVanilla"
    ENDIF

    // NOVO: escrever a marcha no proprio jogo (fisica + som seguem o cambio)
    IF NOT READ_INT_FROM_INI_FILE "cleo/NFRShift Gears Soundize.ini" "Soundize" "WriteGearToGame" iniWriteGearGame
        WRITE_INT_TO_INI_FILE 1 "cleo/NFRShift Gears Soundize.ini" "Soundize" "WriteGearToGame"
    ENDIF

    // NOVO: rev com embreagem pisada (parado), simulado pela velocidade
    // interna da transmissao (sem freio, sem o carro andar)
    IF NOT READ_INT_FROM_INI_FILE "cleo/NFRShift Gears Soundize.ini" "config" "ClutchRevSim" clutchRevSim
        WRITE_INT_TO_INI_FILE 1 "cleo/NFRShift Gears Soundize.ini" "config" "ClutchRevSim"
    ENDIF

    // NOVO: partida realista (tranco em marcha / ligar em ponto morto)
    IF NOT READ_INT_FROM_INI_FILE "cleo/NFRShift Gears Soundize.ini" "config" "RealisticStart" iniRealStart
        WRITE_INT_TO_INI_FILE 1 "cleo/NFRShift Gears Soundize.ini" "config" "RealisticStart"
    ENDIF

    // limite minimo/maximo saneavel para o ShiftThreshold
    IF fThresh < 0.3
    OR fThresh > 1.0
        fThresh = 0.97
    ENDIF
//

// ============================ SOUNDIZE API ============================
    sndLoaded = 0

    IF sndEnabled = 1
        IF GET_LOADED_LIBRARY "Soundize (Junior_Djjr).asi" pSoundize
            GET_DYNAMIC_LIBRARY_PROCEDURE "Ext_IsVehicleUsingAnyBank" pSoundize pIsBank
            GET_DYNAMIC_LIBRARY_PROCEDURE "Ext_GetVehicleRPM" pSoundize pGetRPM
            GET_DYNAMIC_LIBRARY_PROCEDURE "Ext_GetVehicleMaxRPM" pSoundize pGetMaxRPM
            GET_DYNAMIC_LIBRARY_PROCEDURE "Ext_GetVehicleGear" pSoundize pGetGear

            IF pIsBank > 0
            AND pGetRPM > 0
            AND pGetMaxRPM > 0
            AND pGetGear > 0
                sndLoaded = 1
            ELSE
                PRINT_HELP SNDSOLD
            ENDIF
        ELSE
            PRINT_HELP SNDSOFF
        ENDIF
    ENDIF
//

WHILE TRUE
    WAIT 0

    IF IS_CHAR_SITTING_IN_ANY_CAR scplayer
        STORE_CAR_CHAR_IS_IN_NO_SAVE scplayer iCar
        GET_VEHICLE_SUBCLASS iCar iSubclass

        IF iSubclass = VEHICLE_SUBCLASS_AUTOMOBILE
        OR iSubclass = VEHICLE_SUBCLASS_MTRUCK
        OR iSubclass = VEHICLE_SUBCLASS_BIKE
        OR iSubclass = VEHICLE_SUBCLASS_QUAD

            GET_VEHICLE_POINTER iCar pVeh

            pGasPedal = 0xB73458
            pGasPedal += 0x20

            // Trava aqui dirigindo (aplica os limites de marcha) ate pisar na embreagem
            CLEO_CALL Transmission 0 iCar gear pVeh fThresh iniGearLimitMode sndLoaded pIsBank sndWriteGear iniInhibitVanillaFx iniGearHelper pGetRPM pGetMaxRPM pGetGear iniShowRPM iniRealStart iniWriteGearGame clutchRevSim

            IF IS_KEY_PRESSED clutchKey // Embreagem
                SET_CAMERA_CONTROL FALSE
                timera = 0
                WHILE IS_KEY_PRESSED clutchKey
                AND timera < 50
                    WAIT 0
                    WRITE_MEMORY pGasPedal 2 0 FALSE
                ENDWHILE

                IF timera < 50
                    SET_CAMERA_CONTROL TRUE
                    CONTINUE
                ENDIF

                SET_CAMERA_CONTROL FALSE

                IF iniNoAnim = 0
                    REQUEST_ANIMATION changegear
                    WHILE NOT HAS_ANIMATION_LOADED changegear
                        WAIT 0
                    ENDWHILE
                ENDIF

                IF DOES_VEHICLE_EXIST iCar

                    WHILE IS_KEY_PRESSED clutchKey
                    AND IS_CHAR_IN_CAR scplayer iCar
                        WAIT 0

                        IF NOT DOES_VEHICLE_EXIST iCar
                            BREAK
                        ENDIF

                        // Embreagem pisada: desacopla o motor. PARADO,
                        // acelerar da um rev DE VERDADE (gas real no motor +
                        // carro segurado no lugar); em movimento so desacopla
                        // (o carro desacelera so de arrasto, SEM freio)
                        IF IS_BUTTON_PRESSED 0 16
                            IF IS_CAR_STOPPED iCar
                                WRITE_MEMORY pGasPedal 2 255 FALSE
                                APPLY_BRAKES_TO_PLAYERS_CAR 0 1
                                CLEO_CALL ClutchRevSim 0 iCar fThresh clutchRevSim
                            ELSE
                                WRITE_MEMORY pGasPedal 2 0 FALSE
                                APPLY_BRAKES_TO_PLAYERS_CAR 0 0
                            ENDIF
                        ELSE
                            WRITE_MEMORY pGasPedal 2 0 FALSE
                            APPLY_BRAKES_TO_PLAYERS_CAR 0 0
                        ENDIF

                        IF IS_KEY_PRESSED VK_KEY_E
                            SET_CAR_ENGINE_ON iCar 1
                        ENDIF

                        IF IS_BUTTON_PRESSED 0 14
                        AND IS_CAR_STOPPED iCar
                            APPLY_BRAKES_TO_PLAYERS_CAR 0 1
                        ELSE
                            IF NOT IS_CAR_STOPPED iCar
                            AND IS_BUTTON_PRESSED 0 14
                                WRITE_MEMORY pGasPedal 2 -255 FALSE
                            ENDIF
                        ENDIF

                        IF iSubclass = VEHICLE_SUBCLASS_BIKE
                        OR iSubclass = VEHICLE_SUBCLASS_QUAD

                            IF NOT READ_FLOAT_FROM_INI_FILE "cleo/NFRShift Gears Soundize.ini" "Manual" "BikeGearPosX" gear_pointer
                                WRITE_FLOAT_TO_INI_FILE 500.0 "cleo/NFRShift Gears Soundize.ini" "Manual" "BikeGearPosX"
                            ENDIF

                            IF NOT READ_FLOAT_FROM_INI_FILE "cleo/NFRShift Gears Soundize.ini" "Manual" "BikeGearPosY" mouseY
                                WRITE_FLOAT_TO_INI_FILE 350.0 "cleo/NFRShift Gears Soundize.ini" "Manual" "BikeGearPosY"
                            ENDIF

                            DRAW_SPRITE 3 gear_pointer mouseY 70.0 150.0 255 255 255 255
                            USE_TEXT_COMMANDS 0

                            IF IS_KEY_PRESSED VK_LBUTTON
                            AND gear >= 0
                            AND gear <= 6
                                gear += 1
                                IF gear >= 7
                                    gear = 6
                                ENDIF
                                WHILE IS_KEY_PRESSED VK_LBUTTON
                                AND IS_CHAR_SITTING_IN_ANY_CAR scplayer
                                AND IS_KEY_PRESSED clutchKey
                                    WAIT 0

                                    WRITE_MEMORY pGasPedal 2 0 FALSE
                                    IF NOT gear = 1
                                        DRAW_SPRITE 5 gear_pointer mouseY 70.0 150.0 255 255 255 255
                                    ELSE
                                        DRAW_SPRITE 4 gear_pointer mouseY 70.0 150.0 255 255 255 255
                                    ENDIF

                                    USE_TEXT_COMMANDS 0
                                ENDWHILE
                            ENDIF

                            IF IS_KEY_PRESSED VK_RBUTTON
                            AND gear >= 0
                            AND gear <= 6
                                gear -= 1
                                IF gear <= -1
                                    gear = 0
                                ENDIF
                                WHILE IS_KEY_PRESSED VK_RBUTTON
                                AND IS_CHAR_SITTING_IN_ANY_CAR scplayer
                                AND IS_KEY_PRESSED clutchKey
                                    WAIT 0
                                    DRAW_SPRITE 4 gear_pointer mouseY 70.0 150.0 255 255 255 255
                                    USE_TEXT_COMMANDS 0
                                ENDWHILE
                            ENDIF
                        ELSE
                            GET_PC_MOUSE_MOVEMENT mouseX mouseY
                            IF NOT IS_MOUSE_USING_VERTICAL_INVERSION
                                mouseY *= -1.0
                            ENDIF

                            CLEO_CALL GearBoxStruct 0 gear gear_posX gear_posY fps_set mouseX mouseY iniNoAnim gear gear_posX gear_posY

                            IF NOT READ_FLOAT_FROM_INI_FILE "cleo/NFRShift Gears Soundize.ini" "Manual" "gPointerWidth" gear_pointer
                                WRITE_FLOAT_TO_INI_FILE 49.0 "cleo/NFRShift Gears Soundize.ini" "Manual" "gPointerWidth"
                            ENDIF

                            IF gear = 0
                            OR gear = 7
                                IF IS_BUTTON_PRESSED 0 CIRCLE
                                    gear_pointer -= 5.0
                                    gear = 7
                                ENDIF
                            ENDIF
                        ENDIF

                        CLEO_CALL GearHelper 0 iCar gear iniGearHelper pVeh sndLoaded pIsBank pGetRPM pGetMaxRPM pGetGear iniShowRPM

                    ENDWHILE
                ENDIF

                SET_CAMERA_CONTROL TRUE

                IF IS_CHAR_SITTING_IN_ANY_CAR scplayer
                    CLEO_CALL Transmission 0 iCar gear pVeh fThresh iniGearLimitMode sndLoaded pIsBank sndWriteGear iniInhibitVanillaFx iniGearHelper pGetRPM pGetMaxRPM pGetGear iniShowRPM iniRealStart iniWriteGearGame clutchRevSim
                ENDIF

            ENDIF
        ENDIF
    ENDIF

ENDWHILE

}


{
// CLEO_CALL Transmission 0 car gear pVeh fThresh limitMode sndLoaded pIsBank sndWriteGear inhibitFx showHelper pGetRPM pGetMaxRPM pGetGear showRPM realStart writeGearGame clutchRevSim
//
// Laco principal de dirigibilidade com a marcha engatada. Roda preso aqui
// enquanto o jogador nao pisar na embreagem. Controla o acelerador de acordo
// com o limite da marcha atual (GetGearSpeedLimit) e sincroniza o audio:
//   - Com banco do Soundize ativo: so crava a marcha no audio se
//     WriteGearToAudio = 1 (deixa o Soundize mandar no som).
//   - Sem o Soundize: comportamento original (SwitchCarGearAudio + efeitos).
Transmission:
    LVAR_INT car gear pVeh
    LVAR_FLOAT fThresh
    LVAR_INT limitMode sndLoaded pIsBank sndWriteGear inhibitFx showHelper pGetRPM pGetMaxRPM pGetGear showRPM realStart iniWriteGearGame
    LVAR_INT player c maxGears pointer clutch gas acc first sndBank stallFlag clutchRevSim
    LVAR_FLOAT GearSpeedLimit VehSpeed fn
    LVAR_FLOAT fLug fStall

    CONST_INT LOWEST 2
    CONST_INT LOW 1
    CONST_INT NORMAL 0

    GET_PLAYER_CHAR 0 player
    GET_CAR_CHAR_IS_USING player c

    GET_CAR_NUMBER_OF_GEARS c maxGears

    IF NOT READ_INT_FROM_INI_FILE "cleo/NFRShift Gears Soundize.ini" "Manual" "ManualClutchKey" clutch
        WRITE_INT_TO_INI_FILE 160 "cleo/NFRShift Gears Soundize.ini" "Manual" "ManualClutchKey"
    ENDIF

    IF NOT READ_INT_FROM_INI_FILE "cleo/NFRShift Gears Soundize.ini" "Manual" "Acceleration" acc
        WRITE_INT_TO_INI_FILE 0 "cleo/NFRShift Gears Soundize.ini" "Manual" "Acceleration"
    ENDIF

    // fator da faixa minima da marcha (abaixo disso o motor labuta e morre)
    IF NOT READ_FLOAT_FROM_INI_FILE "cleo/NFRShift Gears Soundize.ini" "config" "StallSpeedFactor" fStall
        WRITE_FLOAT_TO_INI_FILE 0.5 "cleo/NFRShift Gears Soundize.ini" "config" "StallSpeedFactor"
    ENDIF

    timera = 0
    timerb = 0

    pointer = 0xB73458
    pointer += 0x20

    IF sndLoaded = 0
        CLEO_CALL ApplyGearSuspensionEffectInVehicle 0 c
        IF inhibitFx = 1
            CLEO_CALL InhibitChangeGearsEffect 0 c
        ENDIF
    ENDIF

    first = 0

    WHILE IS_CHAR_IN_CAR player c
    AND NOT IS_KEY_PRESSED clutch
        WAIT 0

        CLEO_CALL GetCurrentVehicleSpeed 0 c VehSpeed
        CLEO_CALL GetGearSpeedLimit 0 c gear maxGears fThresh limitMode GearSpeedLimit

        fn = VehSpeed - GearSpeedLimit
        fn *= -1.0

        sndBank = 0
        IF sndLoaded = 1
            CALL_FUNCTION_RETURN pIsBank 1 1 (pVeh)(sndBank)
        ENDIF

        SWITCH gear
            CASE 0
                IF VehSpeed > 0.05
                OR VehSpeed < -0.05
                    // em movimento: neutro so desacopla (sem gas, sem freio)
                    IF IS_BUTTON_PRESSED 0 16
                        WRITE_MEMORY pointer 2 0 FALSE
                    ENDIF

                    IF IS_BUTTON_PRESSED 0 14
                        IF VehSpeed < 0.0
                        OR IS_CAR_STOPPED c
                            APPLY_BRAKES_TO_PLAYERS_CAR 0 1
                        ENDIF
                    ELSE
                        APPLY_BRAKES_TO_PLAYERS_CAR 0 0
                    ENDIF
                ELSE
                    // PARADO: acelerar no ponto morto = rev DE VERDADE. O gas
                    // real chega no motor e o carro e segurado parado - a
                    // transmissao gira o RPM nativamente (mesmo caminho do
                    // "freio de mao + acelerar" do vanilla, que o Soundize
                    // acompanha por usar as marchas/estado do jogo)
                    IF IS_BUTTON_PRESSED 0 16
                        WRITE_MEMORY pointer 2 255 FALSE
                        APPLY_BRAKES_TO_PLAYERS_CAR 0 1
                        CLEO_CALL ClutchRevSim 0 c fThresh clutchRevSim
                    ELSE
                        WRITE_MEMORY pointer 2 0 FALSE
                        APPLY_BRAKES_TO_PLAYERS_CAR 0 0
                    ENDIF
                ENDIF
                BREAK
            DEFAULT

                // Zera o contador de afogar quando a velocidade ta saudavel
                // pra marcha (rolando acima da faixa minima x fator)
                CLEO_CALL GetGearMinSpeedLimit 0 c gear fStall fLug
                IF VehSpeed > 0.05
                AND VehSpeed >= fLug
                    timerb = 0
                ELSE
                    IF VehSpeed < -0.05
                        timerb = 0
                    ENDIF
                ENDIF

                GET_CAR_NUMBER_OF_GEARS c maxGears

                IF sndBank = 1
                    IF sndWriteGear = 1
                        CLEO_CALL SwitchCarGearAudio 0 c gear
                    ENDIF
                ELSE
                    IF inhibitFx = 1
                        CLEO_CALL InhibitChangeGearsEffect 0 c
                    ENDIF
                    CLEO_CALL SwitchCarGearAudio 0 c gear
                ENDIF

                // A parte mais importante: ESCREVER a marcha no proprio jogo
                // (CVehicle.m_nCurrentGear). O Soundize usa as marchas do
                // jogo e nao muda a fisica - entao fisica e som seguem o
                // cambio manual, inclusive largada parado em marcha alta.
                IF iniWriteGearGame = 1
                    CLEO_CALL WriteGearToGame 0 pVeh gear maxGears
                ENDIF

                IF VehSpeed > GearSpeedLimit
                    // acima do limite da marcha:
                    timerb = 0
                    IF IS_BUTTON_PRESSED 0 16
                        // corta-giro: corta o gas e deixa o RPM cravado NO
                        // TALO (o som nao hesita nem volta, igual a vida real)
                        WRITE_MEMORY pointer 2 0 FALSE
                    ELSE
                        IF fn < -0.3
                            WRITE_MEMORY pointer 2 -200 FALSE
                        ELSE
                            IF fn < 0.0
                            AND fn > -0.009
                                WRITE_MEMORY pointer 2 0 FALSE
                            ELSE
                                IF fn < -0.009
                                AND fn > -0.1
                                    WRITE_MEMORY pointer 2 -20 FALSE
                                ELSE
                                    IF fn < -0.1
                                    AND fn > -0.25
                                        WRITE_MEMORY pointer 2 -40 FALSE
                                    ELSE
                                        WRITE_MEMORY pointer 2 -80 FALSE
                                    ENDIF
                                ENDIF
                            ENDIF
                        ENDIF
                    ENDIF
                ELSE
                    IF IS_BUTTON_PRESSED 0 16
                        // gas TOTAL: puxa ate o talo sem hesitar
                        gas = 255
                        SWITCH acc
                            CASE NORMAL
                                gas -= 0
                                BREAK
                            CASE LOW
                                gas -= 76
                                BREAK
                            CASE LOWEST
                                gas -= 127
                                BREAK
                        ENDSWITCH

                        IF gas < 0
                            gas = 0
                        ENDIF

                        IF first = 0
                            IF sndBank = 0
                                CLEO_CALL ApplyGearSuspensionEffectInVehicle 0 c
                                IF inhibitFx = 1
                                    CLEO_CALL InhibitChangeGearsEffect 0 c
                                ENDIF
                            ENDIF
                            first = 1
                        ENDIF

                        IF IS_KEY_PRESSED VK_LCONTROL
                            WRITE_MEMORY pointer 2 400 FALSE
                        ENDIF

                        // FALLBACK (WriteGearToGame = 0): marcha fora da
                        // faixa, motor afogando, aceleracao fraca
                        IF iniWriteGearGame = 0
                        AND gear >= 2
                        AND gear <= 6
                            IF VehSpeed < fLug
                                IF gas > 45
                                    gas = 45
                                ENDIF
                            ENDIF
                        ENDIF

                        IF NOT IS_CAR_ENGINE_ON c
                            WRITE_MEMORY pointer 2 0 FALSE
                        ELSE
                            WRITE_MEMORY pointer 2 gas FALSE
                        ENDIF

                        // Muito devagar pra marcha, mesmo acelerando: o
                        // motor labuta e morre depois de um tempo (a escala
                        // de forca em GetGearMinSpeedLimit deixa os potentes
                        // labutarem mais tempo antes de morrer)
                        IF gear >= 2
                        AND gear <= 6
                            IF VehSpeed < fLug
                                IF timerb > 1000
                                    GOSUB KillEngine
                                ENDIF
                            ENDIF
                        ENDIF
                    ELSE
                        gas = 0
                        IF VehSpeed <= 0.05
                        AND VehSpeed >= -0.05
                            // PARADO em marcha com embreagem solta e sem gas:
                            // o motor morre (soltou a embreagem parado)
                            IF realStart = 1
                                IF timerb > 600
                                    GOSUB KillEngine
                                ENDIF
                            ELSE
                                gas = 50
                            ENDIF
                        ELSE
                            IF realStart = 1
                                // rolando mais devagar do que a marcha aguenta:
                                // o motor labuta e morre (falta velocidade)
                                IF fLug > 0.05
                                AND VehSpeed < fLug
                                    IF timerb > 700
                                        GOSUB KillEngine
                                    ENDIF
                                ELSE
                                    IF gear = 1
                                        fn = GearSpeedLimit / 3.0
                                        IF VehSpeed <= fn
                                            gas = 50 // marcha lenta da 1a
                                        ENDIF
                                    ENDIF
                                ENDIF
                            ELSE
                                fn = GearSpeedLimit / 3.0
                                IF VehSpeed <= fn
                                    gas = 50
                                ENDIF
                            ENDIF
                        ENDIF

                        IF NOT IS_CAR_ENGINE_ON c
                            WRITE_MEMORY pointer 2 0 FALSE
                        ELSE
                            WRITE_MEMORY pointer 2 gas FALSE
                        ENDIF
                    ENDIF
                ENDIF

                IF timera > 1000
                    IF VehSpeed <= 0.02
                        GOSUB KillEngine
                    ENDIF
                ENDIF

                IF IS_BUTTON_PRESSED 0 14
                    IF VehSpeed < 0.0
                    OR IS_CAR_STOPPED c
                        APPLY_BRAKES_TO_PLAYERS_CAR 0 1
                    ENDIF
                ELSE
                    APPLY_BRAKES_TO_PLAYERS_CAR 0 0
                ENDIF
                BREAK
            CASE 7 //MARCHA RÉ

                IF IS_BUTTON_PRESSED 0 16
                    gas = -255
                ELSE

                    IF VehSpeed < 0.003
                    AND VehSpeed > -0.07
                        gas = -50
                    ELSE
                        gas = 0
                        IF VehSpeed > 0.003
                            GOSUB KillEngine
                        ENDIF
                    ENDIF

                    IF IS_BUTTON_PRESSED 0 14
                        IF VehSpeed < 0.0
                        OR IS_CAR_STOPPED c
                            APPLY_BRAKES_TO_PLAYERS_CAR 0 1
                        ENDIF
                    ELSE
                        APPLY_BRAKES_TO_PLAYERS_CAR 0 0
                    ENDIF
                ENDIF

                WRITE_MEMORY pointer 2 gas FALSE

                BREAK
        ENDSWITCH

        // ================= PARTIDA REALISTA =================
        // Motor desligado + E SEM embreagem:
        //   - ponto morto: motor fica ligado (vida real)
        //   - parado em marcha: tranco com a forca do proprio carro e o
        //     motor morre em seguida (afogou)
        //   - rolando em marcha (>= ~7 km/h): pega no tranco (bump start)
        // Pisar a embreagem durante o tranco salva o engate.
        IF realStart = 1
        AND NOT IS_CAR_ENGINE_ON c
        AND IS_KEY_PRESSED VK_KEY_E
            IF gear = 0
                SET_CAR_ENGINE_ON c 1 // ponto morto: fica ligado
            ELSE
                CLEO_CALL GetCurrentVehicleSpeed 0 c VehSpeed
                IF VehSpeed > 2.0
                OR VehSpeed < -2.0
                    SET_CAR_ENGINE_ON c 1 // rolando em marcha: pegou no tranco
                ELSE
                    stallFlag = 0 // 0 = afogou | 1 = pegou/salvou
                    SET_CAR_ENGINE_ON c 1
                    timera = 0
                    WHILE timera < 500
                        WAIT 0
                        IF gear = 7
                            gas = -255 // re: tranco pra tras
                        ELSE
                            gas = timera // partida fraca e crescente (motor de arranque)
                            IF gas > 255
                                gas = 255
                            ENDIF
                        ENDIF
                        WRITE_MEMORY pointer 2 gas FALSE
                        IF IS_KEY_PRESSED clutch
                            stallFlag = 1 // pisou a embreagem: salvou o engate
                        ENDIF
                        CLEO_CALL GetCurrentVehicleSpeed 0 c VehSpeed
                        IF VehSpeed > 2.0
                        OR VehSpeed < -2.0
                            stallFlag = 1 // embalou: o motor pega
                        ENDIF
                        IF stallFlag = 1
                            timera = 600
                        ENDIF
                    ENDWHILE
                    IF stallFlag = 0
                        WRITE_MEMORY pointer 2 0 FALSE
                        SET_CAR_ENGINE_ON c 0
                        PRINT_HELP MMSG5 //Afogou! Poe em ponto morto ou pisa na embreagem pra ligar
                        // abuso de partida (tranco falhado): dano leve no motor
                        IF NOT READ_FLOAT_FROM_INI_FILE "cleo/NFRShift Gears Soundize.ini" "config" "StarterDamage" VehSpeed
                            WRITE_FLOAT_TO_INI_FILE 6.0 "cleo/NFRShift Gears Soundize.ini" "config" "StarterDamage"
                        ENDIF
                        IF VehSpeed > 0.0
                            stallFlag = pVeh + 0x4C0 // CVehicle.m_fHealth
                            READ_MEMORY stallFlag 4 FALSE fLug
                            fLug -= VehSpeed
                            IF fLug < 251.0
                                fLug = 251.0 // nao deixa pegar fogo so por abuso de partida
                            ENDIF
                            WRITE_MEMORY stallFlag 4 fLug FALSE
                        ENDIF
                    ENDIF
                ENDIF
            ENDIF
        ENDIF

        CLEO_CALL GetCurrentVehicleSpeed 0 c VehSpeed
        CLEO_CALL GearHelper 0 c gear showHelper pVeh sndLoaded pIsBank pGetRPM pGetMaxRPM pGetGear showRPM

    ENDWHILE

CLEO_RETURN 0

    KillEngine:
        IF IS_CAR_ENGINE_ON c
            IF NOT gear = 0
                WRITE_MEMORY pointer 2 255 FALSE
                WAIT 150
                gas = 0
                SET_CAR_ENGINE_ON c 0
                PRINT_HELP MMSG1 //O motor morreu, pressione E para ligar novamente
            ENDIF
        ENDIF
    RETURN

}

{// CLEO_CALL GearHelper 0 car gear show pVeh sndLoaded pIsBank pGetRPM pGetMaxRPM pGetGear showRPM
//
// HUD: marcha selecionada, marcha do som (Soundize ou vanilla) e barra de RPM.
GearHelper:
    LVAR_INT car gear show pVeh sndLoaded pIsBank pGetRPM pGetMaxRPM pGetGear showRPM
    LVAR_INT rgear maxgear realGear sndBank
    LVAR_FLOAT fRPM fMaxRPM frac w cx

    IF show = 1
        GET_CAR_CURRENT_GEAR car rgear
        GET_CAR_NUMBER_OF_GEARS car maxgear
        GOSUB FormatText
        IF gear = 7
            DISPLAY_TEXT_WITH_NUMBER 620.0 330.0 MMSG3 maxgear
        ELSE
            DISPLAY_TEXT_WITH_2_NUMBERS 620.0 330.0 MMSG2 gear maxgear
        ENDIF

        // Marcha que o SOM esta tocando: usa o Soundize quando o carro tem
        // banco ativo; senao usa a marcha real do jogo (igual mod original)
        realGear = rgear
        sndBank = 0
        IF sndLoaded = 1
            CALL_FUNCTION_RETURN pIsBank 1 1 (pVeh)(sndBank)
            IF sndBank = 1
                CALL_FUNCTION_RETURN pGetGear 1 1 (pVeh)(realGear)
            ENDIF
        ENDIF

        IF NOT gear = realGear
            GOSUB FormatText
            SET_TEXT_COLOUR 255 100 50 255
            DISPLAY_TEXT_WITH_NUMBER 620.0 345.0 MMSG4 realGear
        ENDIF
        USE_TEXT_COMMANDS 0
    ENDIF

    // Barra de RPM em tempo real (independe do GearHelper;
    // aparece somente quando o carro esta tocando um banco do Soundize)
    IF showRPM = 1
    AND sndLoaded = 1
        sndBank = 0
        CALL_FUNCTION_RETURN pIsBank 1 1 (pVeh)(sndBank)
        IF sndBank = 1
            CALL_FUNCTION_RETURN pGetRPM 1 1 (pVeh)(fRPM)
            CALL_FUNCTION_RETURN pGetMaxRPM 1 1 (pVeh)(fMaxRPM)
            IF fMaxRPM > 1.0
                frac = fRPM / fMaxRPM
                IF frac < 0.0
                    frac = 0.0
                ENDIF
                IF frac > 1.0
                    frac = 1.0
                ENDIF
                GOSUB DrawRPMBar
            ENDIF
        ENDIF
    ENDIF
CLEO_RETURN 0

    FormatText:
        SET_TEXT_COLOUR 255 255 255 255
        SET_TEXT_SCALE 0.35 1.2
        SET_TEXT_EDGE 1 0 0 0 255
        SET_TEXT_WRAPX 640.0
        SET_TEXT_RIGHT_JUSTIFY TRUE
    RETURN

    DrawRPMBar:
        w = 236.0 * frac
        cx = w / 2.0
        cx += 202.0
        DRAW_RECT 320.0 440.0 240.0 12.0 10 10 10 150
        IF frac > 0.90
            DRAW_RECT cx 440.0 w 8.0 220 40 40 210
        ELSE
            IF frac > 0.75
                DRAW_RECT cx 440.0 w 8.0 255 160 0 210
            ELSE
                DRAW_RECT cx 440.0 w 8.0 90 220 90 210
            ENDIF
        ENDIF
    RETURN
}

{
// CLEO_CALL WriteGearToGame 0 pVeh gear maxGears
//
// Escreve a marcha escolhida na marcha REAL do veiculo
// (CVehicle+0x4B4, m_nCurrentGear) e zera o contador de troca
// (CVehicle+0x4B8, m_fGearChangeCount) pra o jogo NAO poder subir nem
// descer de marcha por conta propria - nem na largada parado em marcha
// alta (a fisica fraca da marcha alta segura o carro, e o som fica na
// marcha do jogador girando baixo), nem no cruzeiro (o corte-giro em
// ShiftThreshold impede a velocidade de cruzar o ponto de troca).
// A fisica do jogo usa essa marcha, e o Soundize usa as marchas do jogo.
WriteGearToGame:
    LVAR_INT pVeh gear maxGears p n

    IF gear >= 1
    AND gear <= maxGears
        p = pVeh + 0x4B4 // CVehicle.m_nCurrentGear
        READ_MEMORY p 1 FALSE n
        IF NOT n = gear
            WRITE_MEMORY p 1 gear FALSE
        ENDIF
        p = pVeh + 0x4B8 // CVehicle.m_fGearChangeCount
        WRITE_MEMORY p 4 0.0 FALSE
    ENDIF

CLEO_RETURN 0
}

{// CLEO_CALL GetGearSpeedLimit 0 car gear maxGears fThresh limitMode limit
//
// Limite de velocidade da marcha escolhida, ja multiplicado pelo
// ShiftThreshold. E esse limite que segura o RPM abaixo do ponto de troca,
// impedindo o jogo (e o som, que usa as marchas do jogo) de subir de marcha.
GetGearSpeedLimit:
    LVAR_INT car gear maxGears
    LVAR_FLOAT fThresh
    LVAR_INT limitMode p i
    LVAR_FLOAT limit tmp

    CONST_FLOAT GEAR1_LIMIT 25.0
    CONST_FLOAT GEAR2_LIMIT 60.0
    CONST_FLOAT GEAR3_LIMIT 85.0
    CONST_FLOAT GEAR4_LIMIT 110.0
    CONST_FLOAT GEAR5_LIMIT 140.0
    CONST_FLOAT GEAR6_LIMIT 310.0

    IF gear <= 0
    OR gear = 7
        GET_VEHICLE_POINTER car p
        p += 0x384 // CVehicle.tHandlingData
        READ_MEMORY p 4 FALSE p
        p += 0x2C  // tHandlingData.CTransmission
        p += 0x58  // CTransmission.fMaxGearVelocity
        READ_MEMORY p 4 FALSE limit
    ELSE
        IF limitMode = 1 // proporcional ao numero de marchas (estilo antigo)
            GET_VEHICLE_POINTER car p
            p += 0x384 // CVehicle.tHandlingData
            READ_MEMORY p 4 FALSE p
            p += 0x2C  // tHandlingData.CTransmission
            p += 0x58  // CTransmission.fMaxGearVelocity
            READ_MEMORY p 4 FALSE limit
            tmp =# maxGears
            limit /= tmp
            tmp =# gear
            limit *= tmp
            limit *= fThresh
        ELSE
            IF limitMode = 2 // tabela fixa em km/h do mod original
                SWITCH gear
                    CASE 1
                        limit = GEAR1_LIMIT / 3.6
                        BREAK
                    CASE 2
                        limit = GEAR2_LIMIT / 3.6
                        BREAK
                    CASE 3
                        limit = GEAR3_LIMIT / 3.6
                        BREAK
                    CASE 4
                        limit = GEAR4_LIMIT / 3.6
                        BREAK
                    CASE 5
                        limit = GEAR5_LIMIT / 3.6
                        BREAK
                    CASE 6
                        limit = GEAR6_LIMIT / 3.6
                        BREAK
                    DEFAULT
                        GET_VEHICLE_POINTER car p
                        p += 0x384 // CVehicle.tHandlingData
                        READ_MEMORY p 4 FALSE p
                        p += 0x2C  // tHandlingData.CTransmission
                        p += 0x58  // CTransmission.fMaxGearVelocity
                        READ_MEMORY p 4 FALSE limit
                        BREAK
                ENDSWITCH
                limit *= fThresh
            ELSE // 0: janela de marcha da handling (aGears.fChangeUpVelocity)
                GET_VEHICLE_POINTER car p
                p += 0x384 // CVehicle.tHandlingData
                READ_MEMORY p 4 FALSE p
                p += 0x2C  // tHandlingData.CTransmission
                i = 0x0C * gear
                i += p     // CTransmission.aGears[gear]
                i += 0x4   // tTransmissionGear.fChangeUpVelocity
                READ_MEMORY i 4 FALSE limit
                limit *= fThresh
            ENDIF
        ENDIF
    ENDIF

CLEO_RETURN 0 limit
}

{
// CLEO_CALL GetCurrentVehicleSpeed 0 car speed
GetCurrentVehicleSpeed:
    LVAR_INT car
    LVAR_INT pointer
    LVAR_FLOAT mSpeed

    GET_VEHICLE_POINTER car pointer
    pointer += 0x384
    READ_MEMORY pointer 4 FALSE pointer
    pointer += 0x2C //Transmission
    pointer += 0x64 //fCurrentSpeed
    READ_MEMORY pointer 4 FALSE mSpeed

CLEO_RETURN 0 mSpeed
}

{
// CLEO_CALL ClutchRevSim 0 car fThresh enabled
//
// Simula o motor girando com a embreagem pisada (ou em ponto morto), com o
// carro parado: sobe a velocidade INTERNA da transmissao (fCurrentSpeed) como
// se o motor estivesse sendo acelerado. O pedal real continua zerado, entao o
// carro NAO anda e NAO freia. O som padrao do jogo acompanha essa velocidade;
// o Soundize tambem acompanha quando calcula o RPM por ela.
// Em movimento nao mexe em nada (a embreagem so silencia o motor, o carro
// desacelera por arrasto, como na vida real).
ClutchRevSim:
    LVAR_INT car
    LVAR_FLOAT fThresh
    LVAR_INT enabled p i
    LVAR_FLOAT f1st fTgt fCur

    IF IS_CAR_ENGINE_ON car
        GET_VEHICLE_POINTER car p
        p += 0x384 // CVehicle.tHandlingData
        READ_MEMORY p 4 FALSE p
        p += 0x2C  // tHandlingData.CTransmission
        i = p + 0x0C
        i += 0x4   // aGears[1].fChangeUpVelocity
        READ_MEMORY i 4 FALSE f1st
        p += 0x64  // CTransmission.fCurrentSpeed
        READ_MEMORY p 4 FALSE fCur

        IF fCur < 0.6
        AND fCur > -0.6
            IF enabled = 1
            AND IS_BUTTON_PRESSED 0 16
                // sobe o giro RAPIDO ate o talo da 1a (varredura completa,
                // como um rev de ponto morto na vida real)
                fTgt = f1st * fThresh
                fTgt -= fCur
                fTgt *= 0.25
                fCur +=@ fTgt
            ELSE
                IF fCur > 0.01
                    fTgt = fCur * 0.5
                    fCur -=@ fTgt
                ENDIF
            ENDIF
            WRITE_MEMORY p 4 fCur FALSE
        ENDIF
    ENDIF

CLEO_RETURN 0
}

{
// CLEO_CALL GetGearMinSpeedLimit 0 car gear factor speed
// Retorna a faixa minima da marcha (tTransmissionGear.fChangeDownVelocity),
// o mesmo dado que o jogo usa pra saber quando desmultiplicar, ja
// multiplicada pelo fator (StallSpeedFactor). Abaixo disso o motor nao tem
// forca pra puxar a marcha.
GetGearMinSpeedLimit:
    LVAR_INT car gear
    LVAR_FLOAT factor
    LVAR_INT p i n
    LVAR_FLOAT speed fAcc

    GET_VEHICLE_POINTER car p
    p += 0x384 // CVehicle.tHandlingData
    READ_MEMORY p 4 FALSE p
    p += 0x2C  // tHandlingData.CTransmission
    i = 0x0C * gear
    i += p     // CTransmission.aGears[gear]
    i += 0x8   // tTransmissionGear.fChangeDownVelocity
    READ_MEMORY i 4 FALSE speed
    speed *= factor
    // escala de forca do motor: potente labuta menos antes de morrer
    n = p + 0x50 // cTransmission.m_fEngineAcceleration
    READ_MEMORY n 4 FALSE fAcc
    fAcc *= -0.075
    fAcc += 1.25
    IF fAcc < 0.35
        fAcc = 0.35
    ENDIF
    speed *= fAcc

CLEO_RETURN 0 speed
}

{
// Escreve a marcha no byte de marcha da entidade de audio do veiculo
// (CAEVehicleAudioEntity+0xAA). E o mesmo caminho que o mod original usava
// para o audio vanilla (e o Soundize, se ele ler o audio do jogo) seguir
// a marcha do cambio manual.
SwitchCarGearAudio:
    LVAR_INT car gear p i n

    GET_VEHICLE_POINTER car p
    p += 0x138 //CVehicle.CAEVehicleAudioEntity
    i = p + 0xAA //CAEVehicleAudioEntity.m_nCurGear
    READ_MEMORY i 1 FALSE n
    IF NOT n = gear
        WRITE_MEMORY i 1 gear FALSE
    ENDIF

CLEO_RETURN 0
}

{
// Zera os campos do efeito de troca de marcha do audio vanilla
// (usado pelo mod original para nao tocar o som de engate do jogo)
InhibitChangeGearsEffect:
    LVAR_INT car p i

    GET_VEHICLE_POINTER car p
    p += 0x138 //CVehicle.CAEVehicleAudioEntity
    i = p + 0x148
    WRITE_MEMORY i 2 0 FALSE
    i = p + 0x14A
    WRITE_MEMORY i 2 0 FALSE
    i = p + 0x14C
    WRITE_MEMORY i 2 0 FALSE
    i = p + 0x14E
    WRITE_MEMORY i 2 0 FALSE
    i = p + 0x150
    WRITE_MEMORY i 4 0 FALSE
    i = p + 0x154
    WRITE_MEMORY i 2 0 FALSE

CLEO_RETURN 0
}

{
// Forca o efeito de suspensao da troca de marcha do audio vanilla
ApplyGearSuspensionEffectInVehicle:
    LVAR_INT car p i

    GET_VEHICLE_POINTER car p
    p += 0x138 //CVehicle.CAEVehicleAudioEntity
    i = p + 0x148
    WRITE_MEMORY i 2 5000 FALSE

CLEO_RETURN 0
}

{// CLEO_CALL GearBoxStruct 0 gear gear_posX gear_posY fps_set mouseX mouseY iniNoAnim (retorna gear gear_posX gear_posY)
//
// Alavanca em H controlada pelo mouse (carros/caminhoes). Logica identica
// ao mod original; a unica mudanca e a opcao iniNoAnim, que pula a
// animacao de pular marcha (TASK_PLAY_ANIM changegear) para compatibilidade
// com o VEHIK do zzpuma.
GearBoxStruct:

    LVAR_INT gear
    LVAR_FLOAT gear_posX gear_posY
    LVAR_INT fps_set
    LVAR_FLOAT mouseX mouseY
    LVAR_INT iniNoAnim

    LVAR_FLOAT X1 XMED1_2 X2 X3 X33 XMED3_4 X4 X55 X5 XMED5_6 X6 GBOX_Y GBOX_X
    LVAR_FLOAT Y1 Y2 YMED2_3 Y3 Y4 X_SET Y_SET sens
    LVAR_INT scplayer audio

    GET_PLAYER_CHAR 0 scplayer


    GBOX_X = 48.0
    GBOX_Y = 52.9
    //X
    X1 = 0.0
    XMED1_2 = 10.0
    X2 = 19.1
    X3 = 38.5
    X33 = 34.0
    XMED3_4 = 47.75
    X4 = 57.0
    X5 = 76.3
    X55 = 73.0
    XMED5_6 = 84.7
    X6 = 94.7


    //Y

    Y1 = 0.0
    Y2 = 38.9
    YMED2_3 = 52.9
    Y3 = 66.9
    Y4 = 104.9

    //Transform

            IF NOT READ_FLOAT_FROM_INI_FILE "cleo/NFRShift Gears Soundize.ini" "Manual" "ManualSpriteX" X_SET
                WRITE_FLOAT_TO_INI_FILE 0.0 "cleo/NFRShift Gears Soundize.ini" "Manual" "ManualSpriteX"
            ENDIF

            GBOX_X += X_SET
            X1 += X_SET
            XMED1_2 += X_SET
            X2 += X_SET
            X3 += X_SET
            X33 += X_SET
            XMED3_4 += X_SET
            X4 += X_SET
            X5 += X_SET
            X55 += X_SET
            XMED5_6 += X_SET
            X6 += X_SET

            IF NOT READ_FLOAT_FROM_INI_FILE "cleo/NFRShift Gears Soundize.ini" "Manual" "ManualSpriteY" Y_SET
                WRITE_FLOAT_TO_INI_FILE 0.0 "cleo/NFRShift Gears Soundize.ini" "Manual" "ManualSpriteY"
            ENDIF

            GBOX_Y += Y_SET
            Y1 += Y_SET
            Y2 += Y_SET
            YMED2_3 += Y_SET
            Y3 += Y_SET
            Y4 += Y_SET
        //
    //

    GET_PC_MOUSE_MOVEMENT mouseX mouseY
    IF NOT IS_MOUSE_USING_VERTICAL_INVERSION
        mouseY *= -1.0
    ENDIF

    DRAW_SPRITE 1 GBOX_X GBOX_Y (95.0 105.0) (255 255 255 255)

    //Muito longo
        //Limites
            IF gear_posX >= X6
            OR gear_posX <= X1
                IF gear_posX >= X6
                    gear_posX = X6
                    mouseX = 0.0
                ENDIF

                IF gear_posX <= X1
                    gear_posX = X1
                    mouseX = 0.0
                ENDIF
            ENDIF

            IF gear_posY <= Y1
            OR gear_posY >= Y4
                IF gear_posY >= Y4
                    gear_posY = Y4
                    IF mouseY <= 0.0
                        mouseY = 0.0
                    ENDIF
                ENDIF

                IF gear_posY <= Y1
                    gear_posY = Y1
                    IF mouseY >= 0.0
                        mouseY = 0.0
                    ENDIF
                ENDIF
            ENDIF

        //Blocos
            IF gear_posY <= Y2
            OR gear_posY >= Y3

                IF gear_posX > X2 //1° Pilar
                AND gear_posX < X33
                    gear_posX = X2
                ENDIF

                IF gear_posX > X33
                AND gear_posX < X3
                    gear_posX = X3
                ENDIF

                IF gear_posX > X4 //2° Pilar
                AND gear_posX < X55
                    gear_posX = X4
                ENDIF

                IF gear_posX > X55
                AND gear_posX < X5
                    gear_posX = X5
                ENDIF
            ENDIF

            IF gear_posY > Y2
            AND gear_posY < Y3

                IF gear_posX > X1 //Entre a 1 e 2 marcha
                AND gear_posX < X2
                    IF mouseX < 0.0
                        mouseX = 0.0
                    ENDIF
                ENDIF

                IF gear_posX <= XMED1_2
                AND gear_posX < X2
                    gear_posX = XMED1_2 //Média de X1 e X2
                    IF mouseX < -0.0
                        mouseX = 0.0
                    ENDIF
                ENDIF

                IF gear_posX >= XMED5_6
                AND gear_posX > X5
                    gear_posX = XMED5_6 //Média de X5 e X6
                    IF mouseX > 0.0
                        mouseX = 0.0
                    ENDIF
                ENDIF

                IF gear_posX > X2
                AND gear_posX < X3
                    mouseY = 0.0
                    gear_posY = YMED2_3
                ENDIF

                IF gear_posX > X4
                AND gear_posX < X5
                    mouseY = 0.0
                    gear_posY = YMED2_3
                ENDIF
            ELSE
                IF gear_posX >= X1
                AND gear_posX <= X2
                    gear_posX = XMED1_2
                ENDIF

                IF gear_posX >= X3
                AND gear_posX <= X4
                    gear_posX = XMED3_4
                ENDIF
            ENDIF


        //Transmissão
            IF gear_posY < Y2
            OR gear_posY > Y3

                IF gear_posY < Y2 //1 Marcha

                    //blocksg
                    IF gear_posX < X1
                    OR gear_posX <= X1
                        mouseX = 0.0
                        gear_posX = XMED1_2
                    ENDIF

                    IF gear_posX > X1
                    AND gear_posX < X2
                        IF gear_posX < X2
                        OR gear_posX <= X2
                            mouseX = 0.0
                            gear_posX = XMED1_2
                        ENDIF
                    ENDIF

                    IF gear_posX > X1
                    AND gear_posX < X2
                        mouseX = 0.0
                        gear_posX = XMED1_2

                        LOAD_AUDIO_STREAM "cleo/NFR Shift Gear/gears_sound/1.mp3" audio
                        IF NOT gear = 1
                            SET_AUDIO_STREAM_STATE audio 1
                            IF iniNoAnim = 0
                                TASK_PLAY_ANIM scplayer cambio changegear 4.0 0 0 0 0 -1
                            ENDIF
                        ENDIF
                        gear = 1

                    ENDIF
                ENDIF

                IF gear_posY > Y3 //2 Marcha

                    //blocksg
                    IF gear_posX < X1
                    OR gear_posX <= X1
                        mouseX = 0.0
                        gear_posX = XMED1_2
                    ENDIF

                    IF gear_posX > X1
                    AND gear_posX < X2
                        IF gear_posX < X2
                        OR gear_posX <= X2
                            mouseX = 0.0
                            gear_posX = XMED1_2
                        ENDIF
                    ENDIF

                    IF gear_posX > X1
                    AND gear_posX < X2
                        mouseX = 0.0
                        LOAD_AUDIO_STREAM "cleo/NFR Shift Gear/gears_sound/2.mp3" audio
                        IF NOT gear = 2
                            SET_AUDIO_STREAM_STATE audio 1
                            IF iniNoAnim = 0
                                TASK_PLAY_ANIM scplayer cambio changegear 4.0 0 0 0 0 -1
                            ENDIF
                        ENDIF
                        gear = 2

                    ENDIF
                ENDIF

                IF gear_posY < Y2 //3 Marcha

                    //blocksg
                    IF gear_posX > X2
                        IF gear_posX < X3
                        OR gear_posX <= X3
                            mouseX = 0.0
                            gear_posX = XMED5_6
                        ENDIF
                    ENDIF

                    IF gear_posX > X3
                    AND gear_posX < X4
                        IF gear_posX < X4
                        OR gear_posX <= X4
                            mouseX = 0.0
                            gear_posX = XMED3_4
                        ENDIF
                    ENDIF

                    IF gear_posX > X3
                    AND gear_posX < X4
                        mouseX = 0.0
                        LOAD_AUDIO_STREAM "cleo/NFR Shift Gear/gears_sound/3.mp3" audio
                        IF NOT gear = 3
                            SET_AUDIO_STREAM_STATE audio 1
                            IF iniNoAnim = 0
                                TASK_PLAY_ANIM scplayer cambio changegear 4.0 0 0 0 0 -1
                            ENDIF
                        ENDIF
                        gear = 3

                    ENDIF
                ENDIF

                IF gear_posY > Y3 //4 Marcha

                    //blocksg
                    IF gear_posX > X2
                        IF gear_posX < X3
                        OR gear_posX <= X3
                            mouseX = 0.0
                            gear_posX = XMED5_6
                        ENDIF
                    ENDIF

                    IF gear_posX > X3
                    AND gear_posX < X4
                        IF gear_posX < X4
                        OR gear_posX <= X4
                            mouseX = 0.0
                            gear_posX = XMED3_4
                        ENDIF
                    ENDIF

                    IF gear_posX > X3
                    AND gear_posX < X4
                        mouseX = 0.0
                        LOAD_AUDIO_STREAM "cleo/NFR Shift Gear/gears_sound/4.mp3" audio
                        IF NOT gear = 4
                            SET_AUDIO_STREAM_STATE audio 1
                            IF iniNoAnim = 0
                                TASK_PLAY_ANIM scplayer cambio changegear 4.0 0 0 0 0 -1
                            ENDIF
                        ENDIF
                        gear = 4

                    ENDIF
                ENDIF

                IF gear_posY < Y2 //5 Marcha

                    //blocksg
                    IF gear_posX > X4
                        IF gear_posX < X5
                        OR gear_posX <= X5
                            mouseX = 0.0
                            gear_posX = XMED5_6
                        ENDIF

                        IF gear_posX > X6
                        OR gear_posX >= X6
                            mouseX = 0.0
                            gear_posX = XMED5_6
                        ENDIF
                    ENDIF

                    IF gear_posX > X5
                    AND gear_posX < X6
                        mouseX = 0.0
                        gear_posX = 558.5
                        IF gear_posX < X5
                        OR gear_posX <= X5
                            mouseX = 0.0
                            gear_posX = XMED5_6
                        ENDIF

                        IF gear_posX < X6
                        OR gear_posX <= X6
                        OR gear_posX > X6
                            mouseX = 0.0
                            gear_posX = XMED5_6
                        ENDIF
                    ENDIF

                    IF gear_posX > X5
                    AND gear_posX < X6
                        mouseX = 0.0
                        LOAD_AUDIO_STREAM "cleo/NFR Shift Gear/gears_sound/5.mp3" audio
                        IF NOT gear = 5
                            SET_AUDIO_STREAM_STATE audio 1
                            IF iniNoAnim = 0
                                TASK_PLAY_ANIM scplayer cambio changegear 4.0 0 0 0 0 -1
                            ENDIF
                        ENDIF
                        gear = 5

                    ENDIF
                ENDIF

                IF gear_posY > Y3 //Ré / 6 Marcha

                    //blocks
                    IF gear_posX > X4
                        IF gear_posX < X5
                        OR gear_posX <= X5
                            mouseX = 0.0
                            gear_posX = XMED5_6
                        ENDIF

                        IF gear_posX > X6
                        OR gear_posX >= X6
                            mouseX = 0.0
                            gear_posX = XMED5_6
                        ENDIF
                    ENDIF

                    IF gear_posX > X5
                    AND gear_posX < X6
                        mouseX = 0.0
                        gear_posX = XMED5_6
                        IF gear_posX < X5
                        OR gear_posX <= X5
                            mouseX = 0.0
                            gear_posX = XMED5_6
                        ENDIF

                        IF gear_posX < X6
                        OR gear_posX <= X6
                        OR gear_posX > X6
                            mouseX = 0.0
                            gear_posX = XMED5_6
                        ENDIF
                    ENDIF

                    IF gear_posX > X5
                    AND gear_posX < X6
                    AND NOT IS_BUTTON_PRESSED 0 CIRCLE
                        mouseX = 0.0
                        LOAD_AUDIO_STREAM "cleo/NFR Shift Gear/gears_sound/gas.mp3" audio
                        IF NOT gear = 6
                            SET_AUDIO_STREAM_STATE audio 1
                            IF iniNoAnim = 0
                                TASK_PLAY_ANIM scplayer cambio changegear 4.0 0 0 0 0 -1
                            ENDIF
                        ENDIF
                        gear = 6
                    ENDIF

                    IF gear_posX > X5
                    AND gear_posX < X6
                    AND IS_BUTTON_PRESSED 0 CIRCLE
                        mouseX = 0.0
                        LOAD_AUDIO_STREAM "cleo/NFR Shift Gear/gears_sound/gas.mp3" audio
                        IF NOT gear = 7
                            SET_AUDIO_STREAM_STATE audio 1
                            IF iniNoAnim = 0
                                TASK_PLAY_ANIM scplayer cambio changegear 4.0 0 0 0 0 -1
                            ENDIF
                        ENDIF
                        //gear = 7 engate lá embaixo
                    ENDIF

                ENDIF
            ENDIF

            IF gear_posY > Y2
            AND gear_posY < Y3
                IF gear_posX > XMED5_6 //Médias
                AND gear_posX < X6
                    //mouseX = 0.0
                    gear_posX = 558.5
                ENDIF

                IF gear_posX > X1
                AND gear_posX < XMED1_2 //Médias
                    //mouseX = 0.0
                    gear_posX = XMED1_2
                ENDIF

                IF NOT gear = 0
                    IF iniNoAnim = 0
                        TASK_PLAY_ANIM scplayer cambio changegear 4.0 0 0 0 0 -1
                    ENDIF
                ENDIF
                gear = 0
            ENDIF
        //
    //

    IF NOT READ_FLOAT_FROM_INI_FILE "cleo/NFRShift Gears Soundize.ini" "Manual" "PointerSensibility" X1
        WRITE_FLOAT_TO_INI_FILE 6.0 "cleo/NFRShift Gears Soundize.ini" "Manual" "PointerSensibility"
    ENDIF

    mouseX /= X1 // divisor de velocidade
    mouseY /= X1

    IF IS_MOUSE_USING_VERTICAL_INVERSION
        mouseY *= -1.0
    ENDIF

    IF fps_set = 1 // STABLE_FPS
        gear_posX +=@ mouseX
        gear_posY +=@ mouseY
    ELSE
        gear_posX += mouseX
        gear_posY += mouseY
    ENDIF

    //n sei como saporra funcionou :D
    IF gear_posY < Y2
    OR gear_posY > Y3
        sens = YMED2_3 - gear_posY
        sens *= -1.0
        mouseY = YMED2_3 + sens
        mouseX = sens
        mouseX /= 2.0
        mouseY -= mouseX

        IF gear_posY < YMED2_3
            sens -= 20.0
        ELSE
            sens += 20.0
        ENDIF

        DRAW_SPRITE 6 gear_posX mouseY 70.0 sens 255 255 255 255
    ENDIF


    sens = XMED3_4 - gear_posX
    sens *= -1.0
    mouseY = XMED3_4 + sens
    mouseX = sens
    mouseX /= 2.0
    mouseY -= mouseX

    IF gear_posX > XMED3_4
        sens += 15.0
    ELSE
        sens -= 15.0
    ENDIF

    DRAW_SPRITE 7 mouseY YMED2_3 sens 80.0 255 255 255 255


    IF NOT READ_FLOAT_FROM_INI_FILE "cleo/NFRShift Gears Soundize.ini" "Manual" "gPointerWidth" sens
        WRITE_FLOAT_TO_INI_FILE 49.0 "cleo/NFRShift Gears Soundize.ini" "Manual" "gPointerWidth"
    ENDIF

    IF gear = 0
    OR gear = 7
        IF IS_BUTTON_PRESSED 0 CIRCLE
            sens -= 5.0
            gear = 7
        ENDIF
    ENDIF

    IF NOT gear_posY < Y1
    AND NOT gear_posY > Y4
        DRAW_SPRITE 2 gear_posX gear_posY (sens sens) (255 255 255 255)
    ELSE
        IF gear_posY < Y1
            DRAW_SPRITE 2 gear_posX Y1 (sens sens) (255 255 255 255)
        ENDIF

        IF gear_posY > Y4
            DRAW_SPRITE 2 gear_posX Y4 (sens sens) (255 255 255 255)
        ENDIF
    ENDIF


    USE_TEXT_COMMANDS 0

CLEO_RETURN 0 gear gear_posX gear_posY

}

SCRIPT_END
