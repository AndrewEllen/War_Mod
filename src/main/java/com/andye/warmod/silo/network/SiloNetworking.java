package com.andye.warmod.silo.network;

import com.andye.warmod.block.entity.MissileSiloBlockEntity;
import com.andye.warmod.defence.DefenceAlly;
import com.andye.warmod.item.TargetDesignatorItem;
import com.andye.warmod.item.component.ModDataComponents;
import com.andye.warmod.item.component.TargetCoordinates;
import com.andye.warmod.menu.MissileSiloMenu;
import com.andye.warmod.silo.MissileSiloLaunchService;
import com.andye.warmod.silo.MissileSiloLaunchTrigger;
import com.andye.warmod.icbm.IcbmConstants;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class SiloNetworking {
    private static boolean registered;
    private SiloNetworking() {}

    public static void register() {
        if (registered) return;
        PayloadTypeRegistry.serverboundPlay().register(ServerboundSiloSetTargetPayload.TYPE, ServerboundSiloSetTargetPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ServerboundSiloClearTargetPayload.TYPE, ServerboundSiloClearTargetPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ServerboundSiloUseHeldDesignatorPayload.TYPE, ServerboundSiloUseHeldDesignatorPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ServerboundSiloLaunchPayload.TYPE, ServerboundSiloLaunchPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ServerboundSiloOwnershipPayload.TYPE, ServerboundSiloOwnershipPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ServerboundSiloSetTargetPayload.TYPE, (payload, context) -> setTarget(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(ServerboundSiloClearTargetPayload.TYPE, (payload, context) -> {
            MissileSiloMenu menu = menu(context.player(), payload.menuId(), payload.centre(), payload.siloId());
            if (menu != null) menu.silo().setStoredTarget(null);
        });
        ServerPlayNetworking.registerGlobalReceiver(ServerboundSiloUseHeldDesignatorPayload.TYPE, (payload, context) -> useDesignator(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(ServerboundSiloLaunchPayload.TYPE, (payload, context) -> launch(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(ServerboundSiloOwnershipPayload.TYPE, (payload, context) -> ownership(context.player(), payload));
        registered = true;
    }

    private static MissileSiloMenu menu(final ServerPlayer player, final int id, final BlockPos centre, final UUID siloId) {
        if (!(player.containerMenu instanceof MissileSiloMenu menu) || menu.containerId != id
                || !menu.centre().equals(centre) || !menu.siloId().equals(siloId)
                || menu.silo() == null || !menu.stillValid(player)) return null;
        return menu;
    }

    private static void ownership(final ServerPlayer player, final ServerboundSiloOwnershipPayload payload) {
        MissileSiloMenu menu = menu(player, payload.menuId(), payload.centre(), payload.siloId());
        if (menu == null) return;
        MissileSiloBlockEntity silo = menu.silo();
        if (payload.action() == com.andye.warmod.defence.DefenceOwnershipAction.ADD_ALLY) {
            if (!silo.ownership().isOwner(player.getUUID())) {
                denied(player);
                return;
            }
            resolveAndAddAlly(player, payload);
            return;
        }
        if (payload.action() == com.andye.warmod.defence.DefenceOwnershipAction.REMOVE_ALLY
                && !silo.ownership().isOwner(player.getUUID())) {
            denied(player);
            return;
        }
        boolean changed = switch (payload.action()) {
            case CLAIM -> silo.claimOwnership(player);
            case UNCLAIM -> silo.unclaimOwnership(player);
            case ADD_ALLY -> false;
            case REMOVE_ALLY -> {
                DefenceAlly ally = silo.allyByName(payload.playerName());
                yield ally != null && silo.removeAlly(player, ally.playerId());
            }
        };
        if (!changed) denied(player);
    }

    private static void resolveAndAddAlly(final ServerPlayer actor, final ServerboundSiloOwnershipPayload payload) {
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
                MissileSiloMenu currentMenu = menu(current, payload.menuId(), payload.centre(), payload.siloId());
                if (error != null || profile == null || profile.isEmpty() || currentMenu == null
                        || !currentMenu.silo().ownership().isOwner(actorId)
                        || !currentMenu.silo().addAlly(current, profile.get().id(), profile.get().name())) {
                    denied(current);
                }
            }));
    }

    private static void denied(final ServerPlayer player) {
        player.sendSystemMessage(Component.literal("Ownership change was not authorized or the player was not found"));
    }

    private static void setTarget(final ServerPlayer player, final ServerboundSiloSetTargetPayload payload) {
        MissileSiloMenu menu = menu(player, payload.menuId(), payload.centre(), payload.siloId());
        if (menu == null) return;
        Vec3 target = new Vec3(payload.x(), payload.y(), payload.z());
        if (!target.isFinite() || !player.level().getWorldBorder().isWithinBounds(target)
                || player.level().isOutsideBuildHeight(BlockPos.containing(target))
                || Vec3.atCenterOf(menu.centre()).distanceTo(target) > IcbmConstants.MAXIMUM_STRATEGIC_RANGE_BLOCKS) {
            player.sendSystemMessage(Component.literal("Invalid silo target"));
            return;
        }
        menu.silo().setStoredTarget(new TargetCoordinates(player.level().dimension(), target));
    }

    private static void useDesignator(final ServerPlayer player, final ServerboundSiloUseHeldDesignatorPayload payload) {
        MissileSiloMenu menu = menu(player, payload.menuId(), payload.centre(), payload.siloId());
        if (menu == null) return;
        for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (stack.getItem() instanceof TargetDesignatorItem) {
                TargetCoordinates target = stack.get(ModDataComponents.TARGET_COORDINATES);
                if (target != null && target.isValid() && target.dimension().equals(player.level().dimension())) {
                    menu.silo().setStoredTarget(target);
                    return;
                }
            }
        }
        player.sendSystemMessage(Component.literal("Hold a programmed same-dimension Target Designator"));
    }

    private static void launch(final ServerPlayer player, final ServerboundSiloLaunchPayload payload) {
        MissileSiloMenu menu = menu(player, payload.menuId(), payload.centre(), payload.siloId());
        if (menu == null) return;
        var result = MissileSiloLaunchService.requestLaunch(player.level(), menu.silo(), MissileSiloLaunchTrigger.DIRECT_UI, player.getUUID(), player.getGameProfile().name(), menu.silo().storedTarget());
        player.sendSystemMessage(Component.literal(result.message()));
    }
}
