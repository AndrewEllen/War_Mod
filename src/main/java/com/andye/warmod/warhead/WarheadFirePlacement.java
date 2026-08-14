package com.andye.warmod.warhead;

import com.andye.warmod.fire.FireSimulationManager;
import com.andye.warmod.fire.FireSurfaceAnchor;
import com.andye.warmod.item.component.FireDebugConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Swaps only aftermath ignition while leaving every other warhead effect untouched. */
public final class WarheadFirePlacement {
    private WarheadFirePlacement() { }

    public static boolean placeAbove(final ServerLevel level, final BlockPos host,
        final boolean customFire, final float intensity, final long seed,
        final int vanillaUpdateFlags) {
        BlockPos outside = host.above();
		if (!level.isInWorldBounds(outside)) return false;
        if (!customFire) {
            if (!level.getBlockState(outside).isAir()) return false;
            return level.setBlock(outside, Blocks.FIRE.defaultBlockState(), vanillaUpdateFlags);
        }
		if (!level.isInWorldBounds(host) || level.getBlockState(host).isAir()) return false;
        float safeIntensity = Mth.clamp(intensity, FireDebugConfig.MIN_INTENSITY,
            FireDebugConfig.MAX_INTENSITY);
        return FireSimulationManager.igniteSurfaceNuclear(level,
            FireSurfaceAnchor.center(host, Direction.UP),
            new FireDebugConfig(safeIntensity, 1), seed) > 0;
    }

    /** Custom nuclear fire prefers the surface facing ground zero; vanilla is unchanged. */
    public static boolean placeBlastFacing(final ServerLevel level, final BlockPos host,
        final Vec3 blastCenter, final boolean customFire, final float intensity,
        final long seed, final int vanillaUpdateFlags) {
        if (!customFire) return placeAbove(level, host, false, intensity, seed,
            vanillaUpdateFlags);
        if (level == null || host == null || blastCenter == null || !blastCenter.isFinite()
            || !level.isInWorldBounds(host) || level.getBlockState(host).isAir()) return false;
        double dx = blastCenter.x - (host.getX() + 0.5);
        double dz = blastCenter.z - (host.getZ() + 0.5);
        Direction toward;
        if (Math.abs(dx) < 1.0E-5 && Math.abs(dz) < 1.0E-5) {
            toward = Direction.from2DDataValue((int) Math.floorMod(seed, 4L));
        } else if (Math.abs(dx) >= Math.abs(dz)) {
            toward = dx >= 0.0 ? Direction.EAST : Direction.WEST;
        } else {
            toward = dz >= 0.0 ? Direction.SOUTH : Direction.NORTH;
        }
        Direction clockwise = clockwise(toward);
        Direction[] faces = {toward, clockwise, clockwise.getOpposite(),
            toward.getOpposite(), Direction.UP};
        float safeIntensity = Mth.clamp(intensity, FireDebugConfig.MIN_INTENSITY,
            FireDebugConfig.MAX_INTENSITY);
        FireDebugConfig config = new FireDebugConfig(safeIntensity, 1);
        for (Direction face : faces) {
            if (FireSimulationManager.igniteSurfaceNuclear(level,
                FireSurfaceAnchor.center(host, face), config,
                seed ^ face.ordinal() * 0x9E3779B97F4A7C15L) > 0) return true;
        }
        return false;
    }

    private static Direction clockwise(final Direction direction) {
        return switch (direction) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            default -> Direction.NORTH;
        };
    }
}
