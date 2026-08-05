package com.andye.warmod.radar.station.client;

import com.andye.warmod.radar.station.RadarRedstoneMode;
import com.andye.warmod.radar.station.network.ClientboundOpenRadarStationPayload;
import com.andye.warmod.radar.station.network.ClientboundRadarStationObservationPayload;
import com.andye.warmod.radar.station.network.ClientboundRadarStationStatePayload;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

public final class ClientRadarStationState {
    public static final ClientRadarStationState INSTANCE =
        new ClientRadarStationState();

    private final Map<UUID, ClientRadarBlip> blips = new LinkedHashMap<>();
    private List<ClientRadarBlip> renderBlips = List.of();
    private UUID radarId;
    private UUID primaryThreatId;
    private UUID selectedTrackId;
    private BlockPos centre;
    private Identifier dimension;
    private long serverGameTime;
    private long phaseOffset;
    private int sweepPeriod;
    private int redstoneSignal;
    private int contacts;
    private int threats;
    private double detectionRange;
    private double warningRadius;
    private double fireRadius;
    private double primaryThreatDistance;
    private boolean warningActive;
    private boolean controlsEnabled;
    private boolean dynamicChunkLoading = true;
    private boolean followSelected;
    private RadarRedstoneMode redstoneMode = RadarRedstoneMode.ANALOG_DISTANCE;

    private ClientRadarStationState() {
    }

    public void open(final ClientboundOpenRadarStationPayload payload) {
        clear();
        radarId = payload.radarId();
        centre = payload.centre();
        dimension = payload.dimension();
        serverGameTime = payload.serverGameTime();
        sweepPeriod = payload.sweepPeriodTicks();
        detectionRange = payload.detectionRange();
        warningRadius = payload.warningRadius();
        fireRadius = payload.fireRadius();
        redstoneSignal = payload.redstoneSignal();
        redstoneMode = payload.redstoneMode();
        primaryThreatId = payload.primaryThreatId();
        primaryThreatDistance = payload.primaryThreatDistance();
        phaseOffset = payload.phaseOffset();
        controlsEnabled = payload.controlsEnabled();
        dynamicChunkLoading = payload.dynamicChunkLoading();
    }

    public void observe(final ClientboundRadarStationObservationPayload payload) {
        if (!payload.radarId().equals(radarId)) return;
        for (var observation : payload.observations()) {
            blips.put(
                observation.trackId(),
                new ClientRadarBlip(
                    observation,
                    observation.observationGameTime()
                )
            );
        }
        contacts = blips.size();
        rebuildBlips();
    }

    public void state(final ClientboundRadarStationStatePayload payload) {
        if (!payload.radarId().equals(radarId)) return;
        warningRadius = payload.warningRadius();
        fireRadius = payload.fireRadius();
        redstoneSignal = payload.redstoneSignal();
        redstoneMode = payload.redstoneMode();
        primaryThreatId = payload.primaryThreatId();
        primaryThreatDistance = payload.primaryThreatDistance();
        warningActive = payload.warningActive();
        contacts = payload.contacts();
        threats = payload.threats();
        serverGameTime = payload.serverGameTime();
        dynamicChunkLoading = payload.dynamicChunkLoading();
    }

    public void prune(final double now) {
        boolean changed = blips.values().removeIf(
            blip -> blip.alpha(now, sweepPeriod) <= 0.0
        );
        if (changed) {
            contacts = blips.size();
            rebuildBlips();
        }
        if (selectedTrackId != null && !blips.containsKey(selectedTrackId)) {
            selectedTrackId = null;
            followSelected = false;
        }
    }

    public Collection<ClientRadarBlip> blips() { return renderBlips; }
    public ClientRadarBlip selected() {
        return selectedTrackId == null ? null : blips.get(selectedTrackId);
    }
    public void select(final UUID id) { selectedTrackId = id; }
    public void toggleFollow() { followSelected = !followSelected; }
    public void disableFollow() { followSelected = false; }
    public boolean followSelected() { return followSelected; }
    public UUID radarId() { return radarId; }
    public UUID primaryThreatId() { return primaryThreatId; }
    public BlockPos centre() { return centre; }
    public Identifier dimension() { return dimension; }
    public int sweepPeriod() { return sweepPeriod; }
    public int redstoneSignal() { return redstoneSignal; }
    public RadarRedstoneMode redstoneMode() { return redstoneMode; }
    public double detectionRange() { return detectionRange; }
    public double warningRadius() { return warningRadius; }
    public double fireRadius() { return fireRadius; }
    public double primaryThreatDistance() { return primaryThreatDistance; }
    public long phaseOffset() { return phaseOffset; }
    public boolean warningActive() { return warningActive; }
    public int contacts() { return contacts; }
    public int threats() { return threats; }
    public boolean open() { return radarId != null; }
    public boolean controlsEnabled() { return controlsEnabled; }
    public boolean dynamicChunkLoading() { return dynamicChunkLoading; }
    public long serverGameTime() { return serverGameTime; }

    private void rebuildBlips() {
        renderBlips = List.copyOf(blips.values());
    }

    public void clear() {
        blips.clear();
        renderBlips = List.of();
        radarId = primaryThreatId = selectedTrackId = null;
        centre = null;
        dimension = null;
        serverGameTime = phaseOffset = 0L;
        sweepPeriod = 80;
        detectionRange = 8192.0;
        warningRadius = 256.0;
        fireRadius = 500.0;
        redstoneSignal = contacts = threats = 0;
        primaryThreatDistance = Double.POSITIVE_INFINITY;
        warningActive = false;
        controlsEnabled = false;
        dynamicChunkLoading = true;
        followSelected = false;
        redstoneMode = RadarRedstoneMode.ANALOG_DISTANCE;
    }
}
