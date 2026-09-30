package com.cortes.combat.client;

import com.cortes.combat.attack.WeaponStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;

import java.util.Optional;

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
 * every swing with the same cone test.</p>
 */
public final class CombatAim {

    private CombatAim() {
    }

    /** Called once per client tick. */
    public static void update() {
        LocalPlayer player = Minecraft.getInstance().player;
        ClientCombatState.setAiming(player != null && isAiming(player));
    }

    public static boolean isAiming(LocalPlayer player) {
        if (player == null || player.isSpectator()) {
            return false;
        }
        ItemStack held = player.getMainHandItem();
        return isRanged(held)
                ? isDrawingRanged(player, held)
                : ConeTargeting.findTarget(player, WeaponStats.of(held).reach()).isPresent();
    }

    /**
     * Whether the held item is a projectile weapon at all.
     *
     * <p>Checked by class rather than by a list of items, so a mod's own bow is a bow without
     * this project knowing the mod exists. That is the same capability-not-name rule the rest of
     * the compat layer follows.</p>
     */
    public static boolean isRanged(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem
                || stack.getItem() instanceof TridentItem;
    }

    /**
     * Bow and trident charge while the button is held; the crossbow is charged by a previous
     * shot and stays loaded whether or not the button is down.
     *
     * <p>Those are three different questions, so they are three different checks. Treating a
     * loaded crossbow as "not aiming" would make the crosshair blink out the instant the player
     * released the fire button, which is the exact moment they are lining up the next shot.</p>
     */
    private static boolean isDrawingRanged(LocalPlayer player, ItemStack held) {
        if (held.getItem() instanceof CrossbowItem) {
            return CrossbowItem.isCharged(held);
        }
        return player.isUsingItem();
    }

    /** The target the player is currently aiming at, if any. Shared with the debug renderer. */
    public static Optional<LivingEntity> target() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || isRanged(player.getMainHandItem())) {
            return Optional.empty();
        }
        return ConeTargeting.findTarget(player, WeaponStats.of(player.getMainHandItem()).reach());
    }
}
