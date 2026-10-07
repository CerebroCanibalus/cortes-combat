package com.cortes.combat.particle;

/**
 * How many damage-indicator particles a hit is worth, which is vanilla's own rule.
 *
 * <h2>The feedback that went missing when the attack flow was replaced</h2>
 *
 * <p>Vanilla spawns these in {@code Player#attack}, and this mod replaces that method entirely --
 * so every hit this game has ever resolved has been missing the little dark hearts that say "that
 * landed". It is not decoration: the number is the only feedback that scales with how hard the hit
 * was, and a system that replaced the whole attack flow took it with everything else.</p>
 *
 * <p><b>Vanilla's count is half the damage, floored.</b> Verified in 1.21.1 rather than guessed:
 * {@code Player:1400} is {@code int i = (int)((double)damage * 0.5)} and the spawn is
 * {@code ParticleTypes.DAMAGE_INDICATOR} at the target's {@code getY(0.5)} with a tenth of a block
 * of spread. Both are reused verbatim, because "the same feedback as vanilla" is the whole point
 * of the exercise and a different count would be a second rule about one number.</p>
 *
 * <p>Pure and public so a test can walk it. A floored half means a one-point hit shows nothing,
 * which is vanilla's own behaviour and not an off-by-one here.</p>
 */
public final class DamageParticles {

    private DamageParticles() {
    }

    /**
     * The particle count for one landed hit.
     *
     * @param damage the damage the hit dealt, after every modifier
     * @return vanilla's {@code (int)(damage * 0.5)}, and zero for a hit under two points
     */
    public static int countFor(float damage) {
        if (!(damage > 0.0F)) {
            return 0;
        }
        return (int) (damage * 0.5F);
    }
}
