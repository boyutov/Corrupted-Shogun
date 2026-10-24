package com.example.samurai.magic.network;

import com.example.samurai.magic.item.SpellBookItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: make a slot of the held spell book active. */
public record SelectSpellPacket(InteractionHand hand, int slot) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(hand);
        buf.writeVarInt(slot);
    }

    public static SelectSpellPacket decode(FriendlyByteBuf buf) {
        return new SelectSpellPacket(buf.readEnum(InteractionHand.class), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof SpellBookItem) {
                SpellBookItem.setActiveIndex(stack, slot);
            }
        }
        ctx.get().setPacketHandled(true);
    }
}
