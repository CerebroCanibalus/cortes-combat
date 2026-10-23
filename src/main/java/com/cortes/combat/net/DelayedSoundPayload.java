package com.cortes.combat.net;

import com.cortes.combat.CortesCombat;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * "Play this sound, but not yet."
 *
 * <p>The sabre's second cut is drawn eight ticks after the first, and its sound has to be heard
 * eight ticks after the first too. It was not, and the reason is not a wrong number: the second
 * sound was sent from inside the same loop iteration as the first, so both played on the same tick
 * about a millisecond apart. The ear hears one sound. The playtest reported the sound was missing
 * and it was being played twice.</p>
 *
 * <p><b>There is no way to schedule a sound on the server in 1.21.1.</b> No delayed task queue, and
 * {@code ClientboundSoundPacket} carries no delay. So the delay travels to the client and the
 * client holds it -- the same trick the slash particle uses, and for the same reason: a thing that
 * knows it is late is cheaper than a scheduler and needs no per-player state on a server that would
 * have to clean it up if the player logged out mid-swing.</p>
 *
 * <p>Sent to the attacker only, because it is their own swing being reported back to them.</p>
 */
public record DelayedSoundPayload(String sound, float pitch, int delayTicks)
        implements CustomPacketPayload {

    public static final Type<DelayedSoundPayload> TYPE = new Type<>(
            CortesCombat.rl("delayed_sound"));

    public static final StreamCodec<ByteBuf, DelayedSoundPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, DelayedSoundPayload::sound,
                    ByteBufCodecs.FLOAT, DelayedSoundPayload::pitch,
                    ByteBufCodecs.VAR_INT, DelayedSoundPayload::delayTicks,
                    DelayedSoundPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}