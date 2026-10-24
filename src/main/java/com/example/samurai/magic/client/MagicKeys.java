package com.example.samurai.magic.client;

import com.example.samurai.SamuraiMod;
import com.example.samurai.magic.item.SpellBookItem;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Rebindable in Options → Controls, under the mod's own category. */
public final class MagicKeys {

    public static final String CATEGORY = "key.categories." + SamuraiMod.MOD_ID;

    public static final KeyMapping OPEN_SPELL_MENU = new KeyMapping(
            "key." + SamuraiMod.MOD_ID + ".spell_menu",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            CATEGORY);

    /** Client tick: R with a book in either hand opens its menu. */
    static void handleInput(Minecraft mc) {
        while (OPEN_SPELL_MENU.consumeClick()) {
            LocalPlayer player = mc.player;
            if (player == null || mc.screen != null) {
                continue;
            }
            for (InteractionHand hand : InteractionHand.values()) {
                if (player.getItemInHand(hand).getItem() instanceof SpellBookItem) {
                    mc.setScreen(new SpellBookScreen(hand));
                    return;
                }
            }
            player.displayClientMessage(Component.translatable("message." + SamuraiMod.MOD_ID + ".no_book"), true);
        }
    }

    private MagicKeys() {
    }
}
