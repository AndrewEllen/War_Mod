package com.andye.warmod.icbm;

import com.andye.warmod.warhead.WarheadPayloadType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class IcbmCommand {
	private IcbmCommand() { }

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> register(dispatcher));
	}

	private static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("icbm")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(payload("regular", WarheadPayloadType.CONVENTIONAL))
			.then(payload("nuke", WarheadPayloadType.NUCLEAR)));
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> payload(
		final String name, final WarheadPayloadType type) {
		return Commands.literal(name).then(Commands.argument("target", Vec3Argument.vec3())
			.executes(context -> launch(context, type, null))
			.then(Commands.argument("launch", Vec3Argument.vec3())
				.executes(context -> launch(context, type, Vec3Argument.getVec3(context, "launch")))));
	}

	private static int launch(final CommandContext<CommandSourceStack> context, final WarheadPayloadType type,
		final Vec3 launchPosition) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		ServerPlayer player = source.getPlayerOrException();
		Vec3 target = Vec3Argument.getVec3(context, "target");
		if (!IcbmPendingCommandLaunchManager.queue(source.getLevel(), player, target, launchPosition, type)) {
			source.sendFailure(Component.literal("ICBM launch failed: coordinates, build height, world border, or route bounds are invalid"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("ICBM launch queued: loading launch and target areas"), true);
		return 1;
	}

	private static String format(final Vec3 position) {
		return String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", position.x, position.y, position.z);
	}
}