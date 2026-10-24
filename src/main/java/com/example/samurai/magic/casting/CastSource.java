package com.example.samurai.magic.casting;

import com.example.samurai.magic.item.SpellBookItem;
import com.example.samurai.magic.item.SpellScrollItem;
import com.example.samurai.magic.item.SpellSlot;
import com.example.samurai.magic.spell.Spell;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/** What started a cast: decides whether the cast may still finish and what happens to the item when it does. */
public enum CastSource {
    NONE,
    COMMAND,
    SPELLBOOK {
        @Override
        public boolean isStillHeld(ServerPlayer player, @Nullable InteractionHand hand, Spell spell, int level) {
            if (hand == null) {
                return false;
            }
            ItemStack stack = player.getItemInHand(hand);
            SpellSlot active = stack.getItem() instanceof SpellBookItem ? SpellBookItem.getActive(stack) : null;
            return active != null && active.spell() == spell && active.level() == level;
        }
    },
    EQUIPPED_BOOK {
        @Override
        public boolean isStillHeld(ServerPlayer player, @Nullable InteractionHand hand, Spell spell, int level) {
            return com.example.samurai.magic.data.MagicData.get(player).map(data -> {
                SpellSlot active = SpellBookItem.getActive(data.getBookInventory().getItem(0));
                return data.isCastingEquippedBookPresent() && active != null
                        && active.spell() == spell && active.level() == level;
            }).orElse(false);
        }
    },
    SCROLL {
        @Override
        public boolean isStillHeld(ServerPlayer player, @Nullable InteractionHand hand, Spell spell, int level) {
            if (hand == null) {
                return false;
            }
            SpellSlot slot = SpellScrollItem.getSpell(player.getItemInHand(hand));
            return slot != null && slot.spell() == spell && slot.level() == level;
        }

        @Override
        public void onCastSucceeded(ServerPlayer player, @Nullable InteractionHand hand) {
            if (hand != null) {
                player.getItemInHand(hand).shrink(1);
            }
        }

        /** The scroll itself is the price. */
        @Override
        public boolean paysMana() {
            return false;
        }
    };

    /**
     * Whether the item that started the cast is still in the same hand and still
     * carries the same spell. Item-less sources always pass.
     */
    public boolean isStillHeld(ServerPlayer player, @Nullable InteractionHand hand, Spell spell, int level) {
        return true;
    }

    /** Called after {@link #isStillHeld} passed and the spell fired. */
    public void onCastSucceeded(ServerPlayer player, @Nullable InteractionHand hand) {
    }

    /** Whether casts from this source check and spend the caster's mana. */
    public boolean paysMana() {
        return true;
    }

    public boolean isItem() {
        return this == SPELLBOOK || this == SCROLL;
    }

    public static CastSource byName(String name) {
        for (CastSource source : values()) {
            if (source.name().equals(name)) {
                return source;
            }
        }
        return NONE;
    }
}
