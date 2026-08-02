package com.andye.warmod.silo;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record MissileSiloLaunchResult(boolean accepted, MissileSiloLaunchFailure failure, String message,
    @Nullable UUID requestId) {
    public static MissileSiloLaunchResult accepted(final UUID requestId) {
        return new MissileSiloLaunchResult(true, MissileSiloLaunchFailure.NONE, "Launch preparation started", requestId);
    }

    public static MissileSiloLaunchResult failed(final MissileSiloLaunchFailure failure, final String message) {
        return new MissileSiloLaunchResult(false, failure, message, null);
    }
}
