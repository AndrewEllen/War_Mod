package com.andye.warmod.radar.client;

import com.andye.warmod.radar.RadarImpactSnapshot;
import com.andye.warmod.radar.RadarTrackSnapshot;
import com.andye.warmod.radar.network.*;
import java.util.*;
import net.minecraft.resources.Identifier;

public final class ClientRadarState {
	public static final ClientRadarState INSTANCE = new ClientRadarState();
	private final Map<UUID, ClientRadarTrack> activeTracks = new LinkedHashMap<>();
	private final Map<UUID, ClientRadarImpact> recentImpacts = new LinkedHashMap<>();
	private final ClientRadarClock clock = new ClientRadarClock();
	private List<ClientRadarTrack> renderTracks = List.of();
	private List<ClientRadarImpact> renderImpacts = List.of();
	private UUID selectedTrackId;
	private boolean subscribed, followSelectedTrack;
	private long lastSnapshotServerGameTime;
	private Identifier dimensionId;
	private ClientRadarState() { }
	public void open(final ClientboundOpenRadarPayload payload) { if (dimensionId != null && !dimensionId.equals(payload.dimensionId())) clear(); subscribed = true; dimensionId = payload.dimensionId(); clock.synchronize(payload.serverGameTime()); }
	public void snapshot(final ClientboundRadarSnapshotPayload payload) { if (dimensionId != null && !dimensionId.equals(payload.dimensionId())) clear(); subscribed = true; dimensionId = payload.dimensionId(); lastSnapshotServerGameTime = payload.serverGameTime(); clock.synchronize(payload.serverGameTime()); activeTracks.clear(); for (RadarTrackSnapshot track : payload.tracks()) activeTracks.put(track.trackId(), new ClientRadarTrack(track)); recentImpacts.clear(); for (RadarImpactSnapshot impact : payload.impacts()) recentImpacts.put(impact.rootTrackId(), new ClientRadarImpact(impact)); if (selectedTrackId != null && !activeTracks.containsKey(selectedTrackId) && !recentImpacts.containsKey(selectedTrackId)) selectedTrackId = null; rebuildRenderSnapshots(); }
	public void upsert(final ClientboundRadarTrackUpsertPayload payload) { clock.synchronize(payload.serverGameTime()); activeTracks.compute(payload.track().trackId(), (id, old) -> { if (old == null) return new ClientRadarTrack(payload.track()); old.update(payload.track()); return old; }); rebuildTracks(); }
	public void remove(final ClientboundRadarTrackRemovePayload payload) { activeTracks.remove(payload.trackId()); if (!recentImpacts.containsKey(payload.trackId()) && payload.trackId().equals(selectedTrackId)) selectedTrackId = null; rebuildTracks(); }
	public void impact(final ClientboundRadarImpactPayload payload) { recentImpacts.put(payload.impact().rootTrackId(), new ClientRadarImpact(payload.impact())); rebuildImpacts(); }
	public List<ClientRadarTrack> tracks() { return renderTracks; }
	public List<ClientRadarImpact> impacts() { return renderImpacts; }
	public ClientRadarTrack selected() { return selectedTrackId == null ? null : activeTracks.get(selectedTrackId); }
	public void select(final UUID id) { selectedTrackId = id; }
	public UUID selectedTrackId() { return selectedTrackId; }
	public boolean subscribed() { return subscribed; }
	public boolean followSelectedTrack() { return followSelectedTrack; }
	public void toggleFollow() { followSelectedTrack = !followSelectedTrack; }
	public void disableFollow() { followSelectedTrack = false; }
	public Identifier dimensionId() { return dimensionId; }
	public ClientRadarClock clock() { return clock; }
	public long lastSnapshotServerGameTime() { return lastSnapshotServerGameTime; }
	public void clear() { activeTracks.clear(); recentImpacts.clear(); rebuildRenderSnapshots(); selectedTrackId = null; subscribed = false; followSelectedTrack = false; dimensionId = null; lastSnapshotServerGameTime = 0; }
	private void rebuildTracks() { renderTracks = List.copyOf(activeTracks.values()); }
	private void rebuildImpacts() { renderImpacts = List.copyOf(recentImpacts.values()); }
	private void rebuildRenderSnapshots() { rebuildTracks(); rebuildImpacts(); }
}