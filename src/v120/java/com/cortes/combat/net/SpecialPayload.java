package com.cortes.combat.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/**
 * The 1.20.1 twin: the stack travels with {@code writeItemStack}/{@code readItem}, which is
 * the 1.20.1 way of moving an item over the wire. Same contract as the shared twin: the press
 * carries the weapon the press was thrown with.
 */
public record SpecialPayload(ItemStack stack) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeItem(stack);
    }

    public static SpecialPayload decode(FriendlyByteBuf buf) {
        return new SpecialPayload(buf.readItem());
    }
}
