package com.andye.warmod.icbm.client;

import com.andye.warmod.icbm.network.ClientboundIcbmLaunchPayload;
import com.andye.warmod.icbm.network.ClientboundIcbmRemovePayload;
import com.andye.warmod.icbm.network.ClientboundIcbmSeparationPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ClientIcbmNetworking {private static boolean registered;private ClientIcbmNetworking(){}public static void register(){if(registered)return;ClientPlayNetworking.registerGlobalReceiver(ClientboundIcbmLaunchPayload.TYPE,(p,c)->ClientIcbmVisualManager.INSTANCE.acceptLaunch(p));ClientPlayNetworking.registerGlobalReceiver(ClientboundIcbmSeparationPayload.TYPE,(p,c)->ClientIcbmVisualManager.INSTANCE.acceptSeparation(p));ClientPlayNetworking.registerGlobalReceiver(ClientboundIcbmRemovePayload.TYPE,(p,c)->ClientIcbmVisualManager.INSTANCE.acceptRemove(p));ClientTickEvents.END_CLIENT_TICK.register(ClientIcbmVisualManager.INSTANCE::tick);ClientPlayConnectionEvents.DISCONNECT.register((l,c)->ClientIcbmVisualManager.INSTANCE.clear());ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((c,l)->ClientIcbmVisualManager.INSTANCE.clear());registered=true;}}
