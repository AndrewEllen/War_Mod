package com.andye.warmod.warhead;

import com.andye.warmod.icbm.IcbmConstants;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/** The only gameplay source for warhead radii and required preparation chunks. */
public final class WarheadFootprintCalculator {
    private WarheadFootprintCalculator() { }

    public static WarheadFootprint calculate(final WarheadPayloadType payload,
        final WarheadYield yield, final Vec3 target) {
        if (payload == null || yield == null || target == null || !target.isFinite()) {
            throw new IllegalArgumentException("Warhead footprint inputs must be finite");
        }
        if (yield.payloadType() != payload) {
            throw new IllegalArgumentException("Yield and payload type disagree");
        }

        StrategicExplosionProfile profile = StrategicExplosionProfiles.get(yield);
        float visualScale = Mth.clamp(yield.visualScale(), 0.28F, 4.2F);
        double craterRadius = yield.nuclear()
            ? 12.0 + 13.0 * visualScale
            : profile.horizontalRadius();
        double aftermathRadius = yield.nuclear()
            ? Math.ceil(nuclearAftermathRadius(craterRadius, visualScale))
            : profile.horizontalRadius() * profile.aftermathRadiusScale();
        double glassRadius = yield.nuclear()
            ? nuclearGlassRadius((int) aftermathRadius, visualScale)
            : conventionalGlassRadius(visualScale);
        double biomeRadius = yield.nuclear()
            ? NuclearBiomeDome.radius(aftermathRadius)
            : 0.0;
        double maximumRadius = Math.max(Math.max(craterRadius, aftermathRadius),
            Math.max(glassRadius, biomeRadius));
        LongSet required = requiredChunks(target.x, target.z, maximumRadius);
        return new WarheadFootprint(craterRadius, aftermathRadius, glassRadius,
            biomeRadius, maximumRadius, required);
    }

    public static LongSet chunksIntersectingCircle(final double centerX,
        final double centerZ, final double radius) {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ)
            || !Double.isFinite(radius) || radius < 0.0) {
            throw new IllegalArgumentException("Invalid footprint circle");
        }
        int minimumChunkX = Mth.floor((centerX - radius) / 16.0);
        int maximumChunkX = Mth.floor((centerX + radius) / 16.0);
        int minimumChunkZ = Mth.floor((centerZ - radius) / 16.0);
        int maximumChunkZ = Mth.floor((centerZ + radius) / 16.0);
        LongOpenHashSet result = new LongOpenHashSet();
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                if (chunkIntersectsCircle(chunkX, chunkZ, centerX, centerZ, radius)) {
                    result.add(ChunkPos.pack(chunkX, chunkZ));
                }
            }
        }
        return result;
    }

    static LongSet requiredChunks(final double centerX, final double centerZ,
        final double maximumMutationRadius) {
        LongSet required = chunksIntersectingCircle(centerX, centerZ,
            maximumMutationRadius);
        addSquareWindow(required, new ChunkPos(Mth.floor(centerX) >> 4,
            Mth.floor(centerZ) >> 4), IcbmConstants.MINIMUM_PREPARATION_CHUNK_RADIUS);
        return required;
    }

    static boolean chunkIntersectsCircle(final int chunkX, final int chunkZ,
        final double centerX, final double centerZ, final double radius) {
        double minimumX = chunkX * 16.0;
        double minimumZ = chunkZ * 16.0;
        double maximumX = minimumX + 16.0;
        double maximumZ = minimumZ + 16.0;
        double closestX = Mth.clamp(centerX, minimumX, maximumX);
        double closestZ = Mth.clamp(centerZ, minimumZ, maximumZ);
        double dx = closestX - centerX;
        double dz = closestZ - centerZ;
        return dx * dx + dz * dz <= radius * radius;
    }

    private static void addSquareWindow(final LongSet output, final ChunkPos center,
        final int radius) {
        for (int chunkX = center.x() - radius; chunkX <= center.x() + radius; chunkX++) {
            for (int chunkZ = center.z() - radius; chunkZ <= center.z() + radius; chunkZ++) {
                output.add(ChunkPos.pack(chunkX, chunkZ));
            }
        }
    }

    static double nuclearAftermathRadius(final double craterRadius,
        final float visualScale) {
        double multiplier = visualScale < 2.20F ? 6.125 : 5.625;
        return craterRadius * multiplier;
    }

    static int nuclearGlassRadius(final int aftermathRadius,
        final float visualScale) {
        return Mth.ceil(Math.max(aftermathRadius * 1.28,
            conventionalGlassRadius(visualScale) * 1.55));
    }

    static double conventionalGlassRadius(final float visualScale) {
        if (visualScale < 0.49F) return 36.0;
        if (visualScale < 0.82F) return 64.0;
        if (visualScale < 1.19F) return 104.0;
        return 152.0;
    }

    static WarheadYield yieldForVisualScale(final WarheadPayloadType payload,
        final float visualScale) {
        WarheadYield closest = WarheadYield.defaultFor(payload);
        double closestDelta = Double.POSITIVE_INFINITY;
        for (WarheadYield candidate : WarheadYield.values()) {
            if (candidate.payloadType() != payload) continue;
            double delta = Math.abs(candidate.visualScale() - visualScale);
            if (delta < closestDelta) {
                closest = candidate;
                closestDelta = delta;
            }
        }
        return closest;
    }
}
