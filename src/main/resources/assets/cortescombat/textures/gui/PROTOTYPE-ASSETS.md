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
| `damage_overlay.png` | 256x128 | **stretched to the whole screen** | `DamageOverlayLayer` — low-health vignette and damage flash | **All Rights Reserved** (from `neoforge-damageoverlay`, no `LICENSE`, no grant). **Highest risk.** |

> **Corrección del 2026-10-13: esto decía "tiled" y es "stretched".** `DamageOverlayLayer` llama a
> `graphics.blit(OVERLAY, 0, 0, w, h, 0, 0, w, h, w, h)` — y las dos últimas coordenadas son las
> dimensiones de la TEXTURA, así que pasarle `w, h` (el tamaño de la pantalla) le dice que la textura
> *es* la pantalla: el UV va de 0 a 1 y **la imagen entera se estira sobre el viewport**. No se repite
> en mosaico. La buena noticia es que una viñeta simétrica sobrevive el estirado; la mala es que si la
> dibujás como un mosaico chico y repetido, en pantalla vas a ver un borrón.

## Cómo se dibuja `damage_overlay.png` (leído del código, no supuesto)

- **`setColor(1, 1, 1, alpha * 0.75)`** — el tinte es **blanco**, así que los colores de tu arte salen
  tal cual: si la pintás roja, roja se ve. El `0.75` es el techo de opacidad del layer.
- **El alpha del layer es la intensidad, y lo maneja el código**: sube cuando baja la vida (de 14 a 4
  corazones, con *cosine easing*) y da un golpe de flash cuando recibís daño. Tu textura sólo aporta
  **la forma** — el centro tiene que ser transparente y los bordes opacos, o vas a tapar la pantalla.
- **No hay inversión de alpha en este camino.** La inversión (`alpha bajo = más overlay`) es del
  `OverlayTexture` de vanilla, que es el flash del **mob**, no esta viñeta. Son dos sistemas distintos
  y acá el alpha significa lo que parece que significa.

Everything under `sprites/hud/` except those two orb sprites is now **original art** supplied by
the project author: `crosshair`, `crosshair_aim`, `crosshair_hit`, `cooldown_corchetes`,
`charge_bar` and `charge_bar_emtpy`. `hotbar_attack_indicator_full.png` (the `combatify` sword
icon that used to draw the cooldown bar) has been deleted, since the cooldown is now drawn by
code around the crosshair rather than by a sprite.

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
