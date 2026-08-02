package com.andye.warmod.rocket;

import com.andye.warmod.entity.RocketProjectileEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class RocketLaunchService {
    private RocketLaunchService() { }
    public static boolean launch(final ServerLevel level, final ServerPlayer player) {
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 start = player.getEyePosition().add(direction.scale(0.65));
        RocketProjectileEntity rocket = new RocketProjectileEntity(level, player.getUUID(), start,
            direction.scale(RocketConstants.SPEED_BLOCKS_PER_TICK));
        return level.addFreshEntity(rocket);
    }
}
