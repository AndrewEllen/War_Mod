package com.andye.warmod.radar;

import java.util.Arrays;

public record RadarTerrainTile(int chunkX, int chunkZ, int[] colours, short[] heights) {
	public static final int SAMPLE_COUNT = 64;
	public RadarTerrainTile { if (colours.length != SAMPLE_COUNT || heights.length != SAMPLE_COUNT) throw new IllegalArgumentException("8x8 tile required"); colours=Arrays.copyOf(colours,SAMPLE_COUNT);heights=Arrays.copyOf(heights,SAMPLE_COUNT); }
	@Override public int[] colours(){return Arrays.copyOf(colours,SAMPLE_COUNT);}
	@Override public short[] heights(){return Arrays.copyOf(heights,SAMPLE_COUNT);}
	public long key(){return ((long)chunkX<<32)^(chunkZ&0xffffffffL);}
}
