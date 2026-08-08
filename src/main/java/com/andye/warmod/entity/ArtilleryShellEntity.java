package com.andye.warmod.entity;

import com.andye.warmod.artillery.ArtilleryBallistics;
import com.andye.warmod.artillery.ArtilleryConstants;
import com.andye.warmod.icbm.IcbmChunkTicketRegistry;
import com.andye.warmod.icbm.IcbmConstants;
import com.andye.warmod.warhead.WarheadImpactChunkLeaseManager;
import com.andye.warmod.warhead.WarheadImpactService;
import com.andye.warmod.warhead.WarheadYield;
import com.andye.warmod.warhead.WarheadYieldRegistry;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.SectionPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A physical artillery-delivered warhead. Its rolling chunk window mirrors the
 * terminal ICBM warhead strategy: simulation pauses until the current/lookahead
 * corridor is loaded, and impact chunks are held through the explosion tail.
 */
public final class ArtilleryShellEntity extends Entity {
    private static final EntityDataAccessor<Integer> YIELD =
        SynchedEntityData.defineId(ArtilleryShellEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> CLUSTER =
        SynchedEntityData.defineId(ArtilleryShellEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Long> VISUAL_SEED =
        SynchedEntityData.defineId(ArtilleryShellEntity.class, EntityDataSerializers.LONG);

    private UUID shellId;
    private @Nullable UUID ownerPlayerId;
    private Vec3 startPosition = Vec3.ZERO;
    private Vec3 intendedTarget = Vec3.ZERO;
    private Vec3 initialVelocity = Vec3.ZERO;
    private long launchGameTime = Long.MIN_VALUE;
    private int flightTicks;
    private int pausedSimulationTicks;
    private int consecutiveChunkWaitTicks;
    private boolean impacted;
    private boolean ticketCleanupComplete;
    private final Set<ChunkPos> heldTicketChunks = new HashSet<>();

    public ArtilleryShellEntity(final EntityType<? extends ArtilleryShellEntity> type,
        final Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public ArtilleryShellEntity(final ServerLevel level, final UUID shellId,
        final @Nullable UUID ownerPlayerId, final Vec3 startPosition,
        final Vec3 intendedTarget, final Vec3 initialVelocity, final int flightTicks,
        final long visualSeed, final WarheadYield yield, final boolean cluster) {
        this(ModEntityTypes.ARTILLERY_SHELL, level);
        this.shellId = shellId;
        this.ownerPlayerId = ownerPlayerId;
        this.startPosition = startPosition;
        this.intendedTarget = intendedTarget;
        this.initialVelocity = initialVelocity;
        this.flightTicks = flightTicks;
        this.launchGameTime = level.getGameTime();
        this.getEntityData().set(YIELD, yield.ordinal());
        this.getEntityData().set(CLUSTER, cluster);
        this.getEntityData().set(VISUAL_SEED, visualSeed);
        setPos(startPosition);
        setDeltaMovement(initialVelocity);
        updateRotation(initialVelocity);
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        builder.define(YIELD, WarheadYield.CONVENTIONAL.ordinal());
        builder.define(CLUSTER, false);
        builder.define(VISUAL_SEED, 0L);
    }

    public UUID shellId() { return shellId; }
    public WarheadYield yield() {
        int index = getEntityData().get(YIELD);
        WarheadYield[] values = WarheadYield.values();
        return values[Math.max(0, Math.min(values.length - 1, index))];
    }
    public boolean cluster() { return getEntityData().get(CLUSTER); }
    public long visualSeed() { return getEntityData().get(VISUAL_SEED); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() || impacted || isRemoved()) return;
        if (!(level() instanceof ServerLevel server) || !valid()) {
            if (level() instanceof ServerLevel server) releaseStreamingTickets(server);
            discard();
            return;
        }

        double elapsed = effectiveElapsed(server);
        double previousTime = Math.max(0.0, elapsed - 1.0);
        Vec3 previous = ArtilleryBallistics.position(startPosition, initialVelocity, previousTime);
        Vec3 next = ArtilleryBallistics.position(startPosition, initialVelocity,
            Math.min(elapsed, flightTicks));
        if (!previous.isFinite() || !next.isFinite()) {
            releaseStreamingTickets(server);
            discard();
            return;
        }

        Set<ChunkPos> desired = desiredStreamingWindow(elapsed);
        if (!prepareStreamingWindow(server, desired)) {
            pauseForChunkLoading(server, previous);
            return;
        }
        consecutiveChunkWaitTicks = 0;

        RaycastResult raycast = raycastLoaded(server, previous, next);
        if (raycast.missingChunk()) {
            pauseForChunkLoading(server, previous);
            return;
        }
        if (raycast.hit().isPresent()) {
            impact(server, raycast.hit().get().getLocation());
            return;
        }
        if (elapsed >= flightTicks) {
            impact(server, intendedTarget);
            return;
        }

        Vec3 velocity = ArtilleryBallistics.velocity(initialVelocity, elapsed);
        setPos(next);
        setDeltaMovement(velocity);
        updateRotation(velocity);
    }

    private double effectiveElapsed(final ServerLevel level) {
        return Math.max(0.0, level.getGameTime() - launchGameTime - pausedSimulationTicks);
    }

    private Set<ChunkPos> desiredStreamingWindow(final double elapsed) {
        HashSet<ChunkPos> desired = new HashSet<>();
        double future = Math.min(flightTicks,
            elapsed + IcbmConstants.TERMINAL_STREAM_LOOKAHEAD_TICKS);
        Vec3 current = ArtilleryBallistics.position(startPosition, initialVelocity,
            Math.min(elapsed, flightTicks));
        Vec3 lookahead = ArtilleryBallistics.position(startPosition, initialVelocity, future);
        IcbmChunkTicketRegistry.addSegmentWindow(desired, current, lookahead,
            IcbmConstants.TERMINAL_STREAM_RADIUS,
            IcbmConstants.TERMINAL_STREAM_SAMPLE_SPACING_BLOCKS);
        if (flightTicks - elapsed <= IcbmConstants.TERMINAL_TARGET_LEAD_TICKS) {
            IcbmChunkTicketRegistry.addWindow(desired,
                IcbmChunkTicketRegistry.chunk(intendedTarget),
                IcbmConstants.IMPACT_CHUNK_RADIUS);
        }
        return Set.copyOf(desired);
    }

    private boolean prepareStreamingWindow(final ServerLevel level,
        final Set<ChunkPos> desired) {
        for (ChunkPos chunk : desired) {
            if (heldTicketChunks.add(chunk)) IcbmChunkTicketRegistry.acquire(level, chunk);
        }
        if (!IcbmChunkTicketRegistry.allLoaded(level, desired)) return false;
        for (ChunkPos chunk : Set.copyOf(heldTicketChunks)) {
            if (!desired.contains(chunk)) {
                IcbmChunkTicketRegistry.release(level, chunk);
                heldTicketChunks.remove(chunk);
            }
        }
        return true;
    }

    private void pauseForChunkLoading(final ServerLevel level, final Vec3 safePosition) {
        pausedSimulationTicks++;
        consecutiveChunkWaitTicks++;
        setPos(safePosition);
        setDeltaMovement(Vec3.ZERO);
        if (consecutiveChunkWaitTicks >= IcbmConstants.TERMINAL_CHUNK_WAIT_TIMEOUT_TICKS) {
            releaseStreamingTickets(level);
            WarheadImpactChunkLeaseManager.release(level, shellId);
            discard();
        }
    }

    private RaycastResult raycastLoaded(final ServerLevel level, final Vec3 from,
        final Vec3 to) {
        AtomicBoolean missing = new AtomicBoolean();
        ClipContext context = new ClipContext(from, to, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, this);
        Optional<BlockHitResult> hit = BlockGetter.traverseBlocks(from, to, context,
            (clipContext, position) -> {
                if (!level.getChunkSource().hasChunk(
                    SectionPos.blockToSectionCoord(position.getX()),
                    SectionPos.blockToSectionCoord(position.getZ()))) {
                    missing.set(true);
                    return Optional.empty();
                }
                BlockState state = level.getBlockState(position);
                VoxelShape shape = clipContext.getBlockShape(state, level, position);
                BlockHitResult result = level.clipWithInteractionOverride(from, to,
                    position, shape, state);
                return result == null ? null : Optional.of(result);
            }, ignored -> Optional.empty());
        return new RaycastResult(hit == null ? Optional.empty() : hit, missing.get());
    }

    private void impact(final ServerLevel server, final Vec3 hit) {
        if (impacted || hit == null || !hit.isFinite()) return;
        impacted = true;
        ServerPlayer owner = null;
        if (ownerPlayerId != null && server.getServer() != null) {
            ServerPlayer candidate = server.getServer().getPlayerList().getPlayer(ownerPlayerId);
            if (candidate != null && candidate.level() == server) owner = candidate;
        }

        WarheadImpactChunkLeaseManager.hold(server, shellId, hit,
            IcbmConstants.IMPACT_CHUNK_TAIL_TICKS);
        if (!cluster()) {
            WarheadYieldRegistry.put(server, shellId, yield());
            WarheadImpactService.impact(server, owner, shellId, shellId, hit,
                visualSeed(), yield().payloadType());
        } else {
            double rotation = ((visualSeed() >>> 12) & 65535L) / 65535.0 * Math.PI * 2.0;
            for (int index = 0; index < ArtilleryConstants.CLUSTER_CHILDREN; index++) {
                double angle = rotation + index * Math.PI * 2.0 / ArtilleryConstants.CLUSTER_CHILDREN;
                Vec3 childPosition = hit.add(
                    Math.cos(angle) * ArtilleryConstants.CLUSTER_SPREAD_RADIUS_BLOCKS,
                    0.0,
                    Math.sin(angle) * ArtilleryConstants.CLUSTER_SPREAD_RADIUS_BLOCKS);
                UUID childId = UUID.randomUUID();
                WarheadYieldRegistry.put(server, childId, yield());
                WarheadImpactService.detonateAt(server, owner, childId, shellId, childPosition,
                    visualSeed() + index * 0x9E3779B97F4A7C15L,
                    yield().payloadType(), false);
            }
        }
        releaseStreamingTickets(server);
        discard();
    }

    private boolean valid() {
        return shellId != null && startPosition != null && intendedTarget != null
            && initialVelocity != null && startPosition.isFinite() && intendedTarget.isFinite()
            && initialVelocity.isFinite() && launchGameTime != Long.MIN_VALUE
            && flightTicks > 0 && flightTicks <= ArtilleryConstants.MAXIMUM_FLIGHT_TICKS;
    }

    private void updateRotation(final Vec3 velocity) {
        if (velocity.lengthSqr() < 1.0E-8) return;
        setYRot((float)(Math.atan2(velocity.z, velocity.x) * 180.0 / Math.PI) - 90.0F);
        setXRot((float)(-Math.atan2(velocity.y, velocity.horizontalDistance())
            * 180.0 / Math.PI));
    }

    private void releaseStreamingTickets(final ServerLevel level) {
        if (ticketCleanupComplete) return;
        for (ChunkPos chunk : Set.copyOf(heldTicketChunks)) {
            IcbmChunkTicketRegistry.release(level, chunk);
        }
        heldTicketChunks.clear();
        ticketCleanupComplete = true;
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        shellId = input.read("ShellId", UUIDUtil.CODEC).orElse(null);
        ownerPlayerId = input.read("OwnerPlayerId", UUIDUtil.CODEC).orElse(null);
        startPosition = input.read("StartPosition", Vec3.CODEC).orElse(Vec3.ZERO);
        intendedTarget = input.read("IntendedTarget", Vec3.CODEC).orElse(Vec3.ZERO);
        initialVelocity = input.read("InitialVelocity", Vec3.CODEC).orElse(Vec3.ZERO);
        launchGameTime = input.getLongOr("LaunchGameTime", Long.MIN_VALUE);
        flightTicks = input.getIntOr("FlightTicks", 0);
        pausedSimulationTicks = Math.max(0, input.getIntOr("PausedSimulationTicks", 0));
        impacted = input.getBooleanOr("Impacted", false);
        WarheadYield loadedYield = WarheadYield.fromSerializedName(
            input.getStringOr("Yield", "conventional")).orElse(WarheadYield.CONVENTIONAL);
        getEntityData().set(YIELD, loadedYield.ordinal());
        getEntityData().set(CLUSTER, input.getBooleanOr("Cluster", false));
        getEntityData().set(VISUAL_SEED, input.getLongOr("VisualSeed", 0L));
        if (!valid() || impacted) discard();
        else {
            setPos(startPosition);
            setNoGravity(true);
            noPhysics = true;
        }
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        output.storeNullable("ShellId", UUIDUtil.CODEC, shellId);
        output.storeNullable("OwnerPlayerId", UUIDUtil.CODEC, ownerPlayerId);
        if (startPosition != null && startPosition.isFinite()) output.store("StartPosition", Vec3.CODEC, startPosition);
        if (intendedTarget != null && intendedTarget.isFinite()) output.store("IntendedTarget", Vec3.CODEC, intendedTarget);
        if (initialVelocity != null && initialVelocity.isFinite()) output.store("InitialVelocity", Vec3.CODEC, initialVelocity);
        output.putLong("LaunchGameTime", launchGameTime);
        output.putInt("FlightTicks", flightTicks);
        output.putInt("PausedSimulationTicks", pausedSimulationTicks);
        output.putBoolean("Impacted", impacted);
        output.putString("Yield", yield().getSerializedName());
        output.putBoolean("Cluster", cluster());
        output.putLong("VisualSeed", visualSeed());
    }

    @Override public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith(final Entity entity) { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(final double distance) {
        return distance <= ArtilleryConstants.MAXIMUM_RANGE_BLOCKS * ArtilleryConstants.MAXIMUM_RANGE_BLOCKS * 2.5;
    }

    @Override
    public void remove(final RemovalReason reason) {
        if (level() instanceof ServerLevel server) releaseStreamingTickets(server);
        super.remove(reason);
    }

    private record RaycastResult(Optional<BlockHitResult> hit, boolean missingChunk) {
    }
}
