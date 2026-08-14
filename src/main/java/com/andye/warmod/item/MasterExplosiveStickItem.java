package com.andye.warmod.item;

import com.andye.warmod.icbm.IcbmLaunchService;
import com.andye.warmod.item.component.IcbmTestDeliveryMode;
import com.andye.warmod.item.component.MasterExplosiveConfig;
import com.andye.warmod.item.component.MasterExplosiveDelivery;
import com.andye.warmod.item.component.ModDataComponents;
import com.andye.warmod.testtool.DirectWarheadTestVolley;
import com.andye.warmod.testtool.TestTargeting;
import com.andye.warmod.testtool.network.MasterExplosiveNetworking;
import com.andye.warmod.warhead.WarheadConstants;
import com.andye.warmod.warhead.WarheadDeliveryMode;
import com.andye.warmod.warhead.WarheadLaunchService;
import com.andye.warmod.warhead.WarheadYieldRegistry;
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

/** All-yield direct-warhead and full-ICBM development launcher. */
public final class MasterExplosiveStickItem extends Item {
	private static final int COOLDOWN_TICKS = 2;

	public MasterExplosiveStickItem(final Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (level.isClientSide() || !(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (player.isShiftKeyDown()) {
			MasterExplosiveNetworking.open(serverPlayer, hand, stack);
			return InteractionResult.SUCCESS_SERVER;
		}
		if (serverPlayer.getCooldowns().isOnCooldown(stack)) return InteractionResult.PASS;
		Optional<BlockHitResult> target = TestTargeting.findTarget(serverPlayer, WarheadConstants.TARGET_RANGE_BLOCKS);
		if (target.isEmpty()) {
			serverPlayer.sendOverlayMessage(Component.literal(String.format(
				Locale.ROOT,
				"No loaded block found within %.0f blocks",
				WarheadConstants.TARGET_RANGE_BLOCKS
			)));
			return InteractionResult.SUCCESS_SERVER;
		}

		MasterExplosiveConfig config = stack.getOrDefault(
			ModDataComponents.MASTER_EXPLOSIVE_CONFIG,
			MasterExplosiveConfig.DEFAULT
		);
		Vec3 intended = inside(target.get());
		int launched = config.delivery() == MasterExplosiveDelivery.ICBM
			? launchIcbm(server, serverPlayer, intended, config)
			: launchDirect(server, serverPlayer, intended, config);
		if (launched <= 0) {
			serverPlayer.sendOverlayMessage(Component.literal("Explosive test launch failed: target or route unavailable"));
			return InteractionResult.SUCCESS_SERVER;
		}
		serverPlayer.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
		serverPlayer.sendOverlayMessage(Component.literal(
			"Launched " + launched + " × " + config.summary()
		));
		return InteractionResult.SUCCESS_SERVER;
	}

	private static int launchDirect(
		final ServerLevel level,
		final ServerPlayer player,
		final Vec3 intended,
		final MasterExplosiveConfig config
	) {
		IcbmTestDeliveryMode mode = config.cluster()
			? IcbmTestDeliveryMode.CLUSTER_FOUR
			: IcbmTestDeliveryMode.SINGLE;
		List<WarheadLaunchService.LaunchResult> results = DirectWarheadTestVolley.launch(
			level,
			player,
			intended,
			config.yield().payloadType(),
			mode
		);
		for (WarheadLaunchService.LaunchResult result : results) {
			WarheadYieldRegistry.put(level, result.radarRootTrackId(), config.yield(),
				config.customFire());
		}
		return results.size();
	}

	private static int launchIcbm(
		final ServerLevel level,
		final ServerPlayer player,
		final Vec3 intended,
		final MasterExplosiveConfig config
	) {
		WarheadDeliveryMode delivery = config.cluster()
			? WarheadDeliveryMode.CLUSTER_FOUR
			: WarheadDeliveryMode.SINGLE;
		IcbmLaunchService.LaunchResult result = IcbmLaunchService.launch(
			level,
			player,
			intended,
			config.yield().payloadType(),
			delivery
		).orElse(null);
		if (result == null) return 0;
		WarheadYieldRegistry.put(level, result.flightPlan().missileId(), config.yield(),
			config.customFire());
		return config.cluster() ? 4 : 1;
	}

	@Override
	public void appendHoverText(
		final ItemStack stack,
		final TooltipContext context,
		final TooltipDisplay display,
		final Consumer<Component> tooltip,
		final TooltipFlag flag
	) {
		MasterExplosiveConfig config = stack.getOrDefault(
			ModDataComponents.MASTER_EXPLOSIVE_CONFIG,
			MasterExplosiveConfig.DEFAULT
		);
		tooltip.accept(Component.literal(config.summary()));
		tooltip.accept(Component.literal("Aftermath fire: "
			+ (config.customFire() ? "Custom particle fire" : "Vanilla fire blocks")));
		tooltip.accept(Component.literal("Crouch-use: configure | Use: launch at target"));
	}

	private static Vec3 inside(final BlockHitResult hit) {
		return hit.getLocation().subtract(
			hit.getDirection().getStepX() * 0.15,
			hit.getDirection().getStepY() * 0.15,
			hit.getDirection().getStepZ() * 0.15
		);
	}
}
