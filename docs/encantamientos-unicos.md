# Encantamientos únicos por arma — v2 · DECISIONES DEL DUEÑO

> **Estado: diseño CERRADO arma por arma (2026-10-10). Nada implementado todavía.** Esta v2 reemplaza
> las propuestas de la v1: lo que está acá es lo que el dueño decidió, no lo que se propuso. Donde
> falta un número va `?` con una propuesta entre paréntesis para que él la ratifique o la cambie.
>
> **Antes de escribir una línea hay que resolver el bug del tag** (abajo), porque hoy los 6
> encantamientos que ya existen **no se pueden obtener en el juego**.

---

## 🔴 El bug que bloquea todo: el tag `in_enchanting_table`

**Verificado en la fuente** (`EnchantmentMenu.java:207`):

```java
Optional<HolderSet.Named<Enchantment>> optional = registryAccess.registryOrThrow(Registries.ENCHANTMENT)
        .getTag(EnchantmentTags.IN_ENCHANTING_TABLE);
if (optional.isEmpty()) {
    return List.of();
} else {
    List<EnchantmentInstance> list = EnchantmentHelper.selectEnchantment(this.random, stack, cost, optional.get().stream());
```

La mesa pasa **sólo** los encantamientos de ese tag. **El mod no tiene ni un tag de encantamiento** →
los 6 custom (`steel_reflex`, `audacity`, `fleeting_edge`, `rhythm_of_iron`, `riposte`, `dry_powder`)
**no salen en la mesa, ni en cofres, ni en el comercio**. Sólo con `/enchant`.

**Y `treasure` en 1.21.1 ya no es un campo del JSON**: lo verifiqué, ninguno de los 42 encantamientos
de vanilla lo declara. Son **cuatro tags**:

| Tag | Quién lo lee | Qué significa |
|---|---|---|
| `#minecraft:in_enchanting_table` | la mesa de encantar | "sale en la mesa" |
| `#minecraft:treasure` | nadie (nominal) | "es de tesoro" |
| `#minecraft:tradeable` | los aldeanos | "lo vende un bibliotecario" |
| `#minecraft:on_random_loot` | el loot de cofres | "puede salir en cofres" |

**Decisión del dueño ya tomada:** `steel_reflex` va a **`tradeable`** (los aldeanos lo venden).
El resto de los 6 va a `in_enchanting_table` salvo que se diga lo contrario.

---

## 🟡 Tres datos verificados que cambian el plan

1. **El parry NO desvía proyectiles hoy.** No hay una línea que toque `AbstractArrow`/`Projectile`. Lo
   que devuelve cosas es la **guardia**, y devuelve la **onda de filo** (`SlashWave.sweep` →
   `reflect()`). ∴ **"Giro" no era inútil**: desviar proyectiles con el parry es capacidad nueva, y
   **Capacitor necesita implementarla antes** de convertir nada en rayo.
2. **El shotel NO puede cubrir.** `ViewModelMotion:306`, textual: *"the sabre's guard profile is NONE,
   so it never goes up and never breaks"*. ∴ **Contragolpe no puede armarse con un bloqueo en el
   shotel** -- si va a tenerlo, se arma desde el **parry** (que es su mecánica de lectura).
3. **`Frenesí` ya existe como `miner_frenzy`** ("Fiebre del Minero", de las 11 doradas): *mientras más
   picás, más rápido*. **La azada es una herramienta**, así que la mecánica del pedido es la misma con
   otro target. Hay que decidir si se duplica, se renombra o se descarta.

---

## Los 6 que ya existen

| id | Arma (gate) | Niv | Efecto | Cambio pedido |
|---|---|---|---|---|
| `steel_reflex` · Reflejo de Acero | shotel (`profile/sabre`) | 2 | parry **100 → 80 → 60** ticks; la ventana (12) no cambia | **+ tag `tradeable`** |
| `audacity` · Audacia | ropera (`profile/ropera`) | 2 | cooldown del lunge −20 %/nivel | **rediseñada**, ver Ropera |
| `fleeting_edge` · Filo Fugaz | daga (`profile/dagger`) | 2 | Velocidad I 40/60 ticks al conectar, sin renovar | **reemplazada por Adrenalina** |
| `rhythm_of_iron` · Ritmo de Hierro | martillo (`profile/warhammer`) | 2 | ventana de la cadena **80 → 100 → 120** | sin cambios |
| `riposte` · Contragolpe | espada (`enchantable/sword`) | 2 | tras bloquear, el siguiente golpe ignora 25/50 % de armadura | **+ shotel** (ver aviso) |
| `dry_powder` · Pólvora Seca | arcabuz (`profile/arcabuz`) | 2 | recarga **50 → 40 → 30** | sin cambios |

---

# SHOTEL — la lectura del golpe

### `steel_reflex` · Reflejo de Acero ✅ sin cambios
Niv **2** · parry **100 → 80 → 60** ticks · la ventana de 12 ticks no se toca. **+ `tradeable`**.

### `desvio_vampirico` · Desvío Vampírico (reemplaza a Desvío Perfecto)
- **Niv 4.**
- Al **desviar (parry)** un ataque, **robás vida máxima** del enemigo: se la baja a él y te la sube a
  vos. **Duración 10 s en todos los niveles**; desviar otra vez **reinicia** el timer (no acumula
  duración, sí el robo).
- Robo por nivel: **L1 +2 ♥ / L2 +3 / L3 +4 / L4 +5** `?` (propuesta: 2/3/4/5 de vida máxima).
- **Hook**: un `MobEffect` propio que aplique un `AttributeModifier` sobre `Attributes.MAX_HEALTH`
  (`ADD_VALUE`), uno **positivo en el jugador** y uno **negativo en el enemigo** -- el patrón de
  Contragolpe (modifier transitorio), no una suma a mano.
- ⚠️ **Trampa dura:** bajarle `MAX_HEALTH` a un enemigo **puede matarlo** -- vanilla clampea la vida
  actual al nuevo máximo. O sea que esto es una **ejecución gratuita** sobre cualquier mob herido, y
  el doc veta eso por nombre (C7). **Decisión obligatoria:** o el robo al enemigo **no puede bajar su
  vida actual** (sólo su máximo, con la vida clampeada sólo si sobra), o se acepta que mate y se
  documenta. Mi propuesta: **la vida actual no baja** (si el enemigo está por encima del nuevo
  máximo, se le deja la vida y se le baja el techo).
- ⚠️ Necesita `MobEffect` nuevo (factura de mantenimiento) y **HUD**: los corazones del jugador
  suben, que es feedback deseable.

### `corte_gemelo` · Corte Gemelo (rediseñado)
- **Niv 2.**
- El **golpe normal** (no el cargado) da **un golpe extra**, y ese extra es **la mitad del daño base**.
- **Hook**: el mecanismo del **doble golpe del sable ya existe** (dos `hurt()` separados 8 ticks, con
  `IFRAME_TICKS = 3`) -- se reutiliza, no se inventa. El extra va a **50 %** del daño base.
- ⚠️ No se solapa con el cargado doble (ese es a **carga completa**; este es al **tap**), pero hay que
  afirmarlo con un test o los dos van a dispararse juntos.

### `capacitor` · Capacitor (reemplaza a Giro)
- **Niv 2.**
- Al **desviar un proyectil**, el proyectil se convierte en un **rayo** (partículas de `end_rod` en
  zigzag) que **se devuelve**, **daña y quema** al enemigo inmediatamente.
- Daño del rayo: **3 + daño base del arma** (L1) · **4 + daño base del arma** (L2).
- **Hook**: primero hay que **implementar el desvío de proyectiles** en el parry (hoy no existe: ver
  el aviso 🟡1). Después, interceptar el proyectil, cancelarlo, y aplicar daño + `setSecondsOnFire`.
- ⚠️ Esto es la parte más cara del shotel: **desvío de proyectiles + conversión**. Es lo que "Giro"
  pedía, más el rayo.

---

# MARTILLO — el compromiso

### `inercia` · Inercia (rediseñada)
- **Niv 2.**
- El empuje de la cadena es **sólo vertical**: cada golpe de cadena suma **+8 %/+14 %** de empuje
  **hacia arriba** (horizontal **cero**).
- ⚠️ **Números del perfil hoy**: `WeaponTables:393` → `WARHAMMER = KnockbackProfile(0.55F, 0.30F)`
  (horizontal 0.55, vertical 0.30). El techo es `MAX_VERTICAL = 0.45`, así que con 2 golpes ya está
  en el tope. **Decisión**: ¿sube el techo, o se queda clampeado ahí?
- ⚠️ **Y hay dos tests que afirman relaciones con este perfil**: `AxeBalanceTest:229` y
  `ArcabuzBalanceTest:199` (`shove.vertical() < WARHAMMER.vertical()`). Si el martillo pasa a sólo
  vertical, esos números cambian y los tests hay que reescribirlos con la razón nueva.
- ⚠️ **A confirmar**: *"ahora hace daño de altura"* -- el daño de altura del juego es de la **maza**
  (el smash al caer). El martillo no lo tiene. Si la idea es que el empuje vertical **habilite** algo,
  falta decir qué.

### `sentencia` · Sentencia (rediseñada)
- **Niv 2.**
- **Tras 5 golpes** (la cadena del martillo, que ya existe en `StreakState`), el enemigo **revienta**:
  mucho daño al que revienta **y a los de alrededor**.
- Visual: **partículas de encantamiento (`enchant`) + polvo mágico (`effect`)**, ambas vanilla.
- **Niv 2**: hace reventar **también a los enemigos cercanos** (reacción en cadena).
- ⚠️ **A confirmar (ambigüedad del pedido):** *"dañando a enemigos menos al jugador que ataca"* →
  ¿el estallido **no daña al jugador** (como una explosión propia), o **le daña menos**? Son dos
  reglas distintas.
- ⚠️ **Números que faltan**: daño del reventón, radio, y el daño del nivel 2 (¿el mismo, encadenado?).
- ⚠️ Es una **ejecución con contrajuego** (cuesta 5 golpes, y el martillo no resetea al recibir daño
  por diseño) → **dentro de las reglas**, a diferencia del veto C7.

### `yunque` · Yunque (rediseñado)
- **Niv ?** (1 o 2; propongo **2**: L1 sin salto, L2 sin salto ni sprint).
- Los golpes **cargados** aplican **"Aturdimiento"**: no podés **saltar ni correr** durante **1,5 s**.
- **Hook**: `MobEffect` propio + bloqueo de salto (evento de salto, cancelable) + sprint (recompute
  por tick, patrón de las doradas).
- ⚠️ **El nombre choca con vocabulario existente**: el proyecto ya llama **"stun"** a la guardia rota
  (`GuardState`). Un encantamiento llamado "Aturdimiento" que hace *otra cosa* va a confundir. Propongo
  **"Trabazón"**.
- ⚠️ En PvP, prohibir correr 1,5 s es muy fuerte; es el número a medir.

---

# ALABARDA — la multitud

### `barredor` · Barredor (era Hoz Ancha)
- **Niv 2.**
- El barrido alcanza **+1/+2 objetivos** **y** crece el **área** del barrido (radio/ángulo).
- ⚠️ **El nombre "Sweeping" está tomado**: es Sweeping Edge, la onda de filo. ∴ **"Barredor"**.
- ⚠️ **El área creciente choca con el HUD**: el tamaño del cono se dibuja con `ConeGauge`/`ConeSize`.
  Si el barrido crece y el indicador no lo lee, **el indicador miente** -- es el veto C4 del doc v2
  ("Alcance Extendido"). Hay que decidir: el HUD lee el encantamiento, o el área no crece.

### `segunda_pasada` · Segunda Pasada ✅ sin cambios
Niv 2 · si el barrido **no mató a nadie**, devuelve el **30 %/50 %** del cooldown.

### `destripamiento` · Destripamiento (reemplaza a Muralla)
- **Niv ?** (propongo **2**: L1 aplicación, L2 DoT más alto).
- Los ataques **cargados** causan **Sangrado**: **DoT muy alto pero lento**.
- Números: **L1 2 ♥ en 6 s · L2 3 ♥ en 8 s** `?` (a playtestear).
- **Hook**: `MobEffect` propio **Sangrado** (`applyEffectTick`). El Sangrado se **muda** acá desde E6
  (era de Segadora, que ya no existe como tal).
- ⚠️ **Clúster de DoTs**: Sangrado + **Gripa** (daga) + **Wither** (Santa Muerte, azada) + **Fire
  Aspect** (contundentes). Cuatro fuentes de daño por tick. La regla de comer no se rompe (en 1.21.1
  el daño no interrumpe comer), pero el **indicador direccional de daño se satura** → resolver por
  **`DamageType`**, no por encantamiento (así el quinto DoT ya queda cubierto).

---

# ROPERA — el reposicionamiento

### `punta_fina` · Punta Fina ✅ sin cambios
Niv 2 · **+15 %/+25 %** a **1,5 bloques o menos**.

### `audacia` · Audacia (rediseñada)
- **Niv ?** (propongo **2**).
- **Por cada objetivo alcanzado** por el lunge, se acorta el **cooldown posterior** del lunge →
  **lunges en bucle** si hay suficientes enemigos.
- Números: **−10 ticks por objetivo (L1) / −15 (L2)**, con **piso** `?` (propongo **20 ticks**).
- **Hook**: `SpecialHandler.lunge`, leyendo cuántos cobró el cubo del lunge (`alcance × 0.5`).
- ⚠️ **El piso no es opcional**: el lunge tiene **inmunidad doble** (12 ticks + efecto `LUNGE`). Sin
  piso, un bucle de lunges son **i-frames casi permanentes** en medio de un grupo. Es el mismo aviso
  que ya tenía Audacia v1 ("no tocar los i-frames").

### `retirada` · Retirada (rediseñada)
- **Niv ?** (propongo **1**: es un cambio de comportamiento, no una escala).
- **Agachado**, el lunge se **invierte**: te impulsa **hacia atrás** en vez de hacia adelante. Y **al
  dar la retirada das un ataque extra al frente** justo antes de salir.
- **Hook**: el estado de agachado viaja al servidor (`isShiftKeyDown`), y la resolución del lunge ya
  está en `SpecialHandler` → se bifurca ahí.
- ⚠️ **Números que faltan**: cuánto daño hace el ataque extra (¿completo? ¿un %?), y si la retirada
  tiene el mismo cooldown que el lunge.

### `estocada_perforante` · Estocada Perforante ✅ (con números más bajos)
Niv 2 · el golpe **del lunge** ignora armadura. **Baja de 25 %/50 % a 15 %/30 %** `?` (el dueño pidió
menos).

---

# AZADA — el ritmo

### `santa_muerte` · Santa Muerte (reemplaza a Cosecha)
- **Niv 1.**
- **Matar** un enemigo lo hace **explotar en una nube negra de humo putrefacto** que aplica **Wither**
  a los enemigos alrededor.
- Visual: `LARGE_SMOKE` + `SQUID_INK` `?` (a elección; no hay partícula "putrefacta" en vanilla).
- **Hook**: la muerte ya se detecta (`notifyHit` con `killed`) → aplicar el efecto en radio.
- ⚠️ **Wither entra al clúster de DoTs** (ver Destripamiento). **Wither es vanilla** ✓, no necesita
  efecto nuevo.
- ⚠️ Números: radio, duración y nivel de Wither.

### `frenesi` · Frenesí (reemplaza a Surco)
- **Niv ?**
- Conforme atacás, tus ataques se vuelven **más y más rápidos**, en una ventana que se mantiene
  **mientras sigas atacando**.
- ⚠️⚠️ **DOS choques, y son los más serios de toda esta ronda:**
  1. **La velocidad de ataque dura es una decisión cerrada del proyecto**, y el doc v2 §C5 **veta por
     nombre** *"cualquier encantamiento de tempo (`+attack speed`, `-cooldown` de swing)"*, con la
     razón escrita: *"choca con el margen de i-frames (`IFRAME_TICKS = 3`)"*. Acortar el cooldown de
     la azada es exactamente eso. **Si se levanta el veto, tiene que ser una decisión explícita del
     dueño**, no un descuido.
  2. **`frenzy` ya existe**: `FrenzyState` + `miner_frenzy` ("Fiebre del Minero", de las doradas) hace
     *mientras más picás, más rápido*, y **la azada es una herramienta**. Es la misma mecánica con
     otro target.
- ⚠️ Números: cuánto acelera, cuántos stacks, cuánto dura la ventana.

### 🐛 `azada`: la animación está al revés
El dueño reporta que el tajo va **hacia la derecha** y debería ir **hacia la izquierda**. Está en el
perfil de viewmodel (`ViewModelDefaults`: el tajo de la azada usa **112 de yaw**) y probablemente
también en la pose de cuerpo. **Cambio de una constante**, pero hay un test de poses que puede
afirmarla → hay que reescribirlo con la razón nueva, no sólo el número.

---

# MAZA — **sin encantamientos** (decisión del dueño)

Ya tiene varios muy buenos de vanilla (Density, Breach, Wind Burst). Los tres propuestos (**Caída
Astuta**, **Peso Muerto**, **Onda de Impacto**) **quedan descartados**.

---

# DAGA — el tempo

### `apunalar` · Apuñalar (números subidos)
- **Niv 2.**
- **+60 % (L1)** y **casi el doble (L2, +90 %)** `?` si golpeás desde **más de 90° de la espalda**.
- ⚠️ A confirmar: el "+90 %" de L2 es mi lectura de *"casi como el doble"*.

### `sanguijuela` · Sanguijuela ✅
Niv 2 · **+8 %/+14 %** del daño hecho como curación.

### `gripa` · Gripa (reemplaza a Veneno)
- **Niv 2.**
- Al golpear, el enemigo recibe **stacks de "Moco"**: lo hacen **recibir daño aleatoriamente** y
  **saltar con estornudos**. Más stacks = **más daño y estornudos más frecuentes**.
- **Niv 2**: al estornudar sueltan una **nube de moco** que pega a los enemigos cercanos.
- **Arte reutilizado**: el panda ya tiene **partícula y sonido de moco** (`ParticleTypes.SNEEZE` /
  `EntityEvent` del panda) → cero arte nuevo.
- ⚠️ Números que faltan: cuántos stacks máximo, daño por stack, cada cuántos ticks estornuda, radio
  de la nube en L2.
- ⚠️ **"Saltar con estornudos"** = desplazamiento forzado del enemigo. En PvP es control duro; el
  número a medir es la frecuencia.

### `adrenalina` · Adrenalina (reemplaza a Filo Fugaz)
- **Niv 2.**
- Al **recibir daño**: **reduce el daño de ese ataque** y da **Velocidad I (L1) / II (L2)**.
- Cooldown: **30 s (L1) / 15 s (L2)**.
- ⚠️ **Números que faltan**: cuánto reduce el daño (propongo **25 %/40 %**).
- ⚠️ **Objeción de diseño, y es la regla del doc**: *"un encantamiento es de un arma si su mecánica
  no tiene sentido en otra"*. **Adrenalina funciona igual en cualquier arma** → es un **genérico
  disfrazado**, y el eje de la daga (**el tempo**) se pierde: Filo Fugaz era ofensivo/movilidad,
  Adrenalina es defensivo/reactivo. **Decisión del dueño**, pero hay que tomarla sabiéndolo: o se
  acepta como genérico (y entonces debería aplicar a más armas), o se le busca la vuelta para que sea
  de la daga.
- ⚠️ Y el hook cambia de lado: `LivingIncomingDamageEvent`, con un cooldown por jugador (attachment).

---

# PALA — el control del espacio

### `antimaterial` · Pala Antimaterial (reemplaza a Barrido de Tierra)
- **Niv 1.** **Raro y poderoso de conseguir** → `weight` 1 + **`treasure`** (no sale en la mesa).
- **Click derecho carga** (reutilizando la barra de carga del ataque cargado) y suelta un **orbe de
  antimateria** (partículas: esfera de `squid_ink` + `end_rod`). El orbe, al golpear **un bloque o
  una entidad**, **atrae a todas las entidades en un radio** y las **daña tras implosionar**.
- Mucho daño, **cooldown más largo**, y **tarda bastante en cargar**.
- **Hook**: el click derecho de la pala hoy es `NONE` → **hay que darle un cargador propio**. El
  proyectil reutiliza el patrón de `SlashWave` (entidad con posición real, no partícula). La
  atracción puede reutilizar `pullFor` (el mod ya tira hacia adentro con el tridente).
- ⚠️ **Es el encantamiento más caro de todo el documento**: un segundo sistema de carga (el actual es
  del click **izquierdo**), una entidad nueva o reusada, atracción + implosión.
- ⚠️ Números que faltan: tiempo de carga, cooldown, radio de atracción, radio y daño de la implosión.

### `paliza` · Paliza (rediseñada)
- **Niv ?** (propongo **2**).
- Tras **2 golpes normales sucesivos**, el **tercer golpe cargado** da un **knockback horizontal
  enorme** y uno **vertical significativo**.
- ⚠️ El techo de knockback es `MAX_HORIZONTAL = 1.6` / `MAX_VERTICAL = 0.45`, y **la pala ya es el
  empuje máximo del juego (1,365)**. "Enorme" contra un techo de 1.6 no entra: **hay que decidir si se
  sube el techo para este caso**.
- Hook: contador de golpes sucesivos (patrón `StreakState`).

### `zanja` · Zanja — **DESCARTADA sin reemplazo** (decisión del dueño)

---

# HACHA — la que desarma

### `astilladora` · Astilladora (1 nivel)
**Niv 1** · un golpe bloqueado gasta **2** de aguante en vez de 1.

### `mella` · Mella (simplificada)
- **Niv 4** (máximo).
- Aumenta **significativamente** el **desgaste** que el hacha causa a las armaduras.
- Números: **+50 %/+100 %/+150 %/+200 %** de desgaste `?`.
- **Hook**: `ArmorHurtEvent.ArmorEntry.newDamage` -- **ya existe** (`ArmorWearHandler`, el desgaste
  doble del hacha cargado) → es una multiplicación, no un sistema.

### `filo_astillado` · Filo Astillado (rediseñado)
- **Niv 2.**
- **Por cada pieza de armadura** que lleva el enemigo, hace **daño extra**.
- Números: **+6 %/+10 % por pieza** `?` (máx 4 piezas → +24 %/+40 %).
- **Hook**: bonus por objetivo, contando los slots de armadura no vacíos.

---

# ESPADA — la guardia sin escudo

### `guardia_firme` · Guardia Firme (1 nivel)
**Niv 1** · el **primer golpe bloqueado de cada 3 s no gasta aguante**.

### `segundo_aliento` · Segundo Aliento ✅
Niv 1-2 · al **romperse tu guardia** (stun), tu siguiente golpe en 3 s pega **+15 %/+25 %** y empuja
**+50 %**.

### `aventurero` · Aventurero (reemplaza a Filo Deliberado)
- **Niv 5** (máximo).
- Los enemigos **sueltan más experiencia** al morir.
- Números: **+20 % por nivel** `?` (L5 = +100 %).
- **Hook**: `LivingExperienceDropEvent` (NeoForge) → multiplicar el drop.
- ⚠️ Misma objeción que Adrenalina: **un encantamiento de XP funciona igual en cualquier arma** → es
  un genérico disfrazado, no identidad de la espada.

---

# ARCABUZ — la distancia y la mecha

### `culata` · Culata (3 niveles, menos knockback)
**Niv 3** · la bayoneta empuja. **Menos knockback que la propuesta v1** (era +60 %/+100 %): propongo
**+25 %/+50 %/+75 %** `?`.

### `mecha_corta` · Mecha Corta ✅
Niv 2 · la mecha baja de **20 → 15/10** ticks.
⚠️ **Trampa conocida:** `FUSE_TICKS` está atado al `.mcmeta` del `arcabuz.png` (5 ticks × 4 frames =
20) y `ModelDeclarationTest` lo afirma → **requiere trabajo de cliente**.

### `rey_de_las_moscas` · Rey de las Moscas (reemplaza a Pólvora Negra)
- **Niv 1.** **Incompatible con Mungus.**
- El arma usa **carne podrida** de munición en vez de Firestone, y dispara una **nube de moscas**
  (**partícula nueva**) sobre los enemigos: **mucho daño, muy rápido**.
- Tiene la peculiaridad de **"infectar" a los jugadores cercanos**.
- Da **más rango y radio** que el disparo normal, **a costa de un daño total menor**.
- ⚠️ **Cambiar la munición** es tocar el sistema de carga (hoy: 1 Firestone al encender). Es una rama
  por encantamiento.
- ⚠️ **"Infectar a jugadores cercanos"** es lo más delicado: contagio entre jugadores, en PvP y en
  multijugador. **Falta la regla**: ¿se contagia por proximidad? ¿el infectado infecta a otro? ¿es un
  `MobEffect` con radio? Sin esa regla no se puede implementar.
- ⚠️ Números: daño total (menor), rango/radio (mayores), frecuencia del tick de daño.

### `mungus` · Mungus (nuevo, incompatible con Rey de las Moscas)
- **Niv 1.**
- El arma consume **10 Firestone** y tarda **×2 en cargar**, pero lanza un **proyectil en forma de
  Firestone enorme** con **trail de lava** que al impactar causa una **explosión tipo mini-nuke
  (×10 TNT)**. El proyectil tiene **gravedad** y es **lento**.
- **Compatible con Mecha Corta y Pólvora Seca**, que **se ajustan a la velocidad** de este
  encantamiento (o sea: la base ×2 se acorta, no se reemplaza).
- ⚠️ **"×10 TNT" hay que aterrizarlo**: la potencia de una TNT vanilla es **4** → ×10 = **40**. Eso
  arrasa terreno. Y **el mod no rompe bloques en ninguna parte**: hay que decidir si esta explosión
  **rompe bloques** (rompe la regla del mod) o **no** (es una explosión de entidad).
- ⚠️ **La incompatibilidad necesita código**: nuestros encantamientos son **registro Java**, no dato,
  así que **no llevan `exclusive_set` declarativo** (anotado en el doc v2 §I.6). Se implementa con un
  gate en el registro/la mesa.
- ⚠️ Números: daño de la explosión, radio, velocidad y gravedad del proyectil.

---

# GENÉRICOS

| Encantamiento | Clase | Niv | Efecto | Nota |
|---|---|---|---|---|
| **Contragolpe** (`riposte`) | guardia | 2 | tras bloquear de verdad, el siguiente golpe ignora **25 %/50 %** de armadura | ✅ **+ shotel** → pero **armado desde el parry**, no del bloqueo (el sabre no puede cubrir) |
| **Ensarte** | largas (alabarda, tridente) | 2 | la estocada atraviesa y pega a un enemigo alineado detrás: **50 %/100 %** | ✅ sin cambios |
| **Quebranto** | pesadas (martillo, hacha, maza) | 2 | cada golpe baja la armadura del rival 4 s: **−12 %/stack máx 2** → **−18 %, máx 3** | ✅ **con menos porcentaje**: propongo **−8 % por stack** y tope 3 |

---

# Lo que falta decidir (para el dueño)

1. **El tag**: ¿los 6 van a `in_enchanting_table`, y `steel_reflex` además a `tradeable`? ¿Alguno a
   `treasure` (fuera de la mesa)?
2. **Desvío Vampírico**: ¿el robo **puede matar** al enemigo, o la vida actual no baja?
3. **Inercia**: ¿el techo vertical sube, o se queda clampeado en 0.45? ¿Y qué "daño de altura" habilita?
4. **Sentencia**: ¿el reventón **no daña al jugador**, o le daña menos?
5. **Barredor**: si el área crece, ¿el HUD del cono lee el encantamiento? (si no, el indicador miente)
6. **Frenesí**: levantar el veto de "tempo" del §C5 **es una decisión tuya**, y además la mecánica ya
   existe como `miner_frenzy`. ¿Se duplica, se renombra o se descarta?
7. **Adrenalina y Aventurero**: funcionan igual en cualquier arma → son genéricos disfrazados. ¿Se
   aceptan como genéricos, o se les busca la vuelta para que sean de su arma?
8. **Mungus**: ¿la explosión **rompe bloques**?
9. **Rey de las Moscas**: ¿cómo se contagia la infección entre jugadores?
10. **Todos los `?`**: son números que propuse y que necesitan tu OK (o el tuyo cambiado).
11. **Las nueve colisiones de nombres** con `encantamientos-armas-nuevas.md` §G: este documento es el
    canónico, ¿confirmás?
