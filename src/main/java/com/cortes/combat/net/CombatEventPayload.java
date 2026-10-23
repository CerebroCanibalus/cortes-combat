package com.cortes.combat.net;

import com.cortes.combat.CortesCombat;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: <b>something just happened to a player</b>, and the clock cannot work it out.
 *
 * <h2>Why this exists next to {@link PlayerCombatStatePayload}</h2>
 *
 * <p><b>Because four motions are born on a moment rather than on a value.</b> A chop starts on the
 * <em>release</em>, and the clock has to be told how far the hold had got when it let go &mdash;
 * because "the release frame has to show the item still back" is the reason
 * {@code pendingStrike} exists, and because a strike with no charge on it is a tap, forever. State
 * cannot carry that: {@code holdTicks} arriving at 0 says the button came up, not what it was worth
 * on the way.</p>
 *
 * <p>So the state describes the player and this describes the three instants, and the receiving
 * client feeds each one to the same {@code ViewModelMotion} its own player feeds.</p>
 *
 * <h2>Three kinds, not four</h2>
 *
 * <p>There is no {@code GUARD_BREAK}, and that is worth saying because the first draft of this design
 * listed one. <b>{@code ViewModelMotion#onGuardBreak} exists and nothing calls it.</b> The guard's
 * collapse is derived from the stagger &mdash; the guard item's remaining cooldown &mdash; which is a
 * field of {@link PlayerCombatStatePayload} and arrives on the normal rate. A guard break is a state
 * change, not a moment, and putting it here would have meant a second mechanism for something the
 * state channel already carries.</p>
 *
 * <p>That was found by reading who calls what rather than by designing from first principles: the
 * method exists, it is public, it is in the clock's API, and it has zero callers. A method with no
 * callers in a system being redesigned is a question, and the answer was "the design assumed it was
 * used and it is not".</p>
 *
 * <h2>Sent once, not on the rate</h2>
 *
 * <p>Unlike the state, this goes out exactly when the thing happens. There is nothing to
 * heartbeat: a strike that is sent late is a strike that is late, and a client that missed one is a
 * client that will miss the next one too.</p>
 */
public record CombatEventPayload(int entityId, Kind kind, float charge, int durationTicks)
        implements CustomPacketPayload {

    /** The three moments. Ordinals travel, so this list is a wire contract. */
    public enum Kind {
        /** The attack button came up and this is how charged it was. */
        STRIKE,
        /** The right button swept. */
        SWEEP,
        /** Whatever was held was released without a swing. */
        CANCEL
    }

    public static final Type<CombatEventPayload> TYPE = new Type<>(
            CortesCombat.rl("combat_event"));

    /**
     * The kind, on one byte, and <b>out of range is {@link Kind#CANCEL}</b>.
     *
     * <p>That is not a neutral choice: a version skew on this enum animates a cancel rather than a
     * strike, and a cancel is the one of the three that undoes something. A strike on an unknown
     * kind would play a swing the player never made, which is worse than a swing that gets cleaned
     * up.</p>
     *
     * <p><b>Declared on {@code ByteBuf} and not on {@code RegistryFriendlyByteBuf}</b>, because
     * that is what {@code idMapper} returns. It still fits inside the {@code composite} below,
     * whose components are {@code StreamCodec<? super B, T>} &mdash; a {@code ByteBuf} codec is a
     * supertype of one for a {@code RegistryFriendlyByteBuf}, which is exactly the widening the
     * declaration says it is.</p>
     */
    private static final StreamCodec<io.netty.buffer.ByteBuf, Kind> KIND = ByteBufCodecs.idMapper(
            index -> index >= 0 && index < Kind.values().length ? Kind.values()[index] : Kind.CANCEL,
            kind -> kind.ordinal());

    /**
     * Three of the four fit in {@code StreamCodec.composite}, which is the tool for this.
     *
     * <p><b>And that is a real difference from the state payload, not a preference.</b> The state
     * is nine fields whose only useful decomposition puts the whole thing inside one component, so
     * it is hand-written; this is three and {@code composite} is exactly what it is for. The
     * argument order of the first pair reads value-first and the lambda reads buffer-first, which
     * is the one inconsistency in this project's net code and it is documented at both sites.</p>
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatEventPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CombatEventPayload::entityId,
                    KIND, CombatEventPayload::kind,
                    ByteBufCodecs.FLOAT, CombatEventPayload::charge,
                    ByteBufCodecs.VAR_INT, CombatEventPayload::durationTicks,
                    CombatEventPayload::new);

    /** A strike at the given charge, for the given attack interval. */
    public static CombatEventPayload strike(int entityId, float charge, int durationTicks) {
        return new CombatEventPayload(entityId, Kind.STRIKE, charge, durationTicks);
    }

    /** A sweep. It carries nothing, because a sweep has no charge and no length of its own. */
    public static CombatEventPayload sweep(int entityId) {
        return new CombatEventPayload(entityId, Kind.SWEEP, 0.0F, 0);
    }

    /** A hold released without a swing. */
    public static CombatEventPayload cancel(int entityId) {
        return new CombatEventPayload(entityId, Kind.CANCEL, 0.0F, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}