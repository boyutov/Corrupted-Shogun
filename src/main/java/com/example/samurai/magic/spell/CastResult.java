package com.example.samurai.magic.spell;

import com.example.samurai.SamuraiMod;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum CastResult {
    SUCCESS,
    INVALID_SPELL,
    INVALID_LEVEL,
    ALREADY_CASTING,
    ON_COOLDOWN,
    NOT_ENOUGH_MANA,
    /** A spell's own {@link Spell#checkPreCast} refused. */
    CANNOT_CAST;

    public boolean isSuccess() {
        return this == SUCCESS;
    }

    public Component getMessage() {
        return Component.translatable("message." + SamuraiMod.MOD_ID + ".cast." + name().toLowerCase(Locale.ROOT));
    }
}
