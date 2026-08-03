package com.andye.warmod.radar.station;

import com.andye.warmod.WarMod;
import com.andye.warmod.block.entity.RadarStationBlockEntity;
import com.andye.warmod.radar.station.network.RadarStationNetworking;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.ServerLevel;

public final class RadarStationManager {
    private static final Set<ServerLevel> RESTORED = Collections.synchronizedSet(new HashSet<>());
    private static boolean registered;
    private RadarStationManager() { }

    public static void registerLifecycle() {
        if (registered) return;
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            if (RESTORED.add(level)) for (RadarStationRecord record : RadarStationSavedData.get(level).records())
                RadarStationChunkTicketManager.register(level, record.radarId(), record.centre());
            RadarStationTrackingService.tick(level);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            RESTORED.clear();
            RadarStationChunkTicketManager.clear();
        });
        registered = true;
    }

    public static boolean canPlace(ServerLevel level) {
        return RadarStationSavedData.get(level).size() < RadarStationConstants.MAX_RADAR_STATIONS_PER_LEVEL;
    }

    public static boolean register(ServerLevel level, RadarStationBlockEntity radar) {
        if (!RadarStationChunkTicketManager.register(level, radar.radarId(), radar.getBlockPos())) return false;
        updateRecord(radar);
        if (SharedConstants.IS_RUNNING_IN_IDE) WarMod.LOGGER.info("Radar station {} placed at {}", radar.radarId(),
            radar.getBlockPos());
        return true;
    }

    public static void updateRecord(RadarStationBlockEntity radar) {
        if (radar.getLevel() instanceof ServerLevel level) RadarStationSavedData.get(level).put(new RadarStationRecord(
            radar.radarId(), radar.getBlockPos(), radar.ownerId() == null ? new UUID(0, 0) : radar.ownerId(),
            radar.ownerName(), radar.warningRadius(), radar.fireRadius(), radar.phaseOffset(), radar.redstoneMode()));
    }

    public static void unregister(ServerLevel level, RadarStationBlockEntity radar) {
        RadarStationNetworking.closeStation(radar.radarId());
        RadarStationChunkTicketManager.unregister(level, radar.radarId());
        RadarStationSavedData.get(level).remove(radar.radarId());
    }
}