package com.andye.warmod.radar.display;

import com.andye.warmod.block.ModBlocks;
import com.andye.warmod.block.RadarDisplayPanelBlock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

public record RadarDisplayStructure(
    boolean valid,
    int size,
    Direction facing,
    BlockPos controller,
    List<BlockPos> panels,
    int missingPanels
) {
    public RadarDisplayStructure {
        panels = List.copyOf(panels);
    }

    public static RadarDisplayStructure resolve(
        final LevelAccessor level,
        final BlockPos start
    ) {
        BlockState first = level.getBlockState(start);

        if (!first.is(ModBlocks.RADAR_DISPLAY_PANEL)) {
            return invalid(
                start,
                Direction.NORTH,
                List.of(),
                1
            );
        }

        Direction facing =
            first.getValue(RadarDisplayPanelBlock.FACING);

        // From a viewer standing in front of the monitor.
        Direction screenLeft = facing.getClockWise();

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        LinkedHashSet<BlockPos> found = new LinkedHashSet<>();

        queue.add(start);

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();

            if (!found.add(current)) {
                continue;
            }

            if (found.size() > RadarDisplayConstants.MAX_PANELS) {
                return invalid(
                    start,
                    facing,
                    List.copyOf(found),
                    0
                );
            }

            for (BlockPos neighbour : List.of(
                current.above(),
                current.below(),
                current.relative(screenLeft),
                current.relative(screenLeft.getOpposite())
            )) {
                BlockState state = level.getBlockState(neighbour);

                if (state.is(ModBlocks.RADAR_DISPLAY_PANEL)
                    && state.getValue(RadarDisplayPanelBlock.FACING)
                        == facing
                    && !found.contains(neighbour)) {
                    queue.addLast(neighbour);
                }
            }
        }

        int minY = found.stream()
            .mapToInt(BlockPos::getY)
            .min()
            .orElse(start.getY());

        int maxY = found.stream()
            .mapToInt(BlockPos::getY)
            .max()
            .orElse(start.getY());

        int minimumLeftCoordinate = found.stream()
            .mapToInt(position ->
                coordinateAlong(position, screenLeft))
            .min()
            .orElse(0);

        int maximumLeftCoordinate = found.stream()
            .mapToInt(position ->
                coordinateAlong(position, screenLeft))
            .max()
            .orElse(0);

        int width =
            maximumLeftCoordinate - minimumLeftCoordinate + 1;

        int height =
            maxY - minY + 1;

        int missing =
            width * height - found.size();

        // Controller is the bottom-left panel as seen from the front.
        BlockPos controller = found.stream()
            .filter(position ->
                position.getY() == minY
                && coordinateAlong(position, screenLeft)
                    == maximumLeftCoordinate)
            .findFirst()
            .orElse(start);

        boolean valid =
            width == height
            && width >= 1
            && width <= RadarDisplayConstants.MAX_SIZE
            && missing == 0;

        List<BlockPos> orderedPanels =
            new ArrayList<>(found);

        orderedPanels.sort(
            Comparator
                .<BlockPos>comparingInt(BlockPos::getY)
                .thenComparingInt(position ->
                    -coordinateAlong(position, screenLeft))
        );

        return new RadarDisplayStructure(
            valid,
            valid ? width : 0,
            facing,
            controller,
            orderedPanels,
            Math.max(0, missing)
        );
    }

    private static int coordinateAlong(
        final BlockPos position,
        final Direction axis
    ) {
        return position.getX() * axis.getStepX()
            + position.getZ() * axis.getStepZ();
    }

    private static RadarDisplayStructure invalid(
        final BlockPos position,
        final Direction facing,
        final List<BlockPos> panels,
        final int missing
    ) {
        return new RadarDisplayStructure(
            false,
            0,
            facing,
            position,
            panels,
            missing
        );
    }
}
