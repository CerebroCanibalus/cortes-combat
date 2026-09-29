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

La aguante son **3 golpes**, no una barra continua. Ver *Feedback* más abajo para cómo se comunica.

| Estado | Comportamiento |
|---|---|
| Normal | Bloquea 100%. Cada bloqueo consume 1 golpe de aguante. |
| Agotado (0/3) | **Stun**: no bloquea, no aguanta, y hay un hueco de invulnerabilidad. Se recupera solo. |
| Desarmado (hacha) | Un golpe de hacha = stun inmediato, sin importar la aguante. |

Más: **el arco de bloqueo es frontal.** El escudo no cubre la espalda. Flanquear funciona, y es la única forma de dealar a alguien que bloquea bien sin esperar a que se canse.

- Explosiones: daño completo, y **no** consumen aguante (el escudo no estaba trabajando).
- El agotamiento se recupera: 2s sin bloquear = aguante completa.

**Por qué 3 golpes y no una barra:** una barra continua obliga a leer un número y calcular cuánto falta. "Me quedan 2 de 3" es información completa sin aritmética. Además hace que el agotamiento sea un evento con peso en vez de una degradación silenciosa, que es justo lo que tiene que tener un stun para que el rival lo entienda.

---

## 4. Movimientos especiales (click derecho)

**`[DECIDIDO]`** Click derecho = un movimiento por arma, catálogo propio.

Cada uno es un ataque con las mismas reglas de cooldown, pero con forma propia. Cooldown propio, independiente del izquierdo, para que se puedan encadenar.

Propuesta:

Para las armas vanilla:

| Arma | Especial | Efecto |
|---|---|---|
| Manos | Empuje | Empuje corto con la mano abierta, sin daño, aturde un tick |
| Espada | Tajo amplio | Arco horizontal ancho, daño bajo, aturde levemente |
| Hacha | Mandoble | AoE en el suelo, levanta por knockback |
| Pico | Estocada | Avance corto, el único pickaxe que se mueve |
| Pala | Apertura | Golpe al suelo que empuja en arco hacia adelante |
| Maza | Arremate | Golpe cargado: con 100% de carga hace el triple y empuja lejos |
| Tridente | Lanzar | Vanilla, sin cambios |

Los especiales de las 5 armas nuevas se definen cuando se defina el catálogo (ver sección 6).

Regla de diseño del special: tiene que hacer algo que **el click izquierdo no puede hacer**, no más daño. Si un special es solo "más daño", es un click derecho inútil.

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

> **BORRADO — tabla inventada.** El usuario pidió "5 armas nuevas" pero nunca las nombró. Los nombres y roles de esta tabla eran propuesta mía sin respaldo. Las 5 armas se definen aparte; abajo queda solo el criterio.

**Criterio para elegir las 5:** cada una cubre una silueta distinta, no "la espada pero mejor". Conviene diagramar el set completo (vanilla + nuevas) y ver qué roles quedan vacíos: speed, control de área, distancia, anti-escudo, y lo que surja.

**Criterio para el special de cada arma nueva:** tiene que hacer algo que el click izquierdo no puede, y no solo más daño.

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
    weapon/       WeaponStats (record), alcance, cooldown, special
    attack/       resolución del ataque: timing, daño, hitbox
    reach/        check de cono (horizontal + vertical)
    shield/       aguanté, estados, arco frontal
    compat/       detección por capacidad
  ── red ──
  net/            payloads: carga, target en cono, estado de escudo
  ── cliente ──
  client/         HUD (barra, indicador de cono, damage overlay)
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
| Aguante del escudo | **3 esferitas sobre la crosshair**, tenues | Cuántos golpes de bloqueo te quedan |
| Viñeta + flash | Pantalla completa | Salud baja / daño recibido |
| Indicador direccional | Borde de la pantalla | Desde qué dirección vino el golpe |

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

## 11. Pendiente

Definir las 5 armas nuevas. Es la única decisión grande que falta antes de arrancar la implementación.
