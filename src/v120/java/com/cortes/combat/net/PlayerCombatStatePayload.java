package com.cortes.combat.net;

import net.minecraft.network.FriendlyByteBuf;

/**
 * The 1.20.1 twin: one other player's state, delegated to the shared {@code PlayerCombatState}
 * encode/decode. There is no second opinion on the field order because there is only one
 * implementation of it. (⌒‿⌒)
 */
public record PlayerCombatStatePayload(PlayerCombatState state) {

    public void encode(FriendlyByteBuf buf) {
        PlayerCombatState.encode(buf, state);
    }

    public static PlayerCombatStatePayload decode(FriendlyByteBuf buf) {
        return new PlayerCombatStatePayload(PlayerCombatState.decode(buf));
    }
}
