package com.andye.warmod.fire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Exact exposed surface location occupied by a fire patch. */
public record FireSurfaceAnchor(BlockPos host, Direction face,
    float localX, float localY, float localZ) {
    private static final int GRID_SIZE = 4;
    public FireSurfaceAnchor {
        if (host == null || face == null) throw new IllegalArgumentException("Invalid fire surface");
        localX = Mth.clamp(localX, 0.0F, 1.0F);
        localY = Mth.clamp(localY, 0.0F, 1.0F);
        localZ = Mth.clamp(localZ, 0.0F, 1.0F);
    }

    public static FireSurfaceAnchor fromHit(final BlockPos host, final Direction face,
        final Vec3 hit) {
        return new FireSurfaceAnchor(host.immutable(), face,
            (float) (hit.x - host.getX()), (float) (hit.y - host.getY()),
            (float) (hit.z - host.getZ()));
    }

    public static FireSurfaceAnchor center(final BlockPos host, final Direction face) {
        return switch (face) {
            case UP -> new FireSurfaceAnchor(host.immutable(), face, 0.5F, 1.0F, 0.5F);
            case DOWN -> new FireSurfaceAnchor(host.immutable(), face, 0.5F, 0.0F, 0.5F);
            case NORTH -> new FireSurfaceAnchor(host.immutable(), face, 0.5F, 0.5F, 0.0F);
            case SOUTH -> new FireSurfaceAnchor(host.immutable(), face, 0.5F, 0.5F, 1.0F);
            case WEST -> new FireSurfaceAnchor(host.immutable(), face, 0.0F, 0.5F, 0.5F);
            case EAST -> new FireSurfaceAnchor(host.immutable(), face, 1.0F, 0.5F, 0.5F);
        };
    }

    public static FireSurfaceAnchor grid(final BlockPos host, final Direction face,
        final int u, final int v) {
        float a = (Mth.clamp(u, 0, GRID_SIZE - 1) + 0.5F) / GRID_SIZE;
        float b = (Mth.clamp(v, 0, GRID_SIZE - 1) + 0.5F) / GRID_SIZE;
        return switch (face) {
            case UP -> new FireSurfaceAnchor(host.immutable(), face, a, 1.0F, b);
            case DOWN -> new FireSurfaceAnchor(host.immutable(), face, a, 0.0F, b);
            case NORTH -> new FireSurfaceAnchor(host.immutable(), face, a, b, 0.0F);
            case SOUTH -> new FireSurfaceAnchor(host.immutable(), face, a, b, 1.0F);
            case WEST -> new FireSurfaceAnchor(host.immutable(), face, 0.0F, b, a);
            case EAST -> new FireSurfaceAnchor(host.immutable(), face, 1.0F, b, a);
        };
    }

    public FireSurfaceAnchor gridOffset(final int du, final int dv) {
        int u = gridU() + du, v = gridV() + dv;
        return u < 0 || u >= GRID_SIZE || v < 0 || v >= GRID_SIZE
            ? null : grid(host, face, u, v);
    }

    public Vec3 position() {
        return new Vec3(host.getX() + localX, host.getY() + localY,
            host.getZ() + localZ).add(face.getStepX() * 0.035,
                face.getStepY() * 0.035, face.getStepZ() * 0.035);
    }

    public SurfaceKey key() {
        return new SurfaceKey(host.asLong(), (byte) face.ordinal(),
            (byte) gridU(), (byte) gridV());
    }

    private int gridU() {
        return gridCoordinate(switch (face) {
            case UP, DOWN, NORTH, SOUTH -> localX;
            case EAST, WEST -> localZ;
        });
    }

    private int gridV() {
        return gridCoordinate(switch (face) {
            case UP, DOWN -> localZ;
            case NORTH, SOUTH, EAST, WEST -> localY;
        });
    }

    private static int gridCoordinate(final float value) {
        return Mth.clamp((int) Math.floor(value * GRID_SIZE), 0, GRID_SIZE - 1);
    }

    public record SurfaceKey(long packedHost, byte faceOrdinal, byte gridU, byte gridV) {
        public BlockPos host() { return BlockPos.of(packedHost); }
        public Direction face() {
            int index = Byte.toUnsignedInt(faceOrdinal);
            return index < Direction.values().length ? Direction.values()[index] : Direction.UP;
        }
    }
}
