package com.kushtarbek.shogun.magic.network;

import com.kushtarbek.shogun.magic.client.ClientLaunch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> owning client: fly the local player along a smooth arc from {@code start}
 * by {@code offset} over {@code duration} ticks. Player movement is client-side, so the
 * client steers itself against its own exact position; per-tick velocity pushes from
 * the server lose height whenever packets arrive late.
 */
public record LaunchPacket(Vec3 start, Vec3 offset, int duration) {

    /** Ends a flight in progress; the player just falls from where they are. */
    public static LaunchPacket stop() {
        return new LaunchPacket(Vec3.ZERO, Vec3.ZERO, 0);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(start.x);
        buf.writeDouble(start.y);
        buf.writeDouble(start.z);
        buf.writeDouble(offset.x);
        buf.writeDouble(offset.y);
        buf.writeDouble(offset.z);
        buf.writeVarInt(duration);
    }

    public static LaunchPacket decode(FriendlyByteBuf buf) {
        return new LaunchPacket(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientLaunch.start(start, offset, duration));
        ctx.get().setPacketHandled(true);
    }
}
