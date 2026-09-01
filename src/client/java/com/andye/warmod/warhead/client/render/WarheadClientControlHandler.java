package com.andye.warmod.warhead.client.render;

import com.andye.warmod.diagnostics.client.ClientPerformanceTelemetry;
import com.andye.warmod.particle.gpu.GpuParticleEngine;
import com.andye.warmod.fire.client.ClientFireVisualManager;
import com.andye.warmod.fire.client.render.FireWorldRenderer;
import com.andye.warmod.fire.network.FireVisualBand;
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
            case SET_RENDER_QUALITY -> {
                WarheadRenderSettings.setQualityScale(payload.value());
                qualityStatus();
            }
            case RESET_RENDER_QUALITY -> {
                WarheadRenderSettings.resetQualityScale();
                qualityStatus();
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

    private static void qualityStatus() {
        GpuParticleEngine.GpuBudgetSnapshot gpu = GpuParticleEngine.budgetSnapshot();
        feedback("War Mod render quality requested="
            + format(WarheadRenderSettings.qualityScale())
            + ", effectiveAdaptive=" + format(gpu.adaptiveQuality())
            + ", hardParticles=" + gpu.particleCapacity()
            + ", hardEmitters=" + gpu.emitterCapacity()
            + ", liveSlots=" + (gpu.particleCapacity() - gpu.availableDeadSlots())
            + ", transientReserve=" + gpu.protectedTransientSlots());
    }

    private static void debrisStatus() {
        feedback("War Mod conventional debris velocity: horizontal="
            + WarheadDebrisTuning.horizontalVelocityMultiplier()
            + "x, vertical=" + WarheadDebrisTuning.verticalVelocityMultiplier()
            + "x; nuclear legacy transform: horizontal="
            + WarheadDebrisTuning.nuclearHorizontalVelocityMultiplier()
            + "x, vertical="
            + WarheadDebrisTuning.nuclearVerticalVelocityMultiplier()
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
        GpuParticleEngine.DebugSnapshot gpu = GpuParticleEngine.debugSnapshot();
        GpuParticleEngine.GpuBudgetSnapshot budget = GpuParticleEngine.budgetSnapshot();
        ClientPerformanceTelemetry.DebugSnapshot performance =
            ClientPerformanceTelemetry.debugSnapshot();
        FireWorldRenderer.FireRenderStats fire = FireWorldRenderer.debugStats();
        java.util.Map<FireVisualBand, Integer> bands =
            ClientFireVisualManager.INSTANCE.cellCounts(Minecraft.getInstance().level);
        java.util.Map<GpuParticleEngine.VisualLayer,
            GpuParticleEngine.GpuLayerScheduleSnapshot> schedules =
                GpuParticleEngine.layerScheduleSnapshot();
        feedback("War Mod backend requested="
            + gpu.preference().name().toLowerCase(java.util.Locale.ROOT)
            + " effective=" + gpu.effectiveBackend().name().toLowerCase(java.util.Locale.ROOT)
            + " cpuMode=" + WarheadRenderSettings.displayName()
            + " GPU base=" + gpu.readiness().name().toLowerCase(java.util.Locale.ROOT));
        feedback("War Mod layers flames=" + layerRoute(gpu, GpuParticleEngine.VisualLayer.FLAMES)
            + " smoke=" + layerRoute(gpu, GpuParticleEngine.VisualLayer.SMOKE)
            + " embers=" + layerRoute(gpu, GpuParticleEngine.VisualLayer.EMBERS)
            + " shroud=" + layerRoute(gpu, GpuParticleEngine.VisualLayer.SMOKE_SHROUD)
            + " terrainDust=" + layerRoute(gpu,
                GpuParticleEngine.VisualLayer.TERRAIN_OBSCURATION));
        feedback("War Mod fire cells patch/host/local/far/horizon="
            + bandCount(bands, FireVisualBand.PATCH) + "/"
            + bandCount(bands, FireVisualBand.HOST) + "/"
            + bandCount(bands, FireVisualBand.LOCAL) + "/"
            + bandCount(bands, FireVisualBand.FAR) + "/"
            + bandCount(bands, FireVisualBand.HORIZON)
            + " sourceHosts/aggregated/visible=" + fire.sourceHosts() + "/"
            + fire.aggregatedCells() + "/" + fire.visibleCells());
        feedback("War Mod fire cards CPU flame/smoke=" + fire.cpuFlameCards() + "/"
            + fire.cpuSmokeCards() + " GPU emitters flame/smoke="
            + scheduledEmitters(schedules, GpuParticleEngine.VisualLayer.FLAMES) + "/"
            + scheduledEmitters(schedules, GpuParticleEngine.VisualLayer.SMOKE));
        feedback("War Mod quality requested=" + format(WarheadRenderSettings.qualityScale())
            + " effectiveAdaptive=" + format(gpu.adaptiveQuality())
            + " hardParticles=" + gpu.capacity()
            + " hardEmitters=" + budget.emitterCapacity()
             + " liveSlots=" + gpu.activeParticles()
             + " transientReserve=" + budget.protectedTransientSlots()
             + " fireReserve=" + budget.protectedFireSlots()
             + " persistentFree=" + budget.persistentAvailableSlots()
             + " transientFree=" + budget.transientAvailableSlots());
        ClientPerformanceTelemetry.Percentiles frame = performance.frame();
        ClientPerformanceTelemetry.Percentiles fireTiming = performance.fireExtraction();
        feedback("War Mod worldRenderMs p50/p95/p99/max=" + format(frame.p50Millis()) + "/"
            + format(frame.p95Millis()) + "/" + format(frame.p99Millis()) + "/"
            + format(frame.maximumMillis()) + " reportedFPS="
            + Minecraft.getInstance().getFps()
            + " fireExtractionMs p95/max=" + format(fireTiming.p95Millis()) + "/"
            + format(fireTiming.maximumMillis()));
    }

    private static String layerRoute(final GpuParticleEngine.DebugSnapshot gpu,
        final GpuParticleEngine.VisualLayer layer) {
        GpuParticleEngine.LayerHealth health = gpu.layerHealth().getOrDefault(layer,
            GpuParticleEngine.LayerHealth.FAILED);
        return health.name().toLowerCase(java.util.Locale.ROOT) + "->"
            + GpuParticleEngine.actualRoute(layer).toUpperCase(java.util.Locale.ROOT);
    }

    private static int bandCount(final java.util.Map<FireVisualBand, Integer> bands,
        final FireVisualBand band) {
        return bands.getOrDefault(band, 0);
    }

    private static int scheduledEmitters(final java.util.Map<GpuParticleEngine.VisualLayer,
        GpuParticleEngine.GpuLayerScheduleSnapshot> schedules,
        final GpuParticleEngine.VisualLayer layer) {
        GpuParticleEngine.GpuLayerScheduleSnapshot schedule = schedules.get(layer);
        return schedule == null ? 0 : schedule.emittersScheduled();
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
