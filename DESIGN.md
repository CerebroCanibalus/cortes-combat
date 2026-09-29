# DESIGN.md — Cortés Combat

Diseño de combate. Estado: **v2, decisiones de estructura cerradas**. Lo marcado `[DECIDIDO]` viene del usuario; lo demás es propuesta mía y se puede mover.

Principio rector: **un jugador nuevo nunca hace algo que le salga mal; uno experto tiene razones para dominar la mecánica.** Ninguna decisión de diseño puede castigar la falta de conocimiento.

---

## 1. El loop central

**`[DECIDIDO]`** El arma tiene **velocidad de ataque dura**: no se puede atacar a medio cooldown. Y **el daño nunca baja por click rápido** — se elimina el multiplicador de timing del vanilla (que premia spamear más rápido).

La carga acumula **bonus de daño encima del base**, que siempre se conserva. Nunca resta.

### Los dos indicadores

**`[DECIDIDO]`** Son dos barras separadas con trabajos separados:

| Indicador | Muestra | Dónde |
|---|---|---|
| **Indicador vanilla** (sprite del brazo, `getAttackStrengthScale`) | **Carga** | Bajo la crosshair, el nativo de MC |
| **Barra horizontal nueva** | **Cooldown** | Bajo la hotbar |

Por qué así: el indicador vanilla es un sprite pequeño que se llena. No tiene resolución visual para ser un medidor fino de cooldown, pero es *perfecto* para "sincronía" — que es exactamente lo que es la carga. Y la barra horizontal nueva tiene el espacio y la resolución para mostrar un cooldown con precisión, que es un dato que necesita ser exacto.

- La carga solo se acumula mientras se **mantiene** el click izquierdo.
- El cooldown se llena solo, y es la fuente de verdad de si podés atacar.
- El cooldown nunca se saltea: la barra horizontal es el permiso para atacar.

### Números

| Parámetro | Regla |
|---|---|
| `attackInterval` | ticks entre ataques, por arma (vanilla: espada 12.5, hacha 20, pico 16.6, maza 20, tridente 12.5) |
| `chargeFullTicks` | `attackInterval × 2` — carga completa = 2 cooldowns |
| `damage` | `base × (1 + chargeRatio × chargeBonus)` |
| `chargeBonus` | 0.5 (+50%) default, por arma |

Consecuencia intencional: una espada llega a carga completa en ~1.25s, una hacha en ~2s. Las armas lentas son las que *vale la pena* cargar, y eso se siente solo.

La decisión real en cada golpe es: **ataco ya al 50% o espero 0.6s más por el 100%.** Esa es la profundidad, y no requiere saber nada que no esté en la barra.

### Autoataque

**`[DECIDIDO]`** Mantener el click **carga y dispara**. El juego se juega solo con un poco de pulgar: no hace falta hacer clic repeatedly.

Consecuencia que hay que tener presente: la carga se vuelve el estado por defecto en combate sostenido, y **el ritmo del jugador pasa a ser la única variable**. Eso es coherente con "intuitivo", pero significa que el juego base ya está en las manos del jugador y la profundidad tiene que venir de otro lado — no de la carga.

Fuentes de profundidad restantes, que son las que quedan de verdad:
- **Elegir arma** para el objetivo (alcance, cono, cooldown) y para la situación (escudo o carga).
- **Click derecho** en el momento correcto, contra el cooldown del special.
- **Leer al rival**: cuántos golpes le quedan de aguante, si está por stuning.
- **Gestionar la aguante** del escudo: cuándo gastarla y cuándo no.

---

## 2. Alcance: cono por arma

**`[DECIDIDO]`** Cada arma tiene alcance horizontal y vertical distinto, en cono, hacia el frente. **Cono fijo**: el ángulo es el mismo a 1 metro que a 4.

Tres parámetros independientes por arma:

- `distance` — metros hacia adelante
- `horizontalAngle` — medio ángulo horizontal (grados)
- `verticalAngle` — medio ángulo vertical (grados, más ceñido)

El vertical ceñido es lo que hace que el combate se sienta bien: no golpeás lo que tenés a los pies ni lo que pasa volando arriba. Y hace que la posición importa sin necesidad de hitboxes raros.

El cono fijo se elige sobre el progresivo de rafts_combat porque el jugador puede predecir su alcance sin consultar el indicador: "si está ahí, lo alcance". El progresivo obligaba a mirar un widget para saber si algo estaba dentro, que es exactamente la fricción que queremos evitar.

Mismo check para el jugador que para las entidades, así que el autoataque y el golpe a distancia se sienten igual.

**Propuesta de valores** (ajustable):

| Arma | distance | horiz | vert | interval | bonus |
|---|---|---|---|---|---|
| Manos | 3.0 | 25° | 20° | 12 | 0.3 |
| Espada | 3.2 | 35° | 30° | 12.5 | 0.5 |
| Hacha | 3.0 | 45° | 35° | 20 | 0.6 |
| Pico | 3.0 | 35° | 30° | 16.6 | 0.4 |
| Pala | 3.0 | 35° | 30° | 16.6 | 0.4 |
| Maza | 3.4 | 30° | 40° | 20 | 0.7 |
| Tridente | 4.0 | 15° | 20° | 12.5 | 0.5 |

---

## 3. Escudo

**`[DECIDIDO]`** Reduce 100% del daño **excepto explosiones**. Tras unos golpes "cansa" al personaje y lo stunea. El hacha lo stunea al instante de un golpe.

**Un solo sistema de bloqueo**, compartido por el escudo y por la espada vanilla. La aguante son **3 golpes**.

| Estado | Comportamiento |
|---|---|
| Normal | Bloquea 100%. Cada bloqueo consume 1 golpe de aguante. |
| Agotado (0/3) | **Stun**: no bloquea, no aguanta, y hay un hueco de invulnerabilidad. Se recupera solo. |
| Desarmado (hacha) | Un golpe de hacha = stun inmediato, sin importar la aguante. |

### La aguante solo se gasta al recibir

**`[DECIDIDO]`** La aguante se consume **únicamente cuando un golpe es bloqueado**, nunca por mantener la guardia.

Esto resuelve la cobertura de la espada de forma limpia: con click derecho la espada **cubre un solo ataque y después entra en cooldown, y el cooldown no arranca hasta que recibe daño**. Traducido al sistema unificado, eso es exactamente lo que sale de la regla "la aguante solo se gasta al recibir": podés cubrirte indefinidamente con la espada, gratis, y solo pagás cuando alguien efectivamente te golpea.

Cubrirse no es un estado que cuesta, es un estado que **se resuelve**. Y por eso la espada no es un escudo peor: es un escudo de 1 golpe que se recarga solo, contra un escudo de 3 golpes que agota.

- Explosiones: daño completo, y **no** consumen aguante (el escudo no estaba trabajando).
- El agotamiento se recupera: 2s sin bloquear = aguante completa.
- **El arco de bloqueo es frontal** en ambos casos. El escudo no cubre la espalda. Flanquear funciona, y es la única forma de dealar a alguien que bloquea bien sin esperar a que se canse.

**Por qué 3 golpes y no una barra:** una barra continua obliga a leer un número y calcular cuánto falta. "Me quedan 2 de 3" es información completa sin aritmética. Además hace que el agotamiento sea un evento con peso en vez de una degradación silenciosa, que es justo lo que tiene que tener un stun para que el rival lo entienda.

---

## 4. Click derecho

**`[DECIDIDO]`** El click derecho no es un special genérico. **Cada arma define qué hace**, y la respuesta correcta para varias es "nada". No todas las armas necesitan un click derecho: el que no lo necesita es el que más fb forcefully.

### Uso de click derecho por arma

| Arma | Click derecho |
|---|---|
| Manos | — |
| **Espada** | **Cubrirse** (ver sección 3, sistema de aguante) |
| Hacha | Mandoble: AoE en el suelo, levanta por knockback |
| Pico | — |
| Pala | Apertura: empuja en arco hacia adelante |
| Maza | Arremate cargado: con 100% de carga hace el triple y empuja lejos |
| Tridente | Lanzar (vanilla) |
| Ropera | — (sin ataque especial) |
| **Alabarda** | **Multi-hit**, con penalización en el siguiente ataque |
| Martillo de guerra | — (sin ataque especial) |
| Daga | — (sin ataque especial) |
| **Shotel** | **Parry**, cooldown 5s |

3 de 5 armas nuevas no tienen click derecho. Eso es intencional: son armas de una sola decisión, se agarran y se hitmean, sin capa de pensamiento extra. El special tiene que **ganar** su espacio de acción.

**Regla:** un click derecho tiene que hacer algo que el click izquierdo no puede. Si es "más daño", no debería existir.

### Alabarda: multi-hit con coste

Click derecho golpea a **todos** los enemigos en el cono, no solo al primero. Pero el siguiente ataque izquierdo entra con **cooldown extendido**.

El coste importa: si el multi-hit no limitara nada, sería estrictamente superior al click izquierdo. Al penalizar el siguiente golpe, la decisión es "si el siguiente va a ser el que importa, no lo uso ahora".

### Shotel: parry

Click derecho entra en parry. El golpe que parried durante esa ventana **se devuelve al atacante**. Solo ese golpe: no hay búfer acumulado, no se guarda daño pendiente.

- Cooldown de **5s** entre parries.
- El parry no consume aguante del escudo: es un sistema de esquiva, no de bloqueo. Distinto, y esa diferencia es legible para el jugador.

---

## 5. Supervivencia

**`[DECIDIDO]`** Alcance: combate + supervivencia. El objetivo es que una pelea no te deje en 3 corazones sin poder recuperarte.

- Regeneración natural: cada **2s** en vez de 8s.
- Cura hasta **6** puntos de comida, no hasta 18.
- Comer se **interrumpe** al recibir un golpe (anti-heal-mobile).
- Comida líquida (caldos, miel, leche, pociones) en **20 ticks**.
- La aguanté del escudo es un recurso de combate, no de supervivencia: se regenera rápido y sin hambre.

**Por qué:** el vanilla te castiga por pelear. 8s de regen y comer Kisthmetro te deja expuesto durante toda la fight. A 2s, pelear es una decisión viable.

---

## 6. Catálogo de 5 armas nuevas × 7 tiers

**`[DECIDIDO]`** 5 armas nuevas, todas con tiers de mineral.

Tiers: madera, piedra, cobre, hierro, oro, diamante, netherite. (7 × 5 = 35 items, por datagen.)

### 1. Espada Ropera (Rapier)

Cono **muy pequeño**, daño **muy alto**, muy rápida. **No puede cargar.** Sin click derecho.

Es la respuesta al duelo de precisión: alcance corto y bien orientado, un golpe y salís. El cono chico es el costo, y a cambio el daño por segundo es alto porque la velocidad es extrema. Para acertar hay que estar alineado, que es la habilidad que el juego no te da en ningún otro lado.

### 2. Alabarda

Más lenta que la espada, daño ligeramente más alto, pero **mucho más alcance y cono**. Click derecho: **multi-hit a todos los enemigos en el cono**, con el siguiente ataque izquierdo más lento.

Arma de área. Cubre la pantalla en frente, castiga a los que se agrupan, y tiene un solo click derecho real. El alcance largo la hace buena contra el shotel, que es justo de rango corto.

### 3. Martillo de guerra

Daño y rango **significativamente más bajos** que una espada. Cada golpe consecutivo sobre un enemigo **aumenta el daño**, con ventana de **4s** entre golpes para que el escalado siga subiendo. El escalado **no tiene tope práctico** (`[DECIDIDO]`: sin tope, castigo total). El **golpe cargado ignora armadura**. Sin click derecho.

Es el arma de castigo sostenido y la única que ignora armadura, que en Minecraft es una resistedotada muy dura. El precio es claro: rango corto y daño base bajo, así que sin combo es el peor arma del set. Necesita que alguien se quede quieto. Funciona contra el autoataque que carga y dispara, y no contra el jugador que se mueve.

**Contrapunto de diseño (`[DECIDIDO]`)**: la única defensa es que el rival se aleje o te mate antes. No hay reset por recibir daño. Eso hace que el martillo sea el arma de un compromiso, no de una pelea larga.

### 4. Daga

Daño pequeño, **sumamente rápida**, **no carga**, rango corto pero **radio alto**, sin click derecho.

La versión extrema de la ropera en velocidad a cambio de daño. El radio alto la hace forgiving de apuntar: más fácil conectar sin estar perfectamente alineado. Es la que gana cuando necesitás Many weak hits y no querés parar de moverte.

### 5. Shotel

**Radio más alto entre todas las armas**, pero rango **mucho más corto** que una espada. Daño **igual al de una espada** pero más rápido. Click derecho: **parry** con cooldown de **5s** que devuelve el daño del golpe recibido. El **ataque cargado da doble**.

Compensa un rango terrible con el cono más ancho del juego y con un parry que devuelve el daño. Sin embargo, en un cono tan abierto es fácil que el parry no enganche: tiene que entrar el golpe exacto. Es el arma de precisión defensiva, no de cobertura.

**Sobre el parry (`[DECIDIDO]`)**: devuelve **únicamente el daño del golpe parriado**, no un búfer acumulado. No hay daño guardado esperando.

### Tabla resumen

| Arma | Cono | Rango | Velocidad | Carga | Click derecho |
|---|---|---|---|---|---|
| Ropera | Muy pequeño | Medio | Muy rápida | **No** | — |
| Alabarda | Muy ancho | Muy largo | Lenta | Sí | Multi-hit (penaliza el siguiente) |
| Martillo | Medio | Muy corto | Media | Sí (+ignora armadura) | — |
| Daga | Alto | Muy corto | Sumamente rápida | **No** | — |
| Shotel | **Máximo** | Muy corto | Rápida | Sí (**doble**) | Parry (5s) |

Tres de las cinco no cargan. Eso reduce el papel de la carga y sube el de la selección de arma, que es justo lo que necesitamos dado que el autoataque ya cubre la otra mitad.

---

## 6bis. Alcance excluido

**`[DECIDIDO]`** Dos cosas quedan **fuera** del sistema, a propósito:

- **Proyectiles (arco, ballesta, tridente lanzado).** Fuera del cono, con su comportamiento actual. Solo el cuerpo a cuerpo cambia. Agregarlos al cono obligaría a rebalancear todo el ranged en el mismo commit, y el riesgo no vale la pena.
- **Mobs.** No usan el cooldown duro ni el cono. Siguen con el sistema vanilla.

Consecuencia honesta de dejar los mobs fuera: una pelea contra un zombi se siente distinta a una pelea contra un jugador. El jugador tiene ritmo, alcance deliberado y aguante; el zombi no. Se acepta por ahora, pero es el candidato número uno a revisión cuando el vertical slice esté andando. Lo natural es arrancar por el zombi, que es el mob que más se parece a un jugador en comportamiento.

---

## 7. Arquitectura

**`[DECIDIDO]`** Un solo módulo por ahora. Paquete interno de librería separado, extraíble después sin romper nada.

```
com.cortes.combat
  CortesCombat.java          @Mod, registro, config
  ── núcleo (librería, sin UI) ──
  core/
    weapon/       WeaponStats (record), alcance, cooldown, carga, special
    attack/       resolución del ataque: timing, daño, hitbox
    reach/        check de cono (horizontal + vertical)
    shield/       aguante compartida escudo/espada, arco frontal, stun
    compat/       detección por capacidad
  ── red ──
  net/            payloads: carga, aguante, escalado de martillo, parry
  ── cliente ──
  client/         HUD: barra de cooldown, 3 esferitas de aguante, damage overlay
  ── datos ──
  data/           tags, recetas, stats por item (datagen)
```

**Config:** todo parámetro de gameplay en `ModConfigSpec` SERVER, con defaults que son el diseño. La config **no** cambia el juego, afina. Un jugador que nunca abrió la config juega el diseño completo.

---

## 8. Compat: por capacidad, no por mod

**`[DECIDIDO]`** No detectamos mods por nombre. Detectamos **comportamientos** y nos adaptamos.

La regla general: **el sistema lee todos sus números desde data components del item.** Cualquier mod que haga un item con nuestros components, o al que apuntemos desde config, funciona sin que escribamos una línea de código para ese mod.

Tres niveles de detección:

1. **Declarativo** — el item declara sus stats vía nuestros data components. Un mod que use nuestros components es compatible por construcción. Cubre la mayoría de los casos.
2. **Reactivo** — antes de aplicar un ataque, preguntamos: *¿otro mod ya alteró esto?* Si un `AttackEvent` fue cancelado, si el cooldown fue modificado, si el alcance está cambiado, no aplicamos nuestro override. No necesitamos saber qué mod fue, solo que hubo un cambio.
3. **Fallback de config** — para el caso raro que no entre en 1 ni 2, el usuario declara en config: *`item X: interval Y, reach Z`*. Sin código.

Lo que **no** vamos a hacer: una lista de mods en el código fuente. Eso se pudre y es la fuente clásica de bugs de compat.

---

## 9. Feedback (de damageoverlay)

**`[DECIDIDO]`** Tomamos el patrón de `neoforge-damageoverlay`, que es sólido:

- Capa GUI registrada con `registerBelowAll(rl("health"))` para que quede bajo la barra de vida.
- Viñeta roja que se intensifica al bajar de X de vida, con interpolación exponencial contra `deltaTracker.getRealtimeDeltaTicks()`.
- Flash de daño cuya intensidad escala con el daño recibido.
- Detección por delta de salud guardado (`lastKnownHealth`), no por evento.

### Indicadores, cada uno en su lugar

| Indicador | Ubicación | Qué muestra |
|---|---|---|
| Carga | Indicador vanilla, bajo la crosshair | Cuánta carga acumulada |
| Cooldown | Barra horizontal nueva, bajo la hotbar | Cuándo podés atacar (fuente de verdad) |
| Aguante | **3 esferitas sobre la crosshair**, tenues | Cuántos golpes de bloqueo te quedan |
| Escalado del martillo | Junto a la barra de cooldown | En qué golpe vas de la cadena |
| Viñeta + flash | Pantalla completa | Salud baja / daño recibido |
| Indicador direccional | Borde de la pantalla | Desde qué dirección vino el golpe |

Las armas que no cargan (ropera, daga) dejan el indicador vanilla en su estado normal de cooldown, sin bonus. La carga ausente se comunica con el **color** del cono, no con un número.

**`[DECIDIDO]`** Aguante = **3 esferitas sobre la crosshair**, visibles al llevar un escudo, en tono tenue. Cada una es un golpe que podés bloquear.

Esto es mejor que una barra continua por una razón concreta: "me quedan 2 de 3" no requiere leer un número ni calcular. Con barra hay que interpretar cuánto se movió el fill; con esferitas el estado es un conteo directo. Además, una agotación que se ve como transición gradual se siente como desgaste lento, mientras que perder una esfera de golpe se siente como un evento. Eso es justo lo que tiene que ser un stun: que el rival lo note.

Tono tenue en reposo, brillante al perder una. La capa se registra con `registerAbove` sobre la crosshair para que quede encima, no debajo como la viñeta.

Agregamos al patrón de damageoverlay:
- **Indicador direccional de daño**: cuña en el borde de la pantalla en la dirección desde la que vino el golpe. No existe en ninguno de los 3 mods.
- **Flash en la barra de cooldown** cuando se completa, para que el timing sea visible sin mirar números.

---

## 10. Cerrado

Las 6 preguntas de la v1 quedaron resueltas. Resumen:

| # | Pregunta | Respuesta |
|---|---|---|
| 1 | Cono fijo o progresivo | **Fijo** |
| 2 | Autoataque | **Carga y dispara** |
| 3 | Aguante en HUD | **3 esferitas sobre la crosshair** |
| 4 | Proyectiles | **Fuera del sistema** |
| 5 | Mobs | **Sin cambios** |
| 6 | Garrote | **Arma inexistente, tabla borrada** — el catálogo se define aparte |

## 11. Cerrado en v3

Catálogo de armas definido: **ropera, alabarda, martillo de guerra, daga, shotel**.

Ajustes de diseño que salieron de definirlas:

- **Aguante unificada** entre escudo y espada. Cubrirse no cuesta aguante hasta que un golpe entra de verdad. Eso hace que la cobertura de la espada sea una emergent consequence de una regla simple, no un caso especial.
- **Solo 2 de 5 armas nuevas tienen click derecho.** El click derecho pasó de ser "el special de cada arma" a ser una excepción que tiene que ganarse su lugar. Eso baja la complejidad y sube el peso de elegir bien el arma.
- **3 de 5 no cargan.** Menos carga en el set del que se asumiría, lo cual compensa que el autoataque ya cargue solo.

## 12. Pendiente

Nada grande. Antes de implementar, ajustar los valores numéricos concretos de la tabla de la sección 2 con los números de las armas nuevas, y decidir el escalado exacto del martillo (multiplicador por golpe y dónde deja de crecer de forma útil).
