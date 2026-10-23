package com.cortes.combat.net;

import com.cortes.combat.CortesCombat;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: <b>one other player's</b> combat state, because the pose of a player this
 * client is not drawing is a thing it has to work out.
 *
 * <h2>Why a new packet and not more fields on {@code CombatStatePayload}</h2>
 *
 * <p><b>Different destinations and a different question.</b> {@code CombatStatePayload} goes to one
 * player about their own HUD, and only when a number moved enough to see &mdash; a charge climbing
 * from 0.30 to 0.31 is not worth a packet for a bar that has barely moved. This one goes to everyone
 * who can see a given player, about that player, and the state that matters is a charge climbing at
 * all: at 0.30 and at 0.31 the arm is in a different place.</p>
 *
 * <p>So the two cannot share a packet. Folding this into the other one would mean either sending
 * it at the other's rate, which is to say never for most swings, or sending the HUD at this one's,
 * which is a packet per player per tick for three progress bars.</p>
 *
 * <h2>The fields are {@link PlayerCombatState}'s and there is nothing else in here</h2>
 *
 * <p>The record holds the state and the codec wraps it in one line, because
 * {@code StreamCodec.composite} tops out at six components in 1.21.1 and the state is nine fields
 * that do not decompose into anything useful.</p>
 */
public record PlayerCombatStatePayload(PlayerCombatState state) implements CustomPacketPayload {

    public static final Type<PlayerCombatStatePayload> TYPE = new Type<>(
            CortesCombat.rl("player_combat_state"));

    /**
     * Hand-written, for the same reason {@code PlayerCombatState} is hand-written.
     *
     * <p><b>The nine fields are written and read by one class</b>, so this line is the only place
     * the two ends could ever disagree about the order &mdash; and there is nothing to disagree
     * about, because both call the same two methods.</p>
     *
     * <p>{@code RegistryFriendlyByteBuf} is a {@code ByteBuf}, so passing it to
     * {@code PlayerCombatState.encode} is a widening and not a conversion.</p>
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerCombatStatePayload> STREAM_CODEC =
            StreamCodec.of(
                    // **Buffer first.** `StreamEncoder<O, T>` declares `encode(O buffer, T value)`,
                    // and backwards does not fail in an obvious way -- it binds, and the error
                    // surfaces as a missing method on the state.
                    (buf, payload) -> PlayerCombatState.encode(buf, payload.state()),
                    buf -> new PlayerCombatStatePayload(PlayerCombatState.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}