package com.andye.warmod.icbm.client.audio;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class IcbmEngineLoopSound extends AbstractTickableSoundInstance {
	private final int fadeInTicks;
	private int age;
	private int fadeTicksRemaining = -1;
	private int fadeTicksTotal = 1;
	private float targetVolume;

	public IcbmEngineLoopSound(final SoundEvent soundEvent, final boolean looping, final int fadeInTicks,
		final Vec3 position) {
		super(soundEvent, SoundSource.BLOCKS, RandomSource.create());
		this.looping = looping;
		this.delay = 0;
		this.attenuation = SoundInstance.Attenuation.NONE;
		this.relative = false;
		this.fadeInTicks = Math.max(1, fadeInTicks);
		this.volume = 0.001F;
		this.pitch = 1.0F;
		this.update(position, 0.001F);
	}

	public void update(final Vec3 position, final float targetVolume) {
		this.x = position.x; this.y = position.y; this.z = position.z;
		this.targetVolume = Math.max(0.0F, targetVolume);
	}

	public void fadeOut(final int ticks) {
		if (this.fadeTicksRemaining >= 0) return;
		this.fadeTicksTotal = Math.max(1, ticks);
		this.fadeTicksRemaining = this.fadeTicksTotal;
	}

	@Override public boolean canStartSilent() { return true; }

	@Override public void tick() {
		this.age++;
		if (!this.looping && this.age >= 90) { this.volume = 0.0F; this.stop(); return; }
		float fadeIn = Math.min(1.0F, this.age / (float)this.fadeInTicks);
		float fadeOut = 1.0F;
		if (this.fadeTicksRemaining >= 0) {
			fadeOut = this.fadeTicksRemaining / (float)this.fadeTicksTotal;
			this.fadeTicksRemaining--;
			if (this.fadeTicksRemaining < 0) { this.volume = 0.0F; this.stop(); return; }
		}
		this.volume = Math.max(0.001F, this.targetVolume * fadeIn * fadeOut);
	}
}
