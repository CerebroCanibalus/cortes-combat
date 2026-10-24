package com.cortes.combat.attack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.cortes.combat.enchant.CombatEnchantRules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the three enchantment hooks a replaced attack flow drops are actually called, and that
 * Sweeping Edge's wave has one caller that fires it once per swing.
 *
 * <h2>The hole this exists for, which this project has paid for four times</h2>
 *
 * <p><b>A tested function with no caller, and a caller with no test.</b> The maza's fall, the
 * sword's subtraction, the wear-on-hit and the trident's pull all shipped that way: the arithmetic
 * was verified by hand, the engine computed its own copy or none at all, and the build was green.
 * The enchantment hooks are the same shape one level up: {@code EnchantmentHelper} is a library of
 * correct functions nobody in this mod calls, because {@code Player#attack} was replaced whole.
 * Reading the source is brittle by design, and the alternative is a playtest with an enchanted
 * sword and no way to tell "Smite did nothing" from "the mob was not undead".</p>
 */
class EnchantmentHooksTest {

    @Test
    @DisplayName("the damage, knockback and post-attack hooks are all called")
    void theThreeHooksAreCalled() {
        String engine = read();

        assertTrue(engine.contains("EnchantView.modifiedDamage("),
                "**Sharpness, Smite, Bane of Arthropods, Impaling and Density run here.** They are "
                        + "the body of `ServerPlayer#getEnchantedDamage`, which only `Player#attack` "
                        + "calls -- and that flow is replaced. Without this line an enchanted sword "
                        + "does exactly the damage of an unenchanted one, silently. The call goes "
                        + "through EnchantView so the version-coupled helper lives in one file");

        assertTrue(engine.contains("EnchantView.modifiedKnockback("),
                "**and Knockback.** Vanilla's `getKnockback` is the other half of the same lost "
                        + "call; without it a Knockback II sword shoves like a plain one");

        assertTrue(engine.contains("EnchantView.postAttackEffects("),
                "**and the post-attack effects.** This is where Aspect of Fire and Wind Burst "
                        + "live -- an enchanted sword that cannot set anything on fire is a bug a "
                        + "player notices, a build a test does not");
    }

    @Test
    @DisplayName("the post-attack effects run once per body that took the hit")
    void oncePerLandedBody() {
        String engine = read();
        int guard = engine.indexOf("if (anyLanded)");
        int effects = engine.indexOf("EnchantView.postAttackEffects(");

        assertTrue(guard > 0 && effects > 0);
        assertTrue(guard < effects,
                "**inside the landed branch.** Firing it for a swing that hit nothing would set "
                        + "the attacker's hands' enchantments loose on a whiff, and the guard is "
                        + "what says the hook is about a hit");
    }

    /**
     * Sweeping Edge ya no es un bonus de daño: es una onda que vuela.
     *
     * <p>El test cambia con el encantamiento y conserva lo que lo hacía útil: <b>una fórmula pura
     * sin llamador es el bug que esta clase sigue encontrando</b>. La tabla de niveles puede estar
     * perfecta y el encantamiento no hacer nada si nadie dispara la onda.</p>
     *
     * <p>Y afirma la invariante que el gap destapó: <b>una onda por tajo, no por acierto</b>. El
     * llamador tiene que estar en {@code resolveSwing} y no en {@code landTheHit}, porque
     * {@code landTheHit} corre dos veces cuando la ventana de coyote confirma el golpe diferido --
     * un tajo al aire tiene que soltar la onda, y un tajo que confirma tarde no tiene que soltar
     * dos.</p>
     */
    @Test
    @DisplayName("Sweeping Edge dispara la onda: una por tajo, y saltea al objetivo del golpe")
    void sweepingEdgeFiresTheWave() {
        String engine = read();

        assertTrue(engine.contains("SlashWave.fire("),
                "**la onda tiene que tener llamador.** Una fórmula pura sin nadie que la dispare es "
                        + "el bug que esta clase sigue encontrando: la tabla quedaría bien y el "
                        + "encantamiento no haría nada");

        // **Un solo llamador, y en resolveSwing.** Tres aserciones que dicen lo mismo desde tres
        // lados: la posición prueba dónde está, el conteo prueba que no hay un segundo, y el conteo
        // cuenta el NOMBRE del método y no el texto de una llamada -- la primera versión de este
        // guard afirmaba el string exacto del llamador y un segundo llamador con otro argumento se
        // le colaba. Se verificó saboteando: metido un segundo llamador en `landTheHit`, el guard
        // tiene que morir.
        int call = engine.indexOf("fireSweepingWave(player, targetId");
        int land = engine.indexOf("static void landTheHit(");
        assertTrue(call > 0 && land > 0 && call < land,
                "**la onda sale con el tajo, no con el acierto**: el llamador tiene que estar en "
                        + "`resolveSwing` (que corre una vez por tajo) y no en `landTheHit` (que "
                        + "corre dos veces cuando la ventana de coyote confirma el golpe diferido)");
        int occurrences = 0;
        for (int i = engine.indexOf("fireSweepingWave("); i >= 0;
                i = engine.indexOf("fireSweepingWave(", i + 1)) {
            occurrences++;
        }
        assertTrue(occurrences == 2,
                "**una declaración y UN llamador**, y el conteo da " + occurrences + ". Un segundo "
                        + "cobro del mismo tajo saldría de un segundo llamador, no de un bug en la "
                        + "onda -- y un llamador con otro argumento es exactamente cómo se escapaba "
                        + "la versión anterior de esta aserción");

        // El cono del golpe sigue contando lo que siempre contó: la onda es un proyectil aparte y
        // no le suma objetivos a la espada.
        assertTrue(engine.contains("WeaponTables.maxTargets(stats.profile())"),
                "el número de cosas que pega UN golpe sigue siendo `maxTargets`; la onda no le "
                        + "agrega objetivos al cono");
        assertFalse(engine.contains("maxTargets(profile, ") || engine.contains("maxTargets(profile,"),
                "y la tabla no toma un encantamiento: el conteo es un hecho del arma, no del item's "
                        + "enchantments");
    }

    /**
     * **Los dos encantamientos de cooldown tienen llamador, y donde tienen que estar.**
     *
     * <p>Es el mismo agujero que el resto de esta clase, una familia más arriba: la tabla de
     * {@code CombatEnchantRules} puede estar perfecta, con sus tests verdes, y el arma seguir
     * cobrando los cinco segundos de siempre si nadie lee el nivel del arma. La aserción no es "el
     * número es sesenta" -- eso lo afirma {@code ParryTest} sobre el estado -- sino que el
     * <b>nivel entra</b>, que es la parte que vive en un archivo que ningún test puede ejecutar sin
     * un servidor.</p>
     *
     * <p>Y la aserción negativa es la que más vale: {@code ParryState.COOLDOWN_TICKS} no puede
     * aparecer en este archivo. Si vuelve, es que alguien cambió el número que se le manda al
     * cliente por la constante, y la barra del HUD pasaría a durar cinco segundos sobre un arma que
     * se destraba en tres.</p>
     */
    @Test
    @DisplayName("Reflejo de Acero y Audacia llegan al cooldown que se cobra")
    void theCooldownEnchantmentsReachTheCooldown() {
        String special = readSpecial();

        assertTrue(special.contains(".activate(CombatEnchantments.level("),
                "**el nivel del arma tiene que entrar en `activate`.** La regla pura no sirve de "
                        + "nada si el parry abre su ventana con el cooldown de siempre: es una "
                        + "funcion probada y sin llamador, la forma que esta clase existe para cazar");
        assertTrue(special.contains("CombatEnchantments.STEEL_REFLEX"),
                "y el que se lee en el parry es Reflejo de Acero");
        assertTrue(special.contains("CombatEnchantRules.lungeCooldown("),
                "Audacia, en cambio, no puede pasar por el estado: el cooldown del lunge lo cobra "
                        + "`AttackCooldown.consume`, asi que la regla se llama ahi");
        assertTrue(special.contains("CombatEnchantments.AUDACITY"),
                "y con el nivel de Audacia del arma en la mano");
        assertFalse(special.contains("ParryState.COOLDOWN_TICKS"),
                "**y la constante no puede volver.** El numero que se le manda al cliente sale del "
                        + "estado armado, no de la constante: con la constante, la barra del HUD "
                        + "duraria cinco segundos sobre un arma que se destraba en tres");
    }

    /**
     * **Adrenalina: la reduccion vive en el evento del daño, y el cobro en el motor.**
     *
     * <p>Es un encantamiento partido en dos archivos a proposito: el <b>daño que entra</b> se reduce en
     * {@code LivingIncomingDamageEvent}, que es el unico lugar donde eso es posible, y el <b>daño que
     * vuelve</b> se cobra en el golpe. Los dos guards afirman el uso, no la existencia: un
     * {@code setAmount} que nadie llama es una reduccion que el jugador no recibe.</p>
     */
    @Test
    @DisplayName("Adrenalina reduce en el evento y cobra en el motor")
    void adrenalinaReachesBothHalves() {
        String handler = readFile("src/main/java/com/cortes/combat/enchant/AdrenalinaHandler.java");
        assertTrue(handler.contains("\n        event.setAmount(event.getAmount() - saved);"),
                "**Adrenalina no reduce**: el encantamiento existe, se puede poner en la daga, y el "
                        + "daño entrante sigue entrando entero. **Y la asercion pide la llamada al ras "
                        + "y no el nombre del metodo**: un guard que busca `event.setAmount(` queda "
                        + "verde con la llamada envuelta en un `if (false)`, que es la cuarta vez que "
                        + "este archivo se equivoca asi");
        assertTrue(handler.contains("WeaponProfile.DAGGER"),
                "**y la puerta es la daga.** El hook vive en el camino generico del daño, asi que sin "
                        + "esto un datapack que mueva el encantamiento le da un tercio del daño "
                        + "entrante de menos a una espada");
        assertTrue(handler.contains("CombatEnchantRules.adrenalinaCooldown("),
                "y el cooldown tiene que salir de la tabla, no de una constante suelta");
        assertTrue(handler.contains("MOVEMENT_SPEED"),
                "y la Velocidad es la mitad que hace que la Adrenalina se sienta, no solo se calcule");

        String engine = read();
        assertTrue(engine.contains("\n            cashAdrenalina(player, target);"),
                "**y el daño ahorrado no se cobra**: la mitad ofensiva del nivel dos existe en el "
                        + "estado y nadie la lee. La llamada al ras, por lo mismo que la de arriba");
        assertTrue(engine.contains("state.banked()"),
                "y el cobro tiene que leer la cuenta del estado, no recalcularla");
    }

    /**
     * **Los seis de la ultima ronda, cada uno con su asercion de uso.**
     *
     * <p>Cinco se cobran en el golpe y viven en {@code lastRoundOnHit}; Capacitor vive en el parry y
     * Trabazon tiene su handler propio. Las aserciones piden <b>la llamada al ras</b> donde importa,
     * porque el string del nombre del metodo sobrevive a un {@code if (false)} -- la leccion que este
     * archivo ya pago cuatro veces.</p>
     */
    @Test
    @DisplayName("los seis de la ultima ronda llegan a sus hooks")
    void theLastRoundReachesTheirHooks() {
        String engine = read();
        assertTrue(engine.contains("\n        lastRoundOnHit(player, target, stats, result, chargeRatio);"),
                "**los cinco del golpe no se cobran**: los encantamientos existen, se pueden poner en "
                        + "su arma, y el golpe hace lo de siempre");
        assertTrue(engine.contains("CombatEnchantRules.SENTENCIA_STREAK"),
                "**Sentencia no revienta**: la cadena llega a cinco y no pasa nada");
        assertTrue(engine.contains("other == player || other == body"),
                "**y el reventon tiene que dejar afuera al que ataca**: un premio que te cobra a vos es "
                        + "el veto C2 con otro nombre");
        assertTrue(engine.contains("CombatEnchantRules.palizaStreak("),
                "**Paliza no cuenta la serie**, asi que el golpe cargado empuja siempre lo mismo");
        assertTrue(engine.contains("CombatEnchantRules.gripaMaxStacks("),
                "**Gripa no apila moco**");
        assertTrue(engine.contains("ModEffects.trabazon()"),
                "**Trabazon no clava al rival**");
        assertTrue(engine.contains("CombatEnchantRules.corteGemeloInterval("),
                "**Corte Gemelo no corta**, y el nivel uno y el dos serian el mismo encantamiento");

        String parry = readFile("src/main/java/com/cortes/combat/special/ParryHandler.java");
        assertTrue(parry.contains("CombatEnchantRules.capacitorDamage("),
                "**Capacitor no devuelve el rayo**: el parry sigue contestando igual que antes del "
                        + "encantamiento");

        String trabazon = readFile("src/main/java/com/cortes/combat/enchant/TrabazonHandler.java");
        assertTrue(trabazon.contains("setDeltaMovement(motion.x, 0.0, motion.z)"),
                "**y el salto no se deshace.** El evento no es cancelable, asi que el impulso ya esta "
                        + "puesto cuando corre: sin devolverlo el jugador salta igual y el "
                        + "encantamiento no hace nada");
    }

    /**
     * **Retirada, y la trampa de orden que tiene.**
     *
     * <p>Cuatro hechos, y el cuarto es el que no se ve leyendo el codigo una sola vez: el golpe
     * previo se registra en {@code Lunge.prehit}, que limpia el conjunto <b>mientras el lunge no
     * este activo</b>. Un golpe registrado antes de {@code Lunge.begin} se borraria en el primer
     * tick sin que nada falle, sin error y sin golpe -- que es la forma de fallar que este proyecto
     * ya pago varias veces.</p>
     */
    @Test
    @DisplayName("Retirada: el impulso se invierte y el golpe va ANTES de abrir la ventana")
    void theRetreatLeavesBackwardsAndHitsFirst() {
        String special = readSpecial();

        assertTrue(special.contains("Lunge.begin(player, tierOf(player));"),
                "**el lunge ya no abre su ventana**, asi que no hay marcha atras que medir");
        assertTrue(special.contains("retreat(player, level, stats);"),
                "**Retirada no golpea al frente**: el encantamiento se puede poner en la ropera y el "
                        + "jugador que se agacha sale corriendo sin haber pegado nada");
        assertTrue(special.contains("player.isShiftKeyDown() && CombatEnchantments.level("),
                "**y el agachado es la puerta.** Sin leer el agachado, Retirada seria el lunge "
                        + "normal apagado para todo el mundo");

        // El orden, que es la trampa: `prehit` limpia su conjunto si el lunge no esta activo. Se
        // busca por la llamada y no por su indentacion, porque un sabotaje que mueve la linea la
        // mueve de columna tambien -- y un guard que depende del espaciado muerde por la razon
        // equivocada, que es la mitad del problema.
        int opensWindow = special.indexOf("Lunge.begin(player, tierOf(player));");
        int hitsFront = special.indexOf("retreat(player, level, stats);");
        assertTrue(opensWindow >= 0 && hitsFront > opensWindow,
                "**el golpe del frente tiene que venir DESPUES de abrir la ventana.** Llama a "
                        + "`Lunge.prehit` antes y `wasHit` limpia el conjunto en el primer tick: el "
                        + "barrido vuelve a cobrar el mismo cuerpo y Retirada se vuelve el lunge "
                        + "normal con dos golpes");

        String lunge = readFile("src/main/java/com/cortes/combat/special/Lunge.java");
        assertTrue(lunge.contains("\n        wasHit(player).add(target);"),
                "**el golpe previo no se registra en el conjunto.** La firma de `prehit` seguiria "
                        + "ahi, compilaria y no haria nada -- y el cubo, centrado en el jugador y "
                        + "sin direccion, le cobraria otra vez al mismo cuerpo en el primer tick");

        // **Y la fraccion tiene que MULTIPLICAR, no solo existir.** Un 1,0 declarado y no aplicado
        // es una constante que nadie lee: el dia que se baje el numero en el playtest no hay donde
        // tocarlo, y lo que el jugador ve seria distinto de lo que el archivo dice. Es el mismo
        // shape que un canal con su consumidor muerto, que es lo que mas veces pago este proyecto.
        assertTrue(special.contains("* CombatEnchantRules.RETIRADA_FORWARD_HIT_SHARE;"),
                "**la fraccion de Retirada esta declarada y no se aplica**, asi que el golpe pegaria "
                        + "lo que diga `LungeDamage` y el numero del diseño no tendria efecto en el "
                        + "juego");

        // Y el cliente tiene que recibir la orden de invertir, no la vieja de dos argumentos.
        String motion = readFile("src/main/java/com/cortes/combat/client/LungeMotion.java");
        assertFalse(motion.contains("public static void begin(LocalPlayer player, int materialRank) {"),
                "**el impulso quedo sin la bandera de inversion**: el metodo viejo de dos argumentos "
                        + "compila, se ve correcto, y la marcha atras nunca sale para atras");
        assertTrue(motion.contains("double sign = retreating ? -1.0 : 1.0;"),
                "**y la inversion es el signo del impulso**, no de la mirada: voltearse para huir "
                        + "regalaria la espalda, que es exactamente lo que el encantamiento existe "
                        + "para no hacer");
        String input = readFile("src/main/java/com/cortes/combat/client/SpecialInputHandler.java");
        assertTrue(input.contains("LungeMotion.begin(player, tier, retreating);"),
                "**el cliente no le pasa la bandera al impulso**");

        assertEquals(1.0F, CombatEnchantRules.RETIRADA_FORWARD_HIT_SHARE, 1.0E-6,
                "**y la fraccion es 1,0 porque el golpe del frente NO es un bonus: es el golpe del "
                        + "lunge adelantado al press.** Una fraccion menor seria un nerf sobre un "
                        + "golpe que ya estaba pagado");
    }

    /**
     * **Rey de las Moscas, y su cadena entera.**
     *
     * <p>Lo que importa no es que la nube exista sino que <b>se dibuje</b>: un EntityType sin
     * renderer no es una entidad invisible, es un NPE en el render thread, y eso se cayo en las
     * fuentes antes que en un cliente.</p>
     */
    @Test
    @DisplayName("Rey de las Moscas deja una nube que se dibuja y que lastima")
    void theFlyLordLeavesAWorkingCloud() {
        String arcabuz = readArcabuz();
        assertTrue(arcabuz.contains("CombatEnchantments.REY_DE_LAS_MOSCAS"),
                "**El disparo nunca pregunta por Rey de las Moscas**, asi que el encantamiento se "
                        + "puede poner en el arcabuz y el disparo hace lo de siempre");
        assertTrue(arcabuz.contains("com.cortes.combat.registry.ModEntities.MOB_CLOUD.get()"),
                "**El disparo no suelta la nube**");

        // **Y cambia la munición, que es su identidad entera.** Sin esto el encantamiento era el
        // mismo disparo con otro resultado: cuesta lo mismo, hay que fabricar lo mismo, y lo unico
        // que cambiaba era un detalle visual. Le paso en un playtest -- se puso el encantamiento,
        // se aprieto el boton cuarenta veces y las cuarenta salio "sin Firestone".
        assertTrue(arcabuz.contains("return AmmoProfile.of(ROTTEN_FLESH_AMMO, 1);"),
                "**Rey de las Moscas no cambia la munición**: sigue gastando Firestone, asi que el "
                        + "jugador tiene que craftear lo mismo y el encantamiento no compra nada");
        // Y el orden: el encantamiento se pregunta ANTES que el componente, porque es lo que el
        // jugador eligio poner y lo que el tooltip promete. La lectura va por la facade para que
        // la pata 1.20.1 la traduzca, asi que el guard nombra la puerta y no el mecanismo.
        assertTrue(arcabuz.indexOf("CombatEnchantments.REY_DE_LAS_MOSCAS) > 0")
                        < arcabuz.indexOf("WeaponData.ammoProfile"),
                "**El componente manda sobre el encantamiento**, y seria al reves: un arma que "
                        + "declarara su propia municion y tuviera el encantamiento gastaria la suya "
                        + "y no la que el jugador eligio");

        String cloud = readFile("src/main/java/com/cortes/combat/entity/MobCloud.java");
        assertTrue(cloud.contains("CombatEnchantRules.MOSCAS_DAMAGE"),
                "**La nube no lastima**: es un enjambre de moscas que no le hace nada a nadie");
        assertTrue(cloud.contains("MoscaParticles.swarm("),
                "**Y no suelta moscas**, asi que es una caja invisible y silenciosa");
        assertTrue(cloud.contains("if (age % DAMAGE_TICKS != 0)"),
                "**Y lastima cada tick** en vez de cada diez, que es un disparo que mata solo");

        // El renderer, que no dibuja nada y aun asi tiene que existir. EntityRenderDispatcher
        // dereferencia el renderer en shouldRender y en render SIN GUARDA de nulo, asi que un
        // EntityType sin renderer registrado no es una entidad invisible: es un NPE en el render
        // thread la primera vez que alguien mira hacia ahi.
        //
        // Se afirma por separado y no el emparejamiento exacto: casar la indentacion y el fin de
        // linea hace un guard que falla por una razon que no tiene nada que ver con lo que afirma
        // -- que en este repo es CRLF en unos archivos y LF en otros.
        String client = readFile("src/main/java/com/cortes/combat/client/CortesCombatClient.java");
        assertTrue(client.contains("ModEntities.MOB_CLOUD.get(),"),
                "**La nube no esta registrada como entidad**, asi que el disparo no tiene nada "
                        + "que soltar");
        assertTrue(client.contains("MobCloudRenderer::new);"),
                "**Nadie le registra un renderer a la nube**, y un EntityType sin renderer es un "
                        + "NPE en el cliente, no una entidad invisible");
    }

    @Test
    @DisplayName("Filo Fugaz se fue con Adrenalina")    void fleetingEdgeIsGone() {
        // **El test viejo vivio aca y su asercion era la mitad del encantamiento que ya no existe.**
        // Afirmaba `CombatEnchantments.FLEETING_EDGE` y el gate de "ya lo tengo": con el encantamiento
        // renombrado y el efecto mudado al daño recibido, las dos lineas quedaron verdes por
        // casualidad o rojas por el motivo equivocado. Un test que sobrevive a su sujeto es peor que
        // ninguno, porque ocupa el lugar del que deberia estar.
        assertFalse(read().contains("CombatEnchantments.FLEETING_EDGE"),
                "el encantamiento se llama Adrenalina y vive en su propio handler");
    }

    /**
     * **Ritmo de Hierro decide la ventana con la que la cadena sigue.**
     *
     * <p>El estado del martillo puede llevar la ventana perfecta y la cadena seguir midiendo con
     * cuatro segundos si nadie lee el nivel del arma: la forma de siempre. La asercion es que el
     * nivel entra en {@code onHit}, y que el campo se setea ahi -- donde el golpe aterriza -- y no
     * en cada tick.</p>
     */
    @Test
    @DisplayName("Ritmo de Hierro decide la ventana de la cadena")
    void rhythmOfIronSetsTheWindow() {
        String engine = read();

        assertTrue(engine.contains("CombatEnchantments.RHYTHM_OF_IRON"),
                "**la tabla de ventanas necesita quien la lea.** Sin esto el encantamiento esta "
                        + "escrito, probado y el martillo sigue encadenando cuatro segundos");
        assertTrue(engine.contains(".onHit(window)"),
                "**y el numero entra en el estado.** La ventana viaja con la cadena y no se relee "
                        + "del item en cada tick: ver StreakState");
    }

    /**
     * **Contragolpe: la ventana se arma en el bloqueo que costó aguante, y se cobra en un golpe.**
     *
     * <p>Dos archivos, dos aserciones, y las dos importan por separado. Armar la ventana en el lugar
     * equivocado de {@code GuardHandler} la rearmaría con el segundo corte de un mismo swing -- que
     * bloquea pero ya estaba pagado -- y el premio dejaría de ser "por el golpe que pagaste". Y del
     * otro lado, la ventana puede abrirse perfecto y el golpe siguiente no leerla, que es la fórmula
     * sin llamador de siempre.</p>
     */
    @Test
    @DisplayName("Contragolpe se arma en el bloqueo que cuesta aguante y se cobra en el golpe")
    void riposteArmsAndIsCollected() {
        String guard = readGuard();
        String engine = read();

        assertTrue(guard.contains("ContragolpeWindow.armedAt(now)"),
                "**la ventana se arma en `GuardHandler`**");
        // **Y despues del `state.block(..)`, que es la posicion y no el texto.** Armar la ventana
        // antes de esa linea la rearmaria con el segundo corte de un mismo swing -- la rama de
        // arriba bloquea igual pero ya pago su punto -- y el premio dejaria de ser "por el golpe
        // que pagaste". La primera version de esta asercion solo miraba el texto y no mordia:
        // verificada moviendo el armado a la rama de arriba.
        int paid = guard.indexOf("state.block(");
        int armed = guard.indexOf("ContragolpeWindow.armedAt(now)");
        assertTrue(paid > 0 && armed > paid,
                "**y despues del `state.block(..)` que gasta el punto**, no antes: la rama del "
                        + "corte que ya pago bloquea pero no rearma nada");
        assertTrue(engine.contains("CombatEnchantments.RIPOSTE"),
                "y se cobra: el nivel del encantamiento tiene que leerse en el golpe");
        assertTrue(engine.contains(".spent()"),
                "**y se gasta.** 'Tu siguiente ataque' es uno: sin esto un bloqueo daria cuarenta "
                        + "ticks de penetracion de armadura");
        assertTrue(engine.contains("addTransientModifier("),
                "y la armadura se baja durante el golpe y se devuelve: ver ripostePierce para por "
                        + "que no se suma dano en vez de esto");
    }

    /**
     * **Pólvora Seca tiene llamador, y el número sale de las reglas puras.**
     *
     * <p>El mismo agujero que los otros cinco: la tabla puede estar perfecta y el arcabuz recargar
     * en cincuenta ticks. Y una aserción negativa que vale: <b>el helper de Quick Charge de vanilla
     * no puede volver</b>. Se probó primero, el playtest mostró que daba la mitad de lo que el
     * diseño pedía, y una reutilización que da la mitad del número no es una reutilización.</p>
     */
    @Test
    @DisplayName("Pólvora Seca llega a la recarga, y Quick Charge no vuelve")
    void dryPowderReachesTheReload() {
        String arcabuz = readArcabuz();

        assertTrue(arcabuz.contains("CombatEnchantments.DRY_POWDER"),
                "**la tabla de recargas necesita quien la lea.** Sin esto el encantamiento esta "
                        + "escrito, probado y el arcabuz tarda lo mismo");
        assertTrue(arcabuz.contains("CombatEnchantRules.reloadTicks("),
                "y el numero sale de las reglas puras, no de una cuenta escrita al lado");
        assertFalse(arcabuz.contains("modifyCrossbowChargingTime"),
                "**y Quick Charge no vuelve.** El efecto de vanilla es un numero absoluto: sobre la "
                        + "base del arcabuz daba la mitad de lo que el diseno pedia, y el playtest "
                        + "lo midio (de 50 sin encantamiento, de 40 con Quick Charge II)");
    }

    /**
     * NeoForge's attack hook is fired again, for the target the player aimed at.
     *
     * <p>{@code Player#attack} opens with {@code CommonHooks.onPlayerAttackTarget}, and a replaced
     * flow that skips it is invisible until a protection mod that cancels an attack stops working.
     * Fired for the aimed target only, the way vanilla fires it once per attack rather than once
     * per sweep victim.</p>
     */
    @Test
    @DisplayName("NeoForge's attack hook fires again")
    void theCompatHookIsBack() {
        assertTrue(read().contains("CommonHooks.onPlayerAttackTarget("),
                "**`Player#attack` fires this and the replaced flow dropped it.** It is how another "
                        + "mod cancels or reacts to a player hitting something, and losing it is a "
                        + "compatibility break that no number can show");
    }

    private static String read() {
        Path file = Path.of("src", "main", "java", "com", "cortes", "combat", "attack",
                "CombatEngine.java");
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + file + ": " + e.getMessage(), e);
        }
    }

    /**
     * The other file these hooks live in.
     *
     * <p>The parry and the lunge are specials, not swings, so their cooldowns are paid in
     * {@code SpecialHandler} and not in the engine. Reading it from here rather than from a second
     * test class keeps the "a hook with no caller" question in one place.</p>
     */
    private static String readSpecial() {
        Path file = Path.of("src", "main", "java", "com", "cortes", "combat", "special",
                "SpecialHandler.java");
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + file + ": " + e.getMessage(), e);
        }
    }

    /**
     * **Los tres bonus por objetivo llegan al motor, y salen de las reglas puras.**
     *
     * <p>El mismo agujero que los otros: la tabla puede estar perfecta y el hacha pegar igual. Y una
     * aserción sobre la forma: los tres se cobran por <b>un solo canal</b>, porque tres call sites
     * son tres lugares donde olvidarse de llamarlo.</p>
     */
    @Test
    @DisplayName("Punta Fina, Filo Astillado y Apuñalar llegan al motor, por un solo canal")
    void thePerTargetBonusesReachTheEngine() {
        String engine = read();

        for (String fn : new String[] {"puntaFina", "filoAstillado", "apunalar"}) {
            assertTrue(engine.contains("CombatEnchantRules." + fn + "("),
                    "**" + fn + " no tiene llamador.** El encantamiento existe, tiene archivo, tiene "
                            + "idioma, se puede poner en el arma -- y no hace nada. Es el modo de "
                            + "falla que este proyecto ya pago con el track BREAK del sable: el build "
                            + "queda verde");
        }
        assertTrue(engine.contains("enchanted + perTargetBonus("),
                "**el canal tiene que estar CONECTADO, no solo existir.** La funcion puede estar "
                        + "escrita, tener sus tres llamadas adentro y estar perfecta, y el bonus no "
                        + "llegar nunca al numero porque nadie la suma -- que es exactamente el bug "
                        + "del track BREAK del sable, un nivel mas arriba. Saboteado quitando la "
                        + "suma: el guard tiene que morir");
        assertTrue(engine.contains("fromBehind("),
                "y Apunalar necesita la geometria de las reglas puras, no una cuenta escrita al lado "
                        + "en el motor");
    }

    /**
     * **Los cuatro de la segunda tanda tienen llamador, y en el lugar correcto.**
     *
     * <p>Cada uno tiene un hook distinto y por una razón distinta: Sanguijuela y Culata se cobran en
     * el motor, Estocada en el lunge (que vive en otro paquete) y Aventurero <b>al morir el mob</b>,
     * no al golpearlo. Un guard que pidiera los cuatro en el mismo archivo estaría afirmando que los
     * cuatro se cobran en el mismo momento, que es justo lo que no pasa.</p>
     */
    @Test
    @DisplayName("Sanguijuela, Culata, Estocada y Aventurero tienen llamador")
    void theSecondBatchReachesTheirHooks() {
        String engine = read();
        // **Cada guard afirma el USO y no la llamada, y la diferencia costo una ronda.**
        //
        // La primera version afirmaba `CombatEnchantRules.sanguijuela(` y el sabotaje que borraba
        // `player.heal(...)` NO la hacia morir: la regla se seguia llamando, el resultado se seguia
        // asignando a `leech`, y `leech` no se usaba para nada. Es exactamente el bug del track
        // BREAK del sable, y el guard tiene que afirmar el numero que llega, no la funcion que se
        // invoca.
        assertTrue(engine.contains("CombatEnchantRules.sanguijuela(")
                        && engine.contains("player.heal("),
                "**Sanguijuela no cura.** La regla puede estar llamada y el resultado sin usar -- "
                        + "el encantamiento existe, se puede poner en la daga y no devuelve vida. "
                        + "Saboteado borrando el `heal`: el guard tiene que morir");
        assertTrue(engine.contains("CombatEnchantRules.culata(")
                        && engine.contains("push += culata"),
                "**Culata no empuja mas.** Mismo caso: la regla llamada y el resultado no sumado al "
                        + "empuje. Saboteado borrando la suma");
        assertTrue(engine.contains("CombatEnchantRules.estocada("),
                "**Estocada no tiene llamador**: el lunge cobra la armadura entera");

        // Y el uso de Estocada esta en OTRO archivo, que es la mitad que un guard de un solo archivo
        // no ve: la regla puede estar bien y el lunge no pedirla nunca.
        String lunge = readFile("src/main/java/com/cortes/combat/special/Lunge.java");
        assertTrue(lunge.contains("lungePierce("),
                "el lunge no pide la perforacion: Estocada seria un encantamiento del motor de melé "
                        + "que nunca toca el unico ataque del que es dueño");
        assertTrue(lunge.contains("removeModifier("),
                "**y la armadura se devuelve.** Una que queda baja para siempre es el bug que este "
                        + "encantamiento no puede dejar atras: el `finally` se quedo sin cuerpo");

        String adventurer = readFile("src/main/java/com/cortes/combat/enchant/AdventurerExperience.java");
        assertTrue(adventurer.contains("LivingExperienceDropEvent"),
                "**Aventurero se cobra al MORIR y no al golpear.** El hook del golpe cobraria el "
                        + "bonus por un mob que todavia no murio, y que puede morir despues por otra "
                        + "cosa -- o no morir");
        assertTrue(adventurer.contains("CombatEnchantRules.aventurero(")
                        && adventurer.contains("setDroppedExperience("),
                "**y el resultado se escribe en el evento.** Leer el nivel y no tocar la experiencia "
                        + "es un encantamiento que no hace nada, con el build verde");
    }

    /** Un archivo del repo, leido como texto: para los hooks que viven fuera del motor. */
    private static String readFile(String relative) {
        Path file = Path.of(relative);
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + file + ": " + e.getMessage(), e);
        }
    }

    /**
     * **Los dos de la guardia tienen llamador, y en el archivo de la guardia.**
     *
     * <p>Es la tanda que necesitó estado nuevo (dos attachments), así que el modo de falla tiene una
     * forma más: el attachment registrado, el estado escrito y <b>nadie preguntando</b>. Cada guard
     * afirma las dos mitades -- quien lo arma y quien lo cobra.</p>
     */
    @Test
    @DisplayName("Guardia Firme y Segundo Aliento llegan a la guardia")
    void theGuardEnchantmentsReachTheGuard() {
        String guard = readGuard();

        assertTrue(guard.contains("CombatEnchantRules.guardiaFirme(")
                        && guard.contains("CombatState.guardFree(")
                        && guard.contains("CombatState.setGuardFree("),
                "**Guardia Firme no ahorra nada.** La regla puede estar llamada y el reloj sin "
                        + "tocar, que es un bloqueo que cuesta siempre con el build verde. La puerta "
                        + "es CombatState: el reloj se lee con guardFree y se gasta con setGuardFree");
        assertTrue(guard.contains("SecondWindWindow.armedAt("),
                "**Segundo Aliento no arma la ventana**: la guardia se rompe, el jugador paga el "
                        + "stun y no hay premio");

        String engine = read();
        assertTrue(engine.contains("CombatState.secondWindow("),
                "**y la ventana se cobra en el motor.** Armarla y no leerla es un premio que existe "
                        + "en el estado y no en el juego. La puerta es CombatState: el attachment "
                        + "vive ahi y el motor pregunta por la facade");
        assertTrue(engine.contains("wind.spent()") && engine.contains("CombatState.setSecondWindow("),
                "**y se gasta.** \"Tu siguiente golpe\" es uno: sin el `spent()` serian tres segundos "
                        + "de premio en vez de un golpe, y sin escribirlo de vuelta el gasto no existe");
    }

    /**
     * **Los cuatro de los efectos tienen llamador, y cada uno en el momento correcto.**
     *
     * <p>Tres se cobran en el golpe y el cuarto en el parry, así que un guard que los pidiera a todos
     * en el mismo archivo estaría afirmando que los cuatro se cobran en el mismo momento.</p>
     */
    @Test
    @DisplayName("Cicatrizador, Destripamiento, Quebranto y Desvio Vampirico llegan a sus hooks")
    void theEffectEnchantmentsReachTheirHooks() {
        String engine = read();

        assertTrue(engine.contains("applyOnHitEffects(player, armoured"),
                "**los tres que dejan efecto no se cobran.** La funcion puede estar escrita, con sus "
                        + "tres efectos adentro y perfecta, y sin llamador: el build queda verde y el "
                        + "golpe no deja nada");
        assertTrue(engine.contains("ModEffects.antiHealing()") && engine.contains("ModEffects.bleeding()")
                        && engine.contains("ModEffects.quebranto()"),
                "los tres efectos tienen que APLICARSE, no solo leerse");
        assertTrue(engine.contains("CombatEnchantRules.amplifierFor("),
                "y el nivel del efecto sale de la conversion pura: cuatro copias de `nivel - 1` son "
                        + "cuatro lugares donde olvidarse de restar, y el sintoma es un efecto un "
                        + "nivel mas fuerte sin ningun error");
        assertTrue(engine.contains("DamageResolver.FULL_CHARGE"),
                "**Destripamiento solo con carga completa**: sin el gate, un tap de alabarda deja "
                        + "sangrado y el encantamiento deja de ser un premio por cargar");

        String parry = readFile("src/main/java/com/cortes/combat/special/ParryHandler.java");
        assertTrue(parry.contains("ModEffects.vampiric()") && parry.contains("ModEffects.drained()"),
                "**Desvio Vampirico son DOS efectos** y el parry tiene que aplicar los dos: sumarle "
                        + "al que desvia sin restarle al desviado no es un robo, es un regalo");
        assertTrue(parry.contains("CombatEnchantRules.canDrain("),
                "**y el piso se pregunta antes de aplicar nada.** Sin eso el robo le baja el techo a "
                        + "cero a un cuerpo tocado, que es la ejecucion gratuita que el proyecto veta");
        assertTrue(parry.contains("CombatEnchantRules.vampiricoStolen("),
                "**y el acumulado se lee del efecto.** Sin eso cada parry daria el robo del nivel otra "
                        + "vez, el tope no existiria y el encantamiento volveria a ser un premio de "
                        + "una sola vez");

        String antiHealing = readFile("src/main/java/com/cortes/combat/effect/AntiHealingHandler.java");
        assertTrue(antiHealing.contains("LivingHealEvent") && antiHealing.contains("setAmount("),
                "**Anti-curacion se cobra en la curacion y no en el golpe**: el handler tiene que "
                        + "escuchar el heal y escribir el numero, o el efecto no reduce nada");
    }

    /**
     * **Mecha Corta: el total de la mecha tiene que llegar a los dos lados.**
     *
     * <p>El servidor lo usa para contar hasta el disparo y el cliente para dibujar la animacion, y
     * los dos tienen que decir el mismo numero. Un guard de un solo archivo no ve la mitad que
     * importa: el bug es un servidor que dispara a los veinte con una animacion terminada a los diez.
     * </p>
     */
    @Test
    @DisplayName("Mecha Corta: el total de la mecha llega al servidor y al cliente")
    void mechaCortaReachesBothSides() {
        String arcabuz = readArcabuz();
        assertTrue(arcabuz.contains("CombatEnchantRules.fuseTicks("),
                "**el servidor no acorta la mecha**: el encantamiento existe, se puede poner en el "
                        + "arcabuz y el disparo sale igual a los veinte ticks");
        assertTrue(arcabuz.contains("state.light(now, fuse)"),
                "y el estado tiene que arrancar con ESE largo: `light()` sin argumentos cuenta la "
                        + "mecha de fabrica");

        String fuse = readFile("src/main/java/com/cortes/combat/client/ArcabuzFuseState.java");
        assertTrue(fuse.contains("progress(int totalTicks)"),
                "**y el cliente divide por el total, no por la constante.** El progreso era "
                        + "`fuseTicks / FUSE_TICKS`: con una mecha de diez ticks llegaba a 0,5 cuando "
                        + "la mecha se apagaba, y el frame se quedaba a mitad de camino");
        assertTrue(fuse.contains("(float) fuseTicks / (float) totalTicks"),
                "**y el cociente es por el total.** Era `fuseTicks / ArcabuzState.FUSE_TICKS`: con "
                        + "una mecha de diez ticks el progreso llegaba a 0,5 cuando la mecha se "
                        + "apagaba, y el frame se quedaba a mitad de camino. La constante puede "
                        + "seguir nombrada en el javadoc que cuenta el bug -- lo que no puede es "
                        + "volver al cociente");

        String model = readFile("src/main/java/com/cortes/combat/client/ArcabuzModelProperties.java");
        assertTrue(model.contains("CombatEnchantRules.fuseTicks("),
                "y el predicado -- que es quien tiene el stack -- es el que calcula el total");
    }

    /**
     * **Barredor: el nivel tiene que llegar al servidor Y al indicador.**
     *
     * <p>Es el unico encantamiento del proyecto con un <b>veto</b> escrito para el, el C4 del
     * documento de diseno: "alcance extendido: obliga al HUD del cono a releer el nivel; si se
     * olvida, <em>el indicador miente</em>". Asi que este guard no pregunta si el encantamiento
     * existe: pregunta por las dos mitades, y por <b>todos</b> los sitios que dibujan el cono.</p>
     */
    @Test
    @DisplayName("Barredor: el servidor agranda el barrido y los cuatro sitios de display lo dicen")
    void barredorReachesBothSides() {
        String special = readFile("src/main/java/com/cortes/combat/special/SpecialHandler.java");
        assertTrue(special.contains("HalberdSweep.reachFor(stats.reach(), barredor)"),
                "**el servidor no agranda el barrido**: el encantamiento existe, se puede poner en "
                        + "la alabarda, y el area no cambia");
        assertTrue(special.contains("CombatEnchantments.BARREDOR"),
                "y el nivel sale del stack que tiro el barrido");

        String air = readFile("src/main/java/com/cortes/combat/attack/MaceAir.java");
        assertTrue(air.contains("HalberdSweep.reachFor(shown, sweepLevel)"),
                "**y el indicador no lo dibuja.** Un alcance que crece y un crosshair que no lo dice "
                        + "es la mentira que el veto C4 existe para prevenir, y se reporta como 'no "
                        + "noto el encantamiento' y no como un bug");

        // **Y los cuatro, no uno.** El veto habla del HUD, pero el cono lo dibujan cuatro sitios y
        // el que se olvide es el que miente: el crosshair, los corchetes, `/cc cone` y `/cc hud`.
        List<String> blind = new ArrayList<>();
        for (String file : List.of("CombatAim.java", "CooldownBracketLayer.java",
                "ConeDebugRenderer.java", "HudGeometryDump.java")) {
            if (!readFile("src/main/java/com/cortes/combat/client/" + file).contains("barredorLevel(")) {
                blind.add(file);
            }
        }
        assertTrue(blind.isEmpty(),
                "**estos sitios siguen dibujando el cono viejo: " + blind + ".** El alcance del "
                        + "barrido crece con Barredor y el marcador tiene que crecer con el, o el "
                        + "jugador se cree fuera de rango de un barrido que lo alcanza");
    }

    /**
     * **Los cuatro de la tanda de los baratos, y cada guard afirma el USO.**
     *
     * <p>Es la leccion que este archivo ya pago dos veces: una regla que existe y que nadie llama
     * deja el build verde, y el sabotaje que borra la llamada no muere. Por eso cada asercion mira
     * <b>donde el numero se cobra</b> -- el descuento, el multiplicador, la devolucion, el efecto --
     * y no la existencia de la funcion.</p>
     */
    @Test
    @DisplayName("Astilladora, Mella, Segunda Pasada y Santa Muerte llegan a sus hooks")
    void theFourthBatchReachesTheirHooks() {
        String guard = readFile("src/main/java/com/cortes/combat/guard/GuardHandler.java");
        assertTrue(guard.contains("CombatEnchantRules.astilladoraCost("),
                "**Astilladora no cobra**: el encantamiento existe, se puede poner en el hacha, y un "
                        + "golpe bloqueado sigue costando uno");
        assertTrue(guard.contains("state.block(guard.profile().regenTicks(), cost)"),
                "y el coste tiene que llegar al descuento: una variable que nadie usa es el canal "
                        + "muerto de siempre, con el build verde");

        String armor = readFile("src/main/java/com/cortes/combat/attack/ArmorWearHandler.java");
        assertTrue(armor.contains("CombatEnchantRules.mellaWear("),
                "**Mella no desgasta**: el desgaste de la armadura no cambia con el nivel");
        assertTrue(armor.contains("* WEAR_MULTIPLIER * mella"),
                "y el multiplicador tiene que multiplicar, no quedar al lado");

        String special = readFile("src/main/java/com/cortes/combat/special/SpecialHandler.java");
        assertTrue(special.contains("CombatEnchantRules.segundaPasada("),
                "**Segunda Pasada no devuelve nada**: el barrido contra un grupo que aguanta sigue "
                        + "costando cuatro swings");
        assertTrue(special.contains("cooldown.refund("),
                "y la devolucion tiene que llegar al cooldown, no quedarse en un float");
        assertTrue(special.contains("!target.isAlive()"),
                "**y la condicion es 'no mato a NADIE', que se pregunta por la vida y no por el valor "
                        + "de retorno de hurt**: `hurt` contesta 'el dano paso', y un golpe que entra "
                        + "y no mata contesta que si");

        String santa = readFile("src/main/java/com/cortes/combat/enchant/SantaMuerte.java");
        assertTrue(santa.contains("MobEffects.WITHER"),
                "**Santa Muerte no envenena**: la nube sale y no aplica nada");
        assertTrue(santa.contains("other == killer"),
                "y el asesino queda afuera: un encantamiento que castiga al que lo usa es el veto C2 "
                        + "del documento, 'el bloqueo devuelve dano'");
    }

    /**
     * **Inercia y Ensarte: los dos que tocan el empuje y la linea del golpe.**
     *
     * <p>Inercia es el unico encantamiento del proyecto que manda un empuje al aire, contra un javadoc
     * que {@code Knockback.apply} tiene escrito a proposito; Ensarte es el unico que cobra dos veces
     * con un golpe. Los dos son la clase de cosa que se escribe mal de una forma que compila.</p>
     */
    @Test
    @DisplayName("Inercia sube el empuje y Ensarte atraviesa, y ninguno de los dos es el otro")
    void theFifthBatchReachesTheirHooks() {
        String engine = readFile("src/main/java/com/cortes/combat/attack/CombatEngine.java");
        assertTrue(engine.contains("CombatEnchantRules.inerciaLift("),
                "**Inercia no empuja**: el encantamiento existe, se puede poner en el martillo, y el "
                        + "golpe sigue empujando hacia adelante");
        assertTrue(engine.contains("Knockback.applyLift("),
                "y el empuje tiene que ir al metodo que sube, no al que empuja hacia adelante");
        assertTrue(engine.contains("\n        ensarte(player, target, stats, result);"),
                "**Ensarte no atraviesa**: la estocada sigue cobrando a un solo cuerpo. Y la "
                        + "asercion pide la llamada AL RAS y no el nombre de la funcion, porque un "
                        + "guard que busca `ensarteShare(` queda verde con la llamada envuelta en un "
                        + "`if (false)` -- que es la forma exacta en que este archivo ya se equivoco");
        assertTrue(engine.contains("result.damage() * share"),
                "**y el segundo cuerpo tiene que pagar la FRACCION y no el dano entero**, que es lo "
                        + "que lo separa de golpear dos veces al mismo");
        assertTrue(engine.contains("toOther.dot(unit) <= 0.0"),
                "**y tiene que estar DEL OTRO LADO.** Sin esa mitad, Ensarte golpearia a un cuerpo al "
                        + "costado del primero, que es un golpe en area con otro nombre");

        String knock = readFile("src/main/java/com/cortes/combat/attack/Knockback.java");
        assertTrue(knock.contains("motion.x, motion.y + vertical, motion.z"),
                "**y el empuje de Inercia no puede tocar la horizontal**: subir y ademas desplazar es "
                        + "un empuje mas fuerte, y lo que el encantamiento vende es el mismo empuje "
                        + "apuntado a otro lado");
    }

    /** Y el arcabuz, donde vive la recarga que Pólvora Seca acorta. */
    private static String readArcabuz() {
        Path file = Path.of("src", "main", "java", "com", "cortes", "combat", "special",
                "Arcabuz.java");
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + file + ": " + e.getMessage(), e);
        }
    }

    /** Y el de la guardia, que es donde Contragolpe arma su ventana. */
    private static String readGuard() {        Path file = Path.of("src", "main", "java", "com", "cortes", "combat", "guard",
                "GuardHandler.java");
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + file + ": " + e.getMessage(), e);
        }
    }
}
