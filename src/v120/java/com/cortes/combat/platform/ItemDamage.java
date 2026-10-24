package com.cortes.combat.platform;

/**
 * The 1.20.1 body of the durability payment. Same point, older spelling.
 *
 * <p>1.20.1 has no slot-argument overload, so the break callback names the slot by hand.
 * Same Unbreaking rolls, same event, same sound. (⌒‿⌒)</p>
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
                p -> p.broadcastBreakEvent(net.minecraft.world.entity.EquipmentSlot.MAINHAND));
    }
}
