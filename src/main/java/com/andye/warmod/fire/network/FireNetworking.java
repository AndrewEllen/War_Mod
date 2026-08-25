package com.andye.warmod.fire.network;

import com.andye.warmod.fire.FireCellSnapshot;
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

    public static void sendDeltas(final ServerLevel level,
        final List<ViewerDelta> deltas) {
        AsyncFireSnapshotDispatcher.queue(level, deltas);
    }

    /** Immutable, already interest-filtered input for asynchronous packet assembly. */
    public record ViewerDelta(UUID playerId, Vec3 viewerPosition, long serverGameTime,
        long generation,
        boolean complete, List<FireCellSnapshot> changedPatches, List<Long> removedPatchIds,
        List<FireEmberSnapshot> embers, List<FireCellSnapshot> smokeClusterSources,
        boolean smokeClusterComplete) { }
}
