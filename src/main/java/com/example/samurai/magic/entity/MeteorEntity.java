package com.example.samurai.magic.entity;

import com.example.samurai.magic.registry.MagicEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Falling 7-block meteor. Moves straight down at a fixed speed on both sides
 * (no gravity, no physics), ignores entities in flight, and detonates where its
 * bottom-centre first meets a block. Position is the bottom centre of the body.
 */
public class MeteorEntity extends Entity {

    public static final float SIZE = 7f;
    public static final double SPEED = 1.5;
    /** Safety net: discard if something keeps it from landing for this long. */
    private static final int MAX_LIFETIME = 200;

    @Nullable
    private UUID ownerId;
    private int spellLevel = 1;

    public MeteorEntity(EntityType<? extends MeteorEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public MeteorEntity(Level level, ServerPlayer owner, int spellLevel) {
        this(MagicEntities.METEOR.get(), level);
        this.ownerId = owner.getUUID();
        this.spellLevel = spellLevel;
    }

    @Nullable
    public UUID getOwnerId() {
        return ownerId;
    }

    public int getSpellLevel() {
        return spellLevel;
    }

    @Override
    public void tick() {
        Vec3 from = position();
        Vec3 to = from.add(getDeltaMovement());

        if (level() instanceof ServerLevel server) {
            BlockHitResult hit = server.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, this));
            if (hit.getType() != HitResult.Type.MISS) {
                MeteorImpact.impact(server, this, hit);
                discard();
                return;
            }
            if (tickCount > MAX_LIFETIME || to.y < server.getMinBuildHeight()) {
                discard();
                return;
            }
        } else {
            spawnTrail();
        }
        setPos(to);
    }

    /** Fire trail: particles only, never fire blocks. */
    private void spawnTrail() {
        RandomSource random = level().getRandom();
        double half = SIZE / 2;
        // Particles drift back along the flight path.
        Vec3 back = getDeltaMovement().normalize().scale(-0.5);
        for (int i = 0; i < 14; i++) {
            double x = getX() + (random.nextDouble() - 0.5) * SIZE;
            double y = getY() + random.nextDouble() * SIZE;
            double z = getZ() + (random.nextDouble() - 0.5) * SIZE;
            double k = 0.8 + random.nextDouble() * 1.2;
            level().addAlwaysVisibleParticle(ParticleTypes.FLAME, true, x, y, z,
                    back.x * k + (random.nextDouble() - 0.5) * 0.1, back.y * k, back.z * k + (random.nextDouble() - 0.5) * 0.1);
        }
        for (int i = 0; i < 5; i++) {
            level().addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true,
                    getX() + (random.nextDouble() - 0.5) * half + back.x * SIZE, getY() + half + back.y * SIZE,
                    getZ() + (random.nextDouble() - 0.5) * half + back.z * SIZE, back.x * 0.6, back.y * 0.6, back.z * 0.6);
        }
        if (random.nextInt(2) == 0) {
            level().addAlwaysVisibleParticle(ParticleTypes.LAVA, true,
                    getX() + (random.nextDouble() - 0.5) * SIZE, getY() + random.nextDouble() * SIZE,
                    getZ() + (random.nextDouble() - 0.5) * SIZE, 0, 0, 0);
        }
    }

    /** Vanilla's spawn packet carries the velocity but recreateFromPacket ignores it; the client needs it to fly the same path. */
    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        setDeltaMovement(packet.getXa(), packet.getYa(), packet.getZa());
    }

    public void launchTowards(Vec3 target, double speed) {
        setDeltaMovement(target.subtract(position()).normalize().scale(speed));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256 * 256;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        spellLevel = Math.max(1, tag.getInt("SpellLevel"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
        tag.putInt("SpellLevel", spellLevel);
    }
}
