package com.andye.warmod.radar.display.client;

import java.util.List;
import net.minecraft.world.phys.Vec3;

public record RadarDisplayRouteSection(
    List<Vec3> points,
    int completedSegments
) {
    public RadarDisplayRouteSection {
        points = List.copyOf(points);

        completedSegments = Math.max(
            0,
            Math.min(
                completedSegments,
                Math.max(0, points.size() - 1)
            )
        );
    }
}
