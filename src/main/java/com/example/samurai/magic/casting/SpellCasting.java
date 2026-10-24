package com.example.samurai.magic.casting;

import com.example.samurai.magic.data.MagicData;
import com.example.samurai.magic.item.SpellBookItem;
import com.example.samurai.magic.item.SpellScrollItem;
import com.example.samurai.magic.spell.CastEndReason;
import com.example.samurai.magic.spell.CastResult;
import com.example.samurai.magic.spell.CastType;
import com.example.samurai.magic.spell.Spell;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;

import javax.annotation.Nullable;

/**
 * Server-side cast state machine. Idle → (tryCast) → casting → (tick / endCast) → idle.
 * INSTANT never enters the casting state.
 */
public final class SpellCasting {

    /** Vanilla entity event that makes the client finish using its item (same as after eating). */
    private static final byte ENTITY_EVENT_FINISH_USING = 9;

    /** @param hand the hand holding the casting item; null for item-less sources */
    public static CastResult tryCast(ServerPlayer player, Spell spell, int spellLevel, CastSource source,
                                     @Nullable InteractionHand hand) {
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
        float cost = source.paysMana() ? spell.getManaCost(spellLevel) : 0f;
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
                finishSuccessfully(level, player, spell, spellLevel, data, source, hand);
            }
            case LONG, CONTINUOUS -> {
                data.startCasting(spell, spellLevel, spell.getCastTime(spellLevel), source, hand);
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
        if (data.getCastSource() == CastSource.EQUIPPED_BOOK
                && (!data.hasBookHold() || player.containerMenu != player.inventoryMenu)) {
            endCast(player, CastEndReason.CANCELLED);
            return;
        }
        Spell spell = data.getCastingSpell();
        if (spell == null) {
            // Saved cast refers to a spell that no longer exists.
            data.clearCasting();
            return;
        }
        int spellLevel = data.getCastingLevel();
        // Item gone from the hand (dropped, swapped, moved by a container)? Stop now
        // rather than keep charging or draining mana for a cast that cannot finish.
        if (!data.getCastSource().isStillHeld(player, data.getCastHand(), spell, spellLevel)) {
            endCast(player, CastEndReason.CANCELLED);
            return;
        }

        ServerLevel level = player.serverLevel();
        int elapsed = data.advanceCast();
        spell.onCastTick(level, player, spellLevel, data, elapsed);
        if (!data.isCasting()) {
            // The hook ended the cast itself.
            return;
        }

        if (spell.getCastType() == CastType.CONTINUOUS && data.getCastSource().paysMana()
                && elapsed % spell.getManaInterval() == 0 && !data.consumeMana(spell.getManaCost(spellLevel))) {
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
     * succeeds unless it was interrupted by damage or cancelled. Either way it
     * fails if the casting item is no longer in its hand with the same spell.
     */
    public static void endCast(ServerPlayer player, CastEndReason reason) {
        MagicData data = MagicData.get(player).orElse(null);
        if (data == null || !data.isCasting()) {
            return;
        }
        Spell spell = data.getCastingSpell();
        int spellLevel = data.getCastingLevel();
        CastSource source = data.getCastSource();
        InteractionHand hand = data.getCastHand();
        // Decide before the hooks: a successful scroll cast empties the very stack being used.
        // RELEASED comes from Item#onStopUsing, i.e. vanilla is already ending the use.
        boolean releaseUse = source.isItem() && reason != CastEndReason.RELEASED
                && player.isUsingItem() && isSpellItem(player.getUseItem().getItem());
        // Clear before hooks so a hook that starts a new cast isn't clobbered.
        data.clearCasting();

        if (spell != null) {
            CastEndReason actual = reason;
            boolean success = switch (spell.getCastType()) {
                case LONG -> reason == CastEndReason.COMPLETED;
                case CONTINUOUS -> reason == CastEndReason.COMPLETED || reason == CastEndReason.RELEASED
                        || reason == CastEndReason.OUT_OF_MANA;
                default -> false;
            };
            if (success && !source.isStillHeld(player, hand, spell, spellLevel)) {
                success = false;
                actual = CastEndReason.CANCELLED;
            }
            if (success && spell.getCastType() == CastType.LONG && source.paysMana()
                    && !data.consumeMana(spell.getManaCost(spellLevel))) {
                // Finished charging but can no longer pay.
                success = false;
                actual = CastEndReason.OUT_OF_MANA;
                player.displayClientMessage(CastResult.NOT_ENOUGH_MANA.getMessage(), true);
            }

            ServerLevel level = player.serverLevel();
            if (success) {
                finishSuccessfully(level, player, spell, spellLevel, data, source, hand);
            } else {
                spell.onCastInterrupted(level, player, spellLevel, data, actual);
            }
        }
        if (releaseUse) {
            releaseSpellItem(player);
        }
        data.sync();
    }

    /**
     * If the player is still holding use on a spell item, end that use on both
     * sides. Without this a completed LONG cast leaves the player in the use pose
     * (and its movement slowdown) until the button is released.
     */
    private static void releaseSpellItem(ServerPlayer player) {
        if (player.isUsingItem()) {
            // stopUsingItem() calls Item#onStopUsing → endCast, which is a no-op by now.
            player.stopUsingItem();
        }
        stopClientUsing(player);
    }

    /**
     * Makes the client finish its current item use. Also used when the server
     * refuses a cast the client had optimistically started holding.
     */
    public static void stopClientUsing(ServerPlayer player) {
        player.connection.send(new ClientboundEntityEventPacket(player, ENTITY_EVENT_FINISH_USING));
    }

    private static boolean isSpellItem(Item item) {
        return item instanceof SpellBookItem || item instanceof SpellScrollItem;
    }

    private static void finishSuccessfully(ServerLevel level, ServerPlayer player, Spell spell, int spellLevel,
                                           MagicData data, CastSource source, @Nullable InteractionHand hand) {
        spell.onCast(level, player, spellLevel, data);
        data.getCooldowns().start(spell, spell.getCooldown(spellLevel));
        source.onCastSucceeded(player, hand);
    }

    private SpellCasting() {
    }
}
