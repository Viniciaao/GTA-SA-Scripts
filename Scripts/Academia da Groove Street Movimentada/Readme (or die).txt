================================================================================
                    Lively Ganton / Grove Street Gym
                         (Remastered Version)
================================================================================

Original author: SanKing (2013)
Refactored & Optimized: GTA3script standard for CLEO 4.4.4 & CLEO+ (2026)
Compatibility: GTA San Andreas (PC) - CLEO 4.4.4+ / CLEO+ / ModLoader

--------------------------------------------------------------------------------
DESCRIPTION:
--------------------------------------------------------------------------------
Brings the famous Los Santos (Ganton / Grove Street) gym to life!
Multiple people working out, running on treadmills, lifting dumbbells on benches,
practicing shadowboxing, and browsing clothes near the counter, plus a pedestrian
relaxing and smoking a cigarette outside the entrance.

--------------------------------------------------------------------------------
WHAT IS NEW AND FIXED IN THIS VERSION:
--------------------------------------------------------------------------------
1. Individual Random Chance per Pedestrian (New Feature):
   - The old mod ALWAYS spawned all 7 peds at once, repeating the exact scene.
   - Now features an individual random roll for each gym member: every visit
     brings a different combination of people working out.
   - Failsafe ensures the gym is never completely empty.
   - Dynamic asset loading: models and animations are only loaded into memory
     if the corresponding athlete was rolled to appear.

2. Exterior Pedestrian (the Smoker) Now Actually Shows Up + Weather Response:
   - FIXED ROOT CAUSE: the old routine was only called from inside an
     "IF bGymSpawned = 1" block, i.e. during the single 250 ms frame in which the
     gym was cleaned up right after you walked out of the interior -- and it also
     required you to be standing exactly inside a 30x30x15 box around the door at
     that instant. In any other situation (walking up to the gym from the street,
     coming back later, being anywhere else in the neighbourhood) he was simply
     never created. The routine was also reading the weather LOCK variable
     (0xC81318) instead of the real weather.
   - Now the exterior pedestrian is checked/created every cycle, from the street
     side, near the gym door, independently of the gym interior being loaded, and
     the weather is read from the current and upcoming weather values
     (0xC81320 / 0xC8131C).
   - If it is raining outside, the exterior pedestrian is NOT created.
   - If it starts raining while he is already outside smoking, the script cancels
     his smoking animation, assigns him a wandering task (TASK_WANDER_STANDARD) and
     releases him to the game AI to walk away down the street seeking cover.
   - A 15 s cooldown prevents another smoker from popping in right after you push,
     shoot or aim a weapon at the previous one.

3. Complete GTA3script Rewrite:
   - Fully rewritten in Rockstar Games' official modern GTA3script syntax, lean,
     structured, and strictly adhering to the engine's 32 local variable limit.

4. Fixed Interior Trigger Glitch ($ACTIVE_INTERIOR == 5):
   - Validates both the interior ID and the exact 3D bounding box of Ganton Gym,
     preventing false triggers in Cobra Gym, B Dup's apartment, or the Atrium.

5. Fixed Out-of-Bounds Stool (Object 1@):
   - Fixed the original coordinate typo (Y=755.388). All 4 stools are evenly
     spaced and aligned along the front counter.

6. Fixed Critical Memory Leak (Ped 17@):
   - The clothes-browsing ped is now properly tracked and safely deleted on exit.

7. Eliminated Stutters and Freezes (LOAD_ALL_MODELS_NOW):
   - Streaming is now fully asynchronous without freezing frames.

8. Removed 10-Second Door Lock (switch_entry_exit 'GYM1'):
   - Completely eliminated; immediate re-entry is always allowed.

9. Per-NPC Reaction System (Panic, Fight Back and the Door "Teleport"):
   - Every occupant now has HIS OWN reaction state. Hit one of them and only that
     one reacts -- nobody else stops training.
     The old build used a single global "bPanicked" flag, so punching one of the 6
     people in the gym froze the entire room and sent everybody walking into walls.
   - Reactions only happen on REAL aggression:
       * a punch, bat, knife or any other melee attack on the NPC
         (detected by weapon type, HAS_CHAR_BEEN_DAMAGED_BY_WEAPON + ANYMELEE);
       * any serious damage (gunshot, explosion, grenade, molotov, being run over);
       * gunfire nearby (firearms only, even if the shot misses);
       * being aimed at while the player holds a weapon. Aiming with bare fists no
         longer scares anybody.
     Simply walking/bumping into someone no longer counts as aggression.
   - Bumping into an NPC no longer cancels his exercise OR takes the dumbbell out of
     his hand: if the animation gets interrupted (push, fall, game AI) or if the game
     itself drops the object when the athlete is shoved, the script puts him back on
     the equipment and gives the dumbbell back to his hand (TASK_PICK_UP_OBJECT).
   - Every occupant now has HIS OWN reaction state. Hit one of them and only that
     one reacts -- nobody else stops training.
     The old build used a single global "bPanicked" flag, so punching one of the 6
     people in the gym froze the entire room and sent everybody walking into walls.
   - Reactions only happen on REAL aggression:
       * a punch, bat, knife or any other damage dealt to the NPC;
       * gunfire nearby (firearms only, even if the shot misses);
       * grenades, molotovs, satchel charges or any explosion (through damage);
       * being aimed at while the player holds a weapon. Aiming with bare fists no
         longer scares anybody.
   - Bumping into an NPC no longer cancels his exercise: bumps do not damage the
     NPC, so the training keeps going. If the animation does get interrupted (push,
     fall, game AI), the script waits ~1.5 s and then puts the athlete back on the
     equipment with the animation restarted.
   - Dumbbells are dropped with realistic physics (DROP_OBJECT + DETACH_OBJECT) and
     are picked up again when the athlete resumes training.
   - Whoever panics runs to the door the player came in through and is DELETED upon
     arrival ("teleport" out of the gym), instead of bouncing off walls forever.
   - Whoever is punched may fight back instead of running: the boxers almost always
     (90%), the lifters sometimes (40%), the joggers rarely (25%) and the customer
     almost never (20%). Anyone shot, blown up or badly hurt runs for the door.

10. Full Cleanup on Death or Arrest:
    - If the player dies or is arrested inside the gym, all entities are cleaned
      up immediately.

--------------------------------------------------------------------------------
INSTALLATION:
--------------------------------------------------------------------------------
- With ModLoader (Recommended):
  Copy the "Academia da Groove Street Movimentada" folder into your "modloader"
  directory.

- Without ModLoader:
  Copy the "live_gym.cs" file from the CLEO subfolder into your "CLEO" folder.

--------------------------------------------------------------------------------
CREDITS:
--------------------------------------------------------------------------------
- Original author: SanKing (2013)
- GTA3script Refactoring, Random System & Fixes: 2026
