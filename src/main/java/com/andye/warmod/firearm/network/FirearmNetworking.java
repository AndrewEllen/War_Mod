package com.andye.warmod.firearm.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class FirearmNetworking {
    private static final double VISUAL_RANGE_SQUARED = 3_072.0 * 3_072.0;
    private static boolean registered;
    private FirearmNetworking() { }
    public static void register() {
        if (registered) return;
        PayloadTypeRegistry.clientboundPlay().register(ClientboundFirearmShotPayload.TYPE,
            ClientboundFirearmShotPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundFirearmImpactPayload.TYPE,
            ClientboundFirearmImpactPayload.STREAM_CODEC);
        registered = true;
    }
    public static void send(final ServerLevel level, final Vec3 position,
        final CustomPacketPayload payload) {
        for (var player : PlayerLookup.level(level)) {
            if (player.distanceToSqr(position) <= VISUAL_RANGE_SQUARED)
                ServerPlayNetworking.send(player, payload);
        }
    }
}
