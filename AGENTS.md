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

---

# PARTE 2 — Memoria del proyecto

## Fase activa: 2 — Investigación de diseño

Estado: diseño **cerrado** (`DESIGN.md` v6). Arquitectura **aprobada** (`ARCHITECTURE.md`, sección 10). Próximo paso: Fase 1 (esqueleto).

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

## Próximos pasos

1. Fase 1: esqueleto. Copiar el build de `refs/rafts_combat`, `mod_id = cortescombat`, paquete `com.cortes.combat`, `[[mixins]]` habilitado, primer commit con README/LICENSE.
2. Vertical slice: barra de cooldown + cono de alcance + daño + indicador de aguante, de punta a punta, antes de meter las armas nuevas.
3. Números: completar la tabla de alcances (sección 2 de `DESIGN.md`) y definir el escalado del martillo (multiplicador por golpe, dónde deja de crecer de forma útil).
4. Verificar contra las fuentes de 1.21.1 los umbrales internos de `FoodData` antes de escribir el mixin.

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
