package com.andye.warmod.acoustics.client;

import com.andye.warmod.acoustics.ModSoundEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Listener-relative ringing and reversible category ducking after a pressure wave arrives. */
public final class ShockwaveHearingManager {
	public static final ShockwaveHearingManager INSTANCE = new ShockwaveHearingManager();
	private static final int ATTACK_TICKS = 2;
	private static final int MAX_RINGING_TICKS = 590;
	private static final double PRESSURE_REFERENCE_DISTANCE = 64.0;
	private static final double MINIMUM_PRESSURE_EXPOSURE = 0.18;
	private static final double FULL_INTENSITY_SPAN = 1.40;

	private RingingSound activeSound;
	private long soundStartTick;
	private long startTick;
	private long endTick;
	private double intensity;
	private float appliedGain = 1.0F;
	private long pendingTick = Long.MAX_VALUE;
	private double pendingExposure;

	private ShockwaveHearingManager() { }

	/** Defers hearing damage by one rendered shock-front step instead of allowing
	 * the tick-side sound update to lead the visible pressure shell. */
	public void schedule(final float eventVolume, final double listenerDistance,
		final double transmissionGain, final long triggerTick) {
		if (!Float.isFinite(eventVolume) || eventVolume <= 0.0F
			|| !Double.isFinite(listenerDistance) || listenerDistance < 0.0
			|| !Double.isFinite(transmissionGain) || transmissionGain <= 0.0) return;
		double scaledDistance = listenerDistance / PRESSURE_REFERENCE_DISTANCE;
		double pressureExposure = eventVolume * Math.min(1.0, transmissionGain)
			/ (1.0 + scaledDistance * scaledDistance);
		if (pressureExposure <= MINIMUM_PRESSURE_EXPOSURE) return;
		pendingExposure = Math.max(pendingExposure, pressureExposure);
		pendingTick = Math.min(pendingTick, triggerTick);
	}

	private void trigger(final Minecraft minecraft, final double acousticExposure,
		final long now) {
		if (minecraft.level == null || !Double.isFinite(acousticExposure)) return;
		double newIntensity = Mth.clamp(
			(acousticExposure - MINIMUM_PRESSURE_EXPOSURE) / FULL_INTENSITY_SPAN,
			0.0, 1.0);
		if (newIntensity <= 0.0) return;

		RingingProfile profile = newIntensity >= 0.62 ? RingingProfile.HEAVY
			: newIntensity >= 0.28 ? RingingProfile.MEDIUM : RingingProfile.LIGHT;
		if (activeSound != null) {
			/* Overlapping pressure waves compound and extend the existing ringing.
			 * Do not restart the recording at its attack transient. */
			this.intensity = Math.min(1.0, 1.0
				- (1.0 - intensity) * (1.0 - newIntensity * 0.35));
			this.endTick = Math.min(soundStartTick + MAX_RINGING_TICKS,
				Math.max(endTick, now + profile.durationTicks()));
			this.activeSound.setIntensity(this.intensity);
			return;
		}

		this.intensity = newIntensity;
		this.soundStartTick = now;
		this.startTick = now;
		this.endTick = now + profile.durationTicks();
		this.activeSound = new RingingSound(profile.soundEvent(), this.intensity);
		minecraft.getSoundManager().play(activeSound);
	}

	public void tick(final Minecraft minecraft, final long gameTick) {
		if (pendingExposure > 0.0 && gameTick >= pendingTick) {
			double exposure = pendingExposure;
			pendingExposure = 0.0;
			pendingTick = Long.MAX_VALUE;
			trigger(minecraft, exposure, gameTick);
		}
		if (activeSound == null) return;
		if (gameTick >= endTick) {
			finish(minecraft);
			return;
		}
		double age = Math.max(0.0, gameTick - startTick);
		double duration = Math.max(1.0, endTick - startTick);
		double attack = smooth(Math.min(1.0, age / ATTACK_TICKS));
		double recoveryStart = duration * 0.42;
		double recovery = age <= recoveryStart ? 0.0
			: smooth(Math.min(1.0, (age - recoveryStart) / (duration - recoveryStart)));
		activeSound.setEnvelope(1.0 - recovery);
		double ducking = 0.40 + intensity * 0.52;
		float gain = (float) Math.max(0.08,
			1.0 - ducking * attack * (1.0 - recovery));
		applyCategoryGain(minecraft, gain);
	}

	public void clear(final Minecraft minecraft) {
		finish(minecraft);
	}

	private void finish(final Minecraft minecraft) {
		if (activeSound != null) minecraft.getSoundManager().stop(activeSound);
		activeSound = null;
		soundStartTick = 0L;
		startTick = 0L;
		endTick = 0L;
		intensity = 0.0;
		pendingExposure = 0.0;
		pendingTick = Long.MAX_VALUE;
		applyCategoryGain(minecraft, 1.0F);
	}

	private void applyCategoryGain(final Minecraft minecraft, final float gain) {
		if (Math.abs(gain - appliedGain) < 0.002F) return;
		for (SoundSource source : SoundSource.values()) {
			if (source != SoundSource.MASTER) {
				minecraft.getSoundManager().updateCategoryVolume(source, gain);
			}
		}
		appliedGain = gain;
	}

	private static double smooth(final double value) {
		return value * value * (3.0 - 2.0 * value);
	}

	private enum RingingProfile {
		LIGHT(ModSoundEvents.EAR_RINGING_LIGHT, 160),
		MEDIUM(ModSoundEvents.EAR_RINGING_MEDIUM, 300),
		HEAVY(ModSoundEvents.EAR_RINGING_HEAVY, 500);

		private final SoundEvent soundEvent;
		private final int durationTicks;

		RingingProfile(final SoundEvent soundEvent, final int durationTicks) {
			this.soundEvent = soundEvent;
			this.durationTicks = durationTicks;
		}

		SoundEvent soundEvent() { return soundEvent; }
		int durationTicks() { return durationTicks; }
	}

	private static final class RingingSound extends AbstractTickableSoundInstance {
		private float targetVolume;

		private RingingSound(final SoundEvent soundEvent, final double intensity) {
			super(soundEvent, SoundSource.MASTER, RandomSource.create());
			setIntensity(intensity);
			this.pitch = 1.0F;
			this.looping = false;
			this.delay = 0;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.relative = true;
		}

		private void setIntensity(final double intensity) {
			this.targetVolume = (float) Mth.clamp(0.07 + intensity * 0.25, 0.0, 0.32);
			this.volume = targetVolume;
		}

		private void setEnvelope(final double envelope) {
			this.volume = Math.max(0.001F,
				targetVolume * (float) Mth.clamp(envelope, 0.0, 1.0));
		}

		@Override public boolean canStartSilent() { return true; }
		@Override public void tick() { }
	}
}
