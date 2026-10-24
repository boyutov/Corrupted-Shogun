package com.example.samurai.magic.client;

import com.example.samurai.SamuraiMod;
import com.example.samurai.magic.item.SpellBookItem;
import com.example.samurai.magic.item.BookLocation;
import com.example.samurai.magic.network.BookActionPacket;
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

/** Rebindable in Options → Controls, under the mod's own category. Hold the menu key for the spell wheel. */
public final class MagicKeys {

    public static final String CATEGORY = "key.categories." + SamuraiMod.MOD_ID;

    public static final KeyMapping OPEN_SPELL_MENU = key("spell_menu", GLFW.GLFW_KEY_R);
    public static final KeyMapping PREVIOUS_SPELL = key("previous_spell", GLFW.GLFW_KEY_Z);
    public static final KeyMapping NEXT_SPELL = key("next_spell", GLFW.GLFW_KEY_X);

    public static final KeyMapping CAST_EQUIPPED_BOOK = key("cast_equipped_book", GLFW.GLFW_KEY_V);
    private static boolean bookHeld;
    private static int heartbeat;

    static void reset() { bookHeld = false; heartbeat = 0; }

    private static void send(BookActionPacket.Action action) {
        MagicNetwork.CHANNEL.sendToServer(new BookActionPacket(action));
    }

    private static KeyMapping key(String name, int defaultKey) {
        return new KeyMapping("key." + SamuraiMod.MOD_ID + "." + name, KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, defaultKey, CATEGORY);
    }

    static void handleInput(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) { reset(); return; }
        boolean canCast = mc.screen == null && mc.isWindowActive() && player.isAlive() && !player.isSpectator();
        boolean down = canCast && CAST_EQUIPPED_BOOK.isDown();
        boolean pressed = false;
        while (CAST_EQUIPPED_BOOK.consumeClick()) pressed = true;
        if (bookHeld && !down) { send(BookActionPacket.Action.RELEASE); bookHeld = false; }
        if (!bookHeld && down && pressed) {
            send(BookActionPacket.Action.START);
            bookHeld = true;
            heartbeat = 0;
        }
        if (bookHeld && ++heartbeat >= 10) { send(BookActionPacket.Action.HEARTBEAT); heartbeat = 0; }
        while (OPEN_SPELL_MENU.consumeClick()) {
            if (player == null || mc.screen != null) {
                continue;
            }
            BookLocation location = BookLocation.preferred(player);
            if (location != null) {
                mc.setScreen(new SpellWheelScreen(location));
            } else {
                player.displayClientMessage(Component.translatable("message." + SamuraiMod.MOD_ID + ".no_book"), true);
            }
        }
        while (NEXT_SPELL.consumeClick()) {
            if (mc.screen == null) cycle(player, 1);
        }
        while (PREVIOUS_SPELL.consumeClick()) {
            if (mc.screen == null) cycle(player, -1);
        }
    }

    /** Moves the active slot of the held book to the next filled slot in the given direction, wrapping. */
    private static void cycle(LocalPlayer player, int direction) {
        if (player == null) {
            return;
        }
        BookLocation location = BookLocation.preferred(player);
        if (location == null) {
            player.displayClientMessage(Component.translatable("message." + SamuraiMod.MOD_ID + ".no_book"), true);
            return;
        }
        ItemStack stack = location.get(player);
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
                MagicNetwork.CHANNEL.sendToServer(new SelectSpellPacket(location, index));
                player.displayClientMessage(ClientSpellInfo.labelWithCooldown(slots.get(index)), true);
                return;
            }
        }
    }

    private MagicKeys() {
    }
}
