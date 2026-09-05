package com.andye.warmod.block.entity;

import com.andye.warmod.item.component.LinkedSilo;
import com.andye.warmod.silo.LaunchControllerBatchResult;
import com.andye.warmod.silo.LaunchControllerLaunchService;
import com.andye.warmod.silo.MissileSiloLaunchTrigger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public final class LaunchControllerBlockEntity extends BlockEntity {
    public static final int MAX_LINKED_SILOS = 64;

    private UUID controllerId = UUID.randomUUID();
    private final LinkedHashMap<UUID, LinkedSilo> linkedSilos =
        new LinkedHashMap<>();
    private int previousRedstoneSignal;
    private String lastBatchSummary = "No launches requested";

    public LaunchControllerBlockEntity(
        final BlockPos position,
        final BlockState state
    ) {
        super(ModBlockEntities.LAUNCH_CONTROLLER, position, state);
    }

    public static void serverTick(
        final Level level,
        final BlockPos position,
        final BlockState state,
        final LaunchControllerBlockEntity controller
    ) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }

        int signal = Math.max(0, Math.min(15, level.getBestNeighborSignal(position)));
        if (isRisingEdge(controller.previousRedstoneSignal, signal)) {
            LaunchControllerLaunchService.requestLaunches(
                server,
                controller,
                MissileSiloLaunchTrigger.CONTROLLER_REDSTONE,
                null,
                null,
                null
            );
        }
        if (controller.previousRedstoneSignal != signal) {
            controller.previousRedstoneSignal = signal;
            controller.sync();
        }
    }

    public static boolean isRisingEdge(
        final int previousSignal,
        final int currentSignal
    ) {
        return previousSignal <= 0 && currentSignal > 0;
    }

    public UUID controllerId() {
        return controllerId;
    }

    public List<LinkedSilo> linkedSilos() {
        return List.copyOf(linkedSilos.values());
    }

    public String lastBatchSummary() {
        return lastBatchSummary;
    }

    public LinkChange toggleLink(final LinkedSilo link) {
        if (link == null || !link.isValid() || level == null
            || !link.dimension().equals(level.dimension())) {
            return LinkChange.failed("Selected silo link is invalid");
        }

        if (linkedSilos.remove(link.siloId()) != null) {
            sync();
            return new LinkChange(true, false, "Silo removed from Launch Controller");
        }

        boolean coordinateOccupied = linkedSilos.values().stream()
            .anyMatch(existing -> existing.centre().equals(link.centre()));
        if (coordinateOccupied) {
            return LinkChange.failed(
                "That position is already bound to another silo UUID; remove the stale link first"
            );
        }
        if (linkedSilos.size() >= MAX_LINKED_SILOS) {
            return LinkChange.failed("Launch Controller link limit reached");
        }

        linkedSilos.put(link.siloId(), link);
        sync();
        return new LinkChange(true, true, "Silo linked to Launch Controller");
    }

    /** Adds a silo without giving a repeated linking-tool click remove semantics. */
    public LinkChange addLink(final LinkedSilo link) {
        if (link == null || !link.isValid() || level == null
            || !link.dimension().equals(level.dimension())) {
            return LinkChange.failed("Selected silo link is invalid");
        }

        return switch (classifyAdd(linkedSilos.values().stream().toList(), link)) {
            case ALREADY_LINKED -> new LinkChange(
                false,
                true,
                "Silo is already linked to this Launch Controller"
            );
            case IDENTITY_CONFLICT -> LinkChange.failed(
                "That silo UUID is already bound to another position"
            );
            case COORDINATE_CONFLICT -> LinkChange.failed(
                "That position is already bound to another silo UUID; remove the stale link first"
            );
            case LIMIT_REACHED -> LinkChange.failed(
                "Launch Controller link limit reached"
            );
            case ADD -> {
                linkedSilos.put(link.siloId(), link);
                sync();
                yield new LinkChange(
                    true,
                    true,
                    "Silo linked to Launch Controller"
                );
            }
        };
    }

    static AddDecision classifyAdd(
        final List<LinkedSilo> current,
        final LinkedSilo candidate
    ) {
        for (LinkedSilo existing : current) {
            if (existing.siloId().equals(candidate.siloId())) {
                return existing.equals(candidate)
                    ? AddDecision.ALREADY_LINKED
                    : AddDecision.IDENTITY_CONFLICT;
            }
            if (existing.centre().equals(candidate.centre())) {
                return AddDecision.COORDINATE_CONFLICT;
            }
        }
        return current.size() >= MAX_LINKED_SILOS
            ? AddDecision.LIMIT_REACHED
            : AddDecision.ADD;
    }

    public boolean removeLink(final UUID siloId) {
        if (siloId == null || linkedSilos.remove(siloId) == null) {
            return false;
        }
        sync();
        return true;
    }

    public void recordBatch(final LaunchControllerBatchResult result) {
        lastBatchSummary = result == null
            ? "Launch request failed"
            : result.summary();
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(
                worldPosition,
                getBlockState(),
                getBlockState(),
                3
            );
        }
    }

    @Override
    protected void saveAdditional(final ValueOutput output) {
        super.saveAdditional(output);
        output.store("controller_id", UUIDUtil.CODEC, controllerId);
        output.store("linked_silos", LinkedSilo.CODEC.listOf(), linkedSilos());
        output.putInt("previous_redstone_signal", previousRedstoneSignal);
        output.putString("last_batch_summary", lastBatchSummary);
    }

    @Override
    protected void loadAdditional(final ValueInput input) {
        super.loadAdditional(input);
        controllerId = input.read("controller_id", UUIDUtil.CODEC)
            .orElseGet(UUID::randomUUID);
        linkedSilos.clear();
        for (LinkedSilo link : input.read(
            "linked_silos",
            LinkedSilo.CODEC.listOf()
        ).orElse(List.of())) {
            boolean coordinateAlreadyLinked = linkedSilos.values().stream()
                .anyMatch(existing -> existing.centre().equals(link.centre()));
            if (link.isValid()
                && !coordinateAlreadyLinked
                && linkedSilos.size() < MAX_LINKED_SILOS) {
                linkedSilos.putIfAbsent(link.siloId(), link);
            }
        }
        previousRedstoneSignal = Math.max(
            0,
            Math.min(15, input.getIntOr("previous_redstone_signal", 0))
        );
        lastBatchSummary = input.getStringOr(
            "last_batch_summary",
            "No launches requested"
        );
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

    public record LinkChange(boolean changed, boolean linked, String message) {
        public static LinkChange failed(final String message) {
            return new LinkChange(false, false, message);
        }
    }

    enum AddDecision {
        ADD,
        ALREADY_LINKED,
        IDENTITY_CONFLICT,
        COORDINATE_CONFLICT,
        LIMIT_REACHED
    }
}
