# Página de CurseForge

Contenido listo para pegar. Los bloques marked con `<!-- -->` son las galerías, que se llenan con
las capturas de `docs/media/` según la guía de captura.

## Datos del proyecto

| Campo | Valor |
|---|---|
| Nombre | Cortés Combat |
| Resumen (tagline) | A combat rework for Minecraft 1.21.1 that explains itself. |
| Categorías | Combat, Weapons, Magic, Adventure |
| Licencia | GPL-3.0-only |
| Versión del juego | 1.21.1 |
| Versión de Java | 21 |
| Loader | NeoForge 21.1.250 o superior |
| Lado | Client and Server |
| Slug en Modrinth | `cortes-combat` |
| Project ID (propio) | `cortes-combat` |

## Descripción (HTML)

CurseForge renderiza HTML en la descripción. Este es el bloque para pegar.

```html
<div align="center">
  <img src="https://media.curseforge.com/PLACEHOLDER/logos/cortes-combat-logo.png" alt="Cortes Combat" width="320">
  <p><b>A combat rework for Minecraft 1.21.1 that explains itself.</b></p>
  <p>Minecraft 1.21.1 &nbsp;·&nbsp; NeoForge 21.1.250+ &nbsp;·&nbsp; Java 21</p>
</div>

<p>Every weapon carries a reach, an arc, a cooldown and a charge. The numbers sit on the tooltip,
the server decides what a hit does, and the whole thing reads like something you can learn in a
fight instead of something you have to look up.</p>

<p>The sword reaches further and hits a wide arc. The rapier reaches the same distance through a
needle of a cone and dashes on the right click. The halberd stands off and sweeps everything in
front of it. The sabre is short, fast, and parries. And there is a fifth thing nobody expects, a
gunpowder musket with a lit fuse called the Arcabuz, which has no business in a sword fight.</p>

<h2>What it changes</h2>

<ul>
  <li><b>Reach is a cone.</b> Distance, a horizontal arc and a vertical arc. A swing only lands inside them.</li>
  <li><b>The cooldown is a wall.</b> No attacking halfway through a cooldown, and no damage bonus for timing a swing well.</li>
  <li><b>Charge pays.</b> Hold to load a bonus, release to land it. A charged hit always loses on damage per second against fast tapping, and always wins when one big hit is what you need.</li>
  <li><b>Special attacks.</b> The rapier lunges, the halberd sweeps, the sabre parries, the Arcabuz lights its fuse.</li>
  <li><b>A guard with a real cost.</b> Blocking spends stamina. Three hits and you are open.</li>
  <li><b>Hunger is a healing resource.</b> Running and mining cost nothing. Food is only spent to heal, and it comes back on its own clock.</li>
  <li><b>Five new melee families</b> plus the Arcabuz, and every vanilla weapon reworked into the same system.</li>
  <li><b>48 new enchantments</b>, and six vanilla ones reworked.</li>
  <li><b>The Nether Reactor</b>, loadable past its ceiling.</li>
  <li><b>Your own weapons</b> through YAML packs and datapack tags, no code required.</li>
</ul>

<h2>Install</h2>

<p>Drop the jar in <code>mods/</code>. On a server the jar goes on the server and on every client
that connects to it.</p>

<p>Requires Minecraft 1.21.1, NeoForge 21.1.250 or newer, and Java 21.</p>

<h2>When something breaks</h2>

<ul>
  <li><b>Not in the mod list.</b> Wrong loader or wrong Minecraft version. It is NeoForge on 1.21.1 and nothing else.</li>
  <li><b>Unsupported class file major version.</b> Java is too old. It needs Java 21.</li>
  <li><b>The game refuses to load and names another mod.</b> You have a combat mod it cannot run next to.</li>
  <li><b>A weapon from another mod hits like a punch.</b> Nothing claims it, so it fell back to a bare handed profile. Write a pack entry or a tag for it.</li>
  <li><b>Hits that should land miss.</b> You are outside the cone. <code>/cc stats</code> prints the arcs.</li>
</ul>

<h2>Commands</h2>

<p><code>/cc stats</code> prints the profile, reach, cooldown, charge and special of the weapon in
your hand. <code>/cc packs</code> lists the loaded weapon packs. <code>/cc reload</code> re-reads
them without restarting. <code>/cc input</code> prints every gate that can reject a click.</p>

<h2>License</h2>

<p>GNU General Public License v3.0 only.</p>
```

## Galería

Orden recomendado, cada entrada con su archivo de `docs/media/`.

| # | Archivo | Pie de foto |
|---|---|---|
| 1 | `14-arcabuz-shots.gif` | Every weapon shows its numbers. Reach, arc, cooldown, charge, knockback and the right click, right on the item. |
| 2 | `01-reach-cone.gif` | Reach is a cone. Distance plus a horizontal and a vertical arc. |
| 3 | `03-charge.gif` | Hold to charge, release to land it. |
| 4 | `04-guard-stamina.gif` | The guard has a price. Three blocked hits and you are open. |
| 5 | `07-weapons.gif` | The ropera, the alabarda, the war hammer, the dagger and the shotel. |
| 6 | `08-arcabuz.gif` | The Arcabuz. A lit fuse and a very short distance. |
| 7 | `11-fx.gif` | The flash, the shake, the blood and the slash wave. |
| 8 | `10-reactor.gif` | The Nether Reactor, loaded past its ceiling. |
| 9 | `13-weapon-packs.gif` | Your own weapons, from a YAML file, without writing code. |
| 10 | `12-third-person.gif` | The body moves too, in third person, for everyone. |
| 11 | `09-enchantments.gif` | 48 new enchantments, and six vanilla ones reworked. |
| 12 | `06-hunger.gif` | Food heals, and food comes back on its own clock. |
| 13 | `05-specials.gif` | The right click is another weapon. |
| 14 | `02-hard-cooldown.gif` | The cooldown is a wall. |

Fotos fijas aparte, para la galería estática.

| # | Archivo | Pie de foto |
|---|---|---|
| 15 | `shot-hud.png` | The crosshair, the cooldown bracket, the charge bar and the guard orbs. |
| 16 | `shot-tooltip.png` | Seven lines where vanilla had two. |
| 17 | `shot-reactor.png` | The reactor, running. |
| 18 | `shot-arcabuz.png` | The Arcabuz with the fuse lit. |

## Changelog de la primera versión publicada

```
1.4.12

Added
- Master toggles in the server config for the combat rework, the survival rework and the particles.
- Enchantment descriptions for the Enchantment Descriptions mod.
- The jar name now carries the Minecraft version.

Fixed
- Two playtest reports, the /kill command and the item frames.
```

## Notas de publicación

- El jar va con el nombre `Cortes-combat-1.4.12-1.21.1.jar`, que es como lo produce el build.
- El logo cuadrado sale de `cortescombatlogo.png` recortado, 256x256, fondo transparente.
- La descripción corta (una frase) va también en el `summary`, y es la que se ve en el listado.
- Los links del proyecto apuntan al repositorio y al changelog. La licencia es GPL-3.0-only y tiene
  que coincidir con el `LICENSE` de la raíz.
