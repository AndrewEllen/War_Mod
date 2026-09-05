package com.andye.warmod.warhead;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Pure clearance policy shared by conventional debris grouping and tests. */
public final class ConventionalDebrisBallistics {
    public static final double AIR_DRAG = 0.992;
    public static final int OPAQUE_CORE_END_TICK = 78;
    private static final double CLEARANCE_MARGIN = 1.0;

    private ConventionalDebrisBallistics() { }

    public static double opaqueCoreRadius(final float visualScale) {
        float scale = Mth.clamp(visualScale, 0.28F, 1.75F);
        double craterRadius = 2.0 + 13.5 * scale;
        double smallYieldBoost = 1.0
            + Mth.clamp((0.72F - scale) / 0.72F, 0.0F, 1.0F) * 0.34;
        /* The deterministic conventional fire body varies up to 1.17x its base
           radius. Clearance uses that outer envelope, not its average lobe. */
        return craterRadius * 1.17 * smallYieldBoost;
    }

    public static double minimumOutwardVelocity(final float visualScale,
        final double fragmentStartRadius) {
        double distance = Math.max(0.0, opaqueCoreRadius(visualScale)
            + CLEARANCE_MARGIN - Math.max(0.0, fragmentStartRadius));
        if (distance <= 0.0) return 0.0;
        double displacementPerVelocity = AIR_DRAG
            * (1.0 - Math.pow(AIR_DRAG, OPAQUE_CORE_END_TICK))
            / (1.0 - AIR_DRAG);
        return distance / Math.max(1.0E-9, displacementPerVelocity);
    }

    public static Vec3 ensureClearance(final Vec3 encodedVelocity,
        final double offsetX, final double offsetZ, final float visualScale) {
        Vec3 velocity = encodedVelocity == null || !encodedVelocity.isFinite()
            ? Vec3.ZERO : encodedVelocity;
        double startRadius = Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);
        double directionX;
        double directionZ;
        if (startRadius > 1.0E-8) {
            directionX = offsetX / startRadius;
            directionZ = offsetZ / startRadius;
        } else {
            double horizontal = Math.sqrt(velocity.x * velocity.x
                + velocity.z * velocity.z);
            directionX = horizontal > 1.0E-8 ? velocity.x / horizontal : 1.0;
            directionZ = horizontal > 1.0E-8 ? velocity.z / horizontal : 0.0;
        }
        double outward = velocity.x * directionX + velocity.z * directionZ;
        double minimum = minimumOutwardVelocity(visualScale, startRadius);
        if (outward >= minimum) return velocity;
        double correction = minimum - outward;
        return velocity.add(directionX * correction, 0.0, directionZ * correction);
    }

    public static double outwardDisplacement(final double initialOutwardVelocity,
        final int ticks) {
        if (ticks <= 0) return 0.0;
        return initialOutwardVelocity * AIR_DRAG
            * (1.0 - Math.pow(AIR_DRAG, ticks)) / (1.0 - AIR_DRAG);
    }
}
