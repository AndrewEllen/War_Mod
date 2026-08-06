package com.andye.warmod.warhead.client.render;

import net.minecraft.util.Mth;

/** Runtime client renderer selection and profiling controls. */
public final class WarheadRenderSettings {
	public enum ParticleRenderer {
		PACKED,
		LEGACY
	}

	private static final float DEFAULT_PARTICLE_BUDGET_MULTIPLIER = 3.0F;
	private static volatile ParticleRenderer particleRenderer = ParticleRenderer.PACKED;
	private static volatile float particleBudgetMultiplier = DEFAULT_PARTICLE_BUDGET_MULTIPLIER;

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

	public static float particleBudgetMultiplier() {
		return particleBudgetMultiplier;
	}

	public static int conventionalParticleBudget() {
		return Math.max(16_384, Math.round(65_536.0F * particleBudgetMultiplier));
	}

	public static int nuclearSupplementBudget() {
		return Math.max(1_024, Math.round(4_096.0F * particleBudgetMultiplier));
	}

	public static void setParticleBudgetMultiplier(final float multiplier) {
		if (!Float.isFinite(multiplier)) throw new IllegalArgumentException("multiplier");
		particleBudgetMultiplier = Mth.clamp(multiplier, 0.25F, 6.0F);
		ConventionalBlastVisualV4.clear();
	}

	public static void resetParticleBudget() {
		setParticleBudgetMultiplier(DEFAULT_PARTICLE_BUDGET_MULTIPLIER);
	}

	public static String displayName() {
		return particleRenderer == ParticleRenderer.PACKED ? "packed" : "legacy";
	}
}
