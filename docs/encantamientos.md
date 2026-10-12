# Encantamientos — documento canónico

> **Este es el único documento de encantamientos.** Absorbió y reemplaza a
> `encantamientos-armas-nuevas.md` (la propuesta v2: matriz de herencia, onda de filo, genéricos) y a
> `encantamientos-unicos.md` (el plan por arma). Cuando algo de esos dos contradiga esto, **manda
> esto**: entre los dos había **nueve nombres para mecánicas distintas**, y esa era la primera cosa a
> limpiar.
>
> **Estado (2026-10-11):** **35 encantamientos implementados** y en la mesa. Además, dos arreglos de
> cliente: **la animación de la azada** (iba hacia la derecha) y **Mecha Corta**, que resultó no
> necesitar arte — ver §6.
>
> **Retirada ya está**, y fue el primero de los cuatro caros: el lunge invertido no tocó el sistema de
> autoridad del movimiento, porque **la marcha atrás es el mismo impulso con el signo cambiado** y el
> cubo que barre la marcha ya era sin dirección — ver su fila y §9.
>
> **Faltan 3, y los tres necesitan una entidad propia y un camino de munición que el mod todavía no
> tiene:** **Pala Antimaterial**, **Rey de las Moscas** y **Mungus**. Sus ids, sus claves y sus
> **tablas de números ya están escritas** en `CombatEnchantRules` (incluida la potencia **12** del
> Mungus), pero **no están en `IDS`** — y `IDS` es lo que hace que un test pida el archivo, el tag, el
> idioma y la forma del campo. Un encantamiento declarado que no se puede conseguir es un canal muerto
> con el build verde, y eso ya se pagó una vez en esta ronda con `corte_gemelo`.
>
> **Los siete últimos, hechos y con sabotaje verificado:** Paliza, Sentencia, Trabazón, Gripa,
> Corte Gemelo (volvió, ahora con su reloj), Capacitor y Retirada.

---

# 1 · Las reglas duras

Ningún encantamiento puede romper esto:

- **La carga nunca resta.** El piso se fuerza **en código**, no se confía al dato.
- **La guardia es una postura**: con la guardia arriba no se golpea.
- **El cliente nunca decide.** El servidor resuelve y manda estado.
- **La velocidad de ataque es dura**: no se ataca a medio cooldown.
- **Ningún número de gameplay hardcodeado** fuera de una clase pura y testeable.
- **Los gates son por capacidad o perfil**, nunca por lista de ids: un weapon pack que declare un
  arma "tipo shotel" hereda sus encantamientos.

# 2 · El vocabulario (1.21.1, verificado)

| Campo / tag | Qué hace |
|---|---|
| `weight` | frecuencia relativa en la mesa. Vanilla: Sharpness 10, Smite 5, Knockback 5, Fire Aspect 2, Mending 2 |
| `max_level` | el tope |
| `min_cost` / `max_cost` | la banda de niveles de la mesa |
| `anvil_cost` | niveles que cobra el yunque |
| `slots` | dónde tiene que estar el ítem (`mainhand` en todos los nuestros) |
| `supported_items` / `primary_items` | en qué ítems puede estar / cuáles son "primarios". **Un tag, o una lista de ids directos — nunca una lista de tags** |
| `#minecraft:in_enchanting_table` | **lo lee la mesa** (`EnchantmentMenu:207`). Sin esto, no sale nunca |
| `#minecraft:treasure` | nominal (no lo lee nadie) |
| `#minecraft:tradeable` | los aldeanos |
| `#minecraft:on_random_loot` | los cofres |

**`"treasure"` NO es un campo del JSON en 1.21.1** -- verificado: ninguno de los 42 encantamientos de
vanilla lo declara. Y la mesa pasa **sólo** el tag `in_enchanting_table` a la selección.

**Los tags mergean aditivamente** (sin `replace`), así que extender uno de vanilla suma, no pisa.

# 3 · Los 6 que ya existen

| id | Arma (gate) | Niv | Efecto | weight | tags |
|---|---|---|---|---|---|
| **`steel_reflex`** Reflejo de Acero | shotel (`profile/sabre`) | 2 | parry **100 → 80 → 60** ticks; la ventana (12) no cambia | 2 | mesa + **tradeable** |
| **`audacity`** Audacia | ropera (`profile/ropera`) | 2 | −10 ticks de cooldown del lunge **por objetivo alcanzado**, piso 20 | 2 | mesa |
| **`adrenalina`** Adrenalina | daga (`profile/dagger`) | 2 | al **recibir daño**: ver §6 Daga | 2 | mesa |
| **`rhythm_of_iron`** Ritmo de Hierro | martillo (`profile/warhammer`) | 2 | ventana de la cadena **80 → 100 → 120** | 2 | mesa |
| **`riposte`** Contragolpe | espada **+ shotel** | 2 | tras **bloquear** (espada) o **parriar** (shotel), el siguiente golpe ignora **25 %/50 %** de armadura | 2 | mesa |
| **`dry_powder`** Pólvora Seca | arcabuz (`profile/arcabuz`) | 2 | recarga **50 → 40 → 30**; la mecha no cambia | 2 | mesa |

Los seis: `anvil_cost` 2, `slots: [mainhand]`.

# 4 · Los 3 genéricos (por clase física)

| Encantamiento | Clase | Niv | Efecto | Nota |
|---|---|---|---|---|
| **Contragolpe** | guardia | 2 | 25 %/50 % de armadura ignorada tras cubrirse de verdad | ✅ hecho. **En el shotel se arma desde el parry** (el sabre no puede cubrir) |
| **Ensarte** | largas (alabarda, tridente) | 2 | la estocada atraviesa: **50 %/100 %** a un enemigo alineado detrás. El segundo cuerpo **no** cobra bonus de encantamientos | ✅ **HECHO** -- `CombatEngine.ensarte`, con el tag de clase nuevo `enchantable/reach` |
| **Quebranto** | pesadas (martillo, hacha, maza) | 2 | cada golpe baja la armadura del rival: **−8 % por stack**, L1 máx **2** (−16 %), L2 máx **3** (−24 %), 4 s, reaplicar refresca | pendiente. Necesita `MobEffect` |

> **Quebranto toca `Attributes.ARMOR` del rival**, y eso afecta a **todo** el daño que le entre, no
> sólo al tuyo. Por eso **−8 %** y no el −18 % que se propuso antes: es el número más peligroso del
> documento y va a playtest.

# 5 · Los vetos (para no volver a proponerlos)

| | Vetado | Por qué |
|---|---|---|
| C1 | golpear con la guardia arriba | un arma que cubre **y** pega gratis es estrictamente mejor |
| C2 | el bloqueo devuelve daño | castiga al atacante por atacar; diluye al shotel |
| C3 | entrar ya cargado al 50 % | borra la única decisión que queda ("¿ahora o 0,6 s más?") |
| C4 | alcance extendido | obliga al HUD del cono a releer el nivel; si se olvida, **el indicador miente** |
| C5 | encantamientos de **tempo** (`+attack speed`, `-cooldown` de swing) | choca con la velocidad de ataque dura y con `IFRAME_TICKS = 3` |
| C6 | un encantamiento de knockback nuevo | se extiende el de vanilla, no se inventa otro |
| C7 | ejecución incondicional | una ejecución **con contrajuego** (cargar, acercarse, 5 golpes) sí vale |

**El único que se levantó**: C5, y **sólo** en su forma de "la azada ataca más rápido" -- que se
descartó igual (§6 Azada).

# 6 · Arma por arma

## ESPADA · la guardia sin escudo

*Perfil `SWORD`: alcance 3,6 · cono 35/30 · carga 0,5 en 25 ticks · knockback 0,55/0,10*

| Encantamiento | Niv | Efecto | Hook |
|---|---|---|---|
| **Guardia Firme** | **1** | el **primer golpe bloqueado de cada 3 s no gasta aguante** | `GuardHandler.onShieldBlock`, rama del gasto |
| **Segundo Aliento** | 2 | al **romperse tu guardia**, tu siguiente golpe en 3 s pega **+15 %/+25 %** y empuja **+50 %** | donde `breakGuard` marca el stun |
| **Aventurero** | **5** | los enemigos sueltan **+20 % de XP por nivel** (L5 = +100 %) | `LivingExperienceDropEvent` |

> **Aventurero es un genérico a propósito** (decisión del dueño): le da a las espadas un propósito
> late-game. No es identidad y se acepta que no lo sea.

## HACHA · la que desarma

*Perfil `AXE`: alcance 3,4 · carga 2,0 en 60 ticks · knockback 0,34/0,08 · desgaste de armadura ×2*

| Encantamiento | Niv | Efecto | Hook |
|---|---|---|---|
| **Astilladora** | **1** | un golpe bloqueado gasta **2** de aguante en vez de 1 | ✅ **HECHO** -- `GuardState.block(regen, cost)` + `CombatEnchantRules.astilladoraCost` en `GuardHandler` |
| **Mella** | **4** | el desgaste a la armadura sube **+50 %/+100 %/+150 %/+200 %** (sobre el ×2 de base → hasta ×6) | ✅ **HECHO** -- `ArmorHurtEvent.ArmorEntry.newDamage` en `ArmorWearHandler` (`WEAR_MULTIPLIER * mella`) |
| **Filo Astillado** | 2 | **+6 %/+10 % de daño por cada pieza de armadura** del enemigo (máx 4 → +24 %/+40 %) | bonus por objetivo |

## PALA · el control del espacio

*Perfil `SHOVEL`: alcance 3,0 · carga 0,4 en 33 ticks · knockback **1,365/0,156** (el empuje máximo)*

| Encantamiento | Niv | Efecto | Hook |
|---|---|---|---|
| **Pala Antimaterial** | **1** | el click derecho **carga 40 ticks** y suelta un **orbe de antimateria**: al tocar algo **atrae** a todo lo que esté a **5 bloques** y **implosiona** (radio 4, **14 de daño**). Cooldown **120 ticks** | click derecho propio (hoy `NONE`) + entidad patrón `SlashWave` |
| **Paliza** | 2 | tras **2 golpes normales sucesivos**, el **tercer golpe cargado** da knockback **horizontal 2,4** y **vertical 0,6** | contador de golpes sucesivos |

> **Pala Antimaterial es `treasure` + weight 1** (raro y poderoso, decisión del dueño).
> **Paliza necesita techo nuevo**: el actual es 1,6 horizontal / 0,45 vertical, y la pala ya está en
> 1,365. Se sube a **2,5 / 0,7** para los dos casos que lo piden (Paliza e Inercia).
> **Zanja queda descartada sin reemplazo** (decisión del dueño).

## MAZA · **sin encantamientos** (decisión del dueño)

Ya tiene Density, Breach y Wind Burst de vanilla. Caída Astuta, Peso Muerto y Onda de Impacto
**quedan descartados**.

## AZADA · el ritmo sin wind-up

*Perfil `HOE`: alcance 3,2 · cono **60/50** (el más ancho) · sin carga · knockback 0,06/0,01 (el mínimo)*

| Encantamiento | Niv | Efecto | Hook |
|---|---|---|---|
| **Cicatrizador** | **4** | el efecto **Anti-curación**: reduce la efectividad de **todos** los sources de curación **−15 % por nivel** (L4 = **−60 %**), 5 s, refresca | `LivingHealEvent` (existe en NeoForge) → `setAmount(amount * (1 − reducción))` |
| **Santa Muerte** | **1** | **matar** un enemigo lo hace explotar en una **nube negra de humo putrefacto** que aplica **Wither I 5 s** en radio **3** | ✅ **HECHO** -- `enchant/SantaMuerte`, `LivingDeathEvent`. **El asesino queda afuera** (veto C2) y el humo es `SQUID_INK` + `LARGE_SMOKE`, sin arte nuevo |

> **Cicatrizador es el reemplazo de "Cobrador"** (decisión del dueño): no *bloquea* la curación,
> **reduce su efectividad**, y trae un `MobEffect` nuevo (**Anti-curación**) con **un nivel por nivel
> del encantamiento**. **Y el efecto lleva icono** (decisión del dueño): el jugador tiene que poder
> ver que está bajo Anti-curación sin leer un número -- falta la textura.
> **Frenesí queda descartado** (era tempo propio, vetado C5) y **Agobio no se usa**: el dueño eligió
> Cicatrizador.
> 🐛 **Y la animación de la azada está al revés**: el tajo va hacia la derecha y debe ir hacia la
> izquierda. Está en el perfil de viewmodel (el tajo usa **112 de yaw**) y probablemente en la pose de
> cuerpo. **Cambio de una constante**, pero el test de poses que la afirma se reescribe con la razón
> nueva.

## ROPERA · el reposicionamiento

*Perfil `ROPERA`: alcance 3,6 · cono **13/16** (el más cerrado) · sin carga · knockback 0,16/0,03 · daño 6,5 a 10 ticks*

| Encantamiento | Niv | Efecto | Hook |
|---|---|---|---|
| **Punta Fina** | 2 | **+15 %/+25 %** a **1,5 bloques o menos** | bonus por objetivo (distancia) |
| **Audacia** | 2 | el lunge devuelve **−10/−15 ticks de cooldown por cada objetivo alcanzado**, con piso **20** | `SpecialHandler.lunge`, contando lo que cobró el cubo |
| **Retirada** | **1** | **agachado**, el lunge se invierte (impulso hacia atrás) y el golpe al frente (100 %) se cobra **en el press**. Mismo cooldown que el lunge | ó. **HECHO** — `SpecialHandler.retreat` + `LungeMotion` (el signo) |
| **Estocada Perforante** | 2 | el golpe **del lunge** ignora **15 %/30 %** de armadura | el golpe del lunge |

> **Retirada, y la corrección que había que hacerle al diseño:** la tabla decía *"da un ataque **extra**
> al frente"*, y eso no es lo que se implementó porque **no es cierto**. El cubo que barre la marcha
> atrás — `LungeDamage.cube` — está **centrado en el jugador y mide medio alcance en todas las
> direcciones**, o sea que **ya alcanzaba lo que quedaba adelante**: un golpe encima habría cobrado dos
> veces al mismo cuerpo en la misma pulsación.
>
> **Lo que hace es adelantar el cobro, no agregar uno:** el golpe del frente va al conjunto
> `Lunge.prehit`, que es el mismo que el barrido ya no vuelve a tocar. Por eso su fracción es **1,0 y no
> una parte** — no se está sumando nada, se está moviendo el pago de *"cuando cubris el suelo"* a
> *"cuando te vas"*. **Lo que gana no es daño, es garantía:** el golpe se cobra aunque la retirada la
> corte una pared, que es exactamente lo que el lunge normal no puede prometer, porque su golpe depende
> de que el cuerpo llegue. Y el precio es simétrico en las dos direcciones: dos bloques que no gastás en
> acercarte son dos bloques que no te acercan.
>
> **La trampa que tiene, y está testeada:** `Lunge.prehit` limpia su conjunto **mientras el lunge no
> esté activo**, así que el golpe del frente tiene que venir **después** de `Lunge.begin`. Registrado
> antes, se borra en el primer tick sin error y sin golpe — la forma de fallar que este proyecto ya pagó
> varias veces. El guard busca las dos llamadas por su texto y afirma el **orden**.

> **El piso de Audacia no es opcional**: el lunge tiene inmunidad doble (6 ticks + efecto `LUNGE`).
> Sin piso, un bucle de lunges son i-frames casi permanentes en medio de un grupo.
> **Estocada bajó de 25 %/50 % a 15 %/30 %** (pedido del dueño).

## ALABARDA · la multitud

*Perfil `HALBERD`: alcance **5,0** · cono **60/35** · carga 0,6 en 36 ticks · knockback 0,45/0,14 · barrido: `COOLDOWN_FACTOR 4.0`, `PUSH 0.8`, escalas ×1,35*

| Encantamiento | Niv | Efecto | Hook |
|---|---|---|---|
| **Barredor** | 2 | el cono del barrido crece **×1,15/×1,30** (sobre el ×1,35 de base), en distancia **y** en ancho | ✅ **HECHO** -- `HalberdSweep.reachFor(reach, nivel)` + **los 4 sitios de display leen el nivel** (`MaceAir.displayedFor(stats, holder, sweepLevel)`, que a su vez llama a `CombatEnchantments.barredorLevel`) |
| **Segunda Pasada** | 2 | si el barrido **no mató a nadie**, devuelve el **30 %/50 %** del cooldown | ✅ **HECHO** -- `CooldownState.refund(ticks)` + `SpecialHandler.sweep`, que cuenta **muertes** (`!target.isAlive()` después del `hurt`) y no aciertos |
| **Destripamiento** | 2 | los ataques **cargados** causan **Sangrado**: **1 ♥ cada 2 s durante 6 s** (L1) / **durante 10 s** (L2) | `MobEffect` propio **Sangrado** (`applyEffectTick`) |

> **"+1/+2 objetivos" se cayó al escribir el código, y hay que decirlo.** El barrido **no tiene tope de
> objetivos**: `SpecialHandler.targetsInCone` devuelve **todos** los que estén en el cono, y el barrido
> los golpea a todos. Un "+1/+2 objetivos" no tiene dónde aplicarse porque no hay un número del que
> sumar. **Lo que sí hace el encantamiento es agrandar el área, y un área más grande alcanza más
> objetivos por sí sola** -- así que el efecto que el jugador ve es el que el documento prometía, por
> un mecanismo distinto del que el documento describía.
> **Y el dueño decidió que se queda así: sin tope.** Un tope de "+2" sería un *nerf* con nombre de
> encantamiento, porque el barrido **ya** golpea a todos los que atraviesa; el área es el mecanismo, y
> el precio de cuatro swings es lo que lo mantiene en pie.

> **"Sweeping" no se usa como nombre**: es Sweeping Edge, la onda de filo. ∴ **Barredor**.
> **Muralla queda descartada** (reemplazada por Destripamiento).

## MARTILLO · el compromiso

*Perfil `WARHAMMER`: alcance 3,4 · cono 36/36 · carga 0,6 en 32 ticks · knockback 0,55/0,30 · cadena: ventana 80, paso 0,15, sin tope · daño 3,0 a 19 ticks*

| Encantamiento | Niv | Efecto | Hook |
|---|---|---|---|
| **Inercia** | 2 | el empuje de la cadena es **sólo vertical**: **+10 %/+20 % de vertical por golpe de cadena**, horizontal cero | ✅ **HECHO** -- `Knockback.applyLift` + el call site. **Y el techo subió a 2,5/0,70 por esto** (`KnockbackProfile`) |
| **Sentencia** | 2 | **tras 5 golpes de cadena el enemigo revienta**: **8 (L1) / 12 (L2)** de daño a él y **4/6** en radio **2,5**. **No daña al jugador que ataca.** Partículas de `enchant` + `effect`. **L2: los de alrededor también revientan** (al 50 %) | la cadena ya existe (`StreakState`) + `LivingDeathEvent`-style |
| **Trabazón** | 2 | los golpes **cargados** aplican **no poder saltar** (L1) **ni correr** (L2) durante **1,5 s** | `MobEffect` propio + evento de salto cancelable |

> **Inercia necesita el techo vertical nuevo** (0,70): el actual es 0,45 y la base del martillo ya es
> 0,30 → con dos golpes de cadena tocaría el tope.
> **Trabazón se llama así y no "Aturdimiento"**: el proyecto ya llama **stun** a la guardia rota.
> **Yunque queda descartado** (reemplazado por Trabazón).

## DAGA · el tempo

*Perfil `DAGGER`: alcance 2,9 · cono 49/31 · sin carga · knockback 0,14/0,03 · daño 1,6 a **6 ticks** (la más rápida)*

| Encantamiento | Niv | Efecto | Hook |
|---|---|---|---|
| **Apuñalar** | 2 | **+60 % (L1)** y **+90 % (L2)** si golpeás desde **más de 90° de la espalda** | bonus por objetivo (ángulo) |
| **Sanguijuela** | 2 | **+8 %/+14 %** del daño hecho **como curación** | tras el golpe |
| **Gripa** | 2 | al golpear, stacks de **Moco**: **1 de daño por stack cada 20 ticks** (L1) / **15 ticks** (L2), máx **4/6 stacks**, y **estornuda cada 40 ticks** (1 de daño + salto de 0,4). **L2: al estornudar suelta una nube de moco** (radio 2, 2 de daño) | `MobEffect` propio. **Partícula y sonido del panda** (`ParticleTypes.SNEEZE` + su evento) -- cero arte nuevo |
| **Adrenalina** | 2 | al **recibir daño**: **−25 %/−35 % de ese daño** y **Velocidad I/II**. Cooldown **30 s/15 s**. **L2: el daño ahorrado se suma a tu siguiente golpe** | ✅ **HECHO** -- `AdrenalinaHandler` (`LivingIncomingDamageEvent`) + `CombatEngine.cashAdrenalina`. **Reemplaza a `fleeting_edge`**, que hacía otra cosa |

> **Adrenalina reemplaza a Filo Fugaz** y es **ofensiva y defensiva a la vez** (decisión del dueño).
> La mitad ofensiva **sólo en L2**.
> **Veneno queda descartado** (reemplazado por Gripa).

## SHOTEL · la lectura del golpe

*Perfil `SABRE`: alcance **2,25** (el más corto) · cono **75/25** (el más abierto) · carga **1,0 en 20 ticks** (la más fuerte) · knockback 0,38/0,06 · daño 2,7 a 10 ticks · parry: ventana 12, cooldown 100*

| Encantamiento | Niv | Efecto | Hook |
|---|---|---|---|
| **Reflejo de Acero** | 2 | parry **100 → 80 → 60** ticks | ✅ hecho |
| **Desvío Vampírico** | **4** | al **parriar**, robás **1 ♥ de vida máxima**, y **cada parry exitoso suma otro**. El nivel es el **tope del acumulado** (L1 = 1 ♥ · L4 = 4 ♥): al cuatro hay que **desviar cuatro veces** para llegar. **10 s**, refresca. **La vida actual del enemigo no baja más de lo que el robo implica** (vanilla la clampea; ver el aviso) | `MobEffect` propio con `AttributeModifier` sobre `MAX_HEALTH` (patrón Contragolpe) |
| **Corte Gemelo** | 2 | el **golpe normal** da **un golpe extra al 50 % del daño base**; **L1 cada 4 s**, **L2 siempre** | el mecanismo del doble golpe del sable ya existe (`IFRAME_TICKS = 3`) |
| **Capacitor** | 2 | al **desviar un proyectil**, se convierte en un **rayo** (partículas `end_rod` en zigzag) que se devuelve, daña y **quema 4 s**: **3 (L1) / 4 (L2) + el daño base del arma** | la rama de proyectil de `ParrySource` -- **ya existe y tiene test** |

> **El parry ya devolvía proyectiles y era casualidad**: `getEntity()` de una flecha es el **arquero**
> (la flecha es `getDirectEntity()`). Formalizado en `ParrySource.cameThroughTheAir(...)`, y el
> proyectil ahora **se descarta** (antes quedaba clavado en el que parrió).
> **Giro queda descartado** (el parry ya desvía; Capacitor es eso más el rayo).
> **Desvío Perfecto queda descartado** (reemplazado por Desvío Vampírico).

## ARCABUZ · la distancia y la mecha

*Perfil `ARCABUZ`: melee alcance 2,6 · disparo **6,5/32/24** · mecha **20** ticks · recarga **50** ticks · `SHOT_MULTIPLIER 3.0`, `MAX_TARGETS 10`, `FIRE_SECONDS 6.0` · knockback 1,10/0,18*

| Encantamiento | Niv | Efecto | Hook |
|---|---|---|---|
| **Pólvora Seca** | 2 | recarga **50 → 40 → 30** | ✅ hecho |
| **Culata** | **3** | la **bayoneta** empuja **+25 %/+50 %/+75 %** | el golpe de bayoneta |
| **Mecha Corta** | 2 | la mecha baja de **20 → 15/10** ticks | `ArcabuzState.FUSE_TICKS` ⚠️ **trabajo de cliente** (`.mcmeta`) |
| **Rey de las Moscas** | **1** | usa **carne podrida** de munición y dispara una **nube de moscas** (partícula nueva): **4 de daño cada 10 ticks durante 4 s**, radio **3**, rango **8** (vs 6,5). **Infecta por proximidad** (radio 3): basta estar cerca, y **no reinfecta a quien ya tiene el efecto**. Daño total **menor** que el disparo normal | rama de munición + entidad/partícula |
| **Mungus** | **1** | consume **10 Firestone**, carga **×2**, y lanza un **proyectil Firestone enorme** con **trail de lava**, con **gravedad** y **lento**, que al impactar **explota** (**potencia 20 ≈ ×5 TNT**) y **rompe bloques**. Compatible con Mecha Corta y Pólvora Seca (se aplican sobre la base ×2) | entidad con gravedad (patrón `SlashWave`) |

> **Rey de las Moscas y Mungus son incompatibles**, y la incompatibilidad **necesita código**:
> nuestros encantamientos son registro Java, no dato, así que **no llevan `exclusive_set` declarativo**.
> **Mungus rompe bloques** (decisión del dueño) y es la **excepción** a "el mod no rompe bloques".
> **Mecha Corta** resultó **no necesitar arte**, y el documento decía que sí. Los cinco frames del
> arcabuz son **archivos separados** (`textures/item/arcabuz_fuse/`) y el que se dibuja lo elige un
> **predicado** (`ArcabuzModelProperties.FUSE` → `ArcabuzFuseState.progress()`), así que no hay
> animación de atlas que re-temporizar. **Lo que sí había era un número escondido**: `progress()`
> dividía por la constante `ArcabuzState.FUSE_TICKS`, y con una mecha de diez ticks el progreso
> llegaba a 0,5 cuando la mecha se apagaba. El total ahora viaja: el servidor lo calcula al encender y
> el cliente lo lee del stack en el predicado. Y **`light()` pasó a tomar el largo por argumento**,
> porque el estado tiene que contar la mecha que el arma tiene puesta y no la de fábrica.
> 🐛 **Y la animación de la azada iba al revés**: el yaw del tajo pasó de 112 a **−112**, y el **roll
> se queda en negativo** — es lo que la hace una guadaña en vez de un barrido recto, y el test
> (`ShovelAndHoeTest`) lo afirma desde el otro lado, con razón. El test afirmaba la **magnitud** del
> yaw (`Math.abs`) y el **signo** del roll: por eso invertir la dirección no lo rompió y girar el roll
> sí. Es un buen ejemplo de test que afirma la invariante y no el número.
> **La potencia es 12 y no 40** (×3 TNT), y el numero se eligio **balanceando**, no esperando el
> playtest. TNT es 4 en este juego, asi que 12 es tres TNT juntas: un crater de unos cinco o seis
> bloques que **rompe piedra y tierra pero no obsidiana** -- y que no la rompa es la mitad del
> balance, porque la obsidiana es el material anti-explosion del juego y un encantamiento que la
> atraviesa no tiene respuesta. Con diez Firestones, la carga ×2 y un proyectil lento y con gravedad,
> **es un tiro de asedio y no un arma de combate**: el que lo usa renuncia a pelear durante dos
> segundos largos para abrir una pared.
> **Pólvora Negra queda descartada** (reemplazada por Rey de las Moscas).

---

# 7 · La herencia de vanilla (implementado, y qué recibe cada clase)

Las 30 armas salieron de `#minecraft:swords` (hoy ese tag sólo tiene `copper_sword`), y los overrides
apuntan a **tags de clase propios**:

| Encantamiento | Tag | Llega a |
|---|---|---|
| Sharpness | `enchantable/blade` | sólo filos (espada, daga, ropera, sable, alabarda) |
| Smite / Bane of Arthropods | `enchantable/all` | todas |
| Knockback | `enchantable/shove` | hacha, pala, maza, martillo |
| Looting | `enchantable/loot` | daga + espada |
| **Fire Aspect** | `enchantable/blunt` | **contundentes: pala, maza, martillo — al revés de vanilla** |
| Unbreaking / Mending / Vanishing | `enchantable/durability` | todas |

**Sweeping Edge** no se overridea: al salir las 30 de `#minecraft:swords`, el encantamiento quedó
sólo en las espadas de vanilla + `copper_sword`, que es exactamente dónde debe estar. Su mecánica es
**la onda de filo** (`SlashWave`), implementada.

**Reversiones deliberadas a confirmar en la mesa**: la espada **pierde** Knockback y Fire Aspect; el
martillo **pierde** Sharpness.

# 8 · Orden de implementación

| # | Qué | Por qué ahí |
|---|---|---|
| 1 | **El tag de encantamiento** | ✅ **HECHO** |
| 2 | **Los baratos**: los que sólo necesitan el bonus por objetivo que ya existe -- Punta Fina, Filo Astillado, Apuñalar, Sanguijuela, Segundo Aliento, Guardia Firme, Estocada, Culata, Aventurero | el hook ya existe; son casi gratis |
| 3 | **Los de efecto vanilla**: Gripa (panda), Santa Muerte (Wither), Adrenalina (Velocidad), Trabazón | sin `MobEffect` nuevo (salvo Trabazón) |
| 4 | **Los de `MobEffect` nuevo**: Cicatrizador (Anti-curación), Destripamiento (Sangrado), Quebranto, Desvío Vampírico | la factura de mantenimiento más alta |
| 5 | **Los que tocan el cliente**: Mecha Corta, la animación de la azada, el HUD de Barredor | una ronda propia |
| 6 | **Los caros**: Ensarte, Pala Antimaterial, Mungus, Rey de las Moscas, Sentencia, Capacitor | entidades, munición, exclusividad |

# 9 · Lo que falta

1. **Los números**: todos los de arriba son propuesta; **se prueban en juego**.
2. **Sentencia**: **no te daña** — decidido por el dueño.
3. **Cicatrizador**: el efecto **Anti-curación** lleva **icono** — decidido por el dueño. **Falta la
   textura** (`textures/mob_effect/anti_healing.png`).
4. **Mungus**: la potencia **bajó de 20 a 12** (~×3 TNT) — balanceada acá, con el razonamiento en §6.
5. **Barredor**: **sin tope de objetivos** — decidido acá. Un tope de "+2" sería un nerf con nombre de
   encantamiento, porque el barrido ya golpea a todos los que atraviesa.
6. **La onda de filo y el parry contra jugadores** necesitan **2 jugadores** para verificarse.


---

# 10 - Los implementados (tabla con el hook)

> Extraído de `AGENTS.md` (2026-10-12). La columna *dónde* es el sitio donde el número se cobra.

## Encantamientos propios (ronda 2026-10-10)

**6 custom, todos datos + reglas puras.** El patrón: el JSON dice en qué ítems puede estar
(`supported_items`, **un solo tag**, ver Trampas del build), `CombatEnchantRules` (pura, JVM pelada)
tiene los números, y el hook vive donde el número se cobra.

> **El diseño completo (los ~30 por arma, con números y hooks) vive en `docs/encantamientos.md`**, que
> absorbió y reemplazó a los dos docs anteriores. Acá queda sólo lo implementado.

| id | arma | efecto | dónde |
|---|---|---|---|
| `steel_reflex` | shotel (molde `sabre`) | parry 100 → 80 → 60 ticks | `ParryState.activate(nivel)` |
| `audacity` | ropera | lunge −20 %/nivel (fracción) | `SpecialHandler.lunge` |
| `adrenalina` | daga (molde `dagger`) | al **recibir daño**: **−25 %/−35 %** de ese daño y **Velocidad I/II** (5 s). Cooldown **30 s/15 s**. **L2: lo ahorrado se suma al siguiente golpe** | `AdrenalinaHandler` (`LivingIncomingDamageEvent`) + `CombatEngine.cashAdrenalina`. **Reemplaza a `fleeting_edge`**, que hacía otra cosa |
| `rhythm_of_iron` | martillo | ventana de la cadena 80 → 100 → 120 | `StreakState` (la ventana es un **campo**) |
| `riposte` | espada (clase `sword`) | tras bloquear de verdad, el siguiente golpe ignora 25/50 % de armadura | `GuardHandler` arma, `CombatEngine` cobra |
| `dry_powder` | arcabuz (molde `arcabuz`) | recarga 50 → 40 → 30 ticks | `Arcabuz.reloadTicks` |
| `punta_fina` | ropera (molde `ropera`) | +15 %/+25 % de daño a **1,5 bloques o menos** | `CombatEngine.perTargetBonus` |
| `filo_astillado` | hacha (clase `axe`) | +6 %/+10 % de daño **por pieza de armadura** del rival (máx 4) | `CombatEngine.perTargetBonus` |
| `apunalar` | daga (molde `dagger`) | +60 %/+90 % si pegás **desde más de 90° de la espalda** | `CombatEngine.perTargetBonus` |
| `sanguijuela` | daga (molde `dagger`) | +8 %/+14 % del daño del golpe como **curación** | `CombatEngine.apply`, por golpe que aterriza |
| `aventurero` | espada (clase `sword`) | **+20 % de XP por nivel**, máx 5 (al 5 = +100 %) | `enchant/AdventurerExperience` -- **al MORIR, no al golpear** |
| `culata` | arcabuz (molde `arcabuz`) | la bayoneta empuja **+25 %/+50 %/+75 %** | `CombatEngine.enchantPush` |
| `estocada` | ropera (molde `ropera`) | el golpe **del lunge** ignora **15 %/30 %** de armadura | `CombatEngine.lungePierce`, cobrado en `special/Lunge` |
| `segundo_aliento` | espada (clase `sword`) | al **romperse tu guardia**, tu siguiente golpe en 3 s pega **+15 %/+25 %** y empuja **+50 %** | `GuardHandler.breakGuard` arma `SecondWindWindow`; `CombatEngine` la cobra |
| `guardia_firme` | espada (clase `sword`) | **1 nivel** · el primer bloqueo de cada 3 s **no gasta aguante** | `GuardHandler.onShieldBlock` + `GuardFree` |
| `cicatrizador` | azada (clase `hoe`) | el efecto **Anti-curación**: **−15 % por nivel** de toda curación, 4 niveles = 4 del efecto, 5 s | `CombatEngine.applyOnHitEffects` aplica; **`effect/AntiHealingHandler`** cobra en `LivingHealEvent` |
| `destripamiento` | alabarda (molde `halberd`) | el **cargado** deja **Sangrado**: 1 ♥ cada 2 s durante 6 s (L1) / 10 s (L2) | `CombatEngine.applyOnHitEffects` |
| `quebranto` | pesadas (clase `heavy`) | −8 % de armadura por stack, **máx 3**, 4 s, reaplicar refresca | `CombatEngine.applyOnHitEffects` |
| `desvio_vampirico` | shotel (molde `sabre`) | al **parriar** robás **1 ♥ de vida máxima** y cada parry **suma otro**; el nivel es el **tope** (L1 1 ♥ · L4 4 ♥), 10 s, refresca | `ParryHandler` -- **dos efectos**, `VAMPIRIC` (el acumulado) y `DRAINED` (1 ♥ por parry) |
| `mecha_corta` | arcabuz (molde `arcabuz`) | la mecha **20 → 15 → 10** ticks | `Arcabuz.light(fuse)` (el total por argumento) + `ArcabuzFuseState.progress(totalTicks)` -- **el cliente divide por el total, no por la constante** |
| `barredor` | alabarda (molde `halberd`) | el cono del **barrido** crece **×1,15/×1,30** (sobre el ×1,35 base), en distancia **y** en ancho | `HalberdSweep.reachFor(reach, nivel)` + **`MaceAir.displayedFor(stats, holder, sweepLevel)`**: el HUD muestra el cono del **barrido**, y los **4 sitios de display** leen el nivel (`CombatEnchantments.barredorLevel`) |
| `astilladora` | hacha (clase `axe`) | un golpe bloqueado gasta **2** de aguante en vez de 1 | `GuardState.block(regen, cost)` -- **el coste es un parámetro y no dos llamadas**, porque gastar de a uno baja la guardia en el primero |
| `mella` | hacha (clase `axe`) | el desgaste a la armadura sube **×1,5/×2/×2,5/×3** (sobre el ×2 del hacha cargada → hasta **×6**) | `ArmorWearHandler` (el `newDamage` mutable) -- **el único encantamiento que no toca el daño** |
| `segunda_pasada` | alabarda (molde `halberd`) | el barrido que **no mató a nadie** devuelve el **30 %/50 %** del cooldown | `CooldownState.refund(ticks)` + `SpecialHandler.sweep` (cuenta **muertes**, no aciertos) |
| `santa_muerte` | azada (clase `hoe`) | **matar** deja una nube putrefacta: **Wither I 5 s** en radio **3**, con `SQUID_INK` + `LARGE_SMOKE` (sin arte) | `enchant/SantaMuerte` -- `LivingDeathEvent`, **y el asesino queda afuera** (veto C2) |
| `inercia` | martillo (molde `warhammer`) | el empuje de la cadena va **entero al aire**: **+10 %/20 % de vertical por golpe**, **horizontal cero** | `Knockback.applyLift` + el call site del martillo. **Es la excepción con nombre** al javadoc de `Knockback.apply`, que prohíbe mandar un encantamiento al vertical |
| `ensarte` | largas (clase `enchantable/reach` = alabarda + tridente) | la estocada **atraviesa**: **50 %/100 %** del daño base a **un** cuerpo alineado del otro lado | `CombatEngine.ensarte` -- **el segundo cuerpo no cobra bonus por objetivo**, y el gate es el perfil (`HALBERD`/`TRIDENT`) |
| `paliza` | pala (molde `shovel`) | el golpe **cargado** que cierra una serie empuja **2,4 horiz. / 0,6 vert.** (L1 serie de 2, L2 de 1) | `ModAttachments.PALIZA_STREAK` (`StreakState`) + `Knockback.applyLift` con el perfil de Paliza |
| `sentencia` | martillo (molde `warhammer`) | a los **5 golpes de cadena** el cuerpo **revienta**: **8/12** a él y **4/6** en radio **2,5**. **L2 encadena** a los de al lado | `CombatEngine.sentencia`, desde `onHit`. **El que ataca queda afuera** |
| `trabazon` | martillo (molde `warhammer`) | el golpe **cargado** deja **sin saltar** (L1) **y sin correr** (L2), 1,5 s | `enchant/TrabazonHandler` -- **el salto no se cancela, se devuelve** (`LivingJumpEvent` no es cancelable) |
| `gripa` | daga (molde `dagger`) | stacks de **Moco**: 1 de daño por stack cada 20/15 ticks, máx 4/6, y **estornuda** cada 40 (partícula del panda) | `ModEffects.MOCO` + `MocoEffect` -- **sin arte nuevo** |
| `corte_gemelo` | shotel (molde `sabre`) | el golpe normal da un **segundo corte al 50 %**; **L1 cada 4 s**, **L2 siempre** | `state/CorteGemelo` (el reloj) + `CombatEngine.lastRoundOnHit` |
| `capacitor` | shotel (molde `sabre`) | el proyectil parriado vuelve como **rayo**: **3/4 + el daño base**, y **quema 4 s** | `ParryHandler` -- la rama de `ParrySource.cameThroughTheAir`, **que ya existía** |
| `rey_de_las_moscas` | arcabuz (molde `arcabuz`) | el disparo deja una **nube de moscas en CADA cuerpo del cono**: **4 de daño cada 10 ticks, 4 s, radio 3**. El **dueño queda afuera** | `MobCloud` (entidad invisible) + `MobCloudRenderer` (**vacío, pero REGISTRADO**) + rama al tope de `Arcabuz.fire` -- **la munición sigue siendo Firestone, no carne podrida todavia**. Una nube por cuerpo (el cono ya decidía a quién llega, y tomar solo el primero era devolver un golpe por un disparo), con **un solo zumbido** aunque haya ocho |
| `retirada` | ropera (molde `ropera`) | **agachado**, el lunge sale **para atrás** y el golpe al frente (100 %) se cobra **en el press** | `SpecialHandler.retreat` + `LungeMotion` (el **signo** del impulso) — **el golpe va a `Lunge.prehit`**, al mismo conjunto que barre la marcha, para no cobrar dos veces |

> **La linea "nada en `src/test` puede nombrar un tipo de Minecraft" es mas larga de lo que parece: la
> clase QUE EL TEST CARGA tampoco.** `MaceAir` llamaba a `CombatEnchantments.level(...)`, cuya firma
> declara un `HolderLookup.Provider`, y **los tres tests de `MaceAir` murieron con
> `NoClassDefFoundError` antes de la primera asercion** — sin que el archivo de test nombrara nada.
> La regla real es *"la clase que un test carga no puede **nombrar** un tipo de Minecraft, ni en su
> firma ni en un metodo al que llame"*, y el nivel de Barredor entra como **argumento** por eso: el
> sitio de display, que no se testea, es quien lee el encantamiento y lo pasa.

> **Y un guard de wiring que busca un string tiene que pedir LA LLAMADA AL RAS, no el nombre de la
> funcion.** Es la **cuarta vez** que este proyecto paga lo mismo: `assertTrue(x.contains("regla("))`
> queda **verde con la llamada envuelta en un `if (false)`**, porque el string sigue ahi. Lo que
> distingue "esta escrita" de "se ejecuta" es el contexto -- `"\n        cashAdrenalina(player,
> target);"`, con su indentacion -- y sin el, el sabotaje que apaga la llamada **no muere**. Los cuatro
> casos fueron: `ensarteShare(`, `event.setAmount(`, `cashAdrenalina(` y, antes, el `perTargetBonus(`
> de la ronda de las tandas. **La forma barata de escribirlo bien es copiar la linea del codigo con su
> indentacion en vez de escribir el nombre del metodo.**

> **5 efectos nuevos, y las cuentas viven en `CombatEnchantRules` (pura) y no en los efectos.** Un
> `MobEffect` extiende una clase de Minecraft, así que un método suyo **no se puede cargar en un
> test**: la cuenta adentro del efecto sería una cuenta que ningún test mira. Es la línea exacta del
> proyecto -- *"¿implementa algo de Minecraft?"* -- y los tests la cazaron **dos veces**:
>
> 1. **`antiHealingReduction` sin clampear**: con un amplifier alto la reducción **pasaba de 1** y
>    `1 − reducción` daba **negativo** -- o sea, curarse hacía **daño**.
> 2. **`DRAIN_PER_LEVEL` en positivo**: el robo *sumaba* vida máxima en vez de restarla. El techo del
>    enemigo subía y el robo era un regalo para los dos lados. **La cuenta se ve bien leída y al
>    revés**, que es la peor forma de fallar.
>
> **Y el robo no puede matar, pero sí baja la vida actual.** Bajarle el techo a un cuerpo le baja la
> vida -- `LivingEntity#tick` clampea cada tick y no hay forma de evitarlo -- así que lo que se
> garantiza es el **piso**: `canDrain` pregunta si le quedaría al menos un punto de techo, y si no,
> el robo no se cobra.

> **Los dos últimos necesitaron estado nuevo**, y son los primeros del proyecto que lo hacen: dos
> attachments (`SECOND_WINDOW` y `GUARD_FREE`) con **vencimiento absoluto y sin codec**, el patrón de
> `ContragolpeWindow` -- un contador habría que bajarlo cada tick para todos los jugadores, y una
> ventana que reaparece tras reconectar es un premio que el jugador no ganó.
> **`GuardFree.READY` es `-INTERVAL_TICKS` y no cero**, y el test lo afirma: con cero, "listo" sería
> `now - 0 >= 60` y el primer bloqueo gratis de una partida no llegaría hasta el tick sesenta del
> mundo. Saboteado a cero: muere.

> **Punta Fina, Filo Astillado y Apuñalar comparten un solo canal** (`CombatEngine.perTargetBonus`), y
> es a propósito:
> los tres son "un porcentaje del daño que depende de algo del objetivo o de la posición", y el modo
> de falla de un canal que nadie llama es que **el build queda verde**. El guard de wiring afirma
> `enchanted + perTargetBonus(` -- **la suma, no la existencia de la función** -- porque la primera
> versión afirmaba `perTargetBonus(` y el sabotaje que quitaba la suma **no la hacía morir**.
> Y el hacha necesitó un tag de clase nuevo, `#cortescombat:enchantable/axe` (`#minecraft:axes` + el
> molde), por la misma razón que la espada: el hacha del juego es la de vanilla reworkeada, y su
> alcance no puede ser un tag de molde que sólo tiene las nuestras.

- **`CombatEnchantRules.IDS`** es la lista canónica y `CombatEnchantmentFilesTest` la camina: agregar
  un id hace que el test cubra solo el archivo, el tag, la forma del campo y **los 8 idiomas**.
- **Y hay que estar en un TAG DE ENCANTAMIENTO o no se consiguen.** En 1.21.1 el campo `"treasure"`
  del JSON **no existe** (verificado: ninguno de los 42 de vanilla lo declara) -- son **tags de
  registro**: `in_enchanting_table` (lo lee la mesa, `EnchantmentMenu:207`), `treasure`, `tradeable`
  (aldeanos) y `on_random_loot` (cofres). Los 6 nuestros se registraban, tenían archivo e idioma,
  se podían dar con `/enchant`... y **no salían nunca en una mesa**, sin un solo error en el log. ∴
  `data/minecraft/tags/enchantment/{in_enchanting_table,tradeable}.json`, y el guard pregunta si
  **cada id está adentro**, no si el archivo existe.
- **El parry devuelve el daño de un proyectil al tirador, y era casualidad.** `getEntity()` de una
  flecha es **el arquero** (la flecha es `getDirectEntity()`), así que el `instanceof LivingEntity`
  del handler lo aceptaba sin preguntar nada. Formalizado en `ParrySource.cameThroughTheAir(...)`
  (pregunta por **identidad**, no por tipo: así la onda de filo, que no extiende `Projectile`,
  también entra), el proyectil se **descarta** (antes quedaba clavado en el que parrió) y el guard
  afirma que el handler **usa** la respuesta -- una pregunta sin consumidor es el bug.
- **Los ids viven en la clase pura** (`*_ID` como `String`) y no en `CombatEnchantments`, que nombra
  `ResourceKey`/`Enchantment` y no se puede cargar en un test.
- **Identidad = molde** (`#cortescombat:profile/<familia>`), **genérico = clase**
  (`#cortescombat:enchantable/<x>`). `GENERIC_IDS` marca la excepción.
- **Quick Charge se probó y se revirtió.** Se reutilizó el de vanilla (regla: extender antes de
  inventar) y el playtest lo midió: el efecto es **−0,25 s absolutos por nivel**, que sobre la
  ballesta (1,25 s) es −20 % y sobre nuestro arcabuz (2,5 s) es **−10 %** -- la mitad de lo que el
  diseño pedía. El log lo mostró (`de 50` sin encantamiento, `de 40` con Quick Charge II, cuando el
  diseño decía 30). **Lección: un efecto absoluto reutilizado sobre una base distinta da otro
  porcentaje**, y una reutilización que da la mitad del número no es una reutilización.
- **Idiomas: 8 archivos** (`ModLocales`): `en_us` + los **siete** españoles. **Ninguna variante es
  alias de otra** -- la pila es *[seleccionado, en_us]*, así que `es_mx` cae a inglés, no a `es_es`.
- **El entorno de dev no tiene idiomas de vanilla**: el cliente trae **solo** `en_us.json`, así que
  elegir español deja las cadenas de *vanilla* en inglés aunque las nuestras salgan bien.
