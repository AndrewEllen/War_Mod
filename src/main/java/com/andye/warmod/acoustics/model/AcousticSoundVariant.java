package com.andye.warmod.acoustics.model;

import java.util.List;
import java.util.Objects;

public record AcousticSoundVariant(List<AcousticLayer> layers) {
	public AcousticSoundVariant {
		Objects.requireNonNull(layers, "layers");
		if (layers.size() != 4) {
			throw new IllegalArgumentException("An acoustic explosion variant must contain exactly four layers");
		}
		if (layers.stream().anyMatch(Objects::isNull)) {
			throw new IllegalArgumentException("Acoustic layers cannot be null");
		}
		layers = List.copyOf(layers);
	}
}
