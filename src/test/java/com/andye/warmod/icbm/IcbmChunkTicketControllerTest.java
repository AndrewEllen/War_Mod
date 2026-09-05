package com.andye.warmod.icbm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class IcbmChunkTicketControllerTest {
    @Test
    void successfulTerminalHandoffStopsCarrierPreparationRefresh() {
        IcbmFlightPlan plan = new IcbmFlightPlan(UUID.randomUUID(), UUID.randomUUID(),
            new Vec3(0.0, 64.0, 0.0), new Vec3(0.0, 424.0, 0.0),
            new Vec3(1_000.0, 544.0, 0.0), new Vec3(1_064.0, 64.0, 0.0),
            0L, IcbmConstants.IGNITION_TICKS, IcbmConstants.BOOST_TICKS, 300,
            7L, WarheadPayloadType.NUCLEAR);
        IcbmChunkTicketController controller = new IcbmChunkTicketController(plan);

        assertTrue(controller.shouldMaintainTerrainPreparation());
        controller.markSeparated(plan.separationTick());
        assertFalse(controller.shouldMaintainTerrainPreparation());
    }
}
