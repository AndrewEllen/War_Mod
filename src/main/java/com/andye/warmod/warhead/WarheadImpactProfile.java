package com.andye.warmod.warhead;

public record WarheadImpactProfile(
	WarheadPayloadType payloadType,float explosionStrength,float acousticVolume,float acousticPitch,float impactVisualScale,
	double shockwaveThicknessScale,double shockwaveAlphaScale,double shockwaveParticleDensityScale,
	int maximumDebrisEntities,int maximumLargeDebrisEntities,double debrisVelocityScale
) {
	public WarheadImpactProfile {if(payloadType==null||explosionStrength<=0||acousticVolume<=0||acousticPitch<=0||impactVisualScale<=0||shockwaveThicknessScale<=0||shockwaveAlphaScale<=0||shockwaveParticleDensityScale<=0||maximumDebrisEntities<0||maximumLargeDebrisEntities<0||maximumLargeDebrisEntities>maximumDebrisEntities||debrisVelocityScale<=0)throw new IllegalArgumentException("Invalid warhead impact profile");}
}