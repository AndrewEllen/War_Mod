package com.andye.warmod.acoustics.model;

import java.util.List;
import java.util.Objects;
import net.minecraft.resources.Identifier;

public record AcousticSoundDefinition(
	Identifier id,
	List<AcousticSoundVariant> variants,
	double propagationSpeedBlocksPerSecond,
	double maximumDistanceBlocks,
	double minimumAudibleGain,
	boolean environmentEchoesEnabled
) {
	public AcousticSoundDefinition {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(variants, "variants");
		if (variants.isEmpty()) {
			throw new IllegalArgumentException("An acoustic sound definition needs at least one variant");
		}
		if (variants.stream().anyMatch(Objects::isNull)) {
			throw new IllegalArgumentException("Acoustic variants cannot be null");
		}
		variants = List.copyOf(variants);
		if (!Double.isFinite(propagationSpeedBlocksPerSecond) || propagationSpeedBlocksPerSecond <= 0.0) {
			throw new IllegalArgumentException("propagationSpeedBlocksPerSecond must be finite and greater than zero");
		}
		if (!Double.isFinite(maximumDistanceBlocks) || maximumDistanceBlocks <= 0.0) {
			throw new IllegalArgumentException("maximumDistanceBlocks must be finite and greater than zero");
		}
		if (!Double.isFinite(minimumAudibleGain) || minimumAudibleGain < 0.0 || minimumAudibleGain > 1.0) {
			throw new IllegalArgumentException("minimumAudibleGain must be finite and between zero and one");
		}
	}
}
