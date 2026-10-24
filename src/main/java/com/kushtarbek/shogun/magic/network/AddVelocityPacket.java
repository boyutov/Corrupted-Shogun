package com.kushtarbek.shogun.magic.network;

import com.kushtarbek.shogun.magic.client.ClientMotion;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: add this to the local player's velocity. Additive on purpose: the
 * player's own movement still counts, so a pull can be fought. (Vanilla's motion packet
 * replaces the velocity, which would pin the player.)
 */
public record AddVelocityPacket(Vec3 delta) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat((float) delta.x);
        buf.writeFloat((float) delta.y);
        buf.writeFloat((float) delta.z);
    }

    public static AddVelocityPacket decode(FriendlyByteBuf buf) {
        return new AddVelocityPacket(new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientMotion.addVelocity(delta));
        ctx.get().setPacketHandled(true);
    }
}
