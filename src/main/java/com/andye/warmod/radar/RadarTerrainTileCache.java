package com.andye.warmod.radar;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;

public final class RadarTerrainTileCache {
	private static final int MAX_TILES=4096;
	private final Map<Long,RadarTerrainTile> tiles=new LinkedHashMap<>(256,.75F,true){@Override protected boolean removeEldestEntry(final Map.Entry<Long,RadarTerrainTile> e){return size()>MAX_TILES;}};
	public synchronized RadarTerrainTile get(final ServerLevel level,final int x,final int z){long key=((long)x<<32)^(z&0xffffffffL);RadarTerrainTile tile=tiles.get(key);if(tile==null){tile=RadarTerrainService.sampleLoaded(level,x,z);if(tile!=null)tiles.put(key,tile);}return tile;}
	public synchronized void clear(){tiles.clear();}
}
