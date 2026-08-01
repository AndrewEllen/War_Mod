package com.andye.warmod.acoustics.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andye.warmod.acoustics.model.AcousticDistanceProfile;
import com.andye.warmod.acoustics.model.AcousticDistanceSound;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

final class AcousticAttenuationTest {
	private static AcousticDistanceSound sound(
		final AcousticDistanceProfile profile,
		final double minimumDistance,
		final double maximumDistance
	) {
		return new AcousticDistanceSound(
			profile,
			Identifier.fromNamespaceAndPath("war_mod", "test/" + profile.name().toLowerCase()),
			minimumDistance,
			maximumDistance,
			1.0F,
			1.0F
		);
	}

	@Test
	void gainFallsWithDistanceWithinSelectedProfile() {
		AcousticDistanceSound near = sound(AcousticDistanceProfile.NEAR, 0.0, 80.0);

		assertTrue(AcousticAttenuation.gain(16.0, near, 1.0F) > AcousticAttenuation.gain(72.0, near, 1.0F));
	}

	@Test
	void gainIsZeroOutsideProfileRangeAndExtremeIncludesMaximum() {
		AcousticDistanceSound medium = sound(AcousticDistanceProfile.MEDIUM, 80.0, 220.0);
		AcousticDistanceSound extreme = sound(AcousticDistanceProfile.EXTREME, 600.0, 1536.0);

		assertEquals(0.0, AcousticAttenuation.gain(79.99, medium, 1.0F));
		assertTrue(AcousticAttenuation.gain(80.0, medium, 1.0F) > 0.0);
		assertEquals(0.0, AcousticAttenuation.gain(220.0, medium, 1.0F));
		assertTrue(AcousticAttenuation.gain(1536.0, extreme, 1.0F) > 0.0);
	}
}