<!-- Extraido de AGENTS.md por la compactacion del 2026-10-13. Es el indice de la memoria del proyecto: AGENTS.md lo referencia. -->

## Lecciones — la parte que más vale

Cada una costó un bug. Están juntas porque **todas son sobre la distancia entre lo que el código
dice y lo que el juego hace**, o entre lo que el test afirma y lo que el jugador ve.

### Tests

- **Una clase que implementa `LayeredDraw.Layer` ⊘ se puede testear en JVM pelada** — falla al
  cargar, antes de la 1ª aserción. La matemática vive en `ConeGauge` separada, por eso, ⊘ por gusto.
- **Component tests ⊘ ven callers.** `GuardState` tenía sus 4 celdas verdes mientras una guardia rota
  se rellenaba sola, porque el bug estaba en el argumento que le pasaba `CombatTicker`. **La decisión
  tiene que bajar a donde un test la alcanza.**
- **Un test que falla puede tener razón el código.** 3 veces: un eje `up` imposible a 60°, una
  aserción sobre el relleno de la barra, y una comparación escrita al revés. Reconocer cuándo el
  test está mal es parte del trabajo.
- **Y un guard que no *puede* fallar no afirma nada.** El del piso de comida afirmaba
  `healTicks == 0` corriendo sólo con la comida ya bajo el piso — donde el reloj nunca se
  incrementó, así que era verdadera con o sin el `else` que resetea. Se arregló **cargando el
  reloj arriba del piso primero y después sacándole el piso**. Igual de grave y más silencioso:
  **un guard que lee *prosa* en vez de código** — buscaba `ModifyExpressionValue` en un archivo
  cuyo javadoc nombra esa anotación para explicar por qué no la usa. El test falló contra mi
  explicación de que no lo hago. ∴ **los guards de source leen el archivo sin comentarios.**
- **Un test acoplado a datos provisorios es peor ⊘ tener test.** El invariante va **en el canal**,
  ⊘ en el offset compuesto.
- **El invariante va en el canal.** "El wind-up no pierde más que un tick de valor en un frame" es
  exacto, independiente del perfil.
- ⊘ **ignorar la infraestructura que hace el test correr**: DataFixerUpper en `testRuntimeOnly`
  (cualquier clase c/ `Codec` estático lo necesita), `UseJUnitPlatform()`.
- ⊘ **Los enums del proyecto no implementan `StringRepresentable`** — `NoClassDefFoundError` en
  `<clinit>`, que parece un test roto.
- **Y un test que afirma la convención equivocada es peor que ⊘ test**: uno decía *"un strike va
  negativo y un wind-up va positivo"* — **afirmaba que un wind-up levanta el brazo hacia atrás**, y
  estaba verde mientras el playtest reportaba exactamente eso. Cuando la decisión se revela mala, la
  memoria se reescribe, y lo nueva tiene que decir **qué es la regla** y ⊘ qué número es.
- **Un techo que protege lo que no necesita.** El de 60° en las poses **prohibía una motion que
  vanilla ya hace** (su swing llega a 68.8), y por eso el golpe salía como una puñalada. Los techos
  tienen que mirar el rango real de la API de referencia, ⊘ un número redondo.

### Bugs de razonamiento

- **Un `lerp` sin clampear se pasa del objetivo cuando el frame se alarga, y eso se lee como lag.** La
  vineta interpolaba `alpha + (target-alpha)*dt*SPEED`: a 60 fps da 0,1 y nadie lo ve; con un tirón
  `dt` se va, el factor pasa de uno, **el alpha se pasa** y oscila. El playtest lo reportó como DOS
  síntomas —"parpadea" y "siente lag"— y **es uno**. **Ninguna constante de velocidad lo arregla**:
  lo arregla `1 - e^(-rate*dt)`, acotado por construcción. **Un síntoma que aparece "cuando hay lag"
  suele ser un bug de `dt`, ⊘ de rendimiento.**
- **Un número que se justifica con algo que cambió sobrevive a ese cambio.** El lunge movía al
  cliente con `step * 0.5`, un factor para compensar que el teleport viejo se pasaba cada tick. Al
  pasar a moverse solo, la corrección **cubrió la mitad de la distancia**. La pregunta útil ⊘ es
  "qué número es" sino **"qué estaba corrigiendo, y eso todavía pasa"**.
- **Un `Iterator` sobre una lista que el propio loop modifica tira `ConcurrentModificationException`.**
  La versión que no crashea reconstruye por índice.
- **GLFW ⊘ entrega el evento de "botón soltado" si la ventana pierde el foco.** El press abrió un
  swing, el release nunca llegó, `holding` quedó `true` **por el resto de la sesión**: ⊘ atacar, ⊘
  romper, ⊘ interactuar, sin error en el log. **La firma: holds de 142 y 84 ticks al final de la
  sesión.** ∴ `holding` y `predictedHolding` se reconcilian **contra el estado físico del botón cada
  tick** (`glfwGetMouseButton`), ⊘ contra el stream de eventos — y se cierra con `abandon()`, ⊘
  `release()`, que pagaría un golpe contra un mundo que el jugador ya dejó.
- **Devolver `NOT_OURS` para silenciar una cosa entrega el click entero.** `press` tenía
  `if (!canCharge) return`, que para la ropera y la daga ponía `claim = NOT_OURS` y el mixin dejaba
  de cancelar: **el ataque entero pasaba a vanilla.** El comentario decía "sin barra de carga" y el
  `return` apagaba la barra entregando el click.
- **Arreglar un workaround sin arreglar la capacidad que le faltaba convierte un bug en otro.** El
  gate existía porque al cliente le faltaba `if (!profile.canCharge()) return 0`. Quitado el gate,
  una daga de un segundo de hold se reportaba **carga completa**. La guarda vive **en `AttackTiming`**,
  ⊘ en los tres call sites.
- **Estar en la `TABLE` de viewmodel ⊘ es prueba de que corra.** `ropera()` y `dagger()` estaban
  escritas, conectadas y con tests, y **nunca renderizaron**: dependen de que `holding` sea `true`, y
  el gate de arriba hacía que nunca lo fuera. La pregunta que faltaba ⊘ era "¿está el perfil?" sino
  **"quién enciende el canal"**.
- **La mitad de un bug puede estar escondiéndose detrás de la otra.** "La ropera y la daga no tienen
  animación" se contestaba con un perfil nuevo; la respuesta real era un `return` de tres líneas en
  otro archivo. **Dos pedidos, un solo cambio.**
- **`Particle#setSize` ⊘ es el tamaño del quad.** Cambia la caja de colisión; lo que dibuja es
  `quadSize` (default 0.1–0.2). El slash llevaba `setSize(0.9, 0.9)` y se veía a **diez centímetros**:
  el código decía 0,9 y la pantalla no mostraba nada, **que es la peor forma en que un número puede
  mentir.**
- **Un componente en su sitio y su consumidor muerto ⊘ se distinguen leyendo.** El sable **⊘ tenía
  track de `BREAK`**: el canal estaba en cero en todos los frames de todas las sesiones. Un track en un
  canal que nada puede mover es un comentario que cuesta una suma.
- **Las guardas que convierten un crash en un bug invisible esconden el bug.** Las cuatro guardas de
  UV que puse para no matar el render thread por un sprite nulo son lo que convirtió "sprite no
  bindeado" en "no se ve nada". **Un crash te lleva al bug en un paso; un quad invisible te lleva a
  sospechar del arte.**
- **`SwordItem.createAttributes` toma un OFFSET del attack speed, ⊘ el total.** El test que existía
  no podía atraparlo: assertaba `ticksFor(family.attackSpeed())`, **el número declarado afirmándose a
  sí mismo**. ∴ `attackSpeedOffset()` vive al lado de los dos números y un test pasa por la
  aritmética real del item.
- **"Si no lo reconocemos, tratémoslo como lo más común" convierte discretamente todo el mundo en un
  arma.** `deriveProfile` caía a `SWORD` → un bloque de tierra heredaba `charge_profile`. ∴ existe
  `WeaponProfile.NONE`, y es un perfil, ⊘ un `if`.
- **Una regla escrita mal y después "precisada" en vez de corregida se paga sola.** `GuardState.tick`
  llegó a 3 parámetros para describir un aturdimiento con otras palabras. La corrección fue **borrar**
  y dejar un flag.
- **Un número correcto ⊘ basta: el bug puede estar en la relación entre dos.** Tres rondas reportando
  "los corchetes están descentrados" c/ todos los números individualmente bien. El derecho quedaba
  **1px** más cerca del centro. **Una asimetría de 1px es invisible como número y muy visible como
  forma.**
- **Un diagnóstico que recalcula la aritmética es una segunda copia de ella, y uno que miente es peor
  ⊘ tener ninguno.** `/cc hud` lee la **misma** `ConeGauge.geometry()` que dibuja el layer.
- **Cuando un síntoma visual se repite y todos los valores se ven bien, el siguiente paso ⊘ es
  retunear: es imprimir la geometría y mirarla.** Y un recorte de screenshot se come la única
  referencia que la pregunta necesita.
- **Un log limpio de errores ⊘ prueba que todo ande.** Casi reporté un bug falso: leí un
  `server-out.log` sin errores pero el build había fallado y ⊘ arrancó servidor. Hay que mirar que el
  build pasó **Y** que hay `Done (Xs)`, en ese orden.
- **Las 2 líneas DEBUG de un mixin que se queja de su firma son el aviso, ⊘ el ruido.**
- **Un comentario que explica *por qué* una rareza es correcta es exactamente donde hay que ir a
  mirar si la rareza lo es.** La espada tenía `TEMPO.put(WeaponProfile.SWORD, 0.85F)` c/ una nota que
  decía *"0.85 puts it at 10"* -- y `scaleFor` **multiplica la velocidad**, así que 0.85 **frena**:
  quince ticks contra los doce de vanilla. El mismo 0.85 está en el hacha, donde la nota dice
  *"slowed to twenty four"* y **acierta**, así que la tabla era coherente y **la única fila que
  discordaba c/ su propia nota era la que nadie afirmaba**. `IframesTest` recorría la tabla y listaba
  a la espada como el caso veloz; el hacha tenía su test y la espada no. Pasó un round entero más
  lenta que vanilla c/ el comentario diciendo que era más rápida.
- **Y el número real tampoco era el de la nota:** `20.0 / 1.6` da 12,4999… en punto flotante, así que
  la espada de vanilla son **doce** ticks y ⊘ el *"12-13"* que la nota al lado sugería. **Un rango en
  una nota ⊘ es un número contra el que medir**, y ése fue el número que hizo falta para ver el bug.
- **Un guard que lee prosa encuentra su propia explicación, tercera vez.** El comentario que cuenta
  este bug **cita la fila vieja** para poder contarlo, así que el guard de `SwordTempoTest` **tira los
  comentarios antes de preguntar** -- igual que el de `ReactorPhaseTwoWiringTest`. Es la forma barata
  de escribirlo bien y la única que deja que un comentario explique el bug que documenta.

### La ronda del arcabuz — el estado que vivía en el lugar equivocado (2026-10-12)

> **La ronda está archivada entera en `CHANGELOG.md`** (`## Ronda del arcabuz — cuatro rondas, un solo
> error`, 2026-10-12): los tres síntomas, la causa, los tres parches que fallaron uno por uno y el
> refactor. Acá queda **lo que se transfiere a cualquier otro sistema**:

- **El estado de una cosa va en esa cosa.** El error no era una cuenta: era que la recámara, la mecha y
  la recarga del ARMA vivían en el JUGADOR, así que cambiar de arma heredaba lo del anterior. Y la señal
  de que estás parchando la causa equivocada **no es que el arreglo falle: es que aparece un síntoma
  nuevo de la misma familia** — pasó tres veces.
- **El tick absoluto de fin, ⊘ un contador.** Guardando `fuseEndsAt` / `reloadEndsAt` el componente se
  toca **4 veces por ciclo de disparo y nunca entre medio**; con un contador había que reescribirlo cada
  tick, y cada escritura reemplaza el `ItemStack` del cliente y dispara la animación de cambio de item
  (60 cm de arma). Decidir pasa a ser comparar contra `level.getGameTime()`: una función pura.
- **Premio inesperado:** un arma guardada en un cofre **conserva su estado**, porque viaja con el item.

#### Y las siete lecciones que quedan

1. **Un valor cacheado que solo puede subir falla hacia adelante.** `withChamber` era asimétrico —
   `if (!chamberOnStack) return this;` — así que un `loaded=true` viejo **no se podía limpiar nunca**.
   No se equivocaba al azar: se equivocaba siempre en el mismo sentido. **Un espejo que se reescribe
   antes de cada decisión no puede quedar viejo; uno que solo puede llenarse, sí.**

2. **Una asimetría "a propósito" puede tener una justificación falsa, y el comentario la hace parecer
   una decisión.** El de `withChamber` decía que releer `false` durante la combustión *"vaciaría un
   arma a punto de disparar"* — **y `light()` ya devuelve la recámara vacía**, porque encender la mecha
   **es** lo que la gasta. Durante la combustión los dos valores ya coinciden. **Un comentario que
   explica *por qué* una rareza es correcta es exactamente donde hay que ir a mirar si la rareza lo es.**

3. **Un guard puede fijar una equivocación con la misma firmeza con que fija un invariante.** Yo escribí
   el guard que exigía la asimetría, con un mensaje que la justificaba (*"si alguien lo hace simétrico
   para arreglarlo, vacía el arma..."*). **Era la razón por la que la dirección no se podía corregir**
   sin borrarlo primero. ∴ cuando un guard falla tras un arreglo, la primera pregunta es si el guard
   protegía un invariante o una decisión que resultó mala.

4. **El estado de una cosa va en esa cosa, y los parches sobre el síntoma se cuentan.** Tres parches,
   tres síntomas, un error. La señal de que estás parchando la causa equivocada no es que el arreglo
   falle: es que **aparece un síntoma nuevo de la misma familia**.

5. **Un `super()` puede pisar el tipo, y el paquete de spawn viaja el tipo, ⊘ la clase.** `MungusShell
   extends LargeFireball` y el constructor que **dispara** hace `super(EntityType.FIREBALL, ...)` → el
   `getType()` del arma era `FIREBALL`, así que **el cliente construía una bola de fuego vanilla** y
   simulaba otra física, mientras el servidor simulaba la nuestra. Los tirones eran la distancia entre
   las dos copias. Se arregla sobreescribiendo `getType()` (`Entity#getType` no es final). **El síntoma
   de que falta es silencio: el tipo de vanilla existe, la entidad se crea, y lo único que se ve es una
   bola que tiembla.**

6. **Cuando un síntoma visual se repite y ya probaste dos anclas, el siguiente paso es instrumentar, ⊘
   la tercera suposición.** El humo de la mecha se reportó dos veces como "sale de en medio de la
   pantalla" y las dos anclas posibles (cámara fija / matriz del item) ya se habían probado y rechazado
   una vez cada una. **Un solo número en el log cerró la pregunta:** `distOjo` (0,94 constante → está
   sobre el arma, ⊘ en la lente) y la traslación de la matriz (**su Z saltaba entre −0,92 y +0,71, y una
   distancia AL FRENTE no puede ser negativa** → la matriz está en un marco que rota con el jugador, ⊘
   en el de la vista). Es la misma lección de `/cc hud`: **imprimir la geometría y mirarla.**

7. **Un guard que descarta la parte que pregunta está verde.** `SoundPoolTest` comparaba los nombres de
   `sounds.json` contra los archivos del disco **tirando la carpeta de los dos lados**
   (`id.lastIndexOf('/')`), así que `sounds/combat_fx/arcabuz_mosca_loop.ogg` y
   `sounds/weapons/arcabuz_mosca_loop` comparaban **el mismo nombre pelado** — y la carpeta era **lo
   único roto**. Es la segunda vez: la primera fue `sweeping_edgeV2`, donde lo normalizado era una
   mayúscula. **La pregunta es siempre la misma: ¿qué está tirando el guard antes de comparar?**

### Herramientas

- ⊘ **`write`/`edit` corrompe texto insertado.** CJK y acentos. **Escaneo CJK antes de commitear.**
- **Y leer un archivo con acentos por PowerShell los mangla en la *pantalla*, no en el disco.** Un
  `Get-Content` sobre `AGENTS.md` devuelve `Estado tǸcnico` y `3�� persona` (display, ⊘ disco), y entonces
  un `edit` con ese texto **no matchea** y parece que el archivo cambió. **La herramienta `read` da
  UTF-8 correcto: usala para leer, siempre.** El síntoma es `Could not find oldString` sobre un archivo
  que no tocaste.
- **Borrar un attachment deja su clave en el `playerdata`, y el mundo lo dice en el log.**
  `Encountered unknown or non-serializable data attachment cortescombat:arcabuz. Skipping.` Es benigno y
  **se cura solo**: "Skipping" al cargar significa que el próximo guardado lo escribe sin él. **Y ⊘ se
  arregla con código de compatibilidad** — en este caso el dato viejo *era* el bug (la recámara guardada
  en el jugador), así que leerlo sería reintroducir la causa. Es la excepción honesta a "leé la forma
  vieja": **cuando la forma vieja es el bug, se tira.**
- **Reescribir un archivo entero para cambiar un párrafo es la operación más peligrosa que tengo.**
  Documentando el arcabuz reemplacé `.gitignore` completo y **me llevé las reglas de `refs/`,
  `build/`, `run/` y `tools/ffmpeg/`**; `git add -A` empezó a agregar repos embebidos y 600 MB de
  ffmpeg. Lo agarró el aviso *"adding embedded git repository"*. **En un archivo de reglas, cada
  línea que no toqué sigue siendo una regla que no puedo ver.**
- **Un parche por índice de línea es un parche por número.** Encadené varios y perdí firmas de
  métodos y llaves de más — **dos veces la misma noche**. El síntoma es `illegal start of type` en una
  línea que no parece tener nada. La respuesta es `javac`, ⊘ más parches: el error dice qué falta.
- **Y `git checkout -- <archivo>` no es una revert, es una apuesta.** Revertí un archivo que yo había
  roto y **borré trabajo de la ronda anterior, nunca commiteado**. Lo recuperé con `javap -c` contra
  el jar del último build — **suerte pura**: si no hubiera rebuild justo antes, estaba perdido.
- ⊘ **Un commit no es un commit. Es una red.** El costo de no commitear ⊘ es olvidarse: es que una
  operación reversible deje de serlo.
- **Y la red ⊘ está completa hasta que se EMPUJA.** Una sesión entera —54 commits, la ronda de FX, el
  arreglo del reactor y todo el paquete de documentación— quedó viviendo sólo en el disco local: cada
  `git status` decía "limpio" y cada `git log` mostraba el trabajo, porque **los dos miran el HEAD
  local**. La única pregunta que lo ve es `git log origin/main..HEAD`. Y el síntoma desde afuera es
  peor que un error: **GitHub mostraba una versión vieja del proyecto como si fuera la actual**, y el
  README "no aparecía" —ni el archivo, ni renderizado abajo— porque su commit nunca había salido de
  la máquina. **Un `git status` limpio ⊘ significa que el trabajo esté en más de un lado.**
- **La salida de los comandos es información, ⊘ decoración.** Los tres accidents de arriba se evitaron
  o se recuperaron por mirarla, ⊘ por tener cuidado.

### Animación y render

- ⊘ **Todo se evaluaba a 20 Hz.** El item repetía cada posición 3 veces a 60 fps. Eso ⊘ es una
  animación: es una máquina dando pasos. Separado en `tick()` = avanza estado, `snapshot(partialTick)` =
  produce el frame.
- **Un canal tiene que medir su propia edad.** El hand-off lo destapó: `windup` crudo (feedback
  instantáneo) ⊕ `charge` con umbral (es-esto-una-carga) son **dos resoluciones del mismo hold**.
  Un solo número significando dos cosas causó que el FOV quedara pegado.
- **El frame del release tiene que mostrar el item todavía atrás.** El primer tick ya había avanzado.
  Resuelto c/ `pendingStrike`: el release *anuncia* el strike y el primer tick lo arranca en edad 0.
- **`STRIKE` es un bump (0→1→0)**, ⊘ un canal que queda en 1.
- **Todos los canales descansan en cero, s/ excepción.** `COOLDOWN` cuenta el cooldown *gastándose*,
  ⊘ el ya gastado: con "listo = 1" el canal descansa en uno y necesita un caso especial.
- **La longitud de un canal se elige para el movimiento, ⊘ se toma del reloj de otro sistema.**
  `GUARD_RISE_TICKS` era 11 por una teoría sobre peso; la guardia real sube un packet después del
  click, o sea 11 ticks eran latencia pura. Ahora 2, y la asimetría se dio vuelta: la caída (6) es la
  más lenta.
- **Sin timeline, por decisión.** Si alguna vez hace falta, sería *otro canal*, ⊘ un cambio de esta
  clase.
- **Un test acoplado a un reloj de otro sistema se rompe en cada retune.**
- **`Quaternionf` scratch, un solo `mulPose`.**
- **El HUD se dibuja del valor del servidor**, ⊘ de la predicción del cliente.
- **Fade por interpolación exponencial contra `getRealtimeDeltaTicks()`**, ⊘ lineal.
- ⊘ **Sin socket y sin frente suave en la barra.** Un frente suave hacia adelante pone un pixel a
  brillo completo junto a la crosshair con el arma sin lista (un "listo" falso).
- **El brillo más fuerte en el centro de la pantalla tiene que ser lo que el jugador apunta c/ él.**
- **`HudAnchor` es un único punto canónico** y todo lo demás es offset.
- **El cono sigue la mirada**, marco ortonormal anclado a la mirada, ⊘ al suelo.
- **El widget es simétrico por construcción**: los dos corchetes se miden con `ConeGauge.mirrorAnchor`
  y un test exige que los anclajes sumen `width - 1`.

### La ronda de la 3ª persona — las convenciones que estaban al revés

Detalle completo en `CHANGELOG.md`. Lo que queda acá es **la regla**, porque son las cosas que no se
deducen de un número:

1. **El cuerpo del jugador trabaja en GRADOS y el mixin convierte a radianes.** Sumarlos sin convertir
   es lo que produjo la voltereta: 46 grados como 46.0 son 46 vueltas.
2. **Negativo es adelante y arriba.** Leído de `setupAttackAnimation` (`xRot -= f1 * 1.2`, que llega
   a −69°) y de `poseRightArm`'s `ITEM` (`xRot * 0.5 − π/10`). **Positivo es hacia atrás.** El roll
   lateral también es negativo, de `zRot += sin(attackTime·π) · −0.4`.
3. **El TAIL suma encima de vanilla; hay que cancelar en HEAD.** El renderer asigna `attackTime` *antes*
   de `setupAnim`, y sólo si el perfil es nuestro.
4. **Las mangas son `ModelPart` aparte** y vanilla las copia del brazo *antes* de nuestra inyección.
5. **Un bump ⊘ puede ser un tajo** (canal `CHOP`, que sólo baja), y **la puerta es `reads(CHOP)`**, ⊘
   una lista de armas.
6. **`pendingStrike` retrasa un tick**, y para una ALTURA eso es un frame en reposo. `CHOP_START` es la
   **razón** entre las dos alturas, ⊘ un número elegido.
7. **La caída tiene que acelerar.** `(1−t)²` sale a tope y frena — un arma arrojada. `1 − smoothstep`.
   Y 4 ticks, ⊘ 6: el impacto es velocidad sobre distancia y la distancia nunca fue el problema.
8. **El golpe es de un brazo; la guardia y la carga usan los dos**, porque son postura y reunión.
