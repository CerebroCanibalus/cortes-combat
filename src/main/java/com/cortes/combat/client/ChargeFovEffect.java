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
     * <p>Deliberately small. A large FOV shift is a strong signal that something happened, and
     * this is not something that happened: it is a readout of how long the button has been down,
     * which the player can already see on the charge bar and now on the item itself. Three
     * degrees is felt as the world tightening without being startling, and it stays under the
     * threshold where someone notices it against the crosshair.</p>
     */
    private static final float MAX_NARROWING = 3.0F;

    private ChargeFovEffect() {
    }

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        if (!(event.getPlayer() instanceof LocalPlayer player)) {
            return;
        }
        float narrowing = narrowingFor(player);
        if (narrowing == 0.0F) {
            return;
        }
        event.setNewFovModifier(event.getNewFovModifier() - narrowing);
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
}
