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

    /** Spells like meteor launch the caster; the first landing afterwards is free. */
    @SubscribeEvent
    public static void fell(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
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
        }
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SpellCasting.endCast(player, CastEndReason.CANCELLED);
        }
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SpellCasting.endCast(player, CastEndReason.CANCELLED);
        }
    }

    private CastingEvents() {
    }
}
