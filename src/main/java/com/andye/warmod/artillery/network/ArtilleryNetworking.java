package com.andye.warmod.artillery.network;

import com.andye.warmod.artillery.ArtilleryConstants;
import com.andye.warmod.block.entity.ArtilleryCannonBlockEntity;
import com.andye.warmod.item.component.TargetCoordinates;
import com.andye.warmod.menu.ArtilleryCannonMenu;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class ArtilleryNetworking {
    private static boolean registered;
    private ArtilleryNetworking() { }
    public static void register() { if (registered) return; PayloadTypeRegistry.serverboundPlay().register(ServerboundArtilleryTargetPayload.TYPE, ServerboundArtilleryTargetPayload.STREAM_CODEC); PayloadTypeRegistry.serverboundPlay().register(ServerboundArtilleryFirePayload.TYPE, ServerboundArtilleryFirePayload.STREAM_CODEC); ServerPlayNetworking.registerGlobalReceiver(ServerboundArtilleryTargetPayload.TYPE, (payload, context) -> target(context.player(), payload)); ServerPlayNetworking.registerGlobalReceiver(ServerboundArtilleryFirePayload.TYPE, (payload, context) -> fire(context.player(), payload)); registered = true; }
    private static ArtilleryCannonMenu menu(final ServerPlayer player, final int id, final BlockPos position) { return player.containerMenu instanceof ArtilleryCannonMenu menu && menu.containerId == id && menu.position().equals(position) && menu.stillValid(player) ? menu : null; }
    private static void target(final ServerPlayer player, final ServerboundArtilleryTargetPayload payload) { ArtilleryCannonMenu menu = menu(player, payload.menuId(), payload.position()); if (menu == null || menu.cannon() == null) return; Vec3 target = new Vec3(payload.x(), payload.y(), payload.z()); if (!target.isFinite() || !player.level().getWorldBorder().isWithinBounds(target) || player.level().isOutsideBuildHeight(BlockPos.containing(target)) || Vec3.atCenterOf(payload.position()).distanceTo(target) > ArtilleryConstants.MAX_RANGE_BLOCKS) { player.sendSystemMessage(Component.literal("Invalid artillery target")); return; } menu.cannon().setTarget(new TargetCoordinates(player.level().dimension(), target)); }
    private static void fire(final ServerPlayer player, final ServerboundArtilleryFirePayload payload) { ArtilleryCannonMenu menu = menu(player, payload.menuId(), payload.position()); if (menu == null || menu.cannon() == null) return; if (!menu.cannon().fire(player.level(), player)) player.sendSystemMessage(Component.literal(menu.cannon().lastError())); }
}
