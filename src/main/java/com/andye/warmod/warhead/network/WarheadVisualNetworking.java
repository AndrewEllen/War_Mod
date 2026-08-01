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

	private WarheadVisualNetworking() {
	}

	public static void registerPayloadTypes() {
		if (payloadTypesRegistered) {
			return;
		}

		PayloadTypeRegistry.clientboundPlay().register(ClientboundWarheadLaunchPayload.TYPE, ClientboundWarheadLaunchPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundWarheadImpactPayload.TYPE, ClientboundWarheadImpactPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundWarheadRemovePayload.TYPE, ClientboundWarheadRemovePayload.STREAM_CODEC);
		payloadTypesRegistered = true;
	}

	public static void sendLaunch(final ServerLevel level, final ClientboundWarheadLaunchPayload payload, final Vec3 intendedTarget) {
		sendNearby(level, payload, intendedTarget);
	}

	public static void sendImpact(final ServerLevel level, final ClientboundWarheadImpactPayload payload, final Vec3 impactPosition) {
		sendNearby(level, payload, impactPosition);
	}

	public static void sendRemove(final ServerLevel level, final UUID warheadId, final Vec3 intendedTarget) {
		Objects.requireNonNull(warheadId, "warheadId");
		Objects.requireNonNull(intendedTarget, "intendedTarget");
		sendNearby(level, new ClientboundWarheadRemovePayload(warheadId), intendedTarget);
	}

	private static void sendNearby(final ServerLevel level, final ClientboundWarheadLaunchPayload payload, final Vec3 center) {
		if (payload.isWellFormed()) {
			for (ServerPlayer player : PlayerLookup.level(level)) {
				if (player.distanceToSqr(center) <= WarheadConstants.VISUAL_RANGE_BLOCKS * WarheadConstants.VISUAL_RANGE_BLOCKS) {
					ServerPlayNetworking.send(player, payload);
				}
			}
		}
	}

	private static void sendNearby(final ServerLevel level, final ClientboundWarheadImpactPayload payload, final Vec3 center) {
		if (payload.isWellFormed()) {
			for (ServerPlayer player : PlayerLookup.level(level)) {
				if (player.distanceToSqr(center) <= WarheadConstants.VISUAL_RANGE_BLOCKS * WarheadConstants.VISUAL_RANGE_BLOCKS) {
					ServerPlayNetworking.send(player, payload);
				}
			}
		}
	}

	private static void sendNearby(final ServerLevel level, final ClientboundWarheadRemovePayload payload, final Vec3 center) {
		if (payload.isWellFormed()) {
			for (ServerPlayer player : PlayerLookup.level(level)) {
				if (player.distanceToSqr(center) <= WarheadConstants.VISUAL_RANGE_BLOCKS * WarheadConstants.VISUAL_RANGE_BLOCKS) {
					ServerPlayNetworking.send(player, payload);
				}
			}
		}
	}
}