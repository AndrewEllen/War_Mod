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
