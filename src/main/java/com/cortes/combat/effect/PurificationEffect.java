package com.cortes.combat.effect;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.particle.PurifiedParticles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;

/**
 * Purification: what a copper weapon leaves on the thing it hits.
 *
 * <p>The design (DESIGN.md 5bis) gives copper a niche rather than a damage number, and this is the
 * whole of it. Two effects, and both are the reason the metal exists:</p>
 *
 * <ul>
 *   <li><b>Fast, small, armour-piercing damage</b> against the undead and against anyone in
 *       netherite. Not against everything -- a copper weapon against a plain player is a weak
 *       weapon, which is the point.</li>
 *   <li><b>A thirty percent cut to the damage its victim deals</b>, as an attribute modifier, so a
 *       copper hit is a hit that makes the next exchange cheaper.</li>
 * </ul>
 *
 * <p><b>The damage goes through {@code DamageSources#magic}</b>, which is vanilla's own
 * {@code bypasses_armor} source. A damage type of our own would have meant a JSON file, a tag
 * entry and a test, for a mechanic whose entire claim is "it ignores armour" -- and magic is
 * already exactly that.</p>
 */
public class PurificationEffect extends MobEffect {

    /** How hard each tick lands. Small on purpose: this is a debuff, not a hit. */
    public static final float DAMAGE = 0.5F;

    /**
     * Twice a second, so a five second effect is ten small hits rather than a hundred tiny ones.
     *
     * <p>Half a point every half second is the user's number: slow enough to read as a burn
     * rather than as a second weapon, fast enough that five seconds of it is five points of damage
     * through any armour in the game.</p>
     */
    public static final int INTERVAL_TICKS = 10;

    /** How long the weapon leaves it on, in ticks. */
    public static final int DURATION_TICKS = 100;

    /** The cut to the victim's attack damage, as a fraction of its total. */
    public static final double DAMAGE_CUT = -0.30D;

    public PurificationEffect() {
        super(MobEffectCategory.HARMFUL, 0x4AA0A0);
        addAttributeModifier(
                Attributes.ATTACK_DAMAGE,
                ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID, "purification_damage"),
                DAMAGE_CUT,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (isPurifiable(entity)) {
            entity.hurt(entity.damageSources().magic(), DAMAGE);
            // The smoke is the debuff made visible, and it is spawned right where the damage
            // lands. On purpose it only appears on things purification actually hurts: a plain
            // player hit by a copper weapon takes nothing from this effect, and a plume off them
            // would advertise a burn that never comes. Server side (the guard is the cast), so
            // everyone sees it, tied to the effect's own clock rather than a second one.
            if (entity.level() instanceof ServerLevel server) {
                PurifiedParticles.puff(server, entity);
            }
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % INTERVAL_TICKS == 0;
    }

    /**
     * Whether this thing is what copper is for: the undead, or anyone hiding behind netherite.
     *
     * <p>{@code EntityTypeTags.UNDEAD} rather than an {@code instanceof} list or the
     * {@code isInvertedHealAndHarm} flag, so a mod's own undead is included without us knowing it
     * exists -- the same capability-not-name rule the profile derivation follows. And
     * {@code Holder.is(Holder)} rather than comparing {@code value()}, which is the check the
     * runtime actually offers for "is this the same material".</p>
     */
    public static boolean isPurifiable(LivingEntity entity) {
        if (entity.getType().is(EntityTypeTags.UNDEAD)) {
            return true;
        }
        for (ItemStack stack : entity.getArmorSlots()) {
            if (stack.getItem() instanceof ArmorItem armour
                    && armour.getMaterial().is(ArmorMaterials.NETHERITE)) {
                return true;
            }
        }
        return false;
    }
}
