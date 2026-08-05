package com.andye.warmod.warhead.network;

import com.andye.warmod.warhead.WarheadConstants;
import java.util.Objects;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class WarheadVisualNetworking {
	private static boolean payloadTypesRegistered;

	private WarheadVisualNetworking() {
	}

	public static void registerPayloadTypes() {
		if (payloadTypesRegistered) return;
		PayloadTypeRegistry.clientboundPlay().register(ClientboundWarheadLaunchPayload.TYPE, ClientboundWarheadLaunchPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundWarheadImpactPayload.TYPE, ClientboundWarheadImpactPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundWarheadDebrisPayload.TYPE, ClientboundWarheadDebrisPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundWarheadRemovePayload.TYPE, ClientboundWarheadRemovePayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundWarheadTimingCorrectionPayload.TYPE, ClientboundWarheadTimingCorrectionPayload.STREAM_CODEC);
		payloadTypesRegistered = true;
	}

	public static void sendLaunch(final ServerLevel level, final ClientboundWarheadLaunchPayload payload, final Vec3 target) {
		if (payload.isWellFormed()) sendToNearby(level, payload, target);
	}

	public static void sendImpact(final ServerLevel level, final ClientboundWarheadImpactPayload payload, final Vec3 impact) {
		if (payload.isWellFormed()) sendToNearby(level, payload, impact);
	}

	public static void sendDebris(final ServerLevel level, final ClientboundWarheadDebrisPayload payload, final Vec3 impact) {
		if (payload.isWellFormed() && !payload.entries().isEmpty()) sendToNearby(level, payload, impact);
	}

	public static void sendRemove(final ServerLevel level, final UUID id, final Vec3 target) {
		Objects.requireNonNull(id, "warheadId");
		Objects.requireNonNull(target, "intendedTarget");
		ClientboundWarheadRemovePayload payload = new ClientboundWarheadRemovePayload(id);
		if (payload.isWellFormed()) sendToNearby(level, payload, target);
	}

	public static void sendTimingCorrection(final ServerLevel level, final UUID id, final int pausedSimulationTicks,
		final boolean waiting, final Vec3 safePosition) {
		ClientboundWarheadTimingCorrectionPayload payload = new ClientboundWarheadTimingCorrectionPayload(
			id, level.getGameTime(), pausedSimulationTicks, waiting, safePosition
		);
		if (payload.isWellFormed()) sendToNearby(level, payload, safePosition);
	}

	private static void sendToNearby(final ServerLevel level, final CustomPacketPayload payload, final Vec3 center) {
		for (ServerPlayer player : PlayerLookup.level(level)) {
			if (player.distanceToSqr(center) <= WarheadConstants.VISUAL_RANGE_BLOCKS * WarheadConstants.VISUAL_RANGE_BLOCKS) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}
}
