package com.andye.warmod.entity;

import com.andye.warmod.WarMod;
import com.andye.warmod.acoustics.AcousticEngine;
import com.andye.warmod.acoustics.AcousticSounds;
import com.andye.warmod.icbm.IcbmChunkTicketRegistry;
import com.andye.warmod.icbm.IcbmConstants;
import com.andye.warmod.radar.RadarRemovalReason;
import com.andye.warmod.radar.RadarTrackingService;
import com.andye.warmod.scheduler.WarModServerWorkScheduler;
import com.andye.warmod.scheduler.WarModServerWorkScheduler.WorkClass;
import com.andye.warmod.scheduler.WarModServerWorkScheduler.WorkPermit;
import com.andye.warmod.warhead.IncomingWarheadRegistry;
import com.andye.warmod.warhead.WarheadConstants;
import com.andye.warmod.warhead.WarheadImpactChunkLeaseManager;
import com.andye.warmod.warhead.WarheadImpactService;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadYield;
import com.andye.warmod.warhead.WarheadYieldRegistry;
import com.andye.warmod.warhead.WarheadExplosionWorkManager;
import com.andye.warmod.warhead.WarheadPreparationCoordinator;
import com.andye.warmod.warhead.CancellationReason;
import com.andye.warmod.warhead.WarheadTrajectory;
import com.andye.warmod.warhead.WarheadVisualMath;
import com.andye.warmod.warhead.network.WarheadVisualNetworking;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
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

public final class IncomingWarheadEntity extends Entity {
    private UUID warheadId;
    private UUID ownerPlayerId;
    private UUID radarRootTrackId;

    private Vec3 startPosition = Vec3.ZERO;
    private Vec3 intendedTarget = Vec3.ZERO;

    private long launchGameTime = Long.MIN_VALUE;
    private long visualSeed;
    private int flightTicks;
    private int clusterIndex;
    private int clusterCount = 1;

    private boolean impacted;
    private boolean cancelled;
    private boolean sonicBoomEmitted;
    private WarheadPayloadType payloadType = WarheadPayloadType.CONVENTIONAL;
    private WarheadYield authoritativeYield;
    private boolean authoritativeCustomFire;

    private final Set<ChunkPos> heldTicketChunks = new HashSet<>();
    private int pausedSimulationTicks;
    private int consecutiveChunkWaitTicks;
    private boolean ticketCleanupComplete;
    private boolean waitingForChunks;

    public IncomingWarheadEntity(
        final EntityType<IncomingWarheadEntity> type,
        final Level level
    ) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setSilent(true);
    }

    public IncomingWarheadEntity(
        final EntityType<IncomingWarheadEntity> type,
        final ServerLevel level,
        final UUID warheadId,
        final UUID ownerPlayerId,
        final Vec3 startPosition,
        final Vec3 intendedTarget,
        final long launchGameTime,
        final int flightTicks,
        final long visualSeed,
        final WarheadPayloadType payloadType,
        final UUID radarRootTrackId,
        final int clusterIndex,
        final int clusterCount
    ) {
        this(type, level);
        this.warheadId = Objects.requireNonNull(warheadId);
        this.ownerPlayerId = ownerPlayerId;
        this.startPosition = Objects.requireNonNull(startPosition);
        this.intendedTarget = Objects.requireNonNull(intendedTarget);
        this.launchGameTime = launchGameTime;
        this.flightTicks = flightTicks;
        this.visualSeed = visualSeed;
        this.payloadType = Objects.requireNonNull(payloadType);
        this.radarRootTrackId = Objects.requireNonNull(radarRootTrackId);
        this.clusterIndex = clusterIndex;
        this.clusterCount = Math.max(1, clusterCount);
        setPos(startPosition);
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();

        if (isRemoved()
            || impacted
            || !(level() instanceof ServerLevel server)) {
            return;
        }

        IncomingWarheadRegistry.register(server, this);
        RadarTrackingService.reconcileWarhead(server, this);

        if (!valid()) {
            cancel(server);
            releaseStreamingTickets(server);
            discard();
            return;
        }

        double elapsed = effectiveElapsed(server);
        maintainTerrainPreparation(server, elapsed);
        Vec3 previous = WarheadTrajectory.position(
            startPosition,
            intendedTarget,
            Math.max(0, elapsed - 1),
            flightTicks,
            clusterIndex,
            clusterCount
        );
        Vec3 next = WarheadTrajectory.position(
            startPosition,
            intendedTarget,
            elapsed,
            flightTicks,
            clusterIndex,
            clusterCount
        );

        if (!next.isFinite() || !previous.isFinite()) {
            cancel(server);
            releaseStreamingTickets(server);
            discard();
            return;
        }

        try (WorkPermit permit = WarModServerWorkScheduler.acquire(server,
            WorkClass.MISSILE_STREAMING, 400_000L)) {
            if (!permit.available()) {
                pauseForScheduler(server, previous);
                return;
            }
            advanceAuthoritativeFlight(server, elapsed, previous, next);
        }
    }

    private void maintainTerrainPreparation(final ServerLevel level,
        final double elapsed) {
        if (payloadType != WarheadPayloadType.NUCLEAR) return;
        WarheadYield yield = persistentYield(level);
        Vec3 effective = WarheadExplosionWorkManager.resolveDetonationCenter(
            level, intendedTarget, yield);
        long remaining = ceilRemainingTicks(flightTicks - elapsed);
        WarheadPreparationCoordinator.ensureImpact(level, warheadId, warheadId,
            radarRootTrackId(), effective, yield, visualSeed,
            authoritativeCustomFire,
            level.getGameTime() + remaining);
    }

    private WarheadYield persistentYield(final ServerLevel level) {
        if (authoritativeYield == null) {
            authoritativeYield = WarheadYieldRegistry.resolve(level, warheadId,
                radarRootTrackId(), payloadType);
            authoritativeCustomFire = WarheadYieldRegistry.usesCustomFire(level,
                warheadId, radarRootTrackId());
        }
        WarheadYieldRegistry.put(level, radarRootTrackId(), authoritativeYield,
            authoritativeCustomFire);
        return authoritativeYield;
    }

    private static long ceilRemainingTicks(final double value) {
        if (!Double.isFinite(value)) return 1L;
        return Math.max(1L, (long)Math.ceil(value));
    }

    private void advanceAuthoritativeFlight(final ServerLevel server,
        final double elapsed, final Vec3 previous, final Vec3 next) {
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

        Vec3 pendingImpact = raycast.hit().map(BlockHitResult::getLocation)
            .orElse(elapsed >= flightTicks ? intendedTarget : null);
        if (waitingForChunks) {
            waitingForChunks = false;
            WarheadVisualNetworking.sendTimingCorrection(server, warheadId,
                pausedSimulationTicks, false, previous);
        }
        if (pendingImpact != null) {
            impact(server, pendingImpact);
        } else {
            Vec3 velocity = WarheadTrajectory.velocity(
                startPosition,
                intendedTarget,
                elapsed,
                flightTicks,
                clusterIndex,
                clusterCount
            );
            setPos(next);
            setDeltaMovement(velocity);
            updateRotation(velocity);
            emitSonicBoom(server, next, velocity);
        }
    }

    private void pauseForScheduler(final ServerLevel level, final Vec3 safePosition) {
        pausedSimulationTicks++;
        setPos(safePosition);
        setDeltaMovement(Vec3.ZERO);
        if (!waitingForChunks) {
            waitingForChunks = true;
            WarheadVisualNetworking.sendTimingCorrection(level, warheadId,
                pausedSimulationTicks, true, safePosition);
        }
    }

    private double effectiveElapsed(final ServerLevel level) {
        return Math.max(
            0.0,
            Math.min(
                Integer.MAX_VALUE,
                level.getGameTime() - launchGameTime - pausedSimulationTicks
            )
        );
    }

    @Override
    public boolean hurtServer(
        final ServerLevel level,
        final DamageSource source,
        final float amount
    ) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(final Entity entity) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(final double distance) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        warheadId = input.read("WarheadId", UUIDUtil.STRING_CODEC)
            .orElse(null);
        ownerPlayerId = input.read("OwnerPlayerId", UUIDUtil.STRING_CODEC)
            .orElse(null);
        radarRootTrackId = input.read("RadarRootTrackId", UUIDUtil.STRING_CODEC)
            .orElse(warheadId);
        startPosition = input.read("StartPosition", Vec3.CODEC)
            .orElse(Vec3.ZERO);
        intendedTarget = input.read("IntendedTarget", Vec3.CODEC)
            .orElse(Vec3.ZERO);
        launchGameTime = input.getLongOr("LaunchGameTime", Long.MIN_VALUE);
        flightTicks = input.getIntOr("FlightTicks", 0);
        visualSeed = input.getLongOr("VisualSeed", 0);
        pausedSimulationTicks = Math.max(
            0,
            input.getIntOr("PausedSimulationTicks", 0)
        );
        impacted = input.getBooleanOr("Impacted", false);
        sonicBoomEmitted = input.getBooleanOr("SonicBoomEmitted", false);
        payloadType = WarheadPayloadType.fromSerializedName(
            input.getStringOr("PayloadType", "conventional")
        ).orElse(WarheadPayloadType.CONVENTIONAL);
        authoritativeYield = WarheadYield.fromSerializedName(
            input.getStringOr("AuthoritativeYield", "")).orElse(null);
        authoritativeCustomFire = input.getBooleanOr("AuthoritativeCustomFire", false);
        clusterIndex = input.getIntOr("ClusterIndex", 0);
        clusterCount = Math.max(1, input.getIntOr("ClusterCount", 1));

        if (!valid() || impacted) {
            discard();
            return;
        }

        setPos(startPosition);
        setNoGravity(true);
        setSilent(true);
        noPhysics = true;
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        output.storeNullable("WarheadId", UUIDUtil.STRING_CODEC, warheadId);
        output.storeNullable(
            "OwnerPlayerId",
            UUIDUtil.STRING_CODEC,
            ownerPlayerId
        );
        output.storeNullable(
            "RadarRootTrackId",
            UUIDUtil.STRING_CODEC,
            radarRootTrackId
        );

        if (startPosition != null && startPosition.isFinite()) {
            output.store("StartPosition", Vec3.CODEC, startPosition);
        }

        if (intendedTarget != null && intendedTarget.isFinite()) {
            output.store("IntendedTarget", Vec3.CODEC, intendedTarget);
        }

        output.putLong("LaunchGameTime", launchGameTime);
        output.putInt("FlightTicks", flightTicks);
        output.putLong("VisualSeed", visualSeed);
        output.putInt("PausedSimulationTicks", pausedSimulationTicks);
        output.putBoolean("Impacted", impacted);
        output.putBoolean("SonicBoomEmitted", sonicBoomEmitted);
        output.putString("PayloadType", payloadType.serializedName());
        if (authoritativeYield != null) {
            output.putString("AuthoritativeYield", authoritativeYield.getSerializedName());
            output.putBoolean("AuthoritativeCustomFire", authoritativeCustomFire);
        }
        output.putInt("ClusterIndex", clusterIndex);
        output.putInt("ClusterCount", clusterCount);
    }

    public UUID warheadId() {
        return warheadId;
    }

    public UUID ownerPlayerId() {
        return ownerPlayerId;
    }

    public UUID radarRootTrackId() {
        return radarRootTrackId == null ? warheadId : radarRootTrackId;
    }

    public Vec3 startPosition() {
        return startPosition;
    }

    public Vec3 intendedTarget() {
        return intendedTarget;
    }

    public long launchGameTime() {
        return launchGameTime;
    }

    public int flightTicks() {
        return flightTicks;
    }

    public long visualSeed() {
        return visualSeed;
    }

    public WarheadPayloadType payloadType() {
        return payloadType;
    }

    public int clusterIndex() {
        return clusterIndex;
    }

    public int clusterCount() {
        return clusterCount;
    }

    public boolean cancelForInterception(
        final ServerLevel server,
        final UUID interceptorId,
        final Vec3 interceptPosition
    ) {
        if (impacted || cancelled || isRemoved()) {
            return false;
        }

        cancelled = true;
        impacted = true;

        WarheadVisualNetworking.sendRemove(
            server,
            warheadId,
            intendedTarget
        );

        /*
         * Remove this terminal child from the authoritative radar track before
         * discarding the entity. Without this, intercepted/point-defence kills
         * remained on radar forever and kept warning redstone permanently on.
         */
        RadarTrackingService.removeTerminalWarhead(
            server,
            radarRootTrackId(),
            warheadId,
            RadarRemovalReason.INTERCEPTED
        );

        IncomingWarheadRegistry.unregister(
            server,
            radarRootTrackId(),
            warheadId
        );
        releaseStreamingTickets(server);
        WarheadPreparationCoordinator.cancelImpact(server, warheadId,
            CancellationReason.INTERCEPTED);
        discard();
        return true;
    }

    public boolean cancelForPointDefence(
        final ServerLevel server,
        final UUID bulletId,
        final Vec3 hitPosition
    ) {
        return cancelForInterception(server, bulletId, hitPosition);
    }

    private boolean valid() {
        return warheadId != null
            && radarRootTrackId() != null
            && startPosition != null
            && intendedTarget != null
            && payloadType != null
            && startPosition.isFinite()
            && intendedTarget.isFinite()
            && launchGameTime != Long.MIN_VALUE
            && flightTicks >= 1
            && flightTicks <= IcbmConstants.MAXIMUM_TERMINAL_TICKS;
    }

    private void emitSonicBoom(
        final ServerLevel server,
        final Vec3 position,
        final Vec3 velocity
    ) {
        if (impacted
            || sonicBoomEmitted
            || !position.isFinite()
            || !velocity.isFinite()) {
            return;
        }

        double normalized = WarheadVisualMath.normalizedSpeed(
            velocity,
            WarheadConstants.TRAJECTORY_SPEED_BLOCKS_PER_TICK * 1.65
        );

        if (normalized > 0.55 && velocity.y < 0) {
            AcousticEngine.playSound(
                server,
                position,
                AcousticSounds.TERMINAL_SONIC_BOOM_ID,
                SoundSource.BLOCKS,
                1.35F,
                1.0F
            );
            sonicBoomEmitted = true;

            if (SharedConstants.IS_RUNNING_IN_IDE) {
                WarMod.LOGGER.info(
                    "Warhead {} emitted sonic boom at {}",
                    warheadId,
                    position
                );
            }
        }
    }

    private void impact(final ServerLevel server, final Vec3 hit) {
        if (impacted || !hit.isFinite()) {
            return;
        }

        impacted = true;
        ServerPlayer owner = null;

        if (ownerPlayerId != null && server.getServer() != null) {
            ServerPlayer player = server.getServer()
                .getPlayerList()
                .getPlayer(ownerPlayerId);

            if (player != null && player.level() == server) {
                owner = player;
            }
        }

        if (SharedConstants.IS_RUNNING_IN_IDE) {
            WarMod.LOGGER.info(
                "Warhead {} impacted: payload={}, position={}",
                warheadId,
                payloadType.serializedName(),
                hit
            );
        }

        WarheadImpactChunkLeaseManager.hold(
            server,
            warheadId,
            hit,
            IcbmConstants.IMPACT_CHUNK_TAIL_TICKS
        );
        WarheadImpactService.impact(
            server,
            owner,
            warheadId,
            radarRootTrackId(),
            hit,
            visualSeed,
            payloadType
        );
        releaseStreamingTickets(server);
        IncomingWarheadRegistry.unregister(
            server,
            radarRootTrackId(),
            warheadId
        );
        discard();
    }

    private void cancel(final ServerLevel server) {
        if (warheadId != null
            && intendedTarget != null
            && intendedTarget.isFinite()) {
            WarheadVisualNetworking.sendRemove(
                server,
                warheadId,
                intendedTarget
            );
        }
    }

    private void updateRotation(final Vec3 velocity) {
        if (velocity.lengthSqr() < 1.0E-8) {
            return;
        }

        setYRot((float)(Math.atan2(velocity.z, velocity.x) * 180 / Math.PI) - 90);
        setXRot((float)(
            -Math.atan2(velocity.y, velocity.horizontalDistance())
                * 180
                / Math.PI
        ));
    }

    private RaycastResult raycastLoaded(
        final ServerLevel level,
        final Vec3 from,
        final Vec3 to
    ) {
        AtomicBoolean missing = new AtomicBoolean();
        ClipContext context = new ClipContext(
            from,
            to,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            this
        );
        Optional<BlockHitResult> hit = BlockGetter.traverseBlocks(
            from,
            to,
            context,
            (clipContext, position) -> {
                if (!level.getChunkSource().hasChunk(
                    SectionPos.blockToSectionCoord(position.getX()),
                    SectionPos.blockToSectionCoord(position.getZ())
                )) {
                    missing.set(true);
                    return Optional.empty();
                }

                BlockState state = level.getBlockState(position);
                VoxelShape shape = clipContext.getBlockShape(
                    state,
                    level,
                    position
                );
                BlockHitResult result = level.clipWithInteractionOverride(
                    from,
                    to,
                    position,
                    shape,
                    state
                );
                return result == null ? null : Optional.of(result);
            },
            ignored -> Optional.empty()
        );

        return new RaycastResult(
            hit == null ? Optional.empty() : hit,
            missing.get()
        );
    }

    private Set<ChunkPos> desiredStreamingWindow(final double elapsed) {
        HashSet<ChunkPos> desired = new HashSet<>();
        double future = Math.min(
            flightTicks,
            elapsed + IcbmConstants.TERMINAL_STREAM_LOOKAHEAD_TICKS
        );
        Vec3 current = WarheadTrajectory.position(
            startPosition,
            intendedTarget,
            elapsed,
            flightTicks,
            clusterIndex,
            clusterCount
        );
        Vec3 lookahead = WarheadTrajectory.position(
            startPosition,
            intendedTarget,
            future,
            flightTicks,
            clusterIndex,
            clusterCount
        );

        IcbmChunkTicketRegistry.addSegmentWindow(
            desired,
            current,
            lookahead,
            IcbmConstants.TERMINAL_STREAM_RADIUS,
            IcbmConstants.TERMINAL_STREAM_SAMPLE_SPACING_BLOCKS
        );

        if (flightTicks - elapsed
            <= IcbmConstants.TERMINAL_TARGET_LEAD_TICKS) {
            IcbmChunkTicketRegistry.addWindow(
                desired,
                IcbmChunkTicketRegistry.chunk(intendedTarget),
                IcbmConstants.TERMINAL_TARGET_SIMULATION_CHUNK_RADIUS
            );
        }

        if (desired.size() > IcbmConstants.MAX_TERMINAL_STREAM_CHUNKS) {
            throw new IllegalStateException(
                "Terminal rolling window exceeded bound: " + desired.size()
            );
        }

        return Set.copyOf(desired);
    }

    private boolean prepareStreamingWindow(
        final ServerLevel level,
        final Set<ChunkPos> desired
    ) {
        for (ChunkPos chunk : desired) {
            if (heldTicketChunks.add(chunk)) {
                IcbmChunkTicketRegistry.acquire(level, chunk);
            }
        }

        if (!IcbmChunkTicketRegistry.allLoaded(level, desired)) {
            return false;
        }

        for (ChunkPos chunk : Set.copyOf(heldTicketChunks)) {
            if (!desired.contains(chunk)) {
                IcbmChunkTicketRegistry.release(level, chunk);
                heldTicketChunks.remove(chunk);
            }
        }

        return true;
    }

    private void pauseForChunkLoading(
        final ServerLevel level,
        final Vec3 safePosition
    ) {
        pausedSimulationTicks++;
        consecutiveChunkWaitTicks++;
        setPos(safePosition);
        setDeltaMovement(Vec3.ZERO);

        if (!waitingForChunks) {
            waitingForChunks = true;
            WarheadVisualNetworking.sendTimingCorrection(
                level,
                warheadId,
                pausedSimulationTicks,
                true,
                safePosition
            );
        }

        if (consecutiveChunkWaitTicks
            >= IcbmConstants.TERMINAL_CHUNK_WAIT_TIMEOUT_TICKS) {
            cancel(level);
            releaseStreamingTickets(level);
            IncomingWarheadRegistry.unregister(
                level,
                radarRootTrackId(),
                warheadId
            );
            discard();
        }
    }

    private void releaseStreamingTickets(final ServerLevel level) {
        if (ticketCleanupComplete) {
            return;
        }

        for (ChunkPos chunk : Set.copyOf(heldTicketChunks)) {
            IcbmChunkTicketRegistry.release(level, chunk);
        }

        heldTicketChunks.clear();
        ticketCleanupComplete = true;
    }

    @Override
    public void remove(final RemovalReason reason) {
        if (level() instanceof ServerLevel server) {
            releaseStreamingTickets(server);

            /*
             * Catch invalid data, timeout removal and any other non-impact
             * discard path so it cannot leave an orphaned permanent track.
             */
            if (!impacted
                && !cancelled
                && warheadId != null
                && radarRootTrackId() != null) {
                RadarTrackingService.removeTerminalWarhead(
                    server,
                    radarRootTrackId(),
                    warheadId,
                    RadarRemovalReason.CANCELLED
                );
                IncomingWarheadRegistry.unregister(
                    server,
                    radarRootTrackId(),
                    warheadId
                );
                WarheadPreparationCoordinator.cancelImpact(server, warheadId,
                    CancellationReason.ENTITY_REMOVED);
            }
        }

        super.remove(reason);
    }

    private record RaycastResult(
        Optional<BlockHitResult> hit,
        boolean missingChunk
    ) {
    }
}
