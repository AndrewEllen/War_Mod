package com.andye.warmod.fire.network;

import com.andye.warmod.fire.FireCellSnapshot;
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

    public static void sendSnapshot(final ServerLevel level, final List<FireCellSnapshot> snapshots) {
        double rangeSquared = VISUAL_RANGE * VISUAL_RANGE;
        int chunkRadius = (int) Math.ceil(VISUAL_RANGE / 16.0);
        Map<Long, List<FireCellSnapshot>> buckets = new HashMap<>();
        for (FireCellSnapshot snapshot : snapshots) {
            int chunkX = snapshot.position().getX() >> 4;
            int chunkZ = snapshot.position().getZ() >> 4;
            buckets.computeIfAbsent(chunkKey(chunkX, chunkZ), ignored -> new ArrayList<>())
                .add(snapshot);
        }
        for (ServerPlayer player : PlayerLookup.level(level)) {
            PriorityQueue<RankedSnapshot> nearest = new PriorityQueue<>(
                ClientboundFireStatePayload.MAX_ENTRIES + 1,
                Comparator.comparingDouble(RankedSnapshot::distanceSquared).reversed());
            int playerChunkX = player.blockPosition().getX() >> 4;
            int playerChunkZ = player.blockPosition().getZ() >> 4;
            for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                    List<FireCellSnapshot> candidates = buckets.get(
                        chunkKey(playerChunkX + dx, playerChunkZ + dz));
                    if (candidates == null) continue;
                    for (FireCellSnapshot snapshot : candidates) {
                        double distanceSquared = player.distanceToSqr(Vec3.atCenterOf(snapshot.position()));
                        if (distanceSquared > rangeSquared) continue;
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
                entries.add(new ClientboundFireStatePayload.Entry(snapshot.position().asLong(),
                    snapshot.intensity(), snapshot.heat(), snapshot.phase(), snapshot.seed(),
                    (float) snapshot.wind().x, (float) snapshot.wind().y,
                    (float) snapshot.wind().z));
            }
            ServerPlayNetworking.send(player, new ClientboundFireStatePayload(
                level.getGameTime(), List.copyOf(entries)));
        }
    }

    private static long chunkKey(final int x, final int z) {
        return ((long) x << 32) ^ (z & 0xFFFF_FFFFL);
    }

    private record RankedSnapshot(FireCellSnapshot snapshot, double distanceSquared) { }
}
