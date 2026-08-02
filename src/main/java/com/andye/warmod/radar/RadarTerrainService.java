package com.andye.warmod.radar;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

public final class RadarTerrainService {
	private RadarTerrainService() { }
	public static RadarTerrainTile sampleLoaded(final ServerLevel level, final int chunkX, final int chunkZ) {
		LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
		if (chunk == null) return null;
		int[] colours = new int[64]; short[] heights = new short[64];
		for (int z = 0; z < 8; z++) for (int x = 0; x < 8; x++) {
			int worldX = (chunkX << 4) + x * 2 + 1, worldZ = (chunkZ << 4) + z * 2 + 1;
			int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, worldX & 15, worldZ & 15) - 1;
			BlockPos position = new BlockPos(worldX, y, worldZ);
			int index = z * 8 + x;
			int base = chunk.getBlockState(position).getMapColor(level, position).col;
			int shade = Math.max(-24, Math.min(24, (y - level.dimensionType().minY()) / 16 - 8));
			int red = Math.max(0, Math.min(255, ((base >> 16) & 255) + shade));
			int green = Math.max(0, Math.min(255, ((base >> 8) & 255) + shade));
			int blue = Math.max(0, Math.min(255, (base & 255) + shade));
			colours[index] = 0xff000000 | (red << 16) | (green << 8) | blue;
			heights[index] = (short)Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, y));
		}
		return new RadarTerrainTile(chunkX, chunkZ, colours, heights);
	}
	public static List<RadarTerrainTile> loadedTiles(final ServerLevel level, final RadarTerrainTileCache cache,
		final int[] coordinates) {
		List<RadarTerrainTile> result = new ArrayList<>();
		for (int index = 0; index + 1 < coordinates.length && result.size() < 128; index += 2) {
			RadarTerrainTile tile = cache.get(level, coordinates[index], coordinates[index + 1]);
			if (tile != null) result.add(tile);
		}
		return List.copyOf(result);
	}
}