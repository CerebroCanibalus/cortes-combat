package com.cortes.combat.net;

import com.cortes.combat.platform.ResourceIds;

import java.util.List;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * "You fired", and the things the blast reached.
 *
 * <p><b>It exists because the server cannot place the muzzle flash.</b> Not "does not have the
 * number" -- cannot: the weapon's transform is applied by the client while it renders, and
 * {@code GameRenderer#renderItemInHand} hands the item renderer a pose stack holding only the
 * inverse projection, with the camera's rotation in the modelview stack instead. A position derived
 * from the player's eye is therefore the only thing the server can produce, and the eye is where
 * the crosshair is, which is why a flash spawned there reads as the screen flickering.</p>
 *
 * <p>So the server says <em>what happened</em> and the client says <em>where the gun is</em>. The
 * ids are there so the client can throw fire at the same things the server damaged, rather than
 * at whatever happens to be under the crosshair when the packet lands.</p>
 *
 * @param targets entity ids the blast damaged, aimed-at first and capped at ten
 */
public record ArcabuzShotPayload(List<Integer> targets) implements CustomPacketPayload {

    /**
     * How many ids one blast can carry on the wire.
     *
     * <p>Sixteen rather than ten: the cap is the blast's, and the codec's ceiling is a safety
     * bound. Making them the same number would mean a second place where "ten" is written, and the
     * first one to be retuned would be this.</p>
     */
    public static final int MAX_TARGETS_IN_A_PACKET = 16;

    public static final Type<ArcabuzShotPayload> TYPE = new Type<>(
            ResourceIds.rl("arcabuz_shot"));

    /**
     * Hand-written rather than {@code ByteBufCodecs.VAR_INT.apply(list())}, for one reason and
     * one reason only: {@code ByteBufCodecs#list()} is <b>package-private</b> in 1.21.1, so from
     * this package the expression does not compile at all -- "cannot find symbol" on a method that
     * is right there in the class you are calling it on.
     *
     * <p>And writing it out bought the thing that mattered: the count is clamped on both sides of
     * the wire. {@link #MAX_TARGETS_IN_A_PACKET} is a promise about how many ids a blast can carry,
     * and a codec that trusted the sender would let a client announce a thousand.</p>
     */
    public static final StreamCodec<ByteBuf, ArcabuzShotPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        List<Integer> ids = payload.targets();
                        int count = Math.min(ids.size(), MAX_TARGETS_IN_A_PACKET);
                        ByteBufCodecs.VAR_INT.encode(buf, count);
                        for (int i = 0; i < count; i++) {
                            ByteBufCodecs.VAR_INT.encode(buf, ids.get(i));
                        }
                    },
                    buf -> {
                        int count = Math.min(ByteBufCodecs.VAR_INT.decode(buf),
                                MAX_TARGETS_IN_A_PACKET);
                        List<Integer> ids = new java.util.ArrayList<>(count);
                        for (int i = 0; i < count; i++) {
                            ids.add(ByteBufCodecs.VAR_INT.decode(buf));
                        }
                        return new ArcabuzShotPayload(List.copyOf(ids));
                    });

    @Override
    public Type<ArcabuzShotPayload> type() {
        return TYPE;
    }
}