package com.andye.warmod;

import com.andye.warmod.acoustics.client.ClientAcousticNetworking;
import com.andye.warmod.particle.WarheadParticleClient;
import com.andye.warmod.warhead.client.ClientWarheadNetworking;
import com.andye.warmod.warhead.client.render.WarheadWorldRenderer;
import net.fabricmc.api.ClientModInitializer;

public final class WarModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientAcousticNetworking.register();
		ClientWarheadNetworking.register();
		WarheadParticleClient.register();
		WarheadWorldRenderer.register();
		WarMod.LOGGER.info("War Mod client initialized.");
	}
}