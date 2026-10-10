# Propuesta v2 — Encantamientos para las armas del catálogo

> **Fuente**: subagente de investigación, 2026-10-10. Todo lo numérico está anclado a los valores
> reales del código leído (`WeaponFamily`, `WeaponTables`, `ChargeProfile`, `ParryState`
> `WINDOW_TICKS=12/COOLDOWN_TICKS=100`, `StreakState.WINDOW_TICKS=80` y `WARHAMMER_STEP=0.15`,
> `ArcabuzState.FUSE_TICKS=20/RELOAD_TICKS=50`, `ArcabuzShot` `MAX_TARGETS=10/SHOT_MULTIPLIER=3.0/
> FIRE_SECONDS=6.0`, `LungeDamage` `SHARE=0.75/STEP=0.375/CAP=4.0/COOLDOWN_FACTOR=8.0`,
> `HalberdSweep.COOLDOWN_FACTOR=4.0`, `AttackTiming.IFRAME_TICKS=3`, `Knockback.TAP_PUSH=0.55` y
> `ENCHANT_KNOCKBACK_SCALE=0.5`, `SweepingEdge` `0.5`/enemigo/nivel a radio `3.0`) y a los **archivos
> de datos de vanilla leídos directamente del jar** (`data/minecraft/enchantment/knockback.json` y
> `data/minecraft/tags/item/enchantable/*.json`). No hay ningún número de memoria.

## Estado de la v2 (qué cambió respecto de la v1)

| Parte de la v1 | Veredicto del dueño | Qué hace esta v2 |
|---|---|---|
| **E1–E7** (Pólvora Seca, Reflejo de Acero, Audacia, Ritmo de Hierro, Filo Fugaz, Segadora, Contragolpe) | **APROBADOS** | Se conservan **sin cambios**, con la etiqueta `APROBADO`. |
| **E8 Contundente** (knockback nuevo) | **RECHAZADO** | Se reescribe entero. La consigna: *"ya hay un encantamiento para mayor knockback, simplemente deberíamos extender al resto de objetos"*. Ahora la propuesta es **extender el Knockback de vanilla**, no crear uno. Ver **§D**. |
| **Sweeping Edge reworkeado** (crowd charge) | **No convence** | Se analiza por qué no funciona y se ofrecen **3 alternativas** numeradas, sin elegir ganador. Ver **§E**. |
| — | — | Se **agregan 2 genéricos** más (además de E7) para clases físicas. Ver **§F**. |
| — | — | Se **agregan ~3 de identidad por familia** (ropera, alabarda, martillo, daga, shotel, arcabuz). Ver **§G**. |

> **Recordatorio de las reglas duras del proyecto** que ninguna propuesta puede romper: la **carga
> nunca resta** (el piso se fuerza en código), la **guardia es una postura** (no se pega con la
> guardia arriba, y los i-frames del lunge no se tocan), **el cliente nunca decide** (el servidor
> resuelve y manda estado), la **velocidad de ataque es dura** (no se ataca a medio cooldown), y
> **ningún número de gameplay vive hardcodeado** fuera de una clase pura y testeable.

## Principios que gobiernan la propuesta

1. **Identidad primero**: un encantamiento nuevo amplifica una mecánica que **sólo una familia
   tiene** (parry, cadena del martillo, lunge, barrido, recarga, mecha), o es genérico de una
   **clase física** real (pesadas, largas, filo). Nunca crea una mecánica nueva que quedaría mejor en
   otra arma — eso es lo que volvió inservibles las especiales copiadas de las 6 vanilla.
2. **Números en una clase pura** (patrón `GoldEnchantRules`), handler delgado en eventos (patrón
   `GoldEnchantmentHandler`), registro en `ModEnchantments` con el mismo helper `build(...)`. Todo
   server-side; el cliente nunca decide.
3. **Gate por perfil resuelto, no sólo por ítem** (`WeaponStats.of(stack).profile()`), para que un
   weapon pack que declare un arma "tipo shotel" herede el encantamiento correcto (compat por
   capacidad, DESIGN §8).
4. **Preferir % y efectos sobre daño plano**: el daño flat choca con el filo de vanilla
   (`Sharpness`, que ya es flat y escala), y el multiplicador por timing no existe en este mod.
5. **No duplicar lo que ya existe**: ni los 11 del oro (6 de arma + 5 de herramienta, sólo sobre
   ítems dorados), ni la mecánica de parry del sable (`ParryState` + `ParryHandler`), ni la cadena
   del martillo (`StreakState` + `CombatEngine`), ni el lunge (`LungeDamage`), ni el barrido de la
   alabarda (`HalberdSweep`), ni la recarga del arcabuz (`ArcabuzState`), ni la guardia/aguante
   (`GuardState`/`GuardHandler`).

---

# A) Aprobados — E1 a E7 (sin cambios)

> **Estado: `APROBADO` por el dueño el 2026-10-10.** El texto se conserva tal cual de la v1.

## E1 · Pólvora Seca — identidad del ARCABUZ · `REEMPLAZADO`

> **El dueño lo reemplazó (2026-10-10)**: para el arcabuz se **reutiliza Quick Charge de vanilla**
> en vez de un encantamiento propio — misma jugada que el Knockback de §D: extender lo que ya
> existe antes de inventar. **Cómo**: agregar el arcabuz a los `supported_items` de Quick Charge
> (tag propio + override, §D Vía 1) y **leer el efecto de carga vanilla** en nuestro camino de
> recarga (`ArcabuzState.RELOAD_TICKS`), en vez de escribir un número nuestro. Quick Charge es
> sustractivo (−0,25 s por nivel sobre la carga base de la ballesta); falta **verificar la fórmula
> exacta de 1.21.1** y cómo se mapea sobre nuestros 50 ticks. La recarga deja de ser un
> encantamiento del mod.

- **Niveles**: 2.
- **Efecto**: la recarga baja de **50 → 40 → 30 ticks** (−10 por nivel). La mecha (**20 ticks**)
  **no** cambia: el riesgo de encender sigue costando lo mismo.
- **Items**: los arcabuzes del mod (set directo, patrón de los del oro).
- **Hook**: al iniciar la recarga en `Arcabuz`: leer nivel del stack y usar
  `RELOAD_TICKS - 10×nivel`. Reglas puras en `GunpowderRules` (test JVM pelada).
- **Sinergia**: ninguna de las doradas aplica (el arcabuz no es dorado salvo pack).
- **Conflicto**: ninguno. El más barato de la lista: una constante en un solo call site.

## E2 · Reflejo de Acero — identidad del SHOTEL · `APROBADO`

- **Niveles**: 2.
- **Efecto**: el cooldown del parry baja **100 → 80 → 60 ticks** (5 s → 4 s → 3 s). La
  **ventana (12 ticks) no cambia**: 0,6 s es la lectura que el jugador aprende y "no se puede
  sostener" es diseño, no número.
- **Items**: shotel, cualquier tier. Gate extra: `profile() == SABRE` (un pack "tipo sable" lo
  hereda; una espada jamás).
- **Hook**: donde `ParryState.consume()` cierra la ventana: restar `20×nivel` al cooldown
  asignado. El cálculo puro va a una clase testeable.
- **Sinergia**: **Eco de Oro** (un golpe devuelto con el marcador activo pega un 20 % más).
- **Conflicto**: ojo con el precio de anvil: a 3 s el arma deja de ser "precisión defensiva cara"
  y pasa a ser un bloqueo barato — mantener peso 2 y coste alto.

## E3 · Audacia — identidad de la ROPERA · `APROBADO`

- **Niveles**: 2.
- **Efecto**: el cooldown del lunge baja **20 % por nivel**: base `8.0 × intervalo(10 ticks) = 80
  ticks` → **64 (3,2 s) → 48 (2,4 s)**.
- **Items**: ropera, cualquier tier. Gate: `special() == LUNGE`.
- **Hook**: donde se asigna el cooldown del lunge (server, `Lunge`/`SpecialHandler`).
- **Sinergia**: **Velocidad del Viento** (el hit-and-run de la ropera se refuerza temáticamente).
- **Conflicto**: el lunge ya tiene inmunidad doble (12 ticks + efecto LUNGE); bajar el cooldown
  sube la frecuencia de i-frames — **no tocar los i-frames**, sólo el cooldown, y playtest contra
  mobs con ráfagas.

## E4 · Ritmo de Hierro — identidad del MARTILLO · `APROBADO`

- **Niveles**: 2.
- **Efecto**: la ventana de la cadena pasa de **4 s (80 ticks) → 5 s (100) → 6 s (120)**. El
  escalado por golpe, su piso y su techo **no cambian**.
- **Items**: martillo de guerra, cualquier tier. Gate: `profile() == WARHAMMER`.
- **Hook**: el campo de ventana del attachment `hammer_streak` se setea con el nivel al aplicar.
- **Sinergia**: **Golpe Fantasma** (cadenas largas contra armaduras caras).
- **Conflicto**: el escalado **no tiene tope**; alargar la ventana sube el techo efectivo. Regla
  del proyecto: *si queda fuerte, se cambia con el dato medido* → número a playtestear.

## E5 · Filo Fugaz — identidad de la DAGA · `APROBADO`

- **Niveles**: 2.
- **Efecto**: al conectar un golpe: **Velocidad I durante 40 ticks (L1) / 60 ticks (L2)**. No se
  refresca si ya está activo (evita el loop daga→daga→daga).
- **Items**: daga, cualquier tier. (Regla: cualquier arma cuyo intervalo ≤ 8 ticks, hoy sólo la
  daga a 6.)
- **Hook**: tras resolverse el golpe (server): si no tiene el efecto,
  `addEffect(MOVEMENT_SPEED)`. Cero clases nuevas, cero efectos nuevos.
- **Sinergia**: **Peso Pluma** no choca (baja atributo permanente vs buff temporal).
- **Conflicto**: con **Velocidad del Viento** se suman fuentes de mov.; decidir cap.

## E6 · Segadora — identidad de la ALABARDA · `APROBADO`

- **Niveles**: 2.
- **Efecto**: nuevo efecto **Sangrado**: **0,5 de daño cada 10 ticks durante 60 ticks (3,0 = 1,5
  corazones)** en L1; L2 alarga a **90 ticks (4,5)**. Sin apilado: reaplicar refresca. Sólo lo
  aplica el **barrido** (click derecho), no el golpe normal.
- **Items**: alabarda, cualquier tier. Gate: `special() == MULTI_HIT` (**no** el valor `SWEEP` del
  enum: el barrido de la alabarda es `MULTI_HIT`, y `SWEEP` es el leftover del rework de la pala que
  nada lee — ver `WeaponTables`).
- **Hook**: `MobEffect` propio con `applyEffectTick` (server), aplicado al resolver el sweep.
- **Conflicto / trampa (decisión obligatoria)**: el sangrado **NO** debe disparar la interrupción
  de comer ni el indicador direccional de daño (comer se interrumpiría para siempre = el sistema
  de comida se rompe en silencio). Y **Sweeping Edge de vanilla es un enchante muerto en la
  alabarda** (su barrido es nuestro): excluirlo del set o dejar que Segadora ocupe su lugar.

## E7 · Contragolpe — genérico de GUARDIA (lo que puede cubrir) · `APROBADO`

- **Niveles**: 2.
- **Efecto**: tras **bloquear un golpe de verdad** (aguante gastado), tu siguiente ataque en ≤ 40
  ticks ignora el **25 % (L1) / 50 % (L2)** de la armadura del objetivo.
- **Items**: espadas (vanilla: es la única familia que guarda; las 5 nuevas tienen
  `GuardProfile.NONE`, y el escudo no ataca).
- **Hook**: attachment con flag + tick de expiración, seteado donde `GuardState` consume aguante
  (en `GuardHandler.onShieldBlock`, que ya conoce `attackerId`/`now` vía `GuardStamp`); leído por
  `DamageResolver`. Todo server-side.
- **Sinergia**: es el premio que le falta a la postura: "cubrirse no cuesta hasta que te pegan" ya
  está; esto hace que el bloqueo **abra** una ventana ofensiva (con la guardia baja, la regla
  intacta).
- **Conflicto**: con Golpe Fantasma se acumula al 65 % (1−0,7×0,5). Mutuamente excluyentes o
  tope combinado 60 %.

---

# D) E8 rechazado → reemplazado: extender el Knockback de vanilla

> **Decisión del dueño**: *"ya hay un encantamiento para mayor knockback, simplemente deberíamos
> extender al resto de objetos"*. No se crea **E8 · Contundente**. En su lugar se extiende el
> **Knockback de vanilla** al resto del catálogo, **a nivel de datos**.

## D.1 · Cómo está definido hoy el Knockback (verificado leyendo el jar)

En 1.21.1 los encantamientos son **data-driven**. `data/minecraft/enchantment/knockback.json`:

```json
{
  "anvil_cost": 2,
  "effects": { "minecraft:knockback": [ { "effect": { "type": "minecraft:add",
      "value": { "type": "minecraft:linear", "base": 1.0, "per_level_above_first": 1.0 } } } ] },
  "max_level": 2,
  "slots": [ "mainhand" ],
  "supported_items": "#minecraft:enchantable/sword",
  "weight": 5
}
```

Y el tag apunta a un solo lugar:

```json
// data/minecraft/tags/item/enchantable/sword.json
{ "values": [ "#minecraft:swords" ] }
```

Tres consecuencias que hay que tener presentes **antes** de tocar nada:

1. **`#minecraft:enchantable/sword` es exactamente `#minecraft:swords`.** No es "armas de filo":
   es la lista de espadas.
2. **Nuestras 30 armas ya están en `#minecraft:swords`.** `CortesItemTagProvider.everyWeaponIsABlade()`
   llama `tag(CortesTier.swordsTag())` y `CortesTier.swordsTag()` devuelve `ItemTags.SWORDS`; las
   30 se agregan ahí (y `SwordRebalance` también pregunta ese tag, que es por qué son `SwordItem`).
   ∴ **las 30 ya aceptan Knockback de vanilla hoy**.
3. **El hook ya está cableado.** `CombatEngine.enchantPush` pregunta
   `EnchantmentHelper.modifyKnockback(..., base = 0)`, lo escala por
   `ENCHANT_KNOCKBACK_SCALE = 0.5` y `Knockback.apply(..., enchantPush)` lo suma en **horizontal**.
   ∴ un Knockback I en una ropera/cualquier arma nuestra **ya pega** el medio bloque de empuje, sin
   una línea de código nueva. (`Knockback.apply` lo deja fuera del piso de carga de `TAP_PUSH=0.55`
   a propósito — el encantamiento siempre empuja, es una propiedad del item y no un tap spameado.)

**Conclusión honesta:** la mitad que falta no es "aplicar knockback a nuestras armas" — eso ya pasa.
La mitad que falta es **el resto de objetos**: las armas **vanilla que el mod reworkeó** (`AXE`,
`SHOVEL`, `MACE`, `TRIDENT`, `HOE`) y el **ARCABUZ**, que quedan fuera de `#minecraft:swords`
(el arcabuz, a propósito — ver `ModItems`: *"Deliberately not added to #minecraft:swords"*).

## D.2 · El mecanismo concreto — dos vías

### Vía 1 (recomendada) · tag propio + override del archivo de vanilla

- Crear **`#cortescombat:enchantable/shove`** (nombre a definir) que **incluya**
  `#minecraft:enchantable/sword` y enumere lo que se quiera extender.
- **Overridear** `data/minecraft/enchantment/knockback.json` (un archivo de datos nuestro, en
  `src/main/resources/` o generado) cambiando **una sola línea**: `supported_items` pasa de
  `#minecraft:enchantable/sword` a `#cortescombat:enchantable/shove`. El resto del JSON se copia
  idéntico.
- **Ventaja**: preciso. Sólo cambia a qué ítems el Knockback aplica. **Cero colateral**.
- **Costo/riesgo**: es un **override de archivo entero** de un dato de vanilla. Si Mojang lo
  cambiara en un parche, quedamos con una copia vieja; no cambia dentro de una versión, pero hay
  que anotarlo. Y si otro mod también lo overridea, gana uno.

### Vía 2 (más barata) · agregar los ítems al tag que Knockback ya lee

- Una línea por ítem en `CortesItemTagProvider`, agregando axe/pala/maza/tridente/azada/arcabuz a
  `#minecraft:enchantable/sword`. Los tags **mergean aditivamente**, así que se suma a las espadas.
- **Ventaja**: una línea, sin tocar JSON de vanilla.
- **Costo/riesgo (importante)**: ese tag **no es sólo de Knockback**. `supported_items` de
  **Sweeping Edge** y de **Looting** es el mismo `#minecraft:enchantable/sword`, y `primary_items`
  de **Sharpness** también. Agregar las pesadas/largas ahí les regala **Sweeping Edge** (que es
  justo lo que §E está reconsiderando), **Looting** y las vuelve "primarias" de Sharpness. Eso
  acopla dos decisiones que deberían ser independientes. **Por eso la Vía 1 es mejor**: un tag
  propio evita el colateral.

> **Trampa de datagen**: si se opta por la Vía 1, el tag va en `data/cortescombat/tags/item/...`
> (namespace nuestro) y el override en `data/minecraft/enchantment/knockback.json` (namespace
> vanilla, igual que ya escribimos `data/minecraft/tags/item/swords.json`). El override **no** sale
> de un provider de datagen trivial para encantamientos; hoy es un archivo hand-writen o un
> `JsonCodecProvider` con el codec de `Enchantment`. Decidir en implementación.

## D.3 · A qué familias debería llegar (propuesta con argumento)

**Base que NO se toca**: las espadas de vanilla y **nuestras 30** ya lo tienen. Eso es el "resto"
que el dueño no pidió cambiar (sólo extender).

**A agregar** — el criterio es "el arma cuya identidad **es** empujar":

| Perfil | ¿Recibe Knockback? | Por qué |
|---|---|---|
| `AXE` (hacha) | **Sí** | golpe de tajo pesado; el hacha ya "pega fuerte" y el empuje lo cierra. |
| `SHOVEL` (pala) | **Sí, con decisión de techo** | es **la pala de empujar** (la identidad declarada en `KnockbackProfile`: poner algo al borde de un acantilado). **Ojo**: ya es el empuje más duro del catálogo a `1.365` horizontal; un Knockback II (+1.0) la lleva a ~2.36, por encima del `MAX_HORIZONTAL=1.6`. Necesita un techo o un multiplicador, no una suma. |
| `MACE` (maza) | **Sí** | mazo que lanza; `0.30` horizontal — el empuje encaja. |
| `TRIDENT` (tridente) | **No** | su carga **tira hacia adentro** (`WeaponTables.pullFor(TRIDENT)=1.3`), y "un arrastre hacia adentro y un empuje del encantamiento hacia afuera son instrucciones opuestas" (lo dice `CombatEngine.onHit`). Además una lanza es alcance, no empuje. |
| `HOE` (azada) | **No** | su identidad entera es **el empuje más bajo del juego** (`0.06`, "seis cosas se alejan despacio en vez de salir volando"). Un Knockback la contradice. |
| `PICKAXE` | **No** | no pelea (`WeaponKinds.fights` = false). |
| `ARCABUZ` | **Decisión** | su empuje de `1.10` es el retroceso del **disparo**, no de la bayoneta del click izquierdo. Darle Knockback le pone un empuje al poke. Ninguna de las dos es incorrecta; **queda para el dueño**. |

**Recomendación**: agregar **hacha, pala y maza** (las pesadas), y **dejar fuera tridente, azada y
picota**; resolver el arcabuz aparte. La pala se incluye **sólo si** se decide su techo (porque ya
es el empuje máximo).

## D.4 · Qué hay que verificar en juego

- Que las armas nuevas agregadas aparezcan **en la mesa de encantar y en el yunque** con Knockback
  (y que las 30 lo sigan haciendo).
- Que un Knockback I en una pala **no** la mande fuera de diseño (medir el empuje), y definir el
  techo si se incluye.
- Que la **Vía 2** (si se elige) no haya regalado Sweeping Edge/Looting/Sharpness-primary donde no
  se quería — es el colateral, y hay que verlo explícitamente.
- Que el override del JSON **no** haya roto la línea de tooltip del Knockback vanilla en las
  espadas.

---

# E) Sweeping Edge — el rework actual no convence

## E.1 · Qué hace hoy (`weapon/SweepingEdge.java`)

El crowd charge: **`0.5 × nivel × enemigos`** de daño plano **sobre el objetivo apuntado**, donde
"enemigos" son los cuerpos vivos dentro de **radio 3.0** de la **AABB del objetivo** (contados en
`CombatEngine.crowdAround`). Sigue pegando a **uno solo**; lo que compra el nivel es daño contra el
que apuntaste, creciendo con cuántos otros cuerpos están amontonados alrededor de él.

## E.2 · Por qué probablemente no funciona (los argumentos honestos)

1. **El jugador no controla la variable.** El bonus depende de cuántos mobs haya apretados
   *alrededor del objetivo*, que es una situación (un spawn group, un pasillo) y no una decisión.
   La profundidad de este sistema viene de *elegir arma, momento y lectura del rival*; un número
   que sube por lo que el mundo puso ahí no es una elección.
2. **El nivel es secundario al tamaño de la multitud** — lo admite el propio javadoc de la clase.
   Si "p” nivel importa menos que "cuántos había", el jugador no puede aprender a jugar el
   encantamiento.
3. **No se siente.** El swing sigue siendo contra un objetivo; el daño extra no cambia el
   movimiento, ni la partícula, ni a quién le pegás. Un bonus invisible que no altera la acción
   es un renglón de tooltip, no una mecánica.
4. **Castiga al que pelea en campo abierto y premia al que se queda quieto en un charco de mobs** —
   lo opuesto a la identidad de la espada, que es **guardia y tempo**, no atracción de masas.
5. **Se pisa con la premisa de §C de la v1**: "área" ya la tienen la alabarda (barrido) y la azada
   (6 targets). Un tercer "premia la aglomeración" es una tercera lectura del mismo hecho.

## E.3 · Tres reworks alternativos (sin elegir ganador)

La espada es **la única familia que guarda** (`GuardProfile.SWORD`, 1 punto, 90°): cualquier rework
debería apoyarse en eso o en la carga, y no en la multitud. Niveles 3 en los tres (el vanilla es 3).

### SW-A · **Réplica** — el premio del bloqueo se convierte en un barrido

- **Mecánica**: después de **bloquear un golpe de verdad** (aguante gastado, el mismo evento que ya
  usa E7), tu **siguiente golpe** dentro de **40 ticks** barre **todo el cono frontal** de la
  espada (35°×30°) en vez de un solo objetivo.
- **Números**: cada enemigo extra del barrido recibe **60 % / 80 % / 100 %** del daño del golpe.
  El objetivo apuntado recibe el 100 % siempre.
- **Por qué encaja**: le da a la espada un uso **ofensivo de la postura** (cubrir → riposte), que es
  exactamente la identidad que la familia tiene y que ninguna otra tiene. Convierte "aguantar" en
  "aguantar y contestar".
- **Hook**: un flag con expiración seteado en `GuardHandler.onShieldBlock` (donde ya vive
  `GuardStamp`), leído por `CombatEngine`; el barrido reusa el loop de `extra` que ya existe para
  `WeaponTables.maxTargets`.
- **Conflicto**: solapa con **E7 Contragolpe** (que también se dispara con un bloqueo). O son
  excluyentes, o E7 pierde fuerza. **Es la tensión a decidir.**

### SW-B · **Envite** — el sobrante de un golpe mortal pasa al siguiente

- **Mecánica**: cuando un golpe **mata**, el daño **sobrante** ("overkill") se aplica al enemigo más
  cercano en el cono.
- **Números**: el sobrante que se pasa se **eleva a un tope** del **50 % (L1) / 75 % (L2) / 100 %
  (L3)** del daño **base** del arma. No pasa más que el tope: una espada de hierro no borra un
  grupo.
- **Por qué encaja**: premia **terminar algo** (precisión de filo) y mantiene el swing apuntado a
  un objetivo — lo contrario de "el premio es la multitud". La cadena de muertes es una decisión,
  no un accidente del mundo.
- **Hook**: `CombatEngine.apply` ya calcula `healthBefore` y `notifyHit` ya detecta el kill
  (`killed`); el sobrante se calcula ahí y se aplica al segundo cuerpo con el mismo `AttackResult`
  recortado.
- **Conflicto**: ninguno con los aprobados. Con el martillo (que también "encadena") no comparte
  ítem.

### SW-C · **Barrido Comprometido** — sólo el golpe **cargado** barre

- **Mecánica**: un golpe **a carga completa** barre el cono (la forma vanilla del sweep); un tap
  sigue siendo un solo objetivo. Es la versión vanilla-adyacente pero atada a la **carga**, que es
  la decisión que el proyecto valora.
- **Números**: los enemigos extra reciben **40 % / 60 % / 80 %** del daño del golpe cargado; el
  apuntado el 100 %.
- **Por qué encaja**: le da a la espada una **razón para cargar en una multitud** (hoy la carga de
  la espada es 0.5/25 ticks, y contra uno solo el DPS de cargar pierde), y no toca al que pelea de
  a uno.
- **Hook**: `CombatEngine` — el loop de `extra` gateado por `chargeRatio >= DamageResolver.FULL_CHARGE`
  y el nivel. **Ojo**: hoy la espada tiene `maxTargets == 1`; el `extra` del barrido tendría que ser
  un número **por-encantamiento**, no `maxTargets` del perfil.
- **Conflicto**: con **E7** no choca (uno es bloqueo→armadura, el otro es carga→área).

**Decisión pendiente del dueño**: cuál de las tres (o "ninguna, sacar Sweeping Edge del set"). Nota
transversal: las tres **requieren** que la espada deje de tratar Sweeping Edge como un chante
muerto — hoy el efecto vanilla (`player.sweeping_damage_ratio`) no corre porque el flujo de ataque
es nuestro; cualquier rework mantiene el nombre del encantamiento y le da la mecánica de arriba
desde `CombatEngine`.

---

### E.4 — El veredicto del dueño y los candidatos nuevos (2026-10-10)

**Los tres de §E.3 quedan descartados.** Veredicto textual: *"necesitamos un BUEN REWORK, algo
bien interesante pero que no sea nada más otro AoE pedorro"*. Réplica y Barrido Comprometido son
AoE (daño simultáneo en el cono); Envite no lo es, pero cayó con ellos por venir del mismo molde.
Los reemplazos son tres ejes nuevos, ninguno "pegarle a todo en el arco":

#### SW-D ⭐ **Filo Barrido** — el tajo sigue el recorrido de tu mira (habilidad)

- **Mecánica**: mientras el swing está abierto (el wind-up), **el servidor muestrea la rotación del
  jugador cada tick**. Al soltar: el objetivo apuntado cobra el 100 %, y **cada enemigo que el arco
  de tu mira atravesó** cobra un porcentaje reducido (**40 % / 60 % / 80 %** por nivel). Quieto = un
  solo objetivo, siempre: el multi-target **se gana moviendo la mira a propósito**.
- **Por qué no es "otro AoE"**: no hay un cono que barre; hay **una trayectoria que el jugador
  dibuja con el mouse**. El que no hace el gesto no recibe nada extra.
- **Hook**: **sin paquete nuevo** — el servidor ya ve la rotación (packets de movimiento). Un buffer
  chico de yaw/pitch muestreado durante el hold, y al resolver, cada candidato se testea contra la
  **banda angular recorrida**. Regla pura `SweepPath.covers(...)` testeable en JVM pelada.
- **Contras honestos**: el más caro de los tres (buffer por swing + test por candidato) y **el gesto
  hay que enseñarlo** — el tooltip lo tiene que decir y la partícula del arco barrido es lo que lo
  hace aprendible.
- **Conflictos**: elegir uno entre este y **SW-E** (los dos "alcanzan más de uno"); con **E7** no
  comparte eje.

#### SW-E ⭐ **Envite** — el sobrante de un golpe mortal atraviesa al siguiente (flujo)

- **Mecánica**: cuando un golpe **mata**, el daño **sobrante** (overkill) pasa al enemigo **más
  cercano del cono**, con tope del **50 % / 75 % / 100 %** del daño **base** del arma (una espada de
  hierro no borra un grupo). Un objetivo por vez: **nunca pega a dos a la vez**, encadena.
- **Por qué no es AoE**: es un **flujo a través de una fila**, y **requiere matar** para existir.
  Premia terminar (precisión), no aglomerar.
- **Hook**: el más barato — `CombatEngine.apply` ya calcula `healthBefore` y `notifyHit` ya sabe si
  mató; el sobrante se recorta y se aplica al siguiente.
- **Conflictos**: ninguno con los aprobados.

#### SW-F **Desvío** — el barrido corta lo que vuela (defensa activa)

- **Mecánica**: durante el **strike**, los proyectiles que entran al arco son **desviados**
  (destruidos o rebotados) en vez de acertarte. L1 = flechas y tridentes; L2 = también bolas de
  fuego.
- **Por qué es interesante**: es lo que un espadachín hace y hoy no puede — el escudo bloquea, pero
  **atacar no te protege**. Y es identidad de la espada (duelo), no de la multitud.
- **Contra duro, por eso va tercero**: choca con una decisión cerrada (*"los proyectiles quedan
  fuera del cono"*). No es "pegarle" a un proyectil (es desviarlo), pero **es un cambio de diseño
  que tiene que aprobar el dueño**; además necesita lógica de intercepción y decidir el destino de
  la flecha.
- **Conflictos**: ninguno mecánico; sí de diseño.

**Nota transversal (vale para los tres)**: la espada deja de tratar Sweeping Edge como un chante
muerto — hoy el efecto vanilla (`player.sweeping_damage_ratio`) no corre porque el flujo de ataque
es nuestro; cualquier rework conserva el nombre del encantamiento y le da la mecánica nueva desde
`CombatEngine`.

### E.5 — El rework ELEGIDO por el dueño (2026-10-10): **la onda de filo**

> Veredicto: *"al dar un ataque cargado con la espada, este dispara un espadazo mágico que
> atraviesa enemigos y hace una porción del daño de la espada; conforme sube el nivel el proyectil
> aumenta de tamaño y sube la proporción de daño (Nivel 3 = 80 % del daño cargado)"*. Inspirado en
> SW-D; **SW-D/E/F quedan superados**.

**Por qué es el bueno, en términos del proyecto**: le da a la **carga de la espada** una razón de
ser que hoy no tiene (contra un solo enemigo, cargar pierde en DPS — el diseño ya lo tiene anotado),
y es *capacidad nueva*, no "más daño": el click izquierdo no puede pegar a ocho bloques, la onda sí.

**Números propuestos (a playtestear)**

| Nivel | Daño (de la espada **cargada**) | Ancho del tajo | Alcance | Atraviesa hasta |
|---|---|---|---|---|
| I | **40 %** | 0,7 bloques | 8 bloques | 3 enemigos |
| II | **60 %** | 1,0 bloques | 10 bloques | 4 enemigos |
| III | **80 %** | 1,4 bloques | 12 bloques | 5 enemigos |

- **Disparador**: un swing a **carga completa** (`chargeRatio >= 1.0`) con perfil `SWORD`. El
  **costo es la carga misma** (25 ticks de compromiso) — no paga cooldown extra.
- **Sin doble cobro**: la onda **saltea al objetivo que el golpe de melé ya cobró**. La onda es el
  *seguimiento del tajo más allá del cuerpo* — no un segundo golpe al mismo.
- **No empuja** a todos (o empuja reducido): un push masivo a distancia sería el encantamiento más
  fuerte del juego.
- **Gate**: `profile() == SWORD` + nivel de Sweeping Edge ≥ 1. El encantamiento sigue llamándose
  **Sweeping Edge** (vanilla) y **nosotros le damos la mecánica** — se reemplaza el crowd charge
  actual, que nadie aprende.

### E.6 — Partícula o modelo dedicado: la recomendación

**Partícula, y el modelo sólo si algún día se lo gana.** Las razones, todas verificadas en el repo:

1. **El mod no tiene ni una entidad propia.** Sólo `BlockEntityType`. Crear el primer `EntityType`
   arrastra renderer + modelo JSON + textura + spawn/tracking/NBT + registro de cliente: un
   subsistema entero para un efecto de medio segundo.
2. **El patrón del propio mod para "nuestro ataque a distancia" ya es éste**: el arcabuz no dispara
   un proyectil — el **server resuelve a quién golpeó, manda los ids, y el cliente dibuja**
   (`ArcabuzShotPayload`). La onda es el mismo contrato.
3. **Ya existe la tubería de partículas de tajo**: un solo tipo de partícula, el sprite viaja en el
   payload, tiras de 32×160 (5 frames) cortadas por UV, y `SlashSprite` con índice **cerrado**.
   La onda = **una entrada nueva en ese índice + un PNG**.
4. **El movimiento no necesita geometría**: la onda vive ~10 ticks y viaja. Lo que necesita es
   **resolver a quién toca**, y para eso el proyecto ya tiene el precedente exacto: **el lunge** —
   una caja que se barre tick a tick en el servidor y cobra todo lo que cruza. Misma forma.
5. **Caveat honesto**: las partículas no colisionan. La lógica tiene que vivir en el server (un
   movedor por tick, con raycast contra el terreno para morir en la pared), y la partícula es sólo
   la pintura. Eso ya es así en el lunge y en el arcabuz.

**Costo estimado de la versión partícula**: reglas puras (`SlashWave`: % de daño, ancho, alcance,
tope de perforación por nivel, testeable en JVM pelada) + un ticker del movedor (forma `Lunge`) +
el spawn en `CombatEngine` al soltar a carga completa + el sprite nuevo + una pose de brazo
(reusar `STRIKE` al 100 %; si no alcanza, un canal nuevo es trabajo de cliente, como siempre).

# F) Genéricos nuevos (2, además de E7)

Ejes evaluados: **ágiles** (espada, daga, ropera, sable), **largas** (alabarda, tridente),
**pesadas** (martillo, hacha, maza), **a distancia** (arcabuz). Se eligen **dos**:

- Se **descarta el eje a distancia**: el arcabuz es **una sola arma**, y un genérico de una clase de
  uno es, en los hechos, una identidad. Su desarrollo va en §G.
- Se **descarta el eje ágil**: sus mecánicas deseables (movimiento, velocidad) ya las cubren las
  doradas (`Velocidad del Viento`, `Peso Pluma`) y los aprobados (`Filo Fugaz`), y cualquier cosa de
  **tempo** choca con la **velocidad de ataque dura** y con el margen de i-frames
  (`IFRAME_TICKS=3`, la daga a 6 ticks no tiene lugar para bajar). Lo que queda sin cubrir son
  **largas** y **pesadas**.

## G1 · Ensarte — genérico de armas LARGAS (clase: alabarda, tridente)

- **Niveles**: 2.
- **Efecto**: cuando tu **golpe izquierdo** conecta, la estocada **atraviesa** y aplica daño a **un**
  enemigo alineado por detrás del objetivo: **50 % (L1) / 100 % (L2)** del daño del golpe.
  Sin carga, sin bonus de encantamientos en el segundo cuerpo (es el arma la que atraviesa, no el
  filo).
- **Mecánica que amplifica**: "las armas largas pelean en línea y a distancia" — el objetivo
  apuntado ya recibe todo; esto paga por **estar alineado con un segundo cuerpo**.
- **Hook**: `CombatEngine.landTheHit`, después del objetivo apuntado: reusar el scan de cono
  (`ConeTargets.nearestInCone`) buscando el más alineado **más allá** del objetivo y aplicarle un
  `AttackResult` recortado. Regla pura `EnsarteRules.carry(level, baseDamage)` en JVM pelada.
- **Clase**: gate por perfil `{ HALBERD, TRIDENT }` (no por lista de ids). Un pack "tipo alabarda"
  lo hereda.
- **Conflictos**: en la alabarda, esto es el **click izquierdo** y es **distinto** del barrido del
  click derecho (`MULTI_HIT`, 4 swings). En el tridente es **distinto** de la carga que **tira hacia
  adentro** (`pullFor`). No solapa con ningún aprobado. Con `E7` no comparte ítem.
- **MobEffect nuevo**: no.

## G2 · Quebranto — genérico de armas PESADAS (clase: martillo, hacha, maza)

- **Niveles**: 2.
- **Efecto**: cada golpe que aterriza aplica **Quebranto** por **4 s**: la armadura del objetivo es
  menos efectiva. **L1**: −12 % por stack, **máx 2 stacks** (−24 %); **L2**: −18 % por stack,
  **máx 3** (−54 %... ver aviso). Sin apilado infinito: reaplicar **refresca** el timer y suma al
  tope.
- **Mecánica que amplifica**: las pesadas ya son las que **castigan la armadura** (el hacha la
  destroza al doble por `ArmorWearHandler`, el martillo la ignora a carga completa). Quebranto hace
  que **el grupo** se beneficie de lo que la pesada empezó.
- **Hook**: al aterrizar un golpe (server), aplicar/refrescar un `MobEffect` propio
  **`quebranto`** que lleve un `AttributeModifier` **negativo sobre `Attributes.ARMOR`**
  (`ADD_MULTIPLIED_TOTAL`). El número puro
  (`QuebrantoRules.reduction(level, stacks)`) en clase testeable; el handler sólo aplica el efecto.
- **Clase**: gate por perfil `{ WARHAMMER, AXE, MACE }`.
- **Conflictos**: conceptualmente solapa con **Golpe Fantasma** (dorado, ignora armadura por
  probabilidad) y con el **martillo a carga completa** (ignora armadura del todo). Se **acumulan**
  hoy; hay que decidir exclusividad o tope combinado.
- **MobEffect nuevo**: **sí** (`quebranto`).
- **Aviso de equilibrio**: tocar `Attributes.ARMOR` del objetivo es **intrusivo** y afecta a todo el
  daño que le entre, no sólo al tuyo. El −54 % del L2 a 3 stacks es agresivo; **empezar por −10 %
  por stack y medir**. Es el número más peligroso de todo este documento y va a playtest.

---

# G) Identidad nueva por familia (~3 cada una → ~18)

Cada familia ya tiene **1** de los aprobados (E1–E6). Acá van **3 más** para llegar a **~4 únicos**,
como la espada. Para cada encantamiento: niveles, efecto con números, mecánica que amplifica, hook,
conflictos y si necesita `MobEffect` nuevo. Se marcan las **2 más fuertes** de cada familia (⭐) por
si hay que recortar.

> **Nota transversal**: los 11 del oro sólo aplican a **ítems dorados**. Como los tiers dorados
> existen en **las 5 familias** (una ropera, alabarda, martillo, daga y shotel dorados), un arma
> dorada de nuestra familia puede llevar **una dorada y una nuestra a la vez** — por eso cada
> encantamiento lleva sus conflictos anotados contra las 11.

## G.1 · ROPERA — identidad: el duelo de precisión

### R1 ⭐ · **Duelo**
- **Niveles**: 2. **Efecto**: **+10 % / +20 %** de daño cuando el objetivo apuntado es **el único
  enemigo vivo** en el alcance de tu arma (un duelo). Nada si hay un segundo cuerpo.
- **Amplifica**: la ropera es el arma de "un golpe y salís"; premia honrar su lectura (elegir el
  duelo) en vez de la multitud.
- **Hook**: bonus por objetivo en `enchantBonus` (el inverso de `crowdAround`): un scan de enemigos
  alrededor **del jugador** en el alcance. Regla pura `DueloRules.bonus(level, others)`.
- **Conflictos**: **es lo opuesto de Sweeping Edge** → marcar **excluyente** con la Sweeping Edge
  reworkeada (decisión). Con las doradas no choca.
- **MobEffect nuevo**: no.

### R2 ⭐ · **Quebrar Guardia**
- **Niveles**: 2. **Efecto**: tu estocada **atraviesa la guardia** del defensor: su guardia bloquea
  sólo **60 % (L1) / 30 % (L2)** de tu golpe en vez del 100 %. No es "más daño": es que el arma que
  no puede cubrir **le gana al arma que sólo puede cubrir**.
- **Amplifica**: la ropera es el arma de leer al rival; su debilidad es la espada que guarda (única
  familia que guarda). Esto le da la respuesta específica.
- **Hook**: `GuardHandler.blockedFraction` lee el nivel del arma del **atacante** (ya tiene la
  `DamageSource`) y devuelve la fracción reducida. Server-side.
- **Conflictos**: con **E7 Contragolpe** no choca (uno es tu defensa→ataque, el otro tu ataque→su
  defensa). Con las doradas no. **Sólo importa contra jugadores** (los mobs no usan el sistema de
  guardia) → valor real en PvP, que el diseño trata como secundario. Anotarlo.
- **MobEffect nuevo**: no.

### R3 · **Impulso**
- **Niveles**: 2. **Efecto**: tras terminar un lunge, ganás **Velocidad I** por **30 (L1) / 50 (L2)
  ticks** (el hit-and-run del duelo).
- **Amplifica**: el lunge es su especial; esto cierra "pego y me voy".
- **Hook**: al cerrarse el lunge (`Lunge`/`LungeState`), leer nivel y `addEffect(MOVEMENT_SPEED)`.
- **Conflictos**: suma con **Velocidad del Viento** (dorada) y con `Filo Fugaz` no comparte arma.
  Decidir cap de movimiento.
- **MobEffect nuevo**: no (vanilla Speed).

## G.2 · ALABARDA — identidad: la multitud

### A1 ⭐ · **Cosecha**
- **Niveles**: 2. **Efecto**: cada enemigo que **atrapa el barrido** devuelve **4 (L1) / 8 (L2)
  ticks** del cooldown del barrido, con tope de **20 ticks** devueltos por barrido.
- **Amplifica**: el barrido **sólo conviene desde 5 mobs** (`HalberdSweep`: a 1 mob es 3,5× menos
  DPS). Esto hace que el barrido contra 5+ sea el movimiento correcto, en vez de un lujo.
- **Hook**: en `SpecialHandler.sweep`, después de `AttackCooldown.consume(...)`, leer el nivel y el
  `count` de `targets` y reembolsar con tope. Regla pura `CosechaRules.refund(level, hitCount)`.
- **Conflictos**: ninguno. Con las doradas no.
- **MobEffect nuevo**: no.

### A2 ⭐ · **Bastión**
- **Niveles**: 2. **Efecto**: si el barrido atrapa **≥ 3 enemigos**, ganás **Resistencia I** por 3 s
  (L1); **Resistencia II** por 3 s (L2).
- **Amplifica**: "el arma de área se para en el medio de la gente" — la multitud te da una razón
  para quedarte en ella (hoy sólo te da daño).
- **Hook**: en `SpecialHandler.sweep`, leyendo el `count`. Server-side.
- **Conflictos**: ninguno. Con doradas no.
- **MobEffect nuevo**: no (vanilla Resistance).

### A3 · **Ancla**
- **Niveles**: 2. **Efecto**: un golpe **a carga completa** **fija** al objetivo: **Slowness IV** por
  **15 (L1) / 25 (L2)** ticks. La alabarda carga (0.6/36); esto le da un motivo para hacerlo que no
  sea sólo daño.
- **Amplifica**: arma larga y de control; clava a quien intenta acercarse.
- **Hook**: en `CombatEngine`, en un golpe cargado sobre el objetivo apuntado y los del `extra`.
- **Conflictos**: con la carga de la alabarda (bonus 0.6) no; con el **pull** del tridente no
  comparte arma.
- **MobEffect nuevo**: no (vanilla Slowness).

## G.3 · MARTILLO — identidad: el compromiso y la sentencia

### M1 ⭐ · **Yunque**
- **Niveles**: 2. **Efecto**: el escalado por golpe consecutivo sube de **0.15 → 0.20 (L1) → 0.25
  (L2)**. El piso y la ventana (salvo E4) no cambian.
- **Amplifica**: la cadena **es** la identidad del martillo; esto la hace más profunda (y más
  castigadora, porque el techo sigue **sin tope**).
- **Hook**: `DamageResolver` lee el nivel (junto a `WARHAMMER_STEP`); puro y testeable.
- **Conflictos**: con **E4 Ritmo de Hierro** (ventana) se potencian: las dos juntas suben mucho el
  techo efectivo. Regla del proyecto: medir. Con **Golpe Fantasma** (dorado) se acumulan.
- **MobEffect nuevo**: no.

### M2 ⭐ · **Sentencia**
- **Niveles**: 2. **Efecto**: un golpe **a carga completa** suma **+2 (L1) / +4 (L2)** de daño y,
  **si el objetivo quede por debajo del 25 % de vida, duplica su daño** (ejecución).
- **Amplifica**: compromiso → sentencia. El martillo ya es "el que ignora armadura a carga
  completa"; esto lo vuelve el que **cierra** la pelea.
- **Hook**: bonus por objetivo en `enchantBonus` (el cargado y el umbral de vida se leen ahí).
- **Conflictos**: **fuerte**; medir el umbral (25 % puede ser demasiado) y el apilado con la cadena
  (un streak alto + ejecución puede borrar). Con doradas no comparte ítem salvo martillo dorado.
- **MobEffect nuevo**: no.

### M3 · **Plantado**
- **Niveles**: 2. **Efecto**: mientras tu **cadena ≥ 3**, ganás **Resistencia I (L1) / II (L2)**.
  Si la cadena se corta, se va.
- **Amplifica**: el martillo **no resetea al recibir daño** por diseño; "plantarse" es exactamente
  lo que el arma pide. Te deja seguir la cadena en lugar de retroceder.
- **Hook**: `GoldEnchantmentHandler.onPlayerTick`-style — leer `HAMMER_STREAK` (attachment) + nivel y
  aplicar el efecto (recompute por tick, como las doradas).
- **Conflictos**: con **Bastión** (alabarda) no comparte arma. Con doradas no.
- **MobEffect nuevo**: no.

## G.4 · DAGA — identidad: el asesino del tempo

### D1 ⭐ · **Veneno**
- **Niveles**: 2. **Efecto**: al aterrizar, **15 % (L1) / 30 % (L2)** de probabilidad de aplicar
  **Veneno** (L1: Veneno I 3 s; L2: Veneno I 4 s). El drop es por golpe, no acumulable.
- **Amplifica**: la daga es la más rápida (6 ticks) → la que más oportunidades de drop tiene. El
  veneno castiga al rival que aguanta en vez de salir.
- **Hook**: evento de golpe (server), `addEffect(MobEffects.POISON, ...)`, probabilidad pura.
- **Conflictos**: forma un **clúster de DoTs que interrumpen comer** junto a **Fire Aspect**
  (vanilla) y **E6 Sangrado** (alabarda, no comparte arma). El bug ya anotado en la v1: "comer se
  interrumpe para siempre". Decidir la regla del clúster. Con doradas no comparte ítem salvo daga
  dorada.
- **MobEffect nuevo**: no (vanilla Poison).

### D2 ⭐ · **Cazador**
- **Niveles**: 2. **Efecto**: **+20 % (L1) / +40 % (L2)** de daño cuando golpeás a un objetivo que
  **no te está mirando** (por atrás o de flanco).
- **Amplifica**: la daga es la más fácil de acercar sin ser vista (velocidad + radio alto para
  conectar). Es el asesino.
- **Hook**: bonus por objetivo en `enchantBonus` leyendo el yaw del objetivo contra el vector hacia
  el atacante.
- **Conflictos**: con **E5 Filo Fugaz** se potencian (te movés más rápido para llegar por atrás) —
  deseable. Con doradas no.
- **MobEffect nuevo**: no.

### D3 · **Sanguijuela**
- **Niveles**: 2. **Efecto**: cada golpe que aterriza **cura 0,5 (L1) / 1,0 (L2)** al portador, con
  un tope de **3 curas por segundo** (para que el tempo de la daga no lo convierta en fuente
  infinita).
- **Amplifica**: "muchos golpes débiles" — la daga cura por **cantidad de golpes**, no por daño.
- **Hook**: evento de golpe; contador con ventana de 20 ticks. Regla pura.
- **Conflictos**: **Robo de Vida** (dorada) es un **% del daño**; esto es **flat por golpe** —
  distintos (decisión: ¿se excluyen en una daga dorada?).
- **MobEffect nuevo**: no.

## G.5 · SHOTEL — identidad: la defensa de precisión

### S1 ⭐ · **Desvío Perfecto**
- **Niveles**: 2. **Efecto**: un **parry exitoso** aplica **Debilidad I** 3 s al atacante (L1); L2
  agrega **Lentitud I** 3 s.
- **Amplifica**: el parry es **la** mecánica del sable; hoy devuelve daño y nada más. Esto castiga
  al que insiste.
- **Hook**: `ParryHandler.reflect` (ya tiene el `attacker`). Server-side.
- **Conflictos**: con **E2 Reflejo de Acero** se potencian (parry más seguido + parry que debilita).
  Con doradas no.
- **MobEffect nuevo**: no (vanilla Weakness/Slowness).

### S2 ⭐ · **Corte Gemelo**
- **Niveles**: 2. **Efecto**: el **segundo corte** del cargado doble pega **+25 % (L1) / +50 %
  (L2)**.
- **Amplifica**: el cargado doble es la identidad del sable ("el único que mata en un solo golpe
  comprometido"). Esto profundiza el segundo corte, que hoy pega igual que el primero.
- **Hook**: en `CombatEngine.apply`, el loop de `hits` (i>0) lee el nivel.
- **Conflictos**: fuerte; medir contra mobs de armadura. Con doradas no comparte ítem salvo sable
  dorado.
- **MobEffect nuevo**: no.

### S3 · **Giro**
- **Niveles**: 2. **Efecto**: un parry que **no atrapa nada** (la ventana se cierra sin golpe)
  devuelve **30 % (L1) / 50 % (L2)** del cooldown.
- **Amplifica**: el parry es "riesgo de precisión"; esto paga la **lectura correcta** de un ataque
  que no llegó.
- **Hook**: al cerrarse la ventana de `ParryState` sin `consume()`, leer nivel y reembolsar cooldown.
- **Conflictos**: con **E2** da un parry barato (cooldown bajo + reembolso) → cuidar que no se vuelva
  sostenible. **Decisión**.
- **MobEffect nuevo**: no.

## G.6 · ARCABUZ — identidad: la pólvora negra

### B1 ⭐ · **Pólvora Negra**
- **Niveles**: 2. **Efecto**: el disparo incendia **9 s (L1) / 12 s (L2)** en vez de los 6 s.
- **Amplifica**: el fuego es la mitad del daño del disparo (`ArcabuzShot.FIRE_SECONDS`, 1 de daño
  cada 20 ticks, source vanilla `onFire`). Alarga el quemado sin tocar el tope de 10 targets (que
  es deliberado).
- **Hook**: `Arcabuz.fire` lee el nivel y pasa `igniteForSeconds(FIRE_SECONDS + delta)`.
- **Conflictos**: `FIRE_RESISTANCE` lo cancela (deseable). Con doradas no.
- **MobEffect nuevo**: no (fuego vanilla).

### B2 ⭐ · **Mecha Corta**
- **Niveles**: 2. **Efecto**: la mecha baja de **20 → 16 (L1) → 12 (L2)** ticks (el disparo sale
  antes).
- **Amplifica**: 20 ticks de pie quieto es **el costo** del disparo; esto lo acorta, con el mismo
  razonamiento que E1 para la recarga.
- **Hook**: `ArcabuzState.FUSE_TICKS` hecho dependiente del nivel (regla pura) leído en `light()`.
- **Conflictos**: **TRAMPA VERIFICABLE**: `arcabuz.png` es una tira de 5 frames de 64×64 y su
  `.mcmeta` gasta **5 ticks por frame = exactamente 20**, y `ModelDeclarationTest` lo afirma contra
  `FUSE_TICKS`. **Acortar la mecha desincroniza la animación de quema** (el último frame no se ve, o
  el `lit` se corta antes de terminar). Necesita **remapeo de frames en el cliente** o dejar el
  visual como está. Marcar como "requiere trabajo de cliente".
- **MobEffect nuevo**: no.

### B3 · **Culata**
- **Niveles**: 2. **Efecto**: la **bayoneta** (click izquierdo) pega **+20 % (L1) / +40 % (L2)** y,
  a L2, aplica **Lentitud I** 2 s.
- **Amplifica**: el arcabuz tiene dos botones y el izquierdo es "se me acabó la pólvora, uso la
  bayoneta". Esto le da una razón de existir al poke.
- **Hook**: en `CombatEngine`, gate por `profile() == ARCABUZ` (o por la existencia del `shotReach`).
- **Conflictos**: ninguno.
- **MobEffect nuevo**: no (vanilla Slowness).

## G.7 · Resumen de identidad

| Familia | Aprobado (v1) | Nuevos | Total único |
|---|---|---|---|
| Ropera | E3 Audacia | Duelo ⭐, Quebrar Guardia ⭐, Impulso | 4 |
| Alabarda | E6 Segadora | Cosecha ⭐, Bastión ⭐, Ancla | 4 |
| Martillo | E4 Ritmo de Hierro | Yunque ⭐, Sentencia ⭐, Plantado | 4 |
| Daga | E5 Filo Fugaz | Veneno ⭐, Cazador ⭐, Sanguijuela | 4 |
| Shotel | E2 Reflejo de Acero | Desvío Perfecto ⭐, Corte Gemelo ⭐, Giro | 4 |
| Arcabuz | E1 Pólvora Seca | Pólvora Negra ⭐, Mecha Corta ⭐, Culata | 4 |

**MobEffects nuevos requeridos**: **Sangrado** (E6, ya previsto) y **Quebranto** (G2). *Todo el
resto usa efectos vanilla* (Speed, Slowness, Weakness, Resistance, Poison) o no usa ninguno. Eso es
deliberado: cada `MobEffect` nuevo es una factura de mantenimiento y un síntoma posible en el HUD.

---

# B) Identidad vs genérico (tabla actualizada)

| Encantamiento | Clase | Por qué |
|---|---|---|
| Pólvora Seca, Pólvora Negra, Mecha Corta, Culata | **Identidad** (arcabuz) | amplifican mecha/recarga/disparo/bayoneta, mecánicas que sólo él tiene |
| Reflejo de Acero, Desvío Perfecto, Corte Gemelo, Giro | **Identidad** (shotel) | parry y cargado doble, propios del sable |
| Audacia, Duelo, Quebrar Guardia, Impulso | **Identidad** (ropera) | lunge y duelo de precisión |
| Ritmo de Hierro, Yunque, Sentencia, Plantado | **Identidad** (martillo) | la cadena, que es su identidad |
| Segadora, Cosecha, Bastión, Ancla | **Identidad** (alabarda) | barrido y multitud |
| Filo Fugaz, Veneno, Cazador, Sanguijuela | **Identidad** (daga) | el tempo y el asesinato |
| Contragolpe | **Genérico de guardia** | cualquier arma que pueda cubrir (hoy: espadas) |
| Ensarte | **Genérico de largas** | alabarda, tridente |
| Quebranto | **Genérico de pesadas** | martillo, hacha, maza |

**La regla**: identidad = "ya existe una mecánica en este perfil y el encantamiento la empuja".
Genérico = "existe una clase física de arma y el encantamiento aplica a la clase". Ninguno crea una
mecánica que un arma diferente querría más que la suya — ése es el bug que ya se pagó con las
especiales copiadas.

---

# C) Ideas de ALTO riesgo — para descartar explícitamente

- **C1 · "Guardia Ofensiva" (golpear con la guardia arriba).** Rompe la decisión cerrada de la
  postura: un arma que cubre **y** pega gratis es estrictamente mejor y desactiva la lectura de
  aguante del rival. **Vetado por diseño.**
- **C2 · "Espejo de Acero" (el bloqueo devuelve daño).** Con bloqueo al 100 % y 3 golpes de
  aguante, castiga al atacante **por atacar**: en PvP los dos se quedan mirando con la guardia
  arriba. Diluye al shotel, que es *el* "devolver daño" del juego. (Nota: esto es sobre la
  **guardia**; **ampliar el parry** del shotel — S1/S2/S3 — es otra cosa y sí es identidad.)
- **C3 · "Carga Fantasma" (entrar ya cargado al 50 %).** Borra la única decisión que queda
  ("¿ataco ya o espero 0,6 s más?"); reintroducir profundidad vía timing está vetado por nombre.
- **C4 · "Alcance Extendido" (+0,5 m por nivel).** Un alcance modificado obliga al HUD del cono
  (`ConeGauge`) a releer el nivel del stack; si se olvida **el indicador miente**. Vetado salvo que
  el HUD lea el nivel — y aun así, es la peor clase de número mentiroso.
- **C5 · Cualquier encantamiento de tempo** (`+attack speed`, `-cooldown` de swing). Choca con la
  **velocidad de ataque dura** y con el margen de i-frames (`IFRAME_TICKS=3`; la daga a 6 ticks no
  tiene dónde bajar sin que los golpes se traguen solos). Vetado.
- **C6 · "Contundente" (knockback nuevo).** **Rechazado por el dueño**; reemplazado por extender el
  Knockback de vanilla (§D). No volver a proponer un encantamiento de knockback nuevo.
- **C7 · Ejecución garantizada (matar por debajo de un umbral sin más).** M2 Sentencia usa "duplica
  el daño por debajo del 25 %", que es una ejecución **con contrajuego** (hay que cargar, hay que
  acercarse). Una ejecución **incondicional** sería un botón de borrar y queda vetada.

---

# H) Orden de implementación sugerido (el más barato primero)

| # | Qué | Por qué en esta posición |
|---|---|---|
| 1 | **La ronda de los VANILLA** (§J + §D) — **orden explícito del dueño: antes que cualquier custom** | sacar las 30 de `#swords`, tags propios por clase, override de los 7 JSON (Sharpness, Smite, Bane, Knockback, Looting, Fire Aspect, Sweeping Edge), Knockback extendido a hacha/pala/maza/martillo, Fire Aspect **a pala/maza/martillo**, durabilidad explícita. Casi sin código Java nuevo |
| 2 | **Quick Charge en el arcabuz** (reemplaza a E1) | tag + override + leer el efecto vanilla en nuestro camino de recarga |
| 3 | **Reflejo de Acero** (E2) | dos constantes de `ParryState`; reglas puras testeables |
| 4 | **Audacia** (E3) | mismo shape que E2 |
| 5 | **Filo Fugaz** (E5) | `MobEffect` vanilla existente; handler ~10 líneas |
| 6 | **Contragolpe** (E7) | attachment con flag+expiración; alto valor para la postura |
| 7 | **Ritmo de Hierro** (E4) | barato, pero alarga el techo sin tope del martillo → playtest medido |
| 8 | **Sweeping Edge** (§E, rework elegido) | toca `CombatEngine`; decidir cuál de las 3 antes |
| 9 | **Los genéricos** (Ensarte, Quebranto) | Ensarte es hook simple; Quebranto exige `MobEffect` nuevo + atributo en el enemigo |
| 10 | **Segadora** (E6) | el más caro de los aprobados: `MobEffect` nuevo + decisión DoT vs comer |
| 11 | **Las 18 de identidad** | por familia, empezando por las baratas (% de daño / efectos vanilla) y dejando para el final las que tocan el cliente (**Mecha Corta**) |

---

# I) Decisiones que quedan para el dueño

1. **Knockback (§D)**: ¿Vía 1 (tag propio + override del JSON) o Vía 2 (agregar al tag vanilla)?
   ¿Qué perfiles: sólo hacha/pala/maza, o también el arcabuz? Y si entra la **pala**, ¿cuál es su
   techo (ya es el empuje máximo del juego)?
2. **Sweeping Edge (§E)**: ¿Réplica, Envite, Barrido Comprometido, o sacarlo del set? Si es
   **Réplica**, ¿cómo conviven con **E7 Contragolpe** (los dos se disparan al bloquear)?
3. **Genéricos (§F)**: ¿confirmás **Ensarte + Quebranto**? Quebranto toca `Attributes.ARMOR` del
   enemigo — ¿es aceptable que un encantamiento aplique un debuff a la armadura del rival?
4. **Identidad (§G)**: ¿se implementan las **18**, o se recorta a las **2 más fuertes por familia**
   (las ⭐) para llegar a 3 únicos? ¿Alguna familia con menos (la daga tiene "menos" diseño propio)?
5. **Clúster de DoTs**: Sangrado (E6) + Veneno (D1) + Fire Aspect (vanilla) — **tres** fuentes que
   interrumpen comer. ¿Regla: sólo una activa, o tope combinado, o aceptar que comer se interrumpe?
6. **Sets excluyentes**: **Duelo** (ropera) vs **Sweeping Edge** (espada); **Quebranto** vs **Golpe
   Fantasma** (dorado) y vs el martillo a carga completa; **Sanguijuela** vs **Robo de Vida**
   (dorada). ¿Excluyentes por tabla, o se apilan con tope?
7. **Peso y treasure**: la v1 proponía 2 para identidad y 2–3 para genéricos, con **`treasure`** en
   Segadora y Contragolpe. ¿Se mantiene? ¿Mecha Corta y Quebranto también `treasure` (son fuertes)?

---

# J) La herencia de las espadas — qué reciben nuestras armas hoy

**El problema que el dueño destapó:** las 30 armas nuestras están TODAS en `#minecraft:swords`
(`CortesItemTagProvider.everyWeaponIsABlade()`), así que heredan **todo** el set de espada. Un
martillo con Sweeping Edge es una locura, y una daga que saquea como una espada no es una daga.

### J.1 - Lo verificado leyendo el jar (`neoforge-21.1.250-client-extra`)

`#minecraft:enchantable/sword` = `#minecraft:swords`, y de ahí para arriba:

| Encantamiento | Se habilita por | Efecto vanilla | ¿Corre bajo nuestro flujo? | Dónde |
|---|---|---|---|---|
| **Sharpness** | `enchantable/sharp_weapon` (= `#swords` + `#axes`) | +0,5 a +2,5 de daño por nivel | **Sí** | `CombatEngine.enchantBonus` vía `EnchantmentHelper.modifyDamage` |
| **Smite** | `enchantable/weapon` (= sharp_weapon + mace) | +2,5/nivel vs no-muertos | **Sí**, por objetivo | ídem (per-target, que es lo correcto) |
| **Bane of Arthropods** | `enchantable/weapon` | +2,5/nivel vs artrópodos + lentitud | **Sí**, por objetivo | ídem |
| **Knockback** | `enchantable/sword` | empuje +1/nivel (nosotros lo escalamos ×0,5) | **Sí** | `CombatEngine:648` |
| **Fire Aspect** | `enchantable/fire_aspect` (= `#swords` + mace) | prende fuego al objetivo | **Sí** | `EnchantmentHelper.doPostAttackEffects` en `apply` |
| **Looting** | `enchantable/sword` | +drops de mob | **Sí**, vanilla | sin código nuestro (el camino de drops lee el arma del damage source) |
| **Sweeping Edge** | `enchantable/sword` | vanilla: muerto acá; **nuestro** crowd charge | **Sí, el nuestro** | `sweepingEdgeLevel` + `SweepingEdge.bonus` |
| **Unbreaking / Mending** | `enchantable/durability` (incluye `#swords`) | durabilidad / repara con XP | **Sí**, vanilla | `hurtAndBreak` |
| **Vanishing Curse** | `enchantable/vanishing` → durability | desaparece al morir | Sí, vanilla | — |

**Lo que NO heredan** (bien): Density/Breach/Wind Burst (maza), Impaling/Channeling/Riptide/Loyalty
(tridente), Flame/Power/Punch/Infinity (arco), Piercing/Multishot/Quick Charge (ballesta), armadura,
minería. Es decir, el problema **no** es enorme: son las **siete** de la tabla.

### J.2 - El mecanismo para discriminar

No se puede discriminar sin dejar de usar el tag de vanilla, porque el tag **es** la decisión:

1. **Sacar las armas de `#minecraft:swords`** (hoy las mete `everyWeaponIsABlade()`, que pasa a
   construir tags **propios**).
2. **Tags propios por clase** en `data/cortescombat/tags/item/enchantable/<clase>.json`:
   p. ej. `blade` (espada/daga/ropera/sable/alabarda), `heavy` (martillo), `precision` (daga/ropera),
   `loot` (daga/espada), `shove` (hacha/pala/maza/martillo).
3. **Override de los JSON de vanilla** que queremos restringir
   (`data/minecraft/enchantment/{sharpness,smite,bane_of_arthropods,knockback,looting,fire_aspect,sweeping_edge}.json`)
   apuntando `supported_items` a **nuestros** tags. Es la Vía 1 de §D, generalizada.
4. **Unbreaking/Mending/Vanishing**: agregar las armas a `#minecraft:durability` (hoy entran por
   `#swords`; si salen de ahí, se caen de durability y hay que ponerlas explícitamente).

⚠️ **Radio de explosión** (por eso esto es una ronda propia y con playtest): `#minecraft:swords`
también responde "¿esto es una espada?" para otros mods, la mesa de encantamientos y el yunque.
Verificar que sacar las 30 no rompa nada de eso.

### J.3 - Matriz propuesta (a decidir por el dueño)

| Encantamiento | Llega hoy a | Propuesta | Razón |
|---|---|---|---|
| **Sharpness** | las 30 | **sólo filos** (espada, daga, ropera, sable, alabarda) | vanilla ya excluye la maza de Sharpness; el martillo es contundente y su canal es Smite/Bane |
| **Smite / Bane** | las 30 | **todas** | vanilla las da a todo `enchantable/weapon`; "contra criaturas" no rompe identidad |
| **Knockback** | las 30 | **hacha/pala/maza/martillo** (lo de §D) y **fuera de daga/ropera/sable** | empujar lejos es de lo pesado; una daga que empuja no es una daga |
| **Looting** | las 30 | **daga + espada** | saquear es de filo/asesino; la alabarda no saquea mejor |
| **Fire Aspect** | las 30 | **contundentes: pala, maza, martillo** — **al revés de vanilla** (que lo da a espada y maza) | **decisión del dueño**: los filos ya pegan muchísimo con Sharpness y no necesitan un DoT encima; y el quemado le da identidad propia a lo contundente |
| **Sweeping Edge** | las 30 | **sólo la espada** | su rework (a decidir, §E) es identidad de la espada; la alabarda ya barre con su `MULTI_HIT` y duplicaría |
| **Unbreaking / Mending / Vanishing** | las 30 | **todas** | durabilidad, no combate |

### J.4 - Arreglo aplicado en esta ronda (aprobado por el dueño)

`archetype: polearm` declaraba `SpecialType.SWEEP`, que **ningún handler mira** (la alabarda corre
`MULTI_HIT`, y `WeaponTables` ya lo decía al pasar por el miembro del enum). Un pack con `polearm`
recibía un click derecho muerto, en silencio. Corregido a `MULTI_HIT` en `ArchetypeSpec`, el javadoc
del arquetipo, y `docs/armas-custom.md` (que **documentaba `sweep` como valor válido**).
`PackResolverTest` afirmaba `SWEEP`: reescrito para afirmar **la invariante** (el especial del
arquetipo == `WeaponTables.special(perfil)`), sabotaje verificado. `SpecialType.SWEEP` queda
**huérfano** (candidato a borrar o a alias; se serializa por id, no por ordinal).

---

## Notas de arquitectura y trampas detectadas al escribir la v2

- **Registro**: reusar `ModEnchantments.build(...)`. Los 11 del oro son un set directo por nombre
  (`golden` en el path) + la espada y el hacha doradas de vanilla; nuestras familias nuevas no son
  doradas salvo el tier `GOLDEN`, así que su set se arma filtrando `ModItems.weapons()` por
  `WeaponFamily`/perfil, no por nombre.
- **Nuestros encantamientos son registro Java, no dato.** A diferencia del Knockback (que es
  `data/minecraft/enchantment/knockback.json`), los de este mod se registran con
  `DeferredRegister<Enchantment>` y `HolderSet.direct(...)`. ∴ **no** llevan `exclusive_set`
  declarativo gratis: los conflictos 5/6/7 de arriba hay que resolverlos en código o con el gate de
  la mesa, no con un JSON. Anotarlo.
- **Interacciones vanilla ya posibles en las 30**: Sharpness, Knockback, **Looting**, Fire Aspect,
  Unbreaking, Sweeping Edge (las 30 están en `#minecraft:swords`; ver §D.1). Decidir: (a) Sweeping
  Edge muerta en la alabarda; (b) Fire Aspect + Veneno + Segadora = DoTs que interrumpen comer;
  (c) nuestros usan % o efectos para no competir con el flat de Sharpness.
- **Los gates son por capacidad/perfil, nunca por lista de ids** (`WeaponKinds.fights`,
  `stats.profile()`, `stats.special()`). Un pack que declare un arma "tipo shotel" hereda sus
  encantamientos.
- **Todo server-side**: si algún día se quiere hint de HUD, va por payload, nunca por evento de
  cliente.
- **Testing**: cada encantamiento deja su clase de reglas pura (patrón `GoldEnchantRules`) con
  guards verificados mordiendo; handlers sin lógica, sólo orquestación. **Ningún tipo de Minecraft
  en `src/test`** (la línea es "¿implementa algo de Minecraft?", no "¿está en el paquete de red?").
- **Trampa del arcabuz (`Mecha Corta`)**: `FUSE_TICKS` está atado al `.mcmeta` (5 ticks/frame × 4
  frames = 20) y `ModelDeclarationTest` lo afirma. Cualquier cambio de mecha arrastra al cliente.
- **Trampa del tag (§D Vía 2)**: `#minecraft:enchantable/sword` alimenta Knockback **y** Sweeping
  Edge **y** Looting, y es `primary_items` de Sharpness. Extenderlo no es "extender Knockback": es
  extender cuatro cosas.

**Síntesis de la v2**: **E1–E7 aprobados** se conservan; **E8 se reemplaza** por extender el
Knockback de vanilla (que, verificado, ya cubre nuestras 30 — falta el resto de objetos); **Sweeping
Edge** queda con tres reworks a elección; **2 genéricos nuevos** (Ensarte, Quebranto) para las
clases larga y pesada; **18 de identidad** nuevas para que cada familia llegue a ~4 únicos. Todo
amplifica mecánicas que **ya existen**, y nada toca la carga-nunca-resta, la guardia-postura, la
autoridad del servidor ni la velocidad de ataque dura.
