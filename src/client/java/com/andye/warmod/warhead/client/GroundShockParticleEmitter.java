package com.andye.warmod.warhead.client;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/** Creates dense terrain-sourced particle batches without consuming nodes until emission succeeds. */
public final class GroundShockParticleEmitter {
	private GroundShockParticleEmitter() {
	}

	public static List<GroundParticleBatch> collect(
		final ImpactVisualState state,
		final Vec3 center,
		final double pressureDistance,
		final int desiredSpokes,
		final int maximumParticles,
		final double viewerDistance,
		final long gameTime
	) {
		if (state == null || center == null || !center.isFinite() || maximumParticles <= 0) return List.of();
		int maximumNodes = Math.max(1, Math.min(48, maximumParticles / 13));
		List<TerrainShockfrontNode> nodes = state.terrainShockfrontField().readyNodes(pressureDistance, desiredSpokes, maximumNodes, gameTime);
		if (nodes.isEmpty()) return List.of();

		List<GroundParticleBatch> batches = new ArrayList<>();
		int total = 0;
		for (TerrainShockfrontNode node : nodes) {
			if (total >= maximumParticles || !node.valid() || !node.visibleFromImpact()) continue;
			SplittableRandom random = new SplittableRandom(state.visualSeed() ^ gameTime ^ node.surfaceBlock().asLong());
			Vec3 source = node.position().add(0.0, random.nextDouble(0.10, 0.40), 0.0);
			Vec3 outward = new Vec3(node.position().x - center.x, 0.0, node.position().z - center.z);
			double length = Math.sqrt(outward.x * outward.x + outward.z * outward.z);
			if (length < 1.0E-4) continue;
			outward = outward.scale(1.0 / length);
			Vec3 sideways = new Vec3(-outward.z, 0.0, outward.x);
			double strength = Math.max(0.42, 1.0 - node.directDistance() / 260.0);
			boolean forceLongRange = viewerDistance > 32.0;
			List<GroundParticle> particles = new ArrayList<>(28);

			ParticleOptions blockParticle = new BlockParticleOption(ParticleTypes.BLOCK, node.surfaceState());
			addGroup(particles, blockParticle, 6 + random.nextInt(7), source, outward, sideways, strength, random, forceLongRange, 1.0);
			addGroup(particles, ParticleTypes.DUST_PLUME, 3 + random.nextInt(5), source, outward, sideways, strength, random, forceLongRange, 0.72);
			ParticleOptions softDust = random.nextBoolean() ? ParticleTypes.POOF : ParticleTypes.CLOUD;
			addGroup(particles, softDust, 2 + random.nextInt(4), source, outward, sideways, strength, random, forceLongRange, 0.62);
			addGroup(particles, ParticleTypes.LARGE_SMOKE, 2 + random.nextInt(4), source, outward, sideways, strength, random, forceLongRange, 0.48);
			if (node.directDistance() <= pressureDistance * 0.5) {
				addGroup(particles, ParticleTypes.EXPLOSION, random.nextInt(3), source, outward, sideways, strength, random, forceLongRange, 0.85);
			}
			int allowed = Math.min(particles.size(), maximumParticles - total);
			if (allowed > 0) {
				batches.add(new GroundParticleBatch(node, List.copyOf(particles.subList(0, allowed))));
				total += allowed;
			}
		}
		return List.copyOf(batches);
	}

	private static void addGroup(final List<GroundParticle> particles, final ParticleOptions particle, final int count,
		final Vec3 source, final Vec3 outward, final Vec3 sideways, final double strength,
		final SplittableRandom random, final boolean forceLongRange, final double velocityScale) {
		for (int index = 0; index < count; index++) {
			double outwardSpeed = random.nextDouble(0.15, 0.60) * strength * velocityScale;
			double sideSpeed = random.nextDouble(-0.09, 0.09) * strength;
			double upwardSpeed = random.nextDouble(0.16, 0.55) * strength * Math.max(0.55, velocityScale);
			Vec3 velocity = outward.scale(outwardSpeed).add(sideways.scale(sideSpeed)).add(0.0, upwardSpeed, 0.0);
			Vec3 position = source.add(random.nextDouble(-0.18, 0.18), random.nextDouble(0.0, 0.22), random.nextDouble(-0.18, 0.18));
			particles.add(new GroundParticle(particle, position, velocity, forceLongRange));
		}
	}

	public record GroundParticleBatch(TerrainShockfrontNode node, List<GroundParticle> particles) { }
	public record GroundParticle(ParticleOptions particle, Vec3 position, Vec3 velocity, boolean forceLongRange) { }
}