package com.example.samurai.magic.entity;

import com.example.samurai.magic.MagicConfig;
import com.example.samurai.magic.client.BlackHoleVfx;
import com.example.samurai.magic.network.AddVelocityPacket;
import com.example.samurai.magic.network.MagicNetwork;
import com.example.samurai.magic.registry.MagicEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Seven-second singularity. Pulls living things in sight within 30 blocks towards its
 * centre, holds the ones that reach it, and deals 2 magic damage to each once a second.
 * Never touches its caster or what the caster owns, items, vehicles or blocks. Drawn by
 * BlackHoleRenderer (horizon, disk, lensing) with a particle inflow from BlackHoleVfx.
 *
 * <p>Mobs are held for real. Players can only be nudged (their movement is client-side),
 * so for them it pulls but can be escaped.
 */
public class BlackHoleEntity extends Entity {

    public static final double RADIUS = 30.0;
    /** Inside this, mobs are held still instead of pulled further (pulling here only makes them jitter). */
    private static final double HOLD_RADIUS = 2.0;
    public static final int DURATION = 140;
    /** Once a second, in one hit: damage split into smaller, faster hits is eaten by invulnerability frames. */
    private static final int DAMAGE_INTERVAL = 20;
    private static final float DAMAGE = 2f;
    /** Pull acceleration per tick: a floor everywhere in range, plus more the closer the target. */
    private static final double PULL_BASE = 0.04;
    private static final double PULL_NEAR = 0.10;
    private static final double MAX_PULL_SPEED = 0.9;

    // build_model.py palette.
    public static final Vector3f VOID = rgb(13, 11, 19);
    public static final Vector3f PURPLE_LO = rgb(78, 36, 128);
    public static final Vector3f PURPLE = rgb(142, 76, 216);
    public static final Vector3f PURPLE_HI = rgb(206, 156, 255);

    @Nullable
    private UUID ownerId;

    public BlackHoleEntity(EntityType<? extends BlackHoleEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public BlackHoleEntity(Level level, ServerPlayer owner) {
        this(MagicEntities.BLACK_HOLE.get(), level);
        this.ownerId = owner.getUUID();
    }

    private static Vector3f rgb(int r, int g, int b) {
        return new Vector3f(r / 255f, g / 255f, b / 255f);
    }

    @Override
    public void tick() {
        if (level() instanceof ServerLevel server) {
            if (tickCount == 1) {
                server.playSound(null, getX(), getY(), getZ(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 2f, 0.5f);
            } else if (tickCount % 40 == 0) {
                server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 2f, 0.5f);
            }
            affectSurroundings(server);
            if (tickCount >= DURATION) {
                collapse(server);
                discard();
            }
        } else {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BlackHoleVfx.tick(this));
        }
    }

    private void affectSurroundings(ServerLevel level) {
        boolean damageTick = tickCount % DAMAGE_INTERVAL == 0;
        Player owner = ownerId == null ? null : level.getPlayerByUUID(ownerId);
        Vec3 center = position();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(RADIUS),
                this::canAffect)) {
            Vec3 body = target.getBoundingBox().getCenter();
            double distance = body.distanceTo(center);
            // Nothing through walls: pulling a mob into a block suffocates it.
            if (distance > RADIUS || !hasLineOfSight(level, center, body)) {
                continue;
            }
            if (damageTick) {
                // Magic bypasses armour; slowness and damage ignore knockback resistance.
                target.hurt(damageSources().indirectMagic(this, owner), DAMAGE);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DAMAGE_INTERVAL + 10, 1,
                        false, true));
            }
            pull(target, center, body, distance);
        }
    }

    /** Living things only (so never items or vehicles), minus the caster and anything the caster owns. */
    private boolean canAffect(LivingEntity target) {
        if (!target.isAlive() || target instanceof ArmorStand) {
            return false;
        }
        if (ownerId != null && (ownerId.equals(target.getUUID())
                || target instanceof OwnableEntity ownable && ownerId.equals(ownable.getOwnerUUID()))) {
            return false;
        }
        if (target instanceof Player player) {
            return MagicConfig.BLACK_HOLE_AFFECTS_PLAYERS.get() && !player.isSpectator() && !player.isCreative();
        }
        return true;
    }

    private boolean hasLineOfSight(ServerLevel level, Vec3 from, Vec3 to) {
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(from) >= from.distanceToSqr(to) - 0.25;
    }

    /**
     * Scaled by (1 - knockback resistance): the Shogun (0.9) barely feels it, so the pull
     * cannot fight the velocity his dash sets every tick and stall it.
     */
    private void pull(LivingEntity target, Vec3 center, Vec3 body, double distance) {
        double give = 1.0 - Mth.clamp(target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
        if (give <= 0) {
            return;
        }
        if (distance <= HOLD_RADIUS) {
            if (!(target instanceof ServerPlayer)) {
                target.setDeltaMovement(target.getDeltaMovement().scale(1.0 - give));
                target.resetFallDistance(); // only the drop after release should hurt
                target.hurtMarked = true;
            }
            return;
        }
        double strength = (PULL_BASE + PULL_NEAR * (1.0 - distance / RADIUS)) * give;
        Vec3 push = center.subtract(body).normalize().scale(strength);
        if (target instanceof ServerPlayer player) {
            MagicNetwork.sendToPlayer(player, new AddVelocityPacket(push));
            return;
        }
        Vec3 velocity = target.getDeltaMovement().add(push);
        // Never fast enough to fly past the centre in one tick.
        double cap = Math.min(MAX_PULL_SPEED, distance - HOLD_RADIUS);
        if (velocity.length() > cap) {
            velocity = velocity.normalize().scale(cap);
        }
        target.setDeltaMovement(velocity);
        target.hurtMarked = true;
    }

    private void collapse(ServerLevel level) {
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.5f, 0.4f);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.SHULKER_BULLET_HIT, SoundSource.PLAYERS, 2f, 0.5f);
        // Forced like the vortex, or a "Minimal particles" setting would hide the collapse.
        for (ServerPlayer viewer : level.players()) {
            level.sendParticles(viewer, ParticleTypes.REVERSE_PORTAL, true, getX(), getY(), getZ(), 120, 0.5, 0.5, 0.5, 0.6);
            level.sendParticles(viewer, new DustParticleOptions(PURPLE_HI, 2.5f), true, getX(), getY(), getZ(),
                    40, 1.5, 1.5, 1.5, 0);
        }
    }

    /** Client side: the flash and jolt of the collapse. */
    @Override
    public void onClientRemoval() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BlackHoleVfx.collapse(this));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
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
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
    }
}
