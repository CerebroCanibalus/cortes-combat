<div align="center">

<img src="docs/media/logo.png" alt="Cortes Combat" height="140">

# Cortés Combat

**Un rework del combate para Minecraft 1.21.1 que se explica solo.**

Cada arma lleva su alcance, su arco, su cooldown y su carga. Los números están en el tooltip, el
servidor decide qué hace un golpe y todo eso se aprende peleando.

[Read in English](README.md)

</div>

<div align="center">

![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-3C8527?logo=minecraft&logoColor=white)
![NeoForge 21.1+](https://img.shields.io/badge/NeoForge-21.1%2B-EA7E20)
![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Cliente y servidor](https://img.shields.io/badge/lado-cliente%20%26%20servidor-lightgrey)
![Licencia GPL-3.0-only](https://img.shields.io/badge/licencia-GPL--3.0--only-blue)
![Versión 1.4.12](https://img.shields.io/badge/versión-1.4.12-6E4B3A)

</div>

---

Cortés Combat reemplaza todo el flujo de ataque de Minecraft 1.21.1 y le da a cada arma una
identidad que se aprende peleando. La espada llega más lejos y pega en un arco ancho. La ropera
llega a la misma distancia por un cono que es una aguja y embiste con el click derecho. La alabarda
se mantiene lejos y barre todo lo que tiene adelante. El sable es corto, rápido y parnea.

Y hay una quinta cosa que nadie espera. El arcabuz es un mosquete de pólvora con la mecha
encendida, una explosión devastadora a corta distancia y una recarga, y no tiene nada que ver en una
pelea de espadas.

<p align="center">
  <img src="src/main/resources/assets/cortescombat/textures/item/diamond_ropera.png" width="56" alt="Ropera">
  <img src="src/main/resources/assets/cortescombat/textures/item/diamond_alabarda.png" width="56" alt="Alabarda">
  <img src="src/main/resources/assets/cortescombat/textures/item/diamond_martillo.png" width="56" alt="Martillo de Guerra">
  <img src="src/main/resources/assets/cortescombat/textures/item/diamond_daga.png" width="56" alt="Daga">
  <img src="src/main/resources/assets/cortescombat/textures/item/diamond_shotel.png" width="56" alt="Shotel">
  <img src="src/main/resources/assets/cortescombat/textures/item/arcabuz_icon.png" width="56" alt="Arcabuz">
</p>

---

## Míralo funcionar

Estas son las cuatro mecánicas de las que cuelga todo lo demás.

| | |
|---|---|
| <img src="docs/media/01-reach-cone.gif" alt="El alcance es un cono" width="420"> | **El alcance es un cono.** Distancia, un arco horizontal y un arco vertical. Un golpe sólo cae dentro de ellos, así que un mob en el borde del arco es un fallo que se ve venir. |
| <img src="docs/media/02-hard-cooldown.gif" alt="El cooldown es una pared" width="420"> | **El cooldown es una pared.** A mitad de cooldown no podés atacar, y no hay bonus de daño por acertar el timing. El ritmo es del arma. |
| <img src="docs/media/03-charge.gif" alt="La carga paga" width="420"> | **La carga paga.** Mantené el ataque para cargar un bonus y soltala para cobrarlo. Un golpe cargado siempre pierde en daño por segundo contra el aporreo rápido, y siempre gana cuando necesitás un golpe grande. |
| <img src="docs/media/04-guard-stamina.gif" alt="La guardia tiene precio" width="420"> | **La guardia tiene precio.** Bloquear gasta aguante, tres golpes y quedás abierto. Mantener la guardia arriba no cuesta nada, sólo recibe el golpe. |

---

## Qué cambia

### Cada arma tiene números, y el tooltip los muestra

Siete renglones reemplazan los dos de vanilla. Daño, daño cargado, ataques por segundo, alcance,
tamaño del arco, empuje y la habilidad del click derecho, más cuántos objetivos alcanza un golpe.

| Arma | Alcance | Arco (horizontal / vertical) | Carga | Click derecho |
|---|---|---|---|---|
| Manos desnudas | 2.5 | 28° / 22° | +30% en 12 ticks | — |
| Espada | 3.6 | 35° / 30° | +50% en 25 ticks | — |
| Hacha | 3.4 | 35° / 25° | +200% en 60 ticks | — |
| Pala | 3.0 | 35° / 30° | +40% en 33 ticks | — |
| Azada | 3.2 | 60° / 50° | — | Tajo |
| Tridente | 5.3 | 15° / 25° | +50% en 25 ticks | — |
| Ropera | 3.6 | 13° / 16° | — | Embestida |
| Daga | 2.9 | 49° / 31° | — | — |
| Alabarda | 5.0 | 60° / 35° | +60% en 36 ticks | Barrido |
| Martillo de Guerra | 3.4 | 36° / 36° | +60% en 32 ticks | — |
| Shotel | 2.25 | 75° / 25° | +100% en 20 ticks, pega dos cortes | Parry |
| Arcabuz | 2.6 bayoneta, 6.5 disparo | 46° / 35° | — | Encender la mecha |

El pico sigue siendo una herramienta a propósito. Los números de arriba son los de fábrica, y
cualquiera se puede reemplazar por ítem, por mod o por pack.

<p align="center">
  <img src="docs/media/14-arcabuz-shots.gif" alt="Cada arma tiene un alcance" width="620">
</p>

### Cinco familias nuevas de cuerpo a cuerpo, ninguna es una espada retexturizada

<p align="center">
  <img src="docs/media/07-weapons.gif" alt="Cinco familias nuevas" width="620">
</p>

La **ropera** es una aguja. Trece grados de cono obligan a que apunte de verdad, y el click derecho
te atraviesa una fila de mobs. La **alabarda** se planta a cinco bloques y barre todo lo que hay
en un arco de sesenta grados, y paga cuatro cooldowns por el privilegio. El **martillo de
guerra** levanta lo que golpea, sube su daño con cada golpe seguido e ignora la armadura cuando
está cargado. La **daga** es lo más rápido que todavía te puede lastimar, y nunca carga. El
**shotel** es un sable musulmán de hoja curva, parnea por una fracción de segundo y una carga
completa pega dos cortes separados.

Todos vienen por varios tiers, de la madera o la piedra al netherite, y el set de cobre tiene sus
propias texturas.

### El arcabuz

<p align="center">
  <img src="docs/media/08-arcabuz.gif" alt="El arcabuz" width="620">
</p>

Un mosquete de pólvora. El click derecho enciende la mecha y la mirás arder, el click izquierdo
mete una bayoneta en lo que tengas cerca y dispara una explosión a seis y medio bloques. Recarga,
sacude la cámara cuando dispara, y es muy malo para una pelea de espadas.

### El click derecho es otra arma

<p align="center">
  <img src="docs/media/05-specials.gif" alt="El click derecho es otra arma" width="620">
</p>

Tres de las familias nuevas contestan el click derecho, y cada una lo contesta distinto. La
embestida te mueve y pega un cubo adelante tuyo. El barrido limpia un arco y cuesta cuatro
cooldowns. El parry cancela un golpe entrante, lo devuelve, y después entra en cooldown por cinco
segundos.

### El hambre es un recurso de curación

<p align="center">
  <img src="docs/media/06-hunger.gif" alt="La comida cura, la comida vuelve" width="620">
</p>

Correr, minar y pegar no cuestan nada. La comida sólo se gasta para curar, y vuelve por su cuenta
en su propio reloj, así que el ciclo cierra. Un golpe te interrumpe la comida salvo que tengas la
guardia arriba, y podés cubrirte y comer con un solo botón, en ese orden.

### 48 encantamientos, y seis de vanilla reworked

<p align="center">
  <img src="docs/media/09-enchantments.gif" alt="48 encantamientos" width="620">
</p>

Cuarenta y ocho son nuevos, y Filo, Maldición, Saqueo, Empuje, Aspecto de Fuego y Maldición de
Arácnidos fueron reworked al mismo sistema. Van desde una espada espectral hasta cerdos voladores
que no vuelan, y cada uno se apoya en una regla real del sistema de combate. Ninguno es un
número que nadie lee.

### El reactor del Nether

<p align="center">
  <img src="docs/media/10-reactor.gif" alt="El reactor del Nether" width="620">
</p>

Cargá el reactor, pasale su límite, y se vuelve el corazón de tu base. Pasale el límite todavía
y detona, y se lleva el terreno con él.

### Feedback que se lee sin mirar

<p align="center">
  <img src="docs/media/11-fx.gif" alt="Feedback de impacto" width="620">
</p>

Un flash rojo y una barra de vida que se vacía a medida que el mob se desangra, el cuerpo que se
vuelve blanco al morir, temblor de cámara escalado a lo que te pegó, sangre que cae, una onda de
filo que crece con el arma que la hizo, y un indicador direccional para el daño que viene de
detrás.

### El cuerpo se mueve en tercera persona

<p align="center">
  <img src="docs/media/12-third-person.gif" alt="Tercera persona" width="620">
</p>

La preparación, la carga y el golpe existen en el mundo, y la cámara es sólo uno de los lugares
donde se ven. Los demás jugadores también los ven.

### Tus propias armas, sin escribir código

<p align="center">
  <img src="docs/media/13-weapon-packs.gif" alt="Tus propias armas" width="620">
</p>

Un archivo YAML en `config/cortescombat/weapons/` puede describir un arma por el id de su ítem, o
inventar una que todavía no existe y darle los números que quieras. Un tag de datapack puede
apuntar a un arma y decir "tratá esto como una alabarda". Los dos se recargan en caliente, y
`/cc packs` te dice qué archivo está reclamando el ítem que tenés en la mano.

La referencia completa de campos, los nueve arquetipos y la lista de problemas está en
[`docs/armas-custom.md`](docs/armas-custom.md).

---

## Comparado con las alternativas

**Vanilla.** Vanilla tiene un alcance, un arco, un cooldown y un crit que premia aporrear en el
momento justo. Cortés Combat le da a cada arma sus propios números, y el timing pasa a ser una
decisión donde vanilla pide un reflejo. Si querés el feeling de vanilla, el toggle `combatRework`
de la config del servidor apaga todo el sistema.

**Combatify.** El mod de combate más profundo que hay en Fabric, y la referencia contra la que se
midió este proyecto. Reescribe mucho más del juego y pide que leas su documentación antes de
jugar. Cortés Combat deja el foco en el cuerpo a cuerpo, explica cada número en el tooltip y sale
en NeoForge.

**Otros mods de combate.** Cortés Combat se niega a cargar junto a un mod de combate rival y dice
cuál encontró. Dos mods reescribiendo el mismo flujo de ataque desincronizan el juego, y de eso
no sale nada bueno.

**Cuándo no usarlo.** No lo uses si querés combate de supervivencia realista, si tu servidor ya
corre otro overhaul de combate, si jugás en Fabric, o si te gusta el ritmo de ataque de vanilla y
sólo querías un reskin. La azada, la pala y la espada ya traen su propia identidad en vanilla, y
este mod tira esas identidades a la basura a propósito.

---

## Instalación

Los requisitos son Minecraft **1.21.1**, NeoForge **21.1.250** o superior y **Java 21**.

Dejá el jar en `mods/`. En un servidor va en el servidor y en cada cliente que se conecte,
porque el cliente dibuja las animaciones y el servidor decide los golpes.

Todavía no hay jar para descargar. Compilalo desde el código:

```bash
git clone https://github.com/CerebroCanibalus/cortes-combat.git
cd cortes-combat
./gradlew build          # Windows: gradlew.bat build
```

El jar queda en `build/libs/`. Para un entorno de desarrollo, `./gradlew runClient` y
`./gradlew runServer` levantan el juego con el mod ya cargado.

---

## Preguntas frecuentes

**¿El mod tiene que estar en todos los clientes?**
Sí. El servidor decide los golpes y el cliente dibuja los arcos, las cargas y las animaciones.

**¿Sirve en un servidor con más jugadores?**
Sí, y las animaciones del cuerpo van sincronizadas, así que los demás ven tu preparación y tu
carga.

**¿Puedo apagar partes?**
Sí. La config del servidor tiene `combatRework`, `survivalRework` y `particles`. El primero
devuelve el flujo de ataque a vanilla, el segundo devuelve el hambre, y el tercero apaga las
partículas y deja el sonido como estaba.

**¿Cambia las armas de vanilla?**
Sí. La espada, el hacha, la azada, la pala, la maza y el tridente entran al sistema con números
nuevos. El pico sigue siendo una herramienta.

**¿Cómo saco el arcabuz?**
Crafteándolo. Bambú, tablones, un dispensador y un pedernal. La lastima que carga es pólvora con
polvo de piedra luminosa.

**¿Por qué mi arma de otro mod pega como un puñetazo?**
Nada la reclamó, así que cayó en el perfil de manos desnudas. Escribí una entrada de pack o un
tag para ella, mirá [`docs/armas-custom.md`](docs/armas-custom.md).

**¿Puedo hacer una sesión de multijugador de verdad?**
Sí, y la sincronización de animaciones es justo la parte que necesita dos personas para
verificarla.

---

## Configuración

Los ajustes de cliente y los de mundo viven en las rutas estándar de NeoForge. Un admin de
servidor ajusta el balance en el centro y cada jugador conserva sus preferencias visuales.

Los números que deciden cómo se siente el mod están en la config del servidor, y los dos que más
importan son `foodRegenInterval` y `healFoodCost`. Muy rápido y la comida es decorado, muy lento y
una pelea es un callejón sin salida.

Los operadores tienen un comando `/cc`. `/cc stats` imprime el perfil, el alcance, el cooldown, la
carga y la especial del arma que tenés en la mano, `/cc packs` lista los packs cargados y cuál
reclama el ítem actual, `/cc reload` relee los archivos de pack sin reiniciar el servidor, y
`/cc input` imprime todas las puertas que pueden rechazar un click, en orden, con el valor que
cada una lee.

---

## Cuando algo se rompe

**El mod no aparece en la lista de mods.** Loader o versión de Minecraft equivocados. Es NeoForge
en 1.21.1 y nada más.

**`Unsupported class file major version` al arrancar.** El Java es viejo. Necesita Java 21.

**El juego se niega a cargar y nombra otro mod.** Tenés un mod de combate con el que no puede
convivir. Sacá uno de los dos.

**Un arma de otro mod pega como un puñetazo.** Nada la reclamó, así que cayó en el perfil de
manos desnudas. Escribí una entrada de pack o un tag para ella.

**Golpes que deberían caer y no caen.** Estás fuera del cono. `/cc stats` imprime los arcos del
arma que tenés en la mano.

**Tu pack de armas no hizo nada.** Mirá `/cc packs`. El id del ítem tiene que coincidir exacto, y
un resource pack que falló al cargar se lleva el archivo entero con él.

**Las animaciones se ven raras después de actualizar un mod.** El cliente conserva las clases que
cargó al arrancar. Reiniciá el cliente.

---

## Cómo funciona

El servidor es dueño de cada número. Cuando pegás, el cliente manda a qué apuntaste y cuánto
mantuviste el botón, y el servidor decide si el golpe cae, cuánto hace y cuánto empuja. El
cliente sólo dibuja. Un cliente desincronizado muestra una animación equivocada un momento, y
no puede cambiar la pelea.

Los números viven en datos, y el código los lee. Cada ítem lleva un perfil que dice qué arma es,
y cada arma tiene una tabla de alcance, carga, empuje y habilidad. Un pack, un tag o un datapack
puede reemplazar cualquiera de eso para cualquier ítem, y por eso un arma de otro mod puede tener
identidad completa sin que nadie escriba una línea de Java.

Las animaciones son por arma y las maneja un conjunto chico de canales con nombre. Un arma
declara qué canales lee, y un solver solo convierte el estado del jugador en una pose. El mismo
solver corre para tu propio cuerpo en tercera persona y para el cuerpo del jugador al que estás
mirando, así que los dos leen los mismos números.

El balance de los enemigos queda intacto. Un zombi pega lo que pega un zombi. Lo que cambia es la
conversación que tenés con él.

---

## Contribuir · Seguridad · Licencia

- Los reportes de bugs y las ideas de features usan las [plantillas de issue](.github/ISSUE_TEMPLATE).
  La de bug pide un log, y sin log hay muy poco con qué trabajar.
- Leé [CONTRIBUTING.md](.github/CONTRIBUTING.md) antes de abrir un pull request.
- ¿Encontraste un problema de seguridad? Mirá [SECURITY.md](.github/SECURITY.md), y por favor no
  abras un issue público para eso.
- Licencia **GNU General Public License v3.0 only**. Mirá [LICENSE](LICENSE).

---

*"La técnica es una actividad compositora o destructora, violenta, y esto es lo que Aristóteles llamaba la poiesis, la poesía, precisamente."*

```
                                                  █   █
                                                       █
                                                              █           █
                                                          █  █  █  █
                                                ████████████    ███  ██
                                                █ █████████████████   █     █
                                        █   █████████████████████████████
                                         ███ ███████████████████████        █
                                            █████ ███████████████████ █ █    ██   █
                                           ███████████████████████████ █       █
                                          ██████████████████████████████     █ █
                                         ██████████ ██████████████████         █
                                       ██████████ ████████████████████
                                       ████████████████████████████████   █    █ █
                                         ███████████████████████████████
                                     ████    ███ ████  █          ██████   █  █  █
                            █               █ ██    █   ██████  █████████     █
                          █      ██████████  ███████   █      ████   ███     █
                         █     █             █                        ██ █       ██████
                                                ██   █    █ ██████   █ ██  █ █  █ █    █
                                               ███          █  ███████     ███████ █   █
                              █       ███     █████  █    █ ██ ████████ ████  █ █████  █
                          █      ████ ███    ███████ █████   ██ ████████  █████  ████ █
                           █     ██ ████     ███████  █ ████████████████████ ██ █ ██ █
                             █████     █     █████████████████████████████ █ █    █ █
                           ██               █████████  ██████████████████    █████ █
                        ██        ██████   ████████████ ███████████████████ █████ █
                   █  █████       █████ ████████████████████████████████    ██████
                  █ ██████          ██      ██████   ███ ███████████████  ██ ████
                 █ ██████      █████          █ ████████████████████████  █
                  ██████ ██████████████      ███████████████████████████ ██
                 ██████  ████████████████        ██████████████████████  █
              █   ██████  ███████         █████████████████████████████  █
                   ███     ██████                         ████████████ █ █
                    ██  █   █████            █████████████ ██████████ █ ██
                     ██     ███████         ██████████████████████████  █
                       █     ███████         ███████████████████████   ██
                             █████████         ████████████████████  █ ██
                           ██████████████         ███████████████████ ██
                              ██████████████████████████████████  ██ ███
                                █  ███████████ █████████████    █ █ ███
                                    ████████████ █████████    ███  ████
                                 █   ████████████ █████████ ███   ████
                                  █   ████████████   ██ █   █   █████
                                      ██████████████      █   ███████
                                       ██████████████ ██    ████████
                                    █  ██████████████   ████████████
                                         ████████████████     ██████
                                         ████████████████        ███
                                          █████████████████      ██
                                           ████████████████ █  ████
                                              ███████████████  ████
                                                  █████████████  █
                                                       ████████
                                                       ████████
                                                      █████████
                                                     █████████
                                                     ████████
                                                    ███████
                                                    ██████
                                                    ████    —Gustavo Buenoφ
```

<div align="center">
☭ Por los obreros iberófonos del mundo ☭

† 🇪🇸 🇲🇽 🇦🇷 🇨🇴 🇨🇱 🇵🇪 🇻🇪 🇪🇨 🇬🇹 🇨🇺 🇧🇴 🇩🇴 🇭🇳 🇵🇾 🇸🇻 🇳🇮 🇨🇷 🇵🇦 🇺🇾 🇪🇶 🇵🇷 🇬🇶 🇵🇹 🇧🇷 🇲🇿 🇦🇴 🇨🇻 🇲🇴 🇬🇼 🇸🇹 🇹🇱 †

</div>


