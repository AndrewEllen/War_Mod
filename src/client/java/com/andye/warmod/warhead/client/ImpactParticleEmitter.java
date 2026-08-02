package com.andye.warmod.warhead.client;

import java.util.Arrays;
import java.util.Collection;
import java.util.SplittableRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

public final class ImpactParticleEmitter {
	private static final double NEAR_DISTANCE = 192.0;
	private static final double MEDIUM_DISTANCE = 640.0;
	private static final double MAX_DISTANCE = 1536.0;
	private static final double NORMAL_PARTICLE_DISTANCE = 32.0;
	private static final int MAX_PARTICLES_PER_CLIENT_TICK = 300;
	private static final int MAX_PARTICLES_PER_IMPACT_TICK = 80;
	private static final int MIN_PARTICLES_PER_IMPACT_TICK = 4;
	private static final int MAX_ESTIMATED_LIVING_PARTICLES = 1200;
	private static final int ESTIMATED_PARTICLE_LIFETIME_TICKS = 30;

	private static final long INITIAL_BURST_SEED = 0x4D5953494E495443L;
	private static final long SECONDARY_BURST_SEED = 0x5345434F4E444152L;
	private static final long CONTINUOUS_BURST_SEED = 0x434F4E54494E5545L;
	private static final long LINGERING_SMOKE_SEED = 0x4C494E474552494EL;

	private final int[] recentEmissionCounts = new int[ESTIMATED_PARTICLE_LIFETIME_TICKS];
	private int recentEmissionTotal;
	private int recentEmissionCursor;
	private int currentTickEmissionTotal;
	private long lastBudgetGameTime = Long.MIN_VALUE;

	public void emit(
		final Minecraft client,
		final Collection<ImpactVisualState> impacts,
		final long gameTime,
		final int particlesAlreadySpawned
	) {
		ClientLevel level = client.level;
		if (level == null || client.player == null || impacts.isEmpty()) {
			return;
		}

		this.advanceBudget(gameTime);
		Vec3 viewerPosition = client.player.position();
		int visibleImpactCount = this.countVisibleImpacts(level, viewerPosition, impacts);
		if (visibleImpactCount == 0) {
			return;
		}

		int perTickBudget = Math.min(
			MAX_PARTICLES_PER_CLIENT_TICK - Math.max(0, particlesAlreadySpawned) - this.currentTickEmissionTotal,
			MAX_ESTIMATED_LIVING_PARTICLES - this.recentEmissionTotal
		);
		if (perTickBudget <= 0) {
			return;
		}

		TickBudget tickBudget = new TickBudget(perTickBudget);
		for (ImpactVisualState state : impacts) {
			Vec3 center = state.impactPosition();
			double distance = viewerPosition.distanceTo(center);
			if (!center.isFinite() || !Double.isFinite(distance) || distance > MAX_DISTANCE || !isLoaded(level, center.x, center.z)) {
				continue;
			}

			ParticleLod lod = lod(distance);
			int fairShare = Math.max(
				MIN_PARTICLES_PER_IMPACT_TICK,
				perTickBudget / Math.max(1, visibleImpactCount)
			);
			int perImpactTickLimit = Math.min(MAX_PARTICLES_PER_IMPACT_TICK, fairShare);
			int lifetimeLimit = lifetimeLimit(lod);
			int remainingLifetime = Math.max(0, lifetimeLimit - state.emittedParticleCount());
			ImpactBudget impactBudget = new ImpactBudget(
				tickBudget,
				state,
				Math.min(perImpactTickLimit, remainingLifetime)
			);
			this.emitImpact(level, state, center, distance, gameTime, lod, impactBudget);
		}

		this.recordEmissions(gameTime, tickBudget.emitted());
	}

	public void clear() {
		Arrays.fill(this.recentEmissionCounts, 0);
		this.recentEmissionTotal = 0;
		this.recentEmissionCursor = 0;
		this.currentTickEmissionTotal = 0;
		this.lastBudgetGameTime = Long.MIN_VALUE;
	}

	private void emitImpact(
		final ClientLevel level,
		final ImpactVisualState state,
		final Vec3 center,
		final double distance,
		final long gameTime,
		final ParticleLod lod,
		final ImpactBudget budget
	) {
		if (budget.hasCapacity() && !state.initialBurstEmitted()) {
			int emitted = this.emitInitialBurst(level, center, state.visualScale(), state.visualSeed(), lod, budget);
			if (emitted > 0) {
				state.markInitialBurstEmitted();
			}
		}

		double age = state.ageTicks(gameTime, 0.0);
		if (budget.hasCapacity() && age >= 8.0 && !state.secondaryBurstEmitted() && lod != ParticleLod.FAR) {
			int emitted = this.emitSecondaryBurst(level, center, state.visualScale(), state.visualSeed(), lod, distance, budget);
			if (emitted > 0) {
				state.markSecondaryBurstEmitted();
			}
		}

		if (budget.hasCapacity() && state.lastContinuousParticleTick() != gameTime) {
			if (age >= 2.0 && age < 14.0 && lod != ParticleLod.FAR) {
				this.emitExpandingFireball(level, center, state.visualScale(), state.visualSeed(), age, lod, distance, gameTime, budget);
			}
			if (age >= 10.0 && age < 45.0 && lod != ParticleLod.FAR) {
				this.emitLingeringSmoke(level, center, state.visualScale(), state.visualSeed(), lod, distance, gameTime, budget);
			}
			state.markContinuousParticleTick(gameTime);
		}
	}

	private int emitInitialBurst(
		final ClientLevel level,
		final Vec3 center,
		final float visualScale,
		final long visualSeed,
		final ParticleLod lod,
		final ImpactBudget budget
	) {
		SplittableRandom random = new SplittableRandom(visualSeed ^ INITIAL_BURST_SEED);
		BurstCounts counts = new BurstCounts();
		counts.emitter = 1;
		if (lod == ParticleLod.FAR) {
			counts.explosion = scaledCount(random, 3, 5, visualScale, 1.0);
			counts.flame = scaledCount(random, 4, 8, visualScale, 1.0);
			counts.largeSmoke = scaledCount(random, 3, 6, visualScale, 1.0);
			counts.cap(24);
		} else {
			double lodMultiplier = lod == ParticleLod.MEDIUM ? 0.5 : 1.0;
			counts.explosion = scaledCount(random, 6, 10, visualScale, lodMultiplier);
			counts.flash = scaledCount(random, 1, 2, visualScale, lodMultiplier);
			counts.flame = scaledCount(random, 12, 20, visualScale, lodMultiplier);
			counts.lava = scaledCount(random, 8, 14, visualScale, lodMultiplier);
			counts.largeSmoke = scaledCount(random, 10, 16, visualScale, lodMultiplier);
			counts.cloud = scaledCount(random, 6, 10, visualScale, lodMultiplier);
			counts.cap(80);
		}

		return this.emitBurst(level, center, counts, random, budget, true, false);
	}

	private int emitSecondaryBurst(
		final ClientLevel level,
		final Vec3 center,
		final float visualScale,
		final long visualSeed,
		final ParticleLod lod,
		final double distance,
		final ImpactBudget budget
	) {
		SplittableRandom random = new SplittableRandom(visualSeed ^ SECONDARY_BURST_SEED);
		double lodMultiplier = lod == ParticleLod.MEDIUM ? 0.5 : 1.0;
		BurstCounts counts = new BurstCounts();
		counts.largeSmoke = scaledCount(random, 8, 14, visualScale, lodMultiplier);
		counts.smoke = scaledCount(random, 8, 12, visualScale, lodMultiplier);
		counts.cloud = scaledCount(random, 6, 10, visualScale, lodMultiplier);
		counts.lava = scaledCount(random, 4, 8, visualScale, lodMultiplier);
		counts.cap(48);
		return this.emitBurst(level, center, counts, random, budget, distance > NORMAL_PARTICLE_DISTANCE, true);
	}

	private void emitExpandingFireball(
		final ClientLevel level,
		final Vec3 center,
		final float visualScale,
		final long visualSeed,
		final double age,
		final ParticleLod lod,
		final double distance,
		final long gameTime,
		final ImpactBudget budget
	) {
		SplittableRandom random = new SplittableRandom(visualSeed ^ CONTINUOUS_BURST_SEED ^ gameTime);
		double t = clamp((age - 2.0) / 12.0, 0.0, 1.0);
		double radius = lerp(2.0, 10.0, easeOut(t)) * clamp(visualScale, 0.05, 8.0);
		double lodMultiplier = lod == ParticleLod.MEDIUM ? 0.5 : 1.0;
		boolean forceLongRange = distance > NORMAL_PARTICLE_DISTANCE;
		Direction direction = new Direction();
		int flameCount = scaledCount(random, 3, 6, visualScale, lodMultiplier);
		int lavaCount = scaledCount(random, 1, 3, visualScale, lodMultiplier);
		int smokeCount = scaledCount(random, 2, 4, visualScale, lodMultiplier);
		int explosionCount = scaledCount(random, 1, 3, visualScale, lodMultiplier);
		emitRadial(level, ParticleTypes.FLAME, center, random, direction, flameCount, radius, radius, 0.10, 0.35, 0.08, 0.35, budget, forceLongRange);
		emitRadial(level, ParticleTypes.LAVA, center, random, direction, lavaCount, radius, radius, 0.08, 0.30, 0.20, 0.65, budget, forceLongRange);
		emitRadial(level, ParticleTypes.SMOKE, center, random, direction, smokeCount, radius, radius, 0.03, 0.16, 0.08, 0.28, budget, forceLongRange);
		emitRadial(level, ParticleTypes.EXPLOSION, center, random, direction, explosionCount, radius, radius, 0.05, 0.20, 0.02, 0.10, budget, forceLongRange);
	}

	private void emitLingeringSmoke(
		final ClientLevel level,
		final Vec3 center,
		final float visualScale,
		final long visualSeed,
		final ParticleLod lod,
		final double distance,
		final long gameTime,
		final ImpactBudget budget
	) {
		if (lod == ParticleLod.MEDIUM && (gameTime & 1L) != 0L) {
			return;
		}

		SplittableRandom random = new SplittableRandom(visualSeed ^ LINGERING_SMOKE_SEED ^ gameTime);
		int count = lod == ParticleLod.NEAR
			? scaledCount(random, 1, 3, visualScale, 1.0)
			: scaledCount(random, 1, 1, visualScale, 0.5);
		Direction direction = new Direction();
		emitRadial(
			level,
			ParticleTypes.SMOKE,
			center,
			random,
			direction,
			count,
			3.0,
			7.0,
			0.01,
			0.08,
			0.04,
			0.16,
			budget,
			distance > NORMAL_PARTICLE_DISTANCE
		);
	}

	private int emitBurst(
		final ClientLevel level,
		final Vec3 center,
		final BurstCounts counts,
		final SplittableRandom random,
		final ImpactBudget budget,
		final boolean forceLongRange,
		final boolean secondary
	) {
		int before = budget.emittedThisPhase();
		Direction direction = new Direction();
		for (int index = 0; index < counts.emitter && budget.hasCapacity(); index++) {
			emitParticle(level, ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 0.0, 0.0, 0.0, budget, true);
		}

		int mandatoryExplosion = Math.min(1, counts.explosion);
		int mandatoryFlame = Math.min(1, counts.flame);
		int mandatorySmoke = Math.min(1, counts.largeSmoke > 0 ? counts.largeSmoke : counts.smoke);
		int mandatoryCloud = secondary ? Math.min(1, counts.cloud) : 0;
		int mandatoryLava = secondary && mandatorySmoke == 0 ? Math.min(1, counts.lava) : 0;
		emitRadial(level, ParticleTypes.EXPLOSION, center, random, direction, mandatoryExplosion, 1.5, 4.0, 0.05, 0.20, 0.00, 0.06, budget, forceLongRange);
		emitRadial(level, ParticleTypes.FLAME, center, random, direction, mandatoryFlame, 2.0, 6.0, 0.10, 0.35, 0.08, 0.35, budget, forceLongRange);
		emitRadial(level, ParticleTypes.LARGE_SMOKE, center, random, direction, mandatorySmoke, 2.0, 7.0, 0.03, 0.16, 0.08, 0.28, budget, forceLongRange);
		emitRadial(level, ParticleTypes.CLOUD, center, random, direction, mandatoryCloud, 3.0, 8.0, 0.10, 0.32, 0.04, 0.18, budget, forceLongRange);
		emitRadial(level, ParticleTypes.LAVA, center, random, direction, mandatoryLava, 2.0, 5.0, 0.08, 0.30, 0.20, 0.65, budget, forceLongRange);

		counts.explosion -= mandatoryExplosion;
		counts.flame -= mandatoryFlame;
		if (counts.largeSmoke > 0) {
			counts.largeSmoke -= mandatorySmoke;
		} else {
			counts.smoke -= mandatorySmoke;
		}
		counts.cloud -= mandatoryCloud;
		counts.lava -= mandatoryLava;

		emitRadial(level, ParticleTypes.EXPLOSION, center, random, direction, counts.explosion, 1.5, 4.0, 0.05, 0.20, 0.00, 0.06, budget, forceLongRange);
		if (counts.flash > 0) {
			ColorParticleOption flash = ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFD080);
			emitRadial(level, flash, center, random, direction, counts.flash, 0.8, 2.4, 0.02, 0.10, 0.00, 0.04, budget, forceLongRange);
		}
		emitRadial(level, ParticleTypes.FLAME, center, random, direction, counts.flame, 2.0, 6.0, 0.10, 0.35, 0.08, 0.35, budget, forceLongRange);
		emitRadial(level, ParticleTypes.LAVA, center, random, direction, counts.lava, 2.0, 5.0, 0.08, 0.30, 0.20, 0.65, budget, forceLongRange);
		emitRadial(level, ParticleTypes.LARGE_SMOKE, center, random, direction, counts.largeSmoke, 2.0, 7.0, 0.03, 0.16, 0.08, 0.28, budget, forceLongRange);
		emitRadial(level, ParticleTypes.SMOKE, center, random, direction, counts.smoke, 2.0, 7.0, 0.03, 0.16, 0.08, 0.28, budget, forceLongRange);
		emitRadial(level, ParticleTypes.CLOUD, center, random, direction, counts.cloud, 3.0, 8.0, 0.10, 0.32, 0.04, 0.18, budget, forceLongRange);
		return budget.emittedThisPhase() - before;
	}

	private static int emitRadial(
		final ClientLevel level,
		final ParticleOptions particle,
		final Vec3 center,
		final SplittableRandom random,
		final Direction direction,
		final int count,
		final double minimumRadius,
		final double maximumRadius,
		final double minimumOutwardVelocity,
		final double maximumOutwardVelocity,
		final double minimumUpwardVelocity,
		final double maximumUpwardVelocity,
		final ImpactBudget budget,
		final boolean forceLongRange
	) {
		int emitted = 0;
		for (int index = 0; index < count && budget.hasCapacity(); index++) {
			randomDirection(random, direction);
			double radius = minimumRadius == maximumRadius ? minimumRadius : random.nextDouble(minimumRadius, maximumRadius);
			double x = center.x + direction.x * radius;
			double y = center.y + direction.y * radius;
			double z = center.z + direction.z * radius;
			double outwardVelocity = random.nextDouble(minimumOutwardVelocity, maximumOutwardVelocity);
			double xVelocity = direction.x * outwardVelocity;
			double yVelocity = direction.y * outwardVelocity + random.nextDouble(minimumUpwardVelocity, maximumUpwardVelocity);
			double zVelocity = direction.z * outwardVelocity;
			if (emitParticle(level, particle, x, y, z, xVelocity, yVelocity, zVelocity, budget, forceLongRange)) {
				emitted++;
			}
		}
		return emitted;
	}

	private static boolean emitParticle(
		final ClientLevel level,
		final ParticleOptions particle,
		final double x,
		final double y,
		final double z,
		final double xVelocity,
		final double yVelocity,
		final double zVelocity,
		final ImpactBudget budget,
		final boolean forceLongRange
	) {
		if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !isLoaded(level, x, z) || !budget.tryConsume()) {
			return false;
		}

		if (forceLongRange) {
			level.addAlwaysVisibleParticle(particle, true, x, y, z, xVelocity, yVelocity, zVelocity);
		} else {
			level.addAlwaysVisibleParticle(particle, false, x, y, z, xVelocity, yVelocity, zVelocity);
		}
		return true;
	}

	private int countVisibleImpacts(
		final ClientLevel level,
		final Vec3 viewerPosition,
		final Collection<ImpactVisualState> impacts
	) {
		int visible = 0;
		for (ImpactVisualState state : impacts) {
			Vec3 center = state.impactPosition();
			double distance = viewerPosition.distanceTo(center);
			if (center.isFinite() && Double.isFinite(distance) && distance <= MAX_DISTANCE && isLoaded(level, center.x, center.z)) {
				visible++;
			}
		}
		return visible;
	}

	private void advanceBudget(final long gameTime) {
		if (this.lastBudgetGameTime == Long.MIN_VALUE || gameTime < this.lastBudgetGameTime || gameTime - this.lastBudgetGameTime >= ESTIMATED_PARTICLE_LIFETIME_TICKS) {
			Arrays.fill(this.recentEmissionCounts, 0);
			this.recentEmissionTotal = 0;
			this.recentEmissionCursor = 0;
			this.currentTickEmissionTotal = 0;
			this.lastBudgetGameTime = gameTime;
			return;
		}

		long elapsedTicks = gameTime - this.lastBudgetGameTime;
		for (long tick = 0; tick < elapsedTicks; tick++) {
			this.recentEmissionCursor = (this.recentEmissionCursor + 1) % this.recentEmissionCounts.length;
			this.recentEmissionTotal -= this.recentEmissionCounts[this.recentEmissionCursor];
			this.recentEmissionCounts[this.recentEmissionCursor] = 0;
		}
		this.currentTickEmissionTotal = 0;
		this.lastBudgetGameTime = gameTime;
	}

	private void recordEmissions(final long gameTime, final int emitted) {
		if (emitted <= 0 || this.lastBudgetGameTime != gameTime) {
			return;
		}
		this.recentEmissionCounts[this.recentEmissionCursor] += emitted;
		this.recentEmissionTotal += emitted;
		this.currentTickEmissionTotal += emitted;
	}

	private static boolean isLoaded(final ClientLevel level, final double x, final double z) {
		if (!Double.isFinite(x) || !Double.isFinite(z)) {
			return false;
		}
		int chunkX = SectionPos.blockToSectionCoord(Mth.floor(x));
		int chunkZ = SectionPos.blockToSectionCoord(Mth.floor(z));
		return level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) != null;
	}

	private static ParticleLod lod(final double distance) {
		if (distance < NEAR_DISTANCE) {
			return ParticleLod.NEAR;
		}
		if (distance < MEDIUM_DISTANCE) {
			return ParticleLod.MEDIUM;
		}
		return ParticleLod.FAR;
	}

	private static int lifetimeLimit(final ParticleLod lod) {
		return switch (lod) {
			case NEAR -> 180;
			case MEDIUM -> 90;
			case FAR -> 24;
		};
	}

	private static int scaledCount(
		final SplittableRandom random,
		final int minimum,
		final int maximum,
		final float visualScale,
		final double lodMultiplier
	) {
		int baseCount = random.nextInt(minimum, maximum + 1);
		int count = (int) Math.round(baseCount * clamp(visualScale, 0.05, 8.0) * lodMultiplier);
		return Math.max(1, count);
	}

	private static void randomDirection(final SplittableRandom random, final Direction direction) {
		double x;
		double y;
		double z;
		double lengthSquared;
		do {
			x = random.nextDouble(-1.0, 1.0);
			y = random.nextDouble(-1.0, 1.0);
			z = random.nextDouble(-1.0, 1.0);
			if (y < 0.0) {
				y *= 0.2;
			}
			lengthSquared = x * x + y * y + z * z;
		} while (lengthSquared < 1.0E-6);
		double length = Math.sqrt(lengthSquared);
		direction.x = x / length;
		direction.y = y / length;
		direction.z = z / length;
	}

	private static double easeOut(final double value) {
		return 1.0 - (1.0 - value) * (1.0 - value);
	}

	private static double lerp(final double start, final double end, final double t) {
		return start + (end - start) * t;
	}

	private static double clamp(final double value, final double minimum, final double maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private enum ParticleLod {
		NEAR,
		MEDIUM,
		FAR
	}

	private static final class Direction {
		private double x;
		private double y;
		private double z;
	}

	private static final class BurstCounts {
		private int emitter;
		private int explosion;
		private int flash;
		private int flame;
		private int lava;
		private int largeSmoke;
		private int smoke;
		private int cloud;

		private int total() {
			return this.emitter + this.explosion + this.flash + this.flame + this.lava + this.largeSmoke + this.smoke + this.cloud;
		}

		private void cap(final int maximum) {
			int total = this.total();
			if (total <= maximum) {
				return;
			}

			int fixed = Math.min(this.emitter, maximum);
			int remainingMaximum = Math.max(0, maximum - fixed);
			int reducible = Math.max(1, total - this.emitter);
			double factor = remainingMaximum / (double) reducible;
			this.explosion = scaledDown(this.explosion, factor);
			this.flash = scaledDown(this.flash, factor);
			this.flame = scaledDown(this.flame, factor);
			this.lava = scaledDown(this.lava, factor);
			this.largeSmoke = scaledDown(this.largeSmoke, factor);
			this.smoke = scaledDown(this.smoke, factor);
			this.cloud = scaledDown(this.cloud, factor);
			while (this.total() > maximum) {
				if (this.cloud > 0) {
					this.cloud--;
				} else if (this.largeSmoke > 0) {
					this.largeSmoke--;
				} else if (this.smoke > 0) {
					this.smoke--;
				} else if (this.lava > 0) {
					this.lava--;
				} else if (this.flame > 0) {
					this.flame--;
				} else if (this.explosion > 0) {
					this.explosion--;
				} else {
					break;
				}
			}
		}

		private static int scaledDown(final int count, final double factor) {
			if (count <= 0) {
				return 0;
			}
			return Math.max(1, (int) Math.floor(count * factor));
		}
	}

	private static final class TickBudget {
		private int remaining;
		private int emitted;

		private TickBudget(final int maximum) {
			this.remaining = Math.max(0, maximum);
		}

		private boolean tryConsume() {
			if (this.remaining <= 0) {
				return false;
			}
			this.remaining--;
			this.emitted++;
			return true;
		}

		private int emitted() {
			return this.emitted;
		}
	}

	private static final class ImpactBudget {
		private final TickBudget tickBudget;
		private final ImpactVisualState state;
		private int remaining;
		private int emitted;

		private ImpactBudget(final TickBudget tickBudget, final ImpactVisualState state, final int maximum) {
			this.tickBudget = tickBudget;
			this.state = state;
			this.remaining = Math.max(0, maximum);
		}

		private boolean hasCapacity() {
			return this.remaining > 0;
		}

		private boolean tryConsume() {
			if (this.remaining <= 0 || !this.tickBudget.tryConsume()) {
				return false;
			}
			this.remaining--;
			this.emitted++;
			this.state.recordParticleEmission();
			return true;
		}

		private int emittedThisPhase() {
			return this.emitted;
		}
	}
}