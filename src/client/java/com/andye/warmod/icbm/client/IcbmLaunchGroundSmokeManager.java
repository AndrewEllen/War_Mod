package com.andye.warmod.icbm.client;

import com.andye.warmod.block.MissileSiloBlock;
import com.andye.warmod.block.entity.MissileSiloBlockEntity;
import com.andye.warmod.icbm.client.render.IcbmLaunchGroundSmokePolicy;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Persistent, terrain-anchored launch-pad smoke. It outlives the carrier's
 * visual state and has its own monotonic presentation clock, so a correction
 * of client game time cannot rewind an existing cloud.
 */
public final class IcbmLaunchGroundSmokeManager {
    public static final IcbmLaunchGroundSmokeManager INSTANCE = new IcbmLaunchGroundSmokeManager();
    // 960 small lobes are intentionally dense near a launch. Retaining only
    // the four newest clouds prevents an unbounded stack of distant launches.
    private static final int MAX_ACTIVE_CLOUDS = 4;

    private final Map<UUID, LaunchCloud> clouds = new LinkedHashMap<>();
    private ClientLevel activeLevel;
    private double visualTime;
    private long lastVisualNanos;

    private IcbmLaunchGroundSmokeManager() { }

    public synchronized void start(final ClientLevel level, final UUID id, final Vec3 launchPosition,
        final long seed, final long launchGameTime, final float scale, final int lobeCount) {
        if (id == null || launchPosition == null || !launchPosition.isFinite() || !ensure(level)) return;
        advanceVisualClock();
        Vec3 anchor = resolveAnchor(level, launchPosition);
        if (anchor == null) return; // Do not make a ground cloud for an airborne launch.
        clouds.remove(id);
        trim();
        int count = Math.max(0, Math.min(IcbmLaunchGroundSmokePolicy.ICBM_LOBES, lobeCount));
        double[] groundHeights = cacheGroundHeights(level, anchor, seed, scale, count);
        double packetAge = Math.max(0.0, level.getGameTime() - launchGameTime);
        clouds.put(id, new LaunchCloud(id, anchor, seed, visualTime - packetAge, scale, count,
            groundHeights));
    }

    public synchronized Snapshot snapshot(final ClientLevel level) {
        if (!ensure(level)) return Snapshot.EMPTY;
        advanceVisualClock();
        clouds.entrySet().removeIf(entry -> entry.getValue().expired(visualTime));
        return new Snapshot(List.copyOf(clouds.values()), visualTime);
    }

    public synchronized void clear() {
        clouds.clear();
        activeLevel = null;
        visualTime = 0.0;
        lastVisualNanos = 0L;
    }

    private boolean ensure(final ClientLevel level) {
        if (level == null) {
            clear();
            return false;
        }
        if (activeLevel != level) {
            clouds.clear();
            activeLevel = level;
            visualTime = level.getGameTime();
            lastVisualNanos = 0L;
        }
        return true;
    }

    private void advanceVisualClock() {
        long now = System.nanoTime();
        if (lastVisualNanos == 0L) {
            lastVisualNanos = now;
            return;
        }
        if (Minecraft.getInstance().isPaused()) {
            // Keep the clock frozen without accumulating a pause-duration
            // correction that would jump the cloud when gameplay resumes.
            lastVisualNanos = now;
            return;
        }
        // Follow presentation time rather than raw world time. A debugger or
        // render stall cannot make a cloud jump forward by more than one tick.
        double elapsedTicks = Math.min(1.0, Math.max(0.0, now - lastVisualNanos) / 50_000_000.0);
        visualTime += elapsedTicks;
        lastVisualNanos = now;
    }

    private static Vec3 resolveAnchor(final ClientLevel level, final Vec3 launch) {
        BlockPos launchBlock = BlockPos.containing(launch);
        for (int offset = 0; offset <= 6; offset++) {
            BlockPos candidate = launchBlock.above(offset);
            if (!level.hasChunkAt(candidate)) continue;
            if (level.getBlockEntity(candidate) instanceof MissileSiloBlockEntity
                || level.getBlockState(candidate).getBlock() instanceof MissileSiloBlock) {
                return new Vec3(candidate.getX() + 0.5, candidate.getY() + 0.42,
                    candidate.getZ() + 0.5);
            }
        }
        if (!level.hasChunkAt(launchBlock)) return null;
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            launchBlock.getX(), launchBlock.getZ());
        return Math.abs(launch.y - ground) <= 6.0
            ? new Vec3(launch.x, ground + 0.04, launch.z) : null;
    }

    private static double[] cacheGroundHeights(final ClientLevel level, final Vec3 anchor,
        final long seed, final float scale, final int count) {
        double[] heights = new double[count];
        for (int ordinal = 0; ordinal < count; ordinal++) {
            // Probe after the latest delayed outer cohort has completed its
            // rollout, before the long-lived settling phase changes its shape.
            var lobe = IcbmLaunchGroundSmokePolicy.sample(seed, ordinal, 210.0, scale);
            BlockPos surface = BlockPos.containing(anchor.x + lobe.x(), anchor.y,
                anchor.z + lobe.z());
            heights[ordinal] = level.hasChunkAt(surface)
                ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    surface.getX(), surface.getZ())
                : anchor.y;
        }
        return heights;
    }

    private void trim() {
        while (clouds.size() >= MAX_ACTIVE_CLOUDS) {
            Iterator<UUID> iterator = clouds.keySet().iterator();
            if (!iterator.hasNext()) return;
            iterator.next();
            iterator.remove();
        }
    }

    public record Snapshot(List<LaunchCloud> clouds, double visualTime) {
        private static final Snapshot EMPTY = new Snapshot(List.of(), 0.0);
    }

    public record LaunchCloud(UUID id, Vec3 position, long seed, double visualStartTime,
        float scale, int lobeCount, double[] groundHeights) {
        public LaunchCloud {
            groundHeights = groundHeights == null ? new double[0] : groundHeights.clone();
        }

        public double elapsed(final double currentVisualTime) {
            return Math.max(0.0, currentVisualTime - visualStartTime);
        }

        public double groundHeight(final int ordinal) {
            return ordinal >= 0 && ordinal < groundHeights.length && Double.isFinite(groundHeights[ordinal])
                ? groundHeights[ordinal] : position.y;
        }

        boolean expired(final double currentVisualTime) {
            return elapsed(currentVisualTime) >= IcbmLaunchGroundSmokePolicy.LIFETIME_TICKS;
        }
    }
}
