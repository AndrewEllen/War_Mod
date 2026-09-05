package com.andye.warmod.entity;

import com.andye.warmod.artillery.ArtilleryPayload;
import com.andye.warmod.warhead.CancellationReason;
import com.andye.warmod.warhead.PreparedImpactSpec;
import com.andye.warmod.warhead.WarheadDeliveryMode;
import com.andye.warmod.warhead.WarheadExplosionWorkManager;
import com.andye.warmod.warhead.WarheadFireSettings;
import com.andye.warmod.warhead.WarheadImpactService;
import com.andye.warmod.warhead.WarheadPreparationCoordinator;
import com.andye.warmod.warhead.WarheadPreparationRequest;
import com.andye.warmod.warhead.WarheadYield;
import com.andye.warmod.warhead.WarheadYieldRegistry;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public final class TimedWarheadTntEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_YIELD =
        SynchedEntityData.defineId(TimedWarheadTntEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_CLUSTER =
        SynchedEntityData.defineId(TimedWarheadTntEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_FUSE =
        SynchedEntityData.defineId(TimedWarheadTntEntity.class, EntityDataSerializers.INT);
    private UUID chargeId = UUID.randomUUID();
    private UUID ownerId;
    private WarheadYield yield = WarheadYield.CONVENTIONAL;
    private boolean cluster;
    private int fuse;
    private long seed;
    private boolean customFire;
    private boolean terrainPreparationScheduled;
    private Vec3 preparationTarget;
    private boolean exploded;

    public TimedWarheadTntEntity(final EntityType<? extends TimedWarheadTntEntity> type,
        final Level level) {
        super(type, level);
    }

    public TimedWarheadTntEntity(final ServerLevel level, final UUID ownerId,
        final Vec3 position, final Vec3 velocity, final ArtilleryPayload payload) {
        this(ModEntityTypes.TIMED_WARHEAD_TNT, level);
        this.ownerId = ownerId;
        yield = payload.yield();
        cluster = payload.cluster();
        getEntityData().set(DATA_YIELD, yield.ordinal());
        getEntityData().set(DATA_CLUSTER, cluster);
        fuse = yield.nuclear() ? 600 : 200;
        getEntityData().set(DATA_FUSE, fuse);
        seed = java.util.concurrent.ThreadLocalRandom.current().nextLong();
        customFire = WarheadFireSettings.get(level).customFire();
        setPos(position);
        setDeltaMovement(velocity);
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        builder.define(DATA_YIELD, WarheadYield.CONVENTIONAL.ordinal());
        builder.define(DATA_CLUSTER, false);
        builder.define(DATA_FUSE, 200);
    }

    public WarheadYield yield() {
        if (!level().isClientSide()) return yield;
        int ordinal = getEntityData().get(DATA_YIELD);
        return ordinal >= 0 && ordinal < WarheadYield.values().length
            ? WarheadYield.values()[ordinal] : WarheadYield.CONVENTIONAL;
    }

    public boolean cluster() {
        return level().isClientSide() ? getEntityData().get(DATA_CLUSTER) : cluster;
    }

    public int fuse() {
        return level().isClientSide() ? getEntityData().get(DATA_FUSE) : fuse;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        ServerLevel server = (ServerLevel)level();
        move(MoverType.SELF, getDeltaMovement());
        Vec3 velocity = getDeltaMovement().add(0.0, -0.04, 0.0).scale(0.98);
        if (onGround()) {
            velocity = new Vec3(velocity.x * 0.7, -velocity.y * 0.5,
                velocity.z * 0.7);
        }
        setDeltaMovement(velocity);

        if (yield.nuclear() && (!terrainPreparationScheduled
            || preparationTarget == null
            || preparationTargetMoved(preparationTarget, position()))) {
            schedulePreparation(server, position());
        }
        fuse = nextFuse(fuse);
        getEntityData().set(DATA_FUSE, fuse);
        if (fuse <= 0) explode(server, position());
    }

    private void schedulePreparation(final ServerLevel level, final Vec3 center) {
        if (!yield.nuclear() || center == null || !center.isFinite()) return;
        ArrayList<PreparedImpactSpec> impacts = new ArrayList<>();
        int count = cluster ? 4 : 1;
        for (int index = 0; index < count; index++) {
            Vec3 point = impactPoint(center, index, count);
            Vec3 effective = WarheadExplosionWorkManager.resolveDetonationCenter(
                level, point, yield);
            impacts.add(new PreparedImpactSpec(impactId(index), effective,
                yield.payloadType(), yield,
                seed + index * 0x9E3779B97F4A7C15L, customFire));
        }
        WarheadPreparationCoordinator.request(level, new WarheadPreparationRequest(
            chargeId, chargeId, level.dimension(), impacts,
            level.getGameTime() + Math.max(1, fuse),
            cluster ? WarheadDeliveryMode.CLUSTER_FOUR : WarheadDeliveryMode.SINGLE));
        preparationTarget = center;
        terrainPreparationScheduled = true;
    }

    static int nextFuse(final int currentFuse) {
        return Math.max(0, currentFuse - 1);
    }

    /** Called by arming sites immediately after the entity is accepted by the level. */
    public void beginTerrainPreparation(final ServerLevel level) {
        if (!terrainPreparationScheduled && yield.nuclear()) {
            schedulePreparation(level, position());
        }
    }

    private void explode(final ServerLevel level, final Vec3 center) {
        if (exploded) return;
        exploded = true;
        ServerPlayer owner = ownerId == null ? null
            : level.getServer().getPlayerList().getPlayer(ownerId);
        if (owner != null && owner.level() != level) owner = null;
        int count = cluster ? 4 : 1;
        for (int index = 0; index < count; index++) {
            Vec3 point = impactPoint(center, index, count);
            UUID id = impactId(index);
            WarheadYieldRegistry.put(level, id, yield, customFire);
            WarheadImpactService.impact(level, owner, id, id, point,
                seed + index * 0x9E3779B97F4A7C15L, yield.payloadType());
        }
        discard();
    }

    private static Vec3 impactPoint(final Vec3 center, final int index, final int count) {
        double angle = Math.PI * 2.0 * index / count;
        return count == 1 ? center : center.add(Math.cos(angle) * 4.5, 0.0,
            Math.sin(angle) * 4.5);
    }

    static boolean preparationTargetMoved(final Vec3 previous, final Vec3 current) {
        return previous == null || current == null
            || previous.distanceToSqr(current) > 4.0;
    }

    private UUID impactId(final int index) {
        return !cluster ? chargeId : new UUID(
            chargeId.getMostSignificantBits()
                ^ 0x9E3779B97F4A7C15L * (index + 1L),
            chargeId.getLeastSignificantBits()
                ^ 0xD1B54A32D192ED03L * (index + 1L));
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        output.store("id", UUIDUtil.CODEC, chargeId);
        output.storeNullable("owner", UUIDUtil.CODEC, ownerId);
        output.putString("yield", yield.getSerializedName());
        output.putBoolean("cluster", cluster);
        output.putInt("fuse", fuse);
        output.putLong("seed", seed);
        output.putBoolean("custom_fire", customFire);
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        chargeId = input.read("id", UUIDUtil.CODEC).orElseGet(UUID::randomUUID);
        ownerId = input.read("owner", UUIDUtil.CODEC).orElse(null);
        yield = WarheadYield.fromSerializedName(input.getStringOr("yield", "conventional"))
            .orElse(WarheadYield.CONVENTIONAL);
        cluster = input.getBooleanOr("cluster", false);
        getEntityData().set(DATA_YIELD, yield.ordinal());
        getEntityData().set(DATA_CLUSTER, cluster);
        fuse = input.getIntOr("fuse", yield.nuclear() ? 600 : 200);
        getEntityData().set(DATA_FUSE, fuse);
        seed = input.getLongOr("seed", 0L);
        customFire = input.getBooleanOr("custom_fire",
            level() instanceof ServerLevel server
                && WarheadFireSettings.get(server).customFire());
    }

    @Override
    public void remove(final RemovalReason reason) {
        if (!exploded && level() instanceof ServerLevel server && yield.nuclear()) {
            WarheadPreparationCoordinator.cancelPreparation(server, chargeId,
                CancellationReason.ENTITY_REMOVED);
        }
        super.remove(reason);
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source,
        final float amount) {
        return false;
    }
}
