package com.andye.warmod.silo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andye.warmod.item.component.LinkedSilo;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

final class LaunchControllerLaunchServiceTest {
    @Test
    void dispatchContinuesAfterOneSiloThrows() {
        List<LinkedSilo> links = List.of(link(0), link(1), link(2));
        ArrayList<UUID> attempted = new ArrayList<>();

        List<LaunchControllerSiloResult> results =
            LaunchControllerLaunchService.dispatchAll(links, link -> {
                attempted.add(link.siloId());
                if (link == links.get(1)) {
                    throw new IllegalStateException("fixture failure");
                }
                return new LaunchControllerSiloResult(
                    link,
                    true,
                    "Launch accepted"
                );
            });

        assertEquals(links.stream().map(LinkedSilo::siloId).toList(), attempted);
        assertEquals(3, results.size());
        assertTrue(results.get(0).accepted());
        assertFalse(results.get(1).accepted());
        assertTrue(results.get(2).accepted());
    }

    @Test
    void batchSummaryCountsIndependentOutcomes() {
        LinkedSilo first = link(0);
        LinkedSilo second = link(1);
        LaunchControllerBatchResult batch = new LaunchControllerBatchResult(
            1,
            List.of(
                new LaunchControllerSiloResult(first, true, "accepted"),
                LaunchControllerSiloResult.failed(second, "busy")
            )
        );

        assertEquals(2, batch.attempted());
        assertEquals(1, batch.accepted());
        assertEquals(1, batch.failed());
        assertEquals("Launch requests accepted: 1/2 (1 failed)", batch.summary());
    }

    private static LinkedSilo link(final int offset) {
        return new LinkedSilo(
            Level.OVERWORLD,
            new BlockPos(offset * 8, 64, 0),
            UUID.randomUUID()
        );
    }
}
