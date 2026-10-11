# Encantamientos únicos por arma — plan v1 (borrador para revisar)

> **Estado: BORRADOR, nada implementado.** Nace del pedido del dueño (2026-10-10): en vez de 3 por
> **familia** (el §G de `encantamientos-armas-nuevas.md`), **2-3 por arma** (máx 4), **únicos de esa
> arma**, más **2 para pala y azada** enfocados en el combate. 11 armas → ~32 encantamientos.
>
> **Qué está hecho de esta lista, al 2026-10-10: nada.** Lo que sí está hecho son los que ya existían
> como aprobados y quedaron **fuera** de este documento (por eso no se repiten acá): `steel_reflex`
> (shotel), `audacity` (ropera), `fleeting_edge` (daga), `rhythm_of_iron` (martillo), `riposte`
> (espada) y `dry_powder` (arcabuz) -- uno por arma, los seis en `data/cortescombat/enchantment/`.
> Este documento es **la capa de encima**: los que dan a cada arma su eje propio.
>
> **Antes de escribir los ~32 hay que cerrar las 4 decisiones del final**, y una de ellas cambia la
> forma del trabajo: si se recorta a las ⭐, son ~22 en vez de 32.

## La regla que decide si un encantamiento entra

> **Un encantamiento es de un arma si su mecánica no tiene sentido en otra.** Si se puede copiar a
> otra arma sin cambiar nada, no es identidad: es un genérico disfrazado.

Los genéricos ya existen y están decididos (E7 Contragolpe = guardia, Ensarte = largas, Quebranto =
pesadas). Todo lo de este documento tiene que pasar el test de "¿esto podría tenerlo otra arma?".

**Los ejes, uno por arma** — cada lista sale de acá y no de una lluvia de ideas:

| Arma | Su eje | Por qué es SUYO |
|---|---|---|
| Espada | la guardia sin escudo | es la única que bloquea sin gastar la off-hand |
| Hacha | destrozar la armadura | ya la desgasta al doble a carga completa |
| Pala | el control del espacio | tiene el empuje máximo del juego |
| Maza | la caída | su daño entero viene de la altura |
| Azada | el ritmo sin wind-up | no carga, y su click derecho es un tajo ancho |
| Ropera | el reposicionamiento | su click derecho la mueve |
| Alabarda | la multitud | su barrido pega a cuatro |
| Martillo | el compromiso | escala por golpe consecutivo, sin tope |
| Daga | el tempo | 6 ticks: la más rápida |
| Shotel | la lectura del golpe | su parry devuelve el golpe parriado |
| Arcabuz | la distancia y la mecha | es la única a distancia |

---

## ESPADA — la guardia como recurso

- **Guardia Firme** (2) · el primer golpe bloqueado de cada 3 s **no gasta aguante**. *Único:* sólo
  la espada tiene una guardia propia que se puede administrar; el escudo bloquea todo o nada.
  *Hook:* `GuardHandler.onShieldBlock`, en la rama del gasto.
- **Segundo Aliento** (1-2) · al **romperse tu guardia** (stun), tu siguiente golpe en 3 s pega
  **+15 %/+25 %** y empuja **+50 %**. *Único:* la espada es la única cuyo bloqueo se puede *romper*
  y dejar al jugador con una deuda que cobrar. *Hook:* donde `GuardHandler.breakGuard` marca el stun.
- **Filo Deliberado** (2) · el golpe **sin cargar** pega **+10 %/+18 %**. *Único:* es la única arma
  donde cargar **pierde** DPS por diseño; el encantamiento le da precio a la otra mitad.
  *Hook:* `DamageResolver`, por `chargeRatio < FULL_CHARGE`.

## HACHA — la que desarma

- **Astilladora** (2) · un golpe bloqueado gasta **2** puntos de aguante en vez de 1. *Único:* el
  hacha ya stunea de un golpe; esto escala la única cosa que hace y ninguna otra hace.
  *Hook:* `GuardHandler`, en el `state.block(...)`.
- **Mella** (2) · el golpe **cargado** deja la armadura del objetivo con **+1 punto de desgaste
  extra por golpe** durante 6 s (acumula 3). *Único:* profundiza el `ArmorWearHandler` que ya existe
  sólo para el hacha. *Hook:* `ArmorHurtEvent`, junto al desgaste doble.
- **Filo Astillado** (2) · **+12 %/+20 %** contra enemigos **sin armadura**. *Único:* la inversa
  exacta del martillo (que premia pegarle al blindado). Las dos armas pesadas, opuestas.

## PALA — el control del espacio *(pedido explícito)*

- **Barrido de Tierra** (2) · el click derecho (especial) lanza un **cono de empuje** de 4/6 bloques
  que empuja a todos los del arco **sin daño**. *Único:* el empuje es la identidad de la pala y hoy
  no tiene especial (el `SWEEP` que tenía era copiado de la alabarda y se sacó).
- **Paliza** (2) · el golpe cargado empuja **el doble** y **levanta** ligeramente (0,25 de Y).
  *Único:* la pala ya tiene el empuje máximo; esto es ese número, no otro sistema.
- **Zanja** (1) · click derecho apuntando al piso: **excava un bloque de profundidad en un área de
  3×3**. *Único:* utilidad de combate que sólo tiene sentido en la herramienta que empuja — abrir
  espacio o cerrarlo. *(Riesgo: romper bloques es intrusivo; a playtest.)*

## MAZA — la altura

- **Caída Astuta** (2) · si **golpeaste en el aire** hace ≤ 10 ticks, el daño de caída no te llega
  aunque el smash falle. *Único:* el martillo ya tiene el coyote de la caída (`FallCoyote`); esto lo
  extiende al que **falla**, que hoy paga la caída entera.
- **Peso Muerto** (2) · sube el **techo de daño por bloque caído** de `MaceFall.MAX_DAMAGE` en
  **+20 %/+35 %**, y lo alcanza 2 bloques antes. *Único:* la maza es la única cuyo daño depende de
  la caída; es el único número del juego que se puede subir sin tocar el arma.
- **Onda de Impacto** (2) · el smash aterrizado **empuja** a todo lo que esté a 3 bloques.
  *Único:* consecuencia de caer, no de pegar — ninguna otra arma aterriza.

## AZADA — el ritmo *(pedido explícito)*

- **Cosecha** (2) · el **tajo** (click izquierdo, canal `CHOP`) corta también a un segundo enemigo
  pegado al costado, al **50 %/75 %**. *Único:* la azada es la única que **no tiene wind-up** y su
  tajo es un movimiento lateral; el corte extra sale del mismo gesto.
- **Surco** (2) · el **cleave** (click derecho) deja a los alcanzados con **−10 % de velocidad** 3 s.
  *Único:* la azada es la que barre ancho y sin carga; marcar el terreno es lo que hace un surco.

## ROPERA — el reposicionamiento

- **Punta Fina** (2) · **+15 %/+25 %** a **1,5 bloques o menos**. *Único:* la ropera pelea pegada
  (su alcance es medio y su lunge la mete adentro); ninguna otra arma premia estar tan cerca.
- **Retirada** (2) · tras el lunge, **Velocidad I** 20/30 ticks. *Único:* es la única arma que se
  mueve hacia adentro con su especial; el premio es poder salir.
- **Estocada Perforante** (2) · el golpe del lunge **ignora el 25 %/50 %** de la armadura.
  *Único:* el lunge es el único ataque del juego que se resuelve por movimiento.

## ALABARDA — la multitud

- **Hoz Ancha** (2) · el barrido alcanza **+1/+2** objetivos. *Único:* el barrido es su click derecho
  y ya pega a cuatro; es el único encantamiento que sube un tope que ya es el más alto.
- **Segunda Pasada** (2) · si el barrido **no mató a nadie**, te devuelve el **30 %/50 %** del
  cooldown. *Único:* el barrido cuesta 4 swings; es la única arma que paga un precio tan alto por un
  golpe en área, y el reembolso sólo tiene sentido con ese precio.
- **Muralla** (1) · el barrido **empuja** a los alcanzados en vez de dejarlos pegados. *Único:*
  la alabarda es la que pelea rodeada; separarlos es su supervivencia.

## MARTILLO — el compromiso

- **Inercia** (2) · el empuje crece **+8 %/+14 % por golpe** de cadena. *Único:* el martillo es el
  único con una cadena; el empuje es la única cosa del golpe que puede crecer sin tocar el daño.
- **Sentencia** (2) · con **5+ golpes** de cadena, el golpe **cargado** ignora la armadura (hoy sólo
  a carga completa lo hace por el tipo de daño). *Único:* la cadena es su mecánica; esto es el
  premio al que llega.
- **Yunque** (1) · el golpe cargado aplica **Quebranto** sin tener el encantamiento genérico.
  *Único:* identidad cruzada a propósito — el martillo *es* la fuente de Quebranto.

## DAGA — el tempo

- **Apuñalar** (2) · **+20 %/+35 %** si golpeás desde **más de 90° de la espalda** del objetivo.
  *Único:* sólo la daga (6 ticks) puede reposicionarse lo bastante rápido para buscar la espalda.
- **Sanguijuela** (2) · **+8 %/+14 %** del daño hecho como curación. *Único:* ya está en el doc §G.4;
  el gate de ítem es la daga y no el oro.
- **Veneno** (2) · **0,5 de daño cada 10 ticks** durante 3/4,5 s. *Único:* DoT de acumulación, no de
  remate — la daga es la que pega muchas veces por segundo.

## SHOTEL — la lectura

- **Desvío Perfecto** (2) · el parry devuelve **+15 %/+25 %** del daño parriado. *Único:* el parry es
  suyo y devolver exactamente lo mismo es su diseño; esto rompe la simetría **a favor del que leyó**.
- **Corte Gemelo** (2) · tras un parry **exitoso**, tu siguiente golpe en 3 s pega **dos veces**.
  *Único:* encadena la lectura con la carga doble que el sable ya tiene; ninguna otra arma tiene
  ambas mitades.
- **Giro** (1) · el parry también **desvía proyectiles** (flechas y la onda de filo).
  *Único:* es la única defensa de precisión del juego; hoy sólo cubre melé.

## ARCABUZ — la distancia y la mecha

- **Culata** (2) · el click izquierdo (bayoneta) empuja **+60 %/+100 %**. *Único:* la bayoneta es la
  única melee de un arma a distancia; el empuje es su función (hacer espacio para recargar).
- **Mecha Corta** (2) · la mecha baja de 20 a **15/10** ticks. *Único:* la mecha es la decisión
  entera del arcabuz (encender es el riesgo). ⚠️ **Trampa conocida:** está atada al `.mcmeta`
  (5 ticks × 4 frames) y `ModelDeclarationTest` lo afirma → necesita trabajo de cliente.
- **Pólvora Negra** (2) · el disparo hace **2,5/4 de daño en área** de 2 bloques. *Único:* el arma
  más fuerte del juego tiene que tener una razón para no disparar pegado; esto es una razón.

---

## Lo que este documento NO decide (para el dueño)

1. **¿Los ⭐ de cada arma?** Si hay que recortar, cuáles quedan. Mi orden de valor: los que abren una
   decisión nueva (Guardia Firme, Barrido de Tierra, Apuñalar, Segunda Pasada) antes que los que
   suben un número (Paliza, Peso Muerto, Hoz Ancha).
2. **`treasure` y peso** (§I.7 del otro doc): peso 2 para todos, y `treasure` en los más fuertes.
   Candidatos: Guardia Firme, Desvío Perfecto, Sentencia, Pólvora Negra.
3. **Clúster de DoTs** (§I.5): Veneno (daga) + Sangrado (Segadora/alabarda) + Fire Aspect (vanilla,
   ahora en contundentes). Tres fuentes de daño por tick. La regla de comer no se rompe (en 1.21.1 el
   daño no interrumpe comer) pero el **indicador direccional de daño** sí se saturaría: hay que
   excluir las tres fuentes de `DamageOverlayLayer`.
4. **Qué arma del set vanilla reworkeado queda sin únicos.** Hoy este plan cubre 11 armas: las 6
   nuevas + espada, hacha, pala, maza, azada. El **tridente** queda afuera a propósito (sus 3
   proyectiles están fuera del cono, y su identidad es de vanilla, no nuestra).
