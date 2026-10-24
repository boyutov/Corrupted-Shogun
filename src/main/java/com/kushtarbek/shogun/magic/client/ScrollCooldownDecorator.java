package com.kushtarbek.shogun.magic.client;

import com.kushtarbek.shogun.magic.item.SpellScrollItem;
import com.kushtarbek.shogun.magic.item.SpellSlot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.IItemDecorator;

/**
 * Vanilla-style cooldown shade on scroll icons. Vanilla's own ItemCooldowns is
 * keyed by Item, and every scroll is the same Item, so it would shade all
 * scrolls at once; this one looks up the stack's own spell instead.
 */
public final class ScrollCooldownDecorator implements IItemDecorator {

    public static final ScrollCooldownDecorator INSTANCE = new ScrollCooldownDecorator();

    @Override
    public boolean render(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        SpellSlot slot = SpellScrollItem.getSpell(stack);
        if (slot == null) {
            return false;
        }
        float percent = ClientMagicData.getCooldowns().getPercent(slot.spell());
        if (percent <= 0f) {
            return false;
        }
        // Same geometry and colour as GuiGraphics#renderItemDecorations.
        int top = y + Mth.floor(16f * (1f - percent));
        int bottom = top + Mth.ceil(16f * percent);
        graphics.fill(RenderType.guiOverlay(), x, top, x + 16, bottom, Integer.MAX_VALUE);
        return true;
    }

    private ScrollCooldownDecorator() {
    }
}
