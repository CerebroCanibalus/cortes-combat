package com.cortes.combat.platform;

/**
 * The one durability payment this mod makes, named once, here.
 *
 * <p>Vanilla's sword rule generalised: one point per landed hit, through vanilla's own
 * {@code hurtAndBreak} so Unbreaking rolls, the break event, the sound and the statistics
 * all behave. 1.20.1 spells the slot as a break callback and 1.21.1 as a slot argument;
 * the call sites pay the same point either way and never spell the difference. (⌒‿⌒)</p>
 */
public final class ItemDamage {

    private ItemDamage() {
    }

    /**
     * Damages the player's held item by {@code amount}, broadcasting the break in the
     * main hand if it snaps.
     */
    public static void damageMainHand(net.minecraft.world.entity.player.Player player, int amount) {
        player.getMainHandItem().hurtAndBreak(amount, player,
                net.minecraft.world.entity.EquipmentSlot.MAINHAND);
    }
}
