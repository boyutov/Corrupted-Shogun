package com.example.samurai.magic.network;

import com.example.samurai.magic.client.MeteorPlayerAnimation;
import com.example.samurai.magic.data.MeteorGesture;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

/** Sent to the caster and everyone tracking them, including late-arriving observers. */
public record MeteorGesturePacket(UUID player, ResourceLocation dimension, MeteorGesture gesture) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(player);
        buf.writeResourceLocation(dimension);
        buf.writeLong(gesture.startTick());
        buf.writeVarInt(gesture.duration());
        buf.writeFloat(gesture.from());
        buf.writeFloat(gesture.to());
    }

    public static MeteorGesturePacket decode(FriendlyByteBuf buf) {
        return new MeteorGesturePacket(buf.readUUID(), buf.readResourceLocation(),
                new MeteorGesture(buf.readLong(), buf.readVarInt(), buf.readFloat(), buf.readFloat()));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MeteorPlayerAnimation.receive(this));
        context.get().setPacketHandled(true);
    }
}
