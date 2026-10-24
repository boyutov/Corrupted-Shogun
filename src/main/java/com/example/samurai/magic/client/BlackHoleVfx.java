package com.example.samurai.magic.client;

import com.example.samurai.magic.entity.BlackHoleEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/**
 * The black hole's whole look: spiral arms of portal motes streaming into a black core
 * ringed by a purple disk. Spawned as always-visible particles, because with no model a
 * "Minimal particles" setting would otherwise erase the effect entirely; the counts still
 * follow that setting.
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
        // Accretion disk.
        for (int i = 0, n = count(10, density); i < n; i++) {
            double r = 1.4 + random.nextDouble() * 2.6;
            double angle = random.nextDouble() * Mth.TWO_PI - spin * 2;
            Vector3f color = r < 2.4 ? BlackHoleEntity.PURPLE_HI
                    : (random.nextBoolean() ? BlackHoleEntity.PURPLE : BlackHoleEntity.PURPLE_LO);
            level.addAlwaysVisibleParticle(new DustParticleOptions(color, 1.2f), true, x + Math.cos(angle) * r,
                    y + (random.nextDouble() - 0.5) * 0.3, z + Math.sin(angle) * r, 0, 0, 0);
        }
        // Core.
        for (int i = 0, n = count(6, density); i < n; i++) {
            level.addAlwaysVisibleParticle(new DustParticleOptions(BlackHoleEntity.VOID, 3.0f), true,
                    x + (random.nextDouble() - 0.5) * 0.8, y + (random.nextDouble() - 0.5) * 0.8,
                    z + (random.nextDouble() - 0.5) * 0.8, 0, 0, 0);
        }
    }

    private static int count(int full, float density) {
        return Math.max(1, Math.round(full * density));
    }

    private BlackHoleVfx() {
    }
}
