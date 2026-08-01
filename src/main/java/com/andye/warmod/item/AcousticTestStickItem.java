package com.andye.warmod.item;

import com.andye.warmod.acoustics.AcousticEngine;
import com.andye.warmod.acoustics.AcousticSounds;
import com.andye.warmod.acoustics.physics.AcousticPropagation;
import com.andye.warmod.testtool.TestExplosionService;
import com.andye.warmod.testtool.TestTargeting;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

public final class AcousticTestStickItem extends Item {
	public AcousticTestStickItem(final Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (level.isClientSide() || !(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}

		ItemStack stack = player.getItemInHand(hand);
		if (serverPlayer.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.PASS;
		}

		Optional<BlockHitResult> target = TestTargeting.findTarget(serverPlayer, 512.0);
		if (target.isEmpty()) {
			serverPlayer.sendOverlayMessage(Component.literal("No loaded block found within 512 blocks"));
			return InteractionResult.SUCCESS_SERVER;
		}

		BlockHitResult hit = target.get();
		Vec3 explosionPosition = hit.getLocation().subtract(
			hit.getDirection().getStepX() * 0.15,
			hit.getDirection().getStepY() * 0.15,
			hit.getDirection().getStepZ() * 0.15
		);
		double distance = serverPlayer.getEyePosition().distanceTo(explosionPosition);
		long delayTicks = AcousticPropagation.delayTicks(distance, 343.0);

		TestExplosionService.createExplosion(serverLevel, serverPlayer, explosionPosition);
		AcousticEngine.playSound(
			serverLevel,
			explosionPosition,
			AcousticSounds.LARGE_EXPLOSION_ID,
			SoundSource.BLOCKS,
			1.0F,
			1.0F
		);
		serverPlayer.getCooldowns().addCooldown(stack, 20);
		serverPlayer.sendOverlayMessage(Component.literal(String.format(
			Locale.ROOT,
			"Explosion: %.0f blocks | Sound delay: %.2f s",
			distance,
			delayTicks / 20.0
		)));
		return InteractionResult.SUCCESS_SERVER;
	}
}
