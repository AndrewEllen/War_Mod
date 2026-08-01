package com.andye.warmod.acoustics;

import com.andye.warmod.WarMod;
import com.andye.warmod.acoustics.model.AcousticFrequencyBand;
import com.andye.warmod.acoustics.model.AcousticLayer;
import com.andye.warmod.acoustics.model.AcousticSoundDefinition;
import com.andye.warmod.acoustics.model.AcousticSoundVariant;
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
				variant(
					ModSoundEvents.LARGE_EXPLOSION_1_CRACK_ID,
					ModSoundEvents.LARGE_EXPLOSION_1_BODY_ID,
					ModSoundEvents.LARGE_EXPLOSION_1_LOW_ID,
					ModSoundEvents.LARGE_EXPLOSION_1_TAIL_ID
				),
				variant(
					ModSoundEvents.LARGE_EXPLOSION_2_CRACK_ID,
					ModSoundEvents.LARGE_EXPLOSION_2_BODY_ID,
					ModSoundEvents.LARGE_EXPLOSION_2_LOW_ID,
					ModSoundEvents.LARGE_EXPLOSION_2_TAIL_ID
				)
			),
			343.0,
			1536.0,
			0.008,
			true
		));
		registered = true;
		WarMod.LOGGER.info("Registered acoustic definition {} with two variations.", LARGE_EXPLOSION_ID);
	}

	private static AcousticSoundVariant variant(
		final Identifier crack,
		final Identifier body,
		final Identifier low,
		final Identifier tail
	) {
		return new AcousticSoundVariant(List.of(
			new AcousticLayer(crack, AcousticFrequencyBand.TRANSIENT, 0.85F, 1.0F, 24.0, 1.15, 0.0035, 320.0, 0, false),
			new AcousticLayer(body, AcousticFrequencyBand.BODY, 0.85F, 1.0F, 32.0, 0.82, 0.0012, 800.0, 1, true),
			new AcousticLayer(low, AcousticFrequencyBand.LOW, 0.80F, 1.0F, 40.0, 0.62, 0.00025, 1280.0, 2, true),
			new AcousticLayer(tail, AcousticFrequencyBand.TAIL, 0.65F, 1.0F, 48.0, 0.52, 0.00008, 1536.0, 3, true)
		));
	}
}
