package com.example.samurai.magic.network;
import com.example.samurai.magic.data.MagicData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
public record SyncBookPacket(ItemStack book) {
    public void encode(FriendlyByteBuf b) { b.writeItem(book); }
    public static SyncBookPacket decode(FriendlyByteBuf b) { return new SyncBookPacket(b.readItem()); }
    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            var p = Minecraft.getInstance().player;
            if (p != null) MagicData.get(p).ifPresent(d -> d.getBookInventory().setItem(0, book));
        });
        context.get().setPacketHandled(true);
    }
}
