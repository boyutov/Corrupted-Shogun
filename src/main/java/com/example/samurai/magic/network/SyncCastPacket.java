package com.example.samurai.magic.network;

import com.example.samurai.magic.client.ClientMagicData;
import com.example.samurai.magic.data.MagicData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Server -> owning client: cast started or ended. Sent only on transitions;
 * the client advances the progress itself between them.
 */
public record SyncCastPacket(@Nullable ResourceLocation spell, int level, int elapsed, int remaining) {

    public static SyncCastPacket of(MagicData data) {
        return new SyncCastPacket(data.getCastingSpellId(), data.getCastingLevel(),
                data.getCastTicksElapsed(), data.getCastTicksRemaining());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(spell != null);
        if (spell != null) {
            buf.writeResourceLocation(spell);
            buf.writeVarInt(level);
            buf.writeVarInt(elapsed);
            buf.writeVarInt(remaining);
        }
    }

    public static SyncCastPacket decode(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return new SyncCastPacket(null, 0, 0, 0);
        }
        return new SyncCastPacket(buf.readResourceLocation(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientMagicData.setCast(spell, level, elapsed, remaining));
        ctx.get().setPacketHandled(true);
    }
}
