package com.andye.warmod.icbm;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/** Shared reference counts for every ICBM ticket owner. */
public final class IcbmChunkTicketRegistry {
	private static final Map<ServerLevel, Map<ChunkPos, Integer>> REFERENCES = new WeakHashMap<>();
	private IcbmChunkTicketRegistry() { }

	public static synchronized void acquire(final ServerLevel level, final ChunkPos position) {
		Map<ChunkPos, Integer> references = REFERENCES.computeIfAbsent(level, ignored -> new HashMap<>());
		int count = references.getOrDefault(position, 0);
		if (count == 0) level.getChunkSource().addTicketWithRadius(IcbmChunkTicketType.ICBM, position, 0);
		references.put(position, count + 1);
	}

	public static synchronized void release(final ServerLevel level, final ChunkPos position) {
		Map<ChunkPos, Integer> references = REFERENCES.get(level);
		if (references == null) return;
		int count = references.getOrDefault(position, 0);
		if (count <= 1) {
			references.remove(position);
			level.getChunkSource().removeTicketWithRadius(IcbmChunkTicketType.ICBM, position, 0);
		} else references.put(position, count - 1);
		if (references.isEmpty()) REFERENCES.remove(level);
	}

	public static void acquireAll(final ServerLevel level, final Set<ChunkPos> positions) {
		for (ChunkPos position : positions) acquire(level, position);
	}

	public static void releaseAll(final ServerLevel level, final Set<ChunkPos> positions) {
		for (ChunkPos position : positions) release(level, position);
	}

	public static ChunkPos chunk(final Vec3 position) {
		return new ChunkPos((int)Math.floor(position.x) >> 4, (int)Math.floor(position.z) >> 4);
	}

	public static Set<ChunkPos> window(final ChunkPos center, final int radius) {
		Set<ChunkPos> positions = new HashSet<>();
		addWindow(positions, center, radius);
		return positions;
	}

	public static void addWindow(final Set<ChunkPos> positions, final ChunkPos center, final int radius) {
		for (int x = center.x() - radius; x <= center.x() + radius; x++)
			for (int z = center.z() - radius; z <= center.z() + radius; z++) positions.add(new ChunkPos(x, z));
	}

	public static boolean allLoaded(final ServerLevel level, final Set<ChunkPos> chunks) { for (ChunkPos chunk : chunks) if (!level.getChunkSource().hasChunk(chunk.x(), chunk.z())) return false; return true; }
	public static void addSegmentWindow(final Set<ChunkPos> output, final Vec3 from, final Vec3 to, final int radius, final double spacing) { if(output==null||from==null||to==null||!from.isFinite()||!to.isFinite()||radius<0||!Double.isFinite(spacing)||spacing<=0)throw new IllegalArgumentException("Invalid rolling ticket segment");double dx=to.x-from.x,dz=to.z-from.z;int steps=Math.max(1,(int)Math.ceil(Math.max(Math.abs(dx),Math.abs(dz))/spacing));for(int index=0;index<=steps;index++){double fraction=index/(double)steps;addWindow(output,chunk(new Vec3(from.x+dx*fraction,from.y,from.z+dz*fraction)),radius);} }}