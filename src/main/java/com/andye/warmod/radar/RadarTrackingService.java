package com.andye.warmod.radar;

import com.andye.warmod.WarMod;
import com.andye.warmod.antiair.AntiAirFallbackTrajectory;
import com.andye.warmod.antiair.AntiAirMissileVariant;
import com.andye.warmod.antiair.AntiAirRoute;
import com.andye.warmod.antiair.AntiAirTrajectory;
import com.andye.warmod.entity.IncomingWarheadEntity;
import com.andye.warmod.icbm.IcbmFlightControllerManager;
import com.andye.warmod.icbm.IcbmFlightPlan;
import com.andye.warmod.icbm.IcbmTrajectory;
import com.andye.warmod.radar.network.ClientboundRadarImpactPayload;
import com.andye.warmod.radar.network.ClientboundRadarInterceptionPayload;
import com.andye.warmod.radar.network.ClientboundRadarTrackRemovePayload;
import com.andye.warmod.radar.network.ClientboundRadarTrackUpsertPayload;
import com.andye.warmod.warhead.WarheadLaunchService;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadTrajectory;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class RadarTrackingService {
    public static final int MAX_STRATEGIC_TRACKS_PER_LEVEL = 128;
    public static final int MAX_INTERCEPTOR_TRACKS_PER_LEVEL = 2048;
    public static final int MAX_RECENT_IMPACTS_PER_LEVEL = 128;

    public static final long RECENT_IMPACT_RETENTION_TICKS = 1200;
    public static final long IMPACT_TRACK_RETENTION_TICKS = 200;

    private static final UUID SERVER_OWNER = new UUID(0, 0);
    private static final Map<ServerLevel, State> STATES = new WeakHashMap<>();

    private RadarTrackingService() {
    }

    public static synchronized void registerIcbm(
        final ServerLevel level,
        final IcbmFlightPlan plan
    ) {
        State state = state(level);

        if (state.tracks.containsKey(plan.missileId())) {
            return;
        }

        makeStrategicRoom(level, state);
        ServerPlayer owner = level.getServer()
            .getPlayerList()
            .getPlayer(plan.ownerPlayerId());
        RadarTrack track = new RadarTrack(
            plan.missileId(),
            RadarTrackKind.ICBM,
            plan.ownerPlayerId(),
            bounded(owner == null ? "SERVER" : owner.getGameProfile().name()),
            plan.payloadType(),
            null,
            plan.launchGameTime(),
            plan,
            null,
            RadarTrackPhase.IGNITION
        );
        state.tracks.put(track.trackId, track);
        upsert(level, track);
    }

    public static synchronized void updateIcbmPlan(
        final ServerLevel level,
        final IcbmFlightPlan plan
    ) {
        RadarTrack track = track(level, plan.missileId());

        if (track != null && track.kind == RadarTrackKind.ICBM) {
            track.carrierFlightPlan = plan;
            upsert(level, track);
        }
    }

    public static synchronized void registerDirectWarhead(
        final ServerLevel level,
        final ServerPlayer owner,
        final WarheadLaunchService.LaunchResult launch
    ) {
        State state = state(level);

        if (state.tracks.containsKey(launch.radarRootTrackId())) {
            return;
        }

        makeStrategicRoom(level, state);
        UUID ownerId = owner == null ? SERVER_OWNER : owner.getUUID();
        RadarTrack track = new RadarTrack(
            launch.radarRootTrackId(),
            RadarTrackKind.DIRECT_WARHEAD,
            ownerId,
            bounded(owner == null ? "SERVER" : owner.getGameProfile().name()),
            launch.payloadType(),
            null,
            launch.launchGameTime(),
            null,
            null,
            RadarTrackPhase.PAYLOAD_DELIVERY
        );
        attach(state, track, launch);
        state.tracks.put(track.trackId, track);
        upsert(level, track);
    }

    public static synchronized void registerTerminalSeparation(
        final ServerLevel level,
        final UUID rootTrackId,
        final WarheadLaunchService.LaunchResult launch
    ) {
        RadarTrack track = track(level, rootTrackId);

        if (track == null) {
            return;
        }

        attach(state(level), track, launch);
        track.phase = RadarTrackPhase.PAYLOAD_DELIVERY;
        track.lastStateChangeGameTime = level.getGameTime();
        upsert(level, track);
    }

    public static synchronized void reconcileWarhead(
        final ServerLevel level,
        final IncomingWarheadEntity entity
    ) {
        if (entity.warheadId() == null
            || track(level, entity.radarRootTrackId()) != null) {
            return;
        }

        registerDirectWarhead(
            level,
            entity.ownerPlayerId() == null
                ? null
                : level.getServer()
                    .getPlayerList()
                    .getPlayer(entity.ownerPlayerId()),
            new WarheadLaunchService.LaunchResult(
                entity.warheadId(),
                entity.startPosition(),
                entity.intendedTarget(),
                entity.launchGameTime(),
                entity.flightTicks(),
                entity.visualSeed(),
                entity.payloadType(),
                entity.radarRootTrackId()
            )
        );
    }

    public static synchronized void registerInterceptor(
        final ServerLevel level,
        final UUID interceptorId,
        final UUID ownerId,
        final String ownerName,
        final RadarInterceptorPlanSnapshot plan
    ) {
        State state = state(level);

        if (state.tracks.containsKey(interceptorId)
            || interceptorCount(state) >= MAX_INTERCEPTOR_TRACKS_PER_LEVEL) {
            return;
        }

        RadarTrack track = new RadarTrack(
            interceptorId,
            RadarTrackKind.INTERCEPTOR,
            ownerId == null ? SERVER_OWNER : ownerId,
            bounded(ownerName),
            null,
            plan.variant(),
            plan.launchGameTime(),
            null,
            plan,
            RadarTrackPhase.IGNITION
        );
        state.tracks.put(interceptorId, track);
        upsert(level, track);
    }

    public static synchronized void updateInterceptorRoute(
        final ServerLevel level,
        final UUID interceptorId,
        final RadarInterceptorRouteSnapshot route
    ) {
        RadarTrack track = track(level, interceptorId);

        if (track == null || track.interceptorPlan == null) {
            return;
        }

        RadarInterceptorPlanSnapshot plan = track.interceptorPlan;
        track.interceptorPlan = new RadarInterceptorPlanSnapshot(
            plan.variant(),
            plan.targetRootTrackId(),
            plan.launchPosition(),
            plan.burnoutPosition(),
            plan.noTargetHorizontalOffset(),
            plan.launchGameTime(),
            plan.ignitionTicks(),
            plan.boostTicks(),
            plan.guidanceTier(),
            plan.maximumMissDistance(),
            Optional.of(route),
            plan.fallback()
        );
        track.phase = RadarTrackPhase.INTERCEPT;
        upsert(level, track);
    }

    public static synchronized void updateInterceptorFallback(
        final ServerLevel level,
        final UUID interceptorId,
        final RadarInterceptorFallbackSnapshot fallback
    ) {
        RadarTrack track = track(level, interceptorId);

        if (track == null || track.interceptorPlan == null) {
            return;
        }

        RadarInterceptorPlanSnapshot plan = track.interceptorPlan;
        track.interceptorPlan = new RadarInterceptorPlanSnapshot(
            plan.variant(),
            plan.targetRootTrackId(),
            plan.launchPosition(),
            plan.burnoutPosition(),
            plan.noTargetHorizontalOffset(),
            plan.launchGameTime(),
            plan.ignitionTicks(),
            plan.boostTicks(),
            plan.guidanceTier(),
            plan.maximumMissDistance(),
            plan.route(),
            Optional.of(fallback)
        );
        upsert(level, track);
    }

    public static synchronized void updateInterceptorPhase(
        final ServerLevel level,
        final UUID interceptorId,
        final RadarTrackPhase phase
    ) {
        RadarTrack track = track(level, interceptorId);

        if (track != null
            && track.kind == RadarTrackKind.INTERCEPTOR
            && track.phase != phase) {
            track.phase = phase;
            track.lastStateChangeGameTime = level.getGameTime();
            upsert(level, track);
        }
    }

    public static void removeInterceptor(
        final ServerLevel level,
        final UUID interceptorId,
        final RadarRemovalReason reason
    ) {
        removeTrack(level, interceptorId, reason);
    }

    public static void registerInterception(
        final ServerLevel level,
        final RadarInterceptionSnapshot interception
    ) {
        RadarSubscriptionManager.broadcast(
            level,
            new ClientboundRadarInterceptionPayload(
                interception.interceptorId(),
                interception.targetRootTrackId(),
                interception.position(),
                interception.gameTime(),
                interception.variant(),
                interception.interceptedPayload()
            )
        );
    }

    public static synchronized void registerImpact(
        final ServerLevel level,
        final UUID warheadId,
        final UUID rootTrackId,
        final Vec3 position,
        final WarheadPayloadType payloadType,
        final float visualScale
    ) {
        State state = state(level);
        RadarTrack track = state.tracks.get(rootTrackId);
        long now = level.getGameTime();
        RadarImpactSnapshot impact = new RadarImpactSnapshot(
            rootTrackId,
            warheadId,
            position,
            now,
            payloadType,
            visualScale
        );

        if (track != null) {
            track.terminalPlans.remove(warheadId);
            state.terminalToRoot.remove(warheadId);

            if (track.terminalPlans.isEmpty()) {
                track.phase = RadarTrackPhase.IMPACT;
                track.impactPosition = position;
                track.impactGameTime = now;
                track.impactVisualScale = visualScale;
            } else {
                selectPrimaryTerminal(track);
            }

            upsert(level, track);
        }

        state.impacts.addLast(impact);

        while (state.impacts.size() > MAX_RECENT_IMPACTS_PER_LEVEL) {
            state.impacts.removeFirst();
        }

        RadarSubscriptionManager.broadcast(
            level,
            new ClientboundRadarImpactPayload(impact)
        );
    }

    /**
     * Removes one live terminal child from its strategic radar track.
     *
     * <p>Point-defence and interceptor kills previously discarded the entity
     * without updating the radar service. The root track therefore remained
     * active forever, keeping its route, target marker and redstone warning on
     * every radar display. Cluster roots stay alive while at least one child is
     * still flying; the root is removed as soon as its final child is gone.</p>
     */
    public static synchronized void removeTerminalWarhead(
        final ServerLevel level,
        final UUID rootTrackId,
        final UUID warheadId,
        final RadarRemovalReason reason
    ) {
        if (rootTrackId == null || warheadId == null) {
            return;
        }

        State state = STATES.get(level);

        if (state == null) {
            return;
        }

        RadarTrack track = state.tracks.get(rootTrackId);

        if (track == null
            || track.terminalPlans.remove(warheadId) == null) {
            state.terminalToRoot.remove(warheadId);
            return;
        }

        state.terminalToRoot.remove(warheadId);

        if (track.terminalPlans.isEmpty()) {
            removeTrack(level, rootTrackId, reason);
            return;
        }

        selectPrimaryTerminal(track);
        track.phase = RadarTrackPhase.PAYLOAD_DELIVERY;
        track.lastStateChangeGameTime = level.getGameTime();
        upsert(level, track);
    }

    public static synchronized void removeTrack(
        final ServerLevel level,
        final UUID trackId,
        final RadarRemovalReason reason
    ) {
        State state = STATES.get(level);

        if (state == null) {
            return;
        }

        RadarTrack track = state.tracks.remove(trackId);

        if (track == null) {
            return;
        }

        for (UUID child : track.terminalPlans.keySet()) {
            state.terminalToRoot.remove(child);
        }

        RadarSubscriptionManager.broadcast(
            level,
            new ClientboundRadarTrackRemovePayload(trackId, reason)
        );
    }

    public static synchronized List<RadarTrackSnapshot> snapshotTracks(
        final ServerLevel level
    ) {
        State state = STATES.get(level);

        return state == null
            ? List.of()
            : state.tracks.values()
                .stream()
                .map(RadarTrack::snapshot)
                .toList();
    }

    public static synchronized List<RadarTrackTelemetry> currentTelemetry(
        final ServerLevel level,
        final long time
    ) {
        State state = STATES.get(level);

        if (state == null) {
            return List.of();
        }

        List<RadarTrackTelemetry> output = new ArrayList<>();

        for (RadarTrack track : state.tracks.values()) {
            if (track.phase == RadarTrackPhase.IMPACT) {
                continue;
            }

            Vec3 position;
            Vec3 velocity;
            Vec3 target;

            if (track.kind == RadarTrackKind.INTERCEPTOR
                && track.interceptorPlan != null) {
                RadarInterceptorPlanSnapshot plan = track.interceptorPlan;
                long boostEnd = plan.launchGameTime()
                    + plan.ignitionTicks()
                    + plan.boostTicks();

                if (time < boostEnd) {
                    position = AntiAirTrajectory.boostPosition(
                        plan.launchPosition(),
                        plan.burnoutPosition(),
                        plan.noTargetHorizontalOffset(),
                        plan.ignitionTicks(),
                        plan.boostTicks(),
                        time - plan.launchGameTime()
                    );
                    velocity = AntiAirTrajectory.boostVelocity(
                        plan.launchPosition(),
                        plan.burnoutPosition(),
                        plan.noTargetHorizontalOffset(),
                        plan.ignitionTicks(),
                        plan.boostTicks(),
                        time - plan.launchGameTime()
                    );
                    target = plan.route()
                        .map(RadarInterceptorRouteSnapshot::resolvedInterceptPosition)
                        .orElse(plan.burnoutPosition());
                } else if (plan.fallback().isPresent()
                    && time >= plan.fallback().get().transitionGameTime()) {
                    RadarInterceptorFallbackSnapshot fallback =
                        plan.fallback().get();
                    position = AntiAirFallbackTrajectory.positionAt(
                        fallback.transitionPosition(),
                        fallback.transitionVelocity(),
                        time - fallback.transitionGameTime()
                    );
                    velocity = AntiAirFallbackTrajectory.velocityAt(
                        fallback.transitionVelocity(),
                        time - fallback.transitionGameTime()
                    );
                    target = position;
                } else if (plan.route().isPresent()) {
                    RadarInterceptorRouteSnapshot routeSnapshot =
                        plan.route().get();
                    AntiAirRoute route = new AntiAirRoute(
                        plan.burnoutPosition(),
                        routeSnapshot.controlPoint1(),
                        routeSnapshot.controlPoint2(),
                        routeSnapshot.resolvedInterceptPosition(),
                        routeSnapshot.interceptTicks()
                    );
                    position = route.position(
                        time - routeSnapshot.routeLockGameTime()
                    );
                    velocity = route.velocity(
                        time - routeSnapshot.routeLockGameTime()
                    );
                    target = routeSnapshot.resolvedInterceptPosition();
                } else {
                    position = plan.burnoutPosition();
                    velocity = Vec3.ZERO;
                    target = position;
                }
            } else if (track.terminalWarheadId != null) {
                double elapsed = Math.max(
                    0,
                    time - track.terminalLaunchGameTime
                );
                position = WarheadTrajectory.position(
                    track.terminalStartPosition,
                    track.terminalTargetPosition,
                    elapsed,
                    track.terminalFlightTicks
                );
                velocity = WarheadTrajectory.velocity(
                    track.terminalStartPosition,
                    track.terminalTargetPosition,
                    elapsed,
                    track.terminalFlightTicks
                );
                target = track.terminalTargetPosition;
            } else if (track.carrierFlightPlan != null) {
                double elapsed = Math.max(
                    0,
                    time - track.carrierFlightPlan.launchGameTime()
                );
                position = IcbmTrajectory.position(
                    track.carrierFlightPlan,
                    elapsed
                );
                velocity = IcbmTrajectory.velocity(
                    track.carrierFlightPlan,
                    elapsed
                );
                target = track.carrierFlightPlan.intendedTarget();
            } else {
                continue;
            }

            output.add(new RadarTrackTelemetry(
                track.trackId,
                track.kind,
                Optional.ofNullable(track.payloadType),
                Optional.ofNullable(track.interceptorVariant),
                track.phase,
                position,
                velocity,
                target,
                time,
                track.snapshot()
            ));
        }

        return List.copyOf(output);
    }

    public static synchronized List<RadarImpactSnapshot> snapshotImpacts(
        final ServerLevel level
    ) {
        State state = STATES.get(level);
        return state == null ? List.of() : List.copyOf(state.impacts);
    }

    public static void reconcileIcbmFlights(final ServerLevel level) {
        for (IcbmFlightPlan plan : IcbmFlightControllerManager.snapshot(level)) {
            registerIcbm(level, plan);
        }
    }

    public static synchronized void tick(final ServerLevel level) {
        State state = STATES.get(level);

        if (state == null) {
            return;
        }

        long now = level.getGameTime();

        for (RadarTrack track : new ArrayList<>(state.tracks.values())) {
            if (track.phase == RadarTrackPhase.IMPACT) {
                if (now - track.impactGameTime > IMPACT_TRACK_RETENTION_TICKS) {
                    removeTrack(
                        level,
                        track.trackId,
                        RadarRemovalReason.EXPIRED
                    );
                }
            } else if (track.carrierFlightPlan != null
                && track.terminalWarheadId == null) {
                long elapsed = Math.max(
                    0,
                    now - track.carrierFlightPlan.launchGameTime()
                );
                RadarTrackPhase phase =
                    elapsed < track.carrierFlightPlan.ignitionTicks()
                        ? RadarTrackPhase.IGNITION
                        : elapsed < track.carrierFlightPlan.ignitionTicks()
                            + track.carrierFlightPlan.boostTicks()
                            ? RadarTrackPhase.BOOST
                            : RadarTrackPhase.MIDCOURSE;

                if (phase != track.phase) {
                    track.phase = phase;
                    upsert(level, track);
                }
            }
        }

        state.impacts.removeIf(impact ->
            now - impact.impactGameTime() > RECENT_IMPACT_RETENTION_TICKS
        );
    }

    public static synchronized void clear(final ServerLevel level) {
        STATES.remove(level);
    }

    public static synchronized void clearAll() {
        STATES.clear();
    }

    private static RadarTrack track(
        final ServerLevel level,
        final UUID trackId
    ) {
        State state = STATES.get(level);
        return state == null ? null : state.tracks.get(trackId);
    }

    private static State state(final ServerLevel level) {
        return STATES.computeIfAbsent(level, ignored -> new State());
    }

    private static void attach(
        final State state,
        final RadarTrack track,
        final WarheadLaunchService.LaunchResult launch
    ) {
        track.terminalWarheadId = launch.warheadId();
        track.terminalStartPosition = launch.startPosition();
        track.terminalTargetPosition = launch.intendedTarget();
        track.terminalLaunchGameTime = launch.launchGameTime();
        track.terminalFlightTicks = launch.flightTicks();
        track.terminalVisualSeed = launch.visualSeed();
        track.terminalPlans.put(
            launch.warheadId(),
            new RadarTerminalPlanSnapshot(
                launch.warheadId(),
                launch.clusterIndex(),
                launch.clusterCount(),
                launch.startPosition(),
                launch.intendedTarget(),
                launch.launchGameTime(),
                launch.flightTicks(),
                launch.visualSeed(),
                launch.payloadType()
            )
        );
        state.terminalToRoot.put(launch.warheadId(), track.trackId);
    }

    private static void selectPrimaryTerminal(final RadarTrack track) {
        RadarTerminalPlanSnapshot primary =
            track.terminalPlans.values().iterator().next();
        track.terminalWarheadId = primary.warheadId();
        track.terminalStartPosition = primary.startPosition();
        track.terminalTargetPosition = primary.targetPosition();
        track.terminalLaunchGameTime = primary.launchGameTime();
        track.terminalFlightTicks = primary.flightTicks();
        track.terminalVisualSeed = primary.visualSeed();
    }

    private static void makeStrategicRoom(
        final ServerLevel level,
        final State state
    ) {
        while (strategicCount(state) >= MAX_STRATEGIC_TRACKS_PER_LEVEL) {
            RadarTrack victim = state.tracks.values()
                .stream()
                .filter(track -> track.kind != RadarTrackKind.INTERCEPTOR)
                .findFirst()
                .orElse(null);

            if (victim == null) {
                return;
            }

            removeTrack(level, victim.trackId, RadarRemovalReason.EVICTED);
        }
    }

    private static long strategicCount(final State state) {
        return state.tracks.values()
            .stream()
            .filter(track -> track.kind != RadarTrackKind.INTERCEPTOR)
            .count();
    }

    private static long interceptorCount(final State state) {
        return state.tracks.values()
            .stream()
            .filter(track -> track.kind == RadarTrackKind.INTERCEPTOR)
            .count();
    }

    private static void upsert(
        final ServerLevel level,
        final RadarTrack track
    ) {
        RadarSubscriptionManager.broadcast(
            level,
            new ClientboundRadarTrackUpsertPayload(
                level.getGameTime(),
                track.snapshot()
            )
        );
    }

    private static String bounded(final String value) {
        return value == null || value.isBlank()
            ? "SERVER"
            : value.substring(0, Math.min(64, value.length()));
    }

    public record RadarTrackTelemetry(
        UUID trackId,
        RadarTrackKind kind,
        Optional<WarheadPayloadType> strategicPayloadType,
        Optional<AntiAirMissileVariant> interceptorVariant,
        RadarTrackPhase phase,
        Vec3 currentPosition,
        Vec3 currentVelocity,
        Vec3 predictedImpactPosition,
        long gameTime,
        RadarTrackSnapshot snapshot
    ) {
    }

    private static final class State {
        private final LinkedHashMap<UUID, RadarTrack> tracks =
            new LinkedHashMap<>();
        private final Map<UUID, UUID> terminalToRoot = new HashMap<>();
        private final ArrayDeque<RadarImpactSnapshot> impacts =
            new ArrayDeque<>();
    }
}
