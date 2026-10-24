package com.kushtarbek.shogun.magic.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Flies the local player along the arc a {@link com.kushtarbek.shogun.magic.network.LaunchPacket}
 * describes. Each tick, before the player moves, the velocity is set to exactly reach the
 * next point of the curve from wherever the player really is, so lag, input and gravity
 * never accumulate into a lower or shorter flight.
 *
 * <p>Runs on client ticks, which can fall behind the server's under load, so the arc is
 * always flown to the end; only an explicit stop from the server (cast interrupted) cuts
 * it short. Inferring "stop" from the cast state would also cut it on normal completion.
 */
public final class ClientLaunch {

    @Nullable
    private static Vec3 start;
    private static Vec3 offset = Vec3.ZERO;
    private static int duration;
    private static int tick;

    public static void start(Vec3 from, Vec3 by, int ticks) {
        if (ticks <= 0) {
            stop();
            return;
        }
        start = from;
        offset = by;
        duration = ticks;
        tick = 0;
    }

    public static void stop() {
        start = null;
    }

    /** Client tick START, i.e. before the player's own movement this tick. */
    public static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (start == null || player == null || mc.isPaused()) {
            return;
        }
        if (tick >= duration || !player.isAlive()) {
            stop();
            return;
        }
        tick++;
        Vec3 target = start.add(offset.scale(ease(tick / (double) duration)));
        player.setDeltaMovement(target.subtract(player.position()));
        player.resetFallDistance();
    }

    /** Smoothstep: zero speed at both ends, so the flight starts and tops out gently. */
    private static double ease(double t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private ClientLaunch() {
    }
}
