package com.example.samurai.magic.client;

import com.example.samurai.magic.spell.CastType;
import com.example.samurai.magic.spell.Spell;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Cast progress, centred above the selected-item name. LONG fills up as it
 * charges; CONTINUOUS drains as the channel runs out.
 */
public final class CastBarOverlay implements IGuiOverlay {

    public static final CastBarOverlay INSTANCE = new CastBarOverlay();

    private static final int BAR_WIDTH = 101;
    private static final int BAR_HEIGHT = 5;
    /** Gap between the bar and the item name below it. */
    private static final int GAP = 4;
    /** Vanilla's minimum lift of the item name above the screen bottom. */
    private static final int ITEM_NAME_MIN_SHIFT = 59;
    private static final int ITEM_NAME_CREATIVE_DROP = 14;

    private static final int COLOR_BORDER = 0xFF0B0B1A;
    private static final int COLOR_BACKGROUND = 0xFF1E1E3A;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = gui.getMinecraft();
        if (mc.options.hideGui || mc.player == null || !ClientMagicData.isCasting()) {
            return;
        }
        Spell spell = ClientMagicData.getCastingSpell();
        if (spell == null) {
            return;
        }

        // Same formula vanilla uses for the item name, so the bar always clears it.
        int itemNameY = screenHeight - Math.max(Math.max(gui.leftHeight, gui.rightHeight), ITEM_NAME_MIN_SHIFT);
        if (mc.gameMode != null && !mc.gameMode.canHurtPlayer()) {
            itemNameY += ITEM_NAME_CREATIVE_DROP;
        }
        int x = (screenWidth - BAR_WIDTH) / 2;
        int y = itemNameY - GAP - BAR_HEIGHT;

        float progress = ClientMagicData.getCastProgress(partialTick);
        float fill = spell.getCastType() == CastType.CONTINUOUS ? 1f - progress : progress;
        int filled = Math.round((BAR_WIDTH - 2) * fill);
        int color = 0xFF000000 | spell.getSchool().getColor();

        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, COLOR_BORDER);
        graphics.fill(x + 1, y + 1, x + BAR_WIDTH - 1, y + BAR_HEIGHT - 1, COLOR_BACKGROUND);
        if (filled > 0) {
            graphics.fill(x + 1, y + 1, x + 1 + filled, y + BAR_HEIGHT - 1, color);
        }

        Font font = mc.font;
        Component name = spell.getDisplayName();
        graphics.drawString(font, name, (screenWidth - font.width(name)) / 2, y - font.lineHeight - 1, color, true);
    }

    private CastBarOverlay() {
    }
}
