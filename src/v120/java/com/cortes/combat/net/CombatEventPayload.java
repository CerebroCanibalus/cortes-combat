package com.cortes.combat.net;

import net.minecraft.network.FriendlyByteBuf;

/**
 * The 1.20.1 twin: the three moments, with the kind on one byte. An out-of-range kind reads as
 * {@code CANCEL}, the same contract as the shared twin: a version skew animates a cancel (which
 * undoes) rather than a strike nobody threw.
 */
public record CombatEventPayload(int entityId, Kind kind, float charge, int durationTicks) {

    /** The three moments. Ordinals travel, so this list is a wire contract. */
    public enum Kind {
        STRIKE,
        SWEEP,
        CANCEL
    }

    public static CombatEventPayload strike(int entityId, float charge, int durationTicks) {
        return new CombatEventPayload(entityId, Kind.STRIKE, charge, durationTicks);
    }

    public static CombatEventPayload sweep(int entityId) {
        return new CombatEventPayload(entityId, Kind.SWEEP, 0.0F, 0);
    }

    public static CombatEventPayload cancel(int entityId) {
        return new CombatEventPayload(entityId, Kind.CANCEL, 0.0F, 0);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeByte(kind.ordinal());
        buf.writeFloat(charge);
        buf.writeVarInt(durationTicks);
    }

    public static CombatEventPayload decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        int ordinal = buf.readByte();
        Kind[] kinds = Kind.values();
        Kind kind = ordinal >= 0 && ordinal < kinds.length ? kinds[ordinal] : Kind.CANCEL;
        return new CombatEventPayload(id, kind, buf.readFloat(), buf.readVarInt());
    }
}
