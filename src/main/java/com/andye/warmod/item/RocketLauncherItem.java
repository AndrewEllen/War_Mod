package com.andye.warmod.item;

import com.andye.warmod.acoustics.ModSoundEvents;
import com.andye.warmod.item.component.ModDataComponents;
import com.andye.warmod.rocket.RocketLaunchService;
import com.andye.warmod.rocket.RocketPayloadType;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** Shoulder launcher for dedicated HE rockets. Strategic missiles use a silo. */
public final class RocketLauncherItem extends Item {
    public RocketLauncherItem(final Properties properties) { super(properties); }

    @Override
    public InteractionResult use(final Level level, final Player player,
        final InteractionHand hand) {
        // Right-click is deliberately aim-only. The attack key is routed through
        // RocketLauncherNetworking while this held-use state is active.
        player.startUsingItem(hand);
        // Consumes right-click without vanilla's success/punch arm swing.
        return InteractionResult.CONSUME;
    }

    /** Server-authoritative held-aim firing path used by the client attack key. */
    public static boolean fireHeld(final ServerPlayer shooter) {
        ItemStack launcher = shooter.getMainHandItem();
        if (!(launcher.getItem() instanceof RocketLauncherItem)
            || !shooter.isUsingItem()
            || shooter.getUseItem() != launcher
            || shooter.getCooldowns().isOnCooldown(launcher)) return false;
        // Old stacks may retain the previous mode component; it can never select a payload.
        launcher.remove(ModDataComponents.ROCKET_LAUNCHER_MODE);
        ItemStack ammunition = findAmmunition(shooter);
        if (ammunition.isEmpty() && !shooter.hasInfiniteMaterials()) {
            shooter.sendSystemMessage(Component.literal("Rocket Launcher requires HE Rockets"));
            return false;
        }
        if (!(shooter.level() instanceof ServerLevel server)
            || !RocketLaunchService.launch(server, shooter, RocketPayloadType.HE)) {
            shooter.sendSystemMessage(Component.literal("Rocket launch failed"));
            return false;
        }
        if (!shooter.hasInfiniteMaterials()) ammunition.shrink(1);
        shooter.getCooldowns().addCooldown(launcher, RocketPayloadType.HE.cooldown());
        server.playSound(null, shooter.blockPosition(), ModSoundEvents.MISSILE_ENGINE_IGNITION_NEAR,
            SoundSource.PLAYERS, 1.0F, 1.05F);
        shooter.setDeltaMovement(shooter.getDeltaMovement().add(shooter.getLookAngle().scale(-0.12)));
        return true;
    }

    private static ItemStack findAmmunition(final ServerPlayer player) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(ModItems.HE_ROCKET)) return stack;
        }
        ItemStack offhand = player.getOffhandItem();
        return offhand.is(ModItems.HE_ROCKET) ? offhand : ItemStack.EMPTY;
    }

    @Override
    public int getUseDuration(final ItemStack stack, final net.minecraft.world.entity.LivingEntity user) {
        return 72_000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(final ItemStack stack) {
        return ItemUseAnimation.CROSSBOW;
    }

    @Override
    public boolean releaseUsing(final ItemStack stack, final Level level,
        final net.minecraft.world.entity.LivingEntity entity, final int remainingTime) {
        return true;
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context,
        final TooltipDisplay display, final Consumer<Component> builder, final TooltipFlag flag) {
        builder.accept(Component.literal("Ammunition: HE Rockets"));
        builder.accept(Component.literal("Hold right-click to aim; left-click to fire"));
    }
}
