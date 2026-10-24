package com.example.samurai.magic.client;

import com.example.samurai.magic.spell.CastResult;
import com.example.samurai.magic.spell.Spell;

/**
 * Client-side guess of {@link com.example.samurai.magic.casting.SpellCasting#tryCast}'s
 * generic checks, from the synced mirror. Spell-specific checks are left to the server.
 */
public final class ClientCastPrediction {

    public static CastResult predict(Spell spell, int level) {
        if (ClientMagicData.isCasting()) {
            return CastResult.ALREADY_CASTING;
        }
        if (ClientMagicData.getCooldowns().isOnCooldown(spell)) {
            return CastResult.ON_COOLDOWN;
        }
        if (ClientMagicData.getMana() < spell.getManaCost(level)) {
            return CastResult.NOT_ENOUGH_MANA;
        }
        return CastResult.SUCCESS;
    }

    private ClientCastPrediction() {
    }
}
