package com.andye.warmod.warhead.client;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/** Creates bounded, terrain-sourced particles as the shockfront crosses nodes. */
public final class GroundShockParticleEmitter {
	private GroundShockParticleEmitter() {
	}

	public static List<GroundParticle> collect(
		final ImpactVisualState state,
		final Vec3 center,
		final double pressureRadius,
		final int desiredSpokes,
		final int maximumParticles,
		final double viewerDistance,
		final long gameTime
	) {
		if (state == null || center == null || !center.isFinite() || maximumParticles <= 0) {
			return List.of();
		}

		int maximumNodes = Math.max(1, Math.min(48, maximumParticles / 2));
		List<TerrainShockfrontNode> nodes = state.terrainShockfrontField().consumeReached(pressureRadius, desiredSpokes, maximumNodes);
		if (nodes.isEmpty()) {
			return List.of();
		}

		SplittableRandom random = new SplittableRandom(state.visualSeed() ^ gameTime ^ 0x47524F554E444C31L);
		List<GroundParticle> particles = new ArrayList<>();
		for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
			TerrainShockfrontNode node = nodes.get(nodeIndex);
			if (particles.size() >= maximumParticles || !node.valid() || !node.visibleFromImpact()) {
				continue;
			}

			Vec3 source = node.position().add(0.0, random.nextDouble(0.10, 0.40), 0.0);
			Vec3 outward = new Vec3(node.position().x - center.x, 0.0, node.position().z - center.z);
			double horizontalLength = Math.sqrt(outward.x * outward.x + outward.z * outward.z);
			if (horizontalLength < 1.0E-4) {
				continue;
			}
			outward = outward.scale(1.0 / horizontalLength);
			Vec3 sideways = new Vec3(-outward.z, 0.0, outward.x);
			double strength = Math.max(0.18, 1.0 - node.directDistance() / 128.0);
			double outwardSpeed = random.nextDouble(0.08, 0.28) * strength;
			double sideSpeed = random.nextDouble(-0.07, 0.07) * strength;
			Vec3 velocity = outward.scale(outwardSpeed).add(sideways.scale(sideSpeed)).add(0.0, random.nextDouble(0.12, 0.34) * strength, 0.0);
			boolean forceLongRange = viewerDistance > 32.0;

			if (particles.size() < maximumParticles && (nodeIndex % 3 == 0 || random.nextInt(4) == 0)) {
				particles.add(new GroundParticle(
					ParticleTypes.EXPLOSION,
					source,
					velocity.scale(1.45).add(0.0, 0.10, 0.0),
					forceLongRange
				));
			}

			int debrisCount = 1 + random.nextInt(3);
			ParticleOptions debris = new BlockParticleOption(ParticleTypes.BLOCK, node.surfaceState());
			for (int debrisIndex = 0; debrisIndex < debrisCount && particles.size() < maximumParticles; debrisIndex++) {
				particles.add(new GroundParticle(
					debris,
					source,
					velocity.add(random.nextDouble(-0.025, 0.025), random.nextDouble(0.0, 0.08), random.nextDouble(-0.025, 0.025)),
					forceLongRange
				));
			}

			int dustCount = 1 + random.nextInt(2);
			for (int dustIndex = 0; dustIndex < dustCount && particles.size() < maximumParticles; dustIndex++) {
				ParticleOptions dust = (dustIndex == 0 || (random.nextInt() & 1) == 0) ? ParticleTypes.POOF : ParticleTypes.DUST_PLUME;
				particles.add(new GroundParticle(
					dust,
					source,
					velocity.scale(0.65).add(0.0, 0.03, 0.0),
					forceLongRange
				));
			}

			if (particles.size() < maximumParticles && (random.nextInt(3) == 0 || node.directDistance() < 12.0)) {
				particles.add(new GroundParticle(ParticleTypes.SMOKE, source, velocity.scale(0.28).add(0.0, 0.06, 0.0), forceLongRange));
			}
		}
		return List.copyOf(particles);
	}

	public record GroundParticle(ParticleOptions particle, Vec3 position, Vec3 velocity, boolean forceLongRange) {
	}
}
