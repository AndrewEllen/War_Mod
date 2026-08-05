package com.andye.warmod.block.entity;

import com.andye.warmod.WarMod;
import com.andye.warmod.block.RadarStationBlock;
import com.andye.warmod.icbm.IcbmConstants;
import com.andye.warmod.radar.station.RadarRedstoneMode;
import com.andye.warmod.radar.station.RadarStationChunkTicketManager;
import com.andye.warmod.radar.station.RadarStationConstants;
import com.andye.warmod.radar.station.RadarStationManager;
import com.andye.warmod.radar.station.RadarStationObservation;
import com.andye.warmod.radar.station.RadarSweepMath;
import com.andye.warmod.warhead.WarheadConstants;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class RadarStationBlockEntity extends BlockEntity {
    private UUID radarId = UUID.randomUUID();
    private Direction facing = Direction.NORTH;
    private @Nullable UUID ownerId;
    private @Nullable UUID primaryThreatId;
    private String ownerName = "SERVER";
    private double warningRadius = 256.0;
    private double fireRadius = 500.0;
    private double primaryThreatDistance = Double.POSITIVE_INFINITY;
    private long phaseOffset;
    private long primaryThreatEstimatedImpactTime = Long.MAX_VALUE;
    private int redstoneSignal;
    private int comparatorSignal;
    private boolean warningActive;
    private boolean teardown;
    private boolean dynamicChunkLoading = true;
    private RadarRedstoneMode redstoneMode = RadarRedstoneMode.ANALOG_DISTANCE;
    private final Map<UUID, RadarStationObservation> observations =
        new LinkedHashMap<>();

    public RadarStationBlockEntity(
        final BlockPos position,
        final BlockState state
    ) {
        super(ModBlockEntities.RADAR_STATION, position, state);
        if (state.hasProperty(RadarStationBlock.FACING)) {
            facing = state.getValue(RadarStationBlock.FACING);
        }
    }

    public void initialize(
        final ServerLevel level,
        final Direction direction,
        final @Nullable Player owner
    ) {
        radarId = UUID.randomUUID();
        facing = direction;
        ownerId = owner == null ? null : owner.getUUID();
        ownerName = owner == null ? "SERVER" : owner.getGameProfile().name();
        phaseOffset = RadarSweepMath.phaseOffset(radarId);
        dynamicChunkLoading = true;
        sync();
    }

    public UUID radarId() { return radarId; }
    public Direction facing() { return facing; }
    public @Nullable UUID ownerId() { return ownerId; }
    public String ownerName() { return ownerName; }
    public double warningRadius() { return warningRadius; }
    public double fireRadius() { return fireRadius; }
    public RadarRedstoneMode redstoneMode() { return redstoneMode; }
    public int redstoneSignal() { return redstoneSignal; }
    public int comparatorSignal() { return comparatorSignal; }
    public @Nullable UUID primaryThreatId() { return primaryThreatId; }
    public double primaryThreatDistance() { return primaryThreatDistance; }
    public long phaseOffset() { return phaseOffset; }
    public boolean warningActive() { return warningActive; }
    public boolean dynamicChunkLoading() { return dynamicChunkLoading; }

    public int threatCount() {
        return (int)observations.values().stream()
            .filter(RadarStationObservation::threatensWarningZone)
            .count();
    }

    public Collection<RadarStationObservation> observations() {
        return List.copyOf(observations.values());
    }

    public void setTeardownInProgress(final boolean value) { teardown = value; }
    public boolean teardownInProgress() { return teardown; }

    public void configure(final double warning, final double fire) {
        configure(warning, fire, redstoneMode);
    }

    public void configure(
        final double warning,
        final double fire,
        final RadarRedstoneMode mode
    ) {
        RadarRedstoneMode previousMode = redstoneMode;
        warningRadius = clampRadius(warning);
        fireRadius = clampRadius(fire);
        redstoneMode = RadarRedstoneMode.isRegistered(mode)
            ? mode
            : RadarRedstoneMode.ANALOG_DISTANCE;

        if (level instanceof ServerLevel server) {
            Vec3 centre = Vec3.atCenterOf(worldPosition);
            observations.replaceAll((id, observation) -> {
                double dx = observation.predictedImpactPosition().x - centre.x;
                double dz = observation.predictedImpactPosition().z - centre.z;
                return new RadarStationObservation(
                    observation.trackId(),
                    observation.trackSnapshot(),
                    observation.observedPosition(),
                    observation.observedVelocity(),
                    observation.predictedImpactPosition(),
                    observation.observationGameTime(),
                    observation.observedRouteTime(),
                    dx * dx + dz * dz <= warningRadius * warningRadius
                );
            });
            recalculate(server);
            if (previousMode != redstoneMode && SharedConstants.IS_RUNNING_IN_IDE) {
                WarMod.LOGGER.info(
                    "Radar station {} redstone mode changed: old={}, new={}",
                    radarId,
                    previousMode.serializedName(),
                    redstoneMode.serializedName()
                );
            }
        }
        RadarStationManager.updateRecord(this);
        sync();
    }

    public void setWarningRadius(final double value) {
        configure(value, fireRadius, redstoneMode);
    }

    public void setDynamicChunkLoading(final boolean enabled) {
        if (dynamicChunkLoading == enabled) return;
        dynamicChunkLoading = enabled;
        if (!enabled && level instanceof ServerLevel server) {
            RadarStationChunkTicketManager.disableDynamic(server, radarId);
        }
        RadarStationManager.updateRecord(this);
        sync();
    }

    public boolean observe(
        final ServerLevel level,
        final RadarStationObservation observation
    ) {
        observations.put(observation.trackId(), observation);
        while (observations.size() > RadarStationConstants.MAX_OBSERVATIONS) {
            observations.remove(observations.keySet().iterator().next());
        }
        return recalculate(level);
    }

    public boolean prune(
        final ServerLevel level,
        final long now,
        final java.util.Set<UUID> active
    ) {
        observations.values().removeIf(observation ->
            !active.contains(observation.trackId())
                || now - observation.observationGameTime()
                    > RadarStationConstants.SWEEP_PERIOD_TICKS * 2L
        );
        return recalculate(level);
    }

    private boolean recalculate(final ServerLevel level) {
        int oldSignal = redstoneSignal;
        int oldComparatorSignal = comparatorSignal;
        UUID oldPrimary = primaryThreatId;
        Vec3 centre = Vec3.atCenterOf(worldPosition);
        RadarStationObservation best = null;
        int bestDistanceSignal = 0;
        double bestDistance = Double.POSITIVE_INFINITY;
        long bestEta = Long.MAX_VALUE;

        for (RadarStationObservation observation : observations.values()) {
            if (!observation.threatensWarningZone()) continue;
            double distance = Math.hypot(
                observation.observedPosition().x - centre.x,
                observation.observedPosition().z - centre.z
            );
            int distanceSignal = distanceSignal(distance);
            long eta = estimatedImpact(observation);
            if (best == null
                || distanceSignal > bestDistanceSignal
                || distanceSignal == bestDistanceSignal
                    && (eta < bestEta
                        || eta == bestEta
                            && (distance < bestDistance
                                || distance == bestDistance
                                    && observation.trackId().toString()
                                        .compareTo(best.trackId().toString()) < 0))) {
                best = observation;
                bestDistanceSignal = distanceSignal;
                bestDistance = distance;
                bestEta = eta;
            }
        }

        comparatorSignal = best == null ? 0 : bestDistanceSignal;
        redstoneSignal = best == null
            ? 0
            : redstoneMode == RadarRedstoneMode.ANALOG_DISTANCE
                ? bestDistanceSignal
                : bestDistance <= fireRadius ? 15 : 0;
        redstoneSignal = Math.max(0, Math.min(15, redstoneSignal));
        comparatorSignal = Math.max(0, Math.min(15, comparatorSignal));
        warningActive = best != null;
        primaryThreatId = best == null ? null : best.trackId();
        primaryThreatDistance = bestDistance;
        primaryThreatEstimatedImpactTime = bestEta;

        boolean changed = oldSignal != redstoneSignal
            || oldComparatorSignal != comparatorSignal
            || !Objects.equals(oldPrimary, primaryThreatId);

        if (oldSignal != redstoneSignal || oldComparatorSignal != comparatorSignal) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    level.updateNeighborsAt(
                        worldPosition.offset(x, 0, z),
                        getBlockState().getBlock()
                    );
                }
            }
            if (SharedConstants.IS_RUNNING_IN_IDE) {
                WarMod.LOGGER.info(
                    "Radar station {} output changed: block={}, comparator={}, primary={}",
                    radarId,
                    redstoneSignal,
                    comparatorSignal,
                    primaryThreatId
                );
            }
        }
        if (changed) sync();
        return changed;
    }

    private int distanceSignal(final double distance) {
        if (distance <= fireRadius) return 15;
        double denominator = RadarStationConstants.DETECTION_RANGE_BLOCKS - fireRadius;
        if (denominator <= 0.0) return 14;
        double normalized = Math.max(0.0, Math.min(1.0,
            (RadarStationConstants.DETECTION_RANGE_BLOCKS - distance) / denominator
        ));
        double smooth = normalized * normalized * (3.0 - 2.0 * normalized);
        return Math.max(1, Math.min(14, 1 + (int)Math.floor(smooth * 13.0)));
    }

    private static long estimatedImpact(final RadarStationObservation observation) {
        return observation.trackSnapshot().terminalPlan()
            .map(plan -> plan.launchGameTime() + plan.flightTicks())
            .orElseGet(() -> observation.trackSnapshot().carrierPlan()
                .map(plan -> {
                    int terminalTicks = (int)Math.ceil(
                        plan.separationPosition().distanceTo(plan.intendedTarget())
                            / WarheadConstants.TRAJECTORY_SPEED_BLOCKS_PER_TICK
                    );
                    terminalTicks = Math.max(
                        IcbmConstants.MINIMUM_TERMINAL_TICKS,
                        Math.min(IcbmConstants.MAXIMUM_TERMINAL_TICKS, terminalTicks)
                    );
                    return plan.launchGameTime()
                        + plan.ignitionTicks()
                        + plan.boostTicks()
                        + plan.coastTicks()
                        + terminalTicks;
                }).orElse(Long.MAX_VALUE));
    }

    private static double clampRadius(final double value) {
        double clamped = Math.max(16.0, Math.min(4096.0, value));
        return Math.round(clamped / 16.0) * 16.0;
    }

    public void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(final ValueOutput output) {
        super.saveAdditional(output);
        output.store("radar_id", UUIDUtil.CODEC, radarId);
        output.putString("facing", facing.getSerializedName());
        output.storeNullable("owner_id", UUIDUtil.CODEC, ownerId);
        output.putString("owner_name", ownerName);
        output.putDouble("warning_radius", warningRadius);
        output.putDouble("fire_radius", fireRadius);
        output.putLong("phase_offset", phaseOffset);
        output.store("redstone_mode", RadarRedstoneMode.CODEC, redstoneMode);
        output.putInt("redstone_signal", redstoneSignal);
        output.putInt("comparator_signal", comparatorSignal);
        output.putBoolean("dynamic_chunk_loading", dynamicChunkLoading);
    }

    @Override
    protected void loadAdditional(final ValueInput input) {
        super.loadAdditional(input);
        radarId = input.read("radar_id", UUIDUtil.CODEC).orElseGet(UUID::randomUUID);
        facing = Direction.byName(input.getStringOr("facing", "north"));
        if (facing == null || !facing.getAxis().isHorizontal()) facing = Direction.NORTH;
        ownerId = input.read("owner_id", UUIDUtil.CODEC).orElse(null);
        ownerName = input.getStringOr("owner_name", "SERVER");
        warningRadius = clampRadius(input.getDoubleOr("warning_radius", 256.0));
        fireRadius = clampRadius(input.getDoubleOr("fire_radius", 500.0));
        phaseOffset = input.getLongOr("phase_offset", RadarSweepMath.phaseOffset(radarId));
        redstoneMode = input.read("redstone_mode", RadarRedstoneMode.CODEC)
            .orElse(RadarRedstoneMode.ANALOG_DISTANCE);
        redstoneSignal = Math.max(0, Math.min(15, input.getIntOr("redstone_signal", 0)));
        comparatorSignal = Math.max(0, Math.min(15,
            input.getIntOr("comparator_signal", redstoneSignal)
        ));
        dynamicChunkLoading = input.getBooleanOr("dynamic_chunk_loading", true);
        warningActive = redstoneSignal > 0 || comparatorSignal > 0;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(
        final net.minecraft.core.HolderLookup.Provider registries
    ) {
        return saveCustomOnly(registries);
    }
}
