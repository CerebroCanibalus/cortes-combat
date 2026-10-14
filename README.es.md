# Cortés Combat

[Read in English](README.md)

![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-3C8527?logo=minecraft&logoColor=white)
![NeoForge 21.1+](https://img.shields.io/badge/NeoForge-21.1%2B-EA7E20)
![Licencia GPL-3.0-only](https://img.shields.io/badge/licencia-GPL--3.0--only-blue)
![Cliente y servidor](https://img.shields.io/badge/lado-cliente%20y%20servidor-lightgrey)

<p align="center">
  <img src="src/main/resources/assets/cortescombat/textures/item/diamond_ropera.png" width="64" alt="Ropera">
  <img src="src/main/resources/assets/cortescombat/textures/item/diamond_alabarda.png" width="64" alt="Alabarda">
  <img src="src/main/resources/assets/cortescombat/textures/item/diamond_martillo.png" width="64" alt="Martillo de guerra">
  <img src="src/main/resources/assets/cortescombat/textures/item/diamond_daga.png" width="64" alt="Daga">
  <img src="src/main/resources/assets/cortescombat/textures/item/diamond_shotel.png" width="64" alt="Shotel">
  <img src="src/main/resources/assets/cortescombat/textures/item/arcabuz_icon.png" width="64" alt="Arcabuz">
</p>

**Cortés Combat** es un rework completo del combate de Minecraft, pensado para que sea intuitivo y se entienda sin manual: arregla la curva de habilidad, la lectura de la pelea, la falta de opciones y las mecánicas que no se explican solas. Ya fue la época en que había que pegarle al píxel exacto, en el tick exacto, para matar a un bebé zombi. Ahora cada arma tiene su radio, su cooldown y su alcance.

## Qué cambia

**Cada arma trae su propia distancia.** Y su propio arco horizontal y su propio arco vertical: el golpe sólo entra ahí adentro. Eso resuelve el problema de años de tener que pegarle al píxel exacto y errar la mayoría por la cámara de Minecraft (que no es ninguna maravilla) o por enemigos chicos y rápidos.

**La velocidad de ataque es un muro.** No podés pegar a medio cooldown como en vanilla, y no hay multiplicador de daño por acertar el momento. El ritmo lo pone el arma, así que golpear rápido y plano es una decisión y no un error. Las manos desnudas son lo más rápido del juego y lo que menos lejos llega, como es obvio. Y eso arregla dos cosas de una: la falta de claridad del cooldown de vanilla y la ambigüedad del DPS de cada arma.

**La carga reemplaza a los críticos.** Mantenés el ataque y soltás un golpe devastador: es una forma mejor de conservar la habilidad del timing y de agregarle una capa nueva al combate. Un golpe cargado siempre rinde menos que pegar seguido, pero también es la manera de dar vuelta una pelea con un ataque riesgoso. Una decisión constante que hay que balancear en el medio de la pelea.

**Ataques especiales.** Algunas armas tienen una habilidad de click derecho que no sólo le suma identidad al arma: hace que cada set se juegue distinto, cumpliendo un rol o un tipo de juego para cada jugador. Y eso multiplica las combinaciones posibles en una pelea.

**Una guardia que cuesta.** En vanilla bloquear con escudo suele no tener riesgo, y hacer que no bloqueen todo se siente mal, aparte de que termina dejando al escudo como la peor opción casi siempre. Así que ahora tenés puntos de guardia, que se van gastando a medida que bloqueás: sirve como una pequeña pausa para curarte o reordenarte antes de contraatacar.

**La comida deja de ser aguante.** Correr, picar y pegar ya no cuesta nada. La comida es **sólo** un recurso de curación con ciclo cerrado: paga tu regeneración y vuelve sola con su propio reloj. Si comés mientras te están pegando, el golpe te interrumpe, salvo que tengas la guardia arriba, porque se come con escudo o espada en mano.

**5 armas nuevas de cuerpo a cuerpo, y ninguna es una espada disfrazada.** La ropera, la alabarda, el martillo de guerra, la daga y el shotel: cada una es una respuesta distinta a la pelea. Y está el **Arcabuz**: un arma de pólvora que dispara ráfagas demoledoras de corto alcance, ¡y que no tiene nada que hacer en una pelea de espadas!

**¡Las armas de vanilla, rehechas por completo!** La espada, la azada, el hacha, la pala, la maza y el tridente entran al sistema y se rehicieron para él. El pico se queda siendo herramienta, a propósito: cada arma de vanilla se reorientó y recibió una identidad nueva.

**34 encantamientos nuevos, y los de vanilla retocados.** Una pletora de encantamientos nuevos y reworkeados que van de espadas espectrales a cerdos voladores (mentira), moscas asesinas, y muchas, muchas más.

**El Reactor del Nether.** ¡Cargalo, empujalo más allá de su techo y convertilo en el corazón de tu base! Empujalo más todavía y alcanzá el poder absoluto.

**¡TUS armas también pueden pelear!** Si ninguna de las que vinieron te cierra, podés agregar las TUYAS por `config/cortescombat/weapons/` y un resourcepack, y también configurar cualquier arma del mod. Le ponés el alcance, la carga, el empuje y mucho más.

**Efectos de combate revitalizados.** Se agregaron efectos de todo tipo, raros y lindos, al daño, a la vida baja, a los golpes de los mobs y más, con un indicador de vida nuevo y fácil de leer que no se come las visuales del juego.

## Instalación

**Necesitás:** Minecraft **1.21.1**, NeoForge **21.1+**, **Java 21**.

Tirás el jar en tu carpeta `mods/`, en el servidor y en cada cliente que se conecte.

¿Todavía no hay jar? Compilalo:

```bash
git clone https://github.com/CerebroCanibalus/cortes-combat.git
cd cortes-combat
./gradlew build          # En Windows: gradlew.bat build
```

El jar queda en `build/libs/`. A `mods/` como cualquier otro. Para un entorno de desarrollo, `./gradlew runClient` y `./gradlew runServer` levantan el juego con el mod ya cargado.

## Armas de otros mods

Dos puertas, y ninguna pide escribir código.

Un **archivo YAML** en `config/cortescombat/weapons/` puede describir un arma por su id, o inventar una que no existe y darle los stats que quieras. Un **tag de datapack** (`data/<tupack>/tags/item/...`) puede apuntar a un arma y decir "tratala como alabarda". Las dos se releen en caliente, y `/cc packs` te dice qué archivo está reclamando el arma que tenés en la mano.

La referencia completa de campos, los nueve arquetipos y la lista de problemas comunes están en [`docs/armas-custom.md`](docs/armas-custom.md).

## Ajustes y herramientas de operador

Los ajustes del cliente y los del mundo usan las ubicaciones de config estándar de NeoForge, así que un admin puede tocar el balance en el servidor y cada jugador se queda con sus preferencias visuales.

Los operadores tienen `/cc`: `/cc stats` imprime perfil, alcance, cooldown, carga y especial del arma en mano; `/cc packs` lista los packs cargados y cuál reclama el ítem actual; `/cc reload` relee los archivos sin reiniciar el servidor.

## Cuando algo se rompe

- **El mod no aparece en la lista.** Estás en el loader o la versión equivocada. Es NeoForge 1.21.1 y nada más.
- **`Unsupported class file major version` al arrancar.** El Java es viejo. Necesitás Java 21.
- **El juego se niega a cargar y nombra otro mod.** Tenés un mod de combate con el que no puede convivir. Sacá uno de los dos.
- **Un arma de otro mod pega como un puñetazo.** Nadie la reclamó todavía, así que cayó al perfil de manos desnudas. Escribile una entrada de pack o un tag.
- **Golpes que deberían entrar, no entran.** Estás fuera del cono. Mirá qué cubre el arma de verdad — `/cc stats` imprime sus ángulos.
- **Tu pack de armas no hizo nada.** Mirá `/cc packs`: el id del ítem tiene que coincidir exacto, y un resource pack que falló al cargar se lleva el archivo entero puesto.

## Contribuir · Seguridad · Licencia

- Bugs y pedidos: usá las [plantillas de issue](.github/ISSUE_TEMPLATE). La de bug pide un log — sin log hay muy poco que mirar.
- Leé [CONTRIBUTING.md](.github/CONTRIBUTING.md) antes de mandar un pull request.
- ¿Encontraste un problema de seguridad? Mirá [SECURITY.md](.github/SECURITY.md) — no abras un issue público para eso.
- Licencia **GNU General Public License v3.0 only**. Ver [LICENSE](LICENSE).
