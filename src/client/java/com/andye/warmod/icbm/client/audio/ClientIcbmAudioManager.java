package com.andye.warmod.icbm.client.audio;

import com.andye.warmod.WarMod;
import com.andye.warmod.acoustics.AcousticSoundRegistry;
import com.andye.warmod.acoustics.AcousticSounds;
import com.andye.warmod.acoustics.ModSoundEvents;
import com.andye.warmod.acoustics.model.AcousticDistanceProfile;
import com.andye.warmod.acoustics.model.AcousticDistanceSound;
import com.andye.warmod.acoustics.model.AcousticSoundDefinition;
import com.andye.warmod.acoustics.physics.AcousticAttenuation;
import com.andye.warmod.icbm.IcbmConstants;
import com.andye.warmod.icbm.client.IcbmVisualState;
import com.andye.warmod.icbm.network.ClientboundIcbmLaunchPayload;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;

public final class ClientIcbmAudioManager {
	public static final ClientIcbmAudioManager INSTANCE = new ClientIcbmAudioManager();
	private final Map<UUID, IcbmEngineAudioState> states = new LinkedHashMap<>();
	private ClientLevel activeLevel;
	private static boolean registered;
	private ClientIcbmAudioManager() { }

	public static void register() {
		if (registered) return;
		ClientTickEvents.END_CLIENT_TICK.register(INSTANCE::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> INSTANCE.clear(client));
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> INSTANCE.clear(client));
		registered = true;
	}

	public synchronized void acceptLaunch(final ClientboundIcbmLaunchPayload payload) {
		if (!payload.isWellFormed()) return;
		while (this.states.size() >= IcbmConstants.MAX_ACTIVE_CLIENT_ICBMS) {
			Map.Entry<UUID, IcbmEngineAudioState> quietest = this.states.entrySet().stream()
				.min(Comparator.comparingDouble(entry -> entry.getValue().currentGain)).orElse(null);
			if (quietest == null) break;
			quietest.getValue().cancelled = true;
			fadeAll(quietest.getValue(), 12);
			this.states.remove(quietest.getKey());
		}
		this.states.put(payload.missileId(), new IcbmEngineAudioState(IcbmVisualState.fromPayload(payload).flightPlan()));
	}

	public synchronized void acceptCancellation(final UUID missileId) {
		IcbmEngineAudioState state = this.states.get(missileId);
		if (state != null) { state.cancelled = true; fadeAll(state, 12); }
	}

	public synchronized void tick(final Minecraft client) {
		if (client.level == null || client.player == null) { clear(client); return; }
		if (this.activeLevel != null && this.activeLevel != client.level) clear(client);
		this.activeLevel = client.level;
		double currentTime = client.level.getGameTime();
		Vec3 listener = client.player.getEyePosition();
		Iterator<Map.Entry<UUID, IcbmEngineAudioState>> iterator = this.states.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, IcbmEngineAudioState> entry = iterator.next();
			IcbmEngineAudioState state = entry.getValue();
			RetardedTrajectorySampler.Sample sample = RetardedTrajectorySampler.sample(state.flightPlan, currentTime, listener);
			state.apparentPosition = sample.position(); state.apparentDistance = sample.apparentDistance();
			if (!state.cancelled && sample.delayedThrustActive()) {
				if (!state.started) start(client, entry.getKey(), state, sample);
				state.currentGain = gain(sample.apparentDistance(), 0.72F);
				update(state.ignitionSound, sample.position(), state.currentGain * 0.82F);
				update(state.sustainSound, sample.position(), state.currentGain);
			} else if (state.started && !state.shutdownStarted) {
				beginShutdown(client, entry.getKey(), state, sample.position());
			}
			update(state.shutdownSound, sample.position(), state.currentGain * 0.72F);
			if ((state.cancelled || state.shutdownStarted)
				&& stopped(state.ignitionSound) && stopped(state.sustainSound) && stopped(state.shutdownSound)) iterator.remove();
			else if (sample.elapsedTicks() > state.flightPlan.separationTick() + 200.0 && !state.started) iterator.remove();
		}
	}

	private static void start(final Minecraft client, final UUID id, final IcbmEngineAudioState state,
		final RetardedTrajectorySampler.Sample sample) {
		state.selectedProfile = profile(sample.apparentDistance());
		state.currentGain = gain(sample.apparentDistance(), 0.72F);
		state.ignitionSound = new IcbmEngineLoopSound(ignition(state.selectedProfile), false, 4, sample.position());
		state.sustainSound = new IcbmEngineLoopSound(sustain(state.selectedProfile), true, 16, sample.position());
		state.ignitionSound.update(sample.position(), state.currentGain * 0.82F);
		state.sustainSound.update(sample.position(), state.currentGain);
		client.getSoundManager().play(state.ignitionSound);
		client.getSoundManager().play(state.sustainSound);
		state.started = true;
		if (SharedConstants.IS_RUNNING_IN_IDE) WarMod.LOGGER.info(
			"ICBM {} engine audio started: apparentDistance={}, profile={}", id, sample.apparentDistance(), state.selectedProfile);
	}

	private static void beginShutdown(final Minecraft client, final UUID id, final IcbmEngineAudioState state,
		final Vec3 position) {
		state.shutdownStarted = true;
		if (state.ignitionSound != null) state.ignitionSound.fadeOut(state.cancelled ? 10 : 24);
		if (state.sustainSound != null) state.sustainSound.fadeOut(state.cancelled ? 12 : 30);
		if (!state.cancelled && state.selectedProfile != null) {
			state.shutdownSound = new IcbmEngineLoopSound(shutdown(state.selectedProfile), false, 2, position);
			state.shutdownSound.update(position, state.currentGain * 0.72F);
			client.getSoundManager().play(state.shutdownSound);
		}
		if (SharedConstants.IS_RUNNING_IN_IDE) WarMod.LOGGER.info("ICBM {} engine audio entering shutdown tail", id);
	}

	private synchronized void clear(final Minecraft client) {
		for (IcbmEngineAudioState state : this.states.values()) {
			if (state.ignitionSound != null) client.getSoundManager().stop(state.ignitionSound);
			if (state.sustainSound != null) client.getSoundManager().stop(state.sustainSound);
			if (state.shutdownSound != null) client.getSoundManager().stop(state.shutdownSound);
		}
		this.states.clear(); this.activeLevel = null;
	}

	private static void update(final IcbmEngineLoopSound sound, final Vec3 position, final float gain) {
		if (sound != null && !sound.isStopped()) sound.update(position, gain);
	}
	private static boolean stopped(final IcbmEngineLoopSound sound) { return sound == null || sound.isStopped(); }
	private static void fadeAll(final IcbmEngineAudioState state, final int ticks) {
		if (state.ignitionSound != null) state.ignitionSound.fadeOut(ticks);
		if (state.sustainSound != null) state.sustainSound.fadeOut(ticks);
		if (state.shutdownSound != null) state.shutdownSound.fadeOut(ticks);
	}
	private static float gain(final double distance, final float volume) {
		AcousticSoundDefinition definition = AcousticSoundRegistry.get(AcousticSounds.ICBM_ENGINE_RUMBLE_ID).orElse(null);
		AcousticDistanceSound selected = definition == null ? null : definition.soundForDistance(distance).orElse(null);
		return selected == null ? 0.0F : (float)AcousticAttenuation.gain(distance, selected, volume);
	}
	private static AcousticDistanceProfile profile(final double distance) {
		return distance < 140.0 ? AcousticDistanceProfile.NEAR : distance < 400.0 ? AcousticDistanceProfile.MEDIUM
			: distance < 850.0 ? AcousticDistanceProfile.FAR : AcousticDistanceProfile.EXTREME;
	}
	private static SoundEvent ignition(final AcousticDistanceProfile p) { return switch(p) { case NEAR -> ModSoundEvents.MISSILE_ENGINE_IGNITION_NEAR; case MEDIUM -> ModSoundEvents.MISSILE_ENGINE_IGNITION_MEDIUM; case FAR -> ModSoundEvents.MISSILE_ENGINE_IGNITION_FAR; case EXTREME -> ModSoundEvents.MISSILE_ENGINE_IGNITION_EXTREME; }; }
	private static SoundEvent sustain(final AcousticDistanceProfile p) { return switch(p) { case NEAR -> ModSoundEvents.MISSILE_ENGINE_SUSTAIN_NEAR; case MEDIUM -> ModSoundEvents.MISSILE_ENGINE_SUSTAIN_MEDIUM; case FAR -> ModSoundEvents.MISSILE_ENGINE_SUSTAIN_FAR; case EXTREME -> ModSoundEvents.MISSILE_ENGINE_SUSTAIN_EXTREME; }; }
	private static SoundEvent shutdown(final AcousticDistanceProfile p) { return switch(p) { case NEAR -> ModSoundEvents.MISSILE_ENGINE_SHUTDOWN_NEAR; case MEDIUM -> ModSoundEvents.MISSILE_ENGINE_SHUTDOWN_MEDIUM; case FAR -> ModSoundEvents.MISSILE_ENGINE_SHUTDOWN_FAR; case EXTREME -> ModSoundEvents.MISSILE_ENGINE_SHUTDOWN_EXTREME; }; }
}
