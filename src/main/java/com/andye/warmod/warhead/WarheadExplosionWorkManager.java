package com.andye.warmod.warhead;

import com.andye.warmod.entity.IncomingWarheadEntity;
import com.andye.warmod.testtool.WarheadExplosionDropContext;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.EntityBasedExplosionDamageCalculator;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Incrementally prepares and applies War Mod's large explosions.
 *
 * <p>The vanilla explosion implementation calculates every destructive ray and
 * mutates every affected block synchronously inside the impact tick. Nuclear
 * warheads therefore stop both the integrated server and client. This manager
 * performs the same boundary-ray style calculation in bounded server-thread
 * slices while the terminal warhead is still descending. At impact, entity
 * damage and knockback remain immediate, while block callbacks are applied from
 * the prepared result using a global per-tick budget.</p>
 *
 * <p>No mutable world state is read off-thread. Every warhead retains its own
 * destructive plan instead of clustered impacts being collapsed into one
 * gameplay event.</p>
 */
public final class WarheadExplosionWorkManager {
	private static final int MAX_CALCULATION_STEPS_PER_LEVEL_TICK = 80_000;
	private static final int MAX_BLOCK_CALLBACKS_PER_LEVEL_TICK = 6_000;
	private static final int MAX_DEBRIS_CANDIDATES = 1_024;
	private static final long PREPARED_WORK_EXPIRY_TICKS = 240L;
	private static final long FINISHED_WORK_EXPIRY_TICKS = 40L;
	private static final double STEP_SIZE = 0.3;
	private static final float STEP_POWER_LOSS = 0.22500001F;
	private static final double[] DIRECTION_X;
	private static final double[] DIRECTION_Y;
	private static final double[] DIRECTION_Z;
	private static final Map<ServerLevel, LevelWork> LEVELS = new WeakHashMap<>();
	private static boolean registered;

	static {
		ArrayList<double[]> directions = new ArrayList<>(1_352);
		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					if (x != 0 && x != 15 && y != 0 && y != 15 && z != 0 && z != 15) continue;
					double dx = x / 15.0 * 2.0 - 1.0;
					double dy = y / 15.0 * 2.0 - 1.0;
					double dz = z / 15.0 * 2.0 - 1.0;
					double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
					directions.add(new double[] { dx / length, dy / length, dz / length });
				}
			}
		}
		DIRECTION_X = new double[directions.size()];
		DIRECTION_Y = new double[directions.size()];
		DIRECTION_Z = new double[directions.size()];
		for (int index = 0; index < directions.size(); index++) {
			double[] direction = directions.get(index);
			DIRECTION_X[index] = direction[0];
			DIRECTION_Y[index] = direction[1];
			DIRECTION_Z[index] = direction[2];
		}
	}

	private WarheadExplosionWorkManager() {
	}

	public static synchronized void registerLifecycle() {
		if (registered) return;
		ServerTickEvents.END_LEVEL_TICK.register(WarheadExplosionWorkManager::tickLevel);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
		registered = true;
	}

	/**
	 * Executes the immediate entity portion and schedules the prepared block
	 * result. The returned list is a bounded deterministic debris sample, not a
	 * full copy of every affected block.
	 */
	public static synchronized List<WarheadExplosionDropContext.DestroyedBlock> detonate(
		final ServerLevel level,
		final @Nullable ServerPlayer source,
		final UUID warheadId,
		final Vec3 position,
		final float strength,
		final long seed
	) {
		if (level == null || warheadId == null || position == null) throw new NullPointerException();
		if (!position.isFinite() || !Float.isFinite(strength) || strength <= 0.0F) {
			throw new IllegalArgumentException("Invalid staged explosion arguments");
		}

		LevelWork levelWork = LEVELS.computeIfAbsent(level, ignored -> new LevelWork());
		ExplosionWork work = levelWork.byWarhead.get(warheadId);
		double validationRadius = strength >= 100.0F ? 48.0 : 12.0;
		if (work == null || work.detonated
			|| work.center.distanceToSqr(position) > validationRadius * validationRadius
			|| Math.abs(work.strength - strength) > 0.001F) {
			work = new ExplosionWork(position, strength, seed, level.getGameTime());
			levelWork.works.add(work);
			levelWork.byWarhead.put(warheadId, work);
		}
		work.lastTouched = level.getGameTime();

		applyImmediateEntityEffects(level, source, position, strength);

		if (work.detonated) {
			return List.of();
		}
		work.detonated = true;
		work.detonationCenter = position;
		work.explosionContext = new FastExplosion(level, source, position, strength);
		return work.debrisSample(level);
	}

	private static synchronized void tickLevel(final ServerLevel level) {
		LevelWork levelWork = LEVELS.computeIfAbsent(level, ignored -> new LevelWork());
		long gameTime = level.getGameTime();
		prepareActiveWarheads(level, levelWork, gameTime);
		for (ExplosionWork work : levelWork.works) {
			if (work.detonated && !work.applied) work.lastTouched = gameTime;
		}
		cleanup(level, levelWork, gameTime);

		int remainingSteps = MAX_CALCULATION_STEPS_PER_LEVEL_TICK;
		List<ExplosionWork> calculationOrder = new ArrayList<>(levelWork.works);
		calculationOrder.sort(Comparator.comparing((ExplosionWork work) -> !work.detonated));
		while (remainingSteps > 0 && !calculationOrder.isEmpty()) {
			boolean progressed = false;
			int fairShare = Math.max(1_024, remainingSteps / calculationOrder.size());
			for (ExplosionWork work : calculationOrder) {
				if (remainingSteps <= 0) break;
				if (work.calculationComplete) continue;
				int used = work.advance(level, Math.min(fairShare, remainingSteps));
				remainingSteps -= used;
				progressed |= used > 0;
			}
			calculationOrder.removeIf(work -> work.calculationComplete);
			if (!progressed) break;
		}

		int remainingBlocks = MAX_BLOCK_CALLBACKS_PER_LEVEL_TICK;
		for (ExplosionWork work : levelWork.works) {
			if (remainingBlocks <= 0) break;
			if (!work.detonated || !work.calculationComplete || work.applied) continue;
			remainingBlocks -= work.apply(level, remainingBlocks);
		}

		if (levelWork.works.isEmpty()) LEVELS.remove(level);
	}

	private static void prepareActiveWarheads(final ServerLevel level, final LevelWork levelWork, final long gameTime) {
		for (IncomingWarheadEntity warhead : IncomingWarheadRegistry.activeWarheads(level)) {
			UUID id = warhead.warheadId();
			Vec3 target = warhead.intendedTarget();
			if (id == null || target == null || !target.isFinite()) continue;
			float strength = WarheadImpactProfiles.get(warhead.payloadType()).explosionStrength();
			ExplosionWork existing = levelWork.byWarhead.get(id);
			if (existing == null) {
				existing = new ExplosionWork(target, strength, warhead.visualSeed(), gameTime);
				levelWork.works.add(existing);
				levelWork.byWarhead.put(id, existing);
			}
			existing.lastTouched = gameTime;
		}
	}

	private static void cleanup(final ServerLevel level, final LevelWork levelWork, final long gameTime) {
		Iterator<ExplosionWork> iterator = levelWork.works.iterator();
		while (iterator.hasNext()) {
			ExplosionWork work = iterator.next();
			long expiry = work.applied ? FINISHED_WORK_EXPIRY_TICKS : PREPARED_WORK_EXPIRY_TICKS;
			if (gameTime - work.lastTouched <= expiry) continue;
			iterator.remove();
			levelWork.byWarhead.values().removeIf(value -> value == work);
		}
	}

	private static synchronized void clear() {
		LEVELS.clear();
	}

	private static void applyImmediateEntityEffects(
		final ServerLevel level,
		final @Nullable Entity source,
		final Vec3 center,
		final float radius
	) {
		FastExplosion explosion = new FastExplosion(level, source, center, radius);
		level.gameEvent(source, GameEvent.EXPLODE, center);
		float doubleRadius = radius * 2.0F;
		if (doubleRadius < 1.0E-5F) return;

		int minX = Mth.floor(center.x - doubleRadius - 1.0);
		int maxX = Mth.floor(center.x + doubleRadius + 1.0);
		int minY = Mth.floor(center.y - doubleRadius - 1.0);
		int maxY = Mth.floor(center.y + doubleRadius + 1.0);
		int minZ = Mth.floor(center.z - doubleRadius - 1.0);
		int maxZ = Mth.floor(center.z + doubleRadius + 1.0);
		ExplosionDamageCalculator calculator = source == null
			? new ExplosionDamageCalculator()
			: new EntityBasedExplosionDamageCalculator(source);

		for (Entity entity : level.getEntities(source, new AABB(minX, minY, minZ, maxX, maxY, maxZ))) {
			if (entity.ignoreExplosion(explosion)) continue;
			double normalizedDistance = Math.sqrt(entity.distanceToSqr(center)) / doubleRadius;
			if (normalizedDistance > 1.0) continue;

			Vec3 entityOrigin = entity instanceof PrimedTnt ? entity.position() : entity.getEyePosition();
			Vec3 direction = entityOrigin.subtract(center).normalize();
			boolean damage = calculator.shouldDamageEntity(explosion, entity);
			float knockbackMultiplier = calculator.getKnockbackMultiplier(entity);
			float exposure = !damage && knockbackMultiplier == 0.0F
				? 0.0F
				: ServerExplosion.getSeenPercent(center, entity);
			if (damage) {
				entity.hurtServer(level, explosion.getDamageSource(),
					calculator.getEntityDamageAmount(explosion, entity, exposure));
			}

			double resistance = entity instanceof LivingEntity living
				? living.getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE)
				: 0.0;
			double power = (1.0 - normalizedDistance) * exposure * knockbackMultiplier * (1.0 - resistance);
			entity.push(direction.scale(power));
			if (entity.is(EntityTypeTags.REDIRECTABLE_PROJECTILE) && entity instanceof Projectile projectile) {
				projectile.setOwner(explosion.getDamageSource().getEntity());
			}
			entity.onExplosionHit(source);
		}
	}

	private static long mix(long value) {
		value ^= value >>> 30;
		value *= 0xBF58476D1CE4E5B9L;
		value ^= value >>> 27;
		value *= 0x94D049BB133111EBL;
		return value ^ value >>> 31;
	}

	private static double unit(final long value) {
		return (value >>> 11) * 0x1.0p-53;
	}

	private static final class LevelWork {
		private final List<ExplosionWork> works = new ArrayList<>();
		private final Map<UUID, ExplosionWork> byWarhead = new HashMap<>();
	}

	private static final class ExplosionWork {
		private final Vec3 center;
		private final float strength;
		private final long seed;
		private final LongOpenHashSet affectedBlocks = new LongOpenHashSet();
		private final PriorityQueue<RankedPosition> debrisCandidates = new PriorityQueue<>(
			MAX_DEBRIS_CANDIDATES + 1,
			Comparator.comparingLong(RankedPosition::rank).reversed()
		);
		private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		private long lastTouched;
		private int rayIndex;
		private boolean rayActive;
		private double rayX;
		private double rayY;
		private double rayZ;
		private float rayPower;
		private boolean calculationComplete;
		private boolean detonated;
		private boolean applied;
		private Vec3 detonationCenter;
		private FastExplosion explosionContext;
		private LongIterator applyIterator;

		private ExplosionWork(final Vec3 center, final float strength, final long seed, final long gameTime) {
			this.center = center;
			this.strength = strength;
			this.seed = seed;
			this.lastTouched = gameTime;
		}

		private int advance(final ServerLevel level, final int budget) {
			int used = 0;
			while (used < budget && !this.calculationComplete) {
				if (!this.rayActive) {
					if (this.rayIndex >= DIRECTION_X.length) {
						this.calculationComplete = true;
						break;
					}
					this.rayX = this.center.x;
					this.rayY = this.center.y;
					this.rayZ = this.center.z;
					double random = unit(mix(this.seed ^ (long) this.rayIndex * 0x9E3779B97F4A7C15L));
					this.rayPower = this.strength * (float) (0.7 + random * 0.6);
					this.rayActive = true;
				}

				int blockX = Mth.floor(this.rayX);
				int blockY = Mth.floor(this.rayY);
				int blockZ = Mth.floor(this.rayZ);
				this.cursor.set(blockX, blockY, blockZ);
				if (!level.isInWorldBounds(this.cursor)) {
					finishRay();
					continue;
				}
				if (!level.getChunkSource().hasChunk(
					SectionPos.blockToSectionCoord(blockX),
					SectionPos.blockToSectionCoord(blockZ))) {
					break;
				}

				BlockState state = level.getBlockState(this.cursor);
				FluidState fluid = level.getFluidState(this.cursor);
				if (!state.isAir() || !fluid.isEmpty()) {
					float resistance = Math.max(state.getBlock().getExplosionResistance(), fluid.getExplosionResistance());
					this.rayPower -= (resistance + 0.3F) * 0.3F;
				}
				if (this.rayPower > 0.0F && (!state.isAir() || !fluid.isEmpty())) {
					addAffected(this.cursor.asLong());
				}

				this.rayX += DIRECTION_X[this.rayIndex] * STEP_SIZE;
				this.rayY += DIRECTION_Y[this.rayIndex] * STEP_SIZE;
				this.rayZ += DIRECTION_Z[this.rayIndex] * STEP_SIZE;
				this.rayPower -= STEP_POWER_LOSS;
				used++;
				if (this.rayPower <= 0.0F) finishRay();
			}
			return used;
		}

		private void finishRay() {
			this.rayActive = false;
			this.rayIndex++;
		}

		private void addAffected(final long packedPosition) {
			if (!this.affectedBlocks.add(packedPosition)) return;
			long rank = mix(packedPosition ^ this.seed ^ 0x444542524953L);
			RankedPosition candidate = new RankedPosition(rank, packedPosition);
			if (this.debrisCandidates.size() < MAX_DEBRIS_CANDIDATES) {
				this.debrisCandidates.add(candidate);
			} else if (rank < this.debrisCandidates.peek().rank()) {
				this.debrisCandidates.poll();
				this.debrisCandidates.add(candidate);
			}
		}

		private List<WarheadExplosionDropContext.DestroyedBlock> debrisSample(final ServerLevel level) {
			ArrayList<RankedPosition> ranked = new ArrayList<>(this.debrisCandidates);
			ranked.sort(Comparator.comparingLong(RankedPosition::rank));
			ArrayList<WarheadExplosionDropContext.DestroyedBlock> result = new ArrayList<>(ranked.size());
			for (RankedPosition candidate : ranked) {
				BlockPos position = BlockPos.of(candidate.packedPosition());
				BlockState state = level.getBlockState(position);
				if (!state.isAir()) {
					result.add(new WarheadExplosionDropContext.DestroyedBlock(position, state));
				}
			}
			return List.copyOf(result);
		}

		private int apply(final ServerLevel level, final int budget) {
			if (this.applyIterator == null) this.applyIterator = this.affectedBlocks.iterator();
			int appliedThisTick = 0;
			FastExplosion explosion = this.explosionContext;
			if (explosion == null) {
				Vec3 center = this.detonationCenter == null ? this.center : this.detonationCenter;
				explosion = new FastExplosion(level, null, center, this.strength);
				this.explosionContext = explosion;
			}
			while (appliedThisTick < budget && this.applyIterator.hasNext()) {
				BlockPos position = BlockPos.of(this.applyIterator.nextLong());
				BlockState state = level.getBlockState(position);
				if (!state.isAir()) {
					state.onExplosionHit(level, position, explosion, (stack, dropPosition) -> { });
				}
				appliedThisTick++;
			}
			if (!this.applyIterator.hasNext()) {
				this.applied = true;
				this.lastTouched = level.getGameTime();
			}
			return appliedThisTick;
		}
	}

	private record RankedPosition(long rank, long packedPosition) {
	}

	private static final class FastExplosion implements Explosion {
		private final ServerLevel level;
		private final @Nullable Entity source;
		private final Vec3 center;
		private final float radius;
		private final DamageSource damageSource;

		private FastExplosion(final ServerLevel level, final @Nullable Entity source, final Vec3 center, final float radius) {
			this.level = level;
			this.source = source;
			this.center = center;
			this.radius = radius;
			this.damageSource = Explosion.getDefaultDamageSource(level, source);
		}

		private DamageSource getDamageSource() {
			return this.damageSource;
		}

		@Override
		public ServerLevel level() {
			return this.level;
		}

		@Override
		public BlockInteraction getBlockInteraction() {
			return BlockInteraction.DESTROY;
		}

		@Override
		public @Nullable LivingEntity getIndirectSourceEntity() {
			return Explosion.getIndirectSourceEntity(this.source);
		}

		@Override
		public @Nullable Entity getDirectSourceEntity() {
			return this.source;
		}

		@Override
		public float radius() {
			return this.radius;
		}

		@Override
		public Vec3 center() {
			return this.center;
		}

		@Override
		public boolean canTriggerBlocks() {
			return false;
		}

		@Override
		public boolean shouldAffectBlocklikeEntities() {
			return true;
		}
	}
}
