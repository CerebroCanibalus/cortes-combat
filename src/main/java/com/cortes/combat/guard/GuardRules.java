package com.cortes.combat.guard;

/**
 * The geometry of a block, in plain {@code double} and with no Minecraft type anywhere in a
 * signature.
 *
 * <p>Same reason as {@code ConeMath}: a rule that needs the game running cannot be tested in a
 * bare JVM, and a rule that cannot be tested is a rule that will be wrong. Everything here can
 * be checked with a unit test and nothing here can drift from what the game does, because the
 * game only calls it.</p>
 */
public final class GuardRules {

    /**
     * Total width of the frontal block, in degrees. The design says "frontal arc only" without
     * a number, so this is the one to tune: 120 total means 60 degrees either side of where the
     * player is looking. Wide enough to feel like a shield in front of you, narrow enough that
     * being hit from behind is a real mistake.
     */
    public static final double GUARD_ARC_DEGREES = 120.0;

    private GuardRules() {
    }

    /**
     * Whether a source at {@code (sx, sy, sz)} is inside the block arc of a player standing at
     * {@code (px, py, pz)} and looking along {@code (lx, ly, lz)}.
     *
     * <p>Measures the full 3D angle rather than a horizontal yaw, so a hit dropped on you from
     * in front and above counts as frontal and one from behind does not. Same choice as the
     * cone: measure what the player would describe as "in front of me".</p>
     *
     * @param arcDegrees total width of the arc, so 120 means 60 either side
     */
    public static boolean isFromFront(double px, double py, double pz,
                                      double lx, double ly, double lz,
                                      double sx, double sy, double sz,
                                      double arcDegrees) {
        // From the player towards the attacker. Pointing this the other way is a sign error
        // that inverts the whole rule: the player would block from behind and eat from the
        // front, and no amount of reading the code makes that obvious.
        double dx = sx - px;
        double dy = sy - py;
        double dz = sz - pz;
        double lengthSquared = dx * dx + dy * dy + dz * dz;
        if (lengthSquared < 1.0E-12) {
            // The source is on top of us. Standing in someone's fist is the least "behind" a
            // hit can be, and refusing to block it would be a hole players find instantly.
            return true;
        }
        double inverse = 1.0 / Math.sqrt(lengthSquared);
        double dot = (dx * inverse) * lx + (dy * inverse) * ly + (dz * inverse) * lz;
        // A dot drifting past 1 makes acos return NaN and the whole comparison goes false.
        if (dot > 1.0) {
            dot = 1.0;
        } else if (dot < -1.0) {
            dot = -1.0;
        }
        double angle = Math.toDegrees(Math.acos(dot));
        return angle <= arcDegrees / 2.0;
    }

    /**
     * Whether a guard may go up, given that the same button is what starts eating.
     *
     * <p>The playtest found that guarding and eating can happen at once, liked it, and asked for
     * it to be a rule instead of an accident. The rule it asked for is an <em>order</em>: you
     * cover first, you eat after, never the other way round.</p>
     *
     * <p>Both halves of that are already true here, and neither needed a special case, which is
     * the part worth saying out loud:</p>
     *
     * <ul>
     *   <li><b>You cover first.</b> A single press of the button resolves the guard immediately
     *       and the food takes 32 ticks to go down. The guard is up before the first bite is even
     *       finished, by construction, not by a rule.</li>
     *   <li><b>You eat second.</b> Pressing again while already chewing does not raise the guard.
     *       That is this method: {@code eating} refuses the raise.</li>
     * </ul>
     *
     * <p>It works on one button because the guard is read from <em>either</em> hand and eating
     * only ever comes from the main hand. A shield in the off hand and bread in the main hand
     * share the same press without sharing the same state, so the two never had to be arbitrated
     * in the first place.</p>
     *
     * <p>Note what this does <em>not</em> say: it does not stop the player eating while the guard
     * is up. Refusing the meal would be the mirror image and it is the wrong one, because the
     * player who is already covering has already made the decision this rule is about.</p>
     */
    public static boolean mayRaiseGuard(boolean canGuard, boolean eating) {
        return canGuard && !eating;
    }

    /**
     * Whether the refill is paused, from the two facts that pause it.
     *
     * <p>This is a one-line function that exists because the line it replaces was wrong in a way
     * that survived review, and it was wrong in the caller, where nothing could test it. The
     * caller had {@code isActive() && !staggered} feeding a parameter that means <em>stop
     * regenerating</em>, so both branches were backwards: a raised guard refilled and a broken
     * one did not. The playtest saw three full orbs on a guard that was down and no amount of
     * reading the state class would have shown it, because the state class was right.</p>
     *
     * <p>Written as the sum of two negations instead of a boolean expression so that each half
     * can be checked on its own, and so the name has to mean what it says at the call site. The
     * two reasons are not the same thing: one is a button, the other is a countdown the game
     * keeps.</p>
     */
    public static boolean regenPaused(boolean raised, boolean staggered) {
        return raised || staggered;
    }

    /**
     * Whether the guard going down is allowed to stop whatever the player is using.
     *
     * <p>Only the blocking use counts, and narrowing it to that is the fix. The check used to be
     * "do the hands hold anything that can guard", which is true for a shield in the off hand
     * while you are halfway through a steak in the main hand. So the guard breaking cancelled the
     * dinner, which is the exact opposite of the interaction the playtest asked to keep: getting
     * hit while covered is <em>supposed</em> to leave you eating.</p>
     *
     * <p>Blocking use is also the only use a guard can ever produce. A shield in the hand starts
     * one in vanilla, because that is what a shield does. A sword guard starts none at all: the
     * sword has no {@code use} behaviour, our guard is an attachment and not an item use, so there
     * is nothing to cancel and nothing that was ever wrong here. Which is why the predicate is
     * about the use animation rather than about our own guard state -- it is the only thing here
     * that can honestly tell the two apart.</p>
     */
    public static boolean mayCancelItemUse(boolean usingItem, boolean usedItemBlocks) {
        return usingItem && usedItemBlocks;
    }

    /**
     * Whether vanilla is allowed to start using the item in the hand that was right-clicked.
     *
     * <p>This is the fix for "the shield goes up with no points left", and the shape of it is
     * worth spelling out because the obvious version is wrong.</p>
     *
     * <p>A shield raising itself on screen is <b>vanilla</b>, not us: right-clicking starts an
     * item use, the server runs it, the flag syncs, and the shield appears. Our guard refusing to
     * go up did nothing about that, so a player with an empty guard held a shield up and looked
     * covered while nothing was blocking. Stopping the item use on the client does not fix it
     * either, and the reason is uncomfortable: the guard's block decision hangs off
     * {@code LivingShieldBlockEvent}, which only fires while the vanilla use is running. Cancel
     * the use unconditionally and the guard stops blocking altogether.</p>
     *
     * <p>So the use is only refused when the guard <em>owns that hand</em> and <em>refuses to
     * go up</em>. When the guard is up, the use is what carries the block, and it runs exactly as
     * before. Verified on both sides: {@code RightClickItem} fires from
     * {@code ServerPlayerGameMode#useItem} and from {@code MultiPlayerGameMode#useItem}, so
     * refusing it client-side also means the packet is never sent.</p>
     *
     * @param handHoldsGuard  the right-clicked hand holds a shield or a sword
     * @param guardCanBeUsed  that guard has points and the player is not mid-bite
     */
    public static boolean mayStartItemUse(boolean handHoldsGuard, boolean guardCanBeUsed) {
        // Two inputs and both are load-bearing. Collapsing them to "has a guard" is the bug that
        // shipped: a shield in the off hand satisfies it, and so the shield went up on an empty
        // guard. Collapsing them to "is the guard ready" is the mirror image and it eats bread.
        return !handHoldsGuard || guardCanBeUsed;
    }

    /**
     * Cuanto de un golpe entrante se bloquea, <b>en unidades y no en fraccion</b>.
     *
     * <h2>El nombre del campo miente, y esto es la traduccion</h2>
     *
     * <p>{@code LivingShieldBlockEvent#setBlockedDamage} recibe <b>cuanto se bloquea</b>, y
     * {@code DamageContainer} lo <b>resta</b> del dano que entra ({@code newDamage -= blockedDamage}).
     * O sea que la convencion es la opuesta a la que sugiere leer "damage bloqueado" rapido:
     * {@code incoming} bloquea todo, {@code 0} no bloquea nada.</p>
     *
     * <p><b>Y aca vivio el bug que dejo la guardia sin bloquear nada durante rondas.</b> El codigo
     * escribia {@code incoming * (1 - fraccion)}, que con una fraccion de 1 da <b>cero</b>: un
     * golpe frontal no paraba absolutamente nada. Y el sintoma se leia como <b>dos</b> bugs --
     * "me hacen el dano" y "los efectos del ataque tambien pasan" -- cuando era uno: {@code hurt}
     * devolvia {@code true}, asi que el motor aplicaba los efectos del golpe que creia aterrizado.
     * Las explosiones funcionaban <b>de casualidad</b>, porque con 0,5 la cuenta invertida da el
     * medio correcto.</p>
     *
     * <p>Se clampea a {@code [0, incoming]} porque el evento lo clampea igual, y una fraccion fuera
     * de rango es una cuenta mal escrita en algun lado y no un golpe que cura.</p>
     *
     * @param incoming el dano que llega, que es {@code getOriginalBlockedDamage()}
     * @param fraction cuanto se bloquea, de 0 a 1
     * @return las unidades que se bloquean, listas para {@code setBlockedDamage}
     */
    public static float blockedAmount(float incoming, float fraction) {
        if (incoming <= 0.0F) {
            return 0.0F;
        }
        return incoming * Math.max(0.0F, Math.min(1.0F, fraction));
    }
}
