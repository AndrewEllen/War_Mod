package com.andye.warmod.fire.network;

import com.andye.warmod.diagnostics.WarModPerformanceDiagnostics;
import com.andye.warmod.fire.FireCellSnapshot;
import com.andye.warmod.fire.FireEmberSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class FireNetworking {
    public static final double VISUAL_RANGE = 192.0;
    private static final double SMOKE_CLUSTER_RANGE = 1_536.0;
    private static final int SMOKE_CLUSTER_CELL_SIZE = 32;
    private static final int MIN_SMOKE_CLUSTER_HOSTS = 24;
    private static boolean registered;

    private FireNetworking() { }

    public static void registerPayloadTypes() {
        if (registered) return;
        PayloadTypeRegistry.clientboundPlay().register(ClientboundFireStatePayload.TYPE,
            ClientboundFireStatePayload.STREAM_CODEC);
        registered = true;
    }

    public static void sendSnapshot(final ServerLevel level, final List<FireCellSnapshot> snapshots,
		final List<FireEmberSnapshot> emberSnapshots, final boolean authoritative) {
        send(level, snapshots, emberSnapshots, true, authoritative);
    }

    public static void sendEmberSnapshot(final ServerLevel level,
        final List<FireEmberSnapshot> emberSnapshots) {
        send(level, List.of(), emberSnapshots, false, false);
    }

    private static void send(final ServerLevel level, final List<FireCellSnapshot> snapshots,
        final List<FireEmberSnapshot> emberSnapshots, final boolean patchesIncluded,
        final boolean patchesAuthoritative) {
        long diagnosticsStarted = WarModPerformanceDiagnostics.begin();
        int sentPackets = 0;
        double rangeSquared = VISUAL_RANGE * VISUAL_RANGE;
        double smokeClusterRangeSquared = SMOKE_CLUSTER_RANGE * SMOKE_CLUSTER_RANGE;
        int chunkRadius = (int) Math.ceil(VISUAL_RANGE / 16.0);
        Map<Long, List<FireCellSnapshot>> buckets = new HashMap<>();
        Map<Long, SmokeClusterAccumulator> smokeClusterBuckets = new HashMap<>();
        for (FireCellSnapshot snapshot : snapshots) {
            int chunkX = snapshot.anchor().host().getX() >> 4;
            int chunkZ = snapshot.anchor().host().getZ() >> 4;
            buckets.computeIfAbsent(chunkKey(chunkX, chunkZ), ignored -> new ArrayList<>())
                .add(snapshot);
            if (patchesIncluded && snapshot.smoke() >= 0.018F) {
                int cellX = Math.floorDiv(snapshot.anchor().host().getX(), SMOKE_CLUSTER_CELL_SIZE);
                int cellZ = Math.floorDiv(snapshot.anchor().host().getZ(), SMOKE_CLUSTER_CELL_SIZE);
                smokeClusterBuckets.computeIfAbsent(chunkKey(cellX, cellZ),
                    ignored -> new SmokeClusterAccumulator(cellX, cellZ)).add(snapshot);
            }
        }
        List<SmokeCluster> smokeClusters = smokeClusterBuckets.values().stream()
            .filter(SmokeClusterAccumulator::isWildfire)
            .map(SmokeClusterAccumulator::finish).toList();
        for (ServerPlayer player : PlayerLookup.level(level)) {
            PriorityQueue<RankedSnapshot> nearest = new PriorityQueue<>(
                ClientboundFireStatePayload.MAX_ENTRIES + 1,
                Comparator.comparingDouble(RankedSnapshot::distanceSquared).reversed());
            int playerChunkX = player.blockPosition().getX() >> 4;
            int playerChunkZ = player.blockPosition().getZ() >> 4;
            int visibleCandidateCount = 0;
            int patchSearchRadius = patchesIncluded ? chunkRadius : -1;
            for (int dx = -patchSearchRadius; dx <= patchSearchRadius; dx++) {
                for (int dz = -patchSearchRadius; dz <= patchSearchRadius; dz++) {
                    List<FireCellSnapshot> candidates = buckets.get(
                        chunkKey(playerChunkX + dx, playerChunkZ + dz));
                    if (candidates == null) continue;
                    for (FireCellSnapshot snapshot : candidates) {
                        double distanceSquared = player.distanceToSqr(snapshot.anchor().position());
                        if (distanceSquared > rangeSquared) continue;
                        visibleCandidateCount++;
                        nearest.add(new RankedSnapshot(snapshot, distanceSquared));
                        if (nearest.size() > ClientboundFireStatePayload.MAX_ENTRIES) nearest.poll();
                    }
                }
            }
            List<FireCellSnapshot> visible = nearest.stream()
                .sorted(Comparator.comparingDouble(RankedSnapshot::distanceSquared))
                .map(RankedSnapshot::snapshot).toList();
            List<ClientboundFireStatePayload.Entry> entries = new ArrayList<>(visible.size());
            for (FireCellSnapshot snapshot : visible) {
                entries.add(new ClientboundFireStatePayload.Entry(snapshot.id(),
                    snapshot.anchor().host().asLong(), (byte) snapshot.anchor().face().ordinal(),
                    snapshot.anchor().localX(), snapshot.anchor().localY(), snapshot.anchor().localZ(),
                    snapshot.intensity(), snapshot.heat(), snapshot.coverage(), snapshot.smoke(),
                    snapshot.phase(), snapshot.seed(), snapshot.ignitionGameTime(),
                    (float) snapshot.wind().x, (float) snapshot.wind().y,
                    (float) snapshot.wind().z));
            }
			PriorityQueue<RankedEmber> nearestEmbers = new PriorityQueue<>(
				ClientboundFireStatePayload.MAX_EMBERS + 1,
				Comparator.comparingDouble(RankedEmber::distanceSquared).reversed());
			int visibleEmberCount = 0;
			for (FireEmberSnapshot ember : emberSnapshots) {
				double distanceSquared = player.distanceToSqr(ember.position());
				if (distanceSquared > rangeSquared) continue;
				visibleEmberCount++;
				nearestEmbers.add(new RankedEmber(ember, distanceSquared));
				if (nearestEmbers.size() > ClientboundFireStatePayload.MAX_EMBERS)
					nearestEmbers.poll();
			}
			List<ClientboundFireStatePayload.EmberEntry> emberEntries = nearestEmbers.stream()
				.sorted(Comparator.comparingDouble(RankedEmber::distanceSquared))
				.map(ranked -> {
					FireEmberSnapshot ember = ranked.snapshot();
					return new ClientboundFireStatePayload.EmberEntry(ember.id(),
						ember.position().x, ember.position().y, ember.position().z,
						(float) ember.velocity().x, (float) ember.velocity().y,
						(float) ember.velocity().z, (float) ember.wind().x,
                        (float) ember.wind().y, (float) ember.wind().z,
                        ember.intensity(), ember.seed(),
						ember.startGameTime(), ember.lifetime());
				}).toList();
            PriorityQueue<RankedSmokeCluster> nearestSmokeClusters = new PriorityQueue<>(
                ClientboundFireStatePayload.MAX_SMOKE_CLUSTERS + 1,
                Comparator.comparingDouble(RankedSmokeCluster::distanceSquared).reversed());
            int visibleSmokeClusterCount = 0;
            if (patchesIncluded) for (SmokeCluster cluster : smokeClusters) {
                double distanceSquared = player.distanceToSqr(cluster.position());
                if (distanceSquared > smokeClusterRangeSquared) continue;
                visibleSmokeClusterCount++;
                nearestSmokeClusters.add(new RankedSmokeCluster(cluster, distanceSquared));
                if (nearestSmokeClusters.size() > ClientboundFireStatePayload.MAX_SMOKE_CLUSTERS)
                    nearestSmokeClusters.poll();
            }
            List<ClientboundFireStatePayload.SmokeClusterEntry> smokeClusterEntries =
                nearestSmokeClusters.stream()
                    .sorted(Comparator.comparingDouble(RankedSmokeCluster::distanceSquared))
                    .map(ranked -> {
                        SmokeCluster cluster = ranked.cluster();
                        return new ClientboundFireStatePayload.SmokeClusterEntry(cluster.id(),
                            cluster.position().x, cluster.position().y, cluster.position().z,
                            cluster.smoke(), cluster.heat(), cluster.radius(),
                            (float) cluster.wind().x, (float) cluster.wind().y,
                            (float) cluster.wind().z, cluster.seed(), cluster.memberCount());
                    }).toList();
            ServerPlayNetworking.send(player, new ClientboundFireStatePayload(
                level.getGameTime(), patchesAuthoritative
                    && visibleCandidateCount <= ClientboundFireStatePayload.MAX_ENTRIES,
                List.copyOf(entries), visibleEmberCount <= ClientboundFireStatePayload.MAX_EMBERS,
				List.copyOf(emberEntries), patchesAuthoritative
                    && visibleSmokeClusterCount <= ClientboundFireStatePayload.MAX_SMOKE_CLUSTERS,
                List.copyOf(smokeClusterEntries)));
            sentPackets++;
        }
        WarModPerformanceDiagnostics.add(
            WarModPerformanceDiagnostics.Gauge.FIRE_NETWORK_PACKETS, sentPackets);
        WarModPerformanceDiagnostics.record(
            WarModPerformanceDiagnostics.Subsystem.FIRE_NETWORK, diagnosticsStarted);
    }

    private static long chunkKey(final int x, final int z) {
        return ((long) x << 32) ^ (z & 0xFFFF_FFFFL);
    }

    private record RankedSnapshot(FireCellSnapshot snapshot, double distanceSquared) { }
	private record RankedEmber(FireEmberSnapshot snapshot, double distanceSquared) { }
    private record RankedSmokeCluster(SmokeCluster cluster, double distanceSquared) { }

    private record SmokeCluster(long id, Vec3 position, float smoke, float heat,
        float radius, Vec3 wind, long seed, int memberCount) { }

    private static final class SmokeClusterAccumulator {
        private final int cellX;
        private final int cellZ;
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
        private final java.util.HashSet<Long> hosts = new java.util.HashSet<>();

        private SmokeClusterAccumulator(final int cellX, final int cellZ) {
            this.cellX = cellX;
            this.cellZ = cellZ;
            this.seed = mix(chunkKey(cellX, cellZ) ^ 0x534D4F4B455F434CL);
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
            return new SmokeCluster(chunkKey(cellX, cellZ), new Vec3(x / safeWeight,
                y / safeWeight, z / safeWeight), maximumSmoke, maximumHeat, radius,
                new Vec3(windX / safeWeight, windY / safeWeight, windZ / safeWeight),
                mix(seed), memberCount);
        }
    }

    private static long mix(long value) {
        value ^= value >>> 30; value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27; value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
