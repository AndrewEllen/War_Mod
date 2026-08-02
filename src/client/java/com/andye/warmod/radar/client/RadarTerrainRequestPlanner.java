package com.andye.warmod.radar.client;

import com.andye.warmod.radar.client.gui.RadarMapTransform;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

public final class RadarTerrainRequestPlanner {
	private static final int MAX_BATCH = 128;
	private static final long RETRY_TICKS = 200L;
	private static final int MANAGEABLE_VISIBLE_CHUNKS = 4096;
	private final Map<Identifier, DimensionState> dimensions = new HashMap<>();
	private boolean offAtScale;
	private boolean boundedAnchors;

	public int[] plan(final Minecraft client, final ClientRadarState radar, final RadarMapTransform transform,
		final int left, final int top, final int width, final int height, final double now) {
		if (client.level == null || client.player == null || radar.dimensionId() == null || transform.blocksPerPixel() > 8.0) {
			offAtScale = transform.blocksPerPixel() > 8.0;
			return new int[0];
		}
		offAtScale = false;
		DimensionState state = dimensions.computeIfAbsent(radar.dimensionId(), ignored -> new DimensionState());
		long tick = client.level.getGameTime();
		state.pending.removeIf(key -> {
			if (radar.terrainCache().contains(key)) return true;
			Long requested = state.lastRequestTick.get(key);
			return requested == null || tick < requested || tick - requested >= RETRY_TICKS;
		});
		int minimumX = (int)Math.floor(transform.worldX(left, left, width) / 16.0) - 1;
		int maximumX = (int)Math.floor(transform.worldX(left + width, left, width) / 16.0) + 1;
		int minimumZ = (int)Math.floor(transform.worldZ(top, top, height) / 16.0) - 1;
		int maximumZ = (int)Math.floor(transform.worldZ(top + height, top, height) / 16.0) + 1;
		Bounds bounds = new Bounds(minimumX, minimumZ, maximumX, maximumZ);
		if (!bounds.equals(state.visibleBounds)) { state.visibleBounds = bounds; state.generation++; }
		long visibleCount = (long)(maximumX - minimumX + 1) * (maximumZ - minimumZ + 1);
		boundedAnchors = visibleCount > MANAGEABLE_VISIBLE_CHUNKS;
		LinkedHashSet<ChunkPos> candidates = new LinkedHashSet<>();
		addWindow(candidates, chunk(client.player.position()), 16);
		ClientRadarTrack selected = radar.selected();
		if (selected != null) addWindow(candidates, chunk(selected.position(now)), 8);
		for (ClientRadarTrack track : radar.tracks()) addWindow(candidates, chunk(track.position(now)), 4);
		for (ClientRadarTrack track : radar.tracks()) addWindow(candidates, chunk(track.target()), 4);
		for (ClientRadarTrack track : radar.tracks()) addWindow(candidates, chunk(track.launch()), 4);
		if (!boundedAnchors) {
			List<ChunkPos> visible = new ArrayList<>((int)visibleCount);
			for (int z = minimumZ; z <= maximumZ; z++) for (int x = minimumX; x <= maximumX; x++) visible.add(new ChunkPos(x, z));
			double centerX = transform.centerWorldX() / 16.0, centerZ = transform.centerWorldZ() / 16.0;
			visible.sort(Comparator.comparingDouble(position -> distanceSquared(position, centerX, centerZ)));
			candidates.addAll(visible);
		}
		state.anchorPositions = Set.copyOf(candidates);
		int[] coordinates = new int[MAX_BATCH * 2];
		int count = 0;
		for (ChunkPos position : candidates) {
			long key = position.pack();
			if (radar.terrainCache().contains(key) || state.pending.contains(key)) continue;
			Long last = state.lastRequestTick.get(key);
			if (last != null && tick >= last && tick - last < RETRY_TICKS) continue;
			coordinates[count * 2] = position.x(); coordinates[count * 2 + 1] = position.z();
			state.pending.add(key); state.lastRequestTick.put(key, tick);
			if (++count >= MAX_BATCH) break;
		}
		if (count == MAX_BATCH) return coordinates;
		return java.util.Arrays.copyOf(coordinates, count * 2);
	}

	public String status(final ClientRadarTerrainCache cache) {
		if (offAtScale) return "Terrain: Off at this scale";
		int pending = dimensions.values().stream().mapToInt(state -> state.pending.size()).sum();
		if (cache.size() == 0 && pending > 0) return "Terrain: Loading";
		if (cache.size() > 0 && cache.serverCount() == 0) return "Terrain: Local only";
		if (boundedAnchors || pending > 0) return "Terrain: Partial";
		return "Terrain: " + cache.size() + " tiles";
	}

	public void clear() { dimensions.clear(); offAtScale = false; boundedAnchors = false; }
	private static void addWindow(final Set<ChunkPos> result, final ChunkPos center, final int radius) {
		for (int x = center.x() - radius; x <= center.x() + radius; x++)
			for (int z = center.z() - radius; z <= center.z() + radius; z++) result.add(new ChunkPos(x, z));
	}
	private static ChunkPos chunk(final Vec3 position) { return new ChunkPos((int)Math.floor(position.x) >> 4, (int)Math.floor(position.z) >> 4); }
	private static double distanceSquared(final ChunkPos position, final double x, final double z) {
		double dx = position.x() - x, dz = position.z() - z; return dx * dx + dz * dz;
	}
	private static final class DimensionState {
		final Set<Long> pending = new LinkedHashSet<>();
		final Map<Long, Long> lastRequestTick = new HashMap<>();
		long generation;
		Bounds visibleBounds;
		Set<ChunkPos> anchorPositions = Set.of();
	}
	private record Bounds(int minimumX, int minimumZ, int maximumX, int maximumZ) { }
}