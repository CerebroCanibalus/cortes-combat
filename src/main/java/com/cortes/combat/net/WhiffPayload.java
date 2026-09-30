package com.cortes.combat.net;

import com.cortes.combat.CortesCombat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: I swung at nothing, charge me for it.
 *
 * <p>An empty record, deliberately. There is no vanilla packet for a swing that hit air, and
 * {@code Player#attack} cannot be called with a null target because it dereferences it
 * immediately, so without this the only way to make a whiff cost anything was to let vanilla
 * swing, and vanilla swinging tells the server nothing.</p>
 *
 * <p>The consequence of not having it was concrete: the arm would move, the cooldown bar would
 * not start, and the next click was free. Holding the button against empty air banked attacks,
 * so a swing-miss-swing gave two swings on one cooldown. The cooldown is a price paid on the
 * attempt, not on the contact.</p>
 *
 * <p>Carries no data because there is nothing to carry. Whether the swing hit something is
 * already known by the server, from the attack packet that arrives on the same click.</p>
 */
public record WhiffPayload() implements CustomPacketPayload {

    public static final Type<WhiffPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID, "whiff"));

    /**
     * No fields, so the codec writes nothing at all.
     *
     * <p>{@code StreamCodec.unit} rather than a composite with a filler: a record with no
     * components has nothing to serialise, and giving it a placeholder byte would put a byte on
     * the wire that exists only to satisfy the shape of the API.</p>
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, WhiffPayload> STREAM_CODEC =
            StreamCodec.unit(new WhiffPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
