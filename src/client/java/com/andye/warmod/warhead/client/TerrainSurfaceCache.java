package com.andye.warmod.warhead.client;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Shared client-side surface cache for all impact effects.
 *
 * <p>The old implementation repeated heightmap, collision and block-state work
 * for every impact and every visual layer. This cache stores one result per
 * horizontal block column and shares it between overlapping explosions.</p>
 */
public final class TerrainSurfaceCache {
	public static final TerrainSurfaceCache INSTANCE = new TerrainSurfaceCache();

	private static final int MAX_CACHED_CHUNKS = 768;
	private static final long MAX_CHUNK_AGE_TICKS = 40L;
	private static final long PRUNE_INTERVAL_TICKS = 20L;

	private final LinkedHashMap<Long, CachedChunk> chunks = new LinkedHashMap<>(128, 0.75F, true);
	private ClientLevel activeLevel;
	private long gameTime = Long.MIN_VALUE;
	private long lastPruneTime = Long.MIN_VALUE;

	private TerrainSurfaceCache() {
	}

	public synchronized void beginTick(final ClientLevel level, final long currentGameTime) {
		if (level == null) {
			this.clear();
			return;
		}
		if (this.activeLevel != level) {
			this.chunks.clear();
			this.activeLevel = level;
			this.lastPruneTime = Long.MIN_VALUE;
		}
		this.gameTime = currentGameTime;
		if (this.lastPruneTime == Long.MIN_VALUE || currentGameTime - this.lastPruneTime >= PRUNE_INTERVAL_TICKS) {
			this.prune(currentGameTime);
			this.lastPruneTime = currentGameTime;
		}
	}

	public synchronized SurfaceSample sample(final ClientLevel level, final double x, final double z) {
		if (level == null || !Double.isFinite(x) || !Double.isFinite(z)) return null;
		if (this.activeLevel != level) this.beginTick(level, level.getGameTime());

		int blockX = Mth.floor(x);
		int blockZ = Mth.floor(z);
		int chunkX = SectionPos.blockToSectionCoord(blockX);
		int chunkZ = SectionPos.blockToSectionCoord(blockZ);
		long key = chunkKey(chunkX, chunkZ);
		CachedChunk cached = this.chunks.get(key);
		if (cached == null || this.gameTime - cached.sampledAt > MAX_CHUNK_AGE_TICKS) {
			ChunkAccess chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
			if (chunk == null) return null;
			cached = new CachedChunk(chunk, this.gameTime);
			this.chunks.put(key, cached);
			this.trimToCapacity();
		}

		int localX = blockX & 15;
		int localZ = blockZ & 15;
		int index = localZ << 4 | localX;
		if (!cached.sampled[index]) {
			cached.samples[index] = calculate(level, cached.chunk, blockX, blockZ, x, z);
			cached.sampled[index] = true;
		}
		return cached.samples[index];
	}

	public synchronized void clear() {
		this.chunks.clear();
		this.activeLevel = null;
		this.gameTime = Long.MIN_VALUE;
		this.lastPruneTime = Long.MIN_VALUE;
	}

	private static SurfaceSample calculate(final ClientLevel level, final ChunkAccess chunk,
		final int blockX, final int blockZ, final double exactX, final double exactZ) {
		int height = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ) - 1;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int y = height + 2; y >= height - 5; y--) {
			cursor.set(blockX, y, blockZ);
			BlockState state = level.getBlockState(cursor);
			if (state.isAir() || !state.getFluidState().isEmpty()) continue;
			VoxelShape shape = state.getCollisionShape(level, cursor);
			if (shape.isEmpty()) continue;
			double top = shape.max(Direction.Axis.Y);
			if (!Double.isFinite(top) || top <= 0.0) continue;
			BlockPos immutable = cursor.immutable();
			return new SurfaceSample(
				new Vec3(exactX, y + top + 0.08, exactZ),
				immutable,
				state,
				state.getMapColor(level, immutable).col
			);
		}
		return null;
	}

	private void prune(final long currentGameTime) {
		Iterator<Map.Entry<Long, CachedChunk>> iterator = this.chunks.entrySet().iterator();
		while (iterator.hasNext()) {
			CachedChunk chunk = iterator.next().getValue();
			if (currentGameTime - chunk.sampledAt > MAX_CHUNK_AGE_TICKS) iterator.remove();
		}
	}

	private void trimToCapacity() {
		while (this.chunks.size() > MAX_CACHED_CHUNKS) {
			Iterator<Long> iterator = this.chunks.keySet().iterator();
			if (!iterator.hasNext()) return;
			iterator.next();
			iterator.remove();
		}
	}

	private static long chunkKey(final int chunkX, final int chunkZ) {
		return ((long) chunkX << 32) ^ (chunkZ & 0xFFFF_FFFFL);
	}

	private static final class CachedChunk {
		private final ChunkAccess chunk;
		private final long sampledAt;
		private final SurfaceSample[] samples = new SurfaceSample[256];
		private final boolean[] sampled = new boolean[256];

		private CachedChunk(final ChunkAccess chunk, final long sampledAt) {
			this.chunk = chunk;
			this.sampledAt = sampledAt;
		}
	}

	public record SurfaceSample(Vec3 position, BlockPos surfaceBlock, BlockState surfaceState, int tintColor) {
	}
}
