package com.andye.warmod.item;

import com.andye.warmod.fire.FireFuelProfile;
import com.andye.warmod.fire.FireIntensity;
import com.andye.warmod.fire.FireSimulationManager;
import com.andye.warmod.item.component.ModDataComponents;
import com.andye.warmod.testtool.TestTargeting;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
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
        if (player.isShiftKeyDown()) return cycle(player, stack);
        BlockPos clicked = context.getClickedPos();
        BlockPos anchor = FireFuelProfile.of(level.getBlockState(clicked)).flammable()
            ? clicked : clicked.relative(context.getClickedFace());
        return ignite(level, player, stack, anchor);
    }

    @Override public InteractionResult use(final Level level, final Player player,
        final InteractionHand hand) {
        if (level.isClientSide() || !(level instanceof ServerLevel server)
            || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) return cycle(serverPlayer, stack);
        Optional<BlockHitResult> hit = TestTargeting.findTarget(serverPlayer, RANGE);
        if (hit.isEmpty()) {
            serverPlayer.sendOverlayMessage(Component.literal("No loaded surface found within 256 blocks"));
            return InteractionResult.SUCCESS_SERVER;
        }
        BlockPos clicked = hit.get().getBlockPos();
        BlockPos anchor = FireFuelProfile.of(server.getBlockState(clicked)).flammable()
            ? clicked : clicked.relative(hit.get().getDirection());
        return ignite(server, serverPlayer, stack, anchor);
    }

    private static InteractionResult ignite(final ServerLevel level,
        final ServerPlayer player, final ItemStack stack, final BlockPos position) {
        FireIntensity intensity = intensity(stack);
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.PASS;
        boolean ignited = FireSimulationManager.ignite(level, position, intensity,
            level.getRandom().nextLong() ^ position.asLong(), true);
        player.sendOverlayMessage(Component.literal(ignited
            ? "Custom fire started: " + intensity.displayName()
            : "Fire could not start here (water, wet surface, unloaded area, or capacity reached)"));
        player.getCooldowns().addCooldown(stack, 2);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static InteractionResult cycle(final ServerPlayer player, final ItemStack stack) {
        FireIntensity next = intensity(stack).next();
        stack.set(ModDataComponents.FIRE_DEBUG_INTENSITY, next);
        player.sendOverlayMessage(Component.literal("Fire Debug Stick: " + next.displayName()));
        return InteractionResult.SUCCESS_SERVER;
    }

    private static FireIntensity intensity(final ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.FIRE_DEBUG_INTENSITY, FireIntensity.MEDIUM);
    }

    @Override public void appendHoverText(final ItemStack stack, final TooltipContext context,
        final TooltipDisplay display, final Consumer<Component> tooltip, final TooltipFlag flag) {
        tooltip.accept(Component.literal("Starts server-authoritative custom fire"));
        tooltip.accept(Component.literal("Crouch-use to change strength"));
        tooltip.accept(Component.literal("Strength: " + intensity(stack).displayName()));
    }
}
