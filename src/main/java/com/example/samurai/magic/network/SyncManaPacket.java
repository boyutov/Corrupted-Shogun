package com.example.samurai.magic.network;

import com.example.samurai.magic.client.ClientMagicData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> owning client: current mana. Max mana arrives through the synced attribute. */
public record SyncManaPacket(float mana) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(mana);
    }

    public static SyncManaPacket decode(FriendlyByteBuf buf) {
        return new SyncManaPacket(buf.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientMagicData.setMana(mana));
        ctx.get().setPacketHandled(true);
    }
}
