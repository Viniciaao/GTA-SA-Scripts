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

2. Dynamic Weather / Rain Response for Exterior Pedestrian (New Feature):
   - If it is raining outside, the exterior smoking pedestrian is NOT created.
   - If it starts raining while he is already outside smoking, the script cancels
     his smoking animation, assigns him a wandering task (TASK_WANDER_STANDARD),
     and releases him to the game AI to walk away down the street seeking cover.

3. Complete GTA3script Rewrite:
   - Fully rewritten in Rockstar Games' official modern GTA3script syntax, lean,
     structured, and strictly adhering to the engine's 32-variable limit.

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

9. Dynamic Panic & Combat Reaction:
   - Dumbbells drop with realistic physics, NPCs scream and flee intelligently
     upon gunshots, targeting, or physical harm.

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
