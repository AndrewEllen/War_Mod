package com.andye.warmod.icbm.client;

import com.andye.warmod.icbm.client.audio.ClientIcbmAudioManager;
import com.andye.warmod.icbm.network.ClientboundIcbmLaunchPayload;
import com.andye.warmod.icbm.network.ClientboundIcbmRemovePayload;
import com.andye.warmod.icbm.network.ClientboundIcbmSeparationPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class ClientIcbmNetworking {
	private static boolean registered;
	private ClientIcbmNetworking() { }
	public static void register() {
		if (registered) return;
		ClientPlayNetworking.registerGlobalReceiver(ClientboundIcbmLaunchPayload.TYPE, (payload, context) -> {
			ClientIcbmVisualManager.INSTANCE.acceptLaunch(payload);
			ClientIcbmAudioManager.INSTANCE.acceptLaunch(payload);
		});
		ClientPlayNetworking.registerGlobalReceiver(ClientboundIcbmSeparationPayload.TYPE,
			(payload, context) -> ClientIcbmVisualManager.INSTANCE.acceptSeparation(payload));
		ClientPlayNetworking.registerGlobalReceiver(ClientboundIcbmRemovePayload.TYPE, (payload, context) -> {
			ClientIcbmVisualManager.INSTANCE.acceptRemove(payload);
			ClientIcbmAudioManager.INSTANCE.acceptCancellation(payload.missileId());
		});
		ClientTickEvents.END_CLIENT_TICK.register(ClientIcbmVisualManager.INSTANCE::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> ClientIcbmVisualManager.INSTANCE.clear());
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> ClientIcbmVisualManager.INSTANCE.clear());
		registered = true;
	}
}