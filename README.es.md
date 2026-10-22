<div align="center">

<img src="docs/media/logo.png" alt="Cortes Combat" height="140">

# Cortés Combat

**Un rework del combate de Minecraft 1.21.1 que se explica solo.**

Cada arma lleva su alcance, su arco, su cooldown y su carga. Los números están en el tooltip, el
servidor decide qué hace un golpe, y todo eso se aprende peleando.

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

Cortés Combat reemplaza todo el flujo de ataque de Minecraft, el del update 1.9 en adelante, y le
da a cada arma una identidad que se aprende peleando.

Y hay una quinta cosa que nadie espera. El arcabuz es un mosquete de pólvora con la mecha
encendida, una explosión devastadora a corta distancia y una recarga, y no tiene nada que ver en una
pelea de espadas.

![](docs/media/cool_sword_shi.gif)

---

## Míralo funcionar

Estas son las cuatro mecánicas de las que cuelga todo lo demás.

| | |
|---|---|
| ![](docs/media/reach.gif) | **El alcance es un cono.** Pasó a ser un arco horizontal y un arco vertical. Un golpe sólo cae dentro de ellos, así que acertar al píxel dejó de ser una molestia. |
| ![](docs/media/cooldown.gif) | **Cuidado con el cooldown.** A mitad de cooldown no podés atacar, y no hay bonus de daño por acertar el timing. El ritmo es del tipo de arma. |
| ![](docs/media/charged_attack.gif) | **¡Carga!!!** Mantené el ataque para dar un tajo devastador. Riesgo grande, recompensa grande. |
| ![](docs/media/shield.gif) | **La guardia tiene precio.** Bloquear gasta aguante, tres golpes y quedás abierto. Mantener la guardia arriba no cuesta nada, sólo recibir el golpe. |

---

### Cinco familias nuevas de cuerpo a cuerpo, ninguna es una espada retexturizada

La **ropera** es una aguja. Trece grados de cono obligan a que apunte de verdad, y el click derecho
te atraviesa una fila de mobs, con daño bonus por cada mob que atravesás.

La **alabarda** se planta a cinco bloques y barre todo lo que hay en un arco de sesenta grados, y
paga cuatro cooldowns por el privilegio.

El **martillo de guerra** levanta lo que golpea, sube su daño con cada golpe seguido e ignora la
armadura cuando está cargado.

La **daga** es lo más rápido que todavía te puede lastimar, pero no carga. :(

El **shotel** es un sable musulmán de hoja curva (había que aclararlo), parnea por una fracción de
segundo, y una carga completa pega dos cortes separados.

### El arcabuz

Un mosquete primitivo de pólvora. Cargalo, click derecho para encender la mecha, y dispara una
explosión a seis y medio bloques que pega a varios enemigos y los quema a cenizas.

Sabés lo que dicen, nunca llevés un arma de fuego a una pelea de espadas... Espera, no es
exactamente así.

### El hambre fue reworked

Correr, minar y pegar no cuestan nada. La comida se gasta solamente para curar, y regenera muy
despacio. Podés recuperarla rápido comiendo, PERO si te pegan te interrumpen la comida, salvo que
tengas la guardia arriba, porque podés cubrirte y comer al mismo tiempo.

### Más de 40 encantamientos, y 6 reworks de los de vanilla

Con la entrada de armas con más roles propios, hizo falta reconvertir los encantamientos y
expandir el set. Van desde una espada espectral hasta descargas eléctricas y cerdos voladores que
no vuelan (¡lo sabía!).

### El reactor del Nether

![](docs/media/reactor.webp)

Alimentá el reactor y se vuelve el corazón de tu base. Cuanto más lo cargás, más grande es el
radio, y destruye las almas corruptas que rondan tu casa,extendiendo el alcance de energía de tus
mecanismos de redstone.

Pero cuidado con alimentarlo un poquito de más...

### ¡Tus propias armas, sin escribir código!

Podés agregar tus propias armas y compatibilidad con otros mods con un archivo YAML en
`config/cortescombat/weapons/`, describir un arma por el id de su ítem, o inventar una que todavía
no existe y darle los números que quieras. Un tag de datapack puede apuntar a un arma y decir
"tratá esto como una alabarda". Recarga los cambios de stats en caliente, y carga sprites y sonidos
propios a través de un resourcepack.

La guía está en:
[`docs/custom-weapons.es.md`](docs/custom-weapons.es.md).

---

## Instalar

Los requisitos son Minecraft **1.21.1**, NeoForge **21.1.250** o superior, y **Java 21**.

Dejá el jar en `mods/`. En un servidor va en el servidor y en cada cliente que se conecte, porque
el cliente dibuja las animaciones y el servidor decide los golpes.

**Descarga:** [Cortés Combat 1.4.12 para Minecraft 1.21.1](https://github.com/CerebroCanibalus/cortes-combat/releases/tag/v1.4.12)
(`Cortes-combat-1.4.12-1.21.1.jar`).

O compilalo desde el código:

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

**¿Puedo apagar el sistema X?**
Sí se puede, comrade. Casi todo lo que agrega el mod es configurable.

**¿Por qué mi arma de otro mod pega como un puñetazo?**
Nada la reclamó, así que cayó en el perfil de manos desnudas. Escribí una entrada de pack o un
tag para ella, mirá [`docs/custom-weapons.es.md`](docs/custom-weapons.es.md).

**¿Sirve en multijugador?**
Sí, y está totalmente testeado.

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
