package com.andye.warmod.fire.network;

import com.andye.warmod.diagnostics.WarModPerformanceDiagnostics;
import com.andye.warmod.fire.FireEmberSnapshot;
import com.andye.warmod.fire.wind.FireWindImpulse;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Owns the complete-per-band, loss-tolerant client representation of fire. */
public final class FireNetworking {
    private static final double VISUAL_RANGE = 1_536.0;
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
        double range = Math.min(VISUAL_RANGE, impulse.radius() + 192.0);
        double rangeSquared = range * range;
        for (ServerPlayer player : PlayerLookup.level(level)) {
            if (player.distanceToSqr(impulse.center()) <= rangeSquared)
                ServerPlayNetworking.send(player, payload);
        }
    }

    /** Each selected band is complete, so the next snapshot repairs packet loss. */
    public static void sendSnapshots(final ServerLevel level,
        final List<ViewerSnapshot> snapshots) {
        if (level == null || snapshots == null || snapshots.isEmpty()) return;
        long started = WarModPerformanceDiagnostics.begin();
        int sent = 0;
        int sentCells = 0;
        for (ViewerSnapshot snapshot : snapshots) {
            ServerPlayer player = level.getServer().getPlayerList()
                .getPlayer(snapshot.playerId());
            if (player == null || player.level() != level) continue;
            ClientboundFireStatePayload payload = payload(snapshot);
            if (!payload.isWellFormed()) continue;
            ServerPlayNetworking.send(player, payload);
            sent++;
            sentCells += payload.cells().size();
        }
        WarModPerformanceDiagnostics.add(
            WarModPerformanceDiagnostics.Gauge.FIRE_SNAPSHOT_PACKETS_SENT, sent);
        WarModPerformanceDiagnostics.add(
            WarModPerformanceDiagnostics.Gauge.FIRE_NETWORK_SENT_PATCH_ENTRIES,
            sentCells);
        WarModPerformanceDiagnostics.record(
            WarModPerformanceDiagnostics.Subsystem.FIRE_NETWORK, started);
    }

    private static ClientboundFireStatePayload payload(final ViewerSnapshot snapshot) {
        List<ClientboundFireStatePayload.CellEntry> cells = snapshot.representation().cells()
            .stream().map(ClientboundFireStatePayload.CellEntry::from).toList();
        List<ClientboundFireStatePayload.EmberEntry> embers = snapshot.embers().stream()
            .map(FireNetworking::emberEntry).toList();
        return new ClientboundFireStatePayload(snapshot.serverGameTime(),
            snapshot.generation(), snapshot.representation().completeBandMask(),
            cells, true, embers);
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
}
