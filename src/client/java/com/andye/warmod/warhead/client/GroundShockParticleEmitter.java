package com.andye.warmod.warhead.client;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/** Secondary terrain-sourced dust behind the guaranteed emitter shockwave ring. */
public final class GroundShockParticleEmitter {
	public static final int MINIMUM_EXPECTED_PARTICLES_PER_NODE = 11;
	private GroundShockParticleEmitter() { }

	public static List<GroundParticleBatch> collect(final ImpactVisualState state, final Vec3 center,
		final double pressureDistance, final int desiredSpokes, final int maximumParticles,
		final double viewerDistance, final long gameTime) {
		if (state == null || center == null || !center.isFinite() || maximumParticles < MINIMUM_EXPECTED_PARTICLES_PER_NODE) return List.of();
		int maximumNodes = Math.min(desiredSpokes * 8, maximumParticles / MINIMUM_EXPECTED_PARTICLES_PER_NODE);
		List<TerrainShockfrontNode> nodes = state.terrainShockfrontField().readyNodes(pressureDistance, desiredSpokes, maximumNodes, gameTime);
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
			boolean force = viewerDistance > 32.0;
			List<GroundParticle> particles = new ArrayList<>(25);
			addGroup(particles, new BlockParticleOption(ParticleTypes.BLOCK, node.surfaceState()), random.nextInt(4, 9), node.position(), outward, sideways, random, force);
			addGroup(particles, ParticleTypes.DUST_PLUME, random.nextInt(3, 8), node.position(), outward, sideways, random, force);
			addGroup(particles, random.nextBoolean() ? ParticleTypes.POOF : ParticleTypes.CLOUD, random.nextInt(2, 6), node.position(), outward, sideways, random, force);
			addGroup(particles, ParticleTypes.LARGE_SMOKE, random.nextInt(2, 6), node.position(), outward, sideways, random, force);
			int allowed = Math.min(particles.size(), maximumParticles - total);
			if (allowed > 0) { batches.add(new GroundParticleBatch(node, List.copyOf(particles.subList(0, allowed)))); total += allowed; }
		}
		return List.copyOf(batches);
	}

	private static void addGroup(final List<GroundParticle> particles, final ParticleOptions particle, final int count,
		final Vec3 surface, final Vec3 outward, final Vec3 sideways, final SplittableRandom random, final boolean force) {
		for (int index = 0; index < count; index++) {
			Vec3 velocity = outward.scale(random.nextDouble(0.10, 0.40))
				.add(sideways.scale(random.nextDouble(-0.06, 0.06))).add(0.0, random.nextDouble(0.08, 0.32), 0.0);
			Vec3 position = surface.add(outward.scale(random.nextDouble(-0.8, 0.2)))
				.add(sideways.scale(random.nextDouble(-0.35, 0.35))).add(0.0, random.nextDouble(0.02, 0.32), 0.0);
			particles.add(new GroundParticle(particle, position, velocity, force));
		}
	}
	public record GroundParticleBatch(TerrainShockfrontNode node, List<GroundParticle> particles) { }
	public record GroundParticle(ParticleOptions particle, Vec3 position, Vec3 velocity, boolean forceLongRange) { }
}