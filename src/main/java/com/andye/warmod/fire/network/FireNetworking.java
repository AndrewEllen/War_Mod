package com.andye.warmod.fire.network;

import com.andye.warmod.fire.FireCellSnapshot;
import com.andye.warmod.fire.FireEmberSnapshot;
import com.andye.warmod.fire.wind.FireWindImpulse;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class FireNetworking {
    private static final double SMOKE_CLUSTER_RANGE = 1_536.0;
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

    public static void sendSnapshot(final ServerLevel level,
        final List<FireCellSnapshot> snapshots,
        final List<FireEmberSnapshot> emberSnapshots, final boolean authoritative) {
        AsyncFireSnapshotDispatcher.queue(level, snapshots, emberSnapshots, authoritative);
    }

    public static void sendEmberSnapshot(final ServerLevel level,
        final List<FireEmberSnapshot> emberSnapshots) {
        AsyncFireSnapshotDispatcher.queue(level, List.of(), emberSnapshots, false);
    }
}
