package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadVisualMath;
import com.andye.warmod.warhead.client.WarheadClientVisualProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Deterministic macro-voxel layer for impact clouds.
 *
 * <p>The soft analytical billboards remain responsible for density and distant
 * blending. These low-count cubes provide the blocky eruptive shapes, rolling
 * ground cloud and large mushroom-cap structure visible in voxel explosion
 * references without allocating client particle objects.</p>
 */
public final class VoxelImpactCloudRenderer {
	private static final long SMOKE_SEED = 0x564F58454C5F534DL;
	private static final long FIRE_SEED = 0x564F58454C5F4649L;

	private VoxelImpactCloudRenderer() {
	}

	public static void renderSmoke(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final double age,
		final float visualScale,
		final WarheadClientVisualProfile profile,
		final long seed,
		final WarheadMesh.Lod lod
	) {
		if (age < profile.smokeStartTick() || age >= profile.cloudDissipationEndTick()) return;
		boolean nuclear = profile.payloadType() == WarheadPayloadType.NUCLEAR;
		int count = switch (lod) {
			case NEAR -> nuclear ? 260 : 128;
			case MEDIUM -> nuclear ? 150 : 76;
			case FAR -> nuclear ? 72 : 36;
		};
		double scale = nuclear
			? Mth.clamp(visualScale / 3.0F, 0.58F, 1.55F)
			: Mth.clamp(visualScale, 0.48F, 1.70F);
		double rise = smooth(age / Math.max(1.0, profile.cloudRiseEndTick()));
		double dissipate = WarheadVisualMath.clamp(
			(age - profile.cloudRiseEndTick())
				/ Math.max(1.0, profile.cloudDissipationEndTick() - profile.cloudRiseEndTick()),
			0.0,
			1.0
		);
		double fade = Math.pow(1.0 - dissipate, 0.70);
		if (fade < 0.01) return;

		for (int index = 0; index < count; index++) {
			long value = mix(seed ^ SMOKE_SEED ^ (long) index * 0x9E3779B97F4A7C15L);
			double selector = unit(value, 0);
			Vec3 center;
			float size;
			if (selector < 0.36) {
				/* Dense rising stem with a slight corkscrew. */
				double fraction = Math.pow(unit(value, 1), 0.82);
				double y = fraction * profile.maximumCloudHeight() * (0.72 + 0.18 * rise);
				double stemRadius = profile.smokeStemWidth() * (0.18 + fraction * 0.24) * scale;
				double angle = unit(value, 2) * Mth.TWO_PI + y * 0.035 + age * 0.007;
				double radial = Math.sqrt(unit(value, 3)) * stemRadius;
				center = new Vec3(Math.cos(angle) * radial, y * scale, Math.sin(angle) * radial);
				size = (float) ((1.6 + unit(value, 4) * 3.8) * scale * (0.70 + fraction * 0.55));
			} else if (selector < 0.78) {
				/* Rolling cap: broad toroidal mass with a denser centre. */
				double fraction = Math.sqrt(unit(value, 1));
				double angle = unit(value, 2) * Mth.TWO_PI;
				double capRadius = profile.smokeCapWidth() * (0.12 + fraction * 0.52) * scale * rise;
				double y = profile.maximumCloudHeight() * scale
					* (0.66 + 0.20 * rise + (unit(value, 3) - 0.5) * 0.16);
				center = new Vec3(Math.cos(angle) * capRadius, y, Math.sin(angle) * capRadius);
				size = (float) ((2.2 + unit(value, 4) * 5.2) * scale * (0.75 + fraction * 0.50));
			} else {
				/* Ground-hugging dust skirt and turbulent ejecta base. */
				double angle = unit(value, 1) * Mth.TWO_PI;
				double expansion = Math.min(1.0, age / (nuclear ? 44.0 : 28.0));
				double radial = Math.sqrt(unit(value, 2))
					* profile.smokeCapWidth() * 0.72 * scale * expansion;
				double y = (0.5 + unit(value, 3) * profile.smokeStemWidth() * 0.18) * scale;
				center = new Vec3(Math.cos(angle) * radial, y, Math.sin(angle) * radial);
				size = (float) ((1.2 + unit(value, 4) * 3.3) * scale);
			}

			double tonal = unit(value, 5);
			int base = nuclear ? 34 : 42;
			int red = Mth.clamp(base + (int) (tonal * 52.0 + dissipate * 24.0), 24, 118);
			int green = Mth.clamp(red + (nuclear ? 2 : 5), 26, 126);
			int blue = Mth.clamp(red + (nuclear ? 7 : 8), 30, 136);
			float alpha = (float) Mth.clamp((0.22 + unit(value, 6) * 0.30) * fade * Math.min(1.0, age / 8.0), 0.0, 0.58);
			cube(pose, buffer, center, size, red, green, blue, alpha, 0xA000A0);
		}
	}

	public static void renderFire(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final double age,
		final float visualScale,
		final WarheadClientVisualProfile profile,
		final long seed,
		final WarheadMesh.Lod lod,
		final boolean hotPass
	) {
		if (age < profile.fireballGrowthStartTick() || age >= profile.fireballCoolingEndTick()) return;
		boolean nuclear = profile.payloadType() == WarheadPayloadType.NUCLEAR;
		int count = switch (lod) {
			case NEAR -> nuclear ? 128 : 72;
			case MEDIUM -> nuclear ? 72 : 42;
			case FAR -> nuclear ? 32 : 18;
		};
		double scale = nuclear
			? Mth.clamp(visualScale / 3.0F, 0.58F, 1.55F)
			: Mth.clamp(visualScale, 0.45F, 1.70F);
		double cooling = WarheadVisualMath.clamp(
			(age - profile.fireballHoldEndTick())
				/ Math.max(1.0, profile.fireballCoolingEndTick() - profile.fireballHoldEndTick()),
			0.0,
			1.0
		);
		double intensity = hotPass
			? 1.0 - Math.pow(cooling, 1.10)
			: smooth(cooling / 0.28) * Math.pow(1.0 - cooling, 0.62);
		if (intensity < 0.01) return;
		double growth = smooth(age / Math.max(8.0, profile.fireballHoldEndTick()));
		double radius = (nuclear ? 22.0 : 10.0) * scale * (0.28 + growth * 0.92);

		for (int index = 0; index < count; index++) {
			long value = mix(seed ^ FIRE_SEED ^ (long) index * 0xD1B54A32D192ED03L);
			double azimuth = unit(value, 0) * Mth.TWO_PI;
			double vertical = unit(value, 1) * 1.65 - 0.42;
			double horizontal = Math.sqrt(Math.max(0.0, 1.0 - Math.min(0.98, vertical * vertical)));
			double radial = Math.cbrt(unit(value, 2)) * radius;
			double rise = age * (0.045 + unit(value, 3) * 0.055) * scale;
			Vec3 center = new Vec3(
				Math.cos(azimuth) * horizontal * radial,
				vertical * radial + rise + profile.fireballRise() * 0.08 * scale,
				Math.sin(azimuth) * horizontal * radial
			);
			float size = (float) ((1.25 + unit(value, 4) * (nuclear ? 5.2 : 3.2)) * scale);
			int red;
			int green;
			int blue;
			if (hotPass) {
				red = 255;
				green = Mth.clamp(154 + (int) (unit(value, 5) * 82), 154, 236);
				blue = Mth.clamp(28 + (int) (unit(value, 6) * 54), 28, 82);
			} else {
				red = Mth.clamp(176 - (int) (cooling * 92), 74, 176);
				green = Mth.clamp(74 - (int) (cooling * 38), 34, 92);
				blue = Mth.clamp(24 + (int) (unit(value, 6) * 22), 20, 48);
			}
			float alpha = (float) Mth.clamp((hotPass ? 0.38 : 0.30) * intensity * (0.72 + unit(value, 7) * 0.28), 0.0, 0.52);
			cube(pose, buffer, center, size, red, green, blue, alpha, hotPass ? 0xF000F0 : 0xC000C0);
		}
	}

	private static void cube(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final Vec3 center,
		final float size,
		final int red,
		final int green,
		final int blue,
		final float alpha,
		final int light
	) {
		float half = size * 0.5F;
		int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
		face(pose, buffer, center, -half, -half, half, half, half, half, red, green, blue, a, light, 0, 0, 1);
		face(pose, buffer, center, half, -half, -half, -half, half, -half, red, green, blue, a, light, 0, 0, -1);
		face(pose, buffer, center, -half, -half, -half, -half, half, half, red, green, blue, a, light, -1, 0, 0);
		face(pose, buffer, center, half, -half, half, half, half, -half, red, green, blue, a, light, 1, 0, 0);
		face(pose, buffer, center, -half, half, half, half, half, -half, red, green, blue, a, light, 0, 1, 0);
		face(pose, buffer, center, -half, -half, -half, half, -half, half, red, green, blue, a, light, 0, -1, 0);
	}

	private static void face(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final Vec3 center,
		final float x0,
		final float y0,
		final float z0,
		final float x1,
		final float y1,
		final float z1,
		final int red,
		final int green,
		final int blue,
		final int alpha,
		final int light,
		final float nx,
		final float ny,
		final float nz
	) {
		if (nx != 0.0F) {
			vertex(pose, buffer, center, x0, y0, z0, 0, 1, red, green, blue, alpha, light, nx, ny, nz);
			vertex(pose, buffer, center, x0, y1, z1, 0, 0, red, green, blue, alpha, light, nx, ny, nz);
			vertex(pose, buffer, center, x1, y1, z1, 1, 0, red, green, blue, alpha, light, nx, ny, nz);
			vertex(pose, buffer, center, x1, y0, z0, 1, 1, red, green, blue, alpha, light, nx, ny, nz);
		} else if (ny != 0.0F) {
			vertex(pose, buffer, center, x0, y0, z0, 0, 1, red, green, blue, alpha, light, nx, ny, nz);
			vertex(pose, buffer, center, x0, y0, z1, 0, 0, red, green, blue, alpha, light, nx, ny, nz);
			vertex(pose, buffer, center, x1, y1, z1, 1, 0, red, green, blue, alpha, light, nx, ny, nz);
			vertex(pose, buffer, center, x1, y1, z0, 1, 1, red, green, blue, alpha, light, nx, ny, nz);
		} else {
			vertex(pose, buffer, center, x0, y0, z0, 0, 1, red, green, blue, alpha, light, nx, ny, nz);
			vertex(pose, buffer, center, x0, y1, z0, 0, 0, red, green, blue, alpha, light, nx, ny, nz);
			vertex(pose, buffer, center, x1, y1, z1, 1, 0, red, green, blue, alpha, light, nx, ny, nz);
			vertex(pose, buffer, center, x1, y0, z1, 1, 1, red, green, blue, alpha, light, nx, ny, nz);
		}
	}

	private static void vertex(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final Vec3 center,
		final float x,
		final float y,
		final float z,
		final float u,
		final float v,
		final int red,
		final int green,
		final int blue,
		final int alpha,
		final int light,
		final float nx,
		final float ny,
		final float nz
	) {
		buffer.addVertex(pose, (float) center.x + x, (float) center.y + y, (float) center.z + z)
			.setColor(red, green, blue, alpha)
			.setUv(u, v)
			.setOverlay(0)
			.setLight(light)
			.setNormal(pose, nx, ny, nz);
	}

	private static double unit(final long value, final int lane) {
		return (mix(value + lane * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
	}

	private static long mix(long value) {
		value ^= value >>> 30;
		value *= 0xBF58476D1CE4E5B9L;
		value ^= value >>> 27;
		value *= 0x94D049BB133111EBL;
		return value ^ value >>> 31;
	}

	private static double smooth(final double value) {
		double t = WarheadVisualMath.clamp(value, 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}
}
