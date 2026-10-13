# FX — diseño (2026-10-12)

> **v2.** La v1 proponía agrandar el `DAMAGE_INDICATOR`; **el dueño la rechazó por clutter y propuso
> algo mejor**: que las partículas sean una **lectura del daño** (puntos de 4 px, coloreados) y que el
> **flash del modelo** represente la vida. Esta versión es ese diseño, con el mecanismo de vanilla
> **verificado en las fuentes de 21.1.250**, ⊘ de memoria.

## 0 · Inventario (lo que hay, verificado en código)

| Canal | Qué hay | Dónde |
|---|---|---|
| Pantalla | viñeta roja con vida baja (14→4) + flash rojo plano al recibir daño | `DamageOverlayLayer` |
| Pantalla | crosshair que crece y se tiñe (`HIT`/`KILL`/`GUARDED`) + corchetes + orbes | `CrosshairFeedback`, `CombatFeedback` |
| Cámara | shake por **impulsos** con decaimiento, alcance 40, caída cuadrática | `CameraShake` |
| Partículas propias | `slash`, `sangre`, `mosca`, `purified_effect` | `ModParticleTypes` |
| Partículas nuevas (arte listo, ⊘ cableadas) | **`hurt` (4×4)**, **`cargado_hurt` (4×4)**, **`curar` (5×5)** | `textures/particle/` |
| Sonidos | 34 ids, **todos de arma o de reactor. Ninguno de estado del jugador** | `sound/ModSounds` |

**El diagnóstico:** el mod tiene buen feedback **del arma** y casi nada de **lo que te pasa a vos**.
Recibir —el momento más importante del combate— es el más mudo.

---

## 1 · La lectura de daño (tu diseño)

### 1.1 Son **dos** preguntas, y hay que separarlas

Tu frase mezcla dos ejes y conviene decidirlo explícito, porque decide todo el resto:

| Eje | La pregunta | Ejemplo |
|---|---|---|
| **A · el split** | *¿qué hizo el enemigo con mi golpe?* | entró / atravesó / lo paró la armadura / lo frenó una poción |
| **B · el tipo** | *¿de dónde vino el daño?* | fuego, veneno, wither, caída, mío |

Tu ejemplo —*25 % rojas (daño hecho), 25 % blancas (daño puro), 50 % azules (llevaba armadura), y el
veneno en verde*— usa **A** para el golpe y **B** para el veneno. O sea: **el color del golpe es el
split; el color del tick es el tipo.** Eso es coherente y es lo que implemento abajo. **Confirmame si
es así** — si querés que el tipo también tiña el golpe, es otro mapa y otras reglas de precedencia.

### 1.2 El color del SPLIT (el golpe)

| Qué pasó con tu golpe | Color | rgb | Cómo se sabe |
|---|---|---|---|
| **Entró** (vida que bajó) | rojo sangre | `0xE11B1B` | el final menos lo que se perdió |
| **Puro** (atravesó armadura) | blanco | `0xF2F2F2` | `bypasses_armor` |
| **Lo paró la armadura** | azul acero | `0x4A7FE0` | `CombatRules.getDamageAfterAbsorb` |
| **Lo frenó una poción** (Resistencia) | gris | `0x9A9A9A` | `getDamageAfterMagicAbsorb` |
| **Lo frenó un escudo** | azul gris | `0x8296AD` | **el color ya está decidido** en `CombatFeedback.GUARDED` |

⚠ **El rojo ya está gastado dos veces** (`HIT` y `KILL` en `CombatFeedback` son dos rojos distintos).
Acá conviene **un solo rojo** para "entró", ⊘ tres.

### 1.3 El color del TIPO — la tabla de los 50

**Decir "un color por tipo" con 48 tipos de vanilla no se aprende.** Nadie memoriza 48 colores, y un
código que nadie memoriza ⊘ es un código: es decoración. ∴ **12 familias** que cubren los 48, y el
jugador aprende 12 sin que nadie se lo diga. **Todos los tipos de 1.21.1 están asignados** (leídos de
`DamageTypes.java`, ⊘ inventados):

| Familia | Color | rgb | Tipos de vanilla |
|---|---|---|---|
| **Filo / cuerpo a cuerpo** | rojo | `0xE11B1B` | `player_attack`, `mob_attack`, `mob_attack_no_aggro`, `thorns` |
| **Proyectil** | granate | `0x8C1A14` | `arrow`, `trident`, `mob_projectile`, `spit`, `wind_charge`, `thrown`, `fireworks` |
| **Fuego** | naranja | `0xFF7A18` | `in_fire`, `on_fire`, `campfire`, `hot_floor`, `lava`, `fireball`, `unattributed_fireball` |
| **Explosión** | amarillo | `0xFFD24A` | `explosion`, `player_explosion` |
| **Magia / alma** | violeta | `0xB44CE0` | `magic`, `indirect_magic`, `dragon_breath`, `sonic_boom`, `wither_skull` |
| **Podredumbre** | gris oscuro | `0x4A4A4A` | `wither` |
| **Hambre / deshidratación** | oliva sucio | `0x8C8C4A` | `starve`, `dry_out` |
| **Frío** | celeste | `0x7FD8FF` | `freeze` |
| **Caída / impacto** | blanco sucio | `0xD8D8D8` | `fall`, `fly_into_wall`, `falling_block`, `falling_anvil`, `falling_stalactite`, `stalagmite` |
| **Asfixia / aplastamiento** | azul acero oscuro | `0x2E5A8C` | `drown`, `in_wall`, `cramming` |
| **Vegetal / espinas** | verde oliva | `0x8FA83C` | `cactus`, `sweet_berry_bush`, `sting` |
| **Eléctrico** | blanco azulado | `0xE8F4FF` | `lightning_bolt` |
| **Vacío / administrativo** | negro | `0x141414` | `out_of_world`, `generic`, `generic_kill`, `outside_border`, `bad_respawn_point` |

**Y los dos del mod**, que ya existen en `ModDamageTypes`:

| Tipo | Color | rgb | Por qué ese |
|---|---|---|---|
| `cortescombat:axe_charged` | rojo intenso | `0xFF3B1B` | el hacha cargada **conserva** la armadura y la destroza al doble → ⊘ es "puro" |
| `cortescombat:warhammer_ignored_armour` | blanco | `0xF2F2F2` | **es** el que atraviesa → comparte el color con el "puro", ⊘ uno propio |

**Los efectos del mod** (veneno propio): `MOCO`, sangrado de `destripamiento`, `AntiHealing` →
**verde enfermo `0x5FA83C`**. Es la familia "veneno" que tu ejemplo pide.

### 1.4 El conteo — el número de vanilla, repartido

`DamageParticles.countFor` **ya** implementa la regla de vanilla: `(int)(damage * 0.5)`. Ese número se
queda como **total**, y los colores se reparten por proporción con **mayor resto** (así el total
coincide siempre y ⊘ hay un punto que aparece o desaparece por redondeo).

Dos propiedades que salen gratis y valen:

- **El que quiera saber el daño, cuenta.** La regla de vanilla sigue intacta.
- **El que quiera saber si el enemigo resiste, mira el color.** Es la lectura nueva, y es la que
  contesta *"¿le estoy haciendo algo?"* — que hoy ⊘ tiene respuesta en pantalla.

### 1.5 El golpe pesado: `cargado_hurt`

**Misma mecánica, misma paleta, otra textura** — `cargado_hurt` en vez de `hurt`. Es exactamente lo
que pediste: el crit y el cargado se distinguen **por forma**, ⊘ por color, así que ⊘ hay que inventar
un color nuevo ni un canal nuevo. Y el `hurtTime` del mob ya es más largo para el cargado, así que la
ventana también se distingue sola.

### 1.6 Lo que hace falta en la tubería (el trabajo real)

El split **no se puede reconstruir después**: `CombatRules.getDamageAfterAbsorb` corre **en el
defensor, adentro de `hurt`**, y para cuando el daño se aplicó el desglose ya se perdió. ∴

- **`DamageResolver` / `CombatEngine` tienen que emitir el desglose** en el momento en que lo conocen:
  `raw`, `pierced`, `absorbedByArmor`, `absorbedByEffect`, `final`. Los cuatro números son
  **restas de valores que ya están en la mano** — ⊘ hace falta un segundo cálculo.
- **Y no tiene que viajar por la red.** El diseño v1 decía que sí, y era falso: los puntos se
  spawnean **server-side** con `sendParticles`, igual que el slash y la sangre, así que todos ven lo
  mismo y no hay nada que predecir. El split se mide y se consume en el mismo lugar.
- **El `axe_charged` es el caso que hace ruido**: su daño **no** atraviesa armadura, así que su split
  es rojo ⊕ azul, ⊘ blanco. El tipo dice "hacha", el split dice la verdad. **Los dos ejes conviven y
  ⊘ se contradicen.**

---

## 2 · El flash del modelo (tu diseño) — **verificado en las fuentes**

### 2.1 Cómo funciona, de verdad

`OverlayTexture` es una **`DynamicTexture` de 16×16** que el juego llena así:

```java
if (i < 8)  nativeimage.setPixelRGBA(j, i, -1308622593);          // filas 0-7: ROJO, alpha 0xB2
else        nativeimage.setPixelRGBA(j, i, k << 24 | 0xFFFFFF);   // filas 8-15: BLANCO, alpha por COLUMNA
// k = (1 - j/15 * 0.75) * 255  →  columna 0 = alpha 255, columna 15 = alpha 63
```

Y el seam es **una línea, `static`**:

```java
public static int getOverlayCoords(LivingEntity e, float u) {
    return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(e.hurtTime > 0 || e.deathTime > 0));
}
```

**Tres consecuencias que cambian el diseño:**

1. **El rojo es un booleano, ⊘ un gradiente.** `v(hurt)` devuelve **3 o 10** — fila 3 (rojo) o fila 10
   (blanco). Las ocho filas rojas son **idénticas**, así que **el overlay no sabe decir "medio rojo"**.
2. **`u` es la columna, y en las filas rojas ⊘ hace nada** (las 16 columnas rojas son iguales). **En las
   blancas sí**: `u` elige la intensidad del blanco. Por eso el flash blanco del creeper es un
   gradiente y el rojo ⊘.
3. **`deathTime > 0` YA dispara el rojo.** El mob que se muere **ya flashea rojo** en vanilla.

### 2.2 "La mitad del modelo" ⊘ es lo que el overlay sabe hacer

**`getOverlayCoords` devuelve UN entero para el modelo entero.** El overlay es **uniforme**: ⊘ hay
forma de que "la mitad del modelo" se pinte con él. Tu idea **necesita per-vertex**.

### 2.3 Las dos vías, y cuál es la tuya

**(A) Intensidad en vez de cobertura — barata, pero ⊘ es tu idea.**
Reescribir las filas rojas con un gradiente **por columna** y pasar `u = f(vida)`. Da *"el rojo es más
fuerte cuanto menos vida"*, uniforme sobre el cuerpo.
- ✅ ~1 mixin, sin tocar vértices.
- ⚠ **Es global**: la textura la comparten **todos** los mobs, así que el flash de vanilla también
  cambia de fuerza. Puede ser deseable (querés "mucho más notorio") pero hay que elegirlo.

**(B) Cobertura per-vertex — es tu idea, y es factible.**
Un **wrapper de `VertexConsumer`** que, vértice por vértice, elige entre `pack(0, true)` y `NO_OVERLAY`
según la **Y del vértice**. `VertexConsumer.addVertex(x, y, z)` **da la posición**, así que el wrapper
puede decidir sin tocar el modelo.
- **El umbral sale de la vida, y la barra se VACÍA**: a vida llena flashea todo, a 50 % la mitad de
  abajo, y a cero no flashea nada.

  > **Corregido el 2026-10-12, contra el playtest.** Acá decía lo contrario -- *"a 50 % de vida flashea
  > el 50 % del cuerpo, y crece hacia arriba mientras baja la vida (la herida trepa). A 10 %, el 90 %"*
  > -- y el dueño reconoció la barra que se vacía como la correcta: *"comienza completa y se va bajando
  > hasta vaciarse"*. La versión que trepaba **se implementó, se jugó y se descartó**.

- ✅ Es exactamente lo que describiste, y ⊘ necesita shader ni textura nueva.
- ⚠ **Cuesta un mixin en el punto donde el modelo se dibuja** (`LivingEntityRenderer#render` o
  `Model#renderToBuffer`) y un wrapper. **Es la opción que recomiendo.**
- ⚠ **Y el umbral se MIDE, ⊘ se deduce.** Acá decía *"la Y del modelo es local (un jugador mide ~32
  unidades antes de la matriz), así que el umbral se normaliza contra la altura del modelo"*, y la
  premisa era falsa en las dos mitades: la Y de un vértice llega en **bloques** (el modelo divide por
  16 antes de transformar) y **relativa a la cámara** (el dispatcher ya trasladó), así que el modelo
  mide ~2 bloques y no 32 unidades. Deducir el rango de la caja de colisión falló **dos veces**;
  lo que funciona es un pase con un `VertexConsumer` que no dibuja y sólo mide. Ver
  `DamageFlash#barCut`.

### 2.4 El flash blanco al morir ya tiene seam

`getOverlayCoords(e, u)` **ya recibe `u`**, y `pack(u(1.0F), false)` da **blanco al 75 %**. O sea: **el
flash blanco de la muerte ⊘ hay que inventarlo** — se maneja desde `getWhiteOverlayProgress(entity,
partialTicks)` (el método que el creeper ya sobreescribe) y sale casi gratis.

⚠ **Cuidado con el orden**: hoy `deathTime > 0` da **rojo**, así que morir **ya** flashea rojo. Si
además querés blanco, hay que **elegir cuál gana** — y lo natural es que el rojo **se apague** y el
blanco **entre** en los últimos ticks.

---

## 3 · La pantalla

### 3.1 Los tipos que faltaban — tenés razón, y son **tres más**

Tu lista (ahogamiento, wither, hambre) se suma al mapa de §1.3 — **ya están los tres asignados ahí**
(`drown` → azul acero, `wither` → gris oscuro, `starve` → oliva sucio). Faltaban porque la v1 del doc
solo listaba los "obvios" (fuego, veneno, caída, explosión).

**Y la tabla completa de §1.3 cubre los 48**, así que ⊘ queda ninguno sin color. Los que más se van a
ver en un mundo normal: `fall` (blanco sucio), `drown` (azul), `cactus` (verde oliva), `freeze`
(celeste), `starve` (oliva sucio) y `wither` (gris oscuro).

### 3.2 Cerca de morir: desaturar **y** borrar

Dos efectos, y **tienen costos muy distintos** — conviene saberlo antes de elegir:

| Efecto | Cómo | Costo |
|---|---|---|
| **Desaturar** | un `fill` gris con alpha, encima de todo | **Trivial** — es la misma capa que la viñeta, un `setColor` más |
| **Borrar (blur)** | un **post-effect**: `PostChain` + un `post_effect/*.json` | **Caro** — ⊘ hay API pública para encadenar un post-effect al render del mundo; exige un mixin sobre `LevelRenderer` |

**Propuesta:** desaturar **ya** (es gratis y se nota muchísimo), y el blur **después y con su propia
ronda**, porque es lo único de toda esta lista que pide tocar el pipeline de render.

- La desaturación **sube con la vida que baja** (misma curva que la viñeta: 14 → 4), pero **⊘ satura a
  1**: si queda todo gris, el jugador deja de ver los colores de las partículas de §1 — o sea, **el
  efecto se come la lectura**. Techo ~0,55.
- Y **el blur, si algún día entra, tiene que ser de radio chico** y crecer: un blur de radio grande
  tapa la crosshair, que es lo único que el jugador necesita cerca de morir.

---

## 4 · El shake — los tres disparadores que faltaban

> **Estado al 2026-10-13: implementado, y son OCHO.** El diseño de abajo es el original de esta
> ronda y quedó corto en dos cosas. **La tabla de amplitudes vive en `fx/ShakeKind`** y no en cada
> llamador: nuke del reactor 8,0° **a 160 bloques**, TNT 5,0° a 40, matar 2,4°, guardia rota 2,0°,
> parry 1,8°, golpe cargado 1,6°, bloqueo 1,2°, recibir daño 0,8°. Las fuentes están en
> `CombatEngine` (matar + cargado), `GuardHandler` (bloqueo + guardia rota), `ParryHandler`,
> `NetherReactorBlockEntity.detonateStar` (la nuke), `fx/ExplosionShake` y `fx/HurtShake`.
>
> **1 · Y `CameraShake` no funcionaba.** `Mth.sin` es una tabla y su índice **satura** con ángulos
> grandes, y el ángulo era el **reloj del mundo** por la frecuencia: con el reloj del mundo real
> (`Time = 285340`) el `3,1` daba **cero**. Ver `ShakePhase`. Eso también mataba el roll del reactor
> (su `1,9`), que ya está arreglado. La nota de abajo sobre `PITCH_HZ` queda sin efecto: la fase sale
> de la **edad del impulso**, así que un impulso de 5 ticks no hereda la frecuencia de uno de 14.
>
> **2 · El alcance es un dial aparte, y estaba fijo en el cliente.** Cuarenta bloques, el de un
> reventón común -- así que **ningún número podía llegar más lejos**: pasado el alcance el temblor no
> era chico, era **cero**. Ahora viaja en el payload y `amplitudeAt` lo recibe, que es lo que permite
> que una nuke se sienta a 160 bloques y una flecha a dos.
>
> **3 · El riesgo era que sobre, no que faltara.** Con ocho fuentes repartidas, *"¿cuánto sacude
> esto?"* dejó de tener respuesta, así que la tabla es la respuesta y un test la audita.
> `ShakePhase.clampTotal` recorta la **suma** a 10,0° -- los impulsos se acumulan, así que cinco mobs
> pegando en el mismo tick son cinco impulsos -- y el techo está **por encima del más grande** para
> que un solo impulso no se recorte a sí mismo.

`CameraShake` ya tiene decaimiento, dos frecuencias que no son múltiplo, caída cuadrática, filtro por
dimensión y un `ClientConfig.CAMERA_SHAKE` para apagarlo. **Hoy lo usa una sola cosa: el orbe.** Los
tres que pediste, con lo que cada uno necesita:

| Disparador | Amplitud | Qué falta |
|---|---|---|
| **Matar a un enemigo** | `0,5°`, pitch | el cliente ya sabe que mató (el `CombatFeedback.KILL` sale de ahí) → **es el más barato de los tres** |
| **El arcabuz al disparar** | `0,8°`, roll | el disparo **ya** tiene culatazo (`ArcabuzMuzzle.RECOIL`) y es server-side → **el shake se suma al culatazo, ⊘ lo reemplaza** |
| **Flechas que pasan al lado** | `0,3°`, roll hacia el lado | **el único que necesita lógica nueva**: ⊘ hay evento de "casi me pega". Hay que escanear los proyectiles cercanos y medir la **distancia mínima a la trayectoria**, ⊘ la distancia al tirador |

⚠ **El de las flechas es un escaneo por tick**, y el proyecto ya tiene la lección del reactor
(*"algo pide sonidos cada tick y nunca libera handles"*). ∴ **no es un `getEntities` cada tick**: es un
escaneo **cada N ticks** (3 alcanza: una flecha cruza ~3 bloques en 3 ticks) y **solo de proyectiles**.

⚠ **Y los tres son impulsos cortos.** `PITCH_HZ 3,1` está calibrada para 14 ticks; en un impulso de 5
son dos tirones. **El golpe/la flecha necesitan su propia frecuencia, más baja** — es el número a
playtestear.

---

## 5 · Las muertes

### 5.1 El jugador: el alma

**`ParticleTypes.SOUL`** existe (verificado en `ParticleTypes.java`) y es la que sube del soul sand /
soul fire. **Exclusiva del jugador**, como pediste:

- **Sube**, ⊘ cae: `gravity 0` y una velocidad `+Y` inicial — el opuesto exacto de `SangreParticle`
  (`gravity 3` + `hasPhysics`).
- **Vive más que un golpe**: 40–60 ticks, y **se desvanece**, ⊘ se corta.
- ⚠ **El jugador no se puede dibujar después de morir**: la partícula sale **en el momento de la
  muerte** y sigue viva cuando la entidad ya no está. Eso está bien y es lo que la hace un alma — pero
  significa que **el emisor tiene que ser el server en el `LivingDeathEvent`**, ⊘ el renderer.

### 5.2 Cualquier entidad: reventar en sangre

**`SangreParticle` ya está hecha y ya tiene la física correcta** (`gravity 3` + `hasPhysics` →
`stoppedByCollision` congela la gota al chocar, y la salpicadura sale gratis). Solo falta **el volumen**:

- **Un golpe = un salpicón** (lo que ya hace). **Una muerte = un reventón**: ~20–30 gotas con
  velocidad **hacia afuera y arriba**, ⊘ un chorro.
- ⚠ **`sendParticles` ⊘ puede elegir dirección**: con conteo > 0 reparte `gaussian() * maxSpeed` **por
  eje con el mismo sigma**. Solo hay nube **isotrópica**. Para un reventón está bien (un reventón *es*
  isotrópico), pero **el "hacia arriba" hay que darlo con un offset de posición o con una entidad**, ⊘
  con el paquete.
- ⚠ **Y la sangre es roja, ⊘ sirve para un esqueleto.** Un `SangreParticle` tintado por el tipo de
  mob (o por la familia) resuelve los no-muertos y los golems con la misma clase.

---

## 6 · La curación: `curar`

**Los corazoncitos verdes que suben.** El arte ya está (5×5). Es la partícula más simple de las tres y
la que más falta: **el ciclo de comida del proyecto cura todo el tiempo** (`SurvivalTicker`, 1 hp cada
segundo) y **hoy ⊘ se ve**.

- **Sube, ⊘ cae**: `gravity` **negativa** o cero con velocidad `+Y`. `ParticleTypes.HEART` de vanilla
  ⊘ sirve de referencia porque **cae** (es de cría, no de curación).
- **Pocos y lentos**: 1–2 cada ~10 ticks. La regen es **pasiva y constante**, así que una partícula por
  tick convertiría la pantalla en un acuario.
- **Y el momento en que importa es el otro**: cuando el jugador **come** para curarse, y cuando
  `sanguijuela` le devuelve vida. Ahí sí conviene un puñado.

---

## 7 · El bug que apareció revisando esto

**`CortesCombatClient:129` registra el tipo `SANGRE` donde quería `PURIFIED`.**

```java
// L129 — el comentario dice "The purification plume"
event.registerSpriteSet(ModParticleTypes.SANGRE.get(), ... PurifiedParticle ...);
// L138 — el mismo tipo otra vez, con la partícula correcta (y la pisa)
event.registerSpriteSet(ModParticleTypes.SANGRE.get(), ... SangreParticle ...);
```

**`purified_effect` ⊘ tiene proveedor** → `ParticleEngine` devuelve `null` y el cliente **descarta la
partícula en silencio**. Y **`PurificationEffect:75` la spawnea**: el efecto del cobre **cobra el daño
y ⊘ se dibuja**. Se arregla con **una palabra**.

**Y deja la pregunta que vale más que el fix: ¿qué más está registrado y ⊘ se dibuja?** Un guard que
camine `ModParticleTypes` y afirme que **cada tipo tiene proveedor** es de los baratos que pagan solos
— el mismo que `EffectAssetsTest` ya hace para los iconos de efecto.

---

## 8 · Las 3 partículas que añadiste — qué falta para usarlas

**Verificado:** `hurt.png` y `cargado_hurt.png` son de **4×4**, `curar.png` de **5×5**, y las tres
están en `textures/particle/` (la ruta del atlas de partículas). **⊘ están cableadas**: `ModParticleTypes`
sigue teniendo cuatro tipos.

Lo que falta, y son **cuatro piezas por partícula** (el patrón ya está en el repo, ⊘ hay que inventarlo):

1. **`assets/cortescombat/particles/<id>.json`** con `{"textures": ["cortescombat:<id>"]}` — es lo que
   le da el sprite al `registerSpriteSet`. **Sin este archivo el proveedor recibe un `SpriteSet`
   vacío**, y el síntoma es el que el proyecto ya pagó: la partícula se registra, se spawnea, y **⊘ se
   ve nada**.
2. **El registro en `ModParticleTypes`** (3 líneas).
3. **El proveedor en `CortesCombatClient.registerParticles`** (3 bloques, copiando el de `SANGRE`).
4. **La clase de la partícula.**

**Y la decisión de diseño que decide la forma de las dos primeras:**

- **`hurt` y `cargado_hurt` necesitan un COLOR** (son 4 px grises, y §1.2/§1.3 piden ~15 colores).
  ∴ **tienen que ser `ParticleType<HurtParticleOptions>`** con el rgb en las opciones — el patrón
  exacto de **`SlashParticleOptions`**, que ya existe y ya resuelve "un tipo, N variantes" (allí el
  sprite, acá el color). **Un tipo por color sería 15 tipos y 15 registros**, que es la forma cara.
- **`curar` ⊘ necesita color**: el verde está en el arte → `SimpleParticleType` alcanza, como
  `sangre`/`mosca`.

⚠ **Y las opciones necesitan `StreamCodec`** (`StreamCodec.composite` **topa en 6 componentes** en
1.21.1 — un rgb son 3, así que hay margen).

---

## Orden sugerido

1. **El fix del `PURIFIED`** (una palabra) + **el guard** de "todo tipo de partícula tiene proveedor".
2. **Cablear las 3 partículas** (§8) — el arte está listo, y sin esto nada de §1 ni §6 existe.
3. **El desglose del daño en la tubería** (§1.6) + los puntos coloreados — es el corazón de tu idea y
   el trabajo más grande.
4. **El flash del modelo por cobertura** (§2.3-B) — el wrapper per-vertex.
5. **La desaturación** (§3.2) — barata y muy visible.
6. **El shake de matar** (§4) — el más barato de los tres disparadores.
7. **`curar`** (§6), **las muertes** (§5), **el flash blanco** (§2.4).
