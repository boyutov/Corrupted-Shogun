package com.kushtarbek.shogun.magic.client;

import com.kushtarbek.shogun.SamuraiMod;
import com.kushtarbek.shogun.magic.registry.MagicEntities;
import com.kushtarbek.shogun.magic.item.SpellScrollItem;
import com.kushtarbek.shogun.magic.item.SpellSlot;
import com.kushtarbek.shogun.magic.registry.MagicItems;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public final class MagicClientSetup {

    @Mod.EventBusSubscriber(modid = SamuraiMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {

        @SubscribeEvent
        public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
            event.enqueueWork(() -> net.minecraft.client.gui.screens.MenuScreens.register(
                    com.kushtarbek.shogun.magic.registry.MagicMenus.BOOK_EQUIPMENT.get(), BookEquipmentScreen::new));
        }

        @SubscribeEvent
        public static void registerOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "mana_bar", ManaHudOverlay.INSTANCE);
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "spell_ring", SpellRingOverlay.INSTANCE);
            // After the item name so leftHeight/rightHeight are final and the bar can sit above it.
            event.registerAbove(VanillaGuiOverlay.ITEM_NAME.id(), "cast_bar", CastBarOverlay.INSTANCE);
        }

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(MagicEntities.METEOR.get(), MeteorRenderer::new);
            event.registerEntityRenderer(MagicEntities.BLACK_HOLE.get(), BlackHoleRenderer::new);
        }

        /** Tints the scroll's wax seal (model layer 1) with the spell's school colour. */
        @SubscribeEvent
        public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
            event.register((stack, tintIndex) -> {
                SpellSlot slot = tintIndex == 1 ? SpellScrollItem.getSpell(stack) : null;
                return slot == null ? -1 : slot.spell().getSchool().getColor();
            }, MagicItems.SPELL_SCROLL.get());
        }

        @SubscribeEvent
        public static void registerItemDecorations(RegisterItemDecorationsEvent event) {
            event.register(MagicItems.SPELL_SCROLL.get(), ScrollCooldownDecorator.INSTANCE);
        }

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(MagicKeys.OPEN_SPELL_MENU);
            event.register(MagicKeys.PREVIOUS_SPELL);
            event.register(MagicKeys.NEXT_SPELL);
            event.register(MagicKeys.CAST_EQUIPPED_BOOK);
        }

        private ModBus() {
        }
    }

    @Mod.EventBusSubscriber(modid = SamuraiMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class ForgeBus {

        @SubscribeEvent
        public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientMagicData.reset();
            MagicKeys.reset();
            MeteorPlayerAnimation.reset();
            ClientLaunch.stop();
        }

        @SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.START) {
                // Before the player moves this tick.
                ClientLaunch.tick(Minecraft.getInstance());
            } else {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && !mc.isPaused()) {
                    ClientMagicData.tick();
                    MeteorPlayerAnimation.tick();
                }
                MagicKeys.handleInput(mc);
            }
        }

        private ForgeBus() {
        }
    }

    private MagicClientSetup() {
    }
}
