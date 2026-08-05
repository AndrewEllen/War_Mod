package com.andye.warmod.radar.client;

import com.andye.warmod.radar.RadarImpactSnapshot;
import com.andye.warmod.radar.RadarTrackPhase;
import com.andye.warmod.radar.RadarTrackSnapshot;
import com.andye.warmod.radar.network.ClientboundOpenRadarPayload;
import com.andye.warmod.radar.network.ClientboundRadarImpactPayload;
import com.andye.warmod.radar.network.ClientboundRadarInterceptionPayload;
import com.andye.warmod.radar.network.ClientboundRadarSnapshotPayload;
import com.andye.warmod.radar.network.ClientboundRadarTrackRemovePayload;
import com.andye.warmod.radar.network.ClientboundRadarTrackUpsertPayload;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.Identifier;

public final class ClientRadarState {
    public static final ClientRadarState INSTANCE = new ClientRadarState();

    private final Map<UUID, ClientRadarTrack> activeTracks =
        new LinkedHashMap<>();
    private final Map<UUID, ClientRadarImpact> recentImpacts =
        new LinkedHashMap<>();
    private final Map<UUID, ClientRadarInterception> interceptions =
        new LinkedHashMap<>();
    private final ClientRadarClock clock = new ClientRadarClock();

    private List<ClientRadarTrack> renderTracks = List.of();
    private List<ClientRadarImpact> renderImpacts = List.of();

    private UUID selectedTrackId;
    private boolean subscribed;
    private boolean followSelectedTrack;
    private long lastSnapshotServerGameTime;
    private Identifier dimensionId;

    private ClientRadarState() {
    }

    public void open(final ClientboundOpenRadarPayload payload) {
        if (dimensionId != null
            && !dimensionId.equals(payload.dimensionId())) {
            clear();
        }

        subscribed = true;
        dimensionId = payload.dimensionId();
        clock.synchronize(payload.serverGameTime());
    }

    public void snapshot(final ClientboundRadarSnapshotPayload payload) {
        if (dimensionId != null
            && !dimensionId.equals(payload.dimensionId())) {
            clear();
        }

        subscribed = true;
        dimensionId = payload.dimensionId();
        lastSnapshotServerGameTime = payload.serverGameTime();
        clock.synchronize(payload.serverGameTime());

        activeTracks.clear();
        for (RadarTrackSnapshot track : payload.tracks()) {
            if (track.phase() == RadarTrackPhase.IMPACT) {
                continue;
            }

            activeTracks.put(track.trackId(), new ClientRadarTrack(track));
        }

        recentImpacts.clear();
        for (RadarImpactSnapshot impact : payload.impacts()) {
            recentImpacts.put(
                impact.rootTrackId(),
                new ClientRadarImpact(impact)
            );
        }

        if (selectedTrackId != null
            && !activeTracks.containsKey(selectedTrackId)
            && !recentImpacts.containsKey(selectedTrackId)) {
            selectedTrackId = null;
        }

        rebuildRenderSnapshots();
    }

    public void upsert(final ClientboundRadarTrackUpsertPayload payload) {
        clock.synchronize(payload.serverGameTime());

        if (payload.track().phase() == RadarTrackPhase.IMPACT) {
            activeTracks.remove(payload.track().trackId());
            rebuildTracks();
            return;
        }

        activeTracks.compute(payload.track().trackId(), (id, old) -> {
            if (old == null) {
                return new ClientRadarTrack(payload.track());
            }

            old.update(payload.track());
            return old;
        });
        rebuildTracks();
    }

    public void remove(final ClientboundRadarTrackRemovePayload payload) {
        activeTracks.remove(payload.trackId());

        if (!recentImpacts.containsKey(payload.trackId())
            && payload.trackId().equals(selectedTrackId)) {
            selectedTrackId = null;
        }

        rebuildTracks();
    }

    public void impact(final ClientboundRadarImpactPayload payload) {
        UUID rootTrackId = payload.impact().rootTrackId();

        /*
         * An impact marker replaces the active missile route immediately.
         * Do not leave the completed flight path on the M-map while waiting
         * for the server's later retention cleanup.
         */
        activeTracks.remove(rootTrackId);
        recentImpacts.put(
            rootTrackId,
            new ClientRadarImpact(payload.impact())
        );

        if (rootTrackId.equals(selectedTrackId)) {
            selectedTrackId = null;
            followSelectedTrack = false;
        }

        rebuildRenderSnapshots();
    }

    public void interception(
        final ClientboundRadarInterceptionPayload payload
    ) {
        interceptions.put(
            payload.interceptorId(),
            new ClientRadarInterception(payload)
        );
    }

    public List<ClientRadarInterception> interceptions() {
        return List.copyOf(interceptions.values());
    }

    public List<ClientRadarTrack> tracks() {
        return renderTracks;
    }

    public List<ClientRadarImpact> impacts() {
        return renderImpacts;
    }

    public void pruneImpacts(final double now) {
        interceptions.values().removeIf(event -> event.expired(now));

        if (recentImpacts.values().removeIf(impact ->
            now - impact.snapshot().impactGameTime()
                > com.andye.warmod.radar.RadarTrackingService
                    .RECENT_IMPACT_RETENTION_TICKS
        )) {
            rebuildImpacts();
        }
    }

    public ClientRadarTrack selected() {
        return selectedTrackId == null
            ? null
            : activeTracks.get(selectedTrackId);
    }

    public void select(final UUID id) {
        selectedTrackId = id;
    }

    public UUID selectedTrackId() {
        return selectedTrackId;
    }

    public boolean subscribed() {
        return subscribed;
    }

    public boolean followSelectedTrack() {
        return followSelectedTrack;
    }

    public void toggleFollow() {
        followSelectedTrack = !followSelectedTrack;
    }

    public void disableFollow() {
        followSelectedTrack = false;
    }

    public Identifier dimensionId() {
        return dimensionId;
    }

    public ClientRadarClock clock() {
        return clock;
    }

    public long lastSnapshotServerGameTime() {
        return lastSnapshotServerGameTime;
    }

    public void clear() {
        activeTracks.clear();
        recentImpacts.clear();
        interceptions.clear();
        rebuildRenderSnapshots();
        selectedTrackId = null;
        subscribed = false;
        followSelectedTrack = false;
        dimensionId = null;
        lastSnapshotServerGameTime = 0L;
    }

    private void rebuildTracks() {
        renderTracks = List.copyOf(activeTracks.values());
    }

    private void rebuildImpacts() {
        renderImpacts = List.copyOf(recentImpacts.values());
    }

    private void rebuildRenderSnapshots() {
        rebuildTracks();
        rebuildImpacts();
    }
}
