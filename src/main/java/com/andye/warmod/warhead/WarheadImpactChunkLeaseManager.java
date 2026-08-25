package com.andye.warmod.warhead;

import com.andye.warmod.diagnostics.WarModPerformanceDiagnostics;
import com.andye.warmod.icbm.IcbmChunkTicketRegistry;
import com.andye.warmod.icbm.IcbmConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

public final class WarheadImpactChunkLeaseManager {
    private static final Map<ServerLevel, Map<UUID, Lease>> LEASES =
        new WeakHashMap<>();

    private static boolean registered;

    private WarheadImpactChunkLeaseManager() {
    }

    public static void registerLifecycle() {
        if (registered) {
            return;
        }

        ServerTickEvents.END_SERVER_TICK.register(
            WarheadImpactChunkLeaseManager::tick
        );
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
        registered = true;
    }

    /**
     * Acquires the full defensive final-approach area as soon as the terminal
     * warhead is spawned. The corridor extends from impact back toward the
     * incoming path even when the actual separation point is closer than the
     * defence engagement range.
     */
    public static synchronized void holdApproach(
        final ServerLevel level,
        final UUID effectId,
        final Vec3 start,
        final Vec3 impact,
        final int ticks
    ) {
        if (start == null || impact == null
            || !start.isFinite() || !impact.isFinite()) {
            throw new IllegalArgumentException(
                "Final-approach chunk lease positions must be finite"
            );
        }

        HashSet<ChunkPos> chunks = new HashSet<>();
        addImpactWindow(chunks, impact);

        Vec3 outward = new Vec3(
            start.x - impact.x,
            0.0,
            start.z - impact.z
        );

        if (outward.lengthSqr() > 1.0E-8) {
            Vec3 corridorStart = impact.add(
                outward.normalize().scale(
                    IcbmConstants.FINAL_APPROACH_CORRIDOR_BLOCKS
                )
            );

            IcbmChunkTicketRegistry.addSegmentWindow(
                chunks,
                corridorStart,
                impact,
                IcbmConstants.FINAL_APPROACH_CORRIDOR_RADIUS,
                IcbmConstants.FINAL_APPROACH_SAMPLE_SPACING_BLOCKS
            );
        }

        extend(level, effectId, chunks, ticks);
        WarheadPreImpactPreparationManager.schedule(level, effectId, impact, ticks);
    }

    /** Replaces the flight corridor with only the authoritative impact window. */
    public static synchronized void hold(
        final ServerLevel level,
        final UUID effectId,
        final Vec3 impact,
        final int ticks
    ) {
        if (impact == null || !impact.isFinite()) {
            throw new IllegalArgumentException(
                "Impact chunk lease position must be finite"
            );
        }

        HashSet<ChunkPos> chunks = new HashSet<>();
        addImpactWindow(chunks, impact);
        replace(level, effectId, chunks, ticks);
    }

    /**
     * Returns true only after the complete leased area is loaded at simulation
     * level. The ICBM controller uses this as a hard terminal-separation gate.
     */
    public static synchronized boolean ready(
        final ServerLevel level,
        final UUID id
    ) {
        Map<UUID, Lease> leases = LEASES.get(level);
        Lease lease = leases == null ? null : leases.get(id);

        return lease != null
            && !lease.chunks().isEmpty()
            && IcbmChunkTicketRegistry.allLoaded(level, lease.chunks());
    }

    public static synchronized void release(
        final ServerLevel level,
        final UUID id
    ) {
        Map<UUID, Lease> leases = LEASES.get(level);

        if (leases == null) {
            return;
        }

        Lease lease = leases.remove(id);

        if (lease != null) {
            IcbmChunkTicketRegistry.releaseAll(level, lease.chunks());
        }

        if (leases.isEmpty()) {
            LEASES.remove(level);
        }
    }

    private static void addImpactWindow(
        final Set<ChunkPos> chunks,
        final Vec3 impact
    ) {
        IcbmChunkTicketRegistry.addWindow(
            chunks,
            IcbmChunkTicketRegistry.chunk(impact),
            IcbmConstants.IMPACT_CHUNK_RADIUS
        );
    }

    private static void extend(
        final ServerLevel level,
        final UUID effectId,
        final Set<ChunkPos> requested,
        final int ticks
    ) {
        Map<UUID, Lease> leases =
            LEASES.computeIfAbsent(level, ignored -> new HashMap<>());
        Lease existing = leases.get(effectId);
        HashSet<ChunkPos> combined = new HashSet<>(requested);
        long expiresAt = level.getGameTime() + Math.max(1, ticks);

        if (existing != null) {
            combined.addAll(existing.chunks());
            expiresAt = Math.max(expiresAt, existing.expiresAt());
        }

        Set<ChunkPos> previouslyHeld = existing == null
            ? Set.of()
            : existing.chunks();

        for (ChunkPos chunk : combined) {
            if (!previouslyHeld.contains(chunk)) {
                IcbmChunkTicketRegistry.acquire(level, chunk);
            }
        }

        leases.put(effectId, new Lease(Set.copyOf(combined), expiresAt, false));
    }

    private static void replace(
        final ServerLevel level,
        final UUID effectId,
        final Set<ChunkPos> requested,
        final int ticks
    ) {
        Map<UUID, Lease> leases =
            LEASES.computeIfAbsent(level, ignored -> new HashMap<>());
        Lease existing = leases.get(effectId);
        Set<ChunkPos> previouslyHeld = existing == null ? Set.of() : existing.chunks();
        Set<ChunkPos> replacement = Set.copyOf(requested);
        for (ChunkPos chunk : replacement) {
            if (!previouslyHeld.contains(chunk)) IcbmChunkTicketRegistry.acquire(level, chunk);
        }
        for (ChunkPos chunk : previouslyHeld) {
            if (!replacement.contains(chunk)) IcbmChunkTicketRegistry.release(level, chunk);
        }
        long expiresAt = level.getGameTime() + Math.max(1, ticks);
        leases.put(effectId, new Lease(replacement, expiresAt, true));
    }

    private static synchronized void tick(final MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            Map<UUID, Lease> leases = LEASES.get(level);

            if (leases == null) {
                continue;
            }

            for (UUID id : new ArrayList<>(leases.keySet())) {
                Lease lease = leases.get(id);
                boolean workComplete = lease.impactOnly()
                    && !WarheadExplosionWorkManager.hasPendingWork(level, id)
                    && !WarheadGlassShockwaveManager.hasPendingWork(level, id);
                if (level.getGameTime() >= lease.expiresAt() || workComplete) {
                    release(level, id);
                }
            }
        }
        long activeLeases = 0L;
        for (Map<UUID, Lease> leases : LEASES.values()) activeLeases += leases.size();
        WarModPerformanceDiagnostics.gauge(
            WarModPerformanceDiagnostics.Gauge.ACTIVE_CHUNK_LEASES, activeLeases);
    }

    private static synchronized void clear() {
        for (Map.Entry<ServerLevel, Map<UUID, Lease>> levelEntry
            : LEASES.entrySet()) {
            for (Lease lease : levelEntry.getValue().values()) {
                IcbmChunkTicketRegistry.releaseAll(
                    levelEntry.getKey(),
                    lease.chunks()
                );
            }
        }

        LEASES.clear();
    }

    private record Lease(Set<ChunkPos> chunks, long expiresAt, boolean impactOnly) {
    }
}
