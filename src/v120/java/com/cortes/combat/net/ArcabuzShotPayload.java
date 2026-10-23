package com.cortes.combat.net;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * The 1.20.1 twin: the count-prefixed id list, hand-written like the shared one. The count is
 * clamped on both ends for the same reason: a lying count either reads garbage or stalls the
 * buffer, and neither is a muzzle flash.
 */
public record ArcabuzShotPayload(List<Integer> targets) {

    public static final int MAX_TARGETS_IN_A_PACKET = 16;

    public void encode(FriendlyByteBuf buf) {
        List<Integer> ids = targets;
        int count = Math.min(ids.size(), MAX_TARGETS_IN_A_PACKET);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeVarInt(ids.get(i));
        }
    }

    public static ArcabuzShotPayload decode(FriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), MAX_TARGETS_IN_A_PACKET);
        List<Integer> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(buf.readVarInt());
        }
        return new ArcabuzShotPayload(List.copyOf(ids));
    }
}
