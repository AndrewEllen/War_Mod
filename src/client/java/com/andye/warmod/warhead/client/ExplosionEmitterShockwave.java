package com.andye.warmod.warhead.client;

import com.andye.warmod.warhead.WarheadVisualMath;
import java.util.SplittableRandom;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Guaranteed, full-circumference, terrain-following EXPLOSION_EMITTER shockwave band. */
public final class ExplosionEmitterShockwave {
	private static final double MAXIMUM_RADIUS = 512.0;
	private static final long BURST_SEED = 0x45504943454E5445L;
	private ExplosionEmitterShockwave() { }

	public static int emit(final ClientLevel level, final ImpactVisualState state, final double ageTicks,
		final long gameTime, final Profile profile, final ParticleSink sink) {
		if (level == null || state == null || sink == null || ageTicks < 0.0) return 0;
		double radius = WarheadVisualMath.groundShockwaveDistance(ageTicks, state.visualScale());
		if (!Double.isFinite(radius) || radius > MAXIMUM_RADIUS) return 0;
		int emitted = emitInitialBurst(state, ageTicks, sink);
		double[] radii = { radius, Math.max(0.0, radius - 4.0), Math.max(0.0, radius - 8.0) };
		for (int layer = 0; layer < radii.length; layer++) {
			double layerRadius = radii[layer];
			if (layerRadius < 1.0) continue;
			double circumference = Math.PI * 2.0 * layerRadius;
			int segmentCount = Mth.clamp((int) Math.ceil(circumference / profile.segmentSpacing), profile.minimumSegments, profile.maximumSegments);
			double phase = angularPhase(state.visualSeed(), gameTime, layer, segmentCount);
			for (int index = 0; index < segmentCount; index++) {
				double angle = phase + Math.PI * 2.0 * index / segmentCount;
				double x = state.impactPosition().x + Math.cos(angle) * layerRadius;
				double z = state.impactPosition().z + Math.sin(angle) * layerRadius;
				Surface surface = surface(level, x, z);
				if (surface == null) continue;
				Vec3 outward = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
				Vec3 velocity = outward.scale(layer == 2 ? 0.14 : 0.09).add(0.0, layer == 0 ? 0.22 : 0.14, 0.0);
				if (layer == 0) {
					if (index % profile.leadingEmitterStride == 0 && !sink.emit(ParticleTypes.EXPLOSION_EMITTER, surface.position, velocity)) return emitted;
					if (!sink.emit(ParticleTypes.DUST_PLUME, surface.position, velocity)) return emitted;
					emitted += 1 + (index % profile.leadingEmitterStride == 0 ? 1 : 0);
				} else if (layer == 1) {
					int stride = profile.leadingEmitterStride * 2;
					if (index % stride == 0 && !sink.emit(ParticleTypes.EXPLOSION_EMITTER, surface.position, velocity)) return emitted;
					if (!sink.emit(index % 2 == 0 ? ParticleTypes.EXPLOSION : ParticleTypes.CLOUD, surface.position, velocity)) return emitted;
					emitted += 1 + (index % stride == 0 ? 1 : 0);
				} else {
					ParticleOptions block = new BlockParticleOption(ParticleTypes.BLOCK, surface.state);
					ParticleOptions[] trailing = { ParticleTypes.EXPLOSION, ParticleTypes.DUST_PLUME, ParticleTypes.CLOUD, ParticleTypes.LARGE_SMOKE, block };
					for (ParticleOptions particle : trailing) {
						if (!sink.emit(particle, surface.position, velocity)) return emitted;
						emitted++;
					}
					int stride = profile.leadingEmitterStride * 8;
					if (index % stride == 0) {
						if (!sink.emit(ParticleTypes.EXPLOSION_EMITTER, surface.position, velocity)) return emitted;
						emitted++;
					}
				}
			}
		}
		return emitted;
	}

	private static int emitInitialBurst(final ImpactVisualState state, final double ageTicks, final ParticleSink sink) {
		if (ageTicks > 2.0 || state.secondaryBurstEmitted()) return 0;
		SplittableRandom random = new SplittableRandom(state.visualSeed() ^ BURST_SEED);
		int target = random.nextInt(12, 21), emitted = 0;
		for (int index = 0; index < target; index++) {
			double angle = random.nextDouble(0.0, Math.PI * 2.0);
			double radius = random.nextDouble(2.0, 10.0);
			Vec3 position = state.impactPosition().add(Math.cos(angle) * radius, random.nextDouble(0.0, 6.0), Math.sin(angle) * radius);
			if (!sink.emit(ParticleTypes.EXPLOSION_EMITTER, position, new Vec3(Math.cos(angle) * 0.08, 0.18, Math.sin(angle) * 0.08))) break;
			emitted++;
		}
		if (emitted > 0) state.markSecondaryBurstEmitted();
		return emitted;
	}

	private static Surface surface(final ClientLevel level, final double x, final double z) {
		int blockX = Mth.floor(x), blockZ = Mth.floor(z);
		ChunkAccess chunk = level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(blockX), SectionPos.blockToSectionCoord(blockZ));
		if (chunk == null) return null;
		int height = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ) - 1;
		BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
		for (int y = height + 2; y >= height - 4; y--) {
			position.set(blockX, y, blockZ);
			BlockState state = level.getBlockState(position);
			if (state.isAir() || !state.getFluidState().isEmpty()) continue;
			VoxelShape shape = state.getCollisionShape(level, position);
			if (shape.isEmpty()) continue;
			double top = shape.max(Direction.Axis.Y);
			if (!Double.isFinite(top) || top <= 0.0) continue;
			return new Surface(new Vec3(x, y + top + 0.15, z), state);
		}
		return null;
	}

	private static double angularPhase(final long seed, final long gameTime, final int layer, final int segments) {
		long mixed = mix(seed ^ gameTime * 0x9E3779B97F4A7C15L ^ layer * 0x632BE59BD9B4E019L);
		return (mixed & 0xFFFFL) / 65536.0 * (Math.PI * 2.0 / Math.max(1, segments));
	}
	private static long mix(long value) { value ^= value >>> 30; value *= 0xBF58476D1CE4E5B9L; value ^= value >>> 27; value *= 0x94D049BB133111EBL; return value ^ (value >>> 31); }

	public enum Profile {
		NEAR(4.0, 48, 512, 4), MEDIUM(7.0, 32, 320, 6), FAR(12.0, 24, 192, 10),
		NUCLEAR_NEAR(2.5, 72, 768, 3), NUCLEAR_MEDIUM(4.5, 48, 512, 4), NUCLEAR_FAR(8.0, 32, 288, 6);
		private final double segmentSpacing;
		private final int minimumSegments;
		private final int maximumSegments;
		private final int leadingEmitterStride;
		Profile(final double segmentSpacing, final int minimumSegments, final int maximumSegments, final int leadingEmitterStride) {
			this.segmentSpacing = segmentSpacing; this.minimumSegments = minimumSegments;
			this.maximumSegments = maximumSegments; this.leadingEmitterStride = leadingEmitterStride;
		}
	}
	@FunctionalInterface public interface ParticleSink { boolean emit(ParticleOptions particle, Vec3 position, Vec3 velocity); }
	private record Surface(Vec3 position, BlockState state) { }
}