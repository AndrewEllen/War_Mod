package com.andye.warmod.antiair;

import net.minecraft.world.phys.Vec3;

/** Shared discrete fallback integration for the server, radar, and client visual. */
public final class AntiAirFallbackTrajectory {
    public static final double DRAG = .997, GRAVITY_PER_TICK = .065;
    private AntiAirFallbackTrajectory() { }
    public static Vec3 nextVelocity(Vec3 v) { return new Vec3(v.x * DRAG, v.y * DRAG - GRAVITY_PER_TICK, v.z * DRAG); }
    public static Vec3 nextPosition(Vec3 p, Vec3 nextVelocity) { return p.add(nextVelocity); }
    public static Vec3 velocityAt(Vec3 initial, double ticks) {
        double n = Math.max(0, ticks), decay = Math.pow(DRAG, n);
        double gravity = GRAVITY_PER_TICK * (1 - decay) / (1 - DRAG);
        return new Vec3(initial.x * decay, initial.y * decay - gravity, initial.z * decay);
    }
    public static Vec3 positionAt(Vec3 initialPosition, Vec3 initialVelocity, double ticks) {
        double n = Math.max(0, ticks), decay = Math.pow(DRAG, n);
        double velocitySum = DRAG * (1 - decay) / (1 - DRAG);
        double gravitySum = GRAVITY_PER_TICK / (1 - DRAG) * (n - velocitySum);
        return initialPosition.add(initialVelocity.x * velocitySum,
            initialVelocity.y * velocitySum - gravitySum, initialVelocity.z * velocitySum);
    }
}