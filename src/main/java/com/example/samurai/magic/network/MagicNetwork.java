package com.example.samurai.magic.network;

import com.example.samurai.SamuraiMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class MagicNetwork {

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SamuraiMod.MOD_ID, "magic"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static int nextId;

    public static void register() {
        CHANNEL.messageBuilder(SyncManaPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncManaPacket::encode)
                .decoder(SyncManaPacket::decode)
                .consumerMainThread(SyncManaPacket::handle)
                .add();
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    private MagicNetwork() {
    }
}
