package com.andye.warmod.fire.network;

import com.andye.warmod.diagnostics.WarModPerformanceDiagnostics;
import com.andye.warmod.fire.FireEmberSnapshot;
import com.andye.warmod.fire.wind.FireWindImpulse;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Owns persistent per-viewer fire deltas with periodic complete repair snapshots. */
public final class FireNetworking {
    private static final double VISUAL_RANGE = 1_536.0;
    private static final long COMPLETE_REPAIR_INTERVAL_TICKS = 120L;
    private static final Map<ServerLevel, Map<UUID, ViewerState>> VIEWERS =
        new IdentityHashMap<>();
    private static boolean registered;

    private FireNetworking() { }

    public static void registerPayloadTypes() {
        if (registered) return;
        PayloadTypeRegistry.clientboundPlay().register(ClientboundFireStatePayload.TYPE,
            ClientboundFireStatePayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundFireWindImpulsePayload.TYPE,
            ClientboundFireWindImpulsePayload.STREAM_CODEC);
        ServerLevelEvents.UNLOAD.register((server, level) -> {
            synchronized (FireNetworking.class) { VIEWERS.remove(level); }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            synchronized (FireNetworking.class) { VIEWERS.clear(); }
        });
        registered = true;
    }

    public static void sendWindImpulse(final ServerLevel level, final FireWindImpulse impulse) {
        if (level == null || impulse == null) return;
        ClientboundFireWindImpulsePayload payload = ClientboundFireWindImpulsePayload.from(impulse);
        if (!payload.isWellFormed()) return;
        double range = Math.min(VISUAL_RANGE, impulse.radius() + 192.0);
        double rangeSquared = range * range;
        for (ServerPlayer player : PlayerLookup.level(level)) {
            if (player.distanceToSqr(impulse.center()) <= rangeSquared)
                ServerPlayNetworking.send(player, payload);
        }
    }

    /** Deltas are repaired periodically by a complete representation snapshot. */
    public static synchronized void sendSnapshots(final ServerLevel level,
        final List<ViewerSnapshot> snapshots) {
        if (level == null || snapshots == null || snapshots.isEmpty()) return;
        long started = WarModPerformanceDiagnostics.begin();
        int sent = 0;
        int sentCells = 0;
        Map<UUID, ViewerState> viewers = VIEWERS.computeIfAbsent(level,
            ignored -> new HashMap<>());
        Set<UUID> active = new HashSet<>();
        for (ViewerSnapshot snapshot : snapshots) {
            ServerPlayer player = level.getServer().getPlayerList()
                .getPlayer(snapshot.playerId());
            if (player == null || player.level() != level) continue;
            active.add(player.getUUID());
            ViewerState viewer = viewers.computeIfAbsent(player.getUUID(),
                ignored -> new ViewerState());
            for (ClientboundFireStatePayload payload : payloads(snapshot, viewer)) {
                if (!payload.isWellFormed()) continue;
                ServerPlayNetworking.send(player, payload);
                sent++;
                sentCells += payload.cells().size();
            }
        }
        viewers.keySet().removeIf(id -> !active.contains(id));
        if (viewers.isEmpty()) VIEWERS.remove(level);
        WarModPerformanceDiagnostics.add(
            WarModPerformanceDiagnostics.Gauge.FIRE_SNAPSHOT_PACKETS_SENT, sent);
        WarModPerformanceDiagnostics.add(
            WarModPerformanceDiagnostics.Gauge.FIRE_NETWORK_SENT_PATCH_ENTRIES,
            sentCells);
        WarModPerformanceDiagnostics.record(
            WarModPerformanceDiagnostics.Subsystem.FIRE_NETWORK, started);
    }

    private static List<ClientboundFireStatePayload> payloads(final ViewerSnapshot snapshot,
        final ViewerState viewer) {
        LinkedHashMap<Long, ClientboundFireStatePayload.CellEntry> current =
            new LinkedHashMap<>();
        for (FireVisualCell cell : snapshot.representation().cells()) {
            current.put(cell.id(), ClientboundFireStatePayload.CellEntry.from(cell));
        }
        boolean complete = viewer.cells.isEmpty()
            || snapshot.serverGameTime() - viewer.lastCompleteTick
                >= COMPLETE_REPAIR_INTERVAL_TICKS;
        ArrayList<ClientboundFireStatePayload.CellEntry> cells = new ArrayList<>();
        ArrayList<Long> removed = new ArrayList<>();
        if (complete) {
            cells.addAll(current.values());
            viewer.lastCompleteTick = snapshot.serverGameTime();
            WarModPerformanceDiagnostics.add(
                WarModPerformanceDiagnostics.Gauge.FIRE_NETWORK_FULL_REPAIRS, 1L);
            WarModPerformanceDiagnostics.add(
                WarModPerformanceDiagnostics.Gauge.FIRE_NETWORK_CELL_ADDS,
                current.size());
        } else {
            for (Map.Entry<Long, ClientboundFireStatePayload.CellEntry> entry
                : current.entrySet()) {
                if (!entry.getValue().equals(viewer.cells.get(entry.getKey()))) {
                    cells.add(entry.getValue());
                    WarModPerformanceDiagnostics.add(viewer.cells.containsKey(entry.getKey())
                        ? WarModPerformanceDiagnostics.Gauge.FIRE_NETWORK_CELL_UPDATES
                        : WarModPerformanceDiagnostics.Gauge.FIRE_NETWORK_CELL_ADDS, 1L);
                }
            }
            for (long id : viewer.cells.keySet()) {
                if (!current.containsKey(id)) removed.add(id);
            }
            WarModPerformanceDiagnostics.add(
                WarModPerformanceDiagnostics.Gauge.FIRE_NETWORK_CELL_REMOVALS,
                removed.size());
        }
        viewer.cells.clear();
        viewer.cells.putAll(current);
        List<ClientboundFireStatePayload.EmberEntry> embers = snapshot.embers().stream()
            .map(FireNetworking::emberEntry).toList();
        int pageCount = Math.max(1, Math.max(
            divideRoundUp(cells.size(), ClientboundFireStatePayload.MAX_CELLS),
            divideRoundUp(removed.size(), ClientboundFireStatePayload.MAX_REMOVED_CELLS)));
        ArrayList<ClientboundFireStatePayload> pages = new ArrayList<>(pageCount);
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            int cellStart = pageIndex * ClientboundFireStatePayload.MAX_CELLS;
            int cellEnd = Math.min(cells.size(), cellStart
                + ClientboundFireStatePayload.MAX_CELLS);
            int removedStart = pageIndex * ClientboundFireStatePayload.MAX_REMOVED_CELLS;
            int removedEnd = Math.min(removed.size(), removedStart
                + ClientboundFireStatePayload.MAX_REMOVED_CELLS);
            List<ClientboundFireStatePayload.CellEntry> pageCells = cellStart < cellEnd
                ? List.copyOf(cells.subList(cellStart, cellEnd)) : List.of();
            List<Long> pageRemoved = removedStart < removedEnd
                ? List.copyOf(removed.subList(removedStart, removedEnd)) : List.of();
            pages.add(new ClientboundFireStatePayload(snapshot.serverGameTime(),
                snapshot.generation(), snapshot.generation(), pageIndex, pageCount,
                complete ? FireVisualBand.COMPLETE_MASK : 0, pageCells, pageRemoved,
                pageIndex == 0, pageIndex == 0 ? embers : List.of()));
        }
        return List.copyOf(pages);
    }

    private static int divideRoundUp(final int value, final int divisor) {
        return value <= 0 ? 0 : 1 + (value - 1) / divisor;
    }

    private static ClientboundFireStatePayload.EmberEntry emberEntry(
        final FireEmberSnapshot ember) {
        return new ClientboundFireStatePayload.EmberEntry(ember.id(), ember.position().x,
            ember.position().y, ember.position().z, (float) ember.velocity().x,
            (float) ember.velocity().y, (float) ember.velocity().z,
            (float) ember.wind().x, (float) ember.wind().y, (float) ember.wind().z,
            ember.intensity(), ember.seed(), ember.startGameTime(), ember.lifetime());
    }

    public record ViewerSnapshot(UUID playerId, Vec3 viewerPosition,
        long serverGameTime, long generation,
        FireVisualRepresentationBuilder.Representation representation,
        List<FireEmberSnapshot> embers) { }

    private static final class ViewerState {
        private final LinkedHashMap<Long, ClientboundFireStatePayload.CellEntry> cells =
            new LinkedHashMap<>();
        private long lastCompleteTick = Long.MIN_VALUE;
    }
}
