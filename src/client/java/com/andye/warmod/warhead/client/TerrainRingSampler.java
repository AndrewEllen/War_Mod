package com.andye.warmod.warhead.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class TerrainRingSampler {
	public enum RingKind {
		PRESSURE,
		DUST
	}

	public record RingSample(
		double x,
		double y,
		double z,
		boolean valid,
		boolean breakBefore
	) {
	}

	private final Vec3 center;
	private final long visualSeed;
	private long pressureGameTime = Long.MIN_VALUE;
	private long dustGameTime = Long.MIN_VALUE;
	private double pressureRadius = Double.NaN;
	private double dustRadius = Double.NaN;
	private int pressureSegments;
	private int dustSegments;
	private List<RingSample> pressureSamples = List.of();
	private List<RingSample> dustSamples = List.of();

	public TerrainRingSampler(final Vec3 center, final long visualSeed) {
		this.center = center;
		this.visualSeed = visualSeed;
	}

	public List<RingSample> sample(
		final ClientLevel level,
		final double radius,
		final int segments,
		final RingKind kind,
		final long clientGameTime
	) {
		if (level == null || !Double.isFinite(radius) || radius <= 0.0 || segments < 3) {
			return List.of();
		}

		if (kind == RingKind.PRESSURE
			&& this.pressureGameTime == clientGameTime
			&& this.pressureSegments == segments
			&& Math.abs(this.pressureRadius - radius) < 2.0) {
			return this.pressureSamples;
		}
		if (kind == RingKind.DUST
			&& this.dustGameTime == clientGameTime
			&& this.dustSegments == segments
			&& Math.abs(this.dustRadius - radius) < 2.0) {
			return this.dustSamples;
		}

		List<RingSample> samples = new ArrayList<>(segments);
		double phase = ((this.visualSeed & 0xFFFFL) / 65536.0) * (Math.PI * 2.0 / segments);
		for (int index = 0; index < segments; index++) {
			double angle = phase + Math.PI * 2.0 * index / segments;
			double x = this.center.x + Math.cos(angle) * radius;
			double z = this.center.z + Math.sin(angle) * radius;
			int blockX = Mth.floor(x);
			int blockZ = Mth.floor(z);
			ChunkAccess chunk = level.getChunkSource().getChunkNow(blockX >> 4, blockZ >> 4);
			if (chunk == null) {
				samples.add(new RingSample(x, this.center.y, z, false, true));
				continue;
			}

			try {
				int surfaceY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
				samples.add(new RingSample(x, surfaceY + 0.06, z, true, false));
			} catch (RuntimeException ignored) {
				samples.add(new RingSample(x, this.center.y, z, false, true));
			}
		}

		for (int index = 0; index < samples.size(); index++) {
			int nextIndex = (index + 1) % samples.size();
			RingSample current = samples.get(index);
			RingSample next = samples.get(nextIndex);
			if (current.valid() && next.valid() && Math.abs(current.y() - next.y()) > 8.0) {
				samples.set(nextIndex, new RingSample(next.x(), next.y(), next.z(), true, true));
			}
		}

		List<RingSample> cached = List.copyOf(samples);
		if (kind == RingKind.PRESSURE) {
			this.pressureGameTime = clientGameTime;
			this.pressureRadius = radius;
			this.pressureSegments = segments;
			this.pressureSamples = cached;
		} else {
			this.dustGameTime = clientGameTime;
			this.dustRadius = radius;
			this.dustSegments = segments;
			this.dustSamples = cached;
		}
		return cached;
	}
}