package com.cortes.combat.effect;

import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;

/**
 * The 1.20.1 body of Revelacion. Same field-cleaning, older tick contract, older tag.
 *
 * <p>1.20.1 has no {@code ILLAGER} entity-type tag (verified against the mapped jar: only
 * {@code RAIDERS} exists there). The design excludes "the illagers", and the raiders tag is
 * vanilla's own list of exactly those mobs on this version -- the same gameplay meaning,
 * read from data the game already maintains instead of a class list. Endermen stay excluded
 * by direct comparison. (⌒‿⌒)</p>
 */
public class RevelationEffect extends MobEffect {

    /** How hard each pass lands. Four points: a zombie falls in a second and a half. */
    public static final float DAMAGE = 4.0F;

    /** Every four ticks -- the 0.2 seconds of the request, since a tick cannot be halved. */
    public static final int INTERVAL_TICKS = 4;

    /** How long one pass of the reactor lasts. */
    public static final int DURATION_TICKS = 60;

    /** How often the field re-brands what is inside it, in ticks: once a second. */
    public static final int REFRESH_INTERVAL = 20;

    /** A pale gold, close to the icon's own light, so the tint does not wash the art out. */
    private static final int COLOUR = 0xFFF3B0;

    public RevelationEffect() {
        super(MobEffectCategory.HARMFUL, COLOUR);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        // `magic` is vanilla's own bypasses-armor source: a mob in the field is not being
        // fought, it is being unmade, and armour does not negotiate with it.
        entity.hurt(entity.damageSources().magic(), DAMAGE);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % INTERVAL_TICKS == 0;
    }

    /**
     * Whether this thing is what the reactor is for: hostile, minus the raiders and the endermen.
     */
    public static boolean isRevelable(LivingEntity entity) {
        return entity.getType().getCategory() == MobCategory.MONSTER
                && !entity.getType().is(EntityTypeTags.RAIDERS)
                && entity.getType() != EntityType.ENDERMAN;
    }
}
