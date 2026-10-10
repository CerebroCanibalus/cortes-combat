package com.cortes.combat.state;

import com.cortes.combat.net.SwingPayload;

/**
 * A swing the server accepted but whose target had slipped out of the cone, waiting to be
 * confirmed.
 *
 * <h2>The report this exists for</h2>
 *
 * <p>"El golpe a veces se pierde pese a que el indicador de UI muestra que deberías dar el golpe."
 * The crosshair promised a hit and the swing whiffed, and the two most common ways that happens
 * are a target that stepped out between the press and the release, and a server whose copy of the
 * world is one tick behind the client's (entity positions and the player's own rotation arrive
 * over the wire). Both are the same shape: <b>the aim was legitimate when the swing was thrown
 * and the geometry disagreed a tick later.</b></p>
 *
 * <h2>What is remembered, and what is deliberately not</h2>
 *
 * <p>The intent only: <b>which entity, at which charge, with which weapon, expiring when</b>. The
 * damage is recomputed at connection from the live state (streak, fall, enchantments), and the
 * cone is re-checked strictly on every attempt -- the window forgives <em>timing</em>, never
 * <em>aim</em>. A player who has since turned away fails the very first re-check.</p>
 *
 * <p>{@code itemId} is a {@link String} and not an {@code Item}: the registry id of the weapon the
 * swing was thrown with, compared at connection so a scroll-wheel swap inside the window cancels
 * the hit instead of landing it with the wrong weapon. A String also keeps this record free of
 * Minecraft types, which is the line that decides whether a test in {@code src/test} can load it
 * at all.</p>
 *
 * <p>Transient by design: no codec, no {@code copyOnDeath}. A save or a death in the middle of
 * five ticks ends the swing, which is the honest outcome -- the arm already swung.</p>
 *
 * @param targetId the entity the swing is waiting on, or {@link SwingPayload#NO_TARGET} for none
 * @param chargeRatio the charge the release closed at, frozen; the hold is over, this is its verdict
 * @param itemId registry id of the held weapon at release, e.g. {@code "minecraft:iron_sword"}
 * @param dueTick game time when the window stops waiting
 */
public record PendingHit(int targetId, float chargeRatio, String itemId, long dueTick) {

    /** Nothing waiting. The resting value, and the one the window closes back into. */
    public static final PendingHit NONE =
            new PendingHit(SwingPayload.NO_TARGET, 0.0F, "", 0L);

    /**
     * How long a slipped swing waits for the cone to agree, in ticks. Five is 250 ms: one or two
     * round trips of lag, and short enough that a hit landing inside it still reads as the swing
     * that caused it rather than as a second, late attack.
     */
    public static final int WINDOW_TICKS = 5;

    /** Whether nothing is waiting. */
    public boolean none() {
        return targetId == SwingPayload.NO_TARGET;
    }

    /**
     * Whether the window has closed at the given game time.
     *
     * <p>The boundary is inclusive on the expiry side: the tick that reaches {@code dueTick} makes
     * one last connection attempt first and only then gives up, so the window is exactly
     * {@link #WINDOW_TICKS} ticks of trying rather than WINDOW_TICKS minus one.</p>
     */
    public boolean expiredAt(long now) {
        return now >= dueTick;
    }
}
