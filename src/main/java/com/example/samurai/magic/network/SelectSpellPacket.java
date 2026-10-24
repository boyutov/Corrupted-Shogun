package com.example.samurai.magic.network;
import com.example.samurai.magic.casting.SpellCasting;
import com.example.samurai.magic.data.MagicData;
import com.example.samurai.magic.item.*;
import com.example.samurai.magic.spell.CastEndReason;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
public record SelectSpellPacket(BookLocation location, int slot) {
    public SelectSpellPacket(InteractionHand hand, int slot) { this(BookLocation.of(hand), slot); }
    public void encode(FriendlyByteBuf b) { b.writeEnum(location); b.writeVarInt(slot); }
    public static SelectSpellPacket decode(FriendlyByteBuf b) { return new SelectSpellPacket(b.readEnum(BookLocation.class), b.readVarInt()); }
    public void handle(Supplier<NetworkEvent.Context> context) {
        var p = context.get().getSender();
        if (p != null && p.isAlive() && !p.isSpectator() && p.containerMenu == p.inventoryMenu) {
            var stack = location.get(p);
            var slots = SpellBookItem.getSlots(stack);
            if (stack.getItem() instanceof SpellBookItem && slot >= 0 && slot < slots.size() && slots.get(slot) != null) {
                if (slot != SpellBookItem.getEffectiveActiveIndex(stack)) SpellCasting.endCast(p, CastEndReason.CANCELLED);
                SpellBookItem.setActiveIndex(stack, slot);
                if (location == BookLocation.EQUIPPED) MagicData.get(p).ifPresent(d -> { d.getBookInventory().setChanged(); d.sync(); });
            }
        }
        context.get().setPacketHandled(true);
    }
}
