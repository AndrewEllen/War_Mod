package com.andye.warmod.warhead.obscuration;

import com.andye.warmod.diagnostics.WarModPerformanceDiagnostics;
import com.andye.warmod.warhead.obscuration.network.ClientboundNuclearTerrainObscurationPayload;
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
 * One-way server API for authoritative terrain-mutation obscuration progress.
 */
public final class NuclearTerrainObscurationEmitter {
    private static final int MAX_IMPACTS_PER_LEVEL = 128;
    private static final Map<ServerLevel, Map<UUID, EmissionState>> STATES = new IdentityHashMap<>();
    private static boolean registered;

    private NuclearTerrainObscurationEmitter() { }

    public static synchronized void registerLifecycle() {
        if (registered) return;
        ServerLevelEvents.UNLOAD.register((server, level) -> {
            synchronized (NuclearTerrainObscurationEmitter.class) { STATES.remove(level); }
        });
        registered = true;
    }

    /**
     * Root hook called after authoritative terrain work advances. The completed
     * interior radius drives the centre-out reveal independently of the moving front.
     */
    public static synchronized void mutationProgress(final ServerLevel level, final UUID impactId,
        final Vec3 center, final long visualSeed, final float visualScale,
        final double destructionRadius, final double currentMutationRadius,
        final double completedInteriorRadius, final boolean finalBand) {
        if (level == null || impactId == null || center == null || !center.isFinite()
            || !Double.isFinite(destructionRadius) || destructionRadius <= 0.0
            || !Double.isFinite(currentMutationRadius)
            || !Double.isFinite(completedInteriorRadius)) return;
        Map<UUID, EmissionState> states = STATES.computeIfAbsent(level, ignored -> new HashMap<>());
        while (states.size() >= MAX_IMPACTS_PER_LEVEL && !states.containsKey(impactId)) {
            Iterator<UUID> iterator = states.keySet().iterator();
            if (!iterator.hasNext()) break;
            iterator.next(); iterator.remove();
        }
        long now = level.getGameTime();
        EmissionState previous = states.get(impactId);
        double emittedFrom = previous == null ? 0.0 : previous.lastMutationRadius;
        double emittedTo = Mth.clamp(Math.max(emittedFrom, currentMutationRadius),
            emittedFrom, destructionRadius);
        double completed = Mth.clamp(Math.max(previous == null ? 0.0
            : previous.completedInteriorRadius, completedInteriorRadius), 0.0, emittedTo);
        if (!finalBand && previous != null && now - previous.lastSentTick < 2L
            && emittedTo - emittedFrom < 8.0
            && completed - previous.completedInteriorRadius < 8.0) return;
        float from = (float) Mth.clamp(emittedFrom, 0.0, 2_048.0);
        float to = (float) Mth.clamp(emittedTo, from, 2_048.0);
        long diagnosticsStarted = WarModPerformanceDiagnostics.begin();
        int recipients = NuclearTerrainObscurationNetworking.send(level,
            new ClientboundNuclearTerrainObscurationPayload(
            impactId, now, center.x, center.y, center.z, visualSeed,
            Mth.clamp(visualScale, 0.05F, 8.0F),
            (float) Mth.clamp(destructionRadius, 0.1, 2_048.0), from, to,
            (float) completed, finalBand), center);
        WarModPerformanceDiagnostics.add(
            WarModPerformanceDiagnostics.Gauge.TERRAIN_OBSCURATION_EMISSIONS, 1L);
        WarModPerformanceDiagnostics.add(
            WarModPerformanceDiagnostics.Gauge.TERRAIN_OBSCURATION_RECIPIENTS, recipients);
        WarModPerformanceDiagnostics.record(
            WarModPerformanceDiagnostics.Subsystem.TERRAIN_OBSCURATION_SEND,
            diagnosticsStarted);
        states.put(impactId, new EmissionState(emittedTo, completed, now));
        if (finalBand) states.remove(impactId);
    }

    private record EmissionState(double lastMutationRadius,
        double completedInteriorRadius, long lastSentTick) { }
}
