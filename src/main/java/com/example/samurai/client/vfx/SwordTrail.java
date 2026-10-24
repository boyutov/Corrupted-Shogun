package com.example.samurai.client.vfx;

import com.example.samurai.entity.SamuraiBossEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Ribbon trail that follows the blade.
 *
 * Each frame the world positions of the `blade_base` and `blade_tip` marker
 * bones are sampled; consecutive samples are joined into quads, so the ribbon
 * has the blade's real width and curve instead of being a line through the tip.
 *
 * Samples are only taken when the tip actually moved, which means idle and walk
 * produce no trail without any need to ask which animation is playing.
 *
 * Positions are stored in world space and drawn relative to the entity's current
 * position, so the ribbon stays anchored in the world while the boss dashes
 * through it during attack_slash.
 */
public final class SwordTrail {

    /** How long a segment lives, in ticks. */
    private static final float LIFETIME_TICKS = 6.0F;
    /** Minimum tip travel between samples, in blocks. Below this, no segment. */
    private static final double MIN_TRAVEL = 0.22D;
    private static final int MAX_SEGMENTS = 24;

    private static final Map<Entity, SwordTrail> TRAILS = new WeakHashMap<>();

    private final Deque<Segment> segments = new ArrayDeque<>();
    private Vec3 lastBase;
    private Vec3 lastTip;

    private record Segment(Vec3 base, Vec3 tip, float bornAt) {
    }

    public static SwordTrail get(Entity entity) {
        return TRAILS.computeIfAbsent(entity, e -> new SwordTrail());
    }

    private static Optional<Vec3> worldPos(GeoModel<SamuraiBossEntity> model, String boneName) {
        Optional<GeoBone> bone = model.getBone(boneName);
        if (bone.isEmpty()) {
            return Optional.empty();
        }
        // getWorldPosition() enables matrix tracking on first call; the renderer
        // fills the matrix on the NEXT frame, so the first sample is skipped.
        Vector3d pos = bone.get().getWorldPosition();
        if (pos.x == 0 && pos.y == 0 && pos.z == 0) {
            return Optional.empty();
        }
        return Optional.of(new Vec3(pos.x, pos.y, pos.z));
    }

    /** Call once per frame, AFTER the model has rendered and matrices are current. */
    public void sample(GeoModel<SamuraiBossEntity> model, SamuraiBossEntity entity, float partialTick) {
        float now = entity.tickCount + partialTick;
        expire(now);
        if (!entity.isSwordTrailActive()) {
            lastBase = null;
            lastTip = null;
            return;
        }

        Optional<Vec3> base = worldPos(model, "blade_base");
        Optional<Vec3> tip = worldPos(model, "blade_tip");
        if (base.isEmpty() || tip.isEmpty()) {
            return;
        }

        if (lastTip != null && lastTip.distanceTo(tip.get()) >= MIN_TRAVEL) {
            segments.addLast(new Segment(lastBase, lastTip, now));
            segments.addLast(new Segment(base.get(), tip.get(), now));
            while (segments.size() > MAX_SEGMENTS) {
                segments.removeFirst();
            }
        }
        lastBase = base.get();
        lastTip = tip.get();
    }

    private void expire(float now) {
        while (!segments.isEmpty() && now - segments.peekFirst().bornAt() > LIFETIME_TICKS) {
            segments.removeFirst();
        }
    }

    /**
     * Draws the ribbon. The pose stack must be at the entity's origin, which is
     * where it sits once GeoEntityRenderer#render has returned.
     */
    public void render(PoseStack poseStack, MultiBufferSource bufferSource,
                       SamuraiBossEntity entity, float partialTick) {
        if (segments.size() < 2) {
            return;
        }
        float now = entity.tickCount + partialTick;
        Vec3 origin = entity.position();

        // RenderType.lightning() is POSITION_COLOR with additive blending and no
        // texture — exactly what an energy ribbon needs, and it needs no custom
        // RenderType registration.
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();

        Segment[] arr = segments.toArray(new Segment[0]);
        for (int i = 0; i < arr.length - 1; i++) {
            Segment a = arr[i];
            Segment b = arr[i + 1];

            float ageA = 1.0F - Math.min(1.0F, (now - a.bornAt()) / LIFETIME_TICKS);
            float ageB = 1.0F - Math.min(1.0F, (now - b.bornAt()) / LIFETIME_TICKS);
            if (ageA <= 0.0F && ageB <= 0.0F) {
                continue;
            }

            quad(buffer, matrix, origin, a.base(), a.tip(), b.tip(), b.base(), ageA, ageB);
        }
    }

    private void quad(VertexConsumer buffer, Matrix4f matrix, Vec3 origin,
                      Vec3 aBase, Vec3 aTip, Vec3 bTip, Vec3 bBase, float ageA, float ageB) {
        // Near the hilt the trail is dim violet; out at the tip it burns white.
        vertex(buffer, matrix, origin, aBase, 0.42F, 0.16F, 0.70F, 0.35F * ageA);
        vertex(buffer, matrix, origin, aTip, 0.92F, 0.80F, 1.00F, 0.85F * ageA);
        vertex(buffer, matrix, origin, bTip, 0.92F, 0.80F, 1.00F, 0.85F * ageB);
        vertex(buffer, matrix, origin, bBase, 0.42F, 0.16F, 0.70F, 0.35F * ageB);

        // Same quad wound the other way, so the ribbon is visible from both sides.
        vertex(buffer, matrix, origin, bBase, 0.42F, 0.16F, 0.70F, 0.35F * ageB);
        vertex(buffer, matrix, origin, bTip, 0.92F, 0.80F, 1.00F, 0.85F * ageB);
        vertex(buffer, matrix, origin, aTip, 0.92F, 0.80F, 1.00F, 0.85F * ageA);
        vertex(buffer, matrix, origin, aBase, 0.42F, 0.16F, 0.70F, 0.35F * ageA);
    }

    private void vertex(VertexConsumer buffer, Matrix4f matrix, Vec3 origin, Vec3 p,
                        float r, float g, float b, float a) {
        buffer.vertex(matrix,
                        (float) (p.x - origin.x),
                        (float) (p.y - origin.y),
                        (float) (p.z - origin.z))
                .color(r, g, b, a)
                .endVertex();
    }
}
