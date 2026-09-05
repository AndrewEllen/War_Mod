package com.andye.warmod.warhead;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class WarheadLaunchServiceTest {
    @Test
    void clusterQuarterSpreadIsWideDeterministicAndNonUniform() {
        List<Vec3> first = WarheadLaunchService.clusterOffsets(918273645L);
        List<Vec3> second = WarheadLaunchService.clusterOffsets(918273645L);

        assertEquals(4, first.size());
        assertEquals(first, second);
        Set<Long> roundedRadii = new HashSet<>();
        for (Vec3 offset : first) {
            assertEquals(0.0, offset.y, 0.0);
            assertTrue(offset.length() >= 26.0 && offset.length() < 68.0);
            roundedRadii.add(Math.round(offset.length() * 1000.0));
        }
        assertTrue(roundedRadii.size() > 1,
            "cluster quarters should not form an equal-radius cross");

        for (int index = 0; index < first.size(); index++) {
            Vec3 current = first.get(index).normalize();
            Vec3 next = first.get((index + 1) % first.size()).normalize();
            double separation = Math.acos(Math.max(-1.0,
                Math.min(1.0, current.dot(next))));
            assertTrue(separation > 0.45,
                "adjacent cluster quarters must fan into distinct sectors");
        }
    }
}
