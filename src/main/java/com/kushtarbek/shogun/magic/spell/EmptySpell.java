package com.kushtarbek.shogun.magic.spell;

import com.kushtarbek.shogun.magic.data.MagicData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Registry default; stands in for "no spell" in empty book slots and missing ids. */
public class EmptySpell extends Spell {

    public EmptySpell() {
        super(new Properties().castType(CastType.NONE).levels(0, 0));
    }

    @Override
    public void onCast(ServerLevel level, ServerPlayer caster, int spellLevel, MagicData data) {
    }
}
