package com.andye.warmod;

import com.andye.warmod.diagnostics.WarModDiagnosticsCommand;
import com.andye.warmod.icbm.IcbmCommand;
import com.andye.warmod.warhead.WarheadFireSettings;
import com.andye.warmod.warhead.network.ClientboundWarheadClientControlPayload;
import com.andye.warmod.warhead.network.ClientboundWarheadClientControlPayload.Action;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.SharedConstants;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Single operator-facing root for all War Mod server commands. */
public final class WarModCommand {
    private WarModCommand() { }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
            register(dispatcher));
    }

    static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("warmod")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .executes(WarModCommand::help)
            .then(IcbmCommand.command())
            .then(WarModDiagnosticsCommand.command())
            .then(renderCommand())
            .then(debrisCommand())
            .then(fireCommand()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> fireCommand() {
        return Commands.literal("fire")
            .executes(WarModCommand::fireStatus)
            .then(Commands.literal("status").executes(WarModCommand::fireStatus))
            .then(Commands.literal("mode")
                .then(Commands.literal("custom")
                    .executes(context -> setFireMode(context, true)))
                .then(Commands.literal("vanilla")
                    .executes(context -> setFireMode(context, false))));
    }

    private static int help(final CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(
            "War Mod: /warmod icbm <yield> [cluster] <target> [launch], "
                + "/warmod performance, /warmod render <status|backend|cpu-mode|quality>, "
                + "/warmod debris, "
                + "/warmod fire mode <custom|vanilla>"), false);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> renderCommand() {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("render")
            .executes(context -> sendClientControl(context,
                Action.STATUS, 0.0F))
            .then(Commands.literal("status").executes(context -> sendClientControl(context,
                Action.STATUS, 0.0F)))
            .then(backendCommand())
            .then(cpuModeCommand())
            .then(qualityCommand());
        if (SharedConstants.IS_RUNNING_IN_IDE) command.then(diagnoseCommand());
        return command;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> backendCommand() {
        return Commands.literal("backend")
            .then(Commands.literal("auto").executes(context -> sendClientControl(context,
                Action.SET_BACKEND_AUTO, 0.0F)))
            .then(Commands.literal("gpu").executes(context -> sendClientControl(context,
                Action.SET_BACKEND_GPU, 0.0F)))
            .then(Commands.literal("cpu").executes(context -> sendClientControl(context,
                Action.SET_BACKEND_CPU, 0.0F)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> cpuModeCommand() {
        return Commands.literal("cpu-mode")
            .then(Commands.literal("packed").executes(context -> sendClientControl(context,
                Action.SET_CPU_MODE_PACKED, 0.0F)))
            .then(Commands.literal("legacy").executes(context -> sendClientControl(context,
                Action.SET_CPU_MODE_LEGACY, 0.0F)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> diagnoseCommand() {
        return Commands.literal("diagnose")
            .then(Commands.literal("gpu")
                .then(Commands.literal("off").executes(context -> sendClientControl(context,
                    Action.SET_GPU_DIAGNOSTIC_OFF, 0.0F)))
                .then(Commands.literal("depth-off").executes(context -> sendClientControl(context,
                    Action.SET_GPU_DIAGNOSTIC_DEPTH_OFF, 0.0F)))
                .then(Commands.literal("depth-on").executes(context -> sendClientControl(context,
                    Action.SET_GPU_DIAGNOSTIC_DEPTH_ON, 0.0F))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> qualityCommand() {
        return Commands.literal("quality")
            .then(Commands.literal("reset").executes(context -> sendClientControl(context,
                Action.RESET_RENDER_QUALITY, 0.0F)))
            .then(Commands.argument("scale",
                com.mojang.brigadier.arguments.FloatArgumentType.floatArg(0.25F, 4.0F))
                .executes(context -> sendClientControl(context,
                    Action.SET_RENDER_QUALITY,
                    com.mojang.brigadier.arguments.FloatArgumentType.getFloat(
                        context, "scale"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> debrisCommand() {
        return Commands.literal("debris")
            .executes(context -> sendClientControl(context,
                Action.DEBRIS_STATUS, 0.0F))
            .then(Commands.literal("status").executes(context -> sendClientControl(context,
                Action.DEBRIS_STATUS, 0.0F)))
            .then(Commands.literal("velocity")
                .then(Commands.literal("horizontal")
                    .then(Commands.argument("multiplier",
                        com.mojang.brigadier.arguments.FloatArgumentType.floatArg(0.0F, 4.0F))
                        .executes(context -> sendClientControl(context,
                            Action.SET_DEBRIS_HORIZONTAL,
                            com.mojang.brigadier.arguments.FloatArgumentType.getFloat(
                                context, "multiplier")))))
                .then(Commands.literal("vertical")
                    .then(Commands.argument("multiplier",
                        com.mojang.brigadier.arguments.FloatArgumentType.floatArg(0.0F, 4.0F))
                        .executes(context -> sendClientControl(context,
                            Action.SET_DEBRIS_VERTICAL,
                            com.mojang.brigadier.arguments.FloatArgumentType.getFloat(
                                context, "multiplier")))))
                .then(Commands.literal("reset").executes(context -> sendClientControl(context,
                    Action.RESET_DEBRIS, 0.0F))));
    }

    private static int sendClientControl(final CommandContext<CommandSourceStack> context,
        final Action action, final float value) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            ServerPlayNetworking.send(player,
                new ClientboundWarheadClientControlPayload(action, value));
            return 1;
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal(
                "This renderer command requires an in-game player."));
            return 0;
        }
    }

    private static int fireStatus(final CommandContext<CommandSourceStack> context) {
        boolean custom = WarheadFireSettings.get(context.getSource().getLevel()).customFire();
        context.getSource().sendSuccess(() -> Component.literal(
            "War Mod explosive aftermath fire: " + (custom ? "custom" : "vanilla")), false);
        return 1;
    }

    private static int setFireMode(final CommandContext<CommandSourceStack> context,
        final boolean custom) {
        WarheadFireSettings.get(context.getSource().getLevel()).setCustomFire(custom);
        context.getSource().sendSuccess(() -> Component.literal(
            "War Mod explosive aftermath fire set to " + (custom ? "custom" : "vanilla")
                + " for this dimension."), true);
        return 1;
    }
}
