# PROTOTYPE ASSETS — DO NOT SHIP THESE

The textures in this directory were copied from the reference mods in `refs/` purely so the
HUD could be prototyped and tested before the real art exists. They must all be replaced with
original art before any public release.

## What is actually used

Only three files are referenced by code. Everything else has been deleted rather than left
sitting in the repo as unattributed LGPL artwork waiting to be copied into a resource pack by
accident.

| File | Size | Drawn at | Used by | Licence risk |
|---|---|---|---|---|
| `sprites/hud/crosshair_shield_indicator_full.png` | 16x16 | 9x9 | `GuardOrbsLayer` — one guard point still available | **LGPL-2.1** (from `combatify`) |
| `sprites/hud/crosshair_shield_indicator_disabled.png` | 16x16 | 9x9 | `GuardOrbsLayer` — a guard point already spent | **LGPL-2.1** (from `combatify`) |
| `sprites/hud/hotbar_attack_indicator_full.png` | 18x18 | 18x18 | `CooldownBarLayer` — the cooldown bar fill | **LGPL-2.1** (from `combatify`) |
| `damage_overlay.png` | 256x128 | tiled | `DamageOverlayLayer` — low-health vignette and damage flash | **All Rights Reserved** (from `neoforge-damageoverlay`, no `LICENSE`, no grant). **Highest risk.** |

## Replacing them

Keep the same path and the same pixel size. Nothing in the rendering code has to change, and
the sprite sheet does not need to be rebuilt.

Draw them at the pixel size listed above, not the size they are drawn at: the 9x9 orb is a
downscale of a 16x16 sprite, so detail below 16x16 is thrown away before the player sees it.

The two shield sprites are the ones to redraw first. They are the only art the guard mechanic
has, and the full/spent pair has to read as a count at a glance, which is the whole job of an
indicator like this.

## Why not just keep them

LGPL-2.1 is copyleft, so keeping the `combatify` sprites puts the whole mod under terms the
project does not want. The `neoforge-damageoverlay` vignette is worse: that repository has no
licence file at all, which means all rights reserved and no permission was ever granted. It is
the one asset here that could not be kept even by changing the mod's licence.
