package com.cortes.combat.weaponpack;

import com.cortes.combat.CortesCombat;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.UUID;

/**
 * The 1.20.1 wiring of the pack numbers. Same replace-last rule, older event.
 *
 * <p>1.20.1's event carries a {@code Multimap} instead of a component: entries match by
 * UUID (vanilla's fixed base ids, read out of `Item.<clinit>` bytecode like the
 * rebalances do), and the replacement keeps the same UUID so unequipping still cleans
 * up. LOWEST for the same reason: the pack declares the whole number, so it runs last
 * and whatever it does not declare is left for the rebalances. The arithmetic
 * ({@link #damageAmount}, {@link #speedAmount}) is pure, shared in shape, and tested --
 * the event itself needs a running game. (⌒‿⌒)</p>
 */
@Mod.EventBusSubscriber(modid = CortesCombat.MODID)
public final class WeaponPackAttributes {

    /** El atributo de daño arranca en 1: modifier = total - 1. */
    private static final float BASE_DAMAGE = 1.0F;
    /** El atributo de velocidad arranca en 4.0: vanilla's sword es -2.4 por esto. */
    private static final float BASE_SPEED = 4.0F;

    /** Vanilla's damage modifier id, read out of `Item.<clinit>` bytecode. */
    private static final UUID BASE_ATTACK_DAMAGE_UUID =
            UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");

    /** Vanilla's speed modifier id, read out of `Item.<clinit>` bytecode. */
    private static final UUID BASE_ATTACK_SPEED_UUID =
            UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA3");

    private WeaponPackAttributes() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttributes(ItemAttributeModifierEvent event) {
        if (event.getSlotType() != EquipmentSlot.MAINHAND) {
            return;
        }
        ResolvedWeapon pack = WeaponPackStore.resolve(event.getItemStack());
        if (pack == null) {
            return;
        }
        if (pack.damage() != null) {
            replace(event, Attributes.ATTACK_DAMAGE, BASE_ATTACK_DAMAGE_UUID,
                    damageAmount(pack.damage()));
        }
        if (pack.attackSpeed() != null) {
            replace(event, Attributes.ATTACK_SPEED, BASE_ATTACK_SPEED_UUID,
                    speedAmount(pack.attackSpeed()));
        }
    }

    /**
     * Del YAML al modifier: el pack escribe <b>corazones</b> (lo que el jugador lee en un
     * corazón y medio), el atributo vive en puntos -- dos por corazón -- y arranca en
     * {@value #BASE_DAMAGE}.
     */
    static float damageAmount(float hearts) {
        return hearts * 2.0F - BASE_DAMAGE;
    }

    /** Velocidad en toques por segundo, contra el default del atributo (4.0). */
    static float speedAmount(float perSecond) {
        return perSecond - BASE_SPEED;
    }

    /**
     * Reemplaza el modifier base si el ítem lo trae por defecto, y si no lo trae, lo pone en la
     * mano principal. Sobre los <em>defaults</em> del evento y no sobre su lista propia.
     */
    private static void replace(ItemAttributeModifierEvent event, Attribute attribute,
                                UUID id, float amount) {
        for (AttributeModifier modifier
                : new ArrayList<>(event.getModifiers().get(attribute))) {
            if (id.equals(modifier.getId())) {
                event.removeModifier(attribute, modifier);
                event.addModifier(attribute, new AttributeModifier(id, "Weapon modifier",
                        amount, AttributeModifier.Operation.ADDITION));
                return;
            }
        }
        event.addModifier(attribute, new AttributeModifier(id, "Weapon modifier",
                amount, AttributeModifier.Operation.ADDITION));
    }
}
