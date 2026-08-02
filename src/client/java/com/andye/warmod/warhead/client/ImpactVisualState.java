package com.andye.warmod.warhead.client;

import com.andye.warmod.warhead.WarheadEffectMath;
import com.andye.warmod.warhead.network.ClientboundWarheadImpactPayload;
import com.andye.warmod.warhead.client.render.BlastCloudLobe;
import com.andye.warmod.warhead.client.render.FireballLobe;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public final class ImpactVisualState {
	private static final int FIREBALL_LOBE_COUNT = 48;
	private static final int BLAST_CLOUD_LOBE_COUNT = 96;
	private final UUID warheadId;
	private final Vec3 impactPosition;
	private final long impactGameTime;
	private final long visualSeed;
	private final float visualScale;
	private final TerrainRingSampler terrainSampler;
	private final TerrainShockfrontField terrainShockfrontField;
	private final List<FireballLobe> fireballLobes;
	private final List<BlastCloudLobe> blastCloudLobes;
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
		this.blastCloudLobes = createBlastCloudLobes(visualSeed);
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

	public List<BlastCloudLobe> blastCloudLobes() {
		return this.blastCloudLobes;
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
			boolean core = index < 24;
			double angle = random.nextDouble(0.0, Math.PI * 2.0);
			double radial = core ? Math.sqrt(random.nextDouble()) * 8.0 : random.nextDouble(8.0, 22.0);
			double y = core ? random.nextDouble(2.0, 18.0) : random.nextDouble(0.0, 30.0);
			Vec3 offset = new Vec3(Math.cos(angle) * radial, y, Math.sin(angle) * radial);
			lobes.add(new FireballLobe(offset, random.nextDouble(0.0, 3.0), random.nextDouble(2.6, 5.6),
				random.nextDouble(0.55, 1.25), random.nextDouble(0.55, 1.25), random.nextDouble(0.5, 3.6),
				random.nextDouble(0.0, Math.PI * 2.0), random.nextInt(8)));
		}
		return List.copyOf(lobes);
	}

	private static List<BlastCloudLobe> createBlastCloudLobes(final long visualSeed) {
		SplittableRandom random = new SplittableRandom(visualSeed ^ 0x424C415354434C44L);
		List<BlastCloudLobe> lobes = new ArrayList<>(BLAST_CLOUD_LOBE_COUNT);
		for (int index = 0; index < BLAST_CLOUD_LOBE_COUNT; index++) {
			boolean upper = index >= 50;
			double angle = random.nextDouble(0.0, Math.PI * 2.0);
			double radial = upper ? random.nextDouble(2.5, 11.0) : random.nextDouble(0.4, 4.5);
			double y = upper ? random.nextDouble(18.0, 34.0) : random.nextDouble(0.0, 25.0);
			boolean core = index % 3 != 0;
			int red = core ? random.nextInt(18, 43) : random.nextInt(50, 91);
			int green = core ? random.nextInt(20, 46) : random.nextInt(54, 96);
			int blue = core ? random.nextInt(24, 53) : random.nextInt(60, 106);
			float opacity = (float) (core ? random.nextDouble(0.46, 0.63) : random.nextDouble(0.28, 0.45));
			lobes.add(new BlastCloudLobe(new Vec3(Math.cos(angle) * radial, y, Math.sin(angle) * radial),
				upper ? random.nextDouble(3.0, 5.5) : random.nextDouble(2.5, 4.3), random.nextDouble(0.80, 1.20), random.nextDouble(1.0, 7.0),
				random.nextDouble(0.0, Math.PI * 2.0), random.nextDouble(0.0, Math.PI * 2.0), upper,
				red, green, blue, opacity));
		}
		return List.copyOf(lobes);
	}
}