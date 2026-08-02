package com.andye.warmod.radar;

import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public record RadarTerminalPlanSnapshot(UUID warheadId, Vec3 startPosition, Vec3 targetPosition,
	long launchGameTime, int flightTicks, long visualSeed) { }
