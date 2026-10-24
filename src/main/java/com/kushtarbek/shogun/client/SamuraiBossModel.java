package com.kushtarbek.shogun.client;

import com.kushtarbek.shogun.SamuraiMod;
import com.kushtarbek.shogun.entity.SamuraiBossEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

import java.util.Optional;

public class SamuraiBossModel extends GeoModel<SamuraiBossEntity> {

    private static final ResourceLocation MODEL =
            new ResourceLocation(SamuraiMod.MOD_ID, "geo/samurai_shogun.geo.json");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(SamuraiMod.MOD_ID, "textures/entity/samurai_shogun.png");
    private static final ResourceLocation ANIMATIONS =
            new ResourceLocation(SamuraiMod.MOD_ID, "animations/samurai_shogun.animation.json");

    @Override
    public ResourceLocation getModelResource(SamuraiBossEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(SamuraiBossEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(SamuraiBossEntity animatable) {
        return ANIMATIONS;
    }

    /** Keeps the boss unlit-dark so the emissive layer and corruption read properly. */
    @Override
    public RenderType getRenderType(SamuraiBossEntity animatable, ResourceLocation texture) {
        return RenderType.entityCutoutNoCull(texture);
    }

    /**
     * Head tracking. Runs after the animation pass, so it is additive on top of
     * whatever the current clip put on the head bone. Suppressed while dying.
     */
    @Override
    public void setCustomAnimations(SamuraiBossEntity animatable, long instanceId,
                                    AnimationState<SamuraiBossEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        if (animatable.isDeadOrDying()) {
            return;
        }

        // GeoModel#getBone returns Optional<GeoBone>; the processor's own
        // getBone returns the CoreGeoBone interface, which has no setRotX.
        Optional<GeoBone> maybeHead = getBone("head");
        if (maybeHead.isEmpty()) {
            return;
        }
        GeoBone head = maybeHead.get();

        EntityModelData modelData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        float headPitch = modelData.headPitch();
        float netHeadYaw = modelData.netHeadYaw();

        // Clamp so the helmet never twists past what the neck plausibly allows.
        head.setRotX(head.getRotX() + Mth.clamp(headPitch, -28.0F, 28.0F) * Mth.DEG_TO_RAD);
        head.setRotY(head.getRotY() + Mth.clamp(netHeadYaw, -48.0F, 48.0F) * Mth.DEG_TO_RAD);
    }
}
