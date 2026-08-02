package com.andye.warmod.radar.client;

import com.andye.warmod.radar.RadarTerrainTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

public final class ClientRadarTerrainSampler {
	private ClientRadarTerrainSampler() { }
	public static void sampleAroundPlayer(final Minecraft client, final ClientRadarTerrainCache cache, final int radius) {
		if (client.level == null || client.player == null) return;
		int centerX = ((int)Math.floor(client.player.getX())) >> 4;
		int centerZ = ((int)Math.floor(client.player.getZ())) >> 4;
		for (int x = centerX - radius; x <= centerX + radius; x++) for (int z = centerZ - radius; z <= centerZ + radius; z++) {
			long key = ((long)x << 32) ^ (z & 0xffffffffL);
			if (cache.contains(key)) continue;
			LevelChunk chunk = client.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
			if (chunk != null) cache.putLocal(sample(client.level, chunk, x, z));
		}
	}
	private static RadarTerrainTile sample(final ClientLevel level, final LevelChunk chunk, final int chunkX, final int chunkZ) {
		int[] colours = new int[64]; short[] heights = new short[64];
		for (int z = 0; z < 8; z++) for (int x = 0; x < 8; x++) {
			int worldX = (chunkX << 4) + x * 2 + 1, worldZ = (chunkZ << 4) + z * 2 + 1;
			int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, worldX & 15, worldZ & 15) - 1;
			BlockPos position = new BlockPos(worldX, y, worldZ);
			int base = chunk.getBlockState(position).getMapColor(level, position).col;
			int shade = Math.max(-24, Math.min(24, (y - level.dimensionType().minY()) / 16 - 8));
			int red = Math.max(0, Math.min(255, ((base >> 16) & 255) + shade));
			int green = Math.max(0, Math.min(255, ((base >> 8) & 255) + shade));
			int blue = Math.max(0, Math.min(255, (base & 255) + shade));
			int index = z * 8 + x; colours[index] = 0xff000000 | (red << 16) | (green << 8) | blue;
			heights[index] = (short)Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, y));
		}
		return new RadarTerrainTile(chunkX, chunkZ, colours, heights);
	}
}