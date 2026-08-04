package com.andye.warmod.phalanx.client;

import com.andye.warmod.phalanx.network.ClientboundPhalanxShotPayload;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public final class PhalanxTracerManager {
    private static final int MAX_TRACERS = 4096;
    private static final double MAX_VISUAL_AGE_TICKS = 180.0;

    public record Tracer(
        UUID id,
        Vec3 origin,
        Vec3 velocity,
        long seed,
        double startTime
    ) {
    }

    private static final Map<UUID, Tracer> ACTIVE =
        new LinkedHashMap<>();

    private PhalanxTracerManager() {
    }

    public static synchronized void shot(
        final ClientboundPhalanxShotPayload payload,
        final double time
    ) {
        while (
            ACTIVE.size()
                >= MAX_TRACERS
        ) {
            ACTIVE.remove(
                ACTIVE.keySet()
                    .iterator()
                    .next()
            );
        }

        ACTIVE.put(
            payload.shotId(),
            new Tracer(
                payload.shotId(),
                payload.origin(),
                payload.velocity(),
                payload.visualSeed(),
                time
            )
        );
    }

    public static synchronized void impact(
        final UUID id
    ) {
        ACTIVE.remove(id);
    }

    public static synchronized List<Tracer> snapshot(
        final double now
    ) {
        ACTIVE.values().removeIf(tracer ->
            now - tracer.startTime()
                > MAX_VISUAL_AGE_TICKS
        );

        return List.copyOf(
            ACTIVE.values()
        );
    }

    public static synchronized void clear() {
        ACTIVE.clear();
    }
}
