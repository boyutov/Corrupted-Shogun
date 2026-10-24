package com.kushtarbek.shogun.magic.client;

import com.kushtarbek.shogun.SamuraiMod;
import com.kushtarbek.shogun.magic.entity.BlackHoleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A black hole the way it is usually pictured (Interstellar's Gargantua): a pitch-black
 * event horizon, a tilted, spinning accretion disk, a thin photon ring hugging the
 * horizon and a lensed halo of disk light bent around it. The ring and halo always face
 * the camera, which is what lensing looks like from any side.
 *
 * <p>The light layers use the additive "eyes" render type, so they glow against the
 * night and black parts of the texture add nothing. The horizon is drawn first and
 * writes depth, so it hides the far half of the disk while the near half crosses in
 * front of it.
 */
public class BlackHoleRenderer extends EntityRenderer<BlackHoleEntity> {

    private static final ResourceLocation DISK = new ResourceLocation(SamuraiMod.MOD_ID, "textures/entity/black_hole_disk.png");
    private static final Vector3f[][] SPHERE = SphereMesh.unitSphere();

    private static final float HORIZON = 1.3f;
    private static final float DISK_INNER = 1.7f;
    private static final float DISK_OUTER = 6.0f;
    private static final float DISK_TILT = 14f;
    private static final int ARC_SEGMENTS = 72;
    /** Ticks to open from a point, and to collapse back into one. */
    private static final float OPEN_TICKS = 16f;
    private static final float CLOSE_TICKS = 12f;

    public BlackHoleRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0f;
    }

    /** The entity's box is tiny; cull against what is actually drawn. */
    @Override
    public boolean shouldRender(BlackHoleEntity hole, Frustum frustum, double camX, double camY, double camZ) {
        return frustum.isVisible(hole.getBoundingBox().inflate(DISK_OUTER + 1));
    }

    @Override
    public void render(BlackHoleEntity hole, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight) {
        float age = hole.tickCount + partialTick;
        float size = scaleAt(age);
        if (size <= 0.01f) {
            return;
        }
        float flicker = 0.92f + 0.08f * Mth.sin(age * 0.9f);

        // Centred on the entity's position, where the pull and the particle inflow converge.
        pose.pushPose();
        pose.scale(size, size, size);

        // Event horizon: drawn first, opaque black, writes depth.
        pose.pushPose();
        pose.scale(HORIZON, HORIZON, HORIZON);
        SphereMesh.draw(buffers.getBuffer(RenderType.entitySolid(DISK)), pose, SPHERE, 0, 0, 0, 255, 1f, 1f);
        pose.popPose();

        VertexConsumer light = buffers.getBuffer(RenderType.eyes(DISK));

        // Accretion disk: tilted, spinning, inner edge hottest. Both faces, so it shows from below too.
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(DISK_TILT));
        pose.mulPose(Axis.YP.rotationDegrees(-age * 5f));
        flatRing(light, pose, DISK_INNER, DISK_OUTER, 0f, 1f, 2f, bright(flicker), true);
        pose.popPose();

        // Camera-facing layers: the lensed halo, then the photon ring right at the horizon.
        pose.pushPose();
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        pose.mulPose(Axis.XP.rotationDegrees(90)); // ring helper draws in XZ; turn it to face the camera
        pose.mulPose(Axis.YP.rotationDegrees(age * 2f));
        flatRing(light, pose, HORIZON * 1.08f, HORIZON * 2.7f, 0.05f, 0.75f, 1f, bright(0.6f * flicker), true);
        flatRing(light, pose, HORIZON * 1.02f, HORIZON * 1.16f, 0.0f, 0.06f, 1f, bright(flicker), true);
        pose.popPose();

        pose.popPose();
        super.render(hole, yaw, partialTick, pose, buffers, packedLight);
    }

    /** Opens with an overshoot, holds, then collapses into a point. */
    private static float scaleAt(float age) {
        float open = Mth.clamp(age / OPEN_TICKS, 0f, 1f);
        float grow = 1f + 2.4f * (open - 1f) * (open - 1f) * (open - 1f) + 1.4f * (open - 1f) * (open - 1f); // ease-out-back
        float close = Mth.clamp((BlackHoleEntity.DURATION - age) / CLOSE_TICKS, 0f, 1f);
        return grow * close * close;
    }

    private static int bright(float k) {
        return Mth.clamp((int) (255 * k), 0, 255);
    }

    /**
     * Annulus in the XZ plane, mapped polar: u around (repeated uRepeat times), v from
     * vInner at the inner edge to vOuter at the outer edge.
     */
    private static void flatRing(VertexConsumer vc, PoseStack pose, float inner, float outer, float vInner,
                                 float vOuter, float uRepeat, int brightness, boolean doubleSided) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        for (int i = 0; i < ARC_SEGMENTS; i++) {
            double a0 = Mth.TWO_PI * i / ARC_SEGMENTS;
            double a1 = Mth.TWO_PI * (i + 1) / ARC_SEGMENTS;
            float u0 = uRepeat * i / ARC_SEGMENTS;
            float u1 = uRepeat * (i + 1) / ARC_SEGMENTS;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            // Counter-clockwise seen from +Y.
            vertex(vc, m, n, c0 * inner, s0 * inner, u0, vInner, brightness, 1);
            vertex(vc, m, n, c1 * inner, s1 * inner, u1, vInner, brightness, 1);
            vertex(vc, m, n, c1 * outer, s1 * outer, u1, vOuter, brightness, 1);
            vertex(vc, m, n, c0 * outer, s0 * outer, u0, vOuter, brightness, 1);
            if (doubleSided) {
                vertex(vc, m, n, c0 * outer, s0 * outer, u0, vOuter, brightness, -1);
                vertex(vc, m, n, c1 * outer, s1 * outer, u1, vOuter, brightness, -1);
                vertex(vc, m, n, c1 * inner, s1 * inner, u1, vInner, brightness, -1);
                vertex(vc, m, n, c0 * inner, s0 * inner, u0, vInner, brightness, -1);
            }
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float z, float u, float v,
                               int brightness, int normalY) {
        vc.vertex(m, x, 0, z)
                .color(brightness, brightness, brightness, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(n, 0, normalY, 0)
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(BlackHoleEntity hole) {
        return DISK;
    }
}
