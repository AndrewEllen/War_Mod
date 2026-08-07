package com.andye.warmod.acoustics;

import com.andye.warmod.acoustics.model.AcousticSoundDefinition;
import com.andye.warmod.acoustics.network.AcousticNetworking;
import com.andye.warmod.acoustics.network.ClientboundAcousticEventPayload;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public final class AcousticEngine {
	private AcousticEngine() {
	}

	public static UUID playSound(
		final ServerLevel level,
		final Vec3 sourcePosition,
		final Identifier definitionId,
		final SoundSource soundSource,
		final float volume,
		final float pitch
	) {
		Objects.requireNonNull(level, "level");
		Objects.requireNonNull(sourcePosition, "sourcePosition");
		Objects.requireNonNull(definitionId, "definitionId");
		Objects.requireNonNull(soundSource, "soundSource");
		if (!sourcePosition.isFinite()) {
			throw new IllegalArgumentException("sourcePosition must be finite");
		}
		if (!Float.isFinite(volume) || !Float.isFinite(pitch)) {
			throw new IllegalArgumentException("volume and pitch must be finite");
		}

		AcousticSoundDefinition definition = AcousticSoundRegistry.get(definitionId)
			.orElseThrow(() -> new IllegalArgumentException("Unknown acoustic sound definition: " + definitionId));
		float clampedVolume = Math.max(0.0F, Math.min(4.0F, volume));
		float clampedPitch = Math.max(0.10F, Math.min(4.0F, pitch));
		UUID eventId = UUID.randomUUID();
		ClientboundAcousticEventPayload payload = new ClientboundAcousticEventPayload(
			eventId,
			definitionId,
			sourcePosition.x,
			sourcePosition.y,
			sourcePosition.z,
			level.getGameTime(),
			clampedVolume,
			clampedPitch,
			ThreadLocalRandom.current().nextLong(),
			soundSource
		);
		double maxDistanceSquared = definition.maximumDistanceBlocks() * definition.maximumDistanceBlocks();

		for (ServerPlayer player : PlayerLookup.level(level)) {
			if (player.distanceToSqr(sourcePosition) <= maxDistanceSquared) {
				AcousticNetworking.send(player, payload);
			}
		}

		return eventId;
	}
}
