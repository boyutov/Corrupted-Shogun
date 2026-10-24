package com.example.samurai.magic.client;
import com.example.samurai.magic.menu.BookEquipmentMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
public class BookEquipmentScreen extends AbstractContainerScreen<BookEquipmentMenu> {
    public BookEquipmentScreen(BookEquipmentMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176; imageHeight = 158; inventoryLabelY = 64;
    }
    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF21182E);
        g.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 2, 0xFFCCA564);
        for (var slot : menu.slots) {
            int x = leftPos + slot.x, y = topPos + slot.y;
            g.fill(x - 1, y - 1, x + 17, y + 17, slot.index == 0 ? 0xFFCCA564 : 0xFF65546F);
            g.fill(x, y, x + 16, y + 16, 0xFF100D17);
        }
        g.drawCenteredString(font, Component.translatable("screen.samuraiboss.equipment.hint"),
                leftPos + imageWidth / 2, topPos + 51, 0xDDC8F3);
    }
    @Override protected void renderLabels(GuiGraphics g, int x, int y) {
        g.drawString(font, title, 8, 6, 0xFCE296, false);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0xDDC8F3, false);
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g); super.render(g, mx, my, partial); renderTooltip(g, mx, my);
    }
}
