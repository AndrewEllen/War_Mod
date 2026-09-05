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
	private static final int MAX_NEW_ENVIRONMENT_PROBES_PER_TICK = 2;

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
		ShockwaveHearingManager.INSTANCE.tick(minecraft, clientTick);
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
		ShockwaveHearingManager.INSTANCE.clear(Minecraft.getInstance());
		ExplosionShakeManager.INSTANCE.clear();
		AcousticEnvironmentCache.INSTANCE.clear();
	}

	private void processPending(final ClientLevel clientLevel, final Player listener) {
		int remainingNewProbes = MAX_NEW_ENVIRONMENT_PROBES_PER_TICK;
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
			Vec3 listenerEyePosition = listener.getEyePosition();
			double listenerDistance = listenerEyePosition.distanceTo(sourcePosition);
			double waveRadius = AcousticPropagation.waveRadiusBlocks(elapsedTicks,
				definition.propagationSpeedBlocksPerSecond());
			long expectedArrival = AcousticPropagation.delayTicks(listenerDistance,
				definition.propagationSpeedBlocksPerSecond());

			if (waveRadius >= listenerDistance) {
				boolean cached = AcousticEnvironmentCache.INSTANCE.contains(clientLevel,
					sourcePosition, listenerEyePosition, definition.responseProfile(), clientTick);
				if (!cached && remainingNewProbes <= 0) continue;
				if (!cached) remainingNewProbes--;
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
		AcousticDistanceSound distanceSound =
			definition.soundForDistance(listenerDistance).orElse(null);
		if (distanceSound == null) return;

		double unobstructedGain = AcousticAttenuation.gain(
			listenerDistance, distanceSound, payload.volume());
		AcousticEnvironment environment = AcousticEnvironmentCache.INSTANCE.probe(activeLevel,
			sourcePosition, listenerEyePosition, definition.responseProfile(), clientTick);
		double transmissionGain = environment.transmissionGain(definition.responseProfile());
		double gain = unobstructedGain * transmissionGain;
		AcousticDistanceSound playbackSound = soundForTransmission(
			definition, distanceSound, environment);
		float primaryPitch = Math.max(0.10F,
			payload.pitch() * playbackSound.pitchMultiplier()
				* deterministicPitch(payload.randomSeed())
				* environment.transmissionPitch(definition.responseProfile()));
		int echoCount = 0;
		if (gain >= definition.minimumAudibleGain()) {
			schedule(new ScheduledAcousticSound(clientTick, sourcePosition,
				playbackSound.soundEventId(), payload.soundSource(), (float) gain,
				primaryPitch,
				payload.randomSeed() ^ playbackSound.soundEventId().hashCode(), false));

			if (payload.definitionId().equals(AcousticSounds.LARGE_EXPLOSION_ID)) {
				ExplosionShakeManager.INSTANCE.add(payload.randomSeed(), listenerDistance, gain);
			}
			if (definition.responseProfile() == AcousticResponseProfile.EXPLOSION) {
				ShockwaveHearingManager.INSTANCE.schedule(payload.volume(), listenerDistance,
					transmissionGain, clientTick + 1L);
			}

			if (definition.environmentEchoesEnabled()) {
				echoCount = scheduleEchoes(payload, definition, sourcePosition,
					unobstructedGain, gain, environment, listenerDistance);
			}
		}

		if (SharedConstants.IS_RUNNING_IN_IDE) {
			WarMod.LOGGER.info(
				"Acoustic event {} activated: distance={} blocks, profile={}, gain={}, pitch={}, propagationDelay={} ticks ({} s), echoes={}",
				payload.eventId(), String.format(Locale.ROOT, "%.1f", listenerDistance),
				playbackSound.profile(), String.format(Locale.ROOT, "%.3f", gain),
				String.format(Locale.ROOT, "%.3f", primaryPitch), expectedArrival,
				String.format(Locale.ROOT, "%.2f", expectedArrival / 20.0), echoCount);
		}
	}

	private int scheduleEchoes(final ClientboundAcousticEventPayload payload,
		final AcousticSoundDefinition definition, final Vec3 sourcePosition,
		final double unobstructedGain, final double primaryGain,
		final AcousticEnvironment environment,
		final double listenerDistance) {
		AcousticResponseProfile response = definition.responseProfile();
		int added = scheduleTerrainEchoes(payload, definition, unobstructedGain,
			environment, response, listenerDistance);
		if (added >= response.maximumEchoes()) return added;

		double reflection = environment.reflectionStrength(response);
		if (reflection < 0.16) return added;
		double reflectionDistance = environment.effectiveReflectionDistance();
		double firstPath = listenerDistance + 2.0 * reflectionDistance;
		long firstDelay = Math.max(2L, AcousticPropagation.reflectedDelayTicks(
			listenerDistance, firstPath, definition.propagationSpeedBlocksPerSecond()));
		firstDelay = Math.min(40L, firstDelay);
		double firstRatio = (0.075 + reflection * 0.16) * response.reflectionGain();
		if (scheduleReflectedSound(payload, definition, sourcePosition, primaryGain,
			response, listenerDistance, firstPath, firstDelay, firstRatio, 0x5EEDL)) {
			added++;
		}
		if (added < response.maximumEchoes()
			&& (environment.enclosure() >= 0.70 || environment.terrainRelief() >= 0.55)) {
			double secondPath = listenerDistance + 4.6 * reflectionDistance;
			long secondDelay = Math.max(firstDelay + 2L,
				AcousticPropagation.reflectedDelayTicks(listenerDistance, secondPath,
					definition.propagationSpeedBlocksPerSecond()));
			double secondRatio = (0.040 + reflection * 0.060) * response.reflectionGain();
			if (scheduleReflectedSound(payload, definition, sourcePosition, primaryGain,
				response, listenerDistance, secondPath, secondDelay, secondRatio, 0xEC050L)) {
				added++;
			}
		}
		return added;
	}

	private int scheduleTerrainEchoes(final ClientboundAcousticEventPayload payload,
		final AcousticSoundDefinition definition, final double primaryGain,
		final AcousticEnvironment environment, final AcousticResponseProfile response,
		final double directDistance) {
		if (!response.distantTerrainReflections() || !environment.openSky()) return 0;
		int added = 0;
		for (AcousticReflection reflection : environment.terrainReflections()) {
			if (added >= response.maximumEchoes()) break;
			double reflectedPath = Math.max(directDistance, reflection.reflectedPathLength());
			long delay = Math.max(2L, AcousticPropagation.reflectedDelayTicks(directDistance,
				reflectedPath, definition.propagationSpeedBlocksPerSecond()));
			double ratio = (0.11 + reflection.strength() * 0.20)
				* response.reflectionGain();
			if (scheduleReflectedSound(payload, definition, reflection.position(),
				primaryGain * reflection.pathTransmission(), response,
				directDistance, reflectedPath, delay, ratio,
				reflection.position().hashCode())) added++;
		}
		return added;
	}

	private boolean scheduleReflectedSound(final ClientboundAcousticEventPayload payload,
		final AcousticSoundDefinition definition, final Vec3 playbackPosition,
		final double primaryGain, final AcousticResponseProfile response,
		final double directPath, final double reflectedPath,
		final long delay, final double reflectionRatio, final long seedSalt) {
		AcousticDistanceSound reflectedSound = definition.soundForDistance(reflectedPath)
			.orElse(null);
		if (reflectedSound == null) return false;
		/* Primary gain already contains direct-path attenuation and occlusion. Apply
		 * only the additional reflected-path loss here; re-running the complete
		 * attenuation model made physically large returns practically inaudible. */
		double pathGain = Math.pow((Math.max(0.0, directPath) + 32.0)
			/ (Math.max(directPath, reflectedPath) + 32.0), 0.45);
		double volume = primaryGain * pathGain * Math.min(
			response.maximumEchoVolumeRatio(), reflectionRatio);
		if (volume < definition.minimumAudibleGain()) return false;
		float pitch = Math.max(0.10F, payload.pitch() * reflectedSound.pitchMultiplier()
			* deterministicPitch(payload.randomSeed())
			* (response == AcousticResponseProfile.EXPLOSION ? 0.94F : 0.955F));
		schedule(new ScheduledAcousticSound(clientTick + delay, playbackPosition,
			reflectedSound.soundEventId(), payload.soundSource(), (float) volume, pitch,
			payload.randomSeed() ^ reflectedSound.soundEventId().hashCode() ^ seedSalt, true));
		return true;
	}

	private static AcousticDistanceSound soundForTransmission(
		final AcousticSoundDefinition definition,
		final AcousticDistanceSound distanceSound,
		final AcousticEnvironment environment) {
		double loss = environment.highFrequencyLoss();
		int shift = loss >= 0.88 ? 2 : loss >= 0.62 ? 1 : 0;
		if (shift == 0) return distanceSound;
		int index = Math.min(definition.distanceSounds().size() - 1,
			distanceSound.profile().ordinal() + shift);
		return definition.distanceSounds().get(index);
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
