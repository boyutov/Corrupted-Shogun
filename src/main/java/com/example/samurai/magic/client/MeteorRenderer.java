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
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** A tumbling, full-bright textured cube. Texture from tools/build_meteor_texture.py. */
public class MeteorRenderer extends EntityRenderer<MeteorEntity> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(SamuraiMod.MOD_ID, "textures/entity/meteor.png");
    private static final float SPIN_DEGREES_PER_TICK = 6f;

    public MeteorRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0f;
    }

    @Override
    public void render(MeteorEntity meteor, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight) {
        float spin = (meteor.tickCount + partialTick) * SPIN_DEGREES_PER_TICK;
        pose.pushPose();
        pose.translate(0, MeteorEntity.SIZE / 2, 0);
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.mulPose(Axis.XP.rotationDegrees(spin * 0.7f));
        pose.scale(MeteorEntity.SIZE, MeteorEntity.SIZE, MeteorEntity.SIZE);

        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(TEXTURE));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float h = 0.5f;
        // +Y, -Y, +Z, -Z, +X, -X; each quad counter-clockwise seen from outside.
        quad(vc, m, n, 0, 1, 0, -h, h, -h, -h, h, h, h, h, h, h, h, -h);
        quad(vc, m, n, 0, -1, 0, -h, -h, h, -h, -h, -h, h, -h, -h, h, -h, h);
        quad(vc, m, n, 0, 0, 1, -h, -h, h, h, -h, h, h, h, h, -h, h, h);
        quad(vc, m, n, 0, 0, -1, h, -h, -h, -h, -h, -h, -h, h, -h, h, h, -h);
        quad(vc, m, n, 1, 0, 0, h, -h, h, h, -h, -h, h, h, -h, h, h, h);
        quad(vc, m, n, -1, 0, 0, -h, -h, -h, -h, -h, h, -h, h, h, -h, h, -h);

        pose.popPose();
        super.render(meteor, yaw, partialTick, pose, buffers, packedLight);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, float nx, float ny, float nz,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        vertex(vc, m, n, x0, y0, z0, 0, 1, nx, ny, nz);
        vertex(vc, m, n, x1, y1, z1, 1, 1, nx, ny, nz);
        vertex(vc, m, n, x2, y2, z2, 1, 0, nx, ny, nz);
        vertex(vc, m, n, x3, y3, z3, 0, 0, nx, ny, nz);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z,
                               float u, float v, float nx, float ny, float nz) {
        vc.vertex(m, x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(n, nx, ny, nz)
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(MeteorEntity meteor) {
        return TEXTURE;
    }
}
