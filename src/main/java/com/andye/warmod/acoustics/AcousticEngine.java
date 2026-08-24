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
		AcousticSoundDefinition definition = validate(level, sourcePosition,
			definitionId, soundSource, volume, pitch);
		ClientboundAcousticEventPayload payload = payload(level, sourcePosition,
			definitionId, soundSource, volume, pitch);
		double maxDistanceSquared = definition.maximumDistanceBlocks() * definition.maximumDistanceBlocks();

		for (ServerPlayer player : PlayerLookup.level(level)) {
			if (player.distanceToSqr(sourcePosition) <= maxDistanceSquared) {
				AcousticNetworking.send(player, payload);
			}
		}

		return payload.eventId();
	}

	/** Sends a normal propagated event to one listener, used for listener-specific flybys. */
	public static UUID playSoundFor(
		final ServerPlayer player,
		final ServerLevel level,
		final Vec3 sourcePosition,
		final Identifier definitionId,
		final SoundSource soundSource,
		final float volume,
		final float pitch
	) {
		Objects.requireNonNull(player, "player");
		AcousticSoundDefinition definition = validate(level, sourcePosition,
			definitionId, soundSource, volume, pitch);
		ClientboundAcousticEventPayload payload = payload(level, sourcePosition,
			definitionId, soundSource, volume, pitch);
		double maximumDistanceSquared = definition.maximumDistanceBlocks()
			* definition.maximumDistanceBlocks();
		if (player.distanceToSqr(sourcePosition) <= maximumDistanceSquared) {
			AcousticNetworking.send(player, payload);
		}
		return payload.eventId();
	}

	private static AcousticSoundDefinition validate(final ServerLevel level,
		final Vec3 sourcePosition, final Identifier definitionId,
		final SoundSource soundSource, final float volume, final float pitch) {
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
		return AcousticSoundRegistry.get(definitionId).orElseThrow(() ->
			new IllegalArgumentException("Unknown acoustic sound definition: " + definitionId));
	}

	private static ClientboundAcousticEventPayload payload(final ServerLevel level,
		final Vec3 sourcePosition, final Identifier definitionId,
		final SoundSource soundSource, final float volume, final float pitch) {
		return new ClientboundAcousticEventPayload(
			UUID.randomUUID(), definitionId,
			sourcePosition.x, sourcePosition.y, sourcePosition.z,
			level.getGameTime(),
			Math.max(0.0F, Math.min(4.0F, volume)),
			Math.max(0.10F, Math.min(4.0F, pitch)),
			ThreadLocalRandom.current().nextLong(), soundSource
		);
	}
}
