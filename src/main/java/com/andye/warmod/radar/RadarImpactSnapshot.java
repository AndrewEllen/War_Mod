package com.andye.warmod.radar;

import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public record RadarImpactSnapshot(UUID rootTrackId, UUID terminalWarheadId, Vec3 impactPosition,
	long impactGameTime, WarheadPayloadType payloadType, float impactVisualScale) { }
