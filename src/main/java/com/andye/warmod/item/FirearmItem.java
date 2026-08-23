package com.andye.warmod.item;

import com.andye.warmod.firearm.FirearmType;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

public final class FirearmItem extends Item {
    private final FirearmType type;
    public FirearmItem(final Properties properties, final FirearmType type) {
        super(properties); this.type = type;
    }
    public FirearmType firearmType() { return type; }

    @Override public InteractionResult use(final Level level, final Player player,
        final InteractionHand hand) {
        player.startUsingItem(hand);
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    @Override public void onUseTick(final Level level, final LivingEntity entity,
        final ItemStack stack, final int remainingTime) {
        // Right-click is aim-only. Firing is driven by the attack-key packet.
    }

    @Override public boolean releaseUsing(final ItemStack stack, final Level level,
        final LivingEntity entity, final int remainingTime) {
        return true;
    }

    @Override public int getUseDuration(final ItemStack stack, final LivingEntity user) {
        return 72_000;
    }
    @Override public ItemUseAnimation getUseAnimation(final ItemStack stack) {
        return type.scoped() ? ItemUseAnimation.SPYGLASS : ItemUseAnimation.CROSSBOW;
    }
    @Override public void appendHoverText(final ItemStack stack, final TooltipContext context,
        final TooltipDisplay display, final Consumer<Component> tooltip,
        final TooltipFlag flag) {
        tooltip.accept(Component.literal("Left-click to fire; right-click to "
            + (type.scoped() ? "use scope" : "aim down sights")));
        tooltip.accept(Component.literal("Magazine: " + type.magazineCapacity()
            + " rounds (" + type.ammunition().getName(
                new ItemStack(type.ammunition())).getString() + ")"));
    }
}
