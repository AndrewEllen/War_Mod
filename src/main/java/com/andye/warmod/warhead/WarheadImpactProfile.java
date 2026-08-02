package com.andye.warmod.warhead;

public record WarheadImpactProfile(
	WarheadPayloadType payloadType, float explosionStrength, float acousticVolume, float acousticPitch,
	float impactVisualScale, double shockwaveScale, int maximumDebrisEntities,
	int maximumLargeDebrisEntities, double debrisVelocityScale
) {
	public WarheadImpactProfile {
		if (payloadType == null || explosionStrength <= 0.0F || acousticVolume <= 0.0F || acousticPitch <= 0.0F
			|| impactVisualScale <= 0.0F || shockwaveScale <= 0.0 || maximumDebrisEntities < 0
			|| maximumLargeDebrisEntities < 0 || maximumLargeDebrisEntities > maximumDebrisEntities || debrisVelocityScale <= 0.0)
			throw new IllegalArgumentException("Invalid warhead impact profile");
	}
}
