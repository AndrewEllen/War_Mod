package com.andye.warmod;

import com.andye.warmod.acoustics.AcousticSounds;
import com.andye.warmod.acoustics.ModSoundEvents;
import com.andye.warmod.acoustics.network.AcousticNetworking;
import com.andye.warmod.antiair.AntiAirFlightControllerManager;
import com.andye.warmod.antiair.network.AntiAirNetworking;
import com.andye.warmod.artillery.network.ArtilleryNetworking;
import com.andye.warmod.block.ModBlocks;
import com.andye.warmod.block.entity.ModBlockEntities;
import com.andye.warmod.entity.ModEntityTypes;
import com.andye.warmod.icbm.IcbmChunkTicketType;
import com.andye.warmod.icbm.IcbmCommand;
import com.andye.warmod.icbm.IcbmFlightControllerManager;
import com.andye.warmod.icbm.IcbmPendingCommandLaunchManager;
import com.andye.warmod.icbm.network.IcbmVisualNetworking;
import com.andye.warmod.item.ModCreativeModeTabs;
import com.andye.warmod.item.ModItems;
import com.andye.warmod.item.PipeWrenchItem;
import com.andye.warmod.item.component.ModDataComponents;
import com.andye.warmod.menu.ModMenus;
import com.andye.warmod.particle.ModParticleTypes;
import com.andye.warmod.phalanx.PhalanxBulletManager;
import com.andye.warmod.phalanx.PhalanxChunkTicketType;
import com.andye.warmod.phalanx.PhalanxManager;
import com.andye.warmod.phalanx.network.PhalanxNetworking;
import com.andye.warmod.radar.RadarSubscriptionManager;
import com.andye.warmod.radar.RadarTrackingService;
import com.andye.warmod.radar.display.network.RadarDisplayNetworking;
import com.andye.warmod.radar.network.RadarNetworking;
import com.andye.warmod.radar.station.RadarStationChunkTicketType;
import com.andye.warmod.radar.station.RadarStationManager;
import com.andye.warmod.radar.station.network.RadarStationNetworking;
import com.andye.warmod.silo.MissileSiloChunkTicketType;
import com.andye.warmod.silo.MissileSiloManager;
import com.andye.warmod.silo.network.SiloNetworking;
import com.andye.warmod.testtool.network.MasterExplosiveNetworking;
import com.andye.warmod.warhead.IncomingWarheadRegistry;
import com.andye.warmod.warhead.WarheadExplosionWorkManager;
import com.andye.warmod.warhead.WarheadGlassShockwaveManager;
import com.andye.warmod.warhead.WarheadYieldRegistry;
import com.andye.warmod.warhead.network.WarheadVisualNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
        AntiAirNetworking.register();
        PhalanxNetworking.register();
        ArtilleryNetworking.register();
        WarheadVisualNetworking.registerPayloadTypes();
        IcbmVisualNetworking.registerPayloadTypes();
        RadarNetworking.register();
        SiloNetworking.register();
        MasterExplosiveNetworking.register();
        RadarStationNetworking.register();
        RadarDisplayNetworking.register();
        ModDataComponents.register();
        ModMenus.register();
        ModBlocks.register();
        ModBlockEntities.register();
        MissileSiloChunkTicketType.register();
        RadarStationChunkTicketType.register();
        PhalanxChunkTicketType.register();
        IcbmChunkTicketType.register();
        com.andye.warmod.warhead.WarheadImpactChunkLeaseManager.registerLifecycle();
        WarheadExplosionWorkManager.registerLifecycle();
        WarheadGlassShockwaveManager.registerLifecycle();
        WarheadYieldRegistry.registerLifecycle();
        IcbmFlightControllerManager.register();
        AntiAirFlightControllerManager.register();
        PhalanxManager.registerLifecycle();
        PhalanxBulletManager.register();
        IcbmPendingCommandLaunchManager.register();
        IcbmCommand.register();
        ModEntityTypes.register();
        ModItems.register();
        PipeWrenchItem.registerInteractions();
        ModCreativeModeTabs.register();
        MissileSiloManager.registerLifecycle();
        RadarStationManager.registerLifecycle();
        ModParticleTypes.register();

        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            RadarTrackingService.tick(level);
            RadarSubscriptionManager.tick(level);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(RadarSubscriptionManager::stop);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            RadarTrackingService.clearAll();
            IncomingWarheadRegistry.clearAll();
            com.andye.warmod.warhead.StrategicMissilePayloadRegistry.clear();
        });
        LOGGER.info("War Mod initialized.");
    }
}
