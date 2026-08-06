package com.andye.warmod.warhead.client.render;

/** Runtime client renderer selection used for side-by-side performance profiling. */
public final class WarheadRenderSettings {
	public enum ParticleRenderer {
		PACKED,
		LEGACY
	}

	private static volatile ParticleRenderer particleRenderer = ParticleRenderer.PACKED;

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

	public static String displayName() {
		return particleRenderer == ParticleRenderer.PACKED ? "packed" : "legacy";
	}
}
