package com.andye.warmod.fire.network;

import com.andye.warmod.WarMod;
import com.andye.warmod.diagnostics.WarModPerformanceDiagnostics;
import com.andye.warmod.fire.FireCellSnapshot;
import com.andye.warmod.fire.FireEmberSnapshot;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Assembles already AOI-filtered immutable fire deltas away from the server tick. */
final class AsyncFireSnapshotDispatcher {
    private static final int SMOKE_CLUSTER_CELL_SIZE = 32;
    private static final int MIN_SMOKE_CLUSTER_HOSTS = 8;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "war-mod-fire-visual-preparation");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<ServerLevel, LevelQueue> LEVELS = new WeakHashMap<>();

    private AsyncFireSnapshotDispatcher() { }

    static void queue(final ServerLevel level, final List<FireNetworking.ViewerDelta> deltas) {
        if (level == null || deltas == null || deltas.isEmpty()) return;
        SnapshotInput input = new SnapshotInput(level.getGameTime(), List.copyOf(deltas));
        int queuedEntries = deltas.stream().mapToInt(delta -> delta.changedPatches().size()).sum();
        WarModPerformanceDiagnostics.add(
            WarModPerformanceDiagnostics.Gauge.FIRE_ASYNC_QUEUED_PATCH_ENTRIES,
            queuedEntries);
        synchronized (AsyncFireSnapshotDispatcher.class) {
            LevelQueue queue = LEVELS.computeIfAbsent(level, ignored -> new LevelQueue());
            if (queue.pending.isEmpty()) queue.pending.addLast(input);
            else queue.pending.addLast(coalesce(queue.pending.removeLast(), input));
            if (!queue.running) start(level, queue);
        }
    }

    private static void start(final ServerLevel level, final LevelQueue queue) {
        SnapshotInput input = queue.pending.removeFirst();
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
            int sentEntries = 0;
            for (PreparedViewer prepared : batch.viewers()) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(prepared.playerId());
                if (player == null || player.level() != level) continue;
                ServerPlayNetworking.send(player, prepared.payload());
                sent++;
                sentEntries += prepared.payload().entries().size();
            }
            WarModPerformanceDiagnostics.add(
                WarModPerformanceDiagnostics.Gauge.FIRE_NETWORK_PACKETS, sent);
            WarModPerformanceDiagnostics.add(
                WarModPerformanceDiagnostics.Gauge.FIRE_NETWORK_SENT_PATCH_ENTRIES,
                sentEntries);
            WarModPerformanceDiagnostics.record(
                WarModPerformanceDiagnostics.Subsystem.FIRE_NETWORK, started);
        }
        synchronized (AsyncFireSnapshotDispatcher.class) {
            LevelQueue queue = LEVELS.get(level);
            if (queue == null) return;
            if (!queue.pending.isEmpty()) start(level, queue);
            else {
                queue.running = false;
                LEVELS.remove(level);
            }
        }
    }

    private static PreparedBatch prepare(final SnapshotInput input) {
        List<PreparedViewer> viewers = new ArrayList<>(input.deltas().size());
        for (FireNetworking.ViewerDelta delta : input.deltas()) {
            List<ClientboundFireStatePayload.Entry> entries = delta.changedPatches().stream()
                .map(AsyncFireSnapshotDispatcher::entry).toList();
            List<ClientboundFireStatePayload.EmberEntry> embers = delta.embers().stream()
                .map(AsyncFireSnapshotDispatcher::emberEntry).toList();
            List<ClientboundFireStatePayload.SmokeClusterEntry> clusters = smokeClusters(
                delta.viewerPosition(), delta.smokeClusterSources());
            ClientboundFireStatePayload payload = new ClientboundFireStatePayload(
                delta.serverGameTime(), delta.generation(), delta.complete(), entries,
                delta.removedPatchIds(), true, embers, delta.smokeClusterComplete(), clusters);
            viewers.add(new PreparedViewer(delta.playerId(), payload));
        }
        return new PreparedBatch(List.copyOf(viewers));
    }

    private static SnapshotInput coalesce(final SnapshotInput older,
        final SnapshotInput newer) {
        LinkedHashMap<UUID, FireNetworking.ViewerDelta> viewers = new LinkedHashMap<>();
        for (FireNetworking.ViewerDelta delta : older.deltas())
            viewers.put(delta.playerId(), delta);
        for (FireNetworking.ViewerDelta delta : newer.deltas())
            viewers.merge(delta.playerId(), delta, AsyncFireSnapshotDispatcher::merge);
        return new SnapshotInput(newer.gameTime(), List.copyOf(viewers.values()));
    }

    private static FireNetworking.ViewerDelta merge(final FireNetworking.ViewerDelta older,
        final FireNetworking.ViewerDelta newer) {
        LinkedHashMap<Long, FireCellSnapshot> changed = new LinkedHashMap<>();
        HashSet<Long> removed = new HashSet<>();
        if (!newer.complete()) {
            for (FireCellSnapshot patch : older.changedPatches()) changed.put(patch.id(), patch);
            removed.addAll(older.removedPatchIds());
        }
        for (FireCellSnapshot patch : newer.changedPatches()) {
            changed.put(patch.id(), patch);
            removed.remove(patch.id());
        }
        for (long removedId : newer.removedPatchIds()) {
            changed.remove(removedId);
            removed.add(removedId);
        }
        boolean smokeComplete = older.smokeClusterComplete()
            || newer.smokeClusterComplete();
        List<FireCellSnapshot> smokeSources = newer.smokeClusterComplete()
            ? newer.smokeClusterSources() : older.smokeClusterSources();
        return new FireNetworking.ViewerDelta(newer.playerId(), newer.viewerPosition(),
            newer.serverGameTime(), newer.generation(), older.complete() || newer.complete(),
            List.copyOf(changed.values()), List.copyOf(removed), newer.embers(),
            smokeSources, smokeComplete);
    }

    private static List<ClientboundFireStatePayload.SmokeClusterEntry> smokeClusters(
        final Vec3 viewer, final List<FireCellSnapshot> sources) {
        Map<Long, SmokeClusterAccumulator> buckets = new HashMap<>();
        for (FireCellSnapshot snapshot : sources) {
            if (snapshot.smoke() < 0.018F) continue;
            int cellX = Math.floorDiv(snapshot.anchor().host().getX(), SMOKE_CLUSTER_CELL_SIZE);
            int cellZ = Math.floorDiv(snapshot.anchor().host().getZ(), SMOKE_CLUSTER_CELL_SIZE);
            buckets.computeIfAbsent(key(cellX, cellZ),
                ignored -> new SmokeClusterAccumulator(cellX, cellZ)).add(snapshot);
        }
        PriorityQueue<RankedSmokeCluster> nearest = new PriorityQueue<>(
            ClientboundFireStatePayload.MAX_SMOKE_CLUSTERS + 1,
            Comparator.comparingDouble(RankedSmokeCluster::distanceSquared).reversed());
        for (SmokeClusterAccumulator accumulator : buckets.values()) {
            if (!accumulator.isWildfire()) continue;
            SmokeCluster cluster = accumulator.finish();
            nearest.add(new RankedSmokeCluster(cluster,
                viewer.distanceToSqr(cluster.position())));
            if (nearest.size() > ClientboundFireStatePayload.MAX_SMOKE_CLUSTERS) nearest.poll();
        }
        return nearest.stream().sorted(Comparator.comparingDouble(
                RankedSmokeCluster::distanceSquared))
            .map(ranked -> clusterEntry(ranked.cluster())).toList();
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
        private final ArrayDeque<SnapshotInput> pending = new ArrayDeque<>();
    }

    private record SnapshotInput(long gameTime, List<FireNetworking.ViewerDelta> deltas) { }
    private record PreparedViewer(UUID playerId, ClientboundFireStatePayload payload) { }
    private record PreparedBatch(List<PreparedViewer> viewers) { }
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
            this.cellX = cellX;
            this.cellZ = cellZ;
            seed = mix(key(cellX, cellZ) ^ 0x534D4F4B455F434CL);
        }

        private void add(final FireCellSnapshot snapshot) {
            if (!hosts.add(snapshot.anchor().host().asLong())) return;
            double sampleWeight = Math.max(0.01,
                snapshot.smoke() * (0.35 + snapshot.coverage() * 0.65));
            Vec3 position = snapshot.anchor().position();
            weight += sampleWeight;
            x += position.x * sampleWeight;
            y += position.y * sampleWeight;
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
