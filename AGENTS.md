# AGENTS.md — Cortés Combat

Mod de NeoForge **1.21.1** que unifica y reworksa el combate de Minecraft. Sin animaciones custom, sin sistemas sobrecomplicados. Prioridad: **intuitivo p/ cualquiera, c/ profundidad q aparece al aprenderlo**.

> Referencia técnica ⊕ memoria del proyecto, en un archivo. En Windows el FS es case-insensitive → `agents.md` = `AGENTS.md`; escribir uno pisa el otro. El diseño de combate vive aparte, en `DESIGN.md`.

---

# PARTE 1 — Referencia técnica

## Estructura

```
.                  → el mod (git propio, rama main)
DESIGN.md          → qué hace el juego (cerrado v6)
ARCHITECTURE.md    → cómo está construido (aprobado)
refs/              → clones upstream, SOLO LECTURA, gitignored
  combatify/               Fabric, MC 26.1.2, Java 25  (port de Combat Test 8c)
  rafts_combat/            NeoForge, MC 1.21.1         (alcance horizontal + ataque con carga)
  neoforge-damageoverlay/  NeoForge, MC 1.21.1         (viñeta roja + flash de daño)
```

`refs/` gitignored a propósito: son clones c/ su propio `.git`. **Nunca commitees ni edites.** Actualizar: `git -C refs/<repo> pull`.

## Reglas duras

- **Target: NeoForge 1.21.1 / Java 21.** No subir. Combatify está en MC 26.1.2 c/ APIs q no existen acá → **traducir ideas, no copiar código.**
- **Mod id `cortescombat`**, paquete `com.cortes.combat`.
- **`mods.toml` va en `src/main/templates/META-INF/neoforge.mods.toml`**, expandido por `generateModMetadata` (`ProcessResources` + `neoForge.ideSyncTask`). Nunca escribirlo a mano en `src/main/resources`.
- **Cada mixin nuevo va en `src/main/resources/cortescombat.mixins.json`.** El bloque `[[mixins]]` del TOML nombra el **archivo de config**, no cada mixin: una sola línea `config="${mod_id}.mixins.json"`, nunca se toca. (La regla anterior pedía dos lugares; el segundo no existe.)
- **Queda UN mixin: `MinecraftAttackMixin`.** `PlayerAttackMixin` se borró al pasar el ataque a un payload.
- `neoforge.enabledGameTestNamespaces` = `mod_id` en los run configs. Tests nuevos usan ese namespace o no corren.
- **Red:** todo payload es `record implements CustomPacketPayload` c/ `Type` (`ResourceLocation.fromNamespaceAndPath(MOD_ID, "nombre")`) ⊕ `StreamCodec` estático. Registro centralizado, un archivo por payload. ⊘ `FriendlyByteBuf` a mano. **El record necesita `@Override type()`**: tener el campo `TYPE` no alcanza.
- **Config:** `ModConfigSpec` c/ `BUILDER.define*`, `SPEC` declarado **al final**. `CLIENT` y `SERVER` registrados por separado desde el constructor `@Mod`.

## Comandos

```
gradlew build               # compila + jar
gradlew runClient           # cliente de dev
gradlew runServer           # servidor de dev
gradlew runGameTestServer   # corre gametests y sale
gradlew runData             # datagen → src/generated/resources
```

`gradlew` **sin `./`** (Windows). `org.gradle.configuration-cache=true` activo; si tocás un input de build y falla por cache → `--no-configuration-cache`.

JDK instalado **25**, toolchain pide **21**, `foojay-resolver-convention` en `settings.gradle` lo descarga. Si el toolchain falla, el problema es la resolución, no el código. `neo_version=21.1.250` ya está en el cache.

## Las tres referencias

**`refs/combatify`** — el más rico, el más difícil de portar. Config: 13 clases (`KnockbackMode`, `ArmourPiercingMode`, `AttackDecay`, `ProjectileDamage`) = catálogo de palancas. `component/custom/ExtendedBlockingData.java` + `util/blocking/` = bloqueo extensible. `mixin/` (40+ servidor, 25 cliente) = índice de *dónde* el combate se toca. `resourcepacks/` ~15 packs que parchean datos de items vía `defaulted` → replicable c/ `data/` en vez de Java. Depende de `atlas-core` y `defaulted` (solo Fabric): **⊘ no las vamos a usar**, reimplementar la idea. Sus `README.md` tienen la lista de cambios de combate más concreta que existe.

**`refs/rafts_combat`** — el más cercano a lo que hacemos. NeoForge 1.21.1, mixins, payloads, config partida. Ataques c/ fase de carga obligatoria: raycasts horizontales progresivos, área que golpea todo lo que atraviesa (no solo el primer enemigo), indicador de rango que se pone rojo c/ target. Buenos detalles: c/ escudo en alto la carga sigue s/ apretar el click; se cancela cambiando de item; tamed/mounts ⊘ golpeables; knockback ⊘ aplica bajo 60% de carga. Limitaciones declaradas: desactiva Sweeping Edge a propósito, cancela comida/pociones/bows durante la carga.

**`refs/neoforge-damageoverlay`** — 4 clases, se lee en un minuto. Patrón a copiar: `LayeredDraw.Layer` en `RegisterGuiLayersEvent` c/ `registerBelowAll(rl("health"), layer)`. `@Mod(MODID, dist = Dist.CLIENT)`, `ConfigurationScreen::new` como extension point. Dos cosas que hace bien: fade por interpolación exponencial contra `deltaTracker.getRealtimeDeltaTicks()`, y guardar `lastKnownHealth` p/ detectar el delta en vez de suscribirse a eventos.

## Bugs / trampas de entorno

- `git clone` en PowerShell devuelve exit 1 y escribe `Cloning into '.'...` a stderr aunque funcione. **No es error**: verificar c/ `git log`.
- Combatify tiene **dos** bloques `loom {}`; el segundo sobreescribe `accessWidenerPath`. Solo aplica `combatify.classtweaker`; el `accesswidener` del primero es código muerto. No copiar.
- Escribir `agents.md` **pisa `AGENTS.md`**. Por eso todo vive en uno.

### Trampas del build

- **La expansión del TOML es Groovy.** Un backtick en los comentarios del template rompe el parser (`SimpleTemplateScript1.groovy: Unexpected input '('`) — error que apunta lejos de la causa. Comentarios en texto plano.
- **Toda propiedad usada en el TOML tiene que declararse** en el mapa `replaceProperties` de `generateModMetadata` en `build.gradle`. Falta una → `Missing property (x) for Groovy template expansion`.
- **`gradle.properties` ASCII puro.** Gradle lo lee como ISO-8859-1; un acento literal shippea `CortǸs Combat`. Usar escape Java: `mod_name=Cort\u00E9s Combat`.
- **`filteringCharset = 'UTF-8'`** en `generateModMetadata`, si no la salida usa el charset de la plataforma.

### Datagen en 1.21.1 + NeoForge — **cinco trampas, todas cuestan una ronda de debugging**

1. **⊘ se puede heredar de `RecipeProvider`.** `getName()` es **`final`** y devuelve el string `"Recipes"`. NeoForge registra su propio `NeoForgeRecipeProvider` con ese mismo nombre en el mismo `DataGenerator`, y el segundo que llega es rechazado con `IllegalStateException: Duplicate provider: Recipes`, que **no nombra ni al mod ni a la clase**. No hay subclase que lo esquive y `--all` **no** es la causa (quitarlo no cambia nada). ∴ los recipes se escriben como `DataProvider` plano. Los modelos por el mismo motivo (`ModelProvider.run()` es concreto y genera todo el juego; ⊘ hay seam).
2. **`createProvider(..)` YA llama a `addProvider` adentro.** `event.addProvider(event.createProvider(X::new))` registra **dos veces** y el error es `Duplicate provider: <tu nombre>`, que se lee como "dos providers declarados con el mismo nombre" cuando la verdad es uno declarado una vez y registrado dos. Es lo que escondió el bug #1 detrás de un error distinto.
3. **`PackOutput.Target` no es un detalle.** `DATA_PACK` → `data/`, `RESOURCE_PACK` → `assets/`. Recetas y advancements con `RESOURCE_PACK` se escriben en `assets/` y **el juego nunca las lee**: el archivo existe, parsea, y no hay receta. Fallo totalmente silencioso.
4. **Los tags de vanilla necesitan el constructor de 5 args.** `TagsProvider` de 3 args usa el modid `"vanilla"` por defecto y colisiona con el provider de vanilla: `Duplicate provider: Tags for minecraft:item mod id vanilla`. El de 5 args (`..., modId, existingFileHelper`) además decide el directorio de salida.
5. **`ExistingFileHelper` no sirve para preguntar "esto ya está dibujado a mano".** `exists(..)` busca **también en las fuentes de Mojang**, así que da `true` para `minecraft:item/handheld` — el parent de todos nuestros modelos — y el provider no genera **nada**: 32 armas, 0 modelos, datagen "exitoso". `trackGenerated(..)` devuelve **`void`**: registra, no consulta. ∴ la pregunta se responde preguntándosela al filesystem, y el path tiene que derivarse del output folder (el working dir difiere entre IDE y Gradle).

**Y una sixth que no es de la API:** `#` en un grid de receta **no es una celda vacía**, es un símbolo sin entrada en la key. `ShapedRecipePattern.of` tira `Pattern references symbol '#' but it's not defined in the key`. El vacío es un **espacio**; `WeaponShape.toRecipeRows()` traduce, y `rows()` sigue leyendo como lo dibuja `DESIGN.md`.

### Trampas de la API de 1.21.1 — **verificadas con `javap`, ninguna de memoria**

**Las fuentes de vanilla SÍ están disponibles.** En `~/.gradle/caches/neoformruntime/intermediate_results/` hay jars `sourcesAndCompiledWithNeoForge_*.jar` con **6.294 archivos `.java` de vanilla**. Para leer un método entero (y no un `javap` de 6 líneas) es `jar xf` contra ese jar. `javap` contra `compiledWithNeoForge_*_output.jar` sigue siendo lo rápido para *confirmar* una firma; las fuentes son lo que hay que leer cuando el comportamiento es lo que importa.

⚠️ **Ojo:** esas fuentes son de la versión que compiló ese cache (21.1.250 acá), no "la 1.21.1 genérica". Varias API están en versión distinta a 1.21.2+.

**Datagen, además:** `ShapedRecipe.Serializer.CODEC` existe y serializa la receta en el formato exacto de Minecraft. `RecipeProvider` usa `Recipe.CONDITIONAL_CODEC` y `Advancement.CONDITIONAL_CODEC` envueltos en `WithConditions`, y `RecipeBuilder.ROOT_RECIPE_ADVANCEMENT` para el parent. `DisplayInfo` **no tiene builder** (ctor de 8 args) y **sus title/description no aceptan `null`** — `Component.copy()` NPEa adentro del serializador. `InventoryChangeTrigger.hasItems` **no tiene overload de `TagKey<Item>`**: hay que pasar por `ItemPredicate.Builder.item().of(tag).build()` o el criterio nombra un item y no el material.

**Registros, payloads y codecs**
- Data components: `DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MODID)`. ⊘ `Registry.DATA_COMPONENT_REGISTRY`.
- `StreamCodec.composite` arranca en **1** componente (aridades 1–6). Payload de 0 campos → `StreamCodec.unit(instancia)`, que no escribe nada. ⊘ `ByteBufCodecs.VOID`.
- `StreamCodec.fromCodec` ⊘ existe en 1.21.1 → es **`ByteBufCodecs.fromCodec(codec)`**.
- Enums: `ByteBufCodecs.idMapper(IntFunction, ToIntFunction)`.
- ⊘ `com.mojang.serialization.codecs.StringCodec` en el DFU de 1.21.1 → `Codec.STRING.xmap(...)`, que está en todas.
- ⊘ `InputConstants.GLFW_PRESS` → es `org.lwjgl.glfw.GLFW.GLFW_PRESS`.

**Entidades y daño**
- `Vec3` **no** tiene constructor de 2 componentes → `double[]` para pares, o `Vec3.atCenterOf(AABB)`.
- `Player#attack` desreferencia el target de una (offset 9, s/ null-check) → no hay forma de mandar un ataque al aire por ahí (por eso existió `WhiffPayload`, ya fusionado).
- `ServerPlayer` **NO** overridea `stopUsingItem` → el servidor no puede obligar al cliente a soltar un item; la animación de uso se predice en el cliente y no viaja. Hay que hacerlo en el cliente.
- **`ATTACK_SPEED` base = 4.0**, no 0. `AttackTiming.UNARMED_INTERVAL` es fallback p/ entidades s/ atributo.
- `Entity.isRemoved()` existe y es público final (⊘ `hasBeenRemoved`). `EntityTypeTest` está en `net.minecraft.world.level.entity`.
- `Mth.clamp` **siempre 3 args** `(valor, min, max)`.
- `ItemStack.forEachModifier` toma `EquipmentSlot` o `EquipmentSlotGroup`, ⊘ `Holder<Attribute>`: pasar el slot y filtrar el atributo adentro del consumer. Para el intervalo de ataque ni hace falta: `LivingEntity.getAttributeValue(Attributes.ATTACK_SPEED)` ya incluye item, equipo y pociones.

**Item use — lo verificado en la ronda de la guardia**
- **El daño NO interrumpe comer en 1.21.1.** `Player#hurt` son 106 bytes → chequea invulnerable, escala y devuelve `super.hurt`. ⊘ `addCooldown` del item en uso; `LivingEntity#hurt`/`actuallyHurt` ⊘ llaman `stopUsingItem`. Mi nota anterior decía lo contrario y casi hizo escribir un mixin sobre un bug inexistente. **Si dudás de una mecánica de vanilla: `javap` antes de poner un mixin encima.**
- **`Item#isEdible` NO existe** (se fue en 1.20.5). Discriminador real: **`ItemStack#getUseAnimation()`** (no-arg) contra `UseAnim` → `EAT`/`DRINK` comida, `BLOCK` escudo. Preguntar "usando un item" ⊘ sirve: un escudo bloqueando también lo está, y gatear la guardia con eso la traba.
- **`PlayerInteractEvent.RightClickItem` dispara en LOS DOS LADOS**: `ServerPlayerGameMode#useItem` y `MultiPlayerGameMode#useItem` llaman los dos a `CommonHooks.onItemRightClick`. En el cliente corre **antes** de `ItemStack#use` y del packet — la rama cancelada hace `return null` (offset 72 de `lambda$useItem$5`), o sea el paquete ni se manda.
- **Un item use de vanilla se re-arma cada 4 ticks**: `handleKeybinds` tiene un 2º camino, `keyUse.isDown() && rightClickDelay == 0 && !isUsingItem()`. ∴ un `stopUsingItem()` por tick **parpadea**. Parar un item use de vanilla ⊘ se arregla por tick: hay que negar el click en el origen.
- **Cancelar el uso de un mecanismo de vanilla puede romper el que uno se apoya.** El bloqueo vive en `LivingShieldBlockEvent`, que solo dispara con el uso vivo → cancelar siempre = la guardia deja de bloquear. Cancelar **solo cuando la guardia se niega**.

**Attachments y eventos**
- `AttachmentType.builder(Supplier<T>)` quiere supplier: `() -> ChargeState.EMPTY`, ⊘ `ChargeState::EMPTY`.
- `SubscribeEvent` = `net.neoforged.bus.api.SubscribeEvent`. El paquete `neoforge.eventbus.api` ⊘ existe.
- **Registrar en `NeoForge.EVENT_BUS` una clase s/ `@SubscribeEvent` aborta la construcción del mod**: `IllegalArgumentException: class X has no @SubscribeEvent methods`. Si movés un seam de un evento a un mixin, el registro es lo que hay que borrar, y no avisa hasta que el cliente crashea.
- `copyOnDeath()` en un attachment está mal si el estado guarda **input**: `held` es un botón, sobrevive a la muerte y sube la guardia con aguante llena y s/ botón apretado.

**Render y anim — la fuente de más trampas del proyecto**
- **El viewmodel se engancha con `RenderHandEvent`, ⊘ con un mixin.** Lo dispara `ClientHooks.renderSpecificFirstPersonHand(hand, pose, buffer, light, partialTick, pitch, swingProgress, equipProgress, stack)`, que `ItemInHandRenderer.renderHandsWithItems` llama **antes** de `renderArmWithItem`. Orden real: `bobbing de vista → [nosotros] → transform del brazo → transform del modelo` → nuestro transform **envuelve** todo lo de vanilla en vez de ser pisado. `ItemInHandRenderer` ⊘ tiene `getItemTransform`; `ItemProperties` ⊘ lo referencia ninguna clase de `client/` salvo `ItemOverrides` → el `display.transform` viene horneado en el modelo.
- **`RenderArmEvent` existe pero `ItemInHandRenderer` ⊘ lo dispara.** La única llamada a `renderPlayerArm` es la rama del stack **vacío** → c/ item en la mano no hay brazo que animar. "Animar la mano" en 1ª persona = animar el item.
- **El arco de vanilla es `ITEM_SWING_X_ROT_AMOUNT = -80°`, no una traslación.** `applyItemArmAttackTransform` multiplica `XP.rotationDegrees(f6 * -80)` c/ `f6 = sin(√f·π)`. Una traslación mueve puño y punta por igual y **se lee como empujón sin importar cuánto la agrandés**; una rotación pivota en el puño y la punta barre un arco varias veces más largo. Esa diferencia de velocidad *es* el peso.
- **Una rotación pivota en el puño.** La punta de una espada está a >1 unidad → el pitch la mueve mucho más q la distancia puño-ojo. Por eso un pitch grande la saca de pantalla. **El yaw (≈90°) es lo que vuelve la hoja hacia el jugador**: "muy de lado" y "la hoja mira para el otro lado" son el mismo reclamo en ejes distintos.
- `Axis.rotationDegrees(float)` **aloca un `Quaternionf` por llamada** → reusar uno scratch c/ `identity().rotateX(..).rotateY(..).rotateZ(..)`; `mulPose` lo lee antes de volver, o sea una instancia compartida es segura en el hilo de render. **Un `Quaternionf` por frame, no tres.**
- `GuiGraphics#blit` **bypassea la pipeline del GUI** (arma su propio `Tesselator` c/ `POSITION_TEX` y llama un `setShader` que es no-op bajo el pipeline system) → **el alpha de `setColor` se ignora**. `fill` sí va por `RenderType.gui()` y respeta alpha. Por eso los corchetes se dibujan c/ `fill`, no `blit`.

### Partículas y sonidos en 1.21.1 — verificado leyendo las fuentes, no de memoria

**Las fuentes de vanilla SÍ están disponibles.** En `~/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar` está el `.jar` de vanilla, y los `.java` descompilados en `intermediate_results/sourcesAndCompiledWithNeoForge_*_output.jar`. `javap` contra ese último es lo rápido para *confirmar* una firma; para entender un comportamiento hay que **`jar xf` y leer el método**, que es como se descubrieron las trampas de abajo.

**Partículas**
- El atlas es `assets/minecraft/atlases/particles.json` = `{"sources":[{"type":"directory","source":"particle","prefix":""}]}`. O sea **`textures/particle/`** en **singular** y de **todos** los namespaces, y el id del sprite **no lleva path**: `textures/particle/sword_hit.png` → `cortescombat:sword_hit`. El `prefix` vacío es la parte que uno da por hecho que repite el nombre de la carpeta.
- `CustomParticleOptions` **⊘ existe** en 1.21.1. Se implementa `ParticleOptions` a mano, con los dos codecs (`MapCodec` y `StreamCodec`) y `getType()`.
- El registro de `ParticleType` es **de vanilla**: `Registries.PARTICLE_TYPE`. `NeoForgeRegistries.Keys` ⊘ tiene entrada de partículas, y `DeferredRegister.create` quiere un `ResourceKey`, así que equivocarse es error de compilación, ⊘ silencio.
- `Particle` y `ParticleRenderType` viven en **`net.minecraft.client.particle`**, ⊘ en `core.particles`. Sus accesores de UV (`getU0`/`getV0`/…) son **`protected`**, que es lo único que permite cortar una tira sin partirla en archivos.
- `SpriteSet` **no tiene `get(índice)`**. Tiene `get(age, lifetime)` — que es `list.get(age * (size-1) / lifetime)` — y `get(RandomSource)`. **La lista ES la animación**: por eso vanilla tiene `sweep_0` a `sweep_7` como siete archivos, y una descripción por tipo **no puede** llevar las armas de varias familias.
- El sprite set de un tipo registrado **se puebla desde el JSON de descripción**, ⊘ desde el callback del provider. Sin `assets/<ns>/particles/<nombre>.json` el set queda vacío, `sprite` es `null` y la partícula muere en `getU0()`.
- **Una tira vertical 32×160 con 5 frames NO es un formato que el sistema lea.** El corte hay que hacerlo en los UV, dentro de los límites del propio sprite (el atlas cose a tamaño fijo y un sprite ocupa un subrectángulo) — no contra 0..1, que da el quinto equivocado del atlas y parece arte malo en vez de un bug.

**Sonidos**
- El `sounds.json` resuelve los nombres contra **`sounds/`** (plural). `sound/` (singular) es la carpeta vieja y no la mira nadie: el síntoma es silencio total y el log lo dice (`File cortescombat:sounds/... does not exist`), que es fácil de filtrar por error.
- Los eventos de un `sounds.json` de mod **⊘ están en `BuiltInRegistries.SOUND_EVENT`**. `SoundManager` los guarda en un `private final Map<ResourceLocation, WeighedSoundEvents> registry` y el motor los resuelve al reproducir. La forma de nombrar uno es **`SoundEvent.createVariableRangeEvent(id)`**.
- Los pools de tres o más archivos **son el mecanismo nativo**: tres nombres bajo una clave en `sounds.json` es un sorteo, y no necesita código. La numeración (`hit`, `hit2`, `hit3` vs `hit1`, `hit2`, `hit3`) es irrelevante una vez que están en la lista.

**Items y atributos**
- `SwordItem.createAttributes(Tier, float damage, float speed)` — el tercer argumento es un **`ADD_VALUE` desde la base 4,0** del jugador, ⊘ el total. Vanilla escribe `-2.4F` y le da 1,6. Pasar un total suma, y **todas las armas quedan al doble de ritmo con el build verde**.

**Dimensión: el cono con correa**
- El cono angular se estrecha a medida que uno se acerca, así que **a un brazo de distancia es un bolsillo**: la ropera a 1 m de un zombi son 27 cm de ventana vertical contra un bicho de 1,95 m. Y el eje vertical se mide **desde el horizonte**, no desde lo que se apunta, así que mirar a un zombi a 1 m es mirar 33° hacia abajo.
- La medible: la distancia desde la que apuntar a un zombi funciona era **2,62 m para la ropera con 3,4 de alcance**, 1,77 m para las manos, 0,72 m para el martillo. El orden es el orden de los ángulos verticales, que es por eso se leía como "cuanto más angosto, peor".
- `ConeMath.leash(halfExtent)` = `min(0.65 × semiextensión, 0.8)`, y el permitido es `max(distancia · tan(ángulo), correa)`. Cerca la holgura es la del bicho y el cono vuelve a mandar a partir del cruce (2,37 m de los 3,4 de la ropera). El tope del 0,8 es lo que evita que un dragón de 8 blocks reciba 5 m de tolerancia.
- `ConeTargeting` (cliente) y `Reach` (servidor) tienen que pasar la correa **los dos**. El cliente manda solo el id del objetivo, así que si el servidor no la aplica promete un golpe y lo rechaza, y eso parece latencia.

**Otras**
- `StreamCodec.unit` p/ payloads vacíos (arriba). ⊘ `ByteBufCodecs.VOID`.
- `Minecraft` **no** tiene `getDeltaTracker()` → es `getTimer().getGameTimeDeltaPartialTick(false)`.
- `Minecraft#startAttack` abre c/ un guard de pantalla; cancelar en HEAD lo **borra**.
- `ItemCooldowns#getCooldownPercent(Item, float)` devuelve la fracción **restante** y **0 cuando está listo** (⊘ 1). El método es `getCooldownPercent`, ⊘ `getCooldownPercentage`.
- El cuerpo constant-specific de un enum **no** puede leer un campo de instancia: `BACK("1.7") { ...overshoot... }` ⊘ compila. Con un solo valor hace falta un `private static final`.
- Un record ⊘ puede tener un método que compite c/ un accessor de componente (`amplitude()` en un record c/ componente `amplitude` = "invalid accessor method").
- Enum que implementa `StringRepresentable` tiene que implementar `getSerializedName()`; alcanzar c/ un `id()` ⊘ basta.

---

# PARTE 2 — Memoria del proyecto

## Fase activa: 3 — Vertical slice

Diseño cerrado, arquitectura aprobada, pasos 1–8 hechos ⊕ verificados en cliente real, reworks completo del HUD, click unificado, librería de viewmodel c/ perfil de espada, y guardia unificada **cerrada como postura**. **450 tests verdes.** Siguiente: **paso 9**.

| # | Paso | Estado |
|---|---|---|
| 1–8 | esqueleto, registros, `reach/`, cooldown duro, flujo de ataque, carga, HUD, aguante | HECHOS |
| 14 | framework de viewmodel | HECHO (falta 3ª persona, sonido de carga, `CHARGED`) |
| **9** | **las 30 armas + datagen + recetas + set de cobre** | **SIGUIENTE** |
| 10 | especiales: multi-hit de alabarda **hecho**, parry de sable **hecho**, damage type de ignore-armadura | el martillo |
| 11 | supervivencia (`FoodData`): quitar exhaustion, curación de ciclo cerrado | pendiente |
| 15 | compat / conversión de armas de otros mods | pendiente |

## Decisiones tomadas

- **`refs/` dentro del repo pero gitignored.** Clones de terceros c/ su propio historial: meterlos al repo los volvería commits nuestros.
- **Raíz = repo del mod, rama `main`, git nuevo.** El clon de Combatify de la raíz se movió a `refs/combatify` c/ su `.git` intacto.
- **⊘ usar `atlas-core` ni `defaulted`.** Solo Fabric, no existen para NeoForge. Si hace falta patching de componentes vía datapack, se reimplementa.
- **Toolchain Java 21 aunque el JDK del sistema sea 25.**
- **Un solo módulo**, con el paquete de librería como subpaquete, extraíble después s/ romper nada.
- **Compat por capacidad, ⊘ por nombre de mod** (`DESIGN.md` §8).

## Mecánicas acordadas con el usuario

- **Velocidad de ataque dura:** ⊘ se ataca a medio cooldown, daño plano (se elimina el multiplicador de timing de vanilla, que premia spamear más rápido).
- **Carga** = bonus encima del base. **Nunca resta** — el piso se fuerza **en código**, ⊘ se confía en el dato (un override de config puede escribir cualquier bonus).
- **Dos indicadores separados:** el vanilla bajo la crosshair = **carga**; la barra alrededor de la crosshair = **cooldown**.
- **Autoataque carga y dispara:** mantener el click hace las dos.
- **Click derecho = movimiento especial por arma.** Tiene que hacer algo que el click izquierdo no puede, ⊘ más daño.
- **Alcance en cono frontal fijo** (mismo ángulo a 1 m q a 4 m), c/ distancia, ángulo horizontal y vertical independientes por arma.
- **Escudo:** 100% de reducción excepto explosiones, aguante de **3 golpes**, arco frontal solamente. Agotarse produce stun. El hacha stunea de un golpe.
- **Aguante en HUD:** 3 esferitas tenues sobre la crosshair, visibles al llevar algo que bloquea.
- **Supervivencia: la comida deja de ser energía.** ⊘ `exhaustion`: correr, picar y golpear no cuestan nada. La comida es **recurso de curación c/ ciclo cerrado**: se gasta al curar y se regenera sola. Regen 1 hp cada 2 s, cura hasta 6 de comida, comer se interrumpe al recibir daño (la regen pasiva ⊘), comida líquida 20 ticks. Pociones y manzana dorada rebalanceadas (curación I = 6, II = 12, manzana = 8).
- **Crafteo:** 5 grids canónicos, forma única por arma. Tiers **desiguales**: daga 7 (única c/ madera), ropera/alabarda/shotel 6, martillo 5 (s/ madera ni piedra). **30 items total.**
- **Set de cobre propio** (pepita, texturas, armas). Se reemplaza por el de Mojang al actualizar a la versión c/ copper age. Resolvió la "pepita de la punta" de la ropera.
- **Shotel = sable musulmán**, hoja curva. ⊘ es un cañón.
- **Cañón de mano** anotado como arma a distancia **fuera de alcance** (`DESIGN.md` 6º). Choca c/ que los proyectiles quedan fuera del cono.
- **5 armas nuevas:** ropera, alabarda, martillo de guerra, daga, shotel. Solo alabarda y shotel tienen click derecho. Solo alabarda, martillo y shotel cargan.
- **Aguante unificada escudo/espada:** la aguante solo se gasta al **recibir** un golpe bloqueado, **nunca** por mantener la guardia. De ahí sale, s/ caso especial, que la espada pueda cubrirse indefinidamente gratis.
- **Martillo de guerra:** escalado por golpe consecutivo, ventana 4 s, ⊘ tope. Golpe cargado ignora armadura.
- **Shotel: TERMINADO y verificado en juego.** Parry c/ cooldown 5 s que devuelve **solo** el golpe parriado, sin buffer acumulado. Y **la carga pega DOS GOLPES REALES**, cada uno a dano cargado (bonus 1.0), con dos cortes separados 8 ticks y medio bloque al costado, y dos sonidos distintos. El playtest lo confirmo como "perfecto, por fin".
- **El parry no se puede sostener y por eso cuesta caro.** 0,6 s de ventana (12 ticks), un press dentro de la ventana **no la renueva**, y 5 s de cooldown que arrancan en el press. La ventana frena al jugador fuerte (slowness 3) mientras está abierta. Motivo: el sable tiene el alcance más corto del juego y un bloqueo que no cuesta mantener sería un bloqueo mejor que la ropera. Detalle en “Lecciones”.
- **Feedback:** patrón de damageoverlay + indicador direccional de daño (⊘ existe en ninguno de los 3 mods).
- **La guardia es una POSTURA, ⊘ un pasivo.** Mientras está arriba **⊘ se puede golpear**: sin puntos, la postura ⊘ existe. Y ⊘ es un caso por item — c/ escudo en la off y espada en la main, bloquear y pegar gratis haría la off estrictamente mejor que la espada y disolvería la guardia unificada. Taparse cuesta siempre la opción de atacar; es lo que le da precio a todo lo demás.
- **Cubrir y comer a la vez es legal, c/ un orden fijo:** primero te cubrís, después comés. Funciona c/ un solo botón porque la guardia se lee de **cualquiera** de las dos manos y comer siempre sale de la principal → un press arranca las dos, la guardia resuelve al instante y la comida tarda 32 ticks, o sea el orden sale **por construcción**, ⊘ por arbitrar. Lo único prohibido es subir la guardia a mitad de mordisco.
- **Te pegan cubierto ⊘ interrumpe la comida.** ⊘ es una regla nuestra: en 1.21.1 el daño directamente ⊘ interrumpe comer (verificado c/ `javap`). Lo único a cuidar es ⊘ romperlo — y el primer bug que casi lo rompió fue nuestro.

## Arquitectura (`ARCHITECTURE.md`)

- **Todo el estado mutable de combate va en attachments**, nunca en campos de mixin. `AttachType` verificado en 21.1.250: tiene serialización, sync y `copyOnDeath`.
- **El cliente nunca decide.** Predice p/ dibujar, manda intención; el servidor resuelve. `CHARGE` se predice; `COOLDOWN` y `PARRY_READY` son del servidor.
- **⊘ ningún número de gameplay hardcodeado.** 4 data components: `weapon_profile`, `reach_profile`, `charge_profile`, `special` — separados p/ que un mod externo cambie el alcance s/ romper la carga.
- **`canCharge = false` ⊘ es un caso especial** en el código: el sistema consulta el componente. La ropera y la daga ⊘ cargan s/ una sola rama.
- **Se reemplaza el flujo de ataque vanilla entero.** El vanilla hace 4 cosas que el diseño ⊘ quiere (incluido `getAttackStrengthScale`); parchear las 4 = 4 mixins acoplados.
- **PRIORIDAD sobre mods de combate.** ⊘ cedemos el turno: hay **blocklist** de incompatibles, chequeada con `ModList` al iniciar. Si se detecta uno, el mod **se niega a cargar** c/ un mensaje que nombre el mod — **⊘ es un crash c/ stack trace** (un crash parece bug nuestro y ⊘ dice qué hacer). Arranca vacía y se llena c/ reportes; a diferencia de una compat list, ⊘ se pudre.
- **Convertimos armas ajenas pero ⊘ peleamos con sistemas ajenos.** No se contradicen.
- **Orden de 15 pasos por dependencia, ⊘ por dificultad.** `reach/` va antes del flujo de ataque porque es geometría pura testeable. `FoodData` al final, en commit propio.
- **Vetado:** compat list en código (sí se permite la **blocklist**, que es otra cosa: se niega a correr, ⊘ integra), bus de eventos propio, lógica de gameplay en cliente, `static mutable` para gameplay, mixins que reescriban media clase, archivo único de config autogenerada, ruido aleatorio por frame en el shake, crash c/ stack trace por incompatibilidad.

### Conversión de armas de otros mods (§12)

- **4 capas por prioridad:** data components → override del jugador → **derivación automática** → default. El override siempre gana.
- **La derivación automática ES la compat.** Lee `ItemAttributeModifiers` + perfil por tag + escala alcance por velocidad → un mod de armas c/ stats razonables entra **s/ una línea de código nuestro**.
- **Config procedural en DOS archivos:** `generated_weapons.json` (lo escribe el mod, ⊘ editar) y `weapon_overrides.json` (el jugador). Uno solo regeneraría y pisaría los cambios del jugador.
- **Va en JSON plano, ⊘ en `ModConfigSpec`.** No se pudo verificar si `ModConfigSpec` soporta mapas (sources de FML ⊘ están en el cache local) y un archivo autogenerado necesita precedencia explícita. `ModConfigSpec` queda p/ los números de balance estáticos.

### Framework de viewmodel (§11)

- **El framework es código, los perfiles son datos.** Machinery una vez, un perfil declarativo por arma, un solo mixin.
- Composición **aditiva** por capas: `vanilla + perfil + shake + fov`, cada una desactivable por config (mareo).
- Curvas propias (`back`, `expo`, `elastic`, `bounce`): `Mth` solo trae lineal/cuadrática/cúbica y ⊘ alcanzan p/ un golpe c/ peso.
- **Shake determinista sembrado por golpe.** Aleatorio por frame = el mismo golpe se ve distinto cada vez → se siente como ruido gráfico, ⊘ como animación.

## Decisiones de alcance

- **Proyectiles** (arco, ballesta, tridente lanzado): fuera del cono, comportamiento actual.
- **Mobs:** s/ cambios, sistema vanilla. Candidato nº1 a revisión después del vertical slice, empezando por el zombi.
- **Third person:** el viewmodel es solo 1ª persona. El servidor sigue balanceando → los demás jugadores ven el brazo de vanilla. Coherente, pero ⊘ es lo que el framework debería terminar siendo.

- **El cono lleva correa: la tolerancia sube con el tamaño de lo que golpeás, con tope.** `permitido = max(distancia · tan(ángulo), min(0,65 × semiextensión, 0,8))`. El cono angular se estrecha al acercarse, así que a un brazo de distancia la ropera era una ventana de 27 cm contra un zombi de 1,95 m. Cerca la holgura pasa a ser "el tamaño del bicho" y el cono vuelve a mandar a partir de 2,37 m de sus 3,4. El tope del 0,8 m es lo que evita que un dragón reciba 5 m de tolerancia. El eje **horizontal nunca fue el problema** — la caja del objetivo devuelve su propio medio ancho de holgura — y el **vertical** se mide desde el horizonte, no desde lo que se apunta.
- **Ninguna de las cinco familias nuevas cubre.** La guardia es escudo o espada, y una ropera, una alabarda, un martillo de guerra, una daga y un sable no son ninguna de las dos. Doble razón: la daga bloquearía estrictamente mejor que la espada (misma guardia, ataques más rápidos), que es la misma razón por la que un escudo en la off disolvería la guardia unificada; y el martillo es el arma de compromiso — si además bloquea gratis, no queda compromiso. La guardia quedó donde el diseño la puso: **la familia espada y los escudos de vanilla.**
- **El click derecho: gana el especial, y solo si nada puede cubrir.** `SpecialInputHandler` pregunta la guardia **primero y desde cualquiera de las dos manos**; con escudo en la off, el click derecho es bloqueo, como siempre. Con nada que bloquee, el especial lo toma. Un especial que le robara el botón a la guardia sería un especial que cambia de comportamiento según qué hay en la otra mano.
- **La alabarda barre lo que hay en el arco, no lo mejor que haya.** El click izquierdo ya saca un objetivo con todo su daño, así que un especial que hiciera más sería un click izquierdo estrictamente mejor. El daño por enemigo es el mismo y sin cargar, y el valor está entero en la cuenta. **Gasta el mismo cooldown duro**, así que un barrido reemplaza un golpe en vez de añadirse. El sonido y la partícula de barrido salen **haya o no haya enemigo** y se pagan igual: un especial que solo costara algo al conectar sería gratis practicarlo contra el aire.
- **El cobre es el de vanilla con nuestra textura, y la pepita es nuestra.** `assets/minecraft/models/item/copper_ingot.json` apunta a `cortescombat:item/copper_ingot` — un mod puede pisar un archivo del namespace `minecraft` porque los packs se apilan. **No hay item `cortescombat:copper_ingot`**: había uno y dejaba dos lingotes de cobre en el mundo. **La pepita de cobre no existe en vanilla 1.21.1** (`Items` tiene `GOLD_NUGGET` e `IRON_NUGGET` y ningún equivalente de cobre, y el jar de vanilla tampoco tiene modelo ni textura), así que es un item nuestro de verdad, y es la razón por la que la punta de la ropera es una pepita.
- **Los pools de sonido son datos, no código.** Tres archivos bajo una clave en `sounds.json` es sorteo nativo. `ModSounds` solo nombra eventos y calcula pitch, en una clase pura, y la sable es **la espada a 1,2** en hit/miss/cargado pero **no** en el cargo, que es compartido por los diez perfiles que cargan.
- **Partícula de golpe = la del arma; de golpe cargado = `ParticleTypes.CRIT` de vanilla.** El crit ya es una slash animada grande, así que el golpe cargado se distingue **por forma** y no por el mismo dibujo más fuerte. Nace en el **centro de la AABB** (⊘ `getY()`, que son los pies) con 30 partículas y spread que escala con el bicho.

- **El parry del sable: 0,6 s, no se sostiene, y cuesta caro.** `ParryState.WINDOW_TICKS = 12` (0,6 s) y `COOLDOWN_TICKS = 100` (5 s). Un press **dentro** de la ventana no la renueva: una ventana que un segundo press refresca no es un parry, es un bloqueo, y un bloqueo que no cuesta mantener es justo lo que el alcance más corto del juego estaba pagando. El lockout arranca **en el press**, no en el impacto, para que un parry que conecta y uno que no cuesten lo mismo.
- **El golpe parriado se anula Y vuelve, y ambos mitades son el arma.** Cancelar sin devolver sería un bloqueo, y el sable tiene el alcance más corto del juego precisamente porque está pagando por esto. Se devuelve **el mismo número sin modificar** — no el daño del sable, no un multiplicador. Y `consume()` cierra la ventana **sin reiniciar el cooldown**: si no, un press respondería a tres atacantes seguidos y el botón más caro del juego sería el más barato.
- **El parry se engancha con `LivingIncomingDamageEvent`, ⊘ con `LivingDamageEvent`.** Verificado en `LivingEntity#hurt`: el evento dispara en L1165 y el chequeo de `invulnerableTime > 10` en L1190 — **el evento va primero**. Un parriado montado sobre el evento tardío no vería el segundo golpe de un atacante rápido, o sea solo funcionaría contra el lento, que es el único caso donde no sirve. Cancelar ahí además vuelve **antes** de `blockUsingShield` y de todo el bookkeeping, así que un golpe anulado no deja iframes.
- **Reflejar con `playerAttack`, ⊘ con una fuente mágica**, para que cuente como ataque de jugador para todo lo que vanilla cuelga de eso: encantaciones que disparan, encantaciones de armadura que lo reducen, y el mensaje de muerte. Un reflejo que se salteara todo eso sería el golpe más fuerte del juego por un margen que nadie puede ver.
- **El martillo: 0,9 → 1,05 (22 → 19 ticks) y alcance 2,6 → 2,9 con el cono de 40/40 a 30/30.** El playtest lo llamó *inusable*, no *pesado*, y esa diferencia importa: "lento" es un costo que el jugador eligió. Más alcance y menos radio es el mismo trato que hace la sable al revés. **Los 30 grados no pueden bajar más:** la escalera paga sobre una ventana de 4 s de golpes consecutivos, y un arma que se mueve mucho más rápido que eso nunca alcanza su techo.
- **Los pools de sonido están bien y no era un bug de código.** Verificado de punta a punta: `sounds.json` tiene las 3 tomas con peso, los 35 `.ogg` son **bytes distintos** (hash), el jar los tiene todos, y `WeighedSoundEvents.getSound(RandomSource)` es el sorteo ponderado de vanilla. Lo único que quedaba eran las tres tomas, que suenan iguales entre sí — eso es arte, no código. Lo que **sí** estaba mal: `"replace": true` en los 20 eventos, que significa "piso la definición de otro pack con este nombre" y no tiene sentido en eventos nuevos de un mod. Fuera.
- **`CombatStatePayload` no daba lugar para el parry.** `StreamCodec.composite` corta en **6** componentes y ese record ya tenía 5, más los 2 del parry. Packet propio: corto, y además es lo honesto porque lo dibuja un solo arma de las trece.
- **La lentitud del parriado es un efecto de vanilla con las partículas apagadas**, amplifier 3 = −60% (`MOVEMENT_SLOWDOWN` es `−0.15` por nivel sobre `ADD_MULTIPLIED_TOTAL`). Un icono sería una promesa sobre un buff cuya causa el jugador no ve, y las partículas caerían justo donde tiene que poder leerse el golpe que entra.
- **Un track sobre un canal que nada puede mover es un comentario que cuesta una suma.** El perfil del sable tenía un track de `Channel.GUARD` que **nunca corrió**: el sable no cubre (`GuardProfile.NONE`), así que el canal estuvo en 0 en todos los frames de todas las sesiones. Ahora es el track del parry, y hay un test que exige que el sable no lea `GUARD` y que sea el único que lea `PARRY`.
- **El cooldown del parry reusa la rotura de la guardia: mismo canal, mismos números, y el tiempo va en la longitud.** `Channel.BREAK`, con el track **idéntico** al de la espada, y el total durando lo que el servidor dice que falta. El jugador ya sabe qué significa un arma fuera de línea — lo vio cuando le cayeron un escudo — y el sable diciendo lo mismo por otro motivo no necesita vocabulario nuevo. Un segundo juego de números para "esto no lo podés hacer ahora" sería una segunda cosa que aprender. Hay un test que exige que el offset del sable sea **igual** al de la espada.
- **Un canal global con estado que no es del item se filtra al item que sea.** El parry encendió el canal `BREAK` del sable, se cambió a una espada, y **la espada estuvo 5 s dibujándose colapsada** — porque la espada lee `BREAK` para su guardia. El jugador lo leyó como "la guardia se trabó" y cambió de arma dos veces más. Los canales son globales y el estado que los mueve es del **jugador**, no del item. ∴ el gate es `ViewModelProfile.reads(PARRY)`, ⊘ "¿es el sable?": la pregunta es qué puede hacer el perfil con la señal, sigue siendo cierta si el sable gana un segundo item, y es una propiedad del perfil que un test lee.
- **Borrar el estado y frenar el estado son cosas distintas.** `observeParry(cooldown = 0)` solo impide un bump **nuevo**; uno que ya corre sigue sus 100 ticks de largo. Cambiar de arma a mitad del lockout es justo cuando se ve, y por eso hay un `clearParry()` explícito. Es el mismo par de "el early return que silencia un paquete" y "el return de estado" que ya había aparecido dos veces.
- **Una rampa con rama `else` se aplica también al caso "nunca estuvo arriba".** La del parry calculaba `1 - 1/3` en su **primer tick** y arrancaba dos tercios fuera de línea; la de la guardia, `1 - 1/6`, o sea **la espada salía de línea los primeros 6 ticks de cada sesión** — un arma entrando volando al entrar al mundo. El invariante "todos los canales descansan en cero" no lo cubría porque ningún test arrancaba probando un motion virgin. ∴ `else if (channel != 0)` — **solo se cae desde arriba.** Lo encontró un test que itera veinte ticks pidiendo cero.
- **`setBlockedDamage(0)` NO bloquea todo: no bloquea nada.** El evento arranca con `dmgBlocked` en el **daño completo**, así que no llamarlo bloquea todo y llamarlo con 0 no bloquea nada. El comentario viejo en `GuardHandler` lo tenía anotado y el número nuevo de explosiones lo volvió a poner en juego: `original × (1 - blocked)`. Un 0,5 escrito a mano como "mitad" bloquea **nada**, que es el bug del que la primera versión del bloqueo melee se comprobó hace un año.
- **La posición de impacto de un proyectil está DENTRO del jugador, así que no puede juzgar el arco.** `DamageSource#getSourcePosition` de una flecha es el centro de la flecha en el momento del impacto, y para entonces ya está clavada en vos. `isFromFront` recibe un vector de longitud cero y lo **acepta a propósito** ("estar en el puño de alguien es lo menos 'detrás' que puede ser un golpe"), así que **una flecha por la espalda se leía igual que una de frente**: el escudo no distinguía. Ahora el arco se juzga sobre **el tirador**, que es la única dirección que significa algo.
- **Una explosión no tiene atacante, así que "sin atacante → no bloquea" se la comía entera.** La regla vieja era `origin == null → false` y una explosión no tiene `causingEntity`, o sea que por más que se sacara el early return de `IS_EXPLOSION` el blast seguía sin bloquearse. El daño se juzga sobre **dónde está la explosión**, que es lo único que tiene dirección. Y el bypass de `BYPASSES_SHIELD` queda exento para el blast, porque vanilla mete varios tipos en los dos tags y queríamos nuestra regla.
- **El resultado de "cuánto bloquea" es una fracción, ⊘ un booleano, desde el momento en que hay dos respuestas.** `blockedFraction` devuelve 0 / 0,5 / 1. La explosiva se lleva la mitad y **cuesta la misma aguante** que cualquier otro bloqueo, que es el punto: la guardia casi sirve contra un blast, así que la posición sigue importando. Con 0 era un creeper gratis y con 1 la guardia no tenía precio de posicionamiento.

- **El early return que silencia un paquete también silencia un reloj.** `CombatTicker.tickParry` preguntaba *"¿sigue abierta la ventana?"* y salía temprano cuando no, con el motivo razonable de que una ventana cerrada no es nada que reportar. Ese guard **también se tragaba la cuenta regresiva**: el estado queda en `(0, 100)` durante todo el lockout, así que el early return disparaba en cada uno de esos cinco segundos, el cooldown nunca bajaba, y el sable se podía parriar **una vez por sesión**. `ParryState#tick` nunca estuvo mal y el test sobre él estuvo verde todo el tiempo: lo que estaba mal era **la pregunta**, y por eso la pregunta ahora vive adentro del record (`step()` devuelve el estado Y si hay que avisar al cliente). Un caller que solo escribe lo que le devuelven no puede interpretar mal dos preguntas que parecían una.
- **Un `return` que parece de paquete es un `return` de estado.** Es el patrón dos veces en el mismo archivo: el que ya fixeó `observeStagger` y ahora el del ticker. La forma de no repetirlo es no decidir en el call site — que es lo que hizo `step()`.
- **Inferir un press de "el número subió" es adivinar, y el servidor ya te lo dice.** La v2 del bump del lockout reiniciaba cuando el cooldown crecía, y eso está mal por dos motivos a la vez: un parry que **conecta** manda el número completo una segunda vez (porque `consume()` cierra la ventana sin tocar el lockout), así que el bump reiniciaba **en el impacto** y el arma volvía a los 100 ticks en vez de a los 95; y el reloj del cliente corre contra el del servidor, así que el **jitter normal** entre los dos se lee como "subió" en un cierre que nadie presionó. **Solo una apertura arranca el bump.** El servidor es lo único que sabe que hubo un press, y lo dice abriendo la ventana.
- **Medir el invariante en vez de calcularlo a mano.** El test del lockout estaba escrito como una suma de rise y fall, y mi aritmética de ticks daba 95 donde esperaba 100 — no porque el código estuviera mal sino porque **el test contaba solo la cola y no el total**. La versión que vale mide "cuántos ticks estuvo el arma fuera de línea, desde el press", que es lo que el número del servidor promete, y eso no lo rompe un retune.

- **Reusar la curva NO es reusar el aspecto.** `sin(√f·π/2)` es *salida rápida, vuelta suave* **relativo a su propio span**: estirado a 100 ticks, la salida tarda 50. La hoja se iba de línea en medio segundo, y eso no es un arma a la que le pegaron, es un arma que están llevando. La primera versión del lockout usaba la curva de la rotura estirada y el test decía `0.156` donde se esperaba `> 0.5` — el número no estaba mal, **la forma** estaba mal, y un número que no se parece al que esperabas es la única clase de bug que un test con la cifra escrita no atrapa. ∴ rampa con hold: sale en `GUARD_RISE_TICKS` (2), queda fuera de línea en el medio, vuelve en `GUARD_FALL_TICKS` (6), y el total es el número del servidor.
- **El cliente tiene que contar el cooldown del parry él mismo.** El servidor habla **solo en transiciones** (100 al presionar, el resto al cerrar la ventana) y después 5 s en silencio. Un campo que solo guarda el último valor recibido queda con un número viejo no-cero durante todo el lockout, y el detector de flanco ve un "press nuevo" en cada frame: el bump se reinicia al terminar y el arma queda fuera de línea para siempre. **Es el artefacto exacto de `observeStagger`, al revés de la escala.** `ClientCombatState.tickParry()` lo cuenta y una corrección del servidor que llegue más alta gana.
- **Un componente en su sitio y su consumidor muerto no se distinguen leyendo.** El sable **no tenía track de `BREAK`**: la señal del lockout llegaba y no había nadie que la dibujara. Es la misma familia que `ropera()` y `dagger()` en la `TABLE` sin Encender. Un canal sin track es un canal que existe y no hace nada, y ahora hay un test que exige que el sable lo lea.

- **Las seis curvas normalizan, `Curve.SMOOTH.apply(0) == 0`.** Y un canal interpolado se lee en `snapshot(1.0F, ..)`: en offset 0 el valor es **del tick anterior** a propósito, que es lo que hace que un estado de 20 Hz se dibuje suave a 60 fps. Leer en offset 0 para comprobar el valor nuevo es un test que afirma que la rampa va un tick atrasada de lo que es.

## Trampas de diseño marcadas

- El autoataque carga y dispara → el ritmo ya es del jugador, lo que **consume** una fuente de profundidad (el timing). La profundidad restante tiene que venir de: elección de arma, momento del click derecho, lectura de la aguante del rival, gestión de la propia aguante. **⊘ reintroducir profundidad metiendo más timing.**
- El martillo **⊘ resetea** al recibir daño, por decisión. Es un arma de compromiso. Si queda demasiado fuerte, se cambia c/ el dato medido, ⊘ por intuición.
- Cubrirse ⊘ cuesta **aguante** (esa es la regla del diseño) pero **sí cuesta la opción de golpear**. Antes era un pasivo gratis y el playtest lo marcó como "un gravísimo error". Si el escudo queda inútil porque todos cubren c/ la espada, el problema es la densidad de aguante, ⊘ el click derecho.
- **`foodRegenInterval` es el número más importante del proyecto.** Rápido → la comida es decorativa. Lento → pelear es un callejón sin salida, que es justo lo que el mod viene a arreglar. Solo playtest lo resuelve.
- **`FoodData` es vanilla puro, s/ patch de NeoForge** (verificado). Los umbrales internos hay que leerlos de las fuentes, ⊘ de memoria.
- **La espada (3.6) alcanza más que hacha/pico/pala (3.0).** Invierte la relación de vanilla a propósito: la espada es el arma que se supone que tenés. Si hace que el hacha parezca inútil en vez de "para algo más corto", se ajusta el dato.
- **Las manos desnudas: menor alcance (1.9) y lo más rápido (4 ticks).** Cortas, veloces, casi inofensivas: la velocidad es lo único que las hace opción real s/ nada. `ATTACK_SPEED` base = 4.0 → 5 ticks s/ tocar nada, así que "lo más rápido" requería subir el tempo > 1.0.
- **El `verticalAngle` de cada perfil importa más de lo que parece.** C/ el cono siguiendo la mirada, ese número decide si un swing conecta c/ algo overhead o underfoot. Va a necesitar playtest cuando entren las armas nuevas.

---

## Lecciones — la parte que más vale

Cada una costó un bug. Están juntas porque se repiten: **todas son sobre la distancia entre lo que el código dice y lo que el juego hace.**

### Tests

- **Una clase que implementa `LayeredDraw.Layer` ⊘ se puede testear en JVM pelada** — falla al cargar, antes de la 1ª aserción. La matemática del widget vive en `ConeGauge` separada, por eso, ⊘ por gusto.
- **Component tests ⊘ ven callers.** Este es el más caro de todos: `GuardState` tenía sus 4 celdas testeadas ⊕ verdes mientras una guardia rota se rellenaba sola, porque el bug estaba en el argumento que le pasaba `CombatTicker`. **La decisión tiene que bajar a donde un test la alcance** (`GuardRules.regenPaused`).
- **Un test que falla puede tener razón el código.** Me pasó 3 veces: una aserción sobre el eje `up` que era imposible (es perpendicular a la mirada por definición → a 60° da 0.5, ⊘ 0.9); una sobre el relleno de la barra; y una donde escribí la comparación al revés (1 tick de caída con rampa de 2 es 0.83, ⊘ 0.5). Reconocer cuándo el test está mal es parte del trabajo.
- **Un test acoplado a datos provisorios es peor ⊘ tener test**: te entrena a cambiar el test. El del hand-off medía continuidad del offset *compuesto* c/ límite en unidades de item — equivalencia grados↔unidades arbitraria, y el límite derivaba de números escritos pero no jugados. **El invariante va en el canal, ⊘ en el offset compuesto.** Mismo patrón con el temblor: ahora el límite es la garantía que la interpolación da por construcción (`fase × rango`) + una aserción de la media por frame, que es la que distingue un temblor de un zumbido.
- **El invariante va en el canal.** "El wind-up no pierde más que un tick de valor en un frame" es exacto, independiente del perfil, y la versión ingenua del hand-off lo viola tirando el wind-up entero de golpe.
- **⊘ ignorar la infraestructura que hace el test correr.** DataFixerUpper en `testRuntimeOnly` (cualquier clase c/ `Codec` estático lo necesita; sin eso los tests dieben en `<clinit>` c/ un error que parece un test roto). Y `UseJUnitPlatform()`.
- **Los enums del proyecto ⊘ implementan `StringRepresentable`**: es una clase de Minecraft → cualquier cosa q los sostenga necesita el juego en el classpath y los tests mueren c/ `NoClassDefFoundError`. Usan `Codec` escrito a mano. (Me pasó, y violé la regla que yo mismo había escrito 2 pasos antes.)

### Bugs de razonamiento

- **Un `lerp` sin clampear se pasa del objetivo cuando el frame se alarga, y eso se lee como lag.** La
  vineta hacia el rojo interpolaba `alpha + (target - alpha) * dt * SPEED`. A 60 fps da 0,1 y nadie lo
  ve; con un tirón de framerate `dt` se va, el factor pasa de uno, **el alpha se pasa del objetivo**,
  al frame siguiente corrige y al otro se pasa otra vez. El playtest lo reporto como DOS sintomas --
  "parpadea" y "siente lag" -- y **es uno**: la oscilacion solo ocurre durante un tirón, asi que el
  stutter causa el parpadeo y el parpadeo se lee como mas stutter. **Ninguna constante de velocidad lo
  arregla**, porque el fallo es la forma de la interpolacion y no su ritmo -- tres rondas de
  retunear el rate lo habrian movido sin eliminarlo. Lo que lo arregla es `1 - e^(-rate * dt)`, que
  esta acotado por construccion y ademas es el unico que dura lo mismo a 30 que a 200 fps.
  La leccion general: **un sintoma que aparece "cuando hay lag" suele ser un bug de`dt`**, no un bug
  de rendimiento, y por eso buscar optimizaciones antes de mirar la formula pierde el playtest entero.
- **Un numero que se justifica con algo que cambio sobrevive a ese cambio.** El lunge movia al cliente
  con `step * 0.5`, un factor puesto para compensar que el teleport viejo se pasaba cada tick (0,48
  de 0,40 pedidos). Cuando el cliente paso a moverse solo, esa correccion se quedo y **cubrio la
  mitad de la distancia**. Es el mismo patron que las tres rondas anteriores de sintonar el gap del
  sable: un numero sobrevive a lo que lo justificaba. La pregunta util no es "que numero es" sino
  **"que estaba corrigiendo, y eso todavia pasa"**.
- **Un `Iterator` sobre una lista que el propio loop modifica tira `ConcurrentModificationException`.**
  `it.remove()` y despues `add()` sobre el mismo `ArrayList` bumpea su `modCount`. Craseo al primer
  golpe cargado, que fue la primera vez que existio el segundo sonido del sable: **clase escrita y
  nunca ejecutada**. La version que no crashea reconstruye la lista por indice.
- **GLFW ⊘ entrega el evento de "botón soltado" si la ventana pierde el foco con el botón apretado.** El press abrió un swing, el release nunca llegó, `holding` quedó `true` **por el resto de la sesión**: ⊘ atacar, ⊘ romper bloques, ⊘ interactuar, sin error en el log. Los 15 min de trace-mostraban los presses llegando y después cortando, y el log **no tenía ni una línea `[client]`** — la única que dice *qué puerta rechazó* está detrás de `/cc trace`. **La firma en el log:** en toda la sesión los holds fueron de 1 a 48 ticks; los dos últimos, justo antes de morir, de **142 y 84**. Siete segundos de botón de ataque no es un combate, es un botón que quedó apretado. ∴ `holding` y `predictedHolding` se reconcilian **contra el estado físico del botón cada tick** (`glfwGetMouseButton`), no contra el stream de eventos — y se cierra con `abandon()`, ⊘ `release()`, que pagaría un golpe contra un mundo que el jugador ya dejó. Es `InputLiveness`, puro y testeado, porque la parte no testeable (GLFW) es la parte IO y la decisión no.
- **Devolver `NOT_OURS` para silenciar una cosa entrega el click entero.** `press` tenía `if (!canCharge) return`, que para la ropera y la daga —los dos perfiles con `ChargeProfile.NONE`— ponía `claim = NOT_OURS`, y ahí el mixin deja de cancelar: **el ataque entero pasaba a vanilla.** Sin cono (para la ropera, que es toda su identidad), sin cooldown duro, y con `getAttackStrengthScale` de vanilla, o sea **spamear clicks pagaba más**. El comentario decía "sin barra de carga y sin wind-up" y el `return` no hacía eso: apagaba la barra entregando el click. Es la misma familia que `LivingShieldBlockEvent`.
- **Arreglar un workaround sin arreglar la capacidad que le faltaba convierte un bug en otro.** El gate existía por una razón real: **el cliente no tenía la guarda `if (!profile.canCharge()) return 0`** que sí tienen `AttackCooldown` y `CombatEngine`. Nunca hizo falta porque el gate impedía que el cliente viera el ratio de un arma sin carga. Quitado el gate, `chargeRatioFor(ticks, 0)` caía en la rama de `fullChargeTicks <= 4` — "ventana más corta que el hold mínimo, o sea siempre llena" — y una daga de un segundo de hold se reportaba **carga completa** a la crosshair y al FOV. Los dos números son ciertos y los dos entran por los mismos dos `int`: solo el arma puede separarlos. ∴ la guarda vive **en `AttackTiming`**, no en los tres call sites.
- **Estar en la `TABLE` de viewmodel no es prueba de que corra.** Los perfiles `ropera()` y `dagger()` estaban escritos, conectados y con tests, y **nunca renderizaron una sola vez**: dependen de que `holding` sea `true`, y el gate de arriba hacía que nunca lo fuera. Un componente que está en su sitio y su consumidor está muerto no se distinguen leyendo, ni con el build verde. La pregunta que faltaba no era "¿está el perfil?" sino "**quién enciende el canal**".
- **La mitad de un bug puede estar escondiéndose detrás de la otra.** El síntoma reportado —"la ropera y la daga no tienen animación"— se contestaba con un perfil nuevo, y la respuesta real era un `return` de tres líneas en otro archivo. Dos pedidos, un solo cambio, y el segundo se resolvió solo.
- **`Particle#setSize` no es el tamaño del quad.** `setSize(w, h)` cambia `bbWidth`/`bbHeight`, la **caja de colisión**, y `SingleQuadParticle#renderRotatedQuad` dibuja desde `quadSize`, cuyo default es `0.1..0.2`. El slash llevaba `setSize(0.9, 0.9)` con un comentario que decía "0,9 de ancho se ve bien" y se veía a **diez centímetros**: el código decía 0,9 y la pantalla no mostraba nada, que es la peor forma en que un número puede mentir — se lee bien y no hace nada. No hay setter público; `quadSize` es `protected` y se asigna directo. **Un número de tamaño que no cambia el tamaño.**
- **`registerSpriteSet` no bindea el sprite; `pickSprite` solo se llama desde el registro deprecado.** `ParticleEngine` llama `pickSprite` en **un** lugar: el overload deprecado `register(ParticleType, ParticleProvider.Sprite)`, que envuelve al provider y bindea por vos. `registerSpriteSet` solo guarda el provider → la partícula llega con `sprite == null` → los cuatro accesores de UV leen null. Y como los guardas de null devolvían 0, **no crasheaba: no se veía.** Dos lanzamientos buscando si el sprite no se ataba o el arte era demasiado suave, y las dos dan exactamente lo mismo en pantalla.
- **`BuiltInRegistries.SOUND_EVENT` no contiene los sonidos de un mod.** `SoundManager` los guarda en un `private final Map<ResourceLocation, WeighedSoundEvents> registry` propio y el motor los resuelve al reproducir. **Solo los de vanilla están en el registro**, así que buscar `cortescombat:sword_hit` ahí devuelve `null` para siempre, para todo sonido de mod, con el build verde. La forma correcta es `SoundEvent.createVariableRangeEvent(id)`. Y un nombre que no resuelve es exactamente tan silencioso como uno que sí: la línea de diagnóstico decía "el nombre no coincide" y mentía, porque el nombre coincidía y la forma de buscarlo estaba mal.
- **`SwordItem.createAttributes` toma un OFFSET del attack speed del jugador, no el total.** Vanilla escribe `createAttributes(tier, 3, -2.4F)` y le da 1,6, porque la base del jugador es 4,0. `WeaponFamily` guarda el **total** (la ropera es 2,0), así que pasado tal cual **sumaba**: base 4,0 + 2,0 = 6,0 → la ropera iba a **3 ticks entre golpes en vez de 10**, y las treinta armas al doble de ritmo. El test que existía no podía atraparlo: assertaba `ticksFor(family.attackSpeed())`, que es el número declarado afirmándose a sí mismo. ∴ `WeaponFamily.attackSpeedOffset()` vive al lado de los dos números, y un test pasa por la aritmética real del item.
- **Una guarda que convierte un crash en un bug invisible esconde el bug.** Las cuatro guardas de UV que puse para no matar el render thread por un sprite nulo son exactamente lo que convirtió "sprite no bindeado" en "no se ve nada" — y por eso costó dos launches. Un crash te lleva al bug en un paso; un quad invisible te lleva a sospechar del arte, que es una hipótesis indistinguible a simple vista.
- **`sound/` y `sounds/`, `textures/particle/` y `textures/particles/`: la misma trampa dos veces, en dos carpetas que se parecen.** El atlas de partículas de vanilla es `{"type":"directory","source":"particle","prefix":""}` — `textures/particle/`, **singular** — y el `sounds.json` resuelve los nombres contra `sounds/`, **plural**. En los dos casos la carpeta equivocada no da error: el juego simplemente nunca mira, y el síntoma es "no se ve" o "no se oye". El log del segundo lo decía 30 veces (`does not exist, cannot add it to event`) y el filtro lo había dejado pasar.
- **Un parámetro cuyo nombre es un verbo negativo se invierte callado.** `GuardState.tick(interval, regenPaused)` recibió `isActive() && !staggered` → "puedo usarlo" donde significaba "esto frena la regen". **Las dos ramas al revés a la vez**, y las dos se leen como negación, por eso pasó review.
- **Un botón que sobrevive algo que el jugador no hizo es bug por definición.** `held` es input, y sobrevivió al aturdimiento, a la muerte (`copyOnDeath`) y al login. Los tres se buscaron juntos por eso, ⊘ por casualidad.
- **"Si no lo reconocemos, tratémoslo como lo más común" es un default que convierte discretamente todo el mundo en un arma.** `deriveProfile` caía a `SWORD` → un bloque de tierra heredaba `charge_profile`. Por eso existe `WeaponProfile.NONE`, y es un perfil, ⊘ un `if`.
- **Una regla escrita mal y después "precisada" en vez de corregida se paga sola.** `GuardState.tick` llegó a 3 parámetros, un intervalo especial y un contador que saltaba entre dos rates — todo para describir un aturdimiento con otras palabras. La corrección fue *borrar* y dejar un flag.
- **Un número correcto ⊘ basta: el bug puede estar en la relación entre dos.** Tres rondas de playtest reportando "los corchetes están descentrados" c/ todos los números individualmente bien. El derecho quedaba **1px** más cerca del centro porque `(16+19)/2 = 17` ⊘ es el espejo de `4` en 23 de ancho (es `18`). **Una asimetría de 1px es invisible como número y muy visible como forma** — nada en el código se lee como errado y la vista insiste.
- **Un diagnóstico que recalcula la aritmética es una segunda copia de ella, y uno que miente es peor ⊘ tener ninguno.** `/cc hud` lee la **misma** `ConeGauge.geometry()` que dibuja el layer, por eso sirve.
- **Cuando un síntoma visual se repite y todos los valores se ven bien, el siguiente paso ⊘ es retunear: es imprimir la geometría y mirarla.** Y un recorte de screenshot se come la única referencia q la pregunta necesita (dónde está el centro) — dos capturas mías intentan deducir números y fallaron.
- **Un log limpio de errores ⊘ prueba que todo ande.** Casi reporto un bug falso: leí un `server-out.log` sin errores pero el build había fallado y ⊘ arrancó servidor. Hay que mirar que el build pasó **Y** que hay `Done (Xs)`, en ese orden.
- **Las 2 líneas DEBUG de un mixin que se queja de su firma son el aviso, ⊘ el ruido.** Igual que el signo del pitch: un aviso que no entendés ⊘ es un aviso que puedas borrar.
- **Cancelar un mecanismo de vanilla puede romper el que uno se apoya.** El bloqueo cuelga de `LivingShieldBlockEvent`, que solo corre con el item use vivo.

### Animación y render

- **Todo se evaluaba a 20 Hz, ⊘ por frame** — el item repetía cada posición 3 veces a 60 fps. Eso ⊘ es una animación, es una máquina dando pasos. Separado en `tick()` = avanza estado, `snapshot(partialTick)` = produce el frame (partial tick de `RenderHandEvent`).
- **Un canal tiene que medir su propia edad.** El hand-off lo destapó: `windup` crudo (feedback instantáneo) ⊕ `charge` con umbral (es-esto-una-carga) son **dos resoluciones del mismo hold, guardadas aparte**. FOV y cámara leen la thresholded; el wind-up lee la cruda. Un solo número significando dos cosas causó que el FOV quedara pegado.
- **El frame del release tiene que mostrar el item todavía atrás.** El primer tick después del release ya había avanzado → el wind-up bajaba un sexto antes de dibujarse nunca. Resuelto c/ `pendingStrike`: el release *anuncia* el strike y el primer tick lo arranca en edad 0.
- **`STRIKE` es un bump (0→1→0)**, ⊘ un canal que queda en 1. Si quedara en 1 dejaría el item desplazado para siempre y cada perfil necesitaría un 2º track para deshacer el suyo.
- **El hand-off del release** (lo difícil): el wind-up se desenrolla a lo largo del strike mientras el strike empuja atravesándolo → el item sale de la posición cargada en un movimiento continuo. Las dos versiones ingenuas se ven mal: carga→0 = teletransporte en el frame del golpe; carga→1 = el item va el doble de lejos y ⊘ vuelve.
- **Todos los canales descansan en cero, s/ excepción.** `Channel.COOLDOWN` cuenta el cooldown *gastándose*, ⊘ el ya gastado: con "listo = 1" el canal descansa en uno y necesita un caso especial. Un perfil que mueve el item con el jugador parado es un arma a la deriva.
- **Las 6 curvas normalizan `f(0)==0, f(1)==1`.** `Curve.LINEAR` no clampeaba, sola entre las seis → un canal un pelo negativo movía el item de la pose de vanilla. "Cero significa que este track no aporta nada" tiene que ser cierto de **todas**.
- **La longitud de un canal se elige para el movimiento, ⊘ se toma del reloj de otro sistema.** `GUARD_RISE_TICKS` era 11 por una teoría sobre peso; la guardia real sube un packet después del click, o sea 11 ticks eran latencia pura ("se siente poco responsivo"). Ahora 2, y la asimetría se dio vuelta: la caída (6) es la más lenta.
- **Sin timeline, por decisión.** Si alguna vez hace falta, sería *otro canal*, ⊘ un cambio de esta clase.
- **Un test acoplado a un reloj de otro sistema se rompe en cada retune.** Mismo patrón que el del hand-off.
- **`Quaternionf` scratch, un solo `mulPose`.** Ver API facts.
- **El HUD se dibuja del valor del servidor**, ⊘ de la predicción del cliente: si la barra está llena, podés pegarle. La carga vive en el indicador vanilla → los dos nunca compiten.
- **Fade por interpolación exponencial contra `getRealtimeDeltaTicks()`**, ⊘ lineal (la lineal stutter con framerate irregular). A factor fijo a 30 fps corre a la mitad de velocidad.
- **Sin socket y sin frente suave en la barra.** Los dos fallaron por el mismo motivo: un frente suave hacia adelante pone un pixel a brillo completo junto a la crosshair con el arma sin lista (un "listo" falso), y hacia atrás apaga las últimas columnas de la barra llena. La barra es **una línea, un gradiente, y el cooldown solo decide dónde termina.**
- **El brillo más fuerte en el centro de la pantalla tiene que ser lo que el jugador apunta c/ él.** Es el invariante que importa, y por eso barras y corchetes comparten presencia (`WIDGET_ALPHA`) aunque tengan alphas distintos: un widget cuyas dos mitades se atenúan distinto se lee como dos cosas no relacionadas.
- **`HudAnchor` es un único punto canónico** y todo lo demás es offset. Antes 4 layers c/ `guiWidth()/2` y un ajuste a ojo → alineación por coincidencia, que funciona en una resolución y se rompe en la siguiente.
- **El cono sigue la mirada**, marco ortonormal anclado a la mirada, ⊘ al suelo. Antes un punto a 20° de una mirada elevada a 60° salía a 36° y una espada de 35° lo rechazaba. Y el `rimPoint` del debug dibujaba una elipse a 90° del eje.
- **El widget es simétrico por construcción**: los dos corchetes se miden con `ConeGauge.mirrorAnchor` y un test exige que los anclajes sumen `width - 1`.

### Gameplay

- **Un press = un ataque, siempre.** El press abre, el release resuelve. En el press no se puede distinguir click corto de click sostenido → la decisión no se puede tomar ahí. Un click < `MIN_CHARGE_HOLD_TICKS` (4, **global**, ⊘ por arma) es **daño plano, sin excepciones**: una banda invisible donde un click de 3 ticks hace "un poco más" ⊘ se aprende mirando.
- **Un swing que falla igual cuesta el cooldown y gasta la carga** — si ⊘, mantener el click sería daño gratis spameando al aire.
- **Ataque gana sobre minar**, y el criterio es **lo que esté más adelante** (punto del bloque vs centro del objetivo), ⊘ "hay target". Decidirlo por "el crosshair está en un bloque" hacía que una pared *detrás* del enemigo se comiera el golpe; por "no hay objetivo", un zombi a tu espalda te impedía minar. Un swing que no hace nada es peor que uno que pega lo que no debe.
- **Un estado que dura más que el botón que lo creó** — la familia de bugs más repetida del proyecto. Doble click en un tick (→ cerrojo local `readyAtTick`; **el gate del cliente tiene que ser más estricto**), cambio de item a mitad de carga (40 ticks de maza se convertían en carga completa de espada), loguearse apretando. Se buscaron juntos por eso.
- **Los menús bloquean los ataques por completo**, incluido un swing en progreso. No es duplicar el check de vanilla: es **borrarlo** — `startAttack` abre c/ un guard de pantalla y cancelar en HEAD lo elimina, y el inventario ⊘ pausa en singleplayer.
- **Un tap ⊘ es una carga chica.** `TAP_FLOOR = 0.2` hacía que un click normal llegara al viewmodel como un quinto de animación cargada. Ahora libera cero, el perfil ⊘ aporta nada, y el click normal es **exactamente** el swing de vanilla → **la carga es lo diferente**, que es lo que la hace legible.
- **`chargeRatioFor` es un remapeo, ⊘ un clamp.** Debajo del umbral → 0 (nada se ve, nada pasa); arriba → `(ticks - umbral) / (ventana - umbral)`. Antes mostraba el ratio crudo: 8% durante 200 ms en un click de 2 ticks. **La barra mentía antes de tiempo y eso se lee como "el juego me está cobrando".** Y pasaron a ser **una** función en vez de dos — ya no pueden discrepar.
- **El click cargado no existía.** La carga se acumulaba en cada hold y `endCharge` la tiraba en el release → `DamageResolver` nunca vio un ratio distinto de 0. El síntoma ("se anteponen") era el medidor llenándose y después un click normal. **El multiplicador era código muerto y el indicador una promesa incumplida.**
- **La velocidad en la cuál el autoataque carga es del jugador** → no reintroducir timing como profundidad.
- **Falta el feedback del golpe cargado**: `CombatFeedback` solo tiene `HIT`, `KILL`, `GUARDED`. Un golpe a carga completa se ve **idéntico** a uno de carga cero, y el jugador tiene que inferirlo del número de daño — la lectura que el diseño de HUD evita.

---

## Estado técnico actual

**450 tests unitarios verdes.** Todo lo testeable corre en JVM pelada; lo que necesita el juego queda en `ConeGauge`, `ConeMath`, `AttackTiming`, `GuardRules`, `GuardState` y los `viewmodel/*` puros.

- **1 mixin** (`MinecraftAttackMixin`) c/ su seam de render (`ItemInHandRendererMixin`).
- **4 data components**: `weapon_profile` (12 perfiles), `reach_profile`, `charge_profile`, `special`.
- **6 attachments**: `charge`, `guard_stamina`, `cooldown`, `hammer_streak`, `parry`, `last_synced`.
- **7 canales** de viewmodel: `WINDUP`, `STRIKE`, `RECOVER`, `COOLDOWN`, `HOLD`, `GUARD`, `BREAK`.
- **6 clases puras + 1** que toca Minecraft en `viewmodel/`.
- **1 tipo de particula** para las 30 armas: el sprite viaja en el payload, asi que un arma nueva es un PNG y una linea. Tiras de 32x160 (5 frames) cortadas en los UV, sin partir archivos en 40 archivos.
- **Sonidos:** sounds.json con los pools como datos, ModSounds puro para nombres y pitch, CombatSounds para reproducir. Todos server-side.
- **3 capas GUI** (orden verificado c/ `VanillaGuiLayers`): `DamageOverlayLayer` (`registerBelowAll`), `CombatCrosshairLayer` (reemplaza la crosshair), `CooldownBracketLayer` y `GuardOrbsLayer` (`registerAbove(CROSSHAIR)`).
- Defaults de alcance y carga de los 12 perfiles en `WeaponStats` (tabla de `DESIGN.md` §2). La sable carga al doble (`bonus = 1.0`), la única.

### Deuda conocida

| Falta | Dónde está decidido | Nota |
|---|---|---|
| **Retroalimentación de "esto fue cargado"** | — | `CombatFeedback` solo tiene `HIT`/`KILL`/`GUARDED`. Cuando se implemente FOV y sonido hace falta un valor más (`CHARGED`, o un tono aparte): un HUD que reacciona a todo deja de significar nada, y ese enum es una lista cerrada a propósito |
| **FOV de carga** | `DESIGN.md` 512 | Va por `ComputeFovModifierEvent` (`setNewFovModifier(float)`, verificado). **Resta, ⊘ reemplaza** — pelearía con sprint y con cada mod que también toque FOV. Ya implementado, 3° → 0.6° c/ suavizado exponencial |
| Sonido de carga completa | `DESIGN.md` 524 | El proyecto tiene **un solo sonido**: `SHIELD_BREAK`. Faltan 3 de los 4 eventos que el jugador tiene que reconocer s/ mirar: carga completa, parry, escalado del martillo |
| Impacto de golpe cargado | `DESIGN.md` 499 | `addParticle` ⊘ aparece en ninguna parte del código |
| **Tercera persona** | — | Solo 1ª persona. El servidor sigue balanceando → los demás jugadores ven el brazo de vanilla |
| **Los 5 perfiles de las herramientas de vanilla** (hacha, pico, pala, maza, tridente) | **por decisión, ⊘ deuda | ViewModelDefaults los omite a propósito y con el motivo escrito: son items de vanilla, y un movimiento dibujado a mano sobre una silueta que no dibujamos pelea con el modelo. Sin entrada = pose exacta de vanilla. Los 7 perfiles que sí están son espada, manos, y **las cinco familias nuevas**: ropera, daga, alabarda, martillo y sable. Los 30 items caen sobre 13 WeaponProfile, así que el perfil es por familia y no por item |
| `ignoredArmour` del martillo | paso 10 | El flag se calcula y se testea, pero la fuente de daño sigue siendo la normal. `DamageSource` pide un `Holder<DamageType>` → exige un damage type registrado por datapack |
| `/cc weapons` desactualizado | paso 9 | La lista tiene 11 armas vanilla, no las 30 del mod |

### Texturas: DOS SIGUEN SIENDO DE `refs/`

Las de `assets/cortescombat/textures/gui/sprites/hud/` son **arte original del usuario**: `crosshair`, `crosshair_aim`, `crosshair_hit`, `cooldown_corchetes`, `charge_bar`, `charge_bar_emtpy`. `hotbar_attack_indicator_full.png` (icono de `combatify`) se borró: el cooldown ya no se dibuja c/ sprite sino c/ código alrededor de la crosshair.

| Archivo | Origen | Licencia | Riesgo |
|---|---|---|---|
| `sprites/hud/crosshair_shield_indicator_{full,disabled}.png` | `combatify` | LGPL-2.1 | Medio: o se reemplazan, o el mod queda atado a LGPL |
| `damage_overlay.png` | `neoforge-damageoverlay` | **All Rights Reserved** | **Alto.** No hay `LICENSE` y el autor no concedió permiso |

Detalle en `textures/gui/PROTOTYPE-ASSETS.md`. Para reemplazar: **misma ruta y mismo tamaño en píxeles** → no cambia código de render.

> **`cooldown_corchetes.png`:** el usuario lo rediseñó a una línea fina con huecos. Su alpha máximo bajó de **102 a 38**, así que `BRACKET_ALPHA` (0.30) se normaliza contra el pico del arte. Recordar: si se redibuja el arte, el número **no** hay que tocarlo.

### Harness: `/cc` (permiso 2)

```
/cc weapons        → 11 vanilla + las 30 nuestras, en la hotbar
/cc weapon <item>  → cualquier item, c/ autocompletado
/cc shield         → escudo en la mano
/cc fullguard      → restaura la aguante
/cc nofood         → comida a 2, p/ probar que la regen se frena
/cc speed <ticks>  → velocidad p/ alcanzar objetivos rápido
/cc teleport <n>   → te teletransporta n bloques hacia adelante
/cc stats          → IMPRIME las stats resueltas del arma en mano
/cc viewmodel      → ESTADO= del viewmodel
/cc cone           → toggle del cono de alcance
/cc trace          → toggle del trace por swing (off por default)
/cc gui            → toggle del HUD
/cc hud            → geometría del widget en píxeles (el que destrabó los corchetes)
/cc input          → las 7 puertas del press izq., en orden, + VEREDICTO
```

**`/cc input` es el que contesta "el click dejó de funcionar".** Imprime cada gate que puede rechazar un press —con el valor que consulta y su veredicto— en el mismo orden que `SwingInputHandler.press`, y una línea `VEREDICTO` con la primera puerta bloqueada. Existe porque un playtest reportó "no puedo golpear, romper ni interactuar" y el log **no tenía ni una línea `[client]`**: hay 8 lugares que rechazan un press, 6 silenciosos a propósito, y cuál quedó pegado no lo diferencia ni un log del servidor ni una captura. Lee los campos, **⊘ recalcula nada**.

`/cc stats` es el más importante: perfil, alcance (distancia + ángulos), cooldown, carga y especial. Si la derivación automática produce algo raro, se ve ahí en vez de adivinarlo en un combate.

> **Las tres sesiones que no produjeron nada útil:** el log tiene 2 statics en singleplayer, así que un flag que el comando cambia en el servidor **no es el flag que el cliente lee**. Por eso los payloads viajan. Varios playtests no dejaron líneas `[client]` porque faltaba correr `/cc trace`.

---

## Próximos pasos

1. **Paso 9: las 30 armas + datagen + recetas + set de cobre.** Es el paso grande que falta.
2. Paso 10: **el martillo ignorando armadura**, y es lo único que queda del paso 10 — la alabarda multi-hit y el parry de la sable están hechos. Exige un `DamageType` registrado por datapack (`data/cortescombat/damage_type/`), porque `DamageSource` pide un `Holder<DamageType>`. Es el único cambio de los que quedan que puede romper cosas por partida adentro.
3. Paso 11: supervivencia (`FoodData`) — el cambio más caro, en commit propio. Verificar los umbrales internos en las fuentes de 1.21.1 antes de escribir el mixin.
4. Paso 14 (lo que falta): sonido de carga completa, el valor CHARGED de CombatFeedback, **el golpe cargado** (la partícula ya está), tercera persona, y **afinar a playtest los números de los perfiles de viewmodel de las cinco familias nuevas**, que están escritos pero no jugados.
5. **La ropera** (`DESIGN.md` 1). Hoy: cono de 12/15 grados, alcance 3,4, 4,0 de daño base, 10 ticks entre golpes, **sin carga y sin click derecho**. Es el arma más rápida del set y la de menor alcance útil, y su único contenido es hacer clic. Tres ideas para darle profundidad están en la sección de decisiones; la animación de **estocada** es independiente de cuál se elija y se puede hacer igual.
6. Pendiente de decidir: extraer las tablas de `WeaponStats` a una clase pura, para que el orden de los alcances sea un test y ⊘ una acordada. Hoy ⊘ se puede testear porque el `<clinit>` de `WeaponStats` usa `Registries` y `TagKey`, que son de Minecraft.

---


## Changelog

- **Los 35 OGG del mod, q7 mono con ffmpeg.** Re-codificados de la versión mono con libsndfile a Vorbis q7 (≈ 192 kbps mono, VBR) usando el build full de gyan.dev extraído en `tools/ffmpeg/`. **Bitrate efectivo: 130 kbps mono** (Vorbis ahorra en los silencios de cada SFX corto, sin perder calidad en los picos). Tamaño en disco: **414 KB**, dentro del jar **413 KB**. Comparativa: stereo → 668 KB, libsnd mono → 327 KB, ffmpeg q7 mono → 414 KB — ~2.3× más bits que la versión libsnd y **−37%** que el stereo original. Comando: `ffmpeg -i input.ogg -ac 1 -c:a libvorbis -q:a 7 -f ogg tmp` (la `-f ogg` es por el temporal sin extensión). Pipeline PowerShell con `-f ogg` explícito, validación del header Vorbis antes del `Move-Item`. Build OK, 35/35 mono verificados en disco y en el jar.
- **Los 35 OGG del mod, stereo → mono (v1, libsndfile).** Downmix `0.5·(L+R)` con clip, mismo sample rate (44.1 kHz), mismos frames, recomprobación canal por canal antes de mover. **−51.1% en disco** (684 → 335 KB) y **327 KB** dentro del jar. Pipeline: `tools/stereo_to_mono.py` (Python + libsndfile) — backup automático a `sounds.stereo.bak`, verificación de cada `.tmp` antes del `os.replace`. AGENTS.md tenía razón sobre los pools (los 35 `.ogg` son bytes distintos), lo que estaba mal era el **número de canales** que se grabaron.
- **Cierre del paso 9 en cliente real. 407 tests.** Cerrado en una sola ronda: el cono, las partículas, los sonidos, el set de cobre, el click derecho y el tempo. **Cuatro bugs que costaron una vuelta de cliente cada uno y que ninguno era visible leyendo el código:**
  1. **El cono medía el eje vertical desde el horizonte, no desde lo que se apunta.** Apuntar a un zombi a 1 m es mirar 33° hacia abajo contra un límite de 15°, y el punto más cercano de la caja cae a la altura del ojo, así que la altura del bicho no contaba para nada. La ropera necesitaba **2,62 m con 3,4 de alcance**. Ahora la tolerancia es `max(d·tan, min(0,65×semiextensión, 0,8))` y la regla se explica en una frase: *si estás cerca, la tolerancia es el tamaño de lo que estás golpeando*.
  2. **`SwordItem.createAttributes` toma un offset del attack speed del jugador, no el total.** Las treinta armas iban al **doble de ritmo** (ropera a 3 ticks en vez de 10) y el test que existía no podía verlo porque assertaba el número declarado afirmándose a sí mismo.
  3. **`Particle#setSize` no es el tamaño del quad.** Cambia la caja de colisión; lo que dibuja es `quadSize`, con default 0,1–0,2. El slash llevaba `setSize(0.9, 0.9)` y se veía a diez centímetros. Y `registerSpriteSet` **no bindea el sprite** —solo lo hace el registro deprecado— así que llegaba con `sprite == null` y los guardas que evitaban el crash la hacían invisible. Dos hipótesis indistinguibles a simple vista, dos lanzamientos gastados en distinguirlas.
  4. **`BuiltInRegistries.SOUND_EVENT` no tiene los sonidos de un mod.** `SoundManager` los guarda en un `Map` privado; la forma correcta es `SoundEvent.createVariableRangeEvent(id)`. Todo el mod era silencioso con el build verde.
- **El click derecho dejó de estar ocupado por la guardia.** `GuardStats` decidía con `instanceof SwordItem`, y como las treinta armas son `SwordItem` **las treinta cubrían** — incluidas las dos con especial. Ahora el perfil de guardia es un **data component** por familia, y **ninguna de las cinco nuevas cubre**. El especial lo toma solo cuando nada puede cubrir, con escudo en la off sigue siendo bloqueo.
- **La alabarda barre lo que hay en el arco**, con su sonido y su partícula haya o no haya enemigo, gastando el mismo cooldown duro que un golpe.
- **El cobre:** el lingote es el de **vanilla con nuestra textura** (override de `assets/minecraft/models/item/copper_ingot.json`) y **la pepita es un item nuestro**, porque vanilla 1.21.1 no tiene pepita de cobre. Le faltan las dos recetas, que tampoco tenía.
- **Assets del usuario:** 5 `rapier_*` → `ropera_*`, 4 `knife_*` → `daga_*`, 37 `.flp` de FL Studio fuera del árbol de recursos (2,6 MB que iban al jar), `textures/particles` → `textures/particle` y `sound` → `sounds`.
- **Cinco tests que antes no podrían haber atrapar nada:** la aritmética del attack speed pasando por el offset real, la correa del cono con el caso crudo del reporte y el que verifica que ⊘ alcanza atrás, la lista de sprites contra el JSON de descripción, el reparto de la guardia, y los sonidos (que la sable es la espada a 1,2 **salvo en el cargo**).

- **Gate arreglado: la ropera y la daga vuelven al sistema.** `press` devolvía el click a vanilla para todo item sin carga, o sea los dos perfiles con `ChargeProfile.NONE`: sin cono, sin cooldown duro y con el multiplicador de timing de vanilla, que paga más por spamear. El cliente tampoco tenía la guarda de `canCharge`, así que la regla vivía en **cada** call site; ahora vive en `AttackTiming.chargeRatioFor(held, full, canCharge)` y los tres llaman la misma. Cuatro tests. Efecto lateral: los perfiles `ropera()` y `dagger()` **existían desde antes y nunca renderizaron**, porque dependen de `holding`, que el gate nunca dejaba ser `true`.
- Setup: 3 clones en `refs/`, repo del mod en la raíz. `DESIGN.md` v1–v6 c/ las mecánicas acordadas; `ARCHITECTURE.md` aprobado.
- **v6 → arquitectura:** estado por attachments, datos por data components, reemplazo del flujo de ataque vanilla, plan de 15 pasos, framework de viewmodel (capas aditivas, easing, shake determinista), conversión automática de armas ajenas c/ config procedural en dos archivos. Blocklist de incompatibles (se niega a cargar c/ mensaje accionable) en vez de compat list.
- **Fase 1 (esqueleto) verificada:** build OK, jar OK, carga en `runServer`. 4 trampas del build documentadas.
- **Fase 2 (registros) verificada en servidor:** `components=4 attachments=5`. 6 trampas de API documentadas.
- **Fase 3 (`reach/`)**: 21 tests. `ConeMath` en `double` puro s/ tipos de Minecraft → corre en JVM pelada. Bug encontrado: el ángulo vertical medido contra el eje local `up` fallaba al mirar recto arriba.
- **Fase 4 (cooldown duro)**: 35 tests. Resolver de 4 capas c/ derivación por tag (la compat), un solo `CombatTicker` para los 5 estados, `AttackCooldown` como puerta. Bug: `chargeMultiplier` con bonus negativo reducía el daño; el piso ahora se fuerza en código.
- **Fase 5 (flujo de ataque)**: 52 tests. **El vanilla ya no corre en el ataque.** Bug: los enums implementaban `StringRepresentable` y rompían los tests c/ `NoClassDefFoundError` → `Codec` propio.
- **Fase 6 (carga al input)**: jugable. Un payload C2S c/ un bool (⊘ un stream), el cliente predice y el servidor valida, clase `@Mod` client-only separada porque `InputEvent` ⊘ existe en servidor.
- **Fase 7 (HUD + harness)**: 3 capas GUI en el orden correcto + `/cc`.
- **Fase 8 (aguante)**: perfil por item, decisión en `LivingShieldBlockEvent`, stagger reusando el cooldown de vanilla, regen atada al reloj del cooldown. `GuardState` en 4 campos.
- **Reworks de HUD, 8 commits** (`2c2814e`–`632cf5a`), **124 tests**: crosshair de 3 estados c/ shake sembrado por evento, carga bajo la crosshair, `HudAnchor`, corchetes mostrando el **ángulo** del cono y no la distancia, tamaño por proyección angular, el cono siguiendo la mirada, `/cc cone` usando `ConeMath.frame()`. 5 bugs reales: el signo de la barra la dibujaba por fuera del corchete (invisible), el `rimPoint` dibujaba una elipse a 90° del eje, el cono anclado al suelo rechazaba lo que estabas mirando, el socket producía un "listo" falso, y el frente suave apagaba la barra llena justo en el corchete.
- **Optimización del cono:** escaneo throttleado a 3 ticks c/ **revalidación del objetivo cacheado cada tick**. El hallazgo: `CombatAim.target()` no tenía un solo llamador y el HUD solo lee un booleano → se pagaba una resolución completa por tick y se tiraba la entidad. La invalidación es lo que hace segura la frecuencia: lo único que puede quedar viejo es la *llegada* de un target distinto, y eso va a un hint. **La tolerancia es el throttle entero** — en cero, cada vibración del mouse cuenta como girar y la optimización parece gratis.
- **El click unificado, y el bug más grande encontrado.** El golpe cargado **no existía** (`endCharge` tiraba la carga en el release). `ChargeInputPayload` + `WhiffPayload` → un `SwingPayload(entityId, pressed)`, `PlayerAttackMixin` borrado, `MinecraftAttackMixin` en 3 líneas. El cliente manda el objetivo → ⊘ dependencia de orden entre paquetes.
- **Librería de viewmodel, adelantada antes de las 30 armas** (c/ una sola se debuggea fácil; c/ 30 es un desastre). 8 clases puras + 1 que toca Minecraft. **Sin timeline, por decisión.** 77 tests nuevos y 2 bugs reales: **todo se evaluaba a 20 Hz** (el item repetía cada posición 3 veces a 60 fps) → separado en `tick()`/`snapshot(partialTick)`; de paso un **off-by-one en el frame del release** → `pendingStrike`. Y `Curve.LINEAR` no clampeaba, sola entre las seis.
- **Reworks sobre playtest (2ª ronda)**, **228 tests**: el FOV se quedaba **pegado** porque `released` se seteaba una vez en el release y nunca se limpiaba (un follow-through terminado es el final de todo lo que la carga estaba moviendo, FOV incluido). Y "un click normal me lo detecta como cargado" **era la barra, no el umbral**: pintaba el ratio crudo, o sea mentía antes de tiempo. `chargeRatioFor` pasó a ser un remapeo y las dos funciones se fusionaron en una.
- **Tercera ronda — la guardia como postura. 292 tests.** Cinco bugs, cuatro de ellos números que describían algo que ya no era cierto: la regen invertida en el caller (⊘ los 3 escudos), `held` sobreviviendo aturdimiento/muerte/login, la comida cancelada por un scope nuestro, el escudo de vanilla subiendo sin puntos (c/ el arreglo obvio rompiendo el bloqueo), y golpear cubierto. `GUARD_RISE_TICKS` 11 → 2. Corchetes: la transparencia nunca fue el número (el arte se puso más tenue solo, 102 → 38) y el descentrado era un bug de **1px** en la relación entre dos anclajes. **`/cc hud` agregado** y fue lo que destrabó los corchetes.
- **Balance:** espada 3.2 → 3.6, manos desnudas 3.0 → 1.9 de alcance y tempo 1.25, daga tempo 0.7, espada 0.85. `GUARD_RISE_TICKS` 11 → 2. `BRACKET_ALPHA` 0.28 → 0.30 (con normalización contra el pico del arte).
- **⚠️ DEUDA LEGAL: quedan DOS texturas de `refs/`** — la viñeta (**All Rights Reserved**, sin `LICENSE`) y las dos esferitas de aguante (LGPL-2.1). Ver tabla en "Texturas".
