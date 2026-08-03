package com.andye.warmod.acoustics;

import com.andye.warmod.WarMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class ModSoundEvents {
	public static final Identifier PROTOTYPE_EXPLOSION_NEAR_ID=id("prototype_explosion_near"),PROTOTYPE_EXPLOSION_MEDIUM_ID=id("prototype_explosion_medium"),PROTOTYPE_EXPLOSION_FAR_ID=id("prototype_explosion_far"),PROTOTYPE_EXPLOSION_EXTREME_ID=id("prototype_explosion_extreme"),SILENT_ID=id("silent"),PHALANX_FIRE_ID=id("phalanx_fire");
	public static final Identifier MISSILE_ENGINE_NEAR_ID=id("missile_engine_near"),MISSILE_ENGINE_MEDIUM_ID=id("missile_engine_medium"),MISSILE_ENGINE_FAR_ID=id("missile_engine_far"),MISSILE_ENGINE_EXTREME_ID=id("missile_engine_extreme");
	public static final Identifier TERMINAL_RUSH_NEAR_ID=id("terminal_rush_near"),TERMINAL_RUSH_MEDIUM_ID=id("terminal_rush_medium"),TERMINAL_RUSH_FAR_ID=id("terminal_rush_far"),TERMINAL_RUSH_EXTREME_ID=id("terminal_rush_extreme");
	public static final Identifier SONIC_BOOM_NEAR_ID=id("sonic_boom_near"),SONIC_BOOM_MEDIUM_ID=id("sonic_boom_medium"),SONIC_BOOM_FAR_ID=id("sonic_boom_far"),SONIC_BOOM_EXTREME_ID=id("sonic_boom_extreme");
	public static final Identifier IMPACT_THUD_NEAR_ID=id("impact_thud_near"),IMPACT_THUD_MEDIUM_ID=id("impact_thud_medium"),IMPACT_THUD_FAR_ID=id("impact_thud_far"),IMPACT_THUD_EXTREME_ID=id("impact_thud_extreme");

	public static final SoundEvent PROTOTYPE_EXPLOSION_NEAR=fixedRange(PROTOTYPE_EXPLOSION_NEAR_ID),PROTOTYPE_EXPLOSION_MEDIUM=fixedRange(PROTOTYPE_EXPLOSION_MEDIUM_ID),PROTOTYPE_EXPLOSION_FAR=fixedRange(PROTOTYPE_EXPLOSION_FAR_ID),PROTOTYPE_EXPLOSION_EXTREME=fixedRange(PROTOTYPE_EXPLOSION_EXTREME_ID),SILENT=SoundEvent.createVariableRangeEvent(SILENT_ID),PHALANX_FIRE=SoundEvent.createFixedRangeEvent(PHALANX_FIRE_ID,256.0F);
	public static final SoundEvent MISSILE_ENGINE_NEAR=fixedRange(MISSILE_ENGINE_NEAR_ID),MISSILE_ENGINE_MEDIUM=fixedRange(MISSILE_ENGINE_MEDIUM_ID),MISSILE_ENGINE_FAR=fixedRange(MISSILE_ENGINE_FAR_ID),MISSILE_ENGINE_EXTREME=fixedRange(MISSILE_ENGINE_EXTREME_ID);
	public static final SoundEvent TERMINAL_RUSH_NEAR=fixedRange(TERMINAL_RUSH_NEAR_ID),TERMINAL_RUSH_MEDIUM=fixedRange(TERMINAL_RUSH_MEDIUM_ID),TERMINAL_RUSH_FAR=fixedRange(TERMINAL_RUSH_FAR_ID),TERMINAL_RUSH_EXTREME=fixedRange(TERMINAL_RUSH_EXTREME_ID);
	public static final SoundEvent SONIC_BOOM_NEAR=fixedRange(SONIC_BOOM_NEAR_ID),SONIC_BOOM_MEDIUM=fixedRange(SONIC_BOOM_MEDIUM_ID),SONIC_BOOM_FAR=fixedRange(SONIC_BOOM_FAR_ID),SONIC_BOOM_EXTREME=fixedRange(SONIC_BOOM_EXTREME_ID);
	public static final SoundEvent IMPACT_THUD_NEAR=fixedRange(IMPACT_THUD_NEAR_ID),IMPACT_THUD_MEDIUM=fixedRange(IMPACT_THUD_MEDIUM_ID),IMPACT_THUD_FAR=fixedRange(IMPACT_THUD_FAR_ID),IMPACT_THUD_EXTREME=fixedRange(IMPACT_THUD_EXTREME_ID);

	public static final SoundEvent MISSILE_ENGINE_IGNITION_NEAR=sound("missile_engine_ignition_near"),MISSILE_ENGINE_IGNITION_MEDIUM=sound("missile_engine_ignition_medium"),MISSILE_ENGINE_IGNITION_FAR=sound("missile_engine_ignition_far"),MISSILE_ENGINE_IGNITION_EXTREME=sound("missile_engine_ignition_extreme");
	public static final SoundEvent MISSILE_ENGINE_SUSTAIN_NEAR=sound("missile_engine_sustain_near"),MISSILE_ENGINE_SUSTAIN_MEDIUM=sound("missile_engine_sustain_medium"),MISSILE_ENGINE_SUSTAIN_FAR=sound("missile_engine_sustain_far"),MISSILE_ENGINE_SUSTAIN_EXTREME=sound("missile_engine_sustain_extreme");
	public static final SoundEvent MISSILE_ENGINE_SHUTDOWN_NEAR=sound("missile_engine_shutdown_near"),MISSILE_ENGINE_SHUTDOWN_MEDIUM=sound("missile_engine_shutdown_medium"),MISSILE_ENGINE_SHUTDOWN_FAR=sound("missile_engine_shutdown_far"),MISSILE_ENGINE_SHUTDOWN_EXTREME=sound("missile_engine_shutdown_extreme");
	public static final SoundEvent TERMINAL_RUSH_LOOP_NEAR=sound("terminal_rush_loop_near"),TERMINAL_RUSH_LOOP_MEDIUM=sound("terminal_rush_loop_medium"),TERMINAL_RUSH_LOOP_FAR=sound("terminal_rush_loop_far"),TERMINAL_RUSH_LOOP_EXTREME=sound("terminal_rush_loop_extreme");
	public static final SoundEvent TERMINAL_RUSH_TAIL_NEAR=sound("terminal_rush_tail_near"),TERMINAL_RUSH_TAIL_MEDIUM=sound("terminal_rush_tail_medium"),TERMINAL_RUSH_TAIL_FAR=sound("terminal_rush_tail_far"),TERMINAL_RUSH_TAIL_EXTREME=sound("terminal_rush_tail_extreme");

	private static boolean registered;
	private ModSoundEvents() { }
	public static void register() {
		if(registered)return;
		register(PROTOTYPE_EXPLOSION_NEAR_ID,PROTOTYPE_EXPLOSION_NEAR);register(PROTOTYPE_EXPLOSION_MEDIUM_ID,PROTOTYPE_EXPLOSION_MEDIUM);register(PROTOTYPE_EXPLOSION_FAR_ID,PROTOTYPE_EXPLOSION_FAR);register(PROTOTYPE_EXPLOSION_EXTREME_ID,PROTOTYPE_EXPLOSION_EXTREME);register(SILENT_ID,SILENT);register(PHALANX_FIRE_ID,PHALANX_FIRE);
		register(MISSILE_ENGINE_NEAR_ID,MISSILE_ENGINE_NEAR);register(MISSILE_ENGINE_MEDIUM_ID,MISSILE_ENGINE_MEDIUM);register(MISSILE_ENGINE_FAR_ID,MISSILE_ENGINE_FAR);register(MISSILE_ENGINE_EXTREME_ID,MISSILE_ENGINE_EXTREME);
		register(TERMINAL_RUSH_NEAR_ID,TERMINAL_RUSH_NEAR);register(TERMINAL_RUSH_MEDIUM_ID,TERMINAL_RUSH_MEDIUM);register(TERMINAL_RUSH_FAR_ID,TERMINAL_RUSH_FAR);register(TERMINAL_RUSH_EXTREME_ID,TERMINAL_RUSH_EXTREME);
		register(SONIC_BOOM_NEAR_ID,SONIC_BOOM_NEAR);register(SONIC_BOOM_MEDIUM_ID,SONIC_BOOM_MEDIUM);register(SONIC_BOOM_FAR_ID,SONIC_BOOM_FAR);register(SONIC_BOOM_EXTREME_ID,SONIC_BOOM_EXTREME);
		register(IMPACT_THUD_NEAR_ID,IMPACT_THUD_NEAR);register(IMPACT_THUD_MEDIUM_ID,IMPACT_THUD_MEDIUM);register(IMPACT_THUD_FAR_ID,IMPACT_THUD_FAR);register(IMPACT_THUD_EXTREME_ID,IMPACT_THUD_EXTREME);
		registerNamed("missile_engine_ignition_near",MISSILE_ENGINE_IGNITION_NEAR);registerNamed("missile_engine_ignition_medium",MISSILE_ENGINE_IGNITION_MEDIUM);registerNamed("missile_engine_ignition_far",MISSILE_ENGINE_IGNITION_FAR);registerNamed("missile_engine_ignition_extreme",MISSILE_ENGINE_IGNITION_EXTREME);
		registerNamed("missile_engine_sustain_near",MISSILE_ENGINE_SUSTAIN_NEAR);registerNamed("missile_engine_sustain_medium",MISSILE_ENGINE_SUSTAIN_MEDIUM);registerNamed("missile_engine_sustain_far",MISSILE_ENGINE_SUSTAIN_FAR);registerNamed("missile_engine_sustain_extreme",MISSILE_ENGINE_SUSTAIN_EXTREME);
		registerNamed("missile_engine_shutdown_near",MISSILE_ENGINE_SHUTDOWN_NEAR);registerNamed("missile_engine_shutdown_medium",MISSILE_ENGINE_SHUTDOWN_MEDIUM);registerNamed("missile_engine_shutdown_far",MISSILE_ENGINE_SHUTDOWN_FAR);registerNamed("missile_engine_shutdown_extreme",MISSILE_ENGINE_SHUTDOWN_EXTREME);
		registerNamed("terminal_rush_loop_near",TERMINAL_RUSH_LOOP_NEAR);registerNamed("terminal_rush_loop_medium",TERMINAL_RUSH_LOOP_MEDIUM);registerNamed("terminal_rush_loop_far",TERMINAL_RUSH_LOOP_FAR);registerNamed("terminal_rush_loop_extreme",TERMINAL_RUSH_LOOP_EXTREME);
		registerNamed("terminal_rush_tail_near",TERMINAL_RUSH_TAIL_NEAR);registerNamed("terminal_rush_tail_medium",TERMINAL_RUSH_TAIL_MEDIUM);registerNamed("terminal_rush_tail_far",TERMINAL_RUSH_TAIL_FAR);registerNamed("terminal_rush_tail_extreme",TERMINAL_RUSH_TAIL_EXTREME);
		registered=true;WarMod.LOGGER.info("Registered War Mod acoustic and moving-loop sound events.");
	}
	private static Identifier id(final String path){return Identifier.fromNamespaceAndPath(WarMod.MOD_ID,path);}
	private static SoundEvent sound(final String path){return fixedRange(id(path));}
	private static SoundEvent fixedRange(final Identifier id){return SoundEvent.createFixedRangeEvent(id,2048.0F);}
	private static void registerNamed(final String path,final SoundEvent event){register(id(path),event);}
	private static void register(final Identifier id,final SoundEvent event){Registry.register(BuiltInRegistries.SOUND_EVENT,id,event);}
}