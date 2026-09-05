package com.andye.warmod.warhead;

import net.minecraft.util.Mth;

/**
 * Immutable, authoritative terrain policy dimensions for one warhead yield.
 * Snapshot selection, worker compilation and commit scheduling must consume this
 * object rather than deriving radii independently from visual scale.
 */
public record NuclearTerrainProfile(
    WarheadYield yield,
    double horizontalRadius,
    double upwardRadius,
    double downwardRadius,
    double guaranteedVoidScale,
    double boundaryRoughness,
    float maximumDestroyResistance,
    float edgeResistanceScale,
    float entityBlastRadius,
    double aftermathRadius,
    double glassRadius,
    double biomeRadius,
    double maximumMutationRadius
) {
    public NuclearTerrainProfile {
        if (yield == null || !finiteNonNegative(horizontalRadius)
            || !finiteNonNegative(upwardRadius) || !finiteNonNegative(downwardRadius)
            || !finiteNonNegative(guaranteedVoidScale)
            || !finiteNonNegative(boundaryRoughness)
            || !Float.isFinite(maximumDestroyResistance)
            || maximumDestroyResistance < 0.0F || !Float.isFinite(edgeResistanceScale)
            || edgeResistanceScale < 0.0F || !Float.isFinite(entityBlastRadius)
            || entityBlastRadius < 0.0F || !finiteNonNegative(aftermathRadius)
            || !finiteNonNegative(glassRadius) || !finiteNonNegative(biomeRadius)
            || !finiteNonNegative(maximumMutationRadius)) {
            throw new IllegalArgumentException("Invalid nuclear terrain profile");
        }
        double requiredMaximum = Math.max(Math.max(horizontalRadius, aftermathRadius),
            Math.max(glassRadius, biomeRadius));
        if (maximumMutationRadius + 1.0E-6 < requiredMaximum) {
            throw new IllegalArgumentException("Terrain profile maximum omits a mutation radius");
        }
    }

    static NuclearTerrainProfile forYield(final WarheadYield yield) {
        if (yield == null) throw new IllegalArgumentException("Yield is required");
        StrategicExplosionProfile strategic = StrategicExplosionProfiles.get(yield);
        float visualScale = Mth.clamp(yield.visualScale(), 0.28F, 4.2F);

        /* Preserve the established absolute aftermath/glass reach from the behavioural
         * reference. Only excavation ownership is unified on the strategic profile. */
        double legacyAftermathScaleRadius = yield.nuclear()
            ? 12.0 + 13.0 * visualScale : strategic.horizontalRadius();
        double aftermath = yield.nuclear()
            ? Math.ceil(WarheadFootprintCalculator.nuclearAftermathRadius(
                legacyAftermathScaleRadius, visualScale))
            : strategic.horizontalRadius() * strategic.aftermathRadiusScale();
        double glass = yield.nuclear()
            ? WarheadFootprintCalculator.nuclearGlassRadius((int) aftermath, visualScale)
            : WarheadFootprintCalculator.conventionalGlassRadius(visualScale);
        double biome = yield.nuclear() ? NuclearBiomeDome.radius(aftermath) : 0.0;
        double maximum = Math.max(Math.max(strategic.horizontalRadius(), aftermath),
            Math.max(glass, biome));
        return new NuclearTerrainProfile(yield, strategic.horizontalRadius(),
            strategic.upwardRadius(), strategic.downwardRadius(),
            strategic.guaranteedVoidScale(), strategic.boundaryRoughness(),
            strategic.maximumDestroyResistance(), strategic.edgeResistanceScale(),
            strategic.entityBlastRadius(), aftermath, glass, biome, maximum);
    }

    private static boolean finiteNonNegative(final double value) {
        return Double.isFinite(value) && value >= 0.0;
    }
}
