package com.andye.warmod;

import com.andye.warmod.acoustics.client.ClientAcousticNetworking;
import com.andye.warmod.antiair.client.ClientAntiAirNetworking;
import com.andye.warmod.antiair.client.AntiAirWorldRenderer;
import com.andye.warmod.antiair.client.audio.AntiAirEngineAudioManager;
import com.andye.warmod.compat.DistantHorizonsCompat;
import com.andye.warmod.entity.ModEntityTypes;
import com.andye.warmod.entity.client.WarheadDebrisRenderer;
import com.andye.warmod.block.entity.ModBlockEntities;
import com.andye.warmod.silo.client.MissileSiloBlockEntityRenderer;
import com.andye.warmod.silo.client.gui.MissileSiloScreen;
import com.andye.warmod.menu.ModMenus;
import com.andye.warmod.radar.station.client.RadarStationBlockEntityRenderer;
import com.andye.warmod.phalanx.client.PhalanxBlockEntityRenderer;
import com.andye.warmod.phalanx.client.ClientPhalanxNetworking;
import com.andye.warmod.phalanx.client.PhalanxTracerRenderer;
import com.andye.warmod.phalanx.client.gui.PhalanxScreen;
import com.andye.warmod.radar.display.client.RadarDisplayBlockEntityRenderer;
import com.andye.warmod.radar.station.client.ClientRadarStationNetworking;
import com.andye.warmod.radar.display.client.ClientRadarDisplayNetworking;
import com.andye.warmod.testtool.client.ClientMasterExplosiveNetworking;
import net.minecraft.client.gui.screens.MenuScreens;
import com.andye.warmod.rocket.client.RocketProjectileRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import com.andye.warmod.icbm.client.ClientIcbmNetworking;
import com.andye.warmod.icbm.client.audio.ClientIcbmAudioManager;
import com.andye.warmod.warhead.client.audio.ClientTerminalAudioManager;
import com.andye.warmod.icbm.client.render.IcbmWorldRenderer;
import com.andye.warmod.particle.WarheadParticleClient;
import com.andye.warmod.radar.client.ClientRadarNetworking;
import com.andye.warmod.radar.client.RadarKeyBindings;
import com.andye.warmod.warhead.client.ClientWarheadNetworking;
import com.andye.warmod.warhead.client.render.ShockwaveVanillaParticleEmitter;
import com.andye.warmod.warhead.client.render.WarheadRenderCommands;
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
        ClientAntiAirNetworking.register();
		ClientPhalanxNetworking.register();
		ClientRadarNetworking.register();
		ClientRadarStationNetworking.register();
        ClientRadarDisplayNetworking.register();
		ClientMasterExplosiveNetworking.register();
		RadarKeyBindings.register();
		ClientIcbmAudioManager.register();
		AntiAirEngineAudioManager.register();
		ClientTerminalAudioManager.register();
		EntityRendererRegistry.register(ModEntityTypes.WARHEAD_DEBRIS, WarheadDebrisRenderer::new);
		EntityRendererRegistry.register(ModEntityTypes.ROCKET_PROJECTILE, RocketProjectileRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.MISSILE_SILO, MissileSiloBlockEntityRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.RADAR_STATION, RadarStationBlockEntityRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.PHALANX_TURRET, PhalanxBlockEntityRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.RADAR_DISPLAY_PANEL, RadarDisplayBlockEntityRenderer::new);
		MenuScreens.register(ModMenus.MISSILE_SILO, MissileSiloScreen::new);
		MenuScreens.register(ModMenus.PHALANX, PhalanxScreen::new);
		WarheadParticleClient.register();
		WarheadRenderCommands.register();
		ShockwaveVanillaParticleEmitter.register();
		WarheadWorldRenderer.register();
		IcbmWorldRenderer.register();
        AntiAirWorldRenderer.register();
		PhalanxTracerRenderer.register();
		NuclearFlashOverlay.register();
		DistantHorizonsCompat.register();
		WarMod.LOGGER.info("War Mod client initialized.");
	}
}
