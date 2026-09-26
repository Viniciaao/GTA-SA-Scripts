/*
    ============================================================================
    SanNews TV Programs
    ----------------------------------------------------------------------------
    Script original: RenanMSV (2018) - Sanny Builder / {$CLEO}
    Reescrita em GTA3script (gta3sc, NAO Sanny Builder) e correcao: 2026
    ============================================================================
    O QUE O MOD FAZ

    Quando voce esta num veiculo e troca a camera ate o modo CINEMATICO
    (camera de filme, tecla V por padrao), o script desenha uma overlay de
    jornalismo no estilo Weazel News do GTA V: moldura de TV, logos, faixa
    de noticias rolando e manchete. Com estrela de procurado a faixa vira
    perseguicao policial; com chuva vira alerta meteorologico.

    ----------------------------------------------------------------------------
    POR QUE O SCRIPT ANTIGO NUNCA FUNCIONAVA

    1. SPRITES 6500..6504.
       LOAD_SPRITE / DRAW_SPRITE no SA so tem ~15 vagas (CTheScripts::
       ScriptSprites). 6500 escreve FORA do array: crash na hora, memoria
       corrompida, ou textura que nunca aparece. Os IDs certos sao 1..5.

    2. NENHUM ARQUIVO .FXT.
       DISPLAY_TEXT usa chaves GXT (sann_00 .. sann_11). Sem CLEO\\sannews.fxt
       o jogo nao tem o texto e desenha vazio. Esta versao traz o FXT.

    3. USE_TEXT_COMMANDS 0 ANTES DE DESENHAR O TEXTO.
       O original ligava o 03F0 em 0 e depois tentava DISPLAY_TEXT. O padrao
       que funciona no SA e: USE_TEXT_COMMANDS 1, desenha, USE_TEXT_COMMANDS 0.

    4. TEXTO ATRAS DA FAIXA.
       03E0 (SET_TEXT_DRAW_BEFORE_FADE) ia ANTES do ticker, entao a noticia
       rolava ATRAS do sprite newsbar e sumia. Agora sprite primeiro, texto
       por cima.

    5. CLIMA LIDO COM 4 BYTES.
       CWeather::OldWeatherType em 0xC81320 e int16. Ler 4 bytes mistura a
       chuva (float) no valor e a comparacao "== 8" quase nunca passa quando
       esta chovendo de verdade. Alem disso so testava o clima 8 (RAINY_SF)
       e ignorava o 16 (RAINY_COUNTRYSIDE) e o clima NOVO (0xC8131C).

    6. TICKER POR FRAME, NAO POR TEMPO.
       30@ -= 1.0 a cada WAIT 0. Com 60+ FPS (SilentPatch / essentials) a
       faixa voava da tela e as noticias trocavam em segundos. Agora a
       velocidade e em pixels/segundo.

    7. HUD LIGADO NO ELSE TODO FRAME.
       O else fazia DISPLAY_HUD 1 / DISPLAY_RADAR 1 sempre que a overlay
       nao estava ativa, brigando com qualquer outro mod que esconda HUD.
       Agora so restaura se ESTE script escondeu.

    8. SEM IS_PLAYER_PLAYING.
       actor.Driving($PLAYER_ACTOR) com o player morto/preso/indefinido
       e caminho classico de crash.

    9. TXD NAO ERA DESCARREGADA.
       Sprites 1..5 ficam ocupados o jogo inteiro e brigam com outros
       scripts. Aqui o dicionario so existe enquanto a overlay esta na tela.

    10. FERRAMENTA DE DEBUG (:guiTool) MORTA NO ARQUIVO.
        Gosub comentado, mas o label com 0AD1/0AB0 ia junto no compile e
        nao servia pra nada.

    ----------------------------------------------------------------------------
    COMPILACAO (gta3sc / GTA3script, NAO Sanny Builder):

        gta3sc compile --config=gtasa --guesser --cs -fno-entity-tracking -O \
            sannews.sc -o sannews.cs

    Requer: CLEO 4.4+ (FXT + READ_MEMORY). TXD opcional:
        models/txd/sannews.txd  (SANNEWS, tvborder, calogo, newsbar, tv24)
    Sem a TXD a overlay ainda aparece (faixa + textos + letterbox).
    ============================================================================
*/

SCRIPT_START
{
    NOP

    SCRIPT_NAME sannews

    CONST_INT CAM_CINEMATIC     5
    CONST_INT HEADLINE_MS       5000
    CONST_INT TOTAL_HEADLINES   5
    CONST_INT TOTAL_TICKERS     3
    CONST_INT WEATHER_RAIN_SF   8
    CONST_INT WEATHER_RAIN_CS   16
    CONST_INT SPR_LOGO          1
    CONST_INT SPR_BORDER        2
    CONST_INT SPR_CALOGO        3
    CONST_INT SPR_BAR           4
    CONST_INT SPR_TV24          5

    LVAR_INT scplayer
    LVAR_INT camMode
    LVAR_INT headline
    LVAR_INT tickerId
    LVAR_INT weather
    LVAR_INT overlayOn
    LVAR_INT now
    LVAR_INT nextHead
    LVAR_INT lastMs
    LVAR_INT delta
    LVAR_INT showNow
    LVAR_FLOAT tickerX
    LVAR_FLOAT step

    GET_PLAYER_CHAR 0 scplayer

    headline = 0
    tickerId = 0
    overlayOn = FALSE
    nextHead = 0
    lastMs = 0
    tickerX = 850.0

    WHILE TRUE
        WAIT 0

        showNow = FALSE
        IF IS_PLAYER_PLAYING 0
            IF IS_CHAR_IN_ANY_CAR scplayer
                GET_PLAYER_IN_CAR_CAMERA_MODE camMode
                IF camMode = CAM_CINEMATIC
                    showNow = TRUE
                ENDIF
            ENDIF
        ENDIF

        GOSUB AdvanceTicker
        GOSUB AdvanceHeadline

        IF showNow = TRUE
            IF overlayOn = FALSE
                GOSUB LoadGfx
                DISPLAY_RADAR FALSE
                DISPLAY_HUD FALSE
                overlayOn = TRUE
            ENDIF
            GOSUB DrawNews
        ELSE
            IF overlayOn = TRUE
                REMOVE_TEXTURE_DICTIONARY
                DISPLAY_RADAR TRUE
                DISPLAY_HUD TRUE
                overlayOn = FALSE
            ENDIF
        ENDIF
    ENDWHILE

    // ------------------------------------------------------------------
    LoadGfx:
        LOAD_TEXTURE_DICTIONARY sannews
        LOAD_SPRITE SPR_LOGO "SANNEWS"
        LOAD_SPRITE SPR_BORDER "tvborder"
        LOAD_SPRITE SPR_CALOGO "calogo"
        LOAD_SPRITE SPR_BAR "newsbar"
        LOAD_SPRITE SPR_TV24 "tv24"
    RETURN

    // ------------------------------------------------------------------
    // Ticker: 30 pixels/segundo (igual o original a 30 FPS).
    AdvanceTicker:
        GET_GAME_TIMER now
        IF lastMs = 0
            lastMs = now
        ENDIF
        delta = now
        delta -= lastMs
        lastMs = now
        IF delta > 40
            delta = 40
        ENDIF
        step =# delta
        step *= 0.03
        tickerX -= step
        IF tickerX < -200.0
            tickerX = 850.0
            tickerId += 1
            IF tickerId >= TOTAL_TICKERS
                tickerId = 0
            ENDIF
        ENDIF
    RETURN

    // ------------------------------------------------------------------
    AdvanceHeadline:
        GET_GAME_TIMER now
        IF nextHead = 0
            nextHead = now
            nextHead += HEADLINE_MS
        ENDIF
        IF now > nextHead
            headline += 1
            IF headline >= TOTAL_HEADLINES
                headline = 0
            ENDIF
            nextHead = now
            nextHead += HEADLINE_MS
        ENDIF
    RETURN

    // ------------------------------------------------------------------
    DrawNews:
        USE_TEXT_COMMANDS 1

        // Letterbox + faixa: aparecem mesmo sem a TXD.
        DRAW_RECT 320.0 12.0 640.0 24.0 0 0 0 220
        DRAW_RECT 320.0 436.0 640.0 24.0 0 0 0 220
        DRAW_RECT 320.0 392.0 640.0 22.0 10 10 10 210

        DRAW_SPRITE SPR_BORDER 322.0 223.0 645.0 455.0 255 255 255 255
        DRAW_SPRITE SPR_BAR 267.0 382.0 518.0 20.0 255 255 255 255
        DRAW_SPRITE SPR_LOGO 581.0 375.0 70.0 32.0 255 255 255 255
        DRAW_SPRITE SPR_CALOGO 83.0 375.0 67.0 32.0 255 255 255 255
        DRAW_SPRITE SPR_TV24 34.0 21.0 54.0 27.0 255 255 255 255

        // "AO VIVO"
        SET_TEXT_SCALE 0.3 0.8
        SET_TEXT_JUSTIFY FALSE
        SET_TEXT_CENTRE TRUE
        SET_TEXT_PROPORTIONAL TRUE
        SET_TEXT_FONT 2
        SET_TEXT_WRAPX 640.0
        SET_TEXT_COLOUR 255 0 0 255
        SET_TEXT_EDGE 1 0 0 0 255
        DISPLAY_TEXT 581.0 350.0 SANN_00

        // Ticker (noticia rolando na faixa)
        SET_TEXT_SCALE 0.25 0.9
        SET_TEXT_CENTRE FALSE
        SET_TEXT_JUSTIFY TRUE
        SET_TEXT_PROPORTIONAL TRUE
        SET_TEXT_FONT 1
        SET_TEXT_WRAPX 4096.0
        SET_TEXT_COLOUR 255 255 255 255
        SET_TEXT_EDGE 0 0 0 0 0
        GOSUB DrawTickerLine

        // Manchete
        SET_TEXT_SCALE 0.25 0.8
        SET_TEXT_JUSTIFY FALSE
        SET_TEXT_CENTRE TRUE
        SET_TEXT_PROPORTIONAL TRUE
        SET_TEXT_FONT 2
        SET_TEXT_WRAPX 640.0
        SET_TEXT_COLOUR 255 244 255 255
        SET_TEXT_EDGE 0 0 0 0 0
        GOSUB DrawHeadline

        USE_TEXT_COMMANDS 0
    RETURN

    // ------------------------------------------------------------------
    DrawTickerLine:
        IF IS_WANTED_LEVEL_GREATER 0 0
            DISPLAY_TEXT tickerX 392.0 SANN_01
            RETURN
        ENDIF

        READ_MEMORY 0xC81320 2 FALSE weather
        IF weather = WEATHER_RAIN_SF
        OR weather = WEATHER_RAIN_CS
            DISPLAY_TEXT tickerX 392.0 SANN_06
            RETURN
        ENDIF
        READ_MEMORY 0xC8131C 2 FALSE weather
        IF weather = WEATHER_RAIN_SF
        OR weather = WEATHER_RAIN_CS
            DISPLAY_TEXT tickerX 392.0 SANN_06
            RETURN
        ENDIF

        IF tickerId = 0
            DISPLAY_TEXT tickerX 392.0 SANN_03
        ENDIF
        IF tickerId = 1
            DISPLAY_TEXT tickerX 392.0 SANN_04
        ENDIF
        IF tickerId = 2
            DISPLAY_TEXT tickerX 392.0 SANN_05
        ENDIF
    RETURN

    // ------------------------------------------------------------------
    DrawHeadline:
        IF headline = 0
            DISPLAY_TEXT 313.0 378.0 SANN_07
        ENDIF
        IF headline = 1
            DISPLAY_TEXT 313.0 378.0 SANN_08
        ENDIF
        IF headline = 2
            DISPLAY_TEXT 313.0 378.0 SANN_09
        ENDIF
        IF headline = 3
            DISPLAY_TEXT 313.0 378.0 SANN_10
        ENDIF
        IF headline = 4
            DISPLAY_TEXT 313.0 378.0 SANN_11
        ENDIF
    RETURN
}
SCRIPT_END
