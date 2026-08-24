package com.andye.warmod.warhead;

import com.andye.warmod.diagnostics.WarModPerformanceDiagnostics;
import com.andye.warmod.testtool.WarheadExplosionDropContext;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.EntityBasedExplosionDamageCalculator;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/**
 * Smooth, staged strategic crater engine.
 *
 * <p>Stage 3 used thousands of destructive rays. It was considerably faster
 * than vanilla, but those rays left visible straight channels and each block
 * callback could create loot and experience. Stage 4 instead applies compact
 * precomputed vertical column spans describing a noisy ellipsoid. Shape
 * templates are generated on daemon worker threads because they contain no
 * world data; all chunk reads and block writes remain on the server thread.</p>
 *
 * <p>Ordinary terrain is replaced directly with air using suppressed-drop
 * update flags. This intentionally produces no item stacks or XP orbs. TNT is
 * still given its explosion callback so chain reactions continue to work.</p>
 */
public final class WarheadExplosionWorkManager {
	/* Both paths mutate on the server thread. Nuclear work is released immediately
	 * after impact in large bounded slices, so the flash masks a fast thermal change
	 * without one unbounded impact-frame stall. */
	private static final long BLOCK_APPLICATION_BUDGET_NANOS = 4_000_000L;
	private static final int MAX_BLOCK_CHANGES_PER_LEVEL_TICK = 8_192;
	private static final int APPLICATION_SLICE = 256;
	/* World mutation is main-thread-only. Keep nuclear application bounded and
	 * let the independent destruction curtain mask deliberately staged writes. */
	private static final long NUCLEAR_APPLICATION_BUDGET_NANOS = 6_000_000L;
	private static final int NUCLEAR_MAX_BLOCK_CHANGES_PER_LEVEL_TICK = 32_768;
	private static final int NUCLEAR_APPLICATION_SLICE = 2_048;
	private static final int TIME_CHECK_INTERVAL = 32;
	/* Heightmaps are the authoritative starting point; only peel a small surface
	 * cap, never descend through an entire terrain column looking for ground. */
	private static final int SURFACE_SUPPORT_DESCENT = 8;
	private static final int MAX_RESOLVED_DESCENT_BLOCKS = 768;
	private static final int MAX_DEBRIS_SAMPLE = 512;
	private static final int IMMEDIATE_SUPPORT_SCAN_HEIGHT = 12;
	private static final int STRUCTURAL_SCAN_HEIGHT = 56;
	private static final long FINISHED_WORK_EXPIRY_TICKS = 40L;
	private static final int FAST_REMOVE_FLAGS = Block.UPDATE_CLIENTS
		| Block.UPDATE_KNOWN_SHAPE
		| Block.UPDATE_SUPPRESS_DROPS;
	private static final Map<ServerLevel, LevelWork> LEVELS = new WeakHashMap<>();
	private static final Map<WarheadYield, CompletableFuture<ShapeTemplate>> TEMPLATES = new EnumMap<>(WarheadYield.class);
	private static final ExecutorService SHAPE_EXECUTOR = Executors.newFixedThreadPool(
		Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() / 4)),
		runnable -> {
			Thread thread = new Thread(runnable, "war-mod-crater-shape");
			thread.setDaemon(true);
			return thread;
		}
	);
	private static boolean registered;

	private WarheadExplosionWorkManager() {
	}

	public static synchronized void registerLifecycle() {
		if (registered) return;
		for (WarheadYield yield : WarheadYield.values()) templateFuture(yield);
		ServerTickEvents.END_LEVEL_TICK.register(WarheadExplosionWorkManager::tickLevel);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
		registered = true;
	}

	public static synchronized Vec3 resolveDetonationCenter(
		final ServerLevel level,
		final Vec3 requested,
		final WarheadYield yield
	) {
		if (level == null || requested == null || yield == null) throw new NullPointerException();
		if (!requested.isFinite()) throw new IllegalArgumentException("requested impact must be finite");
		LevelWork levelWork = LEVELS.computeIfAbsent(level, ignored -> new LevelWork());
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		cursor.set(Mth.floor(requested.x), Mth.floor(requested.y), Mth.floor(requested.z));
		int startY = cursor.getY();
		int minimumY = level.dimensionType().minY();
		int maximumDescent = Math.min(MAX_RESOLVED_DESCENT_BLOCKS, Math.max(0, startY - minimumY));

		for (int descent = 0; descent <= maximumDescent; descent++) {
			int y = startY - descent;
			cursor.set(cursor.getX(), y, cursor.getZ());
			if (!level.isInWorldBounds(cursor)) continue;
			if (!level.getChunkSource().hasChunk(
				SectionPos.blockToSectionCoord(cursor.getX()),
				SectionPos.blockToSectionCoord(cursor.getZ()))) break;
			if (levelWork.isVirtualAir(cursor)) continue;
			BlockState state = level.getBlockState(cursor);
			FluidState fluid = state.getFluidState();
			if (!state.isAir() || !fluid.isEmpty()) {
				return descent == 0 ? requested : new Vec3(requested.x, y + 0.98, requested.z);
			}
		}
		return requested;
	}

	public static Vec3 resolveDetonationCenter(
		final ServerLevel level,
		final Vec3 requested,
		final WarheadPayloadType payloadType
	) {
		return resolveDetonationCenter(level, requested, WarheadYield.defaultFor(payloadType));
	}

	public static synchronized List<WarheadExplosionDropContext.DestroyedBlock> detonate(
		final ServerLevel level,
		final @Nullable ServerPlayer source,
		final UUID warheadId,
		final Vec3 position,
		final WarheadYield yield,
		final long seed
	) {
		ExplosionWork work = scheduleDetonation(level, source, warheadId, position, yield, seed, false);
		return work == null ? List.of() : work.sampleInitialDebris(level);
	}

	/**
	 * Schedules the normal crater/entity work without performing the legacy
	 * debris sample. Callers that already captured debris should use this path.
	 */
	public static synchronized void detonateWithoutDebrisSample(
		final ServerLevel level,
		final @Nullable ServerPlayer source,
		final UUID warheadId,
		final Vec3 position,
		final WarheadYield yield,
		final long seed
	) {
		detonateWithoutDebrisSample(level, source, warheadId, position, yield, seed, false);
	}

	public static synchronized void detonateWithoutDebrisSample(
		final ServerLevel level,
		final @Nullable ServerPlayer source,
		final UUID warheadId,
		final Vec3 position,
		final WarheadYield yield,
		final long seed,
		final boolean customFire
	) {
		scheduleDetonation(level, source, warheadId, position, yield, seed, customFire);
	}

	/** True once the destructive crater columns can no longer overwrite its final skin. */
	public static synchronized boolean isCraterExcavationComplete(
		final ServerLevel level, final UUID warheadId
	) {
		LevelWork levelWork = LEVELS.get(level);
		ExplosionWork work = levelWork == null ? null : levelWork.byWarhead.get(warheadId);
		return work == null || work.craterExcavationComplete();
	}

	private static ExplosionWork scheduleDetonation(
		final ServerLevel level,
		final @Nullable ServerPlayer source,
		final UUID warheadId,
		final Vec3 position,
		final WarheadYield yield,
		final long seed,
		final boolean customFire
	) {
		if (level == null || warheadId == null || position == null || yield == null) throw new NullPointerException();
		if (!position.isFinite()) throw new IllegalArgumentException("Invalid staged explosion position");
		StrategicExplosionProfile profile = StrategicExplosionProfiles.get(yield);
		LevelWork levelWork = LEVELS.computeIfAbsent(level, ignored -> new LevelWork());
		ExplosionWork existing = levelWork.byWarhead.get(warheadId);
		if (existing != null && !existing.finished) return null;

		ExplosionWork work = new ExplosionWork(
			warheadId,
			position,
			profile,
			seed,
			templateFuture(yield),
			level.getGameTime(),
			customFire
		);
		levelWork.works.add(work);
		levelWork.byWarhead.put(warheadId, work);
		levelWork.addVoidVolume(work.voidVolume);
		applyImmediateEntityEffects(level, source, position, profile.entityBlastRadius());
		work.explosionContext = new FastExplosion(level, source, position, profile.entityBlastRadius());
		return work;
	}

	public static List<WarheadExplosionDropContext.DestroyedBlock> detonate(
		final ServerLevel level,
		final @Nullable ServerPlayer source,
		final UUID warheadId,
		final Vec3 position,
		final WarheadPayloadType payloadType,
		final long seed
	) {
		return detonate(level, source, warheadId, position, WarheadYield.defaultFor(payloadType), seed);
	}

	public static List<WarheadExplosionDropContext.DestroyedBlock> detonate(
		final ServerLevel level,
		final @Nullable ServerPlayer source,
		final UUID warheadId,
		final Vec3 position,
		final float legacyStrength,
		final long seed
	) {
		return detonate(
			level,
			source,
			warheadId,
			position,
			StrategicExplosionProfiles.fromLegacyStrength(legacyStrength).yield(),
			seed
		);
	}

	private static synchronized CompletableFuture<ShapeTemplate> templateFuture(final WarheadYield yield) {
		return TEMPLATES.computeIfAbsent(yield, key -> CompletableFuture.supplyAsync(
			() -> ShapeTemplate.create(StrategicExplosionProfiles.get(key)),
			SHAPE_EXECUTOR
		));
	}

	private static synchronized void tickLevel(final ServerLevel level) {
		LevelWork levelWork = LEVELS.get(level);
		if (levelWork == null) {
			WarModPerformanceDiagnostics.gauge(
				WarModPerformanceDiagnostics.Gauge.ACTIVE_NUCLEAR_CRATERS, 0L);
			return;
		}
		long diagnosticsStarted = WarModPerformanceDiagnostics.begin();
		long activeNuclear = levelWork.works.stream()
			.filter(work -> !work.finished && work.profile.yield().nuclear()).count();
		WarModPerformanceDiagnostics.gauge(
			WarModPerformanceDiagnostics.Gauge.ACTIVE_NUCLEAR_CRATERS, activeNuclear);
		long now = level.getGameTime();
		/*
		 * Nuclear catch-up is intentionally isolated from ordinary explosion work.
		 * A front-gated crater is skipped for the rest of this tick instead of
		 * spinning until the deadline while waiting for the visual shockwave.
		 */
		applyWorkClass(level, levelWork, true, NUCLEAR_MAX_BLOCK_CHANGES_PER_LEVEL_TICK,
			NUCLEAR_APPLICATION_SLICE, NUCLEAR_APPLICATION_BUDGET_NANOS);
		applyWorkClass(level, levelWork, false, MAX_BLOCK_CHANGES_PER_LEVEL_TICK,
			APPLICATION_SLICE, BLOCK_APPLICATION_BUDGET_NANOS);

		Iterator<ExplosionWork> iterator = levelWork.works.iterator();
		while (iterator.hasNext()) {
			ExplosionWork work = iterator.next();
			if (!work.finished || now - work.finishedAt <= FINISHED_WORK_EXPIRY_TICKS) continue;
			levelWork.removeVoidVolume(work.voidVolume);
			levelWork.byWarhead.remove(work.warheadId);
			iterator.remove();
		}
		levelWork.normaliseCursor();
		if (levelWork.works.isEmpty()) LEVELS.remove(level);
		WarModPerformanceDiagnostics.record(
			WarModPerformanceDiagnostics.Subsystem.NUCLEAR_CRATER, diagnosticsStarted);
	}

	private static void applyWorkClass(
		final ServerLevel level,
		final LevelWork levelWork,
		final boolean nuclear,
		final int changeBudget,
		final int applicationSlice,
		final long timeBudgetNanos
	) {
		if (!levelWork.hasActiveWork(nuclear)) return;
		long deadline = System.nanoTime() + timeBudgetNanos;
		int remaining = changeBudget;
		Set<UUID> unavailableThisTick = new HashSet<>();
		while (remaining > 0 && System.nanoTime() < deadline) {
			ExplosionWork work = levelWork.nextApplicationWork(nuclear, unavailableThisTick);
			if (work == null) break;
			int changed = work.apply(level, levelWork, Math.min(applicationSlice, remaining), deadline);
			remaining -= Math.max(1, changed);
			if (work.frontBlockedThisApply || (!work.finished && !work.templateReady())) {
				unavailableThisTick.add(work.warheadId);
			}
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
		AABB bounds = new AABB(
			center.x - doubleRadius - 1.0,
			center.y - doubleRadius - 1.0,
			center.z - doubleRadius - 1.0,
			center.x + doubleRadius + 1.0,
			center.y + doubleRadius + 1.0,
			center.z + doubleRadius + 1.0
		);
		ExplosionDamageCalculator calculator = source == null
			? new ExplosionDamageCalculator()
			: new EntityBasedExplosionDamageCalculator(source);

		/* Query with a null exclusion so the owner is not accidentally immune to
		 * their own conventional or nuclear warhead. The source remains attached
		 * to the damage source for attribution. */
		for (Entity entity : level.getEntities(null, bounds)) {
			/* Existing loose items and XP are vapourised instead of becoming a second lag source. */
			if (entity instanceof ExperienceOrb || entity instanceof ItemEntity) {
				entity.discard();
				continue;
			}
			if (entity.ignoreExplosion(explosion)) continue;
			double normalizedDistance = Math.sqrt(entity.distanceToSqr(center)) / doubleRadius;
			if (normalizedDistance > 1.0) continue;
			Vec3 entityOrigin = entity instanceof PrimedTnt ? entity.position() : entity.getEyePosition();
			Vec3 difference = entityOrigin.subtract(center);
			if (difference.lengthSqr() < 1.0E-9) difference = new Vec3(0.0, 1.0, 0.0);
			Vec3 direction = difference.normalize();
			boolean damage = calculator.shouldDamageEntity(explosion, entity);
			float knockbackMultiplier = calculator.getKnockbackMultiplier(entity);
			/* Seven fixed collision rays preserve real cover without allowing a
			 * strategic yield to turn into an unbounded visibility workload. The
			 * vanilla damage calculator supplies proximity falloff; this value is
			 * strictly the terrain/wall transmission term. */
			float exposure = !damage && knockbackMultiplier == 0.0F
				? 0.0F : terrainExposure(level, center, entity);
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

	private static float terrainExposure(final ServerLevel level, final Vec3 center,
		final Entity entity) {
		AABB box = entity.getBoundingBox();
		double middleX = (box.minX + box.maxX) * 0.5;
		double middleY = (box.minY + box.maxY) * 0.5;
		double middleZ = (box.minZ + box.maxZ) * 0.5;
		double insetX = Math.min(0.20, box.getXsize() * 0.20);
		double insetY = Math.min(0.20, box.getYsize() * 0.12);
		double insetZ = Math.min(0.20, box.getZsize() * 0.20);
		Vec3[] targets = {
			entity.getEyePosition(),
			new Vec3(middleX, middleY, middleZ),
			new Vec3(box.minX + insetX, middleY, middleZ),
			new Vec3(box.maxX - insetX, middleY, middleZ),
			new Vec3(middleX, middleY, box.minZ + insetZ),
			new Vec3(middleX, middleY, box.maxZ - insetZ),
			new Vec3(middleX, box.minY + insetY, middleZ)
		};
		int clearRays = 0;
		for (Vec3 target : targets) {
			if (clearBlastRay(level, center, target)) clearRays++;
		}
		return WarheadBlastExposure.transmission(clearRays, targets.length);
	}

	private static boolean clearBlastRay(final ServerLevel level, final Vec3 center,
		final Vec3 target) {
		Vec3 delta = target.subtract(center);
		double distance = delta.length();
		if (!Double.isFinite(distance) || distance < 1.0E-5) return true;
		/* Skip only the innermost blast cell, which the crater guarantees will be
		 * excavated. Cover beyond it remains fully eligible to absorb pressure. */
		Vec3 start = center.add(delta.scale(Math.min(1.25, distance * 0.25) / distance));
		BlockHitResult hit = level.clip(new ClipContext(start, target,
			ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS
			|| hit.getLocation().distanceToSqr(target) <= 0.12 * 0.12;
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
		return (mix(value) >>> 11) * 0x1.0p-53;
	}

	private static final class LevelWork {
		private final List<ExplosionWork> works = new ArrayList<>();
		private final Map<UUID, ExplosionWork> byWarhead = new HashMap<>();
		private final LongOpenHashSet pendingBlocks = new LongOpenHashSet();
		private final Map<Long, List<VoidVolume>> volumesByChunk = new HashMap<>();
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

		private boolean isVirtualAir(final BlockPos position) {
			if (pendingBlocks.contains(position.asLong())) return true;
			List<VoidVolume> volumes = volumesByChunk.get(chunkKey(position.getX() >> 4, position.getZ() >> 4));
			if (volumes == null) return false;
			for (VoidVolume volume : volumes) {
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

		private ExplosionWork nextApplicationWork(final boolean nuclear, final Set<UUID> unavailable) {
			if (works.isEmpty()) return null;
			for (int checked = 0; checked < works.size(); checked++) {
				if (applicationCursor >= works.size()) applicationCursor = 0;
				ExplosionWork work = works.get(applicationCursor++);
				if (!work.finished
					&& work.profile.yield().nuclear() == nuclear
					&& !unavailable.contains(work.warheadId)) return work;
			}
			return null;
		}

		private boolean hasActiveWork(final boolean nuclear) {
			for (ExplosionWork work : works) {
				if (!work.finished && work.profile.yield().nuclear() == nuclear) return true;
			}
			return false;
		}

		private void normaliseCursor() {
			applicationCursor = works.isEmpty() ? 0 : applicationCursor % works.size();
		}
	}

	private static final class ExplosionWork {
		private final UUID warheadId;
		private final Vec3 center;
		private final StrategicExplosionProfile profile;
		private final long seed;
		private final CompletableFuture<ShapeTemplate> templateFuture;
		private final VoidVolume voidVolume;
		private final long detonationGameTime;
		private final boolean customFire;
		private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		private final BlockPos.MutableBlockPos surfaceScan = new BlockPos.MutableBlockPos();
		private final BlockPos.MutableBlockPos supportScan = new BlockPos.MutableBlockPos();
		private final BlockPos.MutableBlockPos structuralScan = new BlockPos.MutableBlockPos();
		private final int centerX;
		private final int centerY;
		private final int centerZ;
		private ShapeTemplate template;
		private int columnIndex;
		private int surfaceIndex;
		private int currentY = Integer.MIN_VALUE;
		private int currentTopY = Integer.MIN_VALUE;
		private int currentWorldX;
		private int currentWorldZ;
		private Column currentColumn;
		private boolean aftermathStarted;
		private int aftermathX;
		private int aftermathZ;
		private int aftermathStep;
		private int structuralX;
		private int structuralZ;
		private int structuralStep;
		private int structuralCleanupX = Integer.MIN_VALUE;
		private int structuralCleanupZ;
		private int structuralCleanupY;
		private int structuralCleanupEnd;
		private int rotation;
		private boolean mirror;
		private boolean finished;
		private long finishedAt;
		private boolean frontBlockedThisApply;
		/* Set by the bounded heightmap support resolver so snow cleanup counts
		 * against the same surface-work budget as the column that discovered it. */
		private int resolvedSnowChanges;
		private FastExplosion explosionContext;

		private ExplosionWork(
			final UUID warheadId,
			final Vec3 center,
			final StrategicExplosionProfile profile,
			final long seed,
			final CompletableFuture<ShapeTemplate> templateFuture,
			final long gameTime,
			final boolean customFire
		) {
			this.warheadId = warheadId;
			this.center = center;
			this.profile = profile;
			this.seed = seed;
			this.templateFuture = templateFuture;
			this.voidVolume = new VoidVolume(center, profile);
			this.detonationGameTime = gameTime;
			this.customFire = customFire;
			this.centerX = Mth.floor(center.x);
			this.centerY = Mth.floor(center.y);
			this.centerZ = Mth.floor(center.z);
			this.rotation = (int) (mix(seed ^ 0x524F544154494F4EL) & 3L);
			this.mirror = (mix(seed ^ 0x4D4952524F525F34L) & 1L) != 0L;
			this.aftermathStep = profile.yield().nuclear() ? 2 : 1;
			this.structuralStep = profile.horizontalRadius() >= 96.0 ? 2 : 1;
			int radius = Mth.ceil(profile.horizontalRadius() * profile.aftermathRadiusScale());
			this.aftermathX = -radius;
			this.aftermathZ = -radius;
			int structuralRadius = Mth.ceil(profile.horizontalRadius() * 1.08);
			this.structuralX = -structuralRadius;
			this.structuralZ = -structuralRadius;
		}

		private boolean templateReady() {
			return template != null || templateFuture.isDone();
		}

		private boolean craterExcavationComplete() {
			return template != null && columnIndex >= template.columns.length;
		}

		private int apply(
			final ServerLevel level,
			final LevelWork levelWork,
			final int budget,
			final long deadline
		) {
			frontBlockedThisApply = false;
			if (template == null) {
				if (!templateFuture.isDone()) return 0;
				template = templateFuture.join();
			}
			int changed = 0;
			int visited = 0;
			/* Surface damage is released with the visible pressure front, not after the crater is finished. */
			changed += advanceSurfaceWave(level, Math.max(8, budget / 3), deadline);
			while (changed < budget && System.nanoTime() < deadline && !finished) {
				if (columnIndex < template.columns.length) {
					if (currentColumn == null) {
						Column nextColumn = template.columns[columnIndex];
						currentColumn = nextColumn;
						setCurrentWorldColumn(currentColumn.dx, currentColumn.dz);
						int top = currentColumn.topY;
						if (level.getChunkSource().hasChunk(currentWorldX >> 4, currentWorldZ >> 4)) {
							int surfaceOffset = level.getHeight(Heightmap.Types.MOTION_BLOCKING, currentWorldX, currentWorldZ)
								- 1 - centerY;
							top = Math.min(top, Math.max(currentColumn.bottomY, surfaceOffset));
						}
						currentTopY = top;
						currentY = top;
					}
					if (currentY < currentColumn.bottomY) {
						columnIndex++;
						currentColumn = null;
						continue;
					}
					cursor.set(currentWorldX, centerY + currentY, currentWorldZ);
					int yOffset = currentY--;
					boolean topOfColumn = yOffset == currentTopY;
					boolean bottomOfColumn = yOffset == currentColumn.bottomY;
					visited++;
					if (destroyAt(level, levelWork, cursor, currentColumn.radial, yOffset,
						topOfColumn, bottomOfColumn)) changed++;
				} else {
					if (!aftermathStarted) aftermathStarted = true;
					if (advanceAftermath(level)) {
						visited++;
						continue;
					}
					if (!advanceStructuralCleanup(level, deadline)) {
						if (surfaceIndex >= template.surfacePoints.length) {
							finished = true;
							finishedAt = level.getGameTime();
						} else {
							break;
						}
					}
					visited++;
				}
				if ((visited & (TIME_CHECK_INTERVAL - 1)) == 0 && System.nanoTime() >= deadline) break;
			}
			return changed;
		}

		private boolean destroyAt(
			final ServerLevel level,
			final LevelWork levelWork,
			final BlockPos position,
			final double radial,
			final int yOffset,
			final boolean topOfColumn,
			final boolean bottomOfColumn
		) {
			if (!level.isInWorldBounds(position)) return false;
			if (!level.getChunkSource().hasChunk(
				SectionPos.blockToSectionCoord(position.getX()),
				SectionPos.blockToSectionCoord(position.getZ()))) return false;
			long packed = position.asLong();
			if (!levelWork.claim(packed)) return false;
			try {
				BlockState state = level.getBlockState(position);
				FluidState fluid = state.getFluidState();
				if (state.isAir() && fluid.isEmpty()) return false;
				float destroySpeed = state.getDestroySpeed(level, position);
				if (destroySpeed < 0.0F) return false;
				double verticalRadius = yOffset < 0 ? profile.downwardRadius() : profile.upwardRadius();
				double vertical = Math.abs(yOffset) / Math.max(1.0, verticalRadius);
				double normalized = Math.sqrt(Math.min(1.0, radial * radial + vertical * vertical));
				if (normalized > profile.guaranteedVoidScale()) {
					float resistance = Math.max(
						state.getBlock().getExplosionResistance(),
						fluid.getExplosionResistance()
					);
					float threshold = profile.maximumDestroyResistance()
						* (float) Math.max(0.08, 1.0 - normalized * profile.edgeResistanceScale());
					if (resistance > threshold) return false;
				}

				if (state.is(Blocks.TNT)) {
					FastExplosion explosion = explosionContext == null
						? new FastExplosion(level, null, center, profile.entityBlastRadius())
						: explosionContext;
					state.onExplosionHit(level, position, explosion, (stack, dropPosition) -> { });
					return true;
				}
				/* The final block in every nuclear excavation column is the exposed
				 * crater skin. Preserve that one-block shell as fused/charred material
				 * instead of deleting it and relying on a later top-down surface scan,
				 * which cannot see steep crater walls. */
				if (profile.yield().nuclear() && bottomOfColumn) {
					return level.setBlock(position,
						nuclearCraterShell(state, position, normalized), FAST_REMOVE_FLAGS);
				}
				boolean changed = level.setBlock(position, Blocks.AIR.defaultBlockState(), FAST_REMOVE_FLAGS);
				if (changed && topOfColumn) removeUnsupportedAbove(level, position,
					profile.yield().nuclear());
				return changed;
			} finally {
				levelWork.release(packed);
			}
		}

		private BlockState nuclearCraterShell(final BlockState original,
			final BlockPos position, final double normalized) {
			long packed = position.asLong();
			long hash = mix(seed ^ packed ^ 0x4352415445525F53L);
			double selector = unit(hash);
			if (magmaFissure(position, normalized)) {
				return Blocks.MAGMA_BLOCK.defaultBlockState();
			}
			if (original.is(Blocks.SAND)) {
				if (selector < 0.20) return Blocks.TINTED_GLASS.defaultBlockState();
				if (selector < 0.38) return Blocks.STAINED_GLASS.black().defaultBlockState();
				if (selector < 0.56) return Blocks.STAINED_GLASS.gray().defaultBlockState();
				if (selector < 0.78) return Blocks.DYED_TERRACOTTA.white().defaultBlockState();
				return Blocks.SANDSTONE.defaultBlockState();
			}
			if (original.is(Blocks.RED_SAND)) {
				if (selector < 0.28) return Blocks.STAINED_GLASS.black().defaultBlockState();
				if (selector < 0.52) return Blocks.STAINED_GLASS.gray().defaultBlockState();
				if (selector < 0.78) return Blocks.TERRACOTTA.defaultBlockState();
				return Blocks.RED_SANDSTONE.defaultBlockState();
			}
			/* Magma is reserved for the connected fissure field. The remainder forms
				a continuous mottled basalt/deepslate/tuff skin across floor and walls. */
			if (selector < 0.22) return Blocks.BASALT.defaultBlockState();
			if (selector < 0.40) return Blocks.BLACKSTONE.defaultBlockState();
			if (selector < 0.64) return Blocks.DEEPSLATE.defaultBlockState();
			if (selector < 0.84) return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
			return Blocks.TUFF.defaultBlockState();
		}

		private boolean magmaFissure(final BlockPos position, final double normalized) {
			if (normalized > 0.94) return false;
			return NuclearCrackField.contains(seed, center.x, center.z,
				position.getX() + 0.5, position.getZ() + 0.5,
				profile.horizontalRadius() * 0.94);
		}

		private int advanceSurfaceWave(final ServerLevel level, final int budget, final long deadline) {
			if (template == null || surfaceIndex >= template.surfacePoints.length || budget <= 0) return 0;
			if (profile.yield().nuclear()) {
				/* The prepared aftermath wave owns nuclear ground and vegetation.
				 * Advancing this older duplicate path made tree canopies disappear
				 * before the coherent ground transformation reached them. */
				surfaceIndex = template.surfacePoints.length;
				return 0;
			}
			double age = Math.max(0.0, level.getGameTime() - detonationGameTime
				+ (profile.yield().nuclear() ? 1.0 : 0.0));
			if (profile.yield().nuclear()) age *= WarheadVisualMath.NUCLEAR_TIME_SCALE;
			double maximumRadius = Math.max(1.0, profile.horizontalRadius() * profile.aftermathRadiusScale());
			double currentRadius = Math.min(maximumRadius,
				age * WarheadVisualMath.AIR_SHOCKWAVE_SPEED_BLOCKS_PER_TICK);
			int changed = 0;
			int visited = 0;
			int scanHeight = profile.yield().nuclear() ? 22 : 10;
			while (surfaceIndex < template.surfacePoints.length && visited < budget
				&& changed < budget && System.nanoTime() < deadline) {
				SurfacePoint point = template.surfacePoints[surfaceIndex];
				if (point.radial > currentRadius) break;
				surfaceIndex++;
				visited++;
				int worldX = centerX + point.dx;
				int worldZ = centerZ + point.dz;
				if (!level.getChunkSource().hasChunk(worldX >> 4, worldZ >> 4)) continue;
				int groundY = resolveSurfaceSupport(level, worldX, worldZ);
				changed += resolvedSnowChanges;
				if (changed >= budget) break;
				double normalized = point.radial / maximumRadius;
				double pressure = Math.max(0.0, 1.0 - normalized);
				long hash = mix(seed ^ ((long) point.dx << 32) ^ (point.dz & 0xFFFFFFFFL) ^ 0x5355524641434557L);
				for (int up = 1; up <= scanHeight; up++) {
					surfaceScan.set(worldX, groundY + up, worldZ);
					if (!level.isInWorldBounds(surfaceScan)) break;
					BlockState state = level.getBlockState(surfaceScan);
					if (state.isAir()) continue;
					boolean leaves = state.is(BlockTags.LEAVES);
					boolean fragile = isFragileSurface(state);
					boolean glass = state.getBlock().getDescriptionId().contains("glass");
					double removalChance = fragile ? 0.35 + pressure * 0.65
					: leaves ? (profile.yield().nuclear()
						? (customFire ? 0.30 + pressure * 0.48 : 0.18 + pressure * 0.88)
						: pressure * 0.42)
						: glass ? (profile.yield().nuclear() ? pressure * 1.15 : pressure * 0.54) : 0.0;
					if (unit(hash ^ surfaceScan.asLong() ^ up) < Math.min(1.0, removalChance)) {
						if (level.setBlock(surfaceScan, Blocks.AIR.defaultBlockState(), FAST_REMOVE_FLAGS)) changed++;
					}
				}
				surfaceScan.set(worldX, groundY, worldZ);
				if (!level.isInWorldBounds(surfaceScan)) continue;
				BlockState surface = level.getBlockState(surfaceScan);
				if (profile.yield().nuclear() && unit(hash ^ 0x53434F524348L) < pressure * 0.52) {
					if (surface.is(Blocks.GRASS_BLOCK) || surface.is(Blocks.DIRT)
						|| surface.is(Blocks.PODZOL) || surface.is(Blocks.MYCELIUM)) {
						BlockState replacement = pressure > 0.62
							? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.PODZOL.defaultBlockState();
						if (level.setBlock(surfaceScan, replacement, FAST_REMOVE_FLAGS)) changed++;
					} else if (surface.is(Blocks.SAND) || surface.is(Blocks.RED_SAND)) {
						if (level.setBlock(surfaceScan, Blocks.GRAVEL.defaultBlockState(), FAST_REMOVE_FLAGS)) changed++;
					}
					/* The extended prepared shockwave owns coherent fire pockets.
					   Avoid scattering unrelated one-block fires in this earlier pass. */
				}
			}
			return changed;
		}

		private double currentNuclearCraterFront(final ServerLevel level) {
			double age = Math.max(0.0, level.getGameTime() - detonationGameTime + 1.0)
				* WarheadVisualMath.NUCLEAR_TIME_SCALE;
			return Math.min(profile.horizontalRadius(),
				age * WarheadVisualMath.AIR_SHOCKWAVE_SPEED_BLOCKS_PER_TICK);
		}

		private void removeUnsupportedAbove(final ServerLevel level, final BlockPos removedSupport,
			final boolean preserveVegetation) {
			for (int offset = 1; offset <= IMMEDIATE_SUPPORT_SCAN_HEIGHT; offset++) {
				supportScan.set(removedSupport.getX(), removedSupport.getY() + offset, removedSupport.getZ());
				if (!level.isInWorldBounds(supportScan)) break;
				BlockState state = level.getBlockState(supportScan);
				if (state.isAir()) continue;
				if (state.is(BlockTags.LEAVES)) {
					if (preserveVegetation) continue;
					level.setBlock(supportScan, Blocks.AIR.defaultBlockState(), FAST_REMOVE_FLAGS);
					continue;
				}
				if (isFragileSurface(state) || !state.canSurvive(level, supportScan)) {
					level.setBlock(supportScan, Blocks.AIR.defaultBlockState(), FAST_REMOVE_FLAGS);
				}
			}
		}

		/**
		 * Clears only the shallow snow cap reported by the heightmap, then returns
		 * the first durable local support. This prevents floating snow layers while
		 * keeping terrain discovery bounded over tall trees and cliffs.
		 */
		private int resolveSurfaceSupport(final ServerLevel level, final int x, final int z) {
			resolvedSnowChanges = 0;
			int startY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
			int minimumY = level.dimensionType().minY();
			for (int descent = 0; descent <= SURFACE_SUPPORT_DESCENT; descent++) {
				int y = startY - descent;
				if (y < minimumY) break;
				surfaceScan.set(x, y, z);
				if (!level.isInWorldBounds(surfaceScan)) break;
				BlockState state = level.getBlockState(surfaceScan);
				if (isSnowLike(state)) {
					if (level.setBlock(surfaceScan, Blocks.AIR.defaultBlockState(), FAST_REMOVE_FLAGS)) {
						resolvedSnowChanges++;
					}
					continue;
				}
				if (!state.isAir() && state.getFluidState().isEmpty()
					&& !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)
					&& !state.is(BlockTags.PLANKS) && !isFragileSurface(state)) {
					return y;
				}
			}
			return startY;
		}

		private boolean advanceStructuralCleanup(final ServerLevel level, final long deadline) {
			if (structuralCleanupX != Integer.MIN_VALUE) {
				if (!level.getChunkSource().hasChunk(structuralCleanupX >> 4, structuralCleanupZ >> 4)) {
					clearStructuralCleanupCursor();
					return true;
				}
				if (cleanupColumn(level, deadline)) clearStructuralCleanupCursor();
				return true;
			}
			int radius = Mth.ceil(profile.horizontalRadius() * 1.08);
			while (structuralX <= radius) {
				int dx = structuralX;
				int dz = structuralZ;
				structuralZ += structuralStep;
				if (structuralZ > radius) {
					structuralZ = -radius;
					structuralX += structuralStep;
				}
				if ((double) dx * dx + (double) dz * dz > (double) radius * radius) return true;
				int x = centerX + dx;
				int z = centerZ + dz;
				if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) return true;

				int craterFloor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
				int originalBandStart = centerY - 10;
				int scanStart = Math.max(level.dimensionType().minY(), Math.min(craterFloor + 1, originalBandStart));
				int scanEnd = Math.min(
					level.dimensionType().minY() + level.dimensionType().height() - 1,
					Math.max(craterFloor + STRUCTURAL_SCAN_HEIGHT, Mth.floor(center.y + profile.upwardRadius() + 36.0))
				);
				structuralCleanupX = x;
				structuralCleanupZ = z;
				structuralCleanupY = scanStart;
				structuralCleanupEnd = scanEnd;
				if (cleanupColumn(level, deadline)) clearStructuralCleanupCursor();
				return true;
			}
			return false;
		}

		private boolean cleanupColumn(final ServerLevel level, final long deadline) {
			for (; structuralCleanupY <= structuralCleanupEnd; structuralCleanupY++) {
				if ((structuralCleanupY & (TIME_CHECK_INTERVAL - 1)) == 0
					&& System.nanoTime() >= deadline) {
					return false;
				}
				structuralScan.set(structuralCleanupX, structuralCleanupY, structuralCleanupZ);
				BlockState state = level.getBlockState(structuralScan);
				if (state.isAir()) continue;
				if (state.is(BlockTags.LEAVES)) {
					level.setBlock(structuralScan, Blocks.AIR.defaultBlockState(), FAST_REMOVE_FLAGS);
					continue;
				}
				if (isFragileSurface(state) || !state.canSurvive(level, structuralScan)) {
					level.setBlock(structuralScan, Blocks.AIR.defaultBlockState(), FAST_REMOVE_FLAGS);
				}
			}
			return true;
		}

		private void clearStructuralCleanupCursor() {
			structuralCleanupX = Integer.MIN_VALUE;
		}

		private static boolean isFragileSurface(final BlockState state) {
			return state.is(BlockTags.FLOWERS)
				|| state.is(Blocks.SHORT_GRASS)
				|| state.is(Blocks.TALL_GRASS)
				|| state.is(Blocks.FERN)
				|| state.is(Blocks.LARGE_FERN)
				|| state.is(Blocks.DEAD_BUSH)
				|| isSnowLike(state)
				|| state.is(Blocks.VINE)
				|| state.is(Blocks.BROWN_MUSHROOM)
				|| state.is(Blocks.RED_MUSHROOM);
		}

		private static boolean isSnowLike(final BlockState state) {
			return state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK);
		}

		private boolean advanceAftermath(final ServerLevel level) {
			int radius = Mth.ceil(profile.horizontalRadius() * profile.aftermathRadiusScale());
			/* Nuclear surface/vegetation transformation is prepared during flight and
			 * drained in large bounded slices by WarheadGlassShockwaveManager as the
			 * impact flash begins. Retaining this older second pass would overwrite
			 * that thermal result. */
			if (profile.yield().nuclear()) {
				aftermathX = radius + 1;
				return false;
			}
			while (aftermathX <= radius) {
				int dx = aftermathX;
				int dz = aftermathZ;
				aftermathZ += aftermathStep;
				if (aftermathZ > radius) {
					aftermathZ = -radius;
					aftermathX += aftermathStep;
				}
				double distance = Math.sqrt(dx * dx + dz * dz);
				if (distance < profile.horizontalRadius() * 0.72 || distance > radius) return true;
				double chance = profile.aftermathDensity()
					* (1.0 - (distance - profile.horizontalRadius() * 0.72)
					/ Math.max(1.0, radius - profile.horizontalRadius() * 0.72));
				long hash = seed ^ ((long) dx << 32) ^ (dz & 0xFFFFFFFFL) ^ 0x41465445524D4154L;
				if (unit(hash) > chance) return true;
				int x = centerX + dx;
				int z = centerZ + dz;
				if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) return true;
				int groundY = resolveSurfaceSupport(level, x, z);
				cursor.set(x, groundY, z);
				if (!level.isInWorldBounds(cursor)) return true;
				BlockState surface = level.getBlockState(cursor);
				if (surface.is(Blocks.GRASS_BLOCK) || surface.is(Blocks.DIRT)
					|| surface.is(Blocks.PODZOL) || surface.is(Blocks.MYCELIUM)) {
					level.setBlock(cursor, Blocks.COARSE_DIRT.defaultBlockState(), FAST_REMOVE_FLAGS);
				} else if (surface.is(Blocks.SAND) || surface.is(Blocks.RED_SAND)) {
					level.setBlock(cursor, Blocks.GRAVEL.defaultBlockState(), FAST_REMOVE_FLAGS);
				} else if (surface.is(Blocks.SNOW) || surface.is(Blocks.SNOW_BLOCK)) {
					level.setBlock(cursor, Blocks.AIR.defaultBlockState(), FAST_REMOVE_FLAGS);
				}
				/* Remove foliage above the surviving surface, leaving stripped silhouettes. */
				for (int up = 1; up <= 12; up++) {
					cursor.set(x, groundY + up, z);
					if (!level.isInWorldBounds(cursor)) break;
					BlockState state = level.getBlockState(cursor);
					if (state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS)) {
						boolean retainCrown = customFire && state.is(BlockTags.LEAVES)
							&& unit(hash ^ cursor.asLong() ^ 0x43524F574E5F4B50L) < 0.30;
						if (!retainCrown) {
							level.setBlock(cursor, Blocks.AIR.defaultBlockState(), FAST_REMOVE_FLAGS);
						}
					}
				}
				if (profile.yield().nuclear() && unit(hash ^ 0x464952455F504F53L) < 0.025) {
					cursor.set(x, groundY + 1, z);
					if (level.isInWorldBounds(cursor) && level.getBlockState(cursor).isAir()) {
						WarheadFirePlacement.placeAbove(level, cursor.below(), customFire,
							Mth.clamp(0.68F + profile.yield().visualScale() * 0.08F, 0.10F, 1.0F),
							hash ^ 0x464952455F504F53L,
							Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
					}
				}
				return true;
			}
			return false;
		}

		private void setCurrentWorldColumn(final int x, final int z) {
			int tx = mirror ? -x : x;
			int transformedX;
			int transformedZ;
			switch (rotation) {
				case 1 -> { transformedX = -z; transformedZ = tx; }
				case 2 -> { transformedX = -tx; transformedZ = -z; }
				case 3 -> { transformedX = z; transformedZ = -tx; }
				default -> { transformedX = tx; transformedZ = z; }
			}
			currentWorldX = centerX + transformedX;
			currentWorldZ = centerZ + transformedZ;
		}

		private List<WarheadExplosionDropContext.DestroyedBlock> sampleInitialDebris(final ServerLevel level) {
			int target = Math.min(MAX_DEBRIS_SAMPLE, profile.yield().maximumDebris());
			ArrayList<WarheadExplosionDropContext.DestroyedBlock> result = new ArrayList<>(target);
			LongOpenHashSet sampled = new LongOpenHashSet();
			SplittableRandom random = new SplittableRandom(seed ^ 0x4445425249535F36L);
			int patchRadius = profile.yield().nuclear() ? 2 : profile.yield() == WarheadYield.HEAVY_CONVENTIONAL ? 2 : 1;
			int roots = Math.max(8, target / Math.max(4, (patchRadius * 2 + 1) * 3));
			for (int rootIndex = 0; rootIndex < roots && result.size() < target; rootIndex++) {
				double angle = random.nextDouble(0.0, Math.PI * 2.0);
				double radius = Math.sqrt(random.nextDouble()) * profile.horizontalRadius();
				int rootX = Mth.floor(center.x + Math.cos(angle) * radius);
				int rootZ = Mth.floor(center.z + Math.sin(angle) * radius);
				if (!level.getChunkSource().hasChunk(rootX >> 4, rootZ >> 4)) continue;
				int rootY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, rootX, rootZ) - 1;
				/* Capture real adjacent roof/tree/terrain states so the visual fragment preserves the struck structure. */
				for (int dy = 2; dy >= -patchRadius && result.size() < target; dy--) {
					for (int dz = -patchRadius; dz <= patchRadius && result.size() < target; dz++) {
						for (int dx = -patchRadius; dx <= patchRadius && result.size() < target; dx++) {
							if (Math.abs(dx) + Math.abs(dz) > patchRadius + 1) continue;
							cursor.set(rootX + dx, rootY + dy, rootZ + dz);
							if (!level.isInWorldBounds(cursor)) continue;
							long packed = cursor.asLong();
							if (!sampled.add(packed)) continue;
							BlockState state = level.getBlockState(cursor);
							if (!state.isAir()) result.add(new WarheadExplosionDropContext.DestroyedBlock(BlockPos.of(packed), state));
						}
					}
				}
			}
			return List.copyOf(result);
		}
	}

	private static final class ShapeTemplate {
		private final Column[] columns;
		private final SurfacePoint[] surfacePoints;

		private ShapeTemplate(final Column[] columns, final SurfacePoint[] surfacePoints) {
			this.columns = columns;
			this.surfacePoints = surfacePoints;
		}

		private static ShapeTemplate create(final StrategicExplosionProfile profile) {
			int radius = Mth.ceil(profile.horizontalRadius());
			ArrayList<Column> columns = new ArrayList<>((radius * 2 + 1) * (radius * 2 + 1));
			for (int x = -radius; x <= radius; x++) {
				for (int z = -radius; z <= radius; z++) {
					double radial = Math.sqrt((x * x + z * z)
						/ (profile.horizontalRadius() * profile.horizontalRadius()));
					if (radial > 1.0) continue;
					double angle = Math.atan2(z, x);
					double broadNoise = Math.sin(angle * 5.0 + profile.yield().ordinal() * 1.7) * 0.48
						+ Math.sin(angle * 11.0 - radial * 8.0) * 0.22
						+ (unit(((long) x << 32) ^ (z & 0xFFFFFFFFL) ^ profile.yield().ordinal()) - 0.5) * 0.30;
					double roughScale = 1.0 + broadNoise * profile.boundaryRoughness();
					double adjusted = radial / roughScale;
					if (adjusted > 1.0) continue;
					double verticalFactor = Math.sqrt(Math.max(0.0, 1.0 - adjusted * adjusted));
					int bottom = -Math.max(1, (int) Math.floor(profile.downwardRadius() * verticalFactor));
					int top = Math.max(1, (int) Math.floor(profile.upwardRadius() * verticalFactor));
					columns.add(new Column(x, z, bottom, top, adjusted));
				}
			}
			Column[] array = columns.toArray(Column[]::new);
			Arrays.sort(array, Comparator.comparingDouble(column -> column.radial));
			int surfaceRadius = Mth.ceil(profile.horizontalRadius() * profile.aftermathRadiusScale());
			int surfaceStep = profile.yield().nuclear() && surfaceRadius > 80 ? 2 : 1;
			ArrayList<SurfacePoint> surface = new ArrayList<>();
			for (int x = -surfaceRadius; x <= surfaceRadius; x += surfaceStep) {
				for (int z = -surfaceRadius; z <= surfaceRadius; z += surfaceStep) {
					double radial = Math.sqrt((double) x * x + (double) z * z);
					if (radial <= surfaceRadius) surface.add(new SurfacePoint(x, z, radial));
				}
			}
			SurfacePoint[] surfaceArray = surface.toArray(SurfacePoint[]::new);
			Arrays.sort(surfaceArray, Comparator.comparingDouble(point -> point.radial));
			return new ShapeTemplate(array, surfaceArray);
		}
	}

	private record Column(int dx, int dz, int bottomY, int topY, double radial) { }
	private record SurfacePoint(int dx, int dz, double radial) { }

	private static final class VoidVolume {
		private final Vec3 center;
		private final double horizontalRadius;
		private final double upwardRadius;
		private final double downwardRadius;
		private final int minimumChunkX;
		private final int maximumChunkX;
		private final int minimumChunkZ;
		private final int maximumChunkZ;

		private VoidVolume(final Vec3 center, final StrategicExplosionProfile profile) {
			this.center = center;
			this.horizontalRadius = profile.guaranteedHorizontalRadius();
			this.upwardRadius = profile.guaranteedUpwardRadius();
			this.downwardRadius = profile.guaranteedDownwardRadius();
			this.minimumChunkX = Mth.floor(center.x - horizontalRadius) >> 4;
			this.maximumChunkX = Mth.floor(center.x + horizontalRadius) >> 4;
			this.minimumChunkZ = Mth.floor(center.z - horizontalRadius) >> 4;
			this.maximumChunkZ = Mth.floor(center.z + horizontalRadius) >> 4;
		}

		private boolean contains(final double x, final double y, final double z) {
			double dx = x - center.x;
			double dy = y - center.y;
			double dz = z - center.z;
			double verticalRadius = dy < 0.0 ? downwardRadius : upwardRadius;
			return (dx * dx + dz * dz) / (horizontalRadius * horizontalRadius)
				+ (dy * dy) / (verticalRadius * verticalRadius) <= 1.0;
		}
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

		@Override public ServerLevel level() { return level; }
		@Override public BlockInteraction getBlockInteraction() { return BlockInteraction.DESTROY; }
		@Override public @Nullable LivingEntity getIndirectSourceEntity() { return Explosion.getIndirectSourceEntity(source); }
		@Override public @Nullable Entity getDirectSourceEntity() { return source; }
		@Override public float radius() { return radius; }
		@Override public Vec3 center() { return center; }
		@Override public boolean canTriggerBlocks() { return false; }
		@Override public boolean shouldAffectBlocklikeEntities() { return true; }
	}
}
