package com.andye.warmod.acoustics;

import com.andye.warmod.WarMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class ModSoundEvents {
	public static final Identifier PROTOTYPE_EXPLOSION_NEAR_ID=id("prototype_explosion_near"),PROTOTYPE_EXPLOSION_MEDIUM_ID=id("prototype_explosion_medium"),PROTOTYPE_EXPLOSION_FAR_ID=id("prototype_explosion_far"),PROTOTYPE_EXPLOSION_EXTREME_ID=id("prototype_explosion_extreme"),SILENT_ID=id("silent");
	public static final Identifier MISSILE_ENGINE_NEAR_ID=id("missile_engine_near"),MISSILE_ENGINE_MEDIUM_ID=id("missile_engine_medium"),MISSILE_ENGINE_FAR_ID=id("missile_engine_far"),MISSILE_ENGINE_EXTREME_ID=id("missile_engine_extreme");
	public static final Identifier TERMINAL_RUSH_NEAR_ID=id("terminal_rush_near"),TERMINAL_RUSH_MEDIUM_ID=id("terminal_rush_medium"),TERMINAL_RUSH_FAR_ID=id("terminal_rush_far"),TERMINAL_RUSH_EXTREME_ID=id("terminal_rush_extreme");
	public static final Identifier SONIC_BOOM_NEAR_ID=id("sonic_boom_near"),SONIC_BOOM_MEDIUM_ID=id("sonic_boom_medium"),SONIC_BOOM_FAR_ID=id("sonic_boom_far"),SONIC_BOOM_EXTREME_ID=id("sonic_boom_extreme");
	public static final SoundEvent PROTOTYPE_EXPLOSION_NEAR=fixedRange(PROTOTYPE_EXPLOSION_NEAR_ID),PROTOTYPE_EXPLOSION_MEDIUM=fixedRange(PROTOTYPE_EXPLOSION_MEDIUM_ID),PROTOTYPE_EXPLOSION_FAR=fixedRange(PROTOTYPE_EXPLOSION_FAR_ID),PROTOTYPE_EXPLOSION_EXTREME=fixedRange(PROTOTYPE_EXPLOSION_EXTREME_ID),SILENT=SoundEvent.createVariableRangeEvent(SILENT_ID);
	public static final SoundEvent MISSILE_ENGINE_NEAR=fixedRange(MISSILE_ENGINE_NEAR_ID),MISSILE_ENGINE_MEDIUM=fixedRange(MISSILE_ENGINE_MEDIUM_ID),MISSILE_ENGINE_FAR=fixedRange(MISSILE_ENGINE_FAR_ID),MISSILE_ENGINE_EXTREME=fixedRange(MISSILE_ENGINE_EXTREME_ID);
	public static final SoundEvent TERMINAL_RUSH_NEAR=fixedRange(TERMINAL_RUSH_NEAR_ID),TERMINAL_RUSH_MEDIUM=fixedRange(TERMINAL_RUSH_MEDIUM_ID),TERMINAL_RUSH_FAR=fixedRange(TERMINAL_RUSH_FAR_ID),TERMINAL_RUSH_EXTREME=fixedRange(TERMINAL_RUSH_EXTREME_ID);
	public static final SoundEvent SONIC_BOOM_NEAR=fixedRange(SONIC_BOOM_NEAR_ID),SONIC_BOOM_MEDIUM=fixedRange(SONIC_BOOM_MEDIUM_ID),SONIC_BOOM_FAR=fixedRange(SONIC_BOOM_FAR_ID),SONIC_BOOM_EXTREME=fixedRange(SONIC_BOOM_EXTREME_ID);
	private static boolean registered;
	private ModSoundEvents(){}
	public static void register(){if(registered)return;register(PROTOTYPE_EXPLOSION_NEAR_ID,PROTOTYPE_EXPLOSION_NEAR);register(PROTOTYPE_EXPLOSION_MEDIUM_ID,PROTOTYPE_EXPLOSION_MEDIUM);register(PROTOTYPE_EXPLOSION_FAR_ID,PROTOTYPE_EXPLOSION_FAR);register(PROTOTYPE_EXPLOSION_EXTREME_ID,PROTOTYPE_EXPLOSION_EXTREME);register(SILENT_ID,SILENT);register(MISSILE_ENGINE_NEAR_ID,MISSILE_ENGINE_NEAR);register(MISSILE_ENGINE_MEDIUM_ID,MISSILE_ENGINE_MEDIUM);register(MISSILE_ENGINE_FAR_ID,MISSILE_ENGINE_FAR);register(MISSILE_ENGINE_EXTREME_ID,MISSILE_ENGINE_EXTREME);register(TERMINAL_RUSH_NEAR_ID,TERMINAL_RUSH_NEAR);register(TERMINAL_RUSH_MEDIUM_ID,TERMINAL_RUSH_MEDIUM);register(TERMINAL_RUSH_FAR_ID,TERMINAL_RUSH_FAR);register(TERMINAL_RUSH_EXTREME_ID,TERMINAL_RUSH_EXTREME);register(SONIC_BOOM_NEAR_ID,SONIC_BOOM_NEAR);register(SONIC_BOOM_MEDIUM_ID,SONIC_BOOM_MEDIUM);register(SONIC_BOOM_FAR_ID,SONIC_BOOM_FAR);register(SONIC_BOOM_EXTREME_ID,SONIC_BOOM_EXTREME);registered=true;WarMod.LOGGER.info("Registered War Mod acoustic sound events.");}
	private static Identifier id(final String path){return Identifier.fromNamespaceAndPath(WarMod.MOD_ID,path);}private static SoundEvent fixedRange(final Identifier id){return SoundEvent.createFixedRangeEvent(id,1536.0F);}private static void register(final Identifier id,final SoundEvent event){Registry.register(BuiltInRegistries.SOUND_EVENT,id,event);}
}