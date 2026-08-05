package com.andye.warmod.icbm;

import com.andye.warmod.WarMod;
import com.andye.warmod.warhead.WarheadImpactChunkLeaseManager;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

public final class IcbmChunkTicketController {
    private final UUID missileId;
    private final Set<ChunkPos> held = new HashSet<>();

    private IcbmFlightPlan plan;
    private Set<ChunkPos> separationWindow = Set.of();
    private long separationReleaseElapsed = Long.MAX_VALUE;
    private boolean finalApproachLeaseHeld;
    private boolean released;

    public IcbmChunkTicketController(final IcbmFlightPlan plan) {
        missileId = plan.missileId();
        this.plan = plan;
        rebuildSeparationWindow();
    }

    public void updatePlan(final IcbmFlightPlan revised) {
        if (!missileId.equals(revised.missileId())) {
            throw new IllegalArgumentException("Missile identity changed");
        }

        boolean approachChanged =
            !plan.separationPosition().equals(revised.separationPosition())
                || !plan.intendedTarget().equals(revised.intendedTarget())
                || plan.separationTick() != revised.separationTick();

        plan = revised;
        rebuildSeparationWindow();

        if (approachChanged) {
            /* Re-arm on the next update. The lease manager merges safely if a
             * previous route had already been acquired for this missile ID.
             */
            finalApproachLeaseHeld = false;
        }
    }

    public void update(final ServerLevel level, final long elapsed) {
        if (released || (elapsed & 1L) != 0L) {
            return;
        }

        preArmFinalApproach(level, elapsed);

        HashSet<ChunkPos> wanted = new HashSet<>();
        long boostEnd = (long)plan.ignitionTicks() + plan.boostTicks();

        if (elapsed <= boostEnd + IcbmConstants.BOOST_TICKET_TAIL_TICKS) {
            Vec3 carrier = IcbmTrajectory.position(plan, elapsed);
            IcbmChunkTicketRegistry.addWindow(
                wanted,
                IcbmChunkTicketRegistry.chunk(carrier),
                IcbmConstants.CARRIER_CHUNK_RADIUS
            );
        }

        if (elapsed
                >= plan.separationTick()
                    - IcbmConstants.SEPARATION_TICKET_LEAD_TICKS
            && elapsed < separationReleaseElapsed) {
            wanted.addAll(separationWindow);
        }

        replace(level, wanted);
    }

    public boolean separationReady(final ServerLevel level) {
        return !separationWindow.isEmpty()
            && IcbmChunkTicketRegistry.allLoaded(level, separationWindow);
    }

    public void markSeparated(final long elapsed) {
        separationReleaseElapsed = elapsed
            + IcbmConstants.SEPARATION_TICKET_TAIL_TICKS;
    }

    public void releaseAll(final ServerLevel level) {
        if (released) {
            return;
        }

        replace(level, Set.of());
        released = true;

        if (SharedConstants.IS_RUNNING_IN_IDE) {
            WarMod.LOGGER.info(
                "ICBM {} released carrier tickets",
                missileId
            );
        }
    }

    private void preArmFinalApproach(
        final ServerLevel level,
        final long elapsed
    ) {
        if (finalApproachLeaseHeld
            || elapsed < plan.separationTick()
                - IcbmConstants.FINAL_APPROACH_TICKET_LEAD_TICKS) {
            return;
        }

        long ticksUntilSeparation = Math.max(
            0L,
            plan.separationTick() - elapsed
        );
        long requestedTicks = ticksUntilSeparation
            + IcbmConstants.MAXIMUM_TERMINAL_TICKS
            + IcbmConstants.IMPACT_CHUNK_TAIL_TICKS;
        int leaseTicks = (int)Math.min(
            Integer.MAX_VALUE,
            Math.max(1L, requestedTicks)
        );

        WarheadImpactChunkLeaseManager.holdApproach(
            level,
            missileId,
            plan.separationPosition(),
            plan.intendedTarget(),
            leaseTicks
        );
        finalApproachLeaseHeld = true;
    }

    private void rebuildSeparationWindow() {
        HashSet<ChunkPos> window = new HashSet<>();
        IcbmChunkTicketRegistry.addWindow(
            window,
            IcbmChunkTicketRegistry.chunk(plan.separationPosition()),
            1
        );
        separationWindow = Set.copyOf(window);
    }

    private void replace(
        final ServerLevel level,
        final Set<ChunkPos> wanted
    ) {
        for (ChunkPos chunk : wanted) {
            if (held.add(chunk)) {
                IcbmChunkTicketRegistry.acquire(level, chunk);
            }
        }

        for (ChunkPos chunk : Set.copyOf(held)) {
            if (wanted.contains(chunk)) {
                continue;
            }

            IcbmChunkTicketRegistry.release(level, chunk);
            held.remove(chunk);
        }
    }
}
