# Trampas verificadas de la API de 1.21.1

Extraido de AGENTS.md (compactacion 2026-10-07). Todas verificadas con javap/fuentes, ninguna de memoria.

**Build / toolchain (ModDevGradle, MC 1.21.1)**
- **Toda lib externa necesita TRES declaraciones en 1.21.8 y abajo.** NeoForge no carga
  dependencias que no sean mods, así que `implementation` **sólo compila**: las runs revientan
  con `NoClassDefFoundError` al crear el mod instance y la puerta de las runs es
  **`additionalRuntimeClasspath 'g:a:v'`** (docs → Dependencies → Non-Minecraft Dependencies).
  Producción: `jarJar(implementation(...)) { version { strictly; prefer } }`. Desde 1.21.9
  basta `implementation` -- **mirar el número de MC antes de copiar la receta**. Síntoma
  exacto: snakeyaml ausente de `build/moddev/dataRunVmArgs.txt` + `NoClassDefFoundError:
  org/yaml/snakeyaml/Yaml` en `WeaponPackLoader.fromYaml` desde el constructor del mod.

null`** — `Component.copy()` NPEa adentro del
serializador. `InventoryChangeTrigger.hasItems` **⊘ tiene overload de `TagKey<Item>`** →
`ItemPredicate.Builder.item().of(tag).build()`.

#### Trampas de la API de 1.21.1 — todas verificadas, ninguna de memoria

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

**Red y codecs — seis trampas de esta ronda, todas pagadas**
- **`neoforge-21.1.250-merged.jar` tiene `StreamCodec` y ⊘ tiene `io.netty`.** Netty va aparte
  (lo baja el launcher). Un test c/ `ByteBuf` necesita `testImplementation 'io.netty:netty-buffer'`,
  y la versión sale del **manifest de MC**: `4.1.97.Final`, ⊘ el `4.1.118` q también está en el cache.
- **`StreamEncoder<O, T>` es `encode(O buffer, T value)` — buffer primero.** Al revés ⊘ falla claro:
  `input` se bindea al buffer y el error es `cannot find symbol` en el **primer campo**, q apunta al
  dato y ⊘ al orden de los argumentos. Todos los codecs de este proyecto se escriben valor-primero
  (`composite` los toma separados) → el único lambda es la excepción.
- **Un `static final StreamCodec` en la clase MATA los tests en `<clinit>`**: `NoClassDefFoundError:
  StreamEncoder`, q ⊘ parece un test roto. ∴ los codecs son **dos métodos sobre `ByteBuf`** (Netty sí
  está) y el `StreamCodec.of(...)` vive en el payload.
- **`writeVarInt` es de `FriendlyByteBuf`, ⊘ de `ByteBuf`.** Friendly es de Minecraft → el codec
  entero no es testeable. Usar `writeInt`/`readInt` (4 bytes fijos) a cambio, q además deja cada campo
  en un offset conocido y hace posible el test "leé un float y decime cuál es".
- **`StreamCodec.composite` toma `StreamCodec<? super B, T>`**, así q un codec sobre `ByteBuf` entra
  en uno sobre `RegistryFriendlyByteBuf` por widening. Pero el **campo** hay q declararlo sobre
  `ByteBuf` o no compila.
- **Enums por ordinal en la red = contrato de cable.** Insertar un valor renumera los siguientes y un
  cliente viejo lee otra arma. Aceptado y escrito; `byOrdinal` **devuelve null** fuera de rango y el
  constructor lo baja a `NONE` → un build sin el arma dibuja quieto en vez de reventar el handler.
- **`sendToPlayersTrackingEntity` ⊘ incluye al propio jugador.** `PacketDistributor:89` →
  `ServerChunkCache.broadcast:482` → `ChunkMap.broadcast:1185` → `TrackedEntity.broadcast:1302`, q itera
  `seenBy` pelado. El q lo agrega es `broadcastAndSend`. Y `broadcast` **tampoco filtra por distancia**:
  "sin distancia" sale de "a quien lo puede ver".
- **`getCooldownPercent` es la fracción RESTANTE**, 0 cuando está listo. `CooldownState#ratio` es su
  opuesto (readiness, 1 = listo) → los dos se suman, no se mezclan.
- ⊘ **No hay flag de 3ª persona por system property en 1.21.1.** La cá