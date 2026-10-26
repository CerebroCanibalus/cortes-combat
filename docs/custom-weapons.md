# Custom weapons guide (weapon packs)

> This is the English version. The Spanish one is [`custom-weapons.es.md`](custom-weapons.es.md), and
> it says exactly the same thing.

With a `.yaml` file you can: **pull the weapons of other mods into Cortés Combat's combat**,
**tweak the ones this mod ships**, or **create weapons of your own** — all of it without writing
a single line of Java.

Three rules before you start:

1. **The numbers go in `config/cortescombat/weapons/`** — damage, cone, charge, sounds.
   Both sides read that folder (client and server), which is why it lives there.
2. **The art goes in a resource pack** (`resourcepacks/`) — icons, `.ogg` sounds, sprites and poses.
   It is the only place Minecraft loads assets from.
3. **Every field is optional**: whatever you do not declare is inherited from the archetype or the
   template. A two line file is already a weapon.

The first time the mod starts it writes you an example folder in
`resourcepacks/cortescombat-weapon-pack/` with the `pack.mcmeta` and the folders ready — activate it
as a resource pack and drop your files in there.

The first time it starts it also drops an annotated example at
`config/cortescombat/weapons/ejemplo.yaml.example`, with every field commented in Spanish and
English. Copy it to `mi_pack.yaml` and start from there.

The first time it starts it also writes `config/cortescombat/weapons/cortescombat_weapons.yaml`
with all of the mod's own weapons, so you can edit their stats directly. Run `/cc export weapons`
to generate a timestamped copy instead (useful for backups).

---

## Case 1 — Getting another mod's weapons into combat

You put the item in one of our categories with a datapack, and that is it. Zero YAML.

```
# data/tunombrepack/data/mimod/tags/item/mi_datapack.json
{
  "values": ["tercermod:espada_rara"]
}
```

...but that writes it into a generic category. To tell it **what** that weapon is, a YAML:

```yaml
# config/cortescombat/weapons/mimod.yaml
version: 1
weapons:
  - match: { id: tercermod:espada_rara }   # or match: { tag: "#tercermod:espadas" }
    archetype: sword                        # movement template: the plain old sword
    stats:
      damage: 3.5                           # in HEARTS (vanilla says 4 = 2 hearts)
      attack_speed: 1.8                     # swings per second
```

`/cc stats` with the item in your hand shows you everything that was resolved and **where each
number came from** (`source=`).

## Case 2 — Tweaking this mod's weapons

```yaml
version: 1
weapons:
  - match: { id: cortescombat:golden_ropera }
    stats:
      reach: { distance: 3.8 }              # more reach, in blocks
      cone: { horizontal: 40, vertical: 30 }# angles in DEGREES, per axis (there are no "sizes")
      charge: { bonus: 1.5 }                # the charge hits harder
```

And if you want a vanilla item to **stop fighting** (a pickaxe you are not fond of):

```yaml
version: 1
weapons:
  - match: { id: minecraft:iron_pickaxe }
    profile: none                           # "this is not a weapon"
```

## Case 3 — A brand new weapon

```yaml
version: 1
weapons:
  - creates: mitunmod:cuchillo_de_rubi      # the id the mod is going to register
    archetype: dagger
    stats:
      damage: 5.0
      durability: 260
    display:
      name: "Cuchillo de Rubí"              # direct name, no lang file
      tab: cortescombat                     # the tab it shows up in (empty = ours)
    texture:
      slash: mitunmod:textures/particle/slash/rubi.png   # slash sprite (32x160, 5 frames)
    sounds:
      swing: mitunmod:rubi_swing
      hit:   mitunmod:rubi_hit
      charged: mitunmod:rubi_charged
    animation:
      pose: mitunmod:cuchillo               # assets/mitunmod/poses/cuchillo.json, if you have one
```

What surrounds it (none of it is ours, you make it):

| Piece | Where it goes |
|---|---|
| Icon | `resourcepacks/.../assets/mitunmod/models/item/cuchillo_de_rubi.json` + its texture |
| Sounds | `sounds.json` + the `.ogg` files in the same resource pack |
| Slash sprite | `assets/mitunmod/textures/particle/slash/rubi.png` |
| Pose (1st person) | `assets/mitunmod/poses/cuchillo.json` (JSON, see below) |
| Recipe | A normal datapack: `recipes/cuchillo_de_rubi.json` referencing `mitunmod:cuchillo_de_rubi` |

Without an icon the item shows up with the usual purple cube (and complains on its own). Without
stats it is born with the ones of an iron sword.

---

## The 9 archetypes

An archetype is "treat it like this": it gives the weapon its movement template, its default
special and its defaults. The numbers are inherited from that template unless you change them.

| Archetype | What it is for | Default special |
|---|---|---|
| `dagger` | knives and daggers | none |
| `sword` | swords | none |
| `rapier` | roperas / thrusts | **lunge** (the rapier's dash) |
| `sabre` | sabres | **parry** |
| `hammer` | hammers | none (you turn the charge on yourself with `charge:`) |
| `mace` | maces | none |
| `spear` | spears | none (no lunge, on purpose) |
| `polearm` | polearms | **multi_hit** (the halberd's sweep) |
| `axe` | combat axes | none |

**Without YAML**: the categories `#cortescombat:category/<archetype>` do the same job from a
datapack — you put the item in `category/dagger` and it already fights like a dagger (with the
template's defaults). YAML is only needed when you want to **change numbers** or create items.

---

## Field reference

Everything goes inside a `weapons:` entry. Identity: `match: {id|tag}` or `creates`.

| Field | Unit and range | Notes |
|---|---|---|
| `stats.damage` | hearts | If you declare it, it **replaces** the item's whole damage |
| `stats.attack_speed` | swings/s | The tempo (vanilla sword is 1.6). **There is no `cooldown`** |
| `stats.durability` | uses | Only applies to `creates` |
| `stats.charge.can_charge` | true/false | The charge never subtracts: the floor is 0 |
| `stats.charge.bonus` | multiplier | Bonus at full charge (war hammer is 1.0) |
| `stats.charge.full_charge_ticks` | ticks | Ticks to a full charge |
| `stats.max_targets` | how many it hits | *(not wired up yet)* |
| `stats.knockback` | `{horizontal, vertical}` | Ceilings 1.6 / 0.45 — the code complains if you pass them |
| `reach.distance` | blocks, 1.0–6.0 | **The distance only** |
| `cone` | `{horizontal, vertical}` in degrees, 5–85 | Raw radius per axis. **`cone: small` does not exist anymore** |
| `guard` | `{points, arc, regen, stun}` | Stamina: sword 1/90/40/100, shield 3/120 |
| `shot` | `{distance, horizontal, vertical}` | Second reach (ranged) |
| `special` | `none\|multi_hit\|parry\|slam\|shove\|lunge\|throw\|fusee` | Right click |
| `profile` | the 14 profiles or `none` | `none` = the item is out of combat |
| `texture.slash` | texture id | PNG 32x160 with 5 frames |
| `sounds.swing/hit/charged` | ids from `sounds.json` | `swing` plays when the swing **misses** |
| `animation.pose` | pose id | `assets/<ns>/poses/<id>.json`; if missing, the archetype's |
| `display.name/tab` | text | Only in `creates` |

**What YAML cannot do**: turn off the cone's strap (the close range slack is an invariant), make
the charge subtract, or pass the knockback ceilings.

---

## Haste (Mining Fatigue's opposite)

The mod's hard cooldown is normally unaffected by vanilla's Haste effect, because Haste only
touches the attack speed attribute (which the mod's cooldown already reads). To make Haste
matter, the mod applies an **additional** cooldown reduction on top of vanilla's +10% per
level:

| Haste level | Vanilla attack speed | Hard cooldown reduction |
|---|---|---|
| I | +10% | −15% (×0.85) |
| II | +20% | −30% (×0.70) |
| III | +30% | −45% (×0.55) |

The two effects stack: a Haste III player swings 30% faster (vanilla) AND has a 45% shorter
hard cooldown. Levels above III are clamped to III.

---

## Poses (1st person animation)

A pose is a JSON file that describes how the weapon moves in first person. It lives in a
resource pack, because it is an asset (like a texture or a sound).

### Where the file goes

```
resourcepacks/<your-pack>/
└── assets/<namespace>/
    └── poses/
        └── <id>.json          ← the pose file
```

For example, if your YAML says `animation.pose: mimod:espada_custom`, the file must be at:

```
resourcepacks/mimod-pack/assets/mimod/poses/espada_custom.json
```

### What the file looks like

```json
{
  "id": "mimod:espada_custom",          // must match the file's address
  "axes": "first_person",                // reference frame: first_person
  "rest": {                              // resting position (metres + degrees)
    "x": 0.0, "y": 0.0, "z": 0.0,
    "pitch": 0.0, "yaw": 0.0, "roll": 0.0
  },
  "tracks": [                            // the movements
    {
      "channel": "WINDUP",               // WINDUP | STRIKE | CHOP | RECOVER | COOLDOWN | ...
      "curve": "expo",                   // linear | back | expo | elastic | bounce | ...
      "amplitude": {                     // how much it moves
        "x": 0.0, "y": 0.0, "z": 0.0,
        "pitch": -20.0, "yaw": 0.0, "roll": 0.0
      },
      "tremor": { "x": 0.0, "y": 0.0, "z": 0.0, "pitch": 0.0, "yaw": 0.0, "roll": 0.0 },
      "tremor_hz": 0.0,                  // vibration frequency
      "tremor_weight": "linear"          // vibration weight curve
    }
  ]
}
```

### Available channels

`WINDUP`, `STRIKE`, `CHOP`, `RECOVER`, `COOLDOWN`, `HOLD`, `GUARD`, `BREAK`, `PARRY`,
`LUNGE`, `SWEEP`, `HELD`, `RELOAD`, `SHOOT`

### How it resolves

1. The weapon's YAML names a pose id (`animation.pose`)
2. The game looks for `assets/<ns>/poses/<id>.json` in the active resource packs
3. If found, it is compiled to a viewmodel profile and used for 1st person
4. If not found, a warning is logged and the archetype's default pose is used
5. The 3rd person body still uses the archetype's profile (poses only move the hand in 1st person)

### Recarga

- **Stats (YAML)**: `/cc reload` (instant, no restart)
- **Assets (poses, sprites, sounds)**: `F3+T` (resource reload)

---

## How to test it

| Command | What it does |
|---|---|
| `/cc reload` | Re-reads the YAMLs without restarting (new assets need `F3+T`) |
| `/cc export weapons` | Dumps all of the mod's own weapons to an editable YAML in `config/cortescombat/weapons/` |
| `/cc packs` | Lists the loaded packs + which entry matches the weapon in your hand |
| `/cc stats` | Resolved stats of the weapon in hand, with **`source=`**: `pack id=tercermod:x` means your entry is in charge; `sin pack (tag molde → vanilla → NONE)` means nobody spoke and it fell to the usual tree |

## Common problems

- **`unknown version '2'`** — this mod understands `version: 1`.
- **`unknown field 'x'`** — check the reference above; the file keeps working, that field is
  ignored. Unless the file says **`strict: true`**: then any unknown field or wrongly typed
  one fails the whole file with a log error instead. Strict is for pack CI. What strict does
  NOT check: whether a client asset exists (a sprite, a pose, a sound) — the server never
  sees resource packs, so a missing one stays a warning at use time, naming the file.
- **`cone: small does not exist anymore`** — the warning tells you exactly which map to write.
- **The sound does not play** — are the `sounds.json` and the `.ogg` in the active resource pack?
  The log warns if the declared id is not registered.
- **The created item does not fight** — it is missing `archetype` or `profile`; the log says so
  with the file and the entry.
- **It does not show up in the tab** — `display.tab` only knows `cortescombat`; use `/give`.
- **More details**: `docs/weapon-packs-design.md` (the full design) and `/cc packs` to see what is
  loaded right now.
