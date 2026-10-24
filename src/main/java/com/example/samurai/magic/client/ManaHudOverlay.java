package com.example.samurai.magic.client;

import com.example.samurai.magic.item.SpellSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Mana bar to the right of the hotbar, with the held spell's name and cooldown
 * above it. Drawn with plain fills — no textures, so there is nothing to ship or license.
 */
public final class ManaHudOverlay implements IGuiOverlay {

    public static final ManaHudOverlay INSTANCE = new ManaHudOverlay();

    private static final int BAR_WIDTH = 81;
    private static final int BAR_HEIGHT = 5;

    private static final int COLOR_BORDER = 0xFF0B0B1A;
    private static final int COLOR_BACKGROUND = 0xFF1E1E3A;
    private static final int COLOR_FILL = 0xFF3C7DFF;
    private static final int COLOR_FILL_HIGHLIGHT = 0xFF8DB4FF;
    private static final int COLOR_TEXT = 0xFF9CC0FF;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = gui.getMinecraft();
        if (mc.options.hideGui || mc.player == null || mc.player.isSpectator()) {
            return;
        }
        float max = ClientMagicData.getMaxMana();
        if (max <= 0f) {
            return;
        }
        float mana = Mth.clamp(ClientMagicData.getMana(), 0f, max);

        int x = screenWidth / 2 + 91 + 8;
        int y = screenHeight - BAR_HEIGHT - 4;
        int filled = Math.round((BAR_WIDTH - 2) * (mana / max));

        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, COLOR_BORDER);
        graphics.fill(x + 1, y + 1, x + BAR_WIDTH - 1, y + BAR_HEIGHT - 1, COLOR_BACKGROUND);
        if (filled > 0) {
            graphics.fill(x + 1, y + 1, x + 1 + filled, y + BAR_HEIGHT - 1, COLOR_FILL);
            graphics.fill(x + 1, y + 1, x + 1 + filled, y + 2, COLOR_FILL_HIGHLIGHT);
        }

        Font font = mc.font;
        String text = (int) mana + " / " + (int) max;
        int textY = y - font.lineHeight;
        graphics.drawString(font, text, x + (BAR_WIDTH - font.width(text)) / 2, textY, COLOR_TEXT, true);

        SpellSlot held = ClientSpellInfo.heldSpell(mc.player);
        if (held != null) {
            Component label = ClientSpellInfo.labelWithCooldown(held);
            graphics.drawString(font, label, x, textY - font.lineHeight - 1, 0xFFFFFF, true);
        }
    }

    private ManaHudOverlay() {
    }
}
