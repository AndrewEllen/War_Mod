package com.andye.warmod.acoustics;

import com.andye.warmod.WarMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class ModSoundEvents {
	public static final Identifier LARGE_EXPLOSION_1_CRACK_ID = id("large_explosion_1_crack");
	public static final Identifier LARGE_EXPLOSION_1_BODY_ID = id("large_explosion_1_body");
	public static final Identifier LARGE_EXPLOSION_1_LOW_ID = id("large_explosion_1_low");
	public static final Identifier LARGE_EXPLOSION_1_TAIL_ID = id("large_explosion_1_tail");
	public static final Identifier LARGE_EXPLOSION_2_CRACK_ID = id("large_explosion_2_crack");
	public static final Identifier LARGE_EXPLOSION_2_BODY_ID = id("large_explosion_2_body");
	public static final Identifier LARGE_EXPLOSION_2_LOW_ID = id("large_explosion_2_low");
	public static final Identifier LARGE_EXPLOSION_2_TAIL_ID = id("large_explosion_2_tail");
	public static final Identifier SILENT_ID = id("silent");

	public static final SoundEvent LARGE_EXPLOSION_1_CRACK = fixedRange(LARGE_EXPLOSION_1_CRACK_ID);
	public static final SoundEvent LARGE_EXPLOSION_1_BODY = fixedRange(LARGE_EXPLOSION_1_BODY_ID);
	public static final SoundEvent LARGE_EXPLOSION_1_LOW = fixedRange(LARGE_EXPLOSION_1_LOW_ID);
	public static final SoundEvent LARGE_EXPLOSION_1_TAIL = fixedRange(LARGE_EXPLOSION_1_TAIL_ID);
	public static final SoundEvent LARGE_EXPLOSION_2_CRACK = fixedRange(LARGE_EXPLOSION_2_CRACK_ID);
	public static final SoundEvent LARGE_EXPLOSION_2_BODY = fixedRange(LARGE_EXPLOSION_2_BODY_ID);
	public static final SoundEvent LARGE_EXPLOSION_2_LOW = fixedRange(LARGE_EXPLOSION_2_LOW_ID);
	public static final SoundEvent LARGE_EXPLOSION_2_TAIL = fixedRange(LARGE_EXPLOSION_2_TAIL_ID);
	public static final SoundEvent SILENT = SoundEvent.createVariableRangeEvent(SILENT_ID);

	private static boolean registered;

	private ModSoundEvents() {
	}

	public static void register() {
		if (registered) {
			return;
		}

		register(LARGE_EXPLOSION_1_CRACK_ID, LARGE_EXPLOSION_1_CRACK);
		register(LARGE_EXPLOSION_1_BODY_ID, LARGE_EXPLOSION_1_BODY);
		register(LARGE_EXPLOSION_1_LOW_ID, LARGE_EXPLOSION_1_LOW);
		register(LARGE_EXPLOSION_1_TAIL_ID, LARGE_EXPLOSION_1_TAIL);
		register(LARGE_EXPLOSION_2_CRACK_ID, LARGE_EXPLOSION_2_CRACK);
		register(LARGE_EXPLOSION_2_BODY_ID, LARGE_EXPLOSION_2_BODY);
		register(LARGE_EXPLOSION_2_LOW_ID, LARGE_EXPLOSION_2_LOW);
		register(LARGE_EXPLOSION_2_TAIL_ID, LARGE_EXPLOSION_2_TAIL);
		register(SILENT_ID, SILENT);
		registered = true;
		WarMod.LOGGER.info("Registered nine War Mod acoustic sound events.");
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
