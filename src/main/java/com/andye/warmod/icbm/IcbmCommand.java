package com.andye.warmod.icbm;

import com.andye.warmod.warhead.WarheadDeliveryMode;
import com.andye.warmod.warhead.WarheadYield;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class IcbmCommand {
	private IcbmCommand() { }

	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("icbm");
		for (WarheadYield yield : WarheadYield.values()) root.then(yieldCommand(yield));
		return root;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> yieldCommand(final WarheadYield yield) {
		return Commands.literal(yield.getSerializedName())
			.then(target(yield, WarheadDeliveryMode.SINGLE))
			.then(Commands.literal("cluster")
				.then(target(yield, WarheadDeliveryMode.CLUSTER_FOUR)));
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, net.minecraft.commands.arguments.coordinates.Coordinates>
		target(final WarheadYield yield, final WarheadDeliveryMode deliveryMode) {
		return Commands.argument("target", Vec3Argument.vec3())
			.executes(context -> launch(context, yield, deliveryMode, null))
			.then(Commands.argument("launch", Vec3Argument.vec3())
				.executes(context -> launch(context, yield, deliveryMode,
					Vec3Argument.getVec3(context, "launch"))));
	}

	private static int launch(final CommandContext<CommandSourceStack> context,
		final WarheadYield yield, final WarheadDeliveryMode deliveryMode,
		final Vec3 launchPosition) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		ServerPlayer player = source.getPlayerOrException();
		Vec3 target = Vec3Argument.getVec3(context, "target");
		if (!IcbmPendingCommandLaunchManager.queue(source.getLevel(), player, target,
			launchPosition, yield, deliveryMode)) {
			source.sendFailure(Component.literal("ICBM launch failed: coordinates, build height, world border, or route bounds are invalid"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("ICBM launch queued: "
			+ yield.displayName() + (deliveryMode == WarheadDeliveryMode.CLUSTER_FOUR
				? " cluster" : "") + "; loading launch area"), true);
		return 1;
	}

}
