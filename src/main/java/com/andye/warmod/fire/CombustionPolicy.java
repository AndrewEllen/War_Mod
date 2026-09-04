package com.andye.warmod.fire;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Pure cadence, exposure, and burnout policy shared by authoritative fire simulation. */
final class CombustionPolicy {
    private CombustionPolicy() { }

    static int spreadCadenceTicks(final float intensity) {
        if (intensity <= (FireIntensity.SMALL.heat() + FireIntensity.MEDIUM.heat()) * 0.5F) {
            return FireIntensity.SMALL.spreadIntervalTicks();
        }
        if (intensity <= (FireIntensity.MEDIUM.heat() + FireIntensity.INFERNO.heat()) * 0.5F) {
            return FireIntensity.MEDIUM.spreadIntervalTicks();
        }
        return FireIntensity.INFERNO.spreadIntervalTicks();
    }

    /** A delayed update represents one cadence, never a burst of missed cadences. */
    static float cadenceDose(final float calculatedDose) {
        return Math.max(0.0F, calculatedDose);
    }

    static int distinctExposureCadences(final int previousCadences,
        final long previousCadenceTick, final long cadenceTick) {
        return previousCadenceTick == cadenceTick
            ? previousCadences : Math.min(2, previousCadences + 1);
    }

    static boolean mayIgnite(final boolean directContact,
        final int distinctExposureCadences) {
        return directContact || distinctExposureCadences >= 2;
    }

    static BlockState scorchedState(final BlockState state) {
        if (state.is(Blocks.COARSE_DIRT)) return state;
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL))
            return Blocks.COARSE_DIRT.defaultBlockState();
        if (state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.MYCELIUM)
            || state.is(Blocks.DIRT) || state.is(Blocks.ROOTED_DIRT)
            || state.is(BlockTags.DIRT)) return Blocks.COARSE_DIRT.defaultBlockState();
        return state;
    }
}
