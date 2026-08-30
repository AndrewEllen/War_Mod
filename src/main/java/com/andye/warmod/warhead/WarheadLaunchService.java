package com.andye.warmod.warhead;

import com.andye.warmod.WarMod;
import com.andye.warmod.entity.IncomingWarheadEntity;
import com.andye.warmod.entity.ModEntityTypes;
import com.andye.warmod.icbm.IcbmChunkTicketRegistry;
import com.andye.warmod.icbm.IcbmConstants;
import com.andye.warmod.radar.RadarTrackingService;
import com.andye.warmod.warhead.network.ClientboundWarheadLaunchPayload;
import com.andye.warmod.warhead.network.WarheadVisualNetworking;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class WarheadLaunchService {
    private enum SpawnContext {
        DIRECT,
        CARRIER
    }

    private WarheadLaunchService() {
    }

    public static Optional<LaunchResult> launch(
        final ServerLevel level,
        final ServerPlayer owner,
        final Vec3 intendedTarget
    ) {
        return launch(
            level,
            owner,
            intendedTarget,
            WarheadPayloadType.CONVENTIONAL
        );
    }

    public static Optional<LaunchResult> launch(
        final ServerLevel level,
        final ServerPlayer owner,
        final Vec3 intendedTarget,
        final WarheadPayloadType payloadType
    ) {
        Objects.requireNonNull(owner, "owner");

        if (owner.level() != level
            || owner.getEyePosition().distanceTo(intendedTarget)
                > WarheadConstants.TARGET_RANGE_BLOCKS + 0.001) {
            return Optional.empty();
        }

        UUID id = UUID.randomUUID();
        long seed = deriveSeed(
            id,
            id.getMostSignificantBits()
                ^ Long.rotateLeft(id.getLeastSignificantBits(), 17),
            payloadType
        );
        Vec3 start = calculateStartPosition(level, intendedTarget, seed)
            .orElse(null);

        if (start == null) {
            return Optional.empty();
        }

        Optional<LaunchResult> result = spawn(
            level,
            owner,
            id,
            start,
            intendedTarget,
            clamp((int)Math.ceil(
                start.distanceTo(intendedTarget)
                    / WarheadConstants.TRAJECTORY_SPEED_BLOCKS_PER_TICK
            )),
            seed,
            payloadType,
            id,
            0,
            1,
            SpawnContext.DIRECT
        );

        result.ifPresent(value ->
            RadarTrackingService.registerDirectWarhead(level, owner, value)
        );

        return result;
    }

    public static Optional<LaunchResult> launchFromCarrier(
        final ServerLevel level,
        final @Nullable ServerPlayer owner,
        final Vec3 separationPosition,
        final Vec3 intendedTarget,
        final long parentVisualSeed,
        final WarheadPayloadType payloadType,
        final UUID radarRootTrackId
    ) {
        UUID id = UUID.randomUUID();
        long seed = deriveSeed(id, parentVisualSeed, payloadType);
        int ticks = clampTerminal((int)Math.ceil(
            separationPosition.distanceTo(intendedTarget)
                / WarheadConstants.TRAJECTORY_SPEED_BLOCKS_PER_TICK
        ));

        Optional<LaunchResult> result = spawn(
            level,
            owner,
            id,
            separationPosition,
            intendedTarget,
            ticks,
            seed,
            payloadType,
            radarRootTrackId,
            0,
            1,
            SpawnContext.CARRIER
        );
        if (payloadType == WarheadPayloadType.NUCLEAR && result.isPresent()) {
            transferCarrierPreparation(level, radarRootTrackId,
                List.of(result.get()), WarheadDeliveryMode.SINGLE);
        }
        return result;
    }

    public static List<LaunchResult> launchClusterFromCarrier(
        final ServerLevel level,
        final @Nullable ServerPlayer owner,
        final Vec3 separationPosition,
        final Vec3 intendedTarget,
        final long parentVisualSeed,
        final WarheadPayloadType payloadType,
        final UUID radarRootTrackId
    ) {
        ArrayList<LaunchResult> results = new ArrayList<>(4);
        double rotation = ((parentVisualSeed >>> 11) & 65535)
            / 65535.0
            * Math.PI
            * 2.0;

        for (int index = 0; index < 4; index++) {
            double angle = rotation + index * Math.PI * 0.5;
            double radius = 7.0
                + ((parentVisualSeed >>> (index * 7)) & 7) * 0.55;
            Vec3 target = new Vec3(
                intendedTarget.x + Math.cos(angle) * radius,
                intendedTarget.y,
                intendedTarget.z + Math.sin(angle) * radius
            );

            if (!level.getWorldBorder().isWithinBounds(target)
                || level.isOutsideBuildHeight(
                    net.minecraft.core.BlockPos.containing(target)
                )) {
                target = intendedTarget;
            }

            Vec3 start = separationPosition.add(
                Math.cos(angle) * 0.28,
                0.0,
                Math.sin(angle) * 0.28
            );
            UUID id = UUID.randomUUID();
            long seed = deriveSeed(
                id,
                parentVisualSeed
                    + index * 0x9E3779B97F4A7C15L,
                payloadType
            );
            int ticks = clampTerminal((int)Math.ceil(
                start.distanceTo(target)
                    / WarheadConstants.TRAJECTORY_SPEED_BLOCKS_PER_TICK
            ));

            spawn(
                level,
                owner,
                id,
                start,
                target,
                ticks,
                seed,
                payloadType,
                radarRootTrackId,
                index,
                4,
                SpawnContext.CARRIER
            ).ifPresent(results::add);
        }

        if (results.size() != 4) {
            for (LaunchResult result : results) {
                IncomingWarheadRegistry.getByWarheadId(
                    level,
                    result.warheadId()
                ).ifPresent(IncomingWarheadEntity::discard);

                WarheadImpactChunkLeaseManager.release(
                    level,
                    result.warheadId()
                );
            }

            return List.of();
        }

        if (payloadType == WarheadPayloadType.NUCLEAR) {
            transferCarrierPreparation(level, radarRootTrackId, results,
                WarheadDeliveryMode.CLUSTER_FOUR);
        }

        return List.copyOf(results);
    }

    private static void transferCarrierPreparation(final ServerLevel level,
        final UUID radarRootTrackId, final List<LaunchResult> results,
        final WarheadDeliveryMode deliveryMode) {
        ArrayList<PreparedImpactSpec> impacts = new ArrayList<>(results.size());
        long expectedImpactTick = level.getGameTime();
        for (LaunchResult result : results) {
            WarheadYield exactYield = WarheadYieldRegistry.resolve(level,
                result.warheadId(), radarRootTrackId, result.payloadType());
            Vec3 effectiveCenter = WarheadExplosionWorkManager.resolveDetonationCenter(
                level, result.intendedTarget(), exactYield);
            impacts.add(new PreparedImpactSpec(result.warheadId(), effectiveCenter,
                result.payloadType(), exactYield, result.visualSeed(),
                WarheadYieldRegistry.usesCustomFire(level, result.warheadId(),
                    radarRootTrackId)));
            expectedImpactTick = Math.max(expectedImpactTick,
                result.launchGameTime() + result.flightTicks());
        }
        WarheadPreparationCoordinator.request(level,
            new WarheadPreparationRequest(radarRootTrackId, radarRootTrackId,
                level.dimension(), impacts, expectedImpactTick, deliveryMode));
    }

    private static Optional<LaunchResult> spawn(
        final ServerLevel level,
        final @Nullable ServerPlayer owner,
        final UUID id,
        final Vec3 start,
        final Vec3 target,
        final int ticks,
        final long seed,
        final WarheadPayloadType payloadType,
        final UUID radarRootTrackId,
        final int clusterIndex,
        final int clusterCount,
        final SpawnContext context
    ) {
        Objects.requireNonNull(level);
        Objects.requireNonNull(start);
        Objects.requireNonNull(target);
        Objects.requireNonNull(payloadType);

        if (!start.isFinite() || !target.isFinite()) {
            return Optional.empty();
        }

        if (context == SpawnContext.DIRECT) {
            if (!loaded(level, start) || !loaded(level, target)) {
                return Optional.empty();
            }
        } else {
            Set<ChunkPos> initialWindow = IcbmChunkTicketRegistry.window(
                IcbmChunkTicketRegistry.chunk(start),
                IcbmConstants.TERMINAL_STREAM_RADIUS
            );

            if (!IcbmChunkTicketRegistry.allLoaded(level, initialWindow)) {
                if (SharedConstants.IS_RUNNING_IN_IDE) {
                    WarMod.LOGGER.warn(
                        "Warhead {} carrier handoff window not ready at {}",
                        id,
                        start
                    );
                }

                return Optional.empty();
            }
        }

        long gameTime = level.getGameTime();
        IncomingWarheadEntity entity = new IncomingWarheadEntity(
            ModEntityTypes.INCOMING_WARHEAD,
            level,
            id,
            owner == null ? null : owner.getUUID(),
            start,
            target,
            gameTime,
            ticks,
            seed,
            payloadType,
            radarRootTrackId,
            clusterIndex,
            clusterCount
        );

        if (!level.addFreshEntity(entity)) {
            return Optional.empty();
        }

        /*
         * Load the defended area before the missile reaches it, not only after
         * the impact callback. The expiry includes flight time plus the full
         * two-minute post-impact retention period.
         */
        WarheadImpactChunkLeaseManager.holdApproach(
            level,
            id,
            start,
            target,
            ticks + IcbmConstants.IMPACT_CHUNK_TAIL_TICKS
        );

        WarheadYield exactYield = WarheadYieldRegistry.resolve(level, id,
            radarRootTrackId, payloadType);
        if (exactYield.nuclear()) {
            WarheadPreImpactPreparationManager.scheduleKnownNuclearTerrain(level,
                id, target, exactYield, seed,
                ticks + IcbmConstants.IMPACT_CHUNK_TAIL_TICKS);
        }
        WarheadDeliveryMode exactDelivery = context == SpawnContext.CARRIER
            ? StrategicMissilePayloadRegistry.get(radarRootTrackId, payloadType).deliveryMode()
            : WarheadDeliveryMode.SINGLE;
        WarheadVisualNetworking.sendLaunch(
            level,
            new ClientboundWarheadLaunchPayload(
                id,
                start.x,
                start.y,
                start.z,
                target.x,
                target.y,
                target.z,
                gameTime,
                ticks,
                seed,
                payloadType,
                exactYield,
                exactDelivery
            ),
            target
        );

        if (SharedConstants.IS_RUNNING_IN_IDE) {
            WarMod.LOGGER.info(
                "Warhead {} launched: payload={}, start={}, target={}, flight={}",
                id,
                payloadType.serializedName(),
                start,
                target,
                ticks
            );
        }

        return Optional.of(new LaunchResult(
            id,
            start,
            target,
            gameTime,
            ticks,
            seed,
            payloadType,
            radarRootTrackId,
            clusterIndex,
            clusterCount
        ));
    }

    private static Optional<Vec3> calculateStartPosition(
        final ServerLevel level,
        final Vec3 target,
        final long seed
    ) {
        if (!target.isFinite() || !loaded(level, target)) {
            return Optional.empty();
        }

        float cloudHeight;

        try {
            cloudHeight = level.environmentAttributes()
                .getValue(EnvironmentAttributes.CLOUD_HEIGHT, target)
                .floatValue();
        } catch (RuntimeException ignored) {
            cloudHeight = Float.NaN;
        }

        double desiredY = target.y
            + WarheadConstants.PREFERRED_SPAWN_HEIGHT_ABOVE_TARGET;

        if (Float.isFinite(cloudHeight)) {
            desiredY = Math.max(desiredY, cloudHeight + 8.0);
        }

        double maximumY = level.dimensionType().minY()
            + level.dimensionType().height()
            - 2.0;
        double minimumY = target.y
            + WarheadConstants.MINIMUM_SPAWN_HEIGHT_ABOVE_TARGET;
        double startY = maximumY >= minimumY
            ? Math.max(minimumY, Math.min(desiredY, maximumY))
            : maximumY;

        if (!Double.isFinite(startY) || startY <= target.y) {
            return Optional.empty();
        }

        SplittableRandom random = new SplittableRandom(seed);
        double angle = random.nextDouble(0.0, Math.PI * 2.0);
        double radius = random.nextDouble(4.0, 10.0);
        Vec3 offset = new Vec3(
            target.x + Math.cos(angle) * radius,
            startY,
            target.z + Math.sin(angle) * radius
        );

        if (loaded(level, offset)) {
            return Optional.of(offset);
        }

        Vec3 direct = new Vec3(target.x, startY, target.z);
        return loaded(level, direct)
            ? Optional.of(direct)
            : Optional.empty();
    }

    private static long deriveSeed(
        final UUID id,
        final long parent,
        final WarheadPayloadType type
    ) {
        long value = parent
            ^ id.getMostSignificantBits()
            ^ Long.rotateLeft(id.getLeastSignificantBits(), 23)
            ^ ((long)type.ordinal() * 0x9E3779B97F4A7C15L);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static int clamp(final int ticks) {
        return Math.max(
            WarheadConstants.MINIMUM_FLIGHT_TICKS,
            Math.min(WarheadConstants.MAXIMUM_FLIGHT_TICKS, ticks)
        );
    }

    private static int clampTerminal(final int ticks) {
        return Math.max(
            IcbmConstants.MINIMUM_TERMINAL_TICKS,
            Math.min(IcbmConstants.MAXIMUM_TERMINAL_TICKS, ticks)
        );
    }

    private static boolean loaded(
        final ServerLevel level,
        final Vec3 position
    ) {
        return level.getChunkSource().hasChunk(
            SectionPos.blockToSectionCoord(position.x),
            SectionPos.blockToSectionCoord(position.z)
        );
    }

    public record LaunchResult(
        UUID warheadId,
        Vec3 startPosition,
        Vec3 intendedTarget,
        long launchGameTime,
        int flightTicks,
        long visualSeed,
        WarheadPayloadType payloadType,
        UUID radarRootTrackId,
        int clusterIndex,
        int clusterCount
    ) {
        public LaunchResult(
            final UUID warheadId,
            final Vec3 startPosition,
            final Vec3 intendedTarget,
            final long launchGameTime,
            final int flightTicks,
            final long visualSeed,
            final WarheadPayloadType payloadType,
            final UUID radarRootTrackId
        ) {
            this(
                warheadId,
                startPosition,
                intendedTarget,
                launchGameTime,
                flightTicks,
                visualSeed,
                payloadType,
                radarRootTrackId,
                0,
                1
            );
        }
    }
}
