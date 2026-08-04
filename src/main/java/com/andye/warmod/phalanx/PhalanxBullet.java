package com.andye.warmod.phalanx;

import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public final class PhalanxBullet {
    public final UUID bulletId;
    public final UUID turretId;
    public final UUID targetId;

    public Vec3 position;
    public Vec3 previousPosition;
    public Vec3 velocity;

    public int age;

    /**
     * Lifetime calculated from this shot's predicted interception time.
     */
    public final int maximumAge;

    public final long visualSeed;

    public PhalanxBullet(
        final UUID bulletId,
        final UUID turretId,
        final UUID targetId,
        final Vec3 position,
        final Vec3 velocity,
        final int maximumAge,
        final long visualSeed
    ) {
        this.bulletId = bulletId;
        this.turretId = turretId;
        this.targetId = targetId;

        this.position = position;
        this.previousPosition = position;
        this.velocity = velocity;

        this.maximumAge =
            Math.max(1, maximumAge);

        this.visualSeed = visualSeed;
    }

    public void tick() {
        previousPosition = position;

        velocity = velocity.add(
            0.0,
            -PhalanxConstants
                .BULLET_GRAVITY_PER_TICK_SQUARED,
            0.0
        );

        position =
            position.add(velocity);

        age++;
    }
}