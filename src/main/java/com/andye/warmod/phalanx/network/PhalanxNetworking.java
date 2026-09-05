package com.andye.warmod.phalanx.network;

import com.andye.warmod.block.entity.PhalanxBlockEntity;
import com.andye.warmod.defence.DefenceAlly;
import com.andye.warmod.menu.PhalanxMenu;
import com.andye.warmod.phalanx.PhalanxGunStatus;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class PhalanxNetworking {
    private static boolean registered;
    private PhalanxNetworking() {}

    public static void register() {
        if (registered) return;
        PayloadTypeRegistry.clientboundPlay().register(ClientboundPhalanxShotPayload.TYPE, ClientboundPhalanxShotPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundPhalanxImpactPayload.TYPE, ClientboundPhalanxImpactPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundPhalanxStatePayload.TYPE, ClientboundPhalanxStatePayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ServerboundPhalanxOwnershipPayload.TYPE, ServerboundPhalanxOwnershipPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ServerboundPhalanxOwnershipPayload.TYPE, (payload, context) -> ownership(context.player(), payload));
        registered = true;
    }

    private static void ownership(final ServerPlayer player, final ServerboundPhalanxOwnershipPayload payload) {
        PhalanxMenu menu = menu(player, payload.menuId(), payload.centre(), payload.turretId());
        if (menu == null) return;
        PhalanxBlockEntity turret = menu.turret();
        if (payload.action() == com.andye.warmod.defence.DefenceOwnershipAction.ADD_ALLY) {
            if (!turret.ownership().isOwner(player.getUUID())) {
                denied(player);
                return;
            }
            resolveAndAddAlly(player, payload);
            return;
        }
        if (payload.action() == com.andye.warmod.defence.DefenceOwnershipAction.REMOVE_ALLY
                && !turret.ownership().isOwner(player.getUUID())) {
            denied(player);
            return;
        }
        boolean changed = switch (payload.action()) {
            case CLAIM -> turret.claimOwnership(player);
            case UNCLAIM -> turret.unclaimOwnership(player);
            case ADD_ALLY -> false;
            case REMOVE_ALLY -> {
                DefenceAlly ally = turret.allyByName(payload.playerName());
                yield ally != null && turret.removeAlly(player, ally.playerId());
            }
        };
        if (!changed) denied(player);
    }

    private static PhalanxMenu menu(final ServerPlayer player, final int id, final BlockPos centre, final UUID turretId) {
        if (!(player.containerMenu instanceof PhalanxMenu menu) || menu.containerId != id
                || !menu.centre().equals(centre) || menu.turret() == null
                || !menu.turret().turretId().equals(turretId) || !menu.stillValid(player)) return null;
        return menu;
    }

    private static void resolveAndAddAlly(final ServerPlayer actor, final ServerboundPhalanxOwnershipPayload payload) {
        var server = actor.level().getServer();
        UUID actorId = actor.getUUID();
        String name = payload.playerName() == null ? "" : payload.playerName().trim();
        if (name.isBlank()) {
            denied(actor);
            return;
        }
        CompletableFuture.supplyAsync(() -> server.services().profileResolver().fetchByName(name))
            .whenComplete((profile, error) -> server.execute(() -> {
                ServerPlayer current = server.getPlayerList().getPlayer(actorId);
                if (current == null) return;
                PhalanxMenu currentMenu = menu(current, payload.menuId(), payload.centre(), payload.turretId());
                if (error != null || profile == null || profile.isEmpty() || currentMenu == null
                        || !currentMenu.turret().ownership().isOwner(actorId)
                        || !currentMenu.turret().addAlly(current, profile.get().id(), profile.get().name())) {
                    denied(current);
                }
            }));
    }

    private static void denied(final ServerPlayer player) {
        player.sendSystemMessage(Component.literal("Ownership change was not authorized or the player was not found"));
    }

    public static void sendState(final ServerLevel level, final PhalanxBlockEntity turret) {
        ClientboundPhalanxStatePayload payload = new ClientboundPhalanxStatePayload(
            turret.turretId(), level.getGameTime(), turret.yaw(), turret.pitch(), turret.barrelSpin(),
            turret.bloom(), turret.rounds(), turret.status().ordinal(), turret.enabled(),
            turret.status() == PhalanxGunStatus.FIRING
        );
        for (var player : PlayerLookup.tracking(level, turret.getBlockPos()))
            ServerPlayNetworking.send(player, payload);
    }

    public static void send(final ServerLevel level, final CustomPacketPayload payload) {
        for (var player : PlayerLookup.level(level)) ServerPlayNetworking.send(player, payload);
    }
}
