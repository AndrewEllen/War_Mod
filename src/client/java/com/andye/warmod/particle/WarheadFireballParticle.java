package com.andye.warmod.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

public final class WarheadFireballParticle extends SingleQuadParticle {
	private final SpriteSet sprites;
	private final float initialQuadSize;

	public WarheadFireballParticle(
		final ClientLevel level,
		final double x,
		final double y,
		final double z,
		final double xVelocity,
		final double yVelocity,
		final double zVelocity,
		final SpriteSet sprites,
		final TextureAtlasSprite sprite
	) {
		super(level, x, y, z, xVelocity, yVelocity, zVelocity, sprite);
		this.sprites = sprites;
		this.initialQuadSize = 2.0F + this.random.nextFloat() * 2.0F;
		this.quadSize = this.initialQuadSize;
		this.lifetime = 14 + this.random.nextInt(11);
		this.gravity = -0.015F;
		this.friction = 0.95F;
		this.hasPhysics = false;
		this.setColor(1.0F, 0.93F, 0.58F);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.removed) {
			return;
		}
		this.setSpriteFromAge(this.sprites);
		float progress = Mth.clamp((this.age + 0.5F) / this.lifetime, 0.0F, 1.0F);
		this.quadSize = this.initialQuadSize * (0.75F + progress * 0.85F);
		if (progress < 0.45F) {
			float t = progress / 0.45F;
			this.setColor(1.0F, 0.98F - t * 0.20F, 0.70F - t * 0.30F);
		} else {
			float t = (progress - 0.45F) / 0.55F;
			this.setColor(1.0F - t * 0.40F, 0.78F - t * 0.28F, 0.40F - t * 0.28F);
		}
		this.setAlpha(1.0F - progress * progress);
	}

	@Override
	protected int getLightCoords(final float partialTickTime) {
		return this.age < this.lifetime / 2 ? LightCoordsUtil.FULL_BRIGHT : super.getLightCoords(partialTickTime);
	}

	@Override
	protected Layer getLayer() {
		return Layer.TRANSLUCENT;
	}
}
