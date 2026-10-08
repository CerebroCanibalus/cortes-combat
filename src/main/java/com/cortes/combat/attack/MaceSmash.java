package com.cortes.combat.attack;

import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Vanilla's maza smash, as the two moments this project's replaced attack flow has to replay by
 * hand.
 *
 * <h2>What vanilla does, and where it sits</h2>
 *
 * <p>{@code MaceItem#hurtEnemy} runs on every smash and does four things that are not damage: it
 * records the impact point, arms a one-use fall immunity for the descent, kills the current
 * downward velocity in place, and -- when the victim is grounded -- flags the extra fall particles.
 * Vanilla's {@code postHurtEnemy} then calls {@code resetFallDistance()}. All of that lives on the
 * item, and this project never calls the item's attack methods: the flow is replaced by
 * {@code CombatEngine}, so the states have to be reproduced where the swing is resolved.</p>
 *
 * <h2>The order is not decoration</h2>
 *
 * <p><b>{@link #begin} goes before the post-attack effects, and {@link #reaffirm} after.</b> Vanilla
 * runs {@code hurtEnemy} inside {@code hurt}, then {@code EnchantmentHelper.doPostAttackEffects},
 * then {@code postHurtEnemy} -- read at {@code Player#attack} 1377/1380/1385. The split here mirrors
 * that for one reason: Wind Burst. Its explosion is one of those post-attack effects, it launches
 * the player upward, and a descent stop applied after it would cancel the launch the player just
 * earned. What Wind Burst <em>does</em> break is the immunity flag -- {@code ServerPlayer
 * #onExplosionHit} clears it, because the explosion's source entity is the player and not a wind
 * charge -- so the flag (and only the flag, plus the impact position it needs) is set again after
 * the effects.</p>
 *
 * <p>Not a permanent immunity: vanilla's own impulse context is single-use, cleared by
 * {@code tryResetCurrentImpulseContext} on the first landing, and it only forgives the fall above
 * the impact point. {@link FallCoyote} holds the window's half; this holds vanilla's half.</p>
 */
final class MaceSmash {

    /** Vanilla's {@code 0.01} against {@code Y}, which stops a descent in place. */
    private static final double STOP_FALL = 0.01D;

    private MaceSmash() {
    }

    /**
     * A landing for one swing, and the reason this is an object and not two static calls.
     *
     * <p><b>The maza hits up to three things with one left click</b> ({@code WeaponTables}), and
     * {@code CombatEngine} fires the post-attack effects once per body. Wind Burst therefore
     * explodes once per body, and each explosion clears the immunity the previous one had armed.
     * So the two halves do not divide the same way:</p>
     *
     * <ul>
     *   <li><b>{@link #onLanding} once</b> -- the descent stop and the impulse arming belong to the
     *       moment of the first landed hit, before any effect. Running it again for the second or
     *       third body would overwrite the upward launch Wind Burst just applied with the
     *       descent-killing {@code 0.01}, so the enchantment would do nothing on a multi-hit
     *       swing.</li>
     *   <li><b>{@link #afterEffects} every time</b> -- each body's Wind Burst clears the flag, so
     *       the last one would leave the player unprotected for the fall it caused unless the flag
     *       is set again after every body's effects.</li>
     * </ul>
     *
     * <p>One per swing, created before the targets are resolved and threaded through every
     * {@code apply}. Not static, not a field: the state lives exactly as long as the swing that
     * owns it.</p>
     */
    static final class Landing {

        private final ServerPlayer player;
        private boolean landed;

        Landing(ServerPlayer player) {
            this.player = player;
        }

        /** The first landed hit of the swing arms the states; later ones are deliberately no-ops. */
        void onLanding(Entity victim) {
            if (landed) {
                return;
            }
            landed = true;
            begin(player, victim);
        }

        /** After one body's post-attack effects, which may have been a Wind Burst explosion. */
        void afterEffects() {
            reaffirm(player);
        }
    }

    /**
     * The landing states, on a smash that landed and <b>before</b> the post-attack effects.
     *
     * <p>Verbatim {@code MaceItem#hurtEnemy}: the impact point is the lower of the current one and
     * here (a second smash on one fall keeps the lowest point reached), the immunity is armed, the
     * descent is stopped so the player does not keep falling through the hit, and a grounded victim
     * gets the extra-fall-particle flag.</p>
     */
    static void begin(ServerPlayer player, Entity target) {
        setImpact(player);
        player.setIgnoreFallDamageFromCurrentImpulse(true);
        player.setDeltaMovement(player.getDeltaMovement().with(Direction.Axis.Y, STOP_FALL));
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        if (target.onGround()) {
            player.setSpawnExtraParticlesOnFall(true);
        }
    }

    /**
     * The immunity, set again after the post-attack effects have had their chance to clear it.
     *
     * <p>Only the flag and the impact point: the descent is deliberately left alone, because by now
     * Wind Burst may have replaced it with an upward launch and stopping it would erase the
     * enchantment. {@code setIgnoreFallDamageFromCurrentImpulse(true)} also refreshes the impulse
     * context's grace clock, vanilla's own forty-tick window before the context may be reset.</p>
     */
    static void reaffirm(ServerPlayer player) {
        setImpact(player);
        player.setIgnoreFallDamageFromCurrentImpulse(true);
    }

    /**
     * Vanilla's minimum-with-the-saved guard, and it is not a simplification to drop.
     *
     * <p>If an impulse is already being ignored and a position was already saved, a new smash keeps
     * whichever is <b>lower</b> -- so two smashes on one fall forgive down to the lowest point
     * reached, not just the last one. The condition is exactly vanilla's, and its two halves are one
     * thought: there is a saved point to compare against.</p>
     */
    private static void setImpact(ServerPlayer player) {
        Vec3 here = player.position();
        if (player.isIgnoringFallDamageFromCurrentImpulse() && player.currentImpulseImpactPos != null) {
            if (player.currentImpulseImpactPos.y > here.y) {
                player.currentImpulseImpactPos = here;
            }
        } else {
            player.currentImpulseImpactPos = here;
        }
    }
}
