# Changelog

Historia del mod. `AGENTS.md` tiene lo que hay que saber **ahora**; esto es lo que se pensó y se
midió en el camino. Los números de las decisiones racionales están en `AGENTS.md`; los de los bugs
que las costaron están acá.

Las tres cuentas largas que se movieron acá son **el riptide del tridente** (el dash de vanilla
leído entero), **el lunge de la ropera** (cinco bugs del mismo tipo) y **la tanda de las siete
armas** (doce bugs, cinco rondas de playtest). Las tres tienen la misma forma: **un número que
parece controlar algo y no lo controla.**

## Cuentas largas

### El riptide del tridente — el dash de vanilla, leído entero

Verificado en las fuentes de 21.1.250. **En 1.21.1 no está en `TridentItem`**: el encantamiento `RIPTIDE` solo declara `TRIDENT_SPIN_ATTACK_STRENGTH` (`LevelBasedValue.perLevel(1.5F, 0.75F)`) y `TRIDENT_SOUND` (los 3 sonidos). El movimiento está entero en **`TridentItem#releaseUsing`**, tres líneas:

```java
player.push(f2, f3, f4);                                  // f = spin strength, eje de la mirada
player.startAutoSpinAttack(20, 8.0F, stack);              // 1 s de ventana, daño FIJO 8.0
if (player.onGround()) player.move(MoverType.SELF, new Vec3(0, 1.2, 0));   // el saltito
```

- **`Entity#push(double,double,double)` es dos líneas**: `setDeltaMovement(delta.add(x,y,z))` + `hasImpulse = true`. Aditiva. **⊘ push power, ⊘ clamp, ⊘ control por tick, ⊘ distancia destino.** Vanilla **no sabe ni le importa cuán lejos te lleva**: lo decide el rozamiento.
- **Los dos lados ejecutan el mismo código sin ningún packet.** `MultiPlayerGameMode#releaseUsingItem` manda `ServerboundPlayerActionPacket` **y llama `player.releaseUsingItem()` local**. Esa es la única razón por la que el riptide no tira: el cliente se empuja a sí mismo con el mismo `push`.
- **`use()` falla entero** si hay spin attack y `!player.isInWaterOrRain()` → no podés ni empezar a cargar. Lluvia/agua **deshabilitan** el riptide.
- **El daño es el volumen barrido entre dos ticks**, `LivingEntity#checkAutoSpinAttack(before, after)`: `before.minmax(after)`, o sea **literalmente el camino recorrido**. No es una caja desde el torso ni un cono.
- **Pega UNO y se frena**: `break`, `autoSpinAttackTicks = 0`, y `deltaMovement.scale(-0.2)` (revierte 80% de la velocidad). Una pared también lo corta: `else if (horizontalCollision) ticks = 0`.
- **El daño pasa por `Player#attack`** con `autoSpinAttackDmg` en vez del atributo → encantaciones, `getAttackStrengthScale`, armadura y mensaje de muerte funcionan todos.

**∴ lo que nos cambia el diseño:**Llevamos varias rondas intentando que el lunge tenga una **distancia exacta** por tier (`ticksFor`, `FIRST_STEP`, `RETENTION`, y los dos callejones sin salida de escalar paso o decaimiento). **Vanilla no tiene ese concepto.** Un dash en Minecraft es un impulso y el rozamiento hace el resto. Nuestra `ticksFor` resuelve un problema que el motor no plantea, y es exactamente lo que produjo los dos callejones: el techo de la serie geométrica `FIRST_STEP/(1-decay)` hacía el netherite imposible antes de escribir el código.

Lo que se toma: **un impulso y nada más** (⊘ `LungeMotion` seteando velocidad tick a tick), **volumen barrido** para el golpe (`before.minmax(after)`), **la pared lo corta gratis** porque el movimiento es real, **un solo número** (la magnitud del impulso, 2.25 / 3.75 en vanilla = 1.67:1) en vez de una tabla de distancia, y **el saltito de 1.2**. Lo que **se diverge a propósito**: el escalado ×1→×3 del lunge nuestro (el `break` de vanilla es un one-hit-and-stop; una estocada que atraviesa una línea es la fantasía de la ropera) y que la lluvia lo deshabilite.

**Dos verificaciones que supporteron el plan** (las dos del mismo jar, no de memoria): `LocalPlayer#sendPosition` manda `ServerboundMovePlayerPacket.Pos` **cada tick**, así que el servidor ya sabe dónde está el jugador sin ningún packet nuevo → la medición no cuesta nada. Y `isAutoSpinAttack()` aparece **solo** en `getRopeHoldPosition`: no toca `travel` ni el control de aire → **durante el riptide tenés control de aire normal**, te dirigís con WASD. Eso último mata el `STEER_SHARE`: existía porque nosotros **seteábamos** la velocidad y no había nada contra qué pelear; con un `push` solo, el motor dirige.

### El lunge de la ropera — CERRADO, y las cinco cosas que costó

**Estado: verificado en cliente real.** Distancia, daño, escalonado, inmunidad, y el techo que decide si un lunge cuenta. De abajo está el detalle; lo que va antes de todo es la lista de bugs, porque son el mismo tipo.

**1. El rozamiento que no existe.** `Entity#move` termina en `setDeltaMovement(getDeltaMovement().multiply(getBlockSpeedFactor(), 1, ...))` y el default de `BlockBehaviour.speedFactor` es **1.0**, no 0.6. No hay constante que dividir. `impulso / 0,6` era un número inventado y quedó **cuatro rondas** en el código.

**2. Un número medido con el harness encendido.** `DISTANCE_PER_IMPULSE` se "midió" una vez con `/cc speed` corriendo: 2,12. El playtest siguiente logueó **13 lunge y los 13 cerraron `fin=ilegal`** — el techo derivado daba 7,1 y un lunge real lleva 6-7. El error iba en la dirección que hacía los lunge parecer **cortos**, por un factor de casi 3.

**3. El número bueno, de tres corridas limpias** (parado, sin harness): impulso 1,12 → **6,01**; 1,08 → 6,71; 1,12 → 6,76. **`DISTANCE_PER_IMPULSE = 6.0`.** Y el criterio del jugador lo confirma solo: impulso 2 era "demasiado" y 1 es "excelente", y 6 es la mitad de 12.

**4. El techo se derivaba de un número que no lo controlaba.** `ceilingFor` era su propio par de constantes al lado del impulso: cuatro números describiendo dos curvas, y retunar uno dejaba atrás al otro. Ahora es `estimatedDistance × CEILING_MARGIN` — **un número solo**. `CEILING_MARGIN = 3.0` porque tiene que absorber un lunge tomado a carrera (`push` es aditivo) y el harness.

**5. El corredor era más chico que el cono del swing.** `Reach#inCone` mide desde el ojo hasta el **punto más cercano** de la caja con tope `reach.distance()`; el corredor se inflaba `0.3 + alcance*0.5` = 2,0 contra los 3,4 del arma. **Un lunge que cuesta 8 swings pegaba menos que no hacer nada.** Después se resolvió al revés (alcance entero = 6,8 de caja) y tampoco: la hoja apunta a donde apunta el lunge.

**El orden importa y por eso no lo voy a repetir:** el chequeo de legalidad estaba **antes** del barrido, así que un lunge ilegal no llegaba nunca al daño. Eso explicaba los TRES síntomas reportados ("no hace daño", "falla la inmunidad", "sobre todo rápido") como una sola cosa. **Un bug, tres síntomas, cero pruebas rojas** — porque el código era correcto y el número que decidía no lo era.

**Lo que quedó:**

- **Movimiento:** un `push` (`look plano × impulso`) + saltito de 1.2. `BASE_IMPULSE 1.0`, `PER_TIER_IMPULSE 0.02` — la tabla va de 1,00 a 1,12, o sea **el lunge es el único eje que el material no toca**. Dos reglas murieron acá: "un tier mejor nunca es más rápido" y "un tier mejor lleva más lejos". Ambas protegían un modelo de movimiento que ya no existe.
- **Techo:** `impulso × 6.0 × 3.0`. No es una promesa, es un tope anti-trampas.
- **Golpe:** **un cubo de `alcance × 0.5` centrado en el jugador**, no un corredor barrido. La forma es la misma (un corredor dilatado por `h` es el camino dilatado por `h`) pero sin `minmax` que pueda salir invertido ni tapar la esquina cuando el lunge gira. `CUBE_RADIUS = 0.5`.
- **Fin:** **medido, no contado.** Termina cuando el cuerpo deja de moverse (`STILL = 0.02`), porque 20 ticks era casi el doble del movimiento y la mitad de cada lunge era un jugador parado. Tope duro 20 por si un datapack raro.
- **Inmunidad:** doble mecanismo a propósito. `invulnerableTime` a 12 ticks por tick, **más** un `MobEffect` (`ModEffects.LUNGE`) que cancela en `LivingIncomingDamageEvent`, que dispara **antes** del chequeo de `invulnerableTime` (L1165 vs L1190 en `LivingEntity#hurt`). `clear()` saca el efecto, así que la inmunidad dura **exactamente** lo que el lunge. El efecto además se ve en el HUD, que era lo que `invulnerableTime` no puede hacer — aunque el ícono no apareció en el Atlas y da igual: la mecánica funciona igual.
- **Balance final:** daño del lunge `0.75 × base`, techo `4.0`, paso `0.375` (sube solo con el techo: con 0.25 el tope 4.0 tardaba 12 enemigos y era decorativo), daño base de la ropera `5.0`, cono `3.5` (la espada está en 3.6 y la ropera tiene que seguir siendo la de menor alcance de las armas comprometidas).
- **El cruce a un enemigo:** contra uno el lunge pega 3,75 y un swing 5,0 — **pierde**. Contra dos, 5,16 — **gana**. Un enemigo de diferencia entre perder y ganar, y es un test.

**Lo que quedó afuera y por qué:** el cubo se deriva del alcance del arma, así que subir el cono de 3,4 a 3,5 también ensanchó el cubo de 1,70 a 1,75. Cinco centímetros, imperceptible. Si alguna vez hay que congelarlo, `CUBE_RADIUS` pasa a ser un número absoluto.

### El lunge — 2b, y el bug de diseño que escondía

**Elegido: 2b.** El cliente se mueve, el servidor **mide después**, y si no da no concede nada (sin rollback). La razón de elegirlo sobre "esperar permiso": **el servidor puede reconstruir todo menos el movimiento.** El golpe lo recalcula desde la fórmula, el cooldown lo lleva su reloj, los i-frames los decide `Lunge.tick`. Lo único que existe **solo en el cliente** es a dónde llegó el jugador. ∴ no se le pregunta *"¿puedo moverte?"* (que necesita permiso antes) sino *"¿esto fue legal?"* (que se responde después, gratis, y sin rollback).

**El bug que había debajo, y era de diseño y no de red:** `SpecialHandler.lunge` resolvía el golpe **en el press**, con una caja de la distancia *prometida* dibujada adelante del jugador — **antes de que se moviera un centímetro**. O sea: **la cobertura era gratis.** El diseño dice que el paso es el precio y se paga en posición, y un volumen que no mira dónde fue el cuerpo cobraba por nada. Por eso "el lunge se siente mal" nunca se arreglaba con números: los números estaban bien y la transacción estaba incompleta.

- **Movimiento:** un `push` (`flat look × impulseFor(rank)`) + el saltito de 1.2 si está en el piso. `LungeMotion` perdió su `tick` entero.
- **Techo por material:** `BASE_IMPULSE 2.0` + `0.3/rank` (netherite 3.8 → ~6,3 blocks con rozamiento 0.6, la promesa vieja). `BASE_CEILING 7.0` + `1.2/rank`, que es **un tope anti-trampas y NO una promesa** — deliberadamente ~2× lo que el impulso produce, para que girar, llegar corriendo o bajar una cuesta sigan siendo legales.
- **Golpe:** cada tick, `swept(lastX, lastZ, x, z, reach)` = **el volumen barrido entre dos ticks**, y después `corridor.intersects(candidate.getBoundingBox())` (la forma de vanilla). `lastX/lastZ` están en el estado **a propósito**: una caja de `start → actual` taparía el triángulo de la esquina si el jugador gira, o sea prometería suelo que no cruzó.
- **Fin:** 20 ticks (el número de vanilla), o pared (`horizontalCollision`), o techo pasado.
- **El material se guarda en el estado.** Leer el item vivo dejaría que cambiar a una ropera de madera en el tick 14 colapsara la tolerancia bajo un lunge ya legal — que es el bug de "un número que sobrevive a lo que lo justificaba", corriendo al revés.

**Una regla murió con la cosa que protegía:** *"un tier mejor nunca es más rápido"*. Existía para mantener el paso constante mientras el recorrido se alargaba; con un impulso solo no hay paso, así que no quedaba nada que proteger. Ahora el tier compra velocidad **y** distancia, que es lo que hace vanilla. Anotado porque es el contraejemplo útil de la lección del `* 0.5`: **un número puede estar protegiendo algo que ya no existe, y el test que lo defendía sigue verde.**


## Iteraciones

- **La guía de armas propias, en los dos idiomas, y el ejemplo de config anotado.**
  1. **La guía era un solo archivo en español** (`docs/custom-weapons.md`) y el README en inglés
     apuntaba a ella. Ahora son dos: `docs/custom-weapons.md` (inglés) y `docs/custom-weapons.es.md`
     (español), cada uno con una línea arriba que dice dónde está el otro. **Cada README apunta al
     suyo**, que es la regla del par del repo.
  2. **El ejemplo de config es un archivo nuevo** que el mod escribe en
     `config/cortescombat/weapons/ejemplo.yaml.example`, con cada campo anotado en español y en
     inglés, línea por línea. Antes no había ningún ejemplo: la guía mostraba tres fragmentos y
     el usuario tenía que armarlos.
  3. **La extensión es `.example` y no `.yaml`, y eso es la decisión que importa.** El loader lee
     todos los `*.yaml` y `*.yml` de la carpeta, así que un ejemplo con esa extensión se leía como
     un pack vacío y dejaba un warning **en cada arranque**. Con `.example` el loader ni lo ve.
     El test lo afirma: `loadDirectory` sobre la carpeta del ejemplo devuelve cero packs y cero
     warnings.
  4. **El resource pack ahora tiene dos LEEME**, `LEEME.txt` (español) y `README.txt` (inglés), en
     la misma carpeta. Uno mezclado se lee mal de arriba abajo y quien lee un idioma se queda con
     la mitad de las anotaciones.
  5. **El logger quedó afuera del I/O otra vez.** `writeConfigExample` y `writeResourcePackFiles`
     devuelven un booleano y el log lo pone el wrapper. La primera versión logueaba en el camino
     feliz y el test murió con `NoClassDefFoundError: net/neoforged/fml/config/IConfigSpec`:
     `CortesCombat.LOGGER` carga la clase entera y con ella los tipos de Minecraft.
     **`CortesCombat.MODID` sí se puede usar, porque es una constante y javac la pega adentro.**
  6. **1170 tests verdes.** Los tres del template cubren los dos LEEME, el ejemplo bilingüe, y que
     el loader no lo vea.

- **El README, reescrito por el dueño, y las páginas de las dos plataformas.**
  1. **El README nuevo es del dueño** (voces suyas, "Charge!!!", los chistes) y se respetó tal
     cual. Lo que se corrigió fueron las **rutas absolutas de Windows** en los `![](...)`: en
     GitHub, CurseForge y Modrinth no funcionan, y era el mismo bug en los seis.
  2. **Los medios son 5 GIF y 1 foto**, los que capturó el dueño: `cool_sword_shi`, `reach`,
     `cooldown`, `charged_attack`, `shield` y `reactor.webp`. Los 14 placeholders que había puesto
     antes se borraron, y con ellos la guía de captura que los describía.
  3. **Las páginas de CurseForge y Modrinth están en el mismo formato y el mismo tono** que el
     README, con lo que cada plataforma pide encima: metadatos, galería, changelog. CurseForge
     lleva HTML y Modrinth Markdown, porque es lo que renderiza cada una.
  4. **El peso de los GIF.** `cooldown.gif` pesa 5,8 MB. GitHub aguanta, las galerías tardan.
     **El encoder de GIF de ffmpeg es peor que el de la herramienta con la que se capturaron:**
     a la misma resolución y los mismos fps, **ffmpeg agranda los archivos**. La compresión con
     ruido de captura de pantalla no se deja, y el camino es recapturar con menos fps.
  5. **El `README.es.md` se reescribió como espejo** del nuevo, con las mismas seis imágenes y la
     misma tabla. Y se le corrigió una palabra china que se me había colado en la ronda anterior.

- **El README, las galerías y los 14 slots de captura. Sin tocar código de juego.**
  1. **El README inglés se reescribió entero**, con la estructura de la skill `crear-repo`: título y
     tagline, badges, descripción corta, **el demo visual antes de las features**, la comparativa
     con alternativas, instalación, FAQ, configuración, troubleshooting, la explicación técnica sin
     jerga, el bloque de despedida y el pie de Contributing/Security/License. El español se
     reescribió como espejo, porque el par tiene que decir lo mismo.
  2. **Un número del README viejo estaba mal, y era de los que no se ven.** Decía *"34 NEW
     enchantments"*. Los archivos son **48** en `data/cortescombat/enchantment/` y **6** de vanilla
     reworked (`sharpness`, `smite`, `looting`, `knockback`, `fire_aspect`, `bane_of_arthropods`).
     Lo que decía `AGENTS.md` y `docs/encantamientos.md` §10 (34) era de antes de la tanda de
     encantamientos. **El número del README se verificó contra el disco, no contra la memoria.**
  3. **La receta del arcabuz tampoco era la que decía el README.** Es bambú + tablones +
     dispensador + pedernal, y la **lastima** es pólvora + polvo de piedra luminosa. El README
     viejo decía "una lastima y pólvora" y mentía.
  4. **El `README.es.md` del working tree estaba corrupto**, con un carácter de reemplazo U+FFFD en
     cada acento (`Cortés` salía `Cort?s`, `mecánicas` salía `mec?nicas`, y `había` salía `hab??a`).
     La versión commiteada estaba bien, así que el archivo se regeneró desde cero. **El `write` de
     la herramienta escribe UTF-8 bien y PowerShell no**, y el escaneo de U+FFFD es el que lo
     detecta.
  5. **Los slots de captura existen y están marcados.** 14 GIFs en `docs/media/`, cada uno con un
     placeholder gris que dice qué tiene que mostrar, más el comando de ffmpeg en
     `docs/media/CAPTURE-GUIDE.md`. El README, la página de CurseForge y la de Modrinth apuntan a
     **los mismos nombres de archivo**, así que la captura se graba una vez y se usa en los tres.
  6. **La tabla de armas del README sale de `WeaponTables`.** Alcance, arco y carga de los 13
     perfiles, leídos del código y no del `DESIGN.md`.

- **Las siete armas, el arcabuz, y un tajo que no cabe en el canal de la estocada. 639 tests.** Commit `480fd7f`, el primero desde el lunge. Cinco rondas de playtest y **doce bugs reales**, la mayoría de ellos números que describían algo que ya no era cierto.
  1. **Grados sumados a un campo en radianes.** `ModelPart.xRot` está en radianes y el cuerpo sumaba GRADOS ahí: 46 grados como 46.0 son **46 vueltas completas**. La "voltereta hacia el frente varias veces", en los tres canales a la vez, en cada frame. Un número plausible como número y absurdo como ángulo — y el único test que lo atrapa es uno que convierta a radianes y mire.
  2. **El signo al revés en TRES poses.** `setupAttackAnimation` manda el brazo a **−69°**; positivo es **hacia atrás**. La carga, la guardia y el golpe eran positivas, y dos de las tres se reportaron como "al revés". La tercera se iba a reportar después.
  3. **`attackTime` lo asigna el renderer ANTES de `setupAnim`.** Una inyección al TAIL suma encima del swing de vanilla. Hace falta una al HEAD que lo cancele — **solo si el perfil es nuestro**, o un hacha vanilla pierde el golpe.
  4. **La segunda skin layer no seguía al brazo.** `rightSleeve`/`leftSleeve` son `ModelPart` aparte y vanilla copia el brazo a la manga **antes** de nuestra inyección. `copyFrom` después.
  5. **`BodyPose` arg 7 es `bodyPitch` y el 8 `bodyYaw`.** Intercambiados: el torso de la guardia se iba al eje equivocado y un test verde no lo nombraba porque la aserción estaba escrita sobre el mismo campo.
  6. **Un bump no puede ser un tajo.** `STRIKE` es `released · sin(√fase · π)`: sube y vuelve, que es una estocada — y por eso la ropera y la daga suenan *"exactas"*. El tajo necesita un número que **solo baja**: canal `CHOP` con reloj propio. **La puerta es `reads(CHOP)` y no una lista de armas**, que es la lección que el sable ya dio una vez.
  7. **`pendingStrike` retrasa un tick, y para una ALTURA eso es un frame en reposo.** El release sólo *anuncia* el golpe. Para el bump da igual (arranca en cero igual); para el chop es **un frame con el brazo colgando y después al tope** — literalmente *"empieza desde el estado de reposo hacia el golpe"*. El chop arranca en el release, y `CHOP_START` dejó de ser un número elegido para ser **la razón entre las dos alturas**: dos constantes que coincidían sobreviven exactamente un retune.
  8. **El easing de la caída estaba invertido.** Era `(1−t)²`: pendiente máxima en t=0, nula en t=1. **La hoja salía a tope y se frenaba — un arma arrojada.** Una caída que frena no tiene impacto porque lo más rápido ya pasó cuando el ojo llega. Ahora `1 − smoothstep`. Y **4 ticks de caída, no 6**: el impacto es velocidad sobre distancia, y la distancia nunca fue el problema.
  9. **El tajo levantaba los DOS brazos** — el único pitch de segunda mano que quedaba de cuando era copia del strike. Un golpe se compromete con un brazo; la guardia y la carga usan las dos porque son postura y reunión.
  10. **`ItemPropertyUtils` y `assignIndex` no existen en 1.21.1** (son de 1.20). El API real es `ItemProperties.register(Item, ResourceLocation, ItemPropertyFunction)` y **no hay `assignIndex`**: el modelo no guarda un número, la función lo **calcula** por frame desde la pila — por eso el frame de la mecha va en un **data component** y no en un campo del cliente.
  11. **"No tiene texturas" NO era el `.mcmeta`: era la carpeta.** `blocks.json` de 1.21.1 construye el atlas desde `block/` y `item/` **y nada más**. Bajo `models/` nunca se cose. El archivo existía, el path estaba bien, el modelo era válido, el test de "la textura existe" estaba **verde**. **Tercera vez en el proyecto que un archivo presente ≠ un archivo que el juego mire** (con `particle/`/`particles/` y `sound/`/`sounds/`).
  12. **Once tests fallaron al arreglar las poses, y los once fijaban los bugs.** Uno se llamaba *"un strike va negativo y un wind-up va positivo"* — **afirmaba que un wind-up levanta el brazo hacia atrás**, y estaba verde mientras el playtest reportaba exactamente eso. El techo de 60° además **prohibía la motion que vanilla ya hace** (su swing llega a 68.8), y por eso el golpe era una puñalada.
  - **La daga pegaba con el filo porque el pitch era +14**: sobre una hoja dibujada vertical, +14 es *todavía vertical*, así que la mano empujaba y llegaba el plano. Ahora **−72°** (el de la ropera más lo que una daga más corta se permite) lleva **la punta de arriba a adelante**, y 112° de recorrido total desde el cock.
  - **El barrido de la alabarda tenía todos los signos espejados** por una teoría —"que se lea como un arma golpeada en dos direcciones"— que el jugador no aprobó. Lejos: "hasta casi la off hand y regresar" ⇒ **0,98 de un item** de recorrido lateral.
- **Dos destructive y un `.gitignore` casi commiteado.** Ver la sección de Lecciones: es el costo exacto de no commitear.


- **El lunge de la ropera, cerrado. 503 tests.** Reconstruido entero sobre el riptide de vanilla, que son tres líneas, y la lección central es que **un dash en Minecraft no es una distancia: es un impulso y el rozamiento decide.** Toda la tabla de distancia (`FIRST_STEP`, `RETENTION`, `ticksFor`, `stepFor`, `steered`, `STEER_SHARE`) era aritmética resolviendo por un número que el motor no expone, y por eso el camino era inescapable: dos intentos de escalar el paso o el decaimiento, un techo de serie geométrica que hacía el netherite aritméticamente imposible, y un `* 0.5` que sobrevivió a lo que lo justificaba. **Cinco bugs, todos del mismo tipo — un número que parece controlar algo y no lo controla:**
  1. **`impulso / 0,6` con un rozamiento que no existe.** `Entity#move` multiplica por `getBlockSpeedFactor()` y el default es **1,0**. Cuatro rondas en el código.
  2. **El número "medido" con el harness encendido.** 2,12 en vez de 6,0. El playtest siguiente: **13 lunge, los 13 `fin=ilegal`**, porque el techo daba 7,1 y un lunge real lleva 6-7. Error de casi 3× en la dirección que hacía el lunge parecer corto.
  3. **El techo derivado de un par de constantes propias**, no del impulso: cuatro números describiendo dos curvas. Ahora un número solo, `estimatedDistance × 3.0`.
  4. **El corredor del golpe era más chico que el cono del swing** (2,0 contra 3,4) — un lunge que cuesta 8 swings pegaba menos que no hacer nada. Después se lo\notó al revés (alcance entero = 6,8) y tampoco: **la hoja apunta a donde apunta el lunge**, así que lo correcto es `alcance × 0.5`.
  5. **El orden de las líneas.** El chequeo de legalidad estaba **antes** del barrido, así que un lunge ilegal nunca llegaba al daño. Eso explicaba los tres síntomas reportados —"no hace daño", "falla la inmunidad", "sobre todo rápido"— como **una sola cosa**, con el build verde y 503 tests.
  - **El modelo de autoridad (2b):** el cliente se mueve y el servidor **mide después**. No se le pregunta si puede moverse sino si fue legal, y la medición no cuesta un packet porque el cliente ya manda su posición cada tick. Sin rollback, a costa de que un cliente que mienta se deslice gratis — que es preferible a un jugador invulnerable a voluntad.
  - **El cubo reemplaza al corredor** y cubre la misma forma con menos geometría que pueda salir mal. **La ventana se mide, no se cuenta** (termina cuando el cuerpo para, porque 20 ticks era el doble del movimiento), y la inmunidad dura **exactamente** lo que el lunge porque `clear()` saca el efecto.
  - **Balance:** lunge al 75% del daño base, techo 4,0 con paso 0,375, daño base 5,0, cono 3,5. El cruce queda a un enemigo de distancia: contra uno pierde contra un swing, contra dos gana.
  - Dos reglas de diseño murieron protegiendo cosas que ya no existían: "un tier mejor nunca es más rápido" y "un tier mejor lleva más lejos". `PER_TIER_IMPULSE = 0.02` — **el lunge es el único eje que el material no toca.**
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
- **DEUDA LEGAL de texturas: RESUELTA.** La viÃ±eta (que era *All Rights Reserved*, sin `LICENSE`) y las dos esferitas de aguante (que eran de otro mod, LGPL-2.1) las redibujÃ³ el autor. Verificado por hash contra los originales: **difieren los dos**. El mod es **GPL-3.0-only de punta a punta** y no queda arte de terceros en el repo.


e no hacía falta pensarlo.

### Lo que sigue

1. **Decidir cada especial** (las 5 preguntas de arriba). Es lo único del rework que es diseño puro.
2. **`REACH.put(NONE, 3.0F)` sigue mintiendo** — ya no se alcanza desde el click, pero un perfil sin
   combate no debería tener número de alcance. Queda pendiente de decidir.
3. El pico conserva sus filas de alcance/carga/knockback: ya no se leen desde el click, pero siguen
   ahí. **Los getters devuelven `null` si falta la fila** → borrarlas es un NPE esperando.
4. **Vanilla armas y herramientas fuera del alcance de `mods.toml`:** las 5 que vuelven a pelear son
   ítems de vanilla, no nuestros, así que `/cc weapons` no las lista.

---

## Abierta, con el log puesto

**El parry contra jugadores.** `"NINGÚN ARMA PARREA. Todas pasan totalmente. Solo parrea daños de
mobs, explosiones. Pero lo del jugador queda totalmente a segundo plano."` Playtest 2026-10-04.

Descartado **con motivo**: clase de entidad (`Player` **es** `LivingEntity`, el `instanceof` pasa);
source (las 5 rutas usan `playerAttack(player)` → `getEntity()` **es** el atacante); `invulnerableTime`
(el attachment `PARRY` **no** tiene `.sync()`, el estado vive sólo en el server); orden de handlers
(`GuardHandler` **no** escucha `LivingIncomingDamageEvent`); y "caso raro" (con "ningún arma contra
ningún jugador" es el **100%**).

**Y NO es bug, es diseño**: el puño no puede parrear — `SPECIAL.put(UNARMED, NONE)`. *"Ni con puño
parrea"* **no prueba nada**.

Sospechoso, marcado como hipótesis: `LivingEntity#hurt` **L1153** emite `onEntityIncomingDamage` (el
parry escucha ahí), **L1164** corre `onDamageBlock`/`isDamageSourceBlocked`, y
`isDamageSourceBlocked:1362` decide "¿viene DE FRENTE?" con `damageSource.getSourcePosition()`.

**El log**: `[parry] golpe=<msgId> atacante=<nombre> posicion=<Vec3> cantidad=<float>
bloqueando=<bool>`, sólo en el tick con ventana abierta. Recoger con compa, `/cc trace`, parreando a
un jugador. **Sin compa no se cierra, y no se debe escribir el fix a ciegas.**
---

## Ronda del tooltip, la durabilidad y las categorias (2026-10-07)

**Cerro con 841 tests y 1 rojo**, y el rojo es el guard de texturas: son los assets de cobre del
usuario (`copper_axe/boots/chestplate/helmet/leggings/pickaxe/shovel/sword`, `trial_hoe`,
`textures/entity/equipment/armor/copper*`) esperando el rework de oro/cobre. **No se commitean**: son
staging, y en un checkout limpio el guard pasa.

### Lo que entro

- **Tooltip reescrito**: categoria en azul (como vanilla), color por linea, durabilidad como
  contador `actual/max`, empuje base + cargado, numero de objetivos, y la mecanica de cada arma.
  Todo leido del codigo: `WeaponTables`, `Knockback`, `DamageResolver`, `MaceFall`, `ParryState`,
  `StreakState`, `ArcabuzState`, `ArcabuzStats`. Rebalancear = cambiar la tabla.
- **Durabilidad real**: `Item.postHurtEnemy` vive en el item y el flujo de ataque reemplazado nunca
  lo llama -> ninguna arma perdia un punto. 1 punto por golpe aterrizado.
- **Curva de durabilidad nueva**: oro 32 -> **190** (entre piedra y hierro), cobre 190 -> **320**
  (arriba del hierro). El resto en Mojang. Mixin `GoldDurabilityMixin` para el oro de vanilla.
- **Cobre**: dano 1,0 -> **0,5**, por debajo de piedra. Identidad: aguanta y no pega.
- **Maza**: base 10 -> **15**, techo 22 -> **50**, altura 8 -> **31** (derivada), velocidad -10%
  (0,54). El `SCALE` estaba soldado al techo y eso mantenia el tope en 8 bloques.
- **Coyote time**: `resetFallDistance()` en el golpe (lo de vanilla, perdido) + 8 ticks de ventana.
- **Maza en el aire**: `MaceAir.airborne` -- nadar, lava y elytra NO son aire.
- **Pala**: +30% de empuje (1,365), y ahora empuja mas que el arcabuz -- que es lo que
  `KnockbackProfile` decia desde siempre.
- **Categoria** (`WeaponCategory`): por capacidad, no por nombre. "Pesada" pregunta intervalo Y
  alcance, porque la pala de vanilla (20 ticks) es mas lenta que el martillo (19).

### Las tres trampas de esta ronda

1. **`javap` antes de un mixin, siempre.** Me fie de las fuentes decompiladas (`Item.getMaxDamage`)
   y el runtime tiene `ItemStack.getMaxDamage()`. Crash duro en el bootstrap.
2. **Nunca corri `runData`** despues de agregar claves de idioma -> el juego imprime la clave. Un
   `LanguageFilesTest` que lee el *provider* no ve el `.json` generado.
3. **Un tooltip que lee el componente miente sobre lo que el evento cambia.** El mazo decia 6 porque
   `MaceRebalance` mete su 14 por `ItemAttributeModifierEvent`, y el tooltip leia el componente.
   `forEachModifier` es la misma puerta que `getAttributeValue`.

### Los guards que mordieron solos

- `no number carries more than one decimal` cazo `"50.0/31.0"` en un argumento -> partido en dos, que
  ademas es lo correcto: un valor compuesto congela el orden, que es gramatica.
- `the shove is the hardest` cazo que la pala paso al arcabuz -> el test estaba mal, no el +30%.
- `TheCrosshairMeasuresTheSwingTest` (nuevo) lee los 4 sitios de dibujo: si uno vuelve a
  `displayedReach()`, muere.

### Pendiente

- **Rework de oro/cobre** (diseno cerrado, ver seccion propia): sin implementar.
- **Feedback por objetivo del arcabuz**, y el flash a 1,7 m.
- **Paso 11: supervivencia (`FoodData`)**.
---

## Ronda del cobre — el set que 1.21.1 no tiene (2026-10-07)

**854 tests verdes.** Cobre pasa de material del catalogo a **set completo**: 5 herramientas, 4
piezas de armadura, y sus dos mecanicas.

### Lo que entro

- **`core/gear/CopperGear`** (puro): los numeros y los 9 nombres. Los leen los registros y el test.
- **`ModTools`**: espada, pico, hacha, pala, azada. Clases vanilla (`SwordItem`...), asi que el
  perfil sale de `deriveProfile` — pero **hay que meterlas en los tags vanilla** (`#swords`,
  `#axes`...) o resuelven a `NONE` y no pelean. `CopperToolTier` es un enum aparte porque
  `CortesTier.getSpeed()` es 1.0 a proposito (las armas no minan).
- **`ModArmor`**: el `ArmorMaterial` (record, no interfaz) + 4 piezas. Proteccion **3/6/5/3 = 17**
  (arriba de hierro 15 y cadena 12), durabilidad **x8** (menos de la mitad de hierro 15). El trade.
- **Recetas + advancements**: 9 recetas shiftable + 9 advancements. `RecipeBookEntryTest` exige el
  advancement; mi suposicion de que `RecipeBookPopulation` alcanzaba estaba mal.
- **Efectos**: `ANTISEPTIC` (1 nivel por pieza, I-IV; **9% por nivel = 36%** con las cuatro -- era
  5% y el playtest lo llamo corto) y `PURIFICATION` (dano puro a no-muertos y netherite +
  -30% de dano del objetivo).

### La trampa de la ronda: la ruta de la textura de armadura

`ArmorMaterial.Layer` resuelve a **`textures/models/armor/copper_layer_1.png`** y `_layer_2.png`,
NO a `textures/entity/equipment/armor/` (esa es la ruta de 1.21.2+). **Quinta vez** que un archivo
existe donde el juego no mira. Las texturas del usuario estan en la carpeta nueva.

### La trampa del API: no se puede acortar un `MobEffectInstance`

`mapDuration` **no muta** (bytecode: termina en `ireturn`, sin `putfield`) y no hay setter. Y
`MobEffectEvent.Added` dispara **antes** de guardar. Por eso el antiseptico usa **`Applicable`**
(niega) + **`LivingEntity.forceAddEffect`** (mete la version corta sin recursion). Verificado en el
runtime. `MobType` **no existe** en 1.21.1 (es de otra version): no-muerto se pregunta con
`EntityTypeTags.UNDEAD`. Y `Holder.is(Holder)` si existe — mejor que comparar `value()`.

### Pendiente

- **Iconos de los efectos**: placeholder (no hay `textures/mob_effect/*.png`).
- ~~Mover las texturas de armadura~~ HECHO: `copper_layer_1.png` y `copper_layer_2.png` en
  `textures/models/armor/`.
- **Playtest**: la purificacion (**0,5 cada 10 ticks**, 100 ticks — decision del usuario) y el
  antiseptico.
---

## Ronda de cierre del cobre — tooltip de herramientas, autohit, sonidos (2026-10-07)

**857 tests verdes** (3 guards nuevos). Cierra lo que el playtest reporto.

### Lo que entro

- **Tooltip de herramientas**: `linesFor` gano un 12o arg `fights`. Con `!fights` imprime **solo
  categoria + durabilidad** y sale; el bloque de combate se saltea. `showsTooltip` =
  `fights || WeaponCategory.isTool(profile)`, y los atributos vanilla se ocultan **solo si
  fights** (el pico conserva sus renglones).
- **Autohit de daga/ropera**: `WeaponTables.firesOnPress(profile)` = `fights && !charge.canCharge()`.
  El press manda el paquete resolutivo y queda `claim = OURS`; el release cierra sin cobrar.
  **Bug extra**: `autoStrikeWhileHeld` reabre el swing del servidor antes de cada resolver -- sin
  eso, los repeats 2..N caian en `NO_SWING` y **solo un golpe aterrizaba**. Afectaba tambien a
  hoe/arcabuz. Y `ItemViewModel.tick` borra `pendingStrike` en el borde de press, que corre antes
  del tick: se difiere con `pressStrikePending`.
- **Azada**: `SPECIAL.put(HOE, SpecialType.MULTI_HIT)` (el especial **real** de la alabarda;
  `SWEEP` es un enum muerto del rework de la pala). Pitch del sonido de barrido mas bajo (0.7).
- **Sonidos**: maza + `block.heavy_core.place` (1.0 normal, 0.6 en el smash); miss de pala/azada =
  `sword_miss` (0.8 / 1.2); golpe de pala = `block.lantern.break` (0.8) + `entity.wind_charge.wind_burst`
  (0.6) si cargado.
- **Assets**: iconos `textures/mob_effect/antiseptic.png` y `purification.png` (copiados desde
  `textures/effects/`, original intacto). Particulas `maza_hit` y `shovel` = strips 32x160 nuevas
  filas en `SlashSprite`.

### Pendiente

- ~~`purified_effect.aseprite`~~ HECHO: exportada a `purified_effect.png` (16x64 = 4 frames de 16x16)
  y cableada como particula propia (`PURIFIED`, `SimpleParticleType`, `.mcmeta` frametime 2).
  Spawnea en `PurificationEffect.applyEffectTick` solo si `isPurifiable`. **OJO**: el `.mcmeta` usa
  el reloj del **atlas** (compartido) -- si en juego no anima por particula, partir el PNG en 4
  `purified_effect_0..3.png` y listarlos en el JSON.
- **Barrido de la azada** usa el sprite `barrido_alabarda` (no hay arte propio de azada).
- **Playtest**: quitar el gate `cocks` re-habilita la repeticion de daga/ropera que un playtest
  viejo habia apagado -- hay que verlo en juego.

---

## Playtest de cierre del cobre + auditoria de encantamientos (2026-10-07)

**Confirmado en cliente real, "todo perfecto":** autohit de daga/ropera (el press dispara y
repite), tooltip de herramientas (categoria + durabilidad, sin lineas de combate), sonidos
(maza, pala, azada, miss), particula de purificacion, barrido de la azada, y el special del
shotel sin lag. **857 tests.** Sigue **sin commitear** (nada desde `d5f96e0`).

### Auditoria: encantamientos vs. el flujo reemplazado -- ABIERTO

El flujo reemplazado (`CombatEngine`) **nunca llama a `Player#attack`**, asi que cuatro hooks
de encantamiento de vanilla **no corren**. Verificado leyendo las fuentes de `21.1.250`, no de
memoria:

| Hook perdido | Donde vive en vanilla | Efecto que se pierde |
|---|---|---|
| `getEnchantedDamage` -> `EnchantmentHelper.modifyDamage` (`ServerPlayer:2162`) | `Player:1229` | **Filo, Castigo, Perdicion de artropodos, Empalamiento** |
| **Densidad** -> `EnchantmentHelper.modifyFallBasedDamage` (`SMASH_DAMAGE_PER_FALLEN_BLOCK`) | `MaceItem` | **Densidad (maza)** -- **HECHA (2026-10-07)**: `MaceFall.densityBonus` = `1.0 * nivel * sqrt(caida)`, sin tope, leida en `CombatEngine` y sumada plana por `DamageResolver`. NO es `modifyDamage` |
| **Breach** -> `EnchantmentHelper.modifyArmorEffectiveness` (`ARMOR_EFFECTIVENESS`) | `MaceItem` | **Breach (maza)** -- **NO REQUIERE CODIGO**: su unico consumidor, `CombatRules.getDamageAfterAbsorb`, corre en el DEFENSOR dentro de `target.hurt` y lee el arma del atacante desde la fuente -> sobrevive al flujo reemplazado. NO es `modifyDamage` |
| `getKnockback` -> `EnchantmentHelper.modifyKnockback` | `Player:1292` | **Empuje (Knockback)** |
| `EnchantmentHelper.doPostAttackEffects` | `Player:1331` y `1380` | **Aspecto de fuego, Wind Burst** |
| `SWEEPING_DAMAGE_RATIO` | `Player:1313` | Filo arrasador (el barrido es nuestro, y solo la alabarda/azada lo tienen) |

**Intactos (no tocan el flujo del atacante):** Proteccion/Espinas/armaduras de defensor
(dentro de `LivingEntity#hurt`), Irrompible/Reparacion (`hurtAndBreak` ya re-agregado), Saqueo
(`getKiller` sale de la fuente `playerAttack`), y **todos los encantamientos a distancia**
(arco/ballesta: el flujo reemplazado es melee).

**El arreglo natural:** `CombatEngine` tendria que llamar los tres hooks que le faltan
(`modifyDamage`, `modifyKnockback`, `doPostAttackEffects`) contra `ServerLevel`. La pregunta de
diseno abierta es **si el dano de encantamiento escala con la carga** (vanilla lo escala por el
attack-strength) o se **suma plano** como el golpe de la maza.

## Decisiones nuevas (2026-10-07, post-playtest del cobre)

Cierran la ronda de encantamientos. Todo esto es diseno del usuario, anotado antes de codear.

- **Encantamientos perdidos por el flujo reemplazado** (ver auditoria arriba): al restaurarlos, el
  **dano de encantamiento se SUMA PLANO** -- como el golpe de la maza, no escala con la carga. Los
  tres hooks que faltan: `EnchantmentHelper.modifyDamage`, `modifyKnockback` y `doPostAttackEffects`.
- **Filo arrasador (Sweeping Edge)** -- rework a **carga de multitud**: `+dano por cada enemigo
  pegado al objetivo`, y sigue pegando a **UNO**. Deja de ser area porque la alabarda y la azada ya
  cubren el multiobjetivo.
- **Bloque de firestone** (`cortescombat:firestone_block`, el **primer bloque del mod**):
  - Luz **10** (mediana). Emite luz; **no** es fuente de fuego por si solo.
  - **Inestable**: el **fuego** o una **explosion** lo detonan -> explosion de potencia **8.0**
    (2x la TNT, que es 4.0), **con fuego** (`createFire = true`), que rompe bloques y quema a todos.
    **Cuidar la recursion**: el bloque detona, la explosion lo agarra a el, y no debe re-detonar.
  - **Agua -> obsidiana**: el agua que lo toca se solidifica en obsidiana. Base de granja de
    obsidiana.
  - Receta: **9 firestones -> 1 bloque** (reversible), con su advancement (`RecipeBookEntryTest`).
  - Pendiente de arte: **falta la textura**.

## Ronda de encantamientos -- hooks + Filo arrasador (2026-10-07)

**868 tests verdes** (10 guards nuevos). Restaura lo que el flujo reemplazado perdia.

### Lo que entro

- **Tres hooks restaurados en `CombatEngine`**: `modifyDamage` (Filo/Castigo/Perdicion/Empalamiento),
  `modifyKnockback` (Empuje) y `doPostAttackEffects` (Aspecto de fuego/Wind Burst). El dano de
  encantamiento **se suma plano** (`DamageResolver.resolve` gano un 7o arg `enchantBonus`), **por
  objetivo**, despues del multiplicador y del fall.
- **Empuje del encantamiento**: se suma a la componente **horizontal**, **fuera del piso de carga**
  (`knockbackFor`), escalado x0.5 (vanilla hace `f4 * 0.5`). El piso existe para matar el shove-spam
  del arma; un encantamiento pagado en el yunque no es un tap spameado. La rama del **pull del
  tridente queda intacta** -- un tiron hacia adentro y un empuje hacia afuera son instrucciones
  opuestas y gana el especial.
- **Filo arrasador reworkeado a carga de multitud**: `weapon/SweepingEdge.bonus(level, enemigos) =
  0.5 * level * enemigos`, `CROWD_RADIUS = 3.0`. **Sigue pegando a UNO** (no toca `maxTargets`).
- **`onPlayerAttackTarget` se dispara otra vez** -- es el `AttackEntityEvent` de compat que
  `Player:1223` emitia y habiamos perdido. Solo para el objetivo apuntado; si un mod lo cancela, el
  swing igual cuesta (el cooldown ya se pago antes de mirar el objetivo).

### Leccion: `Enchantments.java` manda, no la intuicion

**Densidad y Breach NO usan `modifyDamage`**: son `SMASH_DAMAGE_PER_FALLEN_BLOCK`
(`modifyFallBasedDamage`) y `ARMOR_EFFECTIVENESS` (`modifyArmorEffectiveness`). Mi tabla de auditoria
los tenia bajo `modifyDamage` y estaba mal; el subagente los cazo leyendo la fuente.

**Densidad: HECHA (2026-10-07).** No es lineal como vanilla (`0.5 * nivel * caida`): el diseño pide
**sin tope pero con raiz cuadrada** (`1.0 * nivel * sqrt(caida)`), porque la caida base SI tiene techo
y una recta encima lo volveria decorativo. `MaceFall.densityBonus` (puro, testeado), leida en
`CombatEngine` con `Enchantments.DENSITY` (patron de `sweepingEdgeLevel`) y sumada plana por
`DamageResolver` **al lado del fall** (no en `enchantBonus`, que se suma para todos los perfiles).
El nivel viaja como 8o arg `int densityLevel`, entre `fallDistance` y `enchantBonus`, para que la
maza y su caida se lean juntas en la firma.

**Breach sigue sin codigo y no lo necesita**: corre del lado del defensor dentro de `target.hurt`,
lee el arma del atacante desde la fuente, y las tres fuentes del mod (`playerAttack`,
`warhammerIgnoredArmour`, `axeCharged`) ponen al jugador como entidad directa. Ver la tabla arriba.

### Pendiente

- **Densidad**: HECHA (ronda 2026-10-07). **Breach**: sin codigo por diseño. Ver la tabla de auditoria.
- **Numeros a playtest**: `SweepingEdge.DAMAGE_PER_ENEMY_PER_LEVEL = 0.5`, `CROWD_RADIUS = 3.0`,
  `MaceFall.DENSITY_PER_LEVEL = 1.0` (la raiz), y el aporte de encantamiento plano por objetivo.
- **El empuje del encantamiento y el bonus por objetivo no son testeables en JVM pelada** (necesitan
  `ServerLevel`): verificados por lectura + compilacion, como la red.

## Ronda de Wind Burst + vapor (2026-10-07)

**878 tests verdes.** Cierra lo que reporto el playtest.

### Wind Burst y el dano de caida

- **El smash de vanilla** (`MaceItem.hurtEnemy:71-97`) hace tres cosas que el flujo reemplazado
  perdia: para la caida (`setDeltaMovement(Y, 0.01)` + `ClientboundSetEntityMotionPacket`), guarda
  `currentImpulseImpactPos` y enciende `setIgnoreFallDamageFromCurrentImpulse(true)`. Y
  `Player.causeFallDamage:1629` usa eso para **perdonar la caida por encima del punto del golpe**,
  **una sola vez** (`tryResetCurrentImpulseContext`).
- **Y Wind Burst la borra**: su explosion pasa por `ServerPlayer.onExplosionHit:1117`, que pisa el
  contexto y deja el flag en `entity.getType() == WIND_CHARGE` -- y el atacante es un jugador, asi
  que queda **false**. Por eso el lanzamiento del encantamiento dolia.
- **`attack/MaceSmash`** (nuevo) replica los estados y los **re-afirma despues** de
  `doPostAttackEffects`. Orden final en `anyLanded`: `notifyHit` -> `onLanding` -> `doPostAttackEffects`
  -> `afterEffects` -> `FallCoyote.landed`. **Antes de los efectos no puede ir**: el `0.01` pisaria
  el lanzamiento de Wind Burst.
- **No es inmunidad permanente** (la regla del javadoc de `FallCoyote`): solo con aterrizar el golpe,
  solo con caida (`> THRESHOLD`), y el impulso es de un solo uso. `MaceFall.smashes(fall, fallFlying)`
  es el gate puro, testeado.
- `smashLanding` se resuelve **una vez por swing**: la maza pega a hasta 3 cuerpos y el fall se
  resetea tras el primero, asi que preguntar por cuerpo daria "no smash" en el 2o/3o.

### Vapor al convertir agua en obsidiana

- Se **reusa el evento de vanilla**: `LevelEvent.LAVA_FIZZ` (1501), verificado en `LevelEvent` y en
  `LevelRenderer#levelEvent, case 1501` -> `SoundEvents.LAVA_EXTINGUISH` + 8 `LARGE_SMOKE` en
  `pos.y+1.2`. `ServerLevel.levelEvent` difunde `ClientboundLevelEventPacket` a 64 bloques.
- **Solo desde las dos conversiones reales** (`neighborChanged` de la celda cambiada y
  `hardenWaterAround`), no en cada `neighborChanged`. `FirestoneBlock.harden` es el unico sitio que
  escribe obsidiana y el unico que emite el fizz.

## Ronda del Reactor del Nether — FASE 1 (2026-10-07)

**894 tests verdes.** El primer `BlockEntity` del mod y el tercer mixin.

### Lo que entro

- **`block/ReactorCharge`** (puro): radio `3 x carga`, `MAX_NORMAL 30`, `MAX_OVERLOAD 40`,
  `HOUR_TICKS 72000`, `LIGHT_MAX 15`, `SIGNAL_STRENGTH 15`, `lightFor` lineal con tope,
  `isActive = powered && charged`.
- **`NetherReactorBlock` + `NetherReactorBlockEntity`**: 2 propiedades (`charged`, `active`),
  inventario de 2 slots (firestone limite 40, estrella limite 1), wrapper de automatizacion que
  frena en **30**, reloj de 1 h solo activo, sync al cliente, drop del contenido al romper.
- **Registro**: `ModBlockEntities` -> `cortescombat:nether_reactor`. Capability `ItemHandler.BLOCK`
  contra el TIPO en `RegisterCapabilitiesEvent`.
- **Datos**: tag `#cortescombat:reactor_powerable` (73 bloques), receta `NPN/COC/NBN` + advancement,
  loot, blockstate multipart, 4 modelos de bloque + item, lang ES/EN. `dead_star` registrado s/ uso.
- **`ModBlocks.REACTOR_SOUND`**: `SoundType(1.0F, 0.5F, NETHERITE_*)` -> colocar/romper/pisar/golpear/
  caer con el sonido del netherite a medio pitch. **Un `SoundType` lleva su propio `pitch` y vanilla
  lo multiplica por 0.8 al colocar (`BlockItem.place:88-95`) y al romper (`LevelRenderer:3151`)** ->
  cero eventos custom. Los 6 `.ogg` del reactor ya estan en `sounds.json` + `ReactorSounds`.

### El mixin — la trampa de la ronda

`hasNeighborSignal` y `getBestNeighborSignal` son **`default` de la interfaz `SignalGetter`** (y
`hasNeighborSignal` NO llama al otro: duplica el loop). **`@Mixin` de una clase a una interfaz NO
funciona**: `InvalidMixinException: @Mixin target type mismatch: ... is an interface`. El mixin es
una **interface** con handlers `private`:

```
@Mixin(SignalGetter.class) public interface SignalGetterMixin
  @Inject(method="hasNeighborSignal",     at=HEAD, cancellable) -> true si cubierto
  @Inject(method="getBestNeighborSignal", at=HEAD, cancellable) -> 15 si cubierto
```

En `"mixins"` (comun). Aplica: `runServer` llega a `Done (1.445s)!` s/ `InjectionError`.
Guard `ReactorWiringTest` afirma que es `public interface`.

### El escaneo, optimizado

**No se escanea el volumen.** El hook de consulta devuelve 15 a quien pregunta dentro de un reactor
activo (O(1), fast-path estatico s/ reactores). El **`ReactorScan`** hace re-evaluar a los
componentes al cambiar de estado (activar/desactivar/radio), **presupuestado** (40.000 pos/tick +
256 `neighborChanged`/tick), por secciones, saltando **chunk no cargado** y **seccion vacia**.
Filtro: `state.is(ReactorTags.POWERABLE)`.
**La llamada correcta es `level.neighborChanged(pos, reactor, center)`** — `updateNeighborsAt`
notifica a los vecinos *de* la posicion, no a la posicion.

### Otras verificaciones

- **`emissiveRendering(StatePredicate)`** existe en NeoForge 1.21.1 y es **por block-state, no por
  modelo**: mientras `active` el bloque rinde full-bright y el overlay suma el glow.
- `getLightEmission(state, level, pos)` esta en **`IBlockExtension`** (no `BlockBehaviour`) y hace
  falta **`hasDynamicLightEmission`**.
- `ItemStackHandler.insertItem` **SI** llama `isItemValid` (la premisa "el hopper no lo consulta" era
  falsa). El cap de 30 va en `insertItem` igual, porque necesita el conteo actual.
- `net/` en la raiz era basura de un `jar xf` viejo (3 `.java` de vanilla) -> **borrado**.

## Ronda del Reactor del Nether -- FASE 2 (2026-10-07)

**907 tests verdes** (+13). La bomba y el espectaculo. Sin commitear.

### Lo que entro

- **`block/ReactorOverload`** (puro): el fusible y todos los numeros. `FUSE_TICKS 300` (15 s = la
  duracion de los dos `reactor_overheat_*`; el sonido ES el timer), `WARNING_CHARGE 31`,
  `DETONATION_CHARGE 40`, `STAR_RADIUS 100`, `STAR_POWER 120` (30xTNT, **no se usa como radio**),
  `STAR_DESTRUCTION_RADIUS 48`, `BLAST_POSITIONS_PER_TICK 24000`, `BLAST_REMOVALS_PER_TICK 6000`,
  `QUAKE_RADIUS 64`, `QUAKE_MAX_DEGREES 1.2`, y `sphereVolume`/`ticksToClear`/`rayMarchSteps`/
  `shakeDegrees`/`energyRadius` puras.
- **FIRESTONE**: 31-39 = aviso (particulas, sin cuenta). 40 ⊕ activo -> fusible + `overheat_firestone`
  una vez. La palanca apaga -> se **borra** el fusible (reactivar reinicia, no reanuda). Detona a
  `FirestoneBlock.BLAST_POWER` (8.0) con fuego, **removiendo el bloque primero**.
- **NETHER STAR**: se mete a mano y arranca en el mismo click; **ignora la palanca**, solo el agua la
  corta (cubo -> `DEAD_STAR` + cubo vacio; y agua que llega sola por `neighborChanged`). Radio de
  energia 100. Al final `nuclear_explosion` a volumen 16 (por mano, no por `explode`: el packet de
  sonido de vanilla topa en 64 bloques), flash, dano/kb/fuego y crater escalonado.
- **`block/ReactorBlast`** (`ServerTickEvent.Post`): el crater es una **esfera explicita** de radio 48
  por cursor de chunk/seccion (salta chunk no cargado y seccion vacia), con dos presupuestos/tick
  compartidos (24000 pos / 6000 remov). Sin drops, sin `explode`, sin encadenar.
- **`client/ReactorShake`**: **`ViewportEvent.ComputeCameraAngles`** (existe, game bus) -- evento, no
  mixin. El BE llena un `Map<BlockPos,dim>` desde `loadAdditional`/`onLoad` (el overload viaja en el
  update tag) y lo borra en `setRemoved`. Config `ClientConfig.REACTOR_SHAKE` (mareo).
- **`/cc reactor`**: `charge <0..40>`, `active <bool>`, `clock <ticks>`, `overload
  firestone|star|none`, `status`; sobre el reactor que miras (`pick(20)`).
- **Sync**: `saveAdditional`/`loadAdditional` guardan `overload`+`fuse`.

### La explosion x30: se midio, y se partio

`Explosion.explode` lanza `16^3-14^3 = 1352` rayos y cada uno camina 0.3 bloques con el presupuesto
bajando 0.225/paso en aire. A potencia 120: `1352 * 120/0.225 ≈ 721.000` pasos, cada uno un
`getBlockState` **y** un `getFluidState`, y cada uno puede sumar una posicion al `toBlow` -> hasta
~721k posiciones en un `ClientboundExplodePacket`. **La cifra real es ~0,72M, no "millones"**: la nota
sobreestimaba, pero cientos de ms en un tick + un packet de ~11 MB igual matan al watchdog. El diseno
pedia "x30 TNT" = potencia 120 -> se conserva como `STAR_POWER` documentado, **no** como radio.
Solucion: esfera de radio 48 (~463k bloques) a 6000/tick -> **78 ticks (~4 s)**. Se salta chunk no
cargado (no genera terreno p/ destruirlo) y bloque con `defaultDestroyTime() < 0`. Diferencia con la
explosion literal: forma material-independiente, sin drops, sin encadenar firestone.
`thePointBlastIsTooBigToRun` afirma el numero medido; volver a `explode` o cambiar la constante mata.

### Trampas de esta ronda

1. **La referencia a una clase cliente tiene que estar en un metodo que el server NUNCA llame.**
   `refreshClientRegistry` corre en el server y retorna temprano; `publishShake` (que nombra
   `ReactorShake` -> `Minecraft`) solo lo invoca el cliente -> nunca se verifica en el server. Metida
   directa en `refreshClientRegistry`: `NoClassDefFoundError` al arrancar.
2. **`Entity.setSecondsOnFire` no existe en 1.21.1** -> `igniteForSeconds(float)`.
3. **El gametest server no sirve p/ verificar arranque aca**: `No test functions were given!` tumba
   `GameTestServer.create` **antes** del mundo. Verificado con `runServer` -> `Done (1.556s)!`.

### Pendiente (fase 2)

- **Playtest**: radio 48 (¿mas grande?), dano/kb/fuego del nucleo, volumen de la explosion, el shake
  (`QUAKE_MAX_DEGREES`) y la duracion de la onda (6000/tick).
- **Fuego del crater**: la esfera no replica `createFire`; solo las entidades se prenden.
- `forceOverload(FIRESTONE)` usa `debugActive` (no se guarda): solo debug.

## Ronda de la pala, la azada y el icono del arcabuz (2026-10-07)

**913 tests verdes** (+19). Sin abrir el cliente: pedido explicito del usuario. Sin commitear.

### Lo que entro

- **Pala**: perfil de viewmodel. Es el UNICO golpe de la tabla que termina subiendo. Carga 0.4/33
  (ya estaba en `WeaponTables`); el cabezal baja y el mango vuelve mientras carga (`WINDUP` +
  temblor EXPO), y el golpe es un **scoop**: adelante (`z` -0.60) y ARRIBA (`y` +0.28, pitch +46).
  Va por `STRIKE`, no por `CHOP`: un lanzamiento sube, una caida baja. La direccion no es gusto:
  coincide con su empuje horizontal (1.365, el mayor del set despues del arcabuz).
- **Azada**: dos movimientos, porque es la unica arma que pega en area con el click izquierdo Y con
  el derecho. El izquierdo es un **tajo que cruza**: `CHOP` con 112 de yaw contra los 62 de la. El criterio es que un test que afirma la convencion equivocada es peor que no tener test.

## Ronda de los tags propios (2026-10-09)

**930 tests verdes** (+17). Sin abrir el cliente.

### Lo que entro

- **`cortescombat:profile/<id>`**, un tag por molde, catorce archivos en
  `data/cortescombat/tags/item/profile/`. Los escribe `CortesItemTagProvider.profileTags()`.
- **Orden de lectura nuevo** en `WeaponStats.deriveProfile`: nuestros tags PRIMERO, los de vanilla
  como fallback, `NONE` al final. Los de vanilla no se tocaron.
- **`WeaponProfile.tagPath()`**, la mitad pura; `WeaponProfileTags`, la mitad que necesita el juego.
  Mismo patron que `WeaponMaterial.repairTag()` / `CortesTier.repairTag()`.
- **`WeaponProfileTagsTest`**, 5 tests: el path, el walk del enum, el archivo en disco, el orden del
  resolver, y que el generador camine el enum.

### Por que existe

La clasificacion corria entera por tags de vanilla. `CortesItemTagProvider` escribia nuestras cinco
herramientas de cobre **dentro** de `#minecraft:swords/axes/pickaxes/shovels/hoes`, y su javadoc lo
decia: sin esas cinco lineas las herramientas de cobre no pelean. O sea que cinco items nuestros
entraban a nuestro propio sistema de combate por una categoria que no es nuestra.

**Los tags mergean aditivamente, que es lo que los hace funcionar -- y el mismo mecanismo deja que
un datapack declare uno con `"replace": true`.** Ahi se caen todos los miembros, los nuestros
incluidos, y la espada de cobre deja de pelear. Sin error y sin aviso: un tag que no matchea nada no
es un error, es un silencio, y esta clase de fallo ya se pago cuatro veces en el proyecto.

**Lo que NO cambio, y es la parte que hay que leer dos veces:** los tags de vanilla siguen ahi,
debajo de los nuestros. Sacarlos mandaria cada arma de cada mod que no conocemos de vuelta a vanilla
sin decir nada, y "no lo reconocemos" es una respuesta peor que "supusimos espada".

### Los sabotajes, verificados uno por uno

Que un guard muerde es parte de escribirlo, no una tarea posterior:

| sabotaje | muere |
|---|---|
| invertir el orden de los dos bucles | `oursIsAskedFirst` (L150) |
| borrar `profile/ropera.json` | `everyProfileHasAFile` |
| `tag(...)` fuera del loop del enum | `theGeneratorWalksTheEnum` |
| renombrar el prefijo `profile/` | `thePathIsThePublicName` |
| un path duplicado | `everyProfileHasAPath` |

### Lo que quedo en el camino, a proposito

- **Los cuatro tags vacios** (`none`, `unarmed`, `mace`, `trident`) son la especificacion, no un
  descuido: es el camino donde se escribe despues. Es lo INVERTIDO del bug de
  `stone_tool_materials` en el mismo archivo, donde llamar `tag(...)` para un tag que nada llenaba
  era el error.
- **Las 30 armas del catalogo van en el archivo igual** aunque llegan por componente. El archivo es
  la lista canonica de "cuales de los nuestros usan cual molde", y es la red debajo del dia que una
  via de registro se olvide el `.component(WEAPON_PROFILE, ...)`.
- **El tag lleva el molde, nunca los numeros.** `profile/halberd` contesta "tratalo como alabarda";
  el alcance, la carga y el empuje siguen saliendo de las cuatro tablas.
- **El `weapon_overrides.json` (paso 12) sigue sin existir**, y el "tags genericos para que el
  usuario meta su arma" quedo fuera por decision del usuario: tiene una idea mejor para eso. Esta
  ronda es mantenibilidad del mod y nada mas.

### La tanda de los encantamientos unicos — tres bugs de los tests, y una regla que crecio

**20 encantamientos en cuatro tandas** (los 6 base mas Punta Fina, Filo Astillado, Apunalar,
Sanguijuela, Aventurero, Culata, Estocada, Segundo Aliento, Guardia Firme, Cicatrizador,
Destripamiento, Quebranto, Desvio Vampirico, Mecha Corta y Barredor). Lo que costo cada uno:

- **`antiHealingReduction` sin clampear**: con un amplifier alto la reduccion **pasaba de 1** y
  `1 - reduccion` daba **negativo** -- o sea, curarse hacia **dano**. El test lo cazo.
- **`DRAIN_PER_LEVEL` en positivo**: el robo **sumaba** vida maxima en vez de restarla. El techo del
  enemigo subia y el robo era un regalo para los dos lados. **La cuenta se ve bien leida y al reves**,
  que es la peor forma de fallar.
- **`GuardFree.READY` en cero**: con cero, "listo" era `now - 0 >= 60` y el primer bloqueo gratis de
  una partida no llegaba hasta el tick sesenta del mundo. Ahora es `-INTERVAL_TICKS` y el test lo
  afirma.

**Y la regla de los tests crecio un grado, que es lo que hay que llevarse de esta ronda:**

- *"Nada en `src/test` puede nombrar un tipo de Minecraft"* **es mas larga de lo que parece: la clase
  que el test CARGA tampoco.** `MaceAir` llamaba a `CombatEnchantments.level(...)`, cuya firma declara
  un `HolderLookup.Provider`, y **los tres tests de `MaceAir` murieron con `NoClassDefFoundError`
  antes de la primera asercion** -- sin que el archivo de test nombrara nada. La regla real es "la
  clase que un test carga no puede **nombrar** un tipo de Minecraft, ni en su firma ni en un metodo al
  que llame".
- **Y el sabotaje puede mentir.** Un `Replace` multi-linea de PowerShell se comio una llamada y dejo
  `MaceAir` **sin compilar**; `gradlew test` fallo en `compileTestJava` y **los XML de resultados
  quedaron los de la corrida anterior**, asi que dos sabotajes reportaron "murio 1 test" cuando en
  realidad **no habian corrido**. La verificacion ahora mira **el exit del build** ademas de los
  muertos. Es la misma leccion de siempre con otra cara: *la salida de los comandos es informacion*.

### La tanda de los "del cliente" — la azada, Mecha Corta y Barredor

- **La animacion de la azada iba al reves** (hacia la derecha). El yaw del tajo paso de 112 a **-112**,
  y **el roll se quedo en negativo** -- que es lo que el test cazo cuando yo lo inverti tambien: el
  roll es lo que hace que la hoja se de vuelta como una guadana en vez de barrer recto. **El test
  estaba bien escrito**: afirma la **magnitud** del yaw (`Math.abs`) y el **signo** del roll por
  separado, asi que invertir la direccion no lo rompio y girar el roll si.
- **Mecha Corta no necesitaba arte**, y el documento decia que si. Los cinco frames del arcabuz son
  **archivos separados** y el que se dibuja lo elige un **predicado**; no hay animacion de atlas que
  re-temporizar. **Lo que si habia era un numero escondido**: `progress()` dividia por la constante
  `ArcabuzState.FUSE_TICKS`, asi que con una mecha de diez ticks el progreso llegaba a 0,5 cuando la
  mecha se apagaba. El total ahora viaja y `light()` lo toma por argumento.
- **Barredor y el veto C4.** El documento tiene un veto escrito para el alcance extendido: *"obliga al
  HUD del cono a releer el nivel; si se olvida, el indicador miente"*. Barredor extiende el cono del
  **barrido** de la alabarda, asi que el guard no pregunta si el encantamiento existe: pregunta por
  **el servidor y los cuatro sitios de display** -- el crosshair, los corchetes, `/cc cone` y
  `/cc hud`. El que se olvide es el que miente.
- **"+1/+2 objetivos" se cayo al escribir el codigo.** El barrido **no tiene tope de objetivos**
  (`targetsInCone` devuelve todos), asi que no hay un numero del que sumar. Lo que hace el
  encantamiento es agrandar el area, y **un area mas grande alcanza mas objetivos por si sola** -- el
  efecto que el jugador ve es el que el documento prometia, por otro mecanismo.

### La tanda de los baratos de la ronda 5 — Astilladora, Mella, Segunda Pasada, Santa Muerte

**El inventario real era 17 y no 11**, que es lo primero que hay que anotar: el documento contaba los
que faltaban de memoria. Los cuatro de esta ronda son los que ya tenian su hook escrito.

- **Astilladora** obligo a que `GuardState.block` tome el **coste** por argumento, y no es un capricho
  de estilo: gastar dos de a uno **bajaria la guardia en el primero** y el segundo punto caeria sobre
  una guardia que ya esta abajo. "Este golpe cuesta dos" es la barra, no la postura. El test lo afirma
  con una barra de tres, que es donde los dos caminos se separan.
- **Mella** es **el unico encantamiento del proyecto que no toca el dano**: toca el desgaste, que es un
  recurso del rival en una escala de minutos. Por eso los numeros son grandes (hasta x6).
- **Segunda Pasada** obligo a que `CooldownState` tenga un `refund`, y el borde es lo unico que importa:
  **una devolucion mayor que lo que queda tiene que caer en cero** y no en un contador negativo -- un
  cooldown negativo no es "muy listo", es un estado donde `isReady` contesta si por una razon que nadie
  escribio. Y la condicion del encantamiento es **"no mato a NADIE"**, que se pregunta por `isAlive`
  despues del `hurt`: `hurt` contesta "el dano paso", que es otra pregunta.
- **Santa Muerte** va en `LivingDeathEvent` por el mismo criterio que Aventurero -- el evento no es del
  golpe, y un mob puede morir de tres formas que no pasan por `CombatEngine`. El Wither va al **radio**
  y **el asesino queda afuera**: un encantamiento que castiga al que lo usa es el veto C2.

**Y la leccion de los sabotajes, otra vez con otra cara.** Dos de los siete **no midieron lo que
creia**: uno rompio la compilacion (`astilladoraCostX` no existe) y reporto "EXIT=1, 0 muertos", y el
otro no revirtio porque el `Replace` buscaba un texto que ya no estaba -- y dejo el archivo saboteado
para el resto de la corrida. **Un sabotaje que rompe la compilacion no es un sabotaje: es un error de
tipografia con el mismo color que un exito.** El script ahora imprime el exit y los muertos juntos, y
la diferencia entre "no compila" y "murio un test" se ve en la primera linea.

1044 tests verdes (eran 1035). 26 encantamientos implementados, 7 sabotajes verificados.

### La tanda de los "del empuje y la linea" — Inercia y Ensarte, y un canal muerto que se fue

- **Inercia va contra un javadoc escrito a proposito.** `Knockback.apply` dice, con esas palabras, que
  mandar un encantamiento al empuje vertical "smuggle the launch back in through the one number that is
  supposed to be a property of the weapon", y esa regla nacio de sacar el empuje vertical de vanilla.
  **La regla sigue siendo cierta y esto es la excepcion con nombre**: lo que estaba mal en vanilla no
  era el aire, era que viniera gratis y sin decirlo en cada golpe de cada arma. Aca lo pide un
  encantamiento que hay que conseguir, con un arma que tiene que construir una cadena primero, y la
  **horizontal queda en cero** -- no es un empuje mas fuerte, es el mismo apuntado a otro lado.
  Por eso es un metodo aparte (`Knockback.applyLift`) y no un parametro: un parametro habria dejado la
  puerta abierta para cualquiera.
- **Y el techo subio a 2,5/0,70 por dos encantamientos y no por un arma.** Con el techo viejo (1,6/0,45)
  Inercia y Paliza habrian quedado clampeadas al mismo numero que la pala alcanza sin encantamiento,
  que es la forma exacta en que un encantamiento deja de existir sin que nada falle. `KnockbackCeilingTest`
  afirma las dos mitades: el techo nuevo **y** que ninguna arma del catalogo lo toca de fabrica.
- **Ensarte necesito un tag de clase nuevo** (`enchantable/reach`), porque es el primer encantamiento
  que llega a **dos items sin un perfil que los una** -- la alabarda tiene `profile/halberd` y el
  tridente `profile/trident`, que esta vacio a proposito. Y es la razon por la que `supported_items` no
  puede llevar una lista de tags.

**Corte Gemelo vivio una hora y se fue, y es la parte que hay que anotar.** Las reglas estaban escritas
(el intervalo del L1, la fraccion del corte), el archivo de datos tambien, y el encantamiento se podia
poner en el sable. Lo que no existia era **el cableado**: nadie leia el intervalo, asi que el L1 y el L2
pegaban lo mismo. **Un encantamiento con reglas, archivo, idioma y tag que no hace nada es la peor clase
de canal muerto, porque el build esta verde** y el unico sintoma es que un jugador pague un nivel que no
recibe. Se retira entero -- no se deja a medias -- y vuelve con su attachment.

**Y el guard de wiring volvio a fallar por la razon de siempre, en la primera pasada de sabotajes.** El
de Ensarte afirmaba `ensarteShare(`, y el sabotaje que envolvia la llamada en un `if (false)` lo dejo
**verde**: la regla existia, se llamaba desde ningun lado y el guard no lo veia. La correccion es que la
asercion pida **la llamada al ras** (`"\n        ensarte(player, target, stats, result);"`), que es la
unica forma de distinguir "esta escrita" de "se ejecuta". Es la tercera vez en el proyecto que un guard
afirma la existencia de una funcion en vez de su uso.

1049 tests verdes. 28 encantamientos implementados. `corte_gemelo` con 0 hits en `src/`.

### Adrenalina reemplaza a Filo Fugaz, y los guards de wiring vuelven a fallar

**Filo Fugaz hacía otra cosa y el nombre del id mentía.** Se llamaba `fleeting_edge`, daba Velocidad al
**conectar**, y el documento lo había rediseñado hacía rondas como **Adrenalina**: una reducción del daño
que **entra**. Las dos son "la daga es rápida", y no son lo mismo -- una llega después del golpe y la
otra **antes**, que es la diferencia entre ganar el intercambio y sobrevivirlo. Se renombró el id (un id
que dice `fleeting_edge` sobre un efecto que reduce daño es la misma clase de mentira que `lastRadius`
sobre un campo que sube), se borró el método viejo y el efecto se mudó a `LivingIncomingDamageEvent`.

- **La reducción va ANTES de que el daño se aplique**, y eso no es un detalle de implementación: una
  curación posterior llega tarde. `event.setAmount(amount - saved)` corre en el único lugar donde el
  golpe que te iba a matar no te mata.
- **El L2 no sube el número: parte el cooldown al medio** (30 s → 15 s). En una pelea larga la frecuencia
  vale más que los diez puntos de reducción que también agrega.
- **La Velocidad dura 5 s y NO el cooldown**, y el test lo afirma: si durara lo mismo el jugador correría
  todo el tiempo, y eso es el veto C5 -- la velocidad de movimiento como estado y no como decisión.
- **El daño ahorrado se cobra en un `hurt` aparte y no sumado al golpe.** El `AttackResult` es un record
  y ya salió del `DamageResolver`: sumarle un número después sería una segunda aritmética del mismo
  golpe, y la segunda copia es la que se desincroniza.

**Y los guards de wiring fallaron por cuarta vez, con la misma forma exacta.** Los dos que escribí
-- `event.setAmount(` y `cashAdrenalina(` -- quedaron **verdes** con la llamada envuelta en un
`if (false)`, porque el string sigue estando. La corrección es pedir **la llamada al ras**, con su
indentación: es lo único que distingue "está escrita" de "se ejecuta". Cuatro casos ya (`perTargetBonus(`,
`ensarteShare(`, `event.setAmount(`, `cashAdrenalina(`), y la regla quedó escrita en `AGENTS.md` con la
forma barata de no repetirla: **copiar la línea del código en vez de escribir el nombre del método**.

1051 tests verdes. 28 encantamientos implementados. `fleeting_edge` con 0 hits en `src/main`.

### La ultima ronda: los seis de estado y evento — y lo que quedo afuera

**Paliza, Sentencia, Trabazon, Gripa, Corte Gemelo (que volvio) y Capacitor.** 34 implementados.

- **Paliza obligo a que el techo de knockback subiera de verdad.** Los 2,4 y 0,6 del diseño no entraban
  en 1,6/0,45, y con el techo viejo el encantamiento habria quedado **clampeado al mismo numero que la
  pala alcanza sin encantamiento** -- la forma exacta en que un encantamiento deja de existir sin que
  nada falle. `KnockbackCeilingTest` afirma las dos mitades: el techo nuevo **y** que ninguna arma del
  catalogo lo toca de fabrica.
- **Sentencia y Santa Muerte comparten una regla que no se puede aflojar**: el radio **no cobra a quien
  lo genera**. El reventon ocurre en un cuerpo a dos bloques del jugador, asi que cobrarle tambien
  seria el veto C2 con otro nombre. El guard afirma `other == player || other == body`.
- **Trabazon no puede cancelar un salto, y eso es un hecho del motor.** `LivingJumpEvent` **no es
  cancelable**: el impulso ya esta puesto cuando el evento corre. Lo que se hace es **deshacerlo** --
  bajar el eje Y a cero -- y esa es la diferencia entre un salto que no sale y uno que sale y se cae.
- **Gripa tuvo un problema que el diseño no preveia**: el documento pide 20 ticks por tick de daño en
  el L1 y 15 en el L2, y **el nivel del encantamiento no viaja en el efecto** -- lo que viaja es el
  `amplifier`, que son los stacks. Se ató el intervalo al unico numero que si esta (un moco que llego al
  tope pega mas seguido) y quedo anotado en la clase.
- **Corte Gemelo volvio con lo que le faltaba**: el reloj. Sin el, el L1 y el L2 habrian sido el mismo
  encantamiento con dos precios.
- **Capacitor se colgo de algo que ya existia**: `ParrySource.cameThroughTheAir`, la rama que la ronda
  del parry dejo formalizada. El daño es una **suma** y no un reemplazo -- el parry ya devolvia el
  proyectil antes del encantamiento.

**Y quedo afuera, a proposito y anotado:** Retirada (invierte el lunge agachado, y eso toca el sistema
de autoridad del movimiento, que es lo mas delicado del proyecto) y los tres que necesitan una entidad
propia y un camino de municion: Pala Antimaterial, Rey de las Moscas y Mungus. **Sus numeros estan
escritos** en `CombatEnchantRules` y **no estan en `IDS`**, porque `IDS` es lo que hace que un test pida
el archivo, el tag, el idioma y la forma del campo -- un encantamiento declarado que no se puede
conseguir es un canal muerto con el build verde.

**Y dos cosas mas que costaron una vuelta cada una:**

- **`MaceFallWiringTest` es fragil por diseño y me rompio el build.** Prohibe un `0.0F` al ras en
  `CombatEngine` porque es como se llama por accidente al overload del resolver que no cobra la caida --
  y mi Paliza terminaba en `0.0F);`. La correccion fue una **constante con nombre**
  (`PALIZA_LIFT_SHARE`), que ademas dice por que el cero esta ahi.
- **Un `Replace` multi-linea de PowerShell volvio a no revertir un sabotaje**, y el `ParryHandler` quedo
  saboteado para el `FINAL`. Es la tercera vez en la sesion: la leccion ya esta escrita en `AGENTS.md` y
  la forma barata de no repetirla es **una linea por `Replace`, o la herramienta de edicion**.

1053 tests verdes. 34 encantamientos implementados, 6 sabotajes verificados en esta ronda.

### Retirada - el primero de los cuatro caros, y el diseno le tenia que corregir una palabra

**La tabla decia "da un ataque EXTRA al frente", y eso no es lo que se implemento, porque no es
cierto.** Antes de escribir una linea hay que leer `LungeDamage.cube`: el cubo que barre la marcha
atras esta **centrado en el jugador y mide medio alcance en TODAS las direcciones**. No tiene
nocio de hacia donde mira nadie. O sea que **una marcha atras ya barria lo que quedaba adelante**,
que es exactamente donde el jugador acababa de golpear.

Un golpe encima habria cobrado dos veces al mismo cuerpo en la misma pulsacion.

**Lo que hace es adelantar el cobro, no agregar uno.** El golpe del frente va al conjunto
`Lunge.prehit`, que es el mismo que el barrido ya no vuelve a tocar, y por eso su fraccion es 1,0 y
no una parte -- no se esta sumando nada, se esta moviendo el pago de "cuando cubris el suelo" a
"cuando te vas". Lo que gana no es dano, es **garantia**: el golpe se cobra aunque la retirada la
corte una pared, que es exactamente lo que el lunge normal no puede prometer, porque su golpe
depende de que el cuerpo llegue. Y el precio es simetrico en las dos direcciones -- dos bloques que
no gastas en acercarte son dos bloques que no te acercan.

**Y el impulso es UN SIGNO.** El lunge es direccion-agnostico de punta a punta: `travelled` mide
desplazamiento, `ceilingFor` es una distancia. Asi que invertirlo fue una linea en `LungeMotion`,
sin una segunda tabla de numeros y sin un segundo camino de legalidad. Que no haya mas que eso es
el diseño, no una economia.

**La trampa que tiene, y esta testeada:** `Lunge.prehit` limpia su conjunto **mientras el lunge no
este activo** (`wasHit` pregunta `Lunge.active`). El golpe del frente tiene que venir **DESPUES** de
`Lunge.begin`; registrado antes se borraria en el primer tick **sin error y sin golpe**. El guard
busca las dos llamadas por su texto -- y no por su indentacion, porque un sabotaje que mueve la
linea la mueve de columna tambien, y un guard que depende del espaciado muerde por la razon
equivocada.

**Y una constante muerta que el build no acusaba.** `RETIRADA_FORWARD_HIT_SHARE` estaba declarada y
**no se aplicaba**: el dano salia de `LungeDamage.damageFor` sin multiplicar. Un 1,0 que no se
aplica es una constante que nadie lee -- el dia que el dueño lo baje en el playtest no habria donde
tocarlo, y lo que el jugador ve seria distinto de lo que el archivo dice. Se conecto y el guard
afirma que **multiplica**, no que existe. Es el mismo shape que un canal con su consumidor muerto.

**Siete sabotajes, y el primero no es un test.** Los cinco de los guards mueren por asercion; el
septimo (quitar la bandera de inversion del call site del cliente) **no compila**, porque el metodo
viejo de dos argumentos ya no existe -- el sistema de tipos es guard mas fuerte que cualquier test,
y anotarlo evita que alguien "arregle" un test que no puede fallar.

Y dos accidentes de Herramienta en la misma ronda, ambos del mismo tipo: **un `Replace` multi-linea
se volvio a comer un revert**, dejando un `;` suelto que no compilo para el FINAL; y **un sabotaje
que no compilo no es sabotaje** -- el primero de la lista reporto `EXIT=1 muertos=0`, que es la
firma exacta de "rompi el codigo en vez de probar el guard".

1054 tests verdes. 35 encantamientos implementados. Faltan tres: Pala Antimaterial, Rey de las
Moscas y Mungus -- los tres con su tabla de numeros escrita y **fuera de `IDS`** hasta que tengan
entidad propia.

### La sangre pesa y las moscas revolotean - y un guard me agarro inventandome una constante del motor

Dos particulas nuevas con su propia fisica, y la mitad del trabajo no estuvo en escribirlas sino en
averiguar que ya estaba escrito en el motor.

**La sangre no tiene codigo que la haga pesada: tiene `gravity` en tres y el motor hace el resto.**
Leido en las fuentes de `Particle#tick`: `yd -= 0.04 * gravity` por tick, y despues `move()`. Con
`hasPhysics` -- que es `true` por defecto y que casi todo el mundo apaga -- `move()` pasa por
`Entity.collideBoundingBox`. Y ahi esta el regalo: `Particle` tiene un campo `stoppedByCollision`
que se pone en el momento exacto en que la gota queria bajar y no pudo, y **a partir de ahi `move()`
no hace nada mas nunca**. O sea que la gota cae rapido, toca el suelo y **se queda clavada donde
cayo**, que es una salpicadura, sin una sola linea de la nuestra. Todo el "peso" son dos numeros y
no apagar algo.

**Y las moscas no flotan: se tiran.** Movimiento aleatorio por tick es estatica -- el ojo ve un
parpadeo, no un insecto. Una mosca elige una direccion, se tira, aguanta tres ticks y cambia de
idea, y eso tiene dos tiempos separados: el **dardo** y el **STEERING** (0,65) que la acelera hacia
el rumbo nuevo en vez de teletransportarla. Sin ese acercamiento el cambio es un salto y se lee
como lag; con movimiento por tick sin dardo es ruido. Y `gravity` en cero a proposito, porque una
mosca con gravedad apila la nube en el piso en dos segundos y deja de ser una nube.

**El hallazgo que hay que anotar: `sendParticles` no puede elegir la direccion.** Verificado en
`ClientPacketListener#handleParticleEvent`: con conteo mayor que cero el cliente calcula la velocidad
como `gaussian() * maxSpeed` **por cada eje**, con el mismo sigma en los tres. Solo existe la nube
**isotropica** -- no hay forma de escribir un chorro hacia atras. Para la sangre no hizo falta (un
salpicon lo es, y despues la gravedad pone la direccion), pero es el numero que hay que recordar
antes de querer un chorro dirigido en el futuro.

### Y una constante que me invente, y el guard la agarro

La primera version de `SangreParticle` tenia un javadoc entero -- y un guard en el test -- que
decian: *`move()` solo resuelve la colision si `x²+y²+z² < 3.0` bloques por tick, pasado ese numero
la gota atraviesa el suelo*. **Eso era falso.** `MAXIMUM_COLLISION_VELOCITY_SQUARED` es
`Mth.square(100.0)`: **cien bloques por tick**, y no hay gravedad razonable que lo toque.

El guard mordio, pero **por el motivo equivocadisimo**: suscribia el limite que yo mismo habia
inventado, asi que fallo contra mi mentira y no contra el motor. Lo que salvo el dia fue que
tuviera que **abrir la constante** para contestarle, y al abrirla estaba escrita otra cosa.

Es la segunda vez en dos rondas que un guard sobre una constante inventada da un resultado
aprovechable por casualidad, y las dos se arreglaron igual: **el guard tiene que medir la pregunta
que importa y no mi version de ella.** El correcto no es "no atraviesa el suelo" -- esa amenaza no
existia -- sino *"cuantos ticks de su vida se pasa clavada en el piso"*, que es lo unico que se lee
como sangre. Con la formula de la distancia (`0.02 * gravity * n²`), 1,5 bloques de cuerpo y
gravedad tres salen **cinco ticks de caida y once de salpicadura**.

### Los iconos de efecto: seis archivos correctos en la carpeta equivocada

El usuario dibujo seis iconos y los dejo en `textures/effects/`. **Esa es la carpeta de staging de
este proyecto y el juego no la lee nunca**: `MobEffect` resuelve en
`assets/<ns>/textures/mob_effect/<id>.png`. Y encima los nombres eran los del **encantamiento** y
no los del **efecto** -- `cicatriz` vive en el efecto `anti_healing`, `gripa` en `moco`. Seis
archivos que existen, pesan bytes, y el HUD los ignora por completo, con el build verde.

Copiados bajo su id (`drained` se llevo una copia de `desvio_vampirico`, porque son dos efectos de
un solo encantamiento y el arte es uno). Y **les faltaba el idioma tambien**: sin
`effect.cortescombat.<id>` el HUD muestra el identificador crudo, que es la misma falla con otro
dibujo. Siete entradas nuevas en los ocho idiomas, **y `lunge`, que tambien se dibujaba en el HUD
durante un segundo como `cortescombat.lunge`** y nadie lo habia notado porque dura un segundo.

`EffectAssetsTest` camina `ModEffects` y afirma las dos mitades por cada id: icono **y** nombre.
Dos guards, y los dos muerden -- uno por cada eslabon.

### Diez sabotajes, y ninguno murio por la razon equivocada

Ocho de las particulas (fisica apagada, gravedad de pluma, drip borrado, gate del `landed` caido,
proveedor del cliente cambiado, JSON borrado, moscas con gravedad, sin STEERING) y dos de los
iconos (icono de `moco` fuera de sitio, nombre de `drained` borrado). Los diez con su muerte, y el
`FINAL` limpio.

Y una mas que no cuenta: el guard de las moscas afirmaba `STEERING` -- **el nombre de la
constante**, que sobrevive a borrar el uso. Es la cuarta vez que este proyecto paga esa diferencia
en la sesion; ahora pide la linea de uso.

1060 tests verdes (eran 1054). Las moscas quedan **listas pero sin cablear**: Rey de las Moscas las
dispara con una llamada, y esta anotado como falta en el guard correspondiente.

### Rey de las Moscas deja la nube - y un EntityType sin renderer no es invisible

La nube existe, se dibuja con las moscas que le suelta adentro, y lastima por proximidad cuatro
segundos. El encantamiento esta declarado, en `IDS`, con archivo, tag e idioma en los ocho.

**Lo que costo no fue la nube: fue descubrir que un EntityType sin renderer registrado no es una
entidad invisible sino un NPE en el cliente.** Verificado en las fuentes de 1.21.1:
`EntityRenderDispatcher#shouldRender` toma `getRenderer(entity)` y lo dereferencia EN LA LINEA
SIGUIENTE, sin guarda -- lo mismo en `render`. El mapa devuelve null para lo que nadie registro, y
null no es "no dibujar". Asi que `MobCloudRenderer` existe, tiene el cuerpo del `render` VACIO, y
ese vacio es correcto: la nube la dibujan las particulas, no el renderer. Diez lineas que son el
precio de no tener un cliente que se cae.

**Y el daño es de la nube, no de un efecto.** "No reinfecta" queda resuelto sin apilar nada: el
cobro sale del reloj de la nube, asi que hay una sola verdad por nube, y dos disparos al mismo
sitio son dos nubes cobrando cada una la suya. El dueño queda afuera (veto C2), igual que en el
reventon de Sentencia y en la nube de Santa Muerte.

**LO QUE NO ESTA HECHO, y es la mitad del diseño: la munición.** El diseño dice que gasta carne
podrida y no Firestone -- que es la identidad entera del encantamiento (munición gratis). Hoy
gasta Firestone igual que un disparo normal, o sea que lo que cambia es QUÉ devuelve (un area que
dura) y no QUÉ cuesta. `firestoneSlot` es el sitio, y es un refactor del bucle del arma que no se
toco en esta ronda.

**Y una trampa de Herramienta, tercera vez en la sesion y la mas cara:** un `String.Replace` de
PowerShell reemplaza TODAS las ocurrencias. El sabotaje del renderer usaba `SlashWaveRenderer::new);`
como texto de reversión, y ese string esta en dos sitios -- asi que la reversión puso el renderer
de la nube donde iba el de la onda, rompió la compilación, y el script reportó `EXIT=1 muertos=0`
cuatro veces seguidas antes de que mirara el archivo. **Un sabotaje que no compila no es sabotaje,
y una reversión que pisa no es una reversión.**

1061 tests verdes. Faltan tres cosas y ninguna es chica: **la munición de Rey de las Moscas**,
**Pala Antimaterial** y **Mungus**.

### Tres correcciones de un playtest: la nube sigue al cuerpo, el disparo suelta moscas, y el rayo es una linea

**La nube de moscas no seguia al enemigo**, y eso la hacia lo contrario de una nube: nacia donde
el cuerpo ESTABA en el tick del disparo, asi que un zombi que daba un paso quedaba afuera -- el
encantamiento estaria castigando al que se mueve. Ahora el servidor le pasa el cuerpo y la nube
se va con el, y **muere con el**: si el infectado cae, el seguimiento se apaga y la nube se queda
donde estaba cobrando a los de alrededor, porque una nube que se fuera a buscar a otro no seria el
remate de un disparo sino una bala teledirigida.

**El disparo ahora suelta moscas y humo en la boca del arcabuz.** Un disparo que no deja nada en
el aire es un sonido: el rayo tiene que salir de algun lado. Van juntos porque son el mismo evento
-- un arcabuz quema polvora y esto no dispara plomo.

**Y Capacitor dibuja la linea entera.** Antes spawnaba END_ROD en el objetivo y se leia como
"particulas que aparecen ahi"; **un rayo sin origen no dice quien lo tiro**, y en un duelo eso es
la mitad de la informacion. `LightningParticles.zigzag` camina la linea a mano -- diez puntos, un
paquete por punto -- porque `sendParticles` sabe mandar UNA CAJA y no una trayectoria. El zigzag
va dentro de una envolvente senoidal que es CERO en los extremos: sin eso el rayo empezaria
flotando en el aire al lado del que lo tiro. Y los dos ejes perpendiculares salen de un producto
cruz, asi que zigzaguea en dos dimensiones y no en un plano, que se lee como un dibujo.

1066 tests verdes.

## Ronda de la comida y la regeneración de vida — paso 11 (2026-10-11)

**Estado: implementado. Servidor de dev arrancando limpio (0 ERROR), 1092 tests verdes, y los 13
sabotajes de esta ronda mataron los 13 guardas. Pendiente de playtest** — que es donde se tunean
los números, y donde se ve el seam de `FoodData` corriendo con un jugador adentro.

### Primero: la documentación se contradecía, y había que resolverlo antes de escribir nada

DESIGN.md §5 y dos bloques de AGENTS.md dicen **"comer se interrumpe al recibir daño"**.
`docs/api-trampas.md` y otros dos bloques dicen **"el daño no interrumpe comer, no lo rompamos"**.
Las dos son ciertas y no son la misma afirmación: la primera es una mecánica que **hay que
agregar**, la segunda es lo que hace **vanilla**.

Verificado contra las fuentes de 1.21.1 (no de memoria): `Player#hurt` son 106 bytes que chequean
invulnerable, escalan y delegan a `super`; `LivingEntity#hurt` y `actuallyHurt` **no llaman nunca**
a `stopUsingItem`. Nada en el juego corta una comida. Por eso el faq de trampas tiene razón y la
fila del diseño también: vanilla no interrumpe, y nosotros tenemos que hacerlo.

Resuelto con el usuario: **el golpe interrumpe sólo si no estás cubierto.** Satisface las dos
reglas a la vez — la fila del diseño y la más vieja, "cubrir y comer es legal". Un gate de una
línea sobre `GuardHandler.isGuarding`.

### La arquitectura, y el contrato de portabilidad

El pedido fue explícito: **que sea fácilmente porteable a otras versiones.** Eso sólo es posible si
la superficie específica de la versión está nombrada y es chica, y lo está:

| Capa | Archivos | Cambia al portar |
|---|---|---|
| Reglas | `SurvivalRules`, `SurvivalTuning`, `SurvivalState` | **Nunca.** Ninguno importa un tipo de Minecraft. El paquete se copia entero y sus tests siguen pasando |
| Adaptador | `SurvivalTicker` | Sólo si renombran un método. Todo lo que llama es API pública de `Player`/`FoodData`, verificada con `javap` contra el jar runtime |
| Seams | `FoodDataMixin`, `ItemStackDrinkDurationMixin` | Dos puntos de inyección, cada uno con su descriptor escrito completo. Es el único sitio donde un cambio de versión puede romper, y rompe fuerte |

El tercer punto es una **línea que está escrita en un test**: `theRulesArePortable` afirma que
ninguno de los tres archivos de reglas importa `net.minecraft` ni `net.neoforged`. Un `Player` que
aparezca en un parámetro de una regla hace fallar el build.

### Por qué se reemplaza el tick entero y no se parchean constantes

`refs/combatify` hace exactamente lo contrario y es la mejor demostración de por qué no: su
`FoodDataMixin` cambia el suelo de curación con `@ModifyExpressionValue` sobre el literal `18`
dentro del método, los timers sobre el `10` y los dos `80`, **cada uno addressed por ordinal**.
Funciona hasta que Mojang mueve un número, y entonces cada inyección **deja de matchear en
silencio**, sobre un método que igual compila y corre.

El nuestro es `@Inject(at = HEAD, cancellable)` sobre `tick`, cancelar, y delegar a
`SurvivalTicker`. Un punto de inyección que aplica o revienta en bootstrap, y el comportamiento
donde se puede leer y testear. `theSeamDoesNotPatchConstants` prohíbe `ModifyExpressionValue` y
`intValue=` en el archivo.

### `javap` antes de cada mixin — los tres, contra el jar runtime

La regla propia del proyecto, y se siguió al pie de la letra (`neoforge-21.1.250-merged.jar`):

```
FoodData.tick(net.minecraft.world.entity.player.Player)
    descriptor: (Lnet/minecraft/world/entity/player/Player;)V
FoodData.addExhaustion(float)      descriptor: (F)V
FoodData.setExhaustion(float)      descriptor: (F)V
ItemStack.getUseDuration(LivingEntity)  descriptor: (Lnet/minecraft/world/entity/LivingEntity;)I
```

Y `Player.isHurt()`, `LivingEntity.heal(F)`, `stopUsingItem()` — los tres públicos, presentes.

**`FoodData` no tiene ningún patch de NeoForge**: leída la clase entera, cero comentarios `// Neo:`.
CONFIRMADO lo que DESIGN.md §5 decía.

### Dos relojes, no uno

Vanilla tiene **un** `tickTimer` porque sus tres ramas son mutuamente excluyentes (curar, morir de
hambre, o ninguna). Las nuestras **no lo son**: un jugador puede estar a mitad del reloj de curar
y a mitad del reloj de comida en el mismo tick. `SurvivalState` son dos ints, y es un attachment
**sin codec y sin `copyOnDeath`** por la misma razón que `ContragolpeWindow`: son contadores de
cuatro segundos, y sobrevivir a un guardado sería progreso que el jugador no ganó.

**Un reloj, dos salidas.** El de comida regenera cuando hay lugar y sólo pide hambre en cero
exacto, así que el ciclo se cierra solo: la curación gasta comida, el reloj la devuelve, y las dos
cosas son mutuamente excluyentes por construcción — un tick nunca regenera y mata a la vez.

### El exhaustion se apaga en su llave, no en su consecuencia

Cancelar sólo el tick habría quitado el **efecto** (es el único lugar donde se lee el número), pero
el número seguiría siendo escrito por `Player#causeFoodExhaustion` — correr, saltar, pegar, romper
bloques — y se subiría al tope de 40 y se quedaría ahí. Cualquier mod que lea
`getExhaustionLevel()` le diría que este juego tiene un sistema de hambre **lleno y sin usar**.

`addExhaustion` es el embudo único por el que pasan todos esos escritores, así que se cancela ahí
**y se pone en cero**, que es lo que limpia un valor que un save viejo ya trae.

### `lastFoodLevel` no se escribe, y por eso está documentado

Es `private` con sólo getter, y **nadie en 1.21.1 lo lee** — verificado buscando
`getLastFoodLevel` en las fuentes del juego entero: sólo la definición. El parpadeo de la barra de
comida lo maneja la saturación, no esto. Escribirlo exigiría un accessor mixin sobre un campo sin
consumidor, que es la sección 9 de ARCHITECTURE.

### `ConfigValue.get()` lanza, no devuelve el default

Verificado en las fuentes de NeoForge: `getRaw()` hace
`Preconditions.checkState(loadedConfig != null, "Cannot get config value before config is loaded")`.
Un config SERVER está atado a un mundo, así que hay una ventana real al arranque en la que nadie lo
cargó. **`ServerConfig` pregunta `SPEC.isLoaded()` antes de leer** y devuelve los defaults: cuesta
un field read y quita una clase de crash que se reportaría como bug del mod. El TOML salió con los
cinco valores y sus rangos.

### El diagnóstico que este round necesitaba y ningún test puede hacer

`FoodData` **no se carga hasta que entra el primer jugador.** Verificado con
`jcmd GC.class_histogram` contra un server dedicado vivo y sin nadie conectado: `FoodData` y
`Player` **no estaban cargados**; `ItemStack` ya tenía 4594 instancias.

O sea que **nada del arranque iba a notar un seam caído**: el primer síntoma habría sido un crash
en la primera conexión. Por eso hay un tercer reporte de arranque, al lado de los dos que ya
existen (`[perfiles]` y `[recetas]`): carga la clase y pregunta `FoodDataSeam.isAssignableFrom`.

**Y es un marker interface, no un class literal**, a propósito: cargar `FoodData` y loguear "seam
en su lugar" sería **verdadero justo en el caso que existe para atrapar** — si el config de mixins
nunca se registró, la clase carga perfecto, no aplica nada, y la línea miente. Preguntarle a la
clase qué se convirtió es la única versión de esa línea que no puede mentir. Y **reporta sin
crashar**: un seam caído deja la comida en vanilla, que funciona.

Ese hueco también cerró la mitad que faltaba de la cadena: `theTomlNamesTheMixinConfig` afirma que
`neoforge.mods.toml` tiene el bloque `[[mixins]]` nombrando `config="${mod_id}.mixins.json"`.
**Esa línea es la que hace que los mixins del jar se apliquen**, y la ausencia no tiene ningún
síntoma — misma forma que el bug de las recetas.

### Dos bugs que salieron de escribir los tests

1. **Un guard que no podía fallar.** `theFloorStopsHealing` afirmaba `healTicks == 0` corriendo
   sólo con la comida ya bajo el piso — donde el reloj **nunca se incrementó**, así que la
   aserción era verdadera con o sin el `else { healTicks = 0; }`. Se reescribió para **cargar el
   reloj arriba del piso primero y después sacarle el piso**: ahora saboteando el reset, muere.
   *Un guard que no puede fallar no afirma nada.*
2. **Un guard que leía prosa.** `theSeamDoesNotPatchConstants` buscaba `ModifyExpressionValue` en
   el archivo — y el **javadoc del propio mixin** nombra esa anotación para explicar por qué no la
   usa. El test falló contra mi explicación de que no lo hago. Arreglo: `read()` **borra los
   comentarios** en los `.java` antes de assertar. *Un test que no distingue código de una
   explicación del código está verde hasta el día que importa.*

### Los sabotajes: 13, y los 13 mataron

Tres rondas, todas verificadas antes de dar por bueno cualquier guarda:

| Ronda | Sabotajes | Guardas que murieron |
|---|---|---|
| A (seams + config) | invertir `ci.cancel()`/delegación, borrar el `cancel()` de `addExhaustion`, sacar `FoodDataMixin` del JSON, borrar `registerConfig` | exactamente 4: `theVanillaTickIsReplacedNotAddedTo`, `exhaustionIsShutOff`, `bothMixinsAreRegistered`, `theConfigIsRegistered` |
| B (handler + adaptador + portabilidad) | borrar el gate de guardia, `heal()`→`setHealth()`, import de Minecraft en `SurvivalState`, descriptor a nombre pelado | exactamente 4: `theGuardGatesTheInterrupt`, `healingGoesThroughHeal`, `theRulesArePortable`, `liquidsAreRecognisedByAnimation` |
| C (reglas + seam) | precio de curación a 0, borrar el reset del reloj, `food > 0`→`>= 0`, barra llena que cuenta en vez de descansar, `@At("HEAD")`→`@At(CONSTANT, "intValue=18")` | 9, y los 5 sabotajes están todos accounting: el de la barra llena mataba **dos** aserciones a la vez, que es correcto — el descanso del reloj es lo que sostiene ambas |

### Tuneo de los dos números, antes del playtest (2026-10-12)

El General pidió **la curación al doble de rápida y la comida al doble de lenta**:
`healInterval` 40 → **20** (1 hp cada segundo) y `foodRegenInterval` 80 → **160** (1 punto cada ocho
segundos). No son dos tweaks sueltos — juntos **cambian de dónde sale la tensión**: una barra entera
compra veinte puntos de vida en veinte segundos y tarda 160 s en volver, así que la comida deja de
ser un reloj que acompaña y pasa a ser lo que decide si se puede volver a pelear ahora o recién
después.

**Y el cambio expuso una segunda verdad que no estaba guardada.** Los defaults viven en dos
sitios: `SurvivalTuning.DEFAULTS` (lo que corre antes de que cargue el config) y el
`defineInRange` de `ServerConfig` (lo que se escribe en el TOML y lo que **todo mundo que ya tiene
un `cortescombat-server.toml` tiene en disco**). Mover uno y no el otro habría dado un juego que
cambia según cuál de los dos mires, sin ningún test que lo dijera. ∴ existe
`theConfigDefaultsAreTheOnlyTruth`, que parsea los dos números del código y los compara contra el
record — que es la misma clase de guard que el de los encantamientos, aplicada a un número en vez
de a un archivo.

Y el TOML de desarrollo se **borró**: NeoForge **no cambia** valores que ya están escritos, sólo
agrega los faltantes, así que sin borrarlo el server de dev seguía corriendo 40/80 y toda
verificación posterior habría mentido.

1093 tests verdes.

### Lo que queda abierto

- **Playtest.** `foodRegenInterval` (160 desde el tuneo de 2026-10-12) es, según el propio diseño,
  el número más importante del proyecto, y sólo se tunea jugando. Y el seam de `FoodData` se ve
  corriendo con un jugador adentro.
- **El golpe que interrumpe comer.** Implementado como `stopUsingItem()` de una sola vez; vanilla
  rearma el click sostenido cada 4 ticks, así que en la práctica **cada golpe pierde el mordisco y
  empieza de cero** — que es la lectura correcta de "se interrumpe", pero si en playtest suena a
  que "sólo parpadea", la ventana de rechazo es una línea más acá.
- **Rebalanceo de pociones y manzana dorada** (I: 6, II: 12, daño I: 6, manzana 8) — decisión
  cerrada del diseño, ronda aparte: necesita otro seam sobre `InstantenousMobEffect`, porque en
  21.1 las pociones no son datapack. **Planificado, no empezado.**
- **La barra de comida ya no tiembla.** El temblor vanilla sale de `saturation <= 0`, y con el
  exhaustion fuera la saturación nunca baja de su inicial 5. Es coherente con "la saturación no es
  mecánica", pero es un cambio visual silencioso que conviene mirar.

1092 tests verdes.

### Nota de honestidad sobre el commit 1134a1b

**Ese commit contiene mas de lo que su mensaje dice, y es un error mio.** El mensaje describe dos
cosas -- el estallido al disparar al aire y los pesos de los encantamientos -- y el commit trae
**13 archivos**, porque corri `git add src` sin listar que habia adentro y barri todo el Paso 11
(supervivencia) que estaba sin trackear en el arbol:

- `survival/` (5 archivos: `SurvivalRules`, `SurvivalState`, `SurvivalTicker`, `SurvivalTuning`,
  `EatInterruptHandler`)
- `mixin/FoodDataMixin`, `mixin/ItemStackDrinkDurationMixin`
- `config/ServerConfig`
- `CortesCombat` (+7, el `registerConfig` del server), `ModAttachments` (+18)

**Ninguno de esos lo escribi yo en esa sesion, y no los revise.** El codigo tiene la voz y las
convenciones del proyecto y el `registerConfig` es el patron documentado, pero eso no es una
revision: es una impresion. Quien lea ese commit tiene que saber que su mensaje no alcanza para
saber que hay adentro.

**La leccion, y es la misma que ya estaba escrita con las texturas:** un `git add` a ciegas le
pone mi nombre a trabajo ajeno. La regla es **listar los archivos antes de commitear**
(`git diff --cached --name-only`), que es una linea y ya la usaba cuando el cambio era mio.

**No se reescribio el historial**: el commit esta pusheado y rehacerlo seria un force-push.
Queda anotado, y si se decide limpiarlo, se hace sabiendo que hay que forzar el remoto.

## Ronda del arcabuz — cuatro rondas, un solo error (2026-10-12)

**30 commits.** La ronda arrancó como "arreglemos el arcabuz" y terminó siendo el bug más caro del
proyecto por una razón que tardó cuatro intentos en aparecer: **el estado del arma estaba guardado en el
jugador.**

### Los tres síntomas, y eran el mismo

| lo que reportó el playtest | lo que era |
|---|---|
| *"si recargás un arcabuz y pasás a uno sin recargar, este podrá disparar como si fuera recargado"* | la **recámara** se heredaba |
| *"al recargar y cambiar... si vuelvo una SEGUNDA VEZ al arma cargada, y luego a la descargada, este se carga"* | la **recarga** se heredaba |
| *"cuando recargás el arma pero la cambiás y volvés... se ve como si estuviera descargado"* | la **mecha** se heredaba |

Los tres son **"cambiar de arma hereda el estado del otro"**, porque el `ArcabuzState` era un
attachment del **jugador** y el stack era "la verdad sobre la recámara" → **dos fuentes de verdad para
la misma pregunta**, reconciliadas a mano en tres lugares.

### Los tres parches, y por qué fallaron

1. **Reconciliar en el `resolve`.** El press corre en el **paquete**, el reset en el **tick**: el press
   puede llegar primero. Y `withChamber` solo podía **llenar**, así que un `loaded=true` viejo no se
   bajaba nunca.
2. **Reconciliar en el `tick`.** Mismo problema al revés, y el reset pisaba la recarga en vuelo.
3. **Anotar contra qué arma iba** (`ARCABUZ_GUN`, por identidad de `ItemStack`). **Empeoró las cosas**:
   la instancia del stack la reemplaza vanilla sola, así que el reset disparaba de más — el log lo
   mostró (`estado reiniciado` 2 a 4 veces por segundo) y **la recarga se moría al cambiar de arma**.

**La señal de que estaba parchando la causa equivocada no fue que los arreglos fallaran: fue que
aparecía un síntoma nuevo de la misma familia cada vez.**

### La asimetría que era el bug, y su justificación falsa

`withChamber` era asimétrico a propósito, con un comentario que lo explicaba:

```java
if (!chamberOnStack) return this;   // "el stack solo puede decir que si"
```

La justificación escrita era: *durante la combustión la recámara ya se gastó del stack, así que releer
`false` "vaciaría un arma a punto de disparar"*. **Es falso** — `light()` **ya devuelve la recámara
vacía**, porque encender la mecha **es** lo que la gasta. Durante la combustión los dos valores ya
coinciden, y la asimetría no protegía nada: **lo único que hacía era que un `true` viejo no se pudiera
limpiar nunca.** Y un valor cacheado que solo puede subir **falla hacia adelante**: no se equivoca al
azar, se equivoca siempre en el mismo sentido.

**Y el guard que exigía la asimetría lo escribí yo**, con un mensaje que la justificaba (*"si alguien lo
hace simétrico para arreglarlo, vacía el arma en cada tick de la mecha"*). Era la razón por la que la
dirección no se podía corregir sin borrarlo. **Un guard puede fijar una equivocación con la misma
firmeza con que fija un invariante.**

### El refactor: el estado se muda al arma

**Un componente `ARCABUZ` en el stack** con las tres cosas: recámara, mecha y recarga. El jugador
guarda **solo el botón**. Desaparecen: la reconciliación, la comparación de identidad, el reset, el
`shouldSnuff` y la máquina de estados `step()`.

**Un arma fuera de la mano no se tickea, así que no hay nada que heredar** — la propiedad sale de la
arquitectura y ⊘ de un chequeo.

**Y lo que lo hizo posible sin pagar el bob del modelo: el tick ABSOLUTO de fin.** Un contador obliga a
escribir el componente **cada tick**, y cada escritura reemplaza el `ItemStack` del cliente y dispara
la animación de cambio de item, que mueve el arma **60 cm**. El proyecto ya lo había pagado con la
animación de la mecha. Con `fuseEndsAt`/`reloadEndsAt` el componente se escribe **4 veces por ciclo de
disparo y nunca entre medio**, y decidir es `now >= fuseEndsAt` contra `level.getGameTime()`: una
función pura.

**Premio inesperado:** un arma guardada en un cofre **conserva su estado**, porque viaja con el item.

**Lo que se pierde, y está documentado:** una mecha cuyo tick ya pasó **dispara al primer tick que el
arma vuelve a la mano** (una demora, ⊘ una ganancia: la Firestone se gastó al encender), y una recarga
con el botón suelto se cancela sola al levantar el arma.

### Dos bugs que ⊘ eran de estado

**Mungus y el tipo que un `super()` pisa.** El playtest: *"se siente mejor pero aún da tirones raros, es
como un problema de sincronización, la física se siente correcta"*. La física **era** correcta — lo que
estaba mal era que **el cliente simulaba otra clase**. `LargeFireball(Level, LivingEntity, Vec3, int)`,
el constructor que **dispara**, hace `super(EntityType.FIREBALL, ...)`: heredar el disparo significa
heredar el tipo de vanilla. Y **el paquete de spawn lleva `getType()`, ⊘ la clase** → el cliente
construía una bola de fuego vanilla (sin gravedad, `accelerationPower` 0,1, inercia 0,95) mientras el
servidor simulaba `MungusShell`. Los tirones eran la distancia entre las dos copias. Se arregla
sobreescribiendo `getType()` (`Entity#getType` no es final; los dos únicos lugares de vanilla que leen
el campo crudo — dimensiones y un tag de *fall damage* — dan igual acá).

Y antes de eso: **la bola de fuego vanilla no vuela, se auto-acelera.** `AbstractHurtingProjectile#tick`
hace `v = (v + v̂·0.1)·0.95` arrancando de **0,1** → la velocidad se multiplica por **19** a lo largo del
vuelo, y como la gravedad dobla la dirección la auto-aceleración empuja **siempre hacia donde la curva
ya va**. **Una flecha no hace eso**: `AbstractArrow` es `v·0,99` + gravedad. Se le puso el modelo de la
flecha (`accelerationPower = 0`, inercia 0,99, velocidad de salida real), y el `accelerationPower` va en
**los dos constructores** porque el cliente entra por `(EntityType, Level)` y ese campo **no viaja en el
paquete de spawn**.

**El humo de la mecha, y el número que cerró la pregunta.** Se reportó **dos veces** como *"sale de en
medio de la pantalla"*, y las dos anclas posibles (cámara fija / matriz del item) ya habían sido
probadas y rechazadas una vez cada una. **Un solo número en el log la cerró:** `distOjo` daba **0,94
constante** (→ está sobre el arma, ⊘ en la lente), y la traslación de la matriz tenía **magnitud
constante 1,08** (= `|ITEM_POS|` 1,05, → la matriz **sí** es la del arma) pero **su Z saltaba entre
−0,92 y +0,71** — y una distancia **al frente** no puede ser negativa. La matriz está en un marco que
**rota con el jugador**, ⊘ en el de la vista, y pasarla por `inViewSpace` aplicaba esa rotación **dos
veces**. Vuelve al marco de la cámara del fogonazo, que lleva dos rondas sin una queja.

### Y la ronda de encantamientos que había antes en el mismo día

- **El orbe de la pala salía en el PRESS y ⊘ al soltar**: el `resolve` todavía llamaba a
  `launchAntimatter`, así que el click disparaba en el mismo tick y la carga nunca contaba.
- **Durabilidad del orbe: 5 → 30.** El playtest la llamó *"muy poquito"* y tenía razón por una cuenta
  que la primera versión no hizo: **cinco sobre 2031 de una pala de netherite son cuatrocientos orbes**
  — o sea, lo mismo que gratis. **Un costo que no cambia ninguna decisión no es un costo, es un
  número.**
- **Rey de las Moscas infestaba uno.** Tomaba `seen.get(0)`: el cono ya decide a quién llega, y dejar
  afuera a los otros siete que el mismo cono eligió es cobrar un disparo por lo que devuelve un golpe.
  Ahora una nube por cuerpo, con **un solo zumbido** aunque haya ocho (ocho copias del mismo archivo
  en el mismo tick ⊘ son ocho enjambres, son uno al doble de volumen).
- **El zumbido de Rey de las Moscas no sonaba NUNCA**: el `.ogg` estaba en `sounds/combat_fx/` y el
  JSON lo declaraba en `sounds/weapons/`. **Y el guard ⊘ lo veía** porque `pools()` y `filesOnDisk()`
  **tiraban la carpeta de los dos lados** (`id.lastIndexOf('/')`) y comparaban el nombre pelado, que es
  idéntico — y **la carpeta era lo único roto**. Es la segunda vez: la primera fue `sweeping_edgeV2`,
  donde lo normalizado era una mayúscula. **La pregunta es siempre la misma: ¿qué está tirando el guard
  antes de comparar?**
- **El culatazo**: el disparo empuja hacia atrás por el suelo (0,25 → ~60 cm medidos: un jugador frena a
  0,6/tick), y **Mungus ×4** (~2,5 bloques). Va **antes** de las dos ramas que hacen `return`, o no
  correría justo en los dos tiros que más lo necesitan.
- **Creativo ⊘ paga munición**, y solo creativo. El modo no abarata el **tiempo** de la recarga.

## Ronda de los dos reportes de playtest: `/kill` y los marcos de ítem (2026-10-16)

Dos bugs sin relación, los dos reportados por un playtester. El primero parecía el shader de golpe y
no lo era; el segundo dejó **inutilizables todos los marcos de ítem del mod**.

### 1. `/kill @e[type=..]` crashea el cliente — el número que satura

**El reporte decía "el shader de golpe".** El shader (los tres mixins del flash, `OverlayTextureMixin`
/ `LivingEntityOverlayMixin` / `LivingEntityRenderMixin`) está bien: se leyó entero buscando el crash
y no participa. **El culpable es un conteo de partículas sin techo.**

La cadena, verificada en las fuentes de 21.1.250:

- `LivingEntity#kill()` es una línea: `hurt(damageSources().genericKill(), Float.MAX_VALUE)`.
- `actuallyHurt` toma `float f1 = damageContainers.peek().getNewDamage()` **sin recortarlo contra la
  vida** (L1793) y dispara `onLivingDamagePost` con ese valor. Hay incluso un guard
  `f < 3.4028235E37F` para el stat de absorción — o sea que **saber que `Float.MAX_VALUE` llega hasta
  acá ya estaba escrito en el motor**.
- `OnAnyDamage.onDamage` (`LivingDamageEvent.Post`) ve `getNewDamage()` = `Float.MAX_VALUE`, no es
  `IS_PLAYER_ATTACK`, y lo manda a `HitParticles.impact`.
- `DamageParticles.countFor(Float.MAX_VALUE)` = `(int)(Float.MAX_VALUE * 0.5F)`, y un **cast
  estrechante de Java satura en `Integer.MAX_VALUE`**.
- Ese número viaja en `ClientboundLevelParticlesPacket` y `handleParticleEvent` lo convierte en
  `for (i = 0; i < count; i++) addParticle(..)`: **~2.100 millones de partículas** = OOM y crash.

**El fix es un techo en `countFor`**, no en cada llamador: es la única función que convierte daño en
un conteo, y los tres que la usan (`HitParticles` ×2 y `SlashParticles.damageIndicator`) quedan
cubiertos solos. `MAX_COUNT = 64`, deliberadamente muy por encima de cualquier golpe real (el arma más
grande del catálogo pega un dígito), así que ⊘ toca un golpe de verdad; por encima de él el jugador
que **cuenta** ya ⊘ puede leer el número exacto, que es el costo honesto de no crashear. El `!(x > 0)`
que ya estaba también atrapa `NaN`, y quedó un test que lo fija.

**La moraleja de la ronda:** un índice/reparto sin tope en un sistema de partículas ⊘ es un número
grande, es una bomba de memoria en el cliente — y el nombre del síntoma ("shader") apuntaba a otro
lado. La mitad que se pagó dos veces es que **`getNewDamage()` ⊘ está recortado a la vida**.

### 2. Los marcos de ítem no reciben el ataque — el cono ⊘ mira lo que ⊘ está vivo

**El reporte:** con un arma en mano, apuntar a un marco de ítem y clickear izquierda ⊘ hacía nada. Ni
soltar el ítem ni romper el marco.

La causa son **dos puertas que dan la misma respuesta equivocada**:

- El cono (`ConeTargeting.findTarget`) escanea **`LivingEntity` y nada más**. Un marco, una pintura,
  un bote, un minecart, un cristal del End, un nudo de correa y las partes del dragón **⊘ son
  `LivingEntity`** → "acá ⊘ hay nada".
- El escape de minado de `press` pregunta `isOnBlock(mc)`, y `mc.hitResult` es **ENTITY**, no BLOCK →
  tampoco dispara.

Así que el press caía hasta `claim = OURS` y `MinecraftAttackMixin` cancelaba `startAttack` en el
HEAD, **antes** del caso ENTITY de vanilla (`gameMode.attack` → `player.attack` → `ItemFrame#hurt`,
que es literalmente la ruta que suelta el ítem). La lógica de vanilla, leída: `hurt` con ítem
→ `dropItem(entity, false)` (suelta el objeto, el marco queda); vacío → `super.hurt` → se rompe.

**El fix:** una puerta en `press`, después del gate `isRanged`, que si `mc.hitResult` es un
`EntityHitResult` cuya entidad **⊘ es `LivingEntity`**, devuelve ⊘ (deja `claim` en `NOT_OURS`) y
vanilla atiende el click. **El cursor gana sobre el cono** para lo sin vida: el cono pregunta "¿hay un
cuerpo al que pegarle", y un marco ⊘ es un cuerpo — su interacción es un click puntual sobre una
superficie, y vanilla ya la resuelve. Una regla con forma de cono para los marcos sería inventar una
segunda versión de algo que el motor ya tiene. Va **antes** de los gates de guardia y cooldown a
propósito: atacar un marco ⊘ es un swing, así que lo dictado por el reporte ("como vanilla") es que ⊘
dependa del cooldown duro y funcione igual con el escudo arriba.

**Cubre de una sola regla** los siete tipos de entidad sin vida del juego, ⊘ sólo los marcos.

**Lo que se revisó y ⊘ era el problema:** el click **derecho** (`SpecialRightClickMixin` /
`SpecialInputHandler`) ⊘ está implicado — la especial sólo dispara cuando la interacción del mundo
**⊘** la consume, y la del marco la consume. Sólo faltaba el click izquierdo.

**Guards:** `DamageParticlesTest.hugeDamageIsCapped` (y `Float.MAX_VALUE`, `+Inf`, `1e9` todos al
techo) y `SwingInputHandlerWiringTest.aBodylessEntityIsVanillas`, un source-read que afirma las **dos**
mitades del fix: la pregunta por lo vivo **negada**, y el `return` **antes** de `claim = OURS`. **Los
dos sabotajes mataron por su razón**: quitar el `Math.min` → rojo en `hugeDamageIsCapped`; invertir el
`!(.. instanceof LivingEntity)` → rojo en el guard de wiring. **1167 tests verdes.**

## Ronda de portabilidad (2026-10-23)

**Motivo:** los usuarios piden 1.20.1 a gritos, y el mod estaba construido sobre cuatro sistemas que
ahi no existen (components, attachments, payloads, encantamientos data-driven). Decision del dueno:
refactor de portabilidad primero, port despues.

**Tres facades, cero logica movida:** `CortesCombat.rl()` (67 `fromNamespaceAndPath` en 47 archivos),
`platform/CombatState` (112 `getData`/`setData` en 18 archivos: los 21 attachments, delegacion pura,
sin computo, sin copia, sin cache) y `platform/EnchantView` (los 3 `EnchantmentHelper` del engine).
16 imports huerfanos de `ModAttachments` limpiados.

**Cuatro guards en `PortabilityGuardsTest`, los cuatro sabotaje-verificados, 1174 tests verdes.**

**Lo que costo y no fue regresion:** la migracion rompio 5 tests textuales que nombraban el mecanismo
en el call site -- los tres de `EnchantmentHooksTest` que pedian `GUARD_FREE`/`SECOND_WINDOW`/
`EnchantmentHelper.*`, el orden de `MaceFallWiringTest` y `DamageTypeDeclarationTest` que afirmaba la
fabrica vieja. Migrar la puerta y no la regla es el fix, y es el que se aplico.

**Nota de sabotaje:** el primer sabotaje del guard de estado reemplazo cinco sitios sin su import y
murio en compilacion antes de llegar al guard -- sabotaje mal disenado, no guard sordo. El bueno es
un solo call site fully-qualified, que compila y el guard nombra.
## Ronda del 2026-10-24 — los tres bugs del playtest 1.20.1, en cliente real

**El playtest en Quick Play (`--quickPlaySingleplayer`, instrumento nuevo en build.gradle con
`-Pquickplay`) encontro y cerro tres cosas:**

1. **El arcabuz revienta al disparar.** En 1.20.1 `Minecraft.getTextureAtlas(LOCATION_PARTICLES)`
   tira NPE porque el atlas de particulas no esta en `VANILLA_ATLASES`. Fix: `SlashParticle.resolveSprite`
   envuelve el lookup en try/catch y cae a `setSpriteByIndex(sprites)`.
2. **El sprite 2D del arcabuz en GUI mostraba el misket 3D.** En 1.20.1 el `BakedModelWrapper`
   delega la textura por `getParticleIcon()` (en 1.21.1 es `getTexture()`); el override del wrapper
   apuntaba a la textura del misket. Fix: override de `getParticleIcon` en `FlatIconFor`.
3. **La HUD congelada: cooldown a 100% siempre, carga que nunca aparece, especiales sin cooldown.**
   La copia v120 de `ModNetworking.syncTo` tenia **la condicion invertida**:
   `if (last.isDirty(...)) return;` en vez de `if (!last.isDirty(...)) return;`. Efecto exacto: si el
   estado CAMBIA -> no manda nada; si NO cambia -> manda siempre el mismo valor congelado del ultimo
   save. Los tres sintomas, un boolean mal puesto. Verificado en bytecode que en 1.20.1 el mapa del
   evento `ModifyBakingResult` es el mapa vivo de `getBakedTopLevelModels` (no copia), y que
   `ForgeItemModelShaper` cachea por item y se reconstruye con el reload -- por eso el wrapper del
   icono funciona.

**Verificado en cliente real** (Quick Play): `[sync] primer envio` salio, disparos, recargas,
`FoodDataMixin aplicado`, cero crash. **1186 tests verdes en 1.21.1.**

## Ronda compat automatica severa + arcabuz GUI (2026-10-27)

1. **Los mods detectados generan su propio weapon pack.** `AutoPackGenerator` (nuevo) escanea el
   registro en `ServerStartedEvent`, clasifica las armas ajenas y escribe
   `config/cortescombat/weapons/auto-<modid>.yaml` con una seccion por arquetipo. Solo se escribe
   si el archivo NO existe (es del usuario desde el primer byte), abstiene del item que un pack
   ya contesta y del que no tiene dano propio. `AutoPackYaml` arma el texto y es puro: el
   generador importa Minecraft y la JVM de tests no puede cargarlo (verificado con el
   `NoClassDefFoundError` real). En el server de prueba: `auto: 3 pack(s) generados` en la
   primera corrida y **cero** en la segunda, con los tres archivos intactos.
2. **Lanzas, alabardas y espadas largas, sin enums nuevos.** `spear` ahora mapea a
   `WeaponProfile.TRIDENT` (era el placeholder de ropera) y el clasificador por nombre cubre
   `spear/lance/pike/javelin/...`, `halberd/glaive/naginata/...` y `longsword/claymore/...`.
   La espada larga se escribe como `archetype: sword` + `reach: {distance: 4.1}` en la entrada
   generada: el alcance es consenso de escritorio, no un dato del juego.
3. **Tiering automatico por ore, en la carga y no en el dano.** `AutoProfile.OreTier` lee el
   material del id (netherite +2 ... wood -2) y `WeaponStats.of` escala la carga de las armas
   ajenas: ticks x (1 - 0.08*delta) con piso 5 y bonus + 0.15*delta con piso 0. Lo que el YAML
   declaro explicito no se toca. **El dano no se escala a proposito:** el arma ajena ya trae el
   suyo por material y el golpe lee `ATTACK_DAMAGE`.
4. **Nerfeo de la daga: -1.2 en todos los tiers.** `WeaponFamily.DAGA.attackDamage` 1.6F -> 0.4F
   es el unico numero que los siete materiales comparten; el bono de `CortesTier` se suma encima.
5. **El sprite del arcabuz en la GUI: era una llave, no un modelo.** En 1.20.1 el parche de
   Forge a `ModelBakery` archiva los modelos adicionales bajo el `ResourceLocation` **pelado**;
   `ModelResourceLocation.hashCode()` suma la variante, asi que el `getModel(MRL standalone)` del
   mixin caia en otro balde y `ModelManager` devolvia el **missing model** — el checkerboard del
   reporte. Fix: `ArcabuzModelProperties.iconModel()` por pata (MRL en 1.21.1, `getModel(RL)` en
   1.20.1) y el mixin pregunta la puerta. El wrapper del mapa se elimino en la 1.20.1: los
   overrides de la mecha resuelven despues, y sin override el wrapper dibujaba el sprite **en la
   mano**.
6. **Hueco del port que el diagnostico destapo:** `Trident=NONE(NO PELEA)` en 1.20.1 —
   `enchantable/trident` es tag de 1.21.1 y no existe alla. Cerrado con
   `instanceof TridentItem` al frente de `deriveProfile`; el log ahora dice `Trident=TRIDENT`.

**1232 tests verdes en 1.21.1** (+18). 1.20.1 compila y el server real arranca `Done (3.072s)`
con 7 packs / 650 entradas, `FoodDataMixin aplicado`, 46 recetas y 48 encantamientos, cero
`Caused by`. Playtest pendiente: sprite del arcabuz en GUI/hotbar, un arma de cada arquetipo
nuevo, la carga por tier y las dagas nerfeadas.

## 1.5.1 — config del HUD y ocultamiento por inactividad (2026-10-27)

1. **`[hud]` ahora se configura.** Tamano de la mira (`crosshairSize`, 0.5–3.0), colores de
   reposo/apuntado para la crosshair, los brackets y las dos barras (`crosshairColor`,
   `crosshairAimColor`, `bracketColor`, `chargeBarColor`, `arcaneBarColor`, en `#RRGGBB`), el
   shake del golpe (`crosshairShake`) y tres toggles por elemento (`combatCrosshair`,
   `cooldownBrackets`, `guardOrbs`) mas `chargeBar`. Un color mal escrito cae a su default con
   un fallback explicito, y el flash de un golpe nunca se tinta: ese color es informacion.
2. **Las barras se ocultan sin pelear.** `hideWhenIdle` + `hideIdleTicks` (default 160 ticks =
   8 s): sin objetivo en el cono, sin golpe recien aterrizado y sin carga en curso, los brackets,
   la barra de carga y los orbes de aguante se van. `hideCrosshairWhenIdle` decide si la mira se
   va con ellos (default no). El borde esta testeado: en el tick del limite ya esta oculto.
3. **Dos bugs que la config destapo.** (a) La pata 1.20.1 declaraba `cooldownBrackets` y
   `guardOrbs` y **ninguna capa las leia**: dos opciones sin efecto. (b) Apagar `combatCrosshair`
   dejaba la pantalla sin mira en las dos patas: la 1.21.1 reemplazaba la capa de vanilla para
   siempre y la 1.20.1 cancelaba su overlay sin preguntar el toggle. Ahora la 1.21.1 la envuelve
   (`wrapLayer`) y la 1.20.1 solo cancela con el toggle encendido -- apagado devuelve la vanilla.
4. **El tamano entra por un solo numero**: `CrosshairSprites.baseSize()` y todo lo que se mide
   desde la mira lee eso, asi que el hueco de la barra de carga sigue el tamano del arte.

**1243 tests verdes** en 1.21.1 (+11). 1.20.1 compila y el server real arranca `Done (4.077s)`
con 7 packs / 650 entradas, `Trident=TRIDENT` y el mixin de comida aplicado, cero `Caused by`.
