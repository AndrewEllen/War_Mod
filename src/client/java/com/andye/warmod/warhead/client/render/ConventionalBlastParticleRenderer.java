package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadVisualMath;
import com.andye.warmod.warhead.client.WarheadClientVisualProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Analytical non-nuclear blast field inspired by a ground-coupled high-explosive plume.
 * No particle objects are allocated: every fire, smoke and surface-front sample is
 * reconstructed from impact age and seed during rendering.
 */
public final class ConventionalBlastParticleRenderer {
	private static final long FIRE_SEED = 0x535447365F464952L;
	private static final long SMOKE_SEED = 0x535447365F534D4BL;
	private static final long FRONT_SEED = 0x535447365F46524EL;

	private ConventionalBlastParticleRenderer() { }

	public static void renderHot(final PoseStack.Pose pose, final VertexConsumer buffer,
		final double age, final float visualScale, final WarheadClientVisualProfile profile,
		final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
		renderFire(pose, buffer, age, visualScale, profile, seed, lod, camera, true);
	}

	public static void renderCooling(final PoseStack.Pose pose, final VertexConsumer buffer,
		final double age, final float visualScale, final WarheadClientVisualProfile profile,
		final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
		renderFire(pose, buffer, age, visualScale, profile, seed, lod, camera, false);
	}

	private static void renderFire(final PoseStack.Pose pose, final VertexConsumer buffer,
		final double age, final float visualScale, final WarheadClientVisualProfile profile,
		final long seed, final WarheadMesh.Lod lod, final Quaternionf camera, final boolean hotPass) {
		if (age < 0.0 || age > 108.0) return;
		float scale = Mth.clamp(visualScale, 0.28F, 1.55F);
		int samples = switch (lod) { case NEAR -> 760; case MEDIUM -> 390; case FAR -> 140; };
		Basis basis = Basis.from(camera);
		double craterBase = -(2.2 + 6.8 * scale);
		for (int index = 0; index < samples; index++) {
			long value = mix(seed ^ FIRE_SEED ^ (long) index * 0x9E3779B97F4A7C15L);
			double birth = unit(value, 0) * (6.0 + scale * 5.0);
			double life = 22.0 + unit(value, 1) * (30.0 + scale * 22.0);
			double localAge = age - birth;
			if (localAge < 0.0 || localAge >= life) continue;
			double progress = localAge / life;
			boolean hot = progress < 0.58;
			if (hot != hotPass) continue;

			double angle = unit(value, 2) * Mth.TWO_PI;
			double sourceRadius = Math.sqrt(unit(value, 3)) * (1.1 + 3.6 * scale);
			double radialSpeed = (0.16 + unit(value, 4) * 0.34) * scale;
			double radial = sourceRadius + localAge * radialSpeed * (1.0 - 0.38 * progress);
			/* The hot plume rises from the crater, spreads, then flattens at the top. */
			double upward = (0.34 + unit(value, 5) * 0.58) * scale;
			double y = craterBase + 1.2 + localAge * upward
				- localAge * localAge * (0.0035 + 0.0025 / Math.max(0.35, scale));
			double flatten = 1.0 - 0.48 * smooth(progress);
			double curl = Math.sin(localAge * (0.15 + unit(value, 6) * 0.12) + angle)
				* (0.35 + progress * 1.1) * scale;
			Vec3 center = new Vec3(
				Math.cos(angle) * radial * flatten + Math.cos(angle + Math.PI * 0.5) * curl,
				y,
				Math.sin(angle) * radial * flatten + Math.sin(angle + Math.PI * 0.5) * curl
			);
			float radius = (float) ((0.38 + unit(value, 7) * 0.95) * (0.72 + scale * 0.48)
				* (0.72 + progress * 0.78));
			float alpha = (float) Mth.clamp((hotPass ? 0.82 : 0.62)
				* Math.pow(1.0 - progress, hotPass ? 0.42 : 0.74), 0.0, 0.88);
			int red = hotPass ? 255 : Mth.lerpInt((float) progress, 238, 104);
			int green = hotPass ? Mth.lerpInt((float) progress, 224, 112)
				: Mth.lerpInt((float) progress, 122, 58);
			int blue = hotPass ? Mth.lerpInt((float) progress, 94, 20)
				: Mth.lerpInt((float) progress, 40, 26);
			int frame = Math.floorMod((int) Math.floor(localAge / 2.4 + unit(value, 8) * FireballAtlas.FRAME_COUNT),
				FireballAtlas.FRAME_COUNT);
			float u0 = frame / (float) FireballAtlas.FRAME_COUNT + 0.5F / FireballAtlas.ATLAS_WIDTH;
			float u1 = (frame + 1) / (float) FireballAtlas.FRAME_COUNT - 0.5F / FireballAtlas.ATLAS_WIDTH;
			float v0 = 0.5F / FireballAtlas.ATLAS_HEIGHT;
			float v1 = 1.0F - v0;
			billboard(pose, buffer, center, radius, (float) (angle + localAge * 0.018),
				red, green, blue, alpha, hotPass ? 0xF000F0 : 0xC000C0, basis, u0, u1, v0, v1);
		}
	}

	public static void renderSmoke(final PoseStack.Pose pose, final VertexConsumer buffer,
		final double age, final float visualScale, final WarheadClientVisualProfile profile,
		final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
		if (age < 4.0 || age > 340.0) return;
		float scale = Mth.clamp(visualScale, 0.28F, 1.55F);
		int samples = switch (lod) { case NEAR -> 920; case MEDIUM -> 470; case FAR -> 160; };
		Basis basis = Basis.from(camera);
		double craterBase = -(2.2 + 6.8 * scale);
		for (int index = 0; index < samples; index++) {
			long value = mix(seed ^ SMOKE_SEED ^ (long) index * 0xD1B54A32D192ED03L);
			double birth = 4.0 + unit(value, 0) * (42.0 + scale * 28.0);
			double life = 82.0 + unit(value, 1) * (92.0 + scale * 55.0);
			double localAge = age - birth;
			if (localAge < 0.0 || localAge >= life) continue;
			double progress = localAge / life;
			double angle = unit(value, 2) * Mth.TWO_PI;
			double radialBirth = Math.sqrt(unit(value, 3)) * (2.0 + 8.0 * scale);
			double outward = localAge * (0.025 + unit(value, 4) * 0.075) * scale;
			double rise = localAge * (0.055 + unit(value, 5) * 0.11) * scale;
			/* Heavy smoke remains crater coupled and broad; it does not form a nuclear cap. */
			double y = craterBase + 2.0 + rise + Math.sin(localAge * 0.06 + angle) * 0.8 * scale;
			double radial = radialBirth + outward;
			double shear = Math.sin(localAge * 0.035 + unit(value, 6) * Mth.TWO_PI) * progress * 2.2 * scale;
			Vec3 center = new Vec3(Math.cos(angle) * radial + Math.cos(angle + Math.PI * 0.5) * shear,
				y, Math.sin(angle) * radial + Math.sin(angle + Math.PI * 0.5) * shear);
			float radius = (float) ((0.42 + unit(value, 7) * 1.18) * (0.78 + scale * 0.44)
				* (0.72 + progress * 1.05));
			float alpha = (float) Mth.clamp(0.50 * Math.pow(1.0 - progress, 0.52)
				* smooth(localAge / 9.0), 0.0, 0.58);
			int tone = Mth.clamp(46 + (int) (unit(value, 8) * 62.0) + (int) (progress * 30.0), 42, 142);
			billboard(pose, buffer, center, radius, (float) (angle + localAge * 0.006),
				tone, Math.min(148, tone + 4), Math.min(156, tone + 10), alpha,
				0xA000A0, basis, 0.0F, 1.0F, 0.0F, 1.0F);
		}
	}

	/** Dust, soil and vapour samples carried by the same physical speed-of-sound front. */
	public static void renderSurfaceFront(final PoseStack.Pose pose, final VertexConsumer buffer,
		final double age, final double physicalRadius, final float visualScale, final long seed,
		final WarheadMesh.Lod lod, final Quaternionf camera) {
		if (age < 0.0 || physicalRadius <= 0.0) return;
		float scale = Mth.clamp(visualScale, 0.28F, 1.55F);
		double duration = WarheadVisualMath.airShockwaveDurationTicks(scale);
		if (age >= duration) return;
		double fade = Math.pow(1.0 - age / duration, 0.58);
		int samples = switch (lod) { case NEAR -> 360; case MEDIUM -> 180; case FAR -> 72; };
		Basis basis = Basis.from(camera);
		for (int index = 0; index < samples; index++) {
			long value = mix(seed ^ FRONT_SEED ^ (long) index * 0x94D049BB133111EBL);
			double angle = (index + unit(value, 0)) / samples * Mth.TWO_PI;
			double trail = unit(value, 1) * (4.0 + 8.0 * scale);
			double radius = Math.max(0.0, physicalRadius - trail);
			double height = 0.15 + unit(value, 2) * (0.8 + 1.8 * scale)
				+ Math.sin(angle * 7.0 + age * 0.18) * 0.22;
			Vec3 center = new Vec3(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
			float size = (float) ((0.26 + unit(value, 3) * 0.82) * (0.65 + 0.42 * scale));
			float alpha = (float) Mth.clamp((0.22 + unit(value, 4) * 0.28) * fade, 0.0, 0.50);
			boolean pale = unit(value, 5) > 0.38;
			int red = pale ? 188 + (int) (unit(value, 6) * 48.0) : 92 + (int) (unit(value, 6) * 48.0);
			int green = pale ? Math.min(242, red + 8) : Math.max(72, red - 8);
			int blue = pale ? Math.min(250, red + 16) : Math.max(62, red - 18);
			billboard(pose, buffer, center, size, (float) angle, red, green, blue, alpha,
				0xA000A0, basis, 0.0F, 1.0F, 0.0F, 1.0F);
		}
	}

	private static void billboard(final PoseStack.Pose pose, final VertexConsumer buffer, final Vec3 center,
		final float radius, final float rotation, final int red, final int green, final int blue, final float alpha,
		final int light, final Basis basis, final float u0, final float u1, final float v0, final float v1) {
		float cos = Mth.cos(rotation), sin = Mth.sin(rotation);
		float ux = cos * radius, uy = sin * radius;
		float vx = -sin * radius, vy = cos * radius;
		int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
		vertex(pose, buffer, center, -ux - vx, -uy - vy, u0, v1, red, green, blue, a, light, basis);
		vertex(pose, buffer, center, -ux + vx, -uy + vy, u0, v0, red, green, blue, a, light, basis);
		vertex(pose, buffer, center, ux + vx, uy + vy, u1, v0, red, green, blue, a, light, basis);
		vertex(pose, buffer, center, ux - vx, uy - vy, u1, v1, red, green, blue, a, light, basis);
	}

	private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer, final Vec3 center,
		final float x, final float y, final float u, final float v, final int red, final int green,
		final int blue, final int alpha, final int light, final Basis basis) {
		float ox = basis.rightX * x + basis.upX * y;
		float oy = basis.rightY * x + basis.upY * y;
		float oz = basis.rightZ * x + basis.upZ * y;
		buffer.addVertex(pose, (float) center.x + ox, (float) center.y + oy, (float) center.z + oz)
			.setColor(red, green, blue, alpha).setUv(u, v).setOverlay(0).setLight(light)
			.setNormal(pose, basis.normalX, basis.normalY, basis.normalZ);
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
			rightX = right.x; rightY = right.y; rightZ = right.z;
			upX = up.x; upY = up.y; upZ = up.z;
			normalX = normal.x; normalY = normal.y; normalZ = normal.z;
		}
		private static Basis from(final Quaternionf camera) {
			Quaternionf orientation = camera == null ? new Quaternionf() : camera;
			return new Basis(new Vector3f(1,0,0).rotate(orientation),
				new Vector3f(0,1,0).rotate(orientation), new Vector3f(0,0,1).rotate(orientation));
		}
	}
}
