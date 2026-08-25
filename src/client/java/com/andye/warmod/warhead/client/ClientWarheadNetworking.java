package com.andye.warmod.warhead.client;

import com.andye.warmod.warhead.client.audio.ClientTerminalAudioManager;
import com.andye.warmod.warhead.network.ClientboundWarheadDebrisPayload;
import com.andye.warmod.warhead.network.ClientboundWarheadImpactPayload;
import com.andye.warmod.warhead.network.ClientboundWarheadLaunchPayload;
import com.andye.warmod.warhead.network.ClientboundWarheadRemovePayload;
import com.andye.warmod.warhead.network.ClientboundWarheadTimingCorrectionPayload;
import com.andye.warmod.warhead.network.ClientboundWarheadClientControlPayload;
import com.andye.warmod.warhead.client.render.WarheadClientControlHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ClientWarheadNetworking {
	private static boolean registered;

	private ClientWarheadNetworking() {
	}

	public static void register() {
		if (registered) return;
		ClientPlayNetworking.registerGlobalReceiver(ClientboundWarheadLaunchPayload.TYPE, (payload, context) -> {
			ClientWarheadVisualManager.INSTANCE.acceptLaunch(payload);
			ClientTerminalAudioManager.INSTANCE.acceptLaunch(payload);
		});
		ClientPlayNetworking.registerGlobalReceiver(ClientboundWarheadImpactPayload.TYPE, (payload, context) -> {
			ClientWarheadVisualManager.INSTANCE.acceptImpact(payload);
			ClientTerminalAudioManager.INSTANCE.acceptImpact(payload.warheadId());
		});
		ClientPlayNetworking.registerGlobalReceiver(ClientboundWarheadDebrisPayload.TYPE,
			(payload, context) -> ClientDebrisBatchManager.INSTANCE.accept(payload));
		ClientPlayNetworking.registerGlobalReceiver(ClientboundWarheadTimingCorrectionPayload.TYPE,
			(payload, context) -> ClientWarheadVisualManager.INSTANCE.acceptTimingCorrection(payload));
		ClientPlayNetworking.registerGlobalReceiver(ClientboundWarheadRemovePayload.TYPE, (payload, context) -> {
			ClientWarheadVisualManager.INSTANCE.acceptRemove(payload);
			ClientTerminalAudioManager.INSTANCE.acceptRemoval(payload.warheadId());
		});
		ClientPlayNetworking.registerGlobalReceiver(ClientboundWarheadClientControlPayload.TYPE,
			(payload, context) -> WarheadClientControlHandler.accept(payload));
		ClientTickEvents.END_CLIENT_TICK.register(ClientWarheadVisualManager.INSTANCE::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
			ClientWarheadVisualManager.INSTANCE.clear();
			ClientDebrisBatchManager.INSTANCE.clear();
		});
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> {
			ClientWarheadVisualManager.INSTANCE.clear();
			ClientDebrisBatchManager.INSTANCE.clear();
		});
		registered = true;
	}
}
