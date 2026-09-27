================================================================================
        WORN BRAKE PAD SOUND - v2.8 (CLEO+, NPC edition)
================================================================================

Rewritten from scratch in GTA3script out of the "Som de pastilha de freio
gasta" v2.5.1 mod (Amilton, Fabio, Junior_Djjr). The point of this version: THE
SOUND NOW HAPPENS FOR NPC CARS TOO, not just for the player's car.

Requirements: GTA San Andreas (PC), CLEO+ 1.0.7 or newer, ModLoader or CLEO
Redux. No MoonLoader, no extra .asi needed.

--------------------------------------------------------------------------------
WHAT THE MOD DOES
--------------------------------------------------------------------------------
When somebody brakes an old car, you get that awful high-pitched worn brake pad
squeal. The sound is 3D and comes from the car, so you hear the car next to you,
the one behind you, the one crossing the street - and the car in front of you
screams every time it stops in traffic.

Your controls didn't change: it's still just the brake pedal. The player's car
has no special case anymore - it goes through the exact same path as everyone
else, so whatever works for the NPCs works for you (and vice versa).

--------------------------------------------------------------------------------
WHAT CHANGED FROM v2.5.1
--------------------------------------------------------------------------------
1. NPCs. v2.5.1 only played the sound for the player's car. Here every vehicle
   in the game - with or without a driver - is evaluated every frame.

2. The car's value is now read straight from the game's handling (the CLEO+
   GET_CAR_VALUE opcode, which returns the same "monetary value" the game uses
   for the vehicle export mission). v2.5.1 relied on a list in the .ini that
   broke with add-on cars. The .ini still drives the cut-off (MinValue/
   MaxValue), and to change a specific car's value you edit its line in
   data/handling.cfg.

3. The volume is no longer fixed: it scales with pedal pressure and with speed,
   and it follows the game's sound-effects volume for every sound (the 2019
   fix, which used to only apply to the player).

4. Cooldown. With Cooldown = 0 you get one squeal per brake press, exactly like
   v2.5.1. With Cooldown = 1500 (the default) the squeal repeats while the brake
   is held, which is what a real worn pad does.

5. Everything is frame-rate independent (pressure and cooldown are computed in
   seconds/milliseconds, not "per frame").

6. The .ini is read once, and the sound is only triggered for cars inside the
   configured radius. See PERFORMANCE below.

7. The trigger is DECELERATION, like the original mod (for the player and for
   NPCs alike). v2.7 used a "pressure" that kept building and therefore
   squealed with the car STANDING STILL; v2.8 measures the real drop in speed,
   so it feels like the real thing: it sings when you actually brake, and stays
   SILENT with the car stopped, even with your foot on the pedal. The knob is
   BrakeForce (minimum drop, in m/s2).

MELHORIAS.md (Portuguese) has the full list with implementation details.

--------------------------------------------------------------------------------
INSTALLATION
--------------------------------------------------------------------------------
1. Copy the files to the GTA San Andreas CLEO folder:

     CLEO\BrakePadSound.cs
     CLEO\BrakePadSound.ini
     CLEO\BrakePadSound\brakepad.wav     (create the subfolder too)

   If you use the MixMods ModLoader, extract the whole mod folder into
   ModLoader\Scripts\.

2. Start the game. If the sound file is missing, the mod says so on screen and
   stays idle (no point burning CPU without a sound).

--------------------------------------------------------------------------------
THE SOUND
--------------------------------------------------------------------------------
The brakepad.wav shipped here is SYNTHESIZED (tools/make_sound.py), because the
original mod's sound belongs to its authors and isn't ours to redistribute. If
you have the v2.5.1 sound, drop it into CLEO\BrakePadSound\brakepad.wav (or point
the SoundFile key in the .ini at it - both wav and mp3 work) and the mod will
use yours.

--------------------------------------------------------------------------------
CONFIGURATION (CLEO\BrakePadSound.ini)
--------------------------------------------------------------------------------
[Config]

  Enabled = 1
      1 = on, 0 = the script sits there without burning CPU.

  MinValue = 0
  MaxValue = 32000
      A car's (handling) value must be between the two to squeal. By default the
      old/cheap cars are in (Bandito, FCR-900, Perennial, Vortex, old pickups...)
      and the expensive ones are out (Infernus, Bullet, Super GT...). With
      Debug = 1 the script prints the value of every new car it sees, which is
      how you find the right cut-off.

  Vehicles = 0
      0 = cars only (includes vans, trucks and buses). 1 = cars + bikes and
      quads. Aircraft and boats never squeal, in either mode.

  Volume = 0.7
      Ceiling for the squeal volume. The final volume is also multiplied by the
      game's sound-effects volume, the pedal pressure and the speed.

  Radius = 70.0
      Distance (m) from the camera at which the sound can still be triggered.
      Smaller = cheaper; bigger = you hear cars further away.

  SoundFile = CLEO\BrakePadSound\brakepad.wav
      Path to the sound file (wav or mp3).

  BrakeThreshold = 0.15
      Minimum pedal (0 to 1) to count as braking. Below it the script
      ignores the car - the guard against false positives (only real
      braking counts).

  BrakeForce = 2.5
      Minimum drop in speed (in m/s2, i.e. in "g") for the squeal to
      count. A car really braking passes easily; barely touching the
      brake doesn't. Lower = more squealing (1.0), higher = only hard
      braking (6.0). This is the knob that defines what counts as
      "really braking" - and it is what keeps a STOPPED car silent,
      because a stopped car does not decelerate.

  RefSpeed = 110.0
      Reference speed (km/h). The volume rises with the speed up to 100%
      at RefSpeed (and never drops below 25%).

  Cooldown = 1500
      Milliseconds between two squeals from the same car. 0 = one per brake
      press (same as v2.5.1): the car latches after squealing and only sings
      again once the brake is really released. 1500 = repeats while the
      braking is strong, like a real pad (each squeal lasts 1.2 s, so they do
      not overlap).

  Debug = 0
      1 = prints each new car's value on screen.

--------------------------------------------------------------------------------
TROUBLESHOOTING
--------------------------------------------------------------------------------
"Brake pad sound: sound file not found"

  The message shows the exact path the script looked for. Check that the file
  really is in CLEO\BrakePadSound\ **in the GTA folder**, not inside the mod
  folder. If you install with the ModLoader, remember it copies the contents
  of the mod's "cleo" folder into the game's CLEO folder - after starting the
  game once, look in the GTA CLEO folder to see whether the .ini, the .cs and
  the .wav made it there.

  Same rule for the .ini: the ModLoader only sees an .ini that lives inside a
  "CLEO" folder inside a mod folder. Building the mod by hand? The file has
  to be CLEO\BrakePadSound.ini (not in the mod root).

  The v2.5.1 sound is an .mp3? Fine - point the SoundFile key at it (wav and
  mp3 both work) - but check the name matches, extension included.

"It runs, but the car in the game is worth too much and doesn't squeal"

  Set Debug = 1 in the .ini: the script prints each car's value on screen.
  Adjust MaxValue from what you see. (The value is the same one the game uses
  for the vehicle export mission: the "Monetary Value" column of the car's
  handling.)

Nothing happens at all

  Check that the .cs is in the game's CLEO folder and that CLEO+ 1.0.7+ is
  installed - the mod uses opcodes only CLEO+ has, so plain CLEO 4 won't do
  (and without CLEO+ the script doesn't even start).

I hear no sound at all (neither mine nor the NPCs')

  v2.6 had a bug that zeroed the brake pressure, so it stayed silent in every
  situation. v2.7 ships that fixed - but if you are still running v2.6, follow
  this order (an old .cs keeps running until GTA is closed):

  1. Close the game, replace BrakePadSound.cs, start the game again. Replacing
     the .cs while the game is open changes nothing: the old script keeps
     running.

  2. On startup the game now prints "Som de pastilha v2.7 - <path> (until ...,
     radius ... m)". Check that the path shown is your sound file.

  3. Test WHILE MOVING: get up to street speed and brake. v2.7 is a worn pad,
     so it sings with the car moving. A stopped car is silent by design in
     v2.8 (it does not decelerate) - to hear it right away, roll a little and
     brake hard, or listen to a car on the street (the sound is 3D).

  4. If it still doesn't sound, set Debug = 1: the script prints the value of
     every new car that goes through it. If no car shows up, the script is not
     running at all (go back to step 1).

It squeals with the car STOPPED / won't stop squealing

  If v2.7 squeals while stopped, that version is still installed: close the
  game and replace BrakePadSound.cs with v2.8 (the old file only leaves memory
  when GTA closes). v2.8 does not squeal at a standstill - a stopped car does
  not decelerate. If you WANT to hear it without moving, it isn't a config
  thing: v2.8 keys off deceleration, so a stopped car is silent by definition.
  To hear it right away, roll a little and brake hard - or listen to a car on
  the street, since the sound is 3D and comes from the car.

--------------------------------------------------------------------------------
PERFORMANCE
--------------------------------------------------------------------------------
The script walks the game's vehicle pool (up to ~200 cars) once per frame, but
for each car the only opcode is GET_EXTENDED_CAR_VAR, which only answers for
cars THE SCRIPT HAS ALREADY EVALUATED. Expensive cars, planes, boats and bikes
that aren't allowed get dropped right there - no pedal read, no position read,
no sound. The vehicle type and value are read once per car (registration
happens on the CLEO+ car-create event), and the .ini is never read inside the
loop.

The real per-frame cost is on par with Junior_Djjr's "Air Brake Sound v3" (which
applies to every NPC), plus the value filter.

--------------------------------------------------------------------------------
COMPILING THE SOURCE
--------------------------------------------------------------------------------
The source (CLEO\BrakePadSound.sc) is gta3script, compiled with the gta3sc - not
Sanny Builder. Full recipe, flags and the language's gotchas in BUILD.md:

     bash tools/build.sh

The sound can be regenerated with:

     python3 tools/make_sound.py

And the timing model (pressure, trigger, cooldown, volume) can be checked
without the game - 81 checks, including "one squeal per press" at
30/60/144/240 FPS:

     python3 tools/test_model.py

================================================================================
CREDITS
================================================================================
Original mod: Amilton, Fabio, Junior_Djjr
   https://www.mixmods.com.br/2016/03/som-de-pastilha-de-freio-gasta-v2-5-1/
NPC approach (CLEO+ defaults, script events, audio streams): Junior_Djjr, in
   "Air Brake Sound v3" - https://www.mixmods.com.br/2022/05/air-brake-sound/
CLEO+ opcodes used: GET_CAR_VALUE, GET_CAR_PEDALS, GET_VEHICLE_SUBCLASS,
   GET_ANY_CAR_NO_SAVE_RECURSIVE, EXTENDED_CAR_VARS, SET_SCRIPT_EVENT_CAR_CREATE,
   GET_AUDIO_SFX_VOLUME, audio streams.
Sound in this v2.8: synthesized by tools/make_sound.py (the original mod's sound
   is not redistributed here).
================================================================================
