package com.andye.warmod.diagnostics;

import com.andye.warmod.WarMod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Opt-in, aggregate timing for the mod's expensive server-side systems.
 * Timings are intentionally coarse: recording every source line would itself
 * make a TPS investigation invalid. All mutation hooks remain on the server
 * thread and this class never reads a level from another thread.
 */
public final class WarModPerformanceDiagnostics {
    public enum Subsystem {
        WAR_MOD_SCHEDULED_WORK("War Mod scheduled work total"),
        NUCLEAR_CRATER("nuclear crater mutation"),
        NUCLEAR_WAVE("nuclear aftermath wave"),
        NUCLEAR_PREPARATION("nuclear terrain preparation"),
        FIRE_SIMULATION("fire simulation"),
        FIRE_SNAPSHOT_PREPARATION("fire snapshot preparation"),
        FIRE_NETWORK("fire visual networking"),
        CURTAIN_SEND("nuclear curtain networking");

        private final String label;
        Subsystem(final String label) { this.label = label; }
        String label() { return label; }
    }

    public enum Gauge {
        ACTIVE_NUCLEAR_CRATERS("active nuclear craters"),
        PENDING_CRATER_BLOCK_MUTATIONS("pending crater block mutations"),
        ACTIVE_CHUNK_LEASES("active chunk leases"),
        ACTIVE_NUCLEAR_WAVES("active nuclear waves"),
        ACTIVE_NUCLEAR_PREPARATIONS("active terrain preparations"),
        PENDING_NUCLEAR_MUTATIONS("pending prepared terrain mutations"),
        ACTIVE_FIRE_PATCHES("active fire patches"),
        DORMANT_FIRE_PATCHES("dormant fire patches"),
        ACTIVE_FIRE_EMBERS("active fire embers"),
        FIRE_SNAPSHOT_IN_PROGRESS("fire snapshot in progress"),
        FIRE_SNAPSHOT_PENDING_PATCHES("fire snapshot patches remaining"),
        CURTAIN_EMISSIONS("curtain emissions"),
        CURTAIN_RECIPIENTS("curtain packet recipients"),
        FIRE_SNAPSHOT_PACKETS_SENT("fire snapshot packets sent"),
        FIRE_VIEWER_NEARBY_CANDIDATES("fire viewer nearby candidates"),
        FIRE_VIEWER_SELECTED_PATCHES("fire viewer selected patches"),
        FIRE_SNAPSHOT_ENTRIES_BUILT("fire snapshot entries built"),
        FIRE_COMPLETE_SNAPSHOTS("fire complete snapshots"),
        FIRE_NETWORK_SENT_PATCH_ENTRIES("fire network sent patch entries"),
        SCHEDULER_OVERRUNS("shared scheduler deadline overruns");

        private final String label;
        Gauge(final String label) { this.label = label; }
        String label() { return label; }
    }

    private static final int TICK_WINDOW = 120;
    private static final int OVERLAY_INTERVAL_TICKS = 10;
    private static final DateTimeFormatter REPORT_TIME = DateTimeFormatter
        .ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final EnumMap<Subsystem, Timing> TIMINGS = new EnumMap<>(Subsystem.class);
    private static final EnumMap<Gauge, GaugeValue> GAUGES = new EnumMap<>(Gauge.class);
    private static final ArrayDeque<Long> RECENT_TICKS_NANOS = new ArrayDeque<>(TICK_WINDOW);
    private static final Set<UUID> OVERLAY_VIEWERS = new HashSet<>();
    private static long tickStartNanos;
    private static long totalTickNanos;
    private static long tickSamples;
    private static long peakTickNanos;
    private static long serverTickCounter;
    private static boolean registered;

    static {
        for (Subsystem subsystem : Subsystem.values()) TIMINGS.put(subsystem, new Timing());
        for (Gauge gauge : Gauge.values()) GAUGES.put(gauge, new GaugeValue());
    }

    private WarModPerformanceDiagnostics() { }

    public static void registerLifecycle() {
        if (registered) return;
        ServerTickEvents.START_SERVER_TICK.register(WarModPerformanceDiagnostics::startTick);
        ServerTickEvents.END_SERVER_TICK.register(WarModPerformanceDiagnostics::finishTick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            synchronized (WarModPerformanceDiagnostics.class) {
                OVERLAY_VIEWERS.clear();
                tickStartNanos = 0L;
            }
        });
        registered = true;
    }

    public static long begin() { return System.nanoTime(); }

    public static synchronized void record(final Subsystem subsystem, final long startedNanos) {
        if (subsystem == null || startedNanos <= 0L) return;
        long elapsed = Math.max(0L, System.nanoTime() - startedNanos);
        TIMINGS.get(subsystem).add(elapsed);
    }

    public static synchronized void recordNanos(final Subsystem subsystem, final long elapsedNanos) {
        if (subsystem != null && elapsedNanos >= 0L)
            TIMINGS.get(subsystem).add(elapsedNanos);
    }

    public static synchronized void gauge(final Gauge gauge, final long value) {
        if (gauge != null) GAUGES.get(gauge).observe(Math.max(0L, value));
    }

    public static synchronized void add(final Gauge gauge, final long amount) {
        if (gauge != null && amount > 0L) GAUGES.get(gauge).add(amount);
    }

    public static synchronized boolean toggleOverlay(final ServerPlayer player) {
        UUID id = player.getUUID();
        if (!OVERLAY_VIEWERS.add(id)) {
            OVERLAY_VIEWERS.remove(id);
            return false;
        }
        return true;
    }

    public static synchronized void setOverlay(final ServerPlayer player, final boolean enabled) {
        if (enabled) OVERLAY_VIEWERS.add(player.getUUID());
        else OVERLAY_VIEWERS.remove(player.getUUID());
    }

    public static synchronized void reset() {
        RECENT_TICKS_NANOS.clear();
        totalTickNanos = 0L;
        tickSamples = 0L;
        peakTickNanos = 0L;
        for (Timing timing : TIMINGS.values()) timing.reset();
        for (GaugeValue value : GAUGES.values()) value.reset();
    }

    public static synchronized Path exportReport(final MinecraftServer server) throws IOException {
        Path directory = server.getWorldPath(LevelResource.ROOT).resolve("war_mod_diagnostics");
        Files.createDirectories(directory);
        Path report = directory.resolve("performance-" + REPORT_TIME.format(Instant.now()) + ".txt");
        Files.writeString(report, buildReport());
        return report;
    }

    public static synchronized String compactStatus() {
        double averageMspt = recentAverageMspt();
        double tps = averageMspt <= 0.0 ? 20.0 : Math.min(20.0, 1_000.0 / averageMspt);
        return String.format(Locale.ROOT,
            "War Mod TPS %.1f | MSPT %.2f | crater %.2f | wave %.2f | fire %.2f",
            tps, averageMspt, TIMINGS.get(Subsystem.NUCLEAR_CRATER).recentAverageMillis(),
            TIMINGS.get(Subsystem.NUCLEAR_WAVE).recentAverageMillis(),
            TIMINGS.get(Subsystem.FIRE_SIMULATION).recentAverageMillis());
    }

    private static synchronized void startTick(final MinecraftServer server) {
        tickStartNanos = System.nanoTime();
    }

    private static synchronized void finishTick(final MinecraftServer server) {
        if (tickStartNanos <= 0L) return;
        long elapsed = Math.max(0L, System.nanoTime() - tickStartNanos);
        tickStartNanos = 0L;
        if (RECENT_TICKS_NANOS.size() == TICK_WINDOW) RECENT_TICKS_NANOS.removeFirst();
        RECENT_TICKS_NANOS.addLast(elapsed);
        totalTickNanos += elapsed;
        tickSamples++;
        peakTickNanos = Math.max(peakTickNanos, elapsed);
        serverTickCounter++;
        if (serverTickCounter % OVERLAY_INTERVAL_TICKS != 0L || OVERLAY_VIEWERS.isEmpty()) return;
        Component message = Component.literal(compactStatus());
        for (UUID id : Set.copyOf(OVERLAY_VIEWERS)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                OVERLAY_VIEWERS.remove(id);
            } else {
                player.sendSystemMessage(message, true);
            }
        }
    }

    private static String buildReport() {
        StringBuilder report = new StringBuilder(1_500);
        report.append("War Mod performance diagnostics\n")
            .append("Generated UTC: ").append(Instant.now()).append('\n')
            .append("Scope: aggregate server-thread timings; not a per-line trace.\n\n")
            .append("Server tick observer (last ").append(RECENT_TICKS_NANOS.size())
            .append(" ticks): ").append(format(recentAverageMspt())).append(" mspt, ")
            .append(format(recentTps())).append(" TPS, p50=")
            .append(format(recentTickPercentile(0.50))).append(" ms, p95=")
            .append(format(recentTickPercentile(0.95))).append(" ms, p99=")
            .append(format(recentTickPercentile(0.99))).append(" ms\n")
            .append("Server tick observer lifetime: ").append(tickSamples).append(" samples, avg ")
            .append(format(nanosToMillis(tickSamples == 0L ? 0L : totalTickNanos / tickSamples)))
            .append(" mspt, peak ").append(format(nanosToMillis(peakTickNanos))).append(" ms\n\n")
            .append("Timed subsystems since last /icpm reset:\n");
        for (Subsystem subsystem : Subsystem.values()) {
            Timing timing = TIMINGS.get(subsystem);
            report.append("- ").append(subsystem.label()).append(": calls=")
                .append(timing.samples).append(", avg=")
                .append(format(timing.averageMillis())).append(" ms, peak=")
                .append(format(timing.peakMillis())).append(" ms, recent=")
                .append(format(timing.recentAverageMillis())).append(" ms, p50=")
                .append(format(timing.recentPercentileMillis(0.50))).append(" ms, p95=")
                .append(format(timing.recentPercentileMillis(0.95))).append(" ms, p99=")
                .append(format(timing.recentPercentileMillis(0.99))).append(" ms\n");
        }
        report.append("\nCounters/gauges since last /icpm reset:\n");
        for (Gauge gauge : Gauge.values()) {
            GaugeValue value = GAUGES.get(gauge);
            report.append("- ").append(gauge.label()).append(": last=")
                .append(value.last).append(", peak=").append(value.peak)
                .append(", total=").append(value.total).append('\n');
        }
        report.append("\nInterpretation:\n")
            .append("- NUCLEAR_CRATER is the authoritative excavation writer; NUCLEAR_WAVE is prepared surface, vegetation, glass, fire and biome aftermath.\n")
            .append("- NUCLEAR_PREPARATION is loaded-chunk terrain discovery.\n")
            .append("- FIRE_SIMULATION includes the authoritative fire tick; FIRE_SNAPSHOT_PREPARATION and FIRE_NETWORK isolate visual state work.\n")
            .append("- CURTAIN_SEND measures server emission/packet dispatch only. Client receipt and GPU rendering are intentionally not sampled by the server.\n");
        report.append("- FIRE_SIMULATION is an inclusive parent timing; snapshot and network timings are nested diagnostics and must not be added to it.\n");
        return report.toString();
    }

    private static double recentAverageMspt() {
        if (RECENT_TICKS_NANOS.isEmpty()) return 0.0;
        long total = 0L;
        for (long value : RECENT_TICKS_NANOS) total += value;
        return nanosToMillis(total / RECENT_TICKS_NANOS.size());
    }

    private static double recentTps() {
        double mspt = recentAverageMspt();
        return mspt <= 0.0 ? 20.0 : Math.min(20.0, 1_000.0 / mspt);
    }

    private static double recentTickPercentile(final double percentile) {
        if (RECENT_TICKS_NANOS.isEmpty()) return 0.0;
        long[] sorted = new long[RECENT_TICKS_NANOS.size()];
        int index = 0;
        for (long value : RECENT_TICKS_NANOS) sorted[index++] = value;
        Arrays.sort(sorted);
        int selected = Math.min(sorted.length - 1,
            Math.max(0, (int) Math.ceil(percentile * sorted.length) - 1));
        return nanosToMillis(sorted[selected]);
    }

    private static double nanosToMillis(final long nanos) { return nanos / 1_000_000.0; }
    private static String format(final double value) { return String.format(Locale.ROOT, "%.2f", value); }

    private static final class Timing {
        private static final int RECENT_WINDOW = 60;
        private final ArrayDeque<Long> recent = new ArrayDeque<>(RECENT_WINDOW);
        private long total;
        private long samples;
        private long peak;

        private void add(final long nanos) {
            if (recent.size() == RECENT_WINDOW) recent.removeFirst();
            recent.addLast(nanos);
            total += nanos;
            samples++;
            peak = Math.max(peak, nanos);
        }
        private double averageMillis() { return nanosToMillis(samples == 0L ? 0L : total / samples); }
        private double peakMillis() { return nanosToMillis(peak); }
        private double recentAverageMillis() {
            if (recent.isEmpty()) return 0.0;
            long sum = 0L;
            for (long value : recent) sum += value;
            return nanosToMillis(sum / recent.size());
        }
        private double recentPercentileMillis(final double percentile) {
            if (recent.isEmpty()) return 0.0;
            long[] sorted = new long[recent.size()];
            int index = 0;
            for (long value : recent) sorted[index++] = value;
            Arrays.sort(sorted);
            int selected = Math.min(sorted.length - 1,
                Math.max(0, (int) Math.ceil(percentile * sorted.length) - 1));
            return nanosToMillis(sorted[selected]);
        }
        private void reset() { recent.clear(); total = 0L; samples = 0L; peak = 0L; }
    }

    private static final class GaugeValue {
        private long last;
        private long peak;
        private long total;
        private void observe(final long value) { last = value; peak = Math.max(peak, value); }
        private void add(final long amount) { last = amount; total += amount; peak = Math.max(peak, amount); }
        private void reset() { last = 0L; peak = 0L; total = 0L; }
    }
}
