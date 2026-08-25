package com.andye.warmod.fire.network;

import com.andye.warmod.diagnostics.WarModPerformanceDiagnostics;
import com.andye.warmod.fire.FireCellSnapshot;
import com.andye.warmod.fire.FireEmberSnapshot;
import com.andye.warmod.fire.wind.FireWindImpulse;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Owns the complete, loss-tolerant client representation of authoritative fire. */
public final class FireNetworking {
    private static final double SMOKE_CLUSTER_RANGE = 1_536.0;
    private static final int SMOKE_CLUSTER_CELL_SIZE = 32;
    private static final int MIN_SMOKE_CLUSTER_HOSTS = 8;
    private static boolean registered;

    private FireNetworking() { }

    public static void registerPayloadTypes() {
        if (registered) return;
        PayloadTypeRegistry.clientboundPlay().register(ClientboundFireStatePayload.TYPE,
            ClientboundFireStatePayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundFireWindImpulsePayload.TYPE,
            ClientboundFireWindImpulsePayload.STREAM_CODEC);
        registered = true;
    }

    public static void sendWindImpulse(final ServerLevel level, final FireWindImpulse impulse) {
        if (level == null || impulse == null) return;
        ClientboundFireWindImpulsePayload payload = ClientboundFireWindImpulsePayload.from(impulse);
        if (!payload.isWellFormed()) return;
        double range = Math.min(SMOKE_CLUSTER_RANGE, impulse.radius() + 192.0);
        double rangeSquared = range * range;
        for (ServerPlayer player : PlayerLookup.level(level)) {
            if (player.distanceToSqr(impulse.center()) <= rangeSquared)
                ServerPlayNetworking.send(player, payload);
        }
    }

    /**
     * Sends complete per-viewer representation sets synchronously. The selected
     * set is capped before this method, so delivery never depends on a previous
     * delta or an asynchronous known-version state machine.
     */
    public static void sendSnapshots(final ServerLevel level,
        final List<ViewerSnapshot> snapshots) {
        if (level == null || snapshots == null || snapshots.isEmpty()) return;
        long started = WarModPerformanceDiagnostics.begin();
        int sent = 0;
        int sentEntries = 0;
        for (ViewerSnapshot snapshot : snapshots) {
            ServerPlayer player = level.getServer().getPlayerList()
                .getPlayer(snapshot.playerId());
            if (player == null || player.level() != level) continue;
            ClientboundFireStatePayload payload = payload(snapshot);
            if (!payload.isWellFormed()) continue;
            ServerPlayNetworking.send(player, payload);
            sent++;
            sentEntries += payload.entries().size();
        }
        WarModPerformanceDiagnostics.add(
            WarModPerformanceDiagnostics.Gauge.FIRE_SNAPSHOT_PACKETS_SENT, sent);
        WarModPerformanceDiagnostics.add(
            WarModPerformanceDiagnostics.Gauge.FIRE_NETWORK_SENT_PATCH_ENTRIES,
            sentEntries);
        WarModPerformanceDiagnostics.record(
            WarModPerformanceDiagnostics.Subsystem.FIRE_NETWORK, started);
    }

    private static ClientboundFireStatePayload payload(final ViewerSnapshot snapshot) {
        List<ClientboundFireStatePayload.Entry> entries = snapshot.patches().stream()
            .map(FireNetworking::entry).toList();
        List<ClientboundFireStatePayload.EmberEntry> embers = snapshot.embers().stream()
            .map(FireNetworking::emberEntry).toList();
        List<ClientboundFireStatePayload.SmokeClusterEntry> clusters = smokeClusters(
            snapshot.viewerPosition(), snapshot.smokeClusterSources());
        return new ClientboundFireStatePayload(snapshot.serverGameTime(),
            snapshot.generation(), true, entries, List.of(), true, embers,
            true, clusters);
    }

    private static List<ClientboundFireStatePayload.SmokeClusterEntry> smokeClusters(
        final Vec3 viewer, final List<FireCellSnapshot> sources) {
        Map<Long, SmokeClusterAccumulator> buckets = new HashMap<>();
        for (FireCellSnapshot snapshot : sources) {
            if (snapshot.smoke() < 0.018F) continue;
            int cellX = Math.floorDiv(snapshot.anchor().host().getX(),
                SMOKE_CLUSTER_CELL_SIZE);
            int cellZ = Math.floorDiv(snapshot.anchor().host().getZ(),
                SMOKE_CLUSTER_CELL_SIZE);
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
            if (nearest.size() > ClientboundFireStatePayload.MAX_SMOKE_CLUSTERS)
                nearest.poll();
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
            (float) snapshot.wind().x, (float) snapshot.wind().y,
            (float) snapshot.wind().z);
    }

    private static ClientboundFireStatePayload.EmberEntry emberEntry(
        final FireEmberSnapshot ember) {
        return new ClientboundFireStatePayload.EmberEntry(ember.id(), ember.position().x,
            ember.position().y, ember.position().z, (float) ember.velocity().x,
            (float) ember.velocity().y, (float) ember.velocity().z,
            (float) ember.wind().x, (float) ember.wind().y, (float) ember.wind().z,
            ember.intensity(), ember.seed(), ember.startGameTime(), ember.lifetime());
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

    public record ViewerSnapshot(UUID playerId, Vec3 viewerPosition,
        long serverGameTime, long generation, List<FireCellSnapshot> patches,
        List<FireEmberSnapshot> embers, List<FireCellSnapshot> smokeClusterSources) { }

    private record RankedSmokeCluster(SmokeCluster cluster, double distanceSquared) { }
    private record SmokeCluster(long id, Vec3 position, float smoke, float heat,
        float radius, Vec3 wind, long seed, int memberCount) { }

    private static final class SmokeClusterAccumulator {
        private final int cellX;
        private final int cellZ;
        private final HashSet<Long> hosts = new HashSet<>();
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

        private boolean isWildfire() {
            return memberCount >= MIN_SMOKE_CLUSTER_HOSTS;
        }

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
