package com.andye.warmod.warhead.client.render;

/** Runtime client renderer selection and profiling controls. */
public final class WarheadRenderSettings {
	public enum ParticleRenderer {
		PACKED,
		LEGACY
	}

	private static final float DEFAULT_PARTICLE_BUDGET_MULTIPLIER = 10.0F;
	private static final int MAX_ARRAY_CAPACITY = Integer.MAX_VALUE - 8;
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

	/** Keeps the existing 10x default neutral for the GPU scheduler. */
	public static double gpuBudgetScale() {
		return Math.max(0.001, particleBudgetMultiplier / DEFAULT_PARTICLE_BUDGET_MULTIPLIER);
	}

	public static int conventionalParticleBudget() {
		return scaledCapacity(65_536);
	}

	public static int nuclearSupplementBudget() {
		return scaledCapacity(4_096);
	}

	/**
	 * The multiplier deliberately has no arbitrary upper ceiling. Excessive
	 * values can exhaust client memory; that profiling choice remains explicit.
	 */
	public static void setParticleBudgetMultiplier(final float multiplier) {
		if (!Float.isFinite(multiplier) || multiplier <= 0.0F) {
			throw new IllegalArgumentException("multiplier must be finite and greater than zero");
		}
		particleBudgetMultiplier = multiplier;
		ConventionalBlastVisualV4.clear();
		ConventionalBlastVisualV5.clear();
	}

	public static void resetParticleBudget() {
		setParticleBudgetMultiplier(DEFAULT_PARTICLE_BUDGET_MULTIPLIER);
	}

	public static String displayName() {
		return particleRenderer == ParticleRenderer.PACKED ? "packed" : "legacy";
	}

	private static int scaledCapacity(final int baseCapacity) {
		double scaled = baseCapacity * (double) particleBudgetMultiplier;
		if (!Double.isFinite(scaled) || scaled >= MAX_ARRAY_CAPACITY) {
			return MAX_ARRAY_CAPACITY;
		}
		return Math.max(1, (int) Math.ceil(scaled));
	}
}
