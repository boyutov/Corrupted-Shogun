package com.kushtarbek.shogun.magic.client;

import com.kushtarbek.shogun.client.vfx.CameraShake;
import com.kushtarbek.shogun.magic.entity.BlackHoleEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/**
 * The black hole's moving parts: spiral arms of portal motes streaming into the core,
 * sparks flung off the disk, a low rumble of camera shake near it, and the collapse flash.
 * Particles are always-visible (so "Minimal particles" can't erase the spell's main cue),
 * with counts still following that setting. The body itself is BlackHoleRenderer.
 */
public final class BlackHoleVfx {

    public static void tick(BlackHoleEntity hole) {
        Level level = hole.level();
        RandomSource random = level.getRandom();
        float density = switch (Minecraft.getInstance().options.particles().get()) {
            case ALL -> 1f;
            case DECREASED -> 0.5f;
            case MINIMAL -> 0.25f;
        };
        double x = hole.getX();
        double y = hole.getY();
        double z = hole.getZ();
        double spin = hole.tickCount * 0.12;

        // Inflow: a portal particle travels from (start + offset) to start by itself, with a
        // 1-block rise that fades out on the way; start half a block low so it meets the core.
        for (int i = 0, n = count(24, density); i < n; i++) {
            int arm = i % 3;
            double r = 2.5 + random.nextDouble() * 12.0;
            double angle = arm * Mth.TWO_PI / 3 + r * 0.35 - spin;
            double dy = (random.nextDouble() - 0.5) * r * 0.15;
            level.addAlwaysVisibleParticle(ParticleTypes.PORTAL, true, x, y - 0.5, z,
                    Math.cos(angle) * r, dy, Math.sin(angle) * r);
        }
        // Dust glinting just outside the disk's rim.
        for (int i = 0, n = count(8, density); i < n; i++) {
            double r = 5.5 + random.nextDouble() * 2.5;
            double angle = random.nextDouble() * Mth.TWO_PI - spin * 2;
            Vector3f color = random.nextBoolean() ? BlackHoleEntity.PURPLE : BlackHoleEntity.PURPLE_LO;
            level.addAlwaysVisibleParticle(new DustParticleOptions(color, 1.0f), true, x + Math.cos(angle) * r,
                    y + (random.nextDouble() - 0.5) * 0.8, z + Math.sin(angle) * r, 0, 0, 0);
        }
        // Now and then a spark is flung off the inner disk along its spin.
        if (random.nextFloat() < 0.35f * density) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double r = 2.0;
            level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, x + Math.cos(angle) * r, y,
                    z + Math.sin(angle) * r, -Math.sin(angle) * 0.25, 0.02, Math.cos(angle) * 0.25);
        }
        // Low rumble within 16 blocks.
        if (hole.tickCount % 5 == 0) {
            shakeFor(hole, 0.35f, 16f, 0.75f);
        }
    }

    /** Flash and shockwave when the hole winks out. */
    public static void collapse(BlackHoleEntity hole) {
        Level level = hole.level();
        RandomSource random = level.getRandom();
        level.addAlwaysVisibleParticle(ParticleTypes.FLASH, true, hole.getX(), hole.getY(), hole.getZ(), 0, 0, 0);
        for (int i = 0; i < 60; i++) {
            double yaw = random.nextDouble() * Mth.TWO_PI;
            double pitch = (random.nextDouble() - 0.5) * 0.6;
            double speed = 0.4 + random.nextDouble() * 0.5;
            level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, hole.getX(), hole.getY(), hole.getZ(),
                    Math.cos(yaw) * Math.cos(pitch) * speed, Math.sin(pitch) * speed, Math.sin(yaw) * Math.cos(pitch) * speed);
        }
        shakeFor(hole, 2.5f, 32f, 0.8f);
    }

    private static void shakeFor(BlackHoleEntity hole, float amount, float falloff, float retain) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            CameraShake.addAtDistance(amount, (float) player.position().distanceTo(hole.position()), falloff, retain);
        }
    }

    private static int count(int full, float density) {
        return Math.max(1, Math.round(full * density));
    }

    private BlackHoleVfx() {
    }
}
