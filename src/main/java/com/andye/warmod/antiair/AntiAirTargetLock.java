package com.andye.warmod.antiair;

import com.andye.warmod.icbm.IcbmFlightPlan;
import com.andye.warmod.radar.RadarTerminalPlanSnapshot;
import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Captured authoritative strategic-plan state. Projection details deliberately live elsewhere. */
public record AntiAirTargetLock(
    UUID rootTrackId,
    WarheadPayloadType payloadType,
    IcbmFlightPlan carrierPlan,
    @Nullable RadarTerminalPlanSnapshot terminalPlan,
    long acquisitionGameTime,
    long separationGameTime,
    long estimatedImpactGameTime
) { }