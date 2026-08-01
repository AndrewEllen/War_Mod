package com.andye.warmod.acoustics.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andye.warmod.acoustics.model.AcousticFrequencyBand;
import com.andye.warmod.acoustics.model.AcousticLayer;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

final class AcousticAttenuationTest {
	private static AcousticLayer layer(
		final AcousticFrequencyBand band,
		final double maximumDistanceBlocks
	) {
		return new AcousticLayer(
			Identifier.fromNamespaceAndPath("war_mod", "test/" + band.name().toLowerCase()),
			band,
			1.0F,
			1.0F,
			16.0,
			1.2,
			0.0005,
			maximumDistanceBlocks,
			0,
			true
		);
	}

	@Test
	void nearGainExceedsFarGain() {
		AcousticLayer transientLayer = layer(AcousticFrequencyBand.TRANSIENT, 320.0);

		assertTrue(
			AcousticAttenuation.gain(16.0, transientLayer, 1.0F)
				> AcousticAttenuation.gain(160.0, transientLayer, 1.0F)
		);
	}

	@Test
	void transientEndsBeforeLowAndTailLayers() {
		AcousticLayer transientLayer = layer(AcousticFrequencyBand.TRANSIENT, 320.0);
		AcousticLayer lowLayer = layer(AcousticFrequencyBand.LOW, 1280.0);
		AcousticLayer tailLayer = layer(AcousticFrequencyBand.TAIL, 1536.0);

		double transientGain = AcousticAttenuation.gain(900.0, transientLayer, 1.0F);
		double lowGain = AcousticAttenuation.gain(900.0, lowLayer, 1.0F);
		double tailGain = AcousticAttenuation.gain(900.0, tailLayer, 1.0F);

		assertTrue(lowGain > transientGain);
		assertTrue(tailGain > transientGain);
		assertEquals(0.0, AcousticAttenuation.gain(321.0, transientLayer, 1.0F));
	}
}
