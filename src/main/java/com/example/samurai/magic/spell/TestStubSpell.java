package com.example.samurai.magic.spell;

import com.example.samurai.magic.data.MagicData;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/**
 * Not a real spell: a pipeline probe. Has no effect beyond logging each hook,
 * so mana spend, cooldowns and the cast state machine can be checked in isolation.
 */
public class TestStubSpell extends Spell {

    private static final Logger LOGGER = LogUtils.getLogger();

    public TestStubSpell(Properties properties) {
        super(properties);
    }

    @Override
    public void onCastStart(ServerLevel level, ServerPlayer caster, int spellLevel, MagicData data) {
        LOGGER.info("[{}] start: {} lvl {}, cast time {}t", getId(), caster.getScoreboardName(), spellLevel,
                getCastTime(spellLevel));
    }

    @Override
    public void onCastTick(ServerLevel level, ServerPlayer caster, int spellLevel, MagicData data, int ticksElapsed) {
        if (ticksElapsed % 20 == 0) {
            LOGGER.info("[{}] tick {}: {}", getId(), ticksElapsed, caster.getScoreboardName());
        }
    }

    @Override
    public void onCast(ServerLevel level, ServerPlayer caster, int spellLevel, MagicData data) {
        LOGGER.info("[{}] cast: {} lvl {}, mana left {}, cooldown {}t", getId(), caster.getScoreboardName(),
                spellLevel, data.getMana(), getCooldown(spellLevel));
    }

    @Override
    public void onCastInterrupted(ServerLevel level, ServerPlayer caster, int spellLevel, MagicData data,
                                  CastEndReason reason) {
        LOGGER.info("[{}] interrupted: {} ({})", getId(), caster.getScoreboardName(), reason);
    }
}
