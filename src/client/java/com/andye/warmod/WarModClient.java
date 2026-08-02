package com.andye.warmod;

import com.andye.warmod.acoustics.client.ClientAcousticNetworking;
import com.andye.warmod.entity.ModEntityTypes;
import com.andye.warmod.entity.client.WarheadDebrisRenderer;
import com.andye.warmod.icbm.client.ClientIcbmNetworking;
import com.andye.warmod.icbm.client.audio.ClientIcbmAudioManager;
import com.andye.warmod.warhead.client.audio.ClientTerminalAudioManager;
import com.andye.warmod.icbm.client.render.IcbmWorldRenderer;
import com.andye.warmod.particle.WarheadParticleClient;
import com.andye.warmod.warhead.client.ClientWarheadNetworking;
import com.andye.warmod.warhead.client.render.WarheadWorldRenderer;
import com.andye.warmod.warhead.client.render.NuclearFlashOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public final class WarModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientAcousticNetworking.register();
		ClientWarheadNetworking.register();
		ClientIcbmNetworking.register();
		ClientIcbmAudioManager.register();
		ClientTerminalAudioManager.register();
		EntityRendererRegistry.register(ModEntityTypes.WARHEAD_DEBRIS, WarheadDebrisRenderer::new);
		WarheadParticleClient.register();
		WarheadWorldRenderer.register();
		IcbmWorldRenderer.register();
		NuclearFlashOverlay.register();
		WarMod.LOGGER.info("War Mod client initialized.");
	}
}