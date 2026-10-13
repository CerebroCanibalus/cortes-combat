# Cortés Combat

[Leer en español](README.es.md)

![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-3C8527?logo=minecraft&logoColor=white)
![NeoForge 21.1+](https://img.shields.io/badge/NeoForge-21.1%2B-EA7E20)
![License GPL-3.0-only](https://img.shields.io/badge/license-GPL--3.0--only-blue)
![Client and server](https://img.shields.io/badge/side-client%20%26%20server-lightgrey)

<p align="center">
  <img src="src/main/resources/assets/cortescombat/textures/item/diamond_ropera.png" width="64" alt="Ropera">
  <img src="src/main/resources/assets/cortescombat/textures/item/diamond_alabarda.png" width="64" alt="Halberd">
  <img src="src/main/resources/assets/cortescombat/textures/item/diamond_martillo.png" width="64" alt="War hammer">
  <img src="src/main/resources/assets/cortescombat/textures/item/diamond_daga.png" width="64" alt="Dagger">
  <img src="src/main/resources/assets/cortescombat/textures/item/diamond_shotel.png" width="64" alt="Shotel">
  <img src="src/main/resources/assets/cortescombat/textures/item/arcabuz_icon.png" width="64" alt="Arcabuz">
</p>

Melee combat where the weapon in your hand decides three separate things — how far you reach, how fast you swing, and what the charge buys you. Vanilla treats a rapier, an axe and a halberd as one object with different damage numbers. This mod doesn't.

## What it changes

**Reach is a cone, not a sphere.** Every weapon carries its own distance, its own horizontal arc and its own vertical arc, and a swing only lands inside them. The rapier has the narrowest cone in the game and the sabre the widest, and the vertical angle is what decides whether you connect with something standing over you or under your feet.

**Attack speed is a hard wall.** You cannot attack halfway through a cooldown, and there's no damage multiplier for timing a swing well. The rhythm belongs to the weapon, so a fast hit that deals flat damage is a real choice instead of a mistake. Bare hands are the fastest thing in the game and reach the shortest.

**A charge is a bonus that never takes anything away.** Hold the button and the hit builds on top of its base — it does not subtract when you release early. A charge bonus is also always a worse deal than simply hitting twice, so holding is a decision about reach and armour, not a DPS trick.

**The right button does something the left one can't.** Every right-click is a movement or a piece of crowd control, never just more damage: the rapier lunges, the halberd sweeps everything in its arc, the shotel parries and returns the blow it caught. Some weapons can't charge and some have no right-click at all, and that is written on the weapon rather than guessed by the game.

**A guard with a real cost.** A shield and a sword both block, both stop 100% of a blocked hit except explosions, and both spend stamina — three blocked hits and the guard breaks, leaving you staggered and open. It only works in the arc in front of you. An axe breaks a guard in a single hit, which is the entire reason to carry one.

**Hunger stops being stamina.** Running, mining and swinging cost nothing. Food is a healing resource with a closed loop: it pays for your regeneration and it grows back on its own clock. Eat while something is hitting you and the hit interrupts you, unless your guard is up.

**Thirty weapons, and none of them is a reskinned sword.** Five are brand new — a rapier, a halberd, a war hammer, a dagger and a shotel — and each one is a different answer to a fight. The vanilla sword, axe, shovel, mace and trident join the system and were rebalanced for it. The pickaxe sits it out, on purpose. There is also an **arcabuz**: a black-powder gun that pays for one enormous shot with a reload, and has no business being in a sword fight.

**Thirty-four enchantments**, all built for the mechanics above rather than for vanilla's — lifesteal, guarding, counters, and a few that are only worth having if you understand the charge.

**The Nether Reactor.** Load it, push it past its ceiling, and it pays out. Push it further and it lights a fuse, tears a crater forty-eight blocks across, boils the water out of the ground and shakes the camera for anything within a hundred and sixty blocks.

**Other mods' weapons join the fight.** A YAML file under `config/cortescombat/weapons/` teaches the system about a weapon from another mod — its reach, its charge, its knockback — and a datapack tag does the same thing from a pack. Neither needs a line of Java. There's a [guide](docs/armas-custom.md) for it (in Spanish).

**Eight languages.** English plus seven varieties of Spanish, because the weapon names are Spanish on purpose — a rapier is a *ropera* here and a halberd is an *alabarda*, and renaming them per language would mean two different sets of weapons.

## Pick something else if

This mod replaces how melee works. That isn't what everyone wants, so here's the honest map:

| You want | Try |
| --- | --- |
| Minecraft Dungeons-style combos, dual wielding and fluid attack animations | [Better Combat](https://modrinth.com/mod/better-combat) |
| A souls-like overhaul with skills, dodging and its own animation set | [Epic Fight](https://modrinth.com/mod/epic-fight) |
| Vanilla's combat rules, with the old Combat Test numbers restored | [Combatify](https://www.curseforge.com/minecraft/mc-mods/combatify) (Fabric) |
| Combat that is exactly vanilla, plus cosmetics | Something else entirely |
| To play on **Fabric or Quilt** | Not this — Cortés Combat is NeoForge only |

And two things worth knowing before you install: this mod is **required on both client and server** (the server resolves every hit; the client only draws it), and it **refuses to load alongside a combat mod it can't coexist with**, printing the name of the mod in the log instead of crashing with a stack trace. That list is short and it exists because a silent conflict is worse than a loud refusal.

## Install

**Requirements:** Minecraft **1.21.1**, NeoForge **21.1+**, **Java 21**.

Drop the jar into your `mods/` folder — on the server, and on every client that connects to it.

No jar yet? Build it:

```bash
git clone https://github.com/CerebroCanibalus/cortes-combat.git
cd cortes-combat
./gradlew build          # Windows: gradlew.bat build
```

The jar lands in `build/libs/`. Add it to `mods/` like any other. For a development environment, `./gradlew runClient` and `./gradlew runServer` boot the game with the mod already loaded.

## Playing it

| Action | What happens |
| --- | --- |
| **Hold left click** | Charges the swing and fires it when you release — one button does both |
| **Right click** | The weapon's own move: a lunge, a sweep, a parry, or nothing at all |
| **Right click holding a shield or a sword** | Raises the guard |
| **Left click holding food** | Eats; the guard can be up at the same time |

Two readouts sit on your screen while you hold: the bar **under the crosshair** is the charge, and the bar **wrapping it** is the cooldown. The brackets close when the weapon is ready to swing. When you carry something that blocks, three faint pips appear above the crosshair — that's your guard stamina, and they are worth watching.

## Weapons from other mods

Two ways in, neither of them requires writing code.

A **YAML file** in `config/cortescombat/weapons/` can describe a weapon by its item id, or define one that doesn't exist yet and hand it the stats you like. A **datapack tag** (`data/<yourpack>/tags/item/...`) can point at a weapon and say "treat this like a halberd". Both are reloadable at runtime, and `/cc packs` tells you which file, if any, is currently claiming the weapon in your hand.

The full field reference, the nine archetypes and a troubleshooting list live in [`docs/armas-custom.md`](docs/armas-custom.md).

## Settings and operator tools

Client settings and world settings use NeoForge's standard config locations, so a server admin can tune balance centrally and a player keeps their own visual preferences.

Operators get a `/cc` command for diagnosing and tuning: `/cc stats` prints the profile, reach, cooldown, charge and special of the weapon in your hand, `/cc packs` lists loaded weapon packs and which one claims the current item, and `/cc reload` re-reads the pack files without restarting the server.

## When something breaks

- **The mod doesn't appear in the mod list.** You're on the wrong loader or the wrong Minecraft version. It's NeoForge 1.21.1 only.
- **`Unsupported class file major version` on startup.** Java is too old. It needs Java 21.
- **The game refuses to load and names another mod.** You have a combat mod it can't coexist with. Remove one of the two.
- **A weapon from another mod hits like a punch.** Nothing claims it yet, so it fell back to a bare-handed profile. Write a pack entry or a tag for it.
- **Hits that should land, miss.** You're outside the cone. Look at what the weapon actually covers — `/cc stats` prints its arcs.
- **Your weapon pack did nothing.** Check `/cc packs`: the item id has to match exactly, and a resource pack that failed to load takes the whole file with it.

## Contributing · Security · License

- Bug reports and feature requests: use the [issue templates](.github/ISSUE_TEMPLATE). The bug form asks for a log — without one there is very little to go on.
- Read [CONTRIBUTING.md](.github/CONTRIBUTING.md) before opening a pull request.
- Found a security problem? See [SECURITY.md](.github/SECURITY.md) — please don't open a public issue for it.
- Licensed under the **GNU General Public License v3.0 only**. See [LICENSE](LICENSE).
