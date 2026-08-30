package com.andye.warmod.warhead;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/** Bounded server-thread capture. No world reference escapes in the result. */
final class WarheadWorldSnapshotter {
    private static final int VERTICAL_SCAN_BELOW_SURFACE = 8;
    private static final int VERTICAL_SCAN_ABOVE_SURFACE = 52;
    private static final int SURFACE_SUPPORT_DESCENT = 8;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Direction[] HORIZONTAL = {
        Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    private WarheadWorldSnapshotter() { }

    static WarheadChunkSnapshot capture(final ServerLevel level, final ChunkPos position,
        final List<WarheadSnapshotRequirement> requirements) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(position.x(), position.z());
        if (chunk == null || requirements == null || requirements.isEmpty()) return null;
        WarheadChunkRevisionAccess revisions = (WarheadChunkRevisionAccess)(Object)chunk;
        long startingRevision = revisions.war_mod$getChunkRevision();
        int minimumBuildY = level.dimensionType().minY();
        int maximumBuildY = minimumBuildY + level.dimensionType().height() - 1;
        int minimumSectionY = level.getMinSectionY();
        long[] sectionRevisions = new long[level.getSectionsCount()];
        for (int index = 0; index < sectionRevisions.length; index++) {
            sectionRevisions[index] = revisions.war_mod$getSectionRevision(minimumSectionY + index);
        }

        int craterMinimumY = Integer.MAX_VALUE;
        int craterMaximumY = Integer.MIN_VALUE;
        for (WarheadSnapshotRequirement requirement : requirements) {
            PreparedImpactSpec impact = requirement.impact();
            StrategicExplosionProfile profile = StrategicExplosionProfiles.get(impact.yield());
            if (!WarheadFootprintCalculator.chunkIntersectsCircle(position.x(), position.z(),
                impact.target().x, impact.target().z, profile.horizontalRadius() + 1.0)) continue;
            int centerY = Mth.floor(impact.target().y);
            craterMinimumY = Math.min(craterMinimumY,
                centerY - Mth.ceil(profile.downwardRadius()) - 1);
            craterMaximumY = Math.max(craterMaximumY,
                centerY + Mth.ceil(profile.upwardRadius()) + 1);
        }
        if (craterMaximumY < craterMinimumY) {
            craterMinimumY = 0;
            craterMaximumY = -1;
        } else {
            craterMinimumY = Math.max(minimumBuildY, craterMinimumY);
            craterMaximumY = Math.min(maximumBuildY, craterMaximumY);
        }

        int[] motionTopY = new int[256];
        int[] terrainSurfaceY = new int[256];
        int[] columnFlags = new int[256];
        int[] surfaceStateIds = new int[256 * WarheadChunkSnapshot.SURFACE_LAYERS];
        int[] surfaceFlags = new int[surfaceStateIds.length];
        LongArrayList relevantPositions = new LongArrayList();
        IntArrayList relevantStateIds = new IntArrayList();
        IntArrayList relevantFlags = new IntArrayList();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        int baseX = position.getMinBlockX();
        int baseZ = position.getMinBlockZ();
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int column = localZ * 16 + localX;
                int worldX = baseX + localX;
                int worldZ = baseZ + localZ;
                int motionY = level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                    worldX, worldZ) - 1;
                int terrainY = terrainSurfaceY(level, chunk, worldX, worldZ,
                    minimumBuildY);
                motionTopY[column] = motionY;
                terrainSurfaceY[column] = terrainY;
                if (terrainY >= minimumBuildY && touchesWater(level, worldX, terrainY, worldZ)) {
                    columnFlags[column] |= WarheadSnapshotFlags.WATER_NEAR;
                }

                for (int layer = 0; layer < WarheadChunkSnapshot.SURFACE_LAYERS; layer++) {
                    int y = terrainY + 1 - layer;
                    int destination = layer * 256 + column;
                    if (y < minimumBuildY || y > maximumBuildY) {
                        surfaceStateIds[destination] = Block.getId(Blocks.AIR.defaultBlockState());
                        surfaceFlags[destination] = WarheadSnapshotFlags.AIR;
                        continue;
                    }
                    cursor.set(worldX, y, worldZ);
                    BlockState state = chunk.getBlockState(cursor);
                    surfaceStateIds[destination] = Block.getId(state);
                    surfaceFlags[destination] = WarheadSnapshotFlags.classify(state,
                        false, exposedToAir(level, cursor));
                }

                int columnStart = relevantPositions.size();
                boolean naturalTree = false;
                int scanMinimum = Math.max(minimumBuildY,
                    terrainY - VERTICAL_SCAN_BELOW_SURFACE);
                int scanMaximum = Math.min(maximumBuildY,
                    terrainY + VERTICAL_SCAN_ABOVE_SURFACE);
                for (int y = scanMinimum; y <= scanMaximum; y++) {
                    cursor.set(worldX, y, worldZ);
                    BlockState state = chunk.getBlockState(cursor);
                    int flags = WarheadSnapshotFlags.classify(state, false, false);
                    if (!WarheadSnapshotFlags.relevantVertical(flags)) continue;
                    naturalTree |= (flags & WarheadSnapshotFlags.LEAVES) != 0;
                    relevantPositions.add(cursor.asLong());
                    relevantStateIds.add(Block.getId(state));
                    relevantFlags.add(flags);
                }
                if (naturalTree) {
                    for (int index = columnStart; index < relevantFlags.size(); index++) {
                        int flags = relevantFlags.getInt(index);
                        if ((flags & WarheadSnapshotFlags.LOG) != 0) {
                            relevantFlags.set(index, flags | WarheadSnapshotFlags.NATURAL_TREE);
                        }
                    }
                }
            }
        }

        int craterHeight = Math.max(0, craterMaximumY - craterMinimumY + 1);
        int[] craterStateIds = new int[256 * craterHeight];
        int[] craterFlags = new int[craterStateIds.length];
        float[] craterResistance = new float[craterStateIds.length];
        for (int y = craterMinimumY; y <= craterMaximumY; y++) {
            int layerOffset = (y - craterMinimumY) * 256;
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    int index = layerOffset + localZ * 16 + localX;
                    cursor.set(baseX + localX, y, baseZ + localZ);
                    BlockState state = chunk.getBlockState(cursor);
                    boolean indestructible;
                    try {
                        indestructible = state.getDestroySpeed(level, cursor) < 0.0F;
                    } catch (RuntimeException failure) {
                        indestructible = true;
                    }
                    craterStateIds[index] = Block.getId(state);
                    craterFlags[index] = WarheadSnapshotFlags.classify(state,
                        indestructible, false);
                    craterResistance[index] = Math.max(
                        state.getBlock().getExplosionResistance(),
                        state.getFluidState().getExplosionResistance());
                }
            }
        }

        if (startingRevision != revisions.war_mod$getChunkRevision()) return null;
        return new WarheadChunkSnapshot(position, startingRevision, minimumSectionY,
            sectionRevisions, minimumBuildY, maximumBuildY,
            craterMinimumY, craterMaximumY, motionTopY, terrainSurfaceY, columnFlags,
            surfaceStateIds, surfaceFlags, craterStateIds, craterFlags, craterResistance,
            relevantPositions.toLongArray(), relevantStateIds.toIntArray(),
            relevantFlags.toIntArray());
    }

    private static int terrainSurfaceY(final ServerLevel level, final LevelChunk chunk,
        final int x, final int z, final int minimumY) {
        int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos(x, y, z);
        for (int descent = 0; descent <= SURFACE_SUPPORT_DESCENT && y >= minimumY;
            descent++, y--) {
            position.set(x, y, z);
            BlockState state = chunk.getBlockState(position);
            int flags = WarheadSnapshotFlags.classify(state, false, false);
            if ((flags & (WarheadSnapshotFlags.AIR | WarheadSnapshotFlags.FLUID
                | WarheadSnapshotFlags.LEAVES | WarheadSnapshotFlags.LOG
                | WarheadSnapshotFlags.PLANK | WarheadSnapshotFlags.GLASS
                | WarheadSnapshotFlags.COBBLE | WarheadSnapshotFlags.FRAGILE)) == 0
                && !state.is(Blocks.LEAF_LITTER)) return y;
        }
        return minimumY - 1;
    }

    private static boolean exposedToAir(final ServerLevel level,
        final BlockPos position) {
        for (Direction direction : DIRECTIONS) {
            BlockPos neighbour = position.relative(direction);
            if (level.isInWorldBounds(neighbour) && chunkLoaded(level, neighbour)
                && level.getBlockState(neighbour).isAir()) return true;
        }
        return false;
    }

    private static boolean touchesWater(final ServerLevel level, final int x,
        final int surfaceY, final int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, surfaceY + 1, z);
        if (chunkLoaded(level, cursor) && level.getFluidState(cursor).is(FluidTags.WATER)) {
            return true;
        }
        for (Direction direction : HORIZONTAL) {
            for (int distance = 1; distance <= 2; distance++) {
                cursor.set(x + direction.getStepX() * distance, surfaceY,
                    z + direction.getStepZ() * distance);
                if (chunkLoaded(level, cursor)
                    && level.getFluidState(cursor).is(FluidTags.WATER)) return true;
                cursor.move(Direction.UP);
                if (chunkLoaded(level, cursor)
                    && level.getFluidState(cursor).is(FluidTags.WATER)) return true;
            }
        }
        return false;
    }

    private static boolean chunkLoaded(final ServerLevel level, final BlockPos position) {
        return level.getChunkSource().hasChunk(position.getX() >> 4, position.getZ() >> 4);
    }
}
