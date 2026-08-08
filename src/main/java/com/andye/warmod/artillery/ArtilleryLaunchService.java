package com.andye.warmod.artillery;

import com.andye.warmod.entity.ArtilleryShellEntity;
import com.andye.warmod.icbm.IcbmConstants;
import com.andye.warmod.warhead.WarheadImpactChunkLeaseManager;
import com.andye.warmod.warhead.WarheadYield;
import com.andye.warmod.warhead.WarheadYieldRegistry;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class ArtilleryLaunchService {
    private ArtilleryLaunchService() { }

    public static FireResult fire(final ServerLevel level, final @Nullable UUID ownerPlayerId,
        final Vec3 muzzle, final Vec3 intendedTarget, final WarheadYield yield,
        final boolean cluster) {
        if (level == null || muzzle == null || intendedTarget == null || yield == null
            || !muzzle.isFinite() || !intendedTarget.isFinite()) {
            return FireResult.failed("Invalid artillery firing data");
        }
        if (!level.getWorldBorder().isWithinBounds(intendedTarget)
            || level.isOutsideBuildHeight(BlockPos.containing(intendedTarget))) {
            return FireResult.failed("Target is outside the playable world");
        }

        ArtilleryBallistics.Solution solution = ArtilleryBallistics.solve(level, muzzle, intendedTarget)
            .orElse(null);
        if (solution == null) {
            return FireResult.failed("Target is outside artillery ballistic limits");
        }

        UUID shellId = UUID.randomUUID();
        long seed = deriveSeed(shellId);
        ArtilleryShellEntity shell = new ArtilleryShellEntity(level, shellId,
            ownerPlayerId, muzzle, intendedTarget, solution.initialVelocity(),
            solution.flightTicks(), seed, yield, cluster);
        if (!level.addFreshEntity(shell)) {
            return FireResult.failed("Artillery shell could not be spawned");
        }
        WarheadYieldRegistry.put(level, shellId, yield);
        WarheadImpactChunkLeaseManager.holdApproach(level, shellId, muzzle,
            intendedTarget, solution.flightTicks() + IcbmConstants.IMPACT_CHUNK_TAIL_TICKS);

        return FireResult.accepted(1, solution.angleDegrees(),
            solution.horizontalRange(), solution.apexY(), solution.flightTicks());
    }

    private static long deriveSeed(final UUID id) {
        long value = id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 19);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    public record FireResult(boolean accepted, String message, int shells,
        double angleDegrees, double rangeBlocks, double apexY, int flightTicks) {
        public static FireResult accepted(final int shells, final double angle,
            final double range, final double apex, final int ticks) {
            return new FireResult(true, "Artillery fired", shells, angle, range, apex, ticks);
        }
        public static FireResult failed(final String message) {
            return new FireResult(false, message, 0, 0.0, 0.0, 0.0, 0);
        }
    }
}
