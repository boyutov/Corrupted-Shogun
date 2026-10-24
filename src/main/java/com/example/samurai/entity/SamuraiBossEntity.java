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

    public static final String MAIN_CONTROLLER = "main";
    public static final String DEATH_CONTROLLER = "death";

    /** Death clip is 3.2 s = 64 ticks; keep the body around until it finishes. */
    private static final int DEATH_ANIMATION_TICKS = 64;

    /** Ticks of wind-up before attack_heavy actually lands (1.0 s in the clip). */
    private static final int HEAVY_IMPACT_DELAY = 20;
    /** Ticks of wind-up before attack_slash lands (0.38 s in the clip). */
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
    /** Blocks per tick. 1.0 covers the dash in 50 ticks (2.5 s). */
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

        if (!this.level().isClientSide()) {
            if (this.getPhase() != PHASE_NONE) {
                this.tickPhase();
                this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
                return;
            }
            this.maybeStartPhase();

            if (this.attackAnimationTicks > 0) {
                this.attackAnimationTicks--;
                // Lock the samurai in place for the duration of the swing.
                this.getNavigation().stop();
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.2D, 1.0D, 0.2D));
            }
            if (this.pendingHitTicks > 0 && --this.pendingHitTicks == 0) {
                this.performAttackHit(this.pendingHeavy);
            }
            this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        }
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
                this.getBoundingBox().inflate(reach), e -> e != this && e.isAlive() && !this.isAlliedTo(e))) {

            if (arc < 360.0D) {
                double dx = victim.getX() - this.getX();
                double dz = victim.getZ() - this.getZ();
                double angle = Math.toDegrees(Math.atan2(dz, dx)) - 90.0D - this.getYRot();
                angle = Math.abs(net.minecraft.util.Mth.wrapDegrees(angle));
                if (angle > arc / 2.0D) continue;
            }

            victim.hurt(this.damageSources().mobAttack(this), heavy ? damage * 1.8F : damage);
            if (heavy) {
                victim.knockback(1.4D, this.getX() - victim.getX(), this.getZ() - victim.getZ());
                victim.setDeltaMovement(victim.getDeltaMovement().add(0.0D, 0.55D, 0.0D));
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
        this.setPhase(PHASE_WINDUP);
    }

    private void tickPhase() {
        this.phaseTicks++;
        this.getNavigation().stop();

        switch (this.getPhase()) {
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
            victim.hurt(this.damageSources().mobAttack(this), damage);
            victim.knockback(1.1D, -this.dashDirection.x, -this.dashDirection.z);
            victim.setDeltaMovement(victim.getDeltaMovement().add(0.0D, 0.4D, 0.0D));
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
                new AnimationController<>(this, MAIN_CONTROLLER, 5, this::mainPredicate)
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
        if (this.isDeadOrDying()) {
            return PlayState.STOP;
        }
        // The charge phase is driven by synced state, not by triggers, so it wins
        // over everything below and survives the player re-entering render range.
        switch (this.getPhase()) {
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
