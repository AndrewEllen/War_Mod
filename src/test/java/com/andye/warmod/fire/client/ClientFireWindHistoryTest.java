package com.andye.warmod.fire.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class ClientFireWindHistoryTest {
    private static final double EPSILON = 1.0E-8;

    @Test
    void aNewSnapshotWindCannotChangePastSmokeAdvection() {
        ClientFireVisualManager.WindHistory original =
            ClientFireVisualManager.WindHistory.initial(0L, new Vec3(1.0, 0.0, 0.0));
        double before = original.displacement(0.0, 20.0).x;

        original.retarget(20L, new Vec3(-1.0, 0.0, 0.0));

        assertEquals(20.0, before, EPSILON);
        assertEquals(before, original.displacement(0.0, 20.0).x, EPSILON,
            "a packet at tick 20 must not rewrite a 20-tick-old smoke position");
        assertEquals(0.0, original.displacement(20.0, 26.0).x, EPSILON,
            "the six-tick retarget smoothly averages the old and new winds");
        assertEquals(-4.0, original.displacement(26.0, 30.0).x, EPSILON);
    }

    @Test
    void tickSamplingAlsoChangesOnlyFutureTravel() {
        ClientFireVisualManager.WindHistory history =
            ClientFireVisualManager.WindHistory.initial(0L, new Vec3(0.5, 0.0, 0.0));
        history.sample(10L, new Vec3(2.0, 0.0, 0.0));

        assertEquals(5.0, history.displacement(0.0, 10.0).x, EPSILON);
        assertEquals(1.25, history.displacement(10.0, 11.0).x, EPSILON,
            "the newly sampled velocity is blended during the next tick only");
    }

    @Test
    void fixedRingKeepsPrefixQueriesCorrectAfterHistoryWraps() {
        ClientFireVisualManager.WindHistory history =
            ClientFireVisualManager.WindHistory.initial(0L, new Vec3(0.75, 0.0, 0.0));
        for (long tick = 1L; tick <= 500L; tick++)
            history.sample(tick, new Vec3(0.75, 0.0, 0.0));

        double liveDisplacement = history.displacement(400.0, 500.0).x;
        assertEquals(75.0, liveDisplacement, EPSILON,
            "ring eviction must preserve the cumulative integral for live particles");
        assertDoesNotThrow(() -> history.sample(100L, new Vec3(-3.0, 0.0, 0.0)),
            "a rollback older than the retained ring must be a no-op, not a crash");
        assertEquals(liveDisplacement, history.displacement(400.0, 500.0).x, EPSILON);
    }

    @Test
    void staleOrRepeatedSamplesCannotRewriteTheExistingPath() {
        ClientFireVisualManager.WindHistory history =
            ClientFireVisualManager.WindHistory.initial(0L, new Vec3(1.0, 0.0, 0.0));
        history.sample(10L, new Vec3(2.0, 0.0, 0.0));
        double beforePast = history.displacement(0.0, 10.0).x;
        double beforeLive = history.displacement(10.0, 11.0).x;

        assertDoesNotThrow(() -> history.sample(10L, new Vec3(-4.0, 0.0, 0.0)));
        assertDoesNotThrow(() -> history.sample(-200L, new Vec3(-4.0, 0.0, 0.0)));

        assertEquals(beforePast, history.displacement(0.0, 10.0).x, EPSILON);
        assertEquals(beforeLive, history.displacement(10.0, 11.0).x, EPSILON,
            "a stale sample must not change the live relative displacement either");
    }

    @Test
    void visualClockContinuesAcrossAWorldTimeRollback() {
        ClientFireVisualManager.VisualClock clock = new ClientFireVisualManager.VisualClock();
        clock.reset(512L);
        clock.synchronizeServer(10_000L);
        assertEquals(502L, clock.mapServerTime(9_990L));

        assertEquals(513L, clock.advance(274L),
            "client world time may move backwards without rewinding presentation time");
        clock.synchronizeServer(10_001L);
        assertEquals(513L, clock.mapServerTime(10_001L),
            "new server timestamps should keep their mapped current visual time");
        assertEquals(514L, clock.advance(275L));
    }
}
