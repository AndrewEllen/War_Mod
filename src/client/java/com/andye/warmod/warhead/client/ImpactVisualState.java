package com.andye.warmod.warhead.client;

import com.andye.warmod.warhead.WarheadEffectMath;
import com.andye.warmod.warhead.network.ClientboundWarheadImpactPayload;
import com.andye.warmod.warhead.client.render.FireballLobe;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public final class ImpactVisualState {
	private static final int FIREBALL_LOBE_COUNT = 24;
	private final UUID warheadId;
	private final Vec3 impactPosition;
	private final long impactGameTime;
	private final long visualSeed;
	private final float visualScale;
	private final TerrainRingSampler terrainSampler;
	private final TerrainShockfrontField terrainShockfrontField;
	private final List<FireballLobe> fireballLobes;
	private boolean initialBurstEmitted;
	private boolean secondaryBurstEmitted;
	private long lastContinuousParticleTick = Long.MIN_VALUE;
	private int emittedParticleCount;

	public ImpactVisualState(
		final UUID warheadId,
		final Vec3 impactPosition,
		final long impactGameTime,
		final long visualSeed,
		final float visualScale
	) {
		this.warheadId = warheadId;
		this.impactPosition = impactPosition;
		this.impactGameTime = impactGameTime;
		this.visualSeed = visualSeed;
		this.visualScale = visualScale;
		this.terrainSampler = new TerrainRingSampler(impactPosition, visualSeed);
		this.terrainShockfrontField = new TerrainShockfrontField(impactPosition, visualSeed);
		this.fireballLobes = createFireballLobes(visualSeed);
	}

	public static ImpactVisualState fromPayload(final ClientboundWarheadImpactPayload payload) {
		return new ImpactVisualState(
			payload.warheadId(),
			new Vec3(payload.impactX(), payload.impactY(), payload.impactZ()),
			payload.impactGameTime(),
			payload.visualSeed(),
			payload.visualScale()
		);
	}

	public UUID warheadId() {
		return this.warheadId;
	}

	public Vec3 impactPosition() {
		return this.impactPosition;
	}

	public long impactGameTime() {
		return this.impactGameTime;
	}

	public long visualSeed() {
		return this.visualSeed;
	}

	public float visualScale() {
		return this.visualScale;
	}

	public TerrainRingSampler terrainSampler() {
		return this.terrainSampler;
	}

	public TerrainShockfrontField terrainShockfrontField() {
		return this.terrainShockfrontField;
	}

	public List<FireballLobe> fireballLobes() {
		return this.fireballLobes;
	}

	public double ageTicks(final long clientGameTime, final double partialTick) {
		long wholeTicks = clientGameTime - this.impactGameTime;
		return Math.max(0.0, wholeTicks) + Math.max(0.0, Math.min(1.0, partialTick));
	}

	public boolean isExpired(final long clientGameTime, final double partialTick) {
		return WarheadEffectMath.impactExpired(this.ageTicks(clientGameTime, partialTick));
	}

	boolean initialBurstEmitted() {
		return this.initialBurstEmitted;
	}

	void markInitialBurstEmitted() {
		this.initialBurstEmitted = true;
	}

	boolean secondaryBurstEmitted() {
		return this.secondaryBurstEmitted;
	}

	void markSecondaryBurstEmitted() {
		this.secondaryBurstEmitted = true;
	}

	long lastContinuousParticleTick() {
		return this.lastContinuousParticleTick;
	}

	void markContinuousParticleTick(final long gameTime) {
		this.lastContinuousParticleTick = gameTime;
	}

	int emittedParticleCount() {
		return this.emittedParticleCount;
	}

	void recordParticleEmission() {
		this.emittedParticleCount++;
	}

	private static List<FireballLobe> createFireballLobes(final long visualSeed) {
		SplittableRandom random = new SplittableRandom(visualSeed ^ 0x464952454C4F4245L);
		List<FireballLobe> lobes = new ArrayList<>(FIREBALL_LOBE_COUNT);
		for (int index = 0; index < FIREBALL_LOBE_COUNT; index++) {
			double theta = Math.acos(random.nextDouble(-0.7, 0.95));
			double phi = random.nextDouble(0.0, Math.PI * 2.0);
			double distance = random.nextDouble(1.5, 10.5);
			Vec3 offset = new Vec3(
				Math.sin(theta) * Math.cos(phi) * distance,
				Math.cos(theta) * distance * 0.60,
				Math.sin(theta) * Math.sin(phi) * distance
			);
			lobes.add(new FireballLobe(
				offset,
				random.nextDouble(0.0, 2.2),
				random.nextDouble(1.8, 3.8),
				random.nextDouble(0.70, 1.35),
				random.nextDouble(0.65, 1.15),
				random.nextDouble(0.5, 2.7),
				random.nextDouble(0.0, Math.PI * 2.0),
				random.nextInt(8)
			));
		}
		return List.copyOf(lobes);
	}
}