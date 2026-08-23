package com.andye.warmod.item;

import com.andye.warmod.firearm.FirearmBulletManager;
import com.andye.warmod.firearm.FirearmType;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
        if (type.automatic() || type.scoped()) {
            player.startUsingItem(hand);
            if (!level.isClientSide() && type.automatic() && player instanceof ServerPlayer shooter)
                FirearmBulletManager.fire((ServerLevel) level, shooter, type);
            return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
        }
        if (!level.isClientSide() && level instanceof ServerLevel server
            && player instanceof ServerPlayer shooter)
            FirearmBulletManager.fire(server, shooter, type);
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    @Override public void onUseTick(final Level level, final LivingEntity entity,
        final ItemStack stack, final int remainingTime) {
        if (!type.automatic() || level.isClientSide()
            || !(level instanceof ServerLevel server) || !(entity instanceof ServerPlayer shooter)) return;
        int used = getUseDuration(stack, entity) - remainingTime;
        if (used > 0 && used % type.intervalTicks() == 0)
            FirearmBulletManager.fire(server, shooter, type);
    }

    @Override public boolean releaseUsing(final ItemStack stack, final Level level,
        final LivingEntity entity, final int remainingTime) {
        if (type.scoped() && !level.isClientSide() && level instanceof ServerLevel server
            && entity instanceof ServerPlayer shooter) {
            int held = getUseDuration(stack, entity) - remainingTime;
            if (held >= 5) FirearmBulletManager.fire(server, shooter, type);
        }
        return type.scoped();
    }

    @Override public int getUseDuration(final ItemStack stack, final LivingEntity user) {
        return type.automatic() || type.scoped() ? 72_000 : 0;
    }
    @Override public ItemUseAnimation getUseAnimation(final ItemStack stack) {
        return type.scoped() ? ItemUseAnimation.SPYGLASS
            : type.automatic() ? ItemUseAnimation.CROSSBOW : ItemUseAnimation.NONE;
    }
    @Override public void appendHoverText(final ItemStack stack, final TooltipContext context,
        final TooltipDisplay display, final Consumer<Component> tooltip,
        final TooltipFlag flag) {
        tooltip.accept(Component.literal(type.scoped()
            ? "Hold to scope; release to fire" : type.automatic()
                ? "Hold to fire automatically" : "Use to fire"));
        tooltip.accept(Component.literal("Ammo: " + type.ammunition().getName(
            new ItemStack(type.ammunition())).getString()));
    }
}
