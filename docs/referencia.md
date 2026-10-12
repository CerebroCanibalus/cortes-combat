# Referencia — comandos, harness y convenciones

> Extraído de `AGENTS.md` (P0: se copia literal, no se resume). Lo que vive acá es **consulta
> frecuente y estable**: comandos exactos, el harness `/cc` y las convenciones de registro.

## Comandos


```
gradlew build               # compila + jar
gradlew runClient           # cliente de dev
gradlew runServer           # servidor de dev
gradlew runGameTestServer   # corre gametests y sale
gradlew runData             # datagen → src/generated/resources
```

`gradlew` **sin `./`** (Windows). `org.gradle.configuration-cache=true`; si tocás un input de build
y falla por cache → `--no-configuration-cache`. JDK instalado **25**, toolchain pide **21**,
`foojay-resolver-convention` lo descarga. `neo_version=21.1.250` ya en cache.


---

## Harness: `/cc` (permiso 2)


```
/cc weapons        → 11 vanilla + las 30 nuestras, en la hotbar
/cc weapon <item>  → cualquier item, c/ autocompletado
/cc shield         → escudo en la mano
/cc fullguard      → restaura la aguante
/cc nofood         → comida a 2, p/ probar que la regen se frena
/cc speed <ticks>  → velocidad p/ alcanzar objetivos rápido
/cc teleport <n>   → te teletransporta n bloques hacia adelante
/cc stats          → IMPRIME las stats resueltas del arma en mano
/cc viewmodel      → ESTADO= del viewmodel
/cc cone           → toggle del cono de alcance
/cc trace          → toggle del trace por swing (off por default)
/cc gui            → toggle del HUD
/cc hud            → geometría del widget en píxeles (el que destrabó los corchetes)
/cc input          → las puertas del press izq., en orden, + VEREDICTO
```

**`/cc input` es el que contesta "el click dejó de funcionar".** Imprime cada gate que puede rechazar
un press —con el valor que consulta y su veredicto— en el mismo orden que `SwingInputHandler.press`, y
una línea `VEREDICTO` con la primera puerta bloqueada. Existe porque un playtest reportó "no puedo
golpear, romper ni interactuar" y el log **⊘ tenía ni una línea `[client]`**: hay 8 lugares que
rechazan un press, 6 silenciosos a propósito. **Lee los campos, ⊘ recalcula nada.**

`/cc stats` es el más importante: perfil, alcance (distancia + ángulos), cooldown, carga y especial.

> **Las tres sesiones que no produjeron nada útil:** el log tiene 2 statics en singleplayer, así que un
> flag que el comando cambia en el servidor **⊘ es el flag que el cliente lee**. Por eso los payloads
> viajan. Varios playtests no dejaron líneas `[client]` porque faltaba correr `/cc trace`.

---
