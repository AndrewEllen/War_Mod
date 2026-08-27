package com.andye.warmod.fire.network;

import com.andye.warmod.fire.FirePhase;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * One world-anchored, topology-preserving visual aggregate.  Extents are
 * half-extents in blocks and the occupancy mask describes an 8x8 horizontal
 * subdivision of the cell.
 */
public record FireVisualCell(long id, FireVisualBand band, int cellSize,
    int cellX, int cellY, int cellZ, Vec3 centroid, Vec3 extents,
    long occupancyMask, float flameEnergy, float smokeMass,
    float maximumHeat, float averageIntensity, float coveredArea,
    Vec3 wind, int hostCount, long seed, Direction dominantFace,
    FirePhase phase, long ignitionGameTime) {

    public boolean valid() {
        return id > 0L && band != null && cellSize > 0 && cellSize <= 4_096
            && centroid != null && centroid.isFinite() && extents != null
            && extents.isFinite() && extents.x >= 0.0 && extents.y >= 0.0
            && extents.z >= 0.0 && occupancyMask != 0L
            && finiteNonNegative(flameEnergy) && finiteNonNegative(smokeMass)
            && finiteNonNegative(maximumHeat) && finiteNonNegative(averageIntensity)
            && finiteNonNegative(coveredArea) && wind != null && wind.isFinite()
            && hostCount > 0 && hostCount <= 65_536 && dominantFace != null
            && phase != null;
    }

    public float boundingRadius() {
        return (float) Math.max(0.75,
            Math.sqrt(extents.x * extents.x + extents.y * extents.y
                + extents.z * extents.z));
    }

    private static boolean finiteNonNegative(final float value) {
        return Float.isFinite(value) && value >= 0.0F;
    }
}
