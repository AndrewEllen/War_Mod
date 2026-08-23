package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.client.WarheadDebrisTuning;
import com.andye.warmod.warhead.network.ClientboundWarheadRenderControlPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Applies renderer controls received from the single server-owned /warmod tree. */
public final class WarheadRenderCommands {
    private WarheadRenderCommands() { }

    public static void accept(final ClientboundWarheadRenderControlPayload payload) {
        if (payload == null || !payload.isWellFormed()) return;
        switch (payload.action()) {
            case ClientboundWarheadRenderControlPayload.STATUS -> status();
            case ClientboundWarheadRenderControlPayload.PACKED -> {
                WarheadRenderSettings.setParticleRenderer(
                    WarheadRenderSettings.ParticleRenderer.PACKED);
                rendererStatus();
            }
            case ClientboundWarheadRenderControlPayload.LEGACY -> {
                WarheadRenderSettings.setParticleRenderer(
                    WarheadRenderSettings.ParticleRenderer.LEGACY);
                rendererStatus();
            }
            case ClientboundWarheadRenderControlPayload.BUDGET -> {
                WarheadRenderSettings.setParticleBudgetMultiplier(payload.value());
                budgetStatus();
            }
            case ClientboundWarheadRenderControlPayload.BUDGET_RESET -> {
                WarheadRenderSettings.resetParticleBudget();
                budgetStatus();
            }
            case ClientboundWarheadRenderControlPayload.DEBRIS_HORIZONTAL -> {
                WarheadDebrisTuning.setHorizontalVelocityMultiplier(payload.value());
                debrisStatus();
            }
            case ClientboundWarheadRenderControlPayload.DEBRIS_VERTICAL -> {
                WarheadDebrisTuning.setVerticalVelocityMultiplier(payload.value());
                debrisStatus();
            }
            case ClientboundWarheadRenderControlPayload.DEBRIS_RESET -> {
                WarheadDebrisTuning.reset();
                debrisStatus();
            }
            case ClientboundWarheadRenderControlPayload.DEBRIS_STATUS -> debrisStatus();
            default -> { }
        }
    }

    private static void rendererStatus() {
        feedback("War Mod particle renderer: " + WarheadRenderSettings.displayName());
    }

    private static void budgetStatus() {
        feedback("War Mod particle budget: "
            + WarheadRenderSettings.particleBudgetMultiplier()
            + "x (conventional cap " + WarheadRenderSettings.conventionalParticleBudget()
            + "; nuclear showcase effects use a protected independent floor)");
    }

    private static void debrisStatus() {
        feedback("War Mod debris velocity: horizontal="
            + WarheadDebrisTuning.horizontalVelocityMultiplier()
            + "x, vertical=" + WarheadDebrisTuning.verticalVelocityMultiplier()
            + "x (applies to newly spawned debris)");
    }

    private static void status() {
        WarheadWorldRenderer.DebugSnapshot debug = WarheadWorldRenderer.debugSnapshot();
        feedback("War Mod renderer=" + WarheadRenderSettings.displayName()
            + ", budget=" + WarheadRenderSettings.particleBudgetMultiplier() + "x"
            + ", irisSafePipeline=" + WarheadRenderPipelines.compatibilityRendererActive()
            + ", simulatedParticles=" + debug.activeParticles()
            + ", representedParticles=" + debug.representedParticles()
            + ", spawned/tick=" + debug.spawnedParticlesPerTick()
            + ", culled=" + debug.culledParticles()
            + ", debris=" + debug.activeDebrisFragments()
            + ", backend=" + debug.activeRenderBackend());
    }

    private static void feedback(final String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null)
            minecraft.player.sendSystemMessage(Component.literal(message));
    }
}
