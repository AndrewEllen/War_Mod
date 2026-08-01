package com.andye.warmod.warhead.client;

import com.andye.warmod.warhead.network.ClientboundWarheadImpactPayload;
import com.andye.warmod.warhead.network.ClientboundWarheadLaunchPayload;
import com.andye.warmod.warhead.network.ClientboundWarheadRemovePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ClientWarheadNetworking {
	private static boolean registered;

	private ClientWarheadNetworking() {
	}

	public static void register() {
		if (registered) {
			return;
		}

		ClientPlayNetworking.registerGlobalReceiver(
			ClientboundWarheadLaunchPayload.TYPE,
			(payload, context) -> ClientWarheadVisualManager.INSTANCE.acceptLaunch(payload)
		);
		ClientPlayNetworking.registerGlobalReceiver(
			ClientboundWarheadImpactPayload.TYPE,
			(payload, context) -> ClientWarheadVisualManager.INSTANCE.acceptImpact(payload)
		);
		ClientPlayNetworking.registerGlobalReceiver(
			ClientboundWarheadRemovePayload.TYPE,
			(payload, context) -> ClientWarheadVisualManager.INSTANCE.acceptRemove(payload)
		);
		ClientTickEvents.END_CLIENT_TICK.register(ClientWarheadVisualManager.INSTANCE::tick);
		ClientPlayConnectionEvents.DISCONNECT.register(
			(listener, client) -> ClientWarheadVisualManager.INSTANCE.clear()
		);
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(
			(client, level) -> ClientWarheadVisualManager.INSTANCE.clear()
		);
		registered = true;
	}
}