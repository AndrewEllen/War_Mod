package com.andye.warmod.silo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andye.warmod.block.MissileSiloPart;
import com.andye.warmod.block.MissileSiloStructure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;

final class MissileSiloFootprintTest {
    @Test
    void legacyAndLargeFootprintsResolveEveryPartToTheirOwnCentre() {
        BlockPos centre = new BlockPos(15, 80, -16);
        for (Direction facing :
                new Direction[] {
                    Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
                }) {
            var legacy = MissileSiloStructure.positions(centre, facing, false);
            var large = MissileSiloStructure.positions(centre, facing, true);
            assertEquals(9, legacy.size());
            assertEquals(25, large.size());
            assertTrue(large.containsAll(legacy));
            for (MissileSiloPart part : MissileSiloPart.values()) {
                BlockPos position = MissileSiloStructure.position(centre, part, facing);
                assertEquals(centre, part.resolveCenter(position, facing));
                assertEquals(part.belongsTo(false), legacy.contains(position));
            }
        }
    }
}
