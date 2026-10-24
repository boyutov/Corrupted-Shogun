package com.example.samurai.magic.network;

import com.example.samurai.magic.client.ClientMagicData;
import com.example.samurai.magic.data.CooldownTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server -> owning client: the full cooldown list. Sent when a cooldown starts or is cleared. */
public record SyncCooldownsPacket(List<Entry> entries) {

    public record Entry(ResourceLocation spell, int remaining, int total) {
    }

    public static SyncCooldownsPacket of(CooldownTracker tracker) {
        List<Entry> entries = new ArrayList<>();
        tracker.view().forEach((id, e) -> entries.add(new Entry(id, e.getRemaining(), e.getTotal())));
        return new SyncCooldownsPacket(entries);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeCollection(entries, (b, e) -> {
            b.writeResourceLocation(e.spell());
            b.writeVarInt(e.remaining());
            b.writeVarInt(e.total());
        });
    }

    public static SyncCooldownsPacket decode(FriendlyByteBuf buf) {
        return new SyncCooldownsPacket(buf.readList(
                b -> new Entry(b.readResourceLocation(), b.readVarInt(), b.readVarInt())));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientMagicData.setCooldowns(entries));
        ctx.get().setPacketHandled(true);
    }
}
