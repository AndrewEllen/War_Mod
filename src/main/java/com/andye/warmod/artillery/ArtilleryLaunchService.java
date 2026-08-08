package com.andye.warmod.artillery;

import com.andye.warmod.entity.ArtilleryShellEntity;
import com.andye.warmod.icbm.IcbmConstants;
import com.andye.warmod.warhead.WarheadImpactChunkLeaseManager;
import com.andye.warmod.warhead.WarheadYield;
import com.andye.warmod.warhead.WarheadYieldRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class ArtilleryLaunchService {
    private ArtilleryLaunchService() {
    }

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

        List<TargetSolution> solutions = cluster
            ? clusterSolutions(level, muzzle, intendedTarget)
            : ArtilleryBallistics.solve(level, muzzle, intendedTarget)
                .map(solution -> List.of(new TargetSolution(intendedTarget, solution)))
                .orElseGet(List::of);
        if (solutions.isEmpty()) {
            return FireResult.failed("Target is outside artillery ballistic limits");
        }

        ArrayList<ArtilleryShellEntity> spawned = new ArrayList<>(solutions.size());
        long rootSeed = deriveSeed(UUID.randomUUID());
        for (int index = 0; index < solutions.size(); index++) {
            TargetSolution targetSolution = solutions.get(index);
            UUID shellId = UUID.randomUUID();
            long seed = rootSeed + index * 0x9E3779B97F4A7C15L;
            ArtilleryBallistics.Solution solution = targetSolution.solution();
            ArtilleryShellEntity shell = new ArtilleryShellEntity(level, shellId,
                ownerPlayerId, muzzle, targetSolution.target(), solution.initialVelocity(),
                solution.flightTicks(), seed, yield);
            if (!level.addFreshEntity(shell)) {
                for (ArtilleryShellEntity existing : spawned) existing.discard();
                return FireResult.failed("Artillery shell could not be spawned");
            }
            WarheadYieldRegistry.put(level, shellId, yield);
            WarheadImpactChunkLeaseManager.holdApproach(level, shellId, muzzle,
                targetSolution.target(), solution.flightTicks()
                    + IcbmConstants.IMPACT_CHUNK_TAIL_TICKS);
            spawned.add(shell);
        }

        ArtilleryBallistics.Solution primary = solutions.getFirst().solution();
        return FireResult.accepted(spawned.size(), primary.angleDegrees(),
            primary.horizontalRange(), primary.apexY(), primary.flightTicks());
    }

    private static List<TargetSolution> clusterSolutions(final ServerLevel level,
        final Vec3 muzzle, final Vec3 target) {
        ArrayList<TargetSolution> output = new ArrayList<>(ArtilleryConstants.CLUSTER_CHILDREN);
        long seed = Double.doubleToLongBits(target.x) ^ Long.rotateLeft(Double.doubleToLongBits(target.z), 17);
        double rotation = ((seed >>> 8) & 65535L) / 65535.0 * Math.PI * 2.0;
        for (int index = 0; index < ArtilleryConstants.CLUSTER_CHILDREN; index++) {
            double angle = rotation + index * Math.PI * 0.5;
            Vec3 childTarget = target.add(
                Math.cos(angle) * ArtilleryConstants.CLUSTER_SPREAD_RADIUS_BLOCKS,
                0.0,
                Math.sin(angle) * ArtilleryConstants.CLUSTER_SPREAD_RADIUS_BLOCKS);
            if (!level.getWorldBorder().isWithinBounds(childTarget)
                || level.isOutsideBuildHeight(BlockPos.containing(childTarget))) return List.of();
            Optional<ArtilleryBallistics.Solution> solution =
                ArtilleryBallistics.solve(level, muzzle, childTarget);
            if (solution.isEmpty()) return List.of();
            output.add(new TargetSolution(childTarget, solution.get()));
        }
        return List.copyOf(output);
    }

    private static long deriveSeed(final UUID id) {
        long value = id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 19);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private record TargetSolution(Vec3 target, ArtilleryBallistics.Solution solution) {
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
