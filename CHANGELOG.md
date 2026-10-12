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
- **⚠️ DEUDA LEGAL: quedan DOS texturas de `refs/`** — la viñeta (**All Rights Reserved**, sin `LICENSE`) y las dos esferitas de aguante (LGPL-2.1). Ver tabla en "Texturas".


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
