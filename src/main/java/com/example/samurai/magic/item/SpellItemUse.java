package com.example.samurai.magic.item;

import com.example.samurai.SamuraiMod;
import com.example.samurai.magic.casting.CastSource;
import com.example.samurai.magic.casting.SpellCasting;
import com.example.samurai.magic.data.MagicData;
import com.example.samurai.magic.spell.CastEndReason;
import com.example.samurai.magic.spell.CastResult;
import com.example.samurai.magic.spell.CastType;
import com.example.samurai.magic.spell.Spell;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Right-click and release handling shared by spell books and scrolls. */
public final class SpellItemUse {

    /** Long enough that nobody holds the button until vanilla gives up. */
    public static final int USE_DURATION = 72000;

    public static InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand,
                                                         @Nullable SpellSlot slot, CastSource source) {
        ItemStack stack = player.getItemInHand(hand);
        if (slot == null) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message." + SamuraiMod.MOD_ID + ".no_spell"), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        boolean held = slot.spell().getCastType().hasDuration();

        // The client cannot see cooldowns yet, so it optimistically starts the hold;
        // the server decides. A refused cast just leaves the client holding until release.
        if (level.isClientSide) {
            if (held) {
                player.startUsingItem(hand);
                return InteractionResultHolder.consume(stack);
            }
            return InteractionResultHolder.success(stack);
        }

        CastResult result = SpellCasting.tryCast((ServerPlayer) player, slot.spell(), slot.level(), source);
        if (!result.isSuccess()) {
            player.displayClientMessage(result.getMessage(), true);
            return InteractionResultHolder.fail(stack);
        }
        if (held) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        // INSTANT scrolls are already consumed at this point.
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    /**
     * Called by vanilla on release, hotbar switch, or the item leaving the hand.
     * Ends the cast only if this kind of item started it.
     */
    public static void stopUsing(LivingEntity entity, CastSource source) {
        if (entity instanceof ServerPlayer player) {
            MagicData.get(player).ifPresent(data -> {
                if (data.isCasting() && data.getCastSource() == source) {
                    SpellCasting.endCast(player, CastEndReason.RELEASED);
                }
            });
        }
    }

    public static void appendSpellInfo(List<Component> tooltip, SpellSlot slot) {
        Spell spell = slot.spell();
        String key = "tooltip." + SamuraiMod.MOD_ID + ".";
        tooltip.add(Component.translatable(key + "school", spell.getSchool().getDisplayName())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(key + "cast_type", spell.getCastType().getDisplayName())
                .withStyle(ChatFormatting.GRAY));
        String cost = String.format("%.0f", spell.getManaCost(slot.level()));
        Component mana = spell.getCastType() == CastType.CONTINUOUS
                ? Component.translatable(key + "mana_per_interval", cost, seconds(spell.getManaInterval()))
                : Component.translatable(key + "mana", cost);
        tooltip.add(mana.copy().withStyle(ChatFormatting.BLUE));
        if (spell.getCastType().hasDuration()) {
            tooltip.add(Component.translatable(key + "cast_time", seconds(spell.getCastTime(slot.level())))
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable(key + "cooldown", seconds(spell.getCooldown(slot.level())))
                .withStyle(ChatFormatting.GRAY));
    }

    private static String seconds(int ticks) {
        return String.format("%.1f", ticks / 20f);
    }

    private SpellItemUse() {
    }
}
