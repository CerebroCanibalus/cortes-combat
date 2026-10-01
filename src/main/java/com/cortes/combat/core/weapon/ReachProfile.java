package com.cortes.combat.core.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The shape of a weapon's hit area: a fixed cone pointing forward.
 *
 * <p>Horizontal and vertical angles are independent. The vertical one is deliberately tighter so
 * that a swing does not connect with something at the attacker's feet or something flying
 * overhead. See DESIGN.md section 2.</p>
 *
 * <p><b>There used to be a second, smaller cone inside this one that paid a bonus for landing
 * inside it.</b> It was the ropera's precision bonus, it survived a playtest as a number nobody
 * could feel, and the playtest that removed it said the honest reason: the ropera's hit area is
 * already the smallest in the game and making the player aim inside a cone <em>inside</em> that one
 * was a worse weapon, not a better one. A narrow cone is the weapon's identity and the bonus asked
 * the player to spend it.</p>
 *
 * <p>What made it unfixable rather than merely bad is worth writing down, because the shape of it
 * will come back. The smaller cone shared {@code ConeMath#inCone} and therefore shared the
 * <b>leash</b>, and the leash is a floor rather than a ceiling: near the player, a mob's own size
 * granted enough tolerance that the inner cone was <em>wider</em> than its angles suggested, and
 * far away the angle won and the bonus was a sliver. There is no value of the scale that makes a
 * second cone work inside the first one, because the two want opposite things from the same
 * tolerance: forgiveness for size, and strictness about aim.</p>
 *
 * <p>Pure geometry, no world access: this is the most testable type in the project.</p>
 */
public record ReachProfile(float distance, float horizontalAngle, float verticalAngle) {

    public static final ReachProfile UNARMED = new ReachProfile(3.0F, 25.0F, 20.0F);

    public static final Codec<ReachProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("distance").forGetter(ReachProfile::distance),
            Codec.FLOAT.fieldOf("horizontal_angle").forGetter(ReachProfile::horizontalAngle),
            Codec.FLOAT.fieldOf("vertical_angle").forGetter(ReachProfile::verticalAngle)
    ).apply(instance, ReachProfile::new));
}