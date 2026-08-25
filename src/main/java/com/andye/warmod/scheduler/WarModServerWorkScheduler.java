package com.andye.warmod.scheduler;

import com.andye.warmod.diagnostics.WarModPerformanceDiagnostics;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * One cooperative main-thread deadline shared by all heavy War Mod systems.
 * Work remains authoritative and server-thread-only; a permit merely limits how
 * long a resumable queue may drain during the current tick.
 */
public final class WarModServerWorkScheduler {
    public static final long NORMAL_TARGET_NANOS = 8_000_000L;
    public static final long HARD_MAXIMUM_NANOS = 12_000_000L;

    public enum WorkClass {
        IMPACT_CRITICAL(8),
        MISSILE_STREAMING(7),
        CRATER_COMMIT(6),
        ENTITY_BLAST(5),
        FIRE_ACTIVE(4),
        NUCLEAR_AFTERMATH(3),
        FIRE_NETWORK(2),
        BACKGROUND_PREP(1);

        private final int weight;
        WorkClass(final int weight) { this.weight = weight; }
        public int weight() { return weight; }
    }

    private static final Map<MinecraftServer, TickState> STATES = new WeakHashMap<>();
    private static boolean registered;

    private WarModServerWorkScheduler() { }

    public static synchronized void registerLifecycle() {
        if (registered) return;
        ServerTickEvents.START_SERVER_TICK.register(WarModServerWorkScheduler::beginTick);
        ServerTickEvents.END_SERVER_TICK.register(WarModServerWorkScheduler::finishTick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            synchronized (WarModServerWorkScheduler.class) { STATES.remove(server); }
        });
        registered = true;
    }

    public static synchronized WorkPermit acquire(final ServerLevel level,
        final WorkClass workClass, final long requestedNanos) {
        if (level == null || workClass == null || requestedNanos <= 0L)
            return WorkPermit.UNAVAILABLE;
        MinecraftServer server = level.getServer();
        TickState state = STATES.computeIfAbsent(server, ignored -> TickState.begin());
        long now = System.nanoTime();
        long classBudget = workClass.weight() >= WorkClass.CRATER_COMMIT.weight()
            ? HARD_MAXIMUM_NANOS : NORMAL_TARGET_NANOS;
        long reservedForOtherClasses = state.reservations.entrySet().stream()
            .filter(entry -> entry.getKey() != workClass)
            .mapToLong(Map.Entry::getValue).sum();
        long remaining = Math.max(0L,
            classBudget - state.totalWorkNanos - state.reservedNanos
                - reservedForOtherClasses);
        if (remaining <= 0L) return WorkPermit.UNAVAILABLE;
        state.reservations.remove(workClass);
        long granted = Math.min(requestedNanos, remaining);
        state.reservedNanos += granted;
        long deadline = saturatedAdd(now, granted);
        return new WorkPermit(state, workClass, now, deadline, granted);
    }

    /**
     * Reserves a same-tick slice before a higher-volume class starts draining
     * the shared deadline. The reservation is released when its owner acquires.
     */
    public static synchronized void reserve(final ServerLevel level,
        final WorkClass workClass, final long nanos) {
        if (level == null || workClass == null || nanos <= 0L) return;
        TickState state = STATES.computeIfAbsent(level.getServer(), ignored -> TickState.begin());
        state.reservations.merge(workClass, Math.min(nanos, NORMAL_TARGET_NANOS), Math::max);
    }

    public static synchronized long hardDeadline(final ServerLevel level) {
        if (level == null) return System.nanoTime();
        TickState state = STATES.get(level.getServer());
        if (state == null) return System.nanoTime();
        return saturatedAdd(System.nanoTime(),
            Math.max(0L, HARD_MAXIMUM_NANOS - state.totalWorkNanos
                - state.reservedNanos));
    }

    private static synchronized void beginTick(final MinecraftServer server) {
        STATES.put(server, TickState.begin());
    }

    private static synchronized void finishTick(final MinecraftServer server) {
        TickState state = STATES.get(server);
        if (state == null) return;
        WarModPerformanceDiagnostics.recordNanos(
            WarModPerformanceDiagnostics.Subsystem.WAR_MOD_SCHEDULED_WORK,
            state.totalWorkNanos);
        WarModPerformanceDiagnostics.gauge(
            WarModPerformanceDiagnostics.Gauge.SCHEDULER_OVERRUNS, state.overruns);
    }

    private static long saturatedAdd(final long left, final long right) {
        long result = left + right;
        return result < left ? Long.MAX_VALUE : result;
    }

    public static final class WorkPermit implements AutoCloseable {
        private static final WorkPermit UNAVAILABLE = new WorkPermit();
        private final TickState state;
        private final WorkClass workClass;
        private final long startedNanos;
        private final long deadlineNanos;
        private final long reservedNanos;
        private boolean closed;

        private WorkPermit() {
            state = null; workClass = null; startedNanos = 0L; deadlineNanos = 0L;
            reservedNanos = 0L;
            closed = true;
        }

        private WorkPermit(final TickState state, final WorkClass workClass,
            final long startedNanos, final long deadlineNanos, final long reservedNanos) {
            this.state = state;
            this.workClass = workClass;
            this.startedNanos = startedNanos;
            this.deadlineNanos = deadlineNanos;
            this.reservedNanos = reservedNanos;
        }

        public boolean available() { return state != null && deadlineNanos > startedNanos; }
        public long deadlineNanos() { return deadlineNanos; }

        @Override public void close() {
            if (closed || state == null) return;
            closed = true;
            long finished = System.nanoTime();
            long elapsed = Math.max(0L, finished - startedNanos);
            synchronized (WarModServerWorkScheduler.class) {
                state.reservedNanos = Math.max(0L, state.reservedNanos - reservedNanos);
                state.totalWorkNanos += elapsed;
                state.byClass.merge(workClass, elapsed, Long::sum);
                if (finished > deadlineNanos) state.overruns++;
            }
        }
    }

    private static final class TickState {
        private final long tickStartNanos;
        private final EnumMap<WorkClass, Long> byClass = new EnumMap<>(WorkClass.class);
        private final EnumMap<WorkClass, Long> reservations = new EnumMap<>(WorkClass.class);
        private long totalWorkNanos;
        private long reservedNanos;
        private long overruns;

        private TickState(final long tickStartNanos) {
            this.tickStartNanos = tickStartNanos;
        }

        private static TickState begin() { return new TickState(System.nanoTime()); }
    }
}
