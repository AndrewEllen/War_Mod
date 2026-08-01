package com.andye.warmod.acoustics;

import com.andye.warmod.acoustics.model.AcousticSoundDefinition;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.Identifier;

public final class AcousticSoundRegistry {
	private static final Map<Identifier, AcousticSoundDefinition> DEFINITIONS = new LinkedHashMap<>();

	private AcousticSoundRegistry() {
	}

	public static void register(final AcousticSoundDefinition definition) {
		Objects.requireNonNull(definition, "definition");
		AcousticSoundDefinition previous = DEFINITIONS.putIfAbsent(definition.id(), definition);
		if (previous != null) {
			throw new IllegalStateException("Duplicate acoustic sound definition: " + definition.id());
		}
	}

	public static Optional<AcousticSoundDefinition> get(final Identifier id) {
		return Optional.ofNullable(id == null ? null : DEFINITIONS.get(id));
	}

	public static boolean contains(final Identifier id) {
		return id != null && DEFINITIONS.containsKey(id);
	}
}
