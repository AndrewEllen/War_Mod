package com.andye.warmod;

import net.fabricmc.api.ClientModInitializer;

public final class WarModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		WarMod.LOGGER.info("War Mod client initialized.");
	}
}
