package com.andye.warmod.warhead.network;

import com.andye.warmod.warhead.WarheadConstants;
import java.util.Objects;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class WarheadVisualNetworking {
    private static boolean payloadTypesRegistered;
    private WarheadVisualNetworking() { }
    public static void registerPayloadTypes() {
        if (payloadTypesRegistered) return;
        PayloadTypeRegistry.clientboundPlay().register(ClientboundWarheadLaunchPayload.TYPE, ClientboundWarheadLaunchPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundWarheadImpactPayload.TYPE, ClientboundWarheadImpactPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundWarheadRemovePayload.TYPE, ClientboundWarheadRemovePayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundWarheadTimingCorrectionPayload.TYPE, ClientboundWarheadTimingCorrectionPayload.STREAM_CODEC);
        payloadTypesRegistered = true;
    }
    public static void sendLaunch(ServerLevel level, ClientboundWarheadLaunchPayload payload, Vec3 target) { sendNearby(level, payload, target); }
    public static void sendImpact(ServerLevel level, ClientboundWarheadImpactPayload payload, Vec3 impact) { sendNearby(level, payload, impact); }
    public static void sendRemove(ServerLevel level, UUID id, Vec3 target) { Objects.requireNonNull(id, "warheadId"); Objects.requireNonNull(target, "intendedTarget"); sendNearby(level, new ClientboundWarheadRemovePayload(id), target); }
    public static void sendTimingCorrection(ServerLevel level, UUID id, int pausedSimulationTicks, boolean waiting, Vec3 safePosition) { sendNearby(level, new ClientboundWarheadTimingCorrectionPayload(id, level.getGameTime(), pausedSimulationTicks, waiting, safePosition), safePosition); }
    private static void sendNearby(ServerLevel level, ClientboundWarheadLaunchPayload payload, Vec3 center) { if (payload.isWellFormed()) sendToNearby(level, payload, center); }
    private static void sendNearby(ServerLevel level, ClientboundWarheadImpactPayload payload, Vec3 center) { if (payload.isWellFormed()) sendToNearby(level, payload, center); }
    private static void sendNearby(ServerLevel level, ClientboundWarheadRemovePayload payload, Vec3 center) { if (payload.isWellFormed()) sendToNearby(level, payload, center); }
    private static void sendNearby(ServerLevel level, ClientboundWarheadTimingCorrectionPayload payload, Vec3 center) { if (payload.isWellFormed()) sendToNearby(level, payload, center); }
    private static void sendToNearby(ServerLevel level, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload, Vec3 center) { for (ServerPlayer player : PlayerLookup.level(level)) if (player.distanceToSqr(center) <= WarheadConstants.VISUAL_RANGE_BLOCKS * WarheadConstants.VISUAL_RANGE_BLOCKS) ServerPlayNetworking.send(player, payload); }
}