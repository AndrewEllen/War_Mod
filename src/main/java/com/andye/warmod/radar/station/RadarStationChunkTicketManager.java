package com.andye.warmod.radar.station;

import com.andye.warmod.radar.RadarTrackKind;
import com.andye.warmod.radar.RadarTrackingService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Loads a 5x5 simulation-ticking station area only while a strategic missile
 * route needs that station, or while a player is remotely viewing it.
 */
public final class RadarStationChunkTicketManager {
    private static final int WINDOW_RADIUS = 2;
    private static final int SIMULATION_TICKET_RADIUS = 2;

    private static final Map<ServerLevel, Map<ChunkPos, Integer>> REFERENCES =
        new WeakHashMap<>();
    private static final Map<ServerLevel, Map<UUID, StationLease>> STATIONS =
        new WeakHashMap<>();

    private RadarStationChunkTicketManager() {
    }

    /** Compatibility registration: records the station without permanently loading it. */
    public static synchronized boolean register(
        final ServerLevel level,
        final UUID id,
        final BlockPos centre
    ) {
        Map<UUID, StationLease> stations = STATIONS.computeIfAbsent(
            level,
            ignored -> new LinkedHashMap<>()
        );
        StationLease existing = stations.get(id);
        if (existing != null && !existing.centre.equals(centre)) {
            releaseChunks(level, existing);
            stations.remove(id);
        }
        stations.computeIfAbsent(id, ignored -> new StationLease(centre.immutable()));
        return true;
    }

    public static synchronized void updateDynamic(
        final ServerLevel level,
        final Collection<RadarStationRecord> records,
        final List<RadarTrackingService.RadarTrackTelemetry> telemetry
    ) {
        Map<UUID, StationLease> stations = STATIONS.computeIfAbsent(
            level,
            ignored -> new LinkedHashMap<>()
        );
        Set<UUID> recordIds = new HashSet<>();
        Set<UUID> activeRoots = new HashSet<>();

        for (RadarTrackingService.RadarTrackTelemetry track : telemetry) {
            if (track.kind() != RadarTrackKind.INTERCEPTOR) {
                activeRoots.add(track.trackId());
            }
        }

        for (RadarStationRecord record : records) {
            recordIds.add(record.radarId());
            StationLease lease = stations.get(record.radarId());
            if (lease == null || !lease.centre.equals(record.centre())) {
                if (lease != null) releaseChunks(level, lease);
                lease = new StationLease(record.centre().immutable());
                stations.put(record.radarId(), lease);
            }

            lease.routeRoots.retainAll(activeRoots);
            if (!record.dynamicChunkLoading()) {
                lease.routeRoots.clear();
            } else {
                Vec3 station = Vec3.atCenterOf(record.centre());
                for (RadarTrackingService.RadarTrackTelemetry track : telemetry) {
                    if (track.kind() == RadarTrackKind.INTERCEPTOR
                        || lease.routeRoots.contains(track.trackId())) {
                        continue;
                    }
                    if (routeEntersDetectionArea(track, station)) {
                        /* Once armed, retain this root until the missile ceases
                         * to exist, even after it has flown past the station. */
                        lease.routeRoots.add(track.trackId());
                    }
                }
            }
            reconcile(level, lease);
        }

        for (UUID id : new ArrayList<>(stations.keySet())) {
            if (recordIds.contains(id)) continue;
            StationLease lease = stations.get(id);
            if (lease.viewerCount == 0) {
                releaseChunks(level, lease);
                stations.remove(id);
            } else {
                lease.routeRoots.clear();
                reconcile(level, lease);
            }
        }

        if (stations.isEmpty()) STATIONS.remove(level);
    }

    public static synchronized boolean acquireViewer(
        final ServerLevel level,
        final UUID id,
        final BlockPos centre
    ) {
        register(level, id, centre);
        StationLease lease = STATIONS.get(level).get(id);
        lease.viewerCount++;
        reconcile(level, lease);
        return true;
    }

    public static synchronized void releaseViewer(
        final ServerLevel level,
        final UUID id
    ) {
        Map<UUID, StationLease> stations = STATIONS.get(level);
        if (stations == null) return;
        StationLease lease = stations.get(id);
        if (lease == null) return;
        lease.viewerCount = Math.max(0, lease.viewerCount - 1);
        reconcile(level, lease);
    }

    public static synchronized void disableDynamic(
        final ServerLevel level,
        final UUID id
    ) {
        Map<UUID, StationLease> stations = STATIONS.get(level);
        if (stations == null) return;
        StationLease lease = stations.get(id);
        if (lease == null) return;
        lease.routeRoots.clear();
        reconcile(level, lease);
    }

    public static synchronized void unregister(
        final ServerLevel level,
        final UUID id
    ) {
        Map<UUID, StationLease> stations = STATIONS.get(level);
        if (stations == null) return;
        StationLease lease = stations.remove(id);
        if (lease != null) releaseChunks(level, lease);
        if (stations.isEmpty()) STATIONS.remove(level);
    }

    public static synchronized void clear() {
        for (Map.Entry<ServerLevel, Map<UUID, StationLease>> levelEntry
            : STATIONS.entrySet()) {
            for (StationLease lease : levelEntry.getValue().values()) {
                releaseChunks(levelEntry.getKey(), lease);
            }
        }
        STATIONS.clear();
        REFERENCES.clear();
    }

    private static boolean routeEntersDetectionArea(
        final RadarTrackingService.RadarTrackTelemetry telemetry,
        final Vec3 station
    ) {
        double radiusSquared = RadarStationConstants.DETECTION_RANGE_BLOCKS
            * RadarStationConstants.DETECTION_RANGE_BLOCKS;

        if (distanceToSegmentSquared(
            station,
            telemetry.currentPosition(),
            telemetry.predictedImpactPosition()
        ) <= radiusSquared) {
            return true;
        }

        var snapshot = telemetry.snapshot();
        if (snapshot.carrierPlan().isPresent()) {
            var carrier = snapshot.carrierPlan().get();
            if (distanceToSegmentSquared(
                station,
                telemetry.currentPosition(),
                carrier.separationPosition()
            ) <= radiusSquared
                || distanceToSegmentSquared(
                    station,
                    carrier.separationPosition(),
                    carrier.intendedTarget()
                ) <= radiusSquared) {
                return true;
            }
        }

        for (var terminal : snapshot.terminalPlans()) {
            if (distanceToSegmentSquared(
                station,
                telemetry.currentPosition(),
                terminal.targetPosition()
            ) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    private static double distanceToSegmentSquared(
        final Vec3 point,
        final Vec3 start,
        final Vec3 end
    ) {
        double dx = end.x - start.x;
        double dz = end.z - start.z;
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared <= 1.0E-9) {
            double px = point.x - start.x;
            double pz = point.z - start.z;
            return px * px + pz * pz;
        }
        double fraction = ((point.x - start.x) * dx + (point.z - start.z) * dz)
            / lengthSquared;
        fraction = Math.max(0.0, Math.min(1.0, fraction));
        double nearestX = start.x + dx * fraction;
        double nearestZ = start.z + dz * fraction;
        double px = point.x - nearestX;
        double pz = point.z - nearestZ;
        return px * px + pz * pz;
    }

    private static void reconcile(
        final ServerLevel level,
        final StationLease lease
    ) {
        boolean needed = lease.viewerCount > 0 || !lease.routeRoots.isEmpty();
        if (needed == !lease.chunks.isEmpty()) return;
        if (needed) {
            ChunkPos centre = new ChunkPos(
                lease.centre.getX() >> 4,
                lease.centre.getZ() >> 4
            );
            Set<ChunkPos> chunks = new HashSet<>();
            for (int x = centre.x() - WINDOW_RADIUS;
                x <= centre.x() + WINDOW_RADIUS;
                x++) {
                for (int z = centre.z() - WINDOW_RADIUS;
                    z <= centre.z() + WINDOW_RADIUS;
                    z++) {
                    chunks.add(new ChunkPos(x, z));
                }
            }
            lease.chunks = Set.copyOf(chunks);
            for (ChunkPos chunk : lease.chunks) acquire(level, chunk);
        } else {
            releaseChunks(level, lease);
        }
    }

    private static void acquire(final ServerLevel level, final ChunkPos chunk) {
        Map<ChunkPos, Integer> references = REFERENCES.computeIfAbsent(
            level,
            ignored -> new HashMap<>()
        );
        int count = references.getOrDefault(chunk, 0);
        if (count == 0) {
            level.getChunkSource().addTicketWithRadius(
                RadarStationChunkTicketType.STATION,
                chunk,
                SIMULATION_TICKET_RADIUS
            );
        }
        references.put(chunk, count + 1);
    }

    private static void releaseChunks(
        final ServerLevel level,
        final StationLease lease
    ) {
        for (ChunkPos chunk : lease.chunks) release(level, chunk);
        lease.chunks = Set.of();
    }

    private static void release(final ServerLevel level, final ChunkPos chunk) {
        Map<ChunkPos, Integer> references = REFERENCES.get(level);
        if (references == null) return;
        int count = references.getOrDefault(chunk, 0);
        if (count <= 1) {
            references.remove(chunk);
            level.getChunkSource().removeTicketWithRadius(
                RadarStationChunkTicketType.STATION,
                chunk,
                SIMULATION_TICKET_RADIUS
            );
        } else {
            references.put(chunk, count - 1);
        }
        if (references.isEmpty()) REFERENCES.remove(level);
    }

    private static final class StationLease {
        private final BlockPos centre;
        private final Set<UUID> routeRoots = new HashSet<>();
        private int viewerCount;
        private Set<ChunkPos> chunks = Set.of();

        private StationLease(final BlockPos centre) {
            this.centre = centre;
        }
    }
}
