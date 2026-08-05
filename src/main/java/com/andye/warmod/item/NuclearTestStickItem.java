package com.andye.warmod.item;

import com.andye.warmod.acoustics.physics.AcousticPropagation;
import com.andye.warmod.item.component.IcbmTestDeliveryMode;
import com.andye.warmod.item.component.ModDataComponents;
import com.andye.warmod.testtool.DirectWarheadTestVolley;
import com.andye.warmod.testtool.TestTargeting;
import com.andye.warmod.warhead.WarheadConstants;
import com.andye.warmod.warhead.WarheadLaunchService;
import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Direct-from-above nuclear warhead test stick with single and cluster modes. */
public final class NuclearTestStickItem extends Item {
	private static final int COOLDOWN_TICKS = 2;

	public NuclearTestStickItem(final Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (level.isClientSide() || !(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (player.isShiftKeyDown()) {
			IcbmTestDeliveryMode mode = mode(stack).toggle();
			stack.set(ModDataComponents.ICBM_TEST_DELIVERY_MODE, mode);
			sp.sendOverlayMessage(Component.literal("Nuclear Test Stick: " + label(mode)));
			return InteractionResult.SUCCESS_SERVER;
		}
		if (sp.getCooldowns().isOnCooldown(stack)) return InteractionResult.PASS;

		Optional<BlockHitResult> target = TestTargeting.findTarget(sp, WarheadConstants.TARGET_RANGE_BLOCKS);
		if (target.isEmpty()) {
			sp.sendOverlayMessage(Component.literal(String.format(
				Locale.ROOT,
				"No loaded block found within %.0f blocks",
				WarheadConstants.TARGET_RANGE_BLOCKS
			)));
			return InteractionResult.SUCCESS_SERVER;
		}

		Vec3 intended = inside(target.get());
		IcbmTestDeliveryMode mode = mode(stack);
		List<WarheadLaunchService.LaunchResult> launches = DirectWarheadTestVolley.launch(
			server,
			sp,
			intended,
			WarheadPayloadType.NUCLEAR,
			mode
		);
		if (launches.isEmpty()) {
			sp.sendOverlayMessage(Component.literal("Nuclear launch failed: target area is not loaded"));
			return InteractionResult.SUCCESS_SERVER;
		}

		WarheadLaunchService.LaunchResult first = launches.get(0);
		double distance = sp.getEyePosition().distanceTo(intended);
		long soundDelayTicks = AcousticPropagation.delayTicks(distance, 343.0);
		sp.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
		sp.sendOverlayMessage(Component.literal(String.format(
			Locale.ROOT,
			"Nuclear volley: %d warhead%s | Impact: %.1f s | Sound after impact: %.2f s",
			launches.size(),
			launches.size() == 1 ? "" : "s",
			first.flightTicks() / 20.0,
			soundDelayTicks / 20.0
		)));
		return InteractionResult.SUCCESS_SERVER;
	}

	@Override
	public void appendHoverText(
		final ItemStack stack,
		final TooltipContext context,
		final TooltipDisplay display,
		final Consumer<Component> tooltip,
		final TooltipFlag flag
	) {
		tooltip.accept(Component.literal("Mode: " + label(mode(stack))));
		tooltip.accept(Component.literal("Crouch-use to toggle single / cluster"));
	}

	private static IcbmTestDeliveryMode mode(final ItemStack stack) {
		return stack.getOrDefault(ModDataComponents.ICBM_TEST_DELIVERY_MODE, IcbmTestDeliveryMode.SINGLE);
	}

	private static String label(final IcbmTestDeliveryMode mode) {
		return mode == IcbmTestDeliveryMode.SINGLE ? "Single warhead" : "Cluster - 4 warheads";
	}

	private static Vec3 inside(final BlockHitResult hit) {
		return hit.getLocation().subtract(
			hit.getDirection().getStepX() * 0.15,
			hit.getDirection().getStepY() * 0.15,
			hit.getDirection().getStepZ() * 0.15
		);
	}
}
