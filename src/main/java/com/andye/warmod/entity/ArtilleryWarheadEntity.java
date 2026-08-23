package com.andye.warmod.entity;

import com.andye.warmod.artillery.ArtilleryConstants;
import com.andye.warmod.artillery.ArtilleryTrajectory;
import com.andye.warmod.icbm.IcbmChunkTicketRegistry;
import com.andye.warmod.icbm.IcbmConstants;
import com.andye.warmod.warhead.WarheadImpactChunkLeaseManager;
import com.andye.warmod.warhead.WarheadImpactService;
import com.andye.warmod.warhead.WarheadGlassShockwaveManager;
import com.andye.warmod.warhead.WarheadPreImpactPreparationManager;
import com.andye.warmod.warhead.WarheadYield;
import com.andye.warmod.warhead.WarheadYieldRegistry;
import java.util.HashSet;
import java.util.SplittableRandom;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.core.particles.ParticleTypes;
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
    private static final EntityDataAccessor<Boolean> DATA_CLUSTER_CARRIER = SynchedEntityData.defineId(ArtilleryWarheadEntity.class, EntityDataSerializers.BOOLEAN);
    private UUID id = UUID.randomUUID();
    private UUID ownerId;
    private Vec3 target = Vec3.ZERO;
    private Vec3 velocity = Vec3.ZERO;
    private WarheadYield yield = WarheadYield.CONVENTIONAL;
    private long seed;
    private final Set<ChunkPos> heldChunks = new HashSet<>();
    private boolean impacted;
    private boolean cleaned;
    private boolean terrainPreparationScheduled;
    private int chunkWaitTicks;
    private boolean clusterCarrier;
    private boolean split;
    private int clientLastAuthoritativeTick = -1;
    private int clientStaleTicks;
    private Vec3 clientVisualOffset = Vec3.ZERO;
    private Vec3 clientVisualVelocity = Vec3.ZERO;

    public ArtilleryWarheadEntity(final EntityType<? extends ArtilleryWarheadEntity> type, final Level level) { super(type, level); noPhysics = true; setNoGravity(true); }
    public ArtilleryWarheadEntity(final ServerLevel level, final UUID id, final UUID ownerId, final Vec3 origin, final Vec3 target, final Vec3 velocity, final WarheadYield yield, final long seed, final boolean clusterCarrier) {
        this(ModEntityTypes.ARTILLERY_WARHEAD, level);
        this.id = id; this.ownerId = ownerId; this.target = target; this.velocity = velocity; this.yield = yield; this.seed = seed; this.clusterCarrier = clusterCarrier;
        entityData.set(DATA_VISUAL_SEED, seed);
        entityData.set(DATA_FLIGHT_TICKS, ArtilleryTrajectory.flightTicks(origin, target, velocity));
        entityData.set(DATA_CLUSTER_CARRIER, clusterCarrier);
        setPos(origin); setDeltaMovement(velocity);
    }
    @Override protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        builder.define(DATA_VISUAL_SEED, 0L);
        builder.define(DATA_FLIGHT_TICKS, 1);
        builder.define(DATA_ACTIVE_TICKS, 0);
        builder.define(DATA_CLUSTER_CARRIER, false);
    }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide()) {
            tickClientPrediction();
            return;
        }
        if (impacted || !(level() instanceof ServerLevel server)) return;
        if (!position().isFinite() || !target.isFinite() || !velocity.isFinite() || tickCount > 1_200) { discard(); return; }
        if (!terrainPreparationScheduled && yield.nuclear()) {
            WarheadPreImpactPreparationManager.scheduleKnownNuclearTerrain(
                server, id, target, yield, seed,
                Math.max(1, entityData.get(DATA_FLIGHT_TICKS)
                    + IcbmConstants.IMPACT_CHUNK_TAIL_TICKS + 120)
            );
            terrainPreparationScheduled = true;
        }
        Set<ChunkPos> desired = desiredChunks();
        Vec3 from = position();
        Vec3 next = from.add(velocity);
        if (!prepareChunks(server, desired, from, next)) { chunkWaitTicks++; if (chunkWaitTicks >= ArtilleryConstants.CHUNK_WAIT_TIMEOUT_TICKS) discard(); return; }
        chunkWaitTicks = 0;
        setDeltaMovement(velocity);
        int remainingTicks = entityData.get(DATA_FLIGHT_TICKS) - entityData.get(DATA_ACTIVE_TICKS);
        if (clusterCarrier && !split && velocity.y < 0.0
            && remainingTicks <= Math.max(24, Math.min(52, entityData.get(DATA_FLIGHT_TICKS) / 6))) {
            splitCluster(server, Math.max(8, remainingTicks));
            return;
        }
        /* Ignore the cannon and its muzzle-adjacent blocks while the shell arms;
         * sibling submunitions are never valid projectile collision targets. */
        if (tickCount > 5) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this,
                entity -> entity.isAlive() && !(entity instanceof ArtilleryWarheadEntity)
                    && (ownerId == null || !ownerId.equals(entity.getUUID())));
            if (hit.getType() != HitResult.Type.MISS) { impact(server, hit.getLocation()); return; }
        }
        if (!next.isFinite()) { discard(); return; }
        setPos(next);
        velocity = velocity.add(0.0, -ArtilleryConstants.GRAVITY_PER_TICK, 0.0);
        setDeltaMovement(velocity);
        int activeTicks = entityData.get(DATA_ACTIVE_TICKS) + 1;
        entityData.set(DATA_ACTIVE_TICKS, activeTicks);
        updateRotation(velocity);
        // The solver chooses a fractional final tick.  Impact at the commanded coordinates on
        // the first complete flight tick rather than exploding several blocks short at speed.
        if (activeTicks >= entityData.get(DATA_FLIGHT_TICKS)) impact(server, target);
    }

    /**
     * The integrated server can be waiting on a streamed chunk while the render thread is still
     * healthy. Extrapolate only during that authoritative gap, then snap the render-only offset
     * away as soon as the next server flight tick arrives. Collision and impact remain server-only.
     */
    private void tickClientPrediction() {
        int authoritativeTick = entityData.get(DATA_ACTIVE_TICKS);
        if (authoritativeTick != clientLastAuthoritativeTick) {
            clientLastAuthoritativeTick = authoritativeTick;
            clientStaleTicks = 0;
            clientVisualOffset = Vec3.ZERO;
            clientVisualVelocity = getDeltaMovement();
            return;
        }
        if (authoritativeTick <= 0
            || authoritativeTick + clientStaleTicks >= entityData.get(DATA_FLIGHT_TICKS) + 8
            || clientStaleTicks >= 80) return;
        clientStaleTicks++;
        clientVisualOffset = clientVisualOffset.add(clientVisualVelocity);
        clientVisualVelocity = clientVisualVelocity.add(0.0,
            -ArtilleryConstants.GRAVITY_PER_TICK, 0.0);
    }

    private void splitCluster(final ServerLevel level, final int remainingTicks) {
        if (split) return;
        split = true;
        SplittableRandom random = new SplittableRandom(seed ^ id.getMostSignificantBits()
            ^ level.getGameTime());
        Set<ArtilleryWarheadEntity> children = new HashSet<>();
        for (int index = 0; index < 7; index++) {
            double radius = index == 0 ? 0.0 : 10.0 + random.nextDouble() * 13.0;
            double angle = index == 0 ? 0.0
                : Mth.TWO_PI * (index - 1) / 6.0 + random.nextDouble(-0.16, 0.16);
            Vec3 childTarget = target.add(Math.cos(angle) * radius, 0.0,
                Math.sin(angle) * radius);
            Vec3 delta = childTarget.subtract(position());
            double ticks = remainingTicks;
            Vec3 childVelocity = new Vec3(delta.x / ticks,
                (delta.y + ArtilleryConstants.GRAVITY_PER_TICK * ticks * (ticks - 1) * 0.5)
                    / ticks,
                delta.z / ticks);
            UUID childId = UUID.randomUUID();
            ArtilleryWarheadEntity child = new ArtilleryWarheadEntity(level, childId, ownerId,
                position(), childTarget, childVelocity, yield, random.nextLong(), false);
            children.add(child);
        }
        for (ArtilleryWarheadEntity child : children) {
            if (!level.addFreshEntity(child)) {
                children.forEach(ArtilleryWarheadEntity::discard);
                split = false;
                return;
            }
        }
        // Each child owns a bounded rolling corridor. Sharing the strategic ICBM approach lease
        // here multiplied a 500-block ticket corridor seven times at the split point.
        level.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 24,
            0.55, 0.35, 0.55, 0.025);
        level.sendParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 34,
            0.70, 0.45, 0.70, 0.045);
        WarheadGlassShockwaveManager.cancelNuclearPreparation(level, id);
        WarheadImpactChunkLeaseManager.release(level, id);
        discard();
    }
    private Set<ChunkPos> desiredChunks() {
        Set<ChunkPos> desired = new HashSet<>();
        Vec3 ahead = position().add(velocity.scale(ArtilleryConstants.STREAM_LOOKAHEAD_TICKS));
        IcbmChunkTicketRegistry.addSegmentWindow(desired, position(), ahead, 1, 16.0);
        int impactLeadTicks = yield.nuclear()
            ? Math.max(ArtilleryConstants.TARGET_LEAD_TICKS, 96)
            : ArtilleryConstants.TARGET_LEAD_TICKS;
        if (position().distanceTo(target) <= ArtilleryConstants.MAX_MUZZLE_SPEED * impactLeadTicks) {
            int impactRadius = yield.nuclear() ? IcbmConstants.IMPACT_CHUNK_RADIUS : 1;
            IcbmChunkTicketRegistry.addWindow(
                desired, IcbmChunkTicketRegistry.chunk(target), impactRadius);
        }
        return desired;
    }
    private boolean prepareChunks(final ServerLevel level, final Set<ChunkPos> desired,
        final Vec3 from, final Vec3 next) {
        for (ChunkPos chunk : Set.copyOf(heldChunks)) if (!desired.contains(chunk)) {
            IcbmChunkTicketRegistry.release(level, chunk);
            heldChunks.remove(chunk);
        }
        // Stream the twelve-tick corridor in bounded slices, but always acquire the shell's exact
        // current movement first. This avoids a one-tick burst of dozens of chunk generations.
        Set<ChunkPos> immediate = new HashSet<>();
        IcbmChunkTicketRegistry.addSegmentWindow(immediate, from, next, 0, 4.0);
        for (ChunkPos chunk : immediate) acquireChunk(level, chunk);
        int acquired = 0;
        for (ChunkPos chunk : desired) {
            if (heldChunks.contains(chunk)) continue;
            acquireChunk(level, chunk);
            if (++acquired >= 8) break;
        }
        // Stream the future corridor in the background, but only stall server physics when this exact
        // movement crosses an unloaded chunk.  Waiting for the whole corridor made a newly
        // fired shell visibly freeze above the muzzle while its future chunks loaded.
        return IcbmChunkTicketRegistry.allLoaded(level, immediate);
    }
    private void acquireChunk(final ServerLevel level, final ChunkPos chunk) {
        if (heldChunks.add(chunk)) IcbmChunkTicketRegistry.acquire(level, chunk);
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
    public int activeTicks() {
        return entityData.get(DATA_ACTIVE_TICKS)
            + (level().isClientSide() ? clientStaleTicks : 0);
    }
    public Vec3 clientVisualOffset(final float partialTick) {
        if (!level().isClientSide() || clientStaleTicks <= 0) return Vec3.ZERO;
        return clientVisualOffset.add(clientVisualVelocity.scale(partialTick));
    }
    public boolean clusterCarrier() { return entityData.get(DATA_CLUSTER_CARRIER); }
    private void updateRotation(final Vec3 value) { if (value.lengthSqr() < 1.0E-8) return; setYRot((float)(Math.atan2(value.z, value.x) * 180.0 / Math.PI) - 90.0F); setXRot((float)(-Math.atan2(value.y, value.horizontalDistance()) * 180.0 / Math.PI)); }
    @Override protected void addAdditionalSaveData(final ValueOutput out) { out.store("id", UUIDUtil.CODEC, id); out.storeNullable("owner", UUIDUtil.CODEC, ownerId); out.store("target", Vec3.CODEC, target); out.store("velocity", Vec3.CODEC, velocity); out.putString("yield", yield.getSerializedName()); out.putLong("seed", seed); out.putInt("flightTicks", entityData.get(DATA_FLIGHT_TICKS)); out.putInt("activeTicks", entityData.get(DATA_ACTIVE_TICKS)); out.putBoolean("impacted", impacted); out.putBoolean("clusterCarrier", clusterCarrier); out.putBoolean("split", split); }
    @Override protected void readAdditionalSaveData(final ValueInput in) { id = in.read("id", UUIDUtil.CODEC).orElseGet(UUID::randomUUID); ownerId = in.read("owner", UUIDUtil.CODEC).orElse(null); target = in.read("target", Vec3.CODEC).orElse(Vec3.ZERO); velocity = in.read("velocity", Vec3.CODEC).orElse(Vec3.ZERO); yield = WarheadYield.fromSerializedName(in.getStringOr("yield", "conventional")).orElse(WarheadYield.CONVENTIONAL); seed = in.getLongOr("seed", 0L); impacted = in.getBooleanOr("impacted", false); clusterCarrier = in.getBooleanOr("clusterCarrier", false); split = in.getBooleanOr("split", false); entityData.set(DATA_VISUAL_SEED, seed); entityData.set(DATA_FLIGHT_TICKS, Math.max(1, in.getIntOr("flightTicks", ArtilleryTrajectory.flightTicks(position(), target, velocity)))); entityData.set(DATA_ACTIVE_TICKS, Math.max(0, in.getIntOr("activeTicks", 0))); entityData.set(DATA_CLUSTER_CARRIER, clusterCarrier); setDeltaMovement(velocity); }
    @Override public void remove(final RemovalReason reason) { if (level() instanceof ServerLevel server && !cleaned) { for (ChunkPos chunk : Set.copyOf(heldChunks)) IcbmChunkTicketRegistry.release(server, chunk); heldChunks.clear(); cleaned = true; } super.remove(reason); }
    @Override public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith(final Entity entity) { return false; }
    @Override public boolean shouldRenderAtSqrDistance(final double distance) {
        return distance <= 3072.0 * 3072.0;
    }
}
