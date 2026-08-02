package com.andye.warmod.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.RandomSource;
import net.minecraft.core.particles.SimpleParticleType;

public final class WarheadFireballParticleProvider implements ParticleProvider<SimpleParticleType> {
	private final SpriteSet sprites;

	public WarheadFireballParticleProvider(final SpriteSet sprites) {
		this.sprites = sprites;
	}

	@Override
	public WarheadFireballParticle createParticle(
		final SimpleParticleType options,
		final ClientLevel level,
		final double x,
		final double y,
		final double z,
		final double xVelocity,
		final double yVelocity,
		final double zVelocity,
		final RandomSource random
	) {
		return new WarheadFireballParticle(level, x, y, z, xVelocity, yVelocity, zVelocity, this.sprites, this.sprites.get(random));
	}
}
