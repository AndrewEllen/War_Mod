package com.andye.warmod.acoustics;

import com.andye.warmod.WarMod;
import com.andye.warmod.acoustics.model.AcousticDistanceProfile;
import com.andye.warmod.acoustics.model.AcousticDistanceSound;
import com.andye.warmod.acoustics.model.AcousticSoundDefinition;
import java.util.List;
import net.minecraft.resources.Identifier;

public final class AcousticSounds {
	public static final Identifier LARGE_EXPLOSION_ID=id("large_explosion"),ICBM_ENGINE_RUMBLE_ID=id("icbm_engine_rumble"),TERMINAL_DESCENT_RUSH_ID=id("terminal_descent_rush"),TERMINAL_SONIC_BOOM_ID=id("terminal_sonic_boom"),WARHEAD_IMPACT_THUD_ID=id("warhead_impact_thud");
	private static boolean registered;private AcousticSounds(){}
	public static void register(){if(registered)return;
		register(LARGE_EXPLOSION_ID,ModSoundEvents.PROTOTYPE_EXPLOSION_NEAR_ID,ModSoundEvents.PROTOTYPE_EXPLOSION_MEDIUM_ID,ModSoundEvents.PROTOTYPE_EXPLOSION_FAR_ID,ModSoundEvents.PROTOTYPE_EXPLOSION_EXTREME_ID,new double[]{0,100,250,400,1536},new float[]{1.15F,1.20F,1.35F,1.55F},.008,true);
		register(ICBM_ENGINE_RUMBLE_ID,ModSoundEvents.MISSILE_ENGINE_NEAR_ID,ModSoundEvents.MISSILE_ENGINE_MEDIUM_ID,ModSoundEvents.MISSILE_ENGINE_FAR_ID,ModSoundEvents.MISSILE_ENGINE_EXTREME_ID,new double[]{0,140,400,850,1536},new float[]{1,1.10F,1.25F,1.40F},.006,false);
		register(TERMINAL_DESCENT_RUSH_ID,ModSoundEvents.TERMINAL_RUSH_NEAR_ID,ModSoundEvents.TERMINAL_RUSH_MEDIUM_ID,ModSoundEvents.TERMINAL_RUSH_FAR_ID,ModSoundEvents.TERMINAL_RUSH_EXTREME_ID,new double[]{0,120,320,750,1536},new float[]{1,1.10F,1.20F,1.30F},.006,false);
		register(TERMINAL_SONIC_BOOM_ID,ModSoundEvents.SONIC_BOOM_NEAR_ID,ModSoundEvents.SONIC_BOOM_MEDIUM_ID,ModSoundEvents.SONIC_BOOM_FAR_ID,ModSoundEvents.SONIC_BOOM_EXTREME_ID,new double[]{0,160,400,850,1536},new float[]{1.10F,1.20F,1.35F,1.50F},.006,true);
		register(WARHEAD_IMPACT_THUD_ID,ModSoundEvents.IMPACT_THUD_NEAR_ID,ModSoundEvents.IMPACT_THUD_MEDIUM_ID,ModSoundEvents.IMPACT_THUD_FAR_ID,ModSoundEvents.IMPACT_THUD_EXTREME_ID,new double[]{0,120,300,700,1536},new float[]{.85F,.95F,1.05F,1.15F},.008,false);
		registered=true;WarMod.LOGGER.info("Registered propagated acoustic definitions.");}
	private static void register(final Identifier definition,final Identifier near,final Identifier medium,final Identifier far,final Identifier extreme,final double[] ranges,final float[] volumes,final double minimumGain,final boolean echoes){AcousticSoundRegistry.register(new AcousticSoundDefinition(definition,List.of(new AcousticDistanceSound(AcousticDistanceProfile.NEAR,near,ranges[0],ranges[1],volumes[0],1),new AcousticDistanceSound(AcousticDistanceProfile.MEDIUM,medium,ranges[1],ranges[2],volumes[1],1),new AcousticDistanceSound(AcousticDistanceProfile.FAR,far,ranges[2],ranges[3],volumes[2],1),new AcousticDistanceSound(AcousticDistanceProfile.EXTREME,extreme,ranges[3],ranges[4],volumes[3],1)),343.0,1536.0,minimumGain,echoes));}
	private static Identifier id(final String path){return Identifier.fromNamespaceAndPath(WarMod.MOD_ID,path);}
}