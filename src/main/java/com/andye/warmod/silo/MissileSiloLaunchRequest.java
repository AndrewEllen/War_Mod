package com.andye.warmod.silo;

import com.andye.warmod.antiair.*;
import com.andye.warmod.item.component.TargetCoordinates;
import com.andye.warmod.warhead.WarheadYield;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

public record MissileSiloLaunchRequest(UUID requestId, UUID siloId, BlockPos siloCentre,
    MissileSiloLaunchTrigger trigger, @Nullable UUID triggeringPlayerId, String triggeringPlayerName,
    SiloMissileType missileType, @Nullable WarheadYield strategicYield,
    @Nullable TargetCoordinates strategicTarget,
    @Nullable AntiAirTargetLock interceptorTargetLock, @Nullable AntiAirInterceptSolution interceptorSolution,
    @Nullable AntiAirLaunchMode interceptorLaunchMode, long creationGameTime, Set<ChunkPos> temporaryTickets) {
    public MissileSiloLaunchRequest {
        boolean strategic = missileType.role() == SiloMissileRole.STRATEGIC_STRIKE;
        if (strategic) {
            if (strategicYield == null || strategicTarget == null || interceptorTargetLock != null
                || interceptorSolution != null || interceptorLaunchMode != null) {
                throw new IllegalArgumentException("Invalid strategic silo launch request");
            }
        } else if (strategicYield != null || strategicTarget != null || interceptorLaunchMode == null
            || !validInterceptor(interceptorLaunchMode, interceptorTargetLock, interceptorSolution)) {
            throw new IllegalArgumentException("Invalid interceptor silo launch request");
        }
        temporaryTickets = Set.copyOf(temporaryTickets);
    }

    private static boolean validInterceptor(final AntiAirLaunchMode mode,
        final @Nullable AntiAirTargetLock lock, final @Nullable AntiAirInterceptSolution solution) {
        return switch (mode) {
            case TRACKED_INTERCEPT, BEST_EFFORT_INTERCEPT -> lock != null && solution != null;
            case NO_TARGET_ASCENT -> lock == null && solution == null;
        };
    }
}
