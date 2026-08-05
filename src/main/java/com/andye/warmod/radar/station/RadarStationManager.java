package com.andye.warmod.radar.station;

import com.andye.warmod.WarMod;
import com.andye.warmod.block.entity.RadarStationBlockEntity;
import com.andye.warmod.radar.RadarTrackingService;
import com.andye.warmod.radar.station.network.RadarStationNetworking;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.ServerLevel;

public final class RadarStationManager {
    private static boolean registered;

    private RadarStationManager() {
    }

    public static void registerLifecycle() {
        if (registered) return;
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            long now = level.getGameTime();
            if (Math.floorMod(now, 10L) == 0L) {
                RadarStationSavedData data = RadarStationSavedData.get(level);
                RadarStationChunkTicketManager.updateDynamic(
                    level,
                    data.records(),
                    RadarTrackingService.currentTelemetry(level, now)
                );
            }
            RadarStationTrackingService.tick(level);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server ->
            RadarStationChunkTicketManager.clear()
        );
        registered = true;
    }

    public static boolean canPlace(final ServerLevel level) {
        return RadarStationSavedData.get(level).size()
            < RadarStationConstants.MAX_RADAR_STATIONS_PER_LEVEL;
    }

    public static boolean register(
        final ServerLevel level,
        final RadarStationBlockEntity radar
    ) {
        RadarStationChunkTicketManager.register(
            level,
            radar.radarId(),
            radar.getBlockPos()
        );
        updateRecord(radar);
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            WarMod.LOGGER.info(
                "Radar station {} placed at {}",
                radar.radarId(),
                radar.getBlockPos()
            );
        }
        return true;
    }

    public static void updateRecord(final RadarStationBlockEntity radar) {
        if (!(radar.getLevel() instanceof ServerLevel level)) return;
        RadarStationSavedData.get(level).put(new RadarStationRecord(
            radar.radarId(),
            radar.getBlockPos(),
            radar.ownerId() == null ? new UUID(0, 0) : radar.ownerId(),
            radar.ownerName(),
            radar.warningRadius(),
            radar.fireRadius(),
            radar.phaseOffset(),
            radar.redstoneMode(),
            radar.dynamicChunkLoading()
        ));
    }

    public static void unregister(
        final ServerLevel level,
        final RadarStationBlockEntity radar
    ) {
        RadarStationNetworking.closeStation(radar.radarId());
        RadarStationChunkTicketManager.unregister(level, radar.radarId());
        RadarStationSavedData.get(level).remove(radar.radarId());
    }
}
