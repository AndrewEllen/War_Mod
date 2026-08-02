package com.andye.warmod.item;

import com.andye.warmod.rocket.RocketConstants;
import com.andye.warmod.rocket.RocketLaunchService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class RocketLauncherItem extends Item {
    public RocketLauncherItem(final Properties properties) { super(properties); }

    @Override public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        if (level.isClientSide() || !(level instanceof ServerLevel server) || !(player instanceof ServerPlayer shooter))
            return InteractionResult.SUCCESS;
        ItemStack launcher = player.getItemInHand(hand);
        if (shooter.getCooldowns().isOnCooldown(launcher)) return InteractionResult.PASS;
        ItemStack ammunition = findAmmunition(shooter);
        if (ammunition.isEmpty() && !shooter.hasInfiniteMaterials()) {
            shooter.sendSystemMessage(Component.literal("Rocket Launcher requires a High-Explosive Rocket"));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!RocketLaunchService.launch(server, shooter)) {
            shooter.sendSystemMessage(Component.literal("Rocket launch failed"));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!shooter.hasInfiniteMaterials()) ammunition.shrink(1);
        shooter.getCooldowns().addCooldown(launcher, RocketConstants.COOLDOWN_TICKS);
        server.playSound(null, shooter.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH,
            SoundSource.PLAYERS, 1.0F, 0.78F);
        shooter.setDeltaMovement(shooter.getDeltaMovement().add(shooter.getLookAngle().scale(-0.12)));
        return InteractionResult.SUCCESS_SERVER;
    }

    private static ItemStack findAmmunition(final ServerPlayer player) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems())
            if (stack.is(ModItems.HE_ROCKET)) return stack;
        ItemStack offhand = player.getOffhandItem();
        return offhand.is(ModItems.HE_ROCKET) ? offhand : ItemStack.EMPTY;
    }
}
