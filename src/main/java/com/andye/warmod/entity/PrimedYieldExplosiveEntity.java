package com.andye.warmod.entity;

import com.andye.warmod.artillery.ArtilleryConstants;
import com.andye.warmod.icbm.IcbmConstants;
import com.andye.warmod.warhead.WarheadImpactChunkLeaseManager;
import com.andye.warmod.warhead.WarheadImpactService;
import com.andye.warmod.warhead.WarheadYield;
import com.andye.warmod.warhead.WarheadYieldRegistry;
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
import org.jspecify.annotations.Nullable;

/** Throwable, persistent primed explosive backed by the normal WarheadImpactService. */
public final class PrimedYieldExplosiveEntity extends Entity {
    private static final EntityDataAccessor<Integer> YIELD =
        SynchedEntityData.defineId(PrimedYieldExplosiveEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> CLUSTER =
        SynchedEntityData.defineId(PrimedYieldExplosiveEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> FUSE =
        SynchedEntityData.defineId(PrimedYieldExplosiveEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> VISUAL_SEED =
        SynchedEntityData.defineId(PrimedYieldExplosiveEntity.class, EntityDataSerializers.LONG);

    private UUID explosiveId = UUID.randomUUID();
    private @Nullable UUID ownerPlayerId;
    private boolean detonated;

    public PrimedYieldExplosiveEntity(final EntityType<? extends PrimedYieldExplosiveEntity> type,
        final Level level) {
        super(type, level);
    }

    public PrimedYieldExplosiveEntity(final ServerLevel level, final @Nullable UUID ownerPlayerId,
        final Vec3 position, final Vec3 velocity, final WarheadYield yield,
        final boolean cluster, final int fuseTicks) {
        this(ModEntityTypes.PRIMED_YIELD_EXPLOSIVE, level);
        this.ownerPlayerId = ownerPlayerId;
        this.explosiveId = UUID.randomUUID();
        setPos(position);
        setDeltaMovement(velocity);
        getEntityData().set(YIELD, yield.ordinal());
        getEntityData().set(CLUSTER, cluster);
        getEntityData().set(FUSE, Math.max(1, fuseTicks));
        getEntityData().set(VISUAL_SEED, deriveSeed(this.explosiveId));
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        builder.define(YIELD, WarheadYield.CONVENTIONAL.ordinal());
        builder.define(CLUSTER, false);
        builder.define(FUSE, ArtilleryConstants.CONVENTIONAL_FUSE_TICKS);
        builder.define(VISUAL_SEED, 0L);
    }

    public WarheadYield yield() {
        WarheadYield[] values = WarheadYield.values();
        int index = getEntityData().get(YIELD);
        return values[Math.max(0, Math.min(values.length - 1, index))];
    }
    public boolean cluster() { return getEntityData().get(CLUSTER); }
    public int fuseTicks() { return getEntityData().get(FUSE); }
    public long visualSeed() { return getEntityData().get(VISUAL_SEED); }
    public int age() { return tickCount; }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved() || detonated) return;

        Vec3 velocity = getDeltaMovement().add(0.0, -0.04, 0.0);
        move(MoverType.SELF, velocity);
        boolean grounded = onGround();
        double horizontalDrag = grounded ? 0.72 : 0.985;
        double verticalDrag = grounded ? -0.28 : 0.985;
        setDeltaMovement(velocity.x * horizontalDrag,
            velocity.y * verticalDrag, velocity.z * horizontalDrag);

        if (!(level() instanceof ServerLevel server)) return;
        int fuse = Math.max(0, fuseTicks() - 1);
        getEntityData().set(FUSE, fuse);
        if (fuse <= 0 || tickCount >= ArtilleryConstants.PRIMED_EXPLOSIVE_MAX_LIFETIME_TICKS) {
            detonate(server);
        }
    }

    private void detonate(final ServerLevel server) {
        if (detonated) return;
        detonated = true;
        ServerPlayer owner = null;
        if (ownerPlayerId != null && server.getServer() != null) {
            ServerPlayer candidate = server.getServer().getPlayerList().getPlayer(ownerPlayerId);
            if (candidate != null && candidate.level() == server) owner = candidate;
        }

        Vec3 center = position();
        if (!cluster()) {
            detonateOne(server, owner, explosiveId, center, visualSeed());
        } else {
            double rotation = ((visualSeed() >>> 12) & 65535L) / 65535.0 * Math.PI * 2.0;
            for (int index = 0; index < ArtilleryConstants.CLUSTER_CHILDREN; index++) {
                double angle = rotation + index * Math.PI * 0.5;
                Vec3 child = center.add(Math.cos(angle) * ArtilleryConstants.CLUSTER_SPREAD_RADIUS_BLOCKS,
                    0.0, Math.sin(angle) * ArtilleryConstants.CLUSTER_SPREAD_RADIUS_BLOCKS);
                UUID childId = UUID.randomUUID();
                detonateOne(server, owner, childId, child,
                    visualSeed() + index * 0x9E3779B97F4A7C15L);
            }
        }
        discard();
    }

    private void detonateOne(final ServerLevel server, final @Nullable ServerPlayer owner,
        final UUID id, final Vec3 position, final long seed) {
        WarheadYieldRegistry.put(server, id, this.yield());
        WarheadImpactChunkLeaseManager.hold(server, id, position,
            IcbmConstants.IMPACT_CHUNK_TAIL_TICKS);
        WarheadImpactService.detonateAt(server, owner, id, id, position, seed,
            this.yield().payloadType(), false);
    }

    private static long deriveSeed(final UUID id) {
        long value = id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 21);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        explosiveId = input.read("ExplosiveId", UUIDUtil.CODEC).orElseGet(UUID::randomUUID);
        ownerPlayerId = input.read("OwnerPlayerId", UUIDUtil.CODEC).orElse(null);
        WarheadYield loadedYield = WarheadYield.fromSerializedName(
            input.getStringOr("Yield", "conventional")).orElse(WarheadYield.CONVENTIONAL);
        getEntityData().set(YIELD, loadedYield.ordinal());
        getEntityData().set(CLUSTER, input.getBooleanOr("Cluster", false));
        getEntityData().set(FUSE, Math.max(1, input.getIntOr("Fuse", 1)));
        getEntityData().set(VISUAL_SEED, input.getLongOr("VisualSeed", deriveSeed(explosiveId)));
        detonated = input.getBooleanOr("Detonated", false);
        if (detonated) discard();
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        output.store("ExplosiveId", UUIDUtil.CODEC, explosiveId);
        output.storeNullable("OwnerPlayerId", UUIDUtil.CODEC, ownerPlayerId);
        output.putString("Yield", this.yield().getSerializedName());
        output.putBoolean("Cluster", cluster());
        output.putInt("Fuse", fuseTicks());
        output.putLong("VisualSeed", visualSeed());
        output.putBoolean("Detonated", detonated);
    }

    @Override public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith(final Entity entity) { return false; }
}
