package com.andye.warmod.phalanx;

import com.andye.warmod.acoustics.ModSoundEvents;
import com.andye.warmod.block.entity.PhalanxBlockEntity;
import com.andye.warmod.phalanx.network.ClientboundPhalanxImpactPayload;
import com.andye.warmod.phalanx.network.ClientboundPhalanxShotPayload;
import com.andye.warmod.phalanx.network.PhalanxNetworking;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class PhalanxBulletManager {
    private static final Map<
        ServerLevel,
        LinkedHashMap<UUID, PhalanxBullet>
    > ACTIVE = new WeakHashMap<>();

    private static boolean registered;

    private PhalanxBulletManager() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        ServerTickEvents.END_LEVEL_TICK.register(
            PhalanxBulletManager::tick
        );

        ServerLifecycleEvents.SERVER_STOPPED.register(
            server -> ACTIVE.clear()
        );

        registered = true;
    }

    public static synchronized boolean fire(
        final ServerLevel level,
        final PhalanxBlockEntity turret,
        final UUID target,
        final Vec3 origin,
        final Vec3 velocity,
        final int maximumAge,
        final long seed
    ) {
        LinkedHashMap<UUID, PhalanxBullet> bullets =
            ACTIVE.computeIfAbsent(
                level,
                ignored -> new LinkedHashMap<>()
            );

        if (bullets.size()
            >= PhalanxConstants.MAX_ACTIVE_BULLETS_PER_LEVEL) {
            return false;
        }

        UUID bulletId = UUID.randomUUID();

        bullets.put(
            bulletId,
            new PhalanxBullet(
                bulletId,
                turret.turretId(),
                target,
                turret.ownership(),
                origin,
                velocity,
                maximumAge,
                seed
            )
        );

        float pitch =
            0.94F
                + (float)(
                    ((seed >>> 8) & 15L)
                        / 120.0
                );

        level.playSound(
            null,
            BlockPos.containing(origin),
            ModSoundEvents.PHALANX_FIRE,
            SoundSource.BLOCKS,
            0.72F,
            pitch
        );

        level.sendParticles(
            ParticleTypes.SMOKE,
            origin.x,
            origin.y,
            origin.z,
            2,
            0.05,
            0.05,
            0.05,
            0.015
        );

        PhalanxNetworking.send(
            level,
            new ClientboundPhalanxShotPayload(
                bulletId,
                turret.turretId(),
                target,
                origin,
                velocity,
                seed
            )
        );

        return true;
    }

    public static synchronized void removeForTurret(
        final ServerLevel level,
        final UUID turretId
    ) {
        LinkedHashMap<UUID, PhalanxBullet> bullets =
            ACTIVE.get(level);

        if (bullets == null) {
            return;
        }

        bullets.values().removeIf(
            bullet -> bullet.turretId.equals(turretId)
        );

        if (bullets.isEmpty()) {
            ACTIVE.remove(level);
        }
    }

    private static synchronized void tick(
        final ServerLevel level
    ) {
        LinkedHashMap<UUID, PhalanxBullet> bullets =
            ACTIVE.get(level);

        if (bullets == null) {
            return;
        }

        Map<UUID, PhalanxTargetSnapshot> targets =
            new HashMap<>();

        for (PhalanxTargetSnapshot target
            : PhalanxTargetService.snapshot(level)) {
            targets.put(
                target.targetId(),
                target
            );
        }

        var iterator =
            bullets.values().iterator();

        while (iterator.hasNext()) {
            PhalanxBullet bullet =
                iterator.next();

            bullet.tick();

            if (bullet.age > bullet.maximumAge
                || !bullet.position.isFinite()) {
                iterator.remove();
                continue;
            }

            BlockPos previousChunkPosition =
                BlockPos.containing(
                    bullet.previousPosition
                );

            BlockPos currentChunkPosition =
                BlockPos.containing(
                    bullet.position
                );

            if (!level.hasChunkAt(previousChunkPosition)
                || !level.hasChunkAt(currentChunkPosition)) {
                iterator.remove();
                continue;
            }

            /*
             * A Phalanx bullet is simulated data rather than an Entity.
             *
             * Minecraft 26.2 no longer permits a null Entity in ClipContext:
             * that overload immediately calls CollisionContext.of(entity).
             * Use the dedicated CollisionContext overload with the empty
             * context for a synthetic projectile raycast.
             */
            BlockHitResult blockHit =
                level.clip(
                    new ClipContext(
                        bullet.previousPosition,
                        bullet.position,
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        CollisionContext.empty()
                    )
                );

            double blockDistanceSquared =
                blockHit.getType()
                    == HitResult.Type.MISS
                        ? Double.POSITIVE_INFINITY
                        : blockHit.getLocation()
                            .distanceToSqr(
                                bullet.previousPosition
                            );

            PhalanxTargetSnapshot target =
                targets.get(bullet.targetId);

            if (target != null
                && bullet.ownership.isHostile(target.ownerPlayerId(), target.forcedHostile())
                && PhalanxBulletCollision.intersects(
                    bullet.previousPosition,
                    bullet.position,
                    target.position(),
                    target.hitRadius()
                )) {
                double targetDistanceSquared =
                    target.position()
                        .distanceToSqr(
                            bullet.previousPosition
                        );

                if (targetDistanceSquared
                    <= blockDistanceSquared) {
                    PointDefenceInterceptionService.intercept(
                        level,
                        target,
                        bullet.bulletId,
                        target.position(),
                        bullet.ownership
                    );

                    PhalanxNetworking.send(
                        level,
                        new ClientboundPhalanxImpactPayload(
                            bullet.bulletId
                        )
                    );

                    iterator.remove();
                    continue;
                }
            }

            if (Double.isFinite(
                blockDistanceSquared
            )) {
                iterator.remove();
            }
        }

        if (bullets.isEmpty()) {
            ACTIVE.remove(level);
        }
    }
}
