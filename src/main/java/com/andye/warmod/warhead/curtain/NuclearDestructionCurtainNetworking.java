package com.andye.warmod.warhead.curtain;

import com.andye.warmod.warhead.WarheadConstants;
import com.andye.warmod.warhead.curtain.network.ClientboundNuclearCurtainPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Networking boundary for the standalone packed destruction-curtain renderer. */
public final class NuclearDestructionCurtainNetworking {
    private static boolean registered;

    private NuclearDestructionCurtainNetworking() { }

    public static void registerPayloadTypes() {
        if (registered) return;
        PayloadTypeRegistry.clientboundPlay().register(ClientboundNuclearCurtainPayload.TYPE,
            ClientboundNuclearCurtainPayload.STREAM_CODEC);
        registered = true;
    }

    static int send(final ServerLevel level, final ClientboundNuclearCurtainPayload payload,
        final Vec3 center) {
        if (level == null || center == null || !center.isFinite() || !payload.isWellFormed()) return 0;
        double rangeSquared = WarheadConstants.VISUAL_RANGE_BLOCKS
            * WarheadConstants.VISUAL_RANGE_BLOCKS;
        int recipients = 0;
        for (ServerPlayer player : PlayerLookup.level(level)) {
            if (player.distanceToSqr(center) <= rangeSquared) {
                ServerPlayNetworking.send(player, payload);
                recipients++;
            }
        }
        return recipients;
    }
}
