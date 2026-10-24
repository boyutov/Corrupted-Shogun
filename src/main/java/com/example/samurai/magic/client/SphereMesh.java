package com.example.samurai.magic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Latitude/longitude unit sphere, shared by the meteor and black hole renderers. */
public final class SphereMesh {

    public static final int RINGS = 18;
    public static final int SEGMENTS = 36;

    /** Surface points [ring][segment], pole to pole; the seam column is duplicated so UVs can wrap. */
    public static Vector3f[][] unitSphere() {
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
     * Full-bright quads, counter-clockwise seen from outside.
     *
     * @param uRepeat how many times the texture wraps around
     * @param vRepeat how many times it repeats pole to pole
     */
    public static void draw(VertexConsumer vc, PoseStack pose, Vector3f[][] mesh, int red, int green, int blue,
                            int alpha, float uRepeat, float vRepeat) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        for (int r = 0; r < RINGS; r++) {
            float v0 = r / (float) RINGS * vRepeat;
            float v1 = (r + 1) / (float) RINGS * vRepeat;
            for (int s = 0; s < SEGMENTS; s++) {
                float u0 = s / (float) SEGMENTS * uRepeat;
                float u1 = (s + 1) / (float) SEGMENTS * uRepeat;
                vertex(vc, m, n, mesh[r][s], u0, v0, red, green, blue, alpha);
                vertex(vc, m, n, mesh[r][s + 1], u1, v0, red, green, blue, alpha);
                vertex(vc, m, n, mesh[r + 1][s + 1], u1, v1, red, green, blue, alpha);
                vertex(vc, m, n, mesh[r + 1][s], u0, v1, red, green, blue, alpha);
            }
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, Vector3f p, float u, float v,
                               int red, int green, int blue, int alpha) {
        // Normal from the undisplaced direction: smooth shading, and it is full-bright anyway.
        float len = p.length();
        vc.vertex(m, p.x(), p.y(), p.z())
                .color(red, green, blue, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(n, p.x() / len, p.y() / len, p.z() / len)
                .endVertex();
    }

    private SphereMesh() {
    }
}
