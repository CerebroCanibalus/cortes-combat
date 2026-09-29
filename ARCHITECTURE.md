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

### El límite de esta decisión

> Reemplazar el flujo vanilla **rompe compat con mods que reescriben el ataque**. Está asumido. El mitigador es que los hooks de NeoForge se siguen disparando: si otro mod cancela el `AttackEvent`, nosotros no aplicamos daño. No es que ignoremos a los demás, es que **cedemos el turno**.

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
| Declarativo | Item con nuestros data components | La mayoría de los mods de armas |
| Reactivo | `AttackEvent` cancelado → no aplicamos | Mods que reescriben el ataque |
| Config | Override por item en `ModConfigSpec` | El caso raro |

**Lo que sigue vetado:** una lista de mods en el código. Se pudre.

---

## 9. Lo que NO vamos a hacer

Estas decisiones son tan importantes como las de hacer:

- **Noicros mixin** que reescriba la mitad de una clase de vanilla. Cada mixin es un `@Shadow` de algo que puede cambiar.
- **No** un bus de eventos propio. NeoForge ya tiene uno; agregar otro esbugs esperando.
- **No** lógica de gameplay en clases cliente. El cliente dibuja, no decide.
- **No** `static mutable state` para nada que afecte gameplay. Los attachments existen para eso.
- **No** acceso directo a campos privados de vanilla sin accessor mixin. Y el accessor se anota como tech debt.

---

## 10. Decisiones que necesitan tu aprobación

1. **Reemplazar el flujo de ataque vanilla entero** (sección 3). Rompe compat con mods de combate. El diseño lo pide, pero es la decisión más grande del proyecto.
2. **`FoodData` al final, en commit propio.** Caro de implementar, caro de revertir.
3. **Los attachments con predicción del cliente.** Más código que sin predicción, pero sin desync.
4. **Viewmodel al final** (paso 14), aunque sé que es la parte que más se nota si falta.
