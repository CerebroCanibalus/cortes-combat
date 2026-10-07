package com.cortes.combat.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A fall's damage, held for a moment so a mace hit can cancel it.
 *
 * <h2>Why the damage is deferred and not merely cancelled</h2>
 *
 * <p>The maza's whole argument is the fall, and the vanilla weapon already pays that argument off
 * with {@code resetFallDistance()} on a hit -- land the smash and the fall costs nothing. This mod
 * replaces the attack flow, so that call never happens, and the design asks for the same idea with
 * a window the player can actually use: <b>the fall is worth ten ticks of grace, and a smash landed
 * inside it cancels the damage. Miss it and you take it anyway.</b></p>
 *
 * <p>Which means the damage has to be <em>remembered</em> rather than refused. Cancelling it at the
 * event and forgetting it is the bug this record exists to prevent: the player would simply never
 * take fall damage with a maza in hand, which is a different and much larger gift than a window.</p>
 *
 * <h2>The third field, and why it is not a static</h2>
 *
 * <p>Applying the damage means calling {@code hurt}, which fires the very event that deferred it --
 * so the handler needs to know it is looking at its own second pass and not at a new fall.
 * <b>{@code applying} is that marker, and it lives in the state and not in a static boolean</b>: a
 * static would be one flag for the whole server, and two players landing in the same tick would
 * overwrite each other's. Same reason every other field in this package is an attachment.</p>
 */
public record FallDeferral(float damage, long dueTick, boolean applying) {

    /** Nothing pending. The resting value, and the one the window closes back into. */
    public static final FallDeferral NONE = new FallDeferral(0.0F, 0L, false);

    public static final Codec<FallDeferral> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("damage", 0.0F).forGetter(FallDeferral::damage),
            Codec.LONG.optionalFieldOf("due_tick", 0L).forGetter(FallDeferral::dueTick),
            Codec.BOOL.optionalFieldOf("applying", false).forGetter(FallDeferral::applying)
    ).apply(instance, FallDeferral::new));

    /** Whether a fall is being held right now: damage owed and not already being applied. */
    public boolean pending() {
        return !applying && damage > 0.0F;
    }
}
