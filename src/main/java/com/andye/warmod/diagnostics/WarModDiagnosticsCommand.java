package com.andye.warmod.diagnostics;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Operator-facing control surface for lightweight War Mod diagnostics. */
public final class WarModDiagnosticsCommand {
    private WarModDiagnosticsCommand() { }

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("performance")
            .executes(WarModDiagnosticsCommand::toggle)
            .then(Commands.literal("on").executes(context -> setOverlay(context, true)))
            .then(Commands.literal("off").executes(context -> setOverlay(context, false)))
            .then(Commands.literal("status").executes(WarModDiagnosticsCommand::status))
            .then(Commands.literal("reset").executes(WarModDiagnosticsCommand::reset))
            .then(Commands.literal("report").executes(WarModDiagnosticsCommand::report));
    }

    private static int toggle(final CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            boolean enabled = WarModPerformanceDiagnostics.toggleOverlay(player);
            context.getSource().sendSuccess(() -> Component.literal("War Mod TPS/MSPT viewer "
                + (enabled ? "enabled" : "disabled")), false);
            return 1;
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal("This viewer requires an in-game player."));
            return 0;
        }
    }

    private static int setOverlay(final CommandContext<CommandSourceStack> context,
        final boolean enabled) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            WarModPerformanceDiagnostics.setOverlay(player, enabled);
            context.getSource().sendSuccess(() -> Component.literal("War Mod TPS/MSPT viewer "
                + (enabled ? "enabled" : "disabled")), false);
            return 1;
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal("This viewer requires an in-game player."));
            return 0;
        }
    }

    private static int status(final CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(
            WarModPerformanceDiagnostics.compactStatus()), false);
        return 1;
    }

    private static int reset(final CommandContext<CommandSourceStack> context) {
        WarModPerformanceDiagnostics.reset();
        context.getSource().sendSuccess(() -> Component.literal("War Mod performance counters reset."), false);
        return 1;
    }

    private static int report(final CommandContext<CommandSourceStack> context) {
        try {
            Path report = WarModPerformanceDiagnostics.exportReport(context.getSource().getServer());
            context.getSource().sendSuccess(() -> Component.literal(
                "War Mod performance report written to " + report.toAbsolutePath()), false);
            return 1;
        } catch (IOException exception) {
            context.getSource().sendFailure(Component.literal(
                "Could not write War Mod performance report: " + exception.getMessage()));
            return 0;
        }
    }
}
