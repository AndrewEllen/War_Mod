package com.andye.warmod.radar;

import com.andye.warmod.icbm.IcbmFlightPlan;
import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class RadarTrack {
	final UUID trackId; final RadarTrackKind kind; final UUID ownerPlayerId; final String ownerDisplayName;
	final WarheadPayloadType payloadType; final long creationGameTime; final @Nullable IcbmFlightPlan carrierFlightPlan;
	@Nullable UUID terminalWarheadId; @Nullable Vec3 terminalStartPosition; @Nullable Vec3 terminalTargetPosition;
	long terminalLaunchGameTime; int terminalFlightTicks; long terminalVisualSeed;
	@Nullable Vec3 impactPosition; long impactGameTime; float impactVisualScale;
	RadarTrackPhase phase; long lastStateChangeGameTime;

	RadarTrack(final UUID id, final RadarTrackKind kind, final UUID ownerId, final String ownerName,
		final WarheadPayloadType payload, final long created, final @Nullable IcbmFlightPlan carrier,
		final RadarTrackPhase phase) {
		this.trackId=id;this.kind=kind;this.ownerPlayerId=ownerId;this.ownerDisplayName=ownerName;
		this.payloadType=payload;this.creationGameTime=created;this.carrierFlightPlan=carrier;this.phase=phase;
		this.lastStateChangeGameTime=created;
	}

	RadarTrackSnapshot snapshot() {
		Optional<RadarCarrierPlanSnapshot> carrier = Optional.ofNullable(this.carrierFlightPlan).map(p ->
			new RadarCarrierPlanSnapshot(p.launchPosition(),p.burnoutPosition(),p.separationPosition(),p.intendedTarget(),
				p.launchGameTime(),p.ignitionTicks(),p.boostTicks(),p.coastTicks(),p.visualSeed()));
		Optional<RadarTerminalPlanSnapshot> terminal = this.terminalWarheadId == null ? Optional.empty() : Optional.of(
			new RadarTerminalPlanSnapshot(this.terminalWarheadId,this.terminalStartPosition,this.terminalTargetPosition,
				this.terminalLaunchGameTime,this.terminalFlightTicks,this.terminalVisualSeed));
		return new RadarTrackSnapshot(this.trackId,this.kind,this.ownerPlayerId,this.ownerDisplayName,this.payloadType,this.phase,carrier,terminal);
	}
}
