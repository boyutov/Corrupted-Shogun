package com.example.samurai.magic.client;

import com.example.samurai.SamuraiMod;
import com.example.samurai.magic.item.SpellBookItem;
import com.example.samurai.magic.item.SpellSlot;
import com.example.samurai.magic.network.MagicNetwork;
import com.example.samurai.magic.network.SelectSpellPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Rebindable in Options → Controls, under the mod's own category. */
public final class MagicKeys {

    public static final String CATEGORY = "key.categories." + SamuraiMod.MOD_ID;

    public static final KeyMapping OPEN_SPELL_MENU = key("spell_menu", GLFW.GLFW_KEY_R);
    public static final KeyMapping PREVIOUS_SPELL = key("previous_spell", GLFW.GLFW_KEY_Z);
    public static final KeyMapping NEXT_SPELL = key("next_spell", GLFW.GLFW_KEY_X);

    private static KeyMapping key(String name, int defaultKey) {
        return new KeyMapping("key." + SamuraiMod.MOD_ID + "." + name, KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, defaultKey, CATEGORY);
    }

    static void handleInput(Minecraft mc) {
        LocalPlayer player = mc.player;
        while (OPEN_SPELL_MENU.consumeClick()) {
            if (player == null || mc.screen != null) {
                continue;
            }
            InteractionHand hand = ClientSpellInfo.bookHand(player);
            if (hand != null) {
                mc.setScreen(new SpellBookScreen(hand));
            } else {
                player.displayClientMessage(Component.translatable("message." + SamuraiMod.MOD_ID + ".no_book"), true);
            }
        }
        while (NEXT_SPELL.consumeClick()) {
            cycle(player, 1);
        }
        while (PREVIOUS_SPELL.consumeClick()) {
            cycle(player, -1);
        }
    }

    /** Moves the active slot of the held book to the next filled slot in the given direction, wrapping. */
    private static void cycle(LocalPlayer player, int direction) {
        if (player == null) {
            return;
        }
        InteractionHand hand = ClientSpellInfo.bookHand(player);
        if (hand == null) {
            player.displayClientMessage(Component.translatable("message." + SamuraiMod.MOD_ID + ".no_book"), true);
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        List<SpellSlot> slots = SpellBookItem.getSlots(stack);
        int current = SpellBookItem.getEffectiveActiveIndex(stack);
        if (current < 0) {
            return;
        }
        int size = slots.size();
        for (int step = 1; step < size; step++) {
            int index = Math.floorMod(current + direction * step, size);
            if (slots.get(index) != null) {
                SpellBookItem.setActiveIndex(stack, index);
                MagicNetwork.CHANNEL.sendToServer(new SelectSpellPacket(hand, index));
                player.displayClientMessage(ClientSpellInfo.labelWithCooldown(slots.get(index)), true);
                return;
            }
        }
    }

    private MagicKeys() {
    }
}
