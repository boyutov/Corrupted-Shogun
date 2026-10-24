package com.example.samurai.magic.client;

import com.example.samurai.SamuraiMod;
import com.example.samurai.magic.item.SpellBookItem;
import com.example.samurai.magic.item.SpellSlot;
import com.example.samurai.magic.network.MagicNetwork;
import com.example.samurai.magic.network.SelectSpellPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists the slots of the held book with each spell's cooldown; clicking a
 * filled slot makes it active.
 */
public class SpellBookScreen extends Screen {

    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SPACING = 4;

    private final InteractionHand hand;
    private final List<Button> slotButtons = new ArrayList<>();

    public SpellBookScreen(InteractionHand hand) {
        super(Component.translatable("screen." + SamuraiMod.MOD_ID + ".spell_book"));
        this.hand = hand;
    }

    private ItemStack book() {
        return minecraft == null || minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getItemInHand(hand);
    }

    @Override
    protected void init() {
        slotButtons.clear();
        int count = SpellBookItem.getSlotCount(book());
        int x = (width - BUTTON_WIDTH) / 2;
        int top = (height - count * (BUTTON_HEIGHT + SPACING)) / 2;
        for (int i = 0; i < count; i++) {
            int index = i;
            Button button = Button.builder(Component.empty(), b -> select(index))
                    .bounds(x, top + i * (BUTTON_HEIGHT + SPACING), BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build();
            slotButtons.add(addRenderableWidget(button));
        }
        refreshLabels();
    }

    /** Called every tick so cooldown counters run down while the screen is open. */
    private void refreshLabels() {
        ItemStack stack = book();
        List<SpellSlot> slots = SpellBookItem.getSlots(stack);
        int active = SpellBookItem.getEffectiveActiveIndex(stack);
        for (int i = 0; i < slotButtons.size() && i < slots.size(); i++) {
            SpellSlot slot = slots.get(i);
            Button button = slotButtons.get(i);
            if (slot == null) {
                button.setMessage(Component.translatable("screen." + SamuraiMod.MOD_ID + ".spell_book.empty")
                        .withStyle(ChatFormatting.DARK_GRAY));
                button.active = false;
            } else {
                button.setMessage(Component.literal(i == active ? "▶ " : "")
                        .append(ClientSpellInfo.labelWithCooldown(slot)));
                button.active = true;
            }
        }
    }

    private void select(int index) {
        // Apply locally for instant feedback; the server's copy syncs back anyway.
        if (SpellBookItem.setActiveIndex(book(), index)) {
            MagicNetwork.CHANNEL.sendToServer(new SelectSpellPacket(hand, index));
        }
        refreshLabels();
    }

    @Override
    public void tick() {
        if (!(book().getItem() instanceof SpellBookItem)) {
            onClose();
            return;
        }
        refreshLabels();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (MagicKeys.OPEN_SPELL_MENU.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int top = (height - slotButtons.size() * (BUTTON_HEIGHT + SPACING)) / 2;
        graphics.drawCenteredString(font, title, width / 2, top - 16, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
