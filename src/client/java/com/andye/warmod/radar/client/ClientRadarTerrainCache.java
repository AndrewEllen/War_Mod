package com.andye.warmod.radar.client;

import com.andye.warmod.radar.RadarTerrainTile;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClientRadarTerrainCache {
	private final Map<Long, RadarTerrainTile> tiles = new LinkedHashMap<>(256, .75F, true) {
		@Override protected boolean removeEldestEntry(final Map.Entry<Long, RadarTerrainTile> entry) {
			if (size() <= 8192) return false;
			localKeys.remove(entry.getKey());
			return true;
		}
	};
	private final Set<Long> localKeys = new HashSet<>();
	public void putAll(final Collection<RadarTerrainTile> values) {
		for (RadarTerrainTile tile : values) { tiles.put(tile.key(), tile); localKeys.remove(tile.key()); }
	}
	public void putLocal(final RadarTerrainTile tile) { if (!tiles.containsKey(tile.key())) { tiles.put(tile.key(), tile); localKeys.add(tile.key()); } }
	public boolean contains(final long key) { return tiles.containsKey(key); }
	public Collection<RadarTerrainTile> values() { return List.copyOf(tiles.values()); }
	public Set<Long> keys() { return Set.copyOf(tiles.keySet()); }
	public int size() { return tiles.size(); }
	public int localCount() { return localKeys.size(); }
	public int serverCount() { return Math.max(0, tiles.size() - localKeys.size()); }
	public void clear() { tiles.clear(); localKeys.clear(); }
}