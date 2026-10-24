package com.example.samurai.magic.spell;

import com.example.samurai.SamuraiMod;
import com.example.samurai.magic.data.MagicData;
import com.example.samurai.magic.entity.MeteorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * LONG, 2 s. While charging the caster rises 24 blocks; on completion a meteor
 * drops onto the block the caster stood on when the cast began.
 * Level scales the crater only (radius 5 + level).
 */
public class MeteorSpell extends Spell {

    public static final int CAST_TIME = 40;
    private static final double RISE_HEIGHT = 24.0;
    /** Clear headroom required above the caster before the cast may start. */
    private static final double CEILING_CHECK = 30.0;
    private static final double SPAWN_HEIGHT = 40.0;
    /** Weight of the position correction on top of the planned velocity; the server sees the client late. */
    private static final double RISE_CORRECTION = 0.15;

    private static final String TAG_TARGET = "Target";
    private static final String TAG_START_Y = "StartY";

    public MeteorSpell() {
        super(new Properties()
                .school(SpellSchool.FIRE)
                .castType(CastType.LONG)
                .levels(1, 5)
                .manaCost(100f, 0f)
                .castTime(CAST_TIME)
                .cooldown(400));
    }

    public static int craterRadius(int spellLevel) {
        return 5 + spellLevel;
    }

    @Override
    public CastResult checkPreCast(ServerLevel level, ServerPlayer caster, int spellLevel, MagicData data) {
        // Sweep the caster's own hitbox upwards: catches thin ceilings a single ray would slip past.
        if (!level.noCollision(caster, caster.getBoundingBox().expandTowards(0, CEILING_CHECK, 0))) {
            return CastResult.fail(Component.translatable("message." + SamuraiMod.MOD_ID + ".meteor.no_room"));
        }
        return CastResult.SUCCESS;
    }

    @Override
    public void onCastStart(ServerLevel level, ServerPlayer caster, int spellLevel, MagicData data) {
        CompoundTag tag = data.getCastData();
        BlockPos target = caster.getOnPos();
        tag.putLong(TAG_TARGET, target.asLong());
        tag.putDouble(TAG_START_Y, caster.getY());
        // Held until the next landing, wherever that is — not for a fixed time.
        data.grantFallImmunity();
    }

    @Override
    public void onCastTick(ServerLevel level, ServerPlayer caster, int spellLevel, MagicData data, int ticksElapsed) {
        double startY = data.getCastData().getDouble(TAG_START_Y);
        double planned = riseAt(ticksElapsed + 1) - riseAt(ticksElapsed);
        double error = startY + riseAt(ticksElapsed) - caster.getY();
        double vy = planned + RISE_CORRECTION * error;
        // Player movement is client-side: set the velocity and push it to the client every tick.
        caster.setDeltaMovement(0, vy, 0);
        caster.connection.send(new ClientboundSetEntityMotionPacket(caster));
        caster.resetFallDistance();
    }

    /** Smoothstep ease-in-out from 0 to RISE_HEIGHT over the cast time. */
    private static double riseAt(int tick) {
        double t = Mth.clamp(tick / (double) CAST_TIME, 0, 1);
        return RISE_HEIGHT * t * t * (3 - 2 * t);
    }

    @Override
    public void onCast(ServerLevel level, ServerPlayer caster, int spellLevel, MagicData data) {
        BlockPos target = BlockPos.of(data.getCastData().getLong(TAG_TARGET));
        MeteorEntity meteor = new MeteorEntity(level, caster, spellLevel);
        meteor.setPos(target.getX() + 0.5, target.getY() + 1 + SPAWN_HEIGHT, target.getZ() + 0.5);
        level.addFreshEntity(meteor);
    }
}
