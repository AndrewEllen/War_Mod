package com.andye.warmod.icbm.client;

import com.andye.warmod.icbm.IcbmFlightPlan;
import com.andye.warmod.icbm.IcbmTrajectory;
import com.andye.warmod.icbm.client.render.IcbmVisualGeometry;
import com.andye.warmod.icbm.network.ClientboundIcbmGuidanceUpdatePayload;
import com.andye.warmod.icbm.network.ClientboundIcbmLaunchPayload;
import com.andye.warmod.warhead.WarheadDeliveryMode;
import com.andye.warmod.warhead.WarheadYield;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public record IcbmVisualState(IcbmFlightPlan flightPlan, WarheadYield yield,
    WarheadDeliveryMode deliveryMode) {
    public static IcbmVisualState fromPayload(final ClientboundIcbmLaunchPayload payload) {
        IcbmFlightPlan plan = new IcbmFlightPlan(payload.missileId(), new UUID(0, 0),
            payload.launchPosition(), payload.burnoutPosition(), payload.separationPosition(),
            payload.intendedTarget(), payload.launchGameTime(), payload.ignitionTicks(),
            payload.boostTicks(), payload.coastTicks(), payload.visualSeed(), payload.payloadType());
        return new IcbmVisualState(plan, payload.yield(), payload.deliveryMode());
    }

    public IcbmVisualState withGuidance(final ClientboundIcbmGuidanceUpdatePayload payload) {
        IcbmFlightPlan old = flightPlan;
        IcbmFlightPlan revised = new IcbmFlightPlan(old.missileId(), old.ownerPlayerId(),
            old.launchPosition(), old.burnoutPosition(), payload.separationPosition(),
            payload.resolvedTarget(), old.launchGameTime(), old.ignitionTicks(), old.boostTicks(),
            payload.revisedCoastTicks(), old.visualSeed(), old.payloadType());
        return new IcbmVisualState(revised, yield, deliveryMode);
    }

    public double elapsed(final long time, final double partial) {
        return Math.max(0, time - flightPlan.launchGameTime())
            + Math.max(0, Math.min(1, partial));
    }
    public Vec3 position(final long time, final double partial) {
        return IcbmTrajectory.position(flightPlan, elapsed(time, partial));
    }
    public Vec3 velocity(final long time, final double partial) {
        return IcbmTrajectory.velocity(flightPlan, elapsed(time, partial));
    }
    public boolean expired(final long time) {
        return elapsed(time, 0) > flightPlan.separationTick() + 40;
    }

    public List<IcbmTrailSample> trail(final long time, final double partial) {
        double now = elapsed(time, partial);
        double end = Math.min(now, flightPlan.ignitionTicks() + flightPlan.boostTicks());
        List<IcbmTrailSample> samples = new ArrayList<>();
        for (double sampleTime = Math.max(0, end - 140); sampleTime <= end; sampleTime += 1.5) {
            double age = now - sampleTime;
            if (age > 140) continue;
            SplittableRandom random = new SplittableRandom(flightPlan.visualSeed()
                ^ (long) (sampleTime * 31));
            Vec3 center = IcbmTrajectory.position(flightPlan, sampleTime);
            Vec3 velocity = IcbmTrajectory.velocity(flightPlan, sampleTime);
            Vec3 axis = velocity.lengthSqr() < 1.0E-8
                ? new Vec3(0.0, 1.0, 0.0) : velocity.normalize();
            Vec3 nozzle = center.subtract(
                axis.scale(IcbmVisualGeometry.TOTAL_VISUAL_HEIGHT * 0.5));
            samples.add(new IcbmTrailSample(nozzle, age,
                (float) random.nextDouble(.45, 1.25),
                new Vec3(random.nextDouble(-.012, .012), random.nextDouble(.006, .02),
                    random.nextDouble(-.012, .012)),
                (float) random.nextDouble(0, Math.PI * 2)));
        }
        return List.copyOf(samples);
    }
}
