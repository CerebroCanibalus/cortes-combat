# DESIGN.md — Cortés Combat

Diseño de combate. Estado: **propuesta v1, pendiente de aprobación**. Lo marcado `[DECIDIDO]` viene del usuario; lo demás es propuesta mía y se puede mover.

Principio rector: **un jugador nuevo nunca hace algo que le salga mal; uno experto tiene razones para dominar la mecánica.** Ninguna decisión de diseño puede castigar la falta de conocimiento.

---

## 1. El loop central

Una sola idea sostiene todo: **la barra de estado del arma hace dos trabajos a la vez.**

```
 ATAQUE          [████████████░░░░░░░░░░░░]  40% → 80% carga
                  └────只能在 100%────┘
 BARRA           ├──── cooldown ────┼──── carga extra ────┤
```

- **Tramo izquierdo (cooldown):** el arma no está lista. No se puede atacar. Se llena solo.
- **Tramo derecho (carga):** bonus de daño. Solo se acumula mientras se **mantiene** el click izquierdo.
- El punto de unión es 100% = el momento exacto en que podés atacar de nuevo.

Por qué funciona: el mismo objeto visual que te dice *"podés atacar"* te dice *"cuánto te falta para atacar más fuerte"*. Un solo lugar donde mirar. Y la parte de carga nunca resta: el daño base se conserva siempre.

**`[DECIDIDO]`** Velocidad de ataque dura (no se puede atacar a medio cooldown) y **el daño nunca baja por click rápido**. El daño es plano y predecible. El vanilla multiplica el daño por qué tan bien cronometrado hiciste el click, lo cual significa que spamear más rápido = más daño. Eso se elimina.

### Números

| Parámetro | Regla |
|---|---|
| `attackInterval` | ticks entre ataques, por arma (vanilla: espada 12.5, hacha 20, pico 16.6, maza 20, tridente 12.5) |
| `chargeFullTicks` | `attackInterval × 2` — carga completa = 2 cooldowns |
| `damage` | `base × (1 + chargeRatio × chargeBonus)` |
| `chargeBonus` | 0.5 (+50%) default, por arma |

Consecuencia intencional: una espada llega a carga completa en ~1.25s, una hacha en ~2s. Las armas lentas son las que *vale la pena* cargar, y eso se siente solo.

La decisión real en cada golpe es: **ataco ya al 50% o espero 0.6s más por el 100%.** Esa es la profundidad, y no requiere saber nada que no esté en la barra.

---

## 2. Alcance: cono por arma

**`[DECIDIDO]`** Cada arma tiene alcance horizontal y vertical distinto, en cono, hacia el frente.

Tres parámetros independientes por arma:

- `distance` — metros hacia adelante
- `horizontalAngle` — medio ángulo horizontal (grados)
- `verticalAngle` — medio ángulo vertical (grados, más ceñido)

El vertical ceñido es lo que hace que el combate se sienta bien: no golpeás lo que tenés a los pies ni lo que pasa volando arriba. Y hace que la posición importa sin necesidad de hitboxes raros.

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

Sistema de **aguante** (stamina), con tres estados:

| Estado | Comportamiento |
|---|---|
| Normal | Bloquea 100%. Cada bloqueo consume aguanté. Regenera fuera de combate. |
| Agotado | **Stun**: no bloquea, no aguanta, y hay unJhueco de invulnerabilidad. Se recupera solo. |
| Desarmado (hacha) | Un golpe de hacha = stun inmediato, sin importar la aguanté. |

Más: **el arco de bloqueo es frontal.** El escudo no cubre la espalda. Flanquear funciona, y es la única forma de dealar a alguien que bloquea bien sin esperar a que se canse.

- Explosiones: daño completo, y **no** consumen aguanté (el escudo no estaba trabajando).
- El agotamiento se recupera: 2s sin bloquear = aguanté completa.

**Por qué así:** el escudo vanilla de 100% hace que el combate defensivo sea una Binaria de sí/no. Con aguanté tenés una tercera vía (bloquear un rato y salir), que es donde aparece la lectura de opponent sin ser un sistema complejo.

---

## 4. Movimientos especiales (click derecho)

**`[DECIDIDO]`** Click derecho = un movimiento por arma, catálogo propio.

Cada uno es un ataque con las mismas reglas de cooldown, pero con forma propia. Cooldown propio, independiente del izquierdo, para que se puedan encadenar.

Propuesta:

| Arma | Especial | Efecto |
|---|---|---|
| Espada | Tajo amplio | Arco horizontal ancho, poco daño, aturde levemente |
| Hacha | Mandoble | AoE en el suelo, levanta por knockback |
| Maza | Arremate | Golpe cargado: con 100% de carga hace el triple y empuja lejos |
| Tridente | Arremate | Lanzar (vanilla) |
| Cuchillo | Retroceso | After-image, dash corto hacia atrás, recarga rápida |
| Espada larga | Barrido | 180° frontal, el más ancho del juego |
| Lanza | Estocada | Advance, el de mayor distancia, muy ceñido |
| Hachón | Gancho | Tira al enemigo hacia vos. **Contradice el bloqueo.** |
| Garrote | Enroscado | Al soltar la carga, el área de daño se duplica |

El gancho del hachón contra el escudo de alguien es la combinación que quiero que exista en el juego.

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

Cada arma existe para cubrir una silueta distinta, no para ser "la espada pero mejor":

| Arma | Rol | Identidad |
|---|---|---|
| **Cuchillo** | Speed / crit | Intervalo más corto del juego (8 ticks). Alcance mínimo. El bonus de carga más alto (+0.8) porque tenés que viver del timing. |
| **Espada larga** | Control de área | El cono horizontal más ancho (70°). Lenta (24 ticks). Cubre un flanco entero. |
| **Lanza** | Control de distancia | Distance 4.5, cono casi nulo (10°). Avance con click derecho. Castiga a los que se acercan. |
| **Hachón** | Anti-escudo | El hacha ya stunea; el hachón además **desarma**. Combo con el escudo. |
| **Garrote** | Charging | La carga al 100% **duplica el área**. Recompensa fuerte por esperar, riesgo si te interrumpen. |

**`[DECIDIDO]`** El hacha vanilla stunea al instante. El hachón es la versión extrema: stunea *y* deja al objetivo sin bloqueo.

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

Agregamos:
- **Indicador direccional de daño**: cuña en el borde de la pantalla en la dirección desde la que vino el golpe. Currently inexistente en cualquiera de los 3 mods.
- **Flash de carga en la barra del arma** cuando se completa, para que el timing sea visible.

---

## 10. open Questions

1. ¿El cono es solo horizontal-progresivo como el de rafts (que se ensancha con la distancia) o cono fijo con `distance`? Propongo fijo, es más fácil de leer.
2. ¿El autoataque (mantener click) dispara solo, o también carga? Si carga solo, el juego se plays solo.
3. La aguanté del escudo, ¿es visible en el HUD o se siente por el escudo en la mano? Propuesta: barra sutil bajo la de salud, solo cuando está en uso.
4. ¿Los proyectiles (arco, ballesta, tridente lanzado) entran en el alcance por cono o quedan en su modelo actual? Propongo: fuera del cono, con su propio balanceo.
5. ¿Los mobs usan la misma barra de cooldown? Si no,ITEM contra el jugador se siente falso.
6. ¿El garrote duplicando el área al 100% es suficiente incentivo, o necesita más reward de riesgo?
