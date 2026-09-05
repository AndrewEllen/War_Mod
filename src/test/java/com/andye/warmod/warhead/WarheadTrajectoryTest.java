package com.andye.warmod.warhead;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.andye.warmod.icbm.IcbmConstants;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class WarheadTrajectoryTest {
    @Test
    void terminalEtaUsesTheSameDurationAsCarrierLaunch() {
        Vec3 separation = new Vec3(0.0, 544.0, 0.0);
        Vec3 target = new Vec3(128.0, 64.0, 0.0);

        assertEquals(IcbmConstants.MAXIMUM_TERMINAL_TICKS,
            WarheadTrajectory.terminalFlightTicks(separation, target));
    }

    @Test
    void terminalEtaRetainsMinimumDurationForShortApproaches() {
        assertEquals(IcbmConstants.MINIMUM_TERMINAL_TICKS,
            WarheadTrajectory.terminalFlightTicks(Vec3.ZERO,
                new Vec3(1.0, 0.0, 0.0)));
    }
}
