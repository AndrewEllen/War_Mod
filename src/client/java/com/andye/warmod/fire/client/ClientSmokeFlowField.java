package com.andye.warmod.fire.client;

import com.andye.warmod.fire.FireSurfaceAnchor;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Bounded client-only clearance sampling for analytical fire smoke. This is not a
 * voxel fluid simulation: a small cached probe tells the renderer where a roof and
 * the nearest lateral escape are, so smoke pools under ceilings and turns toward
 * openings without adding any work to the server tick.
 */
public final class ClientSmokeFlowField {
    public static final ClientSmokeFlowField INSTANCE = new ClientSmokeFlowField();
    private static final int MAX_PROBES_PER_TICK = 48;
    private static final int MAX_CACHE_ENTRIES = 2_048;
    private static final int CACHE_TICKS = 80;
    private static final int MAX_VERTICAL_PROBE = 9;
    private static final int MAX_LATERAL_PROBE = 7;
    private static final SmokeFlow OPEN = new SmokeFlow(8.5F, 3.0F,
        Vec3.ZERO, false, 1.0F);

    private final Map<Long, CachedFlow> cache = new LinkedHashMap<>();
    private final ArrayDeque<ProbeRequest> pending = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();
    private ClientLevel activeLevel;

    private ClientSmokeFlowField() { }

    public synchronized SmokeFlow request(final ClientLevel level,
        final FireSurfaceAnchor anchor, final long now) {
        if (!ensureLevel(level) || anchor == null) return OPEN;
        long key = anchor.host().asLong();
        CachedFlow current = cache.get(key);
        if (current == null || current.expiresAt < now) {
            if (queued.add(key)) pending.addLast(new ProbeRequest(key, anchor));
        }
        return current == null ? OPEN : current.flow;
    }

    public synchronized void tick(final ClientLevel level) {
        if (!ensureLevel(level)) return;
        long now = level.getGameTime();
        for (int count = 0; count < MAX_PROBES_PER_TICK && !pending.isEmpty(); count++) {
            ProbeRequest request = pending.removeFirst();
            queued.remove(request.key);
            cache.put(request.key, new CachedFlow(probe(level, request.anchor),
                now + CACHE_TICKS + Math.floorMod(mix(request.key), 31L)));
        }
        if (cache.size() > MAX_CACHE_ENTRIES) {
            Iterator<Long> iterator = cache.keySet().iterator();
            while (cache.size() > MAX_CACHE_ENTRIES && iterator.hasNext()) {
                iterator.next(); iterator.remove();
            }
        }
    }

    public synchronized void clear() {
        cache.clear(); pending.clear(); queued.clear(); activeLevel = null;
    }

    private boolean ensureLevel(final ClientLevel level) {
        if (level == null) { clear(); return false; }
        if (activeLevel != level) {
            cache.clear(); pending.clear(); queued.clear(); activeLevel = level;
        }
        return true;
    }

    private static SmokeFlow probe(final ClientLevel level,
        final FireSurfaceAnchor anchor) {
        BlockPos origin = anchor.host().relative(anchor.face());
        if (blocked(level, origin)) origin = anchor.host().above();
        if (blocked(level, origin)) return new SmokeFlow(0.55F, 0.42F,
            Vec3.ZERO, true, 0.03F);

        int verticalRun = clearRun(level, origin, Direction.UP, MAX_VERTICAL_PROBE);
        int nearestWall = MAX_LATERAL_PROBE;
        int longestRun = 0;
        Direction bestDirection = Direction.NORTH;
        int sampleHeight = Math.max(0, Math.min(verticalRun - 1, 2));
        BlockPos lateralOrigin = origin.above(sampleHeight);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            int run = clearRun(level, lateralOrigin, direction, MAX_LATERAL_PROBE);
            nearestWall = Math.min(nearestWall, run);
            if (run > longestRun) {
                longestRun = run;
                bestDirection = direction;
            }
        }

        boolean enclosed = verticalRun < MAX_VERTICAL_PROBE
            && longestRun < MAX_LATERAL_PROBE;
        float maxRise = enclosed ? Math.max(0.55F, verticalRun - 0.28F) : 8.5F;
        float lateralRadius = enclosed
            ? Mth.clamp(0.46F + nearestWall * 0.72F, 0.46F, 2.8F) : 3.0F;
        float ventilation = enclosed
            ? Mth.clamp(0.06F + longestRun * 0.085F + verticalRun * 0.025F,
                0.06F, 0.72F) : 1.0F;
        Vec3 vent = enclosed && longestRun > 0
            ? new Vec3(bestDirection.getStepX(), 0.0, bestDirection.getStepZ())
            : Vec3.ZERO;
        return new SmokeFlow(maxRise, lateralRadius, vent, enclosed, ventilation);
    }

    private static int clearRun(final ClientLevel level, final BlockPos origin,
        final Direction direction, final int maximum) {
        int run = 0;
        for (int distance = 1; distance <= maximum; distance++) {
            BlockPos sample = origin.relative(direction, distance);
            if (blocked(level, sample)) break;
            run++;
        }
        return run;
    }

    private static boolean blocked(final ClientLevel level, final BlockPos position) {
        if (!level.hasChunkAt(position)) return true;
        BlockState state = level.getBlockState(position);
        return !state.getFluidState().isEmpty()
            || !state.getCollisionShape(level, position).isEmpty();
    }

    private static long mix(long value) {
        value ^= value >>> 30; value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27; value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    public record SmokeFlow(float maximumRise, float lateralRadius,
        Vec3 ventDirection, boolean enclosed, float ventilation) { }
    private record ProbeRequest(long key, FireSurfaceAnchor anchor) { }
    private record CachedFlow(SmokeFlow flow, long expiresAt) { }
}
