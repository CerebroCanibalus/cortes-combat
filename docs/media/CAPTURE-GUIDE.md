# Guía de captura

Todo lo que el README, CurseForge y Modrinth muestran en video sale de acá. La regla es simple:
**un archivo, un nombre, un slot.** Cuando se graba, se reemplaza el archivo y no hay que tocar
el Markdown.

## Estado

| Archivo | Slot | Qué tiene que mostrar |
|---|---|---|
| `01-reach-cone.gif` | Alcance | Un mob parado en el borde del arco. El golpe entra. Un paso más y falla. Con la mira sobre el mob, la crosshair cambia de estado. |
| `02-hard-cooldown.gif` | Cooldown duro | Golpear, ver el corchete de cooldown llenarse, y tratar de pegar antes de tiempo. El golpe no sale. |
| `03-charge.gif` | Carga | Mantener el click, ver la barra de carga, soltar, ver el número de daño y el impacto de la partícula. |
| `04-guard-stamina.gif` | Aguante | Bloquear tres golpes. Al tercero las esferitas de aguante se apagan y el stagger entra. |
| `05-specials.gif` | Click derecho | Ropera (embestida), alabarda (barrido) y shotel (parry), uno detrás de otro. |
| `06-hunger.gif` | Comida | Comer, ver la comida bajar, curarse, ver la comida volver sola. Y comer con la guardia arriba. |
| `07-weapons.gif` | Las cinco familias | Sacar las cinco armas en una hotbar y pegarle a un dummy. El arco y el número cambian en cada una. |
| `08-arcabuz.gif` | Arcabuz | Encender la mecha, ver la mecha arder, disparar, ver la explosión y la recarga. |
| `09-enchantments.gif` | Encantamientos | Un stack con varios encantamientos y el tooltip. Después el efecto en el juego. |
| `10-reactor.gif` | Reactor | El reactor cargando, pasándole el límite, y la detonación con el cráter. |
| `11-fx.gif` | Feedback | Un golpe recibido, el flash, la barra de vida del mob vaciándose, el temblor de cámara y la sangre. |
| `12-third-person.gif` | Tercera persona | F5, y la preparación y el golpe del cuerpo. |
| `13-weapon-packs.gif` | Packs | Un YAML abierto, un `/cc reload`, y el arma con los números nuevos. |
| `14-arcabuz-shots.gif` | Tooltips | El inventario con el tooltip de un arma, línea por línea. |

Los placeholders de `docs/media/` son tarjetas grises con el nombre del slot. Se ven mal a
propósito, para que se note que falta la captura.

## Cómo grabar

1. Cliente de desarrollo, `gradlew runClient`, en un mundo plano con un dummy o un zombi quietos.
2. Grabar con OBS o con la grabadora de Windows, en 1920x1080 a 60 fps, o en 1280x720 a 30 fps si
   la máquina no da.
3. Guardar el bruto en `docs/media/_raw/<slot>.mp4`. Esa carpeta está en el `.gitignore`.
4. Convertir:

```bash
tools/ffmpeg/ffmpeg-2026-10-01-git-0b01ed76aa-full_build/bin/ffmpeg.exe \
  -i docs/media/_raw/01-reach-cone.mp4 \
  -vf "fps=20,scale=640:-1:flags=lanczos,split[s0][s1];[s0]palettegen=max_colors=128[p];[s1][p]paletteuse" \
  -loop 0 docs/media/01-reach-cone.gif
```

5. Reemplazar el placeholder con el GIF nuevo. Mismo nombre, misma extensión, misma carpeta.

## Reglas de la captura

- **El loop tiene que cerrar.** Un GIF que corta a la mitad se siente roto. Lo ideal es que
  empiece y termine en el mismo estado.
- **Nada de HUD de depuración.** Si aparece un log en pantalla, se recorta.
- **El chat cerrado.** Los mensajes de un servidor de pruebas distraen.
- **Menos de 6 segundos.** Más de eso y la gente no lo ve entero.
- **640 de ancho.** El README y las galerías los muestran a la mitad, y un GIF de 1920 pesa
  4 megas de más.
- **El cursor quieto.** Un cursor que se pasea por la pantalla roba la atención.
- **Un solo tema a la vez.** El GIF de la carga no tiene que enseñar también la guardia.
- **Si la captura es clara, va con una variante oscura.** El README se lee en fondo claro y en
  fondo oscuro, y un video con fondo blanco molesta en el modo oscuro. Lo fácil es grabar de noche
  en un mundo oscuro, o bajar la exposición en el OBS.

## Fotos fijas

Además de los GIF hacen falta cuatro capturas fijas, que van en las galerías de CurseForge y
Modrinth:

| Archivo | Qué tiene que mostrar |
|---|---|
| `docs/media/shot-hud.png` | La crosshair, el corchete de cooldown, la barra de carga y las esferitas de aguante, todo visible de golpe. |
| `docs/media/shot-tooltip.png` | El inventario con el tooltip de un arma, con las siete líneas. |
| `docs/media/shot-reactor.png` | El reactor encendido, con su radio de redstone visible. |
| `docs/media/shot-arcabuz.png` | El arcabuz en la mano, con la mecha encendida. |

## Iconos

- **Modrinth** pide un icono de 128x128. `docs/media/logo.png` es la banner, no sirve.
- **CurseForge** pide un logo cuadrado, ideal 256x256.
- Los dos salen de `cortescombatlogo.png` recortado, o de un dibujo nuevo. Las dos plataformas
  aceptan PNG y el fondo transparente pesa menos.
