package com.andye.warmod.acoustics.client;

import com.andye.warmod.acoustics.network.ClientboundAcousticEventPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ClientAcousticNetworking {
	private static boolean registered;

	private ClientAcousticNetworking() {
	}

	public static void register() {
		if (registered) {
			return;
		}
		ClientPlayNetworking.registerGlobalReceiver(ClientboundAcousticEventPayload.TYPE, (payload, context) -> {
			if (payload.isWellFormed()) {
				ClientAcousticEngine.INSTANCE.accept(payload);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(ClientAcousticEngine.INSTANCE::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> ClientAcousticEngine.INSTANCE.clear());
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> ClientAcousticEngine.INSTANCE.clear());
		registered = true;
	}
}
