============================================================================
NFRSHIFT GEARS SOUNDIZE
manual gearbox for GTA SA — recreation of the NFRShift Gears Mod
adapted for Soundize (+ option to disable the shift animation)
============================================================================

WHAT IS THIS?
-------------
A recreation of the manual gearbox mod (NFRShift Gears Mod) built on top of
the Soundize sound system by Junior_Djjr:

    https://www.mixmods.com.br/2026/06/soundize/

Soundize already has the realistic RPM/sound system (gears, shift throttle
cut, backfire, turbo, interior sounds...), so this manual gearbox controls
the gears WITHOUT fighting the sound — and it reads Soundize's real
RPM/gear values through its official API for the HUD.

It also adds `DisableShiftAnim`: turns off the gear-shift animation
(the TASK_PLAY_ANIM changegear arm animation) so it plays nice with driver
animation mods like **VEHIK by zzpuma**, which animates the driver's hand
on the gear lever by itself.

REQUIREMENTS
------------
CLEO 4.4.4+ (or CLEO 5), CLEO+, and preferably Soundize (without it the
gearbox still works, using the vanilla audio behavior).

Install into the game folder:

    cleo/NFRShift Gears Soundize.cs        <- script (compiled)
    cleo/NFRShift Gears Soundize.ini       <- config
    cleo/CLEO_TEXT/NFRShift Gears Soundize.fxt <- HUD texts

From the original mod you also need (you already have it if you used
NFRShift before):

    cleo/gbox.txd                          <- panel textures (gearbox, gear,
                                              gcenter, gup, gdown, tail, tail2)
    cleo/NFR Shift Gear/gears_sound/       <- OPTIONAL: 1.mp3 2.mp3 3.mp3
                                              4.mp3 5.mp3 gas.mp3 (shift sounds)

CONTROLS (same as the original mod)
-----------------------------------
- Bikes/quads: shift with left/right mouse buttons.
- Cars: hold the clutch (left Shift by default) to show the H-pattern panel,
  move the lever with the mouse. Middle rail = neutral (N). R = reverse
  (also engages by holding the Circle button while in neutral).
- Quick clutch taps (<50 ms) don't open the manual gearbox.
- Releasing the gas at very low RPM in gear stalls the engine (press E to
  restart).

REALISTIC STARTING & STALLING (RealisticStart = 1)
--------------------------------------------------
Just like real life (works for Soundize audio and vanilla audio alike —
it is game physics, the sound follows on its own):

- Dead engine, STOPPED IN GEAR, pressing E (no clutch): the engine cranks,
  the car LURCHES with its own power (in reverse, backwards) and the
  engine DIES half a second later ("afogou" = flooded/stalled). Holding E
  repeats the lurch, like someone grinding the starter.
- Pressing the clutch DURING the lurch saves it: the engine catches and
  stays on.
- Dead engine in NEUTRAL + E: the engine just starts and stays on.
- Dead engine ROLLING in gear (from ~7 km/h) + E: bump start!
- Releasing the clutch while STOPPED in gear without gas: the engine
  stalls after ~0.5 s (like dumping the clutch in real life).
- Too slow for the gear (e.g., braking down to 5 km/h still in 4th and
  releasing everything): the engine LUGS and stalls after ~0.7 s.
  Downshift or press the clutch! Tunable via StallSpeedFactor (0 = off).
- Full throttle in any gear now pulls cleanly TO THE REDLINE: the RPM
  pins at the gear limit with a rev-limiter buzz (ShiftThreshold 0.97)
  instead of hesitating/cutting early. No auto-upshift, ever.
- Revving with the clutch pressed now makes SOUND (ClutchRevSim = 1): with
  the car stopped, the engine revs via the transmission's internal speed
  while the real pedal stays zeroed — no movement, NO BRAKES. The vanilla
  audio follows it, and Soundize follows it whenever it derives RPM from
  that speed (if your Soundize build doesn't, only vanilla audio revs).
- Shifting gears no longer brakes the car: clutch pressed = the car just
  coasts on drag, like a real clutch.

HIGH-GEAR LAUNCH (Soundize users, read this)
--------------------------------------------
Starting from a standstill in a high gear (e.g., 5th) no longer makes the
car accelerate normally while the Soundize sound runs through its gears
on its own. Out of the gear's speed window the engine now lugs (weak
acceleration, like real life), and releasing the throttle stalls it.
Technical note: Soundize derives the played gear from SPEED (its API has
no gear setter), so while speed climbs through the lower gears' windows
the sound follows them; as soon as speed enters your gear's window the
sound locks to your gear. A "Ext_SetVehicleGear" request to Junior_Djjr
would solve this on the Soundize side.

Old behavior, for comparison: E only worked while holding the clutch,
starting in gear kept the engine on, the throttle feed was cut early
before the shift point (sound "stuttering" near the top) and revving
with the clutch pressed did nothing. Set RealisticStart = 0 and
ShiftThreshold = 0.8 to go back to that.


WHAT CHANGED VS THE ORIGINAL MOD
--------------------------------
1. SOUNDIZE INTEGRATION ([Soundize] section in the .ini):
   - Loads the official Soundize API (Ext_GetVehicleRPM, Ext_GetVehicleMaxRPM,
     Ext_GetVehicleGear, Ext_IsVehicleUsingAnyBank).
   - While the car plays a Soundize bank:
       * HUD shows the gear the SOUND is in plus a real-time RPM bar.
       * The throttle is progressively cut at 80% of each gear's max speed
         (ShiftThreshold). Since Soundize computes RPM from speed, holding
         the speed below the shift point means Soundize never upshifts on
         its own — the player controls the gears, the sound stays realistic.
       * The selected gear is also mirrored into the game's vehicle audio
         entity gear byte (WriteGearToAudio), in case Soundize derives its
         gear from the native audio state.
   - Cars WITHOUT a Soundize bank keep the original mod behavior
     (SwitchCarGearAudio on audio entity +0xAA).

2. New GearLimitMode: by default each gear's speed limit now comes from the
   car's own handling gear windows (aGears.fChangeUpVelocity) — the same
   data the game and Soundize use for gear logic, so every vehicle
   (3-speed, 5-speed, tuned 6-speed) adapts automatically. Legacy modes
   still available (1 = proportional, 2 = fixed km/h table).

3. DisableShiftAnim (for VEHIK): 1 = the animation is never requested nor
   played. Everything else (shift sounds, panel) stays the same.

4. ShowRPMBar: real RPM bar (Soundize API), green/orange/red by RPM.

5. General cleanup: own .ini, own .fxt, no dead code, documented.

TUNING WITH SOUNDIZE
--------------------
- Soundize's own RPMmode ini option (0 = sport/late shifts, 1 = street/early
  shifts) affects when IT would shift. If the car still upshifts alone with
  RPMmode=1, lower ShiftThreshold to 0.7.
- If gears feel dead before the cut, raise ShiftThreshold to 0.85.
- The orange HUD line ("Som: X") shows the gear the sound is playing. It
  should always match your selected gear; if it doesn't, the car upshifted
  by itself — adjust ShiftThreshold.

BUILDING (for coders)
---------------------
Source: Scripts/NFRShift Gears Soundize/CLEO/NFRShift Gears Soundize.txt

    gta3sc compile "NFRShift Gears Soundize.txt" --config=gtasa ^
        --guesser -fbreak-continue --cs -o "NFRShift Gears Soundize.cs"

CLEO+ is required at runtime for opcodes 0x0E12 (GET_VEHICLE_SUBCLASS),
0x0E60 (SET_CAMERA_CONTROL) and 0x0EFE (GET_LOADED_LIBRARY).

Note: this version uses SET_TEXT_EDGE with 5 arguments (official SA
signature, opcode 081C) — the old script used 4.

CREDITS
-------
- NFRShift Gears Mod — base of this recreation
- Junior_Djjr — Soundize (sound system, realistic RPM, public API)
- zzpuma — VehIK (the reason DisableShiftAnim exists)
- chrystianfarias — Soundize sound engineering
