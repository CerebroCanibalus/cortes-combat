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

## 5. Supervivencia: la comida deja de ser energía

**`[DECIDIDO]`** Alcance: combate + supervivencia. Y el cambio de fondo es que **la comida deja de ser un medidor de energía y pasa a ser un recurso de curación.**

### Qué se elimina

**`[DECIDIDO]`** La comida **no se consume** al correr, picar, golpear ni con el tiempo. Se elimina el sistema de **exhaustion** completo.

En el vanilla la comida es un sistema de dos capas: el `exhaustionLevel` sube con cada acción, la saturación se consume, y cuando se agota el exhaustion empieza a bajar la comida. Eso significa que correr cuesta. Acá se elimina entero: correr, picar y golpear **no cuestan nada** (`[DECIDIDO]`: sin costo, sin límite).

Consecuencia directa: **la comida es un solo número.** La saturación deja de usarse como mecánica — el campo sigue existiendo en `FoodData` porque es vanilla, pero no lo leemos. Un número, una regla.

### Qué se agrega

| Regla | Valor | Nota |
|---|---|---|
| Regeneración natural | 1 de vida cada **2s** | Antes 8s |
| Tope de curación | Cura hasta **6** de comida | Antes 18 |
| **Costo de curar** | Curar **consume comida** | Cada punto de vida curado baja comida |
| **Regen de comida** | La comida **se regenera sola** | Cierra el ciclo sin comer tras cada pelea |
| Comer al recibir daño | **Se interrumpe** | Pero la regen pasiva **no** se interrumpe |
| Comida líquida | **20 ticks** | Caldos, miel, leche, pociones |
| Aguante del escudo | Recurso de combate, **sin comida** | Se regenera por tiempo, no por hambre |

El modelo final es un **recurso con ciclo cerrado**: la comida se gasta al curar y vuelve sola. En una pelea podés recuperar vida, pero no infinitamente rápido. Ese es el balance fino que hay que tunear.

### Números propuestos (a validar en playtest)

| Parámetro | Propuesta | Efecto |
|---|---|---|
| `healInterval` | 40 ticks (2s) | 1 hp cada 2s |
| `healFoodCost` | 1 de comida por hp | Curarse 20 de vida cuesta 20 de comida |
| `foodRegenInterval` | 1 punto cada **4s** | Recuperas 1 hp cada 8s de forma sostenible |
| Curar 20 de vida | ~160s de espera | Hay que comer para curar rápido en una pelea |

**`[TRAMPA]`** Si `foodRegenInterval` es rápido demais, la comida deja de importar y el sistema es decorativo. Si es lento demais, pelear es un callejón sin salida, que es justo lo que este mod viene a arreglar. Es el número más importante de tunear del proyecto y solo se ajusta con playtest, no con intuición.

### Rebalanceo de curación (`[DECIDIDO]`)

Basado en Combatify, que es la referencia:

| Objeto | Vanilla | Cortés Combat |
|---|---|---|
| Poción de curación I | 4 de vida | **6** |
| Poción de curación II | 8 de vida | **12** |
| Poción de daño I | 4 de daño | **6** |
| Manzana dorada | 4 de vida | **8** |

Las pociones pasan a ser una opción **real** de supervivencia en pelea y no marginales. La manzana dorada sube más porque es la que cuesta más.

### Nota técnica

`FoodData` **no tiene patch de NeoForge** (verificado en el userdev jar): es vanilla puro. La implementación va por mixin sobre sus métodos. Los números exactos de `tickCount` y los umbrales internos hay que leerlos de las fuentes de 1.21.1 al implementar, no de memoria.

---

## 5bis. Crafteo de las armas nuevas

**`[DECIDIDO]`** Cada arma tiene una **forma de receta propia**, que refleja su silueta. No son todas iguales como la espada vanilla.

**`[DECIDIDO]`** Los tiers son los normales (madera, piedra, cobre, hierro, oro, diamante, netherite), **pero madera y piedra requieren un ingredient extra**. Los tiers de cobre en adelante son recetas limpias de mineral + palo.

La razón del ingredient extra: sin él, la ropera de madera sería estrictamente mejor que la espada de madera en DPS, y el jugador no entiende por qué. Con un extra, el tier bajo es alcanzable pero se siente como un acceso, y la espada vanilla sigue siendo competitive al principio. El arma nueva no desplaza a la vanilla, la complementa.

### Formas propuestas (ajustables en mesa de crafteo)

`X` = material del tier, `#` = palo, `E` = ingredient extra del tier bajo.

| Arma | Forma | Lectura |
|---|---|---|
| **Ropera** | `E X E` / `X X X` / ` # #` | Hoja delgada y larga, punta hacia arriba |
| **Daga** | `X E` / `E #` | Corta y en diagonal, se lee como daga |
| **Alabarda** | `E X X` / ` # X` / ` # #` | Asta larga con cabeza en ángulo |
| **Martillo** | `E X X` / `E X E` / ` # #` | Cabeza ancha y simétrica, se lee como martillo |
| **Shotel** | `X E X` / ` E X ` / ` # E ` | Cañón abierto que se dispersa |

Cada forma es una silueta reconocible. El jugador que nunca vio el mod entiende qué arma está crafteando por la forma, no por el nombre.

### Ingredient extra (propuesta)

Se usa un ingredient temático por arma, todos vanilla, todos thematic:

| Arma | Ingredient extra | Por qué |
|---|---|---|
| Ropera | Redstone | Arma de precisión y timing |
| Daga | Leather | Arma de sigilo, cuero = cuero |
| Alabarda | Iron ingot | Instrumento largo, requiere ferretería |
| Martillo | Cobblestone block | Cabeza pesada, necesita masa |
| Shotel | Copper ingot | Cañón deEconomicía, cobre por peso |

**`[PENDIENTE]`** Estos extras son propuesta mía. La regla general —"cada arma con un ingredient temático en madera y piedra"— está decidida; los ingredientes concretos se pueden cambiar.

### Estructura de datos

Las recetas se generan por **datagen**, una por arma × tier. Se usa la tag `#minecraft:<tier>_tool_materials` para el material, igual que hace Combatify, así que el ingredient de cada tier se escribe una vez y no siete.

- 35 recetas de arma (5 armas × 7 tiers).
- Las 10 de madera y piedra llevan el extra.
- Todas con `category: "equipment"` y su advancement, para que no aparezcan como recetas discoveries raras.

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

### Orden de render del HUD (verificado en 1.21.1)

El orden real de `VanillaGuiLayers` es:

```
CAMERA_OVERLAYS → CROSSHAIR → HOTBAR → JUMP_METER → EXPERIENCE_BAR
→ PLAYER_HEALTH → ARMOR_LEVEL → FOOD_LEVEL → ... → EFFECTS → ...
```

De ahí sale dónde se registra cada capa, sin adivinar:

| Capa | Registro | Razón |
|---|---|---|
| Viñeta + flash | `registerBelowAll(rl("player_health"))` | Bajo la vida, como damageoverlay |
| Barra de cooldown | `registerAbove(rl("hotbar"))` | Directamente sobre la hotbar, sin tapar nada |
| Esferitas de aguante | `registerAbove(rl("crosshair"))` | Encima de la crosshair, no encima de todo |
| Indicador direccional | `registerBelowAll(...)` | Bordes de pantalla, que no estorban a nada |
| FOV de carga | `ComputeFovModifierEvent` | No es una capa, es un evento |

`registerAboveAll` no se usa para nada del HUD: la viñeta tiene que ir debajo de la salud, no encima de todo.

---

## 9bis. Vista: viewmodel, partículas, cámara y sonido

### Restricción técnica de 1.21.1 (`[VERIFICADO]`)

**1.21.1 no tiene sistema de animaciones de item.** Eso llega en 1.21.4 con los archivos `equippable`. En 1.21.1 el swing del viewmodel es código hardcodeado en `ItemInHandRenderer`.

Consecuencias directas:
- **Toda animación de viewmodel se hace con mixin**, no con JSON. No hay atajo.
- **`RenderHandEvent` existe pero no sirve para esto**: es cancellable, no expone la `PoseStack` de forma utilizable para transformar. Sirve para tapar la mano, no para animarla.
- Para animar hay que meter mixin en `ItemInHandRenderer` / `ItemInHandLayer` y reemplazar la transformación del swing.

Combatify hace exactamente esto (`ClientAttackAnimMixin`, `ItemInHandMixin`, `ClientAttackAnimMixin`), así que hay referencia directa.

### Viewmodel por arma

**`[DECIDIDO]`** Animación distinta por arma. Cada arma tiene su propio perfil de swing, definido como datos (curva de rotación, ángulo, timing) y no como código.

- **Un solo mixin**, tabla de perfiles por item. Cinco mixes serían cinco lugares donde arreglar el mismo bug.
- Los perfiles se declaran vía data component, así que un mod externo puede definir el suyo sin tocar el código.
- La curva base es la misma para todas; lo que cambia es la forma. Una espada corta, el martillo hace un arco pesado y lento, la daga es un tajo seco y rápido.

Es la parte del trabajo con más riesgo técnico del proyecto, porque cada ajuste es a ojo hasta que se siente bien. Por eso va al final, no en el vertical slice.

### Partículas

**`[DECIDIDO]`** Partículas custom **2D**, animadas y no animadas, solo para **ataques, parries y ataques especiales**.

En 1.21.1 las partículas 2D se registran con `RegisterParticleProvidersEvent` (mod bus, lado cliente). Las animadas usan la hoja de sprites del atlas; las no animadas, una textura simple.

Ninguna partícula se dispara por "?se recibió daño". Todas disparan por **eventos con significado de juego**:

| Evento | Partícula | Por qué |
|---|---|---|
| Impacto de golpe cargado | Estallido mayor, más denso | El jugador tiene que ver que cargó |
| Impacto normal | Estallido chico | Confirmación mínima |
| Parry exitoso | Aro que se cierra + destello | Es el momento más tense del juego |
| Bloqueo de escudo | Chispa de escudo | Confirma el aguante gastado |
| Perdida de aguante | Estallido de ruptura | El stun tiene que verse |
| Golpe de alabarda (multi-hit) | Línea de barrido | El multi-hit es lo que define el arma |
| Escalado del martillo | Onda que crece con el streak | El combo tiene que ser visible |
| Ignorar armadura | Destello de fractura | Armadura rota |

### Cámara

**`[DECIDIDO]`** Solo **FOV** y viñeta de daño, curación y vida baja.

- **FOV de carga:** se cierra mientras se carga y se abre de golpe al soltar. Va por `ComputeFovModifierEvent`, que existe en 1.21.1 y es limpio. El `fovEffectScale` vanilla ya está respected por ese evento, así que un jugador que lo bajó por mareo lo sigue teniendo apagado.
- **Viñeta de daño, curación y vida baja:** el patrón de damageoverlay, extendido con dos señales más.

**Sin screen shake y sin slow motion.** La cámara quieta: el feedback viene de sonido, partícula y viewmodel. Es una decisión de legibilidad, y evita el problema de mareo y de desync en multiplayer.

Nota sobre las tres viñetas: daño, curación y vida baja comparten la misma textura y el mismo color base para que se lean como un sistema. Se diferencian por **dirección del color**, no por tres texturas distintas.

### Sonido

**`[DECIDIDO]`** Ambos: reusar vanilla **y** soundscape propio.

- **Vanilla reusado** con variación de pitch: lo trivial ya suena bien y hace juego coherente con el resto de Minecraft.
- **Soundscape propio** para lo que necesita identidad: carga completa, parry, agotamiento de aguante, escalado del martillo. Son los cuatro eventos que el jugador tiene que reconocer sin mirar la pantalla.

El soundscape propio es la parte que más tiempo de producción se lleva: grabar, editar, empaquetar. Por eso se hace al final y no bloquea el vertical slice. La arquitectura de sonido queda preparada desde el principio para que entre después sin refactor.

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

## 13. Capa visual: cerrado en v4

- **Viewmodel por arma:** perfiles de swing como datos, un solo mixin. Es la parte con más riesgo técnico, va al final.
- **Partículas:** custom 2D (animadas y no animadas), disparadas por evento de gameplay, nunca por "?recibí daño".
- **Cámara:** solo FOV de carga + viñeta de daño, curación y vida baja. Sin shake ni slow motion, por legibilidad.
- **Sonido:** vanilla reusado con variación de pitch + soundscape propio para los 4 eventos que el jugador tiene que reconocer sin mirar.
- **Orden de render del HUD verificado** en las fuentes de 1.21.1, así que la posición de cada capa no es a ojo.

Restricción de versión que condiciona todo lo de arriba: **1.21.1 no tiene animaciones de item.** Se llega en 1.21.4. Toda animación de viewmodel es mixin obligatorio.

## 14. Crafteo y supervivencia: cerrado en v5

- **Crafteo:** forma única por arma, 5 formas distintas que se leen por silueta. Tiers normales, con **ingredient extra en madera y piedra** para que el arma nueva no desplace a la vanilla al principio.
- **Supervivencia:** la comida deja de ser energía. Se elimina el **exhaustion** entero — correr, picar y golpear no cuestan nada. La comida pasa a ser recurso de curación con ciclo cerrado: se gasta al curar y se regenera sola.
- **Rebalanceo de pociones y manzana dorada** siguiendo los números de Combatify.

`[TRAMPA PRINCIPAL]` El número `foodRegenInterval` es el más importante del proyecto. Rápido → la comida es decorativa. Lento → pelear es un callejón sin salida, que es justo lo que el mod viene a arreglar. Solo playtest lo resuelve.

## 12. Pendiente

Nada grande. Antes de implementar:
1. Ajustar los valores de la tabla de la sección 2 con los números de las armas nuevas.
2. Decidir el escalado exacto del martillo (multiplicador por golpe, dónde deja de crecer de forma útil).
3. Confirmar o cambiar los ingredients extra propuestos de la sección 5bis.
4. Verificar contra las fuentes de 1.21.1 los umbrales internos de `FoodData` antes de escribir el mixin.

Lo visual y los recipes no bloquean el gameplay: el vertical slice puede ir con sonido placeholder, sin viewmodel y sin recetas, y todo eso se agrega encima.
