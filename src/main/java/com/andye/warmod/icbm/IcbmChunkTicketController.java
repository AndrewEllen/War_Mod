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
    private static final long APPROACH_LEASE_REFRESH_TICKS = 200L;

    private final UUID missileId;
    private final Set<ChunkPos> held = new HashSet<>();

    private IcbmFlightPlan plan;
    private Set<ChunkPos> separationWindow = Set.of();
    private long separationReleaseElapsed = Long.MAX_VALUE;
    private long nextApproachLeaseRefreshElapsed;
    private boolean finalApproachLeaseHeld;
    private boolean finalApproachLeaseDirty;
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
            /*
             * Guidance can move the intended target after launch. Replace the
             * old approach lease on the next server tick rather than merging
             * the obsolete target area into the new one.
             */
            finalApproachLeaseDirty = true;
            nextApproachLeaseRefreshElapsed = 0L;
        }
    }

    public void update(final ServerLevel level, final long elapsed) {
        if (released || (elapsed & 1L) != 0L) {
            return;
        }

        /*
         * The target is known from launch. Begin generating and simulation-
         * loading the complete defensive approach area immediately instead of
         * waiting until ten seconds before separation.
         */
        maintainFinalApproachLease(level, elapsed);

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

    /**
     * Terminal separation is blocked until both the handoff area and the full
     * 7x7 impact square plus 500-block, three-chunk-wide incoming corridor are
     * loaded at entity-ticking simulation level.
     */
    public boolean separationReady(final ServerLevel level) {
        return finalApproachLeaseHeld
            && !finalApproachLeaseDirty
            && !separationWindow.isEmpty()
            && IcbmChunkTicketRegistry.allLoaded(level, separationWindow)
            && WarheadImpactChunkLeaseManager.ready(level, missileId);
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
        WarheadImpactChunkLeaseManager.release(level, missileId);
        finalApproachLeaseHeld = false;
        finalApproachLeaseDirty = false;
        released = true;

        if (SharedConstants.IS_RUNNING_IN_IDE) {
            WarMod.LOGGER.info(
                "ICBM {} released carrier and final-approach tickets",
                missileId
            );
        }
    }

    private void maintainFinalApproachLease(
        final ServerLevel level,
        final long elapsed
    ) {
        if (finalApproachLeaseDirty && finalApproachLeaseHeld) {
            WarheadImpactChunkLeaseManager.release(level, missileId);
            finalApproachLeaseHeld = false;
            finalApproachLeaseDirty = false;
        }

        if (finalApproachLeaseHeld
            && elapsed < nextApproachLeaseRefreshElapsed) {
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
        finalApproachLeaseDirty = false;
        nextApproachLeaseRefreshElapsed = elapsed
            + APPROACH_LEASE_REFRESH_TICKS;
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
