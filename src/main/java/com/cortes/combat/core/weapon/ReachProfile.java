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
