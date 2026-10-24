package com.kushtarbek.shogun.magic.spell;

import com.kushtarbek.shogun.SamuraiMod;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum SpellSchool {
    ARCANE(0xB085F5),
    FIRE(0xFF6A2B),
    FROST(0x7FD8FF),
    STORM(0xF5E663),
    NATURE(0x6BD46B),
    SHADOW(0x8A5CC7),
    HOLY(0xFFE9A8),
    BLOOD(0xC4243A),
    /** build_model.py purple: the black hole's school. */
    VOID(0x8E4CD8);

    private final int color;

    SpellSchool(int color) {
        this.color = color;
    }

    /** RGB, used by UI to tint bars and names. */
    public int getColor() {
        return color;
    }

    public Component getDisplayName() {
        return Component.translatable("school." + SamuraiMod.MOD_ID + "." + name().toLowerCase(Locale.ROOT));
    }
}
