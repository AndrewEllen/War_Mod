package com.andye.warmod.warhead.client.render;

/** Runtime client renderer selection and profiling controls. */
public final class WarheadRenderSettings {
	public enum ParticleRenderer {
		PACKED,
		LEGACY
	}

	public static final float MIN_QUALITY_SCALE = 0.25F;
	public static final float MAX_QUALITY_SCALE = 4.0F;
	public static final float DEFAULT_QUALITY_SCALE = 1.0F;
	private static volatile ParticleRenderer particleRenderer = ParticleRenderer.PACKED;
	private static volatile float qualityScale = DEFAULT_QUALITY_SCALE;

	private WarheadRenderSettings() {
	}

	public static ParticleRenderer particleRenderer() {
		return particleRenderer;
	}

	public static boolean usePackedParticles() {
		return particleRenderer == ParticleRenderer.PACKED;
	}

	public static void setParticleRenderer(final ParticleRenderer renderer) {
		if (renderer == null) throw new IllegalArgumentException("renderer");
		particleRenderer = renderer;
	}

	public static float qualityScale() {
		return qualityScale;
	}

	public static int conventionalParticleBudget() {
		return scaledCapacity(65_536);
	}

	public static void setQualityScale(final float scale) {
		if (!Float.isFinite(scale) || scale < MIN_QUALITY_SCALE
			|| scale > MAX_QUALITY_SCALE) {
			throw new IllegalArgumentException("quality scale must be within 0.25 and 4.0");
		}
		qualityScale = scale;
		ConventionalBlastVisualV4.clear();
		ConventionalBlastVisualV5.clear();
	}

	public static void resetQualityScale() {
		setQualityScale(DEFAULT_QUALITY_SCALE);
	}

	public static String displayName() {
		return particleRenderer == ParticleRenderer.PACKED ? "packed" : "legacy";
	}

	private static int scaledCapacity(final int baseCapacity) {
		return Math.max(1, (int) Math.ceil(baseCapacity * (double) qualityScale));
	}
}
