package com.cortes.combat.effect;

import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;

/**
 * Revelacion: what a running reactor does to everything hostile inside its radius.
 *
 * <p>The reactor was never only a power source. The area it energises is also an area it
 * <em>cleans</em>: everything hostile standing in the field is marked and then unmade a little at a
 * time, and whatever is left of it drops a diamond. The mark is an effect rather than a hidden
 * counter because the player has to be able to see it happening -- an invisible kill is a rumour, a
 * row of icons on the mobs is a mechanic.</p>
 *
 * <h2>Who wears it</h2>
 *
 * <p>Hostile, less the illagers and the endermen -- the request, and each half verified rather than
 * assumed: {@code MobCategory.MONSTER} is vanilla's own definition of hostile, and
 * {@code EntityTypeTags.ILLAGER} <b>exists in 1.21.1</b> (pillagers, vindicators, evokers and the
 * rest are read from vanilla's tag rather than from a list of classes we would have to keep up).
 * The enderman has no tag of its own, so it is the one type compared directly.</p>
 *
 * <p><b>Players never wear it.</b> The category check excludes them by construction -- a player is
 * {@code MobCategory.PLAYER}, not {@code MONSTER} -- so no branch has to remember that.</p>
 *
 * <h2>The two numbers</h2>
 *
 * <p>Four points of damage every {@link #INTERVAL_TICKS} ticks. The request said "4 life every 0.2
 * ticks"; a tick is the smallest unit the game has, so that read as <b>0.2 seconds, four ticks</b> --
 * one life a second, which melts a zombie in a second and a half and leaves a player time to run.
 * <b>Adjustable to playtest</b>, both of them: the damage is the lethality and the interval is the
 * tempo, and only watching it run decides them.</p>
 */
public class RevelationEffect extends MobEffect {

    /** How hard each pass lands. Four points: a zombie falls in a second and a half. */
    public static final float DAMAGE = 4.0F;

    /**
     * Every four ticks -- the 0.2 seconds of the request, since a tick cannot be halved.
     *
     * <p><b>Adjustable to playtest.</b> Faster than this and a hostile mob has no time to leave the
     * radius at all; slower and the effect reads as a suggestion.</p>
     */
    public static final int INTERVAL_TICKS = 4;

    /**
     * How long one pass of the reactor lasts. Comfortably longer than {@link #REFRESH_INTERVAL}, so
     * a mob standing in the field is never briefly un-branded between passes.
     */
    public static final int DURATION_TICKS = 60;

    /**
     * How often the field re-brands what is inside it, in ticks: once a second.
     *
     * <p>The effect carries three seconds, so a pass a second keeps the mark topped up without
     * asking the server to touch every mob in the radius every tick.</p>
     */
    public static final int REFRESH_INTERVAL = 20;

    /** A pale gold, close to the icon's own light, so the tint does not wash the art out. */
    private static final int COLOUR = 0xFFF3B0;

    public RevelationEffect() {
        super(MobEffectCategory.HARMFUL, COLOUR);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        // `magic` is vanilla's own bypasses-armor source, the one purification burns with: a mob in
        // the field is not being fought, it is being unmade, and armour does not negotiate with it.
        entity.hurt(entity.damageSources().magic(), DAMAGE);
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % INTERVAL_TICKS == 0;
    }

    /**
     * Whether this thing is what the reactor is for: hostile, minus the illagers and the endermen.
     *
     * <p>The category first because it is one comparison that excludes players, animals and every
     * passive mob at once; the two exceptions afterwards because they are the request. Not a name
     * list and not an {@code instanceof} chain: both halves are data the game already maintains.</p>
     */
    public static boolean isRevelable(LivingEntity entity) {
        return entity.getType().getCategory() == MobCategory.MONSTER
                && !entity.getType().is(EntityTypeTags.ILLAGER)
                && entity.getType() != EntityType.ENDERMAN;
    }
}
