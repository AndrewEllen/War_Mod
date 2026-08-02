package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadPayloadType;

public final class FireballAtlas {
	public static final int FRAME_COUNT=8,ATLAS_WIDTH=512,FRAME_WIDTH=64,ATLAS_HEIGHT=64;
	private static final float HALF_TEXEL_U=.5F/ATLAS_WIDTH,HALF_TEXEL_V=.5F/ATLAS_HEIGHT;
	private FireballAtlas(){}
	public static Uv frame(final double impactAge,final FireballLobe lobe,final WarheadPayloadType payloadType){
		double localAge=Math.max(0,impactAge-lobe.spawnDelayTicks());
		double baseDuration=payloadType==WarheadPayloadType.NUCLEAR?4.0:2.75;
		double frameDuration=baseDuration/Math.max(.65,Math.min(1.45,lobe.frameRateScale()));
		double frameTime=localAge/frameDuration+lobe.frameOffset();
		int frame=Math.floorMod((int)Math.floor(frameTime),FRAME_COUNT);
		float u0=frame/(float)FRAME_COUNT+HALF_TEXEL_U,u1=(frame+1)/(float)FRAME_COUNT-HALF_TEXEL_U;
		return new Uv(u0,u1,HALF_TEXEL_V,1.0F-HALF_TEXEL_V);
	}
	public record Uv(float u0,float u1,float v0,float v1){}
}