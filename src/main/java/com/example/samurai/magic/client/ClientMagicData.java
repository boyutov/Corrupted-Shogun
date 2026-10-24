package com.example.samurai.magic.client;

import com.example.samurai.magic.registry.MagicAttributes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Client-side mirror of the local player's magic state. Kept static rather than
 * in the client player's capability because the LocalPlayer object is replaced
 * on respawn and dimension change, and sync packets can race that swap.
 */
public final class ClientMagicData {

    private static float mana;

    public static float getMana() {
        return mana;
    }

    public static void setMana(float value) {
        mana = value;
    }

    public static float getMaxMana() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? 0f : (float) player.getAttributeValue(MagicAttributes.MAX_MANA.get());
    }

    public static void reset() {
        mana = 0f;
    }

    private ClientMagicData() {
    }
}
