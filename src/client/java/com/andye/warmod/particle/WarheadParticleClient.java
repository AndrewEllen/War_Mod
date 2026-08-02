package com.andye.warmod.particle;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;

public final class WarheadParticleClient {
	private WarheadParticleClient() {
	}

	public static void register() {
		ParticleProviderRegistry.getInstance().register(ModParticleTypes.WARHEAD_FIREBALL, WarheadFireballParticleProvider::new);
		ParticleProviderRegistry.getInstance().register(ModParticleTypes.WARHEAD_SMOKE, WarheadSmokeParticleProvider::new);
	}
}
