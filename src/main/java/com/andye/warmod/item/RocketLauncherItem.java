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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** Shoulder launcher for dedicated HE rockets. Strategic missiles use a silo. */
public final class RocketLauncherItem extends Item {
    public RocketLauncherItem(final Properties properties) { super(properties); }

    @Override
    public InteractionResult use(final Level level, final Player player,
        final InteractionHand hand) {
        ItemStack launcher = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server)
            || !(player instanceof ServerPlayer shooter)) return InteractionResult.SUCCESS;
        // Old stacks may retain the previous mode component; it can never select a payload.
        launcher.remove(ModDataComponents.ROCKET_LAUNCHER_MODE);
        if (shooter.getCooldowns().isOnCooldown(launcher)) return InteractionResult.PASS;
        ItemStack ammunition = findAmmunition(shooter);
        if (ammunition.isEmpty() && !shooter.hasInfiniteMaterials()) {
            shooter.sendSystemMessage(Component.literal("Rocket Launcher requires HE Rockets"));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!RocketLaunchService.launch(server, shooter, RocketPayloadType.HE)) {
            shooter.sendSystemMessage(Component.literal("Rocket launch failed"));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!shooter.hasInfiniteMaterials()) ammunition.shrink(1);
        shooter.getCooldowns().addCooldown(launcher, RocketPayloadType.HE.cooldown());
        server.playSound(null, shooter.blockPosition(), ModSoundEvents.MISSILE_ENGINE_IGNITION_NEAR,
            SoundSource.PLAYERS, 1.0F, 1.05F);
        shooter.setDeltaMovement(shooter.getDeltaMovement().add(shooter.getLookAngle().scale(-0.12)));
        shooter.swing(hand);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static ItemStack findAmmunition(final ServerPlayer player) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(ModItems.HE_ROCKET)) return stack;
        }
        ItemStack offhand = player.getOffhandItem();
        return offhand.is(ModItems.HE_ROCKET) ? offhand : ItemStack.EMPTY;
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context,
        final TooltipDisplay display, final Consumer<Component> builder, final TooltipFlag flag) {
        builder.accept(Component.literal("Ammunition: HE Rockets"));
    }
}
