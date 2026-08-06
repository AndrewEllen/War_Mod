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
 * Dense analytical non-nuclear blast field.
 *
 * <p>The field is rebuilt deterministically from age and seed rather than from
 * thousands of particle objects. Dense depth-writing cores solve the water
 * ordering problem; smaller translucent fringe particles provide soft detail.</p>
 */
public final class ConventionalBlastParticleRenderer {
	private static final long FIRE_SEED = 0x535447375F464952L;
	private static final long SMOKE_SEED = 0x535447375F534D4BL;
	private static final long FRONT_SEED = 0x535447375F46524EL;
	private static final long RETURN_SEED = 0x535447375F524554L;

	private ConventionalBlastParticleRenderer() { }

	public static void renderFireCore(final PoseStack.Pose pose, final VertexConsumer buffer,
		final double age, final float visualScale, final WarheadClientVisualProfile profile,
		final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
		renderFire(pose, buffer, age, visualScale, seed, lod, camera, FireLayer.CORE);
	}

	public static void renderHot(final PoseStack.Pose pose, final VertexConsumer buffer,
		final double age, final float visualScale, final WarheadClientVisualProfile profile,
		final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
		renderFire(pose, buffer, age, visualScale, seed, lod, camera, FireLayer.HOT_FRINGE);
	}

	public static void renderCooling(final PoseStack.Pose pose, final VertexConsumer buffer,
		final double age, final float visualScale, final WarheadClientVisualProfile profile,
		final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
		renderFire(pose, buffer, age, visualScale, seed, lod, camera, FireLayer.COOL_FRINGE);
	}

	private static void renderFire(final PoseStack.Pose pose, final VertexConsumer buffer,
		final double age, final float visualScale, final long seed, final WarheadMesh.Lod lod,
		final Quaternionf camera, final FireLayer layer) {
		if (age < 0.0 || age > 126.0) return;
		float scale = Mth.clamp(visualScale, 0.28F, 1.55F);
		double density = 0.72 + Math.pow(scale, 1.48);
		int baseSamples = switch (lod) { case NEAR -> 1_080; case MEDIUM -> 560; case FAR -> 190; };
		int samples = Math.min(lod == WarheadMesh.Lod.NEAR ? 2_900 : 1_420,
			Math.max(96, (int) Math.round(baseSamples * density)));
		if (layer == FireLayer.CORE) samples = Math.max(80, samples / 3);
		Basis basis = Basis.from(camera);
		double craterBase = -(1.8 + 5.6 * scale);
		for (int index = 0; index < samples; index++) {
			long value = mix(seed ^ FIRE_SEED ^ (long) index * 0x9E3779B97F4A7C15L);
			double birth = unit(value, 0) * (5.0 + scale * 4.0);
			double life = 32.0 + unit(value, 1) * (30.0 + scale * 30.0);
			double localAge = age - birth;
			if (localAge < 0.0 || localAge >= life) continue;
			double progress = localAge / life;
			double coreMarker = unit(value, 2);
			boolean core = coreMarker < 0.30 + 0.12 * scale;
			boolean hot = progress < 0.56 + 0.06 * unit(value, 3);
			if (layer == FireLayer.CORE && !core) continue;
			if (layer == FireLayer.HOT_FRINGE && (!hot || core)) continue;
			if (layer == FireLayer.COOL_FRINGE && (hot || core)) continue;

			double angle = unit(value, 4) * Mth.TWO_PI;
			double sourceRadius = Math.sqrt(unit(value, 5)) * (0.70 + 2.6 * scale) * (core ? 0.52 : 1.0);
			double outwardVelocity = (0.070 + unit(value, 6) * 0.19) * (0.72 + 0.52 * scale);
			double upwardVelocity = (0.46 + unit(value, 7) * 0.72) * (0.68 + 0.52 * scale);
			double drive = 1.0 - 0.58 * smooth(progress);
			double radial = sourceRadius + localAge * outwardVelocity * drive;
			double y = craterBase + 1.0 + localAge * upwardVelocity
				- localAge * localAge * (0.0032 + 0.0014 / Math.max(0.35, scale));
			/* Narrow hot centre pushes the upper fire outwards, rather than forming a flat disc. */
			double topSpread = smooth(localAge / (16.0 + 8.0 * scale));
			radial *= 0.78 + 0.46 * topSpread;
			double curl = Math.sin(localAge * (0.10 + unit(value, 8) * 0.08) + angle * 2.0)
				* (0.10 + progress * 0.55) * scale;
			Vec3 center = new Vec3(
				Math.cos(angle) * radial + Math.cos(angle + Math.PI * 0.5) * curl,
				y,
				Math.sin(angle) * radial + Math.sin(angle + Math.PI * 0.5) * curl
			);
			float radius = (float) ((0.18 + unit(value, 9) * 0.48)
				* (0.76 + scale * 0.34) * (0.78 + progress * 0.46));
			if (layer == FireLayer.CORE) radius *= 1.18F;
			float alpha = (float) Mth.clamp((layer == FireLayer.CORE ? 0.94 : hot ? 0.80 : 0.58)
				* Math.pow(1.0 - progress, layer == FireLayer.CORE ? 0.30 : hot ? 0.42 : 0.72), 0.0, 0.96);
			int red = layer == FireLayer.COOL_FRINGE ? Mth.lerpInt((float) progress, 244, 105) : 255;
			int green = layer == FireLayer.CORE ? Mth.lerpInt((float) progress, 246, 154)
				: hot ? Mth.lerpInt((float) progress, 224, 112) : Mth.lerpInt((float) progress, 132, 54);
			int blue = layer == FireLayer.CORE ? Mth.lerpInt((float) progress, 154, 30)
				: hot ? Mth.lerpInt((float) progress, 80, 18) : Mth.lerpInt((float) progress, 34, 22);
			int frame = Math.floorMod((int) Math.floor(localAge / 2.1 + unit(value, 10) * FireballAtlas.FRAME_COUNT),
				FireballAtlas.FRAME_COUNT);
			float u0 = frame / (float) FireballAtlas.FRAME_COUNT + 0.5F / FireballAtlas.ATLAS_WIDTH;
			float u1 = (frame + 1) / (float) FireballAtlas.FRAME_COUNT - 0.5F / FireballAtlas.ATLAS_WIDTH;
			float v0 = 0.5F / FireballAtlas.ATLAS_HEIGHT;
			float v1 = 1.0F - v0;
			billboard(pose, buffer, center, radius, (float) (angle + localAge * 0.024),
				red, green, blue, alpha, layer == FireLayer.CORE ? 0xF000F0 : hot ? 0xF000F0 : 0xC000C0,
				basis, u0, u1, v0, v1);
		}
	}

	public static void renderSmokeCore(final PoseStack.Pose pose, final VertexConsumer buffer,
		final double age, final float visualScale, final WarheadClientVisualProfile profile,
		final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
		renderSmokeLayer(pose, buffer, age, visualScale, seed, lod, camera, true);
	}

	public static void renderSmoke(final PoseStack.Pose pose, final VertexConsumer buffer,
		final double age, final float visualScale, final WarheadClientVisualProfile profile,
		final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
		renderSmokeLayer(pose, buffer, age, visualScale, seed, lod, camera, false);
	}

	private static void renderSmokeLayer(final PoseStack.Pose pose, final VertexConsumer buffer,
		final double age, final float visualScale, final long seed, final WarheadMesh.Lod lod,
		final Quaternionf camera, final boolean corePass) {
		if (age < 5.0 || age > 360.0) return;
		float scale = Mth.clamp(visualScale, 0.28F, 1.55F);
		double density = 0.82 + Math.pow(scale, 1.55);
		int baseSamples = switch (lod) { case NEAR -> 1_420; case MEDIUM -> 720; case FAR -> 240; };
		int samples = Math.min(lod == WarheadMesh.Lod.NEAR ? 3_600 : 1_700,
			Math.max(120, (int) Math.round(baseSamples * density)));
		if (corePass) samples = Math.max(100, samples / 4);
		Basis basis = Basis.from(camera);
		double craterBase = -(1.8 + 5.6 * scale);
		for (int index = 0; index < samples; index++) {
			long value = mix(seed ^ SMOKE_SEED ^ (long) index * 0xD1B54A32D192ED03L);
			double birth = 5.0 + unit(value, 0) * (40.0 + scale * 24.0);
			double life = 96.0 + unit(value, 1) * (105.0 + scale * 66.0);
			double localAge = age - birth;
			if (localAge < 0.0 || localAge >= life) continue;
			double progress = localAge / life;
			double coreMarker = unit(value, 2);
			boolean core = coreMarker < 0.20 + 0.10 * scale;
			if (core != corePass) continue;
			double angle = unit(value, 3) * Mth.TWO_PI;
			double radialBirth = Math.sqrt(unit(value, 4)) * (0.9 + 5.8 * scale) * (core ? 0.48 : 1.0);
			double outward = localAge * (0.018 + unit(value, 5) * 0.054) * (0.70 + 0.46 * scale);
			double rise = localAge * (0.080 + unit(value, 6) * 0.145) * (0.68 + 0.50 * scale);
			double y = craterBase + 1.5 + rise + Math.sin(localAge * 0.045 + angle * 2.0) * 0.42 * scale;
			double radial = radialBirth + outward;
			double shear = Math.sin(localAge * 0.027 + unit(value, 7) * Mth.TWO_PI)
				* progress * (0.65 + 0.85 * scale);
			Vec3 center = new Vec3(Math.cos(angle) * radial + Math.cos(angle + Math.PI * 0.5) * shear,
				y, Math.sin(angle) * radial + Math.sin(angle + Math.PI * 0.5) * shear);
			float radius = (float) ((0.17 + unit(value, 8) * 0.54) * (0.78 + scale * 0.34)
				* (0.72 + progress * 0.76));
			if (corePass) radius *= 1.20F;
			float alpha = (float) Mth.clamp((corePass ? 0.86 : 0.40)
				* Math.pow(1.0 - progress, corePass ? 0.42 : 0.58) * smooth(localAge / 7.0),
				0.0, corePass ? 0.90 : 0.48);
			int tone = Mth.clamp((corePass ? 32 : 48) + (int) (unit(value, 9) * (corePass ? 28.0 : 58.0))
				+ (int) (progress * 42.0), 28, 154);
			billboard(pose, buffer, center, radius, (float) (angle + localAge * 0.007),
				tone, Math.min(158, tone + 4), Math.min(168, tone + 10), alpha,
				corePass ? 0x900090 : 0xA000A0, basis, 0.0F, 1.0F, 0.0F, 1.0F);
		}
	}

	/** Dust and vapour carried by the same physical speed-of-sound front. */
	public static void renderSurfaceFront(final PoseStack.Pose pose, final VertexConsumer buffer,
		final double age, final double physicalRadius, final float visualScale, final long seed,
		final WarheadMesh.Lod lod, final Quaternionf camera) {
		if (age < 0.0 || physicalRadius <= 0.0) return;
		float scale = Mth.clamp(visualScale, 0.28F, 1.55F);
		double duration = WarheadVisualMath.airShockwaveDurationTicks(scale);
		if (age >= duration) return;
		double fade = Math.pow(1.0 - age / duration, 0.58);
		int base = switch (lod) { case NEAR -> 520; case MEDIUM -> 260; case FAR -> 96; };
		int samples = Math.min(1_100, (int) Math.round(base * (0.74 + Math.pow(scale, 1.22))));
		Basis basis = Basis.from(camera);
		for (int index = 0; index < samples; index++) {
			long value = mix(seed ^ FRONT_SEED ^ (long) index * 0x94D049BB133111EBL);
			double angle = (index + unit(value, 0)) / samples * Mth.TWO_PI;
			double band = unit(value, 1);
			double trail = band * (3.0 + 7.0 * scale);
			double radius = Math.max(0.0, physicalRadius - trail);
			double lift = Math.pow(1.0 - band, 1.8) * (0.65 + 2.2 * scale);
			double height = 0.10 + unit(value, 2) * (0.35 + 0.75 * scale) + lift
				+ Math.sin(angle * 9.0 + age * 0.21) * 0.16;
			Vec3 center = new Vec3(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
			float size = (float) ((0.10 + unit(value, 3) * 0.34) * (0.76 + 0.34 * scale));
			float alpha = (float) Mth.clamp((0.20 + unit(value, 4) * 0.34) * fade, 0.0, 0.54);
			boolean white = unit(value, 5) > 0.26;
			int tone = white ? 192 + (int) (unit(value, 6) * 54.0) : 104 + (int) (unit(value, 6) * 46.0);
			int red = tone;
			int green = white ? Math.min(248, tone + 3) : Math.max(86, tone - 7);
			int blue = white ? Math.min(252, tone + 8) : Math.max(74, tone - 17);
			billboard(pose, buffer, center, size, (float) angle, red, green, blue, alpha,
				0xA000A0, basis, 0.0F, 1.0F, 0.0F, 1.0F);
		}
	}

	/** Dust pulled back toward a nuclear epicentre during the negative-pressure phase. */
	public static void renderNuclearReturnFront(final PoseStack.Pose pose, final VertexConsumer buffer,
		final double age, final double physicalRadius, final float radiusScale, final long seed,
		final WarheadMesh.Lod lod, final Quaternionf camera) {
		if (physicalRadius <= 0.0) return;
		double alphaEnvelope = WarheadVisualMath.nuclearReturnWaveAlpha(age, radiusScale);
		if (alphaEnvelope <= 0.0) return;
		int samples = switch (lod) { case NEAR -> 620; case MEDIUM -> 300; case FAR -> 112; };
		Basis basis = Basis.from(camera);
		for (int index = 0; index < samples; index++) {
			long value = mix(seed ^ RETURN_SEED ^ (long) index * 0x9E3779B97F4A7C15L);
			double angle = (index + unit(value, 0)) / samples * Mth.TWO_PI;
			double radius = Math.max(0.0, physicalRadius + (unit(value, 1) - 0.5) * 8.0 * radiusScale);
			double height = 0.25 + unit(value, 2) * (1.4 + 1.8 * radiusScale);
			Vec3 center = new Vec3(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
			float size = (float) ((0.12 + unit(value, 3) * 0.38) * (0.82 + 0.35 * radiusScale));
			float alpha = (float) Mth.clamp(alphaEnvelope * (0.42 + unit(value, 4) * 0.46), 0.0, 0.48);
			int tone = 172 + (int) (unit(value, 5) * 62.0);
			billboard(pose, buffer, center, size, (float) -angle, tone, Math.min(240, tone + 4),
				Math.min(246, tone + 10), alpha, 0xA000A0, basis, 0.0F, 1.0F, 0.0F, 1.0F);
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

	private enum FireLayer { CORE, HOT_FRINGE, COOL_FRINGE }

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
