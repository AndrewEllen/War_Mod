package com.andye.warmod.firearm;

import com.andye.warmod.acoustics.AcousticEngine;
import com.andye.warmod.acoustics.AcousticSounds;
import com.andye.warmod.fire.wind.FireWindEngine;
import com.andye.warmod.firearm.network.ClientboundFirearmImpactPayload;
import com.andye.warmod.firearm.network.ClientboundFirearmShotPayload;
import com.andye.warmod.firearm.network.FirearmNetworking;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** High-speed data bullets with continuous block/entity collision. */
public final class FirearmBulletManager {
    private static final int MAX_ACTIVE_BULLETS = 8_192;
    private static final Map<ServerLevel, LinkedHashMap<UUID, Bullet>> ACTIVE =
        new IdentityHashMap<>();
    private static final Map<UUID, long[]> LAST_FIRE_TICK = new LinkedHashMap<>();
    private static boolean registered;

    private FirearmBulletManager() { }

    public static void register() {
        if (registered) return;
        ServerTickEvents.END_LEVEL_TICK.register(FirearmBulletManager::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            synchronized (FirearmBulletManager.class) {
                ACTIVE.clear(); LAST_FIRE_TICK.clear();
            }
        });
        registered = true;
    }

    public static synchronized boolean fire(final ServerLevel level,
        final ServerPlayer shooter, final FirearmType type) {
        long now = level.getGameTime();
        long[] last = LAST_FIRE_TICK.computeIfAbsent(shooter.getUUID(), ignored ->
            new long[] { Long.MIN_VALUE / 2, Long.MIN_VALUE / 2, Long.MIN_VALUE / 2 });
        if (now - last[type.ordinal()] < type.intervalTicks()) return false;
        ItemStack ammunition = findAmmunition(shooter, type);
        if (ammunition.isEmpty() && !shooter.hasInfiniteMaterials()) return false;
        LinkedHashMap<UUID, Bullet> bullets = ACTIVE.computeIfAbsent(level,
            ignored -> new LinkedHashMap<>());
        if (bullets.size() >= MAX_ACTIVE_BULLETS) return false;

        long seed = level.getRandom().nextLong();
        SplittableRandom random = new SplittableRandom(seed);
        Vec3 look = shooter.getLookAngle().normalize();
        Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
        if (right.lengthSqr() < 1.0E-8) right = new Vec3(1.0, 0.0, 0.0);
        else right = right.normalize();
        Vec3 up = right.cross(look).normalize();
        Vec3 direction = look.add(right.scale(random.nextDouble(-type.spread(), type.spread())))
            .add(up.scale(random.nextDouble(-type.spread(), type.spread()))).normalize();
        Vec3 origin = shooter.getEyePosition().add(direction.scale(0.72)).add(0.0, -0.06, 0.0);
        Vec3 velocity = direction.scale(type.muzzleSpeed());
        Vec3 wind = FireWindEngine.windAt(level, origin);
        Vec3 acceleration = new Vec3(wind.x * type.windInfluence(), -type.gravity(),
            wind.z * type.windInfluence());
        UUID shotId = UUID.randomUUID();
        bullets.put(shotId, new Bullet(shotId, shooter.getUUID(), type, origin,
            velocity, seed));
        last[type.ordinal()] = now;
        if (!shooter.hasInfiniteMaterials()) ammunition.shrink(1);
        shooter.setDeltaMovement(shooter.getDeltaMovement().add(look.scale(
            type == FirearmType.SNIPER_RIFLE ? -0.075 : -0.025)));
        shooter.swing(shooter.getUsedItemHand());
        AcousticEngine.playSound(level, origin, type.acousticDefinition(),
            SoundSource.PLAYERS, 1.0F, 0.97F + random.nextFloat() * 0.06F);
        FirearmNetworking.send(level, origin, new ClientboundFirearmShotPayload(shotId,
            (byte) type.ordinal(), origin, velocity, acceleration, seed, type.maximumAge()));
        return true;
    }

    private static ItemStack findAmmunition(final ServerPlayer player,
        final FirearmType type) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems())
            if (stack.is(type.ammunition())) return stack;
        ItemStack offhand = player.getOffhandItem();
        return offhand.is(type.ammunition()) ? offhand : ItemStack.EMPTY;
    }

    private static synchronized void tick(final ServerLevel level) {
        LinkedHashMap<UUID, Bullet> bullets = ACTIVE.get(level);
        if (bullets == null) return;
        Iterator<Bullet> iterator = bullets.values().iterator();
        while (iterator.hasNext()) {
            Bullet bullet = iterator.next();
            if (++bullet.age > bullet.type.maximumAge() || !bullet.position.isFinite()) {
                iterator.remove(); continue;
            }
            Vec3 from = bullet.position;
            Vec3 to = from.add(bullet.velocity);
            if (!level.hasChunkAt(BlockPos.containing(from))
                || !level.hasChunkAt(BlockPos.containing(to))) {
                iterator.remove(); continue;
            }
            BlockHitResult blockHit = level.clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            Vec3 nearest = blockHit.getType() == HitResult.Type.MISS ? null
                : blockHit.getLocation();
            double nearestDistance = nearest == null ? Double.POSITIVE_INFINITY
                : from.distanceToSqr(nearest);
            LivingEntity hitEntity = null;
            Vec3 entityPoint = null;
            AABB sweep = new AABB(from, to).inflate(0.35);
            for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class,
                sweep, entity -> entity.isAlive() && !entity.getUUID().equals(bullet.owner))) {
                Optional<Vec3> clipped = candidate.getBoundingBox().inflate(0.10).clip(from, to);
                if (clipped.isEmpty()) continue;
                double distance = from.distanceToSqr(clipped.get());
                if (distance < nearestDistance) {
                    nearestDistance = distance; nearest = clipped.get();
                    entityPoint = clipped.get(); hitEntity = candidate;
                }
            }
            if (nearest != null) {
                if (hitEntity != null) applyDamage(level, bullet, hitEntity, entityPoint);
                impact(level, bullet, nearest);
                iterator.remove(); continue;
            }
            bullet.position = to;
            Vec3 wind = FireWindEngine.windAt(level, to);
            bullet.velocity = bullet.velocity.add(wind.x * bullet.type.windInfluence(),
                -bullet.type.gravity(), wind.z * bullet.type.windInfluence());
        }
        if (bullets.isEmpty()) ACTIVE.remove(level);
    }

    private static void applyDamage(final ServerLevel level, final Bullet bullet,
        final LivingEntity target, final Vec3 hit) {
        AABB box = target.getBoundingBox();
        double heightFraction = (hit.y - box.minY) / Math.max(0.01, box.getYsize());
        HitRegion region = heightFraction >= 0.76 ? HitRegion.HEAD
            : heightFraction <= 0.38 ? HitRegion.LEG : HitRegion.BODY;
        float damage = switch (region) {
            case HEAD -> bullet.type.headDamage();
            case BODY -> bullet.type.bodyDamage();
            case LEG -> bullet.type.legDamage();
        };
        if (region == HitRegion.HEAD && bullet.type == FirearmType.SNIPER_RIFLE) {
            ItemStack helmet = target.getItemBySlot(EquipmentSlot.HEAD);
            boolean protectedHead = helmet.is(Items.DIAMOND_HELMET)
                || helmet.is(Items.NETHERITE_HELMET);
            damage = protectedHead ? 13.0F
                : Math.max(48.0F, target.getHealth() + target.getAbsorptionAmount() + 24.0F);
        }
        ServerPlayer shooter = level.getServer().getPlayerList().getPlayer(bullet.owner);
        target.hurtServer(level, shooter == null ? level.damageSources().generic()
            : level.damageSources().playerAttack(shooter), damage);
        if (region == HitRegion.LEG)
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 1), shooter);
    }

    private static void impact(final ServerLevel level, final Bullet bullet,
        final Vec3 position) {
        FirearmNetworking.send(level, position,
            new ClientboundFirearmImpactPayload(bullet.id, position));
        AcousticEngine.playSound(level, position, AcousticSounds.BULLET_CRACK_ID,
            SoundSource.PLAYERS, 0.88F, 0.96F + (float) ((bullet.seed >>> 8) & 15L) / 200.0F);
    }

    private enum HitRegion { HEAD, BODY, LEG }

    private static final class Bullet {
        private final UUID id;
        private final UUID owner;
        private final FirearmType type;
        private Vec3 position;
        private Vec3 velocity;
        private final long seed;
        private int age;
        private Bullet(final UUID id, final UUID owner, final FirearmType type,
            final Vec3 position, final Vec3 velocity, final long seed) {
            this.id = id; this.owner = owner; this.type = type;
            this.position = position; this.velocity = velocity; this.seed = seed;
        }
    }
}
