package com.cortes.combat.net;

import com.cortes.combat.CortesCombat;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The server's memory of the right mouse button, and the only way the arcabuz's reload advances.
 *
 * <p><b>One bit, sent on transitions only.</b> The client reads the window every tick and sends
 * this when -- and only when -- what it sees differs from what it last told the server, so a
 * half-second hold is two packets rather than ten.</p>
 *
 * <p><b>Why the server cannot ask the window itself.</b> It cannot: the window lives on the
 * client's machine and the server is a different process in singleplayer and a different
 * computer in multiplayer. A reload that only advances while the button is down needs somebody to
 * tell the server that the button is down.</p>
 *
 * <p><b>And why this is reconciled rather than event-driven.</b> Because GLFW does not deliver a
 * release when the window loses focus with a button held -- the same missing release that once left
 * a swing open for the rest of a session, in {@code InputLiveness}'s own words. If the flag were
 * only set by release events, a player who alt-tabs mid-reload would leave the server waiting for
 * a press that is never coming, and the gun would sit at half loaded until they touched the mouse
 * again. Reading the physical button every tick makes the flag self-healing in the same direction
 * the swing's does.</p>
 *
 * @param held whether the right button is down right now
 */
public record SpecialHeldPayload(boolean held) implements CustomPacketPayload {

    public static final Type<SpecialHeldPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID, "special_held"));

    /** A record, so {@link com.mojang.serialization.codecs.RecordCodecBuilder} is ⊘ needed. */
    public static final StreamCodec<io.netty.buffer.ByteBuf, SpecialHeldPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, SpecialHeldPayload::held,
                    SpecialHeldPayload::new);

    @Override
    public Type<SpecialHeldPayload> type() {
        return TYPE;
    }
}