package com.cortes.combat.client;

import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.core.weapon.ReachProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Decides whether the crosshair should read as "you can hit something".
 *
 * <p>Two different questions share one answer, and they are not the same question:</p>
 * <ul>
 *   <li><b>Melee</b>: is there a target inside the cone? The client runs the identical
 *       {@link com.cortes.combat.reach.ConeMath} test the server runs, because the alternative
 *       is a crosshair promising a hit that the server then refuses.</li>
 *   <li><b>Ranged</b>: projectiles are outside the cone by design, so waiting for a target in it
 *       would leave the crosshair dead for the entire draw. Instead the state turns on the moment
 *       the shot is being prepared, which is when the player is actually aiming at something.</li>
 * </ul>
 *
 * <p>Splitting it that way is what stops a bow from growing a crosshair the instant an arrow
 * happens to line up with a zombie four blocks away, and stops a sword from going dead while
 * the player holds right click drawing a bow-shaped charge they cannot release.</p>
 *
 * <p>This is a hint and never a rule. It decides what the HUD draws; the server still validates
 * every swing with the same cone test, and the attack path resolves its own target from scratch
 * on every click rather than reading the one cached here.</p>
 *
 * <h2>Why the scan is throttled</h2>
 *
 * <p>The HUD wants a yes or a no. It does not want the entity. Paying a full
 * {@code getEntitiesOfClass} scan for that on every one of the twenty ticks a second was the
 * single most expensive thing this mod did on the client, and almost all of it produced a
 * boolean that had not changed since last tick.</p>
 *
 * <p>So the scan runs when the answer could have changed, and not otherwise. Two rules, and the
 * second is the one that makes the first safe:</p>
 * <ol>
 *   <li><b>Scan when the aim moved.</b> A rescan is forced by a change of player, item or reach
 *       profile, by the player physically moving, by the player turning, and by a mob walking
 *       into the cone. So a player sweeping the mouse across a room sees the crosshair react
 *       every tick, exactly as before.</li>
 *   <li><b>Re-check the cached target every tick regardless.</b> The scan is what <em>finds</em> a
 *       target; this is what keeps the found one honest. A target that died, got unloaded, changed
 *       level or stepped out of the cone stops the crosshair claiming a hit on it on that same
 *       tick, not three ticks later.</li>
 * </ol>
 *
 * <p>What rule 2 cannot cover is a <em>different</em> target arriving: while nothing moves, a mob
 * that walks into the cone can go unnoticed for at most {@link #STILL_SCAN_TICKS}. At three ticks
 * that is a hundred and fifty milliseconds on a hint, and the attack itself is unaffected because
 * it re-derives the target at the moment of the click.</p>
 */
public final class CombatAim {

    /**
     * Ticks between scans when the player has not moved, turned or changed weapon.
     *
     * <p>Three is a hundred and fifty milliseconds. Long enough to cut the scan by two thirds
     * while the player is standing still reading their own crosshair, short enough that the
     * polling is not what they notice when a mob wanders in.</p>
     */
    private static final int STILL_SCAN_TICKS = 3;

    /**
     * How far the player has to move, squared, before it counts as having moved. A centimetre.
     *
     * <p>The tolerance is the entire point of the throttle. At zero, every bit of mouse jitter
     * counts as turning, the scan runs on every tick, and the optimisation costs nothing while
     * appearing to work: the most likely way to break this is to set an epsilon to zero and see
     * no difference, which reads as "the scan is free" rather than "the threshold is wrong".</p>
     */
    private static final double MOVE_EPSILON_SQUARED = 1.0E-4;

    /**
     * The dot product below which the player counts as having turned. {@code 0.9999} is about
     * four tenths of a degree, comfortably under the smallest aim correction that changes what
     * the crosshair is about to say.
     */
    private static final double TURN_DOT_THRESHOLD = 0.9999;

    private static LivingEntity target;
    private static LocalPlayer tracked;
    private static Item trackedItem;
    private static ReachProfile trackedReach;
    private static Vec3 scannedFromEye;
    private static Vec3 scannedAlong;
    private static int tick;
    private static int scannedAtTick;

    private CombatAim() {
    }

    /** Called once per client tick. */
    public static void update() {
        tick++;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.level() == null || player.isSpectator()) {
            forget();
            ClientCombatState.setAiming(false);
            return;
        }

        ItemStack held = player.getMainHandItem();
        if (isRanged(held)) {
            // A bow and a crossbow are aimed with the crosshair, not with the cone, so there is
            // nothing to scan for and the melee cache is dropped rather than kept: swapping to a
            // bow and back should not resurrect whoever happened to be standing in front of the
            // sword. The trident is not here -- see isRanged.
            forget();
            ClientCombatState.setAiming(isDrawingRanged(player, held));
            return;
        }

        // The displayed cone with the air bonus applied, like the brackets it sits next to: the aim
        // marker's job is to say where the swing goes, and the maza's cone grows while it falls.
        ClientCombatState.setAiming(stillAiming(player, held,
                com.cortes.combat.attack.MaceAir.displayedFor(WeaponStats.of(held), player,
                        com.cortes.combat.enchant.CombatEnchantments.barredorLevel(player))));
    }

    /**
     * The melee answer, from the cache when the cache is allowed to speak.
     *
     * @return whether there is a target in the cone right now
     */
    private static boolean stillAiming(LocalPlayer player, ItemStack held, ReachProfile reach) {
        // Three reference compares, deliberately. What can change the cone is the player object,
        // which respawn and rejoin replace, the type of item in hand, and the reach profile
        // that item carries. Comparing the whole component patch instead would walk it every tick
        // to learn what three references already answer; comparing only the item would miss a
        // mod rewriting a component on that same item, and the profile is a record held by the
        // component, so an unchanged value is the same instance.
        // **By value and not by reference, which is the change the air bonus forced.** The reach
        // used to be a record held by the component, so an unchanged cone was the same instance and
        // `!=` answered "did the cone change" in one compare. A maza in the air gets a freshly
        // scaled profile every tick, so `!=` would be true forever: the throttle would never
        // throttle, and the scan would run twenty times a second on a weapon that just happens to
        // be falling. Three floats, compared only when the player and the item are unchanged.
        boolean contextChanged = player != tracked
                || held.getItem() != trackedItem
                || !reach.equals(trackedReach);
        if (contextChanged) {
            tracked = player;
            trackedItem = held.getItem();
            trackedReach = reach;
            target = null;
            scannedFromEye = null;
            scannedAlong = null;
            scannedAtTick = 0;
        }

        // Every tick, not every scan. See rule two in the class comment: this is what keeps the
        // throttle from ever showing a hit that is not there.
        boolean invalidated = target != null && !ConeTargeting.stillHittable(player, target, reach);
        if (invalidated) {
            target = null;
        }

        if (target == null) {
            // A target that just went invalid is itself the reason to look again, and that is the
            // one case where the interval and the movement checks are both beside the point.
            if (!contextChanged && !invalidated && !moved(player) && !turned(player)
                    && tick - scannedAtTick < STILL_SCAN_TICKS) {
                return false;
            }
            target = ConeTargeting.findTarget(player, reach).orElse(null);
            // Recorded only on a scan and not on a revalidation, on purpose. While the cached
            // target holds up, the player is free to walk all over the place: the movement check
            // exists to catch a stale answer the instant there is one, and there is no answer to
            // go stale until the target drops out.
            scannedFromEye = player.getEyePosition();
            scannedAlong = player.getLookAngle();
            scannedAtTick = tick;
        }
        return target != null;
    }

    /** Whether the player has shifted far enough that the cone points somewhere else. */
    private static boolean moved(LocalPlayer player) {
        return scannedFromEye == null
                || player.getEyePosition().distanceToSqr(scannedFromEye) > MOVE_EPSILON_SQUARED;
    }

    /** Whether the player has turned far enough that the cone points somewhere else. */
    private static boolean turned(LocalPlayer player) {
        return scannedAlong == null
                || player.getLookAngle().dot(scannedAlong) < TURN_DOT_THRESHOLD;
    }

    /** Drops the cached target and everything that keys it, so the next tick rescans. */
    private static void forget() {
        tracked = null;
        trackedItem = null;
        trackedReach = null;
        target = null;
        scannedFromEye = null;
        scannedAlong = null;
        scannedAtTick = 0;
    }

    /**
     * Vuelve el apuntado a cero, para el cambio de mundo.
     *
     * <p>El reloj {@code tick} tambien: un {@code scannedAtTick} de un mundo anterior contra un
     * contador que sigue subiendo dejaria el "no escanear hasta dentro de tres ticks" apagado para
     * siempre, que es el throttle entero roto sin un sintoma visible.</p>
     */
    public static void reset() {
        forget();
        tick = 0;
    }

    /**
     * Whether the held item is a ranged <b>only</b> weapon: a bow or a crossbow.
     *
     * <p><b>A trident is deliberately not one, and it used to be.</b> The check read
     * {@code instanceof TridentItem} too, which is true and reads as "a projectile weapon" --
     * correct about the item and wrong about the question. This answer is asked in two places and
     * the trident is the one item where they disagree:</p>
     *
     * <ul>
     *   <li><b>The crosshair.</b> In this system a trident is a melee weapon at five point three
     *       blocks on a fifteen degree cone, so its crosshair has to read the melee cone like every
     *       other weapon's. Answering "ranged" here left it permanently unlit unless the player was
     *       drawing a throw.</li>
     *   <li><b>The press gate in {@code SwingInputHandler}, and this is the bug.</b> That gate runs
     *       <em>after</em> the profile question, so a bow and a crossbow are already refused there
     *       -- neither sits in a weapon tag, so both derive to {@code NONE} and
     *       {@code WeaponKinds.fights} says no. For those two this is a redundant second answer;
     *       <b>for the trident it was the one that decided</b>, and it handed the whole left click
     *       back to vanilla. The report was "the trident is still full vanilla" with a green build,
     *       which is the same shape as the {@code #tridents} tag that never existed.</li>
     * </ul>
     *
     * <p>The right button is untouched: the trident's special is {@code NONE}, so vanilla still
     * throws it, and a thrown trident is a projectile and stays outside the cone by design.</p>
     *
     * <p>Checked by class rather than by a list of items, so a mod's own bow is a bow without this
     * project knowing the mod exists. That is the same capability-not-name rule the rest of the
     * compat layer follows.</p>
     */
    public static boolean isRanged(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem;
    }

    /**
     * A bow charges while the button is held; the crossbow is charged by a previous shot and stays
     * loaded whether or not the button is down.
     *
     * <p>Those are two different questions, so they are two different checks. Treating a loaded
     * crossbow as "not aiming" would make the crosshair blink out the instant the player released
     * the fire button, which is the exact moment they are lining up the next shot.</p>
     */
    private static boolean isDrawingRanged(LocalPlayer player, ItemStack held) {
        if (held.getItem() instanceof CrossbowItem) {
            return CrossbowItem.isCharged(held);
        }
        return player.isUsingItem();
    }
}
