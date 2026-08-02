package com.andye.warmod.radar;

import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.Optional;
import java.util.UUID;

public record RadarTrackSnapshot(UUID trackId, RadarTrackKind kind, UUID ownerPlayerId, String ownerDisplayName,
	WarheadPayloadType payloadType, RadarTrackPhase phase, Optional<RadarCarrierPlanSnapshot> carrierPlan,
	Optional<RadarTerminalPlanSnapshot> terminalPlan) { }
