package com.andye.warmod.fire.network;

import com.andye.warmod.fire.FirePhase;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * One world-anchored, topology-preserving visual aggregate.  Extents are
 * half-extents in blocks and the occupancy mask describes an 8x8 horizontal
 * subdivision of the cell.
 */
public record FireVisualCell(long id, long parentId, FireVisualBand band, int cellSize,
    int cellX, int cellY, int cellZ, Vec3 centroid, Vec3 extents,
    long occupancyMask, float flameEnergy, float smokeMass,
    float maximumHeat, float averageIntensity, float coveredArea,
    float clumpStrength, Vec3 wind, int hostCount, long seed, Direction dominantFace,
    FirePhase phase, long ignitionGameTime) {

    /** Compatibility constructor for isolated fixtures and non-hierarchical callers. */
    public FireVisualCell(final long id, final FireVisualBand band, final int cellSize,
        final int cellX, final int cellY, final int cellZ, final Vec3 centroid,
        final Vec3 extents, final long occupancyMask, final float flameEnergy,
        final float smokeMass, final float maximumHeat, final float averageIntensity,
        final float coveredArea, final float clumpStrength, final Vec3 wind,
        final int hostCount, final long seed, final Direction dominantFace,
        final FirePhase phase, final long ignitionGameTime) {
        this(id, 0L, band, cellSize, cellX, cellY, cellZ, centroid, extents,
            occupancyMask, flameEnergy, smokeMass, maximumHeat, averageIntensity,
            coveredArea, clumpStrength, wind, hostCount, seed, dominantFace,
            phase, ignitionGameTime);
    }

    public boolean valid() {
        return id > 0L && parentId >= 0L && parentId != id && band != null
            && cellSize > 0 && cellSize <= 4_096
            && centroid != null && centroid.isFinite() && extents != null
            && extents.isFinite() && extents.x >= 0.0 && extents.y >= 0.0
            && extents.z >= 0.0 && occupancyMask != 0L
            && finiteNonNegative(flameEnergy) && finiteNonNegative(smokeMass)
            && finiteNonNegative(maximumHeat) && finiteNonNegative(averageIntensity)
            && finiteNonNegative(coveredArea) && finiteNonNegative(clumpStrength)
            && clumpStrength <= 2.0F && wind != null && wind.isFinite()
            && hostCount > 0 && hostCount <= 65_536 && dominantFace != null
            && phase != null;
    }

    public boolean hasParent() { return parentId > 0L; }

    public float boundingRadius() {
        return (float) Math.max(0.75,
            Math.sqrt(extents.x * extents.x + extents.y * extents.y
                + extents.z * extents.z));
    }

    private static boolean finiteNonNegative(final float value) {
        return Float.isFinite(value) && value >= 0.0F;
    }
}
