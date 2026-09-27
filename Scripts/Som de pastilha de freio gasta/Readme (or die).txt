================================================================================
        WORN BRAKE PAD SOUND - v2.9 (CLEO+, NPC edition)
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
THE SOUND HAS AN END: IT IS BORN AND DIES WITH THE BRAKING
--------------------------------------------------------------------------------
This is the main fix of this version, and the answer to the problem reported on
v2.8: after braking to a full stop and releasing the brake, the squeal kept
playing for a few seconds.

The reason was simple: the script fired the sound and never touched it again. It
threw the file at the car and forgot the handle, so the audio played to the end -
and the original mod's sound is about 6 seconds long. With the 1.2 s placeholder
you could not even notice.

Now the sound BELONGS to the car:

  - it is born when the braking starts (real deceleration + foot on the brake);
  - the volume FOLLOWS the braking, frame by frame: harder braking, louder
    squeal, just like in real life;
  - when the braking ends - you released the brake, the car stopped braking or
    the car stopped - the volume goes to zero over "Fade" seconds and the sound
    is actually STOPPED AND REMOVED, not just turned down;
  - same thing when the car leaves the camera radius or switches the engine off:
    the squeal does not stay behind, playing in the world on its own.

And the cycle works with ANY file: a 1 s squeal or a 10 s one, the script does
not depend on the length. If the braking lasts longer than the file, the script
starts the squeal again - always ONE sound per car, never two overlapping
copies.

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

4. The sound really ends. v2.5.1 (and v2.8) fired the sound and walked away:
   the file played to the end, and with a ~6 s sound the squeal went on after
   you released the brake. Now the audio stream handle is stored on the car and,
   at the end of the braking, the sound fades out (Fade), is stopped and is
   REMOVED. One sound per car, no overlap. Whether the squeal repeats while you
   hold the brake is now decided by the LENGTH OF YOUR AUDIO FILE (the script
   restarts it when the file ends), so there is no interval to tune anymore -
   and no way to stack copies of the same squeal.

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
     CLEO\BrakePadSound\brakepad.mp3     (create the subfolder too)
     CLEO\BrakePadSound\brakepad.wav     (same sound as wav: use either one)

   If you use the MixMods ModLoader, extract the whole mod folder into
   ModLoader\Scripts\.

2. Start the game. If the sound file is missing, the mod says so on screen and
   stays idle (no point burning CPU without a sound).

--------------------------------------------------------------------------------
THE SOUND
--------------------------------------------------------------------------------
The brakepad shipped here is SYNTHESIZED (tools/make_sound.py), because the
original mod's sound belongs to its authors and isn't ours to redistribute. The
.ini points at brakepad.mp3, and the same sound as brakepad.wav is in the
package too - use whichever you prefer. If you have the v2.5.1 sound (or the
original mod's), drop it into CLEO\BrakePadSound\ or point the SoundFile key in
the .ini at it (both wav and mp3 work) - the mod will use yours.

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
      and the expensive ones are out (Infernus, Bullet, Super GT...). See
      "TROUBLESHOOTING" below for how to find the right cut-off.

  Vehicles = 0
      0 = cars only (includes vans, trucks and buses). 1 = cars + bikes and
      quads. Aircraft and boats never squeal, in either mode.

  Volume = 0.5
      Ceiling for the squeal volume. The final volume is also multiplied by the
      game's sound-effects volume, the pedal pressure and the speed.

  Radius = 70.0
      Distance (m) from the camera at which the sound can still be triggered.
      Smaller = cheaper; bigger = you hear cars further away.

  SoundFile = CLEO\BrakePadSound\brakepad.mp3
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

  Fade = 0.2
      How many SECONDS the sound takes to disappear when the braking ends (foot
      off the brake, car no longer braking, car stopped). 0.2 = a fifth of a
      second: gone before you notice. 0 = hard cut (only if your file clicks at
      the end). 0.5 = slower, if your file has a tail. The same time applies to
      the attack: the squeal fades in instead of popping.

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

  The script is quiet on purpose (it only warns when the sound file is
  missing), so there is no printout to watch. Find the car's value in the game:
  the "Monetary Value" column in data/handling.cfg, under that car's block. It
  is the same number the game uses for the vehicle export mission. Adjust
  MaxValue so that value is included. To see several cars at once, open
  handling.cfg under [VEHICLE MODELS] - the script reads that same file.

Nothing happens at all

  Check that the .cs is in the game's CLEO folder and that CLEO+ 1.0.7+ is
  installed - the mod uses opcodes only CLEO+ has, so plain CLEO 4 won't do
  (and without CLEO+ the script doesn't even start).

Note: the script is quiet. It prints nothing during normal use - the only
message you can see is the "sound file not found" warning, if the file in the
.ini doesn't exist. That is on purpose: a print per frame (or per car) would
be an annoyance and would not help. To find out why one specific car doesn't
squeal, see the problems below.

The script doesn't make a sound at all / nothing shows up

  v2.6 had a bug that zeroed the brake pressure, so it stayed silent in every
  situation. v2.9 ships that fixed - but if you are still running v2.6, follow
  this order (an old .cs keeps running until GTA is closed):

  1. Close the game, replace BrakePadSound.cs, start the game again. Replacing
     the .cs while the game is open changes nothing: the old script keeps
     running.

  2. On startup the game now prints "Som de pastilha v2.9 - <path> (until ...,
     radius ... m)". Check that the path shown is your sound file.

  3. Test WHILE MOVING: get up to street speed and brake. v2.9 is a worn pad,
     so it sings with the car moving. A stopped car is silent by design in
     v2.9 (it does not decelerate) - to hear it right away, roll a little and
     brake hard, or listen to a car on the street (the sound is 3D).

  4. If it still doesn't sound, check MaxValue/MinValue in the .ini (the car's
     value is in data/handling.cfg) and lower BrakeForce. If you are sure the
     car is in range it has to squeal, so the problem is the install - go back
     to step 1.

The sound keeps playing after I release the brake

  If v2.8 does that, it is still installed: its script fired the audio and never
  touched it again, so the file played to the end (the original mod's sound is
  ~6 s) long after the braking was over. v2.9 stores the sound handle on the car
  and stops + removes it at the end of the braking. Close GTA, replace
  BrakePadSound.cs and start the game again (the old .cs only leaves memory
  when GTA closes). If you want it even more abrupt, set Fade = 0 in the .ini.

It squeals with the car STOPPED / won't stop squealing

  If an old version squeals while stopped, that version is still installed:
  close the game and replace BrakePadSound.cs with v2.9 (the old file only
  leaves memory when GTA closes). v2.9 does not squeal at a standstill - a
  stopped car does
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

And the timing model (deceleration, trigger, fade, sound end, volume) can be
checked without the game - 117 checks, including the case reported on v2.8
(braked to a stop, the sound must die), no overlap with the 6.165 s audio at
30/60/144/240 FPS and the CSET operand order (the bug that left the mod mute):

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
Sound in this package: synthesized by tools/make_sound.py (the original mod's sound
   is not redistributed here).
================================================================================
