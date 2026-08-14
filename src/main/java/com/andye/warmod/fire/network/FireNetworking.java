package com.andye.warmod.fire.network;

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
    private static boolean registered;

    private FireNetworking() { }

    public static void registerPayloadTypes() {
        if (registered) return;
        PayloadTypeRegistry.clientboundPlay().register(ClientboundFireStatePayload.TYPE,
            ClientboundFireStatePayload.STREAM_CODEC);
        registered = true;
    }

    public static void sendSnapshot(final ServerLevel level, final List<FireCellSnapshot> snapshots,
		final List<FireEmberSnapshot> emberSnapshots) {
        send(level, snapshots, emberSnapshots, true);
    }

    public static void sendEmberSnapshot(final ServerLevel level,
        final List<FireEmberSnapshot> emberSnapshots) {
        send(level, List.of(), emberSnapshots, false);
    }

    private static void send(final ServerLevel level, final List<FireCellSnapshot> snapshots,
        final List<FireEmberSnapshot> emberSnapshots, final boolean patchesComplete) {
        double rangeSquared = VISUAL_RANGE * VISUAL_RANGE;
        int chunkRadius = (int) Math.ceil(VISUAL_RANGE / 16.0);
        Map<Long, List<FireCellSnapshot>> buckets = new HashMap<>();
        for (FireCellSnapshot snapshot : snapshots) {
            int chunkX = snapshot.anchor().host().getX() >> 4;
            int chunkZ = snapshot.anchor().host().getZ() >> 4;
            buckets.computeIfAbsent(chunkKey(chunkX, chunkZ), ignored -> new ArrayList<>())
                .add(snapshot);
        }
        for (ServerPlayer player : PlayerLookup.level(level)) {
            PriorityQueue<RankedSnapshot> nearest = new PriorityQueue<>(
                ClientboundFireStatePayload.MAX_ENTRIES + 1,
                Comparator.comparingDouble(RankedSnapshot::distanceSquared).reversed());
            int playerChunkX = player.blockPosition().getX() >> 4;
            int playerChunkZ = player.blockPosition().getZ() >> 4;
            int visibleCandidateCount = 0;
            int patchSearchRadius = patchesComplete ? chunkRadius : -1;
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
            ServerPlayNetworking.send(player, new ClientboundFireStatePayload(
                level.getGameTime(), patchesComplete
                    && visibleCandidateCount <= ClientboundFireStatePayload.MAX_ENTRIES,
                List.copyOf(entries), visibleEmberCount <= ClientboundFireStatePayload.MAX_EMBERS,
				List.copyOf(emberEntries)));
        }
    }

    private static long chunkKey(final int x, final int z) {
        return ((long) x << 32) ^ (z & 0xFFFF_FFFFL);
    }

    private record RankedSnapshot(FireCellSnapshot snapshot, double distanceSquared) { }
	private record RankedEmber(FireEmberSnapshot snapshot, double distanceSquared) { }
}
