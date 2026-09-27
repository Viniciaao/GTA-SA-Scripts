================================================================================
        WORN BRAKE PAD SOUND - v2.6 (CLEO+, NPC edition)
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
   v2.5.1. With Cooldown = 1100 (the default) the squeal repeats while the brake
   is held, which is what a real worn pad does.

5. Everything is frame-rate independent (pressure and cooldown are computed in
   seconds/milliseconds, not "per frame").

6. The .ini is read once, and the sound is only triggered for cars inside the
   configured radius. See PERFORMANCE below.

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
      Minimum pedal value (0 to 1) to count as braking. Above the threshold the
      car is stopped/accelerating and doesn't squeal.

  TriggerPressure = 0.08
      Minimum accumulated pressure to fire the sound. This is what tells "really
      braked" from "touched the brake". Lower = more squealing.

  PressureRate = 5.0
      How fast (per second) the pressure builds while the brake is held.
      Higher = the squeal shows up sooner on a short tap.

  RefSpeed = 110.0
      Reference speed (km/h). Below 12% of it (13 km/h by default) the car
      doesn't squeal, and the volume rises with speed up to 100% at RefSpeed.

  Cooldown = 1100
      Milliseconds between two squeals from the same car. 0 = one per brake
      press (same as v2.5.1): the car latches after squealing and only sings
      again once the brake is really released, with a 250 ms floor so an AI
      that taps the brake doesn't turn into a machine gun. 1100 = repeats
      while the brake is held.

  Debug = 0
      1 = prints each new car's value on screen.

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
without the game - 60 cases, including "one squeal per press" at
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
Sound in this v2.6: synthesized by tools/make_sound.py (the original mod's sound
   is not redistributed here).
================================================================================
