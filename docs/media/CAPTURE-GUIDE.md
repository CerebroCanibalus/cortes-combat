# Los medios del repo

Lo que el README y las dos galerías (CurseForge y Modrinth) muestran. Seis archivos, y los tres
lugares apuntan a los mismos.

| Archivo | Qué muestra | Peso |
|---|---|---|
| `cool_sword_shi.gif` | El plano del mod en primera persona, con la daga de diamante | 2,9 MB |
| `reach.gif` | El cono: el golpe entra en el borde del arco | 3,5 MB |
| `cooldown.gif` | El cooldown duro: el golpe a mitad de cooldown no sale | 5,8 MB |
| `charged_attack.gif` | La carga: mantener, soltar, el impacto | 1,4 MB |
| `shield.gif` | La guardia: los tres golpes y el aguante que se apaga | 2,9 MB |
| `reactor.webp` | El reactor del Nether, la única foto fija | 108 KB |
| `logo.png` | La banner, sólo para el README | 172 KB |
| `icon.png` | El icono cuadrado, para Modrinth y CurseForge | 846 B |

## Dónde se usan

- `README.md` y `README.es.md` → los seis.
- `docs/curseforge-page.md` → los seis, en la galería y en la descripción.
- `docs/modrinth-page.md` → los seis, en la galería y en la descripción.

## Los paths tienen que ser relativos

Una captura pegada desde el explorador de Windows deja la ruta absoluta adentro:

```
![](D:\Modding\Neoforge\cortes-combat\docs\media\reach.gif)
```

Eso no funciona en GitHub, ni en CurseForge, ni en Modrinth. La forma que sí:

```
![](docs/media/reach.gif)
```

Y en HTML, `<img src="https://media.curseforge.com/PLACEHOLDER/gallery/reach.gif">`, porque las
dos plataformas sirven los archivos desde su propio CDN.

## El peso

Los GIF pesan entre 1,4 y 5,8 MB. GitHub aguanta hasta 10 MB por archivo, así que suben bien, pero
en CurseForge y Modrinth la galería tarda en cargar.

Si hay que aligerarlos, con el ffmpeg del sistema:

```bash
ffmpeg -i docs/media/cooldown.gif -vf "fps=12,scale=480:-1:flags=lanczos,split[s0][s1];[s0]palettegen=max_colors=48[p];[s1][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle" -loop 0 docs/media/cooldown-small.gif
```

Eso deja el de 5,8 MB en 4,3 MB. **Pero no se sabe si el resultado se ve bien**, así que la
comparación es a ojo, con el GIF original al lado, y no una regla.

⚠️ El encoder de GIF de ffmpeg es peor que el de la herramienta con la que se capturaron: con la
misma resolución y los mismos 20 fps, **ffmpeg agranda los archivos** en vez de achicarlos. La
compresión con ruido de captura de pantalla no se deja. Si hay que aligerar de verdad, el camino
es recapturar con menos fps.
