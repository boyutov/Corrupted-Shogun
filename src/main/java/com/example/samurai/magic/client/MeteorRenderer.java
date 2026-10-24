package com.example.samurai.magic.client;

import com.example.samurai.SamuraiMod;
import com.example.samurai.magic.entity.MeteorEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A lumpy, full-bright rock sphere wrapped in a pulsing translucent fire shell.
 * Textures from tools/build_meteor_texture.py; the rock map is equirectangular.
 */
public class MeteorRenderer extends EntityRenderer<MeteorEntity> {

    private static final ResourceLocation ROCK = new ResourceLocation(SamuraiMod.MOD_ID, "textures/entity/meteor.png");
    private static final ResourceLocation GLOW = new ResourceLocation(SamuraiMod.MOD_ID, "textures/entity/meteor_glow.png");

    private static final int RINGS = 18;
    private static final int SEGMENTS = 36;
    /** Unit sphere surface points, displaced into a rock: [ring][segment], seam duplicated for UVs. */
    private static final Vector3f[][] ROCK_MESH = buildRock();
    private static final Vector3f[][] SHELL_MESH = buildSphere();

    private static final float SPIN_DEGREES_PER_TICK = 5f;

    public MeteorRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0f;
    }

    @Override
    public void render(MeteorEntity meteor, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight) {
        float age = meteor.tickCount + partialTick;
        float radius = MeteorEntity.SIZE / 2;

        pose.pushPose();
        pose.translate(0, radius, 0);

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(age * SPIN_DEGREES_PER_TICK));
        pose.mulPose(Axis.XP.rotationDegrees(age * SPIN_DEGREES_PER_TICK * 0.6f));
        pose.scale(radius, radius, radius);
        drawSphere(buffers.getBuffer(RenderType.entityCutout(ROCK)), pose, ROCK_MESH, 255, 1f, 1f);
        pose.popPose();

        // Fire shells: an inner hot layer and a fainter outer one, counter-rotating and
        // breathing in size and opacity.
        float pulse = 0.5f + 0.5f * Mth.sin(age * 0.45f);
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW));
        pose.pushPose();
        float inner = radius * (1.15f + 0.06f * pulse);
        pose.mulPose(Axis.YP.rotationDegrees(-age * SPIN_DEGREES_PER_TICK * 1.7f));
        pose.scale(inner, inner, inner);
        drawSphere(glow, pose, SHELL_MESH, (int) (160 + 70 * pulse), 3f, 1.5f);
        pose.popPose();
        float outer = radius * (1.4f + 0.1f * (1 - pulse));
        pose.mulPose(Axis.XP.rotationDegrees(age * SPIN_DEGREES_PER_TICK * 1.1f));
        pose.scale(outer, outer, outer);
        drawSphere(glow, pose, SHELL_MESH, (int) (55 + 40 * (1 - pulse)), 2f, 1f);
        pose.popPose();

        super.render(meteor, yaw, partialTick, pose, buffers, packedLight);
    }

    /** @param uRepeat, vRepeat how many times the texture wraps around and pole to pole (the small glow map tiles) */
    private static void drawSphere(VertexConsumer vc, PoseStack pose, Vector3f[][] mesh, int alpha,
                                   float uRepeat, float vRepeat) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        for (int r = 0; r < RINGS; r++) {
            float v0 = r / (float) RINGS * vRepeat;
            float v1 = (r + 1) / (float) RINGS * vRepeat;
            for (int s = 0; s < SEGMENTS; s++) {
                float u0 = s / (float) SEGMENTS * uRepeat;
                float u1 = (s + 1) / (float) SEGMENTS * uRepeat;
                // Counter-clockwise seen from outside (longitude grows from +X towards +Z).
                vertex(vc, m, n, mesh[r][s], u0, v0, alpha);
                vertex(vc, m, n, mesh[r][s + 1], u1, v0, alpha);
                vertex(vc, m, n, mesh[r + 1][s + 1], u1, v1, alpha);
                vertex(vc, m, n, mesh[r + 1][s], u0, v1, alpha);
            }
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, Vector3f p, float u, float v, int alpha) {
        // Normal from the undisplaced direction: smooth shading, cheap, and the rock is full-bright anyway.
        float len = p.length();
        vc.vertex(m, p.x(), p.y(), p.z())
                .color(255, 255, 255, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(n, p.x() / len, p.y() / len, p.z() / len)
                .endVertex();
    }

    private static Vector3f[][] buildSphere() {
        Vector3f[][] mesh = new Vector3f[RINGS + 1][SEGMENTS + 1];
        for (int r = 0; r <= RINGS; r++) {
            double lat = Math.PI * r / RINGS;              // 0 at the top pole, PI at the bottom
            for (int s = 0; s <= SEGMENTS; s++) {
                double lon = 2 * Math.PI * (s % SEGMENTS) / SEGMENTS;
                mesh[r][s] = new Vector3f((float) (Math.sin(lat) * Math.cos(lon)), (float) Math.cos(lat),
                        (float) (Math.sin(lat) * Math.sin(lon)));
            }
        }
        return mesh;
    }

    /**
     * Sphere with each point pushed in or out by a smooth function of its direction:
     * a few broad lumps plus finer knobbles, so it reads as rock rather than a ball.
     * Fixed seed, and a function of direction only, so the seam and poles stay closed.
     */
    private static Vector3f[][] buildRock() {
        RandomSource random = RandomSource.create(20261024L);
        int waves = 9;
        Vector3f[] dirs = new Vector3f[waves];
        float[] freq = new float[waves];
        float[] phase = new float[waves];
        float[] amp = new float[waves];
        for (int i = 0; i < waves; i++) {
            dirs[i] = new Vector3f(random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1,
                    random.nextFloat() * 2 - 1).normalize();
            freq[i] = i < 3 ? 1.5f + random.nextFloat() : 4f + random.nextFloat() * 3f;
            phase[i] = random.nextFloat() * Mth.TWO_PI;
            amp[i] = i < 3 ? 0.07f : 0.025f;
        }
        Vector3f[][] mesh = buildSphere();
        for (Vector3f[] ring : mesh) {
            for (Vector3f p : ring) {
                float bump = 0;
                for (int i = 0; i < waves; i++) {
                    bump += amp[i] * Mth.sin(p.dot(dirs[i]) * freq[i] * Mth.PI + phase[i]);
                }
                p.mul(1f + bump);
            }
        }
        return mesh;
    }

    @Override
    public ResourceLocation getTextureLocation(MeteorEntity meteor) {
        return ROCK;
    }
}
