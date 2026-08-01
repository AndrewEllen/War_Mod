package com.andye.warmod;

import com.andye.warmod.acoustics.client.ClientAcousticNetworking;
import net.fabricmc.api.ClientModInitializer;

public final class WarModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientAcousticNetworking.register();
		WarMod.LOGGER.info("War Mod client initialized.");
	}
}