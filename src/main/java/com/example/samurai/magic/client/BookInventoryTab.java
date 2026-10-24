package com.example.samurai.magic.client;

import com.example.samurai.SamuraiMod;
import com.example.samurai.magic.network.BookActionPacket;
import com.example.samurai.magic.network.MagicNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Adds an equipment tab to both vanilla inventories, so it follows the inventory key binding. */
@Mod.EventBusSubscriber(modid = SamuraiMod.MOD_ID, value = Dist.CLIENT)
public final class BookInventoryTab {
    @SubscribeEvent
    public static void init(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen || event.getScreen() instanceof CreativeModeInventoryScreen)) return;
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) event.getScreen();
        // Inside the lower-right inventory margin, below the hotbar, away from the recipe button and creative tabs.
        int x = Math.min(screen.width - 54, screen.getGuiLeft() + screen.getXSize() - 52);
        int y = Math.min(screen.height - 21, screen.getGuiTop() + screen.getYSize() + 3);
        event.addListener(Button.builder(Component.translatable("screen.samuraiboss.equipment.tab"), button -> {
            Minecraft.getInstance().setScreen(null);
            MagicNetwork.CHANNEL.sendToServer(new BookActionPacket(BookActionPacket.Action.OPEN));
        }).bounds(x, y, 52, 18).tooltip(Tooltip.create(Component.translatable("screen.samuraiboss.equipment.tab_hint"))).build());
    }
    private BookInventoryTab() { }
}
