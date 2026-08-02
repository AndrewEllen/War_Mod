package com.andye.warmod.item;

import com.andye.warmod.block.MissileSiloBlock;
import com.andye.warmod.block.ModBlocks;
import com.andye.warmod.item.component.ModDataComponents;
import com.andye.warmod.item.component.TargetCoordinates;
import com.andye.warmod.testtool.TestTargeting;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.phys.Vec3;

public final class TargetDesignatorItem extends Item {
    private static final double RANGE = 4096.0;

    public TargetDesignatorItem(final Properties properties) {
        super(properties);
    }

    @Override public InteractionResult useOn(final UseOnContext context) {
        if (context.getLevel().isClientSide() || !(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        ItemStack stack = context.getItemInHand();
        var state = context.getLevel().getBlockState(context.getClickedPos());
        if (player.isShiftKeyDown() && state.is(ModBlocks.MISSILE_SILO)) {
            var silo = MissileSiloBlock.resolve(context.getLevel(), context.getClickedPos(), state);
            TargetCoordinates target = stack.get(ModDataComponents.TARGET_COORDINATES);
            if (silo == null || target == null || !target.dimension().equals(context.getLevel().dimension())) {
                player.sendSystemMessage(Component.literal("Target designator has no valid same-dimension target"));
            } else {
                silo.setStoredTarget(target);
                player.sendSystemMessage(Component.literal("Silo target programmed: " + format(target.position())));
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        Vec3 target = context.getClickLocation().add(context.getClickedFace().getStepX() * 0.01,
            context.getClickedFace().getStepY() * 0.01, context.getClickedFace().getStepZ() * 0.01);
        store(stack, context.getLevel(), target, player);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            stack.remove(ModDataComponents.TARGET_COORDINATES);
            player.sendSystemMessage(Component.literal("Target cleared"));
            return InteractionResult.SUCCESS_SERVER;
        }
        Optional<BlockHitResult> hit = TestTargeting.findTarget(serverPlayer, RANGE);
        if (hit.isEmpty()) player.sendSystemMessage(Component.literal("No loaded target found"));
        else {
            BlockHitResult result = hit.get();
            store(stack, level, result.getLocation().add(result.getDirection().getStepX() * 0.01,
                result.getDirection().getStepY() * 0.01, result.getDirection().getStepZ() * 0.01), serverPlayer);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static void store(final ItemStack stack, final Level level, final Vec3 position, final Player player) {
        TargetCoordinates target = new TargetCoordinates(level.dimension(), position);
        if (!target.isValid() || !level.getWorldBorder().isWithinBounds(position)) {
            player.sendSystemMessage(Component.literal("Invalid target"));
            return;
        }
        stack.set(ModDataComponents.TARGET_COORDINATES, target);
        player.sendSystemMessage(Component.literal("Target acquired: " + format(position)));
    }

    @Override public boolean isFoil(final ItemStack stack) { return stack.has(ModDataComponents.TARGET_COORDINATES); }
    @Override public void appendHoverText(final ItemStack stack, final TooltipContext context,
        final TooltipDisplay display, final Consumer<Component> builder, final TooltipFlag flag) {
        TargetCoordinates target = stack.get(ModDataComponents.TARGET_COORDINATES);
        if (target == null) builder.accept(Component.literal("Target: Not set"));
        else {
            builder.accept(Component.literal("Target: " + format(target.position())));
            builder.accept(Component.literal("Dimension: " + target.dimension().identifier()));
        }
    }

    private static String format(final Vec3 position) {
        return String.format(Locale.ROOT, "%.1f, %.1f, %.1f", position.x, position.y, position.z);
    }
}
