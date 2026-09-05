package com.andye.warmod.silo;

import com.andye.warmod.item.component.LinkedSilo;

public record LaunchControllerSiloResult(
    LinkedSilo link,
    boolean accepted,
    String message
) {
    public LaunchControllerSiloResult {
        message = message == null || message.isBlank()
            ? accepted ? "Launch accepted" : "Launch failed"
            : message;
    }

    public static LaunchControllerSiloResult failed(
        final LinkedSilo link,
        final String message
    ) {
        return new LaunchControllerSiloResult(link, false, message);
    }
}
