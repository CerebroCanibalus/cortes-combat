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

- **`supported_items` / `primary_items` de un encantamiento NO aceptan una lista de tags.** El
  campo acepta **un** tag (`"#ns:tag"`) o una lista de **ids directos** — pero un `#` adentro de una
  lista se parsea como nombre de ítem y **el registro entero falla al cargar el mundo**, con un error
  que nombra el *string* (`Not a valid resource location: #cortescombat:enchantable/blade`) y ningún
  archivo. Verificado contra las fuentes: `Enchantment.EnchantmentDefinition` lo declara como
  `RegistryCodecs.homogeneousList(ITEM)`, y el camino de lista usa ids directos. **La unión va
  adentro de un tag** (`tag(x).addTag(otro)`, que sí funciona: `#minecraft:axes` dentro de un tag es
  legal) y el JSON referencia **un solo nombre**. Los overrides de vanilla de la ronda de
  encantamientos estuvieron **rotos y sin verificar** por esto hasta el primer intento de crear un
  mundo. Guard: `CombatEnchantmentFilesTest.itemListsCarryNoTags`, verificado mordiendo.
- **Un test que sólo pregunta si el archivo existe no ve un archivo que existe y no se puede leer.**
  El test viejo de los encantamientos comprobaba archivo + tag + idioma y las tres pasaban mientras
  el juego crasheaba. Lo que faltaba era comprobar la **forma del campo contra la gramática del
  codec** — no más cobertura, otra pregunta.
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
serializador. `InventoryChangeTrigger.hasItems` ⊘ tiene overload de `TagKey<Item>` → `ItemPredicate.Builder.item().of(tag).build()`.

## Referencias separadas

- **Trampas verificadas de la API de 1.21.1** -> `docs/api-trampas.md` (13,7 KB: verificado con javap/fuentes, ninguna de memoria).
- **Historial de rondas cerradas** -> `CHANGELOG.md` (todas las rondas del 2026-10-07 salieron de aqui).
- `ARCHITECTURE.md` y `DESIGN.md` siguen aparte, referenciados abajo.
**No hay flag de 3ª persona por system property en 1.21.1.** La cámara se cambia con **F5** y
  persiste en `options.txt`. Una flag inventada es no-op silencioso.
- **Ningún payload tiene test**, antes y después: implementan `CustomPacketPayload`. Un test de un
  payload **⊘ compila** (`cannot access CustomPacketPayload`). La lógica de red queda verificada por
  lectura ⊕ compilación, nunca por aserción. ∴ `PlayerCombatState` **sí** es testeable: es un record
  puro c/ `encode`/`decode` sobre `ByteBuf` y **no** implementa nada de Minecraft. Esa es la línea.

---

# PARTE 2 — Memoria del proyecto

## Fase activa: 3 — Vertical slice

Pasos 1–10 hechos ⊕ verificados en cliente real. Librería de viewmodel, guardia unificada
**cerrada como postura**, **las siete armas nuevas con sus animaciones**, y el **arcabuz como arma
31**. **958 tests verdes.** Animaciones **casi completas** según playtest. Receta del arcabuz y **tooltips de stats**, que reemplazan los de las armas vanilla también.

| # | Paso | Estado |
|---|---|---|
| 1–10 | esqueleto, registros, `reach/`, cooldown duro, flujo de ataque, carga, HUD, aguante, **las 30 armas + datagen + recetas + cobre**, especiales + `ignoredArmour` | HECHOS ⊕ verificados |
| 14 | framework de viewmodel | HECHO (falta sonido de carga, `CHARGED`) |
| 11 | supervivencia (`FoodData`): quitar exhaustion, curación de ciclo cerrado | pendiente |
| 15 | compat / conversión de armas de otros mods | pendiente |
| — | **arcabuz** (arma 31, fuera del catálogo de 30) | HECHO, sin receta de crafteo |
| — | tercera persona (`thirdperson/`) | HECHO y verificado |
| — | **coyote time del golpe** (buffer de confirmación cliente→server) | HECHO ⊕ 960 tests, **⊥ playtest** — ver la sección propia |
| — | **weapon packs YAML** (docs/weapon-packs-design.md v3) | **FASES 1–5, 7, 8 HECHAS y VERIFICADAS EN CLIENTE** (2026-10-10, tres playtests) — 958 tests. Faltan: fase 6 (poses + sprite slash) y modo `strict` |

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

- **El orden REAL de lectura** (`WeaponStats.readProfile` → `deriveProfile`): data components →
  **`cortescombat:profile/<id>` (nuestros tags, el molde)** → tags de vanilla (fallback p/ armas
  ajenas) → `NONE`.
- **El tag lleva el MOLDE, ⊘ los números.** `profile/halberd` = "tratalo como alabarda"; alcance,
  carga, knockback y especial siguen saliendo de las 4 tablas de `WeaponTables`.
- **La derivación ⊘ lee atributos y ⊘ escala el alcance por velocidad.** Verificado:
  `WeaponStats.of()` **no** lee `ItemAttributeModifiers`. Lo único que sale del ítem propio es el
  **intervalo** (`attackInterval` lee `ATTACK_SPEED` del holder) → el tempo sí es del arma, la
  distancia ⊘. Una espada ajena se lleva nuestro 3.6/35°/30° y nuestra carga de 25 ticks aunque su
  ítem declare 2 de daño. `ARCHITECTURE.md` tenía escritas las dos promesas y ninguna implementada;
  ya corregido allá.
- **Un datapack ⊘ puede setear components en un ítem** (1.21.1: los defaults viven en
  `Item.Properties`, en Java) ∴ sólo puede escribir tags, y por eso el tag lleva el molde.
- **Config procedural en DOS archivos:** `generated_weapons.json` (lo escribe el mod) y
  `weapon_overrides.json` (el jugador). **⊘ IMPLEMENTADO TODAVÍA** — el javadoc de `WeaponStats` dice
  *"the call site is left for that step and currently falls through"*. Es el paso 12, y es la única
  vía p/ que un packmate ponga **sus** números s/ Java.
- **Va en JSON plano, ⊘ en `ModConfigSpec`** (⊘ se pudo verificar si soporta mapas; sources de FML ⊘
  están en el cache). `ModConfigSpec` queda p/ los números de balance estáticos.

### Los tags propios: `cortescombat:profile/<id>` (2026-10-09)

**Por mantenibilidad nuestra, ⊘ p/ que el usuario meta armas** (eso tiene otra idea, futura).

- 14 archivos en `data/cortescombat/tags/item/profile/`, uno por valor del enum. Los generamos
  nosotros (`CortesItemTagProvider.profileTags()`), y `WeaponStats` los pregunta **antes** que los de
  vanilla.
- **El motivo:** la clasificación corría entera por tags de vanilla, y escribíamos nuestras cinco
  herramientas de cobre **dentro** de `#minecraft:swords/axes/…`. Los tags mergean aditivamente, que
  es lo que los hace funcionar — y el mismo mecanismo deja que un datapack declare uno con
  `"replace": true`, caigan todos los miembros (los nuestros incluidos) y la espada de cobre deje de
  pelear **en silencio**.
- **Los 4 vacíos** (`none`, `unarmed`, `mace`, `trident`) son la especificación: el archivo es el
  camino donde se escribe después. Es lo inverso del bug de `stone_tool_materials`, en el mismo
  archivo, donde llamar `tag(...)` p/ un tag que nada llenaba era el error.
- **Las 30 del catálogo van igual** aunque llegan por componente: el archivo es la lista canónica de
  "cuáles de los nuestros usan cuál molde" y la red debajo de una vía de registro que se olvide el
  `.component(WEAPON_PROFILE, …)`.
- `WeaponProfile.tagPath()` es pura (patrón de `WeaponMaterial.repairTag()`); `WeaponProfileTags` la
  envuelve en un `TagKey`. `WeaponProfileTagsTest` (5) verifica path, enum, archivo en disco y orden.
- **⊘ confundir con el arquetipo de arma de terceros**, que es otra cosa y vive en
  `docs/weapon-packs-design.md` (diseño sin implementar, 2026-10-09): allá el canal es YAML y los
  tags son de **categoría** (`knives`, `hammers`); acá el tag es el **molde** y es nuestro. Ese doc
  tiene además dos afirmaciones falsas corregidas: la derivación **no** lee atributos ni escala el
  alcance por velocidad.

### Framework de viewmodel (§11)

- **Machinery código una vez, perfiles datos.** Un perfil declarativo por arma, un solo mixin.
- Composición **aditiva** por capas: `vanilla + perfil + shake + fov`, cada una desactivable por
  config (mareo).
- Curvas propias (`back`, `expo`, `elastic`, `bounce`): `Mth` sólo trae lineal/cuadrática/cúbica y
  ⊘ alcanzan p/ un golpe c/ peso.
- **Shake determinista sembrado por golpe.** Aleatorio por frame = el mismo golpe se ve distinto
  cada vez → ruido gráfico, ⊘ animación.

## Decisiones de alcance

- **Velocidad de ataque dura**: ⊘ atacar a medio cooldown, daño plano (mata el multiplicador de timing
  de vanilla, que premia spamear más rápido).
- **Carga** = bonus encima del base. **Nunca resta** — el piso se fuerza **en código**, ⊘ se confía en
  el dato.
- **Click derecho**: gana el especial, y **sólo si nada puede cubrir**. `SpecialInputHandler` pregunta
  la guardia **primero y desde cualquiera de las dos manos**.
- **Alcance en cono frontal fijo**, c/ distancia y ángulo horizontal y vertical independientes.
- **Guardia**: 100% excepto explosiones, aguante de **3 golpes**, arco frontal. Agotarse = stun. El
  hacha stunea de un golpe.
- **Supervivencia**: la comida deja de ser energía (⊘ `exhaustion`) y es **recurso de curación c/ ciclo
  cerrado**: se gasta al curar y se regenera sola. Regen 1 hp cada 2 s hasta 6 de comida; comer se
  interrumpe al recibir daño; líquida 20 ticks.
- **Aguante unificada escudo/espada**: se gasta **al recibir** un golpe bloqueado, **nunca** por
  mantener la guardia. ∴ la espada puede cubrirse indefinidamente gratis.
- **Cubrir y comer es legal, c/ orden fijo**: primero te cubrís, después comés. Sale por construcción
  (la guardia se lee de las dos manos, comer sale de la principal) y **⊘ por arbitrar**.
- **El hacha = dos damage types, ⊘ una con dos flags**: `warhammer_ignored_armour` (en
  `bypasses_armor`) vs `axe_charged` (en **ningún** tag, sólo se reconoce). El axe cargado conserva la
  armadura y la destroza al doble.
- **Cargar ⊘ cambia el tempo**, y un golpe cargado **siempre pierde en DPS** contra tocar rápido. ∴ el
  axe quedó en bonus 2.0 / carga 60 (86% del tapping), ⊘ en 1.2/70 (44% menos: era una trampa).
- **El hacha ⊘ lleva `damageScale`**: vanilla ya da 7 a 1.0 → menos de la mitad del throughput de la
  ropera. El test lo afirma en vez de multiplicar.
- **El martillo escala por golpe consecutivo**, ventana 4 s, ⊘ tope. 0.9 → 1.05 (22 → 19 ticks),
  alcance 2.6 → 2.9, cono 40/40 → 30/30 (el playtest lo llamó *inusable*, ⊘ *pesado*: "lento" es un
  costo que el jugador eligió).
- **Shotel**: parry c/ cooldown 5 s que devuelve **sólo** el golpe parriado, s/ buffer. Y **la carga
  pega DOS golpes reales**, c/ dos cortes separados 8 ticks y medio bloque al costado, y dos sonidos.
  Playtest: *"perfecto, por fin"*.
- **El parry ⊘ se puede sostener, y por eso cuesta caro.** 0,6 s (12 ticks), un press dentro **⊘** lo
  renueva, 5 s de cooldown que arrancan **en el press**. Motivo: el sable tiene el alcance más corto y
  un bloqueo gratis sería mejor que la ropera.
- El golpe parriado **se anula Y vuelve** — el mismo número sin modificar. `consume()` cierra la
  ventana ⊘ reiniciando el cooldown. Se engancha con `LivingIncomingDamageEvent`, ⊘
  `LivingDamageEvent`, porque va **antes** del chequeo de `invulnerableTime`.
- **Reflejar c/ `playerAttack`, ⊘ una fuente mágica**, para que cuente como ataque de jugador.
- **Partícula de golpe = la del arma; de golpe cargado = `ParticleTypes.CRIT`** (ya es una slash
  animada grande) → se distingue **por forma**. Nace en el **centro de la AABB**, ⊘ `getY()`.
- **La espada (3.6) alcanza más que hacha/pico/pala (3.0)**: invierte la relación de vanilla a
  propósito. Las manos desnudas: alcance 1.9, lo más rápido.
- **El cobre es el de vanilla con nuestra textura**; la pepita es nuestra (`minecraft:copper_nugget` ⊘
  existe en 1.21.1). El lingote se reskin con `assets/minecraft/models/item/copper_ingot.json`.
- **5 armas nuevas** (ropera, alabarda, martillo, daga, shotel): sólo alabarda y shotel tienen click
  derecho; sólo alabarda, martillo y shotel cargan. **Ninguna cubre** — la guardia es escudo o espada.
- **Los sonidos son datos, ⊘ código.** `ModSounds` sólo nombra eventos y calcula pitch.
- **El desgaste doble de armadura es evento, ⊘ mixin**: NeoForge ya reemplazó el loop por
  `CommonHooks.onArmorHurt` + `if (true) return;` → el loop original es **código muerto**.
  `ArmorHurtEvent.ArmorEntry.newDamage` es mutable.
- **Proyectiles** (arco, ballesta, tridente lanzado): fuera del cono, comportamiento actual.
- **Mobs: ⊘ cambios.** Candidato nº1 a revisión después del vertical slice, empezando por el zombi.
- **Un modelo base por familia** (`models/weapon/alabarda.json`) + item models finos generados.

### Trampas de diseño marcadas

- **El autoataque carga y dispara** → el ritmo ya es del jugador, lo que **consume** una fuente de
  profundidad (el timing). La restante tiene q venir de: elección de arma, momento del click derecho,
  lectura de la aguante del rival, gestión de la propia. **⊘ reintroducir profundidad metiendo más
  timing.**
- **`foodRegenInterval` es el número más importante del proyecto.** Rápido → la comida es decorativa;
  lento → pelear es un callejón sin salida. **Sólo playtest lo resuelve.**
- **`FoodData` es vanilla puro, s/ patch de NeoForge** (verificado). Los umbrales internos hay que
  leerlos en las fuentes, ⊘ de memoria.
- El martillo **⊘ resetea** al recibir daño, por decisión. Si queda fuerte, se cambia c/ el dato
  medido, ⊘ por intuición.
- Cubrirse ⊘ cuesta aguante, pero **sí cuesta la opción de golpear**.
- **El `verticalAngle` de cada perfil importa más de lo que parece**: c/ el cono siguiendo la mirada
  decide si un swing conecta c/ algo overhead o underfoot.
- ⊘ **La guardia NO rompe la comida**: en 1.21.1 el daño **⊘** interrumpe comer, así q la regla es
  automáticamente cierta. Lo único a cuidar es ⊘ romperlo.
- **El cono lleva correa**: `permitido = max(distancia · tan(ángulo), min(0,65 × semiextensión, 0,8))`.
  Cerca la holgura pasa a ser "el tamaño de lo que golpeás". El eje **horizontal nunca fue el
  problema** y el **vertical** se mide desde el horizonte.
- **Ninguna de las cinco familias nuevas cubre.** La guardia es escudo o espada. Doble razón: la
  daga bloquearía estrictamente mejor que la espada (misma guardia, ataques más rápidos), que es la
  misma razón por la que un escudo en la off disolvería la guardia unificada; y el martillo es el arma
  de compromiso — si además bloquea gratis, no queda compromiso.
- **La alabarda barre lo que hay en el arco, ⊘ lo mejor que haya** (el click izquierdo ya saca un
  objetivo con todo su daño). **Gasta el mismo cooldown duro**, así q un barrido reemplaza un golpe en
  vez de añadirse. Sonido y partícula salen **haya o no haya enemigo**: un especial q sólo costara
  algo al conectar sería gratis practicarlo.

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

## Encantamientos propios (ronda 2026-10-10)

**6 custom, todos datos + reglas puras.** El patrón: el JSON dice en qué ítems puede estar
(`supported_items`, **un solo tag**, ver Trampas del build), `CombatEnchantRules` (pura, JVM pelada)
tiene los números, y el hook vive donde el número se cobra.

> **El diseño completo (los ~30 por arma, con números y hooks) vive en `docs/encantamientos.md`**, que
> absorbió y reemplazó a los dos docs anteriores. Acá queda sólo lo implementado.

| id | arma | efecto | dónde |
|---|---|---|---|
| `steel_reflex` | shotel (molde `sabre`) | parry 100 → 80 → 60 ticks | `ParryState.activate(nivel)` |
| `audacity` | ropera | lunge −20 %/nivel (fracción) | `SpecialHandler.lunge` |
| `fleeting_edge` | daga | Velocidad 40/60 ticks al conectar, **sin renovar** | `CombatEngine.applyFleetingEdge` |
| `rhythm_of_iron` | martillo | ventana de la cadena 80 → 100 → 120 | `StreakState` (la ventana es un **campo**) |
| `riposte` | espada (clase `sword`) | tras bloquear de verdad, el siguiente golpe ignora 25/50 % de armadura | `GuardHandler` arma, `CombatEngine` cobra |
| `dry_powder` | arcabuz (molde `arcabuz`) | recarga 50 → 40 → 30 ticks | `Arcabuz.reloadTicks` |
| `punta_fina` | ropera (molde `ropera`) | +15 %/+25 % de daño a **1,5 bloques o menos** | `CombatEngine.perTargetBonus` |
| `filo_astillado` | hacha (clase `axe`) | +6 %/+10 % de daño **por pieza de armadura** del rival (máx 4) | `CombatEngine.perTargetBonus` |
| `apunalar` | daga (molde `dagger`) | +60 %/+90 % si pegás **desde más de 90° de la espalda** | `CombatEngine.perTargetBonus` |
| `sanguijuela` | daga (molde `dagger`) | +8 %/+14 % del daño del golpe como **curación** | `CombatEngine.apply`, por golpe que aterriza |
| `aventurero` | espada (clase `sword`) | **+20 % de XP por nivel**, máx 5 (al 5 = +100 %) | `enchant/AdventurerExperience` -- **al MORIR, no al golpear** |
| `culata` | arcabuz (molde `arcabuz`) | la bayoneta empuja **+25 %/+50 %/+75 %** | `CombatEngine.enchantPush` |
| `estocada` | ropera (molde `ropera`) | el golpe **del lunge** ignora **15 %/30 %** de armadura | `CombatEngine.lungePierce`, cobrado en `special/Lunge` |
| `segundo_aliento` | espada (clase `sword`) | al **romperse tu guardia**, tu siguiente golpe en 3 s pega **+15 %/+25 %** y empuja **+50 %** | `GuardHandler.breakGuard` arma `SecondWindWindow`; `CombatEngine` la cobra |
| `guardia_firme` | espada (clase `sword`) | **1 nivel** · el primer bloqueo de cada 3 s **no gasta aguante** | `GuardHandler.onShieldBlock` + `GuardFree` |
| `cicatrizador` | azada (clase `hoe`) | el efecto **Anti-curación**: **−15 % por nivel** de toda curación, 4 niveles = 4 del efecto, 5 s | `CombatEngine.applyOnHitEffects` aplica; **`effect/AntiHealingHandler`** cobra en `LivingHealEvent` |
| `destripamiento` | alabarda (molde `halberd`) | el **cargado** deja **Sangrado**: 1 ♥ cada 2 s durante 6 s (L1) / 10 s (L2) | `CombatEngine.applyOnHitEffects` |
| `quebranto` | pesadas (clase `heavy`) | −8 % de armadura por stack, **máx 3**, 4 s, reaplicar refresca | `CombatEngine.applyOnHitEffects` |
| `desvio_vampirico` | shotel (molde `sabre`) | al **parriar** robás **1 ♥ de vida máxima** y cada parry **suma otro**; el nivel es el **tope** (L1 1 ♥ · L4 4 ♥), 10 s, refresca | `ParryHandler` -- **dos efectos**, `VAMPIRIC` (el acumulado) y `DRAINED` (1 ♥ por parry) |
| `mecha_corta` | arcabuz (molde `arcabuz`) | la mecha **20 → 15 → 10** ticks | `Arcabuz.light(fuse)` (el total por argumento) + `ArcabuzFuseState.progress(totalTicks)` -- **el cliente divide por el total, no por la constante** |

> **5 efectos nuevos, y las cuentas viven en `CombatEnchantRules` (pura) y no en los efectos.** Un
> `MobEffect` extiende una clase de Minecraft, así que un método suyo **no se puede cargar en un
> test**: la cuenta adentro del efecto sería una cuenta que ningún test mira. Es la línea exacta del
> proyecto -- *"¿implementa algo de Minecraft?"* -- y los tests la cazaron **dos veces**:
>
> 1. **`antiHealingReduction` sin clampear**: con un amplifier alto la reducción **pasaba de 1** y
>    `1 − reducción` daba **negativo** -- o sea, curarse hacía **daño**.
> 2. **`DRAIN_PER_LEVEL` en positivo**: el robo *sumaba* vida máxima en vez de restarla. El techo del
>    enemigo subía y el robo era un regalo para los dos lados. **La cuenta se ve bien leída y al
>    revés**, que es la peor forma de fallar.
>
> **Y el robo no puede matar, pero sí baja la vida actual.** Bajarle el techo a un cuerpo le baja la
> vida -- `LivingEntity#tick` clampea cada tick y no hay forma de evitarlo -- así que lo que se
> garantiza es el **piso**: `canDrain` pregunta si le quedaría al menos un punto de techo, y si no,
> el robo no se cobra.

> **Los dos últimos necesitaron estado nuevo**, y son los primeros del proyecto que lo hacen: dos
> attachments (`SECOND_WINDOW` y `GUARD_FREE`) con **vencimiento absoluto y sin codec**, el patrón de
> `ContragolpeWindow` -- un contador habría que bajarlo cada tick para todos los jugadores, y una
> ventana que reaparece tras reconectar es un premio que el jugador no ganó.
> **`GuardFree.READY` es `-INTERVAL_TICKS` y no cero**, y el test lo afirma: con cero, "listo" sería
> `now - 0 >= 60` y el primer bloqueo gratis de una partida no llegaría hasta el tick sesenta del
> mundo. Saboteado a cero: muere.

> **Punta Fina, Filo Astillado y Apuñalar comparten un solo canal** (`CombatEngine.perTargetBonus`), y
> es a propósito:
> los tres son "un porcentaje del daño que depende de algo del objetivo o de la posición", y el modo
> de falla de un canal que nadie llama es que **el build queda verde**. El guard de wiring afirma
> `enchanted + perTargetBonus(` -- **la suma, no la existencia de la función** -- porque la primera
> versión afirmaba `perTargetBonus(` y el sabotaje que quitaba la suma **no la hacía morir**.
> Y el hacha necesitó un tag de clase nuevo, `#cortescombat:enchantable/axe` (`#minecraft:axes` + el
> molde), por la misma razón que la espada: el hacha del juego es la de vanilla reworkeada, y su
> alcance no puede ser un tag de molde que sólo tiene las nuestras.

- **`CombatEnchantRules.IDS`** es la lista canónica y `CombatEnchantmentFilesTest` la camina: agregar
  un id hace que el test cubra solo el archivo, el tag, la forma del campo y **los 8 idiomas**.
- **Y hay que estar en un TAG DE ENCANTAMIENTO o no se consiguen.** En 1.21.1 el campo `"treasure"`
  del JSON **no existe** (verificado: ninguno de los 42 de vanilla lo declara) -- son **tags de
  registro**: `in_enchanting_table` (lo lee la mesa, `EnchantmentMenu:207`), `treasure`, `tradeable`
  (aldeanos) y `on_random_loot` (cofres). Los 6 nuestros se registraban, tenían archivo e idioma,
  se podían dar con `/enchant`... y **no salían nunca en una mesa**, sin un solo error en el log. ∴
  `data/minecraft/tags/enchantment/{in_enchanting_table,tradeable}.json`, y el guard pregunta si
  **cada id está adentro**, no si el archivo existe.
- **El parry devuelve el daño de un proyectil al tirador, y era casualidad.** `getEntity()` de una
  flecha es **el arquero** (la flecha es `getDirectEntity()`), así que el `instanceof LivingEntity`
  del handler lo aceptaba sin preguntar nada. Formalizado en `ParrySource.cameThroughTheAir(...)`
  (pregunta por **identidad**, no por tipo: así la onda de filo, que no extiende `Projectile`,
  también entra), el proyectil se **descarta** (antes quedaba clavado en el que parrió) y el guard
  afirma que el handler **usa** la respuesta -- una pregunta sin consumidor es el bug.
- **Los ids viven en la clase pura** (`*_ID` como `String`) y no en `CombatEnchantments`, que nombra
  `ResourceKey`/`Enchantment` y no se puede cargar en un test.
- **Identidad = molde** (`#cortescombat:profile/<familia>`), **genérico = clase**
  (`#cortescombat:enchantable/<x>`). `GENERIC_IDS` marca la excepción.
- **Quick Charge se probó y se revirtió.** Se reutilizó el de vanilla (regla: extender antes de
  inventar) y el playtest lo midió: el efecto es **−0,25 s absolutos por nivel**, que sobre la
  ballesta (1,25 s) es −20 % y sobre nuestro arcabuz (2,5 s) es **−10 %** -- la mitad de lo que el
  diseño pedía. El log lo mostró (`de 50` sin encantamiento, `de 40` con Quick Charge II, cuando el
  diseño decía 30). **Lección: un efecto absoluto reutilizado sobre una base distinta da otro
  porcentaje**, y una reutilización que da la mitad del número no es una reutilización.
- **Idiomas: 8 archivos** (`ModLocales`): `en_us` + los **siete** españoles. **Ninguna variante es
  alias de otra** -- la pila es *[seleccionado, en_us]*, así que `es_mx` cae a inglés, no a `es_es`.
- **El entorno de dev no tiene idiomas de vanilla**: el cliente trae **solo** `en_us.json`, así que
  elegir español deja las cadenas de *vanilla* en inglés aunque las nuestras salgan bien.

## Estado técnico actual

**960 tests unitarios verdes.** Todo lo testeable corre en JVM pelada; lo que necesita el juego queda
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
- **13 attachments**: `charge`, `guard_stamina`, `guard_last_block`, `cooldown`, `hammer_streak`,
  `parry`, `lunge`, `lunge_hits`, `frenzy`, `last_synced`, `arcabuz`, `fall_deferral`, `pending_hit`.
- **14 canales** de viewmodel: `WINDUP`, `STRIKE`, `CHOP`, `RECOVER`, `COOLDOWN`, `HOLD`, `GUARD`,
  `BREAK`, `PARRY`, `LUNGE`, `SWEEP`, `HELD`, `RELOAD`, `SHOOT`.
- **9 perfiles** en `ViewModelDefaults`: espada, manos, ropera, daga, sable, alabarda, hacha,
  martillo, arcabuz. **Un arma chop (lee `CHOP`) o thrust (lee `STRIKE`)**, y la puerta lo declara el
  perfil.
- **1 tipo de partícula** para las 30 armas: el sprite viaja en el payload → un arma nueva es un PNG y
  una línea. Tiras de 32×160 (5 frames) cortadas en los UV.
- **1 entidad propia**: la onda de filo (`SlashWave`), la primera `EntityType` del mod. **Modelo
  propio, no partícula** (`SlashWaveRenderer` + `models/projectile/sweeping_edge.json`), y **se
  agranda con el nivel** (`SlashWaveModel.scale` = `SweepingEdge.width`: 0,7/1,0/1,4). Dos trampas
  pagadas: el modelo hay que **pedirlo** en `ModelEvent.RegisterAdditional` (si no, cubo violeta) y
  el `texture_size` del export de Blockbench venía en 64 c/ UV en escala 16 → dibujaba un trocito de
  la esquina de la textura. `SlashWaveModelTest` ata el Java al JSON y afirma que **el dibujo cae
  dentro de lo que el modelo muestra** (no que el archivo exista).
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

0. **Weapon packs — lo único que falta (ya con cliente abierto):** **fase 6** = loader de poses
   por YAML + integración al `PoseSolver` + sprite slash por YAML (los dos tocan sistemas
   cerrados del cliente), y **modo `strict`** de la fase 8. El playtest de todo lo demás está
   **cerrado** (2026-10-10).
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
8. **Rework de oro y cobre** — ver sección abajo.

---

## Rework de ORO y COBRE — diseño cerrado, pendiente de implementación

**Decisión del usuario (2026-10-07):** el oro y el cobre pasan de "versiones peores del hierro" a
materiales con **nicho de gameplay** propio. Mantienen su identidad de durabilidad baja, pero ganan
mecánicas que el hierro no cubre.

### ORO — "El material de encantamientos"

**Identidad:** Armas frágiles, velocidad de ataque alta, daño bajo por golpe. No son para pegar
fuerte, son para pegar **mucho** y **robar recursos**.

#### Encantamientos únicos de combate

| Encantamiento | Niveles | Efecto |
|---|---|---|
| **Toque de Midas** | 3 | Al matar un enemigo, suelta oro: N1 = 2-4 nuggets (100%), N2 = 1-2 lingotes + 1-5 nuggets (50% ambos), N3 = 1 manzana dorada (10%) + 1-3 zanahorias doradas (20%) + 1-4 lingotes (100%) + 1 manzana dorada encantada (2%) |
| **Eco de Oro** | 1 | Marca al enemigo con **Glowing** + **"Marcado de muerte"** (+20% daño recibido) |
| **Robo de Vida** | 3 | 5% del daño infligido como curación por nivel |
| **Velocidad del Viento** | 5 | +5% velocidad de movimiento por nivel (máx +25%) |
| **Peso Pluma** | 3 | **-10% velocidad de ataque** por nivel (el oro es ligero pero frágil) |
| **Golpe Fantasma** | 1-2 | 15% por nivel de probabilidad de ignorar armadura |

#### Encantamientos únicos de HERRAMIENTAS (sólo pico/pala/hacha de oro)

| Encantamiento | Niveles | Efecto |
|---|---|---|
| **Toque de Midas (Herramienta)** | 3 | Al picar un bloque con drop de mineral, convierte el drop en su versión de oro (carbón → oro, hierro → oro, etc.). N1 = 25% probabilidad, N2 = 50%, N3 = 75% + 1 nugget extra |
| **Velocidad del Viento (Herramienta)** | 3 | +10% velocidad de minería por nivel (máx +30%) |
| **Golpe Fantasma (Herramienta)** | 1 | 10% de probabilidad de que el bloque picado suelte el doble de drops |

**Razón de ser:** El oro es el material de **control del enemigo** y **ventaja económica**, no de
daño bruto. Las herramientas de oro son para **mineros que quieren oro**, no para mineros que
quieren minerales.

---

### COBRE — "El material antiséptico"

**Identidad:** Tier intermedio entre piedra y hierro, durabilidad media, pero con una mecánica
única de **purificación** y **resistencia a efectos**.

#### Mecánica 1: Armadura antiséptica

- Cada pieza de armadura de cobre equipada reduce la duración de **todos los efectos negativos**
  un **5%**.
- Las 4 piezas acumulan **20% de reducción** de efectos negativos (veneno, wither, hambre, etc.).
- **No reduce efectos positivos** (velocidad, fuerza, etc.).

#### Mecánica 2: Armas purificadoras

- Las armas de cobre dan un nuevo efecto al golpear: **"Purificación"**.
- **Purificación** hace daño pequeño pero muy rápido a:
  - **Enemigos no muertos** (zombis, esqueletos, etc.)
  - **Jugadores que lleven armadura de netherite**
- Es **daño puro** (atraviesa armaduras).
- Además **reduce su daño un 30%** (debuff temporal).

**Razón de ser:** El cobre pasa de "no tiene uso" a ser el arma de **control de no muertos** y
**anti-netherite**, con una mecánica de **sostenimiento** (armadura) que añade profundidad sin
complicar el combate.

---

### Notas de implementación

- **Toque de Midas** necesita un hook en `LivingDeathEvent` o `PlayerEvent.PlayerKill` para
  modificar los drops.
- **Purificación** necesita un efecto nuevo (MobEffect) y un hook en `LivingIncomingDamageEvent` o
  `LivingHurtEvent` para aplicar el daño puro y el debuff.
- **Armadura antiséptica** necesita un hook en `MobEffectEvent` o `LivingUpdateEvent` para reducir
  la duración de efectos negativos.
- **Encantamientos de herramientas** necesitan registrarse como `Enchantment` con `EnchantmentCategory`
  específico para herramientas de oro.
- **Sinergia con redstone** necesita leer `Level.getBestNeighborSignal()` o similar en el bloque
  adyacente al enemigo.

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

### La ronda del radio, el sprite y el click derecho (2026-10-08)

1. **Un radio que sólo crece no es un permiso, es el modelo mental del jugador.** La red se encogía
   cuando terminaba una sobrecarga porque el radio volvía al de la carga real: los cables de más
   perdían membresía y `max(i, j)` **nunca devuelve un punto perdido**. El fix es un *high-water*
   persistido (`publishedRadius`, guardado en NBT) con UNA excepción: `lit` false publica cero,
   porque apagar con la palanca es una decisión, no un encogimiento. **El nombre viejo
   (`lastRadius`) describía "el último" un campo que sube** — un test lo mata.
2. **Un predicate no puede contestar dónde se dibuja.** El sprite 2D del arcabuz colgaba de
   `Minecraft.screen != null`, que es verdad **también para la mano**: abrir el inventario volvía
   plano el arma en primera persona. La pregunta sólo tiene respuesta en `ItemRenderer#render`, que
   recibe el `ItemDisplayContext` — el mismo sitio donde vanilla resuelve lo del tridente. El
   override `gui` y su predicate se borraron; el icono se carga como modelo adicional
   (`ModelEvent.RegisterAdditional`), porque un override target no se publica por nombre.
3. **No se le puede preguntar a un bloque si es interactivo sin ejecutar la interacción.** La puerta
   del click derecho adivinaba "todo bloque consume" y un arcabuz apuntado a una pared de piedra no
   hacía nada. Ahora `SpecialRightClickMixin` lee el resultado real de `useItemOn` / `interact` y
   sólo entonces dispara: **el motor decide, la especial acompaña**. El aire sigue yendo por el
   press, y los dos caminos son disjuntos — que es lo que evita que un click sean dos especiales.

### La ronda del playtest de weapon packs (2026-10-10)

**El sistema entero funcionó en cliente**: los 3 caminos (YAML sobre vanilla, `creates` ajeno,
tag-molde por datapack) + el caso extremo `dalilandia:chistorron` (60.0 damage, 12/s, charge
instantánea ×6, reach 6.0, cono 85/85, knockback 1.6/0.45 = techos exactos clampeados bien,
durability 1). Y **arreglos que salieron del playtest**:

1. **Ropera daño 5.0 → 6.5** (`WeaponFamily.ROPERA`); el lunge sube solo por
   `LUNGE_DAMAGE_SHARE=0.75`. Los stats del tooltip eran correctos y la *sensación* no — el
   número era el bug.
2. **Icono del arcabuz: la causa raíz era un listener sin cablear.** `FlatInventoryIcon.
   onRegisterAdditional` existía, estaba escrito y **nadie lo llamaba** → la variante plana
   nunca se registraba → cubo morado en inventario. Además NeoForge exige
   `ModelResourceLocation.standalone()` en `RegisterAdditional` (el de nombre completo queda
   "unused" en el bake), y el JSON vive en `models/arcabuz_icon.json` (no `models/item/`).
   **Un listener huérfano no da error — sólo `icono=false` en el log.**
3. **Alias de tab** aceptado en `WeaponPackItems` (`cortescombat` y `cortescombat:combat`).

**Las trampas de entorno que costaron rondas (las cuatro, juntas):**

- **`Failed to load options` en `latest.log` es LA causa de "no carga mi resource pack".**
  Una línea rota en `options.txt` (`key_key.pickItem:key.mouse.middle`, formato que el datafix
  de NeoForge parsea como número) hizo que MC descartara options entero → `resourcePacks`
  default vacío → el pack jamás entró en el ResourceManager →
  `FileNotFoundException: jugadorsito:models/...`. **Dos causas de distancia del síntoma.**
  Fix: eliminar la línea (MC regenera el default).
- **`-replace 'resourcePacks:\[\]'` matchea DENTRO de `incompatibleResourcePacks:[]`** —
  cuatro veces seguidas. **Sólo línea exacta** (`-eq` / `-like`), nunca regex con corchetes
  sobre options.txt.
- **`Add-Content`/`Set-Content` de PowerShell sin `-Encoding` corrompe acentos** (`Dalí` →
  `Dal?` en el YAML). El `write`/`edit` de la herramienta escribe UTF-8 bien; PowerShell no.
  Verificación: `Contains('texto con ñ')` + scan CJK/FFFD — los `?` de la consola son display,
  no disco, pero sólo un Contains lo distingue.
- **La prueba dura de "esto es 100% ajeno al mod"**: `grep jugadorsito src/` = **0 hits**. No
  es una promesa, es la misma aserción que hace `WeaponPackBuiltInTest` para los tags.

### El coyote time del golpe (2026-10-10)

**El reporte:** *"el golpe a veces se pierde pese a que el indicador de UI muestra que deberías dar
el golpe"*. Tenía razón, y la causa está en que **la misma pregunta se responde en tres lugares con
tres tolerancias distintas**:

| Quién | Cuándo mira | Con qué |
|---|---|---|
| El indicador (`CombatAim.stillAiming` → `ClientCombatState.setAiming`) | cada tick | el scan **cacheado** del último tick que escaneó, revalidado con `stillHittable` |
| El `release` (`SwingInputHandler.resolveTargetId`) | al soltar el botón | un scan **fresco** y estricto (`ConeTargeting.findTargetForHeldItem`) |
| El server (`CombatEngine.resolveTarget`) | al llegar el paquete | `Reach.inCone` con **su** copia del mundo (posiciones y rotación viajan por red) |

Un mob que da un paso lateral sale del cono entre el tick que dibujó el indicador y el `release` →
el cliente manda `NO_TARGET` y el miss queda decidido **sin preguntarle nada al server**.

**La solución, en dos mitades:**

- **Cliente — el swing recuerda su objetivo** (`swingTargetId`): se setea en el press (donde el cono
  ya lo encontró), se refresca con cada scan exitoso. Si el scan del release da vacío, manda el
  recordado **si pasa el cono con slack** (`ConeTargeting.stillInSlackCone`: +10° por eje, +0,5
  bloques). Slack generoso donde sólo cuesta un paquete, estricto donde cuesta daño.
- **Server — ventana de confirmación** (`PendingHit` + `HitCoyote`): si el id llega, la entidad
  existe y está viva, pero `Reach.inCone` falla, **ya no whiffea al instante**. Guarda la intención
  (entidad, **carga congelada del release**, id del arma, `dueTick`) y **revalida el mismo cono
  estricto cada tick durante 5 ticks**. Conecta → `CombatEngine.landTheHit` (extraído para que el
  golpe diferido use **el mismo camino** que el instantáneo, sin una segunda definición). Vence → el
  sonido de miss recién ahí. El brazo se juega **en el release** (el swing pasó entonces); el
  cooldown ya se pagó ahí; la ventana no cobra nada.

**La invariante que NO se toca:** el cono del server nunca se relaja — la ventana perdona *timing*
(lag, interpolación, un tick de desfase entre copias del mundo), jamás *apuntado*: si el jugador
giró la vista, el primer re-chequeo falla. Cierres: release nuevo cancela el pendiente (en
`resolveSwing`, después del gate de swing abierto para que un release perdido no lo cancele),
cambio de arma cancela (el golpe es del arma que lo tiró), muerte limpia (sin `copyOnDeath`), y el
attachment **sin codec** porque es una confirmación en vuelo, no un estado (patrón `FallDeferral`).

`PendingHitTest` fija el borde de expiración; **sabotaje verificado** (`>=` → `>` hace fallar el
build). **⊥ playtest todavía** — la prueba es pegar en el borde del cono contra un mob que se mueve
lateral, y con lag.

---

# PARTE 3 — Animación por formato de pose + multijugador

> **Casi todo lo de esta sección está HECHO y verificado**, no planeado (2026-10-05). Los hechos
> verificados van como hechos; lo que sigue abierto está marcado como abierto. El detalle de cada
> ronda está en `CHANGELOG.md`.

## El problema, y las tres decisiones del usuario (2026-10-04)

| Dolor | Causa raíz | Estado |
|---|---|---|
| "cambiar una animación duele" | son **4 archivos** y un número mal puesto **⊘ falla el build**: falla en pantalla | **resuelto** → 1 archivo |
| "adivinar los cardinales es una mierda" | cada eje tiene **otra convención según 1ª o 3ª persona** — le pasó al arcabuz **3 veces** | **resuelto** → `axes` en el archivo |
| "cuesta sacar rotaciones naturales" | 4 curvas, ninguna modela "un hombro que carga y vuelve" | pendiente |
| "cuesta separar partes del modelo" | Blockbench exporta un bloque plano **s/ articulaciones** | **resuelto** → `parts` |

| Pregunta | Respuesta | Consecuencia técnica |
|---|---|---|
| a) ¿Hasta dónde tiene q verse bien? | **siempre, sin distancia** | un payload por jugador visible; `broadcast` no filtra distancia |
| b) ¿Los otros necesitan el viewmodel? | **no** | `ItemViewModel` **⊘ se tocó** |
| c) ¿Cuánto autoritativismo? | **el primero**: el server manda estado, el cliente calcula la pose | ⊘ ángulos por red |

Y la distinción que define el orden: *"lo del jugador queda a segundo plano"* (el PvP como feature)
**pero** *"siempre se debe ver bien, sin distancia"* (multijugador como infraestructura). PvP
secundario, **multijugador obligatorio**.

---

## El bug que lo disparaba — y su causa real

**Verificado, leyendo código.** `BodyPoseDispatcher.tick()` recibía **UN** jugador, leía
`ItemViewModel.frame()` (estado del cliente local) y lo guardaba en un `Map<UUID, BodyPose>` **que ya
acepta N**. El javadoc lo decía: *"Only the local player is ever asked... a real limitation"*.

**⊘ era bug. Era una consecuencia**, y la redacción lo disfrazaba de decisión. La causa: la pose se
calculaba desde cosas que **sólo el local tiene**. Arreglar eso, no el `Map`.

---

## 1 · El formato de pose: `pose/WeaponPose.java` — HECHO

Un archivo por arma, escrito a mano. `poses/shotel.json` es el primer caso real (6 tracks).

```json
{ "id": "cortescombat:shotel", "axes": "first_person",
  "rest": { "x":0,"y":0,"z":0,"pitch":0,"yaw":0,"roll":0 },
  "tracks": [ { "channel":"WINDUP", "curve":"LINEAR", "amplitude": {...},
                "tremor": {...}, "tremor_hz": 17.0, "tremor_weight": "EXPO" }, ... ],
  "body": { "aim": { "pitch": -96.0, "roll": -22.0 }, "reload": {...} } }
```

- **`axes` obligatorio, ⊘ con default.** Un default es un guess silencioso, y un guess sobre un signo
  es **exactamente** el bug que apuntó el arcabuz al cielo tres veces. 1ª persona: `-Z` forward,
  pitch positivo = morro arriba. Cuerpo: pitch **negativo** = brazo adelante.
- **Unidades: TODO en metros y grados.** El pivote es punto de pose, ⊘ de modelo → quien convierta un
  export de Blockbench divide por 16 **una vez**.
- **`parts` y `body` opcionales** → un JSON de 3 líneas es válido → **no hay que reescribir 31 armas**.
- **`rotates_on` obligatorio en cada parte**, sin default. Un pivote sin eje es una parte que alguien
  mueve "a ojo", y ése es el bug que la clase previene.
- **`tremor`, `tremor_hz` y `tremor_weight` son TRES campos**, no uno. Los descubrió el guard: Java
  tiene **6 tracks** y el JSON transcrito tenía 5, porque `Track#shaking` es un track de *otro tipo*
  — amplitud 0 y vibración encima, con frecuencia y curva de peso.
- **Matching por POSICIÓN, no por nombre de canal.** El sable tiene dos WINDUP (el cock y el
  temblor); por nombre los cruzabas y ambos comparados pasaban con los númeroswapados.

### `SabrePoseMatchesJavaTest` — el guard que forzo al formato a crecer

**Una copia de un número es una segunda verdad.** El JSON del sable **no** es autoritativo: es
*transcrito*, y este test es lo que hace segura la transcripción. Compara `rest`, `tracks` (canal,
curva, amplitud, los tres de temblor) y `body` contra `ViewModelDefaults.shotel()` y
`BodyPoses.parrying()`.

**Verificado que muerde**: saboteando el signo del CHOP (`-96`→`96`) — el bug exacto del arcabuz —
mueren **2 tests** por nombre de campo.

- `Track#evaluate` **no** sirve para recuperar la amplitud: devuelve "dónde está el arma *ahora*", que
  incluye el temblor sembrado por golpe. Un track de amplitud 0 produce `-0.0014` en z. Hay que leer
  `amplitude()`, el campo.
- **Un frame por canal**, no todos abiertos a la vez: con cinco canales a 1 la respuesta es la suma y
  no pertenece a ningún track. Y el frame del chop tiene que abrir **WINDUP ⊕ CHOP**: `chopping(1.0F)`
  deja windup en 0, y con ambos abiertos es donde "reemplazar" y "sumar" difieren. El guard **no
  mordía** hasta que encontré eso.

---

## 2 · `estado → pose` como función pura: `PoseState` ⊕ `PoseSolver` — HECHO

`PoseState` = arma, `ChargeProfile`, `GuardProfile`, flag de escudo vanilla, canales,
`ViewModelProfile`. `PoseSolver.solve(state) → Pose(Offset hand, BodyPose body)`. Sin reloj, sin azar,
sin `static mutable`.

**Las dos mitades salen de UNA llamada con las MISMAS puertas.** Antes eran dos lugares con dos copias
de "¿esta puerta responde?" — y ése es el shape del bug que ya costó una vez: el sable tenía el track
`PARRY` en el viewmodel y el dispatcher **no miraba** `PARRY` → 1ª persona cruzaba, 3ª el brazo quieto.
Un componente puesto y nadie dibujándolo.

`BodyPoseDispatcher#poseFor`: **103 líneas → 6**. Le pregunta al mundo cuatro cosas y delega.

### Las cuatro puertas, y por qué cada una es pregunta del perfil y no un nombre

| Puerta | Pregunta | Por qué así |
|---|---|---|
| chop | `reads(CHOP)` | el bump no se puede hacer caer; hace falta su propio número |
| hombro | `reads(HELD)` | el arcabuz es el único con HELD → "se puede hombro" tiene respuesta de perfil |
| bayoneta | `shouldered` | aim(−66) + strike(−104) = **−170**, pasado vertical |
| parry | `reads(PARRY)` | el canal es global y el estado es del jugador, no del item |

**Las de lunge y sweep pasaron de NOMBRES a capacidad** (`reads(LUNGE)` / `reads(SWEEP)`), verificado
que hoy es equivalente: ropera lee `LUNGE`, alabarda `SWEEP`, nadie más lee ninguno. **Es el único
cambio de comportamiento posible de la ronda** → hay que verlo en juego.

### `PoseSolverTest` — 12 tests, y dos guards que **no** mordían

- **El parry no tenía test.** Saboteando la puerta a `false`: **0 fallos en toda la suite**. Una
  puerta que falta en una reescritura **no falla nada**: dibuja un arma cuyo brazo no se mueve. Ahora
  hay test, y saboteando la puerta **o borrando la rama entera**, muere.
- **El chop tampoco.** `chopping(1.0F)` deja windup en 0, así que la rama saboteada daba `NONE` igual.
- **El techo de 120° me lo inventé**: `CHOP_ARM_PITCH` es **−140**. La aserción correcta no es un
  techo, es "el pitch **es** el chop, con el 104 del wind-up en ninguna parte".
- **Dos intentos míos fallaron por asumir**: el lunge de la ropera **sí** mueve la mano (lee LUNGE), y
  la espada **sí** lee `GUARD` (un scan de 90 líneas cortó antes del fin del método). El caso real de
  "cuerpo que se mueve y mano que no" es un `ViewModelProfile.NONE`.

---

## 3 · Lo que cruza el cable: **estado, no canales** — HECHO

**El hallazgo que cambió el diseño a mitad de camino.** Los canales **no son estado**: son la salida de
un reloj, y `ItemViewModel.MOTION` es un singleton del **cliente local**. El server **no tiene
reloj**, y `ViewModelMotion` (1500 líneas) no tiene un segundo modo de uso.

∴ mandar canales = darle al server **un segundo reloj para la misma animación**, que el proyecto **veta
por nombre**. Mandar **estado** es lo correcto: es la arquitectura de siempre ("el cliente predice
para dibujar y nunca decide").

`PlayerCombatState` — **10 campos**: `entityId · weapon · holding · holdTicks · guarding · stagger ·
parryActive · parryCooldown · lunging · cooldownRatio`.

- **`rawCharge` y `chargedRatio` NO viajan.** Son `holdTicks` leído contra el `ChargeProfile` del arma,
  y el item del otro **ya está en este cliente** (la hotbar la sincroniza el juego) → se derivan acá con
  `AttackTiming.chargeRatioFor`, la misma función. Mandar el ratio es mandar un número calculable.
- **`weapon` sí viaja** aunque el item sea visible: es la **decisión del server**, una arma de otro
  mod resuelve a algo que sólo el server puede nombrar, y dos clientes con distinto set de mods tienen
  que coincidir.
- **No es un detector de flancos.** `holdTicks` sube de a uno: un flanco mandaría dos paquetes y
  después silencio mientras el brazo vuelve. Va **cada tick si algo pasa** ⊕ **heartbeat de 20 ticks**
  si no (sin él, el primer frame de un desconocido quieto es nada).
- **El reloj del temblor NO viaja, a propósito.** Dos clientes ven el mismo movimiento y **distinto
  temblor**; mandarlo = todos trepan en lockstep y se reconoce a un jugador desde el otro lado del mapa.

`PlayerCombatStateTest` — **5 tests, 4 guards verificados**: swap `holdTicks`↔`parryCooldown` (2
muertes), sacar el clamp de `parryCooldown` (1), el de `stagger` (1), y `byOrdinal` sin guarda →
`ArrayIndexOutOfBoundsException: Index -56 out of bounds for length 14`.

---

## 4 · Los tres momentos: `CombatEventPayload` — HECHO

Un chop nace en el **release** (`ViewModelMotion:1002`, `chopAge = 0`) **con la carga con la que se
soltó**. Ningún estado lo recupera: "el frame del release tiene que mostrar el item atrás" es la razón
de `pendingStrike`. Estado solo = un strike sin carga = **un tap, siempre**.

| | emisor | reloj |
|---|---|---|
| STRIKE | `handleSwing` tras `resolveSwing` | `onStrike(charge, durationTicks)` |
| SWEEP | `SpecialHandler.resolve:82`, **después** del `sweep()` | `onSweep()` |
| CANCEL | `handleSwing` con `NO_SWING` | `onCancel()` |

- **Tres y no cuatro.** `ViewModelMotion#onGuardBreak` existe, es público, está en la API del reloj y
  **no lo llama nadie**: el collapse sale de `observeStagger`, y el `stagger` ya viaja. **Yo lo había
  listado como cuarto evento en dos javadocs.** Preguntar quién llama qué lo resolvió; diseñar desde
  cero lo habría inventado.
- **`resolveSwing` devuelve el ratio que cobró** (`NO_SWING = −1F`). El charge lo decide `CombatEngine`
  contra el `ChargeState` y el perfil del arma; el que lo necesita iba a recalcularlo → dos copias de
  una aritmética que decide si un golpe fue cargado.
- **`NO_SWING` es `−1` y no `0`, porque un whiff es un golpe real sin carga** y se dibuja. Con `0` se
  leería como "un tap": otra animación a otra longitud.

**Un guard que no muerde, y es coherente**: `NO_SWING = 0` → **0 fallos**; la rama del strike forzada a
`true` → **compila**. Ningún payload tiene test (implementan `CustomPacketPayload`) y un test de uno
**no compila**. La lógica de red queda verificada por **lectura ⊕ compilación**.

---

## 5 · El circuito, y el punto donde `BodyPoseDispatcher` se abre

```
CombatTicker.onPlayerTick  → PlayerStateSync.sync     (10 campos desde los attachments)
PacketDistributor.sendToPlayersTrackingEntity
ModNetworking.register     → playToClient
ModNetworking.handlePlayerCombatState → RemotePoseStates.accept
ClientTasks.onClientTick   → RemotePoseStates.tick   (UN ViewModelMotion por remoto)
BodyPoseDispatcher.snapshot → bifurea: local = frame propio, remoto = estado sincronizado
CombatTicker.onStopTracking → RemotePoseStates.forget
```

`snapshot` bifurca por `player != Minecraft.getInstance().player`, y **el mismo solver para los dos**.

**Dos bugs que aparecieron al conectar, y los dos son la misma clase de "algo puesto y muerto":**

1. **`RemoteMotion.cooldownRatio` lo usaba `snapshot()` y nadie lo seteaba** → el canal `COOLDOWN` de
   todo remoto estaba permanentemente en "listo" y un arma con ese track **no se movía nunca, en
   silencio**. La misma forma del track `BREAK` del sable que llevaba rondas muerto. **Se cazó
   chequeando campo por campo cuál se seteaba.**
2. **La interpolación se estiraba con un paquete perdido**: `previous` se empujaba sólo al llegar un
   paquete → `previous` congelado mientras `current` seguía → extrapolación con ropa de interpolador.
   Arreglado con `previous = current` en cada tick. Un paquete perdido ahora cuesta **un tick de
   antigüedad** (paso ≤5% del movimiento), acotado por construcción.

`PlayerStateSync`: el `stagger` es el **cooldown del item de guardia**, no el del arma (dos cooldowns
distintos → el equivocado hace stunear a quien recién puede pegar). El **lunge server-side** es
`ModEffects.LUNGE` → `hasEffect`, sin attachment nuevo.

---

## Lo que debería verse con dos jugadores — **lo local, VERIFICADO en cliente**

**Playtest 2026-10-05: "todas las animaciones parecen funcionar tanto en primera como tercera
persona, perfecto."** Tras la refactorización (103 → 6 líneas en `poseFor` + las dos puertas de
lunge/sweep de nombre a capacidad). ∴ **el refactor no rompió nada**, que era el riesgo real.

**⊘ Y eso es TODO lo que el singleplayer prueba.** El camino del multijugador **no se ejercitó ni una
línea**: `PlayerStateSync.sync` corre y `broadcast` no incluye al local → no mandó un solo paquete;
`RemotePoseStates` quedó con cero relojes; los tres eventos no salieron. El verde dice *"no rompí lo que
funcionaba"*, y nada más. **Un log de compilación ⊘ es un playtest**, y un playtest en single ⊘ es un
playtest del multijugador.

### Lo que falta ver, y con quién

**Hacen falta dos jugadores**: con uno, tu clock corre contra el tuyo y el camino nuevo no se toca.
Uno tiene que **mirar** al otro desde atrás (el que swings lo ve por predicción, como siempre).

Uno con el sable, el otro mirándolo (**F5**; no hay flag de 3ª persona por system property
en 1.21.1):

| | antes | ahora |
|---|---|---|
| wind-up y carga | nada | ✅ el brazo se cockea |
| **el corte** | **nada** | ✅ **ahora sí** |
| carga temblorosa | nada | ✅ el temblor del wind-up |
| parry cruzando | nada | ✅ blade up across, con roll |
| guardia | nada | ✅ sube con aguante |
| arco de la alabarda | nada | ✅ el sweep |

**Lo que NO cambia**: el damage ni las decisiones. El server sigue siendo la autoridad; el otro
cliente calcula una **pose**, no un golpe.

**Tres cosas que pueden verse mal, y el orden de sospecha:**
1. **Timing a destiempo** → el orden en `ClientTasks`: los relojes remotos tickean **antes** del body
   local, y **después** de `SwingInputHandler`.
2. **El primer sighting tarda hasta 1 segundo** (heartbeat). Compromiso escrito, no descuido.
3. La **bayoneta del arcabuz** (aim + strike) es la aritmética más frágil del solver.

---

## Lecciones de esta ronda

1. **Una copia de un número es una segunda verdad**, y el guard que la ata es lo que la hace segura.
   Escribí el JSON del sable *sin* él y el formato tenía un campo de menos: los tres del temblor.
2. **Un guard que no muerde y una aserción que no muerde son el mismo bug.** Uno pide `0.5F` donde la
   función quiere "la mitad de la bajada"; el otro abre `chopping(1.0F)` y cree que abrió el wind-up.
   **Probar que un guard muerde tiene que ser parte de escribirlo**, no una labor de QA posterior.
3. **Nada en `src/test` puede nombrar un tipo de Minecraft**, y eso corta en dos: un record puro con
   `encode`/`decode` sobre `ByteBuf` **sí** se testea; una clase que implementa una interfaz de
   Minecraft **no arranca** (`NoClassDefFoundError` en `<clinit>`). La línea es "**¿implementa algo de
   Minecraft?**", no "¿está en el paquete de red?".
4. **Un componente puesto y su consumidor muerto se distinguen leyendo**, y el síntoma que lo delata
   es **código que compila y no hace nada**: un `return 0.0F` de placeholder, un campo que nadie
   setea. Peor que un error, porque **el build está verde**.
5. **Dos relojes para una animación, cada uno con su propio archivo.** El local en
   `ItemViewModel`; el remoto en `RemotePoseStates`. Una instancia por jugador, y el `Map<UUID>` que
   ya existía era la estructura correcta desde el principio.
6. **Aseverar sobre "qué se ve en juego" sin abrirlo es cómo se affirmaron 7 canales muertos.** Lo
   que se puede verificar bare-JVM: el solver (12 guards), el codec (4 guards), el formato (2). El
   resto se verifica corriendo. **Un log de compilación no es un playtest.**
7. **Suponer "el más obvio" dos veces seguidas produce el test equivocado.** El lunge de la ropera y
   el `GUARD` de la espada: los dos parecían casos y los dos eran falsos. La pregunta útil no era
   "¿este canal se mueve?" sino "**¿quién lo enciende?**".

---

## El rework de las armas vanilla — la lista, y lo que ya está hecho

**Decisión del usuario (2026-10-05):** las herramientas de vanilla entran al combate y se
reworkean, **menos el pico**. Y **todo ítem que no sea un arma —ni el puño— pierde el combate.**

### Paso 1 — la pregunta de capacidad: `WeaponKinds` — HECHO

**`press()` no chequeaba el ítem en absoluto.** `MinecraftAttackMixin` cancela el click y
`SwingInputHandler.claim()` devuelve `OURS` si hay un objetivo en el cono, sin preguntar qué llevás
en la mano. Combinado con `deriveProfile` → `NONE` para lo desconocido y
`REACH.put(NONE, new ReachProfile(3.0F, 25, 20))`:

**un bloque, una poción o una herramienta desconocida clavaban a 3.0 de alcance** — más que el puño
(2.5) y casi como la espada (3.6), con wind-up, carga y cono propios. Es la mitad que faltaba de
*"todo objeto que golpeo puede cargar"*: la otra mitad se había arreglado en `deriveProfile`, y la
puerta nunca se puso.

∴ **`WeaponKinds.fights(perfil)`**, y es una lista de **perfiles**, ⊘ una pregunta sobre el ítem:

| intento | por qué no contesta |
|---|---|
| `instanceof DiggerItem` | agrupa hacha, pico **y pala** — y el pico es justo el que no debe pelear |
| "tiene daño de ataque" | cubre espada/hacha/pico/maza y **se pierde la pala y el tridente** |
| `WeaponItem` | **⊘ existe en 1.21.1** (verificado: sólo `ProjectileWeaponItem`, arco y ballesta) |
| tag vanilla | es lo que ya hacía `deriveProfile`, y responde *qué es* y no *debe pelear* |

**12 pelean, 2 no:** `UNARMED`, las 5 vanilla que entran, las 6 nuestras. **⊘ `NONE`** (es lo que
resuelve lo desconocido) y **`PICKAXE`** (decisión explícita del usuario).

### Paso 2 — las especiales inventadas: las 6 vanilla a `NONE` — HECHO

El hallazgo que hacía el paso 1 urgente: **las especiales de las herramientas estaban copiadas y
nadie las decidió.**

| Perfil | Item | Especial ANTES | Ahora |
|---|---|---|---|
| `SWORD` | espada | `NONE` | `NONE` |
| `AXE` | hacha | `SLAM` | **`NONE`** |
| `SHOVEL` | pala | **`SWEEP`** ← el de la alabarda | **`NONE`** |
| `MACE` | maza | `THROW` | **`NONE`** |
| `TRIDENT` | tridente | `THROW` | **`NONE`** |
| **`PICKAXE`** | **pico** | **`LUNGE`** ← el de la ropera | **`NONE`** |

**Una especial copiada es peor que `NONE`**: un `NONE` es una ausencia y una copiada es una decisión
tomada por omisión que **parece elegida**. Y la del pico era literalmente el paso de la ropera.

**Efecto colateral correcto:** `SpecialInputHandler:173` devuelve sin cancelar cuando la especial es
`NONE` → **la maza y la tridente vuelven a lanzar como vanilla** (`THROW` era el nuestro, no el de
Minecraft). La pala, la hacha y la espada no tienen click derecho, así que no cambia nada.

**Lo que cada una le debe al diseño — sigue sin decidirse:**

- **hacha** — el arma más rápida del juego cerca. No debería pagar su velocidad dos veces.
- **pala** — le queda bien un arco ancho y barato, **pero una pala no es una alabarda**: ¿en qué se
  se diferencia? Es la que más $.
- **maza** — el lanzamiento de vanilla es real. ¿Cuánto cuesta acá?
- **tridente** — 3 proyectiles, y **ninguno está en el cono**.
- **espada** — el rework más chico: ya es una espada.

### La segunda puerta: el tooltip — HECHO

`CombatTooltipHandler.showsTooltip` preguntaba `profile() != NONE`, o sea **otra pregunta** por el
mismo hecho, y el pico pasaba: imprimía alcance y cono de un perfil que ya no pelea. **El jugador se
lee un número de un ítem y se lo cree** — ahí es donde el error se ve, no donde se oculta.

Ahora pregunta `WeaponKinds.fights()`, y eso **devuelve los números de vanilla al pico**, que es lo
correcto: una herramienta que no pelea tiene que **parecer** la herramienta que es.

### La invariante que faltaba, y que el test afirma

**"Lo que no pelea no puede contestar un botón."** `SpecialInputHandler` **no** pregunta `fights()`,
pregunta si la especial es `NONE` → el pico era seguro por **dos razones sin relación**. Cualquiera
de las dos lo cierra y las dos son una línea. Afirmado cruzando las dos tablas
(`WeaponTablesTest.notFightingMeansNoSpecial`), porque un test de cada mitad separada es una
aserción sobre un offset compuesto del invariante que importa.

### Los guards de esta ronda, todos verificados mordiendo

Saboteados a propósito, uno por uno. **6 sabotajes → 6 muertes por su razón:**

| sabotaje | muere |
|---|---|
| el pico entra a `COMBAT` | 3 tests |
| `NONE` entra a `COMBAT` | 3 tests |
| la puerta de `press()` → `if (false)` | `thePressAsksTheQuestion` |
| la puerta **después** del guard de pantalla | `thePressAsksTheQuestion` |
| `combatProfiles` devuelve el set mutable | 2 tests |
| la pala recupera el `SWEEP` | `theVanillaSixHaveNoSpecial` |
| el pico recupera el `LUNGE` | **2** tests — el de la especial **y** el de la invariante |
| el tooltip vuelve a `!= NONE` | `theTooltipAsksTheSameQuestion` |

> **Y una aserción que escribí y que era basura**, anotada porque es la clase exacta de aserción
> que no muerde: puse `assertTrue(handed.add(PICKAXE) == false || true)` para afirmar que el set
> es inmutable. **Es verdadero pase lo que pase.** Lo correcto era `assertThrows
> (UnsupportedOperationException.class, ...)`, que además dice qué pasa. Escribí la versión
> débil porque "afirmar que algo es inmutable" sonaba a que no hacía falta pensarlo.

---

## Pendientes abiertos (el detalle de cada ronda esta en `CHANGELOG.md`)

| # | Que | Estado |
|---|---|---|
| 1 | **Colgado en "Guardando mundo"** con el reactor cargando/sobrecargado. Causa: `getBlockState` dentro de `setRemoved`, que pide con `requireChunk=true` el chunk que se esta descargando y espera a un pipeline que se esta parando. Fix: la pregunta paso a `NetherReactorBlock.onRemove` + `reactor.onBlockRemoved`. | **CERRADO y verificado en juego (2026-10-08)** |
| 2 | **Sonido: `Maximum sound pool size 247 reached`** en spam masivo (22:15:34-35). Algo pide sonidos cada tick y nunca libera handles. Sospechoso: el sonido de encendido de `refreshState` en el flanco `lit` -- si `lit` oscila suena UP/OFF alternados cada tick y en ~120 ticks colma los 247. **Un `lit` oscilante explicaria tambien el radio de redstone erratico.** | **CERRADO: el culpable es `nuclear_explosion`** (primera linea del pool = mismo segundo que la detonacion). La hipotesis del `lit` oscilante quedo descartada con datos (0 lineas de pool en 3 sesiones sin detonar) |
| 3 | Radio de redstone inconsistente (a veces 2 mecanismos, a veces 1): el barrido es asincrono (40k pos/tick, ~1-2 s de recorrido) y cada componente se prende cuando lo pasa el cursor. Opciones: barrido inmediato si cabe en el presupuesto, o mostrar "barriendo..." en `/cc reactor status`. | **CERRADO por el rediseno cableado**: el barrido sigue siendo asincrono pero ahora notifica POWERABLE dentro del radio del reactor, y la membresia no se estira mas alla de ese radio |
| 4 | Sprites de estrella sin cablear: `textures/block/nether_reactor_star.png` + `nether_reactor_star_e.png` (emisivo) -> tercer estado del blockstate + overlay. | **HECHO** (propiedad STAR + 5 partes de blockstate con AND + modelos + runData) |
| 5 | Particulas de brujas disparandose al final de la sobrecarga por nether star. | pedido del usuario, PENDIENTE |
| 6 | Playtest del reactor: radio del crater 48, dano/kb/fuego, volumen 16, shake 1,2 grados, onda 6000/tick. El crater no prende fuego en el terreno (solo entidades). `forceOverload(FIRESTONE)` usa `debugActive` no guardado (solo debug). | **VERIFICADO 2026-10-08** (radio crater y volumetria salieron del rediseno; el crater sigue sin prender fuego al terreno) |
| 7 | Playtest de la pala (scoop 0.4/33, y +0.28) y la azada (tajo CHOP 112 de yaw, cleave SWEEP 190, sin WINDUP). Icono 2D del arcabuz en GUI real; si molesta en la preview, mixin sobre `ItemInHandLayer`. | **VERIFICADO 2026-10-08** |
| 8 | Paso 11: supervivencia (`FoodData`) -- el cambio mas caro, en commit propio. Los umbrales internos hay que LEERLOS en las fuentes de 1.21.1, no de memoria. | pendiente |
| 9 | Rework de ORO y COBRE: diseno cerrado (seccion de abajo), sin implementar. | pendiente |
| 10 | Parry contra jugadores + prueba de multijugador real: se cierra solo con **2 jugadores** (`/cc trace` y un compa). | pendiente |
| 11 | `/cc weapons` trae 11 vanilla. Arcabuz: feedback por objetivo, flash a 1,7 m, sonido de carga completa, `CombatFeedback.CHARGED`. | deuda |
| 12 | Sonido de carga completa del viewmodel e impacto de golpe cargado. | deuda |
| 13 | **La red de redstone se ENCOGE cuando termina una sobrecarga**: la estrella publica radio 100 y el firestone a 40 cargas publica 60, y al cortarse el fusio el radio vuelve al de la carga real -- los cables de mas pierden membresia, recalculan con `-1`, y `max(i, j)` **nunca devuelve el punto perdido** (el toggle los reengancha). Instrumentado: `[red] reactor X dado de baja ... por refreshState` ya nombra al que borra. Fix honesto: **el radio de un reactor solo deberia crecer durante su vida**. | **CERRADO 2026-10-08**: high-water persistido (`publishedRadius`, guardado en NBT); `lit` false publica cero, que es una decision y no un encogimiento |

| 14 | **Weapon packs (fases 1–4 hechas):** resolver + loader + puentes (`WeaponStats.of()` = componente > pack > tabla; guard en `GuardStats.of()`; atributos en `LOWEST`) + **Nivel B** (`WeaponPackItems` en `RegisterEvent` de ITEM: nace con atributos de espada de hierro, nombre vía `CUSTOM_NAME` — 1.21.1 no tiene `displayName` —, tab ajena = warning + `/give`, y el template en `resourcepacks/cortescombat-weapon-pack/` que no pisa lo del usuario). **Expuesto ya:** perfil, special, damage, attack_speed (el tempo — **no hay campo `cooldown`**), reach (sólo distancia), cone (radio crudo por eje; la banda vieja da warning con el mapa a escribir), charge (puerta+bonus+ticks), knockback (clampeado 1.6/0.45), shot, guard. **Encontrados SIN cablear:** `max_targets` (3 callers con `stats.profile()`), `pull` (proyectiles, fuera de diseño), `firesOnPress`/`canHoldDown` (input), `durability` (pensado para `creates`: el ítem nace de1 usos — falta decidir si se puede setear ajenos). **Tests JVM no pueden cargar clases de Minecraft** (el template falló con `NoClassDefFoundError: ItemLike` vía `CortesCombat.LOGGER` → logger en el wrapper, I/O puro adentro). **Fase 5 (tags category) hecha:** `categoryTags()` en el datagen itera `ArchetypeSpec.builtins().keySet()` (sin lista huérfana), 9 archivos vacíos en `generated/tags/item/category/`, `builtin.yaml` traduce los9 → arquetipo, `WeaponPackBuiltInTest` (4) afirma el sistema completo: arquetipos = traductores = archivos. **Fase 7 (sonidos) HECHA:** `WeaponPackSounds` registra los ids del YAML en `Registries.SOUND_EVENT` (el `ClientboundSoundPacket` serializa holder por id — un id crudo no viajaría) y `CombatSounds.play` elige pack sobre perfil: **HIT→`hit`, CHARGED_HIT→`charged`, MISS→`swing`** (el `swing` del YAML es literalmente "the swing found nothing"); CHARGE/PARRY/LUNGE sin campo, siguen con los nuestros. **Fase 6 PARCIAL:** `shotel.json` movido a `assets/cortescombat/poses/` (el `SabrePoseMatchesJavaTest` falló con su mensaje diseñado para esto ✓); **falta el loader de poses + integración al solver y el sprite slash por YAML**: los dos tocan sistemas cerrados del cliente (binding de sprites con índice cerrada, y dos formatos de pose WeaponPose vs ViewModelProfile) y **no se pueden verificar sin abrir cliente** → van en la ronda del primer playtest. **Fase 8 HECHA:** `/cc reload` (relee config sin reiniciar; `WeaponPackStore.reload()` devuelve `Summary(packs, entries, warnings)`), `/cc packs` (lista cargados + qué entrada matchea el arma en mano) y `source=` en `/cc stats` (`pack id=...` vs `sin pack (tag molde → vanilla → NONE)`). **Guía de usuario: `docs/armas-custom.md`** (3 casos: otros mods, los nuestros, `creates` + referencia de campos + troubleshooting). Pendiente de la8: modo `strict` (campo desconocido = error de carga, hoy es warning). **VERIFICADO EN CLIENTE (2026-10-10, 3 playtests):** los 3 caminos cerrados — YAML sobre vanilla (`iron_sword` con stats custom; tooltip 12.0 Damage = 5 corazones × 2), `creates` (`jugadorsito:daga_de_prueba` con icono+textura en resource pack ajeno — **0 hits de `jugadorsito` en `src/`**, la prueba dura de que es 100% externo — y `dalilandia:chistorron` con stats ridículos: 60.0 damage, 12/s, charge instantánea ×6, reach 6.0, cono 85/85, knockback 1.6/0.45 = techos exactos clampeados bien, durability 1) — y tag-molde por datapack (`blaze_rod` → `profile=ropera` tras `/reload`). `/cc reload` y `/cc packs` OK; sonidos del arquetipo como default (`daga_miss` en el pack sin sounds). **Faltan sólo: fase 6 (poses + sprite slash) y modo `strict`.** | **CERRADO salvo fase 6 + strict** |

**El lector nuevo empieza por `ARCHITECTURE.md`, luego esta seccion, luego `docs/api-trampas.md`.**