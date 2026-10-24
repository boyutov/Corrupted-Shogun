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

import java.util.List;

/** Lists the slots of the held book; clicking a filled slot makes it active. */
public class SpellBookScreen extends Screen {

    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SPACING = 4;

    private final InteractionHand hand;

    public SpellBookScreen(InteractionHand hand) {
        super(Component.translatable("screen." + SamuraiMod.MOD_ID + ".spell_book"));
        this.hand = hand;
    }

    private ItemStack book() {
        return minecraft == null || minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getItemInHand(hand);
    }

    @Override
    protected void init() {
        ItemStack stack = book();
        List<SpellSlot> slots = SpellBookItem.getSlots(stack);
        int active = SpellBookItem.getEffectiveActiveIndex(stack);

        int x = (width - BUTTON_WIDTH) / 2;
        int top = (height - slots.size() * (BUTTON_HEIGHT + SPACING)) / 2;
        for (int i = 0; i < slots.size(); i++) {
            SpellSlot slot = slots.get(i);
            int index = i;
            Component label;
            if (slot == null) {
                label = Component.translatable("screen." + SamuraiMod.MOD_ID + ".spell_book.empty")
                        .withStyle(ChatFormatting.DARK_GRAY);
            } else {
                label = Component.literal(i == active ? "▶ " : "").append(slot.getDisplayName());
            }
            Button button = Button.builder(label, b -> select(index))
                    .bounds(x, top + i * (BUTTON_HEIGHT + SPACING), BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build();
            button.active = slot != null;
            addRenderableWidget(button);
        }
    }

    private void select(int index) {
        ItemStack stack = book();
        // Apply locally for instant feedback; the server's copy syncs back anyway.
        if (SpellBookItem.setActiveIndex(stack, index)) {
            MagicNetwork.CHANNEL.sendToServer(new SelectSpellPacket(hand, index));
        }
        rebuildWidgets();
    }

    @Override
    public void tick() {
        if (!(book().getItem() instanceof SpellBookItem)) {
            onClose();
        }
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
        int top = (height - SpellBookItem.getSlotCount(book()) * (BUTTON_HEIGHT + SPACING)) / 2;
        graphics.drawCenteredString(font, title, width / 2, top - 16, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
