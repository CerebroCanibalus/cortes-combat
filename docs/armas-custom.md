# Guía de armas custom (weapon packs)

Con un archivo `.yaml` podés: **meter al combate de Cortés Combat las armas de otros mods**,
**retocar las de este mod**, o **crear armas tuyas** — todo sin escribir una línea de Java.

Tres reglas antes de empezar:

1. **Los números van en `config/cortescombat/weapons/`** — daño, cono, carga, sonidos.
   Esa carpeta la leen los dos lados (cliente y servidor), por eso vive ahí.
2. **Los archivos gráficos van en un resource pack** (`resourcepacks/`) — iconos, sonidos
   `.ogg`, sprites y poses. Es el único sitio del que Minecraft carga assets.
3. **Todo campo es opcional**: lo que no declares, se hereda del arquetipo o del molde.
   Un archivo de dos líneas ya es un arma.

La primera vez que el mod arranca te escribe una carpeta de ejemplo en
`resourcepacks/cortescombat-weapon-pack/` con el `pack.mcmeta` y las carpetas listas —
activála como resource pack y ahí tirás tus archivos.

---

## Caso 1 — Que las armas de otro mod entren al combate

Pones el item en una de nuestras categorías con un datapack, y ya está. Cero YAML.

```
# data/tunombrepack/data/mimod/tags/item/mi_datapack.json
{
  "values": ["tercermod:espada_rara"]
}
```

...pero eso escribe en una categoría genérica. Para decirle **qué es** esa arma, un YAML:

```yaml
# config/cortescombat/weapons/mimod.yaml
version: 1
weapons:
  - match: { id: tercermod:espada_rara }   # o match: { tag: "#tercermod:espadas" }
    archetype: sword                        # molde de movimiento: la espada de siempre
    stats:
      damage: 3.5                           # en CORAZONES (la vanilla dice 4 = 2 corazones)
      attack_speed: 1.8                     # toques por segundo
```

`/cc stats` con el item en la mano te muestra todo lo resuelto y **de dónde salió cada
número** (`source=`).

## Caso 2 — Retocar las armas de este mod

```yaml
version: 1
weapons:
  - match: { id: cortescombat:golden_ropera }
    stats:
      reach: { distance: 3.8 }              # más alcance, en metros
      cone: { horizontal: 40, vertical: 30 }# ángulos en GRADOS, por eje (no hay "tamaños")
      charge: { bonus: 1.5 }                # la carga pegue más fuerte
```

Y si querés que un item vanilla **deje de pelear** (un pico que no te gusta):

```yaml
version: 1
weapons:
  - match: { id: minecraft:iron_pickaxe }
    profile: none                           # "esto no es un arma"
```

## Caso 3 — Un arma nueva de cero

```yaml
version: 1
weapons:
  - creates: mitunmod:cuchillo_de_rubi      # el id que el mod va a registrar
    archetype: dagger
    stats:
      damage: 5.0
      durability: 260
    display:
      name: "Cuchillo de Rubí"              # nombre directo, sin lang
      tab: cortescombat                     # pestaña donde aparece (vacío = la nuestra)
    texture:
      slash: mitunmod:textures/particle/slash/rubi.png   # sprite del golpe (32x160, 5 frames)
    sounds:
      swing: mitunmod:rubi_swing
      hit:   mitunmod:rubi_hit
      charged: mitunmod:rubi_charged
    animation:
      pose: mitunmod:cuchillo               # assets/mitunmod/poses/cuchillo.json, si tenés
```

Lo que falta alrededor (ninguno es nuestro, los hacés vos):

| Pieza | Dónde va |
|---|---|
| Icono | `resourcepacks/.../assets/mitunmod/models/item/cuchillo_de_rubi.json` + su textura |
| Sonidos | `sounds.json` + los `.ogg` en el mismo resource pack |
| Sprite del golpe | `assets/mitunmod/textures/particle/slash/rubi.png` |
| Receta | Un datapack normal: `recipes/cuchillo_de_rubi.json` referenciando `mitunmod:cuchillo_de_rubi` |

Sin icono, el item aparece con el cubo morado de siempre (y grita solo). Sin stats, nace con
los de una espada de hierro.

---

## Los 9 arquetipos

Un arquetipo es "tratalo como esto": le da el molde de movimiento, el especial por defecto y
los defaults. Los números se heredan de ese molde salvo que los cambies.

| Arquetipo | Para qué | Especial por defecto |
|---|---|---|
| `dagger` | cuchillos y dagas | ninguno |
| `sword` | espadas | ninguno |
| `rapier` | roperas / estocadas | **lunge** (el impulso de la ropera) |
| `sabre` | sables | **parry** |
| `hammer` | martillos | ninguno (la carga la encendés vos con `charge:`) |
| `mace` | mazas | ninguno |
| `spear` | lanzas | ninguno (sin lunge, a propósito) |
| `polearm` | armas de asta | **sweep** (barrido) |
| `axe` | hachas de combate | ninguno |

**Sin YAML**: las categorías `#cortescombat:category/<arquetipo>` hacen el mismo trabajo desde
un datapack — metés el item en `category/dagger` y ya pelea como daga (con los defaults del
molde). El YAML sólo hace falta cuando querés **cambiar números** o crear items.

---

## Referencia de campos

Todo va dentro de una entrada de `weapons:`. Identidad: `match: {id|tag}` o `creates`.

| Campo | Unidad y rango | Notas |
|---|---|---|
| `stats.damage` | corazones | Si lo declarás, **reemplaza** el daño del item entero |
| `stats.attack_speed` | toques/s | El tempo (vanilla espada 1.6). **No existe `cooldown`** |
| `stats.durability` | usos | Sólo aplica a `creates` |
| `stats.charge.can_charge` | true/false | La carga nunca resta: el piso es 0 |
| `stats.charge.bonus` | multiplicador | Bonus a carga completa (guerra: 1.0) |
| `stats.charge.full_charge_ticks` | ticks | Ticks a carga completa |
| `stats.max_targets` | cuántos golpea | *(todavía sin cablear)* |
| `stats.knockback` | `{horizontal, vertical}` | Techos 1.6 / 0.45 — el código reclama si pasás |
| `reach.distance` | metros, 1.0–6.0 | **Sólo la distancia** |
| `cone` | `{horizontal, vertical}` en grados, 5–85 | Radio crudo por eje. **`cone: small` ya no existe** |
| `guard` | `{points, arc, regen, stun}` | Aguante: espada 1/90/40/100, escudo 3/120 |
| `shot` | `{distance, horizontal, vertical}` | Segundo alcance (tiro) |
| `special` | `none\|multi_hit\|parry\|slam\|shove\|lunge\|sweep\|throw\|fusee` | Click derecho |
| `profile` | los 14 perfiles o `none` | `none` = excluye el item del combate |
| `texture.slash` | id de textura | PNG 32x160 con 5 frames |
| `sounds.swing/hit/charged` | ids de `sounds.json` | `swing` suena cuando el golpe **no** conecta |
| `animation.pose` | id de pose | `assets/<ns>/poses/<id>.json`; si falta, la del arquetipo |
| `display.name/tab` | texto | Sólo en `creates` |

**Lo que el YAML no puede**: apagar la correa del cono (la holgura de cercanía es una
invariante), hacer que la carga reste, ni pasar los techos de knockback.

---

## Cómo probarlo

| Comando | Qué hace |
|---|---|
| `/cc reload` | Relee los YAML sin reiniciar (los assets nuevos piden `F3+T`) |
| `/cc packs` | Lista los packs cargados + qué entrada matchea el arma de la mano |
| `/cc stats` | Stats resueltos del arma en mano, con **`source=`**: `pack id=tercermod:x` = tu entrada está mandando; `sin pack (tag molde → vanilla → NONE)` = nadie habló y cayó al árbol de siempre |

## Problemas comunes

- **`versión desconocida '2'`** — este mod entiende `version: 1`.
- **`campo desconocido 'x'`** — revisá la referencia de arriba; el archivo sigue funcionando,
  ese campo se ignora.
- **`cone: small ya no existe`** — el warning te dice exactamente qué mapa poner.
- **El sonido no suena** — ¿el `sounds.json` y el `.ogg` están en el resource pack activo? El
  log avisa si el id declarado no está registrado.
- **El ítem creado no pelea** — le falta `archetype` o `profile`; el log lo dice con archivo y
  entrada.
- **No aparece en la pestaña** — `display.tab` sólo conoce `cortescombat`; usá `/give`.
- **Más detalles**: `docs/weapon-packs-design.md` (diseño completo) y `/cc packs` para ver qué
  hay cargado ahora mismo.
