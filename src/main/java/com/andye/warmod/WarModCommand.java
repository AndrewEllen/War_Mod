package com.andye.warmod;

import com.andye.warmod.diagnostics.WarModDiagnosticsCommand;
import com.andye.warmod.icbm.IcbmCommand;
import com.andye.warmod.warhead.WarheadFireSettings;
import com.andye.warmod.warhead.network.ClientboundWarheadRenderControlPayload;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
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

    private static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("warmod")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .executes(WarModCommand::help)
            .then(IcbmCommand.command())
            .then(WarModDiagnosticsCommand.command())
            .then(rendererCommand())
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
                + "/warmod performance, /warmod render backend <auto|gpu|cpu>, "
                + "/warmod renderer, /warmod debris, "
                + "/warmod fire mode <custom|vanilla>"), false);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> rendererCommand() {
        return Commands.literal("renderer")
            .executes(context -> sendClientControl(context,
                ClientboundWarheadRenderControlPayload.STATUS, 0.0F))
            .then(Commands.literal("status").executes(context -> sendClientControl(context,
                ClientboundWarheadRenderControlPayload.STATUS, 0.0F)))
            .then(Commands.literal("packed").executes(context -> sendClientControl(context,
                ClientboundWarheadRenderControlPayload.PACKED, 0.0F)))
            .then(Commands.literal("gpu").executes(context -> sendClientControl(context,
                ClientboundWarheadRenderControlPayload.BACKEND_GPU, 0.0F)))
            .then(Commands.literal("legacy").executes(context -> sendClientControl(context,
                ClientboundWarheadRenderControlPayload.LEGACY, 0.0F)))
            .then(Commands.literal("cpu").executes(context -> sendClientControl(context,
                ClientboundWarheadRenderControlPayload.BACKEND_CPU, 0.0F)))
            .then(Commands.literal("auto").executes(context -> sendClientControl(context,
                ClientboundWarheadRenderControlPayload.BACKEND_AUTO, 0.0F)))
            .then(backendCommand())
            .then(gpuTestCommand())
            .then(budgetCommand());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> renderCommand() {
        return Commands.literal("render")
            .executes(context -> sendClientControl(context,
                ClientboundWarheadRenderControlPayload.STATUS, 0.0F))
            .then(Commands.literal("status").executes(context -> sendClientControl(context,
                ClientboundWarheadRenderControlPayload.STATUS, 0.0F)))
            .then(backendCommand())
            .then(gpuTestCommand())
            .then(budgetCommand());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> backendCommand() {
        return Commands.literal("backend")
            .then(Commands.literal("auto").executes(context -> sendClientControl(context,
                ClientboundWarheadRenderControlPayload.BACKEND_AUTO, 0.0F)))
            .then(Commands.literal("gpu").executes(context -> sendClientControl(context,
                ClientboundWarheadRenderControlPayload.BACKEND_GPU, 0.0F)))
            .then(Commands.literal("cpu").executes(context -> sendClientControl(context,
                ClientboundWarheadRenderControlPayload.BACKEND_CPU, 0.0F)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> gpuTestCommand() {
        return Commands.literal("gpu-test")
            .then(Commands.literal("off").executes(context -> sendClientControl(context,
                ClientboundWarheadRenderControlPayload.GPU_TEST_OFF, 0.0F)))
            .then(Commands.literal("depth-off").executes(context -> sendClientControl(context,
                ClientboundWarheadRenderControlPayload.GPU_TEST_DEPTH_OFF, 0.0F)))
            .then(Commands.literal("depth-on").executes(context -> sendClientControl(context,
                ClientboundWarheadRenderControlPayload.GPU_TEST_DEPTH_ON, 0.0F)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> budgetCommand() {
        return Commands.literal("budget")
            .then(Commands.literal("reset").executes(context -> sendClientControl(context,
                ClientboundWarheadRenderControlPayload.BUDGET_RESET, 0.0F)))
            .then(Commands.argument("multiplier",
                com.mojang.brigadier.arguments.FloatArgumentType.floatArg(0.01F))
                .executes(context -> sendClientControl(context,
                    ClientboundWarheadRenderControlPayload.BUDGET,
                    com.mojang.brigadier.arguments.FloatArgumentType.getFloat(
                        context, "multiplier"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> debrisCommand() {
        return Commands.literal("debris")
            .executes(context -> sendClientControl(context,
                ClientboundWarheadRenderControlPayload.DEBRIS_STATUS, 0.0F))
            .then(Commands.literal("status").executes(context -> sendClientControl(context,
                ClientboundWarheadRenderControlPayload.DEBRIS_STATUS, 0.0F)))
            .then(Commands.literal("velocity")
                .then(Commands.literal("horizontal")
                    .then(Commands.argument("multiplier",
                        com.mojang.brigadier.arguments.FloatArgumentType.floatArg(0.0F, 4.0F))
                        .executes(context -> sendClientControl(context,
                            ClientboundWarheadRenderControlPayload.DEBRIS_HORIZONTAL,
                            com.mojang.brigadier.arguments.FloatArgumentType.getFloat(
                                context, "multiplier")))))
                .then(Commands.literal("vertical")
                    .then(Commands.argument("multiplier",
                        com.mojang.brigadier.arguments.FloatArgumentType.floatArg(0.0F, 4.0F))
                        .executes(context -> sendClientControl(context,
                            ClientboundWarheadRenderControlPayload.DEBRIS_VERTICAL,
                            com.mojang.brigadier.arguments.FloatArgumentType.getFloat(
                                context, "multiplier")))))
                .then(Commands.literal("reset").executes(context -> sendClientControl(context,
                    ClientboundWarheadRenderControlPayload.DEBRIS_RESET, 0.0F))));
    }

    private static int sendClientControl(final CommandContext<CommandSourceStack> context,
        final int action, final float value) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            ServerPlayNetworking.send(player,
                new ClientboundWarheadRenderControlPayload(action, value));
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
