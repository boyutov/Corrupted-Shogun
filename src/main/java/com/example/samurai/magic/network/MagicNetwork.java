package com.example.samurai.magic.network;

import com.example.samurai.SamuraiMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class MagicNetwork {

    private static final String PROTOCOL = "7";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SamuraiMod.MOD_ID, "magic"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static int nextId;

    public static void register() {
        CHANNEL.messageBuilder(MeteorGesturePacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MeteorGesturePacket::encode).decoder(MeteorGesturePacket::decode)
                .consumerMainThread(MeteorGesturePacket::handle).add();
        CHANNEL.messageBuilder(BookActionPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BookActionPacket::encode).decoder(BookActionPacket::decode).consumerMainThread(BookActionPacket::handle).add();
        CHANNEL.messageBuilder(SyncBookPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncBookPacket::encode).decoder(SyncBookPacket::decode).consumerMainThread(SyncBookPacket::handle).add();
        CHANNEL.messageBuilder(SyncManaPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncManaPacket::encode)
                .decoder(SyncManaPacket::decode)
                .consumerMainThread(SyncManaPacket::handle)
                .add();
        CHANNEL.messageBuilder(SyncCastPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncCastPacket::encode)
                .decoder(SyncCastPacket::decode)
                .consumerMainThread(SyncCastPacket::handle)
                .add();
        CHANNEL.messageBuilder(SyncCooldownsPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncCooldownsPacket::encode)
                .decoder(SyncCooldownsPacket::decode)
                .consumerMainThread(SyncCooldownsPacket::handle)
                .add();
        CHANNEL.messageBuilder(LaunchPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LaunchPacket::encode)
                .decoder(LaunchPacket::decode)
                .consumerMainThread(LaunchPacket::handle)
                .add();
        CHANNEL.messageBuilder(AddVelocityPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AddVelocityPacket::encode)
                .decoder(AddVelocityPacket::decode)
                .consumerMainThread(AddVelocityPacket::handle)
                .add();
        CHANNEL.messageBuilder(CreativeBookPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CreativeBookPacket::encode).decoder(CreativeBookPacket::decode)
                .consumerMainThread(CreativeBookPacket::handle).add();
        CHANNEL.messageBuilder(SelectSpellPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SelectSpellPacket::encode)
                .decoder(SelectSpellPacket::decode)
                .consumerMainThread(SelectSpellPacket::handle)
                .add();
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    private MagicNetwork() {
    }
}
