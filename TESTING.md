# Cómo testear el estado actual

Cliente abierto con `gradlew runClient`. Creá un mundo **Creative** con dificultad easy/normal
— en peaceful los mobs no te pegan y no vas a poder ver la viñeta ni probar el escudo.

---

## DEBUG DEL CONO — EMPEZÁ POR ACÁ

```
/cc cone
```

Togglea la visualización del cono. **Apagado es el default**, se prende con el comando.

Qué vas a ver:

| Elemento | Color | Qué es |
|---|---|---|
| Eje del cono | **Celeste** | La línea central, desde tu ojo hasta el alcance máximo |
| Borde del cono | **Celeste** | El círculo del cono a distancia máxima |
| Rayas cada 90° | **Celeste tenue** | Ayudan a leer el ángulo horizontal |
| Rectángulo azul | **Azul** | El límite vertical, dibujado plano al final del cono |
| Entidad golpeable | **VERDE** | Está DENTRO del cono. La podés pegar |
| Entidad no golpeable | **ROJO** | Está FUERA. No la vas a poder pegar |

Lo más importante: **el test de "está dentro" usa el mismo `ConeMath` que el ataque real.**
Si algo se dibuja verde, el juego te lo deja pegar. No es una aproximación visual.

**Cómo debuggear bien:** `/cc cone` + `/cc weapons` + caminá hacia un zombie. Cuando el zombie
pase de rojo a verde, te estás metiendo en el cono. Prestá atención a:

- ¿El zombie se pone verde demasiado lejos? → el alcance es muy largo
- ¿Se pone verde muy cerca? → demasiado corto
- ¿Se pone verde cuando está a tus costados pero no al frente? → el ángulo horizontal es muy ancho
- ¿Puede quedar verde si lo mirás desde arriba? → el ángulo vertical es muy ancho

`/cc gui` apaga nuestro HUD para comparar con el indicador vanilla.

---

## Lo que YA funciona (probalo)

### 1. El cono de alcance
```
/cc weapons
/cc cone      ← prende la visualización
```
Te llena la hotbar con 11 armas vanilla. Andá contra un zombie y probá cada una.

**Lo que tenés que notar:** el alcance es **más corto que el vanilla** en distancia pero más
**ancho** en cono. Por ejemplo la espada tiene 3.2 de distancia (el vanilla es 3.0) pero 35° a
cada lado. Vas a poder pegar cosas que están un poco a los costados, pero **no** cosas a tu
espalda, ni cosas muy arriba o muy abajo.

Probá `/cc stats` con cada arma para ver los números exactos.

### 2. La carga
Mantené el click izquierdo y soltalo.

**Lo que tenés que notar:** el golpe sin cargar hace el daño base. Manteniendo, sube el daño
hasta +50% en la espada. **El daño base nunca baja**, así que mantener el click nunca te
perjudica. That's the whole point.

Notá que el **indicador vanilla** (el sprite del brazo bajo la crosshair) es el de carga, y
que la **barra horizontal bajo la hotbar** es el cooldown.

### 3. El cooldown duro
Hacé click izquierdo muchas veces rápido.

**Lo que tenés que notar:** no podés atacar más rápido que la barra. No es que te haga menos
daño por hacerlo rápido como en el vanilla — directamente no te deja atacar. La barra se pone
**verde** cuando podés.

Probá la diferencia: espada (12.5 ticks) vs maza (20 ticks) vs daga. La maza es
visiblemente más lenta.

### 4. El autoataque
Mantené el click izquierdo sin soltar, apuntando a algo.

**Lo que tenés que notar:** ataca solo Y carga. Es lo que decidimos: no te castiga por no
saber cuándo pegarle.

### 5. La viñeta y el flash
Bajá de vida a propósito (un creeper, `/damage`, lo que sea).

**Lo que tenés que notar:** viñeta roja cuando estás bajo ~14 de vida, más intensa a 4. Y un
flash rojo cuando te pegan, más fuerte cuanto más daño.

### 6. El comando de stats
```
/cc stats
```
El más útil. Imprime perfil, alcance, cooldown, carga y especial del arma en mano. Si algo se
siente raro, fijate acá primero.

---

## Lo que NO va a funcionar todavía (no es bug)

Esto es importante para que no reportes como errores cosas que todavía no están hechas:

| No funciona | Por qué |
|---|---|
| **Las 5 armas nuevas** | No existen todavía (es el paso 9). Solo hay armas vanilla. |
| **Click derecho / especiales** | No implementado (paso 10). El click derecho no hace nada. |
| **La aguante del escudo** | Las 3 esferitas aparecen arriba de la crosshair, pero el escudo usa el bloqueo **vanilla**. Nada consume aguante todavía, así que se quedan en 3/3 siempre. Es el paso 8. |
| **Ignorar armadura (martillo)** | Calculado pero no aplicado. Necesita un damage type de datapack. |
| **Indicador de carga en el vanilla** | Se dibuja el cooldown vanilla, no la carga. Se arregla con el viewmodel (paso 14). |
| **Sonido propio** | Solo sonidos vanilla. Es producción tardía. |
| **Las esferitas de aguante bajan** | Ver arriba. |
| **Mobs con cono/cooldown** | Siguen con el sistema vanilla, por decisión. |

---

## Qué testear específicamente y anotar

Lo que necesito de esta vuelta:

1. **¿El cono se siente bien?** ¿Alcanzás lo que esperás, y no alcanzás lo que no deberías?
   Es el número más subjetivo de todo el mod. Si te parece corto o largo, decímelo.
2. **¿La carga se siente lenta o rápida?** La espada tarda ~1.25s en cargar completo. ¿Querés
   más o menos?
3. **¿El cooldown se siente justo?** ¿Hay alguna arma que se sienta *demasiado* lenta?
4. **¿La viñeta molesta?** ¿Es muy fuerte, muy débil, o justo? especially cuando estás
   sano, que no debería verse nada.
5. **¿El HUD se ve bien?** ¿La barra tapa algo? ¿Las esferitas están en el lugar correcto?
6. **La comida (paso 11, nuevo).** Lo que hay que mirar:
   - **Correr, picar y golpear ya no gastan comida.** Subí la barra a 20 con `/food`, corré un
     rato, rompé bloques: **tiene que quedarse en 20**. Si baja, el exhaustion sigue vivo.
   - **La curación cuesta comida.** Bajate la vida y esperá: te cura 1 punto cada 2 s y **baja la
     comida 1 punto por punto curado**. Ese es el ciclo cerrado.
   - **La comida vuelve sola.** 1 punto cada 4 s hasta 20, sin comer nada.
   - **Abajo de 6 de comida no te curás más.** Es el piso, y es el número a sentir.
   - **Líquidos: 20 ticks.** Poción, miel, leche y caldos se toman 1 s y no 1,6 s.
   - **Comer y recibir un golpe afuera de la guardia pierde el mordisco.** Con la guardia
     arriba **no**. Si al probarlo parece que "sólo parpadea", decilo: es una línea cambiarlo.
   - **La barra de comida ya no tiembla.** El temblor salía de la saturación en 0, y con el
     exhaustion afuera nunca baja. ¿Se echa de menos?
   - Los cinco números son config: `config/cortescombat-server.toml`. **`foodRegenInterval` es
     el número más importante del proyecto** — si se siente decorativo, subilo; si pelear queda
     en callejón sin salida, bajalo.

Si algo crashea, el log está en `run/client-out.log` y el crash report en
`run/crash-reports/`.
