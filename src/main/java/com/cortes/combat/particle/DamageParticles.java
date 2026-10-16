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

    /**
     * The most points one hit is ever worth, and it is a ceiling on the <em>packet</em>, not a
     * balance number.
     *
     * <h2>Why the vanilla rule needs a lid</h2>
     *
     * <p>Vanilla's {@code (int)(damage * 0.5)} is unbounded because vanilla only ever calls it with
     * a real attack's damage. This mod calls it from a damage <em>event</em>, which sees every
     * source -- and one of them is not an attack at all: <b>{@code /kill}</b>. Verified in 1.21.1,
     * {@code LivingEntity#kill} is {@code hurt(damageSources().genericKill(), Float.MAX_VALUE)}, and
     * {@code actuallyHurt} hands that same unbounded number to {@code LivingDamageEvent.Post}
     * without ever clamping it to the victim's health ({@code f1 = getNewDamage()} is used raw).</p>
     *
     * <p>So {@code countFor(Float.MAX_VALUE)} is {@code (int)(Float.MAX_VALUE * 0.5F)}, which a
     * Java narrowing cast <b>saturates to {@code Integer.MAX_VALUE}</b>. That number then rides in a
     * {@code ClientboundLevelParticlesPacket}, and the client's {@code handleParticleEvent} turns it
     * straight into {@code for (i = 0; i < count; i++) addParticle(..)}: roughly two billion
     * particles, which is an out-of-memory crash and not a big number. The player sees the client
     * die on {@code /kill @e[type=..]} -- the shader of the dead creature is not involved.</p>
     *
     * <p><b>Clamping here and not at the three call sites</b> because this is the one function that
     * turns damage into a particle count, and every sender goes through it: {@code HitParticles}
     * (both overloads), {@code SlashParticles.damageIndicator} and the flicker overlay. A lid placed
     * at a call site would leave the next caller to rediscover the overflow.</p>
     *
     * <p>The value is deliberately far above anything the game can deal -- the largest weapon in
     * the catalogue does single-digit damage -- so the clamp never touches a real hit. Above it a
     * player counting points can no longer read the exact number, which is the honest cost of not
     * crashing, and a hit worth more than this was always an effect, a command or a bug.</p>
     */
    public static final int MAX_COUNT = 64;

    private DamageParticles() {
    }

    /**
     * The particle count for one landed hit.
     *
     * @param damage the damage the hit dealt, after every modifier
     * @return vanilla's {@code (int)(damage * 0.5)}, capped at {@link #MAX_COUNT}, and zero for a
     *         hit under two points
     */
    public static int countFor(float damage) {
        if (!(damage > 0.0F)) {
            // `!(x > 0)` and not `x <= 0` so a NaN -- which compares false to everything -- falls
            // here instead of surviving to the cast.
            return 0;
        }
        // The cast saturates before the min would run, so the comparison is against the float:
        // `Float.MAX_VALUE * 0.5F` is still astronomically above MAX_COUNT.
        return Math.min(MAX_COUNT, (int) (damage * 0.5F));
    }
}
