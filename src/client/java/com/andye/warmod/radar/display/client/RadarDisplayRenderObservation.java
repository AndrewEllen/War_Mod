package com.andye.warmod.radar.display.client;

import com.andye.warmod.radar.RadarTrackKind;
import com.andye.warmod.radar.client.ClientRadarTrack;
import com.andye.warmod.radar.station.RadarStationObservation;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadTrajectory;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public record RadarDisplayRenderObservation(
    List<RadarDisplayRouteSection> routes,
    Vec3 launchPosition,
    Vec3 observedPosition,
    Vec3 predictedImpactPosition,
    float alpha,
    int rgb
) {
    public RadarDisplayRenderObservation {
        routes = List.copyOf(routes);
    }

    public static RadarDisplayRenderObservation from(
        final RadarStationObservation observation,
        final double serverNow,
        final int sweepPeriod
    ) {
        ClientRadarTrack track = new ClientRadarTrack(
            observation.trackSnapshot()
        );

        double age = Math.max(
            0.0,
            serverNow - observation.observationGameTime()
        );
        double alpha;

        if (age >= sweepPeriod * 2.0) {
            alpha = 0.0;
        } else {
            double unit = Math.min(1.0, age / sweepPeriod);
            alpha = unit < 0.5
                ? 1.0 - unit * 1.1
                : 0.45 - (unit - 0.5) * 0.65;
        }

        /*
         * Search-radar contacts are sample-and-hold. The sweep animates every
         * frame, but route completion and the contact marker remain frozen at
         * the observation time until the beam crosses the target again.
         */
        double renderTime = observation.observedRouteTime();
        List<RadarDisplayRouteSection> routes = new ArrayList<>();

        if (!track.carrierRoute().isEmpty()) {
            double duration = track.carrierDuration();
            double current = observation.trackSnapshot()
                    .terminalPlans()
                    .isEmpty()
                ? Mth.clamp(
                    track.carrierElapsed(renderTime),
                    0.0,
                    duration
                )
                : duration;

            routes.add(new RadarDisplayRouteSection(
                track.carrierRoute(),
                completedSegments(
                    track.carrierRoute().size(),
                    current,
                    duration
                )
            ));
        }

        for (var terminal : observation.trackSnapshot().terminalPlans()) {
            double duration = terminal.flightTicks();
            double current = Mth.clamp(
                renderTime - terminal.launchGameTime(),
                0.0,
                duration
            );
            List<Vec3> points = new ArrayList<>(49);

            for (int index = 0; index <= 48; index++) {
                double elapsed = duration * index / 48.0;
                points.add(WarheadTrajectory.position(
                    terminal.startPosition(),
                    terminal.targetPosition(),
                    elapsed,
                    terminal.flightTicks()
                ));
            }

            routes.add(new RadarDisplayRouteSection(
                points,
                completedSegments(points.size(), current, duration)
            ));
        }

        int rgb;

        if (observation.threatensWarningZone()) {
            rgb = 0xFF3F32;
        } else if (observation.trackSnapshot().kind()
            == RadarTrackKind.INTERCEPTOR) {
            rgb = 0x50E7FF;
        } else if (observation.trackSnapshot()
            .strategicPayloadType()
            .orElse(null) == WarheadPayloadType.NUCLEAR) {
            rgb = 0xFF663D;
        } else {
            rgb = 0xFFB43B;
        }

        return new RadarDisplayRenderObservation(
            routes,
            track.launch(),
            observation.observedPosition(),
            observation.predictedImpactPosition(),
            (float)Mth.clamp(alpha, 0.0, 1.0),
            rgb
        );
    }

    private static int completedSegments(
        final int pointCount,
        final double current,
        final double duration
    ) {
        if (pointCount < 2 || duration <= 0.0) {
            return 0;
        }

        double fraction = Mth.clamp(
            current / duration,
            0.0,
            1.0
        );

        return (int)Math.floor(fraction * (pointCount - 1));
    }
}
