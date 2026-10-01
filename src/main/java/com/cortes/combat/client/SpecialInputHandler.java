package com.cortes.combat.client;

import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.guard.GuardStats;
import com.cortes.combat.net.SpecialPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The right button, when it means a special instead of a guard.
 *
 * <p><b>The guard is asked first and wins if it can.</b> The guard reads either hand, so a player
 * holding a halberd with a shield in the off hand presses the right button and gets a block: that
 * is what the unified guard is, and a special that stole the button from it would be a special
 * that changed behaviour by what happened to be in the other hand.</p>
 *
 * <p>With nothing to block with, the weapon's special takes it. Four of the five new families
 * cannot guard at all, and the halberd is one of them, which is the whole reason this class
 * exists: those weapons used to have a right button that raised a shield and did nothing else.</p>
 *
 * <p>Same refusals as the attack button, in the same order, because the same reasons apply: a
 * screen owns the click, eating owns the click, and a spectator owns nothing.</p>
 */
public final class SpecialInputHandler {

    private SpecialInputHandler() {
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != InputConstants.MOUSE_BUTTON_RIGHT) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // The button can be pressed on the title screen or while a world loads, and a null
        // connection there takes the render thread with it.
        if (mc.getConnection() == null || mc.level == null) {
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null || player.isSpectator() || player.isUsingItem()) {
            return;
        }

        WeaponStats stats = WeaponStats.of(player.getMainHandItem());
        if (stats.special() == SpecialType.NONE) {
            return;
        }

        // The guard is asked before the special, from either hand. Anything that can block takes
        // the button, and only a player holding nothing that blocks gets the special.
        if (GuardStats.best(player).canGuard()) {
            return;
        }

        PacketDistributor.sendToServer(new SpecialPayload(player.getMainHandItem()));
    }
}