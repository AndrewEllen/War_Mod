package com.andye.warmod.warhead.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Shared visibility and overlap gate for War Mod custom world effects.
 *
 * <p>Screen rejection is intentionally much wider than the real player FOV so
 * particles cannot pop at the edge of the display. Terrain rejection is split
 * into a cheap fully-buried test and a cached coarse line-of-sight test. Only
 * full opaque collision cubes occlude; glass, fluids, leaves and partial blocks
 * remain transparent to this culler.</p>
 */
public final class WarheadParticleVisibility {
    public static final int CHANNEL_CONVENTIONAL_CLOUD = 1;
    public static final int CHANNEL_GROUND_DUST = 2;
    public static final int CHANNEL_GROUND_EXPLOSION = 3;
    public static final int CHANNEL_SETTLED_SMOKE = 4;

    private static final int CELL_SHIFT = 2;
    private static final int CELL_SIZE = 1 << CELL_SHIFT;
    private static final double CELL_HALF = CELL_SIZE * 0.5;
    private static final int MAX_NEW_LOS_CELLS = 192;
    private static final int CACHE_TTL_TICKS = 4;
    private static final double MAX_LOS_DISTANCE = 512.0;
    private static final double VIEW_COSINE_LIMIT = -0.55;
    private static final int MAX_CLUSTER_CLAIMS = 96;

    private static final byte UNKNOWN = 0;
    private static final byte VISIBLE = 1;
    private static final byte OCCLUDED = 2;
    private static final byte NOT_OPAQUE = 1;
    private static final byte FULL_OPAQUE = 2;

    private static final Long2ByteOpenHashMap CELL_VISIBILITY = new Long2ByteOpenHashMap();
    private static final Long2ByteOpenHashMap BLOCK_OPACITY = new Long2ByteOpenHashMap();
    private static final ThreadLocal<Vector3f> TRANSFORMED =
        ThreadLocal.withInitial(Vector3f::new);
    private static final BlockPos.MutableBlockPos BLOCK_CURSOR = new BlockPos.MutableBlockPos();

    private static final double[] CLAIM_X = new double[MAX_CLUSTER_CLAIMS];
    private static final double[] CLAIM_Z = new double[MAX_CLUSTER_CLAIMS];
    private static final int[] CLAIM_CHANNEL = new int[MAX_CLUSTER_CLAIMS];
    private static final long[] CLAIM_OWNER = new long[MAX_CLUSTER_CLAIMS];
    private static final boolean[] CLAIM_OWNER_AWARE = new boolean[MAX_CLUSTER_CLAIMS];

    private static ClientLevel level;
    private static Vec3 cameraPosition = Vec3.ZERO;
    private static final Vector3f forward = new Vector3f(0.0F, 0.0F, -1.0F);
    private static long cacheEpoch = Long.MIN_VALUE;
    private static long frameSequence;
    private static int newLosCells;
    private static int claimCount;
    private static boolean registered;

    static {
        CELL_VISIBILITY.defaultReturnValue(UNKNOWN);
        BLOCK_OPACITY.defaultReturnValue(UNKNOWN);
    }

    private WarheadParticleVisibility() { }

    public static void register() {
        if (registered) return;
        LevelExtractionEvents.END_EXTRACTION.register(context -> {
            ClientLevel currentLevel = context.level();
            CameraRenderState camera = context.levelState().cameraRenderState;
            if (currentLevel == null || camera == null || camera.pos == null) {
                clear();
                return;
            }
            Quaternionf orientation = camera.orientation == null
                ? new Quaternionf() : new Quaternionf(camera.orientation);
            beginFrame(currentLevel, camera.pos, orientation);
        });
        registered = true;
    }

    public static long frameSequence() {
        return frameSequence;
    }

    public static boolean visible(final PoseStack.Pose pose,
        final float localX, final float localY, final float localZ,
        final float radius) {
        ClientLevel currentLevel = level;
        if (currentLevel == null || pose == null) return true;

        Vector3f relative = TRANSFORMED.get().set(localX, localY, localZ);
        pose.pose().transformPosition(relative);
        double rx = relative.x;
        double ry = relative.y;
        double rz = relative.z;
        double distanceSquared = rx * rx + ry * ry + rz * rz;
        if (!Double.isFinite(distanceSquared)) return false;
        double safeRadius = Math.max(0.05, radius);
        if (!insideWideView(rx, ry, rz, distanceSquared, safeRadius)) return false;
        return terrainVisible(currentLevel, cameraPosition.x + rx,
            cameraPosition.y + ry, cameraPosition.z + rz,
            distanceSquared, safeRadius);
    }

    /** World-space variant used before moving-block debris is submitted. */
    public static boolean visibleWorld(final Vec3 worldPosition, final float radius) {
        ClientLevel currentLevel = level;
        if (currentLevel == null || worldPosition == null || !worldPosition.isFinite()) return true;
        double rx = worldPosition.x - cameraPosition.x;
        double ry = worldPosition.y - cameraPosition.y;
        double rz = worldPosition.z - cameraPosition.z;
        double distanceSquared = rx * rx + ry * ry + rz * rz;
        if (!Double.isFinite(distanceSquared)) return false;
        double safeRadius = Math.max(0.05, radius);
        if (!insideWideView(rx, ry, rz, distanceSquared, safeRadius)) return false;
        return terrainVisible(currentLevel, worldPosition.x, worldPosition.y,
            worldPosition.z, distanceSquared, safeRadius);
    }

    /**
     * Reserves one expensive effect for a spatial cluster during this frame.
     * Used for terrain-front layers where several impacts at almost the same
     * location would otherwise submit the same thousands of billboards.
     */
    public static boolean claimWorldClusterOnce(final Vec3 worldPosition,
        final int channel, final double mergeRadius) {
        if (worldPosition == null || !worldPosition.isFinite()) return true;
        return claim(worldPosition.x, worldPosition.z, channel,
            Math.max(1.0, mergeRadius), 0L, false);
    }

    /**
     * Owner-aware cluster reservation. Repeated passes from the same explosion
     * are allowed, while a second overlapping explosion is demoted for that
     * frame. This preserves all fire/smoke passes of the selected explosion.
     */
    public static boolean claimPoseClusterOwner(final PoseStack.Pose pose,
        final int channel, final double mergeRadius, final long owner) {
        if (pose == null || level == null) return true;
        Vector3f relative = TRANSFORMED.get().set(0.0F, 0.0F, 0.0F);
        pose.pose().transformPosition(relative);
        return claim(cameraPosition.x + relative.x,
            cameraPosition.z + relative.z, channel,
            Math.max(1.0, mergeRadius), owner, true);
    }

    private static boolean claim(final double x, final double z,
        final int channel, final double radius, final long owner,
        final boolean ownerAware) {
        double radiusSquared = radius * radius;
        for (int index = 0; index < claimCount; index++) {
            if (CLAIM_CHANNEL[index] != channel) continue;
            double dx = x - CLAIM_X[index];
            double dz = z - CLAIM_Z[index];
            if (dx * dx + dz * dz > radiusSquared) continue;
            if (ownerAware && CLAIM_OWNER_AWARE[index]
                && CLAIM_OWNER[index] == owner) return true;
            return false;
        }
        if (claimCount < MAX_CLUSTER_CLAIMS) {
            CLAIM_X[claimCount] = x;
            CLAIM_Z[claimCount] = z;
            CLAIM_CHANNEL[claimCount] = channel;
            CLAIM_OWNER[claimCount] = owner;
            CLAIM_OWNER_AWARE[claimCount] = ownerAware;
            claimCount++;
        }
        return true;
    }

    private static boolean terrainVisible(final ClientLevel currentLevel,
        final double worldX, final double worldY, final double worldZ,
        final double distanceSquared, final double safeRadius) {
        if (fullyBuried(currentLevel, worldX, worldY, worldZ, safeRadius)) return false;

        double distance = Math.sqrt(distanceSquared);
        if (distance < 16.0 || distance > MAX_LOS_DISTANCE || safeRadius > 4.5) return true;

        int cellX = Mth.floor(worldX) >> CELL_SHIFT;
        int cellY = Mth.floor(worldY) >> CELL_SHIFT;
        int cellZ = Mth.floor(worldZ) >> CELL_SHIFT;
        long key = BlockPos.asLong(cellX, cellY, cellZ);
        byte cached = CELL_VISIBILITY.get(key);
        if (cached == OCCLUDED) return false;
        if (cached == VISIBLE) return true;
        if (newLosCells >= MAX_NEW_LOS_CELLS) return true;
        newLosCells++;

        double centerX = cellX * (double) CELL_SIZE + CELL_HALF;
        double centerY = cellY * (double) CELL_SIZE + CELL_HALF;
        double centerZ = cellZ * (double) CELL_SIZE + CELL_HALF;
        boolean occluded = fullyOccludedCell(currentLevel, centerX, centerY, centerZ);
        CELL_VISIBILITY.put(key, occluded ? OCCLUDED : VISIBLE);
        return !occluded;
    }

    /** Reject only when the centre and every sampled extent lie in opaque cubes. */
    private static boolean fullyBuried(final ClientLevel currentLevel,
        final double x, final double y, final double z, final double radius) {
        if (radius > 6.0) return false;
        if (!fullOpaqueAt(currentLevel, Mth.floor(x), Mth.floor(y), Mth.floor(z))) return false;
        double sample = Math.max(0.35, radius * 0.92);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    if (!fullOpaqueAt(currentLevel,
                        Mth.floor(x + sx * sample),
                        Mth.floor(y + sy * sample),
                        Mth.floor(z + sz * sample))) return false;
                }
            }
        }
        return true;
    }

    public static void clear() {
        level = null;
        cameraPosition = Vec3.ZERO;
        CELL_VISIBILITY.clear();
        BLOCK_OPACITY.clear();
        cacheEpoch = Long.MIN_VALUE;
        newLosCells = 0;
        claimCount = 0;
        frameSequence++;
    }

    private static void beginFrame(final ClientLevel currentLevel,
        final Vec3 currentCamera, final Quaternionf orientation) {
        Vector3f nextForward = new Vector3f(0.0F, 0.0F, -1.0F).rotate(orientation);
        if (nextForward.lengthSquared() > 1.0E-6F) nextForward.normalize();

        long gameTime = currentLevel.getGameTime();
        long nextEpoch = Math.floorDiv(gameTime, CACHE_TTL_TICKS);
        boolean levelChanged = level != currentLevel;
        boolean cameraMoved = cameraPosition.distanceToSqr(currentCamera) > 0.75 * 0.75;
        boolean cameraTurned = forward.dot(nextForward) < 0.985F;
        if (levelChanged || nextEpoch != cacheEpoch || cameraMoved || cameraTurned) {
            CELL_VISIBILITY.clear();
            BLOCK_OPACITY.clear();
            newLosCells = 0;
            cacheEpoch = nextEpoch;
        }
        level = currentLevel;
        cameraPosition = currentCamera;
        forward.set(nextForward);
        claimCount = 0;
        frameSequence++;
    }

    /**
     * Roughly 123 degrees either side of forward. This is intentionally much
     * wider than any normal Minecraft FOV plus a large safety margin.
     */
    private static boolean insideWideView(final double x, final double y,
        final double z, final double distanceSquared, final double radius) {
        double distance = Math.sqrt(distanceSquared);
        if (distance <= radius + 12.0) return true;
        double facing = (x * forward.x + y * forward.y + z * forward.z)
            / Math.max(1.0E-6, distance);
        double angularAllowance = Math.min(0.32, radius / Math.max(1.0, distance));
        return facing + angularAllowance >= VIEW_COSINE_LIMIT;
    }

    /** A 4x4x4 cell is hidden only when centre and all eight corners are blocked. */
    private static boolean fullyOccludedCell(final ClientLevel currentLevel,
        final double x, final double y, final double z) {
        if (!blockedRay(currentLevel, x, y, z)) return false;
        double extent = CELL_HALF + 0.75;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    if (!blockedRay(currentLevel,
                        x + sx * extent, y + sy * extent, z + sz * extent)) return false;
                }
            }
        }
        return true;
    }

    private static boolean blockedRay(final ClientLevel currentLevel,
        final double targetX, final double targetY, final double targetZ) {
        double startX = cameraPosition.x;
        double startY = cameraPosition.y;
        double startZ = cameraPosition.z;
        double dx = targetX - startX;
        double dy = targetY - startY;
        double dz = targetZ - startZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!Double.isFinite(distance) || distance < 3.0
            || distance > MAX_LOS_DISTANCE + 16.0) return false;

        int x = Mth.floor(startX);
        int y = Mth.floor(startY);
        int z = Mth.floor(startZ);
        int endX = Mth.floor(targetX);
        int endY = Mth.floor(targetY);
        int endZ = Mth.floor(targetZ);
        int stepX = Integer.compare(endX, x);
        int stepY = Integer.compare(endY, y);
        int stepZ = Integer.compare(endZ, z);

        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);
        double tMaxX = firstBoundary(startX, x, dx, stepX);
        double tMaxY = firstBoundary(startY, y, dy, stepY);
        double tMaxZ = firstBoundary(startZ, z, dz, stepZ);

        int maximumSteps = Math.min(768,
            Math.abs(endX - x) + Math.abs(endY - y) + Math.abs(endZ - z) + 4);
        for (int step = 0; step < maximumSteps; step++) {
            if (x == endX && y == endY && z == endZ) {
                return fullOpaqueAt(currentLevel, x, y, z);
            }
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY <= tMaxZ) {
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
            if (step < 2) continue;
            if (fullOpaqueAt(currentLevel, x, y, z)) return true;
        }
        return false;
    }

    private static double firstBoundary(final double start, final int block,
        final double delta, final int step) {
        if (step == 0 || Math.abs(delta) < 1.0E-12) return Double.POSITIVE_INFINITY;
        double boundary = step > 0 ? block + 1.0 : block;
        return (boundary - start) / delta;
    }

    private static boolean fullOpaqueAt(final ClientLevel currentLevel,
        final int x, final int y, final int z) {
        long key = BlockPos.asLong(x, y, z);
        byte cached = BLOCK_OPACITY.get(key);
        if (cached == FULL_OPAQUE) return true;
        if (cached == NOT_OPAQUE) return false;

        BLOCK_CURSOR.set(x, y, z);
        if (!currentLevel.hasChunkAt(BLOCK_CURSOR)) {
            BLOCK_OPACITY.put(key, NOT_OPAQUE);
            return false;
        }
        BlockState state = currentLevel.getBlockState(BLOCK_CURSOR);
        boolean opaque = !state.isAir() && state.getFluidState().isEmpty()
            && state.canOcclude()
            && state.isCollisionShapeFullBlock(currentLevel, BLOCK_CURSOR);
        BLOCK_OPACITY.put(key, opaque ? FULL_OPAQUE : NOT_OPAQUE);
        return opaque;
    }
}
