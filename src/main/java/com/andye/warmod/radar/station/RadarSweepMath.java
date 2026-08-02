package com.andye.warmod.radar.station;

import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public final class RadarSweepMath {
    private RadarSweepMath() { }

    public static long phaseOffset(final UUID id) {
        return Math.floorMod(id.getMostSignificantBits() ^ id.getLeastSignificantBits(),
            RadarStationConstants.SWEEP_PERIOD_TICKS);
    }

    public static double angleAt(final long gameTime, final float partialTick, final long phaseOffset) {
        return angleDegrees(gameTime + partialTick, phaseOffset);
    }

    public static double angleDegrees(final double gameTime, final long phaseOffset) {
        double cycle = Math.floorMod((long)Math.floor(gameTime) + phaseOffset,
            RadarStationConstants.SWEEP_PERIOD_TICKS) + gameTime - Math.floor(gameTime);
        return cycle / RadarStationConstants.SWEEP_PERIOD_TICKS * 360.0;
    }

    public static double bearingFrom(final Vec3 station, final Vec3 target) {
        return bearing(target.x - station.x, target.z - station.z);
    }

    public static double bearing(final double deltaX, final double deltaZ) {
        return positive(Math.toDegrees(Math.atan2(deltaX, -deltaZ)));
    }

    public static boolean crossed(final double previousAngle, final double currentAngle,
        final double bearing, final double beamWidth) {
        double travel = positive(currentAngle - previousAngle);
        double toBearing = positive(bearing - previousAngle);
        return toBearing <= travel + beamWidth * 0.5
            || positive(previousAngle - bearing) <= beamWidth * 0.5;
    }

    private static double positive(double angle) {
        angle %= 360.0;
        return angle < 0.0 ? angle + 360.0 : angle;
    }
}