package com.andye.warmod.icbm;

import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public record IcbmFlightPlan(UUID missileId, UUID ownerPlayerId, Vec3 launchPosition, Vec3 burnoutPosition,
	Vec3 separationPosition, Vec3 intendedTarget, long launchGameTime, int ignitionTicks, int boostTicks,
	int coastTicks, long visualSeed, WarheadPayloadType payloadType) {
	public IcbmFlightPlan {
		Objects.requireNonNull(missileId); Objects.requireNonNull(ownerPlayerId); Objects.requireNonNull(launchPosition);
		Objects.requireNonNull(burnoutPosition); Objects.requireNonNull(separationPosition); Objects.requireNonNull(intendedTarget); Objects.requireNonNull(payloadType);
		if(!launchPosition.isFinite()||!burnoutPosition.isFinite()||!separationPosition.isFinite()||!intendedTarget.isFinite()
			|| ignitionTicks<1||ignitionTicks>20||boostTicks<1||boostTicks>200||coastTicks<IcbmConstants.MINIMUM_COAST_TICKS
			||coastTicks>IcbmConstants.MAXIMUM_COAST_TICKS||launchPosition.distanceTo(intendedTarget)>3072.0
			||launchPosition.distanceTo(burnoutPosition)>2048.0||burnoutPosition.distanceTo(separationPosition)>3072.0)
			throw new IllegalArgumentException("Invalid ICBM flight plan");
	}
	public int separationTick(){return ignitionTicks+boostTicks+coastTicks;}
	public int totalCarrierTicks(){return separationTick();}
}
