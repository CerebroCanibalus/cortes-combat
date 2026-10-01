package com.cortes.combat.client;

import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.core.weapon.ChargeProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;

/**
 * The field of view closes while a charge builds and snaps open on the release.
 *
 * <p>An event, not a GUI layer, because that is what it is: {@code ComputeFovModifierEvent} is
 * the only supported way to change the FOV in 1.21.1 and it composes with everything vanilla
 * already does, including the player's own {@code fovEffectScale} setting, so someone who turned
 * effects off for motion sickness keeps them off here.</p>
 *
 * <p>The value is a subtraction from the modifier vanilla already computed, not a replacement,
 * for the same reason. Replacing it would fight the sprint FOV, the speed effect and every mod
 * that also touches the FOV, and the fight would show up as a flicker whenever two of them
 * happened on the same tick.</p>
 */
public final class ChargeFovEffect {

    /**
     * How much the FOV closes at a full charge, in degrees.
     *
     * <p>Three was abrupt. Six tenths was still "too intense". Two tenths was approved as a
     * number and then never actually heard, because the effect was firing on every ordinary tap
     * in the game: the constant micro-zoom that was being called "perfect" was the bug mistaken
     * for the feature. With the swing's floor no longer leaking in here, a charge is the only
     * thing that moves it at all, and this much is enough to feel.</p>
     *
     * <p>It also has to be too small to argue with, because the charge bar and the item both
     * already say precisely how charged the swing is and the FOV is the third copy of that
     * information. It earns its place by being felt rather than read.</p>
     *
     * <p>Twice now it has been too large, and the useful lesson from that is that <b>a full degree
     * of FOV is a very large amount of FOV</b>: at 70 degrees, three degrees is four percent of
     * the whole field of view, which is why it read as a camera effect rather than as a
     * tightening.</p>
     */
    private static final float MAX_NARROWING = 0.22F;

    /**
     * How hard the ramp saturates.
     *
     * <p>This is the "the maximum is too much and the minimum should only be slightly less"
     * request, and both halves are the same change. A linear ramp over the charge is a <b>third
     * meter</b>: the bar already says how charged the swing is, to the pixel, and a camera that
     * says it again at a different resolution is not adding information, it is adding a second
     * thing to look at while the player is trying to aim.</p>
     *
     * <p>So the ramp is squashed hard towards the top. A swing that has just crossed the
     * threshold is already most of the way closed, and a full charge is a little more of it than
     * that. What the camera says is "you are committed", and it says it almost immediately.</p>
     *
     * <p>Six is the exponent, and it was not tuned by eye against a screenshot: it is the point
     * where a quarter-charged swing reads as about four fifths closed, which is the difference
     * between the camera being a state and the camera being a gauge.</p>
     */
    private static final float SATURATION = 6.0F;

    /**
     * How fast the world opens back up, per frame at sixty frames a second.
     *
     * <p>Only used on the way <b>out</b>, and that asymmetry is the whole design.</p>
     *
     * <p>Closing tracks the charge exactly, with no easing of its own, because the charge is
     * already a smooth curve and adding a second smoothing pass to a smooth curve is how you get
     * the two out of step. Opening needs easing, because it is a discontinuity: the follow-through
     * ends and the charge is suddenly zero, and a field of view that snaps open on one frame reads
     * as a glitch rather than as a release.</p>
     *
     * <p>The version this replaced eased <em>both</em> directions, and that is what the playtest
     * called "jerky, not smooth, and the start is too abrupt". Easing the closing direction made
     * the world reach its target in about a quarter of a second while the charge took a full
     * second to get there, so the two ran at different speeds the entire way, and the player was
     * watching a zoom that had already finished while the bar was still half empty.</p>
     */
    private static final float OPENING = 0.18F;

    /** The narrowing currently on screen, in degrees. Never snapped while closing. */
    private static float narrowing;

    private ChargeFovEffect() {
    }

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        if (!(event.getPlayer() instanceof LocalPlayer player)) {
            return;
        }
        float target = narrowingFor(player);
        if (target >= narrowing) {
            // Exactly as fast as the charge. By construction in step with the item, because both
            // read the same interpolated value in the same frame.
            narrowing = target;
        } else {
            narrowing += (target - narrowing)
                    * (1.0F - (float) Math.pow(1.0F - OPENING, rate()));
            if (Math.abs(target - narrowing) < 0.002F) {
                narrowing = target;
            }
        }
        if (narrowing <= 0.0F) {
            return;
        }
        event.setNewFovModifier(event.getNewFovModifier() - narrowing);
    }

    /**
     * How many frames' worth of time has passed, as a multiple of one sixtieth of a second.
     *
     * <p>Read from {@code Minecraft#getTimer()}, verified in the 1.21.1 jar: there is no
     * {@code getDeltaTracker} and no {@code getFrameTime} on the client, and the field behind the
     * timer is private. {@link DeltaTracker.Timer#getGameTimeDeltaPartialTick(boolean)} with
     * {@code false} gives the fraction of a tick the last frame covered, which is exactly the
     * real frame time and not the tick rate.</p>
     *
     * <p>Using the tick rate here instead would make the easing run at a third of its intended
     * speed on a thirty frame machine, and the FOV would visibly lag the charge that is driving
     * it, which is the thing easing was supposed to prevent.</p>
     */
    private static float rate() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getTimer() == null) {
            return 1.0F;
        }
        float ticks = mc.getTimer().getGameTimeDeltaPartialTick(false);
        return Math.max(0.05F, Math.min(4.0F, ticks * 20.0F));
    }

    /**
     * How far the FOV is closed for this player right now, in degrees.
     *
     * <p>Reads the <b>same interpolated value, in the same frame</b> that the item is being drawn
     * with. That is the fix for "the zoom and the animation are not synchronised", and it is not a
     * tuning job: there is no second curve to keep in step because there is only one curve. The
     * effect and the viewmodel are two readings of one number, which is the only way two things
     * driven by a charge stay together.</p>
     *
     * <p>Before, the effect read a per-tick value and eased towards it with a constant faster
     * than the tick rate, so it tracked the twenty-hertz staircase one step at a time instead of
     * smoothing it, and the result was read as twenty small jerks a second. Now the input is
     * already continuous.</p>
     */
    private static float narrowingFor(LocalPlayer player) {
        ItemStack held = player.getMainHandItem();
        ChargeProfile profile = WeaponStats.of(held).charge();
        if (!profile.canCharge()) {
            return 0.0F;
        }
        float charge = clamp01(ItemViewModel.chargeAt(partialTick()));
        // `1 - (1-c)^n`, which is zero at zero and one at one and everything in between pressed
        // against the top. See SATURATION.
        return MAX_NARROWING * (1.0F - (float) Math.pow(1.0F - charge, SATURATION));
    }

    private static float clamp01(float value) {
        return value < 0.0F ? 0.0F : (value > 1.0F ? 1.0F : value);
    }

    /**
     * This frame's interpolation value, read the only way 1.21.1 allows.
     *
     * <p>{@code Minecraft} has no {@code getDeltaTracker()}: it is
     * {@code getTimer().getGameTimeDeltaPartialTick(false)}, verified against the 1.21.1 jar.</p>
     */
    private static float partialTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getTimer() == null) {
            return 0.0F;
        }
        return mc.getTimer().getGameTimeDeltaPartialTick(false);
    }

    /** Test hook and a way for the debug readout to see what the effect is doing. */
    public static float narrowingForDebug() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? 0.0F : narrowingFor(player);
    }

    /**
     * Drop the easing, for a session that has ended or a world that has changed.
     *
     * <p>Without it the field of view eases back to normal on its own over a few frames, which
     * on a disconnect means the last value the player saw before the screen changed is a
     * half-closed one, and the next world opens already mid-transition.</p>
     */
    public static void reset() {
        narrowing = 0.0F;
    }
}
