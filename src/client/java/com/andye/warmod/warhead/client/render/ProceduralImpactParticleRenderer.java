package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadVisualMath;
import com.andye.warmod.warhead.client.WarheadClientVisualProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Object-free analytical particle field.
 *
 * <p>Each billboard is a density-compensated sample of the logical particle
 * field. No particle entities are allocated or ticked. Positions, animation,
 * colour and opacity are reconstructed deterministically from impact age and
 * seed during rendering.</p>
 */
public final class ProceduralImpactParticleRenderer {
	private static final long FIRE_SEED = 0x464952455F464945L;
	private static final long SMOKE_SEED = 0x534D4F4B455F4649L;

	private ProceduralImpactParticleRenderer() {
	}

	public static void renderHot(final PoseStack.Pose pose, final VertexConsumer buffer, final double age,
		final float visualScale, final WarheadClientVisualProfile profile, final long seed,
		final WarheadMesh.Lod lod, final Quaternionf camera) {
		renderFire(pose, buffer, age, visualScale, profile, seed, lod, camera, true);
	}

	public static void renderCooling(final PoseStack.Pose pose, final VertexConsumer buffer, final double age,
		final float visualScale, final WarheadClientVisualProfile profile, final long seed,
		final WarheadMesh.Lod lod, final Quaternionf camera) {
		renderFire(pose, buffer, age, visualScale, profile, seed, lod, camera, false);
	}

	public static void renderSmoke(final PoseStack.Pose pose, final VertexConsumer buffer, final double age,
		final float visualScale, final WarheadClientVisualProfile profile, final long seed,
		final List<BlastCloudLobe> lobes, final WarheadMesh.Lod lod, final Quaternionf camera) {
		if (age < profile.smokeStartTick() || age >= profile.cloudDissipationEndTick()
			|| lobes == null || lobes.isEmpty()) return;
		boolean nuclear = profile.payloadType() == WarheadPayloadType.NUCLEAR;
		int samples = smokeSamples(lod, nuclear);
		double fade = Math.pow(1.0 - WarheadVisualMath.clamp(
			(age - profile.cloudRiseEndTick()) / (double) Math.max(1, profile.cloudDissipationEndTick() - profile.cloudRiseEndTick()),
			0.0, 1.0), 0.72);
		if (fade <= 0.001) return;

		double logicalLivingParticles = (nuclear ? 12_000.0 : 3_800.0)
			* profile.particleScale() * (nuclear ? 105.0 : 80.0);
		double weight = Math.max(1.0, logicalLivingParticles / Math.max(1, samples));
		float densityRadiusScale = (float) Mth.clamp(Math.cbrt(weight), 1.0, 3.6);
		float geometryScale = nuclear ? Mth.clamp(visualScale / 3.0F, 0.58F, 1.55F) : Mth.clamp(visualScale, 0.55F, 1.65F);
		Basis basis = Basis.from(camera);

		for (int index = 0; index < samples; index++) {
			long value = mix(seed ^ SMOKE_SEED ^ (long) index * 0x9E3779B97F4A7C15L);
			BlastCloudLobe lobe = lobes.get((int) Math.floor(unit(value, 0) * lobes.size()) % lobes.size());
			Vec3 lobeCenter = BlastCloudRenderer.center(lobe, profile, age, geometryScale);
			if (!lobeCenter.isFinite()) continue;

			double life = 80.0 + unit(value, 1) * 100.0;
			double particleAge = positiveModulo(age + unit(value, 2) * life, life);
			double progress = particleAge / life;
			double theta = unit(value, 3) * Mth.TWO_PI;
			double radial = Math.sqrt(unit(value, 4));
			double vertical = unit(value, 5) * 2.0 - 1.0;
			double spread = lobe.baseRadius() * (0.45 + 0.75 * progress);
			double x = Math.cos(theta) * radial * spread;
			double z = Math.sin(theta) * radial * spread;
			double y = vertical * spread * 0.58 + progress * (1.2 + unit(value, 6) * 3.0);
			double driftAngle = lobe.rotation() + particleAge * (unit(value, 7) - 0.5) * 0.0018;
			double drift = particleAge * (0.003 + unit(value, 8) * 0.012);
			Vec3 center = lobeCenter.add(x + Math.cos(driftAngle) * drift, y, z + Math.sin(driftAngle) * drift);

			float baseRadius = (float) ((1.15 + unit(value, 9) * 2.7) * geometryScale);
			float radius = baseRadius * densityRadiusScale * (float) (0.82 + progress * 0.72);
			double baseAlpha = lobe.opacity() * 0.018 * Math.pow(1.0 - progress, 0.42) * fade;
			float alpha = opticalAlpha(baseAlpha, weight);
			if (alpha <= 0.003F) continue;
			float rotation = (float) (unit(value, 10) * Mth.TWO_PI + particleAge * (unit(value, 11) - 0.5) * 0.008);
			billboard(pose, buffer, center, radius, rotation, Math.min(148, lobe.red()), Math.min(154, lobe.green()), Math.min(164, lobe.blue()), alpha,
				0xA000A0, basis, 0.0F, 1.0F, 0.0F, 1.0F);
		}
	}

	private static void renderFire(final PoseStack.Pose pose, final VertexConsumer buffer, final double age,
		final float visualScale, final WarheadClientVisualProfile profile, final long seed,
		final WarheadMesh.Lod lod, final Quaternionf camera, final boolean hotPass) {
		if (age < profile.fireballGrowthStartTick() || age >= profile.fireballCoolingEndTick()) return;
		boolean nuclear = profile.payloadType() == WarheadPayloadType.NUCLEAR;
		int samples = fireSamples(lod, nuclear);
		double cooling = WarheadVisualMath.clamp((age - profile.fireballHoldEndTick())
			/ (double) Math.max(1, profile.fireballCoolingEndTick() - profile.fireballHoldEndTick()), 0.0, 1.0);
		double intensity = hotPass ? 1.0 - Math.pow(cooling, 1.25)
			: smooth(cooling / 0.30) * Math.pow(1.0 - cooling, 0.52);
		if (intensity <= 0.002) return;

		double logicalParticles = (nuclear ? 22_000.0 : 11_500.0) * profile.particleScale() * intensity;
		double weight = Math.max(1.0, logicalParticles / Math.max(1, samples));
		float densityRadiusScale = (float) Mth.clamp(Math.sqrt(weight), 1.0, 3.2);
		float geometryScale = nuclear ? Mth.clamp(visualScale / 3.0F, 0.58F, 1.55F) : Mth.clamp(visualScale, 0.45F, 1.70F);
		Basis basis = Basis.from(camera);

		for (int index = 0; index < samples; index++) {
			long value = mix(seed ^ FIRE_SEED ^ (long) index * 0xD1B54A32D192ED03L);
			double life = (nuclear ? 30.0 : 18.0) + unit(value, 0) * (nuclear ? 36.0 : 22.0);
			double particleAge = positiveModulo(age + unit(value, 1) * life, life);
			double progress = particleAge / life;
			boolean particleHot = progress < 0.66;
			if (hotPass != particleHot) continue;

			double azimuth = unit(value, 2) * Mth.TWO_PI;
			double yDirection = unit(value, 3) * 1.35 - 0.22;
			double horizontal = Math.sqrt(Math.max(0.0, 1.0 - Math.min(0.98, yDirection * yDirection)));
			double dx = Math.cos(azimuth) * horizontal;
			double dz = Math.sin(azimuth) * horizontal;
			double baseRadius = 0.7 + unit(value, 4) * (nuclear ? 13.0 : 8.0);
			double speed = 0.055 + unit(value, 5) * (nuclear ? 0.31 : 0.23);
			double distance = baseRadius + speed * particleAge;
			double rise = particleAge * (0.035 + unit(value, 6) * 0.075)
				+ particleAge * particleAge * (nuclear ? 0.0011 : 0.0007);
			double turbulence = Math.sin(particleAge * (0.09 + unit(value, 7) * 0.15) + azimuth)
				* (nuclear ? 1.4 : 0.55) * progress;
			Vec3 center = new Vec3(
				dx * distance * geometryScale + Math.cos(azimuth + (float) (Math.PI * 0.5)) * turbulence,
				(yDirection * distance + rise + profile.fireballRise() * 0.12) * geometryScale,
				dz * distance * geometryScale + Math.sin(azimuth + (float) (Math.PI * 0.5)) * turbulence
			);

			float baseSize = (float) ((0.85 + unit(value, 8) * 2.15) * geometryScale);
			float radius = baseSize * densityRadiusScale * (float) (0.75 + progress * 0.72);
			double particleFade = Math.pow(1.0 - progress, hotPass ? 0.34 : 0.72);
			float alpha = opticalAlpha((hotPass ? 0.036 : 0.030) * particleFade * intensity, weight);
			if (alpha <= 0.003F) continue;

			int red = hotPass ? 255 : Mth.lerpInt((float) progress, 208, 72);
			int green = hotPass ? Mth.lerpInt((float) progress, 188, 108) : Mth.lerpInt((float) progress, 116, 64);
			int blue = hotPass ? Mth.lerpInt((float) progress, 48, 18) : Mth.lerpInt((float) progress, 42, 28);
			int frame = Math.floorMod((int) Math.floor(particleAge / (nuclear ? 3.6 : 2.6) + unit(value, 9) * FireballAtlas.FRAME_COUNT), FireballAtlas.FRAME_COUNT);
			float u0 = frame / (float) FireballAtlas.FRAME_COUNT + 0.5F / FireballAtlas.ATLAS_WIDTH;
			float u1 = (frame + 1) / (float) FireballAtlas.FRAME_COUNT - 0.5F / FireballAtlas.ATLAS_WIDTH;
			float v0 = 0.5F / FireballAtlas.ATLAS_HEIGHT;
			float v1 = 1.0F - v0;
			float rotation = (float) (unit(value, 10) * Mth.TWO_PI + particleAge * (unit(value, 11) - 0.5) * 0.022);
			billboard(pose, buffer, center, radius, rotation, red, green, blue, alpha,
				hotPass ? 0xF000F0 : 0xA000A0, basis, u0, u1, v0, v1);
		}
	}

	private static int fireSamples(final WarheadMesh.Lod lod, final boolean nuclear) {
		return switch (lod) {
			case NEAR -> nuclear ? 1_536 : 1_024;
			case MEDIUM -> nuclear ? 768 : 512;
			case FAR -> nuclear ? 256 : 160;
		};
	}

	private static int smokeSamples(final WarheadMesh.Lod lod, final boolean nuclear) {
		return switch (lod) {
			case NEAR -> nuclear ? 2_048 : 1_024;
			case MEDIUM -> nuclear ? 1_024 : 512;
			case FAR -> nuclear ? 320 : 160;
		};
	}

	private static float opticalAlpha(final double perParticleAlpha, final double representedParticles) {
		double base = Mth.clamp(perParticleAlpha, 0.0, 0.95);
		double result = 1.0 - Math.pow(1.0 - base, Math.max(1.0, representedParticles));
		return (float) Mth.clamp(result, 0.0, 0.84);
	}

	private static void billboard(final PoseStack.Pose pose, final VertexConsumer buffer, final Vec3 center,
		final float radius, final float rotation, final int red, final int green, final int blue, final float alpha,
		final int light, final Basis basis, final float u0, final float u1, final float v0, final float v1) {
		float cos = Mth.cos(rotation), sin = Mth.sin(rotation);
		float ux = cos * radius, uy = sin * radius;
		float vx = -sin * radius, vy = cos * radius;
		int alphaByte = Mth.clamp((int) (alpha * 255.0F), 0, 255);
		vertex(pose, buffer, center, -ux - vx, -uy - vy, u0, v1, red, green, blue, alphaByte, light, basis);
		vertex(pose, buffer, center, -ux + vx, -uy + vy, u0, v0, red, green, blue, alphaByte, light, basis);
		vertex(pose, buffer, center, ux + vx, uy + vy, u1, v0, red, green, blue, alphaByte, light, basis);
		vertex(pose, buffer, center, ux - vx, uy - vy, u1, v1, red, green, blue, alphaByte, light, basis);
	}

	private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer, final Vec3 center,
		final float x, final float y, final float u, final float v, final int red, final int green, final int blue,
		final int alpha, final int light, final Basis basis) {
		float ox = basis.rightX * x + basis.upX * y;
		float oy = basis.rightY * x + basis.upY * y;
		float oz = basis.rightZ * x + basis.upZ * y;
		buffer.addVertex(pose, (float) center.x + ox, (float) center.y + oy, (float) center.z + oz)
			.setColor(red, green, blue, alpha).setUv(u, v).setOverlay(0).setLight(light)
			.setNormal(pose, basis.normalX, basis.normalY, basis.normalZ);
	}

	private static double positiveModulo(final double value, final double modulus) {
		double result = value % modulus;
		return result < 0.0 ? result + modulus : result;
	}

	private static double smooth(final double value) {
		double t = WarheadVisualMath.clamp(value, 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}

	private static double unit(final long value, final int lane) {
		long mixed = mix(value + (long) lane * 0x9E3779B97F4A7C15L);
		return (mixed >>> 11) * 0x1.0p-53;
	}

	private static long mix(long value) {
		value ^= value >>> 30;
		value *= 0xBF58476D1CE4E5B9L;
		value ^= value >>> 27;
		value *= 0x94D049BB133111EBL;
		return value ^ value >>> 31;
	}

	private static final class Basis {
		private final float rightX, rightY, rightZ;
		private final float upX, upY, upZ;
		private final float normalX, normalY, normalZ;

		private Basis(final Vector3f right, final Vector3f up, final Vector3f normal) {
			this.rightX = right.x; this.rightY = right.y; this.rightZ = right.z;
			this.upX = up.x; this.upY = up.y; this.upZ = up.z;
			this.normalX = normal.x; this.normalY = normal.y; this.normalZ = normal.z;
		}

		private static Basis from(final Quaternionf camera) {
			Quaternionf orientation = camera == null ? new Quaternionf() : camera;
			return new Basis(
				new Vector3f(1.0F, 0.0F, 0.0F).rotate(orientation),
				new Vector3f(0.0F, 1.0F, 0.0F).rotate(orientation),
				new Vector3f(0.0F, 0.0F, 1.0F).rotate(orientation)
			);
		}
	}
}
