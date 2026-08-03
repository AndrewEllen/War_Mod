package com.andye.warmod.radar.display.network;

import com.andye.warmod.block.entity.RadarDisplayPanelBlockEntity;
import com.andye.warmod.radar.display.RadarDisplaySnapshot;
import com.andye.warmod.radar.display.RadarDisplayStateService;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class RadarDisplayNetworking {
    private static boolean registered;

    private RadarDisplayNetworking() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        PayloadTypeRegistry.clientboundPlay().register(
            ClientboundRadarDisplayStatePayload.TYPE,
            ClientboundRadarDisplayStatePayload.STREAM_CODEC
        );

        PayloadTypeRegistry.clientboundPlay().register(
            ClientboundRadarDisplayClearPayload.TYPE,
            ClientboundRadarDisplayClearPayload.STREAM_CODEC
        );

        registered = true;
    }

    public static void sendState(
        final RadarDisplayPanelBlockEntity display
    ) {
        if (!(display.getLevel() instanceof ServerLevel level)
            || display.displayId() == null
            || !display.controllerPanel()) {
            return;
        }

        RadarDisplaySnapshot snapshot =
            RadarDisplayStateService.snapshot(level, display);

        ClientboundRadarDisplayStatePayload payload =
            new ClientboundRadarDisplayStatePayload(snapshot);

        for (ServerPlayer player
            : PlayerLookup.tracking(level, display.getBlockPos())) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void clear(
        final ServerLevel level,
        final BlockPos controller,
        final UUID displayId
    ) {
        ClientboundRadarDisplayClearPayload payload =
            new ClientboundRadarDisplayClearPayload(
                level.dimension().identifier(),
                controller,
                displayId
            );

        for (ServerPlayer player
            : PlayerLookup.tracking(level, controller)) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
