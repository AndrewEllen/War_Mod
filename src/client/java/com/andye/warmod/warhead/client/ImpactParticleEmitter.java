package com.andye.warmod.warhead.client;

import com.andye.warmod.particle.ModParticleTypes;
import com.andye.warmod.warhead.WarheadVisualMath;
import com.andye.warmod.warhead.client.render.BlastCloudLobe;
import com.andye.warmod.warhead.client.render.BlastCloudRenderer;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
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
	private static final int MAX_PARTICLES_PER_CLIENT_TICK = 100_000;
	private static final int MAX_PARTICLES_PER_IMPACT_TICK = 50_000;
	private static final int MIN_PARTICLES_PER_IMPACT_TICK = 5_000;
	private static final int MAX_ESTIMATED_LIVING_PARTICLES = 2_000_000;
	private static final int ESTIMATED_PARTICLE_LIFETIME_TICKS = 200;

	private static final long INITIAL_SEED = 0x4D5953494E495443L;
	private static final long CONTINUOUS_SEED = 0x434F4E54494E5545L;
	private static final long SMOKE_SEED = 0x4C494E474552494EL;
	private final int[] recentEmissionCounts = new int[ESTIMATED_PARTICLE_LIFETIME_TICKS];
	private int recentEmissionTotal;
	private int recentEmissionCursor;
	private int currentTickEmissionTotal;
	private long lastBudgetGameTime = Long.MIN_VALUE;

	public void emit(final Minecraft client, final Collection<ImpactVisualState> impacts, final long gameTime, final int particlesAlreadySpawned) {
		ClientLevel level = client.level;
		if (level == null || client.player == null || impacts.isEmpty()) return;
		this.advanceBudget(gameTime);
		Vec3 viewer = client.player.position();
		int visibleCount = this.countVisibleImpacts(level, viewer, impacts);
		if (visibleCount == 0) return;
		int available = Math.min(MAX_PARTICLES_PER_CLIENT_TICK - Math.max(0, particlesAlreadySpawned) - this.currentTickEmissionTotal,
			MAX_ESTIMATED_LIVING_PARTICLES - this.recentEmissionTotal);
		if (available <= 0) return;
		TickBudget tickBudget = new TickBudget(available);
		for (ImpactVisualState state : impacts) {
			Vec3 center = state.impactPosition();
			double distance = viewer.distanceTo(center);
			if (!center.isFinite() || !Double.isFinite(distance) || distance > MAX_DISTANCE || !isLoaded(level, center.x, center.z)) continue;
			ParticleLod lod = lod(distance);
			int fairShare = Math.max(MIN_PARTICLES_PER_IMPACT_TICK, available / Math.max(1, visibleCount));
			int requested = Math.min(MAX_PARTICLES_PER_IMPACT_TICK, Math.min(fairShare, lodMaximum(lod)));
			ImpactBudget budget = new ImpactBudget(tickBudget, state, requested, lod);
			this.emitImpact(level, state, center, distance, gameTime, lod, budget);
		}
		this.recordEmissions(gameTime, tickBudget.emitted());
	}

	public void clear() {
		Arrays.fill(this.recentEmissionCounts, 0);
		this.recentEmissionTotal = 0; this.recentEmissionCursor = 0; this.currentTickEmissionTotal = 0; this.lastBudgetGameTime = Long.MIN_VALUE;
	}

	private void emitImpact(final ClientLevel level, final ImpactVisualState state, final Vec3 center, final double distance,
		final long gameTime, final ParticleLod lod, final ImpactBudget budget) {
		double age = state.ageTicks(gameTime, 0.0);
		if (age < WarheadVisualMath.AIR_SHOCKWAVE_DURATION_TICKS) this.emitGroundShockfront(level, state, center, distance, age, gameTime, lod, budget);
		if (!state.initialBurstEmitted()) {
			int emitted = this.emitInitialBurst(level, center, state.visualScale(), state.visualSeed(), lod, distance, budget);
			if (emitted > 0) state.markInitialBurstEmitted();
		}
		if (state.lastContinuousParticleTick() != gameTime) {
			if (age >= 1.0 && age < 40.0) this.emitExpandingFireball(level, center, state.visualScale(), state.visualSeed(), age, lod, distance, gameTime, budget);
			if (age >= 20.0 && age < 260.0) this.emitLingeringSmoke(level, state, age, lod, distance, gameTime, budget);
			state.markContinuousParticleTick(gameTime);
		}
	}

	private int emitInitialBurst(final ClientLevel level, final Vec3 center, final float scale, final long seed,
		final ParticleLod lod, final double distance, final ImpactBudget budget) {
		SplittableRandom random = new SplittableRandom(seed ^ INITIAL_SEED);
		double multiplier = lod == ParticleLod.NEAR ? 1.0 : lod == ParticleLod.MEDIUM ? 0.5 : 0.25;
		int before = budget.emitted(Category.FIREBALL);
		boolean force = distance > NORMAL_PARTICLE_DISTANCE;
		emitParticle(level, ParticleTypes.EXPLOSION_EMITTER, center, Vec3.ZERO, budget, Category.FIREBALL, true);
		Direction direction = new Direction();
		emitRadial(level, ModParticleTypes.WARHEAD_FIREBALL, center, random, direction, scaled(random, 3_500, 5_000, scale, multiplier), 1.0, 10.0, 0.06, 0.24, 0.12, 0.48, budget, Category.FIREBALL, force);
		emitRadial(level, ParticleTypes.EXPLOSION, center, random, direction, scaled(random, 1_000, 2_000, scale, multiplier), 1.0, 8.0, 0.04, 0.20, 0.02, 0.14, budget, Category.FIREBALL, force);
		emitRadial(level, ParticleTypes.FLAME, center, random, direction, scaled(random, 3_500, 5_000, scale, multiplier), 2.0, 12.0, 0.10, 0.38, 0.08, 0.42, budget, Category.FIREBALL, force);
		emitRadial(level, ParticleTypes.LAVA, center, random, direction, scaled(random, 1_500, 2_500, scale, multiplier), 1.5, 9.0, 0.10, 0.34, 0.22, 0.72, budget, Category.FIREBALL, force);
		ColorParticleOption flash = ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFD080);
		emitRadial(level, flash, center, random, direction, Math.max(1, (int) Math.ceil(multiplier)), 0.6, 2.0, 0.01, 0.05, 0.0, 0.03, budget, Category.FIREBALL, force);
		return budget.emitted(Category.FIREBALL) - before;
	}

	private void emitExpandingFireball(final ClientLevel level, final Vec3 center, final float scale, final long seed,
		final double age, final ParticleLod lod, final double distance, final long gameTime, final ImpactBudget budget) {
		SplittableRandom random = new SplittableRandom(seed ^ CONTINUOUS_SEED ^ gameTime);
		double multiplier = lod == ParticleLod.NEAR ? 1.0 : lod == ParticleLod.MEDIUM ? 0.5 : 0.25;
		double radius = (4.0 + Math.min(1.0, age / 24.0) * 16.0) * Mth.clamp(scale, 0.5F, 1.5F);
		boolean force = distance > NORMAL_PARTICLE_DISTANCE;
		Direction direction = new Direction();
		emitRadial(level, ModParticleTypes.WARHEAD_FIREBALL, center, random, direction, scaled(random, 8, 16, scale, multiplier), 1.0, radius, 0.05, 0.18, 0.10, 0.38, budget, Category.FIREBALL, force);
		emitRadial(level, ParticleTypes.FLAME, center, random, direction, scaled(random, 5, 12, scale, multiplier), 2.0, radius, 0.08, 0.30, 0.08, 0.36, budget, Category.FIREBALL, force);
		emitRadial(level, ParticleTypes.LAVA, center, random, direction, scaled(random, 2, 6, scale, multiplier), 1.5, radius * 0.75, 0.08, 0.28, 0.18, 0.58, budget, Category.FIREBALL, force);
		emitRadial(level, ParticleTypes.EXPLOSION, center, random, direction, scaled(random, 2, 6, scale, multiplier), 1.0, radius, 0.03, 0.14, 0.02, 0.10, budget, Category.FIREBALL, force);
	}

	private void emitGroundShockfront(final ClientLevel level, final ImpactVisualState state, final Vec3 center,
		final double distance, final double age, final long gameTime, final ParticleLod lod, final ImpactBudget budget) {
		int spokes = lod == ParticleLod.NEAR ? 256 : lod == ParticleLod.MEDIUM ? 160 : 96;
		int maximum = budget.remaining(Category.GROUND);
		for (GroundShockParticleEmitter.GroundParticleBatch batch : GroundShockParticleEmitter.collect(state, center,
			WarheadVisualMath.groundShockwaveDistance(age, state.visualScale()), spokes, maximum, distance, gameTime)) {
			int emitted = 0;
			for (GroundShockParticleEmitter.GroundParticle particle : batch.particles()) {
				if (emitParticle(level, particle.particle(), particle.position(), particle.velocity(), budget, Category.GROUND, particle.forceLongRange())) emitted++;
			}
			if (emitted > 0) state.terrainShockfrontField().markEmitted(batch.node(), gameTime);
			if (!budget.hasCapacity(Category.GROUND)) break;
		}
	}

	private void emitLingeringSmoke(final ClientLevel level, final ImpactVisualState state, final double age,
		final ParticleLod lod, final double distance, final long gameTime, final ImpactBudget budget) {
		SplittableRandom random = new SplittableRandom(state.visualSeed() ^ SMOKE_SEED ^ gameTime);
		int count = lod == ParticleLod.NEAR ? random.nextInt(2_500, 5_001) : lod == ParticleLod.MEDIUM ? random.nextInt(1_500, 4_001) : random.nextInt(500, 1_001);
		boolean force = distance > NORMAL_PARTICLE_DISTANCE;
		for (int index = 0; index < count && budget.hasCapacity(Category.SMOKE); index++) {
			BlastCloudLobe lobe = state.blastCloudLobes().get(random.nextInt(state.blastCloudLobes().size()));
			Vec3 offset = BlastCloudRenderer.center(lobe, age, Mth.clamp(state.visualScale(), 0.55F, 1.45F));
			Vec3 horizontal = new Vec3(offset.x, 0.0, offset.z);
			if (horizontal.lengthSqr() > 1.0E-5) horizontal = horizontal.normalize();
			Vec3 position = state.impactPosition().add(offset).add(random.nextDouble(-2.0, 2.0), random.nextDouble(-1.0, 2.0), random.nextDouble(-2.0, 2.0));
			Vec3 velocity = horizontal.scale(random.nextDouble(0.008, 0.045)).add(random.nextDouble(-0.012, 0.012), random.nextDouble(0.035, 0.095), random.nextDouble(-0.012, 0.012));
			emitParticle(level, ModParticleTypes.WARHEAD_SMOKE, position, velocity, budget, Category.SMOKE, force);
		}
	}

	private static int emitRadial(final ClientLevel level, final ParticleOptions particle, final Vec3 center,
		final SplittableRandom random, final Direction direction, final int count, final double minimumRadius,
		final double maximumRadius, final double minimumOutward, final double maximumOutward,
		final double minimumUpward, final double maximumUpward, final ImpactBudget budget,
		final Category category, final boolean forceLongRange) {
		int emitted = 0;
		for (int index = 0; index < count && budget.hasCapacity(category); index++) {
			randomDirection(random, direction);
			double radius = random.nextDouble(minimumRadius, Math.max(minimumRadius + 1.0E-4, maximumRadius));
			Vec3 position = center.add(direction.x * radius, direction.y * radius, direction.z * radius);
			double speed = random.nextDouble(minimumOutward, maximumOutward);
			Vec3 velocity = new Vec3(direction.x * speed, direction.y * speed + random.nextDouble(minimumUpward, maximumUpward), direction.z * speed);
			if (emitParticle(level, particle, position, velocity, budget, category, forceLongRange)) emitted++;
		}
		return emitted;
	}

	private static boolean emitParticle(final ClientLevel level, final ParticleOptions particle, final Vec3 position,
		final Vec3 velocity, final ImpactBudget budget, final Category category, final boolean forceLongRange) {
		if (!position.isFinite() || !velocity.isFinite() || !isLoaded(level, position.x, position.z) || !budget.tryConsume(category)) return false;
		level.addAlwaysVisibleParticle(particle, forceLongRange, position.x, position.y, position.z, velocity.x, velocity.y, velocity.z);
		return true;
	}

	private int countVisibleImpacts(final ClientLevel level, final Vec3 viewer, final Collection<ImpactVisualState> impacts) {
		int visible = 0;
		for (ImpactVisualState state : impacts) {
			double distance = viewer.distanceTo(state.impactPosition());
			if (state.impactPosition().isFinite() && Double.isFinite(distance) && distance <= MAX_DISTANCE && isLoaded(level, state.impactPosition().x, state.impactPosition().z)) visible++;
		}
		return visible;
	}

	private void advanceBudget(final long gameTime) {
		if (this.lastBudgetGameTime == Long.MIN_VALUE || gameTime < this.lastBudgetGameTime || gameTime - this.lastBudgetGameTime >= ESTIMATED_PARTICLE_LIFETIME_TICKS) {
			Arrays.fill(this.recentEmissionCounts, 0); this.recentEmissionTotal = 0; this.recentEmissionCursor = 0; this.currentTickEmissionTotal = 0; this.lastBudgetGameTime = gameTime; return;
		}
		long elapsed = gameTime - this.lastBudgetGameTime;
		for (long tick = 0; tick < elapsed; tick++) {
			this.recentEmissionCursor = (this.recentEmissionCursor + 1) % this.recentEmissionCounts.length;
			this.recentEmissionTotal -= this.recentEmissionCounts[this.recentEmissionCursor]; this.recentEmissionCounts[this.recentEmissionCursor] = 0;
		}
		this.currentTickEmissionTotal = 0; this.lastBudgetGameTime = gameTime;
	}

	private void recordEmissions(final long gameTime, final int emitted) {
		if (emitted <= 0 || this.lastBudgetGameTime != gameTime) return;
		this.recentEmissionCounts[this.recentEmissionCursor] += emitted; this.recentEmissionTotal += emitted; this.currentTickEmissionTotal += emitted;
	}

	private static boolean isLoaded(final ClientLevel level, final double x, final double z) {
		if (!Double.isFinite(x) || !Double.isFinite(z)) return false;
		return level.getChunkSource().getChunk(SectionPos.blockToSectionCoord(Mth.floor(x)), SectionPos.blockToSectionCoord(Mth.floor(z)), ChunkStatus.FULL, false) != null;
	}

	private static ParticleLod lod(final double distance) { return distance < NEAR_DISTANCE ? ParticleLod.NEAR : distance < MEDIUM_DISTANCE ? ParticleLod.MEDIUM : ParticleLod.FAR; }
	private static int lodMaximum(final ParticleLod lod) { return lod == ParticleLod.NEAR ? 50_000 : lod == ParticleLod.MEDIUM ? 27_000 : 8_000; }
	private static int scaled(final SplittableRandom random, final int minimum, final int maximum, final float scale, final double multiplier) {
		return Math.max(1, (int) Math.round(random.nextInt(minimum, maximum + 1) * Mth.clamp(scale, 0.5F, 1.5F) * multiplier));
	}
	private static void randomDirection(final SplittableRandom random, final Direction direction) {
		double x, y, z, lengthSquared;
		do { x = random.nextDouble(-1.0, 1.0); y = random.nextDouble(-0.2, 1.0); z = random.nextDouble(-1.0, 1.0); lengthSquared = x*x+y*y+z*z; } while (lengthSquared < 1.0E-6);
		double length = Math.sqrt(lengthSquared); direction.x=x/length; direction.y=y/length; direction.z=z/length;
	}

	private enum ParticleLod { NEAR, MEDIUM, FAR }
	private enum Category { FIREBALL, GROUND, SMOKE }
	private static final class Direction { double x, y, z; }
	private static final class TickBudget {
		private int remaining; private int emitted;
		TickBudget(final int maximum) { this.remaining = Math.max(0, maximum); }
		boolean tryConsume() { if (this.remaining <= 0) return false; this.remaining--; this.emitted++; return true; }
		int emitted() { return this.emitted; }
	}
	private static final class ImpactBudget {
		private final TickBudget tickBudget; private final ImpactVisualState state;
		private final EnumMap<Category, Integer> remaining = new EnumMap<>(Category.class);
		private final EnumMap<Category, Integer> emitted = new EnumMap<>(Category.class);
		private int shared;
		ImpactBudget(final TickBudget tickBudget, final ImpactVisualState state, final int maximum, final ParticleLod lod) {
			this.tickBudget=tickBudget; this.state=state;
			int fire = lod == ParticleLod.NEAR ? 15_000 : lod == ParticleLod.MEDIUM ? 8_000 : 2_000;
			int ground = lod == ParticleLod.NEAR ? 30_000 : lod == ParticleLod.MEDIUM ? 15_000 : 5_000;
			int smoke = lod == ParticleLod.NEAR ? 5_000 : lod == ParticleLod.MEDIUM ? 4_000 : 1_000;
			int total = fire + ground + smoke;
			double factor = Math.min(1.0, maximum / (double) total);
			this.remaining.put(Category.FIREBALL, Math.max(1, (int) Math.floor(fire * factor)));
			this.remaining.put(Category.GROUND, Math.max(1, (int) Math.floor(ground * factor)));
			this.remaining.put(Category.SMOKE, Math.max(1, (int) Math.floor(smoke * factor)));
			this.shared = Math.max(0, maximum - this.remaining.values().stream().mapToInt(Integer::intValue).sum());
			for (Category category : Category.values()) this.emitted.put(category, 0);
		}
		boolean hasCapacity(final Category category) { return this.remaining(category) > 0 && this.tickBudget.remaining > 0; }
		int remaining(final Category category) { return this.remaining.get(category) + this.shared; }
		boolean tryConsume(final Category category) {
			if (!this.tickBudget.tryConsume()) return false;
			int reserved = this.remaining.get(category);
			if (reserved > 0) this.remaining.put(category, reserved - 1);
			else if (this.shared > 0) this.shared--;
			else { this.tickBudget.remaining++; this.tickBudget.emitted--; return false; }
			this.emitted.put(category, this.emitted.get(category) + 1); this.state.recordParticleEmission(); return true;
		}
		int emitted(final Category category) { return this.emitted.get(category); }
	}
}