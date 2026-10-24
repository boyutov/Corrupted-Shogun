package com.example.samurai.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import com.example.samurai.client.vfx.SamuraiVfx;
import com.example.samurai.registry.ModItems;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;

/**
 * "Samurai Shogun" — a 3-block-tall GeckoLib 4 boss for Forge 1.20.1 (Mojang mappings).
 *
 * Controllers:
 *   "main"   — idle / walk, plus the triggerable attack + defend clips.
 *   "death"  — plays once when the entity starts dying and holds the last frame.
 */
public class SamuraiBossEntity extends Monster implements GeoEntity {

    // ---- animation definitions ------------------------------------------------
    private static final RawAnimation IDLE =
            RawAnimation.begin().thenLoop("animation.samurai_shogun.idle");
    private static final RawAnimation WALK =
            RawAnimation.begin().thenLoop("animation.samurai_shogun.walk");
    private static final RawAnimation SLASH =
            RawAnimation.begin().thenPlay("animation.samurai_shogun.attack_slash");
    private static final RawAnimation HEAVY =
            RawAnimation.begin().thenPlay("animation.samurai_shogun.attack_heavy");
    private static final RawAnimation DEFEND =
            RawAnimation.begin().thenLoop("animation.samurai_shogun.defend_stance");
    private static final RawAnimation DEATH =
            RawAnimation.begin().thenPlayAndHold("animation.samurai_shogun.death");
    private static final RawAnimation CHARGE_WINDUP =
            RawAnimation.begin().thenPlay("animation.samurai_shogun.phase_charge_windup");
    private static final RawAnimation CHARGE_RUN =
            RawAnimation.begin().thenLoop("animation.samurai_shogun.phase_charge_run");
    private static final RawAnimation HEAL_GUARD =
            RawAnimation.begin().thenLoop("animation.samurai_shogun.phase_heal_guard");

    private static final RawAnimation SKYFALL =
            RawAnimation.begin().thenPlay("animation.samurai_shogun.skyfall");
    private static final RawAnimation ENRAGE_ROAR =
            RawAnimation.begin().thenPlay("animation.samurai_shogun.enrage_roar");

    public static final String MAIN_CONTROLLER = "main";
    public static final String DEATH_CONTROLLER = "death";

    /** Death clip is 3.2 s = 64 ticks; keep the body around until it finishes. */
    private static final int DEATH_ANIMATION_TICKS = 64;

    /**
     * Blend GeckoLib plays before a triggered clip reaches its time 0. The attack clips start
     * in their chamber pose and are authored against it (tools/build_boss_animations.py), so
     * changing this shifts every visible impact away from the server hit frames below.
     */
    private static final int ATTACK_BLEND_TICKS = 5;
    /** Ticks of wind-up before attack_heavy actually lands (blend + 0.75 s in the clip). */
    private static final int HEAVY_IMPACT_DELAY = 20;
    /** Ticks of wind-up before attack_slash lands (blend + 0.15 s in the clip). */
    private static final int SLASH_IMPACT_DELAY = 8;

    private static final EntityDataAccessor<Boolean> DEFENDING =
            SynchedEntityData.defineId(SamuraiBossEntity.class, EntityDataSerializers.BOOLEAN);

    // ---- charge phase ---------------------------------------------------------
    public static final int PHASE_NONE = 0;
    public static final int PHASE_WINDUP = 1;
    public static final int PHASE_DASH = 2;
    public static final int PHASE_GUARD = 3;

    private static final EntityDataAccessor<Integer> PHASE =
            SynchedEntityData.defineId(SamuraiBossEntity.class, EntityDataSerializers.INT);

    /** Matches the 1.0 s phase_charge_windup clip. */
    private static final int WINDUP_TICKS = 20;
    /** Blocks per tick; the 20-block dash normally takes about one second. */
    private static final double DASH_SPEED = 1.0D;
    private static final double DASH_DISTANCE = 20.0D;
    /** Safety net so a dash into geometry can never hang the fight. */
    private static final int DASH_MAX_TICKS = 36;
    private static final float DASH_DAMAGE_MULTIPLIER = 1.5F;
    /** 0.5 per tick = 10 HP per second. */
    private static final float GUARD_HEAL_PER_TICK = 0.5F;
    /** Cap so the boss can't heal forever if the player simply walks away. */
    private static final int GUARD_MAX_TICKS = 300;
    private static final int GUARD_TURN_TICKS = 8;

    public static final int PHASE_SKYFALL = 4;
    public static final int PHASE_ROAR = 5;
    public static final int SKY_CROUCH_TICKS = 16;
    public static final int SKY_FLIGHT_TICKS = 24;
    public static final int SKY_IMPACT_TICK = SKY_CROUCH_TICKS + SKY_FLIGHT_TICKS;
    public static final int SKY_END_TICK = SKY_IMPACT_TICK + 20;
    public static final int ROAR_IMPACT_TICK = 20;
    public static final int ROAR_END_TICK = 44;
    public static final int WAVE_SPACING = 14;
    public static final int WAVE_LIFETIME = 24;
    public static final double WAVE_SPEED = 0.55D;
    public static final double WAVE_HEIGHT = 0.65D;
    public static final int SKY_COOLDOWN = 220;
    public static final int ENRAGED_SKY_COOLDOWN = 120;
    public static final byte SKY_IMPACT_EVENT = 61;
    public static final byte ROAR_EVENT = 62;
    private static final UUID RAGE_SPEED_ID = UUID.fromString("663a729e-8873-45d3-a233-df015925cd20");
    private static final UUID RAGE_DAMAGE_ID = UUID.fromString("663a729e-8873-45d3-a233-df015925cd21");
    private static final EntityDataAccessor<Boolean> ENRAGED =
            SynchedEntityData.defineId(SamuraiBossEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> PHASE_STARTED =
            SynchedEntityData.defineId(SamuraiBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> SKY_POINT =
            SynchedEntityData.defineId(SamuraiBossEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Float> SKY_POINT_Y =
            SynchedEntityData.defineId(SamuraiBossEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> WAVE_STARTED =
            SynchedEntityData.defineId(SamuraiBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> WAVES_ACTIVE =
            SynchedEntityData.defineId(SamuraiBossEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Integer> SWING_STARTED =
            SynchedEntityData.defineId(SamuraiBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SWING_IMPACT =
            SynchedEntityData.defineId(SamuraiBossEntity.class, EntityDataSerializers.INT);
    private int skyCooldown = 100;
    private Vec3 skyOrigin = Vec3.ZERO;
    private float specialYaw;
    private final List<Set<UUID>> waveVictims = List.of(new HashSet<>(), new HashSet<>(), new HashSet<>());
    private int phaseTicks;
    private float nextPhaseThreshold = 0.75F;
    private Vec3 dashOrigin = Vec3.ZERO;
    private Vec3 dashDirection = Vec3.ZERO;
    private float guardYaw;
    private float guardYawStart;
    private final Set<Integer> dashVictims = new HashSet<>();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            this.getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);

    /** >0 while an attack animation is playing; the mob stays put and cannot re-attack. */
    private int attackAnimationTicks;
    /** Counts down to the frame where damage is dealt. */
    private int pendingHitTicks;
    private boolean pendingHeavy;

    public SamuraiBossEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 250;
        this.setPersistenceRequired();
        this.bossEvent.setDarkenScreen(true);
    }

    // ---- attributes -----------------------------------------------------------
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 600.0D)
                .add(Attributes.ATTACK_DAMAGE, 16.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.ARMOR, 16.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 10.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 2.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DEFENDING, false);
        this.entityData.define(PHASE, PHASE_NONE);
        this.entityData.define(ENRAGED, false);
        this.entityData.define(PHASE_STARTED, 0);
        this.entityData.define(SKY_POINT, BlockPos.ZERO);
        this.entityData.define(SKY_POINT_Y, 0.0F);
        this.entityData.define(WAVE_STARTED, 0);
        this.entityData.define(WAVES_ACTIVE, false);
        this.entityData.define(SWING_STARTED, 0);
        this.entityData.define(SWING_IMPACT, 0);
    }

    // ---- goals ----------------------------------------------------------------
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SamuraiMeleeGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ---- tick / state ---------------------------------------------------------
    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            if (this.isAlive()) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SamuraiVfx.tick(this));
            }
            return;
        }
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        if (this.isDeadOrDying()) {
            this.entityData.set(WAVES_ACTIVE, false);
            this.pendingHitTicks = 0;
            return;
        }
        this.tickWaves();
        if (this.skyCooldown > 0) this.skyCooldown--;
        if (this.getPhase() != PHASE_NONE) {
            this.tickPhase();
            return;
        }
        // Finish committed swings before changing state, keeping the impact and animation aligned.
        if (this.attackAnimationTicks == 0) {
            if (!this.isEnraged() && this.getHealth() <= this.getMaxHealth() * 0.30F) {
                this.beginRage();
            } else {
                this.maybeStartPhase();
                if (!this.isInPhase() && this.skyCooldown <= 0) this.tryStartSkyfall();
            }
            if (this.isInPhase()) return;
        }
        if (this.attackAnimationTicks > 0) {
            this.attackAnimationTicks--;
            this.getNavigation().stop();
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.2D, 1.0D, 0.2D));
        }
        if (this.pendingHitTicks > 0 && --this.pendingHitTicks == 0) {
            this.performAttackHit(this.pendingHeavy);
        }
    }

    @Override
    public void travel(Vec3 input) {
        // The server moves along a collision-checked arc. Suppress ordinary gravity/AI
        // during flight on both sides; never persist a no-gravity flag to the world.
        if (this.isAlive() && this.getPhase() == PHASE_SKYFALL
                && this.getPhaseAge() >= SKY_CROUCH_TICKS && this.getPhaseAge() <= SKY_IMPACT_TICK) {
            this.setDeltaMovement(Vec3.ZERO);
            this.fallDistance = 0;
            return;
        }
        super.travel(input);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        // Damage is applied from the animation's impact frame instead of instantly.
        if (this.isBusy() || !(target instanceof LivingEntity)) {
            return false;
        }
        this.pendingHeavy = this.random.nextFloat() < 0.35F;
        this.pendingHitTicks = this.pendingHeavy ? HEAVY_IMPACT_DELAY : SLASH_IMPACT_DELAY;
        this.attackAnimationTicks = this.pendingHeavy ? 36 : 20;
        this.entityData.set(SWING_STARTED, (int) this.level().getGameTime());
        this.entityData.set(SWING_IMPACT, this.pendingHitTicks);

        this.triggerAnim(MAIN_CONTROLLER, this.pendingHeavy
                ? "animation.samurai_shogun.attack_heavy"
                : "animation.samurai_shogun.attack_slash");
        this.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        return true;
    }

    /** Applies the actual damage on the animation's impact frame. */
    private void performAttackHit(boolean heavy) {
        float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double reach = heavy ? 5.0D : 4.0D;
        double arc = heavy ? 360.0D : 150.0D;

        for (LivingEntity victim : this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(reach), this::canHit)) {

            if (victim.position().subtract(this.position()).horizontalDistance() > reach
                    || !this.hasLineOfSight(victim)) continue;

            if (arc < 360.0D) {
                double dx = victim.getX() - this.getX();
                double dz = victim.getZ() - this.getZ();
                double angle = Math.toDegrees(Math.atan2(dz, dx)) - 90.0D - this.getYRot();
                angle = Math.abs(net.minecraft.util.Mth.wrapDegrees(angle));
                if (angle > arc / 2.0D) continue;
            }

            boolean hit = victim.hurt(this.damageSources().mobAttack(this), heavy ? damage * 1.8F : damage);
            if (hit) this.hitFlash(victim);
            if (hit && heavy) {
                victim.knockback(1.4D, this.getX() - victim.getX(), this.getZ() - victim.getZ());
                victim.setDeltaMovement(victim.getDeltaMovement().add(0.0D, 0.55D, 0.0D));
                victim.hurtMarked = true;
            }
        }

        if (heavy) {
            this.level().broadcastEntityEvent(this, (byte) 4);
            this.playSound(SoundEvents.GENERIC_EXPLODE, 1.0F, 1.4F);
        }
    }

    // ---- charge phase ---------------------------------------------------------
    public int getPhase() {
        return this.entityData.get(PHASE);
    }

    private void setPhase(int phase) {
        this.entityData.set(PHASE, phase);
        this.phaseTicks = 0;
        this.entityData.set(PHASE_STARTED, (int) this.level().getGameTime());
    }

    public boolean isInPhase() {
        return this.getPhase() != PHASE_NONE;
    }

    /**
     * Fires once every time the boss drops through a quarter of its health:
     * at 75%, 50% and 25%. Needs a target, otherwise the dash has no direction.
     */
    private void maybeStartPhase() {
        if (this.nextPhaseThreshold <= 0.0F || this.isDeadOrDying()) {
            return;
        }
        if (this.getHealth() > this.getMaxHealth() * this.nextPhaseThreshold) {
            return;
        }
        if (this.getTarget() == null || !this.getTarget().isAlive()) {
            return;
        }
        this.nextPhaseThreshold -= 0.25F;
        this.attackAnimationTicks = 0;
        this.pendingHitTicks = 0;
        this.getNavigation().stop();
        this.stopTriggeredAnimation(MAIN_CONTROLLER, null);
        this.setPhase(PHASE_WINDUP);
    }

    private void tickPhase() {
        this.phaseTicks++;
        this.getNavigation().stop();

        switch (this.getPhase()) {
            case PHASE_SKYFALL -> this.tickSkyfall();
            case PHASE_ROAR -> this.tickRoar();
            case PHASE_WINDUP -> {
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.0D, 1.0D, 0.0D));
                LivingEntity target = this.getTarget();
                if (target != null) {
                    this.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.FEET, target.position());
                    this.setYBodyRot(this.getYRot());
                }
                if (this.phaseTicks >= WINDUP_TICKS) {
                    this.beginDash();
                }
            }
            case PHASE_DASH -> {
                this.setDeltaMovement(this.dashDirection.x * DASH_SPEED,
                        this.getDeltaMovement().y,
                        this.dashDirection.z * DASH_SPEED);
                this.hurtEntitiesInPath();

                double travelled = this.position().subtract(this.dashOrigin).horizontalDistance();
                boolean blocked = this.horizontalCollision && this.phaseTicks > 4;
                if (travelled >= DASH_DISTANCE || blocked || this.phaseTicks >= DASH_MAX_TICKS) {
                    this.beginGuard();
                }
            }
            case PHASE_GUARD -> {
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.0D, 1.0D, 0.0D));

                // Turn to face back down the dash line over a few ticks rather
                // than snapping, so the 180 reads as a movement.
                if (this.phaseTicks <= GUARD_TURN_TICKS) {
                    float t = this.phaseTicks / (float) GUARD_TURN_TICKS;
                    float yaw = this.guardYawStart + net.minecraft.util.Mth.wrapDegrees(this.guardYaw - this.guardYawStart) * t;
                    this.setYRot(yaw);
                    this.setYBodyRot(yaw);
                    this.setYHeadRot(yaw);
                }

                if (this.getHealth() < this.getMaxHealth()) {
                    this.heal(GUARD_HEAL_PER_TICK);
                }
                if (this.phaseTicks >= GUARD_MAX_TICKS || this.getHealth() >= this.getMaxHealth()) {
                    this.endPhase();
                }
            }
            default -> this.endPhase();
        }
    }

    private void beginDash() {
        LivingEntity target = this.getTarget();
        Vec3 dir = target != null
                ? target.position().subtract(this.position())
                : Vec3.directionFromRotation(0.0F, this.getYRot());
        dir = new Vec3(dir.x, 0.0D, dir.z);
        if (dir.lengthSqr() < 1.0E-4D) {
            dir = Vec3.directionFromRotation(0.0F, this.getYRot());
            dir = new Vec3(dir.x, 0.0D, dir.z);
        }
        this.dashDirection = dir.normalize();
        this.dashOrigin = this.position();
        this.dashVictims.clear();

        float yaw = (float) (Math.toDegrees(Math.atan2(-this.dashDirection.x, this.dashDirection.z)));
        this.setYRot(yaw);
        this.setYBodyRot(yaw);
        this.setYHeadRot(yaw);

        this.setPhase(PHASE_DASH);
    }

    /** Everything the boss ploughs through takes the hit once. */
    private void hurtEntitiesInPath() {
        float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * DASH_DAMAGE_MULTIPLIER;
        for (LivingEntity victim : this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(1.5D),
                e -> e != this && e.isAlive() && !this.isAlliedTo(e))) {

            if (!this.dashVictims.add(victim.getId())) {
                continue;
            }
            if (!victim.hurt(this.damageSources().mobAttack(this), damage)) continue;
            this.hitFlash(victim);
            victim.knockback(1.1D, -this.dashDirection.x, -this.dashDirection.z);
            victim.setDeltaMovement(victim.getDeltaMovement().add(0.0D, 0.4D, 0.0D));
            victim.hurtMarked = true;
        }
    }

    private void beginGuard() {
        this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
        this.guardYawStart = this.getYRot();
        this.guardYaw = net.minecraft.util.Mth.wrapDegrees(this.getYRot() + 180.0F);
        this.setPhase(PHASE_GUARD);
    }

    private void endPhase() {
        this.setPhase(PHASE_NONE);
        this.dashVictims.clear();
    }

    // ---- skyfall / enrage -----------------------------------------------------
    public boolean isSwordTrailActive() {
        if (!this.isAlive()) return false;
        if (this.getPhase() == PHASE_DASH) return true;
        if (this.getPhase() == PHASE_SKYFALL) return this.getPhaseAge() >= 28 && this.getPhaseAge() <= 44;
        int age = (int) this.level().getGameTime() - this.entityData.get(SWING_STARTED);
        int impact = this.entityData.get(SWING_IMPACT);
        return !this.isInPhase() && impact > 0 && age >= impact - 5 && age <= impact + 5;
    }

    public boolean isEnraged() {
        return this.entityData.get(ENRAGED);
    }

    public int getPhaseAge() {
        return Math.max(0, (int) this.level().getGameTime() - this.entityData.get(PHASE_STARTED));
    }

    public int getWaveAge() {
        return this.entityData.get(WAVES_ACTIVE)
                ? Math.max(0, (int) this.level().getGameTime() - this.entityData.get(WAVE_STARTED)) : -1;
    }

    public Vec3 getSkyPoint() {
        BlockPos p = this.entityData.get(SKY_POINT);
        return new Vec3(p.getX() + 0.5D, p.getY() + this.entityData.get(SKY_POINT_Y), p.getZ() + 0.5D);
    }

    public static double waveRadius(double age) {
        return 0.75D + age * WAVE_SPEED;
    }

    /** Local collision surface, also used by the renderer so rings follow slabs and slopes. */
    public double groundHeight(double x, double z, double referenceY) {
        if (!this.level().hasChunkAt(BlockPos.containing(x, referenceY, z))) return Double.NaN;
        var hit = this.level().clip(new ClipContext(new Vec3(x, referenceY + 2.0D, z),
                new Vec3(x, referenceY - 3.0D, z), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().y : Double.NaN;
    }

    private Vec3 skyPosition(double t, Vec3 destination) {
        // Fast lift, suspended apex, accelerating descent; authored poses supply the anticipation.
        double horizontal = t * t * (3.0D - 2.0D * t);
        return this.skyOrigin.lerp(destination, horizontal).add(0, 6.0D * Math.sin(Math.PI * t), 0);
    }

    private void tryStartSkyfall() {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive() || !this.onGround() || this.isInWater()) return;
        if (this.distanceToSqr(target) > 18.0D * 18.0D || !this.hasLineOfSight(target)) return;
        BlockPos targetBlock = target.blockPosition();
        double x = targetBlock.getX() + 0.5D, z = targetBlock.getZ() + 0.5D;
        double y = this.groundHeight(x, z, target.getY());
        this.skyCooldown = 40; // retry later if the arena does not have room for the leap
        if (!Double.isFinite(y) || Math.abs(y - this.getY()) > 4.0D) return;
        Vec3 destination = new Vec3(x, y, z);
        this.skyOrigin = this.position();
        AABB box = this.getBoundingBox().deflate(0.02D);
        for (int i = 1; i <= SKY_FLIGHT_TICKS; i++) {
            Vec3 at = this.skyPosition(i / (double) SKY_FLIGHT_TICKS, destination);
            if (!this.level().hasChunkAt(BlockPos.containing(at))
                    || !this.level().noCollision(this, box.move(at.subtract(this.position())))) return;
        }
        BlockPos point = BlockPos.containing(destination);
        this.entityData.set(SKY_POINT, point);
        this.entityData.set(SKY_POINT_Y, (float) (y - point.getY()));
        this.skyCooldown = this.isEnraged() ? ENRAGED_SKY_COOLDOWN : SKY_COOLDOWN;
        this.beginSpecial(PHASE_SKYFALL);
        this.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.FEET, destination);
        this.specialYaw = this.getYRot();
        this.playSound(SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.65F);
    }

    private void beginSpecial(int phase) {
        this.pendingHitTicks = 0;
        this.attackAnimationTicks = 0;
        this.getNavigation().stop();
        this.stopTriggeredAnimation(MAIN_CONTROLLER, null);
        this.specialYaw = this.getYRot();
        this.setPhase(phase);
    }

    private void tickSkyfall() {
        this.lockSpecialFacing();
        if (this.phaseTicks <= SKY_CROUCH_TICKS) {
            this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
            return;
        }
        if (this.phaseTicks <= SKY_IMPACT_TICK) {
            this.fallDistance = 0;
            this.setDeltaMovement(Vec3.ZERO);
            Vec3 next = this.skyPosition((this.phaseTicks - SKY_CROUCH_TICKS)
                    / (double) SKY_FLIGHT_TICKS, this.getSkyPoint());
            this.move(MoverType.SELF, next.subtract(this.position()));
            // A newly placed block may interrupt the pre-checked arc. Land naturally without
            // hitting through the obstruction or leaving the boss stuck in a flight state.
            if (this.position().distanceToSqr(next) > 0.12D) {
                this.endPhase();
                this.skyCooldown = 60;
                return;
            }
            if (this.phaseTicks == SKY_IMPACT_TICK) this.skyImpact();
        } else {
            this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
            if (this.phaseTicks >= SKY_END_TICK) this.endPhase();
        }
    }

    private void skyImpact() {
        this.entityData.set(WAVE_STARTED, (int) this.level().getGameTime());
        this.entityData.set(WAVES_ACTIVE, true);
        this.waveVictims.forEach(Set::clear);
        this.level().broadcastEntityEvent(this, SKY_IMPACT_EVENT);
        this.playSound(SoundEvents.GENERIC_EXPLODE, 2.0F, 0.55F);
        Vec3 point = this.getSkyPoint();
        for (LivingEntity victim : this.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(point.add(-2.5, -0.2, -2.5), point.add(2.5, 3, 2.5)), this::canHit)) {
            if (victim.position().subtract(point).horizontalDistance() > 2.5D
                    || !this.hasLineOfSight(victim)) continue;
            if (victim.hurt(this.damageSources().mobAttack(this),
                    (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.8F)) {
                this.hitFlash(victim);
                this.pushAway(victim, point, 1.2D, 0.35D);
            }
        }
    }

    private boolean canHit(LivingEntity victim) {
        return victim != this && victim.isAlive() && !this.isAlliedTo(victim)
                && !(victim instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    private void tickWaves() {
        int age = this.getWaveAge();
        if (age < 0) return;
        if (age >= WAVE_SPACING * 2 + WAVE_LIFETIME) {
            this.entityData.set(WAVES_ACTIVE, false);
            this.waveVictims.forEach(Set::clear);
            return;
        }
        Vec3 point = this.getSkyPoint();
        double maxRadius = waveRadius(WAVE_LIFETIME) + 1;
        for (LivingEntity victim : this.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(point.add(-maxRadius, -3, -maxRadius), point.add(maxRadius, 3, maxRadius)), this::canHit)) {
            double distance = victim.position().subtract(point).horizontalDistance();
            double ground = this.groundHeight(victim.getX(), victim.getZ(), point.y);
            // Feet, not onGround(): falling/ascending through a low ring is still a hit,
            // while an ordinary jump clears its 0.65-block crest.
            if (!Double.isFinite(ground) || victim.getY() > ground + WAVE_HEIGHT
                    || victim.getBoundingBox().maxY < ground) continue;
            var obstruction = this.level().clip(new ClipContext(point.add(0, 0.7D, 0),
                    new Vec3(victim.getX(), ground + 0.7D, victim.getZ()),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (obstruction.getType() != HitResult.Type.MISS) continue;
            for (int wave = 0; wave < 3; wave++) {
                int waveAge = age - wave * WAVE_SPACING;
                if (waveAge < 0 || waveAge >= WAVE_LIFETIME) continue;
                double radius = waveRadius(waveAge);
                double halfWidth = 0.25D + victim.getBbWidth() * 0.5D;
                if (distance < radius - WAVE_SPEED - halfWidth || distance > radius + halfWidth
                        || !this.waveVictims.get(wave).add(victim.getUUID())) continue;
                if (victim.hurt(this.damageSources().mobAttack(this),
                        (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.65F)) {
                    this.hitFlash(victim);
                }
            }
        }
    }

    private void beginRage() {
        this.setEnraged(true);
        this.skyCooldown = Math.min(this.skyCooldown, 60);
        this.beginSpecial(PHASE_ROAR);
    }

    private void setEnraged(boolean enraged) {
        this.entityData.set(ENRAGED, enraged);
        var speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        var damage = this.getAttribute(Attributes.ATTACK_DAMAGE);
        speed.removeModifier(RAGE_SPEED_ID);
        damage.removeModifier(RAGE_DAMAGE_ID);
        if (enraged) {
            speed.addTransientModifier(new AttributeModifier(RAGE_SPEED_ID, "Shogun rage speed", 0.25D,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
            damage.addTransientModifier(new AttributeModifier(RAGE_DAMAGE_ID, "Shogun rage damage", 0.35D,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        this.bossEvent.setColor(enraged ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.RED);
    }

    private void tickRoar() {
        this.lockSpecialFacing();
        this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
        if (this.phaseTicks == ROAR_IMPACT_TICK) {
            this.playSound(SoundEvents.RAVAGER_ROAR, 2.5F, 0.65F);
            this.level().broadcastEntityEvent(this, ROAR_EVENT);
            for (LivingEntity victim : this.level().getEntitiesOfClass(LivingEntity.class,
                    this.getBoundingBox().inflate(10), this::canHit)) {
                if (this.distanceToSqr(victim) <= 100 && this.hasLineOfSight(victim)) {
                    this.pushAway(victim, this.position(), 2.2D, 0.35D);
                }
            }
        }
        if (this.phaseTicks >= ROAR_END_TICK) this.endPhase();
    }

    private void lockSpecialFacing() {
        this.setYRot(this.specialYaw);
        this.setYBodyRot(this.specialYaw);
        this.setYHeadRot(this.specialYaw);
    }

    private void pushAway(LivingEntity victim, Vec3 origin, double strength, double lift) {
        victim.knockback(strength, origin.x - victim.getX(), origin.z - victim.getZ());
        victim.setDeltaMovement(victim.getDeltaMovement().add(0, lift, 0));
        victim.hurtMarked = true;
    }

    private void hitFlash(LivingEntity victim) {
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.FLASH, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5,
                    victim.getZ(), 1, 0, 0, 0, 0);
            server.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5,
                    victim.getZ(), 10, 0.25, 0.35, 0.25, 0.15);
        }
    }

    @Override
    public void handleEntityEvent(byte event) {
        if (event == SKY_IMPACT_EVENT || event == ROAR_EVENT) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SamuraiVfx.onCombatEvent(this, event));
        } else {
            super.handleEntityEvent(event);
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return this.getPhase() == PHASE_SKYFALL || this.getWaveAge() >= 0
                ? super.getBoundingBoxForCulling().inflate(24) : super.getBoundingBoxForCulling();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("NextPhaseThreshold", this.nextPhaseThreshold);
        tag.putBoolean("Enraged", this.isEnraged());
        tag.putInt("SkyCooldown", this.skyCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        float threshold = tag.contains("NextPhaseThreshold") ? tag.getFloat("NextPhaseThreshold") : 0.75F;
        this.nextPhaseThreshold = Float.isFinite(threshold) ? Mth.clamp(threshold, 0, 0.75F) : 0.75F;
        this.setEnraged(tag.getBoolean("Enraged"));
        this.skyCooldown = tag.contains("SkyCooldown") ? Mth.clamp(tag.getInt("SkyCooldown"), 40, SKY_COOLDOWN) : 100;
        // Transient attacks safely cancel on load; consumed health thresholds and rage remain.
        this.setPhase(PHASE_NONE);
        this.entityData.set(WAVES_ACTIVE, false);
        this.attackAnimationTicks = this.pendingHitTicks = 0;
        this.waveVictims.forEach(Set::clear);
    }

    public boolean isBusy() {
        return this.attackAnimationTicks > 0 || this.isInPhase();
    }

    public boolean isDefending() {
        return this.entityData.get(DEFENDING);
    }

    public void setDefending(boolean defending) {
        this.entityData.set(DEFENDING, defending);
        if (defending) {
            this.triggerAnim(MAIN_CONTROLLER, "animation.samurai_shogun.defend_stance");
        }
    }

    /** Any hit breaks the regenerating guard — that's the window to punish it. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !this.level().isClientSide() && this.getPhase() == PHASE_GUARD) {
            this.endPhase();
        }
        return hit;
    }

    // ---- death ----------------------------------------------------------------
    @Override
    protected void dropCustomDeathLoot(DamageSource damageSource, int lootingMultiplier, boolean allowDrops) {
        super.dropCustomDeathLoot(damageSource, lootingMultiplier, allowDrops);
        this.spawnAtLocation(new ItemStack(ModItems.SAMURAI_BLADE.get()));
    }

    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (this.deathTime >= DEATH_ANIMATION_TICKS && !this.level().isClientSide() && !this.isRemoved()) {
            this.level().broadcastEntityEvent(this, (byte) 60);
            this.remove(Entity.RemovalReason.KILLED);
        }
    }

    // ---- boss bar -------------------------------------------------------------
    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(net.minecraft.network.chat.Component name) {
        super.setCustomName(name);
        this.bossEvent.setName(this.getDisplayName());
    }

    // ---- misc boss traits -----------------------------------------------------
    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect) {
        return effect.getEffect() != net.minecraft.world.effect.MobEffects.POISON
                && effect.getEffect() != net.minecraft.world.effect.MobEffects.WITHER
                && super.canBeAffected(effect);
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEAD;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WITHER_SKELETON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_SKELETON_DEATH;
    }

    @Override
    protected float getStandingEyeHeight(net.minecraft.world.entity.Pose pose,
                                         net.minecraft.world.entity.EntityDimensions dims) {
        return 2.65F;
    }

    // ---- GeckoLib -------------------------------------------------------------
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<SamuraiBossEntity> main =
                new AnimationController<>(this, MAIN_CONTROLLER, ATTACK_BLEND_TICKS, this::mainPredicate)
                        .triggerableAnim("animation.samurai_shogun.attack_slash", SLASH)
                        .triggerableAnim("animation.samurai_shogun.attack_heavy", HEAVY)
                        .triggerableAnim("animation.samurai_shogun.defend_stance", DEFEND);

        // Keyframe hooks declared in the .animation.json. GeckoLib processes
        // animations client-side, so these only ever fire on a client — but this
        // method itself runs on both sides, so the client-only VFX class must be
        // reached through DistExecutor. Naming it directly here would load
        // client classes on a dedicated server and crash it.
        main.setSoundKeyframeHandler(event -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT, () -> () -> SamuraiVfx.onSoundKeyframe(event)));
        main.setParticleKeyframeHandler(event -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT, () -> () -> SamuraiVfx.onParticleKeyframe(event)));

        controllers.add(main);

        AnimationController<SamuraiBossEntity> death =
                new AnimationController<>(this, DEATH_CONTROLLER, 0, state -> {
            if (this.isDeadOrDying()) {
                return state.setAndContinue(DEATH);
            }
            state.getController().forceAnimationReset();
            return PlayState.STOP;
        });

        // The death clip carries its own armour-break and sword-drop keyframes.
        death.setSoundKeyframeHandler(event -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT, () -> () -> SamuraiVfx.onSoundKeyframe(event)));
        death.setParticleKeyframeHandler(event -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT, () -> () -> SamuraiVfx.onParticleKeyframe(event)));

        controllers.add(death);
    }

    private PlayState mainPredicate(AnimationState<SamuraiBossEntity> state) {
        state.getController().setTransitionLength(this.isInPhase() ? 0 : ATTACK_BLEND_TICKS);
        if (this.isDeadOrDying()) {
            return PlayState.STOP;
        }
        // The charge phase is driven by synced state, not by triggers, so it wins
        // over everything below and survives the player re-entering render range.
        switch (this.getPhase()) {
            case PHASE_SKYFALL -> {
                return state.setAndContinue(SKYFALL);
            }
            case PHASE_ROAR -> {
                return state.setAndContinue(ENRAGE_ROAR);
            }
            case PHASE_WINDUP -> {
                return state.setAndContinue(CHARGE_WINDUP);
            }
            case PHASE_DASH -> {
                return state.setAndContinue(CHARGE_RUN);
            }
            case PHASE_GUARD -> {
                return state.setAndContinue(HEAL_GUARD);
            }
            default -> {
            }
        }
        // Let a triggered one-shot (slash / heavy) finish before resuming locomotion.
        AnimationController<SamuraiBossEntity> controller = state.getController();
        if (controller.getAnimationState() != AnimationController.State.STOPPED
                && controller.getCurrentAnimation() != null
                && controller.getCurrentAnimation().loopType() == Animation.LoopType.PLAY_ONCE) {
            return PlayState.CONTINUE;
        }
        if (this.isDefending()) {
            return state.setAndContinue(DEFEND);
        }
        if (state.isMoving() && !this.isBusy()) {
            return state.setAndContinue(WALK);
        }
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ---------------------------------------------------------------------------
    /** Melee goal with a longer, boss-appropriate cooldown between swings. */
    static class SamuraiMeleeGoal extends MeleeAttackGoal {

        private final SamuraiBossEntity boss;
        private int cooldown;

        SamuraiMeleeGoal(SamuraiBossEntity boss) {
            super(boss, 1.0D, true);
            this.boss = boss;
        }

        @Override
        public void start() {
            super.start();
            this.cooldown = 0;
        }

        @Override
        public boolean canUse() {
            return !this.boss.isInPhase() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !this.boss.isInPhase() && super.canContinueToUse();
        }

        @Override
        public void tick() {
            super.tick();
            if (this.cooldown > 0) this.cooldown--;
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity target, double distToTargetSqr) {
            double reach = this.getAttackReachSqr(target);
            if (distToTargetSqr <= reach && this.cooldown <= 0 && !this.boss.isBusy()) {
                this.cooldown = 26 + this.boss.getRandom().nextInt(14);
                this.resetAttackCooldown();
                this.boss.doHurtTarget(target);
            }
        }

        @Override
        protected double getAttackReachSqr(LivingEntity target) {
            // Wide reach for a 3-block boss swinging a nodachi.
            return 4.0D + target.getBbWidth() * 2.0F + this.boss.getBbWidth();
        }
    }
}
