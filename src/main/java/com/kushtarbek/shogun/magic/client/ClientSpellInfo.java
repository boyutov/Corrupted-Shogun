package com.kushtarbek.shogun.magic.client;

import com.kushtarbek.shogun.magic.item.SpellBookItem;
import com.kushtarbek.shogun.magic.item.BookLocation;
import com.kushtarbek.shogun.magic.item.SpellScrollItem;
import com.kushtarbek.shogun.magic.item.SpellSlot;
import com.kushtarbek.shogun.magic.spell.Spell;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/** Small client helpers shared by the HUD, the book screen and the hotkeys. */
public final class ClientSpellInfo {

    /** The spell the player would cast with right click: main hand first, like vanilla use. */
    @Nullable
    public static SpellSlot heldSpell(LocalPlayer player) {
        if (player.isUsingItem()) {
            SpellSlot scroll = SpellScrollItem.getSpell(player.getUseItem());
            if (scroll != null) return scroll;
        }
        ItemStack equipped = BookLocation.EQUIPPED.get(player);
        if (equipped.getItem() instanceof SpellBookItem) return SpellBookItem.getActive(equipped);
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof SpellBookItem) {
                return SpellBookItem.getActive(stack);
            }
            SpellSlot scroll = SpellScrollItem.getSpell(stack);
            if (scroll != null) {
                return scroll;
            }
        }
        return null;
    }

    @Nullable
    public static InteractionHand bookHand(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() instanceof SpellBookItem) {
                return hand;
            }
        }
        return null;
    }

    /** "Name II" plus " 3.2s" in red while on cooldown. */
    public static MutableComponent labelWithCooldown(SpellSlot slot) {
        MutableComponent label = slot.getDisplayName();
        Spell spell = slot.spell();
        int remaining = ClientMagicData.getCooldowns().getRemaining(spell);
        if (remaining > 0) {
            label = Component.empty().append(label).append(Component.literal(" " + formatSeconds(remaining))
                    .withStyle(ChatFormatting.RED));
        }
        return label;
    }

    public static String formatSeconds(int ticks) {
        return String.format("%.1fs", ticks / 20f);
    }

    private ClientSpellInfo() {
    }
}
