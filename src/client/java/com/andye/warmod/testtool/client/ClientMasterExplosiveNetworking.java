package com.andye.warmod.testtool.client;

import com.andye.warmod.testtool.network.ClientboundOpenMasterExplosiveScreenPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class ClientMasterExplosiveNetworking {
	private static boolean registered;

	private ClientMasterExplosiveNetworking() {
	}

	public static void register() {
		if (registered) return;
		ClientPlayNetworking.registerGlobalReceiver(
			ClientboundOpenMasterExplosiveScreenPayload.TYPE,
			(payload, context) -> Minecraft.getInstance().gui.setScreen(
				new MasterExplosiveScreen(payload.hand(), payload.config())
			)
		);
		registered = true;
	}
}
