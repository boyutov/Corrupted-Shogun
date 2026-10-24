package com.example.samurai.magic.spell;

public enum CastType {
    /** Not castable. Used by the empty default spell. */
    NONE,
    /** Fires immediately on use. */
    INSTANT,
    /** Charges for the cast time, fires once at the end. Ending early cancels it. */
    LONG,
    /** Channels: ticks every tick and pays mana at the end of each interval until released or out of time. */
    CONTINUOUS;

    public boolean hasDuration() {
        return this == LONG || this == CONTINUOUS;
    }
}
