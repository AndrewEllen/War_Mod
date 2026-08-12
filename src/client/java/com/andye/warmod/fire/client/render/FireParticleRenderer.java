package com.andye.warmod.fire.client.render;

import com.andye.warmod.fire.FirePhase;
import com.andye.warmod.fire.client.render.FireWorldRenderer.FireRenderCell;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Deterministic billboards reconstructed from sparse authoritative fire cells. */
public final class FireParticleRenderer {
    private static final long FLAME_SEED = 0x464952455F464C4DL;
    private static final long SMOKE_SEED = 0x534D4F4B455F4649L;
    private static final long EMBER_SEED = 0x454D4245525F4649L;

    private FireParticleRenderer() { }

    public static void renderFlames(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final List<FireRenderCell> cells, final Quaternionf camera) {
        Basis basis = Basis.from(camera);
        for (FireRenderCell cell : cells) {
            if (cell.phase() != FirePhase.FLAMING || cell.heat() < 0.16F) continue;
            int samples = sampleCount(cell, 5, 18);
            float height = 0.72F + cell.intensity() * 2.35F;
            for (int index = 0; index < samples; index++) {
                long value = mix(cell.seed() ^ FLAME_SEED ^ (long) index * 0x9E3779B97F4A7C15L);
                double life = 13.0 + unit(value, 0) * 17.0;
                double progress = positiveModulo(age + unit(value, 1) * life, life) / life;
                double y = 0.10 + progress * height * (0.62 + unit(value, 2) * 0.58);
                double curl = Math.sin(age * (0.18 + unit(value, 3) * 0.11)
                    + unit(value, 4) * Mth.TWO_PI) * (0.08 + progress * 0.16);
                double spread = (0.10 + unit(value, 5) * 0.34) * (1.0 - progress * 0.52);
                double angle = unit(value, 6) * Mth.TWO_PI;
                double windLift = progress * progress * (0.75 + cell.intensity() * 0.65);
                Vec3 center = cell.relativePosition().add(
                    Math.cos(angle) * spread + curl + cell.wind().x * windLift,
                    y + Math.sin(age * 0.31 + index) * 0.035,
                    Math.sin(angle) * spread - curl * 0.55 + cell.wind().z * windLift);
                float radius = (float) ((0.18 + unit(value, 7) * 0.24)
                    * (0.78 + cell.intensity() * 0.58) * (1.0 - progress * 0.40));
                float temperature = Mth.clamp(cell.heat() * (1.08F - (float) progress * 0.45F), 0.0F, 1.0F);
                Colour colour = fireColour(temperature);
                float alpha = (float) (0.42 + 0.42 * Math.pow(1.0 - progress, 0.35));
                billboard(pose, buffer, center, radius,
                    (float) (unit(value, 8) * Mth.TWO_PI + age * 0.018),
                    colour.red(), colour.green(), colour.blue(), alpha, 0xF000F0, basis);
            }
        }
    }

    public static void renderEmbers(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final List<FireRenderCell> cells, final Quaternionf camera) {
        Basis basis = Basis.from(camera);
        for (FireRenderCell cell : cells) {
            if (cell.heat() < 0.12F) continue;
            int samples = sampleCount(cell, 1, 5);
            for (int index = 0; index < samples; index++) {
                long value = mix(cell.seed() ^ EMBER_SEED ^ (long) index * 0xD1B54A32D192ED03L);
                double life = 28.0 + unit(value, 0) * 34.0;
                double progress = positiveModulo(age + unit(value, 1) * life, life) / life;
                double height = 0.4 + progress * (2.0 + cell.intensity() * 3.1);
                double drift = progress * progress * (3.0 + cell.intensity() * 4.0);
                Vec3 center = cell.relativePosition().add(
                    (unit(value, 2) - 0.5) * 0.7 + cell.wind().x * drift,
                    height,
                    (unit(value, 3) - 0.5) * 0.7 + cell.wind().z * drift);
                float radius = 0.035F + (float) unit(value, 4) * 0.045F;
                billboard(pose, buffer, center, radius, 0.0F, 255,
                    104 + (int) (unit(value, 5) * 80), 18, (float) (0.8 * (1.0 - progress)),
                    0xF000F0, basis);
            }
        }
    }

    public static void renderSmoke(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final List<FireRenderCell> cells, final Quaternionf camera) {
        Basis basis = Basis.from(camera);
        for (FireRenderCell cell : cells) {
            float smokeFactor = Mth.clamp((1.0F - cell.heat()) * 0.72F
                + (cell.phase() == FirePhase.SMOLDERING ? 0.70F : 0.18F), 0.16F, 1.0F);
            int samples = sampleCount(cell, 2, 11);
            for (int index = 0; index < samples; index++) {
                long value = mix(cell.seed() ^ SMOKE_SEED ^ (long) index * 0x94D049BB133111EBL);
                double life = 68.0 + unit(value, 0) * 92.0;
                double progress = positiveModulo(age + unit(value, 1) * life, life) / life;
                double rise = 0.65 + progress * (3.5 + cell.intensity() * 4.8);
                double drift = progress * progress * (5.0 + cell.intensity() * 8.0);
                double turbulence = Math.sin(age * 0.035 + unit(value, 2) * Mth.TWO_PI)
                    * (0.18 + progress * 0.62);
                Vec3 center = cell.relativePosition().add(
                    (unit(value, 3) - 0.5) * 0.85 + turbulence + cell.wind().x * drift,
                    rise,
                    (unit(value, 4) - 0.5) * 0.85 - turbulence * 0.45 + cell.wind().z * drift);
                float radius = (float) ((0.34 + unit(value, 5) * 0.48)
                    * (0.82 + progress * 1.75) * (0.72 + cell.intensity() * 0.44));
                int shade = Mth.clamp(42 + (int) (unit(value, 6) * 34)
                    + (int) ((1.0F - smokeFactor) * 36.0F), 30, 120);
                float fade = (float) Math.pow(1.0 - progress, 0.48);
                float alpha = Mth.clamp((0.22F + smokeFactor * 0.32F) * fade, 0.02F, 0.52F);
                billboard(pose, buffer, center, radius,
                    (float) (unit(value, 7) * Mth.TWO_PI + age * 0.0025),
                    shade, shade + 3, shade + 8, alpha, 0xA000A0, basis);
            }
        }
    }

    private static int sampleCount(final FireRenderCell cell, final int minimum, final int maximum) {
        double lod = cell.distance() < 48.0 ? 1.0 : cell.distance() < 112.0 ? 0.58 : 0.30;
        return Mth.clamp((int) Math.ceil((minimum + (maximum - minimum) * cell.intensity()) * lod),
            minimum, maximum);
    }

    private static Colour fireColour(final float heat) {
        if (heat >= 0.82F) {
            float t = (heat - 0.82F) / 0.18F;
            return new Colour(255, Mth.lerpInt(t, 205, 250), Mth.lerpInt(t, 62, 196));
        }
        if (heat >= 0.48F) {
            float t = (heat - 0.48F) / 0.34F;
            return new Colour(255, Mth.lerpInt(t, 92, 205), Mth.lerpInt(t, 18, 62));
        }
        float t = heat / 0.48F;
        return new Colour(Mth.lerpInt(t, 138, 255), Mth.lerpInt(t, 30, 92), 14);
    }

    private static void billboard(final PoseStack.Pose pose, final VertexConsumer buffer,
        final Vec3 center, final float radius, final float rotation,
        final int red, final int green, final int blue, final float alpha,
        final int light, final Basis basis) {
        float cos = Mth.cos(rotation), sin = Mth.sin(rotation);
        float ux = cos * radius, uy = sin * radius;
        float vx = -sin * radius, vy = cos * radius;
        int alphaByte = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        vertex(pose, buffer, center, -ux - vx, -uy - vy, 0.0F, 1.0F,
            red, green, blue, alphaByte, light, basis);
        vertex(pose, buffer, center, -ux + vx, -uy + vy, 0.0F, 0.0F,
            red, green, blue, alphaByte, light, basis);
        vertex(pose, buffer, center, ux + vx, uy + vy, 1.0F, 0.0F,
            red, green, blue, alphaByte, light, basis);
        vertex(pose, buffer, center, ux - vx, uy - vy, 1.0F, 1.0F,
            red, green, blue, alphaByte, light, basis);
    }

    private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer,
        final Vec3 center, final float x, final float y, final float u, final float v,
        final int red, final int green, final int blue, final int alpha,
        final int light, final Basis basis) {
        float ox = basis.rightX * x + basis.upX * y;
        float oy = basis.rightY * x + basis.upY * y;
        float oz = basis.rightZ * x + basis.upZ * y;
        buffer.addVertex(pose, (float) center.x + ox, (float) center.y + oy,
            (float) center.z + oz).setColor(red, green, blue, alpha).setUv(u, v)
            .setOverlay(0).setLight(light)
            .setNormal(pose, basis.normalX, basis.normalY, basis.normalZ);
    }

    private static double positiveModulo(final double value, final double modulus) {
        double result = value % modulus;
        return result < 0.0 ? result + modulus : result;
    }

    private static double unit(final long value, final int lane) {
        return (mix(value + (long) lane * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private record Colour(int red, int green, int blue) { }

    private static final class Basis {
        private final float rightX, rightY, rightZ;
        private final float upX, upY, upZ;
        private final float normalX, normalY, normalZ;

        private Basis(final Vector3f right, final Vector3f up, final Vector3f normal) {
            rightX = right.x; rightY = right.y; rightZ = right.z;
            upX = up.x; upY = up.y; upZ = up.z;
            normalX = normal.x; normalY = normal.y; normalZ = normal.z;
        }

        private static Basis from(final Quaternionf camera) {
            Quaternionf orientation = camera == null ? new Quaternionf() : camera;
            return new Basis(new Vector3f(1.0F, 0.0F, 0.0F).rotate(orientation),
                new Vector3f(0.0F, 1.0F, 0.0F).rotate(orientation),
                new Vector3f(0.0F, 0.0F, 1.0F).rotate(orientation));
        }
    }
}
