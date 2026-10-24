package com.example.samurai.magic.client;

import com.example.samurai.SamuraiMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public final class MagicClientSetup {

    @Mod.EventBusSubscriber(modid = SamuraiMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {

        @SubscribeEvent
        public static void registerOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "mana_bar", ManaHudOverlay.INSTANCE);
        }

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(MagicKeys.OPEN_SPELL_MENU);
        }

        private ModBus() {
        }
    }

    @Mod.EventBusSubscriber(modid = SamuraiMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class ForgeBus {

        @SubscribeEvent
        public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientMagicData.reset();
        }

        @SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && !mc.isPaused()) {
                    ClientMagicData.tick();
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
