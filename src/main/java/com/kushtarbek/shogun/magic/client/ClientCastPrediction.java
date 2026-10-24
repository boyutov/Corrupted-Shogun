package com.kushtarbek.shogun.magic.client;

import com.kushtarbek.shogun.magic.casting.CastSource;
import com.kushtarbek.shogun.magic.spell.CastResult;
import com.kushtarbek.shogun.magic.spell.Spell;

/**
 * Client-side guess of {@link com.kushtarbek.shogun.magic.casting.SpellCasting#tryCast}'s
 * generic checks, from the synced mirror. Spell-specific checks are left to the server.
 */
public final class ClientCastPrediction {

    public static CastResult predict(Spell spell, int level, CastSource source) {
        if (ClientMagicData.isCasting()) {
            return CastResult.ALREADY_CASTING;
        }
        if (ClientMagicData.getCooldowns().isOnCooldown(spell)) {
            return CastResult.ON_COOLDOWN;
        }
        if (source.paysMana() && ClientMagicData.getMana() < spell.getManaCost(level)) {
            return CastResult.NOT_ENOUGH_MANA;
        }
        return CastResult.SUCCESS;
    }

    private ClientCastPrediction() {
    }
}
