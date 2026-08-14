package com.andye.warmod.warhead;

import com.andye.warmod.fire.FireSimulationManager;
import com.andye.warmod.fire.FireSurfaceAnchor;
import com.andye.warmod.item.component.FireDebugConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;

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
        return FireSimulationManager.igniteSurface(level,
            FireSurfaceAnchor.center(host, Direction.UP),
            new FireDebugConfig(safeIntensity, 1), seed) > 0;
    }
}
