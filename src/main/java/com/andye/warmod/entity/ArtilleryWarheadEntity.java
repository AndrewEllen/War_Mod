package com.andye.warmod.entity;

import com.andye.warmod.artillery.ArtilleryConstants;
import com.andye.warmod.artillery.ArtilleryTrajectory;
import com.andye.warmod.icbm.IcbmChunkTicketRegistry;
import com.andye.warmod.icbm.IcbmConstants;
import com.andye.warmod.warhead.WarheadImpactChunkLeaseManager;
import com.andye.warmod.warhead.WarheadImpactService;
import com.andye.warmod.warhead.WarheadYield;
import com.andye.warmod.warhead.WarheadYieldRegistry;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.SectionPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Server-authoritative gravity projectile with the ICBM rolling ticket discipline. */
public final class ArtilleryWarheadEntity extends Entity {
    private static final EntityDataAccessor<Long> DATA_VISUAL_SEED = SynchedEntityData.defineId(ArtilleryWarheadEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_FLIGHT_TICKS = SynchedEntityData.defineId(ArtilleryWarheadEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTIVE_TICKS = SynchedEntityData.defineId(ArtilleryWarheadEntity.class, EntityDataSerializers.INT);
    private UUID id = UUID.randomUUID();
    private UUID ownerId;
    private Vec3 target = Vec3.ZERO;
    private Vec3 velocity = Vec3.ZERO;
    private WarheadYield yield = WarheadYield.CONVENTIONAL;
    private long seed;
    private final Set<ChunkPos> heldChunks = new HashSet<>();
    private boolean impacted;
    private boolean cleaned;
    private int chunkWaitTicks;

    public ArtilleryWarheadEntity(final EntityType<? extends ArtilleryWarheadEntity> type, final Level level) { super(type, level); noPhysics = true; setNoGravity(true); }
    public ArtilleryWarheadEntity(final ServerLevel level, final UUID id, final UUID ownerId, final Vec3 origin, final Vec3 target, final Vec3 velocity, final WarheadYield yield, final long seed) {
        this(ModEntityTypes.ARTILLERY_WARHEAD, level);
        this.id = id; this.ownerId = ownerId; this.target = target; this.velocity = velocity; this.yield = yield; this.seed = seed;
        entityData.set(DATA_VISUAL_SEED, seed);
        entityData.set(DATA_FLIGHT_TICKS, ArtilleryTrajectory.flightTicks(origin, target, velocity));
        setPos(origin); setDeltaMovement(velocity);
    }
    @Override protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        builder.define(DATA_VISUAL_SEED, 0L);
        builder.define(DATA_FLIGHT_TICKS, 1);
        builder.define(DATA_ACTIVE_TICKS, 0);
    }
    @Override public void tick() {
        super.tick();
        if (impacted || !(level() instanceof ServerLevel server)) return;
        if (!position().isFinite() || !target.isFinite() || !velocity.isFinite() || tickCount > 1_200) { discard(); return; }
        Set<ChunkPos> desired = desiredChunks();
        if (!prepareChunks(server, desired)) { chunkWaitTicks++; if (chunkWaitTicks >= ArtilleryConstants.CHUNK_WAIT_TIMEOUT_TICKS) discard(); return; }
        chunkWaitTicks = 0;
        Vec3 from = position();
        setDeltaMovement(velocity);
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, entity -> entity.isAlive() && (ownerId == null || tickCount > 4 || !ownerId.equals(entity.getUUID())));
        if (hit.getType() != HitResult.Type.MISS) { impact(server, hit.getLocation()); return; }
        Vec3 next = from.add(velocity);
        if (!next.isFinite() || !server.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(next.x), SectionPos.blockToSectionCoord(next.z))) return;
        setPos(next);
        velocity = velocity.add(0.0, -ArtilleryConstants.GRAVITY_PER_TICK, 0.0);
        setDeltaMovement(velocity);
        entityData.set(DATA_ACTIVE_TICKS, entityData.get(DATA_ACTIVE_TICKS) + 1);
        updateRotation(velocity);
        if (next.distanceToSqr(target) <= Math.max(2.25, velocity.lengthSqr())) impact(server, target);
    }
    private Set<ChunkPos> desiredChunks() {
        Set<ChunkPos> desired = new HashSet<>();
        Vec3 ahead = position().add(velocity.scale(ArtilleryConstants.STREAM_LOOKAHEAD_TICKS));
        IcbmChunkTicketRegistry.addSegmentWindow(desired, position(), ahead, 1, 16.0);
        if (position().distanceTo(target) <= ArtilleryConstants.MAX_MUZZLE_SPEED * ArtilleryConstants.TARGET_LEAD_TICKS) IcbmChunkTicketRegistry.addWindow(desired, IcbmChunkTicketRegistry.chunk(target), IcbmConstants.IMPACT_CHUNK_RADIUS);
        return desired;
    }
    private boolean prepareChunks(final ServerLevel level, final Set<ChunkPos> desired) {
        for (ChunkPos chunk : desired) if (heldChunks.add(chunk)) IcbmChunkTicketRegistry.acquire(level, chunk);
        if (!IcbmChunkTicketRegistry.allLoaded(level, desired)) return false;
        for (ChunkPos chunk : Set.copyOf(heldChunks)) if (!desired.contains(chunk)) { IcbmChunkTicketRegistry.release(level, chunk); heldChunks.remove(chunk); }
        return true;
    }
    private void impact(final ServerLevel level, final Vec3 position) {
        if (impacted || !position.isFinite()) return;
        impacted = true;
        ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
        if (owner != null && owner.level() != level) owner = null;
        WarheadImpactChunkLeaseManager.hold(level, id, position, IcbmConstants.IMPACT_CHUNK_TAIL_TICKS);
        WarheadYieldRegistry.put(level, id, yield);
        WarheadImpactService.impact(level, owner, id, id, position, seed, yield.payloadType());
        discard();
    }
    public Vec3 target() { return target; }
    public long visualSeed() { return level().isClientSide() ? entityData.get(DATA_VISUAL_SEED) : seed; }
    public int flightTicks() { return entityData.get(DATA_FLIGHT_TICKS); }
    public int activeTicks() { return entityData.get(DATA_ACTIVE_TICKS); }
    private void updateRotation(final Vec3 value) { if (value.lengthSqr() < 1.0E-8) return; setYRot((float)(Math.atan2(value.z, value.x) * 180.0 / Math.PI) - 90.0F); setXRot((float)(-Math.atan2(value.y, value.horizontalDistance()) * 180.0 / Math.PI)); }
    @Override protected void addAdditionalSaveData(final ValueOutput out) { out.store("id", UUIDUtil.CODEC, id); out.storeNullable("owner", UUIDUtil.CODEC, ownerId); out.store("target", Vec3.CODEC, target); out.store("velocity", Vec3.CODEC, velocity); out.putString("yield", yield.getSerializedName()); out.putLong("seed", seed); out.putInt("flightTicks", entityData.get(DATA_FLIGHT_TICKS)); out.putInt("activeTicks", entityData.get(DATA_ACTIVE_TICKS)); out.putBoolean("impacted", impacted); }
    @Override protected void readAdditionalSaveData(final ValueInput in) { id = in.read("id", UUIDUtil.CODEC).orElseGet(UUID::randomUUID); ownerId = in.read("owner", UUIDUtil.CODEC).orElse(null); target = in.read("target", Vec3.CODEC).orElse(Vec3.ZERO); velocity = in.read("velocity", Vec3.CODEC).orElse(Vec3.ZERO); yield = WarheadYield.fromSerializedName(in.getStringOr("yield", "conventional")).orElse(WarheadYield.CONVENTIONAL); seed = in.getLongOr("seed", 0L); impacted = in.getBooleanOr("impacted", false); entityData.set(DATA_VISUAL_SEED, seed); entityData.set(DATA_FLIGHT_TICKS, Math.max(1, in.getIntOr("flightTicks", ArtilleryTrajectory.flightTicks(position(), target, velocity)))); entityData.set(DATA_ACTIVE_TICKS, Math.max(0, in.getIntOr("activeTicks", 0))); setDeltaMovement(velocity); }
    @Override public void remove(final RemovalReason reason) { if (level() instanceof ServerLevel server && !cleaned) { for (ChunkPos chunk : Set.copyOf(heldChunks)) IcbmChunkTicketRegistry.release(server, chunk); heldChunks.clear(); cleaned = true; } super.remove(reason); }
    @Override public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith(final Entity entity) { return false; }
    @Override public boolean shouldRenderAtSqrDistance(final double distance) { return distance <= 1536.0 * 1536.0; }
}
