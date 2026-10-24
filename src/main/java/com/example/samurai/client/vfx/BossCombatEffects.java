package com.example.samurai.client.vfx;

import com.example.samurai.entity.SamuraiBossEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Additive, untextured energy geometry. All danger radii share the server's timeline. */
public final class BossCombatEffects {
    private static final double TAU = Math.PI * 2;
    private static final int SEGMENTS = 96;

    private BossCombatEffects() { }

    public static void render(SamuraiBossEntity boss, float partial, PoseStack pose,
                              MultiBufferSource buffers) {
        if (!boss.isAlive()) return;
        VertexConsumer out = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = pose.last().pose();
        Vec3 origin = boss.getPosition(partial);
        double time = boss.tickCount + partial;
        if (boss.getPhase() == SamuraiBossEntity.PHASE_SKYFALL) {
            float age = boss.getPhaseAge() + partial;
            if (age < SamuraiBossEntity.SKY_IMPACT_TICK) {
                Vec3 point = boss.getSkyPoint();
                float charge = age / SamuraiBossEntity.SKY_IMPACT_TICK;
                float pulse = 0.65F + 0.2F * Mth.sin(age * 0.65F);
                ring(out, matrix, origin, point, 2.5, 0.16, 0.03, 0.65F, 0.24F, 1, pulse, false, boss);
                ring(out, matrix, origin, point, 2.5, 0.045, 0.04, 0.92F, 0.78F, 1, 0.95F, false, boss);
                ring(out, matrix, origin, point, 3.2 - 0.55 * charge, 0.06, 0.04,
                        0.6F, 0.3F, 1, 0.6F, true, boss);
                // Four inward-facing chevrons make the marked point readable on bright terrain.
                for (int i = 0; i < 4; i++) {
                    double a = i * TAU / 4 + time * 0.015;
                    Vec3 tip = polar(point, 1.8, a, 0.06);
                    line(out, matrix, origin, polar(point, 2.2, a - 0.13, 0.06), tip, 0.045, 0.9F, 0.65F, 1, pulse);
                    line(out, matrix, origin, polar(point, 2.2, a + 0.13, 0.06), tip, 0.045, 0.9F, 0.65F, 1, pulse);
                }
                line(out, matrix, origin, point.add(-0.5, 0.05, 0), point.add(0.5, 0.05, 0), 0.03, 1, 0.85F, 1, pulse);
                line(out, matrix, origin, point.add(0, 0.05, -0.5), point.add(0, 0.05, 0.5), 0.03, 1, 0.85F, 1, pulse);
            }
        }
        int waveAge = boss.getWaveAge();
        if (waveAge >= 0) {
            Vec3 point = boss.getSkyPoint();
            for (int wave = 0; wave < 3; wave++) {
                double age = waveAge + partial - wave * SamuraiBossEntity.WAVE_SPACING;
                if (age < 0 || age >= SamuraiBossEntity.WAVE_LIFETIME) continue;
                double radius = SamuraiBossEntity.waveRadius(age);
                float alpha = (float) Math.min(1, (SamuraiBossEntity.WAVE_LIFETIME - age) / 5);
                // Broad translucent violet foot, pale razor edge and a low raised crest.
                ring(out, matrix, origin, point, radius - 0.16, 0.48, 0.045,
                        0.5F, 0.13F, 1, 0.3F * alpha, false, boss);
                ring(out, matrix, origin, point, radius, 0.09, 0.10,
                        0.92F, 0.8F, 1, 0.95F * alpha, false, boss);
                ring(out, matrix, origin, point, radius - 0.12, 0.17, 0.40,
                        0.65F, 0.34F, 1, 0.45F * alpha, false, boss);
            }
            if (waveAge < 12) {
                float fade = 1 - (waveAge + partial) / 12;
                // Short vertical lance at impact. It clears before the first jump window.
                for (int i = 0; i < 2; i++) {
                    double angle = i * Math.PI / 2;
                    Vec3 side = new Vec3(Math.cos(angle), 0, Math.sin(angle)).scale(0.35 * fade);
                    quad(out, matrix, origin, point.subtract(side), point.add(side),
                            point.add(side.scale(0.08)).add(0, 6 * fade, 0),
                            point.subtract(side.scale(0.08)).add(0, 6 * fade, 0),
                            0.85F, 0.6F, 1, 0.75F * fade, 0);
                }
            }
        }
        if (boss.isEnraged()) {
            Vec3 center = origin;
            float pulse = 0.5F + 0.15F * (float) Math.sin(time * 0.16);
            ring(out, matrix, origin, center, 1.45, 0.08, 0.06, 0.8F, 0.15F, 1, pulse, true, boss);
            // Three rising, tapered ribbons leave the face and attack silhouette exposed.
            for (int ribbon = 0; ribbon < 3; ribbon++) {
                for (int i = 0; i < 22; i++) {
                    double t0 = i / 22.0, t1 = (i + 1) / 22.0;
                    double a0 = ribbon * TAU / 3 + time * 0.055 + t0 * 2.2;
                    double a1 = ribbon * TAU / 3 + time * 0.055 + t1 * 2.2;
                    Vec3 a = polar(center, 1.3 - 0.4 * t0, a0, 0.15 + 3.4 * t0);
                    Vec3 b = polar(center, 1.3 - 0.4 * t1, a1, 0.15 + 3.4 * t1);
                    float fade = (float) (Math.sin(t0 * Math.PI) * 0.28);
                    quad(out, matrix, origin, a, b, b.add(0, 0.16 * (1-t1), 0), a.add(0, 0.16 * (1-t0), 0),
                            0.75F, 0.18F, 1, fade, 0);
                }
            }
        }
        if (boss.getPhase() == SamuraiBossEntity.PHASE_ROAR) {
            double age = boss.getPhaseAge() + partial - SamuraiBossEntity.ROAR_IMPACT_TICK;
            if (age >= 0 && age < 20) {
                ring(out, matrix, origin, origin, 1 + age * 0.48, 0.25, 0.15,
                        0.8F, 0.3F, 1, (float) (0.8 * (1 - age / 20)), false, boss);
            }
        }
    }

    private static Vec3 polar(Vec3 center, double radius, double angle, double height) {
        return center.add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
    }

    private static void ring(VertexConsumer out, Matrix4f matrix, Vec3 origin, Vec3 center,
                             double radius, double width, double height, float r, float g, float b,
                             float alpha, boolean dashed, SamuraiBossEntity boss) {
        if (radius <= 0 || alpha <= 0) return;
        for (int i = 0; i < SEGMENTS; i++) {
            if (dashed && i % 8 >= 5) continue;
            double a = i * TAU / SEGMENTS, c = (i+1) * TAU / SEGMENTS;
            Vec3 aOuter = polar(center, radius + width/2, a, height);
            Vec3 bOuter = polar(center, radius + width/2, c, height);
            double ya = boss.groundHeight(aOuter.x, aOuter.z, center.y);
            double yb = boss.groundHeight(bOuter.x, bOuter.z, center.y);
            if (!Double.isFinite(ya) || !Double.isFinite(yb) || Math.abs(ya-yb) > 1.1) continue;
            Vec3 aInner = polar(center, Math.max(0, radius - width/2), a, height).add(0, ya-center.y, 0);
            Vec3 bInner = polar(center, Math.max(0, radius - width/2), c, height).add(0, yb-center.y, 0);
            quad(out, matrix, origin, aInner, bInner, bOuter.add(0, yb-center.y, 0), aOuter.add(0, ya-center.y, 0),
                    r, g, b, alpha, alpha * 0.7F);
        }
    }

    private static void line(VertexConsumer out, Matrix4f matrix, Vec3 origin, Vec3 a, Vec3 b,
                             double width, float r, float g, float blue, float alpha) {
        Vec3 along = b.subtract(a).normalize();
        Vec3 side = new Vec3(-along.z, 0, along.x).scale(width);
        quad(out, matrix, origin, a.subtract(side), b.subtract(side), b.add(side), a.add(side),
                r, g, blue, alpha, alpha);
    }

    private static void quad(VertexConsumer out, Matrix4f matrix, Vec3 origin, Vec3 a, Vec3 b,
                             Vec3 c, Vec3 d, float r, float g, float blue, float bottom, float top) {
        vertex(out, matrix, origin, a, r, g, blue, bottom);
        vertex(out, matrix, origin, b, r, g, blue, bottom);
        vertex(out, matrix, origin, c, r, g, blue, top);
        vertex(out, matrix, origin, d, r, g, blue, top);
        vertex(out, matrix, origin, d, r, g, blue, top);
        vertex(out, matrix, origin, c, r, g, blue, top);
        vertex(out, matrix, origin, b, r, g, blue, bottom);
        vertex(out, matrix, origin, a, r, g, blue, bottom);
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 origin, Vec3 p,
                               float r, float g, float b, float alpha) {
        out.vertex(matrix, (float)(p.x-origin.x), (float)(p.y-origin.y), (float)(p.z-origin.z))
                .color(r, g, b, alpha).endVertex();
    }
}
