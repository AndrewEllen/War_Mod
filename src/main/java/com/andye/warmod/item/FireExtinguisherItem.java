package com.andye.warmod.item;

import com.andye.warmod.fire.FireSuppressionService;
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
import net.minecraft.world.level.Level;

public final class FireExtinguisherItem extends Item {
    public FireExtinguisherItem(final Properties properties) { super(properties); }

    @Override public InteractionResult use(final Level level, final Player player,
        final InteractionHand hand) {
        if (level.isClientSide() || !(level instanceof ServerLevel server)
            || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        if (serverPlayer.getCooldowns().isOnCooldown(stack)) return InteractionResult.PASS;
        FireSuppressionService.sprayExtinguisher(server, serverPlayer);
        player.getCooldowns().addCooldown(stack, 4);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override public void appendHoverText(final ItemStack stack, final TooltipContext context,
        final TooltipDisplay display, final Consumer<Component> tooltip, final TooltipFlag flag) {
        tooltip.accept(Component.literal("Short, wide high-strength suppression cloud"));
    }
}
