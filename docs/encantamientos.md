# Encantamientos — documento canónico

> **Este es el único documento de encantamientos.** Absorbió y reemplaza a
> `encantamientos-armas-nuevas.md` (la propuesta v2: matriz de herencia, onda de filo, genéricos) y a
> `encantamientos-unicos.md` (el plan por arma). Cuando algo de esos dos contradiga esto, **manda
> esto**: entre los dos había **nueve nombres para mecánicas distintas**, y esa era la primera cosa a
> limpiar.
>
> **Estado (2026-10-11):** **9 encantamientos implementados** y en la mesa (los 6 de §3 más Punta
> Fina, Filo Astillado y Apuñalar, de la primera tanda). Los números de los ~24 restantes están
> **aprobados hasta el playtest final**; falta escribirlos.

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
| **`fleeting_edge`** → **Adrenalina** | daga (`profile/dagger`) | 2 | ver §6 Daga | 2 | mesa |
| **`rhythm_of_iron`** Ritmo de Hierro | martillo (`profile/warhammer`) | 2 | ventana de la cadena **80 → 100 → 120** | 2 | mesa |
| **`riposte`** Contragolpe | espada **+ shotel** | 2 | tras **bloquear** (espada) o **parriar** (shotel), el siguiente golpe ignora **25 %/50 %** de armadura | 2 | mesa |
| **`dry_powder`** Pólvora Seca | arcabuz (`profile/arcabuz`) | 2 | recarga **50 → 40 → 30**; la mecha no cambia | 2 | mesa |

Los seis: `anvil_cost` 2, `slots: [mainhand]`.

# 4 · Los 3 genéricos (por clase física)

| Encantamiento | Clase | Niv | Efecto | Nota |
|---|---|---|---|---|
| **Contragolpe** | guardia | 2 | 25 %/50 % de armadura ignorada tras cubrirse de verdad | ✅ hecho. **En el shotel se arma desde el parry** (el sabre no puede cubrir) |
| **Ensarte** | largas (alabarda, tridente) | 2 | la estocada atraviesa: **50 %/100 %** a un enemigo alineado detrás. El segundo cuerpo **no** cobra bonus de encantamientos | pendiente |
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
| **Astilladora** | **1** | un golpe bloqueado gasta **2** de aguante en vez de 1 | `GuardHandler`, en `state.block(...)` |
| **Mella** | **4** | el desgaste a la armadura sube **+50 %/+100 %/+150 %/+200 %** (sobre el ×2 de base → hasta ×6) | `ArmorHurtEvent.ArmorEntry.newDamage` -- **ya existe** (`ArmorWearHandler`) |
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
| **Santa Muerte** | **1** | **matar** un enemigo lo hace explotar en una **nube negra de humo putrefacto** que aplica **Wither I 5 s** en radio **3** | la muerte ya se detecta (`notifyHit` con `killed`) |

> **Cicatrizador es el reemplazo de "Cobrador"** (decisión del dueño): no *bloquea* la curación,
> **reduce su efectividad**, y trae un `MobEffect` nuevo (**Anti-curación**) con **un nivel por nivel
> del encantamiento**.
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
| **Retirada** | **1** | **agachado**, el lunge se invierte (impulso hacia atrás) y **da un ataque extra al frente** justo antes de salir. Mismo cooldown que el lunge | estado de agachado + `SpecialHandler` |
| **Estocada Perforante** | 2 | el golpe **del lunge** ignora **15 %/30 %** de armadura | el golpe del lunge |

> **El piso de Audacia no es opcional**: el lunge tiene inmunidad doble (6 ticks + efecto `LUNGE`).
> Sin piso, un bucle de lunges son i-frames casi permanentes en medio de un grupo.
> **Estocada bajó de 25 %/50 % a 15 %/30 %** (pedido del dueño).

## ALABARDA · la multitud

*Perfil `HALBERD`: alcance **5,0** · cono **60/35** · carga 0,6 en 36 ticks · knockback 0,45/0,14 · barrido: `COOLDOWN_FACTOR 4.0`, `PUSH 0.8`, escalas ×1,35*

| Encantamiento | Niv | Efecto | Hook |
|---|---|---|---|
| **Barredor** | 2 | el barrido alcanza **+1/+2 objetivos** **y** el área crece **×1,15/×1,30** (sobre el ×1,35 de base) | `HalberdSweep` (objetivos y escalas). **El HUD del cono lee el nivel** -- confirmado por el dueño |
| **Segunda Pasada** | 2 | si el barrido **no mató a nadie**, devuelve el **30 %/50 %** del cooldown | tras el barrido |
| **Destripamiento** | 2 | los ataques **cargados** causan **Sangrado**: **1 ♥ cada 2 s durante 6 s** (L1) / **durante 10 s** (L2) | `MobEffect` propio **Sangrado** (`applyEffectTick`) |

> **"Sweeping" no se usa como nombre**: es Sweeping Edge, la onda de filo. ∴ **Barredor**.
> **Muralla queda descartada** (reemplazada por Destripamiento).

## MARTILLO · el compromiso

*Perfil `WARHAMMER`: alcance 3,4 · cono 36/36 · carga 0,6 en 32 ticks · knockback 0,55/0,30 · cadena: ventana 80, paso 0,15, sin tope · daño 3,0 a 19 ticks*

| Encantamiento | Niv | Efecto | Hook |
|---|---|---|---|
| **Inercia** | 2 | el empuje de la cadena es **sólo vertical**: **+10 %/+20 % de vertical por golpe de cadena**, horizontal cero | knockback + la cadena |
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
| **Adrenalina** | 2 | al **recibir daño**: **−25 %/−35 % de ese daño** y **Velocidad I/II**. Cooldown **30 s/15 s**. **L2: el daño ahorrado se suma a tu siguiente golpe** | `LivingIncomingDamageEvent` + cooldown por jugador |

> **Adrenalina reemplaza a Filo Fugaz** y es **ofensiva y defensiva a la vez** (decisión del dueño).
> La mitad ofensiva **sólo en L2**.
> **Veneno queda descartado** (reemplazado por Gripa).

## SHOTEL · la lectura del golpe

*Perfil `SABRE`: alcance **2,25** (el más corto) · cono **75/25** (el más abierto) · carga **1,0 en 20 ticks** (la más fuerte) · knockback 0,38/0,06 · daño 2,7 a 10 ticks · parry: ventana 12, cooldown 100*

| Encantamiento | Niv | Efecto | Hook |
|---|---|---|---|
| **Reflejo de Acero** | 2 | parry **100 → 80 → 60** ticks | ✅ hecho |
| **Desvío Vampírico** | **4** | al **parriar**, robás **vida máxima**: **+1/+2/+3/+4 ♥** al tuyo y se los quita al enemigo, **10 s**, refresca. **La vida actual del enemigo no baja** (sólo su techo) | `MobEffect` propio con `AttributeModifier` sobre `MAX_HEALTH` (patrón Contragolpe) |
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
> **La potencia es 20 y no 40** (×10 TNT): a 40 el cráter se come el terreno de alrededor, y como el
> mod no rompía bloques en ninguna parte, ese número se elige **después** del primer playtest.
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

1. **Los números**: todos los de arriba son propuesta, **ninguno está ratificado**. Se prueban en juego.
2. **Sentencia**: ¿el reventón **no te daña** (propuesto), o **te daña menos**?
3. **Cicatrizador**: ¿el efecto **Anti-curación** es un `MobEffect` visible en el HUD (con icono), o
   invisible?
4. **Mungus**: la potencia queda en **20** hasta el playtest.
5. **La onda de filo y el parry contra jugadores** necesitan **2 jugadores** para verificarse.
