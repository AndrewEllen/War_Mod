package com.andye.warmod.antiair;

import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/** Future strategic path data kept separate from the immutable target identity lock. */
public record AntiAirThreatProjection(
    UUID rootTrackId,
    Vec3 firstEntryPosition,
    long firstEntryGameTime,
    Vec3 closestApproachPosition,
    long closestApproachGameTime,
    double closestHorizontalDistance,
    Vec3 predictedImpactPosition,
    long estimatedImpactGameTime,
    boolean currentlyInsideRadius,
    boolean terminalAtSelection
) { }