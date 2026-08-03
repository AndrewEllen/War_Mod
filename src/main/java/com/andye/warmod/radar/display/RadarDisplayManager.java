package com.andye.warmod.radar.display;

import com.andye.warmod.block.ModBlocks;
import com.andye.warmod.block.RadarDisplayPanelBlock;
import com.andye.warmod.block.entity.RadarDisplayPanelBlockEntity;
import com.andye.warmod.radar.display.network.RadarDisplayNetworking;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public final class RadarDisplayManager {
    private RadarDisplayManager() {
    }

    public static RadarDisplayStructure rebuild(
        final ServerLevel level,
        final BlockPos panel
    ) {
        RadarDisplayStructure structure =
            RadarDisplayStructure.resolve(level, panel);

        RadarDisplayLink commonLink = null;
        UUID existingId = null;

        List<OldController> oldControllers = new ArrayList<>();

        for (BlockPos position : structure.panels()) {
            if (!(level.getBlockEntity(position)
                instanceof RadarDisplayPanelBlockEntity display)) {
                continue;
            }

            if (commonLink == null && display.link() != null) {
                commonLink = display.link();
            }

            if (existingId == null && display.displayId() != null) {
                existingId = display.displayId();
            }

            if (display.controllerPanel()
                && display.displayId() != null) {
                oldControllers.add(new OldController(
                    display.controller(),
                    display.displayId()
                ));
            }
        }

        for (OldController old : oldControllers) {
            RadarDisplayNetworking.clear(
                level,
                old.position(),
                old.displayId()
            );
        }

        UUID displayId = existingId;

        if (displayId == null && structure.valid()) {
            displayId = UUID.nameUUIDFromBytes(
                (
                    level.dimension().identifier()
                    + ":"
                    + structure.controller()
                    + ":"
                    + structure.facing().getSerializedName()
                ).getBytes(StandardCharsets.UTF_8)
            );
        }

        for (BlockPos position : structure.panels()) {
            if (level.getBlockEntity(position)
                instanceof RadarDisplayPanelBlockEntity display) {
                display.configure(
                    displayId,
                    structure.controller(),
                    structure.size(),
                    structure.valid(),
                    commonLink
                );
            }
        }

        if (structure.valid()
            && level.getBlockEntity(structure.controller())
                instanceof RadarDisplayPanelBlockEntity controller) {
            RadarDisplayNetworking.sendState(controller);
        }

        return structure;
    }

    public static void applyLink(
        final ServerLevel level,
        final RadarDisplayStructure structure,
        final RadarDisplayLink link
    ) {
        if (!structure.valid()) {
            return;
        }

        for (BlockPos position : structure.panels()) {
            if (level.getBlockEntity(position)
                instanceof RadarDisplayPanelBlockEntity display) {
                display.link(link);
            }
        }

        if (level.getBlockEntity(structure.controller())
            instanceof RadarDisplayPanelBlockEntity controller) {
            RadarDisplayNetworking.sendState(controller);
        }
    }

    /**
     * Called after one panel is removed. Rebuild each surviving planar
     * component which may have been connected through that panel.
     */
    public static void rebuildNeighbours(
        final ServerLevel level,
        final BlockPos removed,
        final Direction facing
    ) {
        Direction horizontal = facing.getClockWise();

        LinkedHashSet<BlockPos> candidates =
            new LinkedHashSet<>(List.of(
                removed.above(),
                removed.below(),
                removed.relative(horizontal),
                removed.relative(horizontal.getOpposite())
            ));

        for (BlockPos candidate : candidates) {
            BlockState state = level.getBlockState(candidate);

            if (state.is(ModBlocks.RADAR_DISPLAY_PANEL)
                && state.getValue(RadarDisplayPanelBlock.FACING)
                    == facing) {
                rebuild(level, candidate);
            }
        }
    }

    private record OldController(
        BlockPos position,
        UUID displayId
    ) {
    }
}
