package com.andye.warmod.acoustics.client;

import com.andye.warmod.WarMod;
import com.andye.warmod.acoustics.AcousticSoundRegistry;
import com.andye.warmod.acoustics.model.AcousticFrequencyBand;
import com.andye.warmod.acoustics.model.AcousticLayer;
import com.andye.warmod.acoustics.model.AcousticSoundDefinition;
import com.andye.warmod.acoustics.model.AcousticSoundVariant;
import com.andye.warmod.acoustics.network.ClientboundAcousticEventPayload;
import com.andye.warmod.acoustics.physics.AcousticAttenuation;
import com.andye.warmod.acoustics.physics.AcousticPropagation;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class ClientAcousticEngine {
	public static final ClientAcousticEngine INSTANCE = new ClientAcousticEngine();

	private static final int MAX_PENDING_EVENTS = 128;
	private static final int MAX_SCHEDULED_LAYERS = 512;
	private static final long MAX_LIFETIME_AFTER_EXPECTED_ARRIVAL = 100L;

	private final Map<UUID, PendingAcousticEvent> pendingEvents = new LinkedHashMap<>();
	private final PriorityQueue<ScheduledAcousticLayer> scheduledLayers = new PriorityQueue<>(
		Comparator.comparingLong(ScheduledAcousticLayer::playbackClientTick)
	);
	private long clientTick;
	private ClientLevel activeLevel;

	private ClientAcousticEngine() {
	}

	public void accept(final ClientboundAcousticEventPayload payload) {
		if (payload == null || !payload.isWellFormed() || !AcousticSoundRegistry.contains(payload.definitionId())) {
			return;
		}
		if (pendingEvents.containsKey(payload.eventId())) {
			return;
		}
		while (pendingEvents.size() >= MAX_PENDING_EVENTS) {
			Iterator<UUID> iterator = pendingEvents.keySet().iterator();
			if (!iterator.hasNext()) {
				break;
			}
			iterator.next();
			iterator.remove();
		}
		pendingEvents.put(payload.eventId(), new PendingAcousticEvent(payload));
	}

	public void tick(final Minecraft minecraft) {
		ClientLevel clientLevel = minecraft.level;
		Player listener = minecraft.player;
		if (clientLevel == null || listener == null) {
			clear();
			return;
		}
		if (activeLevel != null && activeLevel != clientLevel) {
			clear();
		}
		activeLevel = clientLevel;
		clientTick = clientLevel.getGameTime();

		processPending(clientLevel, listener);
		while (!scheduledLayers.isEmpty() && scheduledLayers.peek().playbackClientTick() <= clientTick) {
			minecraft.getSoundManager().play(new AcousticSoundInstance(scheduledLayers.poll()));
		}
	}

	public void clear() {
		pendingEvents.clear();
		scheduledLayers.clear();
		activeLevel = null;
	}

	private void processPending(final ClientLevel clientLevel, final Player listener) {
		Iterator<Map.Entry<UUID, PendingAcousticEvent>> iterator = pendingEvents.entrySet().iterator();
		while (iterator.hasNext()) {
			PendingAcousticEvent pending = iterator.next().getValue();
			ClientboundAcousticEventPayload payload = pending.payload();
			AcousticSoundDefinition definition = AcousticSoundRegistry.get(payload.definitionId()).orElse(null);
			if (definition == null) {
				iterator.remove();
				continue;
			}

			long elapsedTicks = clientLevel.getGameTime() - payload.emissionGameTime();
			Vec3 sourcePosition = pending.sourcePosition();
			Vec3 listenerPosition = listener.position();
			Vec3 listenerEyePosition = listener.getEyePosition();
			double listenerDistance = listenerPosition.distanceTo(sourcePosition);
			double waveRadius = AcousticPropagation.waveRadiusBlocks(
				elapsedTicks,
				definition.propagationSpeedBlocksPerSecond()
			);
			long expectedArrival = AcousticPropagation.delayTicks(listenerDistance, definition.propagationSpeedBlocksPerSecond());

			if (waveRadius >= listenerDistance) {
				activate(payload, definition, sourcePosition, listenerDistance, listenerEyePosition);
				iterator.remove();
			} else if (elapsedTicks > expectedArrival + MAX_LIFETIME_AFTER_EXPECTED_ARRIVAL) {
				iterator.remove();
			}
		}
	}

	private void activate(
		final ClientboundAcousticEventPayload payload,
		final AcousticSoundDefinition definition,
		final Vec3 sourcePosition,
		final double listenerDistance,
		final Vec3 listenerEyePosition
	) {
		int variationIndex = Math.floorMod(Long.hashCode(payload.randomSeed()), definition.variants().size());
		AcousticSoundVariant variation = definition.variants().get(variationIndex);
		AcousticEnvironment environment = AcousticEnvironmentProbe.probe(activeLevel, listenerEyePosition);
		// The eye position is sampled once so every layer and echo sees the same local environment.
		int echoCount = 0;
		double[] gains = new double[4];
		for (int i = 0; i < variation.layers().size(); i++) {
			AcousticLayer layer = variation.layers().get(i);
			double gain = AcousticAttenuation.gain(listenerDistance, layer, payload.volume());
			gains[i] = gain;
			if (gain < definition.minimumAudibleGain()) {
				continue;
			}

			schedule(new ScheduledAcousticLayer(
				clientTick + layer.additionalDelayTicks(),
				sourcePosition,
				layer.soundEventId(),
				payload.soundSource(),
				(float)gain,
				payload.pitch() * layer.pitchMultiplier(),
				payload.randomSeed() ^ layer.soundEventId().hashCode(),
				false
			));

			if (definition.environmentEchoesEnabled()
				&& layer.echoable()
				&& layer.band() != AcousticFrequencyBand.TRANSIENT) {
				echoCount += scheduleEchoes(payload, definition, sourcePosition, layer, gain, environment, echoCount);
			}
		}

		if (SharedConstants.IS_RUNNING_IN_IDE) {
			WarMod.LOGGER.info(
				"Acoustic event {} activated: definition={}, distance={}, delay={}, variation={}, gains=[{},{},{},{}], enclosure={}, echoes={}",
				payload.eventId(),
				payload.definitionId(),
				String.format(java.util.Locale.ROOT, "%.1f", listenerDistance),
				AcousticPropagation.delayTicks(listenerDistance, definition.propagationSpeedBlocksPerSecond()),
				variationIndex,
				String.format(java.util.Locale.ROOT, "%.3f", gains[0]),
				String.format(java.util.Locale.ROOT, "%.3f", gains[1]),
				String.format(java.util.Locale.ROOT, "%.3f", gains[2]),
				String.format(java.util.Locale.ROOT, "%.3f", gains[3]),
				String.format(java.util.Locale.ROOT, "%.2f", environment.enclosure()),
				echoCount
			);
		}
	}

	private int scheduleEchoes(
		final ClientboundAcousticEventPayload payload,
		final AcousticSoundDefinition definition,
		final Vec3 sourcePosition,
		final AcousticLayer layer,
		final double originalGain,
		final AcousticEnvironment environment,
		final int currentEchoCount
	) {
		if (environment.enclosure() < 0.40 || currentEchoCount >= 6) {
			return 0;
		}
		int firstDelay = (int)Math.round((2.0 * environment.averageReflectionDistance() / 343.0) * 20.0);
		firstDelay = Math.max(2, Math.min(20, firstDelay));
		float firstVolume = (float)(originalGain * (0.10 + environment.enclosure() * 0.20));
		int added = 0;
		if (firstVolume >= definition.minimumAudibleGain()) {
			schedule(new ScheduledAcousticLayer(
				clientTick + layer.additionalDelayTicks() + firstDelay,
				sourcePosition,
				layer.soundEventId(),
				payload.soundSource(),
				firstVolume,
				payload.pitch() * layer.pitchMultiplier() * 0.98F,
				payload.randomSeed() ^ layer.soundEventId().hashCode() ^ 0x5EEDL,
				true
			));
			added++;
		}

		if (environment.enclosure() >= 0.70 && currentEchoCount + added < 6) {
			float secondVolume = firstVolume * 0.45F;
			if (secondVolume >= definition.minimumAudibleGain()) {
				schedule(new ScheduledAcousticLayer(
					clientTick + layer.additionalDelayTicks() + firstDelay + Math.round(firstDelay * 1.7F),
					sourcePosition,
					layer.soundEventId(),
					payload.soundSource(),
					secondVolume,
					payload.pitch() * layer.pitchMultiplier() * 0.96F,
					payload.randomSeed() ^ layer.soundEventId().hashCode() ^ 0xEC050L,
					true
				));
				added++;
			}
		}
		return added;
	}

	private void schedule(final ScheduledAcousticLayer layer) {
		if (scheduledLayers.size() < MAX_SCHEDULED_LAYERS) {
			scheduledLayers.offer(layer);
		}
	}
}
