package com.andye.warmod;

import com.andye.warmod.acoustics.AcousticSounds;
import com.andye.warmod.acoustics.ModSoundEvents;
import com.andye.warmod.acoustics.network.AcousticNetworking;
import com.andye.warmod.entity.ModEntityTypes;
import com.andye.warmod.icbm.network.IcbmVisualNetworking;
import com.andye.warmod.icbm.IcbmChunkTicketType;
import com.andye.warmod.icbm.IcbmCommand;
import com.andye.warmod.icbm.IcbmFlightControllerManager;
import com.andye.warmod.item.ModItems;
import com.andye.warmod.particle.ModParticleTypes;
import com.andye.warmod.radar.RadarSubscriptionManager;
import com.andye.warmod.radar.RadarTrackingService;
import com.andye.warmod.radar.network.RadarNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.andye.warmod.warhead.network.WarheadVisualNetworking;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WarMod implements ModInitializer {
	public static final String MOD_ID="war_mod";public static final Logger LOGGER=LoggerFactory.getLogger(MOD_ID);
	@Override public void onInitialize(){ModSoundEvents.register();AcousticSounds.register();AcousticNetworking.registerPayloadTypes();WarheadVisualNetworking.registerPayloadTypes();IcbmVisualNetworking.registerPayloadTypes();RadarNetworking.register();IcbmChunkTicketType.register();IcbmFlightControllerManager.register();IcbmCommand.register();ModEntityTypes.register();ModItems.register();ModParticleTypes.register();ServerTickEvents.END_LEVEL_TICK.register(level->{RadarTrackingService.tick(level);RadarSubscriptionManager.tick(level);});ServerLifecycleEvents.SERVER_STOPPING.register(RadarSubscriptionManager::stop);ServerLifecycleEvents.SERVER_STOPPED.register(server->RadarTrackingService.clearAll());LOGGER.info("War Mod initialized.");}
}
