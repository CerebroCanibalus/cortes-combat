# FX — propuesta (2026-10-12)

> Estado: **propuesta**, nada de esto está implementado salvo lo marcado. Lo que sigue es el diseño,
> ordenado por lo que más se nota. Los números son puntos de partida a playtestear, ⊘ verdades.

## 0 · El inventario de hoy (lo que hay, verificado en código)

| Canal | Qué hay | Dónde |
|---|---|---|
| Pantalla | **viñeta roja** con vida baja (14→4) + **flash rojo** al recibir daño | `DamageOverlayLayer` |
| Pantalla | crosshair que crece y se tiñe (`HIT`/`KILL`/`GUARDED`) + corchetes de cooldown + orbes de aguante | `CrosshairFeedback`, `CombatFeedback`, `CooldownBracketLayer`, `GuardOrbsLayer` |
| Cámara | shake **por impulsos** con decaimiento, alcance 40, caída cuadrática | `CameraShake` (+ `ReactorShake`, continuo) |
| Partículas propias | `slash`, `sangre`, `mosca`, `purified_effect` | `ModParticleTypes` |
| Partículas vanilla reusadas | `DAMAGE_INDICATOR` (el golpe), `CRIT` (el cargado), `LARGE_SMOKE`/`FLAME` (arcabuz), y las del reactor/orbe | varios |
| Sonidos | 34 ids en `sounds.json` — **todos de arma o de reactor. Ninguno de estado del jugador** | `sound/ModSounds` |

**El diagnóstico de una línea:** el juego tiene muy buen feedback **del arma** (sonidos, viewmodel,
slash) y casi nada de **lo que te pasa a vos**. La pantalla es roja o no es nada, el shake solo lo usa
una explosión, y el momento más importante del combate —**recibir**— es el más mudo.

---

## 1 · Las partículas de daño: cuáles son y por qué hay que matarlas

**Las dos que preguntaste:**

| Partícula | Qué es | Dónde se usa hoy |
|---|---|---|
| **`ParticleTypes.DAMAGE_INDICATOR`** | los **corazoncitos negros** que vanilla suelta al golpear. `Player#attack` hace `int i = (int)(damage * 0.5)` y los suelta con un décimo de bloque de dispersión | `DamageParticles.countFor` (reusa el número de vanilla verbatim) y `SlashParticles:77` |
| **`ParticleTypes.CRIT`** | las **estrellitas** del golpe crítico | `Lunge:308`, `SlashParticles:188` |

**Por qué el `DAMAGE_INDICATOR` es un asco, y es peor de lo que parece:** es arte de 2011 — un sprite
casi negro, sin animación, del tamaño de un píxel de pantalla, que **nace en el cuerpo del enemigo**. A
más de dos bloques no se distingue de suciedad. Y tiene un problema peor que el arte: **el número que
lo controla es la única retroalimentación del juego que escala con la fuerza del golpe**, y hoy ese
número decide **cuántos** aparecen — o sea, el jugador tiene que **contarlos** para saber si el golpe
fue fuerte. Contar partículas no es una lectura.

### La propuesta: `impacto`

Una partícula propia, en la línea de `sangre`/`mosca` (sprite animado en tira vertical, `.mcmeta`,
como `purified_effect`):

- **El tamaño escala con el daño, no la cantidad.** Un golpe de 3 y uno de 12 se distinguen **de un
  vistazo**, sin contar. Es la misma idea que el proyecto ya usó con el slash y con `SlashWaveModel.scale`.
- **El color sale del arma** (o de `CombatFeedback.rgb()`, que ya tiene la paleta de HIT/KILL/GUARDED).
  Es la forma barata de que "me pegó un hacha" y "me pegó una daga" no se vean iguales.
- **Nace en el centro de la AABB** — la regla ya está escrita dos veces en el proyecto (`getY()` es los
  pies, y una nube en el suelo lee como polvo en las botas).
- **⊘ hereda la dispersión isotrópica de vanilla**: `sendParticles` con conteo > 0 reparte la velocidad
  `gaussian() * maxSpeed` **por eje con el mismo sigma** — solo hay nube isotrópica. Para un impacto
  eso está bien (un golpe es isotrópico), pero **si alguna vez se quiere un chorro dirigido, el paquete
  no puede** y hay que ir por entidad, como la onda de filo.

**`CRIT` se queda para el golpe cargado** (ya lo decidiste), pero el cargado merece **su** partícula:
hoy comparte las estrellas de vanilla con cualquier crítico de cualquier mod.

---

## 2 · La pantalla — el frente más grande

### 2.1 El flash de daño tiene que decir **DE DÓNDE**

Hoy: `graphics.fill(0, 0, w, h, 0xFFFFFFFF)` con `(0.8, 0.1, 0.1)` — un rojo plano en toda la
pantalla. **No dice nada que el jugador no supiera ya** (que le pegaron) y **ocupa el canal más
valioso** (la vista entera).

**Propuesta: gradiente lateral según el ángulo.** El flash deja de ser un `fill` y pasa a ser un
**blit de una textura de gradiente**, orientada hacia la fuente del daño:

- El ángulo sale del **`DamageSource`**, que en `LivingIncomingDamageEvent` ya trae la entidad. El
  cliente puede calcularlo solo si el atacante está en el mundo (no hace falta payload nuevo), o el
  server manda un `float` — un payload de 1 campo, el mismo patrón que `CameraShakePayload`.
- Con **4 orientaciones fijas** (delante/detrás/izquierda/derecha, elegidas por el ángulo más cercano)
  alcanza y ⊘ hay que rotar matrices: cuatro blits con la misma textura girada por UV.
- **Y el centro de la pantalla queda limpio**: hoy el flash rojo tapa justo lo que el jugador está
  mirando. La regla ya está escrita en el proyecto — *"el brillo más fuerte en el centro de la pantalla
  tiene que ser lo que el jugador apunta c/ él"*.

Esto **ya está anotado como decisión del usuario** en `AGENTS.md`: *"indicador direccional de daño (⊘
existe en ninguno de los 3 mods)"*. Es lo que falta para cerrarla.

### 2.2 El flash tiene que distinguir el **tipo** de daño

Hoy todo es `(0.8, 0.1, 0.1)`. Un mapa **`DamageType → rgb`** es **datos, ⊘ código**, y el jugador
aprende el código de color sin que nadie se lo explique:

| Daño | Color | Por qué |
|---|---|---|
| genérico / melee | rojo sangre | el de siempre |
| **fuego / lava** | naranja `0xFF7A18` | y **⊘ rojo**: el rojo ya está gastado en "te pegaron" |
| **veneno / wither** | verde enfermo `0x5FA83C` | |
| **caída** | blanco sucio `0xD8D8D8` | un impacto **abajo**, ⊘ al frente: la orientación también cambia |
| **explosión** | amarillo `0xFFD24A` | + el shake ya existe |
| **bloqueado** (`GUARDED`) | azul gris `0x8296AD` | **el color ya está decidido en `CombatFeedback`** — reusarlo |

El caso **bloqueado** es el que más falta: hoy un bloqueo exitoso tiñe el crosshair y **nada más**. El
jugador que se cubre y aguanta tres golpes no ve la aguante bajar en la pantalla, solo en los orbes.

### 2.3 La viñeta de vida baja tiene que **latir**

Hoy el alpha converge con easing coseno a un valor **estático** según la vida. Una capa roja quieta se
lee como un filtro; **un pulso lento se lee como un corazón**.

- **0,8–1,2 Hz**, amplitud chica (±0,06 de alpha) que **crece** cuando la vida baja: a 8 de vida casi
  no se nota, a 2 late fuerte.
- ⚠ **Determinista, del reloj del mundo** (`level.getGameTime() + partialTick`, como `CameraShake`), y
  **⊘ ruido aleatorio por frame** — está **vetado por nombre** en `ARCHITECTURE.md`.
- ⚠ Y **no toca el `Fade`**: el latido es un **término que se suma** al alpha objetivo, ⊘ un segundo
  sistema de fade con su propia constante.

### 2.4 Lo que ⊘ hay que hacer

- **⊘ un segundo sistema de fade.** `Fade.toward`/`Fade.decay` ya son exponenciales contra
  `getRealtimeDeltaTicks()` y ya tienen el clamp de `MAX_FRAME_SECONDS`. Todo lo nuevo se compone ahí.
- **⊘ más capas de GUI.** Cada capa es un blit de pantalla completa por frame. Si se puede, el flash
  direccional y el tipo van **en la misma capa** (`DamageOverlayLayer`), con `setColor` distinto.

---

## 3 · El shake — lo que está tirado

`CameraShake` es **la mejor infraestructura de FX del mod y la usa una sola cosa**: el orbe de
antimateria (14 ticks). Ya tiene todo lo que hace falta:

- impulsos con **decaimiento** (arranca al máximo y baja a cero — sin eso "se lee como un glitch"),
- **dos frecuencias que no son múltiplo** (3,1 y 4,7 rad/tick) para que la figura no se repita,
- caída **cuadrática** por distancia (lineal deja el borde del alcance a media fuerza),
- **filtro por dimensión** (el reloj de un mundo nuevo arranca de cero → un impulso viejo no vencería nunca),
- y `ClientConfig.CAMERA_SHAKE` para apagarlo.

**Un golpe recibido no sacude nada, y es el momento donde el shake es más barato y más informativo.**

### La propuesta: el shake como el indicador direccional que se **siente**

| Evento | Amplitud | Eje | Nota |
|---|---|---|---|
| **recibís un golpe** | `0,35°–0,6°` escalado por daño (cap ~1°) | el **roll va hacia el lado del atacante** | es la versión seca de §2.1: ⊘ hace falta leerlo |
| **golpe cargado aterrizado** | `0,5°` | pitch | el que pega siente el peso |
| **guardia rota** | `0,8°` | roll + pitch | el stun es el momento más caro del juego y hoy ⊘ se ve |
| **Mungus cerca** | `0,3°` continuo mientras vuela | — | hoy solo tiene el sonido |

- **Escala con el daño y con la distancia** (la caída cuadrática ya está).
- **Se compone solo**: `CameraShake` **suma** al pitch/roll, así que un golpe cerca de un reactor
  sobrecargado sacude más que cualquiera de los dos — y **nadie tuvo que escribir esa regla**.
- ⚠ **⊘ por frame y ⊘ random**: todo sale de `sin/cos` del reloj del mundo. El proyecto ya tiene la
  lección escrita (*"ruido aleatorio por frame en el shake"* está en la lista de vetados).
- ⚠ **Y el shake de un golpe NO es el shake de una explosión**: `PITCH_HZ`/`ROLL_HZ` son altas (3,1/4,7)
  porque están pensadas para 14 ticks. Un impulso de 6 ticks con esas frecuencias son **dos tirones**;
  el golpe necesita **su** frecuencia (más baja) o **su** duración. **Es el número a playtestear.**

---

## 4 · El sonido de carga completa — `NOTE_BLOCK_HAT`

**`SoundEvents.NOTE_BLOCK_HAT`** es el tick corto de la caja de notas (la caja de ritmo). Encaja por
tres razones y las tres importan:

1. **Es seco y corto (~50 ms)** → se lee como *"listo"*, ⊘ como una nota.
2. **⊘ es melódico** → no compite con la música ni con los sonidos de arma, que ya son 34.
3. **El jugador ya lo conoce** de las granjas de caja de notas, así que suena "de Minecraft".

**Dónde:** en el **flanco** `!wasCharged && isCharged` del cliente, server-side como todos
(`CombatSounds`) → **un solo disparo por carga, ⊘ por tick**. Reproducirlo por tick es exactamente el
bug que ya costó el `Maximum sound pool size 247 reached` del reactor.

**Y `CombatFeedback.CHARGED`** con rgb ámbar (`0xFFD24A`): el crosshair lo dice **y** el oído lo dice.
El enum es una lista cerrada a propósito — *"un HUD que reacciona a todo deja de significar nada"* — y
`CHARGED` es de los que sí significan algo: es la mecánica más larga del juego (40 ticks) y **el único
canal que funciona sin mirar**.

---

## 5 · Partículas que faltan (las que el juego ya cobra y ⊘ muestra)

| # | FX | Dónde vive el efecto | Por qué |
|---|---|---|---|
| 1 | **`impacto`** (reemplaza `DAMAGE_INDICATOR`) | `CombatEngine` / `DamageParticles` | §1: el tamaño pasa a decir la fuerza |
| 2 | **chispas de guardia** | `GuardHandler.onShieldBlock` | hoy un bloqueo ⊘ deja **nada** en el mundo: solo el crosshair se tiñe |
| 3 | **polvo de la guardia rota** | `GuardState` al llegar a 0 | el stun es el momento más caro para el jugador y ⊘ se ve |
| 4 | **estela del lunge** | `Lunge` | la ropera cruza 6 bloques sin dejar rastro |
| 5 | **aire cortado del barrido** | `HalberdSweep` | ya tiene sonido y la onda de filo; falta lo que corta el aire |
| 6 | **impacto de proyectil** | flecha / bola de fuego al aterrizar | hoy el proyectil simplemente aparece |
| 7 | **la purificación** | `PurificationEffect:75` | **existe, llama a la partícula, y ⊘ se dibuja** — ver §6 |
| 8 | **curación** | `SurvivalTicker` / `sanguijuela` | el ciclo de comida cura **todo el tiempo** y ⊘ se ve; un `ENCHANT` subiendo lo dice |

**Los 3 primeros son los que valen**: los tres son "el juego ya decidió algo y el jugador ⊘ se enteró".

---

## 6 · Un bug real que apareció mirando esto

**`CortesCombatClient:129` registra el tipo `SANGRE` donde quería `PURIFIED`.**

```java
// L129 — el comentario dice "The purification plume"
event.registerSpriteSet(ModParticleTypes.SANGRE.get(), ... PurifiedParticle ...);
// L138 — y acá el mismo tipo, otra vez, con la partícula correcta
event.registerSpriteSet(ModParticleTypes.SANGRE.get(), ... SangreParticle ...);
```

Consecuencias, las dos verificadas:

1. **`PURIFIED` (`purified_effect`) ⊘ tiene proveedor.** `ParticleEngine` devuelve `null` para un tipo
   sin provider y el cliente **descarta la partícula en silencio** — sin error, sin log, sin nada.
2. **La purificación del cobre existe y la spawnea**: `PurificationEffect.java:75` llama a
   `PurifiedParticles.puff(server, entity)`. O sea: **el efecto del cobre cobra el daño y ⊘ se ve**.
3. Y el segundo registro **pisa** al primero, así que el `PurifiedParticle` es código muerto.

**Es la forma exacta del bug que el proyecto ya tiene escrito tres veces** — un componente puesto y su
consumidor muerto, y **el build verde**. Se arregla cambiando **una palabra** en la línea 129. Y deja
la pregunta obvia: **¿qué más está registrado y ⊘ se dibuja?** Un guard que camine `ModParticleTypes` y
afirme que **cada tipo tiene proveedor** es de las cosas baratas que pagan solas — es el mismo guard que
`EffectAssetsTest` hace para los iconos de efecto.

---

## Orden sugerido

1. **El fix del `PURIFIED`** (una palabra) + **el guard** de que todo tipo de partícula tiene proveedor.
2. **El flash direccional + por tipo** (§2.1/2.2) — es el cambio que más se nota, y el que cierra una
   decisión que ya está tomada en `AGENTS.md`.
3. **El sonido de carga completa + `CHARGED`** (§4) — es el más barato de todos y tapa el hueco más
   viejo de la lista.
4. **El shake del golpe recibido** (§3) — infraestructura que ya existe, sin usar.
5. **La viñeta que late** (§2.3) y **`impacto`** (§1) — los dos siguientes por impacto.
