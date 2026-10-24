package com.example.samurai.magic.spell;

import com.example.samurai.magic.data.MagicData;
import com.example.samurai.magic.entity.BlackHoleEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * LONG, 1.5 s. Opens a {@link BlackHoleEntity} where the caster is looking (up to 30
 * blocks; 20 blocks ahead if the look ray hits nothing). The point is taken when the cast
 * completes, so the caster can keep aiming while charging.
 */
public class BlackHoleSpell extends Spell {

    private static final int CAST_TIME = 30;
    private static final double AIM_RANGE = 30.0;
    private static final double MISS_DISTANCE = 20.0;
    /** The centre floats this far off the surface it was aimed at, so it never sits inside a block. */
    private static final double SURFACE_OFFSET = 2.0;

    public BlackHoleSpell() {
        super(new Properties()
                .school(SpellSchool.VOID)
                .castType(CastType.LONG)
                .levels(1, 1)
                .manaCost(60f, 0f)
                .castTime(CAST_TIME)
                .cooldown(600));
    }

    /** Both arms rise over the charge, the same gesture the meteor uses, and drop when it opens. */
    @Override
    public void onCastStart(ServerLevel level, ServerPlayer caster, int spellLevel, MagicData data) {
        data.raiseMeteorArms(CAST_TIME);
    }

    @Override
    public void onCastInterrupted(ServerLevel level, ServerPlayer caster, int spellLevel, MagicData data,
                                  CastEndReason reason) {
        data.lowerMeteorArms();
    }

    @Override
    public void onCast(ServerLevel level, ServerPlayer caster, int spellLevel, MagicData data) {
        data.lowerMeteorArms();
        BlackHoleEntity hole = new BlackHoleEntity(level, caster);
        hole.setPos(aimPoint(level, caster));
        level.addFreshEntity(hole);
    }

    private static Vec3 aimPoint(ServerLevel level, ServerPlayer caster) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(AIM_RANGE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        if (hit.getType() == HitResult.Type.MISS) {
            return eye.add(look.scale(MISS_DISTANCE));
        }
        // Back off from the hit face, but not behind the caster.
        Vec3 away = Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(SURFACE_OFFSET);
        Vec3 point = hit.getLocation().add(away);
        return point.distanceTo(eye) < SURFACE_OFFSET ? eye.add(look.scale(SURFACE_OFFSET)) : point;
    }
}
