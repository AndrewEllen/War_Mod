package com.andye.warmod.acoustics.client;

import com.andye.warmod.WarMod;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

public final class AcousticSoundInstance extends AbstractSoundInstance {
	private static final Set<Identifier> MISSING_SOUND_WARNINGS = ConcurrentHashMap.newKeySet();

	public AcousticSoundInstance(final ScheduledAcousticSound sound) {
		super(sound.soundEventId(), sound.soundSource(), RandomSource.create(sound.seed()));
		this.volume = sound.volume();
		this.pitch = sound.pitch();
		this.x = sound.sourcePosition().x;
		this.y = sound.sourcePosition().y;
		this.z = sound.sourcePosition().z;
		this.looping = false;
		this.delay = 0;
		this.attenuation = SoundInstance.Attenuation.NONE;
		this.relative = false;
	}

	@Override
	public @Nullable WeighedSoundEvents resolve(final SoundManager soundManager) {
		WeighedSoundEvents result = super.resolve(soundManager);
		if (result == null && MISSING_SOUND_WARNINGS.add(getIdentifier())) {
			WarMod.LOGGER.warn("Acoustic sound event is missing: {}", getIdentifier());
		}
		return result;
	}
}