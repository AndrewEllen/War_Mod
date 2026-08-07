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
 * Shared visibility gate for War Mod's custom particle billboards and debris.
 *
 * <p>The renderer already rejects whole effects outside the view. This class
 * handles the expensive case where a partly-visible blast still contains many
 * particles/fragments behind terrain. Frustum rejection is per item and
 * allocation free. Terrain visibility is cached per 8x8x8 world cell and
 * shared by every simultaneous explosion, so it never performs block ray tests
 * per particle. Transparent, fluid and non-full blocks never occlude. Large
 * smoke billboards intentionally skip coarse terrain LOS culling so a lobe
 * peeking around an edge cannot disappear because its centre cell is hidden.</p>
 */
public final class WarheadParticleVisibility {
    private static final int CELL_SHIFT = 3;
    private static final int CELL_SIZE = 1 << CELL_SHIFT;
    private static final double CELL_HALF = CELL_SIZE * 0.5;
    private static final int MAX_NEW_LOS_CELLS = 96;
    private static final double MAX_LOS_DISTANCE = 512.0;
    private static final byte UNKNOWN = 0;
    private static final byte VISIBLE = 1;
    private static final byte OCCLUDED = 2;

    private static final Long2ByteOpenHashMap CELL_VISIBILITY = new Long2ByteOpenHashMap();
    private static final ThreadLocal<Vector3f> TRANSFORMED =
        ThreadLocal.withInitial(Vector3f::new);
    private static final BlockPos.MutableBlockPos BLOCK_CURSOR = new BlockPos.MutableBlockPos();

    private static ClientLevel level;
    private static Vec3 cameraPosition = Vec3.ZERO;
    private static final Vector3f forward = new Vector3f(0.0F, 0.0F, -1.0F);
    private static final Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F);
    private static final Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
    private static long cacheGameTime = Long.MIN_VALUE;
    private static int newLosCells;
    private static boolean registered;

    static {
        CELL_VISIBILITY.defaultReturnValue(UNKNOWN);
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
        if (!insideGenerousFrustum(rx, ry, rz, distanceSquared, safeRadius)) return false;
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
        if (!insideGenerousFrustum(rx, ry, rz, distanceSquared, safeRadius)) return false;
        return terrainVisible(currentLevel, worldPosition.x, worldPosition.y,
            worldPosition.z, distanceSquared, safeRadius);
    }

    private static boolean terrainVisible(final ClientLevel currentLevel,
        final double worldX, final double worldY, final double worldZ,
        final double distanceSquared, final double safeRadius) {
        double distance = Math.sqrt(distanceSquared);
        if (distance < 24.0 || distance > MAX_LOS_DISTANCE || safeRadius > 4.0) return true;

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

    public static void clear() {
        level = null;
        cameraPosition = Vec3.ZERO;
        CELL_VISIBILITY.clear();
        cacheGameTime = Long.MIN_VALUE;
        newLosCells = 0;
    }

    private static void beginFrame(final ClientLevel currentLevel,
        final Vec3 currentCamera, final Quaternionf orientation) {
        Vector3f nextForward = new Vector3f(0.0F, 0.0F, -1.0F).rotate(orientation);
        Vector3f nextRight = new Vector3f(1.0F, 0.0F, 0.0F).rotate(orientation);
        Vector3f nextUp = new Vector3f(0.0F, 1.0F, 0.0F).rotate(orientation);
        if (nextForward.lengthSquared() > 1.0E-6F) nextForward.normalize();
        if (nextRight.lengthSquared() > 1.0E-6F) nextRight.normalize();
        if (nextUp.lengthSquared() > 1.0E-6F) nextUp.normalize();

        long gameTime = currentLevel.getGameTime();
        boolean levelChanged = level != currentLevel;
        boolean cameraMoved = cameraPosition.distanceToSqr(currentCamera) > 0.35 * 0.35;
        boolean cameraTurned = forward.dot(nextForward) < 0.996F;
        if (levelChanged || gameTime != cacheGameTime || cameraMoved || cameraTurned) {
            CELL_VISIBILITY.clear();
            newLosCells = 0;
            cacheGameTime = gameTime;
        }
        level = currentLevel;
        cameraPosition = currentCamera;
        forward.set(nextForward);
        right.set(nextRight);
        up.set(nextUp);
    }

    private static boolean insideGenerousFrustum(final double x, final double y,
        final double z, final double distanceSquared, final double radius) {
        double distance = Math.sqrt(distanceSquared);
        if (distance <= radius + 10.0) return true;
        double front = x * forward.x + y * forward.y + z * forward.z;
        if (front < -radius - 6.0) return false;
        if (front <= 0.0) return true;
        double horizontal = x * right.x + y * right.y + z * right.z;
        double vertical = x * up.x + y * up.y + z * up.z;
        double margin = radius + 12.0;
        /* Intentionally wider than normal Minecraft FOV to avoid edge popping. */
        return Math.abs(horizontal) <= front * 1.80 + margin
            && Math.abs(vertical) <= front * 1.45 + margin;
    }

    /**
     * A cell is rejected only when its centre and all eight corners are hidden.
     * The sampled volume is larger than any billboard allowed through this LOS
     * path, so an item near a terrain silhouette remains visible whenever a
     * representative edge/corner line can reach the camera.
     */
    private static boolean fullyOccludedCell(final ClientLevel level,
        final double x, final double y, final double z) {
        double extent = CELL_HALF + 1.0;
        if (!blockedRay(level, x, y, z)) return false;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    if (!blockedRay(level,
                        x + sx * extent, y + sy * extent, z + sz * extent)) return false;
                }
            }
        }
        return true;
    }

    private static boolean blockedRay(final ClientLevel level,
        final double targetX, final double targetY, final double targetZ) {
        double startX = cameraPosition.x;
        double startY = cameraPosition.y;
        double startZ = cameraPosition.z;
        double dx = targetX - startX;
        double dy = targetY - startY;
        double dz = targetZ - startZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!Double.isFinite(distance) || distance < 3.0 || distance > MAX_LOS_DISTANCE + 16.0) {
            return false;
        }

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
                return fullOpaqueAt(level, x, y, z);
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
            /* Ignore the first two voxels around the camera/player. */
            if (step < 2) continue;
            if (fullOpaqueAt(level, x, y, z)) return true;
        }
        return false;
    }

    private static double firstBoundary(final double start, final int block,
        final double delta, final int step) {
        if (step == 0 || Math.abs(delta) < 1.0E-12) return Double.POSITIVE_INFINITY;
        double boundary = step > 0 ? block + 1.0 : block;
        return (boundary - start) / delta;
    }

    private static boolean fullOpaqueAt(final ClientLevel level,
        final int x, final int y, final int z) {
        BLOCK_CURSOR.set(x, y, z);
        if (!level.hasChunkAt(BLOCK_CURSOR)) return false;
        BlockState state = level.getBlockState(BLOCK_CURSOR);
        return !state.isAir() && state.getFluidState().isEmpty()
            && state.canOcclude() && state.isCollisionShapeFullBlock(level, BLOCK_CURSOR);
    }
}
