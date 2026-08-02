package com.andye.warmod.warhead.client;

import com.andye.warmod.warhead.WarheadPayloadType;

public record WarheadClientVisualProfile(WarheadPayloadType payloadType,int totalImpactLifetimeTicks,int flashStartTick,
	int flashEndTick,int fireballGrowthStartTick,int fireballGrowthEndTick,int fireballHoldEndTick,int fireballCoolingEndTick,
	int smokeStartTick,int cloudRiseStartTick,int cloudRiseEndTick,int cloudDissipationEndTick,double fireballWidth,
	double fireballHeight,double fireballRise,double smokeStemWidth,double smokeCapWidth,double maximumCloudHeight,
	int nearFireballLobes,int mediumFireballLobes,int farFireballLobes,int nearSmokeLobes,int mediumSmokeLobes,
	int farSmokeLobes,double shockwaveScale,double particleScale) { }
