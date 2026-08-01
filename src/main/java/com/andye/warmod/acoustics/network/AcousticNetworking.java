package com.andye.warmod.acoustics.network;

import java.util.Objects;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class AcousticNetworking {
	private static boolean payloadTypesRegistered;

	private AcousticNetworking() {
	}

	public static void registerPayloadTypes() {
		if (payloadTypesRegistered) {
			return;
		}

		PayloadTypeRegistry.clientboundPlay().register(ClientboundAcousticEventPayload.TYPE, ClientboundAcousticEventPayload.STREAM_CODEC);
		payloadTypesRegistered = true;
	}

	public static void send(final ServerPlayer player, final ClientboundAcousticEventPayload payload) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(payload, "payload");
		if (payload.isWellFormed()) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
