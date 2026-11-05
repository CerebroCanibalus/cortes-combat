package com.cortes.combat.net;

import net.minecraft.network.FriendlyByteBuf;

/**
 * The 1.20.1 twin of the server settings message. Same four values, same order, one source.
 *
 * <p><b>The order of the fields is written by {@link ServerSettings}</b> and both ends call the
 * same two methods, so there is no second opinion about what goes first. The only thing that
 * changes here is the plumbing: 1.20.1 has no {@code CustomPacketPayload}, so the message is a
 * plain record with a hand-written encoder.</p>
 *
 * <p><b>And an unknown message id does not disconnect.</b> Verified in the Forge 47.4.28 source
 * ({@code IndexedMessageCodec#consume}): a discriminator the receiver does not know is logged as
 * an error and dropped. That is why this message can be appended to the channel's list without
 * breaking a client that runs an older jar.</p>
 */
public record ServerSettingsPayload(ServerSettings settings) {

    public void encode(FriendlyByteBuf buf) {
        ServerSettings.encode(buf, settings);
    }

    public static ServerSettingsPayload decode(FriendlyByteBuf buf) {
        return new ServerSettingsPayload(ServerSettings.decode(buf));
    }
}
