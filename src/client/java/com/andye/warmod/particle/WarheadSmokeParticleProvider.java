package com.andye.warmod.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

public final class WarheadSmokeParticleProvider implements ParticleProvider<SimpleParticleType> {
	private final SpriteSet sprites;
	public WarheadSmokeParticleProvider(final SpriteSet sprites) { this.sprites = sprites; }
	@Override
	public WarheadSmokeParticle createParticle(final SimpleParticleType options, final ClientLevel level,
		final double x, final double y, final double z, final double xVelocity, final double yVelocity,
		final double zVelocity, final RandomSource random) {
		return new WarheadSmokeParticle(level, x, y, z, xVelocity, yVelocity, zVelocity, this.sprites, this.sprites.get(random));
	}
}