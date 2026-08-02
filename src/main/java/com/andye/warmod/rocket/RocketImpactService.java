package com.andye.warmod.rocket;

import com.andye.warmod.entity.RocketProjectileEntity;
import com.andye.warmod.testtool.TestExplosionService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class RocketImpactService {
    private RocketImpactService() { }
    public static void impact(final ServerLevel level, final RocketProjectileEntity rocket, final Vec3 position) {
        ServerPlayer owner = rocket.ownerId() == null ? null : level.getServer().getPlayerList().getPlayer(rocket.ownerId());
        if (owner != null && owner.level() != level) owner = null;
        TestExplosionService.createExplosion(level, owner, position, RocketConstants.EXPLOSION_STRENGTH);
        level.playSound(null, BlockPos.containing(position), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.35F, 1.0F);
        level.sendParticles(ParticleTypes.SMOKE, position.x, position.y, position.z, 18, 0.6, 0.45, 0.6, 0.08);
        level.sendParticles(ParticleTypes.FLAME, position.x, position.y, position.z, 10, 0.45, 0.35, 0.45, 0.06);
    }
}
