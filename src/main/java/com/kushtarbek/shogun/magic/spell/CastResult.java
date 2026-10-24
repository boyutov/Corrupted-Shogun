package com.kushtarbek.shogun.magic.spell;

import com.kushtarbek.shogun.SamuraiMod;
import net.minecraft.network.chat.Component;

/**
 * Outcome of a cast attempt. The generic outcomes are constants; a spell's own
 * {@link Spell#checkPreCast} can refuse with its own message via {@link #fail}.
 */
public final class CastResult {

    public static final CastResult SUCCESS = generic("success", true);
    public static final CastResult INVALID_SPELL = generic("invalid_spell", false);
    public static final CastResult INVALID_LEVEL = generic("invalid_level", false);
    public static final CastResult ALREADY_CASTING = generic("already_casting", false);
    public static final CastResult ON_COOLDOWN = generic("on_cooldown", false);
    public static final CastResult NOT_ENOUGH_MANA = generic("not_enough_mana", false);
    /** Generic spell-specific refusal; prefer {@link #fail} with a precise message. */
    public static final CastResult CANNOT_CAST = generic("cannot_cast", false);

    private final boolean success;
    private final Component message;

    private CastResult(boolean success, Component message) {
        this.success = success;
        this.message = message;
    }

    private static CastResult generic(String key, boolean success) {
        return new CastResult(success, Component.translatable("message." + SamuraiMod.MOD_ID + ".cast." + key));
    }

    public static CastResult fail(Component message) {
        return new CastResult(false, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public Component getMessage() {
        return message;
    }
}
