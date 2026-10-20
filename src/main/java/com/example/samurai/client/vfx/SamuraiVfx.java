package com.example.samurai.client.vfx;

import com.example.samurai.entity.SamuraiBossEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.keyframe.event.ParticleKeyframeEvent;
import software.bernie.geckolib.core.keyframe.event.SoundKeyframeEvent;
import software.bernie.geckolib.model.GeoModel;

import java.util.Optional;

/**
 * Client-side handler for the boss's particle and sound keyframes.
 *
 * Everything here runs on each client that is rendering the boss, because
 * GeckoLib processes animations client-side. That is why no packets are needed
 * for the dust, sparks or camera shake.
 *
 * NOTE: this class touches client-only types. It must only ever be reached
 * through DistExecutor from common code, never referenced directly.
 */
public final class SamuraiVfx {

    private static final RandomSource RANDOM = RandomSource.create();
    private static final double TAU = Math.PI * 2.0D;

    private static final Vector3f CORRUPT_PURPLE = new Vector3f(0.58F, 0.30F, 0.86F);
    private static final Vector3f CORRUPT_PALE = new Vector3f(0.81F, 0.61F, 1.00F);

    private SamuraiVfx() {
    }

    // -----------------------------------------------------------------------
    // Entry points
    // -----------------------------------------------------------------------
    public static void onParticleKeyframe(ParticleKeyframeEvent<SamuraiBossEntity> event) {
        SamuraiBossEntity boss = event.getAnimatable();
        Level level = boss.level();
        if (!level.isClientSide()) {
            return;
        }

        String effect = event.getKeyframeData().getEffect();
        String locator = event.getKeyframeData().getLocator();
        Vec3 at = resolve(boss, locator);

        switch (effect) {
            case "shockwave" -> shockwave(level, boss, at);
            case "corruption_slash" -> slashSparks(level, at);
            case "corruption_burst" -> burst(level, at);
            case "armor_break" -> armorBreak(level, at);
            case "katana_plant" -> katanaPlant(level, at);
            case "heal_pulse" -> healPulse(level, boss, at);
            default -> {
                // unknown effect id — ignore rather than crash the render thread
            }
        }
    }

    public static void onSoundKeyframe(SoundKeyframeEvent<SamuraiBossEntity> event) {
        SamuraiBossEntity boss = event.getAnimatable();
        Level level = boss.level();
        if (!level.isClientSide()) {
            return;
        }

        String effect = event.getKeyframeData().getSound();
        // Swap these for your own registered SoundEvents once you have them.
        SoundEvent sound;
        float volume = 1.0F;
        float pitch = 1.0F;

        switch (effect) {
            case "samurai_step" -> {
                sound = SoundEvents.NETHERITE_BLOCK_STEP;
                volume = 1.4F;
                pitch = 0.6F;
            }
            case "samurai_slash" -> {
                sound = SoundEvents.PLAYER_ATTACK_SWEEP;
                volume = 1.6F;
                pitch = 0.7F;
            }
            case "samurai_raise" -> {
                sound = SoundEvents.ANVIL_LAND;
                volume = 0.5F;
                pitch = 1.8F;
            }
            case "samurai_slam" -> {
                sound = SoundEvents.GENERIC_EXPLODE;
                volume = 1.5F;
                pitch = 0.6F;
            }
            case "katana_drop" -> {
                sound = SoundEvents.ANVIL_PLACE;
                volume = 0.9F;
                pitch = 1.2F;
            }
            case "armor_crash" -> {
                sound = SoundEvents.NETHERITE_BLOCK_BREAK;
                volume = 1.2F;
                pitch = 0.7F;
            }
            case "armor_break" -> {
                sound = SoundEvents.ITEM_BREAK;
                volume = 1.3F;
                pitch = 0.5F;
            }
            default -> {
                return;
            }
        }

        level.playLocalSound(boss.getX(), boss.getY(), boss.getZ(), sound,
                SoundSource.HOSTILE, volume, pitch, false);
    }

    // -----------------------------------------------------------------------
    // Locator resolution
    // -----------------------------------------------------------------------
    /**
     * Resolves a keyframe's locator string to a world position.
     *
     * The strings name BONES, not Blockbench locators: GeckoLib parses the
     * `locators` block but never bakes it into anything renderable, so a locator
     * cannot report a position. The model carries empty marker bones
     * (`blade_tip`, `blade_mid`, `blade_base`, `ground_impact`, `aura`) for this.
     *
     * Falls back to the entity's own position if the bone is missing or its
     * matrix has not been filled in yet.
     */
    private static Vec3 resolve(SamuraiBossEntity boss, String locator) {
        Vec3 fallback = boss.position().add(0, boss.getBbHeight() * 0.5D, 0);
        if (locator == null || locator.isEmpty()) {
            return fallback;
        }

        GeoModel<SamuraiBossEntity> model = currentModel(boss);
        if (model == null) {
            return fallback;
        }
        Optional<GeoBone> bone = model.getBone(locator);
        if (bone.isEmpty()) {
            return fallback;
        }
        Vector3d pos = bone.get().getWorldPosition();
        if (pos.x == 0 && pos.y == 0 && pos.z == 0) {
            return fallback;
        }
        return new Vec3(pos.x, pos.y, pos.z);
    }

    @SuppressWarnings("unchecked")
    private static GeoModel<SamuraiBossEntity> currentModel(SamuraiBossEntity boss) {
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(boss);
        if (renderer instanceof software.bernie.geckolib.renderer.GeoEntityRenderer<?> geo) {
            return (GeoModel<SamuraiBossEntity>) geo.getGeoModel();
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Effects
    // -----------------------------------------------------------------------
    /** Ground slam: ring of real terrain dust, a dome of smoke, and camera shake. */
    private static void shockwave(Level level, SamuraiBossEntity boss, Vec3 at) {
        double y = boss.getY();
        BlockPos below = BlockPos.containing(at.x, y - 0.2D, at.z);
        BlockState ground = level.getBlockState(below);
        ParticleOptions dust = ground.isAir()
                ? ParticleTypes.POOF
                : new BlockParticleOption(ParticleTypes.BLOCK, ground);

        // Three expanding rings so the wave reads as travelling outward.
        for (int ring = 0; ring < 3; ring++) {
            double radius = 1.1D + ring * 1.25D;
            int count = 26 + ring * 14;
            double speed = 0.26D + ring * 0.10D;
            for (int i = 0; i < count; i++) {
                double angle = (i / (double) count) * TAU + RANDOM.nextDouble() * 0.08D;
                double px = at.x + Math.cos(angle) * radius;
                double pz = at.z + Math.sin(angle) * radius;
                level.addParticle(dust, px, y + 0.15D, pz,
                        Math.cos(angle) * speed,
                        0.18D + RANDOM.nextDouble() * 0.22D,
                        Math.sin(angle) * speed);
            }
        }

        // Low dust cloud sitting on the ground
        for (int i = 0; i < 40; i++) {
            double angle = RANDOM.nextDouble() * TAU;
            double radius = RANDOM.nextDouble() * 3.4D;
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    at.x + Math.cos(angle) * radius,
                    y + 0.05D + RANDOM.nextDouble() * 0.4D,
                    at.z + Math.sin(angle) * radius,
                    Math.cos(angle) * 0.06D, 0.02D, Math.sin(angle) * 0.06D);
        }

        // Corrupted sparks kicked up at the point of impact
        for (int i = 0; i < 22; i++) {
            level.addParticle(new DustParticleOptions(CORRUPT_PURPLE, 1.6F),
                    at.x + (RANDOM.nextDouble() - 0.5D) * 1.6D,
                    y + RANDOM.nextDouble() * 1.2D,
                    at.z + (RANDOM.nextDouble() - 0.5D) * 1.6D,
                    (RANDOM.nextDouble() - 0.5D) * 0.3D,
                    0.25D + RANDOM.nextDouble() * 0.3D,
                    (RANDOM.nextDouble() - 0.5D) * 0.3D);
        }
        level.addParticle(ParticleTypes.EXPLOSION, at.x, y + 0.4D, at.z, 0, 0, 0);

        Player viewer = Minecraft.getInstance().player;
        if (viewer != null) {
            CameraShake.addAtDistance(4.2F, (float) viewer.position().distanceTo(at), 22.0F, 0.68F);
        }
    }

    /** Horizontal slash: corrupted motes thrown off the edge. */
    private static void slashSparks(Level level, Vec3 at) {
        for (int i = 0; i < 16; i++) {
            level.addParticle(new DustParticleOptions(
                            RANDOM.nextBoolean() ? CORRUPT_PURPLE : CORRUPT_PALE, 1.1F),
                    at.x + (RANDOM.nextDouble() - 0.5D) * 0.7D,
                    at.y + (RANDOM.nextDouble() - 0.5D) * 0.7D,
                    at.z + (RANDOM.nextDouble() - 0.5D) * 0.7D,
                    (RANDOM.nextDouble() - 0.5D) * 0.22D,
                    (RANDOM.nextDouble() - 0.5D) * 0.12D,
                    (RANDOM.nextDouble() - 0.5D) * 0.22D);
        }
        for (int i = 0; i < 5; i++) {
            level.addParticle(ParticleTypes.ENCHANTED_HIT,
                    at.x, at.y, at.z,
                    (RANDOM.nextDouble() - 0.5D) * 0.4D,
                    (RANDOM.nextDouble() - 0.5D) * 0.2D,
                    (RANDOM.nextDouble() - 0.5D) * 0.4D);
        }
    }

    private static void burst(Level level, Vec3 at) {
        for (int i = 0; i < 30; i++) {
            double angle = RANDOM.nextDouble() * TAU;
            double speed = 0.12D + RANDOM.nextDouble() * 0.28D;
            level.addParticle(new DustParticleOptions(CORRUPT_PURPLE, 1.8F),
                    at.x, at.y + 0.2D, at.z,
                    Math.cos(angle) * speed,
                    0.1D + RANDOM.nextDouble() * 0.35D,
                    Math.sin(angle) * speed);
        }
        level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 0, 0.05D, 0);
    }

    /** Armour plates shattering during the death animation. */
    private static void armorBreak(Level level, Vec3 at) {
        for (int i = 0; i < 24; i++) {
            level.addParticle(ParticleTypes.CRIT,
                    at.x + (RANDOM.nextDouble() - 0.5D) * 1.6D,
                    at.y + (RANDOM.nextDouble() - 0.5D) * 1.8D,
                    at.z + (RANDOM.nextDouble() - 0.5D) * 1.6D,
                    (RANDOM.nextDouble() - 0.5D) * 0.35D,
                    (RANDOM.nextDouble() - 0.2D) * 0.3D,
                    (RANDOM.nextDouble() - 0.5D) * 0.35D);
        }
        for (int i = 0; i < 14; i++) {
            level.addParticle(new DustParticleOptions(CORRUPT_PALE, 1.4F),
                    at.x + (RANDOM.nextDouble() - 0.5D) * 1.2D,
                    at.y + RANDOM.nextDouble() * 1.5D,
                    at.z + (RANDOM.nextDouble() - 0.5D) * 1.2D,
                    0, 0.06D, 0);
        }
        Player viewer = Minecraft.getInstance().player;
        if (viewer != null) {
            CameraShake.addAtDistance(1.4F, (float) viewer.position().distanceTo(at), 16.0F, 0.6F);
        }
    }

    /** Regenerating guard: motes spiral inward and gather into the boss. */
    private static void healPulse(Level level, SamuraiBossEntity boss, Vec3 at) {
        double radius = 2.6D;
        for (int i = 0; i < 26; i++) {
            double angle = RANDOM.nextDouble() * TAU;
            double px = at.x + Math.cos(angle) * radius;
            double pz = at.z + Math.sin(angle) * radius;
            double py = boss.getY() + RANDOM.nextDouble() * 2.4D;
            // velocity points back at the boss, so the motes read as being drawn in
            level.addParticle(new DustParticleOptions(CORRUPT_PALE, 1.3F),
                    px, py, pz,
                    -Math.cos(angle) * 0.16D, 0.04D, -Math.sin(angle) * 0.16D);
        }
        for (int i = 0; i < 6; i++) {
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    at.x + (RANDOM.nextDouble() - 0.5D) * 1.2D,
                    boss.getY() + 0.2D,
                    at.z + (RANDOM.nextDouble() - 0.5D) * 1.2D,
                    0.0D, 0.06D, 0.0D);
        }
    }

    /**
     * Fired at the moment the katana leaves the hand in the death animation.
     * The held sword is scaled to zero by the clip; spawn your planted-sword
     * prop here if you want one standing in the ground.
     */
    private static void katanaPlant(Level level, Vec3 at) {
        BlockPos below = BlockPos.containing(at.x, at.y - 0.2D, at.z);
        BlockState ground = level.getBlockState(below);
        if (!ground.isAir()) {
            for (int i = 0; i < 18; i++) {
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground),
                        at.x + (RANDOM.nextDouble() - 0.5D) * 0.8D,
                        at.y + 0.1D,
                        at.z + (RANDOM.nextDouble() - 0.5D) * 0.8D,
                        (RANDOM.nextDouble() - 0.5D) * 0.2D,
                        0.18D + RANDOM.nextDouble() * 0.2D,
                        (RANDOM.nextDouble() - 0.5D) * 0.2D);
            }
        }
        for (int i = 0; i < 12; i++) {
            level.addParticle(new DustParticleOptions(CORRUPT_PURPLE, 1.5F),
                    at.x, at.y + RANDOM.nextDouble() * 1.4D, at.z,
                    (RANDOM.nextDouble() - 0.5D) * 0.1D, 0.08D, (RANDOM.nextDouble() - 0.5D) * 0.1D);
        }
    }
}
