package com.andye.warmod.icbm;

import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.defence.MissileAffiliation;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public record IcbmPointDefenceSnapshot(UUID missileId, UUID ownerPlayerId, MissileAffiliation affiliation, WarheadPayloadType payloadType, Vec3 position, Vec3 velocity, Vec3 intendedTarget, double ticksToImpact) { }
