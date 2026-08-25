package com.andye.warmod.warhead.client.render;

import com.andye.warmod.particle.gpu.GpuParticleEngine;
import com.andye.warmod.diagnostics.client.ClientPerformanceTelemetry;
import com.andye.warmod.warhead.client.WarheadDebrisTuning;
import com.andye.warmod.warhead.network.ClientboundWarheadClientControlPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Applies renderer controls received from the single server-owned /warmod tree. */
public final class WarheadClientControlHandler {
    private WarheadClientControlHandler() { }

    public static void accept(final ClientboundWarheadClientControlPayload payload) {
        if (payload == null || !payload.isWellFormed()) return;
        switch (payload.action()) {
            case STATUS -> status();
            case SET_CPU_MODE_PACKED -> {
                WarheadRenderSettings.setParticleRenderer(
                    WarheadRenderSettings.ParticleRenderer.PACKED);
                cpuModeStatus();
            }
            case SET_CPU_MODE_LEGACY -> {
                WarheadRenderSettings.setParticleRenderer(
                    WarheadRenderSettings.ParticleRenderer.LEGACY);
                cpuModeStatus();
            }
            case SET_RENDER_BUDGET -> {
                WarheadRenderSettings.setParticleBudgetMultiplier(payload.value());
                budgetStatus();
            }
            case RESET_RENDER_BUDGET -> {
                WarheadRenderSettings.resetParticleBudget();
                budgetStatus();
            }
            case SET_DEBRIS_HORIZONTAL -> {
                WarheadDebrisTuning.setHorizontalVelocityMultiplier(payload.value());
                debrisStatus();
            }
            case SET_DEBRIS_VERTICAL -> {
                WarheadDebrisTuning.setVerticalVelocityMultiplier(payload.value());
                debrisStatus();
            }
            case RESET_DEBRIS -> {
                WarheadDebrisTuning.reset();
                debrisStatus();
            }
            case DEBRIS_STATUS -> debrisStatus();
            case SET_BACKEND_AUTO ->
                setBackend(GpuParticleEngine.BackendPreference.AUTO);
            case SET_BACKEND_GPU ->
                setBackend(GpuParticleEngine.BackendPreference.GPU);
            case SET_BACKEND_CPU ->
                setBackend(GpuParticleEngine.BackendPreference.CPU);
            case SET_GPU_DIAGNOSTIC_OFF ->
                setGpuTest(GpuParticleEngine.DiagnosticMode.OFF);
            case SET_GPU_DIAGNOSTIC_DEPTH_OFF ->
                setGpuTest(GpuParticleEngine.DiagnosticMode.DEPTH_DISABLED);
            case SET_GPU_DIAGNOSTIC_DEPTH_ON ->
                setGpuTest(GpuParticleEngine.DiagnosticMode.DEPTH_ENABLED);
        }
    }

    private static void cpuModeStatus() {
        feedback("War Mod CPU particle mode: " + WarheadRenderSettings.displayName());
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

    private static void setBackend(final GpuParticleEngine.BackendPreference preference) {
        GpuParticleEngine.setBackendPreference(preference);
        feedback("War Mod particle backend preference: "
            + preference.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static void setGpuTest(final GpuParticleEngine.DiagnosticMode mode) {
        GpuParticleEngine.setDiagnosticMode(mode);
        feedback("War Mod direct GPU billboard test: "
            + mode.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'));
    }

    private static void status() {
        WarheadWorldRenderer.DebugSnapshot debug = WarheadWorldRenderer.debugSnapshot();
        GpuParticleEngine.DebugSnapshot gpu = GpuParticleEngine.debugSnapshot();
        GpuParticleEngine.FireDebugCounters fire = gpu.fire();
        ClientPerformanceTelemetry.DebugSnapshot cpu = ClientPerformanceTelemetry.debugSnapshot();
        feedback("War Mod backendPreference="
            + gpu.preference().name().toLowerCase(java.util.Locale.ROOT)
            + ", effectiveBackend="
            + gpu.effectiveBackend().name().toLowerCase(java.util.Locale.ROOT)
            + ", gpuResources=" + gpu.backend().name().toLowerCase(java.util.Locale.ROOT)
            + ", gpuReadiness=" + gpu.readiness().name().toLowerCase(java.util.Locale.ROOT)
            + ", cpuMode=" + WarheadRenderSettings.displayName()
            + ", budget=" + WarheadRenderSettings.particleBudgetMultiplier() + "x"
            + ", irisSafePipeline=" + WarheadRenderPipelines.compatibilityRendererActive()
            + ", activeParticles=" + debug.activeParticles()
            + ", visibleInstances=" + gpu.visibleParticles()
            + ", spawned/tick=" + debug.spawnedParticlesPerTick()
            + ", gpuAlive=" + gpu.activeParticles()
            + ", deadSlots=" + gpu.deadSlots()
            + ", rejectedSpawns=" + gpu.rejectedSpawns()
            + ", debris=" + debug.activeDebrisFragments()
            + ", rasterPipeline=" + debug.activeRenderBackend()
            + ", vfxQuality=" + String.format(java.util.Locale.ROOT, "%.2f", gpu.adaptiveQuality())
            + ", vfxLayers=" + gpu.scheduledLayers()
            + ", vfxEmitters=" + gpu.scheduledEmitters());
        feedback("War Mod GPU truth: distanceCulled=" + gpu.distanceCulled()
            + ", sizeCulled=" + gpu.sizeCulled()
            + ", frustumCulled=" + gpu.frustumCulled()
            + ", statsReadbackSkipped=" + gpu.statsReadbackSkipped()
            + ", requestedSpawns=" + gpu.requestedParticles()
            + ", acceptedSpawns=" + gpu.submittedParticles()
            + ", debug=" + gpu.diagnosticMode().name().toLowerCase(java.util.Locale.ROOT)
            + ", anySamplesPassed=" + gpu.diagnosticSamplesPassed());
        feedback("War Mod GPU ms p50/p95/p99: update=" + timing(gpu.gpuTime().update())
            + ", spawn=" + timing(gpu.gpuTime().spawn())
            + ", cull=" + timing(gpu.gpuTime().cull())
            + ", raster=" + timing(gpu.gpuTime().raster()));
        feedback("War Mod CPU ms p50/p95/p99: effectExtract="
            + timing(cpu.explosionExtraction())
            + ", fireExtract=" + timing(cpu.fireExtraction())
            + ", gpuDrain=" + timing(cpu.gpuExtractionCpu())
            + ", vfxSchedule=" + timing(cpu.gpuSchedulerCpu())
            + ", terrain=" + timing(cpu.terrainShockfrontCpu())
            + ", vanillaExtract=" + timing(cpu.vanillaParticleExtractionCpu())
            + ", vanillaRender=" + timing(cpu.vanillaParticleRenderCpu())
            + ", vanillaCount=" + cpu.vanillaParticleCount());
        feedback("War Mod fire VFX: clientPatches=" + fire.clientPatches()
            + ", fieldSubmissions=" + fire.fieldSubmissions()
            + ", fireSpawned=" + fire.fireSpawned()
            + ", fireVisible=" + fire.fireVisible()
            + ", smokeSpawned=" + fire.smokeSpawned()
            + ", smokeVisible=" + fire.smokeVisible()
            + ", packetsAccepted=" + fire.acceptedPackets()
            + ", packetsRejected=" + fire.rejectedPackets()
            + ", stalePackets=" + fire.stalePackets()
            + ", receivedPatchEntries=" + fire.receivedPatchEntries()
            + ", storedPatches=" + fire.storedPatches());
    }

    private static String timing(final GpuParticleEngine.GpuTiming timing) {
        return format(timing.p50Millis()) + "/" + format(timing.p95Millis())
            + "/" + format(timing.p99Millis());
    }

    private static String timing(final ClientPerformanceTelemetry.Percentiles timing) {
        return format(timing.p50Millis()) + "/" + format(timing.p95Millis())
            + "/" + format(timing.p99Millis());
    }

    private static String format(final double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static void feedback(final String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null)
            minecraft.player.sendSystemMessage(Component.literal(message));
    }
}
