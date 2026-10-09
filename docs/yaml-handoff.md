# Handoff para la ronda de los weapon packs (YAML)

> Resumen de lo hecho el 2026-10-09 (commit `df7c7d6`) y de lo verificado que **cambia
> supuestos del diseño** en `docs/weapon-packs-design.md`. No es un resumen de esa ronda: es lo
> que esa ronda dejó sembrado en el camino de ésta.

---

## 1. Lo que ya existe y el diseño daba por ausente

**`cortescombat:profile/<id>`** — catorce tags, uno por valor de `WeaponProfile`, en
`src/generated/resources/data/cortescombat/tags/item/profile/`. Los escribe
`CortesItemTagProvider.profileTags()` y `WeaponStats.deriveProfile` los pregunta **antes** que los
de vanilla:

```
1. data component weapon_profile     (nuestros 31 items, y mods que adopten la API)
2. #cortescombat:profile/<id>        (los nuestros, el molde)
3. tags de vanilla                   (compat: armas de otros mods)
4. WeaponProfile.NONE                (no es un arma)
```

**El tag lleva el molde, nunca los números.** `profile/halberd` contesta *"tratalo como
alabarda"*; el alcance, la carga, el knockback y el especial siguen saliendo de las cuatro tablas
de `WeaponTables`. O sea: **la capa 2 y la capa 4 del diseño ya existen y se solapan.** Dónde cae
el YAML decide si un packmate que sólo escribe YAML y cuyo item además está en un tag recibe el
molde del tag o el suyo → el YAML tiene que ir **por encima** del tag, no por debajo. Eso es lo que
el §6 del diseño ya dice; queda escrito acá porque ahora hay algo debajo que lo puede pisar.

## 2. Tres afirmaciones del diseño que son falsas, y ya corregidas en el doc

| El doc dice | La verdad (verificado) |
|---|---|
| "Los 5 data components (`weapon_profile`, `reach_profile`, `charge_profile`, `special`, `arcabuz_fuse`)" | **Hay 10** en `ModComponents`. Los 8 que el YAML puede rellenar: `weapon_profile`, `reach_profile`, `shot_reach`, `charge_profile`, `special`, `guard_profile`, `viewmodel_profile`, `knockback_profile`. Los otros dos (`arcabuz_fuse`, `arcabuz_chamber`) son estado del arma, no stats |
| "la derivación lee `ItemAttributeModifiers`" | **No los lee.** `WeaponStats.of()` no toca atributos. Lo único que sale del ítem propio es el **intervalo**: `attackInterval` lee `ATTACK_SPEED` del holder |
| "escala el alcance por la velocidad" | **No existe.** El alcance es `WeaponTables.reach(profile)`, fila fija |

Consecuencia directa para el YAML: **una espada ajena en `#minecraft:swords` se lleva hoy nuestro
3.6 / 35° / 30° y nuestra carga de 25 ticks**, aunque su ítem declare 2 de daño. Darle *sus*
números es exactamente el trabajo del YAML.

## 3. Las tres trampas que el YAML se va a comer si nadie se las dice

**a) `damage` y `attack_speed` no son data components.** Son atributos. En 1.21.1 un datapack
tampoco puede agregarle modificadores a un ítem. El camino que ya usa el mod es
`ItemAttributeModifierEvent` —Ver `SwordRebalance.onAttributes`, que **saca y vuelve a poner**
`Item.BASE_ATTACK_DAMAGE_ID` sobre el builder por defecto, y `HoeRebalance`. Ojo con la
interacción: `SwordRebalance` le resta 1 de daño a todo lo que esté en `#minecraft:swords`, y una
entrada de YAML que además esté en ese tag recibe el descuento. Decidir el orden antes de
implementarlo.

**b) NO materializar los números del YAML como componentes sobre stacks vivos.** El javadoc de
`ModComponents.arcabuz_chamber` documenta el costo exacto: escribir un componente en el stack que
la mano lleva **reemplaza el `ItemStack` del cliente**, y eso es lo que mueve el arma sesenta
centímetros mientras se asienta (la animación de swap). El mismo write que antes ocurría 60 veces
por segundo para el frame de la mecha. Resolver el YAML en una **tabla en memoria que consulte
`WeaponStats.of()`**, no escribiendo componentes.

**c) Los 10 componentes son `persistent` + `networkSynchronized`.** Si algo de esto termina
tocando un componente real, viaja por red; no asumir que es local.

## 4. Los límites duros, que el YAML no puede mover

- **`WeaponProfile` es un enum cerrado de 14 valores y es un contrato de red.**
  `PlayerCombatState` manda el perfil **por ordinal** (`byOrdinal`), y su javadoc lo dice: el orden
  de declaración es contrato. Un molde nuevo es la categoría *"reemplazo completo"* de la
  jerarquía de intervención → el YAML **elige** entre los 14, no crea. El `spears` del §5 del doc
  choca con esto: `cortescombat:spears → ROPERA` es expresable; una lanza con estocada propia es
  un perfil nuevo.
- **`SpecialType` también es enum cerrado**, con el mismo límite.
- **`WeaponKinds.fights()` es la puerta**, y sólo le importa el perfil: `NONE` y `PICKAXE` no
  pelean. `profile: none` en YAML es una exclusión válida y no necesita una segunda pregunta.
- **Los clamps van después de todo**: el piso de carga en cero, la correa del cono
  (`max(distancia·tan(ángulo), min(0.65·semiextensión, 0.8))`), `canCharge` como consulta y no como
  caso especial. Un YAML no desactiva una invariante.

## 5. Testeabilidad, que ya está decidida por el proyecto

- **Nada en `src/test` puede nombrar un tipo de Minecraft.** ∴ `weaponpack/` tiene que ser DTO
  puro (el patrón de `PlayerCombatState`: `encode`/`decode` sobre `ByteBuf`, sin implementar nada
  de Minecraft). El `PackResolver` es la función a testear, como dice el §12 del diseño.
- **Los enums del proyecto no implementan `StringRepresentable`** (es clase de Minecraft) → codec a
  mano. `DataFixerUpper` en `testRuntimeOnly`.
- **SnakeYAML sombreado**: la tarea `:jarJar` existe (ModDevGradle 2.0.141 en `build.gradle`),
  pero **cómo se configura una dependencia sombreada no está verificado**. Verificar antes de
  diseñar alrededor de ello.
- Si el YAML toca datagen (los tags de arquetipo del §5), leer primero las **seis trampas de
  datagen** en `AGENTS.md`: `RecipeProvider.getName()` es final, `createProvider` ya llama
  `addProvider`, `PackOutput.Target`, el constructor de 5 args, `ExistingFileHelper` contesta por
  las fuentes de Mojang, y `#` en un grid no es celda vacía.

## 6. La pregunta de diseño que queda abierta y conviene cerrar antes de codear

Los tags de arquetipo del §5 (`cortescombat:knives`, `hammers`, …) serían tags **nuestros**, igual
que `cortescombat:profile/<id>`. Si un ítem está en `knives` y en `profile/sword` y los dos
responden cosas distintas, **¿quién gana?** Hoy no hay respuesta porque sólo hay una capa de tags
nuestros. Las opciones son: (i) el de categoría gana, porque es más específico; (ii) el de molde
gana, porque `profile/<id>` es el nombre canónico; (iii) se leen en la misma pasada y gana el
primero que matchee. **No es un detalle de implementación: es el orden que el packmate va a tener
que aprender.**

## 7. Dónde está cada cosa

| Qué | Dónde |
|---|---|
| El tag, la mitad pura | `core/weapon/WeaponProfile.java` → `tagPath()` |
| El tag, la mitad con el juego | `item/WeaponProfileTags.java` |
| El orden de lectura | `attack/WeaponStats.java` → `readProfile` / `deriveProfile` |
| Los 14 archivos | `src/generated/resources/data/cortescombat/tags/item/profile/` |
| Quién los genera | `data/CortesItemTagProvider.java` → `profileTags()` |
| Los 10 componentes | `registry/ModComponents.java` |
| La puerta de quién pelea | `item/WeaponKinds.java` → `fights()` |
| El doc de diseño | `docs/weapon-packs-design.md` |
| El guard de esta ronda | `src/test/java/com/cortes/combat/item/WeaponProfileTagsTest.java` (5 tests) |
| La memoria | `AGENTS.md` §"Los tags propios", `CHANGELOG.md` ronda 2026-10-09 |

## 8. Lo que esta ronda **no** hizo, y sigue pendiente

- `weapon_overrides.json` / `generated_weapons.json` (el paso 12 original): **sin implementar**. El
  javadoc de `WeaponStats` todavía dice *"the call site is left for that step and currently falls
  through"*. Es el canal de números para un packmate; el §14 del diseño ya se pregunta si el YAML
  lo absorbe o convive.
- Los tags de arquetipo de categoría (§5): fuera de alcance por decisión.
- Nivel B (crear ítems desde YAML): fuera de alcance.
