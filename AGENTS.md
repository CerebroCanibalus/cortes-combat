# AGENTS.md — Cortés Combat

Mod de NeoForge **1.21.1** que unifica y reworksa el combate de Minecraft. Sin animaciones custom, sin sistemas sobrecomplicados. Prioridad de diseño: **intuitivo para cualquiera, con profundidad que aparece al aprenderlo**.

> Este archivo es a la vez la referencia técnica y la memoria del proyecto. En Windows el filesystem es case-insensitive, así que `agents.md` y `AGENTS.md` son el mismo archivo: mantener todo en uno solo. El diseño de combate vive aparte, en `DESIGN.md`.

---

# PARTE 1 — Referencia técnica

## Estructura

```
.                  → el mod (git propio, rama main)
DESIGN.md          → qué hace el juego (diseño, cerrado v6)
ARCHITECTURE.md    → cómo está construido (propuesta, pendiente de aprobación)
refs/              → clones upstream, SOLO LECTURA, gitignored
  combatify/           Fabric, MC 26.1.2, Java 25  (port de Combat Test 8c)
  rafts_combat/        NeoForge, MC 1.21.1         (alcance horizontal + ataque con carga)
  neoforge-damageoverlay/  NeoForge, MC 1.21.1    (viñeta roja de vida baja + flash de daño)
```

`refs/` está en `.gitignore` a propósito: son clones con su propio `.git`. **Nunca los commitees ni los edites.** Para actualizar: `git -C refs/<repo> pull`.

## Reglas duras

- **Target: NeoForge 1.21.1 / Java 21.** No subir a versiones nuevas. Combatify está en MC 26.1.2 con Java 25 y APIs que no existen en 1.21.1: **cualquier idea que tomes de `refs/combatify` hay que traducirla a las APIs de 1.21.1, no copiarla.**
- **Mod id: `cortescombat`.** Paquete base: `com.cortes.combat`.
- **`mods.toml` va en `src/main/templates/META-INF/neoforge.mods.toml`** y se expande con `generateModMetadata` (task `ProcessResources` + `neoForge.ideSyncTask`). Nunca escribirlo a mano en `src/main/resources`.
- **Cada mixin nuevo va en `src/main/resources/cortescombat.mixins.json`** y además en el bloque `[[mixins]]` de `mods.toml`. Olvidarse de la segunda línea = el mixin no se carga en producción, solo en dev.
- `neoforge.enabledGameTestNamespaces` está fijado al `mod_id` en los run configs. Tests nuevos deben usar ese namespace o no corren.
- Red: todo payload es un `record` que implementa `CustomPacketPayload` con un `Type` (`ResourceLocation.fromNamespaceAndPath(MOD_ID, "nombre")`) y un `StreamCodec` estático. Registro centralizado, un archivo por payload. No usar `FriendlyByteBuf` a mano.
- Config: `ModConfigSpec` con `BUILDER.define*` y `SPEC` declarado **al final** de la clase. Tipos `CLIENT` y `SERVER` registrados por separado desde el constructor `@Mod`.

## Comandos

Build base (MDG `net.neoforged.moddev`, Gradle wrapper 9.x):

```
gradlew build               # compila + jar
gradlew runClient           # cliente de dev
gradlew runServer           # servidor de dev
gradlew runGameTestServer   # corre los gametests y sale
gradlew runData             # datagen → src/generated/resources
```

`gradlew` sin `./` — es Windows. El proyecto usa `org.gradle.configuration-cache=true`; si tocás un input de build y falla con error de cache, corré con `--no-configuration-cache` para diagnóstico.

Entorno: JDK instalado es **25**. El toolchain pide **21**, y el `foojay-resolver-convention` en `settings.gradle` lo descarga solo. Si el toolchain falla, el problema es la resolución, no el código. NeoForge 21.1.250 ya está en el cache de Gradle local.

## Las tres referencias

**`refs/combatify`** — el más rico, y el más difícil de portar. Mod de library/standalone que implementa Combat Test 8c. Lo valioso:
- `src/main/java/net/atlas/combatify/config/` — 13 clases de config: `KnockbackMode`, `ArmourPiercingMode`, `AttackDecay`, `ProjectileDamage`. Buen catálogo de las palancas que existen.
- `component/custom/ExtendedBlockingData.java` y `util/blocking/` — sistema de bloqueo extensible (tipos de bloqueo, condiciones, efectos post-bloqueo). Sustituto del escudo vanilla.
- `mixin/` — 40+ de servidor, 25 de cliente. El índice de *dónde* el combate se toca de verdad.
- `src/main/resources/resourcepacks/` — ~15 packs que parchean datos de items vía la librería `defaulted`. Casi todo replicable con `data/` en vez de código Java.
- Depende de `atlas-core` y `defaulted` (del mismo autor, solo Fabric). **No las vamos a usar.** Reimplementar la idea, no la dependencia.
- Sus `README.md` tienen la lista de cambios de combate más concreta que vas a encontrar. Leerlo antes de diseñar.

**`refs/rafts_combat`** — el más cercano a lo que vamos a hacer. NeoForge 1.21.1, mixins, payloads, config partida cliente/servidor. Ataques con fase de carga obligatoria: raycasts horizontales progresivos que crecen al mantener el click, área que golpea todo lo que atraviesa (no solo el primer enemigo), indicador de rango en HUD que se pone rojo si hay un target. Buenos detalles: con escudo en alto la carga sigue sin apretar el click; se cancela cambiando de item en la hotbar; tamed/mounts no son golpeables; el knockback no aplica bajo 60% de carga. Limitaciones declaradas: desactiva Sweeping Edge a propósito, y cancela comida/pociones/bows durante la carga.

**`refs/neoforge-damageoverlay`** — 4 clases, se lee entero en un minuto. El patrón a copiar: `LayeredDraw.Layer` registrada en `RegisterGuiLayersEvent` con `event.registerBelowAll(rl("health"), layer)` para que quede *debajo* de la salud. `@Mod(value = MODID, dist = Dist.CLIENT)`, `ModConfig.Type.COMMON`, `ConfigurationScreen::new` como extension point. La viñeta usa `blit` de textura tiled con `setColor` para el alpha; el flash usa `guiGraphics.fill` con tinte rojo. Dos cosas que hace bien y hay que imitar: el fade es interpolación exponencial contra `deltaTracker.getRealtimeDeltaTicks()`, y guarda `lastKnownHealth` para detectar el delta en vez de suscribirse a eventos de daño.

## Bugs / trampas de entorno

- `git clone` en PowerShell devuelve exit code 1 y escribe a stderr `Cloning into '.'...` aunque el clone funcione. No es un error real: verificar con `git log` antes de reaccionar.
- Combatify tiene **dos** bloques `loom {}` en su `build.gradle`; el segundo sobreescribe `accessWidenerPath` del primero. Solo aplica `combatify.classtweaker`; el `combatify.accesswidener` del primero es código muerto. No copiar esa duplicación.
- Escribir `agents.md` en la raíz **pisa `AGENTS.md`** (case-insensitive en Windows). Por eso todo vive en un solo archivo.

### Trampas del build (las 4 que costaron tiempo)

- **La expansión de `mods.toml` es Groovy.** Un backtick en los comentarios del template rompe el parser con `SimpleTemplateScript1.groovy: Unexpected input '('` — un error que apunta lejos de la causa. Dejar los comentarios del template en texto plano.
- **Toda propiedad usada en `mods.toml` tiene que declararse** en el mapa `replaceProperties` de `generateModMetadata` en `build.gradle`. Falta una y falla con `Missing property (x) for Groovy template expansion`.
- **`gradle.properties` tiene que ser ASCII puro.** Gradle lo lee como ISO-8859-1. Con un acento literal, el `displayName` del mod se shippea corrupto (`CortǸs Combat`). Usar el escape de Java: `mod_name=Cort\u00E9s Combat`.
- **`filteringCharset = 'UTF-8'`** está puesto en `generateModMetadata` para escribir el TOML en UTF-8. Sin eso la salida usa el charset de la plataforma.

Verificado que el esqueleto compila (`gradlew build`) y que el mod **carga en un servidor real** (`gradlew runServer`, mod coordinates encontrados, log `CortesCombat/]: Cortés Combat loading`, cero errores de mixin).

### Trampas de la API de 1.21.1 (verificadas con `javap`)

Ninguna de estas se adivina, y varias están en versión **distinta** a 1.21.2+:

- **Data components:** el registro es `DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MODID)`. **No** existe `Registry.DATA_COMPONENT_REGISTRY`.
- **`StreamCodec.fromCodec(codec)` NO existe en 1.21.1.** El que existe es **`ByteBufCodecs.fromCodec(codec)`**. `StreamCodec.fromCodec` llegó después.
- **`Mth.clamp` siempre toma 3 argumentos** `(valor, min, max)`. No hay versión de un solo argumento.
- **Enum que implementa `StringRepresentable` tiene que implementar `getSerializedName()`**, no alcanza con tener un `id()`.
- **`AttachmentType.builder(Supplier<T>)`** quiere un supplier: `() -> ChargeState.EMPTY`, no la referencia al campo `ChargeState::EMPTY`.
- Los sources de vanilla **no están** en el jar de NeoForge ni en el de FML. Para verificar un símbolo de vanilla hay que usar `javap` contra el jar compilado que deja ModDevGradle en `~/.gradle/caches/neoformruntime/intermediate_results/compiledWithNeoForge_*_output.jar`.
- **`SubscribeEvent` es `net.neoforged.bus.api.SubscribeEvent`**, no `net.neoforged.neoforge.eventbus.api`. Ese último paquete no existe.
- **`ItemStack.forEachModifier` toma un `EquipmentSlot` o `EquipmentSlotGroup`**, no un `Holder<Attribute>`. Para sacar los modificadores de un item hay que pasar el slot y filtrar el atributo adentro del consumer. Para el intervalo de ataque ni hace falta: `LivingEntity.getAttributeValue(Attributes.ATTACK_SPEED)` ya incluye el item en la mano, el equipo y las pociones.

---

# PARTE 2 — Memoria del proyecto

## Fase activa: 3 — Vertical slice

Estado: diseño cerrado, arquitectura aprobada, esqueleto compilando y verificado en servidor. Siguiente: paso 2 del plan (registros) y paso 3 (`reach/` con tests).

## Decisiones tomadas

- **`refs/` dentro del repo pero gitignored.** Son clones de terceros con su propio historial git. Meterlos al repo del mod los contaminaría y los volvería commits nuestros.
- **Raíz = repo del mod, rama `main`, git nuevo.** El clon de Combatify que estaba en la raíz se movió a `refs/combatify` con su `.git` intacto.
- **No usar `atlas-core` ni `defaulted`.** Dependencias de Combatify, solo Fabric, no existen para NeoForge. Si necesitamos su funcionalidad (patching de componentes de item vía datapack), se reimplementa.
- **Toolchain Java 21 aunque el JDK del sistema sea 25.** 1.21.1 corre en 21; 25 solo lo necesita Combatify.
- **Un solo módulo**, con el paquete de librería separado como subpaquete, extraíble después sin romper nada.
- **Compat por capacidad, no por nombre de mod.** Ver sección 8 de `DESIGN.md`.

## Mecánicas acordadas con el usuario

- **Velocidad de ataque dura:** no se puede atacar a medio cooldown, y el daño es plano (se elimina el multiplicador de timing del vanilla, que premia spamear más rápido).
- **Carga** = bonus de daño encima del base. Nunca resta.
- **Dos indicadores separados:** el vanilla bajo la crosshair muestra **carga**; barra horizontal nueva bajo la hotbar muestra **cooldown**.
- **Autoataque carga y dispara:** mantener el click hace las dos cosas.
- **Click derecho = movimiento especial por arma.** Tiene que hacer algo que el click izquierdo no puede, no más daño.
- **Alcance en cono frontal fijo** (mismo ángulo a 1m que a 4m), con distancia, ángulo horizontal y vertical independientes por arma.
- **Escudo:** 100% de reducción excepto explosiones, aguante de **3 golpes**, arco frontal solamente. Agotarse produce stun. El hacha stunea de un golpe.
- **Aguante en HUD:** 3 esferitas tenues sobre la crosshair, visibles al llevar escudo.
- **Supervivencia: la comida deja de ser energía.** Se elimina el sistema de **exhaustion**: correr, picar y golpear no cuestan nada. La comida es **recurso de curación con ciclo cerrado**: se gasta al curar y se regenera sola. Regen 1 hp cada 2s, cura hasta 6 de comida, comer se interrumpe al recibir daño (la regen pasiva no), comida líquida 20 ticks. Pociones y manzana dorada rebalanceadas (curación I = 6, II = 12, manzana = 8).
- **Crafteo:** 5 grids canónicos, forma única por arma. Tiers **desiguales**: daga 7 (única con madera), ropera/alabarda/shotel 6, martillo 5 (sin madera ni piedra). **30 items total.**
- **Set de cobre propio** (pepita, texturas, armas). Se reemplaza por el de Mojang al actualizar a la versión con copper age. Resolvió la "pepita de la punta" de la ropera.
- **Shotel = sable musulmán**, hoja curva. NO es un cañón.
- **Cañón de mano:** anotado como arma a distancia **fuera de alcance** (`DESIGN.md` 6ter). Choca con la decisión de que los proyectiles quedan fuera del cono.
- **5 armas nuevas:** ropera, alabarda, martillo de guerra, daga, shotel. Solo alabarda y shotel tienen click derecho. Solo alabarda, martillo y shotel cargan.
- **Aguante unificada escudo/espada:** la aguante solo se gasta al **recibir** un golpe bloqueado, nunca por mantener la guardia. De ahí sale, sin caso especial, que la espada pueda cubrirse indefinidamente gratis.
- **Martillo de guerra:** escalado por golpe consecutivo, ventana 4s, sin tope. Golpe cargado ignora armadura.
- **Shotel:** parry con cooldown 5s que devuelve **solo el golpe parriado**, sin búfer acumulado. Carga da doble.
- **Feedback:** patrón de damageoverlay + indicador direccional de daño (no existe en ninguno de los 3 mods).

## Arquitectura (propuesta, `ARCHITECTURE.md`)

- **Todo el estado mutable de combate va en attachments**, nunca en campos de mixin. `AttachType` verificado en NeoForge 21.1.250: tiene serialización, sync y `copyOnDeath`.
- **El cliente nunca decide.** Predice para dibujar, manda intención; el servidor resuelve. `CHARGE` se predice, `COOLDOWN` y `PARRY_READY` son del servidor.
- **Ningún número de gameplay hardcodeado.** 4 data components: `weapon_profile`, `reach_profile`, `charge_profile`, `special`. Separados para que un mod externo cambie el alcance sin romper la carga.
- **`canCharge = false` no es un caso especial** en el código: el sistema consulta el componente. La ropera y la daga no cargan sin una sola rama.
- **Se reemplaza el flujo de ataque vanilla entero.** El vanilla hace 4 cosas que el diseño no quiere (incluido `getAttackStrengthScale`). Parchear las 4 sería 4 mixins acoplados.
- **Tenemos PRIORIDAD sobre mods de combate.** No cedemos el turno: hay **blocklist** de mods incompatibles, chequeada con `ModList` al iniciar. Si se detecta uno, el mod **se niega a cargar** con un mensaje que nombre el mod — **no es un crash con stack trace** (un crash parece bug nuestro y no dice qué hacer). La blocklist arranca vacía y se llena con reportes; a diferencia de una compat list, no se pudre.
- **Convertimos armas ajenas pero nos negamos a pelear con sistemas ajenos.** Esas dos cosas no se contradicen.
- **Orden de 15 pasos por dependencia, no por dificultad.** `reach/` va antes que el flujo de ataque porque es geometría pura y testeable. `FoodData` al final, en commit propio.
- **Vetado:** compat list de mods en el código (sí se permite la **blocklist** de incompatibles, que es otra cosa: se niega a correr, no integra), bus de eventos propio, lógica de gameplay en cliente, `static mutable` para gameplay, mixins que reescriban media clase, archivo único de config autogenerada, ruido aleatorio por frame en el shake, crash con stack trace por incompatibilidad.

### Conversión de armas de otros mods (`ARCHITECTURE.md` 12)

- **Resolver en 4 capas por prioridad:** data components → override del jugador → **derivación automática** → default. El override siempre gana.
- **La derivación automática ES la compat.** Lee `ItemAttributeModifiers` del item + perfil por tag + escala alcance por velocidad. Un mod de armas con stats razonables entra **sin una línea de código nuestro**.
- **Config procedural en DOS archivos:** `generated_weapons.json` (lo escribe el mod, no editar) y `weapon_overrides.json` (el jugador). Uno solo regeneraría y pisaría los cambios del jugador.
- **Va en JSON plano, no en `ModConfigSpec`.** No se pudo verificar si `ModConfigSpec` soporta mapas (sources de FML no están en el cache local) y un archivo autogenerado necesita precedencia explícita. `ModConfigSpec` queda para los números de balance estáticos.

### Framework de viewmodel (`ARCHITECTURE.md` 11)

- **El framework es código, los perfiles son datos.** Machinery una vez, un perfil declarativo por arma, un solo mixin.
- Composición **aditiva** por capas: `vanilla + perfil + shake + fov`. Cada capa desactivable por config (mareo).
- Curvas de Penter propias (`back`, `expo`, `elastic`, `bounce`): `Mth` solo trae lineal/cuadrática/cúbica y no alcanzan para un golpe con peso.
- **Shake determinista sembrado por golpe.** Aleatorio por frame = el mismo golpe se ve distinto cada vez, y se siente como ruido gráfico, no como animación.

## Decisiones de alcance (qué queda fuera)

- **Proyectiles** (arco, ballesta, tridente lanzado): fuera del cono, comportamiento actual.
- **Mobs:** sin cambios, siguen con el sistema vanilla. Candidato número uno a revisión después del vertical slice, empezando por el zombi.

## Trampa conocida

El autoataque que carga y dispara deja el ritmo en manos del jugador, lo que consume una de las fuentes de profundidad (el timing del golpe). La profundidad restante tiene que venir de: elección de arma, momento del click derecho, lectura de la aguante del rival, y gestión de la propia aguante. No reintroducir profundidad con más timing.

## Fase 1: esqueleto — HECHA

Esqueleto NeoForge 1.21.1 creado en la raíz y **verificado**: `gradlew build` compila, el jar sale bien formado, y `gradlew runServer` levanta un servidor donde el mod carga sin errores.

- `mod_id=cortescombat`, paquete `com.cortes.combat`, grupo `com.cortes.combat`
- `neo_version=21.1.250` (la que está en el cache local), Gradle wrapper 9.2.1
- `cortescombat.mixins.json` creado y **registrado** en `mods.toml` (el bloque `[[mixins]]` ya está)
- `build.sh` copia el jar a `test-server/mods/`; `test.sh` delega a Gradle

## Fase 2: registros — HECHA

Verificado en servidor real: `components=4 attachments=5`.

- **4 data components** (`ModComponents`): `weapon_profile` (enum de 12 perfiles), `reach_profile` (cono), `charge_profile` (canCharge + bonus), `special` (tipo de click derecho).
- **5 attachments** (`ModAttachments`): `charge`, `guard_stamina`, `cooldown`, `hammer_streak`, `parry`. Todos serializados; los de combate con `copyOnDeath`.
- **5 records de estado** en `state/`, cada uno con su `Codec` y su lógica de tick. La lógica de negocio ya vive adentro del estado, no en los listeners.
- Los ítems y tags quedan para el paso 9 (armas + datagen). No se registraron ítems de prueba.

## Fase 3: `reach/` — HECHA

El check de cono, con **21 tests unitarios verdes**. Es la clase más testeable del proyecto y va primera a propósito: si el cono está mal, todo lo que se construya encima hereda el error.

- **`reach/ConeMath.java`** — geometría en `double` puro, **sin un solo tipo de Minecraft en la firma**. Corre en JVM pelado, sin mundo ni bootstrap. Por eso el test es JUnit y no gametest.
- **`reach/Reach.java`** — adaptador finísimo que extrae posición y look de las entidades y calcula el punto más cercano de la AABB del objetivo. Toda la lógica está en `ConeMath`.
- **Los dos límites se miden en marcos distintos, a propósito:**
  - **Horizontal**: ángulo respecto de la proyección en el suelo de la mirada. Algo directamente atrás da 180° y falla sin caso especial.
  - **Vertical**: diferencia entre la elevación mundial del objetivo y la de la mirada. Esto se mantiene bien definido en los polos: al mirar recto arriba, ambas elevaciones son 90 y un objetivo recto arriba es *dead ahead*, no 90° de desvío. Medirlo contra el eje `upLocal` degeneraba ahí y rechazaba justo lo que el jugador está mirando.

**Bug real que encontraron los tests:** la primera versión medía el ángulo vertical contra el eje local `up` y fallaba al mirar recto arriba (2 tests en rojo). Los tests atraparon una degeneración matemática real, no un problema del test. La corrección está comentada en el código.

- `build.gradle`: JUnit 5 agregado, `useJUnitPlatform()`. Comment en el build advierte que `ConeMath` **no** puede ganar tipos de Minecraft o los tests dejan de funcionar.
- **Los enums (`WeaponProfile`, `SpecialType`) NO implementan `StringRepresentable`.** Es una clase de Minecraft: implementarla hace que cualquier cosa que sostenga el enum necesite el juego en el classpath para inicializarse, y los tests unitarios mueren con `NoClassDefFoundError`. Usan un `Codec` escrito a mano.
- **DataFixerUpper va en `testRuntimeOnly`.** Cualquier clase con un `Codec` estático lo necesita. Sin eso los tests dieben en `<clinit>` con un error que parece un test roto y no una dependencia faltante.

## Fase 4: cooldown duro — HECHA

**35 tests verdes** (21 de `ConeMath` + 14 de `AttackTiming`). Mod carga en servidor sin errores.

- **`attack/AttackTiming.java`** — matemática pura: intervalo desde attack speed, multiplicador de carga, spread. Sin tipos de Minecraft, testeable en JVM pelado.
- **`attack/WeaponStats.java`** — el resolver de 4 capas. Our components → override (paso 12, todavía cae al default) → **derivación automática por tag vanilla** → default. Es la capa de compat: un arma de otro mod entra por tag sin una línea de código nuestro.
- **`attack/CombatTicker.java`** — **un solo handler** avanza los 5 estados por tick, solo en servidor. Uno y no cinco: el orden entre ellos es una regla y las reglas que no están escritas en un mismo lugar dejan de ser ciertas en cuanto alguien agrega un sexto listener.
- **`attack/AttackCooldown.java`** — la puerta: `isReady` / `consume` / `beginCharge` / `endCharge`.

**Segundo bug real que encontraron los tests:** `chargeMultiplier` con bonus negativo devolvía 0.5 en vez de 1.0, contradiciendo la invariante de "la carga nunca resta". El piso ahora se fuerza **en el código**, no se confía en el dato, porque un override de config puede escribir cualquier bonus y esa invariante es demasiado importante para dejarla en manos de quien edite ese archivo.

Defaults de alcance y carga de los 12 perfiles ya están en `WeaponStats` (tabla de la sección 2 de `DESIGN.md`). La de la sable carga al doble (`bonus = 1.0`), la única que lo hace.

## Fase 5: reemplazo del flujo de ataque — HECHA

**52 tests verdes.** El mixin aplicó limpio contra la clase real (`Mixing PlayerAttackMixin ... into net.minecraft.world.entity.player.Player`, sin `Mixin apply failed`).

- **`mixin/PlayerAttackMixin.java`** — `@Inject` en HEAD de `Player#attack(Entity)`, `cancellable`. Cancelar en HEAD significa que **el vanilla nunca corre**: ni su cálculo de daño, ni su raycast a la crosshair, ni su sweep. Eso es el reemplazo, no un parche.
- **`attack/CombatEngine.java`** — el flujo entero en un método. Orden: gate de cooldown → stats del arma → check de cono → resolver daño → aplicar → consumir cooldown. **Un swing que falla igual cuesta el cooldown y gasta la carga**: si no, mantener el click sería daño gratis spameando al aire.
- **`attack/DamageResolver.java`** — la fórmula de daño, pura y testeable. `AttackResult` la devuelve.
- **`attack/Knockback.java`** — escalado por carga, con piso del 60%. Debajo del piso no hay knockback: ese es el fix del shove-spam.
- **`PlayerAttackMixin` va en `cortescombat.mixins.json` Y en el `[[mixins]]` de `mods.toml`.** Ambos verificados en el log.

**Pendiente consciously:** el ignore de armadura del martillo **no** se aplica todavía. `DamageSource` pide un `Holder<DamageType>` y eso exige un damage type registrado por datapack, que es trabajo del paso de especiales. El flag `ignoredArmour` se calcula y se testea, pero la fuente de daño es la normal. Está anotado en el código.

**Tercer bug real, y el más instructive:** los tests de `DamageResolver` morían con `NoClassDefFoundError`. La causa: `WeaponProfile` y `SpecialType` implementaban `StringRepresentable`, una clase **de Minecraft**, así que cualquier cosa que sostuviera esos enums necesitaba el juego en el classpath para inicializarse. Violé la propia regla que escribí en el paso 3. Arreglé la **causa**, no el síntoma: los dos enums ahora usan un `Codec` escrito a mano y siguen siendo Java puro. `DamageFixerUpper` igual se agrega al test runtime porque los records con `Codec` sí lo necesitan.

## Fase 6: carga cableada al input — HECHA

**52 tests verdes, mod carga en servidor sin errores.** Esto ya es jugable: click izquierdo = swing, mantener = carga.

- **`net/ChargeInputPayload`** (C2S, un solo bool) — el botón bajó o subió. **Solo dos paquetes por golpe**, no un stream: el servidor acumula en su propio tick mientras el flag está activo.
- **`net/CombatStatePayload`** (S2C, dos floats) — `chargeRatio` y `cooldownRatio`, los dos valores que el HUD necesita.
- **`client/ChargeInputHandler`** — el cliente **solo reporta intención**. Predice localmente para que el indicador sea instantáneo y le avisa al servidor; el servidor acumula solo y es lo único que cuenta para el daño. `acceptServerState` corrige hacia el valor del servidor sin saltar para atrás, así que un paquete atrasado no teletransporta la barra.
- **`client/CortesCombatClient`** — `@Mod(dist = Dist.CLIENT)` separado. `InputEvent` no existe en servidor dedicado, así que referenciarlo desde código común rompe la carga ahí. NeoForge permite varias clases `@Mod` con el mismo id si difieren en `dist`.

**Anti-cheat del input:** el servidor **ignora un "down" si ya está apretando**. Un cliente que spamee el paquete no acumula carga más rápido que mantenerlo honestamente. Está en `handleChargeInput` con el comentario explicando por qué.

**Sync con bandwidth:** enviar cada tick a cada jugador serían 20 paquetes/segundo para una barra que casi no cambia. Solo se manda si el valor se movió más de 0.01. Eso obligó a guardar el último valor enviado... en un `static Map`, **que es exactamente lo que la arquitectura veta**. Se limpia en `PlayerLoggedOutEvent` y está comentado como deuda: si crece, el fix es un attachment, no otro listener.

## Fase 7: HUD + harness de testeo — HECHA

**52 tests verdes, mod carga en servidor, `/cc` registrado (91 comandos raíz).**

### HUD (3 capas, orden verificado contra `VanillaGuiLayers`)

| Capa | Registro | Qué hace |
|---|---|---|
| `DamageOverlayLayer` | `registerBelowAll` | Viñeta de vida baja + flash de daño. **Debajo de la salud** a propósito: la viñeta tapa la vida, que es justo lo que advierte. |
| `CooldownBarLayer` | `registerAbove(HOTBAR)` | Barra de cooldown. Verde cuando podés atacar. |
| `GuardOrbsLayer` | `registerAbove(CROSSHAIR)` | 3 esferitas de aguante, solo si llevás algo que bloquea. |

La barra de cooldown usa **exponential approach contra `getRealtimeDeltaTicks()`**, no interpolación lineal: la lineal se ve stutter con framerate irregular. Mismo truco que damageoverlay. Y se dibuja del valor **del servidor**, no de la predicción del cliente: si la barra está llena, podés pegarle. La carga vive en el indicador vanilla, así que los dos nunca compiten por la atención.

### Harness: comando `/cc` (requiere permissão 2)

```
/cc weapons        → te da 11 armas vanilla de una, en la hotbar
/cc weapon <item>  → cualquier item, con autocompletado
/cc shield         → escudo en la mano
/cc fullguard      → restaura la aguante
/cc nofood         → comida a 2, para probar que la regen se frena
/cc speed <ticks>  → velocidad para alcanzar objetivos rápido
/cc teleport <n>   → te teletransporta n bloques hacia adelante
/cc stats          → IMPRIME las stats resueltas del arma en mano
```

`/cc stats` es el más importante: imprime perfil, alcance (distance + ángulos), cooldown, carga y especial. Si la derivación automática produce algo raro, se ve ahí en vez de adivinarlo en un combate.

**Trampa de verificación que casi me hace reportar un bug falso:** leí un `server-out.log` que parecía limpio, pero el build **había fallado** y no arrancó servidor. Un log vacío de errores no prueba que todo ande — hay que mirar que el build pasó Y que hay `Done (Xs)`. Verificar en el orden correcto.

## Texturas: PROTOTIPO, HAY QUE REEMPLAZARLAS

**Las texturas en `assets/cortescombat/textures/gui/` son copiadas de los mods de `refs/` y son de prueba. Ninguna puede ir en una publicación.**

| Origen | Licencia | Riesgo |
|---|---|---|
| `neoforge-damageoverlay` (la viñeta) | **All Rights Reserved** | **Alto.** No hay `LICENSE` y el autor no concedió permiso. |
| `combatify` (los sprites de indicador) | LGPL-2.1 | Medio: o se reemplazan, o el mod queda atado a LGPL. |
| `rafts_combat` | MIT | Bajo: se puede copiar con atribución. |

El detalle está en `textures/gui/PROTOTYPE-ASSETS.md`, archivo por archivo. El plan es que el usuario reemplace cada PNG con su propio dibujo manteniendo **las mismas rutas y dimensiones**, así que ningún código de render cambia.

## Próximos pasos

1. Paso 8: aguante escudo/espada (la lógica está en `GuardState`, falta input de bloqueo + damage reduction real).
2. Paso 9: las 30 armas + datagen + recetas.
3. Paso 10: especiales (alabarda multi-hit, parry de la sable) + damage type de ignore de armadura.
4. Paso 11: supervivencia (`FoodData`) — el cambio más caro, en commit propio.
5. Números: definir el escalado del martillo en playtest (`WARHAMMER_STEP` está en 0.15 como punto de partida).
6. Verificar contra las fuentes de 1.21.1 los umbrales internos de `FoodData` antes de escribir el mixin.

## Trampas de diseño marcadas

- El autoataque carga y dispara → el ritmo ya es del jugador. La profundidad tiene que venir de arma, click derecho, lectura de aguante y gestión de aguante. **No reintroducir profundidad metiendo más timing.**
- El martillo **no resetea** al recibir daño, por decisión. Es un arma de compromiso. Si en el playtest resulta demasiado fuerte, se cambia con el dato medido, no por intuición.
- Cubrirse no tiene coste. Si el escudo termina siendo inútil porque todos cubren gratis con la espada, el problema es la densidad de aguante, no el click derecho.
- **`foodRegenInterval` es el número más importante del proyecto.** Rápido → la comida es decorativa. Lento → pelear es un callejón sin salida, que es justo lo que el mod viene a arreglar. Solo playtest lo resuelve.
- **`FoodData` es vanilla puro, sin patch de NeoForge** (verificado). Los umbrales internos hay que leerlos de las fuentes de 1.21.1, no de memoria.

## Changelog

- Setup inicial: 3 clones en `refs/`, repo del mod en la raíz, instrucciones escritas.
- `DESIGN.md` v1 redactado con las mecánicas acordadas.
- v2: las 6 preguntas de estructura resueltas por el usuario. Cambios: indicadores separados (vanilla = carga, barra nueva = cooldown), cono fijo, autoataque carga y dispara, aguante como 3 esferitas sobre la crosshair, proyectiles y mobs fuera de alcance.
- Tabla de armas nuevas borrada por ser invención sin respaldo del usuario.
- v3: catálogo definido por el usuario (ropera, alabarda, martillo, daga, shotel). Consecuencias: aguante unificada escudo/espada, click derecho pasó a ser excepción, 3 de 5 armas no cargan.
- v4: capa visual (viewmodel por arma, partículas, FOV, sonido mixto). Verificado que 1.21.1 no tiene animaciones de item → todo viewmodel es mixin. Verificado el orden de render del HUD.
- v5: crafteo y supervivencia. La comida deja de ser energía: se elimina exhaustion, queda recurso de curación autociclico. Recetas con forma única por arma + ingredient extra en madera y piedra.
- v6: grids canónicos de las 5 armas (los del usuario, no los inventados). Tiers desiguales → 30 items. Set de cobre propio. **Shotel corregido: es el sable musulmán, no un cañón** — el error fue mío, inventé el cañón porque el usuario nunca nombró las armas. Cañón de mano anotado fuera de alcance.
- `ARCHITECTURE.md`: estado por attachments, datos por data components, reemplazo del flujo de ataque vanilla, plan de 15 pasos. Pendiente de aprobación.
- Framework de viewmodel (capas aditivas, easing, shake determinista) y **conversión automática de armas de otros mods** con config procedural en dos archivos.
- **Arquitectura aprobada** con un cambio: prioridad sobre mods de combate vía blocklist, y el mod se niega a cargar con mensaje accionable en vez de ceder el turno.
- **Fase 1 (esqueleto) completa y verificada.** Build OK, jar OK, mod carga en `runServer` sin errores. Cuatro trampas del build documentadas (Groovy en el template, `replaceProperties`, ASCII en `gradle.properties`, `filteringCharset`).
- **Fase 2 (registros) completa y verificada en servidor**: 4 data components + 5 attachments, confirmado con un log temporal `components=4 attachments=5`. Seis trampas de API de 1.21.1 documentadas, todas verificadas con `javap` contra el jar compilado.
- **Fase 3 (`reach/`) completa**: 21 tests JUnit verdes. `ConeMath` es geometría en `double` puro, sin tipos de Minecraft en la firma, para que los tests corran sin levantar el juego. Los tests encontraron un bug real de degeneración matemática al mirar recto arriba.
- **Fase 4 (cooldown duro) completa**: 35 tests verdes. Resolver de 4 capas con derivación automática por tag (la compat), un solo `CombatTicker` para los 5 estados, y `AttackCooldown` como puerta. Segundo bug real encontrado por tests: el multiplicador de carga con bonus negativo reducía el daño; el piso ahora se fuerza en código.
- **Fase 5 (reemplazo del flujo de ataque) completa**: 52 tests verdes, mixin aplica limpio contra `Player`. **El vanilla ya no corre en el ataque.** Tercer bug: los enums implementaban `StringRepresentable` (clase de Minecraft) y rompían los tests con `NoClassDefFoundError`; ahora usan un `Codec` propio y siguen siendo Java puro.
- **Fase 6 (carga cableada al input) completa**: es jugable — click = swing, mantener = carga. Un payload C2S con un bool (no un stream), el cliente predice y el servidor valida, clase `@Mod` client-only separada porque `InputEvent` no existe en servidor.
- **Fase 7 (HUD + harness) completa**: 3 capas GUI registradas en el orden correcto, y comando `/cc` para testear armas sin correr a un cofre. `/cc stats` imprime las stats resueltas del arma en mano.
- **⚠️ DEUDA LEGAL: las texturas del HUD son de prototipo, copiadas de `refs/`.** La viñeta viene de `neoforge-damageoverlay`, que es **All Rights Reserved**. Los sprites de indicador vienen de `combatify` (LGPL-2.1). **Ninguna puede ir en una publicación.** Ver `textures/gui/PROTOTYPE-ASSETS.md` para el detalle archivo por archivo. El usuario va a reemplazarlas con su propio arte manteniendo rutas y dimensiones.
