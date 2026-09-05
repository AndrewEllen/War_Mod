package com.andye.warmod.radar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andye.warmod.defence.MissileAffiliation;
import com.andye.warmod.warhead.WarheadLaunchService;
import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class RadarClusterTrackPolicyTest {
    @Test
    void regularTerminalKeepsCarrierTrackIdentity() {
        UUID root = UUID.randomUUID();
        UUID warhead = UUID.randomUUID();

        assertEquals(
            root,
            RadarTrackingService.visibleTerminalTrackId(root, warhead, 1)
        );
    }

    @Test
    void clusterQuartersReceiveFourIndependentTrackIdentities() {
        UUID root = UUID.randomUUID();
        HashSet<UUID> visibleTracks = new HashSet<>();

        for (int index = 0; index < 4; index++) {
            UUID warhead = UUID.randomUUID();
            UUID visible = RadarTrackingService.visibleTerminalTrackId(
                root,
                warhead,
                4
            );
            assertEquals(warhead, visible);
            assertNotEquals(root, visible);
            visibleTracks.add(visible);
        }

        assertEquals(4, visibleTracks.size());
    }

    @Test
    void clusterHandoffRequiresEveryQuarterExactlyOnce() {
        UUID root = UUID.randomUUID();
        ArrayList<WarheadLaunchService.LaunchResult> launches =
            new ArrayList<>();

        for (int index = 0; index < 4; index++) {
            launches.add(launch(root, index));
        }

        assertTrue(RadarTrackingService.isCompleteCluster(root, launches));

        List<WarheadLaunchService.LaunchResult> duplicateQuarter = List.of(
            launches.get(0),
            launches.get(1),
            launches.get(2),
            launch(root, 2)
        );
        assertFalse(
            RadarTrackingService.isCompleteCluster(root, duplicateQuarter)
        );
    }

    private static WarheadLaunchService.LaunchResult launch(
        final UUID root,
        final int clusterIndex
    ) {
        return new WarheadLaunchService.LaunchResult(
            UUID.randomUUID(),
            new Vec3(0.0, 300.0, 0.0),
            new Vec3(clusterIndex * 20.0, 64.0, clusterIndex * -13.0),
            100L,
            200,
            500L + clusterIndex,
            WarheadPayloadType.CONVENTIONAL,
            root,
            clusterIndex,
            4,
            MissileAffiliation.unowned()
        );
    }
}
