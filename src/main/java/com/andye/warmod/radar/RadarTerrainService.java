package com.andye.warmod.radar;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

public final class RadarTerrainService {
	private RadarTerrainService() { }
	public static RadarTerrainTile sampleLoaded(final ServerLevel level,final int chunkX,final int chunkZ){
		if(!level.getChunkSource().hasChunk(chunkX,chunkZ))return null;
		int[] colours=new int[64];short[] heights=new short[64];
		for(int z=0;z<8;z++)for(int x=0;x<8;x++){int wx=(chunkX<<4)+x*2+1,wz=(chunkZ<<4)+z*2+1;
			int y=level.getHeight(Heightmap.Types.WORLD_SURFACE,wx,wz)-1;BlockPos pos=new BlockPos(wx,y,wz);
			int index=z*8+x;int base=level.getBlockState(pos).getMapColor(level,pos).col;
			int shade=Math.max(-24,Math.min(24,(y-level.dimensionType().minY())/16-8));
			int r=Math.max(0,Math.min(255,((base>>16)&255)+shade)),g=Math.max(0,Math.min(255,((base>>8)&255)+shade)),b=Math.max(0,Math.min(255,(base&255)+shade));
			colours[index]=0xff000000|(r<<16)|(g<<8)|b;heights[index]=(short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,y));}
		return new RadarTerrainTile(chunkX,chunkZ,colours,heights);
	}
	public static List<RadarTerrainTile> loadedTiles(final ServerLevel level,final RadarTerrainTileCache cache,final int[] coordinates){
		List<RadarTerrainTile> result=new ArrayList<>();for(int i=0;i+1<coordinates.length&&result.size()<128;i+=2){RadarTerrainTile tile=cache.get(level,coordinates[i],coordinates[i+1]);if(tile!=null)result.add(tile);}return List.copyOf(result);
	}
}
