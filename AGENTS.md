# AGENTS.md — Cortés Combat

Mod de NeoForge **1.21.1** que unifica y reworksa el combate de Minecraft. Sin animaciones custom, sin sistemas sobrecomplicados. Prioridad de diseño: **intuitivo para cualquiera, con profundidad que aparece al aprenderlo**.

> En Windows el filesystem es case-insensitive, así que `agents.md` y `AGENTS.md` son el mismo archivo y uno pisa al otro. La memoria del proyecto va en `MEMORIA.md` a propósito. No la renombres.

## Estado actual

El proyecto todavía **no existe**. La raíz solo tiene `.gitignore` y `refs/`. Antes de escribir código hay que crear el esqueleto (ver *Fases*).

## Estructura

```
.                  → el mod (git propio, rama main)
refs/              → clones upstream, SOLO LECTURA, gitignored
  combatify/           Fabric, MC 26.1.2, Java 25  (port de Combat Test 8c)
  rafts_combat/        NeoForge, MC 1.21.1         (alcance horizontal + ataque con carga)
  neoforge-damageoverlay/  NeoForge, MC 1.21.1    (viñeta roja de vida baja + flash de daño)
```

`refs/` está en `.gitignore` y es gitignored a propósito: son clones con su propio `.git`. **Nunca los commitees ni los edites.** Para actualizar: `git -C refs/<repo> pull`.

## Reglas duras

- **Target: NeoForge 1.21.1 / Java 21.** No subir a versiones nuevas. Combatify está en MC 26.1.2 con Java 25 y APIs que no existen en 1.21.1: **cualquier idea que tomes de `refs/combatify` hay que traducirla a las APIs de 1.21.1, no copiarla.**
- **`mods.toml` va en `src/main/templates/META-INF/neoforge.mods.toml`** y se expande con `generateModMetadata` (task `ProcessResources` + `neoForge.ideSyncTask`). Nunca escribirlo a mano en `src/main/resources`.
- **Cada mixin nuevo va en `src/main/resources/<mod_id>.mixins.json`** y además en el bloque `[[mixins]]` de `mods.toml`. Olvidarse de la segunda línea = el mixin no se carga en producción, solo en dev.
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

Entorno: JDK instalado es **25**. El toolchain pide **21**, y el `foojay-resolver-convention` en `settings.gradle` lo descarga solo. Si el toolchain falla, el problema es la resolución, no el código.

## Las tres referencias

**`refs/combatify`** — el más rico, y el más difícil de portar. Es un mod de library/standalone que implementa Combat Test 8c. Lo valioso:
- `src/main/java/net/atlas/combatify/config/` — 13 clases de config, incluyendo `KnockbackMode`, `ArmourPiercingMode`, `AttackDecay`, `ProjectileDamage`. Buen catálogo de las palancas que existen.
- `component/custom/ExtendedBlockingData.java` y `util/blocking/` — sistema de bloqueo extensible (tipos de bloqueo, condiciones, efectos post-bloqueo). Es el sustituto del escudo vanilla.
- `mixin/` — 40+ mixins de servidor y 25 de cliente. El índice de *dónde* el combate se toca de verdad en Minecraft.
- `src/main/resources/resourcepacks/` — ~15 packs que parchean datos de items vía la librería `defaulted`. La mayoría de sus features se pueden replicar con `data/` del mod en vez de con código Java.
- Depende de `atlas-core` y `defaulted` (mod del mismo autor). **No las vamos a usar**: son Fabric y no están publicadas para NeoForge. Reimplementar la idea, no la dependencia.
- Sus `README.md` tienen la lista de cambios de combate más concreta que vas a encontrar. Leerlo antes de diseñar.

**`refs/rafts_combat`** — el más cercano a lo que vamos a hacer. NeoForge 1.21.1, mixins, payloads, config partida cliente/servidor. Ataques con fase de carga obligatoria: raycasts horizontales progresivos que crecen al mantener el click, area que golpea todo lo que atraviesa (no solo el primer enemigo), indicador de rango en HUD que se pone rojo si hay un target. Tiene buenos detalles: con escudo en alto la carga sigue sin apretar el click; se cancela cambiando de item en la hotbar; tamed/mounts no son golpeables; el knockback no aplica bajo 60% de carga. Limitaciones declaradas: desactiva Sweeping Edge a propósito, y cancela comida/pociones/bows durante la carga.

**`refs/neoforge-damageoverlay`** — 4 clases, se lee entero en un minuto. El patrón a copiar: `LayeredDraw.Layer` registrada en `RegisterGuiLayersEvent` con `event.registerBelowAll(rl("health"), layer)` para que quede *debajo* de la salud. `@Mod(value = MODID, dist = Dist.CLIENT)`, `ModConfig.Type.COMMON`, `ConfigurationScreen::new` como extension point. La viñeta usa un `blit` de una textura tiled con `setColor` para el alpha, y el flash usa `guiGraphics.fill` con tinte rojo. Cosas que hace bien y que conviene imitar: el fade es interpolación exponencial contra `deltaTracker.getRealtimeDeltaTicks()`, y guarda `lastKnownHealth` para detectar el delta en vez de suscribirse a eventos de daño.

## Fases

1. **Esqueleto** — clonar la estructura de build de `refs/rafts_combat` (es la que ya está en 1.21.1), cambiar `mod_id` a `cortes_combat`, `mods.toml` con `[[mixins]]` habilitado, paquete base `com.cortes.combat`, commit inicial con README/LICENSE.
2. **Investigación de diseño** — escribir en `MEMORIA.md` el diseño de combate: qué se toma de cada mod, qué se descarta, y qué se agrega. **Pedirle al usuario que lo apruebe antes de Fase 3.** El diseño es la parte que el usuario tiene que firmar; el código se puede iterar barato.
3. **Vertical slice** — un solo eje de gameplay funcionando de punta a punta (damage + feedback), con su HUD. Antes de meter tres mecánicas.
4. **Iterar** — el resto.

## Docs

- Reglas globales de trabajo (fases, compactación, research): `C:\Users\Admin\.config\opencode\AGENTS.md`.
- Este archivo es la referencia técnica. El progreso y las decisiones van en `MEMORIA.md`.
- Verificar una API contra los sources reales de 1.21.1 antes de usarla. La de `refs/combatify` es 26.1.2 y miente.
