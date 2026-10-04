# AGENTS.md — Cortés Combat

Mod de NeoForge **1.21.1** c/ Java 21. Unifica el combate de Minecraft. Sin animaciones custom, sin
sistemas sobrecomplicados. Prioridad: **intuitivo p/ cualquiera, c/ profundidad q aparece al
aprenderlo**.

> Referencia técnica ⊕ memoria del proyecto, en un archivo. En Windows el FS es case-insensitive →
> `agents.md` = `AGENTS.md`; escribir uno pisa el otro. El diseño de combate vive en `DESIGN.md`, la
> historia en `CHANGELOG.md`.

---

# PARTE 1 — Referencia técnica

## Estructura

```
.                  → el mod (git propio, rama main, remoto GitHub PRIVADO)
DESIGN.md          → qué hace el juego (cerrado v6)
ARCHITECTURE.md    → cómo está construido (aprobado)
CHANGELOG.md       → historia: las tres cuentas largas + las iteraciones
refs/              → clones upstream, SOLO LECTURA, gitignored
  combatify/               Fabric, MC 26.1.2, Java 25  (port de Combat Test 8c)
  rafts_combat/            NeoForge, MC 1.21.1         (alcance horizontal + ataque c/ carga)
  neoforge-damageoverlay/  NeoForge, MC 1.21.1         (viñeta roja + flash de daño)
```

`refs/` gitignored a propósito: clones c/ su propio `.git`. **⊘ commitees ni edites.** Actualizar:
`git -C refs/<repo> pull`.

## Reglas duras

- **Target: NeoForge 1.21.1 / Java 21.** ⊘ subir. Combatify está en MC 26.1.2 c/ APIs q no existen
  acá → **traducir ideas, ⊘ copiar código.**
- **Mod id `cortescombat`**, paquete `com.cortes.combat`.
- **`mods.toml` va en `src/main/templates/META-INF/neoforge.mods.toml`**, expandido por
  `generateModMetadata`. ⊘ escribirlo a mano en `src/main/resources`.
- **Cada mixin nuevo va en `src/main/resources/cortescombat.mixins.json`.** El bloque `[[mixins]]`
  del TOML nombra el **archivo de config**, no cada mixin: una línea
  `config="${mod_id}.mixins.json"`, nunca se toca.
- **Mixins: 2.** `MinecraftAttackMixin` (flujo de ataque) y `PlayerModelMixin` (3ª persona).
- `neoforge.enabledGameTestNamespaces` = `mod_id` en los run configs. Tests nuevos usan ese
  namespace o ⊘ corren.
- **Red:** todo payload es `record implements CustomPacketPayload` c/ `Type`
  (`ResourceLocation.fromNamespaceAndPath(MOD_ID, "nombre")`) ⊕ `StreamCodec` estático. Registro
  centralizado. ⊘ `FriendlyByteBuf` a mano. **El record necesita `@Override type()`**: tener el
  campo `TYPE` ⊘ alcanza.
- **Config:** `ModConfigSpec` c/ `BUILDER.define*`, `SPEC` declarado **al final**. `CLIENT` y
  `SERVER` registrados por separado desde el constructor `@Mod`.
- ⊘ **Nada en `src/test` puede nombrar un tipo de Minecraft** (no está en el classpath de test).
  `DataFixerUpper` sí, vía `testRuntimeOnly`. **Los enums del proyecto ⊘ implementan
  `StringRepresentable`** (es clase de Minecraft) — usan `Codec` a mano.

## Comandos

```
gradlew build               # compila + jar
gradlew runClient           # cliente de dev
gradlew runServer           # servidor de dev
gradlew runGameTestServer   # corre gametests y sale
gradlew runData             # datagen → src/generated/resources
```

`gradlew` **sin `./`** (Windows). `org.gradle.configuration-cache=true`; si tocás un input de build
y falla por cache → `--no-configuration-cache`. JDK instalado **25**, toolchain pide **21**,
`foojay-resolver-convention` lo descarga. `neo_version=21.1.250` ya en cache.

## Las tres referencias

**`refs/combatify`** — el más rico, el más difícil de portar. Config: 13 clases (`KnockbackMode`,
`ArmourPiercingMode`, `AttackDecay`, `ProjectileDamage`) = catálogo de palancas.
`mixin/` (40+ servidor, 25 cliente) = índice de *dónde* se toca el combate. `resourcepacks/` ~15
packs que parchean datos vía `defaulted` → replicable c/ `data/` en vez de Java. Depende de
`atlas-core` y `defaulted` (solo Fabric): **⊘ no las vamos a usar**, reimplementar. Sus `README.md`
tienen la lista de cambios de combate más concreta que existe.

**`refs/rafts_combat`** — el más cercano. NeoForge 1.21.1, mixins, payloads, config partida. Ataques
c/ fase de carga: raycasts horizontales progresivos, área que golpea **todo** lo que atraviesa.
Bueno: c/ escudo en alto la carga sigue s/ apretar el click; se cancela cambiando de item;
knockback ⊘ aplica bajo 60% de carga. Limitaciones declaradas: desactiva Sweeping Edge a propósito.

**`refs/neoforge-damageoverlay`** — 4 clases. Patrón a copiar: `LayeredDraw.Layer` en
`RegisterGuiLayersEvent` c/ `registerBelowAll(rl("health"), layer)`. `@Mod(MODID, dist = Dist.CLIENT)`,
`ConfigurationScreen::new`. Fade por interpolación **exponencial** contra
`deltaTracker.getRealtimeDeltaTicks()`, y guardar `lastKnownHealth` p/ detectar el delta en vez de
suscribirse a eventos.

## Bugs / trampas de entorno

- `git clone` en PowerShell devuelve exit 1 y escribe `Cloning into '.'...` a stderr aunque
  funcione. **⊘ error**: verificar c/ `git log`.
- Combatify tiene **dos** bloques `loom {}`; el segundo sobreescribe `accessWidenerPath`. Solo
  aplica `combatify.classtweaker`. ⊘ copiar el `accesswidener` del primero.
- Escribir `agents.md` **pisa `AGENTS.md`**. ∴ todo vive en uno.

### Verificar una API: dos herramientas, y las dos son baratas

- **Preguntarle a `javac` es verificación, ⊘ adivinar.** Escribir el uso y compilar responde en un
  minuto: así se descubrió que **`ItemPropertyUtils` y `assignIndex` ⊘ existen en 1.21.1** (son de
  1.20); el API real es `ItemProperties.register(Item, ResourceLocation, ItemPropertyFunction)` y
  **⊘ hay `assignIndex`** — el modelo ⊘ guarda un número, la función lo *calcula* por frame desde la
  pila. Buscar la clase en el cache primero es más lento y no encuentra nada.
- **`javap -c` recupera código, ⊘ sólo firmas.** Los `ldc` en orden dan el número exacto de cada
  argumento. Sirvió para reconstruir un archivo perdido desde el `.class` del jar.
- **Las fuentes de vanilla SÍ están disponibles:** `build/moddev/artifacts/neoforge-21.1.250-sources.jar`
  (o `~/.gradle/caches/neoformruntime/intermediate_results/sourcesAndCompiledWithNeoForge_*.jar`,
  6.294 `.java`). `javap` p/ *confirmar* una firma; `jar xf` + **leer el método** cuando lo que
  importa es el comportamiento. ⚠️ son de la versión que compiló el cache (**21.1.250** acá), ⊘ "la
  1.21.1 genérica".

### Trampas del build

- **La expansión del TOML es Groovy.** Un backtick en los comentarios del template rompe el parser
  (`Unexpected input '('`) — error que apunta lejos de la causa. Comentarios en texto plano.
- **Toda propiedad usada en el TOML tiene que declararse** en el mapa `replaceProperties` de
  `generateModMetadata` en `build.gradle`. Falta una → `Missing property (x) for Groovy template
  expansion`.
- **`gradle.properties` ASCII puro.** Gradle lo lee como ISO-8859-1; un acento literal shippea
  `CortǸs Combat`. Usar escape Java: `mod_name=Cort\u00E9s Combat`.
- **`filteringCharset = 'UTF-8'`** en `generateModMetadata`, si no la salida usa el charset de la
  plataforma.
- ⊘ **`write`/`edit` corrompe texto insertado** (CJK y acentos). **Verificar siempre con escaneo
  CJK** antes de commitear.

### Datagen en 1.21.1 + NeoForge — seis trampas, cada una cuesta una ronda

1. **⊘ se puede heredar de `RecipeProvider`.** `getName()` es **`final`** y devuelve `"Recipes"`.
   NeoForge registra su `NeoForgeRecipeProvider` con ese nombre y el segundo es rechazado con
   `IllegalStateException: Duplicate provider: Recipes`, que **⊘ nombra mod ni clase**. ⊘ hay
   subclase que lo esquive y `--all` **⊘** es la causa. ∴ recipes = `DataProvider` plano. Modelos
   igual (`ModelProvider.run()` genera todo el juego; ⊘ hay seam).
2. **`createProvider(..)` YA llama `addProvider` adentro.** `event.addProvider(event.createProvider(X::new))`
   registra **dos veces** y el error se lee como "dos providers con el mismo nombre" cuando es uno
   registrado dos. Es lo que escondió el #1 detrás de un error distinto.
3. **`PackOutput.Target` no es un detalle.** `DATA_PACK` → `data/`, `RESOURCE_PACK` → `assets/`.
   Recetas/advancements c/ `RESOURCE_PACK` se escriben en `assets/` y **el juego nunca las lee**.
   Fallo totalmente silencioso.
4. **Los tags de vanilla necesitan el constructor de 5 args.** El de 3 usa modid `"vanilla"` y
   colisiona: `Duplicate provider: Tags for minecraft:item mod id vanilla`. El de 5 además decide el
   directorio de salida.
5. **`ExistingFileHelper` ⊘ sirve p/ preguntar "esto ya está dibujado a mano".** `exists(..)` busca
   **también en las fuentes de Mojang** → da `true` p/ `minecraft:item/handheld`, el parent de todos
   nuestros modelos → el provider no genera **nada**: 32 armas, 0 modelos, datagen "exitoso".
   `trackGenerated(..)` devuelve **`void`**: registra, ⊘ consulta. ∴ preguntárselo al filesystem, con
   el path derivado del output folder.
6. **⊘ de la API:** `#` en un grid **⊘ es celda vacía**, es un símbolo sin entrada en la key →
   `Pattern references symbol '#' but it's not defined in the key`. El vacío es un **espacio**;
   `WeaponShape.toRecipeRows()` traduce.

**Datagen, además:** `ShapedRecipe.Serializer.CODEC` existe y serializa el formato exacto.
`RecipeProvider` usa `Recipe.CONDITIONAL_CODEC` y `Advancement.CONDITIONAL_CODEC` en `WithConditions`,
`RecipeBuilder.ROOT_RECIPE_ADVANCEMENT` p/ el parent. `DisplayInfo` **⊘ tiene builder** (ctor de 8
args) y **sus title/description ⊘ aceptan `null`** — `Component.copy()` NPEa adentro del
serializador. `InventoryChangeTrigger.hasItems` **⊘ tiene overload de `TagKey<Item>`** →
`ItemPredicate.Builder.item().of(tag).build()`.

### Trampas de la API de 1.21.1 — todas verificadas, ninguna de memoria

**Registros, payloads, codecs**
- Data components: `DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MODID)`. ⊘
  `Registry.DATA_COMPONENT_REGISTRY`.
- `StreamCodec.composite` arranca en **1** componente (aridades 1–6). Payload de 0 campos →
  `StreamCodec.unit(instancia)`. ⊘ `ByteBufCodecs.VOID`.
- `StreamCodec.fromCodec` ⊘ existe en 1.21.1 → **`ByteBufCodecs.fromCodec(codec)`**.
- Enums: `ByteBufCodecs.idMapper(IntFunction, ToIntFunction)`.
- ⊘ `com.mojang.serialization.codecs.StringCodec` en el DFU 1.21.1 → `Codec.STRING.xmap(...)`.
- ⊘ `InputConstants.GLFW_PRESS` → `org.lwjgl.glfw.GLFW.GLFW_PRESS`.

**Entidades y daño**
- `Vec3` **⊘** tiene ctor de 2 componentes → `double[]` o `Vec3.atCenterOf(AABB)`.
- `Player#attack` desreferencia el target de una (offset 9, s/ null-check) → ⊘ hay forma de mandar
  un ataque al aire por ahí (el `WhiffPayload` que existía ya está fusionado).
- `ServerPlayer` **⊘** overridea `stopUsingItem` → el servidor ⊘ puede obligar al cliente a soltar
  un item. La animación de uso se predice en el cliente y ⊘ viaja. Hay que hacerlo en el cliente.
- **`ATTACK_SPEED` base = 4.0**, ⊘ 0. `AttackTiming.UNARMED_INTERVAL` = fallback p/ entidades s/
  atributo.
- `Entity.isRemoved()` existe, público final (⊘ `hasBeenRemoved`). `EntityTypeTest` en
  `net.minecraft.world.level.entity`.
- `Mth.clamp` **siempre 3 args** `(valor, min, max)`.
- `ItemStack.forEachModifier` toma `EquipmentSlot` o `EquipmentSlotGroup`, ⊘ `Holder<Attribute>`.
  Para el intervalo de ataque ni hace falta: `LivingEntity.getAttributeValue(ATTACK_SPEED)` ya
  incluye item, equipo y pociones.

**Item use — verificado en la ronda de la guardia**
- **El daño ⊘ interrumpe comer en 1.21.1.** `Player#hurt` son 106 bytes → chequea invulnerable,
  escala, devuelve `super.hurt`. ⊘ `addCooldown`; `LivingEntity#hurt`/`actuallyHurt` ⊘ llaman
  `stopUsingItem`. **Si dudás de una mecánica de vanilla: `javap` antes de poner un mixin encima.**
- **`Item#isEdible` ⊘ existe** (se fue en 1.20.5). Discriminador: **`ItemStack#getUseAnimation()`**
  (no-arg) contra `UseAnim` → `EAT`/`DRINK` comida, `BLOCK` escudo. Preguntar "usando un item" ⊘
  sirve: un escudo bloqueando también lo está, y gatear la guardia con eso la traba.
- **`PlayerInteractEvent.RightClickItem` dispara en LOS DOS LADOS**: `ServerPlayerGameMode#useItem`
  y `MultiPlayerGameMode#useItem` llaman los dos a `CommonHooks.onItemRightClick`. En el cliente
  corre **antes** de `ItemStack#use` y del packet — la rama cancelada hace `return null`, o sea el
  paquete ni se manda.
- **Un item use de vanilla se re-arma cada 4 ticks** (`handleKeybinds` tiene un 2º camino). ∴ un
  `stopUsingItem()` por tick **parpadea**. Pararlo ⊘ se arregla por tick: hay que negar el click en
  el origen.
- **Cancelar el uso de un mecanismo de vanilla puede romper el que uno se apoya.** El bloqueo vive
  en `LivingShieldBlockEvent`, que sólo dispara con el uso vivo → cancelar siempre = la guardia
  deja de bloquear. Cancelar **sólo cuando la guardia se niega**.

**Attachments y eventos**
- `AttachmentType.builder(Supplier<T>)` quiere supplier: `() -> ChargeState.EMPTY`, ⊘
  `ChargeState::EMPTY`.
- `SubscribeEvent` = `net.neoforged.bus.api.SubscribeEvent`. El paquete `neoforge.eventbus.api` ⊘
  existe.
- **Registrar en `NeoForge.EVENT_BUS` una clase s/ `@SubscribeEvent` aborta la construcción**:
  `IllegalArgumentException: class X has no @SubscribeEvent methods`. Si movés un seam de un evento
  a un mixin, el registro es lo que hay que borrar, y ⊘ avisa hasta que el cliente crashea.
- `copyOnDeath()` en un attachment está mal si el estado guarda **input**: `held` es un botón,
  sobrevive a la muerte y sube la guardia con aguante llena y s/ botón apretado.

**Render y anim — la fuente de más trampas**
- **El viewmodel se engancha con `RenderHandEvent`, ⊘ con un mixin.** Lo dispara
  `ClientHooks.renderSpecificFirstPersonHand(...)`, que `ItemInHandRenderer.renderHandsWithItems`
  llama **antes** de `renderArmWithItem`. Orden real: `bobbing de vista → [nosotros] → transform
  del brazo → transform del modelo` → nuestro transform **envuelve** todo lo de vanilla. ⊘ hay
  `ItemInHandRenderer.getItemTransform`; el `display.transform` viene horneado en el modelo.
- **`RenderArmEvent` existe pero `ItemInHandRenderer` ⊘ lo dispara.** La única llamada a
  `renderPlayerArm` es la rama del stack **vacío** → c/ item no hay brazo que animar. "Animar la
  mano" en 1ª persona = animar el item.
- **El arco de vanilla es `ITEM_SWING_X_ROT_AMOUNT = -80°`, ⊘ una traslación.**
  `applyItemArmAttackTransform` multiplica `XP.rotationDegrees(f6 * -80)` c/ `f6 = sin(√f·π)`. Una
  traslación mueve puño y punta por igual y **se lee como empujón sin importar cuánto la agrandés**;
  una rotación pivota en el puño y la punta barre un arco varias veces más largo. **Esa diferencia de
  velocidad *es* el peso.**
- **Una rotación pivota en el puño**, y la punta de una espada está a >1 unidad → por eso un pitch
  grande la saca de pantalla. **El yaw (≈90°) es lo que vuelve la hoja hacia el jugador**: "muy de
  lado" y "la hoja mira para el otro lado" son el mismo reclamo en ejes distintos.
- `Axis.rotationDegrees(float)` **aloca un `Quaternionf` por llamada** → reusar uno scratch c/
  `identity().rotateX(..).rotateY(..).rotateZ(..)`; `mulPose` lo lee antes de volver, o sea una
  instancia compartida es segura en el hilo de render. **Un `Quaternionf` por frame, no tres.**
- `GuiGraphics#blit` **bypassea la pipeline del GUI** (arma su propio `Tesselator` c/ `POSITION_TEX`
  y llama un `setShader` no-op bajo el pipeline system) → **el alpha de `setColor` se ignora**.
  `fill` sí va por `RenderType.gui()` y respeta alpha. ∴ los corchetes se dibujan c/ `fill`, ⊘
  `blit`.

**Partículas y sonidos**
- Atlas de partículas: `atlases/particles.json` = `{"sources":[{"type":"directory","source":"particle","prefix":""}]}`.
  O sea **`textures/particle/`** en **singular**, de **todos** los namespaces, y el id **⊘ lleva
  path**: `textures/particle/sword_hit.png` → `cortescombat:sword_hit`. El `prefix` vacío es la
  parte que uno da por hecho que repite el nombre de la carpeta.
- `CustomParticleOptions` **⊘ existe** en 1.21.1 → implementar `ParticleOptions` a mano, c/ los dos
  codecs (`MapCodec` y `StreamCodec`) y `getType()`.
- Registro de `ParticleType` = **de vanilla**: `Registries.PARTICLE_TYPE`. `NeoForgeRegistries.Keys`
  ⊘ tiene entradas de partículas, y `DeferredRegister.create` quiere un `ResourceKey` → equivocarse
  es **error de compilación**, ⊘ silencio.
- `Particle` y `ParticleRenderType` en **`net.minecraft.client.particle`**. Sus accesores de UV
  (`getU0`/`getV0`/…) son **`protected`** — lo único que permite cortar una tira sin partirla.
- `SpriteSet` **⊘ tiene `get(índice)`**. Tiene `get(age, lifetime)` (= `list.get(age*(size-1)/
  lifetime)`) y `get(RandomSource)`. **La lista ES la animación** — por eso vanilla tiene
  `sweep_0`..`sweep_7` como siete archivos, y una descripción por tipo **⊘ puede** llevar armas de
  varias familias.
- El sprite set **se puebla desde el JSON de descripción**, ⊘ desde el callback del provider. Sin
  `assets/<ns>/particles/<nombre>.json` el set queda vacío, `sprite` es `null` y la partícula muere.
- **Una tira vertical 32×160 c/ 5 frames ⊘ es un formato que el sistema lea.** El corte va en los
  UV, dentro de los límites del sprite — ⊘ contra 0..1, que da el quinto equivocado del atlas.
- `registerSpriteSet` **⊘ bindea el sprite**; sólo lo hace el registro deprecado `register(..,
  Sprite)`. Sin sprite, los guardas de null devuelven 0, **⊕ crashea: ⊘ se ve nada.**
- El `sounds.json` resuelve contra **`sounds/`** (plural). `sound/` (singular) es la carpeta vieja y
  ⊘ la mira nadie: síntoma = silencio total, y el log lo dice.
- Los eventos de un `sounds.json` de mod **⊘ están en `BuiltInRegistries.SOUND_EVENT`**.
  `SoundManager` los guarda en un `Map` privado. La forma de nombrarlos:
  **`SoundEvent.createVariableRangeEvent(id)`**.
- **Los pools de tres o más archivos son el mecanismo nativo**: tres nombres bajo una clave en
  `sounds.json` es un sorteo, sin código. La numeración es irrelevante una vez en la lista.

**Items y atributos**
- `SwordItem.createAttributes(Tier, float damage, float speed)` — el tercer argumento es un
  **`ADD_VALUE` desde la base 4,0** del jugador, ⊘ el total. Vanilla escribe `-2.4F` y le da 1,6.
  Pasar un total suma, y **las treinta armas quedan al doble de ritmo con el build verde**.

**Atlas — un archivo presente ≠ un archivo que el juego mire** (3ª vez en el proyecto)
- **`blocks.json` de 1.21.1 construye el atlas desde `block/` y `item/` y NADA MÁS.** Bajo
  `models/` ⊘ se cose nunca → cubo faltante. El archivo existe, el path está bien, el modelo es
  válido y el test de "la textura existe" está **verde**. El log lo dice en una línea:
  `Missing textures in model X#inventory`. Casos anteriores: `textures/particle/` vs `particles/`,
  `sound/` vs `sounds/`. **La pregunta que el test tiene que hacer es "¿el juego la mira?", ⊘ "¿el
  archivo está?"**

**Dimensión: el cono con correa**
- El cono angular se estrecha al acercarse, así que **a un brazo de distancia es un bolsillo**: la
  ropera a 1 m de un zombi son 27 cm contra un bicho de 1,95 m. Y el eje vertical se mide **desde
  el horizonte**, ⊘ desde lo que se apunta → mirar a un zombi a 1 m es mirar 33° hacia abajo.
- `ConeMath.leash(halfExtent)` = `min(0.65 × semiextensión, 0.8)`, y lo permitido es
  `max(distancia · tan(ángulo), correa)`. Cerca la holgura es la del bicho; el cono vuelve a mandar
  a partir del cruce. El tope del 0,8 evita que un dragón reciba 5 m de tolerancia.
- `ConeTargeting` (cliente) y `Reach` (servidor) pasan la correa **los dos** — el cliente manda
  sólo el id, así que si el servidor no la aplica promete un golpe y lo rechaza, y eso parece
  latencia.

**Otras**
- `Minecraft` **⊘** tiene `getDeltaTracker()` → `getTimer().getGameTimeDeltaPartialTick(false)`.
- `Minecraft#startAttack` abre c/ un guard de pantalla; cancelar en HEAD lo **borra**.
- `ItemCooldowns#getCooldownPercent(Item, float)` devuelve la fracción **restante** y **0 cuando
  está listo** (⊘ 1). El nombre es `getCooldownPercent`, ⊘ `getCooldownPercentage`.
- El cuerpo constant-specific de un enum **⊘** puede leer un campo de instancia: hace falta un
  `private static final`.
- Un record **⊘** puede tener un método que compite c/ un accessor de componente.
- Enum que implementa `StringRepresentable` tiene que implementar `getSerializedName()`;
  alcanzar c/ un `id()` ⊘ basta.

---

# PARTE 2 — Memoria del proyecto

## Fase activa: 3 — Vertical slice

Pasos 1–10 hechos ⊕ verificados en cliente real. Librería de viewmodel, guardia unificada
**cerrada como postura**, **las siete armas nuevas con sus animaciones**, y el **arcabuz como arma
31**. **660 tests verdes.** Animaciones **casi completas** según playtest. Receta del arcabuz y **tooltips de stats**, que reemplazan los de las armas vanilla también.

| # | Paso | Estado |
|---|---|---|
| 1–10 | esqueleto, registros, `reach/`, cooldown duro, flujo de ataque, carga, HUD, aguante, **las 30 armas + datagen + recetas + cobre**, especiales + `ignoredArmour` | HECHOS ⊕ verificados |
| 14 | framework de viewmodel | HECHO (falta sonido de carga, `CHARGED`) |
| 11 | supervivencia (`FoodData`): quitar exhaustion, curación de ciclo cerrado | pendiente |
| 15 | compat / conversión de armas de otros mods | pendiente |
| — | **arcabuz** (arma 31, fuera del catálogo de 30) | HECHO, sin receta de crafteo |
| — | tercera persona (`thirdperson/`) | HECHO y verificado |

## Decisiones tomadas

- **`refs/` dentro del repo pero gitignored.** Clones de terceros c/ su propio historial: meterlos
  al repo los volvería commits nuestros.
- **Raíz = repo del mod, rama `main`, remoto privado.** El clon de Combatify de la raíz se movió a
  `refs/combatify` c/ su `.git` intacto.
- ⊘ **usar `atlas-core` ni `defaulted`.** Sólo Fabric; reimplementar la idea si hace falta.
- **Toolchain Java 21 aunque el JDK del sistema sea 25.**
- **Un solo módulo**, con el paquete de librería como subpaquete, extraíble después s/ romper nada.
- **Compat por capacidad, ⊘ por nombre de mod** (`DESIGN.md` §8).

## Mecánicas acordadas con el usuario

- **Velocidad de ataque dura:** ⊘ atacar a medio cooldown, daño plano (se elimina el multiplicador
  de timing de vanilla, que premia spamear más rápido).
- **Carga** = bonus encima del base. **Nunca resta** — el piso se fuerza **en código**, ⊘ se confía
  en el dato.
- **Dos indicadores separados:** el vanilla bajo la crosshair = **carga**; la barra alrededor =
  **cooldown**.
- **Autoataque carga y dispara**: mantener el click hace las dos.
- **Click derecho = movimiento especial por arma.** Tiene que hacer algo que el click izquierdo no
  puede, ⊘ más daño.
- **Alcance en cono frontal fijo**, c/ distancia, ángulo horizontal y vertical independientes.
- **Escudo:** 100% de reducción excepto explosiones, aguante de **3 golpes**, arco frontal solamente.
  Agotarse produce stun. El hacha stunea de un golpe.
- **Aguante en HUD:** 3 esferitas tenues sobre la crosshair, visibles al llevar algo que bloquea.
- **Supervivencia: la comida deja de ser energía.** ⊘ `exhaustion` — correr, picar y golpear no
  cuestan nada. La comida es **recurso de curación c/ ciclo cerrado**: se gasta al curar y se
  regenera sola. Regen 1 hp cada 2 s, cura hasta 6 de comida, comer se interrumpe al recibir daño
  (la regen pasiva ⊘), comida líquida 20 ticks. Pociones y manzana dorada rebalanceadas (curación
  I = 6, II = 12, manzana = 8).
- **Crafteo:** 5 grids canónicos, forma única por arma. Tiers **desiguales**: daga 7 (única c/
  madera), ropera/alabarda/shotel 6, martillo 5 (s/ madera ni piedra). **30 items.**
- **Set de cobre propio.** Se reemplaza por el de Mojang al actualizar a la versión c/ copper age.
- **Shotel = sable musulmán**, hoja curva. ⊘ es un cañón.
- ⊘ **Cañón de mano** anotado como arma a distancia **fuera de alcance** (`DESIGN.md` 6º): choca c/
  que los proyectiles quedan fuera del cono.
- **5 armas nuevas:** ropera, alabarda, martillo de guerra, daga, shotel. Sólo alabarda y shotel
  tienen click derecho. Sólo alabarda, martillo y shotel cargan.
- **Aguante unificada escudo/espada:** la aguante sólo se gasta al **recibir** un golpe bloqueado,
  **nunca** por mantener la guardia. ∴ la espada puede cubrirse indefinidamente gratis.
- **Martillo:** escalado por golpe consecutivo, ventana 4 s, ⊘ tope. Golpe cargado ignora armadura.
- **Shotel:** parry c/ cooldown 5 s que devuelve **sólo** el golpe parriado, s/ buffer acumulado.
  Y **la carga pega DOS golpes reales**, cada uno a daño cargado (bonus 1.0), con dos cortes
  separados 8 ticks y medio bloque al costado, y dos sonidos distintos. Playtest: *"perfecto, por
  fin"*.
- **El parry ⊘ se puede sostener, y por eso cuesta caro.** 0,6 s de ventana (12 ticks), un press
  dentro de la ventana **⊘** la renueva, 5 s de cooldown que arrancan **en el press**. La ventana
  frena al jugador fuerte (slowness 3). Motivo: el sable tiene el alcance más corto del juego y un
  bloqueo que no cuesta mantener sería un bloqueo mejor que la ropera.
- **Feedback:** patrón de damageoverlay + indicador direccional de daño (⊘ existe en ninguno de los
  3 mods).
- **La guardia es una POSTURA, ⊘ un pasivo.** Mientras está arriba **⊘ se puede golpear**. Y ⊘ es un
  caso por item — c/ escudo en la off y espada en la main, bloquear y pegar gratis haría que la off
  sea estrictamente mejor y disolvería la guardia unificada.
- **Cubrir y comer a la vez es legal, c/ un orden fijo:** primero te cubrís, después comés. Funciona
  c/ un solo botón porque la guardia se lee de **cualquiera** de las dos manos y comer sale de la
  principal → un press arranca las dos y el orden sale **por construcción**, ⊘ por arbitrar. Lo
  único prohibido es subir la guardia a mitad de mordisco.
- **Te pegan cubierto ⊘ interrumpe la comida.** ⊘ es regla nuestra: en 1.21.1 el daño ⊘ interrumpe
  comer. Lo único a cuidar es ⊘ romperlo.

## Arquitectura (`ARCHITECTURE.md`)

- **Todo el estado mutable de combate va en attachments**, ⊘ en campos de mixin. `AttachType` en
  21.1.250 tiene serialización, sync y `copyOnDeath`.
- **El cliente nunca decide.** Predice p/ dibujar, manda intención; el servidor resuelve. `CHARGE`
  se predice; `COOLDOWN` y `PARRY_READY` son del servidor.
- ⊘ **ningún número de gameplay hardcodeado.** 5 data components: `weapon_profile`, `reach_profile`,
  `charge_profile`, `special`, `arcabuz_fuse` — separados p/ que un mod externo cambie el alcance
  ⊘ romper la carga.
- **`canCharge = false` ⊘ es un caso especial:** el sistema consulta el componente.
- **Se reemplaza el flujo de ataque vanilla entero.** El vanilla hace 4 cosas que el diseño ⊘ quiere
  (incluido `getAttackStrengthScale`); parchear las 4 = 4 mixins acoplados.
- **PRIORIDAD sobre mods de combate.** ⊘ cedemos el turno: hay **blocklist** de incompatibles,
  chequeada con `ModList` al iniciar. Si se detecta uno, el mod **se niega a cargar** c/ un mensaje
  que nombre el mod — **⊘ es un crash c/ stack trace** (un crash parece bug nuestro y ⊘ dice qué
  hacer). Arranca vacía y se llena c/ reportes; a diferencia de una compat list, ⊘ se pudre.
- **Convertimos armas ajenas pero ⊘ peleamos con sistemas ajenos.**
- **Orden de 15 pasos por dependencia, ⊘ por dificultad.** `FoodData` al final, en commit propio.
- **Vetado:** compat list en código (sí la **blocklist**, que es otra cosa), bus de eventos propio,
  lógica de gameplay en cliente, `static mutable` para gameplay, mixins que reescriban media clase,
  archivo único de config autogenerada, ruido aleatorio por frame en el shake, crash c/ stack trace
  por incompatibilidad.

### El lunge: un impulso, ⊘ una distancia

**En 1.21.1 el riptide no está en `TridentItem`** — el encantamiento sólo declara
`TRIDENT_SPIN_ATTACK_STRENGTH` y `TRIDENT_SOUND`. El movimiento está entero en
`TridentItem#releaseUsing`, tres líneas: `player.push(...)` + `startAutoSpinAttack(20, 8.0F, stack)`
+ un saltito de 1.2. Lectura completa en `CHANGELOG.md`.

**Lo que cambió el diseño:** un dash en Minecraft es **un impulso y el rozamiento hace el resto**.
Nuestra tabla de distancia exacta (`ticksFor`, `FIRST_STEP`, `RETENTION`) resolvía un problema que
el motor no plantea. Números que quedaron: `BASE_IMPULSE 1.0` + `0.02/tier`, techo
`impulso × 6.0 × 3.0` (tope anti-trampas, ⊘ promesa), golpe = **cubo de `alcance × 0.5`** centrado
en el jugador, fin **medido** (`STILL = 0.02`) con tope duro 20, inmunidad doble
(`invulnerableTime` 12 ticks/tick **⊕** `MobEffect LUNGE` que cancela en `LivingIncomingDamageEvent`,
que dispara **antes** del chequeo de `invulnerableTime`).

**2b — el cliente se mueve, el servidor mide después.** Razón: el servidor puede reconstruir todo
⊘ menos el movimiento, así que no se le pregunta "¿puedo moverte?" sino "¿esto fue legal?" — gratis
y s/ rollback. El material se guarda **en el estado**: leer el item vivo dejaría que cambiar a una
ropera de madera en el tick 14 colapsara la tolerancia de un lunge ya legal.

### Conversión de armas de otros mods (§12)

- **4 capas por prioridad:** data components → override del jugador → **derivación automática** →
  default. El override siempre gana.
- **La derivación automática ES la compat.** Lee `ItemAttributeModifiers` + perfil por tag + escala
  alcance por velocidad → un mod de armas c/ stats razonables entra **s/ una línea nuestra**.
- **Config procedural en DOS archivos:** `generated_weapons.json` (lo escribe el mod, ⊘ editar) y
  `weapon_overrides.json` (el jugador). Uno solo regeneraría y pisaría los cambios del jugador.
- **Va en JSON plano, ⊘ en `ModConfigSpec`** (⊘ se pudo verificar si soporta mapas; sources de FML ⊘
  están en el cache). `ModConfigSpec` queda p/ los números de balance estáticos.

### Framework de viewmodel (§11)

- **Machinery código una vez, perfiles datos.** Un perfil declarativo por arma, un solo mixin.
- Composición **aditiva** por capas: `vanilla + perfil + shake + fov`, cada una desactivable por
  config (mareo).
- Curvas propias (`back`, `expo`, `elastic`, `bounce`): `Mth` sólo trae lineal/cuadrática/cúbica y
  ⊘ alcanzan p/ un golpe c/ peso.
- **Shake determinista sembrado por golpe.** Aleatorio por frame = el mismo golpe se ve distinto
  cada vez → ruido gráfico, ⊘ animación.

## Decisiones de alcance

- **Proyectiles** (arco, ballesta, tridente lanzado): fuera del cono, comportamiento actual.
- **Mobs:** s/ cambios. Candidato nº1 a revisión después del vertical slice, empezando por el zombi.
- **Third person:** el viewmodel es 1ª persona; el servidor sigue balanceando → los demás
  jugadores ven el brazo de vanilla. Coherente, pero ⊘ es lo que el framework debería terminar
  siendo.
- **El cono lleva correa:** `permitido = max(distancia · tan(ángulo), min(0,65 × semiextensión,
  0,8))`. Cerca la holgura pasa a ser "el tamaño de lo que golpeás"; el eje **horizontal nunca fue el
  problema** y el **vertical** se mide desde el horizonte.
- **Ninguna de las cinco familias nuevas cubre.** La guardia es escudo o espada, y una ropera, una
  alabarda, un martillo, una daga y un sable no son ninguna de las dos. Doble razón: la daga
  bloquearía estrictamente mejor que la espada (misma guardia, ataques más rápidos), que es la misma
  razón por la que un escudo en la off disolvería la guardia unificada; y el martillo es el arma de
  compromiso — si además bloquea gratis, no queda compromiso.
- **El click derecho: gana el especial, y sólo si nada puede cubrir.** `SpecialInputHandler` pregunta
  la guardia **primero y desde cualquiera de las dos manos**; c/ escudo en la off sigue siendo
  bloqueo.
- **La alabarda barre lo que hay en el arco, ⊘ lo mejor que haya.** El click izquierdo ya saca un
  objetivo con todo su daño. El daño por enemigo es el mismo y sin cargar. **Gasta el mismo cooldown
  duro**, así que un barrido reemplaza un golpe en vez de añadirse. El sonido y la partícula salen
  **haya o no haya enemigo**: un especial que sólo costara algo al conectar sería gratis practicarlo.
- **El cobre es el de vanilla con nuestra textura**, y la pepita es nuestra (`minecraft:copper_nugget`
  ⊘ existe en 1.21.1). El lingote se reskin con `assets/minecraft/models/item/copper_ingot.json` (los
  packs se apilan).
- **Los pools de sonido son datos, ⊘ código.** `ModSounds` sólo nombra eventos y calcula pitch.
- **Partícula de golpe = la del arma; de golpe cargado = `ParticleTypes.CRIT` de vanilla**, que ya
  es una slash animada grande → el golpe cargado se distingue **por forma**. Nace en el **centro de
  la AABB** (⊘ `getY()`, que son los pies).
- **El parry del sable:** 0,6 s, ⊘ se sostiene, 5 s de cooldown. El golpe parriado **se anula Y
  vuelve** — se devuelve **el mismo número sin modificar**. Y `consume()` cierra la ventana **⊘**
  reiniciando el cooldown. Se engancha con `LivingIncomingDamageEvent`, ⊘ `LivingDamageEvent`,
  porque el evento va **antes** del chequeo de `invulnerableTime` (L1165 vs L1190).
- **Reflejar c/ `playerAttack`, ⊘ una fuente mágica**, para que cuente como ataque de jugador para
  encantaciones que disparan, las de armadura y el mensaje de muerte.
- **El martillo: 0,9 → 1,05 (22 → 19 ticks), alcance 2,6 → 2,9, cono 40/40 → 30/30.** El playtest
  lo llamó *inusable*, ⊘ *pesado*, y esa diferencia importa: "lento" es un costo que el jugador
  eligió. **Los 30 grados ⊘ pueden bajar más**: la escalera paga sobre una ventana de 4 s de golpes
  consecutivos.
- **El hacha = dos damage types, ⊘ una con dos flags.** `warhammer_ignored_armour` (en
  `bypasses_armor`) vs `axe_charged` (en **ningún** tag, sólo se reconoce). El axe cargado conserva la
  armadura y la destroza al doble.
- **El desgaste doble de armadura es evento, ⊘ mixin:** NeoForge ya reemplazó el loop de
  `LivingEntity#doHurtEquipment` por `CommonHooks.onArmorHurt` + `if (true) return;`. El loop original
  es código muerto. `ArmorHurtEvent.ArmorEntry.newDamage` es mutable.
- **Cargar ⊘ cambia el tempo**, y un golpe cargado **siempre pierde en DPS** contra tocar rápido
  (aritmética, ⊘ gusto). ∴ el axe quedó en bonus 2.0 / carga 60 (21 dmg = 3 golpes sin cargar, 86%
  del tapping), ⊘ en 1.2/70 (44% menos: la carga era una trampa).
- **El hacha ⊘ lleva `damageScale`**: vanilla ya da 7 dmg a 1.0 → menos de la mitad del throughput de
  la ropera. El test lo afirma en vez de multiplicar.
- **Un modelo base por familia** (`models/weapon/alabarda.json`) + item models finos generados, en
  vez de 6 copias a mano.

### Trampas de diseño marcadas

- **El autoataque carga y dispara** → el ritmo ya es del jugador, lo que **consume** una fuente de
  profundidad (el timing). La profundidad restante tiene que venir de: elección de arma, momento
  del click derecho, lectura de la aguante del rival, gestión de la propia aguante. **⊘ reintroducir
  profundidad metiendo más timing.**
- El martillo **⊘ resetea** al recibir daño, por decisión. Si queda demasiado fuerte, se cambia c/
  el dato medido, ⊘ por intuición.
- Cubrirse ⊘ cuesta aguante (regla del diseño) pero **sí cuesta la opción de golpear**.
- **`foodRegenInterval` es el número más importante del proyecto.** Rápido → la comida es decorativa.
  Lento → pelear es un callejón sin salida. Sólo playtest lo resuelve.
- **`FoodData` es vanilla puro, s/ patch de NeoForge** (verificado). Los umbrales internos hay que
  leerlos de las fuentes, ⊘ de memoria.
- **La espada (3.6) alcanza más que hacha/pico/pala (3.0).** Invierte la relación de vanilla a
  propósito: la espada es el arma que se supone que tenés.
- **Las manos desnudas:** menor alcance (1.9) y lo más rápido. Cortas, veloces, casi inofensivas.
- **El `verticalAngle` de cada perfil importa más de lo que parece.** C/ el cono siguiendo la mirada,
  ese número decide si un swing conecta c/ algo overhead o underfoot.
- ⊘ **La guardia NO rompe la comida**: en 1.21.1 el daño ⊘ interrumpe comer, así que la regla es
  automáticamente cierta. Lo único a cuidar es ⊘ romperlo.

## Lecciones — la parte que más vale

Cada una costó un bug. Están juntas porque **todas son sobre la distancia entre lo que el código
dice y lo que el juego hace**, o entre lo que el test afirma y lo que el jugador ve.

### Tests

- **Una clase que implementa `LayeredDraw.Layer` ⊘ se puede testear en JVM pelada** — falla al
  cargar, antes de la 1ª aserción. La matemática vive en `ConeGauge` separada, por eso, ⊘ por gusto.
- **Component tests ⊘ ven callers.** `GuardState` tenía sus 4 celdas verdes mientras una guardia rota
  se rellenaba sola, porque el bug estaba en el argumento que le pasaba `CombatTicker`. **La decisión
  tiene que bajar a donde un test la alcanza.**
- **Un test que falla puede tener razón el código.** 3 veces: un eje `up` imposible a 60°, una
  aserción sobre el relleno de la barra, y una comparación escrita al revés. Reconocer cuándo el
  test está mal es parte del trabajo.
- **Un test acoplado a datos provisorios es peor ⊘ tener test.** El invariante va **en el canal**,
  ⊘ en el offset compuesto.
- **El invariante va en el canal.** "El wind-up no pierde más que un tick de valor en un frame" es
  exacto, independiente del perfil.
- ⊘ **ignorar la infraestructura que hace el test correr**: DataFixerUpper en `testRuntimeOnly`
  (cualquier clase c/ `Codec` estático lo necesita), `UseJUnitPlatform()`.
- ⊘ **Los enums del proyecto no implementan `StringRepresentable`** — `NoClassDefFoundError` en
  `<clinit>`, que parece un test roto.
- **Y un test que afirma la convención equivocada es peor que ⊘ test**: uno decía *"un strike va
  negativo y un wind-up va positivo"* — **afirmaba que un wind-up levanta el brazo hacia atrás**, y
  estaba verde mientras el playtest reportaba exactamente eso. Cuando la decisión se revela mala, la
  memoria se reescribe, y lo nueva tiene que decir **qué es la regla** y ⊘ qué número es.
- **Un techo que protege lo que no necesita.** El de 60° en las poses **prohibía una motion que
  vanilla ya hace** (su swing llega a 68.8), y por eso el golpe salía como una puñalada. Los techos
  tienen que mirar el rango real de la API de referencia, ⊘ un número redondo.

### Bugs de razonamiento

- **Un `lerp` sin clampear se pasa del objetivo cuando el frame se alarga, y eso se lee como lag.** La
  vineta interpolaba `alpha + (target-alpha)*dt*SPEED`: a 60 fps da 0,1 y nadie lo ve; con un tirón
  `dt` se va, el factor pasa de uno, **el alpha se pasa** y oscila. El playtest lo reportó como DOS
  síntomas —"parpadea" y "siente lag"— y **es uno**. **Ninguna constante de velocidad lo arregla**:
  lo arregla `1 - e^(-rate*dt)`, acotado por construcción. **Un síntoma que aparece "cuando hay lag"
  suele ser un bug de `dt`, ⊘ de rendimiento.**
- **Un número que se justifica con algo que cambió sobrevive a ese cambio.** El lunge movía al
  cliente con `step * 0.5`, un factor para compensar que el teleport viejo se pasaba cada tick. Al
  pasar a moverse solo, la corrección **cubrió la mitad de la distancia**. La pregunta útil ⊘ es
  "qué número es" sino **"qué estaba corrigiendo, y eso todavía pasa"**.
- **Un `Iterator` sobre una lista que el propio loop modifica tira `ConcurrentModificationException`.**
  La versión que no crashea reconstruye por índice.
- **GLFW ⊘ entrega el evento de "botón soltado" si la ventana pierde el foco.** El press abrió un
  swing, el release nunca llegó, `holding` quedó `true` **por el resto de la sesión**: ⊘ atacar, ⊘
  romper, ⊘ interactuar, sin error en el log. **La firma: holds de 142 y 84 ticks al final de la
  sesión.** ∴ `holding` y `predictedHolding` se reconcilian **contra el estado físico del botón cada
  tick** (`glfwGetMouseButton`), ⊘ contra el stream de eventos — y se cierra con `abandon()`, ⊘
  `release()`, que pagaría un golpe contra un mundo que el jugador ya dejó.
- **Devolver `NOT_OURS` para silenciar una cosa entrega el click entero.** `press` tenía
  `if (!canCharge) return`, que para la ropera y la daga ponía `claim = NOT_OURS` y el mixin dejaba
  de cancelar: **el ataque entero pasaba a vanilla.** El comentario decía "sin barra de carga" y el
  `return` apagaba la barra entregando el click.
- **Arreglar un workaround sin arreglar la capacidad que le faltaba convierte un bug en otro.** El
  gate existía porque al cliente le faltaba `if (!profile.canCharge()) return 0`. Quitado el gate,
  una daga de un segundo de hold se reportaba **carga completa**. La guarda vive **en `AttackTiming`**,
  ⊘ en los tres call sites.
- **Estar en la `TABLE` de viewmodel ⊘ es prueba de que corra.** `ropera()` y `dagger()` estaban
  escritas, conectadas y con tests, y **nunca renderizaron**: dependen de que `holding` sea `true`, y
  el gate de arriba hacía que nunca lo fuera. La pregunta que faltaba ⊘ era "¿está el perfil?" sino
  **"quién enciende el canal"**.
- **La mitad de un bug puede estar escondiéndose detrás de la otra.** "La ropera y la daga no tienen
  animación" se contestaba con un perfil nuevo; la respuesta real era un `return` de tres líneas en
  otro archivo. **Dos pedidos, un solo cambio.**
- **`Particle#setSize` ⊘ es el tamaño del quad.** Cambia la caja de colisión; lo que dibuja es
  `quadSize` (default 0.1–0.2). El slash llevaba `setSize(0.9, 0.9)` y se veía a **diez centímetros**:
  el código decía 0,9 y la pantalla no mostraba nada, **que es la peor forma en que un número puede
  mentir.**
- **Un componente en su sitio y su consumidor muerto ⊘ se distinguen leyendo.** El sable **⊘ tenía
  track de `BREAK`**: el canal estaba en cero en todos los frames de todas las sesiones. Un track en un
  canal que nada puede mover es un comentario que cuesta una suma.
- **Las guardas que convierten un crash en un bug invisible esconden el bug.** Las cuatro guardas de
  UV que puse para no matar el render thread por un sprite nulo son lo que convirtió "sprite no
  bindeado" en "no se ve nada". **Un crash te lleva al bug en un paso; un quad invisible te lleva a
  sospechar del arte.**
- **`SwordItem.createAttributes` toma un OFFSET del attack speed, ⊘ el total.** El test que existía
  no podía atraparlo: assertaba `ticksFor(family.attackSpeed())`, **el número declarado afirmándose a
  sí mismo**. ∴ `attackSpeedOffset()` vive al lado de los dos números y un test pasa por la
  aritmética real del item.
- **"Si no lo reconocemos, tratémoslo como lo más común" convierte discretamente todo el mundo en un
  arma.** `deriveProfile` caía a `SWORD` → un bloque de tierra heredaba `charge_profile`. ∴ existe
  `WeaponProfile.NONE`, y es un perfil, ⊘ un `if`.
- **Una regla escrita mal y después "precisada" en vez de corregida se paga sola.** `GuardState.tick`
  llegó a 3 parámetros para describir un aturdimiento con otras palabras. La corrección fue **borrar**
  y dejar un flag.
- **Un número correcto ⊘ basta: el bug puede estar en la relación entre dos.** Tres rondas reportando
  "los corchetes están descentrados" c/ todos los números individualmente bien. El derecho quedaba
  **1px** más cerca del centro. **Una asimetría de 1px es invisible como número y muy visible como
  forma.**
- **Un diagnóstico que recalcula la aritmética es una segunda copia de ella, y uno que miente es peor
  ⊘ tener ninguno.** `/cc hud` lee la **misma** `ConeGauge.geometry()` que dibuja el layer.
- **Cuando un síntoma visual se repite y todos los valores se ven bien, el siguiente paso ⊘ es
  retunear: es imprimir la geometría y mirarla.** Y un recorte de screenshot se come la única
  referencia que la pregunta necesita.
- **Un log limpio de errores ⊘ prueba que todo ande.** Casi reporté un bug falso: leí un
  `server-out.log` sin errores pero el build había fallado y ⊘ arrancó servidor. Hay que mirar que el
  build pasó **Y** que hay `Done (Xs)`, en ese orden.
- **Las 2 líneas DEBUG de un mixin que se queja de su firma son el aviso, ⊘ el ruido.**

### Herramientas

- ⊘ **`write`/`edit` corrompe texto insertado.** CJK y acentos. **Escaneo CJK antes de commitear.**
- **Reescribir un archivo entero para cambiar un párrafo es la operación más peligrosa que tengo.**
  Documentando el arcabuz reemplacé `.gitignore` completo y **me llevé las reglas de `refs/`,
  `build/`, `run/` y `tools/ffmpeg/`**; `git add -A` empezó a agregar repos embebidos y 600 MB de
  ffmpeg. Lo agarró el aviso *"adding embedded git repository"*. **En un archivo de reglas, cada
  línea que no toqué sigue siendo una regla que no puedo ver.**
- **Un parche por índice de línea es un parche por número.** Encadené varios y perdí firmas de
  métodos y llaves de más — **dos veces la misma noche**. El síntoma es `illegal start of type` en una
  línea que no parece tener nada. La respuesta es `javac`, ⊘ más parches: el error dice qué falta.
- **Y `git checkout -- <archivo>` no es una revert, es una apuesta.** Revertí un archivo que yo había
  roto y **borré trabajo de la ronda anterior, nunca commiteado**. Lo recuperé con `javap -c` contra
  el jar del último build — **suerte pura**: si no hubiera rebuild justo antes, estaba perdido.
- ⊘ **Un commit no es un commit. Es una red.** El costo de no commitear ⊘ es olvidarse: es que una
  operación reversible deje de serlo.
- **La salida de los comandos es información, ⊘ decoración.** Los tres accidents de arriba se evitaron
  o se recuperaron por mirarla, ⊘ por tener cuidado.

### Animación y render

- ⊘ **Todo se evaluaba a 20 Hz.** El item repetía cada posición 3 veces a 60 fps. Eso ⊘ es una
  animación: es una máquina dando pasos. Separado en `tick()` = avanza estado, `snapshot(partialTick)` =
  produce el frame.
- **Un canal tiene que medir su propia edad.** El hand-off lo destapó: `windup` crudo (feedback
  instantáneo) ⊕ `charge` con umbral (es-esto-una-carga) son **dos resoluciones del mismo hold**.
  Un solo número significando dos cosas causó que el FOV quedara pegado.
- **El frame del release tiene que mostrar el item todavía atrás.** El primer tick ya había avanzado.
  Resuelto c/ `pendingStrike`: el release *anuncia* el strike y el primer tick lo arranca en edad 0.
- **`STRIKE` es un bump (0→1→0)**, ⊘ un canal que queda en 1.
- **Todos los canales descansan en cero, s/ excepción.** `COOLDOWN` cuenta el cooldown *gastándose*,
  ⊘ el ya gastado: con "listo = 1" el canal descansa en uno y necesita un caso especial.
- **La longitud de un canal se elige para el movimiento, ⊘ se toma del reloj de otro sistema.**
  `GUARD_RISE_TICKS` era 11 por una teoría sobre peso; la guardia real sube un packet después del
  click, o sea 11 ticks eran latencia pura. Ahora 2, y la asimetría se dio vuelta: la caída (6) es la
  más lenta.
- **Sin timeline, por decisión.** Si alguna vez hace falta, sería *otro canal*, ⊘ un cambio de esta
  clase.
- **Un test acoplado a un reloj de otro sistema se rompe en cada retune.**
- **`Quaternionf` scratch, un solo `mulPose`.**
- **El HUD se dibuja del valor del servidor**, ⊘ de la predicción del cliente.
- **Fade por interpolación exponencial contra `getRealtimeDeltaTicks()`**, ⊘ lineal.
- ⊘ **Sin socket y sin frente suave en la barra.** Un frente suave hacia adelante pone un pixel a
  brillo completo junto a la crosshair con el arma sin lista (un "listo" falso).
- **El brillo más fuerte en el centro de la pantalla tiene que ser lo que el jugador apunta c/ él.**
- **`HudAnchor` es un único punto canónico** y todo lo demás es offset.
- **El cono sigue la mirada**, marco ortonormal anclado a la mirada, ⊘ al suelo.
- **El widget es simétrico por construcción**: los dos corchetes se miden con `ConeGauge.mirrorAnchor`
  y un test exige que los anclajes sumen `width - 1`.

### La ronda de la 3ª persona — las convenciones que estaban al revés

Detalle completo en `CHANGELOG.md`. Lo que queda acá es **la regla**, porque son las cosas que no se
deducen de un número:

1. **El cuerpo del jugador trabaja en GRADOS y el mixin convierte a radianes.** Sumarlos sin convertir
   es lo que produjo la voltereta: 46 grados como 46.0 son 46 vueltas.
2. **Negativo es adelante y arriba.** Leído de `setupAttackAnimation` (`xRot -= f1 * 1.2`, que llega
   a −69°) y de `poseRightArm`'s `ITEM` (`xRot * 0.5 − π/10`). **Positivo es hacia atrás.** El roll
   lateral también es negativo, de `zRot += sin(attackTime·π) · −0.4`.
3. **El TAIL suma encima de vanilla; hay que cancelar en HEAD.** El renderer asigna `attackTime` *antes*
   de `setupAnim`, y sólo si el perfil es nuestro.
4. **Las mangas son `ModelPart` aparte** y vanilla las copia del brazo *antes* de nuestra inyección.
5. **Un bump ⊘ puede ser un tajo** (canal `CHOP`, que sólo baja), y **la puerta es `reads(CHOP)`**, ⊘
   una lista de armas.
6. **`pendingStrike` retrasa un tick**, y para una ALTURA eso es un frame en reposo. `CHOP_START` es la
   **razón** entre las dos alturas, ⊘ un número elegido.
7. **La caída tiene que acelerar.** `(1−t)²` sale a tope y frena — un arma arrojada. `1 − smoothstep`.
   Y 4 ticks, ⊘ 6: el impacto es velocidad sobre distancia y la distancia nunca fue el problema.
8. **El golpe es de un brazo; la guardia y la carga usan los dos**, porque son postura y reunión.

## Estado técnico actual

**660 tests unitarios verdes.** Todo lo testeable corre en JVM pelada; lo que necesita el juego queda
en `ConeGauge`, `ConeMath`, `AttackTiming`, `GuardRules`, `GuardState`, `ConeSize`, `WeaponTooltip`
y los `viewmodel/*` puros.

- **2 mixins**: `MinecraftAttackMixin` (ataque) c/ su seam `ItemInHandRendererMixin`, y
  `PlayerModelMixin` (3ª persona, 2 inyecciones).
- **Tooltips de stats, en TODAS las armas ⊘ vanilla.** `CombatTooltipHandler` (2 eventos) +
  `ConeSize` + `WeaponTooltip` + `TooltipLine`. Reemplaza los 2 renglones de vanilla
  (`GatherSkippedAttributeTooltipsEvent` por **id de modificador**, ⊘ por texto) c/ 7 renglones.
  Cero componente de Minecraft en la capa que decide.
- **5 data components**: `weapon_profile` (14 perfiles), `reach_profile`, `charge_profile`, `special`,
  `arcabuz_fuse`.
- **6 attachments**: `charge`, `guard_stamina`, `cooldown`, `hammer_streak`, `parry`, `arcabuz`,
  `last_synced`.
- **14 canales** de viewmodel: `WINDUP`, `STRIKE`, `CHOP`, `RECOVER`, `COOLDOWN`, `HOLD`, `GUARD`,
  `BREAK`, `PARRY`, `LUNGE`, `SWEEP`, `HELD`, `RELOAD`, `SHOOT`.
- **9 perfiles** en `ViewModelDefaults`: espada, manos, ropera, daga, sable, alabarda, hacha,
  martillo, arcabuz. **Un arma chop (lee `CHOP`) o thrust (lee `STRIKE`)**, y la puerta lo declara el
  perfil.
- **1 tipo de partícula** para las 30 armas: el sprite viaja en el payload → un arma nueva es un PNG y
  una línea. Tiras de 32×160 (5 frames) cortadas en los UV.
- **Sonidos:** `sounds.json` c/ los pools como datos, `ModSounds` puro, `CombatSounds` para
  reproducir. Todos server-side.
- **3 capas GUI** (orden verificado c/ `VanillaGuiLayers`): `DamageOverlayLayer` (`registerBelowAll`),
  `CombatCrosshairLayer` (reemplaza la crosshair), `CooldownBracketLayer` y `GuardOrbsLayer`
  (`registerAbove(CROSSHAIR)`).
- Defaults de alcance y carga de los perfiles en `WeaponStats` (tabla de `DESIGN.md` §2).

### Paquetes que se agregaron después del mapa original

| Paquete | Qué es | La trampa que tiene |
|---|---|---|
| `thirdperson/` | `BodyPoses` (ángulos), `BodyPose` (record de 8 + `lerp`), `BodyPoseDispatcher` (PREVIOUS/CURRENT) | **Todo en GRADOS y el mixin convierte.** Arg 7 = `bodyPitch`, arg 8 = `bodyYaw` |
| `mixin/PlayerModelMixin` | HEAD cancela `attackTime`, TAIL suma la pose | HEAD **obligatoria**. Firma de **6** parámetros, sin `partialTick`; `T` erasa a `LivingEntity`; la clase debe ser `public abstract` |
| `special/Arcabuz*.java`, `state/ArcabuzState` | arma 31: mecha, disparo, transición | La **Firestone** se gasta **al ENCENDER** |
| `client/ArcabuzModelProperties` | registra `cortescombat:fuse`, que elige el frame | Va en `FMLClientSetupEvent`, ⊘ en el constructor: el item no está bound |
| `attack/ArmorWearHandler` | doble desgaste del hacha cargado, vía `ArmorHurtEvent` | El loop de vanilla ya es código muerto (`if (true) return;`) → ⊘ hay mixin |
| `special/HalberdSweep` | números del barrido, puros y testeados | `COOLDOWN_FACTOR 4.0` — gasta 4× el cooldown |
| `item/tooltip/` | `TooltipLine` (record), `WeaponTooltip` (7 renglones, decide todo), `ConeSize` (6 bandas) | **`Component` es de Minecraft → ⊘ el tooltip se construye como `List<TooltipLine>`** y sólo la capa del evento lo pasa a `Component`. Los bordes de `ConeSize` (20/29/37.5/50/65) son **el punto medio de los huecos** de los ángulos reales, ⊘ números redondos |
| `client/CombatTooltipHandler` | 2 eventos: `skipId` los 2 de vanilla + agrega los 7 | Va en `net.neoforged.neoforge.event.entity.player.ItemTooltipEvent` (⊘ `event.item`). `skipId`, ⊘ `skipGroup`/`skipAll`: se llevarían los encantamientos |
| `data/ArcabuzRecipes` | el grid del arcabuz, vertical | **`#` ⊘ es celda vacía**: es un símbolo sin entrada en la key. El vacío es un **espacio**, y Minecraft **no** recorta el borde: escribe `" /V"`, `" O "`, `" MM"` tal cual |
| `data/CortesLanguageProvider` | **DOS** archivos: `es_es` + `en_us`, un provider cada uno | El ctor toma el locale; `LanguageProvider.getName()` **ya incluye el locale** → dos instancias ⊘ colisionan (leído, ⊘ asumido). **El nombre de la munición del arcabuz es `Firestone` en los dos idiomas** — `LASTIMA` era un nombre interno que se filtró a AGENTS.md y a dos `trace()`, y yo lo metí en el tooltip |

### Deuda conocida

| Falta | Nota |
|---|---|
| **Rotar el worldmodel en la guardia** (3ª persona) | Pedido explícito: espada y sable deberían girar hacia el jugador como en 1ª. Exige un mixin nuevo sobre `ItemInHandLayer`; **⊘ se hizo a propósito** para no meter una segunda inyección sin verificar las cinco primeras |
| **Arcabuz** | Receta **hecha** (grid vertical decided por el usuario). Queda: feedback por objetivo, y el flash a 1.7 m está derivado del dibujo **a ojo** — primer número a tocar si se ve mal. El click izquierdo ya tiene `STRIKE`+`RECOVER` |
| `/cc weapons` desactualizado | Tiene 11 armas vanilla. Arcabuz y Firestone: `/cc weapon cortescombat:<id>` |
| **Retroalimentación de "esto fue cargado"** | `CombatFeedback` sólo tiene `HIT`/`KILL`/`GUARDED`. Hace falta `CHARGED`: un HUD que reacciona a todo deja de significar nada, y el enum es una lista cerrada a propósito |
| **FOV de carga** | Hecho: 3° → 0.6° c/ suavizado exponencial, por `ComputeFovModifierEvent`. **Resta, ⊘ reemplaza** |
| Sonido de carga completa | El proyecto tiene **un solo sonido**: `SHIELD_BREAK`. Faltan 3 de los 4 eventos que el jugador tiene que reconocer s/ mirar |
| Impacto de golpe cargado | `addParticle` ⊘ aparece en ninguna parte del código |
| **Los 4 perfiles de pico/pala/maza/tridente** | **Por decisión, ⊘ deuda.** `ViewModelDefaults` los omite: son items de vanilla, y un movimiento dibujado a mano sobre una silueta que no dibujamos pelea c/ el modelo. Sin entrada = pose exacta de vanilla |
| **El arcabuz no tiene animación de cuerpo** | Su perfil no lee `CHOP` ni `STRIKE`: su click izquierdo es una puñalada de bayoneta y nunca se le escribió |

### Texturas: DOS SIGUEN SIENDO DE `refs/`

Las de `assets/cortescombat/textures/gui/sprites/hud/` son **arte original del usuario**:
`crosshair`, `crosshair_aim`, `crosshair_hit`, `cooldown_corchetes`, `charge_bar`,
`charge_bar_emtpy`. `hotbar_attack_indicator_full.png` (icono de combatify) se borró.

| Archivo | Origen | Licencia | Riesgo |
|---|---|---|---|
| `sprites/hud/crosshair_shield_indicator_{full,disabled}.png` | `combatify` | LGPL-2.1 | Medio: o se reemplazan, o el mod queda atado a LGPL |
| `damage_overlay.png` | `neoforge-damageoverlay` | **All Rights Reserved** | **Alto.** No hay `LICENSE` y el autor no concedió permiso |

Detalle en `textures/gui/PROTOTYPE-ASSETS.md`. Para reemplazar: **misma ruta y mismo tamaño en
píxeles** → no cambia código de render.

> **`cooldown_corchetes.png`:** el usuario lo rediseñó a una línea fina con huecos. Su alpha máximo
> bajó de **102 a 38**, así que `BRACKET_ALPHA` (0.30) se normaliza contra el pico del arte. Si se
> redibuja el arte, el número **⊘** hay que tocarlo.

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
/cc input          → las puertas del press izq., en orden, + VEREDICTO
```

**`/cc input` es el que contesta "el click dejó de funcionar".** Imprime cada gate que puede rechazar
un press —con el valor que consulta y su veredicto— en el mismo orden que `SwingInputHandler.press`, y
una línea `VEREDICTO` con la primera puerta bloqueada. Existe porque un playtest reportó "no puedo
golpear, romper ni interactuar" y el log **⊘ tenía ni una línea `[client]`**: hay 8 lugares que
rechazan un press, 6 silenciosos a propósito. **Lee los campos, ⊘ recalcula nada.**

`/cc stats` es el más importante: perfil, alcance (distancia + ángulos), cooldown, carga y especial.

> **Las tres sesiones que no produjeron nada útil:** el log tiene 2 statics en singleplayer, así que un
> flag que el comando cambia en el servidor **⊘ es el flag que el cliente lee**. Por eso los payloads
> viajan. Varios playtests no dejaron líneas `[client]` porque faltaba correr `/cc trace`.

---

## Próximos pasos

1. **Rotar el worldmodel en la guardia** (3ª persona) — mixin sobre `ItemInHandLayer`.
2. **Arcabuz**: feedback por objetivo y el flash a 1.7 m (los dos siguen abiertos).
3. **Paso 11: supervivencia (`FoodData`)** — el cambio más caro, en commit propio. **Los umbrales
   internos hay que leerlos en las fuentes de 1.21.1 antes de escribir el mixin.**
4. **Sonido de carga completa** y el valor `CHARGED` de `CombatFeedback`.
5. **Afinar a playtest**: la bayoneta del arcabuz (`STRIKE` −0.28/−14°), los números de los
   tooltips, y los perfiles de las armas nuevas.
6. **Los 30 advancements dicen "Hecho a mano."** — el arcabuz ya tiene su propia línea. Una por
   familia es el siguiente paso natural; ⊘ son 30 entradas que sólo cambian el nombre.
7. **El `arcabuz.png` original se queda en `textures/models/`** (donde fue dibujado) **y los cinco
   frames en `textures/item/arcabuz_fuse/`** (que es lo que el atlas lee). Duplicado a propósito; el
   test lo afirma.

### La ronda del arcabuz + tooltips — las cuatro cosas que costaron

1. **Un número que el jugador lee, y el juego no, es un bug con buen Marketing.** El tooltip tiene
   que llamar a `DamageResolver.resolve` y a `SlashTiming.hitsAt`, ⊘ recalcular. Y el sable carga
   pegando **dos cortes**: el daño cargado es ×4 del base, ⊘ ×2, y la mitad de un número grande
   sigue pareciendo grande en pantalla, así que **nada se ve mal** hasta que un jugador compara la
   línea contra una muerte.
2. **`%s` es el único formato que el idioma de tooltip soporta.** `TranslatableContents` rechaza
   cualquier otro en render: `"Unsupported format: '%1.1f'"`. ∴ los números se pre-formatean en Java,
   y `String.valueOf` sobre un float de aritmética imprime `6.949999809265137` → `%.1f`.
3. **Borrar un renglón de vanilla por texto es adivinar; por id de modificador es un contrato.**
   `GatherSkippedAttributeTooltipsEvent` + `skipId(Item.BASE_ATTACK_DAMAGE_ID)` saca exactamente
   los 2 que el tooltip reemplaza y deja los de encantamientos. `ItemStack#forEachModifier` **no**
   sirve para esto: recorre también los de encantamiento, así que una espada con Filo reportaría
   más daño que el arma.
4. **Un arma con su botón principal mudo es la forma exacta del canal muerto.** El sable sin track
   de `BREAK`; el arcabuz sin track de `STRIKE`: sus 3 tracks eran todos del click **derecho**, así
   que el click izquierdo no movía nada en 1ª persona. La **bayoneta es una estocada**, y el
   `ChopVersusThrustTest` la tenía anotada como hueco. ∴ preguntarse "**¿quién enciende el canal
   del botón principal?**" y no "¿este canal se mueve?".

> **Y `#` en un grid vuelve a ser el símbolo sin entrada en la key.** Está en la sección de datagen
> desde hace rondas. Se cayó igual, y el comentario del código que explicaba la trampa estaba
> **en el mismo archivo, dos líneas más arriba, mientras se escribía `#`**. Un comentario no
> previene: previene **leer**.

### Idiomas — `es_es` + `en_us`, y las dos reglas que había que tirar

- **⊘ sólo español.** El razonamiento era *"una traducción tosca es peor que la clave"*, y de ahí
  salió *"no generamos inglés"*. **El paso que faltaba:** una `en_us` faltante ⊘ muestra una
  traducción tosca, **muestra los internals a todo el que no abrió el menú de opciones** — y `en_us`
  es lo que corre Minecraft hasta que alguien lo cambie. Un mod en español sin español por defecto
  ⊘ es una traducción, es un build roto.
- **⊘ "que lo traduzca un humano".** Esa regla **no la decidió nadie**: estaba escrita en el javadoc
  como si fuera del proyecto, y el siguiente lector la tomó como tal. **Una regla que nadie eligió
  es peor que ninguna**, porque sobrevive: parece un muro y no hay nadie detrás.
- **Los nombres NO son una traducción de los seis** (decisión del usuario): **Ropera, Alabarda y
  Arcabuz** quedan en español porque son las icónicas — "rapier" y "halberd" son palabras
  ordinarias y renombrar el set le da a cada idioma dos juegos de armas distintos. **Martillo de
  Guerra → War Hammer** y **Daga → Dagger** sí se traducen, porque son descriptivas. **Shotel
  queda "Shotel"**: es un término musulmán y su traducción a otro alfabeto es una
  transliteración; la transliteración de una palabra latina es la palabra.
- **El idioma cambia el orden de palabras, no sólo las palabras.** ES `"Ropera de Hierro"` (el
  material atrás, con `de`), EN `"Iron Ropera"` (el material adelante, porque es adjetivo). ∴
  `displayNameEn(material)` es un método, ⊘ una traducción de `displayName`. Un test lo afirma.
- **`LanguageFilesTest` camina los enums**, ⊘ una lista escrita a mano: agregar un `SpecialType` o
  una banda de `ConeSize` sin su línea en los dos idiomas **falla el build**, en vez de imprimir el
  identificador en pantalla.

---

**Compactado desde ~28.000 tokens.** Lo que se movió: las tres cuentas largas (riptide, lunge ×2) y
las iteraciones al changelog. Lo que se conservó: cada hecho verificado, cada ruta, cada comando, las
decisiones y las trampas — que es lo que no se vuelve a encontrar solo.
