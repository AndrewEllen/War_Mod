package com.andye.warmod;

import com.andye.warmod.acoustics.AcousticSounds;
import com.andye.warmod.acoustics.ModSoundEvents;
import com.andye.warmod.acoustics.network.AcousticNetworking;
import com.andye.warmod.entity.ModEntityTypes;
import com.andye.warmod.item.ModItems;
import com.andye.warmod.warhead.network.WarheadVisualNetworking;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WarMod implements ModInitializer {
	public static final String MOD_ID = "war_mod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModSoundEvents.register();
		AcousticSounds.register();
		AcousticNetworking.registerPayloadTypes();
		WarheadVisualNetworking.registerPayloadTypes();
		ModEntityTypes.register();
		ModItems.register();
		LOGGER.info("War Mod initialized.");
	}
}