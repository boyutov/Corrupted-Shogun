package com.kushtarbek.shogun.magic.item;

import com.kushtarbek.shogun.magic.registry.MagicSpells;
import com.kushtarbek.shogun.magic.spell.Spell;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/** A spell at a given level, as stored on a book slot or a scroll. */
public record SpellSlot(Spell spell, int level) {

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Spell", spell.getId().toString());
        tag.putInt("Level", level);
        return tag;
    }

    /** Null if the tag is empty or names a spell that does not exist (any more). */
    @Nullable
    public static SpellSlot load(CompoundTag tag) {
        Spell spell = MagicSpells.byId(ResourceLocation.tryParse(tag.getString("Spell")));
        return spell == null ? null : new SpellSlot(spell, spell.clampLevel(tag.getInt("Level")));
    }

    /** "Name IV", tinted with the school colour. */
    public MutableComponent getDisplayName() {
        return Component.empty()
                .append(spell.getDisplayName())
                .append(" ")
                .append(Component.translatable("enchantment.level." + level))
                .withStyle(style -> style.withColor(spell.getSchool().getColor()));
    }
}
