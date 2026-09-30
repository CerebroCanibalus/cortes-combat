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
- **Cada mixin nuevo va en `src/main/resources/cortescombat.mixins.json`.** El bloque `[[mixins]]` de `mods.toml` nombra el **archivo de config**, no cada mixin: es una sola línea `config="${mod_id}.mixins.json"` y no hay que tocarla nunca. (La versión anterior de esta regla pedía dos lugares y el segundo no existe; costó una búsqueda inútil confirmarlo.)
- **Queda UN solo mixin: `MinecraftAttackMixin`.** `PlayerAttackMixin` se borró cuando el ataque pasó a resolverse desde un payload.
- `neoforge.enabledGameTestNamespaces` está fijado al `mod_id` en los run configs. Tests nuevos deben usar ese namespace o no corren.
- Red: todo payload es un `record` que implementa `CustomPacketPayload` con un `Type` (`ResourceLocation.fromNamespaceAndPath(MOD_ID, "nombre")`) y un `StreamCodec` estático. Registro centralizado, un archivo por payload. No usar `FriendlyByteBuf` a mano. **Y el record necesita el `@Override type()`**: tener el campo `TYPE` no alcanza, la interfaz lo pide explícitamente.
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
- **`Vec3` no tiene constructor de 2 componentes en 1.21.1.** `new Vec3(x, y)` no compila; usá un `double[]` para pares o `Vec3.atCenterOf(AABB)`.
- **`Player#attack` desreferencia el target de una** (offset 9 del bytecode, sin null-check). No hay forma de mandar un ataque al aire por esa vía: por eso existe `WhiffPayload`.
- **`ServerPlayer` NO overridea `stopUsingItem`.** El servidor no puede obligar al cliente a soltar un item: la animación de uso se predice del lado cliente y no viaja por la red. Hay que hacerlo en el cliente.
- **`ByteBufCodecs.VOID` no existe.** Para un payload sin campos: `StreamCodec.unit(instancia)`, que no escribe nada.
- **`ATTACK_SPEED` base de un jugador es 4.0**, no 0. `AttackTiming.UNARMED_INTERVAL` es un fallback para entidades sin atributo, no el caso de las manos desnudas.
- **Aritmética del HUD: las clases que implementan `LayeredDraw.Layer` no se pueden testear en JVM pelada.** Fallan al cargar, antes de la primera aserción. `ConeGauge` existe separada del layer por eso.
- **`StreamCodec.composite` arranca en 1 componente** (aridades 1 a 6, verificado). Para un payload de 0 campos: `StreamCodec.unit(instancia)`. Para enums: `ByteBufCodecs.idMapper(IntFunction, ToIntFunction)`.
- **El viewmodel se engancha con `RenderHandEvent`, no con un mixin.** Lo dispara `ClientHooks.renderSpecificFirstPersonHand(hand, pose, buffer, light, partialTick, pitch, swingProgress, equipProgress, stack)`, que `ItemInHandRenderer.renderHandsWithItems` llama **antes** de `renderArmWithItem`. Orden real, verificado con `javap`: `bobbing de vista → [nosotros] → transform del brazo → transform del modelo`. O sea que nuestro transform **envuelve** todo lo de vanilla en vez de ser pisado por él. `ItemInHandRenderer` en 1.21.1 **no tiene** `getItemTransform`, y `ItemProperties` no lo referencia ninguna clase de `client/` salvo `ItemOverrides`: el `display.transform` viene horneado en el modelo.
- **`InputConstants.GLFW_PRESS` no existe.** Es `org.lwjgl.glfw.GLFW.GLFW_PRESS`.
- **`com.mojang.serialization.codecs.StringCodec` no existe** en el DFU que trae 1.21.1. Usar `Codec.STRING.xmap(...)`, que está en todas las versiones.
- **El cuerpo constant-specific de un enum no puede leer un campo de instancia.** `BACK("1.7") { ... overshoot ... }` no compila: el cuerpo no es un contexto de instancia. Con un solo valor hace falta un `private static final`.
- **Un record no puede tener un método que compite con un accessor de componente.** `amplitude()` en un record con un componente `amplitude` es "invalid accessor method". Renombrar el método o el componente.
- **`Axis.rotationDegrees(float)` aloca un `Quaternionf` nuevo en cada llamada.** Para el viewmodel hay que reusar uno scratch con `identity().rotateX(..).rotateY(..).rotateZ(..)`: `mulPose` lo lee antes de volver, así que una sola instancia compartida es segura en el hilo de render.
- **`Entity.isRemoved()` existe y es público final** en 1.21.1 (no `hasBeenRemoved`). `EntityTypeTest` vive en `net.minecraft.world.level.entity`, no en `net.minecraft.world.entity`.
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

Estado: diseño cerrado, arquitectura aprobada, pasos 1 a 8 del plan hechos y verificados en
cliente real, un reworks completo del HUD, el click unificado (que destapó que el golpe
cargado no existía) y la librería de viewmodel con el primer perfil real. **201 tests
verdes.** Siguiente: ajustar el viewmodel de la espada en playtest y después el paso 9
(las 30 armas + datagen + recetas).

Lo que queda del vertical slice y en qué orden:

| # | Paso | Estado |
|---|---|---|
| 1-8 | esqueleto, registros, `reach/`, cooldown duro, flujo de ataque, carga, HUD, aguante | HECHOS |
| 14 | framework de viewmodel | **HECHO** (librería + perfil de espada; falta tercera persona, FOV, sonido, `CHARGED`) |
| 9 | las 30 armas + datagen + recetas + set de cobre | **SIGUIENTE** |
| 10 | especiales: multi-hit de alabarda, parry de sable, damage type de ignore-armadura | pendiente |
| 11 | supervivencia (`FoodData`): quitar exhaustion, curación de ciclo cerrado | pendiente |
| 15 | compat / conversión de armas de otros mods | pendiente |

Pendiente de una sesión, con diagnóstico hecho: **la optimización del cono del cliente ya
está hecha** (ver "Optimización del cono" más abajo). Ya no queda ahí.

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

## Texturas: DOS SON PROTOTIPO, EL RESTO YA ES ARTE PROPIO

Las de `assets/cortescombat/textures/gui/sprites/hud/` son **arte original del usuario**, ya
subido: `crosshair`, `crosshair_aim`, `crosshair_hit`, `cooldown_corchetes`, `charge_bar`,
`charge_bar_emtpy`. `hotbar_attack_indicator_full.png` (el icono de espada de `combatify`) se
borró: el cooldown ya no se dibuja con un sprite sino con código alrededor de la crosshair.

**Las dos que quedan y siguen siendo de `refs/`:**

| Archivo | Origen | Licencia | Riesgo |
|---|---|---|---|
| `sprites/hud/crosshair_shield_indicator_{full,disabled}.png` | `combatify` | LGPL-2.1 | Medio: o se reemplazan, o el mod queda atado a LGPL |
| `damage_overlay.png` | `neoforge-damageoverlay` | **All Rights Reserved** | **Alto.** No hay `LICENSE` y el autor no concedió permiso |

El detalle está en `textures/gui/PROTOTYPE-ASSETS.md`. Para reemplazarlas: **misma ruta y mismo
tamaño en píxeles**, así que no cambia código de render.

## Reworks del HUD (todo sobre playtest del usuario)

Ocho commits, `2c2814e` a `632cf5a`. Lo que se cambió y **por qué**, porque las razones son lo
que evita volver a cometer los errores:

- **Crosshair de tres estados** con los sprites del usuario, crecimiento en código y **shake sembrado por evento** (ciclo fijo de 8 pasos rotado por un contador, no aleatorio por frame: el mismo golpe tiene que verse igual cada vez).
- **La carga bajó debajo de la crosshair**, con hueco calculado desde el tamaño *ya agrandado* del crosshair, no desde una constante.
- **`HudAnchor`**: un único punto canónico del que todo lo demás es offset. Antes cada layer calculaba `guiWidth()/2` y ajustaba a ojo, y el crosshair —que es de tamaño impar— se corría medio píxel al crecer.
- **Los corchetes muestran el ÁNGULO del cono, no la distancia.** Son preguntas distintas y mezclar las dos hacía que espada, hacha, pico y pala dibujaran lo mismo.
- **El tamaño sale de la proyección angular del cono:** `(altura/2)·tan(a)/tan(fov/2)`. Con eso el widget es idéntico en 1080p y 2K, en cualquier GUI scale, y se achica cuando el jugador zoomea — que es lo que el cono hace de verdad. Verificado con tests.
- **Sin socket y sin frente suave en la barra.** Los dos fallaron: un frente suave hacia adelante pone un pixel a brillo completo junto a la crosshair con el arma sin lista (un "listo" falso), y hacia atrás apaga las últimas columnas de la barra llena (justo donde se mira si podés pegar). La barra es **una línea, un gradiente, y el cooldown solo decide dónde termina.** El alcance lo siguen mostrando los corchetes, que no se mueven.
- **El cono ahora sigue la mirada** (marco ortonormal anclado a la mirada, no al suelo). Antes un punto a 20° de una mirada elevada a 60° salía a 36° y una espada de 35° lo rechazaba.
- **`/cc cone` pide el marco a `ConeMath.frame()`** en vez de reescribir la matemática. Y dibuja dos curvas de límite, no un elipse: la región golpeable es la intersección de dos semi-espacios (una pirámide rectangular), no un cono de revolución.
- **Un swing al aire cuesta cooldown** (`WhiffPayload`, 0 bytes). Antes el click no mandaba nada al servidor, y swing-miss-swing daba dos swings por un solo cooldown.
- **El flash de daño no strobea** y no grita al revivir: la salud recordada está keyeada al objeto `LocalPlayer` que describe, y un flash ya encendido no se rearma con golpes chicos.

Ver `PROTOTYPE-ASSETS.md` y la tabla de arriba para lo que falta de assets.

## Optimización del cono (hecha)

El diagnóstico estaba desde hacía varias rondas y era correcto: **`ConeMath` no era el costo.**
Es geometría pura en `double`, unas veinte operaciones por entidad. Lo caro era el
`getEntitiesOfClass` alrededor, que se ejecutaba 20 veces por segundo en el cliente.

Y el hallazgo que justificaba el trabajo: **`CombatAim.target()` no lo llamaba nadie.** El HUD
solo consume un booleano, `ClientCombatState.aiming()`. O sea que cada tick pagábamos una
resolución de objetivo completa y **tirábamos la entidad**. Ese era el desperdicio real, más que
la frecuencia.

**Dos reglas, y la segunda es la que hace segura a la primera:**

1. **Escanear cuando la puntería se movió.** Fuerza el reescaneo un cambio de jugador, de item o
   de perfil de alcance; que el jugador se haya desplazado; que haya girado; y que un mob haya
   entrado al cono. Barriendo el mouse con el cursor el crosshair reacciona cada tick, igual que
   antes.
2. **Revalidar el objetivo cacheado CADA tick, igual.** El escaneo es lo que *encuentra* un
   objetivo; esto es lo que deja de creerse al que encontró. Un mob que murió, se descargó, cambió
   de dimensión o salió del cono deja de contar en el mismo tick, no tres ticks después.

Lo que la regla 2 **no** cubre es la llegada de un objetivo *distinto*: si nada se mueve, un mob
que entra al cono puede pasar hasta `STILL_SCAN_TICKS` (3) inadvertido. Son 150 ms sobre una
pista visual, y el ataque no se entera porque re-deriva el objetivo en el click.

**Lo que hay que entender para no romperlo:**

- **La tolerancia es el throttle entero.** `MOVE_EPSILON_SQUARED` y `TURN_DOT_THRESHOLD` no son
  detalles: en cero, cada vibración del mouse cuenta como girar, el escaneo corre cada tick, y la
  optimización no cuesta nada *pareciendo* funcionar. La forma más probable de romper esto es
  poner un epsilon en cero, no ver diferencia, y concluir que el escaneo es gratis.
- **La revalidación se registra en el escaneo, no en cada chequeo.** El jugador puede pasearse
  libremente mientras el objetivo aguante: el chequeo de movimiento existe para descubrir una
  respuesta vieja *en el instante* en que existe, y no hay respuesta vieja hasta que el objetivo
  se cae.
- **Tres comparaciones de referencia, no comparación de componentes.** `ItemStack.isSameItemSameComponents`
  recorre el patch entero cada tick para aprender lo que tres referencias ya dicen. Pero con solo
  el `Item` se pierde un mod que reescriba un componente sobre ese mismo item, y el perfil de
  alcance es un record guardado por el componente, así que un valor sin cambios es la misma
  instancia.
- **`isCandidate` es una sola definición compartida** entre el escaneo y la revalidación. Dos
  versiones de "esto es algo a lo que se puede pegar" es exactamente cómo el crosshair y el
  swing terminan discrepando, y esa discrepancia es invisible hasta que un jugador reporta que el
  juego rechazó un golpe que el crosshair había prometido.
- **`ConeDebugRenderer` escanea por su cuenta, cada frame, a propósito.** Es el único lugar donde
  pagar lo caro es lo correcto: un overlay cuyo trabajo es responder "por qué no pegó" no puede
  ir tres ticks atrasado respecto del mundo que dibuja. Está apagado salvo que se pida.
- **El reuso de la lista sigue sin hacerse, y ahora está justificado por escrito.** Existe un
  overload de `Level#getEntities` que toma un `Consumer` y llena una lista del llamador, que
  ahorraría un `ArrayList` y un `Optional` por llamada. Son un objeto chico cada tres ticks. El
  costo que dominaba el perfil era recorrer las entidades, y los dos overloads lo hacen igual.

## Deuda conocida: el feedback del golpe cargado

**El viewmodel ya está hecho** (ver la sección de la librería). Lo que sigue siendo placeholder es
todo lo demás, y es menos de lo que parece:

| Falta | Dónde está decidido | Nota |
|---|---|---|
| **Retroalimentación de "esto fue cargado"** | — | `CombatFeedback` solo tiene `HIT`, `KILL` y `GUARDED`. Un golpe a carga completa se ve **idéntico** a uno de carga cero: misma expansión, mismo color, misma duración. El jugador tiene que inferirlo del número de daño, que es la lectura que el diseño de HUD evita |
| **FOV de carga** | `DESIGN.md` 512 | Va por `ComputeFovModifierEvent`, que **existe** en 1.21.1 (verificado). No es una capa, es un evento |
| Sonido de carga completa | `DESIGN.md` 524 | El proyecto tiene **un solo sonido**: `SHIELD_BREAK` en `GuardHandler`. Los cuatro eventos que el jugador tiene que reconocer sin mirar son carga completa, parry, agotamiento de aguante y escalado del martillo: ninguno existe |
| Impacto de golpe cargado (partículas) | `DESIGN.md` 499 | `addParticle` no aparece en ninguna parte del código |
| **Tercera persona** | — | El viewmodel es solo first person. El servidor sigue balanceando, así que los demás jugadores ven el brazo de vanilla. Es coherente, pero no es lo que el framework debería terminar siendo |
| **Las 29 armas sin perfil** | paso 9 | Solo la espada tiene perfil. El resto usa la pose de vanilla, que es el default correcto |

La primera fila es la que más duele y la que no está escrita en `DESIGN.md`: **el crosshair no
distingue un golpe cargado de uno normal.** Cuando se implemente el FOV y el sonido, lo
necesario es un valor más en `CombatFeedback` (un `CHARGED`, o un tono aparte del que usa
`HIT`), porque un HUD que reacciona a todo deja de significar nada y ese enum es una lista
cerrada a propósito.

**Y lo que se acaba de arreglar de paso:** hasta esta ronda el golpe cargado **no existía**. La
carga se acumulaba y se tiraba en cada release, así que el multiplicador nunca se aplicó. Ahora
un click de menos de 4 ticks es un golpe plano y uno de más es un golpe cargado de verdad, y el
viewmodel lo muestra.

## El click unificado — y el bug que lo motivó

**El bug primero, porque es más grave de lo que parecía.** El síntoma reportado era "a veces los
ataques cargados se anteponen a los normales y viceversa". La causa real: **el golpe cargado
nunca ocurría.**

El flujo viejo, rastreado paso a paso:

1. **Bajás el click** → arranca la carga **y en el mismo click** vanilla dispara el ataque.
2. El servidor lee la carga en ese instante: **0 ticks**. Daño base, siempre.
3. **Mantenés** → la carga sube a 25/25, el indicador se llena.
4. **Soltás** → `endCharge` resetea a `EMPTY`. **Los 25 ticks se tiran**, y vanilla no dispara
   nada en el release porque `startAttack` solo corre en el press.

`DamageResolver` nunca vio un ratio distinto de 0. El multiplicador era código muerto y el
indicador una promesa incumplida. Lo que el jugador veía — el medidor llenarse y después un
click normal — es exactamente "se anteponen".

**El arreglo: un solo evento, y el ataque se resuelve en el release.**

Un botón tiene que significar dos cosas: click corto pega, click sostenido carga. En el momento
del press no se puede distinguir, así que la decisión no se puede tomar ahí. Resolver en el
release es lo único que hace que **un press sea siempre un ataque**, y la carga es lo que el
servidor midió mientras el botón estuvo abajo.

| Decisión | Valor | Por qué |
|---|---|---|
| Umbral mínimo de carga | `AttackTiming.MIN_CHARGE_HOLD_TICKS` = 4 (200 ms) | Un click deliberado es ~150 ms. Decidido por el usuario |
| ¿Umbral por arma? | **NO, global** | Si variara, el mismo gesto cargaría una espada y no un martillo. La memoria muscular tiene que ser global o no es memoria muscular |
| Bajo el umbral | **Daño plano, sin excepciones** | Una banda invisible donde un click de 3 ticks hace "un poco más" no se aprende mirando; se descubre por haber pegado de menos |

**Lo que sobró, y por qué sobró:** `ChargeInputPayload` y `WhiffPayload` se fusionaron en un
`SwingPayload(entityId, pressed)`, y `PlayerAttackMixin` se borró. El cliente manda **el
objetivo**, no le pide al servidor que lo busque, y eso es lo que colapsa el peligro de orden:
antes el release decía "dejá de cargar" y un paquete aparte decía "el swing falló", y cuál de los
dos llegaba primero decidía si se cobraba el cooldown. **Un paquete no puede llegar en el orden
equivocado.** `MinecraftAttackMixin` quedó en tres líneas: cancela vanilla y nada más.

**Consecuencia aceptada:** un click corto ahora se resuelve en su release, o sea un par de ticks
(100 ms) más tarde que antes. Es el precio de un press = un ataque, y es el mismo precio que paga
todo juego con botón cargable.

**Costo que NO se paga — la predicción de daño.** El cliente ya no llama `gameMode.attack`, así
que el mob no se encoge hasta el viaje de vuelta. Es deliberado: el mod **borra a propósito** la
ventana de invulnerabilidad del objetivo antes de pegarle (`invulnerableTime = 0`), así que
predecir el daño en el cliente lo aplicaría dos veces. El crosshair sigue respondiendo al
feedback del servidor.

### Tres huecos que aparecieron al implementarlo

Los tres son de la familia "un estado que dura más que el botón que lo creó":

- **Doble click dentro de un tick.** El cliente abría dos swings porque su cooldown llega un viaje
  de vuelta tarde; el servidor honraba el primero y tiraba el segundo, y el jugador veía dos
  animaciones de golpe y un impacto. Es exactamente el fallo que el proyecto ya había rechazado
  ("un swing que se ve y no hace nada"). Se arregla con un **cerrojo local**: `readyAtTick`, que
  el cliente pone en el release con el intervalo del arma. **El gate del cliente tiene que ser
  más estricto que el valor que tiene**: puede rechazar un golpe que el servidor habría
  permitido, por un viaje de vuelta como mucho, y nunca al revés.
- **Cambio de item con el botón apretado.** Con el scroll se puede cambiar de arma a mitad de
  carga. El release leía los ticks acumulados y los pasaba por la escala del arma **nueva**: 40
  ticks de maza (que carga al doble de lento) se convertían en carga completa de espada. Ahora el
  cambio de item **resuelve el swing como si fuera un release**, que es lo único honesto: se cobra
  el cooldown porque el jugador sí se comprometió, y se cierra la carga.
- **Loguearse a mitad de carga.** La carga vive en un attachment persistente, así que un jugador
  que se desconectaba apretando se reconectaba con `holding` en true y el ticker seguía contando
  una carga que ningún botón estaba haciendo. Corregido en `PlayerEvent.PlayerLoggedInEvent`.

**Y una decisión de criterio que vale la pena recordar:** minar contra un enemigo en el cono.
Decidirlo por "el crosshair está en un bloque" hacía que una pared *detrás* del enemigo se comiera
el golpe; decidirlo por "no hay objetivo" hacía que un zombi a tu espalda te impidiera minar. La
regla que funciona es **lo que esté más adelante**, comparando el punto del bloque contra el
centro del objetivo.

## La librería de viewmodel (`viewmodel/`)

Decidido por el usuario: **adelantarla**, antes que las 30 armas, porque con una sola se debuggea
fácil y de ahí se construye. Y porque hacerlo después de que existan 30 armas es un desastre.

**Todo el cálculo es Java puro. Una sola clase toca Minecraft** (`client/ItemViewModel.java`), y
esa separación es lo que hace que la librería sea testeable *y* extraíble. La regla ya se había
aprendido dos veces con `ConeMath` y `AttackTiming`: lo que se puede testear en JVM pelada se
testea, lo demás no se toca hasta que hace falta.

| Clase | Qué es | Por qué es así |
|---|---|---|
| `Curve` | `LINEAR`, `SMOOTH`, `EXPO`, `BACK`, `ELASTIC`, `BOUNCE` | `Mth` solo trae lineal/cuadrática/cúbica y un golpe con peso necesita más. **Todas normalizadas: `f(0)==0`, `f(1)==1`** |
| `Offset` | record de 3 posiciones + 3 ángulos, aditivo | Posiciones en unidades de item y ángulos en grados: son las unidades de vanilla, así que un número copiado de un `ItemInHandRenderer` descompilado funciona tal cual |
| `Channel` | `WINDUP`, `STRIKE`, `COOLDOWN`, `HOLD` | Los cuatro significan lo mismo: *cuánto de este movimiento está pasando ahora*. Ninguno es un reloj |
| `ViewModelInput` | el snapshot de un frame | Un record inmutable, armado una vez por frame. Un perfil **no consulta el juego**, es función de este record |
| `Track` | `(channel, curve, amplitude, tremor, tremorHz, tremorWeight)` | La fórmula entera es `amplitude × curve(channel)`. Como toda curva arranca en 0, un track no aporta nada en reposo sin que nadie tenga que escribirlo |
| `ViewModelProfile` | una lista de tracks | Sin timeline, sin keyframes, sin máquina de estados. Un timeline es lo correcto para un ataque con seis tiempos distintos y lo **equivocado** para "el item se tira atrás en proporción a la carga", que es lo que casi todo movimiento del juego es |
| `ViewModelNoise` | hash entero por bucket, interpolado | El temblor tiene que ser sembrado por golpe, no aleatorio por frame |
| `ViewModelMotion` | el wind-up y el strike a lo largo del tiempo | Ver abajo, que es la parte difícil |

**No hay timeline, y es un límite deliberado, no una feature faltante.** Si alguna vez hace
falta, un timeline sería *otro canal*, no un cambio de esta clase.

### El hand-off del release, que es la parte difícil

El item está todo atrás y tiene que venir adelante y pegar. Las dos versiones ingenuas se ven
mal y con claridad:

- **La carga se pone en cero en el release** → el item teletransporta a reposo en el frame del
  golpe y *después* se lanza. El swing tiene un tirón visible en su punto más rápido y las dos
  mitades se leen como dos movimientos.
- **La carga se queda en 1 y el strike suma encima** → el item va el doble de lejos y nunca
  vuelve, porque nadie deshizo el wind-up.

Lo que hace `ViewModelMotion` es **entregarse**: el wind-up se desenrolla a lo largo del strike
mientras el strike empuja hacia adelante atravesándolo, así que el item sale de la posición
cargada en un movimiento continuo y el pico del follow-through pasa con el wind-up ya a la mitad.
Los dos canales vuelven a reposo y el item regresa a la pose de vanilla sin que nadie escriba un
track de "deshacer".

`STRIKE` es un **bump** (0 → 1 → 0) y no un canal que queda en 1, a propósito: si se quedara en 1
dejaría el item desplazado para siempre al terminar el movimiento, y cada perfil necesitaría un
segundo track para deshacer su propio primero, y los dos tendrían que coincidir.

### Rendimiento

Un `ViewModelInput` por frame, después una pasada por una lista de records de cuatro elementos
haciendo unas pocas multiplicaciones. **Sin alocación, sin lookup, sin branch por arma.** Lo
único que pasa por frame en el código del juego es leer cuatro floats que ya se estaban
midiendo para el HUD, y el perfil se resuelve cuando cambia el item en mano, no por frame.

Dos decisiones que existen solo por esto:

- **`Axis.rotationDegrees` aloca un `Quaternionf` por llamada.** Se reusa uno scratch con
  `identity().rotateX().rotateY().rotateZ()` y un solo `mulPose`.
- **Un `Quaternionf` por frame, no tres**, porque `mulPose` lo lee antes de volver.

### Estado actual: solo la espada, y es placeholder

`ViewModelDefaults` tiene **una** entrada real, la espada: wind-up en proporción a la carga
(`SMOOTH`), temblor que crece con la carga a 14 Hz sembrado por golpe, y un strike `EXPO` hacia
adelante y más allá del reposo. **Los números están escritos, no ajustados.** Son la magnitud y
la forma correctas; lo que no son es un feeling que alguien haya jugado.

El resto de las armas **no tienen perfil a propósito**: sin entrada, la pose es exactamente la de
vanilla, que es el default correcto. Un viewmodel sutilmente mal es peor que ninguno.

`ownsSwing` en el perfil significa "el perfil dibuja el strike entero", y lo que hace es que el
cliente **no** llame a `player.swing` (si no, el item se mueve dos veces y las dos animaciones
se desfasan). El servidor igual balancea, así que **en tercera persona se sigue viendo el
brazo de vanilla**: first person tiene el viewmodel real, third person tiene vanilla hasta que
el framework tenga historia de tercera persona.

## Dos bugs reales que encontraron los tests del viewmodel

**201 tests verdes** (124 previos + 77 nuevos). Los tests no encontraron bugs "de lógica" sino
dos cosas que cambian cómo se ve el mod, y las dos son las que un playtest delata tarde:

1. **Todo se movía a 20 Hz, no por frame.** `ViewModelMotion` solo se evaluaba una vez por tick,
   así que el item repetía cada posición tres veces a 60 fps. Eso no es una animación, es una
   máquina dando pasos, y el temblor se convertía en zumbido. El arreglo fue separar las dos
   responsabilidades que había mezclado: **`tick()` avanza el estado, `snapshot(partialTick)`
   produce el frame.** Vanilla interpola su propio swing por el mismo motivo. El partial tick
   viene de `RenderHandEvent.getPartialTick()`.
   - De paso, el mismo bug escondía un **off-by-one en el frame del release**: el primer tick
     después del release ya había avanzado un tick, así que el wind-up había bajado un sexto
     antes de dibujarse nunca. Se resolvió con `pendingStrike`: el release *anuncia* el strike y
     el primer tick lo arranca en edad 0. **El frame en el que soltás tiene que mostrar el item
     todavía atrás.**

2. **`Curve.LINEAR` no clampeaba.** Las otras cinco curvas arrancan de `clamp01`; esa no. Un
   canal que se pase un pelo de cero — por aritmética de float, o por un valor que nunca debió
   ser negativo — movía el item un pelo de la pose de vanilla. "Cero significa que este track no
   aporta nada" tiene que ser cierto de **todas** o no es una regla.

Y una decisión de API que los tests impusieron: **`Channel.COOLDOWN` cuenta el cooldown que *se
está gastando*, no el que ya se gastó.** Con "listo = 1" el canal descansa en uno, y un canal
que descansa en uno necesita un caso especial — y un perfil que mueve el item con el jugador
parado es un arma que se va de la pose de vanilla a la deriva. Los cuatro canales descansan en
cero, sin excepción, y `ViewModelInput.RESTING` lo dice.

### La lección: tests acoplados a datos provisorios

El primer intento de testear el hand-off fue una aserción de continuidad sobre el **offset
compuesto**, con un límite en unidades de item. Falló, y el test estaba mal dos veces:

- **Plegar grados en unidades de item necesita una equivalencia arbitraria.** La que elegí
  terminó tan ajustada que rechazaba los propios frames rápidos del strike, que son
  exactamente lo que tiene que ser rápido.
- Peor: **el límite tenía que derivarse de los números actuales de la espada**, y esos números
  están escritos, no jugados. El test iba a fallar en cada retune. **Un test acoplado a datos
  provisorios es peor que no tener test: te entrena a cambiar el test.**

El invariante va en el **canal**, no en el offset compuesto: *"el wind-up no pierde más que un
tick de valor en un frame"*. Es exacto, independiente del perfil, y la versión ingenua del
hand-off lo viola tirando el wind-up entero de golpe.

**El mismo patrón apareció en `ViewModelNoiseTest`.** En vez de un número mágico, el límite es
ahora **la garantía que la interpolación da por construcción** — la señal no puede moverse más
que `fase × rango` — más una aserción de que la *media* por frame está muy por debajo de lo que
daría una señal escalonada. Esa segunda es la que distingue un temblor de un zumbido, y no se
rompe al cambiar la frecuencia.

## Seis reportes de playtest — y uno sigue abierto

Ronda de seis cambios a partir de un playtest largo. **223 tests verdes** (201 + 22 nuevos),
cliente verificado con `runClient`: mod carga, mundo entra, cero crashes, mixin aplica.

### 1. "Cualquier objeto con el que golpeo tiene carga"

**Causa: una sola línea.** `deriveProfile` caía a `WeaponProfile.SWORD` cuando no reconocía el
item. Un bloque de tierra no está en ningún tag de vanilla → derivaba a espada → heredaba su
`charge_profile` → podía cargarse para bonus de daño. **Todo objeto del juego era un arma.**

Agregué `WeaponProfile.NONE`, y el default de la derivación pasó a ser ese:

> **"Si no lo reconocemos, tratémoslo como lo más común" es un default que convierte discretamente
> todo el mundo en un arma.** Es invisible hasta que un jugador agarra un bloque, y el síntoma
> ("¿por qué mi bloque carga?") no señala ni de lejos a la línea culpable.

Es un perfil y no un `if`: el item tiene no-carga, este alcance, este viewmodel. Agregar un arma
que no sea una herramienta sigue siendo solo un tag.

### 2. "Al cargar está invertido: el ítem se hace hacia abajo"

**Acá me equivoqué y la lección es la que importa: no verifiqué el signo antes de escribir los
números.** Lo deduje mal, y el usuario lo vio en un segundo.

Lo que sí es verificable, con `javap` sobre las constantes de `ItemInHandRenderer`:

| Constante | Valor | Qué fija |
|---|---|---|
| `ARM_POS_Y` | `-0.75` | **+Y es arriba** en este frame. No es una opinión |
| arco de vanilla | `BOW_CHARGE_Z_POS_SCALE = 0.04` | el arco tira hacia **-Z** |

Entonces el wind-up ahora **lo lleva la traslación en Y (+0.22)**, y el pitch quedó como un
**lean chico de 18°**. Ese 18 es deliberadamente pequeño: aunque su signo esté mal, no puede dar
vuelta la lectura. La primera versión leading con −35° de pitch y la espada salía **hacia
abajo**; ahora el error posible es una inclinación, no una inversión.

**El pitch es el único eje del perfil que hay que leer de la pantalla.** El test lo admite
explícitamente: asserta la magnitud y deja la dirección para el próximo playtest.

### 3. "La animación de golpe normal se ve como una carga mal hecha"

Causa exacta: **el strike era el mismo para todos.** Un click de 2 ticks y una carga completa
jugaban los mismos 34° de barrido; lo único que cambiaba era la duración. Una versión corta
del movimiento equivocado sigue siendo el movimiento equivocado.

`ViewModelMotion.onStrike(charge)` ahora escala **wind-up y strike** por la carga del release.
Con un piso, `TAP_FLOOR = 0.2`:

> Un tap con strike literalmente cero **salta** el item de donde esté a reposo en un frame. Eso
> es un pop visible en cada click commonplace. Un quinto del swing completo es bastante para
> leerse como swing y demasiado poco para confundirse con el cargado.

El cliente pasa **`AttackTiming.chargeRatioFor`, el mismo umbral que el servidor**, así que el
viewmodel es un *readout* de la regla: lo que ves es la clasificación que va a recibir tu golpe.

### 4. "Cualquier ataque me lo detecta como carga" — ⚠️ ABIERTO

**No encontré la causa raíz y no la inventé.** Lo que hice fue volverla diagnosticable:

- `/cc stats` ahora imprime **cuenta viva, umbral y veredicto** (`TAP` o `CARGADO`).
- El viewmodel usa el mismo corte que el servidor, así que **la item misma dice qué clasificación
  va a recibir el golpe**.

Eso parte el problema en dos y cada mitad tiene un dueño distinto:

| Síntoma | Dónde está el bug |
|---|---|
| El item dibuja wind-up, el daño es plano | el umbral en el cliente |
| El item NO dibuja wind-up, el daño sale cargado | la cuenta en el servidor |

Sospecha — **sin confirmar** — que el umbral de 4 ticks (200 ms) es corto para el ritmo de click
del usuario. Eso es balance, no bug, y el número es suyo.

### 5. "El FOV no se ve afectado al cargar"

**No lo había implementado nunca.** Estaba anotado como deuda y el usuario lo probó, así que se
hizo. `ComputeFovModifierEvent` con **`setNewFovModifier(float)`** (verificado en el jar: existe
`getFovModifier`, `getNewFovModifier` y el setter).

Dos decisiones que no son cosméticas:

- **Resta, no reemplaza.** Reemplazar pelearía con el FOV del sprint, con el efecto de velocidad
  y con cada mod que también toque el FOV, y la pelea se vería como parpadeo cuando coincidieran
  dos en el mismo tick.
- **3° a carga completa, y se mantiene cerrado durante el follow-through** para que el snap de
  apertura caiga **en el golpe** y no antes. El mundo cerrándose mientras cargas y abriéndose
  cuando la espada impacta es un ritmo; cerrarlo temprano lo arruina.

Compone con el `fovEffectScale` del jugador: el que lo apagó por mareo lo sigue teniendo apagado.

### 6. "El knockback es muy intenso y demasiado vertical, debería ser por arma"

Nuevo `KnockbackProfile(horizontal, vertical)` como **sexto data component**, con una tabla por
arma. Y **el knockback salió del `AttackResult`**: era un subproducto del daño, y ahora es del
arma. Ese es el cambio de fondo — un número que viajaba dentro del resultado del golpe no
tenía dónde vivir como parámetro de un arma.

Defaults (bajados enteros de los 0.4–0.9 que había):

| Arma | h | v | | Arma | h | v |
|---|---|---|---|---|---|---|
| Bloque (`NONE`) | 0.12 | 0.02 | | Ropera | 0.16 | 0.03 |
| Manos | 0.10 | 0.02 | | Daga | 0.14 | 0.03 |
| Espada | 0.28 | 0.05 | | Alabarda | 0.45 | 0.14 |
| Hacha | 0.34 | 0.08 | | **Martillo** | **0.55** | **0.30** |
| Pico / Pala | 0.22 | 0.05 | | Shotel | 0.38 | 0.06 |
| Maza | 0.30 | 0.10 | | Tridente | 0.30 | 0.12 |

El vertical está **separado a propósito** porque escalarlos juntos es lo que hacía que todo
golpe se sintiera como una catapulta: el knockback de vanilla suma un término vertical que escala
con el empuje, así que un empujón fuerte también saca al mob del piso. Nada de golpear con una
espada debería poner a nadie en el aire. Además `MAX_VERTICAL = 0.35` está **muy por debajo** de
`MAX_HORIZONTAL = 1.2`, y hay un test que lo verifica: los topes son la forma de que "casi nada"
signifique exactamente cero en vez de un número chico que todavía levanta.


## Próximos pasos

1. **Paso 9: las 30 armas + datagen + recetas + set de cobre.** Es el paso grande que falta.
2. Paso 10: especiales (alabarda multi-hit, parry de la sable) + damage type de ignore de armadura. El flag `ignoredArmour` ya se calcula y se testea, pero la fuente de daño sigue siendo la normal.
3. Paso 11: supervivencia (`FoodData`) — el cambio más caro, en commit propio.
4. Paso 14 (lo que falta): FOV de carga, sonido de carga completa, el valor `CHARGED` de `CombatFeedback`, tercera persona, y los perfiles de las otras 29 armas. **Antes que nada, ajustar los números de la espada en playtest** — están escritos, no jugados.
5. Números: definir el escalado del martillo en playtest (`WARHAMMER_STEP` está en 0.15 como punto de partida).
6. Verificar contra las fuentes de 1.21.1 los umbrales internos de `FoodData` antes de escribir el mixin.
7. Pendiente de decidir: extraer las tablas de `WeaponStats` a una clase pura, para que el orden de los alcances sea un test y no una acordada. Hoy no se puede testear porque el `<clinit>` de `WeaponStats` usa `Registries` y `TagKey`, que son de Minecraft.

## Trampas de diseño marcadas

- El autoataque carga y dispara → el ritmo ya es del jugador. La profundidad tiene que venir de arma, click derecho, lectura de aguante y gestión de aguante. **No reintroducir profundidad metiendo más timing.**
- El martillo **no resetea** al recibir daño, por decisión. Es un arma de compromiso. Si en el playtest resulta demasiado fuerte, se cambia con el dato medido, no por intuición.
- Cubrirse no tiene coste. Si el escudo termina siendo inútil porque todos cubren gratis con la espada, el problema es la densidad de aguante, no el click derecho.
- **`foodRegenInterval` es el número más importante del proyecto.** Rápido → la comida es decorativa. Lento → pelear es un callejón sin salida, que es justo lo que el mod viene a arreglar. Solo playtest lo resuelve.
- **`FoodData` es vanilla puro, sin patch de NeoForge** (verificado). Los umbrales internos hay que leerlos de las fuentes de 1.21.1, no de memoria.
- **La espada (3.6) ahora alcanza más que el hacha, el pico y la pala (3.0).** Invierte la relación de vanilla y es a propósito: la espada es el arma que se supone que tenés. Si en el playtest hace que el hacha parezca inútil en vez de "para algo más corto", se ajusta el dato.
- **Las manos desnudas son el menor alcance del juego (1.9) y lo más rápido (4 ticks).** Cortas, veloces y casi inofensivas: la velocidad es lo único que las hace una opción real cuando no tenés nada. El `ATTACK_SPEED` base de un jugador es 4.0 (verificado con `javap`), o sea 5 ticks sin tocar nada, así que "lo más rápido" requería subir el tempo por encima de 1.0.
- **El `verticalAngle` de cada perfil importa mucho más que antes.** Con el cono siguiendo la mirada, ese número decide si un swing conecta con algo overhead o underfoot. Va a necesitar playtest cuando entren las armas nuevas.

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
- **Fase 8 (aguante escudo/espada) completa**: perfil de aguante por item (`guard_profile`), decision en `LivingShieldBlockEvent`, stagger reusando el cooldown de item de vanilla, y la regen atada al reloj del cooldown. `GuardState` quedó en 4 campos.
- **Reworks de HUD sobre playtest, 8 commits** (`2c2814e` a `632cf5a`): ver la sección "Reworks del HUD" de arriba. **124 tests verdes.** Cinco bugs reales encontrados por tests en el camino: el signo de la barra la dibujaba por fuera del corchete (invisible), el `rimPoint` del debug dibujaba una elipse a 90° del eje, el cono anclado al suelo rechazaba lo que estabas mirando, el socket de la barra producía un "listo" falso, y el frente suave apagaba la barra llena justo en el corchete.
- **Dos lessons que valieron cada una más que el código que escribieron:**
  - **Una clase que implementa `LayeredDraw.Layer` no se puede testear en JVM pelada** — falla al cargar, antes de la primera aserción. La matemática del widget vive en `ConeGauge` por eso, no por gusto.
  - **Un test que falla puede tener razón el código.** Me pasó dos veces: una aserción sobre el eje `up` que era imposible (es perpendicular a la mirada por definición, así que a 60° da 0.5 y no 0.9), y otra sobre el relleno de la barra. Reconocer cuándo el test está mal es parte del trabajo.
  - **`refs/rafts_combat` tiene un `RangeIndicatorHud` que resuelve mal lo mismo.** Usa `191.5 * 0.33` y un `widthRatio` que es una tabla de números mágicos por arma en su config. Copiar su enfoque era copiar el bug; la proyección angular lo resuelve sin un número por resolución.
- **Balance:** espada 3.2 → 3.6, manos desnudas 3.0 → 1.9 de alcance y tempo 1.25 (lo más rápido del juego), daga tempo 0.7, espada 0.85.
- **Optimización del cono, hecha.** Escaneo throttleado a 3 ticks cuando nada se movió, con revalidación del objetivo cacheado **cada tick**. El hallazgo que la justificaba: `CombatAim.target()` no tenía un solo llamador y el HUD solo lee un booleano, así que cada tick se pagaba una resolución de objetivo completa y se tiraba la entidad. Se borró el método muerto. La invalidación del objetivo es lo que hace segura la frecuencia: lo único que puede quedar viejo es la *llegada* de un objetivo distinto, y eso va a un hint. Detalle en la sección "Optimización del cono".
- **El click unificado, y con él el bug más grande encontrado hasta ahora.** El golpe cargado **no existía**: la carga se acumulaba en cada hold y `endCharge` la tiraba en el release, así que `DamageResolver` nunca vio un ratio distinto de 0. El síntoma ("se anteponen") era el medidor llenándose y después un click normal. Ahora un press abre el swing y el release lo resuelve, con `MIN_CHARGE_HOLD_TICKS = 4` **global** y **plano** por debajo del umbral (decidido por el usuario). `ChargeInputPayload` + `WhiffPayload` → un `SwingPayload(entityId, pressed)`; `PlayerAttackMixin` borrado; `MinecraftAttackMixin` quedó en tres líneas. El cliente manda el objetivo, y por eso no hay dependencia de orden entre paquetes.
- **Tres huecos del mismo tipo, la familia "un estado que dura más que el botón":** doble click en un tick (se arregla con un cerrojo local `readyAtTick`; **el gate del cliente tiene que ser más estricto que el valor que tiene**), cambio de item a mitad de carga (40 ticks de maza se convertían en carga completa de espada), y loguearse apretando (el attachment persistente dejaba `holding` en true y el ticker seguía contando una carga inexistente). Los tres se buscaron juntos por eso, no por casualidad.
- **La librería de viewmodel, adelantada antes de las 30 armas por decisión del usuario** (con una sola se debuggea fácil; con 30 es un desastre). 8 clases puras + **una sola** que toca Minecraft. `Curve` con 6 curvas normalizadas, `Offset` aditivo, 4 canales, `Track` = `amplitude × curve(channel)`, `ViewModelProfile` = una lista de tracks. **Sin timeline, y es un límite deliberado.** El punto difícil es el hand-off del release: el wind-up se desenrolla mientras el strike empuja atravesándolo, así que el item sale de la posición cargada en un movimiento continuo. Rendimiento: un record por frame, sin alocación, sin lookup, un `Quaternionf` scratch. El perfil de la espada tiene **los números escritos, no ajustados**.
- **API verificada que costó encontrar:** el viewmodel se engancha con `RenderHandEvent` (o sea `ClientHooks.renderSpecificFirstPersonHand`), llamado desde `renderHandsWithItems` **antes** de `renderArmWithItem`, así que nuestro transform envuelve todo lo de vanilla. **Ninguna mixin hizo falta para el viewmodel.**
- **77 tests nuevos del viewmodel, y dos bugs reales.** **Todo se evaluaba a 20 Hz en vez de por frame** (el item repetía cada posición tres veces a 60 fps: no es una animación, es una máquina dando pasos). Separado en `tick()` = avanza estado y `snapshot(partialTick)` = produce el frame, con el partial tick de `RenderHandEvent`. De paso apareció un **off-by-one en el frame del release**: el wind-up ya había bajado un sexto antes de dibujarse nunca, resuelto con `pendingStrike`. Y **`Curve.LINEAR` no clampeaba**, sola entre las seis, así que un canal un pelo negativo movía el item de la pose de vanilla. Los tests también impusieron que **`Channel.COOLDOWN` cuente el cooldown gastándose**: los cuatro canales descansan en cero, sin excepción, porque un canal que descansa en uno necesita un caso especial y un perfil que mueve el item con el jugador parado es un arma a la deriva.
- **La lección que más vale: un test acoplado a datos provisorios es peor que no tener test.** El primer test del hand-off medía la continuidad del offset *compuesto* con un límite en unidades de item, y estaba mal dos veces — la equivalencia grados↔unidades es arbitraria, y el límite tenía que derivarse de los números de la espada, que están escritos y no jugados, así que iba a fallar en cada retune. **El invariante va en el canal**, no en el offset compuesto. Lo mismo pasó con el bound del temblor: ahora es la garantía que la interpolación da por construcción (`fase × rango`) más una aserción de la media por frame, que es la que distingue un temblor de un zumbido.
- **⚠️ DEUDA LEGAL: quedan DOS texturas de `refs/`.** La viñeta viene de `neoforge-damageoverlay`, que es **All Rights Reserved**. Las dos esferitas de aguante vienen de `combatify` (LGPL-2.1). El resto del HUD ya es arte original del usuario. Ver `textures/gui/PROTOTYPE-ASSETS.md`.
