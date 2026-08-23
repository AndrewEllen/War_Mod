package com.andye.warmod;

import com.andye.warmod.diagnostics.WarModDiagnosticsCommand;
import com.andye.warmod.icbm.IcbmCommand;
import com.andye.warmod.warhead.WarheadFireSettings;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Single operator-facing root for all War Mod server commands. */
public final class WarModCommand {
    private WarModCommand() { }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
            register(dispatcher));
    }

    private static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("war_mod")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .executes(WarModCommand::help)
            .then(IcbmCommand.command())
            .then(WarModDiagnosticsCommand.command())
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
            "War Mod: /war_mod icbm <yield> [cluster] <target> [launch], "
                + "/war_mod performance, /war_mod fire mode <custom|vanilla>"), false);
        return 1;
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
