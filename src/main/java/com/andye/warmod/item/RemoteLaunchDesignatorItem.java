package com.andye.warmod.item;

import com.andye.warmod.block.MissileSiloBlock;
import com.andye.warmod.block.ModBlocks;
import com.andye.warmod.block.entity.MissileSiloBlockEntity;
import com.andye.warmod.item.component.LinkedSilo;
import com.andye.warmod.item.component.ModDataComponents;
import com.andye.warmod.item.component.TargetCoordinates;
import com.andye.warmod.silo.MissileSiloLaunchService;
import com.andye.warmod.silo.MissileSiloLaunchTrigger;
import com.andye.warmod.testtool.TestTargeting;
import java.util.Locale;
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
import net.minecraft.world.phys.Vec3;

public final class RemoteLaunchDesignatorItem extends Item {
    private static final double RANGE = 4096.0;

    public RemoteLaunchDesignatorItem(final Properties properties) { super(properties); }

    public static void link(final ItemStack stack, final MissileSiloBlockEntity silo, final Player player) {
        stack.set(ModDataComponents.LINKED_SILO, new LinkedSilo(silo.dimension(), silo.getBlockPos(), silo.siloId()));
        player.sendSystemMessage(Component.literal("Remote designator linked to silo " + shortId(silo.siloId())));
    }

    @Override public InteractionResult useOn(final UseOnContext context) {
        if (context.getLevel().isClientSide() || !(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        var state = context.getLevel().getBlockState(context.getClickedPos());
        if (player.isShiftKeyDown() && state.is(ModBlocks.MISSILE_SILO)) {
            MissileSiloBlockEntity silo = MissileSiloBlock.resolve(context.getLevel(), context.getClickedPos(), state);
            if (silo != null) link(context.getItemInHand(), silo, player);
            return InteractionResult.SUCCESS_SERVER;
        }
        Vec3 target = context.getClickLocation().add(context.getClickedFace().getStepX() * 0.01,
            context.getClickedFace().getStepY() * 0.01, context.getClickedFace().getStepZ() * 0.01);
        launch(player, context.getItemInHand(), target);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            stack.remove(ModDataComponents.LINKED_SILO);
            player.sendSystemMessage(Component.literal("Remote link cleared"));
            return InteractionResult.SUCCESS_SERVER;
        }
        var hit = TestTargeting.findTarget(serverPlayer, RANGE);
        if (hit.isEmpty()) player.sendSystemMessage(Component.literal("No loaded target found"));
        else launch(serverPlayer, stack, hit.get().getLocation());
        return InteractionResult.SUCCESS_SERVER;
    }

    private static void launch(final ServerPlayer player, final ItemStack stack, final Vec3 position) {
        LinkedSilo link = stack.get(ModDataComponents.LINKED_SILO);
        if (link == null || !link.isValid() || !link.dimension().equals(player.level().dimension())) {
            player.sendSystemMessage(Component.literal("Remote designator is not linked to a valid same-dimension silo"));
            return;
        }
        ServerLevel level = player.level();
        var state = level.getBlockState(link.centre());
        MissileSiloBlockEntity silo = state.is(ModBlocks.MISSILE_SILO)
            ? MissileSiloBlock.resolve(level, link.centre(), state) : null;
        if (silo == null || !silo.siloId().equals(link.siloId())) {
            player.sendSystemMessage(Component.literal("Linked silo no longer exists"));
            return;
        }
        TargetCoordinates target = new TargetCoordinates(level.dimension(), position);
        var result = MissileSiloLaunchService.requestLaunch(level, silo, MissileSiloLaunchTrigger.DIRECT_DESIGNATOR,
            player.getUUID(), player.getGameProfile().name(), target);
        player.sendSystemMessage(Component.literal(result.message()));
    }

    @Override public boolean isFoil(final ItemStack stack) { return stack.has(ModDataComponents.LINKED_SILO); }
    @Override public void appendHoverText(final ItemStack stack, final TooltipContext context,
        final TooltipDisplay display, final Consumer<Component> builder, final TooltipFlag flag) {
        LinkedSilo link = stack.get(ModDataComponents.LINKED_SILO);
        if (link == null) builder.accept(Component.literal("Linked Silo: None"));
        else {
            builder.accept(Component.literal("Silo: " + shortId(link.siloId())));
            builder.accept(Component.literal("Position: " + link.centre().getX() + ", " + link.centre().getY() + ", " + link.centre().getZ()));
            builder.accept(Component.literal("Dimension: " + link.dimension().identifier()));
        }
    }

    private static String shortId(final java.util.UUID id) { return id.toString().substring(0, 8); }
}
