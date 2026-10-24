package com.example.samurai.magic.entity;

import com.example.samurai.SamuraiMod;
import com.example.samurai.magic.MagicConfig;
import com.example.samurai.magic.spell.MeteorSpell;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * What happens when a meteor lands: area damage right away, then a crater
 * carved over as many ticks as the per-tick block budget needs, then a few fires.
 */
@Mod.EventBusSubscriber(modid = SamuraiMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MeteorImpact {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final double DAMAGE_RADIUS = 12.0;
    private static final float DAMAGE_CENTER = 40f;
    private static final float DAMAGE_EDGE = 10f;

    /** Blocks at or above this blast resistance survive (obsidian, ancient debris, anvils...). */
    private static final float RESISTANCE_LIMIT = 600f;
    /** No neighbour updates, no shape updates, no drops: just turn the block into air. */
    private static final int REMOVE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private static final int MAX_FIRES = 10;
    private static final int FIRE_ATTEMPTS = 40;

    private static final List<CraterJob> JOBS = new ArrayList<>();

    public static void impact(ServerLevel level, MeteorEntity meteor, BlockHitResult hit) {
        Vec3 center = hit.getLocation();
        damage(level, meteor, center);

        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 8f, 0.6f);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 1, center.z, 4, 3, 1, 3, 0);
        level.sendParticles(ParticleTypes.LAVA, center.x, center.y + 1, center.z, 60, 4, 1, 4, 0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + 2, center.z, 80, 5, 2, 5, 0.05);

        if (MagicConfig.METEOR_DESTROYS_TERRAIN.get()) {
            int radius = MeteorSpell.craterRadius(meteor.getSpellLevel());
            JOBS.add(new CraterJob(level, hit.getBlockPos(), radius));
        }
    }

    /** Linear falloff from 40 at the centre to 10 at 12 blocks. Spares the caster and everything they own. */
    private static void damage(ServerLevel level, MeteorEntity meteor, Vec3 center) {
        UUID owner = meteor.getOwnerId();
        Entity caster = owner == null ? null : level.getPlayerByUUID(owner);
        DamageSource source = level.damageSources().explosion(meteor, caster);
        AABB area = new AABB(center, center).inflate(DAMAGE_RADIUS);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (!target.isAlive() || target instanceof ArmorStand || isFriendly(target, owner)) {
                continue;
            }
            double distance = target.position().distanceTo(center);
            if (distance > DAMAGE_RADIUS) {
                continue;
            }
            float amount = (float) (DAMAGE_CENTER - (DAMAGE_CENTER - DAMAGE_EDGE) * (distance / DAMAGE_RADIUS));
            target.hurt(source, amount);
        }
    }

    private static boolean isFriendly(LivingEntity target, @Nullable UUID owner) {
        if (owner == null) {
            return false;
        }
        if (owner.equals(target.getUUID())) {
            return true;
        }
        // Tamed wolves and cats, horses, llamas... anything that remembers an owner.
        return target instanceof OwnableEntity ownable && owner.equals(ownable.getOwnerUUID());
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || JOBS.isEmpty()) {
            return;
        }
        int budget = MagicConfig.METEOR_BLOCKS_PER_TICK.get();
        for (Iterator<CraterJob> it = JOBS.iterator(); it.hasNext() && budget > 0; ) {
            CraterJob job = it.next();
            budget -= job.carve(budget);
            if (job.isDone()) {
                job.finish();
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent event) {
        JOBS.clear();
    }

    /**
     * A bowl: ellipsoid with horizontal radius r, depth d below the impact and d/2
     * above it (so it also shaves the ground's lip). Volume ≈ π·r²·d.
     */
    private static final class CraterJob {
        private final ServerLevel level;
        private final BlockPos center;
        private final int radius;
        private final int depth;
        private final int up;
        private final List<BlockPos> positions = new ArrayList<>();
        private int next;

        private int removed;
        private int ticks;
        private long nanos;
        private long maxTickNanos;

        CraterJob(ServerLevel level, BlockPos center, int radius) {
            this.level = level;
            this.center = center;
            this.radius = radius;
            this.depth = Math.round(radius * 2f / 3f);
            this.up = Math.max(1, depth / 2);
            // Top-down so the carved space never leaves a floating ceiling mid-way through a split carve.
            for (int dy = up; dy >= -depth; dy--) {
                double ry = dy >= 0 ? up : depth;
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        double e = (dx * dx + dz * dz) / (double) (radius * radius) + (dy * dy) / (ry * ry);
                        if (e <= 1.0) {
                            positions.add(center.offset(dx, dy, dz));
                        }
                    }
                }
            }
        }

        /** Removes up to {@code budget} blocks; returns how many positions it consumed. */
        int carve(int budget) {
            long start = System.nanoTime();
            int used = 0;
            while (next < positions.size() && used < budget) {
                BlockPos pos = positions.get(next++);
                used++;
                if (remove(pos)) {
                    removed++;
                }
            }
            long spent = System.nanoTime() - start;
            nanos += spent;
            maxTickNanos = Math.max(maxTickNanos, spent);
            ticks++;
            return used;
        }

        private boolean remove(BlockPos pos) {
            if (!level.isLoaded(pos)) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getDestroySpeed(level, pos) < 0
                    || state.getBlock().getExplosionResistance() >= RESISTANCE_LIMIT) {
                return false;
            }
            // onRemove runs regardless of flags and would spill chest contents; drop the
            // block entity first so the container has nothing to spill.
            if (state.hasBlockEntity()) {
                level.removeBlockEntity(pos);
            }
            return level.setBlock(pos, Blocks.AIR.defaultBlockState(), REMOVE_FLAGS);
        }

        boolean isDone() {
            return next >= positions.size();
        }

        void finish() {
            int fires = placeFires();
            LOGGER.info("Meteor crater at {}: radius {}, depth {}, {} blocks removed over {} tick(s), "
                            + "{} ms total, {} ms worst tick, {} fires",
                    center.toShortString(), radius, depth, removed, ticks,
                    String.format("%.2f", nanos / 1e6), String.format("%.2f", maxTickNanos / 1e6), fires);
        }

        /** A handful of fires on the crater floor. Capped hard: spreading fire is what eats TPS. */
        private int placeFires() {
            RandomSource random = level.getRandom();
            int placed = 0;
            for (int attempt = 0; attempt < FIRE_ATTEMPTS && placed < MAX_FIRES; attempt++) {
                int dx = Math.round((random.nextFloat() * 2 - 1) * radius * 0.7f);
                int dz = Math.round((random.nextFloat() * 2 - 1) * radius * 0.7f);
                BlockPos column = center.offset(dx, 0, dz);
                // From the top of the carved volume down: the first air-on-solid is the crater floor.
                for (int dy = up; dy >= -depth - 2; dy--) {
                    BlockPos pos = column.above(dy);
                    if (level.getBlockState(pos).isAir() && !level.getBlockState(pos.below()).isAir()) {
                        BlockState fire = BaseFireBlock.getState(level, pos);
                        if (fire.canSurvive(level, pos)) {
                            level.setBlock(pos, fire, Block.UPDATE_ALL);
                            placed++;
                        }
                        break;
                    }
                }
            }
            return placed;
        }
    }

    private MeteorImpact() {
    }
}
