package com.example.samurai.magic.casting;

import com.example.samurai.magic.data.MagicData;
import com.example.samurai.magic.spell.CastEndReason;
import com.example.samurai.magic.spell.CastResult;
import com.example.samurai.magic.spell.CastType;
import com.example.samurai.magic.spell.Spell;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side cast state machine. Idle → (tryCast) → casting → (tick / endCast) → idle.
 * INSTANT never enters the casting state.
 */
public final class SpellCasting {

    public static CastResult tryCast(ServerPlayer player, Spell spell, int spellLevel, CastSource source) {
        MagicData data = MagicData.get(player).orElse(null);
        if (data == null || spell.getCastType() == CastType.NONE) {
            return CastResult.INVALID_SPELL;
        }
        if (!spell.isValidLevel(spellLevel)) {
            return CastResult.INVALID_LEVEL;
        }
        if (data.isCasting()) {
            return CastResult.ALREADY_CASTING;
        }
        if (data.getCooldowns().isOnCooldown(spell)) {
            return CastResult.ON_COOLDOWN;
        }
        // Only INSTANT pays now. LONG pays at the end, CONTINUOUS at the end of each
        // interval — this check just makes sure the first payment is affordable.
        float cost = spell.getManaCost(spellLevel);
        if (!data.hasMana(cost)) {
            return CastResult.NOT_ENOUGH_MANA;
        }
        ServerLevel level = player.serverLevel();
        CastResult custom = spell.checkPreCast(level, player, spellLevel, data);
        if (!custom.isSuccess()) {
            return custom;
        }

        switch (spell.getCastType()) {
            case INSTANT -> {
                data.consumeMana(cost);
                spell.onCastStart(level, player, spellLevel, data);
                finishSuccessfully(level, player, spell, spellLevel, data, source);
            }
            case LONG, CONTINUOUS -> {
                data.startCasting(spell, spellLevel, spell.getCastTime(spellLevel), source);
                spell.onCastStart(level, player, spellLevel, data);
            }
            default -> {
                return CastResult.INVALID_SPELL;
            }
        }
        data.sync();
        return CastResult.SUCCESS;
    }

    /** Once per server player tick, after mana regen. */
    public static void tick(ServerPlayer player, MagicData data) {
        data.getCooldowns().tick();
        if (!data.isCasting()) {
            return;
        }
        Spell spell = data.getCastingSpell();
        if (spell == null) {
            // Saved cast refers to a spell that no longer exists.
            data.clearCasting();
            return;
        }

        ServerLevel level = player.serverLevel();
        int spellLevel = data.getCastingLevel();
        int elapsed = data.advanceCast();
        spell.onCastTick(level, player, spellLevel, data, elapsed);
        if (!data.isCasting()) {
            // The hook ended the cast itself.
            return;
        }

        if (spell.getCastType() == CastType.CONTINUOUS && elapsed % spell.getManaInterval() == 0
                && !data.consumeMana(spell.getManaCost(spellLevel))) {
            endCast(player, CastEndReason.OUT_OF_MANA);
            return;
        }
        if (data.getCastTicksRemaining() <= 0) {
            endCast(player, CastEndReason.COMPLETED);
        }
    }

    /**
     * Ends the current cast, if any. Whether that counts as success depends on
     * the cast type: a LONG cast succeeds only on COMPLETED; a CONTINUOUS cast
     * succeeds unless it was interrupted by damage or cancelled.
     */
    public static void endCast(ServerPlayer player, CastEndReason reason) {
        MagicData data = MagicData.get(player).orElse(null);
        if (data == null || !data.isCasting()) {
            return;
        }
        Spell spell = data.getCastingSpell();
        int spellLevel = data.getCastingLevel();
        CastSource source = data.getCastSource();
        // Clear before hooks so a hook that starts a new cast isn't clobbered.
        data.clearCasting();
        if (spell == null) {
            return;
        }

        ServerLevel level = player.serverLevel();
        boolean success = switch (spell.getCastType()) {
            case LONG -> reason == CastEndReason.COMPLETED && data.consumeMana(spell.getManaCost(spellLevel));
            case CONTINUOUS -> reason == CastEndReason.COMPLETED || reason == CastEndReason.RELEASED
                    || reason == CastEndReason.OUT_OF_MANA;
            default -> false;
        };
        if (success) {
            finishSuccessfully(level, player, spell, spellLevel, data, source);
        } else {
            CastEndReason actual = reason;
            if (reason == CastEndReason.COMPLETED) {
                // LONG finished charging but the player can no longer pay.
                actual = CastEndReason.OUT_OF_MANA;
                player.displayClientMessage(CastResult.NOT_ENOUGH_MANA.getMessage(), true);
            }
            spell.onCastInterrupted(level, player, spellLevel, data, actual);
        }
        data.sync();
    }

    private static void finishSuccessfully(ServerLevel level, ServerPlayer player, Spell spell, int spellLevel,
                                           MagicData data, CastSource source) {
        spell.onCast(level, player, spellLevel, data);
        data.getCooldowns().start(spell, spell.getCooldown(spellLevel));
        source.onCastSucceeded(player, spell);
    }

    private SpellCasting() {
    }
}
