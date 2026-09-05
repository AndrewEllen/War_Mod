package com.andye.warmod.silo;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class MissileSiloCollisionDetector {
    private MissileSiloCollisionDetector() {
    }

    public static Collision findFirst(final ServerLevel level, final Vec3 from, final Vec3 to,
        final MissileSiloCollisionContext context) {
        double distance = from.distanceTo(to);
        int steps = Math.max(1, (int)Math.ceil(distance / MissileSiloConstants.COLLISION_STEP_BLOCKS));
        for (int step = 0; step <= steps; step++) {
            double progress = (double)step / steps;
            Vec3 point = from.lerp(to, progress);
            double half = context.missileWidth() * 0.5;
            double halfHeight = context.missileHeight() * 0.5;
            AABB body = new AABB(point.x - half, point.y - halfHeight, point.z - half,
                point.x + half, point.y + halfHeight, point.z + half);
            BlockPos min = BlockPos.containing(body.minX, body.minY, body.minZ);
            BlockPos max = BlockPos.containing(body.maxX, body.maxY, body.maxZ);
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                BlockPos immutable = pos.immutable();
                if (context.ignoredStructureBlocks().contains(immutable)) continue;
                VoxelShape collision = level.getBlockState(immutable).getCollisionShape(level, immutable);
                if (collision.isEmpty()) continue;
                VoxelShape worldShape = collision.move(immutable.getX(), immutable.getY(), immutable.getZ());
                if (Shapes.joinIsNotEmpty(Shapes.create(body), worldShape, BooleanOp.AND))
                    return new Collision(immutable, point);
            }
        }
        return null;
    }

    public record Collision(BlockPos blockPosition, Vec3 impactPosition) {
    }
}
