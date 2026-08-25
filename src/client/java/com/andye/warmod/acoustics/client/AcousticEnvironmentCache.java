package com.andye.warmod.acoustics.client;

import com.andye.warmod.acoustics.model.AcousticResponseProfile;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/** Quantized, short-lived acoustic probes shared by every layer of an event. */
public final class AcousticEnvironmentCache {
    public static final AcousticEnvironmentCache INSTANCE = new AcousticEnvironmentCache();
    private static final int MAX_ENTRIES = 256;
    private static final long TTL_TICKS = 10L;
    private static final int TIMING_WINDOW = 120;
    private final LinkedHashMap<Key, Entry> entries = new LinkedHashMap<>();
    private final ArrayDeque<Long> probeNanos = new ArrayDeque<>(TIMING_WINDOW);
    private ClientLevel activeLevel;
    private long hits;
    private long misses;

    private AcousticEnvironmentCache() { }

    public synchronized boolean contains(final ClientLevel level, final Vec3 source,
        final Vec3 listener, final AcousticResponseProfile response, final long now) {
        ensureLevel(level);
        Entry entry = entries.get(Key.of(source, listener, response));
        return entry != null && now - entry.gameTime <= TTL_TICKS;
    }

    public synchronized AcousticEnvironment probe(final ClientLevel level,
        final Vec3 source, final Vec3 listener, final AcousticResponseProfile response,
        final long now) {
        ensureLevel(level);
        Key key = Key.of(source, listener, response);
        Entry cached = entries.get(key);
        if (cached != null && now - cached.gameTime <= TTL_TICKS) {
            hits++;
            return cached.environment;
        }
        long started = System.nanoTime();
        AcousticEnvironment environment = AcousticEnvironmentProbe.probe(
            level, source, listener, response);
        long elapsed = Math.max(0L, System.nanoTime() - started);
        if (probeNanos.size() == TIMING_WINDOW) probeNanos.removeFirst();
        probeNanos.addLast(elapsed);
        misses++;
        entries.put(key, new Entry(now, environment));
        while (entries.size() > MAX_ENTRIES) {
            Iterator<Key> iterator = entries.keySet().iterator();
            if (!iterator.hasNext()) break;
            iterator.next();
            iterator.remove();
        }
        return environment;
    }

    public synchronized void clear() {
        entries.clear();
        probeNanos.clear();
        activeLevel = null;
        hits = misses = 0L;
    }

    public synchronized DebugSnapshot debugSnapshot() {
        long[] values = new long[probeNanos.size()];
        int index = 0;
        for (long value : probeNanos) values[index++] = value;
        Arrays.sort(values);
        return new DebugSnapshot(entries.size(), hits, misses,
            percentileMillis(values, 0.50), percentileMillis(values, 0.95),
            percentileMillis(values, 0.99), values.length == 0 ? 0.0
                : values[values.length - 1] / 1_000_000.0);
    }

    private void ensureLevel(final ClientLevel level) {
        if (activeLevel == level) return;
        entries.clear();
        activeLevel = level;
    }

    private static double percentileMillis(final long[] sorted, final double percentile) {
        if (sorted.length == 0) return 0.0;
        int selected = Math.min(sorted.length - 1,
            Math.max(0, (int) Math.ceil(percentile * sorted.length) - 1));
        return sorted[selected] / 1_000_000.0;
    }

    private static long quantize(final double value, final int cellSize) {
        return (long) Math.floor(value / cellSize);
    }

    private record Key(long sourceX, long sourceY, long sourceZ,
        long listenerX, long listenerY, long listenerZ,
        AcousticResponseProfile response) {
        private static Key of(final Vec3 source, final Vec3 listener,
            final AcousticResponseProfile response) {
            return new Key(quantize(source.x, 8), quantize(source.y, 6),
                quantize(source.z, 8), quantize(listener.x, 4),
                quantize(listener.y, 4), quantize(listener.z, 4), response);
        }
    }
    private record Entry(long gameTime, AcousticEnvironment environment) { }
    public record DebugSnapshot(int entries, long hits, long misses,
        double p50Millis, double p95Millis, double p99Millis, double maximumMillis) { }
}
