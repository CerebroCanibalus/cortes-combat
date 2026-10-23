package com.cortes.combat.net;

import com.cortes.combat.platform.ResourceIds;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Client to server: the right button was pressed on a weapon that has a special.
 *
 * <p>The stack travels so the server can check it against what is actually in the hand. A client
 * that sent a bare "sweep" could be made to sweep with anything, and a special that fires on an
 * item the player is not holding is a special nobody can predict.</p>
 *
 * <p>Only the press travels. The server resolves it and the client draws its own viewmodel, so a
 * second press inside the same cooldown produces nothing the player can see, which is the correct
 * answer for an input that was refused.</p>
 */
public record SpecialPayload(ItemStack stack) implements CustomPacketPayload {

    public static final Type<SpecialPayload> TYPE = new Type<>(
            ResourceIds.rl("special"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpecialPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.STREAM_CODEC, SpecialPayload::stack,
                    SpecialPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}