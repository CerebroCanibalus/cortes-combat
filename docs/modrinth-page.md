# Página de Modrinth

Contenido listo para pegar. Mismo formato y mismo tono que el README, con lo que la plataforma
pide encima (metadatos, galería, changelog).

## Datos del proyecto

| Campo | Valor |
|---|---|
| Slug | `cortes-combat` |
| Nombre | Cortés Combat |
| Resumen | A combat rework for Minecraft 1.21.1 that explains itself. |
| Categorías | `combat`, `weapons`, `adventure` |
| Licencia | `GPL-3.0-only` |
| Versiones del juego | `1.21.1` |
| Loaders | `neoforge` |
| Versión de NeoForge | `21.1.250` o superior |
| Lado | `client` y `server` (ambos requeridos) |
| Icono | 128x128 PNG (o 160x160, Modrinth reescala) |
| Código fuente | `https://github.com/CerebroCanibalus/cortes-combat` |
| Issues | `https://github.com/CerebroCanibalus/cortes-combat/issues` |
| Changelog | `https://github.com/CerebroCanibalus/cortes-combat/blob/main/CHANGELOG.md` |

## Descripción

Modrinth renderiza Markdown, así que esta página y el README comparten el mismo texto.

```markdown
![Cortes Combat](https://media.modrinth.com/data/PLACEHOLDER/logos/cortes-combat-logo.png)

**A combat rework for Minecraft 1.21.1 that explains itself.**

Cortes Combat replaces the whole attack flow of Minecraft post 1.9 combat update and gives every
weapon an identity you can learn in a fight.

There is a fifth thing nobody expects. The Arcabuz is a gunpowder musket with a lit fuse, a
devastating short blast and a reload, and it has no business being brought to a sword fight.

![The mod in action](https://media.modrinth.com/data/PLACEHOLDER/gallery/cool_sword_shi.gif)

## See it work

These are the four mechanics everything else hangs off.

| | |
|---|---|
| ![](https://media.modrinth.com/data/PLACEHOLDER/gallery/reach.gif) | **Reach is a cone.** It has been turned into a horizontal arc and a vertical arc. A swing only lands inside them, so pixel perfect hits are not an annoyance anymore. |
| ![](https://media.modrinth.com/data/PLACEHOLDER/gallery/cooldown.gif) | **Beware the cooldown.** Halfway through a cooldown you cannot attack, and there is no damage bonus for timing a swing. The rhythm belongs to the weapon type. |
| ![](https://media.modrinth.com/data/PLACEHOLDER/gallery/charged_attack.gif) | **Charge!!!** Hold the attack to deal a devastating slash. Big risk, big reward. |
| ![](https://media.modrinth.com/data/PLACEHOLDER/gallery/shield.gif) | **The guard has a price.** Blocking spends stamina, three hits and you are open. Holding the guard up costs nothing, only taking a hit on it does. |

## Five new melee families join the game, none of them a reskinned sword

The **ropera** is a needle. Thirteen degrees of cone means you have to mean it, and the right click
dashes you through a line of mobs, dealing bonus damage per mob penetrated.

The **alabarda** stands off at five blocks and sweeps everything in a sixty degree arc, and it pays
four times the cooldown for the privilege.

The **war hammer** lifts what it hits, grows its damage with every consecutive swing, and ignores
armour when charged.

The **dagger** is the fastest thing that can still hurt you, but it can't charge. :(

The **shotel** is a Muslim sabre with a curved blade (Needed to mention it), it parries for a
fraction of a second, and a full charge lands two separate cuts.

## The Arcabuz

A gunpowder primitive musket. Load and right click to light the fuse, firing a blast at six and a
half blocks to multiple enemies and burning them to ashes.

You know what they say, never bring a gun to a sword fight... Wait, that's not quite right.

## Hunger is reworked

Running, mining and swinging cost nothing now. Food is only ever spent to heal, and it regenerates
very slowly. You can gain it back quickly by eating, BUT if you get hit it interrupts your meal,
however you can guard and eating at the same time.

## 40+ enchantments, and 6 vanilla reworks

With the introduction of more fulfilling weapons with specific roles, a repurpose of enchantments
was needed, along with an expansion for both new and olds ones. They range from a spectral sword,
to electrical shocks and flying pigs that are not really flying pigs (I knew it!).

## The Nether Reactor

![The Nether Reactor](https://media.modrinth.com/data/PLACEHOLDER/gallery/reactor.webp)

Feed the reactor and it will become the heart of your base. The more you charge, the bigger the
range, destroying the corrupted souls that wander your home and providing extended energy range
for your redstone mechanisms.

But be careful of feeding it a bit too much...

## Your own weapons, without writing code!

You can add your own weapons and compatibility with other mods through a YAML file in
`config/cortescombat/weapons/`, describe a weapon by its item id, or invent one that does not exist
yet and hand it the stats you like. A datapack tag can point at a weapon and say "treat this like
a halberd". It is able to hotreload stat changes and load custom sprites and sounds through a
resourcepack.

The first time the mod runs it drops an annotated example at
`config/cortescombat/weapons/ejemplo.yaml.example`, with every field commented in Spanish and
English.

The full guide lives in `docs/armas-custom.md` (English) and `docs/armas-custom.es.md` (Spanish).

## Install

Requirements are Minecraft **1.21.1**, NeoForge **21.1.250** or newer, and **Java 21**.

Drop the jar in `mods/`. On a server the jar goes on the server and on every client that connects
to it, because the client draws the animations and the server decides the hits.

## FAQ

**Does the mod need to be on every client?**
Yes. The server decides the hits and the client draws the arcs, the charges and the animations.

**Can I turn X system off?**
Yes you can comrade. Almost everything the mod adds is configurable.

**Why does my weapon from another mod hits like a punch?**
Nothing has claimed it, so it fell back to a bare handed profile. Write a pack entry or a tag for
it.

**Is it multiplayer compatible?**
Yes and fully tested!

## License

GNU General Public License v3.0 only.
```

## Galería

Los mismos seis archivos que usa el README, con el pie de foto corto que corresponde.

| # | Archivo | Pie de foto |
|---|---|---|
| 1 | `cool_sword_shi.gif` | The hero shot. |
| 2 | `reach.gif` | Reach is a cone. |
| 3 | `cooldown.gif` | Beware the cooldown. |
| 4 | `charged_attack.gif` | Charge!!! |
| 5 | `shield.gif` | The guard has a price. |
| 6 | `reactor.webp` | The Nether Reactor. |

## Changelog de la primera versión publicada

```markdown
## 1.4.12

### Added
- Master toggles for the combat rework, the survival rework and the particles.
- Enchantment descriptions for the Enchantment Descriptions mod.
- The jar name now carries the Minecraft version.

### Fixed
- Two playtest reports, the /kill command and the item frames.
```

## Notas de publicación

- El icono es `docs/media/icon.png`, que son 160x160. Modrinth acepta cualquier tamaño y lo
  reescala, así que sirve tal cual. La banner `docs/media/logo.png` (1876x699) no sirve como icono.
- El `side` tiene que ser `client` y `server` a la vez, porque el mod va en los dos lados.
- Los juegos declarados son sólo 1.21.1. Declarar 1.21 y 1.21.4 rompería el build, porque el
  código usa APIs que cambiaron.
- El changelog largo vive en `CHANGELOG.md` del repositorio, y el link va en el campo de links.
- Los GIF pesan entre 1,4 y 5,8 MB. Modrinth los aguanta, pero si la carga molesta, recomprimirlos
  con ffmpeg a 480 de ancho y 12 fps los deja en menos de la mitad sin que se note.
