package com.andye.warmod.acoustics;

import com.andye.warmod.WarMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class ModSoundEvents {
	public static final Identifier PROTOTYPE_EXPLOSION_NEAR_ID = id("prototype_explosion_near");
	public static final Identifier PROTOTYPE_EXPLOSION_MEDIUM_ID = id("prototype_explosion_medium");
	public static final Identifier PROTOTYPE_EXPLOSION_FAR_ID = id("prototype_explosion_far");
	public static final Identifier PROTOTYPE_EXPLOSION_EXTREME_ID = id("prototype_explosion_extreme");
	public static final Identifier SILENT_ID = id("silent");

	public static final SoundEvent PROTOTYPE_EXPLOSION_NEAR = fixedRange(PROTOTYPE_EXPLOSION_NEAR_ID);
	public static final SoundEvent PROTOTYPE_EXPLOSION_MEDIUM = fixedRange(PROTOTYPE_EXPLOSION_MEDIUM_ID);
	public static final SoundEvent PROTOTYPE_EXPLOSION_FAR = fixedRange(PROTOTYPE_EXPLOSION_FAR_ID);
	public static final SoundEvent PROTOTYPE_EXPLOSION_EXTREME = fixedRange(PROTOTYPE_EXPLOSION_EXTREME_ID);
	public static final SoundEvent SILENT = SoundEvent.createVariableRangeEvent(SILENT_ID);

	private static boolean registered;

	private ModSoundEvents() {
	}

	public static void register() {
		if (registered) {
			return;
		}

		register(PROTOTYPE_EXPLOSION_NEAR_ID, PROTOTYPE_EXPLOSION_NEAR);
		register(PROTOTYPE_EXPLOSION_MEDIUM_ID, PROTOTYPE_EXPLOSION_MEDIUM);
		register(PROTOTYPE_EXPLOSION_FAR_ID, PROTOTYPE_EXPLOSION_FAR);
		register(PROTOTYPE_EXPLOSION_EXTREME_ID, PROTOTYPE_EXPLOSION_EXTREME);
		register(SILENT_ID, SILENT);
		registered = true;
		WarMod.LOGGER.info("Registered five War Mod acoustic sound events.");
	}

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(WarMod.MOD_ID, path);
	}

	private static SoundEvent fixedRange(final Identifier id) {
		return SoundEvent.createFixedRangeEvent(id, 1536.0F);
	}

	private static void register(final Identifier id, final SoundEvent event) {
		Registry.register(BuiltInRegistries.SOUND_EVENT, id, event);
	}
}