package com.andye.warmod.icbm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andye.warmod.silo.MissileSiloConstants;
import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class IcbmTrajectoryTest {
    @Test
    void boostRemainsVerticalUntilOneHundredFiftyBlocksAboveOceanOrLaunch() {
        IcbmFlightPlan plan = plan(new Vec3(0.0, 20.0, 0.0),
            new Vec3(80.0, 380.0, 0.0), new Vec3(1200.0, 500.0, 0.0), 220);
        boolean sawArc = false;
        for (double tick = plan.ignitionTicks();
            tick <= plan.ignitionTicks() + plan.boostTicks(); tick += 0.25) {
            Vec3 position = IcbmTrajectory.position(plan, tick);
            if (position.y < IcbmConstants.BOOST_CURVE_START_MINIMUM_WORLD_Y - 0.01) {
                assertEquals(plan.launchPosition().x, position.x, 1.0E-6);
                assertEquals(plan.launchPosition().z, position.z, 1.0E-6);
            } else if (Math.abs(position.x - plan.launchPosition().x) > 1.0E-4) {
                sawArc = true;
            }
        }
        assertTrue(sawArc);
    }

    @Test
    void coastIsAContinuousGravityArcWithAnExactEndpoint() {
        IcbmFlightPlan plan = plan(new Vec3(0.0, 64.0, 0.0),
            new Vec3(80.0, 424.0, 0.0), new Vec3(4000.0, 544.0, 0.0), 240);
        double coastStart = plan.ignitionTicks() + plan.boostTicks();
        assertVecEquals(plan.burnoutPosition(), IcbmTrajectory.position(plan, coastStart));
        assertVecEquals(plan.separationPosition(),
            IcbmTrajectory.position(plan, plan.separationTick()));
        assertVecEquals(IcbmTrajectory.coastInitialVelocity(plan),
            IcbmTrajectory.velocity(plan, coastStart));
        assertTrue(IcbmTrajectory.velocity(plan, coastStart).y > 0.0);
        assertTrue(IcbmTrajectory.velocity(plan, plan.separationTick()).y < 0.0);
        assertTrue(IcbmTrajectory.estimatedPeakCoastSpeed(plan)
            <= IcbmConstants.MAXIMUM_CARRIER_SPEED_BLOCKS_PER_TICK + 0.001);
    }

    @Test
    void fullMissileClearsTheDoorPlaneBeforeTheDoorsBeginClosing() {
        Vec3 launch = new Vec3(0.0,
            MissileSiloConstants.MISSILE_HIDDEN_CENTER_OFFSET_Y, 0.0);
        IcbmFlightPlan plan = plan(launch, new Vec3(80.0, 297.0, 0.0),
            new Vec3(1200.0, 500.0, 0.0), 220);
        Vec3 center = IcbmTrajectory.position(plan,
            MissileSiloConstants.DOOR_CLOSE_DELAY_TICKS);
        double modelBottom = center.y
            - MissileSiloConstants.MISSILE_COLLISION_HEIGHT * 0.5;
        double doorPlane = 6.0 / 16.0;
        assertTrue(modelBottom > doorPlane,
            () -> "missile bottom " + modelBottom + " has not cleared " + doorPlane);
    }

    @Test
    void longRangeRouteNeverTurnsBackAndHasOneHighBallisticApex() {
        double[] distances = {10_000.0, 50_000.0, 99_900.0};
        int[] coastDurations = {359, 2025, 4096};
        for (int route = 0; route < distances.length; route++) {
            assertLongRangeRoute(distances[route], coastDurations[route]);
        }
    }

    private static void assertLongRangeRoute(final double distance,
        final int coastDuration) {
        IcbmFlightPlan plan = plan(new Vec3(0.0, 64.0, 0.0),
            new Vec3(0.0, 424.0, 0.0), new Vec3(distance, 544.0, 0.0),
            coastDuration);
        Vec3 downRange = new Vec3(
            plan.separationPosition().x - plan.launchPosition().x,
            0.0,
            plan.separationPosition().z - plan.launchPosition().z
        ).normalize();
        double previousProgress = Double.NEGATIVE_INFINITY;
        for (double tick = 0.0; tick <= plan.separationTick(); tick += 1.0) {
            Vec3 offset = IcbmTrajectory.position(plan, tick)
                .subtract(plan.launchPosition());
            double progress = offset.dot(downRange);
            assertTrue(progress + 1.0E-6 >= previousProgress,
                "carrier reversed down-range near tick " + tick + " at " + distance);
            assertTrue(IcbmTrajectory.velocity(plan, tick).length()
                    <= IcbmConstants.MAXIMUM_CARRIER_SPEED_BLOCKS_PER_TICK + 0.001,
                "carrier exceeded its speed cap near tick " + tick + " at " + distance);
            previousProgress = progress;
        }

        double coastStart = plan.ignitionTicks() + plan.boostTicks();
        int positiveToNegativeCrossings = 0;
        double previousVerticalVelocity = IcbmTrajectory.velocity(plan, coastStart).y;
        double apexY = plan.burnoutPosition().y;
        for (double tick = coastStart + 1.0; tick <= plan.separationTick(); tick += 1.0) {
            Vec3 position = IcbmTrajectory.position(plan, tick);
            double verticalVelocity = IcbmTrajectory.velocity(plan, tick).y;
            if (previousVerticalVelocity > 0.0 && verticalVelocity <= 0.0) {
                positiveToNegativeCrossings++;
            }
            apexY = Math.max(apexY, position.y);
            previousVerticalVelocity = verticalVelocity;
        }
        assertEquals(1, positiveToNegativeCrossings);
        assertTrue(apexY > Math.max(plan.burnoutPosition().y,
            plan.separationPosition().y) + 500.0);

        Vec3 before = IcbmTrajectory.velocity(plan, coastStart - 0.00001);
        Vec3 after = IcbmTrajectory.velocity(plan, coastStart);
        assertTrue(before.distanceTo(after) < 0.0001,
            "burnout must preserve speed and heading at " + distance);
    }

    @Test
    void siloLiftTakesThreeSecondsAndJoinsAscentWithoutJumping() {
        Vec3 launch = new Vec3(0, 61, 0);
        IcbmFlightPlan flight = new IcbmFlightPlan(UUID.randomUUID(), UUID.randomUUID(),
            launch, new Vec3(0, 421, 0), new Vec3(2000, 544, 0),
            new Vec3(2064, 64, 0), 0L, IcbmConstants.SILO_IGNITION_TICKS,
            IcbmConstants.BOOST_TICKS, 400, 7L, WarheadPayloadType.CONVENTIONAL);
        assertEquals(60, flight.ignitionTicks());
        assertEquals(2.0, IcbmTrajectory.position(flight, 30).y - launch.y, 1.0E-9);
        assertEquals(8.0, IcbmTrajectory.position(flight, 60).y - launch.y, 1.0E-9);
        assertTrue(IcbmTrajectory.velocity(flight, 59).y > IcbmTrajectory.velocity(flight, 20).y);
        assertTrue(IcbmTrajectory.position(flight, 60 - 1.0E-5)
            .distanceTo(IcbmTrajectory.position(flight, 60)) < 1.0E-4);
        assertTrue(IcbmTrajectory.velocity(flight, 60 - 1.0E-5)
            .distanceTo(IcbmTrajectory.velocity(flight, 60)) < 1.0E-4);
        double previousPitch = Math.PI / 2;
        for (double tick = .25; tick < flight.separationTick(); tick += .25) {
            Vec3 velocity = IcbmTrajectory.velocity(flight, tick);
            double pitch = Math.atan2(velocity.y, Math.hypot(velocity.x, velocity.z));
            assertTrue(pitch <= previousPitch + 1.0E-7, "slow silo launch must not reintroduce an S bend");
            previousPitch = pitch;
        }
        assertTrue(IcbmTrajectory.position(flight, MissileSiloConstants.DOOR_CLOSE_DELAY_TICKS).y
            - MissileSiloConstants.MISSILE_COLLISION_HEIGHT / 2 > 64.375);
    }

    @Test
    void headingNeverSteepensAgainAfterTheVerticalRise() {
        for (double launchY : new double[]{-40, 64, 600}) {
            for (double distance : new double[]{500, 1_000, 2_000, 10_000, 50_000, 99_900}) {
                Vec3 launch = new Vec3(17, launchY, -23);
                Vec3 burnout = launch.add(0, 360, 0);
                // Diagonal world coordinates also exercise direction projection.
                Vec3 separation = new Vec3(17 + distance * 0.8, 544,
                    -23 - distance * 0.6);
                int duration = IcbmTrajectory.requiredCoastTicks(burnout, separation);
                IcbmFlightPlan flight = plan(launch, burnout, separation, duration);
                double previousPitch = Math.PI / 2;
                for (double tick = 0.25; tick < flight.separationTick(); tick += 0.25) {
                    Vec3 velocity = IcbmTrajectory.velocity(flight, tick);
                    double pitch = Math.atan2(velocity.y, Math.hypot(velocity.x, velocity.z));
                    assertTrue(pitch <= previousPitch + 1.0E-7,
                        "S bend: pitch increased at tick " + tick + " range "
                            + distance + " launchY " + launchY);
                    assertTrue(velocity.length() <= IcbmConstants.MAXIMUM_CARRIER_SPEED_BLOCKS_PER_TICK + .001,
                        "speed limit at tick " + tick + " range " + distance);
                    previousPitch = pitch;
                }
            }
        }
    }

    private static IcbmFlightPlan plan(final Vec3 launch, final Vec3 burnout,
        final Vec3 separation, final int coastTicks) {
        return new IcbmFlightPlan(UUID.randomUUID(), UUID.randomUUID(), launch, burnout,
            separation, new Vec3(separation.x + 64.0, 64.0, separation.z), 0L,
            IcbmConstants.IGNITION_TICKS, IcbmConstants.BOOST_TICKS, coastTicks, 7L,
            WarheadPayloadType.CONVENTIONAL);
    }

    private static void assertVecEquals(final Vec3 expected, final Vec3 actual) {
        assertEquals(expected.x, actual.x, 1.0E-9);
        assertEquals(expected.y, actual.y, 1.0E-9);
        assertEquals(expected.z, actual.z, 1.0E-9);
    }
}
