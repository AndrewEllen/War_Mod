package com.andye.warmod.fire.network;

import com.andye.warmod.WarMod;
import com.andye.warmod.diagnostics.WarModPerformanceDiagnostics;
import com.andye.warmod.fire.FireCellSnapshot;
import com.andye.warmod.fire.FireEmberSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Converts immutable fire snapshots into per-player payloads away from the server tick.
 * The only main-thread work is capturing player positions and sending completed packets.
 */
final class AsyncFireSnapshotDispatcher {
    private static final double VISUAL_RANGE = 320.0;
    private static final double SMOKE_CLUSTER_RANGE = 1_536.0;
    private static final int SMOKE_CLUSTER_CELL_SIZE = 32;
    private static final int MIN_SMOKE_CLUSTER_HOSTS = 8;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "war-mod-fire-visual-preparation");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<ServerLevel, LevelQueue> LEVELS = new WeakHashMap<>();

    private AsyncFireSnapshotDispatcher() { }

    static void queue(final ServerLevel level, final List<FireCellSnapshot> snapshots,
        final List<FireEmberSnapshot> embers, final boolean authoritative) {
        if (level == null) return;
        List<Viewer> viewers = new ArrayList<>();
        for (ServerPlayer player : PlayerLookup.level(level))
            viewers.add(new Viewer(player.getUUID(), player.position()));
        if (viewers.isEmpty()) return;
        SnapshotInput input = new SnapshotInput(level.getGameTime(), List.copyOf(snapshots),
            List.copyOf(embers), List.copyOf(viewers), authoritative);
        synchronized (AsyncFireSnapshotDispatcher.class) {
            LevelQueue queue = LEVELS.computeIfAbsent(level, ignored -> new LevelQueue());
            /* Never let a frequent ember-only refresh displace an authoritative patch
             * snapshot that is already waiting behind the active preparation job. */
            boolean emberOnly = !input.authoritative() && input.snapshots().isEmpty();
            boolean authoritativePending = queue.latest != null
                && (queue.latest.authoritative() || !queue.latest.snapshots().isEmpty());
            if (!emberOnly || !authoritativePending) queue.latest = input;
            if (!queue.running) start(level, queue);
        }
    }

    private static void start(final ServerLevel level, final LevelQueue queue) {
        SnapshotInput input = queue.latest;
        queue.latest = null;
        queue.running = true;
        CompletableFuture.supplyAsync(() -> prepare(input), EXECUTOR)
            .whenComplete((batch, failure) -> level.getServer().execute(
                () -> complete(level, batch, failure)));
    }

    private static void complete(final ServerLevel level, final PreparedBatch batch,
        final Throwable failure) {
        if (failure != null) {
            WarMod.LOGGER.warn("Asynchronous fire visual preparation failed", failure);
        } else if (batch != null) {
            long started = WarModPerformanceDiagnostics.begin();
            int sent = 0;
            for (PreparedViewer prepared : batch.viewers()) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(prepared.playerId());
                if (player == null || player.level() != level) continue;
                ServerPlayNetworking.send(player, prepared.payload());
                sent++;
            }
            WarModPerformanceDiagnostics.add(
                WarModPerformanceDiagnostics.Gauge.FIRE_NETWORK_PACKETS, sent);
            WarModPerformanceDiagnostics.record(
                WarModPerformanceDiagnostics.Subsystem.FIRE_NETWORK, started);
        }
        synchronized (AsyncFireSnapshotDispatcher.class) {
            LevelQueue queue = LEVELS.get(level);
            if (queue == null) return;
            if (queue.latest != null) start(level, queue);
            else {
                queue.running = false;
                LEVELS.remove(level);
            }
        }
    }

    private static PreparedBatch prepare(final SnapshotInput input) {
        double rangeSquared = VISUAL_RANGE * VISUAL_RANGE;
        double clusterRangeSquared = SMOKE_CLUSTER_RANGE * SMOKE_CLUSTER_RANGE;
        int chunkRadius = (int) Math.ceil(VISUAL_RANGE / 16.0);
        Map<Long, List<FireCellSnapshot>> buckets = new HashMap<>();
        Map<Long, SmokeClusterAccumulator> clusterBuckets = new HashMap<>();
        for (FireCellSnapshot snapshot : input.snapshots()) {
            int chunkX = snapshot.anchor().host().getX() >> 4;
            int chunkZ = snapshot.anchor().host().getZ() >> 4;
            buckets.computeIfAbsent(key(chunkX, chunkZ), ignored -> new ArrayList<>())
                .add(snapshot);
            if (snapshot.smoke() >= 0.018F) {
                int cellX = Math.floorDiv(snapshot.anchor().host().getX(), SMOKE_CLUSTER_CELL_SIZE);
                int cellZ = Math.floorDiv(snapshot.anchor().host().getZ(), SMOKE_CLUSTER_CELL_SIZE);
                clusterBuckets.computeIfAbsent(key(cellX, cellZ),
                    ignored -> new SmokeClusterAccumulator(cellX, cellZ)).add(snapshot);
            }
        }
        List<SmokeCluster> clusters = clusterBuckets.values().stream()
            .filter(SmokeClusterAccumulator::isWildfire)
            .map(SmokeClusterAccumulator::finish).toList();
        List<PreparedViewer> prepared = new ArrayList<>(input.viewers().size());
        for (Viewer viewer : input.viewers()) {
            PriorityQueue<RankedSnapshot> nearest = new PriorityQueue<>(
                ClientboundFireStatePayload.MAX_ENTRIES + 1,
                Comparator.comparingDouble(RankedSnapshot::distanceSquared).reversed());
            int playerChunkX = (int) Math.floor(viewer.position().x) >> 4;
            int playerChunkZ = (int) Math.floor(viewer.position().z) >> 4;
            int candidateCount = 0;
            for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                    List<FireCellSnapshot> candidates = buckets.get(
                        key(playerChunkX + dx, playerChunkZ + dz));
                    if (candidates == null) continue;
                    for (FireCellSnapshot snapshot : candidates) {
                        double distanceSquared = viewer.position().distanceToSqr(
                            snapshot.anchor().position());
                        if (distanceSquared > rangeSquared) continue;
                        candidateCount++;
                        nearest.add(new RankedSnapshot(snapshot, distanceSquared));
                        if (nearest.size() > ClientboundFireStatePayload.MAX_ENTRIES) nearest.poll();
                    }
                }
            }
            List<ClientboundFireStatePayload.Entry> entries = nearest.stream()
                .sorted(Comparator.comparingDouble(RankedSnapshot::distanceSquared))
                .map(ranked -> entry(ranked.snapshot())).toList();
            PriorityQueue<RankedEmber> nearestEmbers = new PriorityQueue<>(
                ClientboundFireStatePayload.MAX_EMBERS + 1,
                Comparator.comparingDouble(RankedEmber::distanceSquared).reversed());
            int emberCount = 0;
            for (FireEmberSnapshot ember : input.embers()) {
                double distanceSquared = viewer.position().distanceToSqr(ember.position());
                if (distanceSquared > rangeSquared) continue;
                emberCount++;
                nearestEmbers.add(new RankedEmber(ember, distanceSquared));
                if (nearestEmbers.size() > ClientboundFireStatePayload.MAX_EMBERS)
                    nearestEmbers.poll();
            }
            List<ClientboundFireStatePayload.EmberEntry> emberEntries = nearestEmbers.stream()
                .sorted(Comparator.comparingDouble(RankedEmber::distanceSquared))
                .map(ranked -> emberEntry(ranked.snapshot())).toList();
            PriorityQueue<RankedSmokeCluster> nearestClusters = new PriorityQueue<>(
                ClientboundFireStatePayload.MAX_SMOKE_CLUSTERS + 1,
                Comparator.comparingDouble(RankedSmokeCluster::distanceSquared).reversed());
            int clusterCount = 0;
            for (SmokeCluster cluster : clusters) {
                double distanceSquared = viewer.position().distanceToSqr(cluster.position());
                if (distanceSquared > clusterRangeSquared) continue;
                clusterCount++;
                nearestClusters.add(new RankedSmokeCluster(cluster, distanceSquared));
                if (nearestClusters.size() > ClientboundFireStatePayload.MAX_SMOKE_CLUSTERS)
                    nearestClusters.poll();
            }
            List<ClientboundFireStatePayload.SmokeClusterEntry> clusterEntries =
                nearestClusters.stream()
                    .sorted(Comparator.comparingDouble(RankedSmokeCluster::distanceSquared))
                    .map(ranked -> clusterEntry(ranked.cluster())).toList();
            ClientboundFireStatePayload payload = new ClientboundFireStatePayload(
                input.gameTime(), input.authoritative()
                    && candidateCount <= ClientboundFireStatePayload.MAX_ENTRIES,
                entries, emberCount <= ClientboundFireStatePayload.MAX_EMBERS, emberEntries,
                input.authoritative()
                    && clusterCount <= ClientboundFireStatePayload.MAX_SMOKE_CLUSTERS,
                clusterEntries);
            prepared.add(new PreparedViewer(viewer.playerId(), payload));
        }
        return new PreparedBatch(List.copyOf(prepared));
    }

    private static ClientboundFireStatePayload.Entry entry(final FireCellSnapshot snapshot) {
        return new ClientboundFireStatePayload.Entry(snapshot.id(),
            snapshot.anchor().host().asLong(), (byte) snapshot.anchor().face().ordinal(),
            snapshot.anchor().localX(), snapshot.anchor().localY(), snapshot.anchor().localZ(),
            snapshot.intensity(), snapshot.heat(), snapshot.coverage(), snapshot.smoke(),
            snapshot.phase(), snapshot.seed(), snapshot.ignitionGameTime(),
            (float) snapshot.wind().x, (float) snapshot.wind().y, (float) snapshot.wind().z);
    }

    private static ClientboundFireStatePayload.EmberEntry emberEntry(
        final FireEmberSnapshot ember) {
        return new ClientboundFireStatePayload.EmberEntry(ember.id(), ember.position().x,
            ember.position().y, ember.position().z, (float) ember.velocity().x,
            (float) ember.velocity().y, (float) ember.velocity().z, (float) ember.wind().x,
            (float) ember.wind().y, (float) ember.wind().z, ember.intensity(), ember.seed(),
            ember.startGameTime(), ember.lifetime());
    }

    private static ClientboundFireStatePayload.SmokeClusterEntry clusterEntry(
        final SmokeCluster cluster) {
        return new ClientboundFireStatePayload.SmokeClusterEntry(cluster.id(),
            cluster.position().x, cluster.position().y, cluster.position().z,
            cluster.smoke(), cluster.heat(), cluster.radius(), (float) cluster.wind().x,
            (float) cluster.wind().y, (float) cluster.wind().z, cluster.seed(),
            cluster.memberCount());
    }

    private static long key(final int x, final int z) {
        return ((long) x << 32) ^ (z & 0xFFFF_FFFFL);
    }

    private static long mix(long value) {
        value ^= value >>> 30; value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27; value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static final class LevelQueue {
        private boolean running;
        private SnapshotInput latest;
    }
    private record SnapshotInput(long gameTime, List<FireCellSnapshot> snapshots,
        List<FireEmberSnapshot> embers, List<Viewer> viewers, boolean authoritative) { }
    private record Viewer(UUID playerId, Vec3 position) { }
    private record PreparedViewer(UUID playerId, ClientboundFireStatePayload payload) { }
    private record PreparedBatch(List<PreparedViewer> viewers) { }
    private record RankedSnapshot(FireCellSnapshot snapshot, double distanceSquared) { }
    private record RankedEmber(FireEmberSnapshot snapshot, double distanceSquared) { }
    private record RankedSmokeCluster(SmokeCluster cluster, double distanceSquared) { }
    private record SmokeCluster(long id, Vec3 position, float smoke, float heat,
        float radius, Vec3 wind, long seed, int memberCount) { }

    private static final class SmokeClusterAccumulator {
        private final int cellX;
        private final int cellZ;
        private final java.util.HashSet<Long> hosts = new java.util.HashSet<>();
        private double weight;
        private double x;
        private double y;
        private double z;
        private double windX;
        private double windY;
        private double windZ;
        private float maximumSmoke;
        private float maximumHeat;
        private long seed;
        private int memberCount;

        private SmokeClusterAccumulator(final int cellX, final int cellZ) {
            this.cellX = cellX; this.cellZ = cellZ;
            seed = mix(key(cellX, cellZ) ^ 0x534D4F4B455F434CL);
        }

        private void add(final FireCellSnapshot snapshot) {
            if (!hosts.add(snapshot.anchor().host().asLong())) return;
            double sampleWeight = Math.max(0.01,
                snapshot.smoke() * (0.35 + snapshot.coverage() * 0.65));
            Vec3 position = snapshot.anchor().position();
            weight += sampleWeight;
            x += position.x * sampleWeight; y += position.y * sampleWeight;
            z += position.z * sampleWeight;
            windX += snapshot.wind().x * sampleWeight;
            windY += snapshot.wind().y * sampleWeight;
            windZ += snapshot.wind().z * sampleWeight;
            maximumSmoke = Math.max(maximumSmoke, snapshot.smoke());
            maximumHeat = Math.max(maximumHeat, snapshot.heat());
            seed ^= mix(snapshot.seed() + memberCount * 0x9E3779B97F4A7C15L);
            memberCount++;
        }

        private boolean isWildfire() { return memberCount >= MIN_SMOKE_CLUSTER_HOSTS; }

        private SmokeCluster finish() {
            double safeWeight = Math.max(0.01, weight);
            float radius = (float) Math.min(40.0, Math.max(8.0,
                Math.sqrt(memberCount) * 1.60));
            return new SmokeCluster(key(cellX, cellZ), new Vec3(x / safeWeight,
                y / safeWeight, z / safeWeight), maximumSmoke, maximumHeat, radius,
                new Vec3(windX / safeWeight, windY / safeWeight, windZ / safeWeight),
                mix(seed), memberCount);
        }
    }
}
