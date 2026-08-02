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
	public static final int MINIMUM_EXPECTED_PARTICLES_PER_NODE = 41;

	private GroundShockParticleEmitter() { }

	public static List<GroundParticleBatch> collect(final ImpactVisualState state, final Vec3 center,
		final double pressureDistance, final int desiredSpokes, final int maximumParticles,
		final double viewerDistance, final long gameTime) {
		if (state == null || center == null || !center.isFinite() || maximumParticles < MINIMUM_EXPECTED_PARTICLES_PER_NODE) return List.of();
		int maximumNodes = Math.min(desiredSpokes * 8, maximumParticles / MINIMUM_EXPECTED_PARTICLES_PER_NODE);
		List<TerrainShockfrontNode> nodes = state.terrainShockfrontField().readyNodes(pressureDistance, desiredSpokes, maximumNodes, gameTime);
		if (nodes.isEmpty()) return List.of();

		List<GroundParticleBatch> batches = new ArrayList<>();
		int total = 0;
		for (TerrainShockfrontNode node : nodes) {
			if (total >= maximumParticles || !node.valid() || !node.visibleFromImpact()) continue;
			SplittableRandom random = new SplittableRandom(state.visualSeed() ^ gameTime ^ node.surfaceBlock().asLong());
			Vec3 outward = new Vec3(node.position().x - center.x, 0.0, node.position().z - center.z);
			double length = Math.sqrt(outward.x * outward.x + outward.z * outward.z);
			if (length < 1.0E-4) continue;
			outward = outward.scale(1.0 / length);
			Vec3 sideways = new Vec3(-outward.z, 0.0, outward.x);
			double strength = Math.max(0.62, 1.0 - node.directDistance() / 900.0);
			boolean forceLongRange = viewerDistance > 32.0;
			List<GroundParticle> particles = new ArrayList<>(98);
			ParticleOptions blockParticle = new BlockParticleOption(ParticleTypes.BLOCK, node.surfaceState());
			addGroup(particles, blockParticle, random.nextInt(15, 31), node.position(), outward, sideways, strength, random, forceLongRange, 1.00);
			addGroup(particles, ParticleTypes.DUST_PLUME, random.nextInt(10, 26), node.position(), outward, sideways, strength, random, forceLongRange, 0.86);
			addGroup(particles, random.nextBoolean() ? ParticleTypes.POOF : ParticleTypes.CLOUD, random.nextInt(8, 21), node.position(), outward, sideways, strength, random, forceLongRange, 0.74);
			addGroup(particles, ParticleTypes.LARGE_SMOKE, random.nextInt(8, 21), node.position(), outward, sideways, strength, random, forceLongRange, 0.68);
			addGroup(particles, ParticleTypes.EXPLOSION, random.nextInt(0, 4), node.position(), outward, sideways, strength, random, forceLongRange, 0.90);
			int allowed = Math.min(particles.size(), maximumParticles - total);
			if (allowed > 0) {
				batches.add(new GroundParticleBatch(node, List.copyOf(particles.subList(0, allowed))));
				total += allowed;
			}
		}
		return List.copyOf(batches);
	}

	private static void addGroup(final List<GroundParticle> particles, final ParticleOptions particle, final int count,
		final Vec3 surface, final Vec3 outward, final Vec3 sideways, final double strength,
		final SplittableRandom random, final boolean forceLongRange, final double velocityScale) {
		for (int index = 0; index < count; index++) {
			double outwardSpeed = random.nextDouble(0.20, 0.90) * strength * velocityScale;
			double sideSpeed = random.nextDouble(-0.13, 0.13) * strength;
			double upwardSpeed = random.nextDouble(0.20, 0.75) * strength * Math.max(0.68, velocityScale);
			Vec3 velocity = outward.scale(outwardSpeed).add(sideways.scale(sideSpeed)).add(0.0, upwardSpeed, 0.0);
			double radialLayer = random.nextDouble(-0.9, 0.9);
			Vec3 position = surface.add(outward.scale(radialLayer)).add(sideways.scale(random.nextDouble(-0.45, 0.45)))
				.add(0.0, random.nextDouble(0.02, 0.37), 0.0);
			particles.add(new GroundParticle(particle, position, velocity, forceLongRange));
		}
	}

	public record GroundParticleBatch(TerrainShockfrontNode node, List<GroundParticle> particles) { }
	public record GroundParticle(ParticleOptions particle, Vec3 position, Vec3 velocity, boolean forceLongRange) { }
}