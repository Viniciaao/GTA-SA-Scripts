/*
    ============================================================================
    SOM DE PASTILHA DE FREIO GASTA - v2.7 (CLEO+, edicao NPCs)
    ----------------------------------------------------------------------------
    Reescrito do zero em gta3script (gta3sc) a partir do mod original
    "Som de pastilha de freio gasta" v2.5.1 (Amilton, Fabio, Junior_Djjr),
    usando a mesma abordagem do "Air Brake Sound v3" do Junior_Djjr - que e' o
    mod que passou a aplicar o som de freio a ar em TODOS os NPCs, e nao so no
    carro do jogador.

    O QUE O MOD FAZ
    ============================================================================
    Quando alguem - VOCE ou um NPC - freia um carro velho, aparece aquele
    chiado agudo e horrivel de pastilha gasta. O som e' 3D e sai do carro, entao
    da para ouvir o chiado do carro ao lado, de tras, atravessando a rua.

    O QUE MUDOU EM RELACAO AO v2.5.1
    ============================================================================
    1. NPCs. O som passa a valer para QUALQUER veiculo do pool do jogo, com ou
       sem motorista - inclusive o seu, que nao tem mais tratamento especial
       (antes o script so olhava para o carro do jogador).
    2. Valor do carro lido DIRETO da handling, pelo opcode GET_CAR_VALUE do
       CLEO+ (CHandlingData::m_nMonetaryValue). A v2.5.1 dependia de uma lista
       no .ini que quebrava com carro added; agora o valor vem do jogo e o .ini
       so manda no limiar (MinValue/MaxValue). Continua valendo o que o post
       original falava: "o valor do carro e' definido pelo handling" - quem
       quiser um carro mais ou menos "velho" mexe no data/handling.cfg.
    3. Volume ESCALA com a pressao do pedal e com a velocidade (a v2.5.1 tinha
       volume fixo) e segue o volume de efeitos do menu (0xB5FCCC) em todos os
       sons, inclusive os dos NPCs.
    4. Sons 3D de verdade: a pastilha e' do carro que esta freando, com a
       atenuacao por distancia do proprio jogo.
    5. Cooldown: com Cooldown = 0 o chiado acontece UMA vez a cada aperto no
       freio (comportamento da v2.5.1). Com Cooldown = 1500 (padrao) ele se
       repete enquanto o freio estiver apertado, que e' como funciona na vida
       real (a pastilha canta durante a frenagem).
    6. Independente de FPS: pressao, cooldown e dt sao calculados em segundos
       e milissegundos (GET_GAME_TIMER), e nao "por frame".
    7. Pouco I/O: o .ini e' lido UMA vez, no comeco. O som so e' disparado para
       carros dentro do raio (Radius), com motor ligado, com valor dentro do
       limite e com o pedal mesmo apertado. A velocidade minima (MinSpeed) e a
       leitura do .ini tambem sao unicas, por frame e por carro, respectivamente.
    8. MinSpeed no .ini: a v2.5.1 cantava com o carro parado; aqui a pastilha
       so canta a partir de MinSpeed km/h (10 por padrao). Ponha 0 se quiser
       ouvir o chiado mesmo com o carro parado - util para testar.

    DESEMPENHO (o ponto que mais pesa quando o mod roda no mundo inteiro)
    ============================================================================
    O pool de veiculos do SA (ate ~200 slots) e' percorrido uma vez por frame,
    mas para cada carro o unico opcode e' o GET_EXTENDED_CAR_VAR, que so
    responde para os carros QUE ESTE SCRIPT JA AVALIOU (var 1 = 0 ainda nao
    avaliado, 1 = registrado e armado, 2 = descartado, 3 = registrado e
    travado, ou seja, ja cantou neste aperto e so canta de novo quando o freio
    for solto de verdade). Carro caro, aviao, barco e moto que nao estao
    liberados sao filtrados ali mesmo, sem ler pedal, sem ler posicao e sem
    tocar som. A avaliacao (tipo de veiculo + valor) acontece uma unica vez por
    carro; o registro acontece uma vez por carro, no evento de criacao do
    CLEO+ (SET_SCRIPT_EVENT_CAR_CREATE). O .ini nao e' lido no laco.

    REQUISITOS
    ============================================================================
    CLEO+ 1.0.7 ou superior. Os opcodes usados sao do CLEO+: GET_CAR_VALUE,
    GET_CAR_PEDALS, GET_VEHICLE_SUBCLASS, GET_ANY_CAR_NO_SAVE_RECURSIVE,
    GET/SET/INIT_EXTENDED_CAR_VAR, SET_SCRIPT_EVENT_CAR_CREATE,
    GET_AUDIO_SFX_VOLUME e os de audio stream (LOAD_3D_AUDIO_STREAM etc.).

    DETALHE DA LINGUAGEM QUE LIMITA O CODIGO
    ============================================================================
    Um script CLEO tem no maximo 32 "slots" de variavel local, e o preco nao e'
    igual para todo mundo: um LVAR_INT/LVAR_FLOAT custa 1 slot, um
    LVAR_TEXT_LABEL (8 bytes, 7 caracteres) custa 2 e um LVAR_TEXT_LABEL16
    (16 bytes, 15 caracteres) custa 4. Nao existe escopo por label, entao GOSUB
    nao abre um conjunto novo de variaveis. Por isso o codigo reaproveita
    algumas variaveis em trechos onde o valor delas ja morreu - por exemplo
    iValue, que e' o dt no comeco do frame e o "valor do carro" dentro da
    avaliacao, e iNext, que so vive dentro do UpdateCar e tambem guarda a
    leitura do modelo no Debug. Os pontos de reuso sempre estao comentados.
    Este script usa 32 dos 32 slots: qualquer variavel nova exige apagar
    outra.

    E o caminho do arquivo de som nao cabe em text label nenhum: 7 ou 15
    caracteres nao armazenam um caminho de 30 e poucos. Ele vai para um buffer
    de 128 bytes na area de dados (txtSoundBuffer), acessado por pBuffer.

    ARMADILHAS QUE JA MORDERAM ESTE MOD (leia antes de mexer no codigo)
    ============================================================================
    A1. Text label de 7/15 caracteres. Um caminho de arquivo truncava e o
        DOES_FILE_EXIST dava falso: "arquivo de som nao encontrado" mesmo
        com o arquivo no lugar. Resolvido na v2.6 com buffer de 128 bytes.

    A2. O ponteiro do buffer precisa do cifrao. E' GET_LABEL_POINTER
        txtSoundBuffer (pBuffer) e depois $pBuffer - sem o $, o nome da
        variavel vira o proprio texto e a funcao le um caminho chamado
        "pBuffer".

    A3. CSET tem os NOMES trocados em relacao ao que ele faz. O opcode 0092,
        chamado de CSET_LVAR_INT_TO_LVAR_FLOAT, na verdade faz FLOAT -> INT
        (o mesmo 0092 do Sanny e' "22@ = float 17@ to_integer"); o 0093,
        chamado de CSET_LVAR_FLOAT_TO_LVAR_INT, faz INT -> FLOAT. Nos dois o
        DESTINO vem primeiro. Como a v2.6 escrevia no sentido inverso, o dt
        virava 0.0, a pressao de frenagem nunca passava do gatilho e o mod
        nao tocava SOM NENHUM - nem no carro do jogador, nem no de NPC. Foi
        o quarto bug deste mod, e ele nao aparecia em nenhum teste de mesa:
        so apareceu no log de execucao (SCRLog) do jogo.

    A4. Recarregar o jogo depois de trocar a .cs. O script ja carregado em
        memoria continua o antigo ate o GTA fechar.

    Compilacao: veja BUILD.md / tools/build.sh nesta pasta.
*/

SCRIPT_START
{
NOP

// ------------------------------------------------------------------ variaveis
LVAR_INT hVeh hNewCar hStream pBuffer
LVAR_INT iSearch iReg iPress iNext iNow iPrevTime iSounds
LVAR_INT iValue iMinValue iMaxValue iCooldown iVehicles iDebug
LVAR_FLOAT x y z fBrake fPress fVol f fStep fRate
LVAR_FLOAT fBrakeMin fTrigger fVolume fRadius fRefSpeed fMinSpeed

    // ============================================================ inicializacao
    WAIT 0
    WAIT 0

    // O caminho do .ini aparece como literal nos comandos abaixo: um text
    // label custaria 4 dos 32 slots do script (e um slot so para o .wav).
    //
    // O caminho do SOM vai para um buffer de 128 bytes (pBuffer) e nao para um
    // text label, porque text label so tem 7 (TEXT_LABEL) ou 15 (TEXT_LABEL16)
    // caracteres e um caminho de arquivo tem 30 e poucos. Ler a string direto
    // num text label truncava o caminho e o DOES_FILE_EXIST dava falso mesmo
    // com o arquivo no lugar certo. Buffer = ate 127 caracteres, 1 slot so.
    GET_LABEL_POINTER txtSoundBuffer (pBuffer)

    // iValue ainda nao e' usada pelo laco principal aqui, entao serve de
    // rascunho para as chaves lidas so no comeco do script.
    IF NOT READ_INT_FROM_INI_FILE "CLEO\BrakePadSound.ini" "Config" "Enabled" iValue
        WRITE_INT_TO_INI_FILE 1 "CLEO\BrakePadSound.ini" "Config" "Enabled"
        iValue = 1
    ENDIF
    IF iValue = 0
        WHILE TRUE
            WAIT 0
        ENDWHILE
    ENDIF

    // ---- arquivo de som (tem que vir antes da checagem, senao o script
    // ---- conferiria sempre o caminho padrao e nunca o que voce configurou) ---
    IF NOT READ_STRING_FROM_INI_FILE "CLEO\BrakePadSound.ini" "Config" "SoundFile" pBuffer
        WRITE_STRING_TO_INI_FILE "CLEO\BrakePadSound\brakepad.wav" "CLEO\BrakePadSound.ini" "Config" "SoundFile"
        READ_STRING_FROM_INI_FILE "CLEO\BrakePadSound.ini" "Config" "SoundFile" pBuffer
    ENDIF

    // Sem arquivo de som nao adianta inspecionar carro nenhum: avisa (com o
    // caminho que ele tentou) e para.
    IF NOT DOES_FILE_EXIST $pBuffer
        PRINT_FORMATTED_NOW "~r~Som de pastilha: nao achei o arquivo de som:~n~w~%s" 10000 $pBuffer
        PRINT_STRING_NOW "~r~Coloque o arquivo em CLEO\BrakePadSound\ ou aponte o caminho na chave SoundFile do BrakePadSound.ini" 10000
        WHILE TRUE
            WAIT 0
        ENDWHILE
    ENDIF

    // ---- quais veiculos cantam (0 = so carros, 1 = + motos/quadriciclos) ----
    IF NOT READ_INT_FROM_INI_FILE "CLEO\BrakePadSound.ini" "Config" "Vehicles" iVehicles
        WRITE_INT_TO_INI_FILE 0 "CLEO\BrakePadSound.ini" "Config" "Vehicles"
        iVehicles = 0
    ENDIF
    IF iVehicles < 0
        iVehicles = 0
    ENDIF
    IF iVehicles > 1
        iVehicles = 1
    ENDIF

    // ---- depuracao: imprime o valor de cada carro novo que aparece ----
    IF NOT READ_INT_FROM_INI_FILE "CLEO\BrakePadSound.ini" "Config" "Debug" iDebug
        WRITE_INT_TO_INI_FILE 0 "CLEO\BrakePadSound.ini" "Config" "Debug"
        iDebug = 0
    ENDIF
    IF iDebug < 0
        iDebug = 0
    ENDIF
    IF iDebug > 1
        iDebug = 1
    ENDIF

    // ---- valor do carro (vem da handling) ----
    IF NOT READ_INT_FROM_INI_FILE "CLEO\BrakePadSound.ini" "Config" "MinValue" iMinValue
        WRITE_INT_TO_INI_FILE 0 "CLEO\BrakePadSound.ini" "Config" "MinValue"
        iMinValue = 0
    ENDIF
    IF NOT READ_INT_FROM_INI_FILE "CLEO\BrakePadSound.ini" "Config" "MaxValue" iMaxValue
        WRITE_INT_TO_INI_FILE 32000 "CLEO\BrakePadSound.ini" "Config" "MaxValue"
        iMaxValue = 32000
    ENDIF

    // ---- audio ----
    IF NOT READ_FLOAT_FROM_INI_FILE "CLEO\BrakePadSound.ini" "Config" "Volume" fVolume
        WRITE_FLOAT_TO_INI_FILE 0.7 "CLEO\BrakePadSound.ini" "Config" "Volume"
        fVolume = 0.7
    ENDIF
    IF NOT READ_FLOAT_FROM_INI_FILE "CLEO\BrakePadSound.ini" "Config" "Radius" fRadius
        WRITE_FLOAT_TO_INI_FILE 70.0 "CLEO\BrakePadSound.ini" "Config" "Radius"
        fRadius = 70.0
    ENDIF

    // ---- como o pedal vira "pressao" ----
    IF NOT READ_FLOAT_FROM_INI_FILE "CLEO\BrakePadSound.ini" "Config" "BrakeThreshold" fBrakeMin
        WRITE_FLOAT_TO_INI_FILE 0.15 "CLEO\BrakePadSound.ini" "Config" "BrakeThreshold"
        fBrakeMin = 0.15
    ENDIF
    IF NOT READ_FLOAT_FROM_INI_FILE "CLEO\BrakePadSound.ini" "Config" "TriggerPressure" fTrigger
        WRITE_FLOAT_TO_INI_FILE 0.08 "CLEO\BrakePadSound.ini" "Config" "TriggerPressure"
        fTrigger = 0.08
    ENDIF
    IF NOT READ_FLOAT_FROM_INI_FILE "CLEO\BrakePadSound.ini" "Config" "PressureRate" fRate
        WRITE_FLOAT_TO_INI_FILE 5.0 "CLEO\BrakePadSound.ini" "Config" "PressureRate"
        fRate = 5.0
    ENDIF

    // ---- velocidade e repeticao ----
    IF NOT READ_FLOAT_FROM_INI_FILE "CLEO\BrakePadSound.ini" "Config" "RefSpeed" fRefSpeed
        WRITE_FLOAT_TO_INI_FILE 110.0 "CLEO\BrakePadSound.ini" "Config" "RefSpeed"
        fRefSpeed = 110.0
    ENDIF
    // Velocidade minima para cantar, em km/h. O script compara em "~m/s" (o
    // que GET_CAR_SPEED devolve), entao a divisao por 3.6 acontece uma vez
    // aqui e o laco principal so compara.
    IF NOT READ_FLOAT_FROM_INI_FILE "CLEO\BrakePadSound.ini" "Config" "MinSpeed" fMinSpeed
        WRITE_FLOAT_TO_INI_FILE 10.0 "CLEO\BrakePadSound.ini" "Config" "MinSpeed"
        fMinSpeed = 10.0
    ENDIF
    fMinSpeed /= 3.6
    IF NOT READ_INT_FROM_INI_FILE "CLEO\BrakePadSound.ini" "Config" "Cooldown" iCooldown
        WRITE_INT_TO_INI_FILE 1500 "CLEO\BrakePadSound.ini" "Config" "Cooldown"
        iCooldown = 1500
    ENDIF

    // ---- protecoes contra .ini editado na mao ----
    IF iMaxValue < iMinValue
        iMaxValue = iMinValue
    ENDIF
    IF iCooldown < 0
        iCooldown = 0
    ENDIF
    IF fMinSpeed < 0.0
        fMinSpeed = 0.0
    ENDIF
    IF fRefSpeed < 10.0
        fRefSpeed = 10.0
    ENDIF
    IF fRate < 0.1
        fRate = 0.1
    ENDIF
    IF fRadius < 5.0
        fRadius = 5.0
    ENDIF
    CLAMP_FLOAT fVolume 0.0 1.0 (fVolume)
    CLAMP_FLOAT fBrakeMin 0.0 0.95 (fBrakeMin)
    CLAMP_FLOAT fTrigger 0.0 1.0 (fTrigger)

    // Os eventos de criacao rodam no MEIO deste laco, entao o handler nao pode
    // encostar em hVeh/iSearch/iReg/iPress/...: ele usa so hNewCar.
    SET_SCRIPT_EVENT_CAR_CREATE ON OnCarCreate hNewCar

    // Carros que ja estavam no pool quando o script carregou nao passam pelo
    // evento de criacao: registra todos uma vez.
    iSearch = 0
    WHILE GET_ANY_CAR_NO_SAVE_RECURSIVE iSearch (iSearch hNewCar)
        GOSUB InitCar
    ENDWHILE

    GET_GAME_TIMER (iPrevTime)
    // A mensagem mostra o caminho que o script REALMENTE esta usando. Se o
    // jogo mostra outra coisa aqui, o problema esta no BrakePadSound.ini (ou no
    // ModLoader, que reescreve o .ini na pasta CLEO do mod).
    PRINT_FORMATTED_NOW "Som de pastilha v2.7 - %s (ate %d, raio %f m)" 5000 $pBuffer iMaxValue fRadius

    // =================================================================== laco
    WHILE TRUE
        WAIT 0

        // ---- quanto tempo passou desde o ultimo frame (independe do FPS) ----
        // iValue e' o rascunho do dt aqui: dentro do laco ele so e' usado pela
        // avaliacao de carro, que acontece mais abaixo e sobrescreve ele.
        GET_GAME_TIMER (iNow)
        iValue = iNow
        iValue -= iPrevTime
        iPrevTime = iNow
        IF iValue < 0
            iValue = 0
        ENDIF
        IF iValue > 200
            iValue = 200
        ENDIF
        // CSET tem os NOMES trocados em relacao ao que ele faz (ver a nota
        // "ARMADILHA 4" no cabecalho): o 0092 e' float->int e o 0093 e' int->float,
        // e o destino vem PRIMEIRO. Para fStep = (float)dt uso o 0093, ou seja,
        // CSET_LVAR_FLOAT_TO_LVAR_INT com o destino na frente. Escrito ao
        // contrario (como estava antes), o dt virava 0.0, a pressao nunca
        // subia acima do gatilho e o mod NAO TOCAVA NENHUM SOM - nem no carro
        // do jogador, nem no dos NPCs.
        CSET_LVAR_FLOAT_TO_LVAR_INT fStep iValue
        fStep *= 0.001
        fStep *= fRate          // quanto a pressao sobe neste frame

        iSounds = 0
        iSearch = 0
        WHILE GET_ANY_CAR_NO_SAVE_RECURSIVE iSearch (iSearch hVeh)
            GET_EXTENDED_CAR_VAR hVeh AUTO 1 iReg
            IF iReg = 0
                // primeira vez que este script ve este carro
                GOSUB EvaluateCar
            ELSE
                // iReg = 1 = armado, 3 = travado (Cooldown = 0, ja cantou
                // neste aperto), 2 = descartado. So o 2 sai do laco.
                IF NOT iReg = 2
                    IF IS_CAR_ENGINE_ON hVeh
                        GET_CAR_COORDINATES hVeh x y z
                        IF LOCATE_CAMERA_DISTANCE_TO_COORDINATES x y z fRadius
                            GET_CAR_PEDALS hVeh f fBrake     // o gas vai no temporario f
                            GET_CAR_SPEED hVeh fVol
                            fVol *= 3.6                     // fVol = km/h
                            GOSUB UpdateCar
                        ENDIF
                    ENDIF
                ENDIF
            ENDIF
        ENDWHILE
    ENDWHILE

    // ================================================================== eventos
    // Veiculo criado no jogo: so reserva as 3 var. estendida. Quem decide se
    // ele pode chiar e' o EvaluateCar, no laco principal (aqui as variaveis de
    // configuracao estao em uso pelo laco).
    OnCarCreate:
        GOSUB InitCar
        RETURN_SCRIPT_EVENT

    InitCar:
        // 1 = avaliado (1 = pode chiar, 2 = descartado) | 2 = pressao x1000
        // 3 = instante (ms) do proximo chiado permitido
        INIT_EXTENDED_CAR_VARS hNewCar AUTO 3
        SET_EXTENDED_CAR_VAR hNewCar AUTO 1 0
        RETURN

    // ================================================================= avaliacao
    // Roda UMA vez por veiculo e decide se ele tem pastilha gasta.
    // iReg entra aqui valendo 0 e sai com 1 (pode chiar) ou 2 (descartado).
    EvaluateCar:
        // ---- tipo de veiculo ----
        // iVehicles vem da inicializacao (0 = so carros; 1 = carros +
        // motos/quadriciclos). Aereo e aquatico nunca chiama: o "freio" deles
        // nao e' pastilha.
        GET_VEHICLE_SUBCLASS hVeh (iReg)
        IF iVehicles = 0
            IF iReg = VEHICLE_SUBCLASS_AUTOMOBILE
                GOTO EvalValue
            ENDIF
        ELSE
            IF iReg = VEHICLE_SUBCLASS_PLANE
            OR iReg = VEHICLE_SUBCLASS_FPLANE
            OR iReg = VEHICLE_SUBCLASS_HELI
            OR iReg = VEHICLE_SUBCLASS_FHELI
            OR iReg = VEHICLE_SUBCLASS_BOAT
                GOTO EvalReject
            ENDIF
        ENDIF

        EvalValue:
        // Valor monetario do carro, lido do proprio jogo (handling).
        GET_CAR_VALUE hVeh (iValue)

        // Debug = 1 mostra o valor de TODO carro que chegou ate aqui, antes dos
        // cortes: e' assim que se descobre o MinValue/MaxValue certo (se o
        // print viesse depois, so apareceria o que ja foi aceito).
        // iNext so e' usado dentro do UpdateCar, entao serve de rascunho aqui.
        IF iDebug = 1
            GET_CAR_MODEL hVeh (iNext)
            PRINT_FORMATTED_NOW "modelo %d = %d (corte %d..%d)" 2500 iNext iValue iMinValue iMaxValue
        ENDIF

        IF iValue < iMinValue
            GOTO EvalReject
        ENDIF
        IF iValue > iMaxValue
            GOTO EvalReject
        ENDIF

        iReg = 1
        GOTO EvalDone

        EvalReject:
        iReg = 2

        EvalDone:
        SET_EXTENDED_CAR_VAR hVeh AUTO 1 iReg
        RETURN

    // =================================================================== update
    // Um carro registrado (iReg = 1), com motor ligado, dentro do raio.
    UpdateCar:
        // var 2 = pressao (x1000) | var 3 = instante do proximo som permitido
        GET_EXTENDED_CAR_VAR hVeh AUTO 2 iPress
        GET_EXTENDED_CAR_VAR hVeh AUTO 3 iNext
        CSET_LVAR_FLOAT_TO_LVAR_INT fPress iPress
        fPress *= 0.001

        // ---- pressao de frenagem: sobe pisando, desce soltando ----
        IF fBrake > fBrakeMin
            f = fStep
            f *= fBrake
            fPress += f
        ELSE
            // Soltou o pedal: a pressao cai o DOBRO do passo, para o chiado
            // morrer rapido em vez de ficar "grudado" no carro.
            f = fStep
            f *= 2.0
            fPress -= f
        ENDIF
        IF fPress > 1.0
            fPress = 1.0
        ENDIF
        IF fPress < 0.0
            fPress = 0.0
        ENDIF

        // ---- parado ou quase parado: a pastilha nao canta ----
        // fVol esta em km/h; fMinSpeed foi convertido para a mesma unidade do
        // GET_CAR_SPEED na inicializacao. MinSpeed = 0 desliga este corte (util
        // para ouvir o chiado com o carro parado).
        IF fVol < fMinSpeed
            fPress = 0.0
        ENDIF

        // ---- destrava o "um som por aperto" (Cooldown = 0): so quando a
        // ---- pressao caiu de verdade abaixo do gatilho E ja passou o piso
        // ---- de 250 ms desde o ultimo som. O piso existe porque a IA que
        // ---- fica pisando no freio conta cada pisada como um aperto novo e,
        // ---- sem ele, viraria metralhadeira.
        IF iReg = 3
            IF fPress <= fTrigger
                iValue = iNow
                iValue -= iNext
                IF iValue >= 250
                    iReg = 1
                    fPress = 0.0
                    SET_EXTENDED_CAR_VAR hVeh AUTO 1 1
                ENDIF
            ENDIF
        ENDIF

        // ---- deu pra chiar? ----
        // iReg = 3 = travado (ja cantou neste aperto): o som so sai com iReg
        // = 1, porque a pressao continua subindo enquanto o freio esta preso.
        IF fPress > fTrigger
            IF iReg = 1
                IF iNow > iNext
                    GOSUB PlaySqueal
                    iNext = iNow
                    IF iCooldown = 0
                        iReg = 3
                        SET_EXTENDED_CAR_VAR hVeh AUTO 1 3
                    ELSE
                        iNext += iCooldown
                    ENDIF
                    SET_EXTENDED_CAR_VAR hVeh AUTO 3 iNext
                ENDIF
            ENDIF
        ENDIF

        // a var. estendida guarda inteiro: pressao x1000 (0.001 de precisao)
        f = fPress
        f *= 1000.0
        CSET_LVAR_INT_TO_LVAR_FLOAT iPress f
        SET_EXTENDED_CAR_VAR hVeh AUTO 2 iPress
        RETURN

    // ====================================================================== som
    PlaySqueal:
        // quanto mais rapido o carro esta, mais alto o chiado (0.25 ate 1.0)
        f = fVol
        f /= fRefSpeed
        IF f < 0.25
            f = 0.25
        ENDIF
        IF f > 1.0
            f = 1.0
        ENDIF

        GET_AUDIO_SFX_VOLUME (fVol)     // volume de efeitos do menu (0xB5FCCC)
        fVol *= fVolume
        // A pressao do pedal pesa no volume, mas so depois de um piso de 50%:
        // um toque leve de freio (0.2 de pedal) daria 20% do volume e o chiado
        // sumiria no meio do barulho da rua. Aqui 0 vira 0.5 e 1.0 continua
        // 1.0. fBrake ja e' lido de novo no proximo frame.
        fBrake *= 0.5
        fBrake += 0.5
        fVol *= fBrake
        fVol *= f                      // f = speed/RefSpeed, com piso de 0.25
        CLAMP_FLOAT fVol 0.0 1.0 (fVol)

        // Teto de 4 sons por frame: uma batida de 15 carros na sua frente nao
        // pode virar 15 chiados ao mesmo tempo. (O iSounds volta a zero a cada
        // frame; a avaliacao de carro nao mexe nele.)
        IF iSounds < 4
            IF LOAD_3D_AUDIO_STREAM $pBuffer (hStream)
                SET_AUDIO_STREAM_LOOPED hStream FALSE
                SET_PLAY_3D_AUDIO_STREAM_AT_CAR hStream hVeh
                SET_AUDIO_STREAM_VOLUME hStream fVol
                SET_AUDIO_STREAM_STATE hStream 1
                iSounds += 1
            ENDIF
        ENDIF
        RETURN
}
SCRIPT_END

// ============================================================================
// AREA DE DADOS
// ----------------------------------------------------------------------------
// txtSoundBuffer = 128 bytes zerados onde o script guarda o caminho do arquivo
// de som lido do .ini (ate 127 caracteres). Um LVAR_TEXT_LABEL nao serve aqui:
// text label guarda so 7 (TEXT_LABEL) ou 15 (TEXT_LABEL16) caracteres, e um
// caminho de arquivo tem 30 e poucos - o caminho era truncado e o script
// achava que o arquivo nao existia. Ver BUILD.md, armadilha 3.
// ============================================================================
txtSoundBuffer:
DUMP
00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
ENDDUMP
