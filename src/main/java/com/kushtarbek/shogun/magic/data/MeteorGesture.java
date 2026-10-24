package com.kushtarbek.shogun.magic.data;

import net.minecraft.util.Mth;

/** A short-lived visual transition, timed against the world's ticks; never persisted. */
public record MeteorGesture(long startTick, int duration, float from, float to) {
    public float amount(double now) {
        float t = Mth.clamp((float) ((now - startTick) / Math.max(1, duration)), 0, 1);
        return Mth.lerp(t * t * (3 - 2 * t), from, to);
    }

    public boolean expired(double now) {
        // Hold the apex briefly until the server sends the meteor's flight transition.
        return duration <= 0 || now >= startTick + duration + (to > 0 ? 20 : 0);
    }
}
