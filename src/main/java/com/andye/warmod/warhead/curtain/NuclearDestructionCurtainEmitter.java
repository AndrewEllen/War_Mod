package com.andye.warmod.warhead.curtain;

import com.andye.warmod.diagnostics.WarModPerformanceDiagnostics;
import com.andye.warmod.warhead.curtain.network.ClientboundNuclearCurtainPayload;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * One-way server API for an analytical, client-only destruction curtain.
 * It deliberately knows nothing about terrain preparation, mutation queues, or shockwave visuals.
 */
public final class NuclearDestructionCurtainEmitter {
    private static final int MAX_IMPACTS_PER_LEVEL = 128;
    private static final Map<ServerLevel, Map<UUID, EmissionState>> STATES = new IdentityHashMap<>();
    private static boolean registered;

    private NuclearDestructionCurtainEmitter() { }

    public static synchronized void registerLifecycle() {
        if (registered) return;
        ServerLevelEvents.UNLOAD.register((server, level) -> {
            synchronized (NuclearDestructionCurtainEmitter.class) { STATES.remove(level); }
        });
        registered = true;
    }

    /**
     * Root hook: call after the authoritative nuclear pressure front crosses one or more shells.
     * The caller supplies only shell radii; this subsystem does not read or alter the world.
     */
    public static synchronized void crossedShells(final ServerLevel level, final UUID impactId,
        final Vec3 center, final long visualSeed, final float visualScale,
        final double previousRadius, final double currentRadius, final boolean finalBand) {
        if (level == null || impactId == null || center == null || !center.isFinite()
            || !Double.isFinite(previousRadius) || !Double.isFinite(currentRadius)
            || currentRadius < previousRadius || currentRadius < 0.0) return;
        Map<UUID, EmissionState> states = STATES.computeIfAbsent(level, ignored -> new HashMap<>());
        while (states.size() >= MAX_IMPACTS_PER_LEVEL && !states.containsKey(impactId)) {
            Iterator<UUID> iterator = states.keySet().iterator();
            if (!iterator.hasNext()) break;
            iterator.next(); iterator.remove();
        }
        long now = level.getGameTime();
        EmissionState previous = states.get(impactId);
        double emittedFrom = previous == null ? previousRadius : previous.lastRadius;
        double emittedTo = Math.max(emittedFrom, currentRadius);
        if (!finalBand && previous != null && now - previous.lastSentTick < 2L
            && emittedTo - emittedFrom < 8.0) return;
        float from = (float) Mth.clamp(emittedFrom, 0.0, 2_048.0);
        float to = (float) Mth.clamp(emittedTo, from, 2_048.0);
        long diagnosticsStarted = WarModPerformanceDiagnostics.begin();
        int recipients = NuclearDestructionCurtainNetworking.send(level, new ClientboundNuclearCurtainPayload(
            impactId, now, center.x, center.y, center.z, visualSeed,
            Mth.clamp(visualScale, 0.05F, 8.0F), from, to, finalBand), center);
        WarModPerformanceDiagnostics.add(
            WarModPerformanceDiagnostics.Gauge.CURTAIN_EMISSIONS, 1L);
        WarModPerformanceDiagnostics.add(
            WarModPerformanceDiagnostics.Gauge.CURTAIN_RECIPIENTS, recipients);
        WarModPerformanceDiagnostics.record(
            WarModPerformanceDiagnostics.Subsystem.CURTAIN_SEND, diagnosticsStarted);
        states.put(impactId, new EmissionState(emittedTo, now));
        if (finalBand) states.remove(impactId);
    }

    private record EmissionState(double lastRadius, long lastSentTick) { }
}
