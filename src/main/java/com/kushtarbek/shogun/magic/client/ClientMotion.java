package com.kushtarbek.shogun.magic.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class ClientMotion {

    public static void addVelocity(Vec3 delta) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.setDeltaMovement(player.getDeltaMovement().add(delta));
        }
    }

    private ClientMotion() {
    }
}
