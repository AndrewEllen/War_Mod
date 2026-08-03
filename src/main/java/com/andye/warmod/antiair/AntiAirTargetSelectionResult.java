package com.andye.warmod.antiair;

/** The route solution selected and claimed as one server-thread operation. */
public record AntiAirTargetSelectionResult(AntiAirTargetSelection selection, AntiAirInterceptSolution solution,
    AntiAirLaunchMode mode, int claimsBefore, int candidateCount, int unclaimedCandidates) { }
