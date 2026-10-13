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

This is an intuitive and noob-friendly Minecraft combat full rework focused on fixing combat skill curve, readability, lack of gameplay options and unintuitive mechanics. Those were the days you had to hit pixel perfect exactly timed hits to kill a baby zombie.. Each weapon now has a radius, cooldown and reach.

## What it overhauls

**Every weapon carries its own distance.** And its own horizontal arc and its own vertical arc, a swing only lands inside them. This solves the years long issue of requiring to hit pixel perfect attacks and missing most of them due to the horrible Minecraft's camera or enemies being too small or fast.

**Attack speed is a hard wall.** You cannot attack halfway through a cooldown like in Vanilla, and there's no damage multiplier for timing a swing well. The rhythm belongs to the weapon, so a fast hit that deals flat damage is a real choice instead of a mistake. Bare hands are the fastest thing in the game but reach the shortest as obvious. This solves both the intuitiveness problem of vanilla cooldown and the ambiguosity in weapons DPS. 

*Charge replaces crits.** Hold attack in order to release a devastating hit, this is a better way of keeping timing skills and also adding a new depth layer to combat. A charged bonus is always a worse deal to constant attacks, but is also a way to comeback during fights through a risky attack, a constant choice you need to balance during confrontation.

**Special attacks.** Some weapons have right-click abilities which not only add to the weapon's identity, but also make every weapon set unique in their playstyle, fulfilling a certain role or gameplay type for each player. Adding a ton of possible combinations to actual combat. 

**A guard with a real cost.** In vanilla blocking with shields often has no risk, and making them not block all damage feels badly, apart from the fact that it just makes not having a shield a better option most of the time. So instead you now have Guard points, which will drain as you block damage, this way it serves as a small pacing for you to heal or reorganize before counterattacking. 

**Hunger stops being stamina.** Running, mining and swinging cost nothing mow. Food is ONLY a healing resource with a closed loop: it pays for your regeneration and it grows back on its own clock. Eat while something is hitting you and the hit interrupts you, unless your guard up, you can eat while using shields or swords.

**6 completely new weapons, and none of them is a reskinned sword.** The ropera, the alabarda, the war hammer, the dagger and the shotel, each one is a different answer to a fight. There is also the **Arcabuz**: a gunpowder gun that shoots demolishing short-ranged blasts, it has no business being brought to a sword fight!


**Vanilla weapons fully reworked!**The vanilla sword, hoe, axe, shovel, mace and trident join the system and were fully reworked for it. The pickaxe stays a tool on purpose though, each vanilla weapon has been reoriented and given a new fresh identity. 

**34 NEW enchantments and reworked vanilla ones.**, A pletora of new and reworked enchantments that go from spectral swords, flying pigs (Not really), killer flies, and many, many more.

**The Nether Reactor.** Load it, push it past its ceiling and turn it into the heart of your base! Push it further and reach the absolute power!! 

**YOUR weapons can join the fight!** If none of the added ones fit you, you can add YOUR OWN through `config/cortescombat/weapons/` and a resourcepack, you can also configurate every weapon of the mod. You can set its reach, its charge, its knockback and much more. 

**Revitalized combat effects.** All kind of wacky and cool effects have been added to damage, low health, mob attacks and more, with a new easy to understand life indicator that won't cannibalize the game's visuals.

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
