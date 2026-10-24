package com.kushtarbek.shogun.magic.spell;

import com.kushtarbek.shogun.SamuraiMod;
import com.kushtarbek.shogun.magic.data.MagicData;
import com.kushtarbek.shogun.magic.entity.MeteorEntity;
import com.kushtarbek.shogun.magic.network.LaunchPacket;
import com.kushtarbek.shogun.magic.network.MagicNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * LONG, 2 s. The target is fixed when the cast starts: the ground where the caster is
 * looking, up to 100 blocks away. While charging, the caster is carried up and back
 * along an arc (32 up, 18 away from the target) to a safe vantage point; on completion
 * a meteor comes in over the target from beyond it. The caster then falls, with fall
 * damage cancelled for that landing.
 * Level scales the crater only (radius 5 + level).
 */
public class MeteorSpell extends Spell {

    public static final int CAST_TIME = 40;
    private static final double RISE_HEIGHT = 32.0;
    /** Travel away from the target. Even aiming at their own feet, the caster lands beyond the crater (r 10) and blast (14). */
    private static final double DRIFT = 18.0;
    /** How far the caster can aim the meteor. */
    private static final double AIM_RANGE = 100.0;
    /** Closer than this the target gives no useful direction; the look direction is used instead. */
    private static final double MIN_AIM_DISTANCE = 4.0;
    /** Clear headroom straight above the caster required before anything else is considered. */
    private static final double CEILING_CHECK = 30.0;
    private static final double SPAWN_HEIGHT = 40.0;
    /** Horizontal distance of the meteor's spawn point from the target, on the side opposite the caster's drift. */
    private static final double APPROACH_OFFSET = 24.0;
    /** Points sampled along the flight arc when checking it for obstacles. */
    private static final int ARC_SAMPLES = 16;
    /** How far below the arc's end to look for ground to land on. */
    private static final int LANDING_SCAN_DEPTH = 64;

    /** How far below the aimed block to look for ground to strike. */
    private static final int GROUND_SCAN_DEPTH = 32;
    /** "Ground" = a solid block with at least GROUND_MIN_SOLID solid blocks among the GROUND_PROBE from it downwards. */
    private static final int GROUND_PROBE = 5;
    private static final int GROUND_MIN_SOLID = 3;

    private static final String TAG_TARGET = "Target";
    private static final String TAG_HEADING_X = "HeadingX";
    private static final String TAG_HEADING_Z = "HeadingZ";

    /** Where the meteor strikes and the horizontal direction it comes in from (the caster flies the other way). */
    private record Plan(BlockPos target, Vec3 heading) {
    }

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
            return noRoom();
        }
        BlockPos aim = aimedBlock(level, caster);
        if (aim == null) {
            return CastResult.fail(Component.translatable("message." + SamuraiMod.MOD_ID + ".meteor.no_target"));
        }
        if (findGround(level, aim) == null) {
            return CastResult.fail(Component.translatable("message." + SamuraiMod.MOD_ID + ".meteor.no_ground"));
        }
        if (plan(level, caster) == null) {
            // No direction has a clear arc, solid ground to land on and a clear line for the meteor.
            return noRoom();
        }
        return CastResult.SUCCESS;
    }

    private static CastResult noRoom() {
        return CastResult.fail(Component.translatable("message." + SamuraiMod.MOD_ID + ".meteor.no_room"));
    }

    @Override
    public void onCastStart(ServerLevel level, ServerPlayer caster, int spellLevel, MagicData data) {
        Plan plan = plan(level, caster);
        if (plan == null) {
            // checkPreCast just passed on the same tick; only a hook racing us could get here.
            plan = new Plan(caster.getOnPos(), Vec3.ZERO);
        }
        CompoundTag tag = data.getCastData();
        tag.putLong(TAG_TARGET, plan.target().asLong());
        tag.putDouble(TAG_HEADING_X, plan.heading().x);
        tag.putDouble(TAG_HEADING_Z, plan.heading().z);
        // Held until the next landing, wherever that is — not for a fixed time.
        data.grantFallImmunity();
        data.raiseMeteorArms(CAST_TIME);
        MagicNetwork.sendToPlayer(caster, new LaunchPacket(caster.position(), flightOffset(plan.heading()), CAST_TIME));
    }

    @Override
    public void onCastTick(ServerLevel level, ServerPlayer caster, int spellLevel, MagicData data, int ticksElapsed) {
        // The client flies the arc (see ClientLaunch); nothing to push from here.
        caster.resetFallDistance();
    }

    /** Released, hit hard enough, died...: stop the flight where it is. The fall stays harmless. */
    @Override
    public void onCastInterrupted(ServerLevel level, ServerPlayer caster, int spellLevel, MagicData data,
                                  CastEndReason reason) {
        MagicNetwork.sendToPlayer(caster, LaunchPacket.stop());
        data.lowerMeteorArms();
    }

    @Override
    public void onCast(ServerLevel level, ServerPlayer caster, int spellLevel, MagicData data) {
        CompoundTag tag = data.getCastData();
        BlockPos target = BlockPos.of(tag.getLong(TAG_TARGET));
        Vec3 heading = new Vec3(tag.getDouble(TAG_HEADING_X), 0, tag.getDouble(TAG_HEADING_Z));
        Vec3 impact = impactPoint(target);
        MeteorEntity meteor = new MeteorEntity(level, caster, spellLevel);
        meteor.setPos(spawnPoint(impact, heading));
        meteor.launchTowards(impact, MeteorEntity.SPEED);
        level.addFreshEntity(meteor);
        int flightTicks = (int) Math.ceil(meteor.position().distanceTo(impact) / MeteorEntity.SPEED);
        data.followMeteor(meteor.getUUID(), flightTicks);
    }

    // ---- planning ----

    /** The block the caster's crosshair is on, within AIM_RANGE, or null if they are looking at nothing. */
    @Nullable
    private static BlockPos aimedBlock(ServerLevel level, ServerPlayer caster) {
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(AIM_RANGE));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, caster));
        return hit.getType() == HitResult.Type.MISS ? null : hit.getBlockPos();
    }

    /**
     * Picks the meteor's heading: from the caster towards the target first, so the meteor
     * comes in from beyond it and the caster drifts back, watching; then the others
     * around it, 45 degrees apart. A heading is usable when the caster's arc is clear,
     * there is ground under its end, and the meteor's line to the target is clear.
     */
    @Nullable
    private static Plan plan(ServerLevel level, ServerPlayer caster) {
        BlockPos aim = aimedBlock(level, caster);
        BlockPos target = aim == null ? null : findGround(level, aim);
        if (target == null) {
            return null;
        }
        Vec3 impact = impactPoint(target);
        Vec3 toTarget = new Vec3(impact.x - caster.getX(), 0, impact.z - caster.getZ());
        Vec3 look = caster.getLookAngle();
        Vec3 forward = toTarget.length() >= MIN_AIM_DISTANCE ? toTarget : new Vec3(look.x, 0, look.z);
        forward = forward.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : forward.normalize();
        // 0, +45, -45, +90, -90, ... degrees around that direction.
        for (int i = 0; i < 8; i++) {
            int step = (i + 1) / 2 * (i % 2 == 1 ? 1 : -1);
            Vec3 heading = forward.yRot((float) Math.toRadians(45 * step));
            if (isArcClear(level, caster, heading) && hasLandingGround(level, caster, heading)
                    && isMeteorPathClear(level, caster, spawnPoint(impact, heading), impact)) {
                return new Plan(target, heading);
            }
        }
        return null;
    }

    /** Up RISE_HEIGHT and DRIFT away from where the meteor comes in. */
    private static Vec3 flightOffset(Vec3 heading) {
        return heading.scale(-DRIFT).add(0, RISE_HEIGHT, 0);
    }

    private static boolean isArcClear(ServerLevel level, ServerPlayer caster, Vec3 heading) {
        Vec3 offset = flightOffset(heading);
        AABB box = caster.getBoundingBox();
        for (int i = 1; i <= ARC_SAMPLES; i++) {
            double t = i / (double) ARC_SAMPLES;
            double s = t * t * (3 - 2 * t); // same easing the client flies
            if (!level.noCollision(caster, box.move(offset.scale(s)))) {
                return false;
            }
        }
        return true;
    }

    /** Never drop the caster off an island edge into the void, or into a bottomless shaft. */
    private static boolean hasLandingGround(ServerLevel level, ServerPlayer caster, Vec3 heading) {
        Vec3 apex = caster.position().add(flightOffset(heading));
        BlockPos column = BlockPos.containing(apex);
        for (int dy = 0; dy <= LANDING_SCAN_DEPTH; dy++) {
            BlockPos pos = column.below(dy);
            if (pos.getY() < level.getMinBuildHeight()) {
                return false;
            }
            if (isSolid(level, pos)) {
                return true;
            }
        }
        return false;
    }

    /** The flight line must reach the target point without striking anything else first. */
    private static boolean isMeteorPathClear(ServerLevel level, Entity context, Vec3 spawn, Vec3 impact) {
        Vec3 beyond = impact.add(impact.subtract(spawn).normalize().scale(2));
        BlockHitResult hit = level.clip(new ClipContext(spawn, beyond, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, context));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceTo(impact) < 2.0;
    }

    private static Vec3 impactPoint(BlockPos target) {
        return new Vec3(target.getX() + 0.5, target.getY() + 1, target.getZ() + 0.5);
    }

    private static Vec3 spawnPoint(Vec3 impact, Vec3 heading) {
        return impact.add(heading.scale(APPROACH_OFFSET)).add(0, SPAWN_HEIGHT, 0);
    }

    /**
     * Top block of real ground at or below {@code from}, or null over the void, on a lone
     * platform or a thin floating island. A pillar over solid terrain is skipped in favour
     * of the terrain below it.
     */
    @Nullable
    public static BlockPos findGround(ServerLevel level, BlockPos from) {
        for (int dy = 0; dy <= GROUND_SCAN_DEPTH; dy++) {
            BlockPos pos = from.below(dy);
            if (pos.getY() < level.getMinBuildHeight()) {
                return null;
            }
            if (isSolid(level, pos) && isGroundMass(level, pos)) {
                return pos;
            }
        }
        return null;
    }

    /**
     * Unbreakable blocks (bedrock) always count as ground: nothing lies under the world's
     * bottom layer, so the usual "solid blocks below" test would wrongly reject it, e.g. on
     * the floor of an old crater in a superflat world. Below the world counts as solid.
     */
    private static boolean isGroundMass(ServerLevel level, BlockPos top) {
        if (level.getBlockState(top).getDestroySpeed(level, top) < 0) {
            return true;
        }
        int solid = 0;
        for (int i = 0; i < GROUND_PROBE; i++) {
            BlockPos pos = top.below(i);
            if (pos.getY() < level.getMinBuildHeight() || isSolid(level, pos)) {
                solid++;
            }
        }
        return solid >= GROUND_MIN_SOLID;
    }

    private static boolean isSolid(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && !state.getCollisionShape(level, pos).isEmpty();
    }
}
