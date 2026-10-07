package com.cortes.combat.guard;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Who was blocked, and when, so that one swing costs one point.
 *
 * <h2>The bug this record exists for</h2>
 *
 * <p>Two reports arrived together: a guard that <em>sometimes</em> let a hit through and
 * <em>sometimes</em> lost two or all three of its points to a single attack. They are one bug.</p>
 *
 * <p>In 1.21.1's {@code LivingEntity#hurt}, the shield is resolved at line 1164 and the
 * invulnerability window is not even checked until line 1190 &mdash; twenty six lines later. So
 * anything that calls {@code hurt()} twice on the same entity in the same tick pays twice, and
 * nothing in between will stop it.</p>
 *
 * <p>Our own code does exactly that. The sabre's full charge lands twice by design, the loop in
 * {@code CombatEngine} clears {@code invulnerableTime} before <em>each</em> hit so the second cut
 * is not eaten, and both {@code hurt()} calls happen inside one swing. <b>So a charged sabre
 * took two points off a raised shield, and a third cut would have taken the third.</b></p>
 *
 * <p>And that answers the other half of the report without a theory: at zero stamina
 * {@code GuardState#isActive()} is false, the handler returns without ever setting the damage, and
 * the shield stops working. "Sometimes it went through" was the shield that had already been
 * emptied by the same swing that should have cost one.</p>
 *
 * <h2>Why a stamp and not a boolean</h2>
 *
 * <p>Because the two things that hit in one tick are not the same thing. A single swing lands
 * several cuts; an area weapon lands several cuts <em>on different mobs</em>. Both are one
 * attacker's tick. Keying on "the player just blocked something" would make a raised shield free
 * against the third mob in a sweep, which is its own bug in the opposite direction.</p>
 *
 * <p>So the key is the triple &mdash; <b>tick, attacker, target</b> &mdash; and a new target or a
 * new tick always pays. Pure, no Minecraft in it, and the handler is the only thing that knows
 * about any of it.</p>
 */
public record GuardStamp(long tick, int attacker, int target) {

    /** Nothing blocked yet, so the first hit always pays. */
    public static final GuardStamp NONE = new GuardStamp(Long.MIN_VALUE, 0, 0);

    public static final Codec<GuardStamp> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.LONG.fieldOf("tick").forGetter(GuardStamp::tick),
                    Codec.INT.fieldOf("attacker").forGetter(GuardStamp::attacker),
                    Codec.INT.fieldOf("target").forGetter(GuardStamp::target)
            ).apply(instance, GuardStamp::new));

    /**
     * Whether this hit is a different strike from the last one blocked.
     *
     * @param now the current game time
     * @param attackerId who swung
     * @param targetId who is defending
     * @return true when the point should be spent
     */
    public boolean shouldCharge(long now, int attackerId, int targetId) {
        return tick != now || attacker != attackerId || target != targetId;
    }

    /** The stamp to store once the point has been spent. */
    public GuardStamp charged(long now, int attackerId, int targetId) {
        return new GuardStamp(now, attackerId, targetId);
    }
}