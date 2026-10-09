# Weapon Packs — armas custom por YAML

> Diseño, no implementado (2026-10-09). Reabre DESIGN.md en dos puntos: la sección de compat
> (§12, "convertimos armas ajenas") y el veto de números hardcodeados (lo lleva al extremo:
> ahora el número lo puede poner un tercero sin tocar Java).

## 1. Que es

Un **weapon pack** es un archivo `.yaml` que dice, para un conjunto de items identificados por
**tag** o por id, que son armas y como se comportan. El mod ya hace casi todo esto por detrás:

- `WeaponStats.deriveProfile` clasifica por **tags**: primero los nuestros
  (`cortescombat:profile/<id>`), después los de vanilla. **No lee atributos** — verificado: el
  alcance sale de `WeaponTables.reach(profile)`, y lo único que viene del ítem propio es el
  intervalo (`attackInterval` lee `ATTACK_SPEED` del holder).
- `WeaponKinds.fights(profile)` decide quien pelea.
- Los 5 data components (`weapon_profile`, `reach_profile`, `charge_profile`, `special`,
  `arcabuz_fuse`) son exactamente los campos que el YAML va a rellenar.
- El sprite de la slash ya viaja en el payload: **un arma nueva es un PNG y una línea**.

El YAML no agrega poder al sistema: le agrega una puerta de entrada que hoy solo se abre con
codigo Java. Y lo hace **por capacidad, no por nombre de mod** (§8), via tags.

## 2. Alcance: dos niveles, y el segundo no es esta ronda

| Nivel | Que hace | Riesgo |
|---|---|---|
| **A — declarar** | Configura items **ya registrados**: vanilla, de otros mods, los nuestros, o un item vanilla retexturizado por resource pack | Cero. No toca el registro de nada |
| **B — crear** | El YAML **registra items nuevos** (su item, su modelo, su receta) | Alto: registro dinámico en `RegisterEvent` antes del freeze, lectura de disco en el constructor `@Mod`, y si un cliente de MP no tiene el pack, el id no existe en su lado |

**Nivel A cubre "meter mis propias armas"** en los dos sentidos reales: items de otros mods
compatibilizados, y items vanilla reskinneados (un resource pack ya puede hacer
`assets/minecraft/models/item/iron_sword.json` + el YAML pone los stats: eso es "una espada de
rubi" sin registrar nada). El Nivel B queda como fase posterior, con su propia decisión de
diseño sobre sincronización.

## 3. Donde viven los archivos

```
config/cortescombat/weapons/*.yaml    ← del jugador/administrador (este es el channel)
assets o classpath del mod            ← nuestro pack built-in (los arquetipos genéricos)
```

- **No van en datapacks.** Un datapack se recarga desde el servidor y los stats hacen falta en
  el cliente para tooltips, predicción y HUD; leerlo de `config/` en ambos lados simétrico
  evita un payload de sincronización entero. Ademas es donde ya vive la decisión de §12
  (config procedural en JSON plano, no `ModConfigSpec`).
- **Orden de merge:** pack built-in (menor prioridad) < archivos de `config/` en orden
  alfabético. Dentro de un archivo, mas abajo gana. Mas específico gana sobre mas genérico:
  **id individual > tag > arquetipo**.
- **Versión de formato:** campo `version: 1` obligatorio. Un versión desconocido es error de
  carga con nombre de archivo, no un parseo a medias.

## 4. Esquema

```yaml
version: 1

# Arquetipos propios: paquetes reutilizables dentro del mismo pack (opcionales).
archetypes:
  chef_knife:
    profile: dagger
    animation: { pose: mymod:chef_knife }
    sounds: { swing: mymod:knife_swing }

weapons:
  # --- identidad: tag, o lista de ids. Los dos valen.
  - match: { tag: mymod:knives }

    # --- lo que el mod provee: un arquetipo de nuestra libreria (o el propio de arriba).
    archetype: knife

    # --- lo que el pack provee: solo lo que quiera retocar.
    stats:
      damage: 4.5
      attack_speed: 1.6
      cooldown: 14
      reach: { distance: 2.6, horizontal: 22, vertical: 18 }
      charge: { can_charge: false }
      durability: 220
      guard: none                 # la guardia es escudo o espada; un arma nueva nunca cubre
    special: parry                # NONE | MULTI_HIT | PARRY | SLAM | SHOVE | LUNGE | SWEEP | THROW | FUSEE
    cone: small                   # VERY_SMALL..MAXIMUM (6 bandas)

    texture:
      slash: mymod:textures/particle/slash/chef.png   # 32x160, 5 frames. Si falta → el genérico
    sounds:
      swing: mymod:knife_swing
      hit:   mymod:knife_hit
      charged: mymod:knife_charged
    animation:
      pose: mymod:chef_knife      # assets/mymod/poses/chef_knife.json. Si no existe → la del arquetipo

  # --- un arma suelta, sin tag.
  - match: { id: mymod:ruby_knife }
    archetype: knife
    stats: { damage: 6.0 }
```

**Regla de oro: todo campo es opcional y todo lo omitido cae a la capa siguiente.** Un YAML de
dos líneas es valido, igual que un JSON de 3 líneas lo es hoy en el formato de pose.

## 5. Los arquetipos genéricos — lo que le damos a los mods de terceros

El pedido: categorías que los mods suelen traer ("Knives", "Hammers"...). Se entregan como
**tags nuestros** que cualquier datapack puede poblar — un mod tercero no escribe YAML, solo
mete su item en nuestro tag y ya tiene combate completo:

| Tag `cortescombat:` | Perfil | Nota |
|---|---|---|
| `knives` | `DAGGER` | rápido, sin carga |
| `hammers` | `WARHAMMER` | carga, golpe cargado ignora armadura |
| `spears` | `ROPERA`? | alcance + lunge (abierto: ¿perfil nuevo o ropera?) |
| `katanas` | `SABRE` | parry |
| `greatswords` | `ROPERA` | |
| `polearms` | `HALBERD` | sweep |

- **Un arquetipo NO es un perfil nuevo.** Es un paquete: perfil existente + números de
  `WeaponStats` + pose de `ViewModelDefaults` + sprite slash genérico + set de sonidos.
  Crear perfiles nuevos solo si la categoría lo exige de verdad (una lanza con estocada
  propia seria una), y eso es fase 2.
- Tags convencionales `c:`: **verificar** si NeoForge/`c:` ya tiene algo aprovechable
  (`c:tools` y compania); si existe un tag estándar de cuchillos/hammbergs, alias nuestro a
  ese en vez de duplicar.
- Investigar que tags traen hoy los mods populares de armas (DDGS) antes de nombrar los
  nuestros: el tag que ya usan ellos vale mas que el nuestro.

> **No confundir con `cortescombat:profile/<id>`**, que ya existe (ronda 2026-10-09): son catorce
> tags, uno por valor del enum, que clasifican **nuestros propios** items y se leen antes que los de
> vanilla. Los de esta seccion son arquetipos de **categoria** (`knives`, `hammers`), que es otra
> cosa: un arquetipo es un paquete, un perfil es un molde.

## 6. Resolución por capas

```
1. data components        lo que un mod declara en codigo/datapack sobre su item
2. weapon pack (YAML)     lo que el pack declara            ← NUEVO
3. override del jugador   weapon_overrides.json / su YAML   (retunear números)
4. derivación automática  tags nuestros `profile/<id>` + tags vanilla (**sin** atributos ni
   escala de alcance: eso se prometió y no está implementado; ver §6 de AGENTS.md)
5. defaults               tabla de WeaponStats (la de DESIGN §2)
```

- Los clamps duros del codigo se aplican **después** de todo: el piso de carga en cero, la
  correa del cono, `canCharge=false` como consulta y no como caso especial. **Un YAML no puede
  desactivar una invariante**, solo elegir dentro del rango que el codigo permite.
- `WeaponKinds.fights` responde solo: una entrada de YAML le da un perfil != `NONE`, asi que
  pelear no depende de una segunda pregunta.
- §12 manda: `generated_weapons.json` (cache de derivación, lo escribe el mod) y
  `weapon_overrides.json` (jugador) pueden migrar al mismo folder YAML después. **Primero
  conviven; absorber es una decisión posterior** (ver §14).

## 7. Texturas: tres canales distintos

| Canal | Quien lo resuelve | Como |
|---|---|---|
| Icono del item | El dueno del item (el mod, o el resource pack del jugador) | Modelo vanilla `models/item/`, no pasamos por aqui |
| Sprite de la slash | El pack, via `texture.slash` | Ya viaja en el payload; un PNG 32x160 con 5 frames |
| Poses (1a y 3a persona) | El pack, via `animation.pose` | Loader nuevo, ver §9 |

Validación: si el sprite no existe, **caer al genérico con un warning que nombre el path**, no
dibujar un quad invisible (leccion de las guardas de UV).

## 8. Sonidos — los dos casos son distintos

1. **El sonido lo registra otro mod** (caso compat): su `SoundEvent` ya esta en el registro,
   el YAML solo lo referencia. Cero trabajo nuestro.
2. **El sonido es de un resource pack sin codigo** (caso usuario): no hay `SoundEvent`
   registrado. Camino propuesto: reproducirlo en el **cliente** con un `SoundEvent` de id
   crudo construido en el momento (la clase solo carga el id; el motor resuelve contra
   `sounds.json` del resource pack) y transportar el id por payload cuando el evento es
   server-side. **Verificar con javap** antes de diseñar el payload: constructor publico de
   `SoundEvent`, y si `SimpleSoundInstance` acepta una instancia no registrada.
3. Si la verificación falla, plan B: pool de slots genéricos pre-registrados
   (`cortescombat:custom_swing_1..N`) que su `sounds.json` re-mapea.

Los nuestros siguen siendo `sounds.json` como datos + `ModSounds` puro.

## 9. Animaciones — solo si ellos traen las suyas

- **Hoy `WeaponPose.CODEC` no lo consume nadie.** El formato esta hecho (y con su guard test),
  pero no hay loader. Este sistema es el que lo cablea.
- Busqueda: `assets/<ns>/poses/<id>.json` (namespace del arma). No existe → **pose del
  arquetipo** (el perfil de `ViewModelDefaults`, que ya es "sin animacion custom" y se ve bien).
- **Mover `data/cortescombat/poses/shotel.json` a `assets/cortescombat/poses/` antes de
  cablear**: las poses son visuales de cliente, y un resource pack de tercero solo puede
  escribir en `assets/`. Es gratis ahora; después de cablear, caro.
- Carga en `RegisterClientReloadListenersEvent` (cliente puro, no necesita reload de datapack).

## 10. Multijugador

- Ambos lados leen el mismo archivo de `config/` → mismos stats, sin payload nuevo para
  números. El `weapon` id ya viaja en `PlayerCombatState`.
- Cliente sin el pack: el servidor resuelve igual (la autoridad no cambia); el cliente dibuja
  con lo que tenga (si no tiene la entrada, cae a derivación/defaults) y **log de warning**
  con la diferencia. No es crash, es degradacion visible.
- Opción futura: el servidor manda su tabla resuelta al conectar (payload compacto, solo ids +
  overrides). No hace falta en la ronda 1.

## 11. Fallo raudoso y auditoria

- **Nunca fallar en silencio.** Cada warning lleva `archivo:línea` y el arma afectada.
- Modo `strict: true` en el YAML: campo desconocido o recurso faltante = error de carga
  (para CI de packs). Por defecto: warn y seguir.
- `/cc weapons` y `/cc stats` ganan una columna **`source`**: de donde salio cada número
  (`yaml:knives.yaml`, `tag`, `derived`, `default`). Un número sin fuente es un número que
  nadie puede depurar.

## 12. Dependencias y paquete nuevo

- **SnakeYAML** (Apache-2.0, compatible con GPL-3.0) **sombreado y relocalizado** en el jar.
  Es la única dependencia nueva.
- Paquete `weaponpack/` (clases puras, testeables en JVM pelada):
  - `WeaponPack` — el parseo del YAML a un DTO sin tipos de Minecraft.
  - `ArchetypeLibrary` — arquetipos built-in + los del pack, con su orden.
  - `PackResolver` — `(item, packs) -> ResolvedWeapon`, la función de las 5 capas. **Esta es
    la que se testea**: casos tag, id, precedencia, clamps, campo omitido.
  - El adaptador a data components vive al lado, en la capa que ya existe.

## 13. Fases (por dependencia, no por dificultad)

1. `weaponpack/` puro: parseo + resolver + tests (sin Minecraft).
2. Carga de `config/cortescombat/weapons/*.yaml` al arranque + merge con el built-in.
3. Puente a data components (el resolver rellena los 5 components del item).
4. Arquetipos genéricos + tags `cortescombat:knives`, `hammers`, ... + datagen de tags.
5. Loader de poses (con el move a `assets/`) + fallback al arquetipo.
6. Sprite slash custom (ya soportado por payload, solo hay que leerlo del YAML).
7. Sonidos: verificación javap → camino 8.2 o plan B.
8. `/cc weapons` con `source`, warnings, modo strict.
9. (Fase 2) Nivel B: crear items desde YAML; sync de tabla al conectar.

## 14. Abierto — decisiones que no tomo solo

1. **¿El YAML absorbe a `weapon_overrides.json` (§12) o conviven?** Recomiendo convivir en la
   ronda 1 y absorber después: son el mismo channel con distinta sintaxis, y migrar antes
   duplica el testing.
2. **Precedencia cuando el YAML y el override del jugador tocan el mismo número.**
   Recomiendo: **gana el jugador** (el archivo mas reciente y mas específico; en singleplayer
   es su partida). Alternativa: gana el pack (la intencion del servidor/administrador).
   Conviene decidirlo ahora porque es el orden de las capas 2 y 3.
3. **Primer set de arquetipos**: ¿los 6 de la tabla o solo `knives` + `hammers` y crecer con
   reportes?
4. **`spears`**: ¿perfil nuevo (estocada propia) o `ROPERA` + lunge por defecto?
5. El Nivel B (crear items) queda fuera de esta ronda salvo que lo pidas explicitamente.
