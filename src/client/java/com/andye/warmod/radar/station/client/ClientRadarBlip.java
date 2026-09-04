package com.andye.warmod.radar.station.client;

import com.andye.warmod.radar.station.RadarStationObservation;
import com.andye.warmod.radar.RadarTerminalPlanSnapshot;
import com.andye.warmod.radar.client.ClientRadarTrack;
import com.andye.warmod.warhead.WarheadTrajectory;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Immutable render data captured when the rotating beam observes a target.
 * Route sampling is done once per sweep rather than once per rendered frame.
 */
public final class ClientRadarBlip {
    private static final int TERMINAL_ROUTE_SAMPLES = 24;

    private final RadarStationObservation observation;
    private final long lastRefreshTime;
    private final ClientRadarTrack track;
    private final double carrierProgress;
    private final List<TerminalRender> terminals;

    public ClientRadarBlip(
        final RadarStationObservation observation,
        final long lastRefreshTime
    ) {
        this.observation = observation;
        this.lastRefreshTime = lastRefreshTime;
        track = new ClientRadarTrack(observation.trackSnapshot());

        carrierProgress = observation.trackSnapshot().terminalPlans().isEmpty()
            ? Mth.clamp(
                track.carrierElapsed(observation.observedRouteTime()),
                0.0,
                track.carrierDuration()
            )
            : track.carrierDuration();

        List<TerminalRender> rendered = new ArrayList<>();
        for (RadarTerminalPlanSnapshot terminal
            : observation.trackSnapshot().terminalPlans()) {
            double current = Mth.clamp(
                observation.observedRouteTime() - terminal.launchGameTime(),
                0.0,
                terminal.flightTicks()
            );
            List<Vec3> route = new ArrayList<>(TERMINAL_ROUTE_SAMPLES + 1);
            for (int index = 0; index <= TERMINAL_ROUTE_SAMPLES; index++) {
                double elapsed = terminal.flightTicks()
                    * index
                    / (double)TERMINAL_ROUTE_SAMPLES;
                route.add(WarheadTrajectory.position(
                    terminal.startPosition(),
                    terminal.targetPosition(),
                    elapsed,
                    terminal.flightTicks(),
                    terminal.clusterIndex(),
                    terminal.clusterCount()
                ));
            }
            rendered.add(new TerminalRender(
                terminal,
                List.copyOf(route),
                current,
                WarheadTrajectory.position(
                    terminal.startPosition(),
                    terminal.targetPosition(),
                    current,
                    terminal.flightTicks(),
                    terminal.clusterIndex(),
                    terminal.clusterCount()
                )
            ));
        }
        terminals = List.copyOf(rendered);
    }

    public RadarStationObservation observation() {
        return observation;
    }

    public ClientRadarTrack track() {
        return track;
    }

    public double carrierProgress() {
        return carrierProgress;
    }

    public List<TerminalRender> terminals() {
        return terminals;
    }

    public double alpha(final double now, final int period) {
        double age = Math.max(0.0, now - lastRefreshTime);
        if (age >= period * 2.0) return 0.0;
        double unit = Math.min(1.0, age / period);
        return unit < 0.5
            ? 1.0 - unit * 1.1
            : 0.45 - (unit - 0.5) * 0.65;
    }

    public record TerminalRender(
        RadarTerminalPlanSnapshot plan,
        List<Vec3> route,
        double progress,
        Vec3 observedPosition
    ) {
    }
}
