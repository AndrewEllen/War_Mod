package com.andye.warmod.radar.network;

import com.andye.warmod.radar.RadarSubscriptionManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class RadarNetworking {
	private static boolean registered;
	private RadarNetworking() { }
	public static void register(){if(registered)return;
		PayloadTypeRegistry.serverboundPlay().register(ServerboundOpenRadarPayload.TYPE,ServerboundOpenRadarPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ServerboundCloseRadarPayload.TYPE,ServerboundCloseRadarPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ServerboundRadarResyncPayload.TYPE,ServerboundRadarResyncPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundOpenRadarPayload.TYPE,ClientboundOpenRadarPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundCloseRadarPayload.TYPE,ClientboundCloseRadarPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundRadarSnapshotPayload.TYPE,ClientboundRadarSnapshotPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundRadarTrackUpsertPayload.TYPE,ClientboundRadarTrackUpsertPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundRadarTrackRemovePayload.TYPE,ClientboundRadarTrackRemovePayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundRadarImpactPayload.TYPE,ClientboundRadarImpactPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundRadarInterceptionPayload.TYPE,ClientboundRadarInterceptionPayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ServerboundOpenRadarPayload.TYPE,(payload,context)->RadarSubscriptionManager.open(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(ServerboundCloseRadarPayload.TYPE,(payload,context)->RadarSubscriptionManager.close(context.player(),false));
		ServerPlayNetworking.registerGlobalReceiver(ServerboundRadarResyncPayload.TYPE,(payload,context)->RadarSubscriptionManager.resync(context.player()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->RadarSubscriptionManager.disconnect(handler.getPlayer()));registered=true;}
}
