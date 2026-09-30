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
     * <p>This started at three and the playtest called it abrupt. It is a fifth of that now,
     * which is below the threshold where a player consciously notices the FOV at all, which is
     * the right place for something that is a readout rather than an event.</p>
     *
     * <p>The charge bar and the item both already say how charged the swing is, and they say it
     * precisely. The FOV is the third copy of that information and it earns its place by being
     * felt rather than read, so it has to be too small to argue with.</p>
     */
    private static final float MAX_NARROWING = 0.6F;

    /**
     * How fast the narrowing catches up, per frame at sixty frames a second.
     *
     * <p>Exponential approach against the real frame delta rather than a straight set, for the
     * same reason the damage vignette does: a FOV that snaps between two values on one frame
     * reads as a glitch, and one that interpolates reads as the world tightening. Linear
     * interpolation is the thing that looks wrong, because it holds a constant speed that ends
     * as abruptly as it started.</p>
     */
    private static final float APPROACH = 0.22F;

    /** The narrowing currently on screen, in degrees. Eased towards the target, never snapped. */
    private static float narrowing;

    private ChargeFovEffect() {
    }

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        if (!(event.getPlayer() instanceof LocalPlayer player)) {
            return;
        }
        float target = narrowingFor(player);
        if (target == 0.0F && narrowing == 0.0F) {
            return;
        }
        float delta = rate();
        // The real frame rate, not a nominal one: at thirty frames a second a fixed per-frame
        // factor runs at half speed and the effect stops matching the charge driving it.
        narrowing += (target - narrowing) * (1.0F - (float) Math.pow(1.0F - APPROACH, delta));
        if (Math.abs(target - narrowing) < 0.002F) {
            narrowing = target;
        }
        if (narrowing == 0.0F) {
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
     * <p>Follows the charge while the button is down and stays closed for the length of the
     * follow-through, so the snap open lands on the strike rather than before it. The strike
     * reading is the one that makes it feel connected: the world tightening as you wind up and
     * opening as the sword lands is a rhythm, and closing early gives it away.</p>
     */
    private static float narrowingFor(LocalPlayer player) {
        ItemStack held = player.getMainHandItem();
        ChargeProfile profile = WeaponStats.of(held).charge();
        if (!profile.canCharge()) {
            return 0.0F;
        }
        float charge = ItemViewModel.currentCharge();
        return MAX_NARROWING * charge;
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
