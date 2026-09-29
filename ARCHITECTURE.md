# ARCHITECTURE.md — Cortés Combat

Arquitectura del mod. **Todo lo que está acá es propuesta mía**, pendiente de tu aprobación. `DESIGN.md` dice *qué* hace el juego; este dice *cómo* está construido.

---

## Principio rector

**Todo el estado mutable del combate vive en attachments. Nunca en mixins con campos.**

Esa es la decisión que sostiene el resto. Un mixin con un campo temporal es una bomba: no se serializa, no se limpia al morir, no se copia al respawn, y dos jugadores en el mismo tick lo comparten mal. Un attachment es un objeto con ciclo de vida definido por NeoForge.

---

## 1. Estado: todo por attachment

`net.neoforged.neoforge.attachment.AttachmentType` (verificado en 21.1.250). Permite colgar datos de cualquier `IAttachmentHolder` — jugador, entidad, itemstack — con serialización, sync al cliente y copiado al morir configurables.

### Attachment de jugador

| Attachment | Guarda | Por qué attachment |
|---|---|---|
| `CHARGE` | ticks acumulados de carga + si el click está mantenido | Es estado de combate del jugador, no del item |
| `GUARD_STAMINA` | 0-3, aguante del escudo/espada | Debe sobrevivir logout yverse al respawn |
| `COOLDOWN` | tick del próximo ataque permitido | El servidor es la fuente de verdad |
| `HAMMER_STREAK` | golpe actual de la cadena + tick del último | La ventana de 4s necesita reloj propio |
| `PARRY_READY` | tick hasta el próximo parry | Cooldown de 5s del shotel |

`COOLDOWN` y `PARRY_READY` van **en el servidor y se sincronizan al cliente**. `CHARGE` es Interesting: el cliente predice y el servidor **valida**. Nunca al revés — un cliente no puede decir "cargué 40 ticks", solo "hice clic ahora".

### Regla dura de arquitectura

> **El cliente nunca decide nada.** El cliente calcula para mostrar, y manda intención. El servidor resuelve. Si un valor afecta daño, cooldown o aguante, sale del servidor.

Esto no es solo anti-cheat. Es lo que hace que el mod funcione con lag sin que el combate se desincronice: el jugador ve la barra fluida porque el cliente predice, pero el golpe que se aplica es el que el servidor calculó.

---

## 2. Datos: todo por data component

Ningún número de gameplay está hardcodeado en Java. El item **declara** sus stats.

### Componentes propios

| Componente | Guarda |
|---|---|
| `cortescombat:weapon_profile` | Plantilla de comportamiento: swing, ratios, flags |
| `cortescombat:reach_profile` | `distance`, `horizontalAngle`, `verticalAngle` |
| `cortescombat:charge_profile` | booleano `canCharge`, `bonus`, ticks de carga completa |
| `cortescombat:special` | Tipo de click derecho + sus parámetros |

Cuatro componentes, no uno giant. La razón: un mod externo quiere cambiar el alcance de un item **sin** romperle la carga. Con componentes separados, cada aspecto se overridea por separado.

### El flag `canCharge`

Las armas que no cargan (ropera, daga) llevan `canCharge = false`. **No es un caso especial en el código**: el sistema de carga consulta el componente y no corre. Agregar el próximo arma que no carga es un item, no una rama.

### `WeaponProfile` de referencia única

Un `WeaponProfile` declara el **molde** del arma (cómo se comporta), y los data components declalan los **números**. Un perfil por arma, no uno por tier.

---

## 3. Ataque: reemplazar el sistema vanilla

**`[PROPUESTA]`** El flujo de ataque vanilla se **reemplaza entero**. Esto es lo que pediste y es correcto para este mod: el vanilla calcula el daño con `getAttackStrengthScale`, que es exactamente la mecánica que queremos eliminar.

### Flujo nuevo

```
click izquierdo
    │
    ├─► ¿hay objetivo en el cono?  ── sí ──► resolver golpe
    │       (reach/)                      (attack/)
    │            │                          │
    │            │                          ├─► daño = base × (1 + carga × bonus)
    │            │                          ├─► aplicar escalado (martillo)
    │            │                          └─► knockback según carga
    │            │
    │            no ──► swing vacío, la carga igual se mantiene
    │
    └─► arrancar COOLDOWN (servidor)
```

### Por qué reemplazar y no parchear

El vanilla hace cuatro cosas que este mod **no quiere**:

1. `getAttackStrengthScale` premia el timing del click → eliminado por diseño.
2. El alcance es un raycast único al crosshair → reemplazado por cono.
3. El cooldown se resetea al cambiar de item → no queremos eso.
4. El daño se calcula en el item (`Item#hurtEnemy`) → lo queremos en el sistema central.

Parchear las cuatro sería cuatro mixins acoplados. Reemplazar es **un punto de entrada** y reglas limpias.

### Prioridad: nosotros, no ellos

**`[DECIDIDO]`** Cortés Combat tiene **prioridad sobre cualquier otro mod** que toque el flujo de ataque. No cedemos el turno.

Reemplazar el flujo vanilla rompe compat con mods de combate. Eso está **asumido a propósito**: es un mod de combate, y un mod que reescribe el ataque está reescribiendo lo que nosotros somos.

**Detección por blocklist, no por heurística.** No se puede preguntar "otro mod hookeó este método" — los mixins de los demás simplemente corren y no dejan rastro consultable. La detección viable es una lista explícita de mods incompatibles, chequeada con `ModList` al iniciar.

**Fallo controlado, no crash.** Si se detecta un mod incompatible, el mod **se niega a cargar** con un mensaje que diga qué pasó:

```
Cortes Combat no puede correr junto a <mod>.
Ambos reescriben el flujo de ataque de Minecraft.
Desactivá <mod> para jugar con Cortés Combat.
```

**No es un crash con stack trace.** Un crash parece un bug nuestro y deja al jugador sin saber qué hacer. Un refusal con el nombre del mod y la instrucción es accionable. La diferencia entre "se rompió" y "esto no es compatible, sacá X" es la diferencia entre una queja y un bug reportado.

### La contradicción que esto crea, y cómo se resuelve

`DESIGN.md` y `ARCHITECTURE.md` dicen "vetado: lista de mods en el código". Y esta decisión **es** una lista de mods en el código.

La distinción que lo resuelve:

| Lista | Propósito | Mantenimiento |
|---|---|---|
| **Blocklist** (esta) | **Negarse a correr** | Corta, estática, 5-10 entradas. No crece. |
| Compat list (vetada) | **Hacer que funcione con ellos** | Crece sin límite, se pudre. |

Una blocklist no se pudre: si un mod deja de ser incompatible, se borra una línea y ya. No hay que mantener código de integración para nadie.

### Dos cosas que esta decisión NO cambia

- **Las armas de otros mods se siguen convirtiendo** (sección 12). Convertir *items* y pelear contra *sistemas de combate* son cosas distintas.
- **La derivación automática sigue funcionando.** Un mod de armas con stats razonables entra sin código nuestro.

> Traducción: **convertimos sus armas, nos negamos a pelear con sus sistemas.**

---

## 4. Alcance: `reach/`

Una clase, una responsabilidad:

```java
static boolean inCone(LivingEntity source, Entity target, ReachProfile reach)
```

- Vector forward del atacante, vector hacia el target.
- Angle horizontal y vertical **por separado** (`Mth.atan2`).
- La misma función para jugador y mobs → el autoataque y el golpe a distancia se sienten iguales.

Es la clase más testeable del proyecto: geometría pura, sin estado. Le van a entrar gametests.

---

## 5. Alcance de los cambios vanilla

| Sistema vanilla | Qué hacemos | Riesgo |
|---|---|---|
| `FoodData` | Reemplazo total del tick: sin exhaustion, regen nueva | **Alto** — toca supervivencia |
| `Player#attack` / `Item#hurtEnemy` | Reemplazo total | Medio — es el corazón del mod |
| `LivingEntity#isBlocking` / escudo | Reemplazo: aguante + arco frontal | Medio |
| `getAttackStrengthScale` | Se ignora completamente | Bajo |
| Mobs | **Sin tocar** | — |
| Proyectiles | **Sin tocar** | — |
| Comida (`Item#finishUsingItem`) | Solo el timing líquido a 20 ticks | Bajo |

`FoodData` es el cambio más caro y el que más conviene aislar. Va **último**, no primero, y en un commit propio para que si se rompe sea bisectable.

---

## 6. Orden de implementación

Cada paso es un commit que compila y corre. **Nunca una capa incompleta en `main`.**

| # | Paso | Depende de | Riesgo |
|---|---|---|---|
| 1 | Esqueleto: build, `mods.toml`, mixins.json, `@Mod` vacío | — | Bajo |
| 2 | Registros: componentes, attachments, tags, items | 1 | Bajo |
| 3 | `reach/` + gametests de geometría | 2 | Bajo |
| 4 | Cooldown duro + `COOLDOWN` attachment | 2,3 | Medio |
| 5 | Flujo de ataque nuevo (reemplazo del vanilla) | 4 | **Alto** |
| 6 | Carga + `CHARGE` attachment | 5 | Medio |
| 7 | Barra de cooldown en HUD | 4,6 | Bajo |
| 8 | Aguante escudo/espada + esferitas | 5 | Medio |
| 9 | Armas nuevas + datagen + recetas | 2 | Bajo |
| 10 | Especiales (alabarda, parry) | 5,6 | Medio |
| 11 | Supervivencia (`FoodData`) | 5 | **Alto** |
| 12 | Supervivencia HUD (viñeta, direccional) | 11 | Bajo |
| 13 | Partículas | 5,6,8 | Bajo |
| 14 | Viewmodel | 5 | **Alto** |
| 15 | Sonido | 5,6,8,10 | Bajo |

**`[REGLA]`** El orden no es por dificultad, es por dependencia. El paso 3 va antes que el 5 porque la geometría del cono es lo único que se puede testear sin nada más andando.

---

## 7. Testabilidad

Tres niveles, y el más importante es el primero:

1. **Gametests de geometría** (`reach/`) — puro, sin mundo, sin jugador. Enganchable en `runGameTestServer`.
2. **Gametests de reglas** — daño, carga, aguante, con un jugador spawneado.
3. **Playtest manual** — balance. Nada de esto se testea, se juega.

El `foodRegenInterval` y el escalado del martillo **no se testean**, se tunean jugando. No escribir un test que verifique que un número es "equilibrado" es una pérdida de tiempo.

---

## 8. Compat: tres niveles, ya definidos

Recordatorio de la sección 8 de `DESIGN.md`, con lo que significa a nivel de código:

| Nivel | Mecanismo | Cubre |
|---|---|---|
| Declarativo | Item con nuestros data components | Mods que adoptan nuestra API |
| **Derivación automática** | Resolver por atributos del item | **Todo mod de armas, sin código nuestro** |
| Reactivo | `AttackEvent` cancelado → no aplicamos | Mods que reescriben el ataque |
| Config | Override por item en JSON | El jugador decide |

**Lo que sigue vetado:** una lista de mods en el código. Se pudre.

El nivel de **derivación automática** es nuevo y es el que resuelve la compat real. Ver sección 12.

---

## 9. Lo que NO vamos a hacer

Estas decisiones son tan importantes como las de hacer:

- **No** un mixin que reescriba la mitad de una clase de vanilla. Cada mixin es un `@Shadow` de algo que puede cambiar.
- **No** un bus de eventos propio. NeoForge ya tiene uno; agregar otro es bugs esperando.
- **No** lógica de gameplay en clases cliente. El cliente dibuja, no decide.
- **No** `static mutable state` para nada que afecte gameplay. Los attachments existen para eso.
- **No** acceso directo a campos privados de vanilla sin accessor mixin. Y el accessor se anota como tech debt.
- **No** un archivo único de config autogenerada. Regenerar pisaría los cambios del jugador. Ver sección 12.
- **No** ruido aleatorio por frame en el viewmodel. El shake se siembra por golpe, o el mismo golpe se ve distinto cada vez.
- **No** un crash con stack trace por incompatibilidad. Se niega a cargar con un mensaje accionable. Ver sección 3.

---

## 10. Decisiones: APROBADAS

Aprobadas por el usuario. La 4 se aprovou **con una modificación**: la prioridad es nuestra y el mod se niega a cargar ante mods incompatibles, en vez de ceder el turno. Ver sección 3.

| # | Decisión | Estado |
|---|---|---|
| 1 | Reemplazar el flujo de ataque vanilla entero | ✅ Aprobada (con prioridad + blocklist) |
| 2 | `FoodData` al final, en commit propio | ✅ Aprobada |
| 3 | Attachments con predicción del cliente | ✅ Aprobada |
| 4 | Viewmodel al final (paso 14 de 15) | ✅ Aprobada |
| 5 | Resolver en 4 capas, override del jugador gana | ✅ Aprobada |
| 6 | Config procedural en dos archivos JSON | ✅ Aprobada |

**Pendiente de la blocklist:** la lista concreta de mods incompatibles se arma cuando sepamos cuáles son los que rompen. Se arranca vacía y se llena con reportes.
---

## 11. Framework de viewmodel

**`[PROPUESTA]`** Un framework chico para animar el item en primera persona **desde código**: shake, easing, timelines y composición por capas.

Restricción de versión que lo obliga: en 1.21.1 no hay animaciones de item (`[VERIFICADO]`). Sin esto habría que escribir mixes a mano por cada arma. Con el framework, **un perfil por arma** y el mixin es uno solo, fijo.

### El principio: el framework es código, los perfiles son datos

Se escribe la machinery **una vez** y cada arma declara una línea de tiempo. Si el framework no puede expresar una animación, se agrega una función nueva — no se escribe un mixin nuevo.

### Estructura

```
client/viewmodel/
  ViewmodelPipeline.java   compone todas las capas
  Channel.java             interfaz: float value(float t, Ctx ctx)
  motion/                  una línea de tiempo
    Motion.java            canal + inicio + duración + easing + amplitud
    Timeline.java          lista ordenada + evaluación
  easing/
    Easing.java            interfaz + registro
    Curves.java            linear, inOutCubic, back, elastic, bounce, expo
  noise/
    ShakeNoise.java        ruido determinista
```

### Composición por capas (aditiva)

```
final = vanilla(t)
      + profile(t)      ← la animación del arma
      + shake(t)        ← ruido
      + fov(t)          ← FOV de carga
```

Cada capa es un `Channel`. Sumar es barato, y las capas se pueden **desactivar por config**: un jugador con mareo apaga `shake` y `fov` y le queda la animación sola.

### Easing

Vanilla `Mth` solo trae lineal, cuadrática y cúbica, que no alcanzan para un golpe que tiene que sentirse con peso. El framework trae las curvas de Penner estándar:

`linear` · `inOutCubic` · `back` (sobrepasa y vuelve — un golpe que se frena) · `elastic` · `bounce` · `expo` (impacto instantáneo, la más útil para armas)

`back` y `expo` son las dos que hacen que un golpe se sienta distinto según el arma. La alabarda usa `back` porque el mandoble pesa; la daga usa `expo` porque corta.

### Shake determinista

**`[REGLA]`** El ruido es **determinista y sembrado por golpe**, no aleatorio por frame.

Si el shake usa `Math.random()` por frame, tiembla de forma distinta cada vez que mirás el mismo golpe. Con ruido sembrado (`sin(f·t + φ)` sumado en varias frecuencias) el mismo golpe se ve **idéntico** cada vez. Eso es lo que lo hace sentir como animación y no como ruido gráfico.

Se suma 3-4 senos con frecuencias y fases distintas. Barato, sin tablas de ruido, y suficiente.

### Un perfil se ve así

```java
Timeline.of(
    Motion.of(Channel.ROT_X, 0f,  4,  Easing.expo(),  -1.2f),  // el golpe
    Motion.of(Channel.POS_Y, 2f,  6,  Easing.back(),  -0.15f),  // el levantamiento
    Motion.of(Channel.SHAKE,  1f,  8,  Easing.expo(),   0.4f)   // el temblor
)
```

Un arma con `Timeline.EMPTY` no hace nada, y eso no es un caso especial: el pipeline corre igual.

---

## 12. Conversión de armas + config procedural

**`[PROPUESTA]`** El problema real: el mod tiene 30 armas propias y el jugador tiene 300 items de otros mods. No todos son armas, y los que lo son necesitan alcance, carga y especial.

**La respuesta no es "qué mods soportamos". Es "cómo convierte el sistema cualquier item".**

### El resolver: una función, cuatro capas de prioridad

```
WeaponStats resolve(ItemStack stack)
  1. data components presentes   → declarativo (nuestro, y mods que adoptan la API)
  2. override en config          → lo que el jugador escribió
  3. derivación por atributos    → automático para CUALQUIER item
  4. default                     → manos
```

El orden importa y está documentado. El jugador escribiendo en config **gana** sobre lo automático; lo automático gana sobre el default.

### El paso 3 es la conversión, y es automática

No hay código por mod. La derivación hace tres cosas:

1. **Lee los atributos del item**: attack damage y attack speed salen de su propio `ItemAttributeModifiers`. Un mod que seteó 8 de daño y 1.6 de velocidad ya está balanceado solo.
2. **Elige un perfil por tag**: `minecraft:swords` → perfil espada, `minecraft:axes` → perfil hacha, y así.
3. **Escala el alcance por la velocidad**: un arma rápida hereda alcance corto, una lenta alcance largo. La coherencia sale sola.

> Un mod de armas con stats razonables entra a nuestro sistema **sin una línea de código nuestro**. Esa es toda la compat que hace falta, y no depende de que sepamos que el mod existe.

### La config procedural: generada + override

Dos archivos, con precedencia clara:

| Archivo | Quién lo toca | Contenido |
|---|---|---|
| `config/cortescombat/generated_weapons.json` | **Lo escribe el mod.** No editar a mano. | Todos los items detectados y su perfil asignado |
| `config/cortescombat/weapon_overrides.json` | **El jugador.** | Solo lo que quiere cambiar |

Se fusionan: **override gana siempre**. El archivo generado lleva un header que dice que se regenera, para que nadie edite el equivocado.

**Por qué dos archivos y no uno:** si fuera uno solo, regenerarlo pisaría lo que el jugador escribió. Ese es el bug clásico de las configs procedurales.

**Cuándo se regenera:** al primer arranque, y cuando cambia el registro de items (se compara un hash). No en cada load.

### Qué puede cambiar el jugador

Cualquier cosa, sin código:

```json
{
  "othermod:ruby_sword": {
    "profile": "alabarda",
    "reach": { "distance": 4.2, "horizontal": 55, "vertical": 35 },
    "canCharge": true,
    "chargeBonus": 0.7,
    "special": "multi_hit"
  }
}
```

Ese ítem pasa a tener alcance de alabarda, con su propio special. El jugador puede darle a un arma de otro mod un comportamiento que **ni existe en nuestro catálogo**.

### Detección: qué entra y qué no

- **Entra** si está en un tag de arma cuerpo a cuerpo **y** tiene atributo de attack damage.
- **No entra** si no tiene daño (mesas, capas, comida, bloques).
- **Se avisa al jugador** al arrancar: *"N items convertidos, M sobrescritos por tu config"*. Sin eso es magia invisible.

### Nota de API

La config va en **JSON plano**, no en `ModConfigSpec`. Motivo: no pude verificar en el cache local si `ModConfigSpec` soporta mapas (las sources de FML no están descargadas), y **un archivo que se regenera necesita Priorities explícitas y estables** — una tabla autogenerada dentro de un TOML de Gradle es frágil. JSON nos da precedencia clara, y es independiente de la API.

`ModConfigSpec` se queda para lo que sí es estático: los números globales de balance (`healInterval`, `foodRegenInterval`, escalado del martillo).
