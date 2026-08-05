package com.andye.warmod.warhead;

import com.andye.warmod.testtool.WarheadExplosionDropContext;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.SplittableRandom;
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
 * Custom staged explosion engine for strategic warheads.
 *
 * <p>This intentionally does not call Minecraft's large vanilla explosion
 * implementation. Crater geometry is produced by an evenly distributed
 * Fibonacci sphere, resistance is evaluated against a War Mod profile, and
 * world edits are applied in a measured server-tick budget. The guaranteed
 * core is exposed as virtual air immediately, allowing later missiles in the
 * same salvo to continue into the crater before queued block callbacks finish.</p>
 *
 * <p>All world reads and writes remain on the server thread. The expensive work
 * is incremental rather than off-thread, avoiding unsafe access to mutable
 * chunks while still eliminating the impact-tick freeze.</p>
 */
public final class WarheadExplosionWorkManager {
	private static final long CALCULATION_BUDGET_NANOS = 7_500_000L;
	private static final long BLOCK_APPLICATION_BUDGET_NANOS = 8_500_000L;
	private static final int MAX_CALCULATION_STEPS_PER_TICK = 180_000;
	private static final int MAX_BLOCK_CALLBACKS_PER_TICK = 8_000;
	private static final int MAX_DEBRIS_CANDIDATES = 1_024;
	private static final int MAX_RESOLVED_DESCENT_BLOCKS = 512;
	private static final long FINISHED_WORK_EXPIRY_TICKS = 40L;
	private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));
	private static final Map<ServerLevel, LevelWork> LEVELS = new WeakHashMap<>();
	private static final Map<Integer, DirectionSet> DIRECTION_CACHE = new HashMap<>();
	private static boolean registered;

	private WarheadExplosionWorkManager() {
	}

	public static synchronized void registerLifecycle() {
		if (registered) return;
		ServerTickEvents.END_LEVEL_TICK.register(WarheadExplosionWorkManager::tickLevel);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
		registered = true;
	}

	/**
	 * Resolves a requested impact through already planned crater volumes.
	 *
	 * <p>This is what makes repeated strikes cumulative. A later warhead aimed at
	 * a block already guaranteed to be destroyed is moved down to the next real
	 * solid block instead of detonating forever at the obsolete target height.</p>
	 */
	public static synchronized Vec3 resolveDetonationCenter(
		final ServerLevel level,
		final Vec3 requested,
		final WarheadPayloadType payloadType
	) {
		if (level == null || requested == null) throw new NullPointerException();
		if (!requested.isFinite()) throw new IllegalArgumentException("requested impact must be finite");
		LevelWork levelWork = LEVELS.computeIfAbsent(level, ignored -> new LevelWork());
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		cursor.set(Mth.floor(requested.x), Mth.floor(requested.y), Mth.floor(requested.z));
		int minimumY = level.dimensionType().minY();
		int startY = cursor.getY();
		int maximumDescent = Math.min(MAX_RESOLVED_DESCENT_BLOCKS, Math.max(0, startY - minimumY));

		for (int descent = 0; descent <= maximumDescent; descent++) {
			int y = startY - descent;
			cursor.set(cursor.getX(), y, cursor.getZ());
			if (!level.isInWorldBounds(cursor)) continue;
			if (!level.getChunkSource().hasChunk(
				SectionPos.blockToSectionCoord(cursor.getX()),
				SectionPos.blockToSectionCoord(cursor.getZ()))) {
				break;
			}
			long packed = cursor.asLong();
			if (levelWork.isVirtualAir(packed, cursor, null)) continue;
			BlockState state = level.getBlockState(cursor);
			FluidState fluid = level.getFluidState(cursor);
			if (!state.isAir() || !fluid.isEmpty()) {
				if (descent == 0) return requested;
				return new Vec3(requested.x, y + 0.98, requested.z);
			}
		}
		return requested;
	}

	public static synchronized List<WarheadExplosionDropContext.DestroyedBlock> detonate(
		final ServerLevel level,
		final @Nullable ServerPlayer source,
		final UUID warheadId,
		final Vec3 position,
		final WarheadPayloadType payloadType,
		final long seed
	) {
		if (level == null || warheadId == null || position == null || payloadType == null) {
			throw new NullPointerException();
		}
		if (!position.isFinite()) throw new IllegalArgumentException("Invalid staged explosion position");

		StrategicExplosionProfile profile = StrategicExplosionProfiles.get(payloadType);
		LevelWork levelWork = LEVELS.computeIfAbsent(level, ignored -> new LevelWork());
		ExplosionWork existing = levelWork.byWarhead.get(warheadId);
		if (existing != null && !existing.finished) return List.of();

		ExplosionWork work = new ExplosionWork(warheadId, position, profile, seed, level.getGameTime());
		levelWork.works.add(work);
		levelWork.byWarhead.put(warheadId, work);
		levelWork.addVoidVolume(work.coreVolume);
		applyImmediateEntityEffects(level, source, position, profile.entityBlastRadius());
		work.explosionContext = new FastExplosion(level, source, position, profile.entityBlastRadius());
		return work.sampleInitialDebris(level, levelWork);
	}

	/** Compatibility bridge for callers compiled against Stage 2. */
	public static List<WarheadExplosionDropContext.DestroyedBlock> detonate(
		final ServerLevel level,
		final @Nullable ServerPlayer source,
		final UUID warheadId,
		final Vec3 position,
		final float strength,
		final long seed
	) {
		return detonate(
			level,
			source,
			warheadId,
			position,
			StrategicExplosionProfiles.fromLegacyStrength(strength).payloadType(),
			seed
		);
	}

	private static synchronized void tickLevel(final ServerLevel level) {
		LevelWork levelWork = LEVELS.get(level);
		if (levelWork == null) return;
		long now = level.getGameTime();
		long calculationDeadline = System.nanoTime() + CALCULATION_BUDGET_NANOS;
		int remainingSteps = MAX_CALCULATION_STEPS_PER_TICK;

		while (remainingSteps > 0 && System.nanoTime() < calculationDeadline) {
			ExplosionWork work = levelWork.nextCalculationWork();
			if (work == null) break;
			int slice = Math.min(8_192, remainingSteps);
			int used = work.advance(level, levelWork, slice);
			remainingSteps -= Math.max(1, used);
			if (used == 0 && !work.calculationComplete) break;
		}

		long applicationDeadline = System.nanoTime() + BLOCK_APPLICATION_BUDGET_NANOS;
		int remainingBlocks = MAX_BLOCK_CALLBACKS_PER_TICK;
		while (remainingBlocks > 0 && System.nanoTime() < applicationDeadline) {
			ExplosionWork work = levelWork.nextApplicationWork();
			if (work == null) break;
			int slice = Math.min(512, remainingBlocks);
			int applied = work.apply(level, levelWork, slice);
			remainingBlocks -= Math.max(1, applied);
			if (applied == 0 && !work.finished) break;
		}

		Iterator<ExplosionWork> iterator = levelWork.works.iterator();
		while (iterator.hasNext()) {
			ExplosionWork work = iterator.next();
			if (!work.finished || now - work.finishedAt <= FINISHED_WORK_EXPIRY_TICKS) continue;
			levelWork.removeVoidVolume(work.coreVolume);
			levelWork.byWarhead.remove(work.warheadId);
			iterator.remove();
		}
		levelWork.normaliseCursors();
		if (levelWork.works.isEmpty()) LEVELS.remove(level);
	}

	private static synchronized void clear() {
		LEVELS.clear();
		DIRECTION_CACHE.clear();
	}

	private static DirectionSet directions(final int count) {
		return DIRECTION_CACHE.computeIfAbsent(count, DirectionSet::new);
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
			Vec3 difference = entityOrigin.subtract(center);
			if (difference.lengthSqr() < 1.0E-9) difference = new Vec3(0.0, 1.0, 0.0);
			Vec3 direction = difference.normalize();
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

	private static long chunkKey(final int chunkX, final int chunkZ) {
		return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
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
		private final LongOpenHashSet pendingBlocks = new LongOpenHashSet();
		private final Map<Long, List<VoidVolume>> volumesByChunk = new HashMap<>();
		private int calculationCursor;
		private int applicationCursor;

		private void addVoidVolume(final VoidVolume volume) {
			for (int chunkX = volume.minimumChunkX; chunkX <= volume.maximumChunkX; chunkX++) {
				for (int chunkZ = volume.minimumChunkZ; chunkZ <= volume.maximumChunkZ; chunkZ++) {
					volumesByChunk.computeIfAbsent(chunkKey(chunkX, chunkZ), ignored -> new ArrayList<>()).add(volume);
				}
			}
		}

		private void removeVoidVolume(final VoidVolume volume) {
			for (int chunkX = volume.minimumChunkX; chunkX <= volume.maximumChunkX; chunkX++) {
				for (int chunkZ = volume.minimumChunkZ; chunkZ <= volume.maximumChunkZ; chunkZ++) {
					long key = chunkKey(chunkX, chunkZ);
					List<VoidVolume> volumes = volumesByChunk.get(key);
					if (volumes == null) continue;
					volumes.remove(volume);
					if (volumes.isEmpty()) volumesByChunk.remove(key);
				}
			}
		}

		private boolean isVirtualAir(
			final long packed,
			final BlockPos position,
			final @Nullable ExplosionWork ignoredOwner
		) {
			if (pendingBlocks.contains(packed)) return true;
			List<VoidVolume> volumes = volumesByChunk.get(chunkKey(position.getX() >> 4, position.getZ() >> 4));
			if (volumes == null) return false;
			for (VoidVolume volume : volumes) {
				if (volume.owner == ignoredOwner) continue;
				if (volume.contains(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5)) return true;
			}
			return false;
		}

		private boolean claim(final long packed) {
			return pendingBlocks.add(packed);
		}

		private void release(final long packed) {
			pendingBlocks.remove(packed);
		}

		private ExplosionWork nextCalculationWork() {
			if (works.isEmpty()) return null;
			for (int checked = 0; checked < works.size(); checked++) {
				if (calculationCursor >= works.size()) calculationCursor = 0;
				ExplosionWork work = works.get(calculationCursor++);
				if (!work.calculationComplete) return work;
			}
			return null;
		}

		private ExplosionWork nextApplicationWork() {
			if (works.isEmpty()) return null;
			for (int checked = 0; checked < works.size(); checked++) {
				if (applicationCursor >= works.size()) applicationCursor = 0;
				ExplosionWork work = works.get(applicationCursor++);
				if (work.calculationComplete && !work.finished) return work;
			}
			return null;
		}

		private void normaliseCursors() {
			if (works.isEmpty()) {
				calculationCursor = 0;
				applicationCursor = 0;
			} else {
				calculationCursor %= works.size();
				applicationCursor %= works.size();
			}
		}
	}

	private static final class ExplosionWork {
		private final UUID warheadId;
		private final Vec3 center;
		private final StrategicExplosionProfile profile;
		private final long seed;
		private final VoidVolume coreVolume;
		private final DirectionSet directionSet;
		private final LongOpenHashSet ownedBlocks = new LongOpenHashSet();
		private final LongArrayList[] applicationBuckets;
		private final PriorityQueue<RankedPosition> debrisCandidates = new PriorityQueue<>(
			MAX_DEBRIS_CANDIDATES + 1,
			Comparator.comparingLong(RankedPosition::rank).reversed()
		);
		private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		private final int coreMinimumX;
		private final int coreMaximumX;
		private final int coreMinimumY;
		private final int coreMaximumY;
		private final int coreMinimumZ;
		private final int coreMaximumZ;
		private int coreX;
		private int coreY;
		private int coreZ;
		private boolean coreComplete;
		private int rayIndex;
		private boolean rayActive;
		private double rayX;
		private double rayY;
		private double rayZ;
		private double rayDistance;
		private double rayMaximumDistance;
		private double activeDirectionX;
		private double activeDirectionY;
		private double activeDirectionZ;
		private float rayEnergy;
		private long lastRayBlock = Long.MIN_VALUE;
		private boolean calculationComplete;
		private int applyBucket;
		private int applyIndex;
		private boolean finished;
		private long finishedAt;
		private FastExplosion explosionContext;

		private ExplosionWork(
			final UUID warheadId,
			final Vec3 center,
			final StrategicExplosionProfile profile,
			final long seed,
			final long gameTime
		) {
			this.warheadId = warheadId;
			this.center = center;
			this.profile = profile;
			this.seed = seed;
			this.directionSet = directions(profile.rayCount());
			this.coreVolume = new VoidVolume(this, center, profile);
			int bucketCount = Math.max(8, (int) Math.ceil(profile.maximumRadius() / 2.0) + 2);
			this.applicationBuckets = new LongArrayList[bucketCount];
			for (int index = 0; index < bucketCount; index++) applicationBuckets[index] = new LongArrayList();
			this.coreMinimumX = Mth.floor(center.x - profile.coreHorizontalRadius());
			this.coreMaximumX = Mth.floor(center.x + profile.coreHorizontalRadius());
			this.coreMinimumY = Mth.floor(center.y - profile.coreDownwardRadius());
			this.coreMaximumY = Mth.floor(center.y + profile.coreUpwardRadius());
			this.coreMinimumZ = Mth.floor(center.z - profile.coreHorizontalRadius());
			this.coreMaximumZ = Mth.floor(center.z + profile.coreHorizontalRadius());
			this.coreX = coreMinimumX;
			this.coreY = coreMinimumY;
			this.coreZ = coreMinimumZ;
		}

		private int advance(final ServerLevel level, final LevelWork levelWork, final int budget) {
			int used = 0;
			while (used < budget && !calculationComplete) {
				if (!coreComplete) {
					advanceCore(level, levelWork);
					used++;
					continue;
				}
				if (!rayActive) {
					if (rayIndex >= directionSet.count()) {
						calculationComplete = true;
						break;
					}
					beginRay();
				}
				advanceRay(level, levelWork);
				used++;
			}
			return used;
		}

		private void advanceCore(final ServerLevel level, final LevelWork levelWork) {
			cursor.set(coreX, coreY, coreZ);
			if (level.isInWorldBounds(cursor) && coreVolume.contains(coreX + 0.5, coreY + 0.5, coreZ + 0.5)) {
				if (level.getChunkSource().hasChunk(
					SectionPos.blockToSectionCoord(coreX),
					SectionPos.blockToSectionCoord(coreZ))) {
					long packed = cursor.asLong();
					BlockState state = level.getBlockState(cursor);
					FluidState fluid = level.getFluidState(cursor);
					if ((!state.isAir() || !fluid.isEmpty()) && !levelWork.pendingBlocks.contains(packed)) {
						addAffected(levelWork, packed, cursor);
					}
				}
			}
			coreZ++;
			if (coreZ > coreMaximumZ) {
				coreZ = coreMinimumZ;
				coreX++;
				if (coreX > coreMaximumX) {
					coreX = coreMinimumX;
					coreY++;
					if (coreY > coreMaximumY) coreComplete = true;
				}
			}
		}

		private void beginRay() {
			double baseX = directionSet.x[rayIndex];
			double baseY = directionSet.y[rayIndex];
			double baseZ = directionSet.z[rayIndex];
			double rotation = unit(mix(seed ^ 0x5241595F524F544CL)) * Math.PI * 2.0;
			double cos = Math.cos(rotation);
			double sin = Math.sin(rotation);
			activeDirectionX = baseX * cos - baseZ * sin;
			activeDirectionY = baseY;
			activeDirectionZ = baseX * sin + baseZ * cos;
			rayX = center.x;
			rayY = center.y;
			rayZ = center.z;
			rayDistance = 0.0;
			rayMaximumDistance = ellipsoidRadius(
				activeDirectionX,
				activeDirectionY,
				activeDirectionZ,
				profile
			);
			double random = unit(mix(seed ^ (long) rayIndex * 0x9E3779B97F4A7C15L));
			rayEnergy = profile.initialEnergy() * (float) (0.88 + random * 0.24);
			rayActive = true;
			lastRayBlock = Long.MIN_VALUE;
		}

		private void advanceRay(final ServerLevel level, final LevelWork levelWork) {
			int blockX = Mth.floor(rayX);
			int blockY = Mth.floor(rayY);
			int blockZ = Mth.floor(rayZ);
			cursor.set(blockX, blockY, blockZ);
			if (!level.isInWorldBounds(cursor)) {
				finishRay();
				return;
			}
			if (!level.getChunkSource().hasChunk(
				SectionPos.blockToSectionCoord(blockX),
				SectionPos.blockToSectionCoord(blockZ))) {
				finishRay();
				return;
			}

			long packed = cursor.asLong();
			if (packed != lastRayBlock) {
				lastRayBlock = packed;
				if (!levelWork.isVirtualAir(packed, cursor, this)) {
					BlockState state = level.getBlockState(cursor);
					FluidState fluid = level.getFluidState(cursor);
					if (!state.isAir() || !fluid.isEmpty()) {
						float resistance = Math.max(
							state.getBlock().getExplosionResistance(),
							fluid.getExplosionResistance()
						);
						float resistanceCost = Math.min(
							profile.maximumResistanceCost(),
							resistance * profile.resistanceScale()
						);
						rayEnergy -= resistanceCost;
						if (rayEnergy > 0.0F) addAffected(levelWork, packed, cursor);
					}
				}
			}

			double step = profile.rayStep();
			rayX += activeDirectionX * step;
			rayY += activeDirectionY * step;
			rayZ += activeDirectionZ * step;
			rayDistance += step;
			rayEnergy -= profile.airEnergyLossPerBlock() * (float) step;
			if (rayEnergy <= 0.0F || rayDistance >= rayMaximumDistance) finishRay();
		}

		private void finishRay() {
			rayActive = false;
			rayIndex++;
		}

		private void addAffected(final LevelWork levelWork, final long packed, final BlockPos position) {
			if (!levelWork.claim(packed)) return;
			ownedBlocks.add(packed);
			double normalized = normalizedEllipsoidDistance(
				position.getX() + 0.5,
				position.getY() + 0.5,
				position.getZ() + 0.5,
				center,
				profile
			);
			int bucket = Math.min(applicationBuckets.length - 1,
				Math.max(0, (int) Math.floor(normalized * (applicationBuckets.length - 1))));
			applicationBuckets[bucket].add(packed);
			long rank = mix(packed ^ seed ^ 0x444542524953L);
			RankedPosition candidate = new RankedPosition(rank, packed);
			if (debrisCandidates.size() < MAX_DEBRIS_CANDIDATES) {
				debrisCandidates.add(candidate);
			} else if (rank < debrisCandidates.peek().rank()) {
				debrisCandidates.poll();
				debrisCandidates.add(candidate);
			}
		}

		private List<WarheadExplosionDropContext.DestroyedBlock> sampleInitialDebris(
			final ServerLevel level,
			final LevelWork levelWork
		) {
			int target = profile.payloadType() == WarheadPayloadType.NUCLEAR ? 640 : 320;
			ArrayList<WarheadExplosionDropContext.DestroyedBlock> result = new ArrayList<>(target);
			LongOpenHashSet sampled = new LongOpenHashSet();
			SplittableRandom random = new SplittableRandom(seed ^ 0x4445425249535F33L);
			int attempts = target * 8;
			for (int attempt = 0; attempt < attempts && result.size() < target; attempt++) {
				double angle = random.nextDouble(0.0, Math.PI * 2.0);
				double radial = Math.sqrt(random.nextDouble()) * profile.coreHorizontalRadius();
				double verticalRadius = random.nextBoolean()
					? profile.coreUpwardRadius()
					: profile.coreDownwardRadius();
				double yOffset = random.nextDouble(-verticalRadius, verticalRadius);
				cursor.set(
					Mth.floor(center.x + Math.cos(angle) * radial),
					Mth.floor(center.y + yOffset),
					Mth.floor(center.z + Math.sin(angle) * radial)
				);
				if (!level.isInWorldBounds(cursor)) continue;
				long packed = cursor.asLong();
				if (!sampled.add(packed)) continue;
				BlockState state = level.getBlockState(cursor);
				if (state.isAir()) continue;
				result.add(new WarheadExplosionDropContext.DestroyedBlock(BlockPos.of(packed), state));
			}
			return List.copyOf(result);
		}

		private int apply(final ServerLevel level, final LevelWork levelWork, final int budget) {
			int applied = 0;
			FastExplosion explosion = explosionContext;
			if (explosion == null) {
				explosion = new FastExplosion(level, null, center, profile.entityBlastRadius());
				explosionContext = explosion;
			}
			while (applied < budget && applyBucket < applicationBuckets.length) {
				LongArrayList bucket = applicationBuckets[applyBucket];
				if (applyIndex >= bucket.size()) {
					applyBucket++;
					applyIndex = 0;
					continue;
				}
				long packed = bucket.getLong(applyIndex++);
				BlockPos position = BlockPos.of(packed);
				BlockState state = level.getBlockState(position);
				if (!state.isAir()) {
					state.onExplosionHit(level, position, explosion, (stack, dropPosition) -> { });
				}
				levelWork.release(packed);
				ownedBlocks.remove(packed);
				applied++;
			}
			if (applyBucket >= applicationBuckets.length) {
				finished = true;
				finishedAt = level.getGameTime();
			}
			return applied;
		}
	}

	private static double ellipsoidRadius(
		final double dx,
		final double dy,
		final double dz,
		final StrategicExplosionProfile profile
	) {
		double verticalRadius = dy < 0.0 ? profile.downwardRadius() : profile.upwardRadius();
		double inverseSquared = (dx * dx + dz * dz)
			/ (profile.horizontalRadius() * profile.horizontalRadius())
			+ (dy * dy) / (verticalRadius * verticalRadius);
		return inverseSquared <= 1.0E-12 ? profile.maximumRadius() : 1.0 / Math.sqrt(inverseSquared);
	}

	private static double normalizedEllipsoidDistance(
		final double x,
		final double y,
		final double z,
		final Vec3 center,
		final StrategicExplosionProfile profile
	) {
		double dx = x - center.x;
		double dy = y - center.y;
		double dz = z - center.z;
		double verticalRadius = dy < 0.0 ? profile.downwardRadius() : profile.upwardRadius();
		return Math.min(1.0, Math.sqrt(
			(dx * dx + dz * dz) / (profile.horizontalRadius() * profile.horizontalRadius())
				+ (dy * dy) / (verticalRadius * verticalRadius)
		));
	}

	private static final class DirectionSet {
		private final double[] x;
		private final double[] y;
		private final double[] z;
		private DirectionSet(final int count) {
			x = new double[count];
			y = new double[count];
			z = new double[count];
			for (int index = 0; index < count; index++) {
				double vertical = 1.0 - 2.0 * (index + 0.5) / count;
				double horizontal = Math.sqrt(Math.max(0.0, 1.0 - vertical * vertical));
				double angle = GOLDEN_ANGLE * index;
				x[index] = Math.cos(angle) * horizontal;
				y[index] = vertical;
				z[index] = Math.sin(angle) * horizontal;
			}
		}

		private int count() {
			return x.length;
		}
	}

	private static final class VoidVolume {
		private final ExplosionWork owner;
		private final Vec3 center;
		private final double horizontalRadius;
		private final double upwardRadius;
		private final double downwardRadius;
		private final int minimumChunkX;
		private final int maximumChunkX;
		private final int minimumChunkZ;
		private final int maximumChunkZ;

		private VoidVolume(
			final ExplosionWork owner,
			final Vec3 center,
			final StrategicExplosionProfile profile
		) {
			this.owner = owner;
			this.center = center;
			this.horizontalRadius = profile.coreHorizontalRadius();
			this.upwardRadius = profile.coreUpwardRadius();
			this.downwardRadius = profile.coreDownwardRadius();
			this.minimumChunkX = Mth.floor(center.x - horizontalRadius) >> 4;
			this.maximumChunkX = Mth.floor(center.x + horizontalRadius) >> 4;
			this.minimumChunkZ = Mth.floor(center.z - horizontalRadius) >> 4;
			this.maximumChunkZ = Mth.floor(center.z + horizontalRadius) >> 4;
		}

		private boolean contains(final double x, final double y, final double z) {
			double dx = x - center.x;
			double dy = y - center.y;
			double dz = z - center.z;
			double vertical = dy < 0.0 ? downwardRadius : upwardRadius;
			return (dx * dx + dz * dz) / (horizontalRadius * horizontalRadius)
				+ (dy * dy) / (vertical * vertical) <= 1.0;
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
			return damageSource;
		}

		@Override
		public ServerLevel level() {
			return level;
		}

		@Override
		public BlockInteraction getBlockInteraction() {
			return BlockInteraction.DESTROY;
		}

		@Override
		public @Nullable LivingEntity getIndirectSourceEntity() {
			return Explosion.getIndirectSourceEntity(source);
		}

		@Override
		public @Nullable Entity getDirectSourceEntity() {
			return source;
		}

		@Override
		public float radius() {
			return radius;
		}

		@Override
		public Vec3 center() {
			return center;
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
