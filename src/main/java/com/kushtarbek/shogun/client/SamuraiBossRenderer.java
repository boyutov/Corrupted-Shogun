package com.kushtarbek.shogun.client;

import com.kushtarbek.shogun.client.vfx.SwordTrail;
import com.kushtarbek.shogun.client.vfx.BossCombatEffects;
import com.kushtarbek.shogun.entity.SamuraiBossEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class SamuraiBossRenderer extends GeoEntityRenderer<SamuraiBossEntity> {

    public SamuraiBossRenderer(EntityRendererProvider.Context context) {
        super(context, new SamuraiBossModel());
        this.shadowRadius = 1.25F;

        // Emissive pass: reads textures/entity/samurai_shogun_glowmask.png
        // (eyes, corrupted blade edge and the purple cracks in the armour).
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    /**
     * Draws the blade ribbon after the model, while the pose stack is back at
     * the entity's origin and the bone matrices from this frame are still valid.
     */
    @Override
    public void render(SamuraiBossEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        SwordTrail trail = SwordTrail.get(entity);
        trail.sample(getGeoModel(), entity, partialTick);
        trail.render(poseStack, bufferSource, entity, partialTick);
        BossCombatEffects.render(entity, partialTick, poseStack, bufferSource);
    }

    /**
     * The geometry is already authored at 48 units (3 blocks), so no scaling is
     * needed. A tiny death-stagger wobble is applied here instead of in the clip
     * so it stays frame-rate independent.
     */
    @Override
    public void preRender(PoseStack poseStack, SamuraiBossEntity entity, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {

        if (entity.isDeadOrDying()) {
            float t = (entity.deathTime + partialTick) / 64.0F;
            float shake = (1.0F - Mth.clamp(t * 2.0F, 0.0F, 1.0F)) * 0.04F;
            poseStack.translate(
                    Mth.sin((entity.deathTime + partialTick) * 1.7F) * shake, 0.0D, 0.0D);
        }

        super.preRender(poseStack, entity, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public RenderType getRenderType(SamuraiBossEntity animatable, ResourceLocation texture,
                                    MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityCutoutNoCull(getTextureLocation(animatable));
    }

    @Override
    public ResourceLocation getTextureLocation(SamuraiBossEntity entity) {
        return getGeoModel().getTextureResource(entity);
    }
}
