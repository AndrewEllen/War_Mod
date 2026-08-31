package com.andye.warmod.fire.client.render;

import com.andye.warmod.fire.FirePhase;
import com.andye.warmod.fire.FireRepresentationPlan.Card;
import com.andye.warmod.fire.client.ClientSmokeFlowField.SmokeFlow;
import com.andye.warmod.fire.client.render.FireWorldRenderer.FireRenderCell;
import com.andye.warmod.fire.client.render.FireWorldRenderer.FireRenderEmber;
import com.andye.warmod.fire.client.render.FireWorldRenderer.FireRenderEmberTrail;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Packed analytical rendering of the shared coverage-preserving cell plan. */
public final class FireParticleRenderer {
    private static final long FLAME_SEED = 0x464952455F464C4DL;
    private static final long SMOKE_SEED = 0x534D4F4B455F4649L;
    private static final long EMBER_SEED = 0x454D4245525F4649L;

    private FireParticleRenderer() { }

    public static void renderFlames(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double gameTime, final List<FireRenderCell> cells,
        final Quaternionf camera) {
        Basis basis = Basis.from(camera);
        for (FireRenderCell render : cells) {
            if (render.cell().phase() == FirePhase.SMOLDERING
                || render.cell().maximumHeat() < 0.075F) continue;
            double age = Math.max(0.0, gameTime - render.cell().ignitionGameTime());
            float stage = stageScale(render.cell().phase(), render.cell().coveredArea()
                / Math.max(1, render.cell().hostCount()));
            double flameHeight = Math.max(0.05, render.cell().flameEnvelopeHeight());
            double footprint = Math.max(0.06, Math.min(render.cell().cellSize() * 0.48,
                (0.055 + render.cell().coveredArea()
                    / Math.max(1, render.cell().hostCount()) * 0.48)
                    * (1.0 + render.cell().clumpStrength() * 0.18)));
            for (int index = 0; index < render.plan().flames().size(); index++) {
                Card card = render.plan().flames().get(index);
                long value = mix(card.seed() ^ FLAME_SEED);
                double delay = index * 1.55 + unit(value, 0) * 5.0;
                if (age < delay) continue;
                double life = 12.0 + unit(value, 1) * 16.0;
                double particleAge = positiveModulo(age - delay, life);
                double progress = particleAge / life;
                double angle = unit(value, 2) * Mth.TWO_PI;
                double radius = Math.sqrt(unit(value, 3)) * footprint
                    * (1.0 - progress * 0.48);
                Vec3 base = surfaceOffset(render.cell().dominantFace(),
                    Math.cos(angle) * radius, Math.sin(angle) * radius);
                double curl = Math.sin(gameTime * (0.16 + unit(value, 4) * 0.13)
                    + unit(value, 5) * Mth.TWO_PI) * (0.035 + progress * 0.12);
                double windAge = particleAge
                    * (0.075 + render.cell().averageIntensity() * 0.045);
                Vec3 center = card.position().add(base).add(
                    curl + render.wind().x * windAge,
                    0.035 + progress * flameHeight * (0.72 + unit(value, 6) * 0.48),
                    -curl * 0.48 + render.wind().z * windAge);
                float temperature = Mth.clamp(render.cell().maximumHeat()
                    * (1.16F - (float) progress * 0.48F), 0.0F, 1.0F);
                Colour colour = fireColour(temperature);
                float alpha = Mth.clamp((float) (card.opacity() * stage
                    * Math.pow(1.0 - progress, 0.22)), 0.0F, 0.96F);
                float size = card.radius() * (0.88F + (float) unit(value, 7) * 0.22F)
                    * (1.0F - (float) progress * 0.38F);
                billboard(pose, buffer, center, size,
                    (float) (unit(value, 8) * Mth.TWO_PI + gameTime * 0.016),
                    colour.red(), colour.green(), colour.blue(), alpha, 0xF000F0, basis);
            }
        }
    }

    public static void renderSmoke(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double gameTime, final List<FireRenderCell> cells,
        final Quaternionf camera) {
        Basis basis = Basis.from(camera);
        for (FireRenderCell render : cells) {
            SmokeFlow flow = render.smokeFlow();
            double age = Math.max(0.0, gameTime - render.cell().ignitionGameTime());
            for (int index = 0; index < render.plan().smoke().size(); index++) {
                Card card = render.plan().smoke().get(index);
                long value = mix(card.seed() ^ SMOKE_SEED);
                double delay = 4.0 + index * 1.7 + unit(value, 0) * 6.0;
                if (age < delay) continue;
                double life = 78.0 + unit(value, 1) * 92.0;
                double particleAge = positiveModulo(age - delay, life);
                double progress = particleAge / life;
                double turbulence = Math.sin(gameTime * 0.037
                    + unit(value, 2) * Mth.TWO_PI) * (0.08 + progress * 0.32);
                double windAge = particleAge * (0.34
                    + render.cell().averageIntensity() * 0.24);
                double unconstrainedRise = 0.18 + progress
                    * (2.2 + render.cell().maximumHeat() * 4.2);
                double rise = unconstrainedRise;
                double flowAlongCeiling = 0.0;
                double outdoorWind = 1.0;
                if (flow.enclosed()) {
                    double roof = Math.max(0.55, flow.maximumRise());
                    double pooling = Mth.clamp((unconstrainedRise - roof * 0.55)
                        / Math.max(0.35, roof * 0.45), 0.0, 1.0);
                    rise = Math.min(unconstrainedRise,
                        roof * (0.82 + unit(value, 3) * 0.12));
                    flowAlongCeiling = pooling * (0.28 + progress * 1.55)
                        * Math.max(0.45, roof);
                    outdoorWind = 0.04 + flow.ventilation() * 0.16;
                    turbulence *= 0.24 + flow.ventilation() * 0.34;
                }
                Vec3 center = card.position().add(
                    turbulence + render.wind().x * windAge * outdoorWind
                        + flow.ventDirection().x * flowAlongCeiling,
                    rise,
                    -turbulence * 0.46 + render.wind().z * windAge * outdoorWind
                        + flow.ventDirection().z * flowAlongCeiling);
                float radius = card.radius() * (0.82F + (float) progress * 0.68F)
                    * (0.88F + (float) unit(value, 4) * 0.20F);
                if (flow.enclosed()) radius = Math.min(radius,
                    Math.max(0.20F, flow.lateralRadius() * 0.42F));
                int shade = Mth.clamp(154
                    - (int) (render.plan().representedSmokeOpticalDepth() * 34.0F)
                    - (int) (progress * 20.0) + (int) (unit(value, 5) * 17.0), 48, 172);
                float alpha = Mth.clamp(card.opacity()
                    * (float) Math.pow(1.0 - progress, 0.58), 0.0F, 0.72F);
                billboard(pose, buffer, center, radius,
                    (float) (unit(value, 6) * Mth.TWO_PI + gameTime * 0.0022),
                    shade, shade + 4, shade + 9, alpha, 0xA000A0, basis);
            }
        }
    }

    /** Aggregate local sparks; authoritative windborne firebrands render below. */
    public static void renderEmbers(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double gameTime, final List<FireRenderCell> cells,
        final Quaternionf camera) {
        Basis basis = Basis.from(camera);
        for (FireRenderCell render : cells) {
            int samples = Math.min(render.plan().sparkCount(), render.plan().flames().size());
            for (int index = 0; index < samples; index++) {
                Card source = render.plan().flames().get(index);
                long value = mix(source.seed() ^ EMBER_SEED);
                double progress = positiveModulo(gameTime * 0.055 + unit(value, 0), 1.0);
                Vec3 center = source.position().add(
                    (unit(value, 1) - 0.5) * source.radius() + render.wind().x * progress,
                    0.18 + progress * (0.36 + render.cell().averageIntensity() * 0.42),
                    (unit(value, 2) - 0.5) * source.radius() + render.wind().z * progress);
                float radius = Math.min(0.09F, Math.max(0.025F,
                    source.radius() * 0.12F));
                billboard(pose, buffer, center, radius, 0.0F, 255,
                    118 + (int) (unit(value, 3) * 94), 28,
                    (float) (0.78 * (1.0 - progress)), 0xF000F0, basis);
            }
        }
    }

    /** Renders the same authoritative firebrands that perform server collision. */
    public static void renderFirebrands(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double gameTime, final List<FireRenderEmber> embers,
        final Quaternionf camera) {
        Basis basis = Basis.from(camera);
        for (FireRenderEmber ember : embers) {
            double age = Math.max(0.0, gameTime - ember.startGameTime());
            double progress = Mth.clamp(age / Math.max(1.0, ember.lifetime()), 0.0, 1.0);
            int tongues = ember.projectedDiameter() >= 3.25 ? 3
                : ember.projectedDiameter() >= 1.15 ? 2 : 1;
            for (int index = 0; index < tongues; index++) {
                long value = mix(ember.seed() ^ index * 0x9E3779B97F4A7C15L);
                double flutter = Math.sin(gameTime * (0.24 + unit(value, 0) * 0.12)
                    + unit(value, 1) * Mth.TWO_PI) * 0.030;
                Vec3 center = ember.relativePosition().add(
                    flutter + (unit(value, 2) - 0.5) * 0.035,
                    (unit(value, 3) - 0.5) * 0.050,
                    -flutter * 0.62 + (unit(value, 4) - 0.5) * 0.035);
                float radius = (float) ((0.045 + ember.intensity() * 0.075)
                    * (1.0 - index * 0.13) * (1.0 - progress * 0.30)
                    * ember.lodScale());
                Colour colour = fireColour((float) (0.95 - progress * 0.35));
                billboard(pose, buffer, center, radius,
                    (float) (gameTime * 0.08 + unit(value, 5) * Mth.TWO_PI),
                    colour.red(), colour.green(), colour.blue(),
                    (float) (0.88 - progress * 0.36), 0xF000F0, basis);
            }
        }
    }

    public static void renderFirebrandSmoke(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double gameTime,
        final List<FireRenderEmber> embers, final Quaternionf camera) {
        Basis basis = Basis.from(camera);
        for (FireRenderEmber ember : embers) {
            double age = Math.max(0.0, gameTime - ember.startGameTime());
            if (age < 5.0) continue;
            List<FireRenderEmberTrail> trail = ember.trail();
            int wisps = ember.projectedDiameter() >= 3.25 ? 8
                : ember.projectedDiameter() >= 1.15 ? 4
                : ember.projectedDiameter() >= 0.60 ? 2 : 1;
            int first = Math.max(0, trail.size() - wisps);
            for (int index = first; index < trail.size(); index++) {
                FireRenderEmberTrail sample = trail.get(index);
                double trailAge = Math.max(0.0, gameTime - sample.gameTime());
                double rank = trail.size() - 1 - index + trailAge;
                long value = mix(ember.seed() ^ SMOKE_SEED
                    ^ (long) index * 0xD1B54A32D192ED03L);
                Vec3 center = sample.relativePosition().add(
                    sample.wind().x * rank * 0.030,
                    rank * 0.028 + Math.sin(gameTime * 0.10
                        + unit(value, 0) * Mth.TWO_PI) * 0.018,
                    sample.wind().z * rank * 0.030);
                float radius = (float) ((0.065 + rank * 0.018 + ember.intensity() * 0.040)
                    * (0.85 + unit(value, 1) * 0.30) * Math.sqrt(ember.lodScale()));
                int shade = 112 + (int) (unit(value, 2) * 28.0);
                float alpha = (float) ((0.10F + ember.intensity() * 0.065F)
                    * Math.max(0.18, 1.0 - rank / 10.0));
                billboard(pose, buffer, center, radius,
                    (float) (unit(value, 3) * Mth.TWO_PI), shade, shade + 4, shade + 8,
                    alpha, 0xA000A0, basis);
            }
        }
    }

    private static float stageScale(final FirePhase phase, final float coverage) {
        return switch (phase) {
            case IGNITION -> 0.20F + coverage * 0.80F;
            case GROWING -> 0.34F + coverage * 0.66F;
            case FLAMING -> 1.0F;
            case DECAYING -> 0.68F;
            case SMOLDERING -> 0.0F;
        };
    }

    private static Vec3 surfaceOffset(final Direction face, final double a,
        final double b) {
        return switch (face) {
            case UP, DOWN -> new Vec3(a, 0.0, b);
            case NORTH, SOUTH -> new Vec3(a, b, 0.0);
            case EAST, WEST -> new Vec3(0.0, b, a);
        };
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
        value ^= value >>> 30; value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27; value *= 0x94D049BB133111EBL;
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
            return new Basis(new Vector3f(1, 0, 0).rotate(orientation),
                new Vector3f(0, 1, 0).rotate(orientation),
                new Vector3f(0, 0, 1).rotate(orientation));
        }
    }
}
