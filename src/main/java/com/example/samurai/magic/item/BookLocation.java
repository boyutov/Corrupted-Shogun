package com.example.samurai.magic.item;

import com.example.samurai.magic.data.MagicData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** A server-resolved book location; never accepts an item supplied by a client. */
public enum BookLocation {
    EQUIPPED, MAIN_HAND, OFF_HAND;

    public ItemStack get(Player player) {
        return this == EQUIPPED ? MagicData.get(player).map(d -> d.getBookInventory().getItem(0)).orElse(ItemStack.EMPTY)
                : player.getItemInHand(this == MAIN_HAND ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
    }
    public static BookLocation of(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? MAIN_HAND : OFF_HAND;
    }
    public static BookLocation preferred(Player player) {
        for (BookLocation location : values()) {
            if (location.get(player).getItem() instanceof SpellBookItem) return location;
        }
        return null;
    }
}
