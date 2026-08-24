package com.andye.warmod.firearm;

import com.andye.warmod.acoustics.AcousticEngine;
import com.andye.warmod.acoustics.AcousticSounds;
import com.andye.warmod.fire.wind.FireWindEngine;
import com.andye.warmod.firearm.network.ClientboundFirearmImpactPayload;
import com.andye.warmod.firearm.network.ClientboundFirearmShotPayload;
import com.andye.warmod.firearm.network.FirearmNetworking;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** High-speed data bullets with continuous block/entity collision. */
public final class FirearmBulletManager {
    private static final int MAX_ACTIVE_BULLETS = 8_192;
    private static final double NEAR_MISS_RADIUS_SQUARED = 4.75 * 4.75;
    private static final double COMBINED_IMPACT_RADIUS_SQUARED = 28.0 * 28.0;
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
        if (!shooter.hasInfiniteMaterials()) ammunition.hurtAndBreak(1, level, shooter,
            broken -> { });
        shooter.setDeltaMovement(shooter.getDeltaMovement().add(look.scale(
            type == FirearmType.SNIPER_RIFLE ? -0.075 : -0.025)));
        shooter.swing(InteractionHand.MAIN_HAND);
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
                HitRegion hitRegion = hitEntity == null ? null
                    : applyDamage(level, bullet, hitEntity, entityPoint);
                ImpactMaterial material = hitEntity != null
                    ? (hasHardArmor(hitEntity, hitRegion)
                        ? ImpactMaterial.METAL : ImpactMaterial.BODY)
                    : (isHardSurface(level.getBlockState(blockHit.getBlockPos()))
                        ? ImpactMaterial.METAL : ImpactMaterial.DIRT);
                Set<UUID> combinedListeners = emitFlybys(level, bullet, from, nearest,
                    hitEntity, material == ImpactMaterial.METAL);
                impact(level, bullet, nearest, material, combinedListeners);
                iterator.remove(); continue;
            }
            emitFlybys(level, bullet, from, to, null, false);
            bullet.position = to;
            Vec3 wind = FireWindEngine.windAt(level, to);
            bullet.velocity = bullet.velocity.add(wind.x * bullet.type.windInfluence(),
                -bullet.type.gravity(), wind.z * bullet.type.windInfluence());
        }
        if (bullets.isEmpty()) ACTIVE.remove(level);
    }

    private static HitRegion applyDamage(final ServerLevel level, final Bullet bullet,
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
        return region;
    }

    private static void impact(final ServerLevel level, final Bullet bullet,
        final Vec3 position, final ImpactMaterial material,
        final Set<UUID> combinedListeners) {
        FirearmNetworking.send(level, position,
            new ClientboundFirearmImpactPayload(bullet.id, position));
        Identifier definition = switch (material) {
            case DIRT -> AcousticSounds.BULLET_IMPACT_DIRT_ID;
            case BODY -> AcousticSounds.BULLET_IMPACT_BODY_ID;
            case METAL -> AcousticSounds.BULLET_IMPACT_METAL_ID;
        };
        float pitch = 0.96F + (float) ((bullet.seed >>> 8) & 15L) / 200.0F;
		float volume = material == ImpactMaterial.DIRT ? 1.0F : 0.88F;
        for (ServerPlayer player : PlayerLookup.level(level)) {
            if (!combinedListeners.contains(player.getUUID())) {
                AcousticEngine.playSoundFor(player, level, position, definition,
                    SoundSource.PLAYERS, volume, pitch);
            }
        }
    }

    private static Set<UUID> emitFlybys(final ServerLevel level, final Bullet bullet,
        final Vec3 start, final Vec3 end, final LivingEntity hitEntity,
        final boolean hardImpact) {
        Set<UUID> combinedListeners = new HashSet<>();
        Vec3 segment = end.subtract(start);
        double lengthSquared = segment.lengthSqr();
        if (lengthSquared < 1.0E-8) return combinedListeners;
        for (ServerPlayer player : PlayerLookup.level(level)) {
            UUID playerId = player.getUUID();
            if (playerId.equals(bullet.owner) || bullet.flybyListeners.contains(playerId)
                || hitEntity != null && playerId.equals(hitEntity.getUUID())) continue;
            Vec3 ear = player.getEyePosition();
            double along = Math.max(0.0, Math.min(1.0,
                ear.subtract(start).dot(segment) / lengthSquared));
            Vec3 closest = start.add(segment.scale(along));
            if (ear.distanceToSqr(closest) > NEAR_MISS_RADIUS_SQUARED) continue;

            boolean combined = hardImpact
                && end.distanceToSqr(ear) <= COMBINED_IMPACT_RADIUS_SQUARED;
            Identifier definition = combined
                ? AcousticSounds.BULLET_PASS_METAL_ID : AcousticSounds.BULLET_CRACK_ID;
            float typePitch = switch (bullet.type) {
                case PISTOL -> 1.08F;
                case ASSAULT_RIFLE -> 1.0F;
                case SNIPER_RIFLE -> 0.94F;
            };
            float seedPitch = 0.97F + (float) ((bullet.seed >>> 16) & 15L) / 250.0F;
            AcousticEngine.playSoundFor(player, level, closest, definition,
                SoundSource.PLAYERS, combined ? 1.0F : 0.88F, typePitch * seedPitch);
            bullet.flybyListeners.add(playerId);
            if (combined) combinedListeners.add(playerId);
        }
        return combinedListeners;
    }

    private static boolean isHardSurface(final BlockState state) {
        SoundType sound = state.getSoundType();
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE)
            || sound == SoundType.METAL || sound == SoundType.ANVIL
            || sound == SoundType.COPPER || sound == SoundType.COPPER_BULB
            || sound == SoundType.COPPER_GRATE || sound == SoundType.CHAIN
            || sound == SoundType.IRON || sound == SoundType.NETHERITE_BLOCK
            || sound == SoundType.STONE || sound == SoundType.DEEPSLATE
            || sound == SoundType.DEEPSLATE_BRICKS || sound == SoundType.DEEPSLATE_TILES
            || sound == SoundType.POLISHED_DEEPSLATE || sound == SoundType.TUFF
            || sound == SoundType.TUFF_BRICKS || sound == SoundType.POLISHED_TUFF;
    }

    private static boolean hasHardArmor(final LivingEntity target, final HitRegion region) {
        if (region == null) return false;
        return switch (region) {
            case HEAD -> isHardArmorPiece(target.getItemBySlot(EquipmentSlot.HEAD));
            case BODY -> isHardArmorPiece(target.getItemBySlot(EquipmentSlot.CHEST));
            case LEG -> isHardArmorPiece(target.getItemBySlot(EquipmentSlot.LEGS))
                || isHardArmorPiece(target.getItemBySlot(EquipmentSlot.FEET));
        };
    }

    private static boolean isHardArmorPiece(final ItemStack stack) {
        return stack.is(Items.COPPER_HELMET) || stack.is(Items.COPPER_CHESTPLATE)
            || stack.is(Items.COPPER_LEGGINGS) || stack.is(Items.COPPER_BOOTS)
            || stack.is(Items.CHAINMAIL_HELMET) || stack.is(Items.CHAINMAIL_CHESTPLATE)
            || stack.is(Items.CHAINMAIL_LEGGINGS) || stack.is(Items.CHAINMAIL_BOOTS)
            || stack.is(Items.IRON_HELMET) || stack.is(Items.IRON_CHESTPLATE)
            || stack.is(Items.IRON_LEGGINGS) || stack.is(Items.IRON_BOOTS)
            || stack.is(Items.GOLDEN_HELMET) || stack.is(Items.GOLDEN_CHESTPLATE)
            || stack.is(Items.GOLDEN_LEGGINGS) || stack.is(Items.GOLDEN_BOOTS)
            || stack.is(Items.DIAMOND_HELMET) || stack.is(Items.DIAMOND_CHESTPLATE)
            || stack.is(Items.DIAMOND_LEGGINGS) || stack.is(Items.DIAMOND_BOOTS)
            || stack.is(Items.NETHERITE_HELMET) || stack.is(Items.NETHERITE_CHESTPLATE)
            || stack.is(Items.NETHERITE_LEGGINGS) || stack.is(Items.NETHERITE_BOOTS);
    }

    private enum HitRegion { HEAD, BODY, LEG }
    private enum ImpactMaterial { DIRT, BODY, METAL }

    private static final class Bullet {
        private final UUID id;
        private final UUID owner;
        private final FirearmType type;
        private Vec3 position;
        private Vec3 velocity;
        private final long seed;
        private final Set<UUID> flybyListeners = new HashSet<>();
        private int age;
        private Bullet(final UUID id, final UUID owner, final FirearmType type,
            final Vec3 position, final Vec3 velocity, final long seed) {
            this.id = id; this.owner = owner; this.type = type;
            this.position = position; this.velocity = velocity; this.seed = seed;
        }
    }
}
