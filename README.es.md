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

El combate cuerpo a cuerpo donde el arma que llevás en la mano decide tres cosas distintas: hasta dónde llegás, cada cuánto podés pegar, y qué te compra la carga. Vanilla trata una ropera, un hacha y una alabarda como el mismo objeto con daño distinto. Acá no.

## Qué cambia

**El alcance es un cono, no una esfera.** Cada arma trae su distancia, su ángulo horizontal y su ángulo vertical, y el golpe sólo entra ahí adentro. La ropera tiene el cono más angosto del juego y el sable el más ancho; el ángulo vertical es el que decide si le pegás a algo que te pasa por arriba o a algo que te muerde los tobillos.

**La velocidad de ataque es un muro.** No podés pegar a medio cooldown, y no hay multiplicador por acertar el momento. El ritmo lo pone el arma, así que golpear rápido y plano es una decisión y no un error. Las manos desnudas son lo más veloz del juego y lo que menos lejos llega.

**La carga suma, nunca resta.** Mantenés el botón y el golpe crece sobre su base: si soltás temprano, no perdés nada. Y cargar siempre rinde menos que pegar dos veces, así que sostener es una decisión sobre distancia y armadura, no un truco de daño.

**El botón derecho hace algo que el izquierdo no puede.** Nunca es "más daño": la ropera se lanza, la alabarda barre todo lo que hay en su arco, el shotel para el golpe y lo devuelve. Hay armas que no cargan y armas sin click derecho, y eso lo declara el arma en vez de adivinarlo el juego.

**La guardia cuesta.** Escudo y espada cubren los dos, los dos frenan el 100% de un golpe bloqueado salvo las explosiones, y los dos gastan aguante: tres golpes y la guardia se rompe, y quedás abierto y aturdido. Sólo funciona en el arco de adelante. El hacha te la rompe de un solo golpe, que es la razón entera de llevar un hacha.

**La comida deja de ser energía.** Correr, picar y pegar no gastan nada. La comida es recurso de curación con ciclo cerrado: paga tu regeneración y vuelve sola con su propio reloj. Si comés mientras te están pegando, el golpe te interrumpe — salvo que tengas la guardia arriba. Y si la tenés arriba, primero te cubrís y después mordés: el orden no se elige, sale por construcción.

**Treinta armas, y ninguna es una espada disfrazada.** Cinco son nuevas — ropera, alabarda, martillo de guerra, daga y shotel — y cada una es una respuesta distinta a la pelea. La espada, el hacha, la pala, la maza y el tridente de vanilla entran al sistema y se rebalancearon para él. El pico se queda afuera, a propósito. Y está el **arcabuz**: un arma de pólvora que paga un disparo enorme con una recarga larga, y que no tiene nada que hacer en una pelea de espadas.

**Treinta y cuatro encantamientos**, todos hechos para estas mecánicas y no para las de vanilla: robo de vida, guardia, contragolpes, y unos cuantos que sólo valen la pena si entendés la carga.

**El reactor del Nether.** Cargalo, empujalo más allá de su techo y paga. Empujalo más todavía y enciende la mecha: abre un cráter de cuarenta y ocho bloques, se lleva el agua del terreno y sacude la cámara de cualquiera que esté a ciento sesenta bloques.

**Las armas de otros mods también pelean.** Un archivo YAML en `config/cortescombat/weapons/` le enseña al sistema cómo es un arma de otro mod — su alcance, su carga, su empuje — y un tag de datapack hace lo mismo desde un pack. Ninguna de las dos vías necesita una línea de Java. Hay [guía](docs/armas-custom.md).

**Ocho idiomas.** Inglés y siete variedades de español, porque los nombres son españoles a propósito: acá una espada ropera es una ropera y una alabarda es una alabarda, y traducirlas por idioma daría dos juegos de armas distintos.

## Si buscás otra cosa

Este mod reemplaza cómo funciona el cuerpo a cuerpo. No es lo que todos quieren, así que acá va el mapa honesto:

| Si querés | Andá a |
| --- | --- |
| Combos estilo Minecraft Dungeons, doble empuñadura y animaciones de ataque fluidas | [Better Combat](https://modrinth.com/mod/better-combat) |
| Un souls-like con habilidades, esquiva y su propio set de animaciones | [Epic Fight](https://modrinth.com/mod/epic-fight) |
| Las reglas de vanilla con los números del Combat Test viejo restaurados | [Combatify](https://www.curseforge.com/minecraft/mc-mods/combatify) (Fabric) |
| Combate exactamente vanilla, más cosmética | Otra cosa |
| Jugar en **Fabric o Quilt** | No acá — Cortés Combat es sólo NeoForge |

Y dos cosas que conviene saber antes de instalarlo: el mod va **en el cliente y en el servidor** (el servidor resuelve cada golpe; el cliente sólo lo dibuja), y **se niega a cargar junto a un mod de combate con el que no puede convivir**, imprimiendo el nombre del mod en el log en vez de reventar con un stack trace. La lista es corta y existe porque un conflicto silencioso es peor que una negativa a gritos.

## Instalación

**Necesitás:** Minecraft **1.21.1**, NeoForge **21.1+**, **Java 21**.

Tirás el jar en tu carpeta `mods/` — en el servidor y en cada cliente que se conecte.

¿Todavía no hay jar? Compilalo:

```bash
git clone https://github.com/CerebroCanibalus/cortes-combat.git
cd cortes-combat
./gradlew build          # En Windows: gradlew.bat build
```

El jar queda en `build/libs/`. A `mods/` como cualquier otro. Para un entorno de desarrollo, `./gradlew runClient` y `./gradlew runServer` levantan el juego con el mod ya cargado.

## Cómo se juega

| Acción | Qué pasa |
| --- | --- |
| **Mantener click izquierdo** | Carga el golpe y lo dispara al soltar — un botón hace las dos |
| **Click derecho** | El movimiento propio del arma: lanzarse, barrer, parar, o nada |
| **Click derecho con escudo o espada** | Levanta la guardia |
| **Click izquierdo con comida** | Come; la guardia puede estar arriba al mismo tiempo |

Mientras sostenés hay dos marcas en pantalla: la barra **debajo de la mira** es la carga, y la que la **abraza** es el cooldown. Los corchetes se cierran cuando el arma está lista. Si llevás algo que bloquea, aparecen tres esferitas tenues arriba de la mira: ésa es tu aguante, y conviene mirarla.

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
