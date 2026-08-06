package com.andye.warmod.warhead.client.render;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.arguments.FloatArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;

/** Client-only profiling commands. They never contact or modify the server. */
public final class WarheadRenderCommands {
	private static boolean registered;

	private WarheadRenderCommands() {
	}

	public static void register() {
		if (registered) return;
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
			literal("warmod")
				.then(literal("renderer")
					.then(literal("packed").executes(context -> setMode(context.getSource(),
						WarheadRenderSettings.ParticleRenderer.PACKED)))
					.then(literal("gpu").executes(context -> setMode(context.getSource(),
						WarheadRenderSettings.ParticleRenderer.PACKED)))
					.then(literal("legacy").executes(context -> setMode(context.getSource(),
						WarheadRenderSettings.ParticleRenderer.LEGACY)))
					.then(literal("cpu").executes(context -> setMode(context.getSource(),
						WarheadRenderSettings.ParticleRenderer.LEGACY)))
					.then(literal("budget")
						.then(literal("reset").executes(context -> resetBudget(context.getSource())))
						.then(argument("multiplier", FloatArgumentType.floatArg(0.25F, 6.0F))
							.executes(context -> setBudget(context.getSource(),
								FloatArgumentType.getFloat(context, "multiplier")))))
					.then(literal("status").executes(context -> status(context.getSource())))
				)
		));
		registered = true;
	}

	private static int setMode(final net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source,
		final WarheadRenderSettings.ParticleRenderer renderer) {
		WarheadRenderSettings.setParticleRenderer(renderer);
		source.sendFeedback(Component.literal("War Mod particle renderer: " + WarheadRenderSettings.displayName()));
		return 1;
	}

	private static int setBudget(
		final net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source,
		final float multiplier
	) {
		WarheadRenderSettings.setParticleBudgetMultiplier(multiplier);
		source.sendFeedback(Component.literal(
			"War Mod particle budget: " + WarheadRenderSettings.particleBudgetMultiplier()
				+ "x (conventional cap " + WarheadRenderSettings.conventionalParticleBudget() + ")"));
		return 1;
	}

	private static int resetBudget(
		final net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source
	) {
		WarheadRenderSettings.resetParticleBudget();
		return setBudget(source, WarheadRenderSettings.particleBudgetMultiplier());
	}

	private static int status(final net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
		WarheadWorldRenderer.DebugSnapshot debug = WarheadWorldRenderer.debugSnapshot();
		source.sendFeedback(Component.literal(
			"War Mod renderer=" + WarheadRenderSettings.displayName()
				+ ", budget=" + WarheadRenderSettings.particleBudgetMultiplier() + "x"
				+ ", irisSafePipeline=" + WarheadRenderPipelines.compatibilityRendererActive()
				+ ", particles=" + debug.activeParticles()
				+ ", spawned/tick=" + debug.spawnedParticlesPerTick()
				+ ", culled=" + debug.culledParticles()
				+ ", debris=" + debug.activeDebrisFragments()
		));
		return 1;
	}
}
