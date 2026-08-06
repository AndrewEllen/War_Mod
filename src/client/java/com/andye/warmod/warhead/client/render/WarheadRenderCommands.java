package com.andye.warmod.warhead.client.render;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

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

	private static int status(final net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
		WarheadWorldRenderer.DebugSnapshot debug = WarheadWorldRenderer.debugSnapshot();
		source.sendFeedback(Component.literal(
			"War Mod renderer=" + WarheadRenderSettings.displayName()
				+ ", irisSafePipeline=" + WarheadRenderPipelines.compatibilityRendererActive()
				+ ", particles=" + debug.activeParticles()
				+ ", spawned/tick=" + debug.spawnedParticlesPerTick()
				+ ", culled=" + debug.culledParticles()
				+ ", debris=" + debug.activeDebrisFragments()
		));
		return 1;
	}
}
