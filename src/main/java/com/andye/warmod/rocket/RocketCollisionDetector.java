package com.andye.warmod.rocket;

import com.andye.warmod.entity.RocketProjectileEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.HitResult;

public final class RocketCollisionDetector {
    private RocketCollisionDetector() { }
    public static HitResult detect(final RocketProjectileEntity rocket) {
        return ProjectileUtil.getHitResultOnMoveVector(rocket,
            entity -> entity.isAlive() && (rocket.age() >= RocketConstants.OWNER_IGNORE_TICKS
                || rocket.ownerId() == null || !rocket.ownerId().equals(entity.getUUID())));
    }
}
