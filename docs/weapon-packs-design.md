# Weapon Packs — armas custom por YAML

> **Diseño CERRADO (v2, 2026-10-09)** con las decisiones del General: Nivel A y B juntos, el YAML
> absorbe a `weapon_overrides.json` (un solo channel), arquetipos = **tipos de arma genéricos**
> (nada de katana ni greatsword), **control fino del cono** en las configs, y los packs pueden
> añadir compatibilidad a mods existentes — tanto las de un jugador como las que nosotros
> shippeemos para los populares. Incorpora el handoff de la ronda de tags (`docs/yaml-handoff.md`).
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

## 3. Dónde viven los archivos

```
config/cortescombat/weapons/*.yaml    ← el ÚNICO channel: packs de mods, compat de terceros,
                                        y los números del jugador (absorbe weapon_overrides.json)
classpath del mod                     ← nuestros packs built-in: los arquetipos + la compat oficial
```

- **No van en datapacks**: los stats hacen falta en el cliente (tooltips, predicción, HUD) y
  leer el mismo archivo en ambos lados simétrico mata un payload de sincronización entero.
  Un datapack tampoco puede setear componentes en 1.21.1 (los defaults viven en
  `Item.Properties`, en Java) — por eso el canal es config, no data.
- **Orden de merge**: built-in (menor) < archivos de `config/` en orden alfabético, más abajo
  gana. Dentro de un archivo: **`creates`/id individual > tag > arquetipo**.
- `version: 1` obligatorio: versión desconocida = error con nombre de archivo.

## 4. Esquema

```yaml
version: 1

# Paquetes propios reutilizables (opcionales).
archetypes:
  chef_knife:
    profile: dagger
    animation: { pose: mymod:chef_knife }

weapons:
  # ---- A. configurar un item ya registrado
  - match: { tag: mymod:knives }
    archetype: dagger            # tipo genérico: le da el molde de movimiento
    stats:
      damage: 4.5                # atributo, no componente; gana sobre SwordRebalance (§6)
      attack_speed: 1.6
      cooldown: 14
      durability: 220
      charge: { can_charge: false }
      guard: none                # la guardia es escudo o espada; un arma nueva nunca cubre

    # ---- control fino del cono: banda O números crudos
    cone: small                  # atajo: VERY_SMALL..MAXIMUM (las 6 bandas de ConeSize)
    reach: { distance: 2.6, horizontal: 22, vertical: 18 }   # números crudos: ganan sobre la banda

    special: parry               # none|multi_hit|parry|slam|shove|lunge|sweep|throw|fusee
    texture: { slash: mymod:textures/particle/slash/chef.png }  # 32x160, 5 frames
    sounds:  { swing: mymod:knife_swing, hit: mymod:knife_hit }
    animation: { pose: mymod:chef_knife }   # assets/mymod/poses/chef_knife.json; si falta → arquetipo

  # ---- B. crear un item nuevo
  - creates: mymod:ruby_knife
    archetype: dagger
    stats: { damage: 6.0, durability: 220 }
    display: { name: "Cuchillo de Rubí", tab: cortescombat }

  # ---- excluir algo que hoy pelea por accidente
  - match: { id: somemod:heavy_shovel }
    profile: none                # "no es un arma" — WeaponKinds responde solo
```

**Regla de oro: todo campo es opcional y lo omitido cae a la capa siguiente.** Un YAML de dos
líneas es válido, igual que un JSON de 3 líneas en el formato de pose.

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
| `rapier` | `ROPERA` | estocadas largas | sin especial |
| `sabre` | `SABRE` | sables | parry |
| `hammer` | `WARHAMPER` | martillos | molde pesado; **carga opcional, el pack decide** |
| `mace` | `MACE` | mazas | sin especial |
| `spear` | `ROPERA` | lanzas | alcance largo; sin perfil nuevo (§límites) |
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

## 10. Multijugador y compat built-in

- Ambos lados leen el mismo `config/` → mismos stats, sin payload nuevo. El `weapon` id ya viaja
  en `PlayerCombatState`.
- **Packs de compat oficial**: `compat/<mod>.yaml` en nuestro classpath, con los ids de ese mod.
  **Si el mod no está instalado, sus ids no existen y el resolver descarta la entrada en debug** —
  no es warning, es ausencia normal. Ese es el coste de declarar compat por datos: crece sin
  código nuestro.
- Cliente sin el pack en MP: el servidor resuelve igual (la autoridad no cambia), el cliente
  dibuja con lo que tenga y **log de warning** con la diferencia. Degradación visible, no crash.
- Futuro: el servidor manda su tabla resuelta al conectar. No hace falta en la ronda 1.

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
   con el orden vs `SwordRebalance`.
4. Nivel B: `RegisterEvent` + clase de ítem + `display` + faltantes ruidosos.
5. Tags `cortescombat:category/<tipo>` + datagen + el pack built-in que los traduce.
6. Loader de poses (con el move a `assets/`) + sprite slash por YAML.
7. Sonidos: verificación javap → camino §9.2 o plan B.
8. `/cc weapons` con `source`, warnings, strict.

## 14. Decisiones cerradas (antes abiertas)

1. **Absorción**: el YAML absorbe `weapon_overrides.json` ya — nunca se implementa el paso 12 en
   JSON; el call site de `WeaponStats` es el punto de entrada del YAML. Un solo channel.
2. **Precedencia**: components > YAML > tag molde > vanilla > `NONE`. Dentro del YAML: id >
   tag > arquetipo; entre archivos, el alfabético posterior.
3. **Arquetipos**: los 9 tipos de la tabla §5 (nombres de tipo, no de cultura) + investigación de
   tags hecha: no hay convencional `c:` aprovechable, inventamos `cortescombat:category/<tipo>`.
4. **`spear`**: molde `ROPERA` por ahora — el enum es contrato de red y un perfil nuevo es
   "reemplazo completo"; sólo si el playtest pide estocada propia.
5. **Nivel B**: dentro de esta ronda, fase 4.
