package com.andye.warmod.diagnostics.client;

import java.util.ArrayDeque;
import java.util.Arrays;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

/** Bounded client frame/extraction timing with tail-latency percentiles. */
public final class ClientPerformanceTelemetry {
    private static final int WINDOW = 240;
    private static final Samples FRAME = new Samples();
    private static final Samples EXPLOSION = new Samples();
    private static final Samples FIRE = new Samples();
    private static final Samples GPU_ENGINE_CPU = new Samples();
    private static long frameStarted;
    private static boolean registered;

    private ClientPerformanceTelemetry() { }

    public static synchronized void register() {
        if (registered) return;
        LevelRenderEvents.START_MAIN.register(context -> frameStarted = System.nanoTime());
        LevelRenderEvents.END_MAIN.register(context -> {
            long started = frameStarted;
            frameStarted = 0L;
            if (started > 0L) FRAME.add(System.nanoTime() - started);
        });
        registered = true;
    }

    public static synchronized void recordExplosionNanos(final long nanos) {
        EXPLOSION.add(nanos);
    }
    public static synchronized void recordFireNanos(final long nanos) { FIRE.add(nanos); }
    public static synchronized void recordGpuEngineCpuNanos(final long nanos) {
        GPU_ENGINE_CPU.add(nanos);
    }

    public static synchronized DebugSnapshot debugSnapshot() {
        return new DebugSnapshot(FRAME.snapshot(), EXPLOSION.snapshot(), FIRE.snapshot(),
            GPU_ENGINE_CPU.snapshot());
    }

    public record Percentiles(double p50Millis, double p95Millis,
        double p99Millis, double maximumMillis) { }
    public record DebugSnapshot(Percentiles frame, Percentiles explosionExtraction,
        Percentiles fireExtraction, Percentiles gpuEngineCpu) { }

    private static final class Samples {
        private final ArrayDeque<Long> values = new ArrayDeque<>(WINDOW);
        private void add(final long nanos) {
            if (nanos < 0L) return;
            if (values.size() == WINDOW) values.removeFirst();
            values.addLast(nanos);
        }
        private Percentiles snapshot() {
            long[] sorted = new long[values.size()];
            int index = 0;
            for (long value : values) sorted[index++] = value;
            Arrays.sort(sorted);
            return new Percentiles(percentile(sorted, 0.50), percentile(sorted, 0.95),
                percentile(sorted, 0.99), sorted.length == 0 ? 0.0
                    : sorted[sorted.length - 1] / 1_000_000.0);
        }
        private static double percentile(final long[] sorted, final double fraction) {
            if (sorted.length == 0) return 0.0;
            int selected = Math.min(sorted.length - 1,
                Math.max(0, (int) Math.ceil(fraction * sorted.length) - 1));
            return sorted[selected] / 1_000_000.0;
        }
    }
}
