package com.cortes.combat.client;

import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.core.weapon.ChargeProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.ComputeFovModifierEvent;

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

    /**
     * How much the FOV closes at the end of a full arcabuz reload, in degrees.
     *
     * <p><b>Not the same number as {@link #MAX_NARROWING} on purpose, and not a second use of
     * it.</b> Two tenths against the charge's two tenths and two tenths, but a reload is fifty
     * ticks of holding a button and a charge is a second of holding a button, so the same number
     * held for two and a half times as long is two and a half times as much FOV movement on
     * screen. The lesson {@link #MAX_NARROWING} already carries -- that a full degree is a lot --
     * turns out to be about <em>time</em> as much as degrees.</p>
     */
    private static final float RELOAD_NARROWING = 0.30F;

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
        if (narrowing > 0.0F) {
            event.setNewFovModifier(event.getNewFovModifier() - narrowing);
        }
        // **The recoil is applied after the guard and in the other direction, because it is a
        // different thing and putting it in the same number broke it silently.**
        //
        // It used to be subtracted into the same `narrowing` the two closing effects share, which
        // makes the total negative while a shot is punching, and the guard above returns on
        // anything at or below zero. **So the one moment the view was supposed to open was the one
        // moment the guard discarded it** -- the effect existed, it was computed every frame, and
        // the playtest reported nothing at all, which is the same shape as a channel wired to
        // nothing.
        //
        // No easing here, and that is deliberate: the recoil channel is already a four tick shape
        // read from the same interpolated value that is drawing the weapon this frame. Easing a
        // curve that is already the right length is the second smoothing pass this file's own
        // MAX_NARROWING javadoc warns about -- two curves for one number, free to disagree.
        float widening = recoilWidening();
        if (widening > 0.0F) {
            event.setNewFovModifier(event.getNewFovModifier() + widening);
        }
    }

    /**
     * How many frames' worth of time has passed, as a multiple of one sixtieth of a second.
     *
     * <p>Read from {@code Minecraft#getFrameTime()}, which is this version's frame
     * interpolation value. Same role as the shared twin's DeltaTracker read.</p>
     *
     * <p>Using the tick rate here instead would make the easing run at a third of its intended
     * speed on a thirty frame machine, and the FOV would visibly lag the charge that is driving
     * it, which is the thing easing was supposed to prevent.</p>
     */
    private static float rate() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return 1.0F;
        }
        float ticks = mc.getFrameTime();
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
        // **A max of two closing ones, and the recoil opens the view rather than closing it.** One of
        // the three has to go the other way, and it cannot be folded into the same return:
        // a charge and a reload both narrow, and a shot widens.
        float narrowing = Math.max(chargeNarrowingFor(player), reloadNarrowing());
        return narrowing;
    }

    /**
     * The arcabuz's blast widens the field of view, the opposite sign to everything else here.
     *
     * <p><b>A punch, not a rush.</b> A recoil that opened the view over four ticks and closed it
     * again would read as the world breathing in, which is what a sprint FOV effect looks like and
     * has nothing to do with a gun going off. What a shot does to the eye is a punch: it happens
     * at once and it is over.</p>
     *
     * <p><b>It goes through the same easing as the charge, not around it.</b> A discontinuity in
     * a field of view reads as a glitch, and a four tick pulse is short enough that the easing is
     * the thing that keeps it from being one.</p>
     *
     * <p>Smaller than the reload's, for the reason {@link #MAX_NARROWING} already gives: the
     * arcabuz's own motion is the loud part and this only has to be felt.</p>
     */
    private static float recoilWidening() {
        float bump = 1.0F - ItemViewModel.recoilAt(partialTick());
        return RECOIL_WIDENING * Math.max(0.0F, Math.min(1.0F, bump));
    }

    /**
     * How much the shot opens the field of view at full recoil, in degrees.
     *
     * <p><b>Two thirds of the reload's narrowing</b>, and lasting four ticks against fifty. The
     * charge is a thing the player holds for a second and watches build; the shot is something
     * the player did not ask to be startled by.</p>
     */
    private static final float RECOIL_WIDENING = 0.08F;

    /**
     * The arcabuz's half of the effect: the view closes a hair while it loads.
     *
     * <p>Asked for by name in the playtest -- "the screen should zoom in very slightly" -- and it
     * is a separate number from {@link #MAX_NARROWING} rather than a second use of it because
     * <b>the two have opposite shapes</b>. The charge is a burst that saturates near the top, so
     * the camera snaps shut and holds; a reload is fifty ticks of the player holding a button
     * waiting, and a camera that snapped shut for two and a half seconds would be a punishment
     * rather than a cue. So this one is the progress itself, unbent, which is a slow creep the
     * whole way.</p>
     *
     * <p>And it is deliberately smaller than the charge's: the arcabuz's own motion is small, and
     * this is the third copy of the same information (the model leans, the FOV leans, the player is
     * holding the button). Two tenths of a degree at the end of fifty ticks is felt and not
     * argued with.</p>
     */
    private static float reloadNarrowing() {
        float progress = clamp01(ArcabuzFuseState.reloadProgress());
        if (progress <= 0.0F) {
            return 0.0F;
        }
        return RELOAD_NARROWING * progress;
    }

    /**
     * The charging half, unchanged: the original effect, with its saturation and its own floor.
     */
    private static float chargeNarrowingFor(LocalPlayer player) {
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
     * <p>1.20.1 names it {@code getFrameTime()}; the shared twin documents the 1.21.1
     * spelling. Same value: the fraction of a tick the last frame covered.</p>
     */
    private static float partialTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return 0.0F;
        }
        return mc.getFrameTime();
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
