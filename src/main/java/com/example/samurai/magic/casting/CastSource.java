package com.example.samurai.magic.casting;

import com.example.samurai.magic.spell.Spell;
import net.minecraft.server.level.ServerPlayer;

/** What started a cast. Decides what happens to the item when the cast succeeds. */
public enum CastSource {
    NONE,
    COMMAND,
    SPELLBOOK,
    SCROLL;

    public void onCastSucceeded(ServerPlayer player, Spell spell) {
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
