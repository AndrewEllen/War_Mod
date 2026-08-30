package com.andye.warmod.entity;

import com.andye.warmod.fire.wind.FireWindEngine;
import com.andye.warmod.rocket.RocketCollisionDetector;
import com.andye.warmod.rocket.RocketConstants;
import com.andye.warmod.rocket.RocketImpactService;
import com.andye.warmod.rocket.RocketPayloadType;
import com.andye.warmod.warhead.CancellationReason;
import com.andye.warmod.warhead.WarheadExplosionWorkManager;
import com.andye.warmod.warhead.WarheadPreparationCoordinator;
import com.andye.warmod.warhead.WarheadYield;
import com.andye.warmod.warhead.WarheadYieldRegistry;
import java.util.UUID;
import net.minecraft.core.SectionPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class RocketProjectileEntity extends Entity {
    private static final EntityDataAccessor<Integer> PAYLOAD = SynchedEntityData.defineId(
        RocketProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> VISUAL_SEED = SynchedEntityData.defineId(
        RocketProjectileEntity.class, EntityDataSerializers.LONG);
    private @Nullable UUID ownerId;
    private boolean impactHandled;
    private Vec3 pendingImpact;

    public RocketProjectileEntity(final EntityType<? extends RocketProjectileEntity> type,
        final Level level) { super(type, level); }

    public RocketProjectileEntity(final ServerLevel level, final UUID owner,
        final Vec3 position, final Vec3 velocity, final RocketPayloadType payload,
        final long seed) {
        this(ModEntityTypes.ROCKET_PROJECTILE, level);
        ownerId = owner; setPos(position); setDeltaMovement(velocity);
        setPayloadType(payload); getEntityData().set(VISUAL_SEED, seed);
    }

    @Override protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        builder.define(PAYLOAD, RocketPayloadType.HE.ordinal());
        builder.define(VISUAL_SEED, 0L);
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide() || !(level() instanceof ServerLevel server)) return;
        if (pendingImpact != null) {
            setDeltaMovement(Vec3.ZERO);
            if (terrainReady(server, pendingImpact)) {
                impactHandled = true;
                RocketImpactService.impact(server, this, pendingImpact);
                discard();
            }
            return;
        }
        if (tickCount >= RocketConstants.LIFETIME_TICKS || !position().isFinite()) {
            discard(); return;
        }
        Vec3 velocity = getDeltaMovement();
        Vec3 destination = position().add(velocity);
        if (!server.getChunkSource().hasChunk(
            SectionPos.blockToSectionCoord(destination.x),
            SectionPos.blockToSectionCoord(destination.z))) {
            discard(); return;
        }
        HitResult hit = RocketCollisionDetector.detect(this);
        if (hit.getType() != HitResult.Type.MISS) {
            if (!impactHandled) {
                if (!terrainReady(server, hit.getLocation())) {
                    pendingImpact = hit.getLocation();
                    setDeltaMovement(Vec3.ZERO);
                    return;
                }
                impactHandled = true;
                RocketImpactService.impact(server, this, hit.getLocation());
            }
            discard(); return;
        }
        setPos(destination);
        Vec3 wind = FireWindEngine.windAt(server, destination);
        Vec3 nextVelocity = velocity.scale(RocketConstants.DRAG_PER_TICK)
            .add(wind.x * RocketConstants.WIND_RESPONSE_PER_TICK,
                -RocketConstants.GRAVITY_PER_TICK,
                wind.z * RocketConstants.WIND_RESPONSE_PER_TICK);
        setDeltaMovement(nextVelocity);
    }

    private boolean terrainReady(final ServerLevel level, final Vec3 impact) {
        if (payloadType() != RocketPayloadType.NUCLEAR_ICBM) return true;
        WarheadYield yield = WarheadYieldRegistry.resolve(level, getUUID(), getUUID(),
            payloadType().warhead());
        Vec3 effective = WarheadExplosionWorkManager.resolveDetonationCenter(
            level, impact, yield);
        return WarheadPreparationCoordinator.ensureImpact(level, getUUID(), getUUID(),
            getUUID(), effective, yield, visualSeed(),
            WarheadYieldRegistry.usesCustomFire(level, getUUID(), getUUID()),
            level.getGameTime() + 1L);
    }

    public RocketPayloadType payloadType() {
        return RocketPayloadType.byId(getEntityData().get(PAYLOAD));
    }
    public void setPayloadType(final RocketPayloadType payload) {
        getEntityData().set(PAYLOAD, payload.ordinal());
    }
    public long visualSeed() { return getEntityData().get(VISUAL_SEED); }
    public @Nullable UUID ownerId() { return ownerId; }
    public int age() { return tickCount; }

    @Override protected void readAdditionalSaveData(final ValueInput input) {
        ownerId = input.read("owner", UUIDUtil.CODEC).orElse(null);
        impactHandled = input.getBooleanOr("impact_handled", false);
        pendingImpact = input.read("pending_impact", Vec3.CODEC).orElse(null);
        try { setPayloadType(RocketPayloadType.valueOf(input.getStringOr("payload", "HE"))); }
        catch (IllegalArgumentException exception) { setPayloadType(RocketPayloadType.HE); }
        getEntityData().set(VISUAL_SEED, input.getLongOr("visual_seed", 0L));
    }

    @Override protected void addAdditionalSaveData(final ValueOutput output) {
        output.storeNullable("owner", UUIDUtil.CODEC, ownerId);
        output.putBoolean("impact_handled", impactHandled);
        if (pendingImpact != null && pendingImpact.isFinite()) {
            output.store("pending_impact", Vec3.CODEC, pendingImpact);
        }
        output.putString("payload", payloadType().name());
        output.putLong("visual_seed", visualSeed());
    }

    @Override public boolean hurtServer(final ServerLevel level,
        final DamageSource source, final float amount) { return false; }
    @Override public void remove(final RemovalReason reason) {
        if (!impactHandled && level() instanceof ServerLevel server
            && payloadType() == RocketPayloadType.NUCLEAR_ICBM) {
            WarheadPreparationCoordinator.cancelImpact(server, getUUID(),
                CancellationReason.ENTITY_REMOVED);
        }
        super.remove(reason);
    }
    @Override public boolean shouldRenderAtSqrDistance(final double distance) {
        return distance <= RocketConstants.VISUAL_RANGE_BLOCKS
            * RocketConstants.VISUAL_RANGE_BLOCKS;
    }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith(final Entity entity) { return false; }
}
