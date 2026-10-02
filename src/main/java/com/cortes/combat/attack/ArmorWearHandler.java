package com.cortes.combat.attack;

import com.cortes.combat.registry.ModDamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;

/**
 * The axe's charged swing takes the armour apart twice as fast.
 *
 * <p><b>An event and not a mixin, and NeoForge is why.</b> The obvious way to double armour wear
 * is to intercept the loop inside {@code LivingEntity#doHurtEquipment}, and that loop is
 * <em>already dead code</em> in NeoForge: it calls {@code CommonHooks.onArmorHurt} and then
 * {@code if (true) return;}. The real damage goes through {@link ArmorHurtEvent}, which hands out
 * a mutable {@code newDamage} per slot. So there was never a loop to wrap.</p>
 *
 * <p>Worth writing down, because the alternative looked obvious and would have been a mixin on a
 * method whose body no longer runs -- silently doing nothing, exactly like the friction constant
 * that was never there.</p>
 *
 * <p><b>And it only fires for the one source.</b> The event also fires for fire, for cactus, for
 * anything that wears armour down, so without the type check this would double the wear of every
 * damage source in the game.</p>
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
    public static void onArmorHurt(ArmorHurtEvent event) {
        if (!ModDamageTypes.isAxeCharged(event.getDamageSource())) {
            return;
        }
        // The map is keyed by slot and only holds armour the hit could actually hurt, so iterating
        // it is the same set vanilla was about to iterate and not a guess about where armour is.
        for (ArmorHurtEvent.ArmorEntry entry : event.getArmorMap().values()) {
            if (entry == null || entry.newDamage <= 0.0F) {
                continue;
            }
            entry.newDamage = Math.max(entry.originalDamage,
                    entry.newDamage * WEAR_MULTIPLIER);
        }
    }

    /** Whether a slot could hold armour at all, named so the test above has something to assert. */
    public static boolean isArmourSlot(EquipmentSlot slot) {
        return slot != null && slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
    }
}