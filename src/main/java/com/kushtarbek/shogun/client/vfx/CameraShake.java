package com.kushtarbek.shogun.client.vfx;

import com.kushtarbek.shogun.SamuraiMod;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side camera shake.
 *
 * Minecraft has no built-in screen shake, so this nudges the camera angles each
 * frame in {@link ViewportEvent.ComputeCameraAngles} and decays the magnitude
 * once per client tick.
 *
 * No packets are involved: the shake is kicked off from the animation's particle
 * keyframe, which already runs on every client that is rendering the boss.
 */
@Mod.EventBusSubscriber(modid = SamuraiMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CameraShake {

    /** Current magnitude in degrees. */
    private static float intensity = 0.0F;
    /** Fraction of the magnitude kept per tick. */
    private static float retention = 0.72F;

    private static final RandomSource RANDOM = RandomSource.create();
    private static final float MAX_INTENSITY = 6.0F;

    private CameraShake() {
    }

    /**
     * Starts (or reinforces) a shake.
     *
     * @param amount    magnitude in degrees at the strongest point
     * @param retainPerTick how much of the magnitude survives each tick, 0..1 —
     *                      lower is a snappier jolt, higher is a longer rumble
     */
    public static void add(float amount, float retainPerTick) {
        intensity = Math.min(MAX_INTENSITY, Math.max(intensity, amount));
        retention = Mth.clamp(retainPerTick, 0.1F, 0.95F);
    }

    /**
     * Shake scaled by how close the viewer is to the impact, so a slam across
     * the arena doesn't rattle the camera as hard as one at your feet.
     *
     * @param distance  distance from the viewer to the impact, in blocks
     * @param falloff   distance at which the shake reaches zero
     */
    public static void addAtDistance(float amount, float distance, float falloff, float retainPerTick) {
        if (distance >= falloff) {
            return;
        }
        float scale = 1.0F - (distance / falloff);
        add(amount * scale * scale, retainPerTick);
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (intensity <= 0.01F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) {
            return;
        }
        float i = intensity;
        event.setYaw(event.getYaw() + (RANDOM.nextFloat() - 0.5F) * i);
        event.setPitch(event.getPitch() + (RANDOM.nextFloat() - 0.5F) * i);
        event.setRoll(event.getRoll() + (RANDOM.nextFloat() - 0.5F) * i * 0.6F);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || intensity <= 0.0F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) {
            return;
        }
        intensity *= retention;
        if (intensity < 0.01F) {
            intensity = 0.0F;
        }
    }
}
