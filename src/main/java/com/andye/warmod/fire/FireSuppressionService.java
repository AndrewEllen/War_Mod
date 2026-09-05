package com.andye.warmod.fire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public final class FireSuppressionService {
    private FireSuppressionService() { }

    public static int sprayHose(final ServerLevel level, final ServerPlayer player) {
        return spray(level, player, 26.0, 0.72, 1.45, 0.80F, ParticleTypes.SPLASH, true);
    }

    public static int sprayExtinguisher(final ServerLevel level, final ServerPlayer player) {
        return spray(level, player, 10.0, 0.48, 2.15, 2.75F, ParticleTypes.CLOUD, false);
    }

    private static int spray(final ServerLevel level, final ServerPlayer player,
        final double range, final double step, final double radius, final float amount,
        final ParticleOptions particle, final boolean water) {
        Vec3 start = player.getEyePosition().add(0.0, -0.12, 0.0);
        Vec3 direction = player.getViewVector(1.0F).normalize();
        Vec3 end = start;
        int samples = Math.max(1, (int) Math.ceil(range / step));
        for (int index = 1; index <= samples; index++) {
            Vec3 point = start.add(direction.scale(index * step));
            BlockPos position = BlockPos.containing(point);
            if (!FireSimulationManager.isLoaded(level, position)
                || !level.getWorldBorder().isWithinBounds(position)) break;
            if (!level.getBlockState(position).isAir()
                && !level.getBlockState(position).getCollisionShape(level, position).isEmpty()) {
                end = point.subtract(direction.scale(step * 0.5));
                level.sendParticles(particle, end.x, end.y, end.z,
                    water ? 3 : 5, 0.12, 0.12, 0.12, water ? 0.025 : 0.012);
                break;
            }
            end = point;
            if (index % 2 == 0) {
                level.sendParticles(particle, point.x, point.y, point.z,
                    water ? 2 : 3, 0.10, 0.10, 0.10, water ? 0.025 : 0.012);
            }
        }
        int affected = FireSimulationManager.suppressJet(level, player, start, end,
            radius, amount);
        level.playSound(null, player.blockPosition(), water ? SoundEvents.BUCKET_EMPTY
            : SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS,
            water ? 0.55F : 0.75F, water ? 1.35F : 0.90F);
        return affected;
    }
}
