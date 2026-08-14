package com.andye.warmod.item;

import com.andye.warmod.fire.FireIntensity;
import com.andye.warmod.fire.FireSimulationManager;
import com.andye.warmod.fire.FireSurfaceAnchor;
import com.andye.warmod.fire.network.FireDebugNetworking;
import com.andye.warmod.item.component.FireDebugConfig;
import com.andye.warmod.item.component.ModDataComponents;
import com.andye.warmod.testtool.TestTargeting;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

public final class FireDebugStickItem extends Item {
    private static final double RANGE = 256.0;

    public FireDebugStickItem(final Properties properties) { super(properties); }

    @Override public InteractionResult useOn(final UseOnContext context) {
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (!(context.getLevel() instanceof ServerLevel level)
            || !(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.PASS;
        ItemStack stack = context.getItemInHand();
        if (player.isShiftKeyDown()) {
            FireDebugNetworking.open(player, context.getHand(), stack);
            return InteractionResult.SUCCESS_SERVER;
        }
        FireSurfaceAnchor anchor = FireSurfaceAnchor.fromHit(context.getClickedPos(),
            context.getClickedFace(), context.getClickLocation());
        return ignite(level, player, stack, anchor);
    }

    @Override public InteractionResult use(final Level level, final Player player,
        final InteractionHand hand) {
        if (level.isClientSide() || !(level instanceof ServerLevel server)
            || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            FireDebugNetworking.open(serverPlayer, hand, stack);
            return InteractionResult.SUCCESS_SERVER;
        }
        Optional<BlockHitResult> hit = TestTargeting.findTarget(serverPlayer, RANGE);
        if (hit.isEmpty()) {
            serverPlayer.sendOverlayMessage(Component.literal(
                "No loaded surface found within 256 blocks"));
            return InteractionResult.SUCCESS_SERVER;
        }
        BlockHitResult result = hit.get();
        return ignite(server, serverPlayer, stack, FireSurfaceAnchor.fromHit(
            result.getBlockPos(), result.getDirection(), result.getLocation()));
    }

    private static InteractionResult ignite(final ServerLevel level,
        final ServerPlayer player, final ItemStack stack, final FireSurfaceAnchor anchor) {
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.PASS;
        FireDebugConfig config = config(stack);
        int placed = FireSimulationManager.igniteSurface(level, anchor, config,
            level.getRandom().nextLong() ^ anchor.host().asLong());
        player.sendOverlayMessage(Component.literal(placed > 0
            ? "Custom fire placed on " + placed + " surface" + (placed == 1 ? "" : "s")
                + " | " + config.summary()
            : "Fire could not attach to this exposed surface"));
        player.getCooldowns().addCooldown(stack, 3);
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Reads the new slider config while preserving old enum-configured sticks. */
    public static FireDebugConfig config(final ItemStack stack) {
        FireDebugConfig configured = stack.get(ModDataComponents.FIRE_DEBUG_CONFIG);
        if (configured != null) return configured;
        FireIntensity legacy = stack.get(ModDataComponents.FIRE_DEBUG_INTENSITY);
        return legacy == null ? FireDebugConfig.DEFAULT
            : new FireDebugConfig(legacy.heat(), 1);
    }

    @Override public void appendHoverText(final ItemStack stack, final TooltipContext context,
        final TooltipDisplay display, final Consumer<Component> tooltip, final TooltipFlag flag) {
        tooltip.accept(Component.literal("Places custom fire at the exact targeted surface"));
        tooltip.accept(Component.literal("Crouch-use: intensity and size menu"));
        tooltip.accept(Component.literal(config(stack).summary()));
    }
}
