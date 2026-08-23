package com.andye.warmod.fire.client.render;

import com.andye.warmod.fire.FirePhase;
import com.andye.warmod.fire.client.ClientSmokeFlowField.SmokeFlow;
import com.andye.warmod.fire.client.render.FireWorldRenderer.FireRenderPatch;
import com.andye.warmod.fire.client.render.FireWorldRenderer.FireRenderEmber;
import com.andye.warmod.fire.client.render.FireWorldRenderer.FireRenderEmberTrail;
import com.andye.warmod.fire.client.render.FireWorldRenderer.FireRenderSmokeCluster;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Temperature-coloured analytical particles reconstructed from surface patches. */
public final class FireParticleRenderer {
    private static final long FLAME_SEED = 0x464952455F464C4DL;
    private static final long SMOKE_SEED = 0x534D4F4B455F4649L;
    private static final long EMBER_SEED = 0x454D4245525F4649L;

    private FireParticleRenderer() { }

    public static void renderFlames(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double gameTime, final List<FireRenderPatch> patches,
        final Quaternionf camera) {
        Basis basis = Basis.from(camera);
        for (FireRenderPatch patch : patches) {
            if (patch.phase() == FirePhase.SMOLDERING || patch.heat() < 0.075F) continue;
            double patchAge = Math.max(0.0, gameTime - patch.ignitionGameTime());
            float stageScale = stageScale(patch.phase(), patch.coverage());
			float clumpScale = 1.0F + patch.clumpStrength() * 0.55F;
            int samples = flameSamples(patch);
            double footprint = (0.055 + patch.coverage() * 0.48)
				* (1.0 + patch.clumpStrength() * 0.18);
            double height = (0.22 + patch.intensity() * 1.85) * stageScale * clumpScale;
            for (int index = 0; index < samples; index++) {
                long value = mix(patch.seed() ^ FLAME_SEED
                    ^ (long) index * 0x9E3779B97F4A7C15L);
                double delay = index * 1.55 + unit(value, 0) * 5.0;
                if (patchAge < delay) continue;
                double life = 12.0 + unit(value, 1) * 16.0;
                double particleAge = positiveModulo(patchAge - delay, life);
                double progress = particleAge / life;
                double angle = unit(value, 2) * Mth.TWO_PI;
                double radius = Math.sqrt(unit(value, 3)) * footprint
                    * (1.0 - progress * 0.48);
                Vec3 base = surfaceOffset(patch.face(), Math.cos(angle) * radius,
                    Math.sin(angle) * radius);
                double curl = Math.sin(gameTime * (0.16 + unit(value, 4) * 0.13)
                    + unit(value, 5) * Mth.TWO_PI) * (0.035 + progress * 0.12);
                double windAge = particleAge * (0.075 + patch.intensity() * 0.045);
                Vec3 center = patch.relativePosition().add(base).add(
                    curl + patch.wind().x * windAge,
                    0.035 + progress * height * (0.72 + unit(value, 6) * 0.48),
                    -curl * 0.48 + patch.wind().z * windAge);
                float size = (float) ((0.095 + unit(value, 7) * 0.19)
                    * (0.55 + patch.coverage() * 0.72)
					* (1.0 + patch.clumpStrength() * 0.28)
                    * (1.0 - progress * 0.38));
                float temperature = Mth.clamp(patch.heat()
                    * (1.16F - (float) progress * 0.48F), 0.0F, 1.0F);
                Colour colour = fireColour(temperature);
                float alpha = (float) ((0.30 + stageScale * 0.54)
                    * Math.pow(1.0 - progress, 0.22));
                billboard(pose, buffer, center, size,
                    (float) (unit(value, 8) * Mth.TWO_PI + gameTime * 0.016),
                    colour.red(), colour.green(), colour.blue(), alpha, 0xF000F0, basis);
            }
        }
    }

    public static void renderEmbers(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double gameTime, final List<FireRenderPatch> patches,
        final Quaternionf camera) {
        Basis basis = Basis.from(camera);
        for (FireRenderPatch patch : patches) {
            if (patch.heat() < 0.22F || patch.coverage() < 0.26F) continue;
            double patchAge = Math.max(0.0, gameTime - patch.ignitionGameTime());
            int samples = lodSamples(patch, 1,
                Math.max(1, Mth.ceil(patch.coverage() * patch.intensity() * 3.0F)));
            for (int index = 0; index < samples; index++) {
                long value = mix(patch.seed() ^ EMBER_SEED
                    ^ (long) index * 0xD1B54A32D192ED03L);
                double delay = 20.0 + index * 7.0 + unit(value, 0) * 18.0;
                if (patchAge < delay) continue;
				/* These are short local sparks only. Networked firebrands below own
				   every long windborne path capable of ignition. */
				double life = 8.0 + unit(value, 1) * 6.0;
                double particleAge = positiveModulo(patchAge - delay, life);
                double progress = particleAge / life;
				double windAge = particleAge * 0.015;
                Vec3 center = patch.relativePosition().add(
                    (unit(value, 2) - 0.5) * patch.coverage()
                        + patch.wind().x * windAge,
					0.16 + progress * (0.32 + patch.intensity() * 0.36),
                    (unit(value, 3) - 0.5) * patch.coverage()
                        + patch.wind().z * windAge);
                float radius = 0.025F + (float) unit(value, 4) * 0.038F;
                billboard(pose, buffer, center, radius, 0.0F, 255,
                    118 + (int) (unit(value, 5) * 94), 28,
                    (float) (0.76 * (1.0 - progress)), 0xF000F0, basis);
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
			/* Keep the flame at the hot firebrand. Older positions become smoke below;
			   a fixed backward chain looked like a detached, static flame object. */
			int tongues = ember.distance() < 72.0 ? 3 : 1;
			for (int index = 0; index < tongues; index++) {
				long value = mix(ember.seed() ^ index * 0x9E3779B97F4A7C15L);
				double flutter = Math.sin(gameTime * (0.24 + unit(value, 0) * 0.12)
					+ unit(value, 1) * Mth.TWO_PI) * 0.030;
				Vec3 center = ember.relativePosition().add(
					flutter + (unit(value, 2) - 0.5) * 0.035,
					(unit(value, 3) - 0.5) * 0.050,
					-flutter * 0.62 + (unit(value, 4) - 0.5) * 0.035);
				float radius = (float) ((0.045 + ember.intensity() * 0.075)
					* (1.0 - index * 0.13) * (1.0 - progress * 0.30));
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
			int wisps = ember.distance() < 72.0 ? 8 : ember.distance() < 112.0 ? 4 : 2;
			int first = Math.max(0, trail.size() - wisps);
			for (int index = first; index < trail.size(); index++) {
				FireRenderEmberTrail sample = trail.get(index);
				double trailAge = Math.max(0.0, gameTime - sample.gameTime());
				double rank = trail.size() - 1 - index + trailAge;
				long value = mix(ember.seed() ^ SMOKE_SEED
					^ (long) index * 0xD1B54A32D192ED03L);
				/* Each wisp departs from an actual older firebrand position, then rises
				   and continues to drift with that sampled local wind. */
				Vec3 center = sample.relativePosition().add(
					sample.wind().x * rank * 0.030,
					rank * 0.028 + Math.sin(gameTime * 0.10 + unit(value, 0) * Mth.TWO_PI) * 0.018,
					sample.wind().z * rank * 0.030);
				float radius = (float) ((0.065 + rank * 0.018 + ember.intensity() * 0.040)
					* (0.85 + unit(value, 1) * 0.30));
				int shade = 112 + (int) (unit(value, 2) * 28.0);
				float alpha = (float) ((0.10F + ember.intensity() * 0.065F)
					* Math.max(0.18, 1.0 - rank / 10.0));
				billboard(pose, buffer, center, radius,
					(float) (unit(value, 3) * Mth.TWO_PI), shade, shade + 4, shade + 8,
					alpha, 0xA000A0, basis);
			}
		}
	}

    public static void renderSmoke(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double gameTime, final List<FireRenderPatch> patches,
        final Quaternionf camera) {
        Basis basis = Basis.from(camera);
        for (FireRenderPatch patch : patches) {
            if (patch.smoke() < 0.018F) continue;
            SmokeFlow flow = patch.smokeFlow();
            double patchAge = Math.max(0.0, gameTime - patch.ignitionGameTime());
            int desired = patch.phase() == FirePhase.IGNITION ? 2
				: Math.max(2, Mth.ceil((2.0F + patch.smoke() * 24.0F)
					* (1.0F + patch.clumpStrength() * 0.62F)));
            int samples = lodSamples(patch, 1, desired);
            double footprint = (0.08 + patch.coverage() * 0.50)
				* (1.0 + patch.clumpStrength() * 0.22);
            for (int index = 0; index < samples; index++) {
                long value = mix(patch.seed() ^ SMOKE_SEED
                    ^ (long) index * 0x94D049BB133111EBL);
                double delay = 5.0 + index * 4.2 + unit(value, 0) * 7.0;
                if (patchAge < delay) continue;
                double life = 64.0 + unit(value, 1) * 76.0;
                double particleAge = positiveModulo(patchAge - delay, life);
                double progress = particleAge / life;
                double angle = unit(value, 2) * Mth.TWO_PI;
                double radial = Math.sqrt(unit(value, 3)) * footprint;
                Vec3 base = surfaceOffset(patch.face(), Math.cos(angle) * radial,
                    Math.sin(angle) * radial);
                double turbulence = Math.sin(gameTime * 0.037
                    + unit(value, 4) * Mth.TWO_PI) * (0.09 + progress * 0.34);
                /* Integrating local wind over particle age creates a readable,
                   shared downwind trail instead of a tiny static offset. */
                double windAge = particleAge * (0.46 + patch.intensity() * 0.36);
                double unconstrainedRise = 0.20 + progress
                    * (2.0 + patch.intensity() * 4.0)
                    * (1.0 + patch.clumpStrength() * 0.28);
                double rise = unconstrainedRise;
                double flowAlongCeiling = 0.0;
                double outdoorWind = 1.0;
                if (flow.enclosed()) {
                    double roof = Math.max(0.55, flow.maximumRise());
                    double pooling = Mth.clamp((unconstrainedRise - roof * 0.55)
                        / Math.max(0.35, roof * 0.45), 0.0, 1.0);
                    rise = Math.min(unconstrainedRise,
                        roof * (0.82 + unit(value, 8) * 0.12));
                    flowAlongCeiling = pooling * (0.28 + progress * 1.55)
                        * Math.max(0.45, roof);
                    outdoorWind = 0.04 + flow.ventilation() * 0.16;
                    turbulence *= 0.24 + flow.ventilation() * 0.34;
                }
                Vec3 center = patch.relativePosition().add(base).add(
                    turbulence + patch.wind().x * windAge * outdoorWind
                        + flow.ventDirection().x * flowAlongCeiling,
					rise,
                    -turbulence * 0.46 + patch.wind().z * windAge * outdoorWind
                        + flow.ventDirection().z * flowAlongCeiling);
                float radius = (float) ((0.16 + unit(value, 5) * 0.31)
                    * (0.70 + progress * 1.20)
					* (0.58 + patch.coverage() * 0.58)
					* (1.0 + patch.clumpStrength() * 0.20));
                if (flow.enclosed()) radius = Math.min(radius,
                    Math.max(0.20F, flow.lateralRadius() * 0.42F));
                int shade = Mth.clamp(150 - (int) (patch.smoke() * 72.0F)
                    - (int) (progress * 18.0) + (int) (unit(value, 6) * 18.0), 52, 168);
                float fade = (float) Math.pow(1.0 - progress, 0.62);
                float alpha = Mth.clamp((0.10F + patch.smoke() * 0.44F) * fade,
					0.025F, 0.58F);
                billboard(pose, buffer, center, radius,
                    (float) (unit(value, 7) * Mth.TWO_PI + gameTime * 0.0022),
                    shade, shade + 4, shade + 9, alpha, 0xA000A0, basis);
            }
        }
    }

    /**
     * Long-range wildfires are transmitted as spatial clusters rather than as
     * every individual flame patch.  A few broad analytical lobes are cheaper
     * to assemble than the thousands of small smoke billboards they replace.
     */
    public static void renderSmokeClusters(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double gameTime, final List<FireRenderSmokeCluster> clusters,
        final Quaternionf camera) {
        Basis basis = Basis.from(camera);
        for (FireRenderSmokeCluster cluster : clusters) {
            int lobes = cluster.distance() < 480.0 ? 9 : cluster.distance() < 960.0 ? 6 : 4;
            float baseRadius = cluster.radius() * (0.48F + cluster.smoke() * 0.38F);
            float baseHeight = cluster.radius() * (0.72F + cluster.smoke() * 0.65F);
            for (int index = 0; index < lobes; index++) {
                long value = mix(cluster.seed() ^ SMOKE_SEED
                    ^ (long) index * 0xD1B54A32D192ED03L);
                double cycle = positiveModulo(gameTime * 0.018 + unit(value, 0), 1.0);
                double angle = unit(value, 1) * Mth.TWO_PI;
                double lateral = (unit(value, 2) - 0.5) * baseRadius * 0.82;
                double rise = cycle * baseHeight;
                double drift = cycle * (7.0 + cluster.radius() * 0.16);
                double curl = Math.sin(gameTime * 0.041 + unit(value, 3) * Mth.TWO_PI)
                    * (0.35 + cluster.radius() * 0.09);
                Vec3 center = cluster.relativePosition().add(
                    Math.cos(angle) * lateral + cluster.wind().x * drift + curl,
                    rise,
                    Math.sin(angle) * lateral + cluster.wind().z * drift - curl * 0.42);
                float radius = baseRadius * (0.42F + (float) cycle * 0.48F)
                    * (0.76F + (float) unit(value, 4) * 0.34F);
                int shade = Mth.clamp(132 - (int) (cluster.smoke() * 58.0F)
                    - (int) (cycle * 22.0F) + (int) (unit(value, 5) * 15.0), 48, 146);
                float alpha = (float) ((0.17 + cluster.smoke() * 0.34)
                    * Math.pow(1.0 - cycle, 0.54));
                billboard(pose, buffer, center, radius,
                    (float) (unit(value, 6) * Mth.TWO_PI + gameTime * 0.0014),
                    shade, shade + 4, shade + 9, alpha, 0xA000A0, basis);
            }
        }
    }

    /** Low-count emissive cores keep a distant wildfire visibly alight without
        transmitting or rebuilding every individual surface flame. */
    public static void renderFlameClusters(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double gameTime,
        final List<FireRenderSmokeCluster> clusters, final Quaternionf camera) {
        Basis basis = Basis.from(camera);
        for (FireRenderSmokeCluster cluster : clusters) {
            if (cluster.heat() < 0.12F) continue;
            int lobes = cluster.distance() < 480.0 ? 3 : cluster.distance() < 960.0 ? 2 : 1;
            float intensity = Mth.clamp(cluster.heat(), 0.0F, 1.0F);
            for (int index = 0; index < lobes; index++) {
                long value = mix(cluster.seed() ^ FLAME_SEED
                    ^ (long) index * 0x9E3779B97F4A7C15L);
                double phase = positiveModulo(gameTime * (0.045 + unit(value, 0) * 0.018)
                    + unit(value, 1), 1.0);
                double angle = unit(value, 2) * Mth.TWO_PI;
                double spread = cluster.radius() * (0.10 + unit(value, 3) * 0.22);
                Vec3 center = cluster.relativePosition().add(Math.cos(angle) * spread,
                    0.30 + cluster.radius() * (0.035 + phase * 0.055),
                    Math.sin(angle) * spread);
                float size = cluster.radius() * (0.075F + intensity * 0.085F)
                    * (0.82F + (float) unit(value, 4) * 0.32F);
                Colour colour = fireColour(intensity * (1.0F - (float) phase * 0.18F));
                float alpha = 0.34F + intensity * 0.42F;
                billboard(pose, buffer, center, size,
                    (float) (unit(value, 5) * Mth.TWO_PI + gameTime * 0.004),
                    colour.red(), colour.green(), colour.blue(), alpha, 0xF000F0, basis);
            }
        }
    }

    private static int flameSamples(final FireRenderPatch patch) {
        int maximum = Math.max(1, Mth.ceil((2.0F + patch.intensity() * 15.0F)
			* (0.18F + patch.coverage() * 0.82F)
			* (1.0F + patch.clumpStrength() * 0.42F)));
        if (patch.phase() == FirePhase.IGNITION) maximum = Math.min(2, maximum);
        return lodSamples(patch, 1, maximum);
    }

    private static int lodSamples(final FireRenderPatch patch, final int minimum,
        final int maximum) {
        double lod = patch.distance() < 48.0 ? 1.0
            : patch.distance() < 112.0 ? 0.58 : 0.30;
        return Mth.clamp((int) Math.ceil(maximum * lod), minimum, maximum);
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

    private static Vec3 surfaceOffset(final Direction face, final double a, final double b) {
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
