package com.andye.warmod.acoustics.client;

import com.andye.warmod.WarMod;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

public final class AcousticSoundInstance extends AbstractSoundInstance {
	private static final Set<Identifier> MISSING_SOUND_WARNINGS = ConcurrentHashMap.newKeySet();

	public AcousticSoundInstance(final ScheduledAcousticLayer layer) {
		super(layer.soundEventId(), layer.soundSource(), RandomSource.create(layer.seed()));
		this.volume = layer.volume();
		this.pitch = layer.pitch();
		this.x = layer.sourcePosition().x;
		this.y = layer.sourcePosition().y;
		this.z = layer.sourcePosition().z;
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
