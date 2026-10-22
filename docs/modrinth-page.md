# Página de Modrinth

Contenido listo para pegar. Modrinth renderiza Markdown, así que esta página y el README pueden
compartir casi el mismo texto.

## Datos del proyecto

| Campo | Valor |
|---|---|
| Slug | `cortes-combat` |
| Nombre | Cortés Combat |
| Resumen | A combat rework for Minecraft 1.21.1 that explains itself. |
| Categorías | `combat`, `weapons`, `adventure`, `utility` |
| Licencia | `GPL-3.0-only` |
| Versiones del juego | `1.21.1` |
| Loaders | `neoforge` |
| Versión de NeoForge | `21.1.250` o superior |
| Lado | `client` y `server` (ambos requeridos) |
| Icono | 128x128 PNG |
| Código fuente | `https://github.com/CerebroCanibalus/cortes-combat` |
|issues | `https://github.com/CerebroCanibalus/cortes-combat/issues` |
| Changelog | `https://github.com/CerebroCanibalus/cortes-combat/blob/main/CHANGELOG.md` |

## Descripción

```markdown
![Cortes Combat](https://media.modrinth.com/data/PLACEHOLDER/logos/cortes-combat-logo.png)

**A combat rework for Minecraft 1.21.1 that explains itself.**

Every weapon carries a reach, an arc, a cooldown and a charge. The numbers sit on the tooltip, the
server decides what a hit does, and the whole thing reads like something you can learn in a fight.

The sword reaches further and hits a wide arc. The rapier reaches the same distance through a
needle of a cone and dashes on the right click. The halberd stands off and sweeps everything in
front of it. The sabre is short, fast, and parries. And there is a fifth thing nobody expects, a
gunpowder musket with a lit fuse called the Arcabuz, which has no business in a sword fight.

## Reach is a cone

Distance, a horizontal arc and a vertical arc. A swing only lands inside them, so a mob at the
edge of the arc is a miss you can see coming.

## The cooldown is a wall

You cannot attack halfway through a cooldown, and there is no damage bonus for timing a swing
well. The rhythm belongs to the weapon, so a fast hit that deals flat damage is a real choice
instead of a mistake.

## Charge pays

Hold the attack to load a bonus, release to land it. A charged hit always loses on damage per
second against fast tapping, and always wins when one big hit is what the fight needs.

## A guard with a real cost

Blocking spends stamina, three hits and you are open. Holding the guard up costs nothing, only
taking a hit on it does. You can cover and eat at the same time, in that order.

## Hunger is a healing resource

Running, mining and swinging cost nothing. Food is only ever spent to heal, and it comes back on
its own clock.

## Five new melee families, plus the Arcabuz

The ropera is a needle with a lunge. The alabarda stands off at five blocks and sweeps a sixty
degree arc. The war hammer lifts what it hits and grows with every consecutive swing. The dagger
is the fastest thing that can still hurt you. The shotel is a curved sabre that parries, and a
full charge lands two separate cuts. The Arcabuz is a gunpowder musket with a lit fuse.

Every vanilla weapon joins the same system with its own numbers, along with 48 new enchantments and
six reworked vanilla ones.

## Your own weapons, no code

A YAML file in `config/cortescombat/weapons/` can describe a weapon by its item id, or invent one
that does not exist yet and hand it the stats you like. A datapack tag can point at a weapon and
say "treat this like a halberd". Both reload at runtime.

## Requirements

Minecraft 1.21.1, NeoForge 21.1.250 or newer, Java 21. The mod goes on the server and on every
client that connects to it.

## Commands

`/cc stats` prints the profile, reach, cooldown, charge and special of the weapon in your hand.
`/cc packs` lists the loaded weapon packs. `/cc reload` re-reads them without restarting.
`/cc input` prints every gate that can reject a click, in order.

## License

GNU General Public License v3.0 only.
```

## Galería

Orden de carga, con el pie de foto corto que usa Modrinth.

| # | Archivo | Pie de foto |
|---|---|---|
| 1 | `14-arcabuz-shots.gif` | Every weapon shows its numbers. |
| 2 | `01-reach-cone.gif` | Reach is a cone. |
| 3 | `03-charge.gif` | Hold to charge, release to land it. |
| 4 | `04-guard-stamina.gif` | The guard has a price. |
| 5 | `07-weapons.gif` | The five new families. |
| 6 | `08-arcabuz.gif` | The Arcabuz. |
| 7 | `11-fx.gif` | The flash, the shake and the blood. |
| 8 | `10-reactor.gif` | The Nether Reactor. |
| 9 | `13-weapon-packs.gif` | Your own weapons from a YAML file. |
| 10 | `12-third-person.gif` | The body moves in third person. |
| 11 | `09-enchantments.gif` | 48 new enchantments, and six vanilla ones reworked. |
| 12 | `06-hunger.gif` | Food heals and comes back. |
| 13 | `05-specials.gif` | The right click is another weapon. |
| 14 | `02-hard-cooldown.gif` | The cooldown is a wall. |

Fotos fijas, en la galería estática.

| # | Archivo | Pie de foto |
|---|---|---|
| 15 | `shot-hud.png` | The HUD. |
| 16 | `shot-tooltip.png` | Seven lines where vanilla had two. |
| 17 | `shot-reactor.png` | The reactor running. |
| 18 | `shot-arcabuz.png` | The Arcabuz with the fuse lit. |

## Changelog de la primera versión publicada

```markdown
## 1.4.12

### Added
- Master toggles in the server config for the combat rework, the survival rework and the particles.
- Enchantment descriptions for the Enchantment Descriptions mod.
- The jar name now carries the Minecraft version.

### Fixed
- Two playtest reports, the /kill command and the item frames.
```

## Notas de publicación

- Modrinth quiere el icono en 128x128. La banner `cortescombatlogo.png` no sirve como icono.
- El `side` tiene que ser `client` y `server` a la vez, porque el mod va en los dos lados.
- La categoría `utility` es opcional, pero el mod trae el comando `/cc` y los packs, así que
  entra.
- Los juegos declarados son sólo 1.21.1. Declarar 1.21 y 1.21.4 rompería el build, porque el
  código usa APIs que cambiaron.
- El changelog largo vive en `CHANGELOG.md` del repositorio, y el link va en el campo de links.
