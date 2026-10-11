package com.cortes.combat.attack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

        assertTrue(engine.contains("EnchantmentHelper.modifyDamage("),
                "**Sharpness, Smite, Bane of Arthropods, Impaling and Density run here.** They are "
                        + "the body of `ServerPlayer#getEnchantedDamage`, which only `Player#attack` "
                        + "calls -- and that flow is replaced. Without this line an enchanted sword "
                        + "does exactly the damage of an unenchanted one, silently");

        assertTrue(engine.contains("EnchantmentHelper.modifyKnockback("),
                "**and Knockback.** Vanilla's `getKnockback` is the other half of the same lost "
                        + "call; without it a Knockback II sword shoves like a plain one");

        assertTrue(engine.contains("EnchantmentHelper.doPostAttackEffects("),
                "**and the post-attack effects.** This is where Aspect of Fire and Wind Burst "
                        + "live -- an enchanted sword that cannot set anything on fire is a bug a "
                        + "player notices, a build a test does not");
    }

    @Test
    @DisplayName("the post-attack effects run once per body that took the hit")
    void oncePerLandedBody() {
        String engine = read();
        int guard = engine.indexOf("if (anyLanded)");
        int effects = engine.indexOf("EnchantmentHelper.doPostAttackEffects(");

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
     * **Filo Fugaz se aplica, y no se renueva si ya está.**
     *
     * <p>El gate es la mitad del encantamiento: la daga pega cada seis ticks y la Velocidad dura
     * cuarenta, así que sin él cada golpe la renovaría y el buff pasaría a ser permanente mientras
     * haya algo enfrente. Es la diferencia entre un premio y un estado, y es una línea.</p>
     */
    @Test
    @DisplayName("Filo Fugaz tiene llamador, y el gate de 'ya lo tengo'")
    void fleetingEdgeHasItsGate() {
        String engine = read();

        assertTrue(engine.contains("CombatEnchantments.FLEETING_EDGE"),
                "**la tabla de duraciones necesita quien la lea.** Sin esto el encantamiento esta "
                        + "escrito, probado y no hace nada");
        assertTrue(engine.contains("hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED)"),
                "**y el gate de 'ya lo tengo'.** Sin el, la daga a seis ticks renovaria la "
                        + "Velocidad en cada golpe y el premio se volveria un estado");
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

        assertTrue(guard.contains("CombatEnchantRules.guardiaFirme(") && guard.contains("GUARD_FREE"),
                "**Guardia Firme no ahorra nada.** La regla puede estar llamada y el reloj sin "
                        + "tocar, que es un bloqueo que cuesta siempre con el build verde");
        assertTrue(guard.contains("SecondWindWindow.armedAt("),
                "**Segundo Aliento no arma la ventana**: la guardia se rompe, el jugador paga el "
                        + "stun y no hay premio");

        String engine = read();
        assertTrue(engine.contains("SECOND_WINDOW"),
                "**y la ventana se cobra en el motor.** Armarla y no leerla es un premio que existe "
                        + "en el estado y no en el juego");
        assertTrue(engine.contains("wind.spent()"),
                "**y se gasta.** \"Tu siguiente golpe\" es uno: sin el `spent()` serian tres segundos "
                        + "de premio en vez de un golpe");
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
        assertTrue(engine.contains("ModEffects.ANTI_HEALING") && engine.contains("ModEffects.BLEEDING")
                        && engine.contains("ModEffects.QUEBRANTO"),
                "los tres efectos tienen que APLICARSE, no solo leerse");
        assertTrue(engine.contains("CombatEnchantRules.amplifierFor("),
                "y el nivel del efecto sale de la conversion pura: cuatro copias de `nivel - 1` son "
                        + "cuatro lugares donde olvidarse de restar, y el sintoma es un efecto un "
                        + "nivel mas fuerte sin ningun error");
        assertTrue(engine.contains("DamageResolver.FULL_CHARGE"),
                "**Destripamiento solo con carga completa**: sin el gate, un tap de alabarda deja "
                        + "sangrado y el encantamiento deja de ser un premio por cargar");

        String parry = readFile("src/main/java/com/cortes/combat/special/ParryHandler.java");
        assertTrue(parry.contains("ModEffects.VAMPIRIC") && parry.contains("ModEffects.DRAINED"),
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
        assertTrue(arcabuz.contains("state.light(fuse)"),
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
