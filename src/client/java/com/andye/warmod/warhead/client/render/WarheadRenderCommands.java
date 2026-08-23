package com.andye.warmod.warhead.client.render;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.andye.warmod.warhead.client.WarheadDebrisTuning;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;

/** Client-only profiling and visual tuning commands. They never contact or modify the server. */
public final class WarheadRenderCommands {
	private static boolean registered;

	private WarheadRenderCommands() {
	}

	public static void register() {
		if (registered) return;
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
			literal("war_mod")
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
						.then(argument("multiplier", FloatArgumentType.floatArg(0.01F))
							.executes(context -> setBudget(context.getSource(),
								FloatArgumentType.getFloat(context, "multiplier")))))
					.then(literal("status").executes(context -> status(context.getSource())))
				)
				.then(literal("debris")
					.then(literal("velocity")
						.then(literal("horizontal")
							.then(argument("multiplier", FloatArgumentType.floatArg(0.0F, 4.0F))
								.executes(context -> setDebrisHorizontal(context.getSource(),
									FloatArgumentType.getFloat(context, "multiplier")))))
						.then(literal("vertical")
							.then(argument("multiplier", FloatArgumentType.floatArg(0.0F, 4.0F))
								.executes(context -> setDebrisVertical(context.getSource(),
									FloatArgumentType.getFloat(context, "multiplier")))))
						.then(literal("reset").executes(context -> resetDebrisVelocity(context.getSource())))
						.then(literal("status").executes(context -> debrisVelocityStatus(context.getSource())))
					)
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
				+ "x (conventional cap " + WarheadRenderSettings.conventionalParticleBudget()
				+ "; very large values may exhaust client memory)"));
		return 1;
	}

	private static int resetBudget(
		final net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source
	) {
		WarheadRenderSettings.resetParticleBudget();
		return setBudget(source, WarheadRenderSettings.particleBudgetMultiplier());
	}

	private static int setDebrisHorizontal(
		final net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source,
		final float multiplier
	) {
		WarheadDebrisTuning.setHorizontalVelocityMultiplier(multiplier);
		return debrisVelocityStatus(source);
	}

	private static int setDebrisVertical(
		final net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source,
		final float multiplier
	) {
		WarheadDebrisTuning.setVerticalVelocityMultiplier(multiplier);
		return debrisVelocityStatus(source);
	}

	private static int resetDebrisVelocity(
		final net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source
	) {
		WarheadDebrisTuning.reset();
		return debrisVelocityStatus(source);
	}

	private static int debrisVelocityStatus(
		final net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source
	) {
		source.sendFeedback(Component.literal(
			"War Mod debris velocity: horizontal="
				+ WarheadDebrisTuning.horizontalVelocityMultiplier()
				+ "x, vertical=" + WarheadDebrisTuning.verticalVelocityMultiplier()
				+ "x (applies to newly spawned debris)"));
		return 1;
	}

	private static int status(final net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
		WarheadWorldRenderer.DebugSnapshot debug = WarheadWorldRenderer.debugSnapshot();
		source.sendFeedback(Component.literal(
			"War Mod renderer=" + WarheadRenderSettings.displayName()
				+ ", budget=" + WarheadRenderSettings.particleBudgetMultiplier() + "x"
				+ ", irisSafePipeline=" + WarheadRenderPipelines.compatibilityRendererActive()
				+ ", simulatedParticles=" + debug.activeParticles()
				+ ", representedParticles=" + debug.representedParticles()
				+ ", spawned/tick=" + debug.spawnedParticlesPerTick()
				+ ", culled=" + debug.culledParticles()
				+ ", debris=" + debug.activeDebrisFragments()
				+ ", backend=" + debug.activeRenderBackend()
		));
		return 1;
	}
}
