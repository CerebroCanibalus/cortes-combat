# Animación por formato de pose + multijugador

> Extraído de `AGENTS.md` (2026-10-12), literal. Casi todo HECHO y verificado (2026-10-05); lo abierto va marcado. El resumen con las reglas vive en `AGENTS.md` § PARTE 3.

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

