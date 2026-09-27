/*
    ============================================================================
    SOM DE PASTILHA DE FREIO GASTA - v2.9 (CLEO+, edicao NPCs)
    ----------------------------------------------------------------------------
    Reescrito do zero em gta3script (gta3sc) a partir do mod original
    "Som de pastilha de freio gasta" v2.5.1 (Amilton, Fabio, Junior_Djjr),
    usando a mesma abordagem do "Air Brake Sound v3" do Junior_Djjr - que e' o
    mod que passou a aplicar o som de freio a ar em TODOS os NPCs, e nao so no
    carro do jogador.

    O QUE O MOD FAZ
    ============================================================================
    Quando alguem - VOCE ou um NPC - freia de verdade um carro velho, aparece
    aquele chiado agudo e horrivel de pastilha gasta. O som e' 3D e sai do
    carro, entao da para ouvir o chiado do carro ao lado, de tras, atraves da
    rua.

    O SOM TEM FIM: NASCE E MORRE COM A FRENAGEM
    ============================================================================
    Este e' o ponto que mais mudou na v2.9, e o que o jogador pediu depois de
    testar: o som nao pode ser um evento solto, ele tem inicio, meio e FIM - e
    esse fim e' exatamente "a frenagem acabou".

    A v2.8 (como a v2.5.1 antes dela) disparava o som e nunca mais mexia nele:
    jogava o stream no carro e esquecia o handle. Com um som curto (o
    placeholder de 1,2 s) isso passava batido. Com um som de verdade - o
    brakepad de ~6 s que o jogador usa - o chiado continuava soando por 6 s
    DEPOIS de soltar o freio e parar o carro. E' este o bug reportado.

    A v2.9 guarda o handle do stream de cada carro (var. estendida 3) e o
    desliga de verdade quando a frenagem termina:

      - o som toca SO ENQUANTO a frenagem estiver valendo;
      - quando o pe sai do freio, ou quando a frenagem alivia (a queda de
        velocidade passa a ser menor que BrakeForce), ou quando o carro para,
        o volume vai a zero em ~Fade segundos e o stream e' REMOVIDO de
        verdade (REMOVE_AUDIO_STREAM) - ele nao fica soando no vazio;
      - a proxima frenagem comeca um som novo, do zero.

    Isso vale para o carro do jogador e para o de TODOS os NPCs, porque o
    caminho e' o mesmo.

    TRES COISAS QUE A v2.9 FAZ E A v2.8 NAO FAZIA
    ============================================================================
    1. Um som so por carro, nao um por disparo. A v2.8 criava um stream novo
       toda vez que a frenagem passava do gatilho: com um som de 6 s e um
       intervalo de 1,5 s entre disparos, isso significava 4 copias do mesmo
       chiado sobrepostas no mesmo carro. A v2.9 cria no maximo UM stream por
       carro e so cria outro depois de ter desligado e removido o anterior.
       Por isso nao existe mais sobreposicao, por mais longo que o som seja.

    2. O volume ACOMPANHA a frenagem, frame a frame. Quanto mais forte a
       frenagem, mais alto o chiado; quando a frenagem acaba, o som desce ate
       zero sozinho. A pastilha canta DURANTE a frenagem, como na vida real.

    3. O som nunca gruda. Todo carro que esta com som tocando continua sendo
       atualizado mesmo se sair do raio da camera ou desligar o motor: nesses
       casos o som e' desvanecido e removido, em vez de ficar tocando sozinho
       num carro que ja nao esta mais na cena.

    O GATILHO
    ============================================================================
    A DESACELERACAO do carro, exatamente como no mod original: o script compara
    a velocidade deste frame com a do frame anterior e so comeca o chiado
    quando a queda passa de BrakeForce (medida em m/s2, o popular "g"). E' por
    isso que o mod soa naturalmente:

      - freando a 80 km/h, o chiado aparece na hora;
      - com o carro PARADO, nao comeca nada (carro parado nao desacelera);
      - uma freada de leve nao chega no gatilho;
      - uma batida forte canta alto e longo;
      - o chiado NAO precisa de uma "pressao" que acumule: ele mede a frenagem
        de cada frame, entao some no mesmo instante em que ela acaba.

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
    3. Volume ESCALA com a velocidade, com a pressao do pedal e com o volume de
       efeitos do menu (0xB5FCCC) em todos os sons, inclusive os dos NPCs.
    4. Sons 3D de verdade: a pastilha e' do carro que esta freando, com a
       atenuacao por distancia do proprio jogo.
    5. Fim de som de verdade: o stream e' desvanecido e removido no fim da
       frenagem (Fade, no .ini).
    6. Sem sobreposicao e sem acumulo: no maximo um som por carro, e um teto
       de 6 sons ao mesmo tempo no mundo inteiro.
    7. Independente de FPS: o desvanecimento e' calculado em segundos e
       milissegundos (GET_GAME_TIMER), e nao "por frame".
    8. Pouco I/O: o .ini e' lido UMA vez, no comeco. O som so e' disparado para
       carros dentro do raio (Radius), com motor ligado, com valor dentro do
       limite e com o pedal mesmo apertado.

    DESEMPENHO (o ponto que mais pesa quando o mod roda no mundo inteiro)
    ============================================================================
    O pool de veiculos do SA (ate ~200 slots) e' percorrido uma vez por frame,
    mas para cada carro o unico opcode barato e' o GET_EXTENDED_CAR_VAR, que
    so responde para os carros QUE ESTE SCRIPT JA AVALIOU (var 1 = 0 ainda nao
    avaliado, 1 = registrado e armado, 2 = descartado, 3 = registrado e com
    som tocando). Carro caro, aviao, barco e moto que nao estao liberados sao
    filtrados ali mesmo, sem ler velocidade, sem ler posicao e sem tocar som. A
    avaliacao (tipo de veiculo + valor) acontece uma unica vez por carro; o
    registro acontece uma vez por carro, no evento de criacao do CLEO+ (SET_
    SCRIPT_EVENT_CAR_CREATE). O .ini nao e' lido no laco.

    Detalhe importante do contador de sons: iSounds e' REcontado a cada frame e
    incrementado so para os carros que estao com som tocando. Isso e' proposital
    - se um carro com som for destruido, ele some do laco e para de contar,
    entao o contador se conserta sozinho e o mod nao "trava" depois de algumas
    destruicoes de veiculo no meio da cidade. (O stream daquele carro continua
    tocando sozinho ate acabar, porque ninguem mais tem o handle dele; como o
    som nao e' loopado, ele simplesmente termina.)

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
    iValue, que e' o dt no comeco do frame, o "valor do carro" dentro da
    avaliacao e o rascunho dentro do MeasureBrake; e f, que e' o gas do
    pedal em um trecho e o volume em outro. Os pontos de reuso sempre estao
    comentados. Este script usa 31 dos 32 slots.

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
        DESTINO vem primeiro. Como a v2.6/v2.7 escrevia no sentido inverso, o
        dt virava 0.0, a pressao nunca passava do gatilho e o mod nao tocava
        SOM NENHUM. Foi o quarto bug deste mod, so visivel no log de execucao
        (SCRLog) do jogo. Ver as tres conversoes no MeasureBrake/AdjustSound.

    A4. Recarregar o jogo depois de trocar a .cs. O script ja carregado em
        memoria continua o antigo ate o GTA fechar.

    Compilacao: veja BUILD.md / tools/build.sh nesta pasta.
*/

SCRIPT_START
{
NOP

// ------------------------------------------------------------------ variaveis
LVAR_INT hVeh hNewCar hStream pBuffer
LVAR_INT iSearch iReg iPrevVel iSounds iNow iPrevTime iValue
LVAR_INT iMinValue iMaxValue iVehicles iDebug
LVAR_FLOAT x y z fVel fDecel fBrake f fStep fVol fIncr
LVAR_FLOAT fBrakeMin fForce fVolume fRadius fRefSpeed fFade

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

    // ---- como a frenagem vira chiado ----
    // BrakeThreshold: pedal minimo (0 a 1) para contar como freada. E' a
    // trava contra falso positivo - so conta frenagem com pe mesmo no freio.
    IF NOT READ_FLOAT_FROM_INI_FILE "CLEO\BrakePadSound.ini" "Config" "BrakeThreshold" fBrakeMin
        WRITE_FLOAT_TO_INI_FILE 0.15 "CLEO\BrakePadSound.ini" "Config" "BrakeThreshold"
        fBrakeMin = 0.15
    ENDIF
    // BrakeForce: queda minima de velocidade (em m/s2, ou seja, "g") para o
    // chiado comecar E continuar. Freada de verdade passa facil; encostinho
    // no freio nao chega.
    IF NOT READ_FLOAT_FROM_INI_FILE "CLEO\BrakePadSound.ini" "Config" "BrakeForce" fForce
        WRITE_FLOAT_TO_INI_FILE 2.5 "CLEO\BrakePadSound.ini" "Config" "BrakeForce"
        fForce = 2.5
    ENDIF
    // Fade: em quantos segundos o som desaparece quando a frenagem acaba.
    // 0.2 = some em um quinto de segundo. 0 = corte seco (use so se o seu
    // arquivo estalar no fim).
    IF NOT READ_FLOAT_FROM_INI_FILE "CLEO\BrakePadSound.ini" "Config" "Fade" fFade
        WRITE_FLOAT_TO_INI_FILE 0.2 "CLEO\BrakePadSound.ini" "Config" "Fade"
        fFade = 0.2
    ENDIF
    IF fFade < 0.0
        fFade = 0.0
    ENDIF
    // vira "volume por segundo": com 0.2 s de fade, o volume cai 5.0 por s.
    IF fFade > 0.0
        f = 1.0
        f /= fFade
        fFade = f
    ELSE
        fFade = 1000.0          // Fade = 0: o primeiro passo ja zera
    ENDIF

    // ---- velocidade de referencia (so para o volume) ----
    IF NOT READ_FLOAT_FROM_INI_FILE "CLEO\BrakePadSound.ini" "Config" "RefSpeed" fRefSpeed
        WRITE_FLOAT_TO_INI_FILE 110.0 "CLEO\BrakePadSound.ini" "Config" "RefSpeed"
        fRefSpeed = 110.0
    ENDIF

    // ---- protecoes contra .ini editado na mao ----
    IF iMaxValue < iMinValue
        iMaxValue = iMinValue
    ENDIF
    IF fForce < 0.0
        fForce = 0.0
    ENDIF
    IF fRefSpeed < 10.0
        fRefSpeed = 10.0
    ENDIF
    IF fRadius < 5.0
        fRadius = 5.0
    ENDIF
    CLAMP_FLOAT fVolume 0.0 1.0 (fVolume)
    CLAMP_FLOAT fBrakeMin 0.0 0.95 (fBrakeMin)

    // Os eventos de criacao rodam no MEIO deste laco, entao o handler nao pode
    // encostar em hVeh/iSearch/iReg/...: ele usa so hNewCar.
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
    PRINT_FORMATTED_NOW "Som de pastilha v2.9 - %s (ate %d, raio %f m)" 5000 $pBuffer iMaxValue fRadius

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
        // CSET tem os NOMES trocados (0093 = int -> float), destino primeiro.
        // fStep = dt em SEGUNDOS (o desvanecimento e' medido por segundo, e
        // nao por frame - e' por isso que o fade dura o mesmo em 30 ou 240 FPS).
        CSET_LVAR_FLOAT_TO_LVAR_INT fStep iValue
        fStep *= 0.001

        // Contador de sons tocando: recontado do zero a cada frame e
        // incrementado so para os carros que tem som (var 1 = 3). Se um
        // desses carros for destruido, ele some do laco e para de contar, e o
        // teto de 6 sons se refaz sozinho.
        iSounds = 0

        iSearch = 0
        WHILE GET_ANY_CAR_NO_SAVE_RECURSIVE iSearch (iSearch hVeh)
            GET_EXTENDED_CAR_VAR hVeh AUTO 1 iReg
            IF iReg = 0
                // primeira vez que este script ve este carro
                GOSUB EvaluateCar
            ELSE
                IF NOT iReg = 2
                    IF iReg = 3
                        // tem som tocando: este carro NAO passa pelo teste de
                        // distancia nem de motor. Se ele saiu do raio ou
                        // desligou o motor, o som e' desvanecido e removido
                        // aqui - e' o que impede o chiado de ficar tocando
                        // sozinho depois que o carro sumiu de cena.
                        iSounds += 1
                        GOSUB UpdateSounding
                    ELSE
                        // armado: so interessa se o carro estiver perto, com
                        // motor ligado
                        IF IS_CAR_ENGINE_ON hVeh
                            GET_CAR_COORDINATES hVeh x y z
                            IF LOCATE_CAMERA_DISTANCE_TO_COORDINATES x y z fRadius
                                GOSUB PlaySqueal
                            ENDIF
                        ENDIF
                    ENDIF
                ENDIF
            ENDIF
        ENDWHILE
    ENDWHILE

    // ================================================================== eventos
    // Veiculo criado no jogo: so reserva as 4 var. estendida. Quem decide se
    // ele pode chiar e' o EvaluateCar, no laco principal (aqui as variaveis de
    // configuracao estao em uso pelo laco).
    OnCarCreate:
        GOSUB InitCar
        RETURN_SCRIPT_EVENT

    InitCar:
        // 1 = avaliado (1 = armado, 2 = descartado, 3 = com som tocando)
        // 2 = velocidade do frame anterior (em m/s x 1000) - o proprio carro
        //     guarda a velocidade, para o script medir a queda no proximo frame
        // 3 = handle do stream de audio deste carro (0 = sem som tocando)
        // 4 = volume atual do som (x1000, 0 a 1000) - e' o que o fade move
        INIT_EXTENDED_CAR_VARS hNewCar AUTO 4
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
            // Sem este GOTO, um aviao/barco "caia" na avaliacao de valor em
            // vez de ser descartado (e, com MinValue = 0, era aceito).
            GOTO EvalReject
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
        // iValue ja morreu como valor do carro aqui, entao serve de rascunho.
        IF iDebug = 1
            PRINT_FORMATTED_NOW "carro %d = %d (corte %d..%d)" 2500 hVeh iValue iMinValue iMaxValue
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

    // ================================================================== medicao
    // (o gatilho em si mora no PlaySqueal, que mede a frenagem do carro
    //  ATUAL - hVeh - e decide se o som nasce, continua ou morre)

    // ================================================================== medicao
    // Mede a frenagem do carro ATUAL (hVeh) e deixa o resultado em fDecel e
    // fVel. Chamado pelo PlaySqueal (que e' chamado tanto por um carro que
    // acabou de dar o gatilho quanto por um que ja tem som) - e' por isso que
    // fBrake, f e iValue sao tratados como rascunho dentro daqui.
    MeasureBrake:
        GET_CAR_SPEED hVeh fVel
        // ---- queda de velocidade desde o frame anterior, em m/s2 ----
        // A velocidade anterior mora na var. estendida 2 do proprio carro.
        // (0093 = int -> float: destino primeiro, ver a armadilha A3.)
        GET_EXTENDED_CAR_VAR hVeh AUTO 2 iPrevVel
        CSET_LVAR_FLOAT_TO_LVAR_INT f iPrevVel
        f *= 0.001                     // velocidade anterior, em m/s
        fDecel = f
        fDecel -= fVel                  // quanto caiu neste frame (m/s)
        IF fStep > 0.0
            fDecel /= fStep             // ...por segundo = m/s2
        ENDIF
        // guarda a velocidade de HOJE, para o proximo frame medir a queda
        // (0092 = float -> int: destino primeiro)
        f = fVel
        f *= 1000.0
        CSET_LVAR_INT_TO_LVAR_FLOAT iPrevVel f
        SET_EXTENDED_CAR_VAR hVeh AUTO 2 iPrevVel
        RETURN

    // ===================================================================== som
    // Chamado do laco principal para TODO carro que tem som tocando (var 1 = 3),
    // incluindo os que ja sairam do raio: eles precisam de um chance de ter o
    // som desligado, senao o chiado ficaria tocando no mundo sem carro nenhum.
    UpdateSounding:
        IF IS_CAR_ENGINE_ON hVeh
            GET_CAR_COORDINATES hVeh x y z
            IF LOCATE_CAMERA_DISTANCE_TO_COORDINATES x y z fRadius
                GOSUB PlaySqueal
                RETURN
            ENDIF
        ENDIF
        GOSUB StopSound
        RETURN

    // Todo o ciclo de vida do som passa por aqui. O som do carro ATUAL (hVeh)
    // e' uma "maquina" de tres estados, guardada nas var. estendidas do carro:
    //
    //   var 3 = 0 (sem som)  ->  criado em CreateSound
    //   var 3 = handle       ->  volume em AdjustSound
    //   var 3 = 0 de novo    ->  pronto para a proxima frenagem
    //
    // PlaySqueal e' o unico ponto de entrada do laco principal (chamado tanto
    // por um carro armado quanto por um carro que ja tem som). Ele descobre o
    // estado e chama a rotina certa.
    PlaySqueal:
        // ---- o carro ja tem som? entao o cuidado agora e' MANTER e TERMINAR
        GET_EXTENDED_CAR_VAR hVeh AUTO 3 hStream
        IF NOT hStream = 0
            GOSUB KeepSound
            RETURN
        ENDIF
        // ---- sem som: o gatilho e' a DESACELERACAO, como no mod original ----
        // (a leitura do pedal vem aqui, e nao no laco principal, porque o
        //  gas e' guardado no temporario f - ver a nota de reuso no topo)
        GET_CAR_PEDALS hVeh f fBrake
        GOSUB MeasureBrake
        IF NOT fVel < 0.5            // 0.5 m/s = 1,8 km/h: abaixo disso o carro
                                    // esta parado e nao existe frenagem
            IF fBrake > fBrakeMin    // pe no freio?
                IF fDecel > fForce    // queda de velocidade forte o bastante?
                    IF fDecel < 25.0  // teto de seguranca (ver MeasureBrake)
                        GOSUB CreateSound
                    ENDIF
                ENDIF
            ENDIF
        ENDIF
        RETURN

    // =================================================================== volume
    // Quanto de volume a frenagem DESTE frame merece. Deixa o resultado em f,
    // que e' o "alvo" do AdjustSound. f = 0 significa "a frenagem acabou:
    // o som tem de morrer".
    BrakeVolume:
        f = 0.0
        IF fVel < 0.5
            RETURN                      // carro parado: pastilha nao canta
        ENDIF
        IF fBrake < fBrakeMin
            RETURN                      // pe fora do freio
        ENDIF
        IF fDecel < fForce
            RETURN                      // freada leve demais
        ENDIF
        IF fDecel > 25.0
            RETURN                      // pico falso (velocidade antiga)
        ENDIF
        // ---- quanto mais forte a frenagem, mais alto ----
        // A escala e' fDecel / (2 x BrakeForce): no proprio limite do gatilho
        // o som ja aparece a ~50% e numa batida forte (2x o limite, ~0,5 g)
        // chega ao maximo. Como o BrakeForce e' o mesmo numero que dispara o
        // som, mexer nele muda o gatilho E a curva de volume juntas - faz
        // sentido: frenagem mais forte exigida = chiado mais alto.
        f = fDecel
        f *= 0.5
        f /= fForce
        IF f > 1.0
            f = 1.0
        ENDIF
        // ---- mais rapido = mais alto (piso de 0.25, teto de 1.0) ----
        // O 3.6 converte o "~m/s" do GET_CAR_SPEED em km/h, como no mod
        // original; fVel ja morreu como medida depois do fDecel acima.
        fVel *= 3.6
        fVel /= fRefSpeed
        IF fVel > 1.0
            fVel = 1.0
        ENDIF
        IF fVel < 0.25
            fVel = 0.25
        ENDIF
        f *= fVel
        // ---- o pedal ainda pesa, com piso de 50% ----
        fBrake *= 0.5
        fBrake += 0.5
        f *= fBrake
        CLAMP_FLOAT f 0.0 1.0 (f)
        RETURN

    // ===================================================================== cria
    // Primeira vez que este carro chiadeia dentro desta frenagem: cria UM
    // stream, liga nele e comeca a tocar em volume zero (o AdjustSound
    // levanta o volume em seguida, e isso ja e' o fade-in).
    CreateSound:
        GOSUB BrakeVolume
        // ---- teto global: 6 sons ao mesmo tempo no mundo inteiro ----
        // Alem disso o carro NAO comeca a cantar agora, mas continua armado
        // (var 1 = 1): na proxima frenagem dele o som toca, nada se perde.
        IF iSounds < 6
            IF LOAD_3D_AUDIO_STREAM $pBuffer (hStream)
                SET_AUDIO_STREAM_LOOPED hStream FALSE
                SET_PLAY_3D_AUDIO_STREAM_AT_CAR hStream hVeh
                SET_AUDIO_STREAM_VOLUME hStream 0.0
                SET_AUDIO_STREAM_STATE hStream 1
                SET_EXTENDED_CAR_VAR hVeh AUTO 3 hStream
                SET_EXTENDED_CAR_VAR hVeh AUTO 4 0     // volume atual = 0
                SET_EXTENDED_CAR_VAR hVeh AUTO 1 3     // agora este carro tem som
                // NAO chama AdjustSound aqui de proposito: ele confere o estado
                // do stream, e um stream recem-criado pode responder "parado"
                // no mesmo frame em que foi ligado - e o script mataria o som
                // recem-criado. O volume sobe a partir do proximo frame, pelo
                // KeepSound (indiferenca de um frame, de uns 16 ms).
            ENDIF
        ENDIF
        RETURN

    // =================================================================== mantem
    // O carro ja tem som: mede a frenagem deste frame e manda o volume agir.
    // Se a frenagem acabou, o alvo vira 0 e o AdjustSound desliga.
    KeepSound:
        GET_CAR_PEDALS hVeh f fBrake
        GOSUB MeasureBrake
        GOSUB BrakeVolume
        GOSUB AdjustSound
        RETURN

    // =================================================================== desliga
    // O som deste carro esta tocando, mas o carro parou de ser avaliado (saiu
    // do raio da camera ou desligou o motor). Mesmo assim o som tem de sumir -
    // e' aqui que o chiado de um carro abandonado nao fica tocando no mundo.
    StopSound:
        f = 0.0
        GOSUB AdjustSound
        RETURN

    // =================================================================== ajusta
    // Move o volume do som deste carro em direcao ao alvo f, com o passo dado
    // por Fade (volume por segundo), e DESLIGA + REMOVE o stream no fim. Esta
    // e' a rotina que resolve o bug do som continuar depois da frenagem: o
    // som nao e' solto no mundo, ele pertence ao carro e so sai com ele.
    AdjustSound:
        // ---- handle do som deste carro (0 = nao ha som: nao ha o que fazer) ----
        // A leitura mora aqui, e nao nos chamadores, porque o StopSound chega
        // ate aqui sem ter lido o handle antes.
        GET_EXTENDED_CAR_VAR hVeh AUTO 3 hStream
        IF hStream = 0
            RETURN
        ENDIF

        // ---- o som acabou sozinho? ----
        // Um arquivo nao-loopado acaba por conta propria (o do jogador dura
        // ~6 s; o placeholder deste repositorio, 1,2 s). Se a frenagem ainda
        // estiver valendo, o certo e' comecar o som de novo; se ja acabou, o
        // certo e' so limpar o registro. O GET_AUDIO_STREAM_STATE e' o que
        // permite isso sem depender da duracao do arquivo.
        GET_AUDIO_STREAM_STATE hStream (iValue)
        IF iValue = 0
            REMOVE_AUDIO_STREAM hStream
            SET_EXTENDED_CAR_VAR hVeh AUTO 3 0
            SET_EXTENDED_CAR_VAR hVeh AUTO 1 1
            RETURN
        ENDIF

        // ---- volume atual (var. 4, em milesimos) virado de float ----
        GET_EXTENDED_CAR_VAR hVeh AUTO 4 iValue
        CSET_LVAR_FLOAT_TO_LVAR_INT fVol iValue
        fVol *= 0.001

        // ---- passo deste frame: volume por segundo (Fade) x dt ----
        fIncr = fStep
        fIncr *= fFade

        // ---- move o volume em direcao ao alvo f ----
        IF fVol > f
            fVol -= fIncr             // fade-out / baixa
            IF fVol < f
                fVol = f
            ENDIF
        ELSE
            fVol += fIncr             // fade-in / sobe
            IF fVol > f
                fVol = f
            ENDIF
        ENDIF
        SET_AUDIO_STREAM_VOLUME hStream fVol

        // ---- grava o volume novo (x1000) na var. 4 do carro ----
        f = fVol
        f *= 1000.0
        CSET_LVAR_INT_TO_LVAR_FLOAT iValue f
        SET_EXTENDED_CAR_VAR hVeh AUTO 4 iValue

        // ---- chegou a zero? entao DESLIGA e REMOVE de verdade ----
        // (SET_AUDIO_STREAM_STATE 0 + REMOVE_AUDIO_STREAM e' o que garante
        //  que o chiado nao continue soando depois que o carro parou.)
        IF fVol <= 0.0
            SET_AUDIO_STREAM_STATE hStream 0
            REMOVE_AUDIO_STREAM hStream
            SET_EXTENDED_CAR_VAR hVeh AUTO 3 0
            SET_EXTENDED_CAR_VAR hVeh AUTO 4 0
            SET_EXTENDED_CAR_VAR hVeh AUTO 1 1   // desarmado: pode chiar de novo
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
// achava que o arquivo nao existia. Ver BUILD.md, armadilha A1.
// ============================================================================
txtSoundBuffer:
DUMP
00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
ENDDUMP
