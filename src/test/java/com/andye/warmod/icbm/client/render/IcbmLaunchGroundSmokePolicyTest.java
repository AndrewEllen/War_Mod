package com.andye.warmod.icbm.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class IcbmLaunchGroundSmokePolicyTest {
    private static final long SEED = 0x5EEDB10BL;

    @Test
    void lobeIsDeterministicAndFiniteForItsEntireLifetime() {
        var first = IcbmLaunchGroundSmokePolicy.sample(SEED, 5, 64.0, 1.0F);
        var repeated = IcbmLaunchGroundSmokePolicy.sample(SEED, 5, 64.0, 1.0F);
        assertEquals(first, repeated);
        assertTrue(Double.isFinite(first.x()) && Double.isFinite(first.y())
            && Double.isFinite(first.z()));
        assertTrue(Float.isFinite(first.radius()) && Float.isFinite(first.alpha()));
    }

    @Test
    void groundRollExpandsWhileVerticalFeedBuildsARealBillow() {
        var earlyOuter = IcbmLaunchGroundSmokePolicy.sample(SEED, 80, 8.0, 1.0F);
        var lateOuter = IcbmLaunchGroundSmokePolicy.sample(SEED, 80, 120.0, 1.0F);
        assertFalse(earlyOuter.verticalFeed());
        assertTrue(Math.hypot(lateOuter.x(), lateOuter.z())
            > Math.hypot(earlyOuter.x(), earlyOuter.z()));

        var earlyFeed = IcbmLaunchGroundSmokePolicy.sample(SEED, 0, 8.0, 1.0F);
        var lateFeed = IcbmLaunchGroundSmokePolicy.sample(SEED, 0, 120.0, 1.0F);
        assertTrue(earlyFeed.verticalFeed());
        assertTrue(lateFeed.y() > earlyFeed.y());
        assertTrue(lateFeed.y() >= 9.0 && lateFeed.y() <= 21.0,
            "the central feed rises into the bounded smoke volume");
    }

    @Test
    void denseCohortsFillTheThroatBeforeExpandingIntoWideShells() {
        var firstCohort = IcbmLaunchGroundSmokePolicy.sample(SEED, 0, 3.0, 1.0F);
        var laterCohort = IcbmLaunchGroundSmokePolicy.sample(SEED, 57, 3.0, 1.0F);
        assertTrue(firstCohort.alpha() > laterCohort.alpha(),
            "cohorts fade in over time instead of appearing as a single ragged burst");

        double furthestShell = 0.0;
        double highestFeed = 0.0;
        double latestBirth = 0.0;
        for (int ordinal = 0; ordinal < IcbmLaunchGroundSmokePolicy.ICBM_LOBES; ordinal++) {
            var lobe = IcbmLaunchGroundSmokePolicy.sample(SEED, ordinal, 140.0, 1.0F);
            furthestShell = Math.max(furthestShell, Math.hypot(lobe.x(), lobe.z()));
            if (lobe.verticalFeed()) highestFeed = Math.max(highestFeed, lobe.y());
            latestBirth = Math.max(latestBirth, lobe.birthDelay());
            assertTrue(lobe.radius() >= 0.0F && lobe.radius() <= 2.4F,
                "small smoke lobes never regress into oversized flat cards");
        }
        assertTrue(furthestShell >= 14.0,
            "outer shell centres form a roughly 30-block-wide ground cloud");
        assertTrue(highestFeed >= 12.0 && highestFeed <= 21.0);
        assertTrue(latestBirth > 90.0 && latestBirth <= 96.0,
            "cohorts keep feeding the smoke volume for roughly the first five seconds");
    }

    @Test
    void lobeFadesAfterItsPersistentLaunchWindowAndCardBudgetIsBounded() {
        var alive = IcbmLaunchGroundSmokePolicy.sample(SEED, 2, 150.0, 1.0F);
        var expired = IcbmLaunchGroundSmokePolicy.sample(SEED, 2,
            IcbmLaunchGroundSmokePolicy.LIFETIME_TICKS + 8.0, 1.0F);
        assertTrue(alive.alpha() > 0.0F);
        assertEquals(0.0F, expired.alpha(), 1.0E-6F);
        assertEquals(960, IcbmLaunchGroundSmokePolicy.ICBM_LOBES);
        assertEquals(96, IcbmLaunchGroundSmokePolicy.ANTI_AIR_LOBES);
        assertTrue(IcbmLaunchGroundSmokePolicy.LIFETIME_TICKS > 320.0,
            "the pad cloud remains after the minimum carrier separation time");
    }
}
