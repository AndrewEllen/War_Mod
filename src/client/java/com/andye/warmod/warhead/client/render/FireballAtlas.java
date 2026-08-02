package com.andye.warmod.warhead.client.render;

public final class FireballAtlas {
	public static final int FRAME_COUNT=8,ATLAS_WIDTH=512,FRAME_WIDTH=64;
	private static final float HALF_TEXEL_U=.5F/ATLAS_WIDTH;
	private FireballAtlas(){}
	public static Uv frame(final double impactAge,final FireballLobe lobe,final int coolingEndTick){double localAge=Math.max(0,impactAge-lobe.spawnDelayTicks());double progress=Math.max(0,Math.min(.999999,localAge/Math.max(1.0,coolingEndTick-lobe.spawnDelayTicks())));int base=(int)Math.floor(progress*FRAME_COUNT);int frame=Math.max(0,Math.min(FRAME_COUNT-1,base+(lobe.animationOffset()&1)));float u0=frame/(float)FRAME_COUNT+HALF_TEXEL_U,u1=(frame+1)/(float)FRAME_COUNT-HALF_TEXEL_U;return new Uv(u0,u1,0,1);}
	public record Uv(float u0,float u1,float v0,float v1){}
}