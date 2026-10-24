package com.example.samurai.magic.data;

import com.example.samurai.SamuraiMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SamuraiMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MagicDataEvents {

    private static final ResourceLocation KEY = new ResourceLocation(SamuraiMod.MOD_ID, "magic_data");

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player player) {
            MagicDataProvider provider = new MagicDataProvider(player);
            event.addCapability(KEY, provider);
            event.addListener(provider::invalidate);
        }
    }

    /**
     * Fired both on death and on leaving the End. The new player object starts
     * with a blank capability, so copy everything over or mana resets to full.
     */
    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        original.reviveCaps();
        MagicData.get(original).ifPresent(oldData ->
                MagicData.get(event.getEntity()).ifPresent(newData -> newData.copyFrom(oldData)));
        original.invalidateCaps();
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide) {
            MagicData.get(event.player).ifPresent(MagicData::tick);
        }
    }

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        MagicData.get(event.getEntity()).ifPresent(MagicData::sync);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        MagicData.get(event.getEntity()).ifPresent(MagicData::sync);
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        MagicData.get(event.getEntity()).ifPresent(MagicData::sync);
    }

    private MagicDataEvents() {
    }
}
