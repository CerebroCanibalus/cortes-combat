package com.cortes.combat.attack;

/**
 * A fully resolved attack, before anything is applied to the world.
 *
 * <p>Producing this is a pure computation from the attacker's stats and the charge, so the
 * interesting part of the damage formula can be tested without a world, a player or a
 * target. {@link CombatEngine} turns it into actual damage only once this exists.</p>
 *
 * <p>It used to carry a knockback value. It does not any more, and that removal is the point:
 * knockback is what the weapon does, not a by-product of how hard the swing hit, and folding it
 * into the damage result is what made a sword, a warhammer and a block in the hand all shove a
 * mob the same distance. It is now the weapon's own {@code KnockbackProfile}, applied separately.</p>
 *
 * <h2>Y la clase de golpe es un tipo, no dos booleanos</h2>
 *
 * <p>Llevaba {@code ignoredArmour} y {@code axeCharged}, con un javadoc explicando que <em>"son
 * mutuamente excluyentes por construcción"</em>. <b>Eso era una promesa y no un tipo</b>: nada
 * impedía construir un resultado con los dos en {@code true} y el único guard era el comentario.
 * Ahora es {@link DamageKind}, y las tres consecuencias -- si la armadura participa, si se desgasta
 * al doble y si la guardia se rompe -- se leen de un solo lugar en vez de estar repartidas entre
 * dos booleanos acá, un tag en {@code ModDamageTypes} y dos modificadores de atributo en
 * {@code CombatEngine}.</p>
 *
 * @param damage      final damage before armour, after charge and escalation
 * @param chargeRatio 0..1, how charged the swing was
 * @param kind        what this hit does to the target's armour and guard
 */
public record AttackResult(float damage, float chargeRatio, DamageKind kind) {

    public static final AttackResult MISS = new AttackResult(0.0F, 0.0F, DamageKind.NORMAL);

    public boolean isMiss() {
        return damage <= 0.0F;
    }
}
