package com.cortes.combat.attack;

import com.cortes.combat.enchant.CombatEnchantRules;
import com.cortes.combat.enchant.CombatEnchantments;
import com.cortes.combat.registry.ModDamageTypes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The 1.20.1 wiring of the axe's doubled armour wear. Same doubling, no event, no mixin.
 *
 * <p>1.20.1 has no armour-wear event (verified: no such class in Forge 47.4.28), and a mixin
 * would need a per-leg config split for one loop -- so the extra lands here, at
 * {@code LivingHurtEvent}, computed with vanilla's own per-piece formula read out of
 * {@code Inventory#hurtArmor}'s bytecode: every armour piece takes
 * {@code max(1, (int)(damage / 4))}, fire skips the fire-resistant, non-armour is skipped.
 * Vanilla then applies its own share on top when the hit proceeds, so the piece takes
 * vanilla + extra instead of vanilla with a multiplied field. (⌒‿⌒)</p>
 *
 * <p><b>Two rolls of Unbreaking instead of one, and that is the documented cost.</b>
 * Vanilla rolls its share inside its own {@code hurtAndBreak} and this rolls the extra in
 * a second call. The expectation is identical ({@code vanilla * mult} either way); only the
 * variance differs, which no playtest can see.</p>
 *
 * <p><b>And it only fires for the one source.</b> Without the type check this would double
 * the wear of every damage source in the game, including the fire the loop itself
 * special-cases.</p>
 */
public final class ArmorWearHandler {

    /**
     * How much harder the axe's charged swing eats armour.
     *
     * <p>Two, and it is on the durability rather than on the damage: doubling the damage would have
     * doubled the health loss too, and the axe already hits for less through armour than the
     * weapon it is supposed to beat a player in a long fight.</p>
     */
    public static final float WEAR_MULTIPLIER = 2.0F;

    private ArmorWearHandler() {
    }

    @SubscribeEvent
    public static void onArmorHurt(LivingHurtEvent event) {
        if (event.getAmount() <= 0.0F) {
            return;
        }
        if (!ModDamageTypes.isAxeCharged(event.getSource())) {
            return;
        }
        // **Mella: el desgaste sube, el dano no.** Y el nivel sale del arma del ATACANTE, que el
        // evento no trae -- se llega por la fuente del dano. Sin jugador, el multiplicador queda
        // en uno y el comportamiento es el de siempre.
        float mella = 1.0F;
        if (event.getSource().getEntity() instanceof Player attacker) {
            mella = CombatEnchantRules.mellaWear(CombatEnchantments.level(
                    attacker.level().registryAccess(), attacker.getMainHandItem(),
                    CombatEnchantments.MELLA));
        }
        // Vanilla's own share per piece, then the extra on top. The max() mirrors the 1.21.1
        // event's `max(original, newDamage * 2 * mella)`: never less than vanilla.
        int vanilla = Math.max(1, (int) (event.getAmount() / 4.0F));
        int total = Math.max(vanilla, (int) (vanilla * WEAR_MULTIPLIER * mella));
        int extra = total - vanilla;
        if (extra <= 0) {
            return;
        }
        boolean fire = event.getSource().is(DamageTypeTags.IS_FIRE);
        LivingEntity living = event.getEntity();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!isArmourSlot(slot)) {
                continue;
            }
            ItemStack stack = living.getItemBySlot(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem)) {
                continue;
            }
            if (fire && stack.getItem().isFireResistant()) {
                continue;
            }
            EquipmentSlot seen = slot;
            stack.hurtAndBreak(extra, living, p -> p.broadcastBreakEvent(seen));
        }
    }

    /** Whether a slot could hold armour at all, named so the test above has something to assert. */
    public static boolean isArmourSlot(EquipmentSlot slot) {
        return slot != null && slot.getType() == EquipmentSlot.Type.ARMOR;
    }
}
