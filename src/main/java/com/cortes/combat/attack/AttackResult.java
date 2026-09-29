package com.cortes.combat.attack;

/**
 * A fully resolved attack, before anything is applied to the world.
 *
 * <p>Producing this is a pure computation from the attacker's stats and the charge, so the
 * interesting part of the damage formula can be tested without a world, a player or a
 * target. {@link CombatEngine} turns it into actual damage only once this exists.</p>
 *
 * @param damage      final damage before armour, after charge and escalation
 * @param chargeRatio 0..1, how charged the swing was
 * @param knockback   how hard to push, scaled by charge the way rafts_combat does
 * @param ignoredArmour whether this swing bypasses armour (warhammer's charged hit)
 */
public record AttackResult(float damage, float chargeRatio, float knockback, boolean ignoredArmour) {

    public static final AttackResult MISS = new AttackResult(0.0F, 0.0F, 0.0F, false);

    public boolean isMiss() {
        return damage <= 0.0F;
    }
}
