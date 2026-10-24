package com.kushtarbek.shogun.magic.network;
import com.kushtarbek.shogun.magic.data.MagicData;
import com.kushtarbek.shogun.magic.item.SpellBookItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * Creative inventory edits of the book slot. Vanilla's creative slot packet only accepts the
 * 45 vanilla inventory slots, so the book slot needs its own; like vanilla, creative is trusted.
 */
public record CreativeBookPacket(ItemStack book) {
    public void encode(FriendlyByteBuf b) { b.writeItem(book); }
    public static CreativeBookPacket decode(FriendlyByteBuf b) { return new CreativeBookPacket(b.readItem()); }
    public void handle(Supplier<NetworkEvent.Context> context) {
        var p = context.get().getSender();
        if (p != null && p.isCreative() && (book.isEmpty() || book.getItem() instanceof SpellBookItem)) {
            ItemStack stack = book.copy();
            if (!stack.isEmpty()) stack.setCount(1);
            MagicData.get(p).ifPresent(d -> d.getBookInventory().setItem(0, stack));
        }
        context.get().setPacketHandled(true);
    }
}
