package com.andye.warmod.acoustics;

import com.andye.warmod.WarMod;
import com.andye.warmod.acoustics.model.AcousticDistanceProfile;
import com.andye.warmod.acoustics.model.AcousticDistanceSound;
import com.andye.warmod.acoustics.model.AcousticSoundDefinition;
import java.util.List;
import net.minecraft.resources.Identifier;

public final class AcousticSounds {
	public static final Identifier LARGE_EXPLOSION_ID = Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "large_explosion");

	private static boolean registered;

	private AcousticSounds() {
	}

	public static void register() {
		if (registered) {
			return;
		}

		AcousticSoundRegistry.register(new AcousticSoundDefinition(
			LARGE_EXPLOSION_ID,
			List.of(
				new AcousticDistanceSound(AcousticDistanceProfile.NEAR, ModSoundEvents.PROTOTYPE_EXPLOSION_NEAR_ID, 0.0, 100.0, 1.15F, 1.0F),
				new AcousticDistanceSound(AcousticDistanceProfile.MEDIUM, ModSoundEvents.PROTOTYPE_EXPLOSION_MEDIUM_ID, 100.0, 250.0, 1.20F, 1.0F),
				new AcousticDistanceSound(AcousticDistanceProfile.FAR, ModSoundEvents.PROTOTYPE_EXPLOSION_FAR_ID, 250.0, 400.0, 1.35F, 1.0F),
				new AcousticDistanceSound(AcousticDistanceProfile.EXTREME, ModSoundEvents.PROTOTYPE_EXPLOSION_EXTREME_ID, 400.0, 1536.0, 1.55F, 1.0F)
			),
			343.0,
			1536.0,
			0.008,
			true
		));
		registered = true;
		WarMod.LOGGER.info("Registered acoustic definition {} with four distance profiles.", LARGE_EXPLOSION_ID);
	}
}