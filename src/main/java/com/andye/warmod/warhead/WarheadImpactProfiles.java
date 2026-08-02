package com.andye.warmod.warhead;

import java.util.Objects;

public final class WarheadImpactProfiles {
	private static final WarheadImpactProfile CONVENTIONAL=new WarheadImpactProfile(WarheadPayloadType.CONVENTIONAL,WarheadConstants.EXPLOSION_STRENGTH,1.0F,1.0F,1.0F,1.0,1.0,1.0,24,320,112,1.0);
	private static final WarheadImpactProfile TACTICAL_HE=new WarheadImpactProfile(WarheadPayloadType.CONVENTIONAL,5.0F,.34F,1.12F,.24F,.32,.55,.28,10,24,4,.48);
	private static final WarheadImpactProfile NUCLEAR=new WarheadImpactProfile(WarheadPayloadType.NUCLEAR,160.0F,2.4F,.96F,3.0F,2.4,1.15,2.5,36,640,224,1.10);
	private WarheadImpactProfiles(){}
	public static WarheadImpactProfile get(final WarheadPayloadType payloadType){return Objects.requireNonNull(payloadType,"payloadType")==WarheadPayloadType.NUCLEAR?NUCLEAR:CONVENTIONAL;}
	public static WarheadImpactProfile tacticalHe(){return TACTICAL_HE;}
}