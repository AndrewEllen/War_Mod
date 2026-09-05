package com.andye.warmod.silo;

import java.util.List;

public record LaunchControllerBatchResult(
    int accepted,
    List<LaunchControllerSiloResult> silos
) {
    public LaunchControllerBatchResult {
        silos = List.copyOf(silos);
        accepted = Math.max(0, Math.min(accepted, silos.size()));
    }

    public int attempted() {
        return silos.size();
    }

    public int failed() {
        return attempted() - accepted;
    }

    public String summary() {
        if (attempted() == 0) {
            return "No silos are linked";
        }
        return "Launch requests accepted: " + accepted + "/" + attempted()
            + (failed() == 0 ? "" : " (" + failed() + " failed)");
    }
}
