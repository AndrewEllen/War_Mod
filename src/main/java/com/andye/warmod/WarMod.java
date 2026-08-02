package com.andye.warmod;

import com.andye.warmod.acoustics.AcousticSounds;
import com.andye.warmod.acoustics.ModSoundEvents;
import com.andye.warmod.acoustics.network.AcousticNetworking;
import com.andye.warmod.entity.ModEntityTypes;
import com.andye.warmod.menu.ModMenus;
import com.andye.warmod.silo.network.SiloNetworking;
import com.andye.warmod.radar.station.RadarStationChunkTicketType;
import com.andye.warmod.radar.station.RadarStationManager;
import com.andye.warmod.radar.station.network.RadarStationNetworking;
import com.andye.warmod.block.ModBlocks;
import com.andye.warmod.block.entity.ModBlockEntities;
import com.andye.warmod.item.component.ModDataComponents;
import com.andye.warmod.silo.MissileSiloChunkTicketType;
import com.andye.warmod.silo.MissileSiloManager;
import com.andye.warmod.icbm.network.IcbmVisualNetworking;
import com.andye.warmod.icbm.IcbmChunkTicketType;
import com.andye.warmod.icbm.IcbmCommand;
import com.andye.warmod.icbm.IcbmFlightControllerManager;
import com.andye.warmod.icbm.IcbmPendingCommandLaunchManager;
import com.andye.warmod.item.ModItems;
import com.andye.warmod.item.ModCreativeModeTabs;
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
	@Override public void onInitialize(){ModSoundEvents.register();AcousticSounds.register();AcousticNetworking.registerPayloadTypes();WarheadVisualNetworking.registerPayloadTypes();IcbmVisualNetworking.registerPayloadTypes();RadarNetworking.register();SiloNetworking.register();RadarStationNetworking.register();ModDataComponents.register();ModMenus.register();ModBlocks.register();ModBlockEntities.register();MissileSiloChunkTicketType.register();RadarStationChunkTicketType.register();IcbmChunkTicketType.register();IcbmFlightControllerManager.register();IcbmPendingCommandLaunchManager.register();IcbmCommand.register();ModEntityTypes.register();ModItems.register();ModCreativeModeTabs.register();MissileSiloManager.registerLifecycle();RadarStationManager.registerLifecycle();ModParticleTypes.register();ServerTickEvents.END_LEVEL_TICK.register(level->{RadarTrackingService.tick(level);RadarSubscriptionManager.tick(level);});ServerLifecycleEvents.SERVER_STOPPING.register(RadarSubscriptionManager::stop);ServerLifecycleEvents.SERVER_STOPPED.register(server->RadarTrackingService.clearAll());LOGGER.info("War Mod initialized.");}
}
