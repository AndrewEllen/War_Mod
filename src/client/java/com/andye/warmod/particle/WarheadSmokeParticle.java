package com.andye.warmod.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;

public final class WarheadSmokeParticle extends SingleQuadParticle {
	private final SpriteSet sprites;
	private final float initialSize;
	private final float maximumSize;

	public WarheadSmokeParticle(final ClientLevel level, final double x, final double y, final double z,
		final double xVelocity, final double yVelocity, final double zVelocity, final SpriteSet sprites,
		final TextureAtlasSprite sprite) {
		super(level, x, y, z, xVelocity, yVelocity, zVelocity, sprite);
		this.sprites = sprites;
		this.initialSize = 3.0F + this.random.nextFloat() * 3.0F;
		this.maximumSize = Math.min(14.0F, Math.max(7.0F, this.initialSize * (2.0F + this.random.nextFloat() * 0.35F)));
		this.quadSize = this.initialSize;
		this.lifetime = 80 + this.random.nextInt(101);
		this.gravity = -0.0015F;
		this.friction = 0.992F;
		this.hasPhysics = false;
		float shade = 0.10F + this.random.nextFloat() * 0.16F;
		this.setColor(shade, shade * 1.04F, shade * 1.12F);
		this.setAlpha(0.88F);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.removed) return;
		this.setSpriteFromAge(this.sprites);
		float progress = Mth.clamp((this.age + 0.5F) / this.lifetime, 0.0F, 1.0F);
		float growth = 1.0F - (1.0F - progress) * (1.0F - progress);
		this.quadSize = Mth.lerp(growth, this.initialSize, this.maximumSize);
		this.setAlpha(progress < 0.75F ? 0.88F : 0.88F * (1.0F - (progress - 0.75F) / 0.25F));
	}

	@Override
	protected Layer getLayer() { return Layer.TRANSLUCENT; }
}