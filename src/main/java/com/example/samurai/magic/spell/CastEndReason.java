package com.example.samurai.magic.spell;

public enum CastEndReason {
    /** Cast time ran out normally. */
    COMPLETED,
    /** Player let go of the use button. */
    RELEASED,
    /** CONTINUOUS ran dry, or LONG could not pay at the end. */
    OUT_OF_MANA,
    /** Hit for at least the spell's interrupt threshold. */
    DAMAGE,
    /** Death, logout, dimension change, command. */
    CANCELLED
}
