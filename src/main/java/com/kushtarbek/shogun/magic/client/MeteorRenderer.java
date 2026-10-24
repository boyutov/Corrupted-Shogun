package com.kushtarbek.shogun.magic.client;

import com.kushtarbek.shogun.SamuraiMod;
import com.kushtarbek.shogun.magic.entity.MeteorEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

/**
 * A lumpy, full-bright rock sphere wrapped in a pulsing translucent fire shell.
 * Textures from tools/build_meteor_texture.py; the rock map is equirectangular.
 */
public class MeteorRenderer extends EntityRenderer<MeteorEntity> {

    private static final ResourceLocation ROCK = new ResourceLocation(SamuraiMod.MOD_ID, "textures/entity/meteor.png");
    private static final ResourceLocation GLOW = new ResourceLocation(SamuraiMod.MOD_ID, "textures/entity/meteor_glow.png");

    /** Unit sphere displaced into a rock. */
    private static final Vector3f[][] ROCK_MESH = buildRock();
    private static final Vector3f[][] SHELL_MESH = SphereMesh.unitSphere();

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
        SphereMesh.draw(buffers.getBuffer(RenderType.entityCutout(ROCK)), pose, ROCK_MESH, 255, 255, 255, 255, 1f, 1f);
        pose.popPose();

        // Fire shells: an inner hot layer and a fainter outer one, counter-rotating and
        // breathing in size and opacity.
        float pulse = 0.5f + 0.5f * Mth.sin(age * 0.45f);
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW));
        pose.pushPose();
        float inner = radius * (1.15f + 0.06f * pulse);
        pose.mulPose(Axis.YP.rotationDegrees(-age * SPIN_DEGREES_PER_TICK * 1.7f));
        pose.scale(inner, inner, inner);
        SphereMesh.draw(glow, pose, SHELL_MESH, 255, 255, 255, (int) (160 + 70 * pulse), 3f, 1.5f);
        pose.popPose();
        float outer = radius * (1.4f + 0.1f * (1 - pulse));
        pose.mulPose(Axis.XP.rotationDegrees(age * SPIN_DEGREES_PER_TICK * 1.1f));
        pose.scale(outer, outer, outer);
        SphereMesh.draw(glow, pose, SHELL_MESH, 255, 255, 255, (int) (55 + 40 * (1 - pulse)), 2f, 1f);
        pose.popPose();

        super.render(meteor, yaw, partialTick, pose, buffers, packedLight);
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
        Vector3f[][] mesh = SphereMesh.unitSphere();
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
