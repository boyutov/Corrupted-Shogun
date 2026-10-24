package com.kushtarbek.shogun.magic.client;

import com.kushtarbek.shogun.SamuraiMod;
import com.kushtarbek.shogun.magic.menu.BookSlot;
import com.kushtarbek.shogun.magic.network.CreativeBookPacket;
import com.kushtarbek.shogun.magic.network.MagicNetwork;
import com.kushtarbek.shogun.magic.registry.MagicItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;

/** Draws the spellbook slot's frame in both vanilla inventories and places it in the creative tab. */
@Mod.EventBusSubscriber(modid = SamuraiMod.MOD_ID, value = Dist.CLIENT)
public final class BookSlotScreen {
    /** Creative inventory tab: right of the armour, mirroring the offhand slot on the left. */
    private static final int CREATIVE_X = 127, CREATIVE_Y = 20;
    private static final Constructor<?> CREATIVE_WRAPPER;

    static {
        try {
            // Package-private; the creative screen casts every inventory-tab slot to it on click.
            CREATIVE_WRAPPER = Class.forName(CreativeModeInventoryScreen.class.getName() + "$SlotWrapper")
                    .getDeclaredConstructor(Slot.class, int.class, int.class, int.class);
            CREATIVE_WRAPPER.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Mirrors creative-tab edits of the book slot to the server; vanilla's listener skips it. */
    private static final ContainerListener CREATIVE_SYNC = new ContainerListener() {
        @Override
        public void slotChanged(AbstractContainerMenu menu, int index, ItemStack stack) {
            if (index < menu.slots.size() && menu.slots.get(index) instanceof BookSlot)
                MagicNetwork.CHANNEL.sendToServer(new CreativeBookPacket(stack.copy()));
        }
        @Override
        public void dataChanged(AbstractContainerMenu menu, int id, int value) { }
    };

    @SubscribeEvent
    public static void opened(ScreenEvent.Init.Post event) {
        var player = Minecraft.getInstance().player;
        if (event.getScreen() instanceof CreativeModeInventoryScreen && player != null) {
            player.inventoryMenu.removeSlotListener(CREATIVE_SYNC);
            player.inventoryMenu.addSlotListener(CREATIVE_SYNC);
        }
    }

    @SubscribeEvent
    public static void closed(ScreenEvent.Closing event) {
        var player = Minecraft.getInstance().player;
        if (event.getScreen() instanceof CreativeModeInventoryScreen && player != null)
            player.inventoryMenu.removeSlotListener(CREATIVE_SYNC);
    }

    @SubscribeEvent
    public static void beforeRender(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen) || !screen.isInventoryOpen()) return;
        // The creative tab rebuilds its wrappers on every tab switch, laying unknown slots over the hotbar.
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        var slots = screen.getMenu().slots;
        for (int i = 0; i < slots.size() && i < player.inventoryMenu.slots.size(); i++) {
            Slot target = player.inventoryMenu.slots.get(i);
            Slot shown = slots.get(i);
            if (!(target instanceof BookSlot) || shown.x == CREATIVE_X && shown.y == CREATIVE_Y) continue;
            try {
                Slot moved = (Slot) CREATIVE_WRAPPER.newInstance(target, i, CREATIVE_X, CREATIVE_Y);
                moved.index = shown.index;
                slots.set(i, moved);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @SubscribeEvent
    public static void background(ContainerScreenEvent.Render.Background event) {
        Slot slot = bookSlot(event.getContainerScreen());
        if (slot == null) return;
        GuiGraphics g = event.getGuiGraphics();
        int x = event.getContainerScreen().getGuiLeft() + slot.x, y = event.getContainerScreen().getGuiTop() + slot.y;
        // The vanilla bevelled slot: dark top-left, light bottom-right, grey face.
        g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF8B8B8B);
        g.fill(x - 1, y - 1, x + 16, y, 0xFF373737);
        g.fill(x - 1, y - 1, x, y + 16, 0xFF373737);
        g.fill(x, y + 16, x + 17, y + 17, 0xFFFFFFFF);
        g.fill(x + 16, y, x + 17, y + 17, 0xFFFFFFFF);
        if (!slot.hasItem()) {
            g.renderFakeItem(new ItemStack(MagicItems.SPELL_BOOK.get()), x, y);
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.fill(x, y, x + 16, y + 16, 0xB08B8B8B);
            g.pose().popPose();
        }
    }

    @SubscribeEvent
    public static void afterRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        Slot hovered = screen.getSlotUnderMouse();
        if (hovered != null && hovered == bookSlot(screen) && !hovered.hasItem() && screen.getMenu().getCarried().isEmpty()) {
            event.getGuiGraphics().renderTooltip(Minecraft.getInstance().font,
                    Component.translatable("screen.samuraiboss.equipment.tab_hint"), event.getMouseX(), event.getMouseY());
        }
    }

    /** The book slot as this screen shows it; the creative tab wraps it, sharing its container. */
    @Nullable
    private static Slot bookSlot(AbstractContainerScreen<?> screen) {
        var player = Minecraft.getInstance().player;
        if (player == null || screen instanceof CreativeModeInventoryScreen creative && !creative.isInventoryOpen()
                || !(screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen)) return null;
        Slot book = null;
        for (Slot s : player.inventoryMenu.slots) if (s instanceof BookSlot) book = s;
        if (book == null) return null;
        for (Slot s : screen.getMenu().slots) if (s.container == book.container) return s;
        return null;
    }

    private BookSlotScreen() { }
}
