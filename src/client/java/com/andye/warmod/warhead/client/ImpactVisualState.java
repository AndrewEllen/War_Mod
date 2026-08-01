package com.andye.warmod.warhead.client;

import com.andye.warmod.warhead.WarheadEffectMath;
import com.andye.warmod.warhead.network.ClientboundWarheadImpactPayload;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public final class ImpactVisualState {
	private final UUID warheadId;
	private final Vec3 impactPosition;
	private final long impactGameTime;
	private final long visualSeed;
	private final float visualScale;
	private final TerrainRingSampler terrainSampler;

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

	public double ageTicks(final long clientGameTime, final double partialTick) {
		long wholeTicks = clientGameTime - this.impactGameTime;
		return Math.max(0.0, wholeTicks) + Math.max(0.0, Math.min(1.0, partialTick));
	}

	public boolean isExpired(final long clientGameTime, final double partialTick) {
		return WarheadEffectMath.impactExpired(this.ageTicks(clientGameTime, partialTick));
	}
}