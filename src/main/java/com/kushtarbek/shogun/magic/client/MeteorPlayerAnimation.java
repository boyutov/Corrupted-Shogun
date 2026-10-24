package com.kushtarbek.shogun.magic.client;

import com.kushtarbek.shogun.SamuraiMod;
import com.kushtarbek.shogun.magic.network.MeteorGesturePacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Original two-arm casting pose, using vanilla skins and sleeves in both camera modes. */
@Mod.EventBusSubscriber(modid = SamuraiMod.MOD_ID, value = Dist.CLIENT)
public final class MeteorPlayerAnimation {
    private static final Map<UUID, MeteorGesturePacket> GESTURES = new HashMap<>();
    private static final HumanoidModel.ArmPose METEOR_POSE = HumanoidModel.ArmPose.create(
            "SAMURAIBOSS_METEOR", true, (model, entity, arm) -> {
                float amount = amount(entity, Minecraft.getInstance().getFrameTime());
                // Both invocations write the same pose, including when vanilla only poses the used arm.
                model.rightArm.xRot = model.leftArm.xRot = -2.85f * amount;
                model.rightArm.yRot = -0.12f * amount;
                model.leftArm.yRot = 0.12f * amount;
                model.rightArm.zRot = 0.22f * amount;
                model.leftArm.zRot = -0.22f * amount;
            });

    public static void receive(MeteorGesturePacket packet) {
        if (packet.gesture().duration() <= 0) GESTURES.remove(packet.player());
        else GESTURES.put(packet.player(), packet);
    }

    public static void reset() { GESTURES.clear(); }

    public static void tick() {
        var level = Minecraft.getInstance().level;
        if (level == null) { reset(); return; }
        GESTURES.values().removeIf(p -> !p.dimension().equals(level.dimension().location())
                || p.gesture().expired(level.getGameTime()));
    }

    private static float amount(LivingEntity entity, float partial) {
        MeteorGesturePacket packet = GESTURES.get(entity.getUUID());
        double now = entity.level().getGameTime() + partial;
        if (!entity.isAlive() || entity.isSpectator() || packet == null
                || !packet.dimension().equals(entity.level().dimension().location()) || packet.gesture().expired(now)) return 0;
        return packet.gesture().amount(now);
    }

    @SubscribeEvent
    public static void playerPre(RenderPlayerEvent.Pre event) {
        if (amount(event.getEntity(), event.getPartialTick()) <= 0) return;
        var model = event.getRenderer().getModel();
        model.leftArmPose = model.rightArmPose = METEOR_POSE;
        // PlayerRenderer assigns fresh arm poses before every render; no shared pose leaks to other players.
    }

    @SubscribeEvent
    public static void hands(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer player = mc.player;
        if (player == null || player.isInvisible()) return;
        float lift = amount(player, event.getPartialTick());
        if (lift <= 0) return;
        event.setCanceled(true);
        // Vanilla normally draws main-hand first; a bow in active offhand is the exception.
        boolean offhandOnly = player.isUsingItem() && player.getUsedItemHand() == InteractionHand.OFF_HAND
                && (player.getUseItem().is(Items.BOW) || player.getUseItem().is(Items.CROSSBOW));
        if (event.getHand() != (offhandOnly ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND)) return;
        if (!(mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) return;
        PoseStack pose = event.getPoseStack();
        for (int side : new int[]{1, -1}) {
            pose.pushPose();
            // Raise hands along the sides of the screen, keeping the target visible in the centre.
            pose.translate(side * (0.64 - 0.12 * lift), -0.6 + 0.85 * lift, -0.72 - 0.12 * lift);
            pose.mulPose(Axis.XP.rotationDegrees(-35 * lift));
            pose.mulPose(Axis.YP.rotationDegrees(side * 45));
            pose.translate(side * -1.0, 3.6, 3.5);
            pose.mulPose(Axis.ZP.rotationDegrees(side * 120));
            pose.mulPose(Axis.XP.rotationDegrees(200));
            pose.mulPose(Axis.YP.rotationDegrees(side * -135));
            pose.translate(side * 5.6, 0, 0);
            if (side == 1) renderer.renderRightHand(pose, event.getMultiBufferSource(), event.getPackedLight(), player);
            else renderer.renderLeftHand(pose, event.getMultiBufferSource(), event.getPackedLight(), player);
            pose.popPose();
        }
    }

    private MeteorPlayerAnimation() { }
}
