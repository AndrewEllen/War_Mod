package com.andye.warmod.antiair;

/** The selected root trajectory and its projection at one authoritative server game time. */
public record AntiAirTargetSelection(AntiAirTargetLock targetLock, AntiAirThreatProjection projection) { }