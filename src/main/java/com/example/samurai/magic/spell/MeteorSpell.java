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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * LONG, 2 s. While charging the caster rises 24 blocks; on completion a meteor
 * comes in diagonally onto the ground under the spot where the cast began.
 * Level scales the crater only (radius 5 + level).
 */
public class MeteorSpell extends Spell {

    public static final int CAST_TIME = 40;
    private static final double RISE_HEIGHT = 24.0;
    /** Clear headroom required above the caster before the cast may start. */
    private static final double CEILING_CHECK = 30.0;
    private static final double SPAWN_HEIGHT = 40.0;
    /** Horizontal distance of the spawn point from the target: a 24:40 slope clears the caster by ~14 blocks. */
    private static final double APPROACH_OFFSET = 24.0;

    /** How far below the caster to look for ground. */
    private static final int GROUND_SCAN_DEPTH = 32;
    /** "Ground" = a solid block with at least GROUND_MIN_SOLID solid blocks among the GROUND_PROBE from it downwards. */
    private static final int GROUND_PROBE = 5;
    private static final int GROUND_MIN_SOLID = 3;
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
        if (findGround(level, caster) == null) {
            return CastResult.fail(Component.translatable("message." + SamuraiMod.MOD_ID + ".meteor.no_ground"));
        }
        return CastResult.SUCCESS;
    }

    /**
     * Top block of real ground under the caster, or null over the void, on a lone
     * platform or a thin floating island. A pillar over solid terrain is skipped
     * in favour of the terrain below it.
     */
    @Nullable
    public static BlockPos findGround(ServerLevel level, ServerPlayer caster) {
        BlockPos feet = caster.blockPosition();
        for (int dy = 0; dy <= GROUND_SCAN_DEPTH; dy++) {
            BlockPos pos = feet.below(dy);
            if (pos.getY() < level.getMinBuildHeight()) {
                return null;
            }
            if (isSolid(level, pos) && isGroundMass(level, pos)) {
                return pos;
            }
        }
        return null;
    }

    private static boolean isGroundMass(ServerLevel level, BlockPos top) {
        int solid = 0;
        for (int i = 0; i < GROUND_PROBE; i++) {
            if (isSolid(level, top.below(i))) {
                solid++;
            }
        }
        return solid >= GROUND_MIN_SOLID;
    }

    private static boolean isSolid(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && !state.getCollisionShape(level, pos).isEmpty();
    }

    @Override
    public void onCastStart(ServerLevel level, ServerPlayer caster, int spellLevel, MagicData data) {
        CompoundTag tag = data.getCastData();
        BlockPos ground = findGround(level, caster);
        BlockPos target = ground != null ? ground : caster.getOnPos();
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
        Vec3 impact = new Vec3(target.getX() + 0.5, target.getY() + 1, target.getZ() + 0.5);
        MeteorEntity meteor = new MeteorEntity(level, caster, spellLevel);
        Vec3 spawn = chooseSpawn(level, meteor, impact, caster.getLookAngle());
        meteor.setPos(spawn);
        meteor.launchTowards(impact, MeteorEntity.SPEED);
        level.addFreshEntity(meteor);
    }

    /**
     * Spawn point up and off to the side, coming in from where the caster is looking
     * so they can watch it. Tries other headings if terrain blocks the line; falls
     * back to straight down if every diagonal is blocked.
     */
    private static Vec3 chooseSpawn(ServerLevel level, MeteorEntity meteor, Vec3 impact, Vec3 look) {
        Vec3 heading = new Vec3(look.x, 0, look.z);
        heading = heading.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : heading.normalize();
        // 0, +45, -45, +90, -90, ... degrees around the look direction.
        for (int i = 0; i < 8; i++) {
            int step = (i + 1) / 2 * (i % 2 == 1 ? 1 : -1);
            Vec3 dir = heading.yRot((float) Math.toRadians(45 * step));
            Vec3 spawn = impact.add(dir.scale(APPROACH_OFFSET)).add(0, SPAWN_HEIGHT, 0);
            if (isClearPath(level, meteor, spawn, impact)) {
                return spawn;
            }
        }
        return impact.add(0, SPAWN_HEIGHT, 0);
    }

    /** The flight line must reach the target point without striking anything else first. */
    private static boolean isClearPath(ServerLevel level, MeteorEntity meteor, Vec3 spawn, Vec3 impact) {
        Vec3 beyond = impact.add(impact.subtract(spawn).normalize().scale(2));
        BlockHitResult hit = level.clip(new ClipContext(spawn, beyond, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, meteor));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceTo(impact) < 2.0;
    }
}
