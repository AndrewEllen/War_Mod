package com.andye.warmod.item;

import com.andye.warmod.block.MissileSiloBlock;
import com.andye.warmod.block.ModBlocks;
import com.andye.warmod.block.entity.LaunchControllerBlockEntity;
import com.andye.warmod.block.entity.MissileSiloBlockEntity;
import com.andye.warmod.item.component.LinkedLaunchController;
import com.andye.warmod.item.component.LinkedSilo;
import com.andye.warmod.item.component.ModDataComponents;
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

public final class ControllerLinkingToolItem extends Item {
    public ControllerLinkingToolItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.FAIL;
        }
        var state = context.getLevel().getBlockState(context.getClickedPos());
        if (state.is(ModBlocks.LAUNCH_CONTROLLER)
            && context.getLevel().getBlockEntity(context.getClickedPos())
                instanceof LaunchControllerBlockEntity controller) {
            return selectController(context.getItemInHand(), controller, player);
        }
        if (state.is(ModBlocks.MISSILE_SILO)) {
            MissileSiloBlockEntity silo = MissileSiloBlock.resolve(
                context.getLevel(),
                context.getClickedPos(),
                state
            );
            return silo == null
                ? InteractionResult.FAIL
                : addSilo(context.getItemInHand(), silo, player);
        }
        return InteractionResult.PASS;
    }

    public static InteractionResult selectController(
        final ItemStack stack,
        final LaunchControllerBlockEntity controller,
        final Player player
    ) {
        if (!(controller.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.FAIL;
        }
        stack.set(
            ModDataComponents.LINKED_LAUNCH_CONTROLLER,
            new LinkedLaunchController(
                level.dimension(),
                controller.getBlockPos(),
                controller.controllerId()
            )
        );
        player.sendSystemMessage(Component.literal(
            "Controller Linking Tool selected Launch Controller "
                + shortId(controller.controllerId())
        ));
        return InteractionResult.SUCCESS_SERVER;
    }

    public static InteractionResult addSilo(
        final ItemStack stack,
        final MissileSiloBlockEntity silo,
        final Player player
    ) {
        if (!(silo.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.FAIL;
        }
        LinkedLaunchController link = stack.get(
            ModDataComponents.LINKED_LAUNCH_CONTROLLER
        );
        if (link == null || !link.isValid()
            || !link.dimension().equals(level.dimension())) {
            return fail(player, "Select a same-dimension Launch Controller first");
        }
        if (!(level.getBlockEntity(link.centre())
            instanceof LaunchControllerBlockEntity controller)
            || !controller.controllerId().equals(link.controllerId())) {
            return fail(player, "Selected Launch Controller is unavailable or was replaced");
        }

        LinkedSilo siloLink = new LinkedSilo(
            level.dimension(),
            silo.getBlockPos(),
            silo.siloId()
        );
        LaunchControllerBlockEntity.LinkChange change = controller.addLink(siloLink);
        player.sendSystemMessage(Component.literal(change.message()));
        return change.linked()
            ? InteractionResult.SUCCESS_SERVER
            : InteractionResult.FAIL;
    }

    @Override
    public InteractionResult use(
        final Level level,
        final Player player,
        final InteractionHand hand
    ) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        player.getItemInHand(hand).remove(
            ModDataComponents.LINKED_LAUNCH_CONTROLLER
        );
        player.sendSystemMessage(Component.literal("Launch Controller selection cleared"));
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void appendHoverText(
        final ItemStack stack,
        final TooltipContext context,
        final TooltipDisplay display,
        final Consumer<Component> builder,
        final TooltipFlag flag
    ) {
        LinkedLaunchController link = stack.get(
            ModDataComponents.LINKED_LAUNCH_CONTROLLER
        );
        if (link == null) {
            builder.accept(Component.literal("Controller: Not selected"));
            return;
        }
        builder.accept(Component.literal("Controller: " + shortId(link.controllerId())));
        builder.accept(Component.literal(
            "Position: " + link.centre().getX() + ", "
                + link.centre().getY() + ", " + link.centre().getZ()
        ));
        builder.accept(Component.literal(
            "Dimension: " + link.dimension().identifier()
        ));
    }

    private static InteractionResult fail(final Player player, final String message) {
        player.sendSystemMessage(Component.literal(message));
        return InteractionResult.FAIL;
    }

    private static String shortId(final java.util.UUID id) {
        return id.toString().substring(0, 8);
    }
}
