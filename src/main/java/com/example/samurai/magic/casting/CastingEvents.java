package com.example.samurai.magic.casting;

import com.example.samurai.SamuraiMod;
import com.example.samurai.magic.data.MagicData;
import com.example.samurai.magic.spell.CastEndReason;
import com.example.samurai.magic.spell.Spell;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SamuraiMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CastingEvents {

    /**
     * Damage interrupts only spells with a non-zero threshold. Uses the final
     * amount after armor/absorption — what the hit actually did to the player.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damaged(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MagicData.get(player).ifPresent(data -> {
            Spell spell = data.getCastingSpell();
            if (spell != null && spell.isInterruptedBy(event.getAmount())) {
                SpellCasting.endCast(player, CastEndReason.DAMAGE);
            }
        });
    }

    /**
     * Spells like meteor launch the caster; the first real landing afterwards is free.
     * Touchdowns from a safe height don't use the immunity up: at takeoff the caster
     * can bob against the ground for a tick or two before the lift wins.
     */
    @SubscribeEvent
    public static void fell(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getDistance() > MagicData.SAFE_FALL_DISTANCE) {
            MagicData.get(player).ifPresent(data -> {
                if (data.isFallImmune()) {
                    data.clearFallImmunity();
                    event.setCanceled(true);
                }
            });
        }
    }

    @SubscribeEvent
    public static void died(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SpellCasting.endCast(player, CastEndReason.CANCELLED);
            MagicData.get(player).ifPresent(MagicData::resetMeteorArms);
        }
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SpellCasting.endCast(player, CastEndReason.CANCELLED);
            MagicData.get(player).ifPresent(MagicData::resetMeteorArms);
        }
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SpellCasting.endCast(player, CastEndReason.CANCELLED);
            MagicData.get(player).ifPresent(MagicData::resetMeteorArms);
        }
    }

    private CastingEvents() {
    }
}
