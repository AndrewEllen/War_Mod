package com.andye.warmod.warhead.client;

import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.SplittableRandom;

public final class WarheadClientVisualProfiles {
	private static final WarheadClientVisualProfile CONVENTIONAL=new WarheadClientVisualProfile(WarheadPayloadType.CONVENTIONAL,260,0,0,0,24,42,80,20,35,200,260,60,84,28,14,24,64,72,44,24,96,56,28,1.0,1.0,1.0,1.0);
	private WarheadClientVisualProfiles(){}
	public static WarheadClientVisualProfile get(final WarheadPayloadType type,final long seed){if(type!=WarheadPayloadType.NUCLEAR)return CONVENTIONAL;SplittableRandom r=new SplittableRandom(seed^0x4E55434C454152L);return new WarheadClientVisualProfile(type,1000,0,20,2,120,240,380,100,180,720,1000,r.nextDouble(190,230),r.nextDouble(150,190),60,r.nextDouble(48,72),r.nextDouble(320,420),r.nextDouble(340,440),320,180,72,360,210,90,2.4,1.15,2.5,3.5);}
}
