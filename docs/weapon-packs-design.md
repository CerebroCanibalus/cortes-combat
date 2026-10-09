# Weapon Packs — armas custom por YAML

> **Diseño CERRADO (v3, 2026-10-09)**: A y B juntos, un solo channel, arquetipos = tipos de
> arma genéricos, control fino del cono, y los 10 puntos de balance/comportamiento resueltos en
> §14. **Los assets viven en resource packs** (§3 — es el único sitio del que el juego los
> carga) y los stats en `config/`. Incorpora el handoff de tags (`docs/yaml-handoff.md`).
> Reabre DESIGN.md §12: el número lo puede poner un tercero sin tocar Java.

## 1. Qué es

Un **weapon pack** es un archivo `.yaml` que dice, para un conjunto de items identificados por
**tag** o por **id**, que son armas y cómo se comportan. El mod ya hace casi todo esto por detrás:

- `WeaponStats.readProfile` clasifica por tags, en este orden: **component → tag molde
  `cortescombat:profile/<id>` → tags vanilla → `NONE`**. No lee atributos (verificado): el
  alcance sale de `WeaponTables.reach(profile)` y lo único propio del ítem es el intervalo
  (`attackInterval` lee `ATTACK_SPEED` del holder).
- `WeaponKinds.fights(profile)` decide quién pelea.
- **10 data components** en `ModComponents`; los **8 que el YAML puede rellenar**:
  `weapon_profile`, `reach_profile`, `shot_reach`, `charge_profile`, `special`, `guard_profile`,
  `viewmodel_profile`, `knockback_profile`. Los otros dos (`arcabuz_*`) son estado, no stats.
- El sprite de la slash ya viaja en el payload: **un arma nueva es un PNG y una línea**.

El YAML no agrega poder al sistema: abre la puerta de entrada a uno que existe. Una espada ajena
en `#minecraft:swords` se lleva hoy nuestro 3.6 / 35° / 30° y nuestra carga de 25 ticks aunque su
ítem declare 2 de daño — **darle sus números es exactamente el trabajo del YAML**.

## 2. Alcance: A y B, juntos

| Nivel | Qué hace | Cómo |
|---|---|---|
| **A — declarar** | Configura items **ya registrados**: vanilla, de otros mods, los nuestros, o vanilla reskinneado por resource pack | `match: { tag: ... }` o `match: { id: ... }`. Cero riesgo |
| **B — crear** | El YAML **registra items nuevos**: su ítem, sus atributos, su pestaña | `creates: <id>` + `RegisterEvent` de `Registries.ITEM` antes del freeze |

Nivel A cubre "compatibilizar cualquier mod": el jugador escribe su pack para los mods que tenga,
y **nosotros shippeamos packs built-in de compat** para los populares (§5). Nivel B cubre "crear
un arma de cero": ítem del YAML + icono de su resource pack + receta de su datapack (una receta
referencia cualquier item id libremente) — tres piezas de terceros ensambladas sin código nuestro.

## 3. Dónde viven los archivos — dos casas, un trabajo cada uno

**Regla dura de Minecraft: los assets sólo se cargan del mod jar o de resource packs.** No hay
ningún otro sitio del que texturas, modelos, sonidos o poses puedan leerse — `config/` no lo hace
el juego nunca. De ahí sale la forma canónica:

```
config/cortescombat/weapons/*.yaml    ← los STATS. Leído del disco por ambos lados: simétrico,
                                        sin activar nada, sin payloads. Absorbe
                                        weapon_overrides.json (ya no existen dos formatos)
resourcepacks/<pack>/                 ← TODOS los assets. Carpeta o .zip: se arrastra, se activa.
                                        En MP el server lo ofrece y ambos lados quedan iguales
classpath del mod                     ← los built-in: arquetipos; la compat oficial, cuando se
                                        investigue (pendiente, §14.3)
```

**Por qué los stats no van al resource pack**: el servidor no lee `assets/` — su
`ResourceManager` sólo carga datapacks. `config/` es el único canal que ambos lados leen del
disco sin payload. (Y un datapack no sirve tampoco: en 1.21.1 no puede setear componentes, que
viven en `Item.Properties`.)

**Estructura canónica de un weapon pack de assets:**

```
resourcepacks/mi-armario/             # carpeta o .zip
├── pack.mcmeta                       # mínimo, el de siempre
└── assets/miarmario/
    ├── models/item/ruby_knife.json    # Nivel B: el icono del ítem creado
    ├── textures/item/ruby_knife.png
    ├── textures/particle/slash/*.png  # texture.slash (32x160, 5 frames)
    ├── poses/*.json                   # animation.pose busca acá
    └── sounds.json + sounds/*.ogg     # sounds.<campo>
```

- **Recarga en caliente (§14.5)**: `F3+T` dispara `reloadResourcePacks()`, que relee los YAML
  que estén dentro del pack **y** horneda atlas/sounds/poses juntos — datos y assets en el mismo
  gesto. Los YAML de `config/` se releen con `/cc reload` (reparsear unos KB a demanda; la
  consulta es un HashMap por ítem → el rendimiento no entra en la ecuación). Lo que **no** se
  puede en caliente es hornedar un atlas nuevo fuera de un resource reload: es límite de
  vanilla, no nuestro — si un PNG se puso después, al cargarlo cae al genérico con warning.
- **Orden de merge**: built-in (menor) < archivos de `config/` en orden alfabético, más abajo
  gana. Dentro de un archivo: **`creates`/id individual > tag > arquetipo**.
- `version: 1` obligatorio: versión desconocida = error con nombre de archivo.

## 4. Esquema — y lo que la config le tiene que decir al jugador

**Política de comentarios: el archivo se explica solo, sin abrir el wiki.** Toda config que
salga del mod (template, `ejemplo.yaml`, built-in) cumple cuatro reglas:

1. **Cabecera completa en cada archivo**: qué es un weapon pack, dónde van los assets
   (`resourcepacks/`), el orden de merge, y `version`.
2. **Cada campo lleva unidad + rango + default**: `cooldown: 14  # ticks (1 = 0.05 s); si se
   omite, el del molde`. Un número sin unidad es una pregunta.
3. **Enums listados con su significado**, no sólo sus valores: `special: parry # la ventana de
   0,6 s que devuelve sólo el golpe parriado`.
4. **Las invariantes quedan escritas donde se intentarían violar**: "no baja de 0", "la correa
   no se apaga", "85° máx" — para que el packmate no descubra el límite con un crash.

Un campo ilegible o una unidad omitida es un campo que el jugador va a adivinar, y adivinar
números de balance es como no tenerlos.

```yaml
# ═══ WEAPON PACK ═════════════════════════════════════════════════════════════
# Define qué items son armas y cómo se comportan en Cortés Combat.
#
# · Los STATS de este archivo se leen en el ARRANQUE (o con /cc reload) por el
#   cliente y el servidor: esté en config/ en ambos lados para que coincidan.
# · Los ASSETS (icono, sonido, pose, sprite) van en un RESOURCE PACK:
#       resourcepacks/mi-pack/assets/<ns>/...
#   Este archivo NO los carga — Minecraft sólo carga assets de resource packs.
#
# Orden: los built-in del mod < estos archivos (alfabético, más abajo gana).
# Dentro de un archivo: match por id > match por tag; lo más abajo, gana.
# version es obligatorio: una versión desconocida es un error, no un parseo a medias.
version: 1

# ── Arquetipos propios (opcionales): paquetes de defaults reutilizables.
#    Heredan lo que no declares de nuestro arquetipo built-in del mismo nombre.
archetypes:
  chef_knife:
    profile: dagger                 # molde de movimiento: dagger|sword|rapier|sabre|hammer|mace|spear|polearm|axe (o los 14: none|unarmed|pickaxe|shovel|trident|warhammer|halberd|...)

weapons:
  - match: { tag: mymod:knives }    # ← IDENTIDAD. tag: "#mymod:knives" o id: "mymod:ruby_knife".
                                    #    Cualquier item puede ser arma: vanilla, de otro mod, el nuestro.
    archetype: dagger               # le da el molde de movimiento + defaults; si lo omitís, el
                                    # perfil sale del tag molde (cortescombat:profile/<id>) o de vanilla

    # ── STATS. Todo opcional: lo que no declares se hereda del molde del arquetipo.
    stats:
      damage: 4.5                   # corazones = dmg/2. Si lo declarás, REEMPLAZA el atributo del
                                    # ítem entero (el rebalance vanilla de espadas no lo toca)
      attack_speed: 1.6             # por segundo (vanilla: espada 1.6, puño 4.0). Vacío = molde
      cooldown: 14                  # ticks duros hasta el próximo golpe (1 tick = 0.05 s)
      durability: 220               # usos; vacío = el del ítem original
      charge: { can_charge: false } # si acepta carga. La carga NUNCA resta: el piso es 0, en código

    # ── CONO: banda O números crudos. Los números crudos ganan sobre la banda.
    cone: small                     # atajo: very_small|small|medium|wide|very_wide|maximum
    reach:                          # control fino (opcional, en reemplazo de cone):
      distance: 2.6                 # metros, 1.0–6.0
      horizontal: 22                # grados ± desde la mirada, 5–85
      vertical: 18                  # grados ± sobre/horizonte, 5–85 (la mirada decide)
      # La correa NO se puede apagar: cerca la holgura es max(d·tanθ, min(0.65·semi, 0.8)).

    special: parry                  # click derecho: none|multi_hit|parry|slam|shove|lunge|sweep|throw|fusee
                                    #  · parry = ventana 0,6 s que devuelve sólo el golpe parriado (cd 5 s)
                                    #  · lunge = impulso corto; sweep = barrido de arco; slam = pisotón
                                    # none también sirve: devuelve el click al juego (maza = lanzar)
    profile: none                   # EXCLUSIÓN: "este item no pelea". Si no lo ponés y el item no
                                    # es arma, el sistema lo ignora solo (WeaponKinds)

    # ── ASSETS. Van en el resource pack (arriba), NO acá. Sólo se referencian por id.
    texture:
      slash: mymod:textures/particle/slash/chef.png   # 32x160 (5 frames); si falta → el genérico + warning
    sounds:
      swing: mymod:knife_swing      # ids de sounds.json (su resource pack). Si el evento no existe: no suena
      hit:   mymod:knife_hit
      charged: mymod:knife_charged
    animation:
      pose: mymod:chef_knife        # assets/mymod/poses/chef_knife.json; si no existe → la del arquetipo
                                    # (una pose es de ambos lados: el server lo ofrece como resource pack)

    display:                        # SÓLO en `creates` — renombrar un item ajeno pisa su idioma
      name: "Cuchillo de Rubí"
      tab: cortescombat             # pestaña de creativo; vacío = la nuestra
    # creates: mymod:ruby_knife      # ← ÍTEM NUEVO: el id que registra el mod. Su icono va en
                                    #    resourcepacks/mi-pack/assets/mymod/models/item/ruby_knife.json
                                    #    y su receta en un datapack (referencia el id libremente)
```

### Control fino del cono — y su correa

`reach: {distance, horizontal, vertical}` son metros y grados crudos (el mismo `ReachProfile` de
los perfiles). Rango que el resolver valida: distancia 1.0–6.0, horizontal 5–85°, vertical
5–85°. **La correa del cono se aplica después igual**: `permitido = max(distancia·tan(ángulo),
min(0.65·semiextensión, 0.8))`. El pack elige dentro del rango; no puede desactivar la holgura
de cercanía — eso es invariante, no número.

## 5. Los arquetipos: tipos de arma genéricos

**Nombres de tipo, no de cultura.** La ropera no es una greatsword, el sable no es una katana, y
nuestro warhammer es un martillo *especial* — por eso el arquetipo se llama `hammer` y el perfil
molde `warhammer` siguen siendo dos cosas distintas (§6). 9 arquetipos, todos **datos puros**
sobre perfiles que ya existen (14 valores, enum cerrado):

| Arquetipo | Molde (perfil) | Cubre | Mecánica por defecto |
|---|---|---|---|
| `dagger` | `DAGGER` | cuchillos, dagas, katars | sin carga, sin especial |
| `sword` | `SWORD` | espadas genéricas | sin especial |
| `rapier` | `ROPERA` | roperas | **lunge** — es la identidad de la ropera |
| `sabre` | `SABRE` | sables | parry |
| `hammer` | `WARHAMPER` | martillos | molde pesado; **sin carga gratis** — la enciende el pack |
| `mace` | `MACE` | mazas | sin especial |
| `spear` | `ROPERA` | lanzas | **sin lunge**; placeholder para las lanzas de futuras versiones (§14.2) |
| `polearm` | `HALBERD` | armas de asta | sweep |
| `axe` | `AXE` | hachas de combate | sin especial |

**Un arquetipo es un paquete**: molde de movimiento (pose de `ViewModelDefaults`) + números de
tipo (alcance, cooldown, cono) + sprite slash genérico + set de sonidos. Las mecánicas especiales
(carga, parry, sweep) son **datos** (`charge_profile`, `special`) que el arquetipo declara y el
pack puede retocar — no vienen pegadas al molde.

**La llave de entrada son tags de categoría nuestros** — `cortescombat:category/<tipo>` (uno por
arquetipo, vacíos salvo los nuestros, igual que nacieron los `profile/`): un mod tercero no
escribe YAML, mete su item en `#cortescombat:category/hammers` y ya tiene combate. Los pobla
nuestro datagen y cualquier datapack los mergea. Investigación DDGS: **no existe tag convencional
`c:` de cuchillos/hammbergs** que podamos aliasar (los mods de armas populares no publican compat
por tags) — los nuestros son inventar, y crecen con reportes.

### La pregunta del handoff, cerrada: ¿categoría vs tag molde?

**El tag de categoría no es una capa de resolución paralela: es la llave de `match` del pack
built-in.** El pack built-in dice `match: {tag: cortescombat:category/hammers}` → `archetype:
hammer`. Así no hay dos tags compitiendo — el orden de lectura queda siendo cuatro capas y una
sola pregunta:

```
1. data component (API Java de un mod)
2. weapon pack YAML (tabla en memoria)   ← también el built-in, que es quien traduce categoría→molde
3. tag molde cortescombat:profile/<id>   ← fallback nuestro
4. tags vanilla                          ← fallback de compat
5. WeaponProfile.NONE                    ← no es un arma
```

La regla que el packmate aprende en una línea: **el YAML manda; el tag molde es el fallback**.
Un item en `category/hammers` y en `profile/sword` a la vez queda como martillo, porque el pack
built-in está por encima del tag molde.

## 6. Resolución por capas — y los tres puentes con el juego

- **Los 8 componentes del YAML se consultan en una tabla en memoria** que `WeaponStats.of()`
  lee. **No se materializan como componentes sobre stacks vivos**: escribir un componente en el
  stack que la mano lleva reemplaza el `ItemStack` del cliente y mueve el arma sesenta
  centímetros mientras se asienta (la animación de swap — costo documentado en
  `ModComponents.arcabuz_chamber`). Además los 10 componentes son `persistent` +
  `networkSynchronized`: si algo toca uno real, viaja por red.
- **`damage` y `attack_speed` no son componentes: son atributos.** El puente es
  `ItemAttributeModifierEvent` (el camino que ya usan `SwordRebalance` y `HoeRebalance`). Orden
  decidido: **si el pack declara `damage` explícito, su número gana sobre el −1 de
  SwordRebalance** para ese ítem — número explícito > regla genérica, la misma precedencia de
  todo el sistema. Si el pack no lo declara, el rebalance vanilla sigue aplicando.
- **`attack_speed` del YAML → modifier de `ATTACK_SPEED` → `attackInterval` lo lee solo.** El
  tempo queda en el ítem, la distancia en nuestras tablas: coherente con cómo funciona hoy.
- **Enums cerrados: el YAML elige, no crea.** `WeaponProfile` es contrato de red
  (`PlayerCombatState` manda el perfil por ordinal) y `SpecialType` también. Una lanza con
  estocada propia sería perfil nuevo = categoría "reemplazo completo"; hasta que el playtest la
  pida, `spear` = molde `ROPERA` y ya.
- **`profile: none` es una exclusión válida** y no necesita segunda pregunta: `WeaponKinds`
  responde solo.
- Los clamps duros van después de todo: piso de carga en cero, correa del cono, `canCharge` como
  consulta. **Un pack no desactiva una invariante.**

## 7. Texturas: tres canales

| Canal | Quién lo resuelve | Cómo |
|---|---|---|
| Icono del ítem | El dueño del ítem (mod o resource pack) | Modelo vanilla `models/item/`; el cubo morado ya es ruidoso |
| Sprite de la slash | El pack, vía `texture.slash` | Ya viaja en el payload; PNG 32x160, 5 frames |
| Poses (1ª y 3ª persona) | El pack, vía `animation.pose` | Loader nuevo, §8 |

Si el sprite no existe: **caer al genérico con warning que nombre el path**, no un quad invisible
(lección de las guardas de UV).

## 8. Animaciones — solo si ellos traen las suyas

- `WeaponPose.CODEC` existe con su guard test pero **nadie lo consume**: este sistema es el que
  cablea el loader.
- Lookup `assets/<ns>/poses/<id>.json` (namespace del arma). No existe → pose del arquetipo
  (perfil de `ViewModelDefaults`, que ya se ve bien sin custom).
- **Mover `data/cortescombat/poses/shotel.json` a `assets/` antes de cablear**: las poses son
  visuales de cliente y un resource pack ajeno sólo escribe en `assets/`. Gratis ahora, caro
  después. Carga vía `RegisterClientReloadListenersEvent`.

## 9. Sonidos — los dos casos

1. **Lo registra otro mod**: su `SoundEvent` ya está, el YAML lo referencia. Cero trabajo.
2. **Es de un resource pack sin código**: reproducir en cliente con un `SoundEvent` de id crudo
   (la clase sólo carga el id; el motor resuelve contra `sounds.json`) + id por payload cuando el
   evento es server-side. **Verificar con javap antes de tocar red**: constructor público de
   `SoundEvent` y si `SimpleSoundInstance` acepta instancia no registrada.
3. Si falla: pool de slots pre-registrados (`cortescombat:custom_swing_1..N`) re-mapeados por su
   `sounds.json`.

## 10. Multijugador y assets compartidos

- Ambos lados leen el mismo `config/` → mismos stats, sin payload nuevo. El `weapon` id ya viaja
  en `PlayerCombatState`.
- **Falta de assets en el cliente** (no tiene el resource pack del server): **warning + textura
  de error** en lo que falte y los sonidos simplemente no suenan. Las animaciones **no** entran
  en esto: un pose va en un resource pack que ambos deben tener sí o sí para jugar — esa es la
  condición de entrada, y el server lo ofrece al conectar.
- **Packs de compat oficial**: `compat/<mod>.yaml` en nuestro classpath, con los ids de ese mod.
  Si el mod no está, sus ids no existen y el resolver descarta la entrada en debug. **Pendiente
  (§14.3)**: primero el sistema, después una investigación profunda de qué mods conviene cubrir.

## 11. Fallo raudoso y auditoría

- Todo warning lleva `archivo:línea` y el arma afectada. Nunca en silencio.
- `strict: true` opcional: campo desconocido o recurso faltante = error de carga (CI de packs).
- `/cc weapons` y `/cc stats` ganan columna **`source`** (`pack:knives.yaml`, `tag`, `derived`,
  `default`): un número sin fuente es un número que nadie puede depurar.

## 12. Dependencias y paquete nuevo

- **SnakeYAML** (Apache-2.0, compatible GPL-3.0), sombreado y relocalizado. La tarea `jarJar`
  existe (ModDevGradle 2.0.141) pero **cómo se configura la dependencia sombreada está sin
  verificar** — verificación obligatoria antes de la fase 2.
- Paquete `weaponpack/` **DTO puro** (nada de `src/test` puede nombrar un tipo de Minecraft):
  - `WeaponPack` — parseo YAML → DTO sin tipos de Minecraft.
  - `ArchetypeLibrary` — los 9 built-in + los del pack, con su orden.
  - `PackResolver` — `(item, packs) -> ResolvedWeapon`: la función de las 5 capas. **La que se
    testea**: precedencia id>tag>arquetipo, campo omitido cae, clamps, `profile: none`.
  - El puente a componentes/atributos vive al lado, en la capa que ya existe (con `WeaponStats`,
    `WeaponProfileTags`, `ItemAttributeModifierEvent`).

## 13. Fases (por dependencia)

1. `weaponpack/` puro: `PackResolver` + `ArchetypeLibrary` + DTOs, **sin parseo aún** (los 9
   arquetipos y la precedencia se testean hoy mismo en JVM pelada).
2. Verificar `jarJar` con SnakeYAML → parseo YAML + merge built-in/`config/`.
3. Puentes: tabla en memoria para `WeaponStats.of()`, atributos por `ItemAttributeModifierEvent`
   con el orden vs `SwordRebalance` (§14.7).
4. Nivel B: `RegisterEvent` + clase de ítem + `display` + faltantes ruidosos + **template de
   resource pack** (la estructura de §3, escrita en `resourcepacks/` la primera vez que arranca,
   para que el packmate tenga dónde soltar el primer PNG).
5. Tags `cortescombat:category/<tipo>` (los 9 vacíos) + datagen + el pack built-in que los
   traduce a arquetipo.
6. Loader de poses (con el move a `assets/`) + sprite slash por YAML.
7. Sonidos: verificación javap → camino §9.2 o plan B.
8. `/cc weapons` con `source`, warnings, strict, y **`/cc reload`** (§14.5, datos de `config/`).
9. **Futura**: investigación a gran profundidad de compat oficial + la sync de tabla al conectar
   en MP (§14.3, §10).

## 14. Los 10 puntos, cerrados

1. **Números de los arquetipos**: (i) **heredan todo del molde** (`WeaponTables` por perfil).
   El arquetipo sólo aporta especial default + sprite + sonido; números de tipo si después se
   quieren, como campos opcionales que no rompen nada.
2. **Identidad del tipo va, mecánica especial no gratis.** `rapier` = `ROPERA` **con** lunge (la
   ropera es su propio arquetipo); `spear` = **sin lunge**, placeholder para las lanzas de
   versiones futuras. `hammer` sin carga hasta que el pack la encienda.
3. **Compat oficial built-in: pendiente.** Primero el sistema; después, investigación a gran
   profundidad de qué mods populares cubrir (DDGS).
4. **`spears` sin perfil nuevo**: el enum es contrato de red; sólo si el playtest pide estocada.
5. **Recarga en caliente: sí, factible** (verificado: `reloadResourcePacks()` + `listResources`).
   Datos → `/cc reload` relee `config/` a demanda (KBs, HashMap por ítem, rendimiento fuera de
   la ecuación). Assets → `F3+T` (resource reload horneda atlas/sounds/poses, y relee los YAML
   del pack). Atlas nuevo fuera de un resource reload es límite de vanilla: fallback + warning.
6. **MP sin assets**: warning + textura de error + sonidos mudos; poses = resource pack que
   ambos deben tener sí o sí (el server lo ofrece). Y **la forma de los assets es la de §3**:
   resource pack, porque es el único sitio del que el juego carga assets — `config/` sólo lleva
   stats.
7. **"Número explícito gana", en todo**: lo que el pack declara reemplaza ese atributo entero
   del ítem (se salta todos los rebalances vanilla que le toquen); lo que no declara, sigue con
   los rebalances.
8. **`display.name` sólo para `creates`**: renombrar un item ajeno desde config pisa su lang.
9. **Los 9 tags `category/<tipo>` nacen vacíos** — el patrón de los `profile/`, donde los
   vacíos son la especificación.
10. **Sonidos por defecto del arquetipo: los nuestros** (ya son datos en `sounds.json`); sonidos
    propios de tipo, cuando aparezcan.
