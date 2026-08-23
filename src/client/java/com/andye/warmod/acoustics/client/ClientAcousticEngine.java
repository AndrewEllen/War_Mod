package com.andye.warmod.acoustics.client;

import com.andye.warmod.WarMod;
import com.andye.warmod.acoustics.AcousticSoundRegistry;
import com.andye.warmod.acoustics.AcousticSounds;
import com.andye.warmod.acoustics.model.AcousticDistanceSound;
import com.andye.warmod.acoustics.model.AcousticResponseProfile;
import com.andye.warmod.acoustics.model.AcousticSoundDefinition;
import com.andye.warmod.acoustics.network.ClientboundAcousticEventPayload;
import com.andye.warmod.acoustics.physics.AcousticAttenuation;
import com.andye.warmod.acoustics.physics.AcousticPropagation;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
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

	private static final int MAX_PENDING_EVENTS = 512;
	private static final int MAX_SCHEDULED_SOUNDS = 2048;
	private static final long MAX_LIFETIME_AFTER_EXPECTED_ARRIVAL = 100L;

	private final Map<UUID, PendingAcousticEvent> pendingEvents = new LinkedHashMap<>();
	private final PriorityQueue<ScheduledAcousticSound> scheduledSounds = new PriorityQueue<>(
		Comparator.comparingLong(ScheduledAcousticSound::playbackClientTick)
	);
	private long clientTick;
	private ClientLevel activeLevel;

	private ClientAcousticEngine() {
	}

	public void accept(final ClientboundAcousticEventPayload payload) {
		if (payload == null || !payload.isWellFormed()
			|| !AcousticSoundRegistry.contains(payload.definitionId())) return;
		if (pendingEvents.containsKey(payload.eventId())) return;
		while (pendingEvents.size() >= MAX_PENDING_EVENTS) {
			Iterator<UUID> iterator = pendingEvents.keySet().iterator();
			if (!iterator.hasNext()) break;
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
		if (activeLevel != null && activeLevel != clientLevel) clear();
		activeLevel = clientLevel;
		clientTick = clientLevel.getGameTime();
		ExplosionShakeManager.INSTANCE.tick(clientTick);
		processPending(clientLevel, listener);
		while (!scheduledSounds.isEmpty()
			&& scheduledSounds.peek().playbackClientTick() <= clientTick) {
			minecraft.getSoundManager().play(new AcousticSoundInstance(scheduledSounds.poll()));
		}
	}

	public void clear() {
		pendingEvents.clear();
		scheduledSounds.clear();
		activeLevel = null;
		ExplosionShakeManager.INSTANCE.clear();
	}

	private void processPending(final ClientLevel clientLevel, final Player listener) {
		Iterator<Map.Entry<UUID, PendingAcousticEvent>> iterator = pendingEvents.entrySet().iterator();
		while (iterator.hasNext()) {
			PendingAcousticEvent pending = iterator.next().getValue();
			ClientboundAcousticEventPayload payload = pending.payload();
			AcousticSoundDefinition definition =
				AcousticSoundRegistry.get(payload.definitionId()).orElse(null);
			if (definition == null) {
				iterator.remove();
				continue;
			}

			long elapsedTicks = clientLevel.getGameTime() - payload.emissionGameTime();
			Vec3 sourcePosition = pending.sourcePosition();
			Vec3 listenerPosition = listener.position();
			Vec3 listenerEyePosition = listener.getEyePosition();
			double listenerDistance = listenerPosition.distanceTo(sourcePosition);
			double waveRadius = AcousticPropagation.waveRadiusBlocks(elapsedTicks,
				definition.propagationSpeedBlocksPerSecond());
			long expectedArrival = AcousticPropagation.delayTicks(listenerDistance,
				definition.propagationSpeedBlocksPerSecond());

			if (waveRadius >= listenerDistance) {
				activate(payload, definition, sourcePosition, listenerDistance,
					listenerEyePosition, expectedArrival);
				iterator.remove();
			} else if (elapsedTicks > expectedArrival + MAX_LIFETIME_AFTER_EXPECTED_ARRIVAL) {
				iterator.remove();
			}
		}
	}

	private void activate(final ClientboundAcousticEventPayload payload,
		final AcousticSoundDefinition definition, final Vec3 sourcePosition,
		final double listenerDistance, final Vec3 listenerEyePosition,
		final long expectedArrival) {
		AcousticDistanceSound selectedSound =
			definition.soundForDistance(listenerDistance).orElse(null);
		if (selectedSound == null) return;

		double gain = AcousticAttenuation.gain(listenerDistance, selectedSound, payload.volume());
		AcousticEnvironment environment = AcousticEnvironmentProbe.probe(activeLevel,
			sourcePosition, listenerEyePosition, definition.responseProfile());
		gain *= environment.transmissionGain(definition.responseProfile());
		float primaryPitch = Math.max(0.10F,
			payload.pitch() * selectedSound.pitchMultiplier()
				* deterministicPitch(payload.randomSeed())
				* environment.transmissionPitch(definition.responseProfile()));
		int echoCount = 0;
		if (gain >= definition.minimumAudibleGain()) {
			schedule(new ScheduledAcousticSound(clientTick, sourcePosition,
				selectedSound.soundEventId(), payload.soundSource(), (float) gain,
				primaryPitch,
				payload.randomSeed() ^ selectedSound.soundEventId().hashCode(), false));

			if (payload.definitionId().equals(AcousticSounds.LARGE_EXPLOSION_ID)) {
				ExplosionShakeManager.INSTANCE.add(payload.randomSeed(), listenerDistance, gain);
			}

			if (definition.environmentEchoesEnabled()) {
				echoCount = scheduleEchoes(payload, definition, sourcePosition,
					selectedSound, gain, primaryPitch, environment, listenerDistance);
			}
		}

		if (SharedConstants.IS_RUNNING_IN_IDE) {
			WarMod.LOGGER.info(
				"Acoustic event {} activated: distance={} blocks, profile={}, gain={}, pitch={}, propagationDelay={} ticks ({} s), echoes={}",
				payload.eventId(), String.format(Locale.ROOT, "%.1f", listenerDistance),
				selectedSound.profile(), String.format(Locale.ROOT, "%.3f", gain),
				String.format(Locale.ROOT, "%.3f", primaryPitch), expectedArrival,
				String.format(Locale.ROOT, "%.2f", expectedArrival / 20.0), echoCount);
		}
	}

	private int scheduleEchoes(final ClientboundAcousticEventPayload payload,
		final AcousticSoundDefinition definition, final Vec3 sourcePosition,
		final AcousticDistanceSound selectedSound, final double primaryGain,
		final float primaryPitch, final AcousticEnvironment environment,
		final double listenerDistance) {
		AcousticResponseProfile response = definition.responseProfile();
		int added = scheduleTerrainEchoes(payload, definition, selectedSound,
			primaryGain, primaryPitch, environment, response, listenerDistance);
		if (added >= response.maximumEchoes()) return added;

		double reflection = environment.reflectionStrength(response);
		if (reflection < 0.16) return added;
		int firstDelay = (int) Math.round((2.0 * environment.effectiveReflectionDistance()
			/ definition.propagationSpeedBlocksPerSecond()) * 20.0);
		firstDelay = Math.max(2, Math.min(40, firstDelay));
		double firstRatio = Math.min(response.maximumEchoVolumeRatio(),
			(0.06 + reflection * 0.18) * response.reflectionGain());
		float firstVolume = (float) (primaryGain * firstRatio);
		if (firstVolume >= definition.minimumAudibleGain()) {
			schedule(new ScheduledAcousticSound(clientTick + firstDelay, sourcePosition,
				selectedSound.soundEventId(), payload.soundSource(), firstVolume,
				Math.max(0.10F, primaryPitch * 0.99F),
				payload.randomSeed() ^ selectedSound.soundEventId().hashCode() ^ 0x5EEDL,
				true));
			added++;
		}
		if (added < response.maximumEchoes()
			&& (environment.enclosure() >= 0.70 || environment.terrainRelief() >= 0.55)) {
			float secondVolume = (float) (primaryGain
				* (0.035 + reflection * 0.065) * response.reflectionGain());
			if (secondVolume >= definition.minimumAudibleGain()) {
				schedule(new ScheduledAcousticSound(
					clientTick + firstDelay + Math.round(firstDelay * 1.7F), sourcePosition,
					selectedSound.soundEventId(), payload.soundSource(), secondVolume,
					Math.max(0.10F, primaryPitch * 1.01F),
					payload.randomSeed() ^ selectedSound.soundEventId().hashCode() ^ 0xEC050L,
					true));
				added++;
			}
		}
		return added;
	}

	private int scheduleTerrainEchoes(final ClientboundAcousticEventPayload payload,
		final AcousticSoundDefinition definition, final AcousticDistanceSound selectedSound,
		final double primaryGain, final float primaryPitch,
		final AcousticEnvironment environment, final AcousticResponseProfile response,
		final double directDistance) {
		if (!response.distantTerrainReflections() || !environment.openSky()) return 0;
		int added = 0;
		for (AcousticReflection reflection : environment.terrainReflections()) {
			if (added >= response.maximumEchoes()) break;
			double reflectedPath = Math.max(directDistance, reflection.reflectedPathLength());
			long delay = Math.max(2L, AcousticPropagation.reflectedDelayTicks(directDistance,
				reflectedPath, definition.propagationSpeedBlocksPerSecond()));
			double pathGain = Math.sqrt((directDistance + 32.0) / (reflectedPath + 32.0));
			double ratio = Math.min(response.maximumEchoVolumeRatio(),
				(0.10 + reflection.strength() * 0.26) * response.reflectionGain());
			float volume = (float) (primaryGain * pathGain * ratio);
			if (volume < definition.minimumAudibleGain()) continue;
			schedule(new ScheduledAcousticSound(clientTick + delay, reflection.position(),
				selectedSound.soundEventId(), payload.soundSource(), volume,
				Math.max(0.10F, primaryPitch * 0.985F),
				payload.randomSeed() ^ selectedSound.soundEventId().hashCode()
					^ reflection.position().hashCode(), true));
			added++;
		}
		return added;
	}

	private static float deterministicPitch(final long seed) {
		long mixed = seed ^ seed >>> 33;
		mixed *= 0xff51afd7ed558ccdL;
		mixed ^= mixed >>> 33;
		double unit = (mixed & 0xFFFFL) / 65535.0;
		return (float) (0.98 + unit * 0.04);
	}

	private void schedule(final ScheduledAcousticSound sound) {
		if (scheduledSounds.size() < MAX_SCHEDULED_SOUNDS) scheduledSounds.offer(sound);
	}
}
