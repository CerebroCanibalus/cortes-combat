package com.cortes.combat.attack;

import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The 1.20.1 replay of the maza smash's two moments. Same swing-owned shape, older tools.
 *
 * <p>1.21.1 leans on the wind-burst impulse context (armed immunity, saved impact point,
 * descent stop, extra particles). 1.20.1 has none of that, and it turns out only two of the
 * four need replacing:</p>
 * <ul>
 *   <li>The <b>fall above the smash is forgiven by resetting it</b>
 *       ({@code resetFallDistance}): the descent that follows accumulates fresh, and the
 *       landing it produces runs through {@code FallCoyote} like any other mace landing --
 *       which is also what happens on the other leg, where the impulse context only ever
 *       forgave the fall <em>above</em> the impact point.</li>
 *   <li>The <b>descent stops in place</b>, same constant, same motion packet.</li>
 * </ul>
 * <p>The rest is deliberately absent: there is no Wind Burst on this leg, so there is
 * nothing to reaffirm after the effects ({@code afterEffects} stays as the call site the
 * engine expects, and does nothing); there is no lowest-point tracking across smashes,
 * each smash forgives from itself; and there is no extra-particles flag, which was always
 * cosmetic next to the smash's own sound, shake and particles. (⌒‿⌒)</p>
 */
final class MaceSmash {

    /** Vanilla's {@code 0.01} against {@code Y}, which stops a descent in place. */
    private static final double STOP_FALL = 0.01D;

    private MaceSmash() {
    }

    /**
     * A landing for one swing, and the reason this is an object and not two static calls.
     * Same contract as the shared twin; see it for why the two halves divide this way.
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

        /** After one body's post-attack effects. Nothing to reaffirm on this leg. */
        void afterEffects() {
        }
    }

    /**
     * The landing states, on a smash that landed and <b>before</b> the post-attack effects.
     */
    static void begin(ServerPlayer player, Entity target) {
        // The impulse context's job, in the one call 1.20.1 has: everything fallen so far is
        // forgiven, and what accumulates from here is a new fall the landing logic will judge.
        player.resetFallDistance();
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.with(Direction.Axis.Y, STOP_FALL));
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }
}
