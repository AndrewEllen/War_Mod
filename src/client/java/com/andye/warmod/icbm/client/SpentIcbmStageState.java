package com.andye.warmod.icbm.client;

import com.andye.warmod.icbm.IcbmConstants;
import com.andye.warmod.icbm.network.ClientboundIcbmSeparationPayload;
import com.andye.warmod.warhead.WarheadDeliveryMode;
import com.andye.warmod.warhead.WarheadYield;
import java.util.SplittableRandom;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public record SpentIcbmStageState(UUID missileId, Vec3 startPosition, Vec3 startVelocity, Vec3 orientationVelocity,
	long separationGameTime, long visualSeed, int lifetimeTicks, int fadeTicks, double drag, double gravity,
	float rollDrift, WarheadYield yield, WarheadDeliveryMode deliveryMode) {
	public static SpentIcbmStageState from(final ClientboundIcbmSeparationPayload payload) {
		SplittableRandom random = new SplittableRandom(payload.visualSeed() ^ 0x5350454E54535447L);
		Vec3 direction = payload.carrierVelocity().lengthSqr() < 1.0E-8 ? new Vec3(0.0, -1.0, 0.0)
			: payload.carrierVelocity().normalize();
		Vec3 lateral = new Vec3(-direction.z, 0.0, direction.x);
		if (lateral.lengthSqr() < 1.0E-8) lateral = new Vec3(1.0, 0.0, 0.0);
		lateral = lateral.normalize().scale(random.nextDouble(0.04, 0.12) * (random.nextBoolean() ? 1.0 : -1.0));
		Vec3 velocity = payload.carrierVelocity().scale(0.55).add(lateral).add(0.0, random.nextDouble(0.02, 0.08), 0.0);
		return new SpentIcbmStageState(payload.missileId(), payload.separationPosition(), velocity,
			payload.carrierVelocity(), payload.separationGameTime(), payload.visualSeed(),
			random.nextInt(IcbmConstants.SPENT_STAGE_MINIMUM_LIFETIME_TICKS, IcbmConstants.SPENT_STAGE_MAXIMUM_LIFETIME_TICKS + 1),
			random.nextInt(20, 31), random.nextDouble(0.975, 0.989), random.nextDouble(0.012, 0.020),
			(float)random.nextDouble(-0.008, 0.008), payload.yield(), payload.deliveryMode());
	}
	public double age(final long gameTime, final double partialTick) {
		return Math.max(0.0, gameTime - this.separationGameTime) + Math.max(0.0, Math.min(1.0, partialTick));
	}
	public Vec3 position(final long gameTime, final double partialTick) {
		double age = this.age(gameTime, partialTick);
		double displacementScale = (1.0 - Math.pow(this.drag, age)) / (1.0 - this.drag);
		return this.startPosition.add(this.startVelocity.scale(displacementScale)).add(0.0, -0.5 * this.gravity * age * age, 0.0);
	}
	public boolean expired(final long gameTime) { return this.age(gameTime, 0.0) >= this.lifetimeTicks; }
	public float alpha(final long gameTime, final double partialTick) {
		double remaining = this.lifetimeTicks - this.age(gameTime, partialTick);
		return (float)Math.max(0.0, Math.min(1.0, remaining / this.fadeTicks));
	}
}
