package com.andye.warmod.artillery.network;

import com.andye.warmod.artillery.ArtilleryConstants;
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
    private ArtilleryNetworking() {
    }

    public static void register() {
        if (registered) return;
        PayloadTypeRegistry.serverboundPlay().register(ServerboundArtillerySetTargetPayload.TYPE,
            ServerboundArtillerySetTargetPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ServerboundArtilleryClearTargetPayload.TYPE,
            ServerboundArtilleryClearTargetPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ServerboundArtilleryFirePayload.TYPE,
            ServerboundArtilleryFirePayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ServerboundArtillerySetTargetPayload.TYPE,
            (payload, context) -> setTarget(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(ServerboundArtilleryClearTargetPayload.TYPE,
            (payload, context) -> clearTarget(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(ServerboundArtilleryFirePayload.TYPE,
            (payload, context) -> fire(context.player(), payload));
        registered = true;
    }

    private static ArtilleryCannonMenu menu(final ServerPlayer player, final int menuId,
        final BlockPos cannonPos) {
        if (!(player.containerMenu instanceof ArtilleryCannonMenu menu)
            || menu.containerId != menuId || !menu.cannonPos().equals(cannonPos)
            || menu.cannon() == null || !menu.stillValid(player)) return null;
        return menu;
    }

    private static void setTarget(final ServerPlayer player,
        final ServerboundArtillerySetTargetPayload payload) {
        ArtilleryCannonMenu menu = menu(player, payload.menuId(), payload.cannonPos());
        if (menu == null) return;
        Vec3 target = new Vec3(payload.x(), payload.y(), payload.z());
        Vec3 origin = Vec3.atCenterOf(payload.cannonPos());
        double horizontal = Math.hypot(target.x - origin.x, target.z - origin.z);
        if (!target.isFinite() || !player.level().getWorldBorder().isWithinBounds(target)
            || player.level().isOutsideBuildHeight(BlockPos.containing(target))
            || horizontal > ArtilleryConstants.MAXIMUM_RANGE_BLOCKS) {
            player.sendSystemMessage(Component.literal("Invalid artillery target"));
            return;
        }
        menu.cannon().setStoredTarget(new TargetCoordinates(player.level().dimension(), target));
    }

    private static void clearTarget(final ServerPlayer player,
        final ServerboundArtilleryClearTargetPayload payload) {
        ArtilleryCannonMenu menu = menu(player, payload.menuId(), payload.cannonPos());
        if (menu != null) menu.cannon().setStoredTarget(null);
    }

    private static void fire(final ServerPlayer player,
        final ServerboundArtilleryFirePayload payload) {
        ArtilleryCannonMenu menu = menu(player, payload.menuId(), payload.cannonPos());
        if (menu == null) return;
        var result = menu.cannon().fire(player.level(), player);
        player.sendSystemMessage(Component.literal(result.message()));
    }
}
