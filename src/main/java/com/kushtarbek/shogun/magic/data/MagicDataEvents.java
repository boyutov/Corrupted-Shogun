package com.kushtarbek.shogun.magic.data;

import com.kushtarbek.shogun.SamuraiMod;
import com.kushtarbek.shogun.magic.casting.SpellCasting;
import com.kushtarbek.shogun.magic.registry.MagicAttributes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
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
        // Attribute base values are saved with the player, so a player from before the
        // default went up still carries the old 100; move them to the new default once.
        AttributeInstance maxMana = event.getEntity().getAttribute(MagicAttributes.MAX_MANA.get());
        if (maxMana != null && maxMana.getBaseValue() == MagicAttributes.OLD_DEFAULT_MAX_MANA) {
            maxMana.setBaseValue(MagicAttributes.DEFAULT_MAX_MANA);
        }
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

    @SubscribeEvent
    public static void dropped(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY)) return;
        MagicData.get(player).ifPresent(data -> {
            var stack = data.getBookInventory().removeItemNoUpdate(0);
            if (!stack.isEmpty()) {
                var p = event.getEntity();
                event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(p.level(), p.getX(), p.getY(), p.getZ(), stack));
                data.getBookInventory().setChanged();
            }
        });
    }

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer && event.getTarget() instanceof ServerPlayer caster) {
            MagicData.get(caster).ifPresent(data -> {
                MeteorGesture gesture = data.getMeteorGesture();
                if (gesture != null) com.kushtarbek.shogun.magic.network.MagicNetwork.sendToPlayer(observer,
                        new com.kushtarbek.shogun.magic.network.MeteorGesturePacket(caster.getUUID(),
                                caster.level().dimension().location(), gesture));
            });
        }
    }

    private MagicDataEvents() {
    }
}
