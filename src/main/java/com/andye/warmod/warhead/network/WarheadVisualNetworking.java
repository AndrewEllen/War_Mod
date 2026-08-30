package com.andye.warmod.warhead.network;

import com.andye.warmod.warhead.WarheadConstants;
import com.andye.warmod.warhead.WarheadGlassShockwaveManager;
import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.andye.warmod.warhead.WarheadPreparedCommitManager;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class WarheadVisualNetworking {
    private static final int MAX_RECENT_IMPACTS = 128;
    private static final int MAX_STATE_SEQUENCES = 512;
    private static final Map<UUID, ImpactDescriptor> RECENT_IMPACTS = new LinkedHashMap<>();
    private static final Map<UUID, Long> STATE_SEQUENCES = new LinkedHashMap<>();
    private static boolean payloadTypesRegistered;

    private WarheadVisualNetworking() { }

    public static void registerPayloadTypes() {
        if (payloadTypesRegistered) return;
        PayloadTypeRegistry.clientboundPlay().register(ClientboundWarheadLaunchPayload.TYPE,
            ClientboundWarheadLaunchPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundWarheadImpactPayload.TYPE,
            ClientboundWarheadImpactPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundWarheadDebrisPayload.TYPE,
            ClientboundWarheadDebrisPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundWarheadRemovePayload.TYPE,
            ClientboundWarheadRemovePayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundWarheadTimingCorrectionPayload.TYPE,
            ClientboundWarheadTimingCorrectionPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundWarheadClientControlPayload.TYPE,
            ClientboundWarheadClientControlPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundWarheadTerrainCommitPayload.TYPE,
            ClientboundWarheadTerrainCommitPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ServerboundWarheadTerrainCommitAckPayload.TYPE,
            ServerboundWarheadTerrainCommitAckPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ServerboundWarheadTerrainCommitAckPayload.TYPE,
            (payload, context) -> {
                if (payload.isWellFormed()) WarheadPreparedCommitManager.ack(
                    context.player(), payload.impactId(), payload.sequence());
            });
        payloadTypesRegistered = true;
    }

    public static synchronized void sendLaunch(final ServerLevel level,
        final ClientboundWarheadLaunchPayload payload,
        final Vec3 target) {
        if (payload == null || payload.warheadId() == null) return;
        STATE_SEQUENCES.put(payload.warheadId(), 1L);
        trimStateSequences();
        ClientboundWarheadLaunchPayload authoritative = payload.withAuthoritativeState(
            1L, level.getGameTime());
        if (authoritative.isWellFormed()) sendToNearby(level, authoritative, target);
    }

    public static synchronized void sendImpact(final ServerLevel level,
        final ClientboundWarheadImpactPayload payload, final Vec3 impact) {
		sendImpact(level, payload, impact, false);
	}

    public static synchronized void sendImpact(final ServerLevel level,
        final ClientboundWarheadImpactPayload payload, final Vec3 impact,
		final boolean customFire) {
        sendImpact(level, payload, impact, customFire, false);
    }

    public static synchronized void sendImpact(final ServerLevel level,
        final ClientboundWarheadImpactPayload payload, final Vec3 impact,
		final boolean customFire, final boolean preparedTerrain) {
        if (payload == null || payload.warheadId() == null) return;
        ClientboundWarheadImpactPayload authoritative = payload.withAuthoritativeState(
            nextSequence(payload.warheadId()), level.getGameTime());
        if (!authoritative.isWellFormed()) return;
        while (RECENT_IMPACTS.size() >= MAX_RECENT_IMPACTS) {
            UUID oldest = RECENT_IMPACTS.keySet().iterator().next();
            RECENT_IMPACTS.remove(oldest);
        }
        RECENT_IMPACTS.put(authoritative.warheadId(), new ImpactDescriptor(
            authoritative.impactVisualScale(),
            authoritative.payloadType() == WarheadPayloadType.NUCLEAR));
        if (!preparedTerrain) {
            WarheadGlassShockwaveManager.schedule(level, authoritative, impact, customFire);
        }
        sendToNearby(level, authoritative, impact);
    }

    public static synchronized void sendDebris(final ServerLevel level,
        final ClientboundWarheadDebrisPayload payload, final Vec3 impact) {
        if (!payload.isWellFormed() || payload.entries().isEmpty()) return;
        ImpactDescriptor descriptor = RECENT_IMPACTS.remove(payload.impactId());
        ClientboundWarheadDebrisPayload tuned = descriptor == null ? payload
            : WarheadDebrisVisualTuner.tune(payload, descriptor.visualScale(), descriptor.nuclear());
        if (tuned.isWellFormed() && !tuned.entries().isEmpty()) sendToNearby(level, tuned, impact);
    }

    public static synchronized void sendRemove(final ServerLevel level, final UUID id,
        final Vec3 target) {
        Objects.requireNonNull(id, "warheadId");
        Objects.requireNonNull(target, "intendedTarget");
        ClientboundWarheadRemovePayload payload = new ClientboundWarheadRemovePayload(id,
            nextSequence(id), WarheadNetworkState.REMOVED, level.getGameTime(),
            target.x, target.y, target.z);
        if (payload.isWellFormed()) sendToNearby(level, payload, target);
    }

    public static synchronized void sendTimingCorrection(final ServerLevel level, final UUID id,
        final int pausedSimulationTicks, final boolean waiting, final Vec3 safePosition) {
        ClientboundWarheadTimingCorrectionPayload payload = new ClientboundWarheadTimingCorrectionPayload(
            id, nextSequence(id), waiting ? WarheadNetworkState.WAITING_FOR_WORLD
                : WarheadNetworkState.FLIGHT, level.getGameTime(), pausedSimulationTicks,
            safePosition);
        if (payload.isWellFormed()) sendToNearby(level, payload, safePosition);
    }

    private static void sendToNearby(final ServerLevel level, final CustomPacketPayload payload,
        final Vec3 center) {
        for (ServerPlayer player : PlayerLookup.level(level)) {
            if (player.distanceToSqr(center)
                <= WarheadConstants.VISUAL_RANGE_BLOCKS * WarheadConstants.VISUAL_RANGE_BLOCKS) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    private static long nextSequence(final UUID id) {
        long sequence = STATE_SEQUENCES.getOrDefault(id, 0L) + 1L;
        STATE_SEQUENCES.put(id, sequence);
        trimStateSequences();
        return sequence;
    }

    private static void trimStateSequences() {
        while (STATE_SEQUENCES.size() > MAX_STATE_SEQUENCES) {
            UUID oldest = STATE_SEQUENCES.keySet().iterator().next();
            STATE_SEQUENCES.remove(oldest);
        }
    }

    private record ImpactDescriptor(float visualScale, boolean nuclear) { }
}
