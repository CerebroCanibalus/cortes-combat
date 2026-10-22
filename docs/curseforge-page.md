# Página de CurseForge

Contenido listo para pegar. Mismo formato y mismo tono que el README, con lo que la plataforma
pide encima (metadatos, galería, changelog).

## Datos del proyecto

| Campo | Valor |
|---|---|
| Nombre | Cortés Combat |
| Resumen (tagline) | A combat rework for Minecraft 1.21.1 that explains itself. |
| Categorías | Combat, Weapons, Adventure |
| Licencia | GPL-3.0-only |
| Versión del juego | 1.21.1 |
| Versión de Java | 21 |
| Loader | NeoForge 21.1.250 o superior |
| Lado | Client and Server (el mod va en los dos) |
| Slug en Modrinth | `cortes-combat` |

## Descripción (HTML)

CurseForge renderiza HTML en la descripción. Este es el bloque para pegar.

```html
<div align="center">
  <img src="https://media.curseforge.com/PLACEHOLDER/logos/cortes-combat-logo.png" alt="Cortes Combat" width="320">
  <p><b>A combat rework for Minecraft 1.21.1 that explains itself.</b></p>
  <p>Minecraft 1.21.1 &nbsp;·&nbsp; NeoForge 21.1.250+ &nbsp;·&nbsp; Java 21</p>
</div>

<p>Cortes Combat replaces the whole attack flow of Minecraft post 1.9 combat update and gives every
weapon an identity you can learn in a fight.</p>

<p>There is a fifth thing nobody expects. The Arcabuz is a gunpowder musket with a lit fuse, a
devastating short blast and a reload, and it has no business being brought to a sword fight.</p>

<p><img src="https://media.curseforge.com/PLACEHOLDER/gallery/cool_sword_shi.gif" alt="Cortes Combat"></p>

<h2>See it work</h2>

<p>These are the four mechanics everything else hangs off.</p>

<table>
<tr>
<td width="50%"><img src="https://media.curseforge.com/PLACEHOLDER/gallery/reach.gif" alt="Reach is a cone"></td>
<td><b>Reach is a cone.</b> It has been turned into a horizontal arc and a vertical arc. A swing only lands inside them, so pixel perfect hits are not an annoyance anymore.</td>
</tr>
<tr>
<td width="50%"><img src="https://media.curseforge.com/PLACEHOLDER/gallery/cooldown.gif" alt="Beware the cooldown"></td>
<td><b>Beware the cooldown.</b> Halfway through a cooldown you cannot attack, and there is no damage bonus for timing a swing. The rhythm belongs to the weapon type.</td>
</tr>
<tr>
<td width="50%"><img src="https://media.curseforge.com/PLACEHOLDER/gallery/charged_attack.gif" alt="Charge"></td>
<td><b>Charge!!!</b> Hold the attack to deal a devastating slash. Big risk, big reward.</td>
</tr>
<tr>
<td width="50%"><img src="https://media.curseforge.com/PLACEHOLDER/gallery/shield.gif" alt="The guard has a price"></td>
<td><b>The guard has a price.</b> Blocking spends stamina, three hits and you are open. Holding the guard up costs nothing, only taking a hit on it does.</td>
</tr>
</table>

<h2>Five new melee families join the game, none of them a reskinned sword</h2>

<p>The <b>ropera</b> is a needle. Thirteen degrees of cone means you have to mean it, and the right
click dashes you through a line of mobs, dealing bonus damage per mob penetrated.</p>

<p>The <b>alabarda</b> stands off at five blocks and sweeps everything in a sixty degree arc, and it
pays four times the cooldown for the privilege.</p>

<p>The <b>war hammer</b> lifts what it hits, grows its damage with every consecutive swing, and
ignores armour when charged.</p>

<p>The <b>dagger</b> is the fastest thing that can still hurt you, but it can't charge. :(</p>

<p>The <b>shotel</b> is a Muslim sabre with a curved blade (Needed to mention it), it parries for a
fraction of a second, and a full charge lands two separate cuts.</p>

<h2>The Arcabuz</h2>

<p>A gunpowder primitive musket. Load and right click to light the fuse, firing a blast at six and
a half blocks to multiple enemies and burning them to ashes.</p>

<p>You know what they say, never bring a gun to a sword fight... Wait, that's not quite right.</p>

<h2>Hunger is reworked</h2>

<p>Running, mining and swinging cost nothing now. Food is only ever spent to heal, and it
regenerates very slowly. You can gain it back quickly by eating, BUT if you get hit it interrupts
your meal, however you can guard and eating at the same time.</p>

<h2>40+ enchantments, and 6 vanilla reworks</h2>

<p>With the introduction of more fulfilling weapons with specific roles, a repurpose of
enchantments was needed, along with an expansion for both new and olds ones. They range from a
spectral sword, to electrical shocks and flying pigs that are not really flying pigs (I knew it!).</p>

<h2>The Nether Reactor</h2>

<p><img src="https://media.curseforge.com/PLACEHOLDER/gallery/reactor.webp" alt="The Nether Reactor"></p>

<p>Feed the reactor and it will become the heart of your base. The more you charge, the bigger the
range, destroying the corrupted souls that wander your home and providing extended energy range
for your redstone mechanisms.</p>

<p>But be careful of feeding it a bit too much...</p>

<h2>Your own weapons, without writing code!</h2>

<p>You can add your own weapons and compatibility with other mods through a YAML file in
<code>config/cortescombat/weapons/</code>, describe a weapon by its item id, or invent one that does
not exist yet and hand it the stats you like. A datapack tag can point at a weapon and say "treat
this like a halberd". It is able to hotreload stat changes and load custom sprites and sounds
through a resourcepack.</p>

<p>The first time the mod runs it drops an annotated example at
<code>config/cortescombat/weapons/ejemplo.yaml.example</code>, with every field commented in Spanish
and English.</p>

<p>Check the guide in <code>docs/custom-weapons.md</code> (English) or
<code>docs/custom-weapons.es.md</code> (Spanish).</p>

<h2>Install</h2>

<p>Requirements are Minecraft <b>1.21.1</b>, NeoForge <b>21.1.250</b> or newer, and <b>Java 21</b>.</p>

<p>Drop the jar in <code>mods/</code>. On a server the jar goes on the server and on every client
that connects to it, because the client draws the animations and the server decides the hits.</p>

<h2>FAQ</h2>

<p><b>Does the mod need to be on every client?</b> Yes. The server decides the hits and the client
draws the arcs, the charges and the animations.</p>

<p><b>Can I turn X system off?</b> Yes you can comrade. Almost everything the mod adds is
configurable.</p>

<p><b>Why does my weapon from another mod hits like a punch?</b> Nothing has claimed it, so it fell
back to a bare handed profile. Write a pack entry or a tag for it.</p>

<p><b>Is it multiplayer compatible?</b> Yes and fully tested!</p>

<h2>License</h2>

<p>GNU General Public License v3.0 only.</p>
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

```
1.4.12

Added
- Master toggles for the combat rework, the survival rework and the particles.
- Enchantment descriptions for the Enchantment Descriptions mod.
- The jar name now carries the Minecraft version.

Fixed
- Two playtest reports, the /kill command and the item frames.
```

## Notas de publicación

- El jar se sube con el nombre `Cortes-combat-1.4.12-1.21.1.jar`, que es como lo produce el build.
- El logo cuadrado es `docs/media/icon.png`, que son 160x160 y funciona. La banner
  `docs/media/logo.png` (1876x699) no sirve como logo, es la del README.
- La descripción corta (una frase) va también en el `summary`, y es la que se ve en el listado.
- Los links del proyecto apuntan al repositorio y al changelog. La licencia es GPL-3.0-only y tiene
  que coincidir con el `LICENSE` de la raíz.
- Los GIF pesan entre 1,4 y 5,8 MB. CurseForge los aguanta, pero si la carga molesta, se pueden
  recomprimir con ffmpeg a 480 de ancho y 12 fps, que los deja en menos de la mitad sin que se
  note.
