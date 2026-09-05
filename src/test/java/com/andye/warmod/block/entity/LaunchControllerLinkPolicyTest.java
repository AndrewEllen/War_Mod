package com.andye.warmod.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.andye.warmod.item.component.LinkedSilo;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

final class LaunchControllerLinkPolicyTest {
    @Test
    void repeatedLinkIsIdempotentAddRatherThanToggleRemove() {
        LinkedSilo silo = link(new BlockPos(10, 64, 20), UUID.randomUUID());

        assertEquals(
            LaunchControllerBlockEntity.AddDecision.ALREADY_LINKED,
            LaunchControllerBlockEntity.classifyAdd(List.of(silo), silo)
        );
    }

    @Test
    void replacementAtSameCoordinateIsRejectedByUuidBinding() {
        BlockPos centre = new BlockPos(10, 64, 20);
        LinkedSilo original = link(centre, UUID.randomUUID());
        LinkedSilo replacement = link(centre, UUID.randomUUID());

        assertEquals(
            LaunchControllerBlockEntity.AddDecision.COORDINATE_CONFLICT,
            LaunchControllerBlockEntity.classifyAdd(
                List.of(original),
                replacement
            )
        );
    }

    private static LinkedSilo link(final BlockPos centre, final UUID siloId) {
        return new LinkedSilo(Level.OVERWORLD, centre, siloId);
    }
}
