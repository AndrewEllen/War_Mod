package com.andye.warmod.warhead;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Ash, coral and hanging-moss detail decisions from the reference aftermath. */
final class NuclearDecorationPolicy {
    private NuclearDecorationPolicy() { }

    static int ash(final WarheadStatePalette palette, final long hash,
        final double normalized) {
        double fade = Mth.clamp((1.0 - normalized) / 0.70, 0.0, 1.0);
        if (NuclearPolicyHash.unit(hash ^ 0x4153485F4445434FL)
            >= 0.012 + fade * 0.046) return NuclearSurfacePolicy.NO_CHANGE;
        double kind = NuclearPolicyHash.unit(hash ^ 0x4153485F4B494E44L);
        if (kind < 0.20) return palette.deadBush();
        if (kind < 0.43) return palette.shortDryGrass();
        if (kind < 0.61) return palette.tallDryGrass();
        if (kind < 0.73) return palette.decoration().paleMossCarpet();
        if (kind < 0.92) {
            return palette.decoration().deadCoralFan((int)(hash >>> 20));
        }
        if (kind < 0.947) return palette.decoration().witherRose();
        return palette.decoration().closedEyeblossom();
    }

    static Remnants treeRemnants(final PreparedImpactSpec impact,
        final WarheadStatePalette palette, final BlockPos log,
        final double normalized) {
        double heat = Mth.clamp((0.92 - normalized) / 0.58, 0.0, 1.0);
        long packed = log.asLong();
        long hash = impact.seed() ^ packed ^ 0x545245455F52454DL;
        Direction fanDirection = null;
        int fanState = NuclearSurfacePolicy.NO_CHANGE;
        if (NuclearPolicyHash.unit(hash) < 0.055 * heat) {
            Direction toward = horizontalTowardCenter(impact.target(), log);
            Direction clockwise = clockwise(toward);
            fanDirection = NuclearPolicyHash.unit(hash ^ 0x57414C4C5F46414EL) < 0.5
                ? clockwise : clockwise.getOpposite();
            fanState = palette.decoration().deadCoralWallFan((int)(hash >>> 24),
                fanDirection);
        }
        boolean moss = NuclearPolicyHash.unit(hash ^ 0x48414E47494E475FL)
            < 0.045 * heat;
        return new Remnants(fanDirection, fanState, moss);
    }

    private static Direction horizontalTowardCenter(final Vec3 center,
        final BlockPos position) {
        double dx = center.x - (position.getX() + 0.5);
        double dz = center.z - (position.getZ() + 0.5);
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0.0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0.0 ? Direction.SOUTH : Direction.NORTH;
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

    record Remnants(Direction fanDirection, int fanStateId, boolean hangingMoss) {
        boolean hasFan() { return fanDirection != null; }
    }
}
