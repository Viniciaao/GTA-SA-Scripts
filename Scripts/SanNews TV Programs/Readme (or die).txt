================================================================================
                         SanNews TV Programs
                    (GTA3script / gta3sc rewrite)
================================================================================

Original script: RenanMSV (2018) - Sanny Builder
Rewrite and fixes: GTA3script (NOT Sanny Builder)

--------------------------------------------------------------------------------
WHAT IT DOES
--------------------------------------------------------------------------------
When you are in a vehicle and switch to CINEMATIC camera (the movie camera,
V by default), the screen gets a Weazel News-style overlay: TV frame, logos,
a scrolling ticker and a headline.

Special tickers:
  - wanted stars -> police chase
  - rain (SF or countryside) -> weather warning

--------------------------------------------------------------------------------
WHY THE OLD SCRIPT NEVER WORKED
--------------------------------------------------------------------------------
1. Sprite IDs 6500-6504. SA only has ~15 script sprite slots. 6500 writes
   off the array (crash / invisible textures).
2. No .FXT file. DISPLAY_TEXT keys sann_00..sann_11 did not exist.
3. USE_TEXT_COMMANDS 0 before drawing any text.
4. Ticker was drawn behind the news-bar sprite (03E0) so it vanished.
5. Weather read as 4 bytes from a 16-bit field; "== 8" failed while raining.
   Only weather 8 was tested (16 / NewWeatherType ignored).
6. Ticker moved 1px per frame: at 60 FPS it flew off the screen.
7. else-branch re-enabled HUD/radar every frame, fighting other mods.
8. No IS_PLAYER_PLAYING: crash when wasted/busted.
9. TXD never unloaded (sprite slots 1-5 leaked to other scripts).

--------------------------------------------------------------------------------
INSTALL
--------------------------------------------------------------------------------
Requires: GTA SA v1.0 + CLEO 4.4+ (FXT + READ_MEMORY).

Compile with gta3sc (NOT Sanny Builder):

    gta3sc compile --config=gtasa --guesser --cs -fno-entity-tracking -O \
        sannews.sc -o sannews.cs

Copy into the game folder:

    CLEO\sannews.cs
    CLEO\sannews.fxt

Optional TXD (original logos / TV frame):

    models\txd\sannews.txd

    Textures inside the TXD: SANNEWS, tvborder, calogo, newsbar, tv24.
    Grab it from the original "SanNews TV Programs For GTA SA" pack
    (RenanMSV / GTAinside). WITHOUT the TXD the overlay still runs
    (letterbox + bar + text).

With ModLoader you can drop the whole "SanNews TV Programs" folder into
modloader\ (keep CLEO\ and, if you have it, models\txd\).

--------------------------------------------------------------------------------
HOW TO USE
--------------------------------------------------------------------------------
1. Get in any vehicle.
2. Press V (Change Camera) until CINEMATIC - the movie camera that cuts
   between angles, not close/mid/far chase and not bumper.
3. Overlay shows. Press V again to leave.

--------------------------------------------------------------------------------
CREDITS
--------------------------------------------------------------------------------
- Original idea / Sanny script: RenanMSV (2018)
- GTA3script rewrite and fixes: 2026
================================================================================
