package com.example.samurai.magic.spell;

import com.example.samurai.SamuraiMod;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum CastType {
    /** Not castable. Used by the empty default spell. */
    NONE,
    /** Fires immediately on use. */
    INSTANT,
    /** Charges for the cast time, fires once at the end. Ending early cancels it. */
    LONG,
    /** Channels: ticks every tick and pays mana at the end of each interval until released or out of time. */
    CONTINUOUS;

    public Component getDisplayName() {
        return Component.translatable("cast_type." + SamuraiMod.MOD_ID + "." + name().toLowerCase(Locale.ROOT));
    }

    public boolean hasDuration() {
        return this == LONG || this == CONTINUOUS;
    }
}
