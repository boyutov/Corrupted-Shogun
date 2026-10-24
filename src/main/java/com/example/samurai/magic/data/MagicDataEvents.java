package com.example.samurai.magic.data;

import com.example.samurai.SamuraiMod;
import com.example.samurai.magic.casting.SpellCasting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
            // No invalidate listener on purpose: PlayerEvent.Clone needs reviveCaps() to bring the
            // old player's data back, and a LazyOptional invalidated by a listener stays dead.
            // Forge already hides the capability while the provider is invalid.
            event.addCapability(KEY, new MagicDataProvider(player));
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
                MagicData.get(event.getEntity()).ifPresent(newData -> {
                    newData.copyFrom(oldData);
                    if (event.isWasDeath()) {
                        newData.clearFallImmunity();
                    }
                }));
        original.invalidateCaps();
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            MagicData.get(player).ifPresent(data -> {
                data.tick();
                SpellCasting.tick(player, data);
            });
        }
    }

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        MagicData.get(event.getEntity()).ifPresent(MagicData::syncAll);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        MagicData.get(event.getEntity()).ifPresent(MagicData::syncAll);
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        MagicData.get(event.getEntity()).ifPresent(MagicData::syncAll);
    }

    private MagicDataEvents() {
    }
}
