package com.andye.warmod.block.entity;

import com.andye.warmod.WarMod;
import com.andye.warmod.block.MissileSiloBlock;
import com.andye.warmod.block.MissileSiloState;
import com.andye.warmod.defence.DefenceAlly;
import com.andye.warmod.defence.DefenceOwnershipSnapshot;
import com.andye.warmod.item.component.TargetCoordinates;
import com.andye.warmod.silo.MissilePayloadItems;
import com.andye.warmod.silo.MissileSiloConstants;
import com.andye.warmod.silo.MissileSiloLaunchService;
import com.andye.warmod.silo.MissileSiloLaunchTrigger;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

public final class MissileSiloBlockEntity extends BlockEntity implements WorldlyContainer {
    private static final int[] SLOT = {0};
    private UUID siloId = UUID.randomUUID();
    private ResourceKey<Level> dimension = Level.OVERWORLD;
    private Direction facing = Direction.NORTH;
    private @Nullable UUID ownerPlayerId;
    private String ownerDisplayName = "SERVER";
    private final LinkedHashMap<UUID, String> allies = new LinkedHashMap<>();
    private final MissileSiloInventory inventory = new MissileSiloInventory(this::inventoryChanged);
    private @Nullable TargetCoordinates storedTarget;
    private MissileSiloState siloState = MissileSiloState.EMPTY;
    private int previousRedstoneSignal;
    private boolean redstoneCycleConsumed;
    private boolean pendingRedstoneLaunch;
    private int launchingTicksRemaining;
    private int cooldownTicksRemaining;
    private int reloadTicksRemaining;
    private int reloadTicksTotal;
    private long reloadStartGameTime;
    private long animationStartGameTime;
    private int installedGuidanceTier;
    private int leftGuidanceTier;
    private int rightGuidanceTier;
    private @Nullable UUID pendingLaunchRequestId;
    private ItemStack reservedMissile = ItemStack.EMPTY;
    private @Nullable UUID activeMissileId;
    private boolean teardownInProgress;
    private String lastError = "";

    public MissileSiloBlockEntity(final BlockPos pos, final BlockState state) {
        super(ModBlockEntities.MISSILE_SILO, pos, state);
        if (state.hasProperty(MissileSiloBlock.FACING))
            this.facing = state.getValue(MissileSiloBlock.FACING);
    }

    public void initialize(
            final ServerLevel level, final Direction facing, final @Nullable Player owner) {
        this.siloId = UUID.randomUUID();
        this.dimension = level.dimension();
        this.facing = facing;
        this.ownerPlayerId = owner == null ? null : owner.getUUID();
        this.ownerDisplayName = owner == null ? "SERVER" : owner.getGameProfile().name();
        this.allies.clear();
        this.recalculateIdleState();
        this.sync();
    }

    public static void serverTick(
            final Level level,
            final BlockPos pos,
            final BlockState state,
            final MissileSiloBlockEntity silo) {
        if (!(level instanceof ServerLevel server) || silo.teardownInProgress) return;
        if ((level.getGameTime() & 31L) == 0L
                && !com.andye.warmod.block.MissileSiloStructure.isComplete(
                        server, pos, state.getValue(MissileSiloBlock.FACING))) {
            silo.enterState(MissileSiloState.INVALID_STRUCTURE);
            com.andye.warmod.block.MissileSiloStructure.teardown(server, pos, state, true);
            return;
        }
        if (silo.siloState == MissileSiloState.PREPARING
                && !MissileSiloLaunchService.isPending(server, silo.pendingLaunchRequestId))
            silo.restoreReserved();
        long launchElapsed = Math.max(0L, level.getGameTime() - silo.animationStartGameTime);
        boolean preparingVentTick = silo.siloState == MissileSiloState.PREPARING
                && (level.getGameTime() & 1L) == 0L;
        boolean emergingVentTick = silo.siloState == MissileSiloState.LAUNCHING
                && launchElapsed <= MissileSiloConstants.DOOR_CLOSE_DELAY_TICKS;
        if (preparingVentTick || emergingVentTick) {
            // This plume is fixed to the throat rather than either door. During
            // emergence it fades as the moving nozzle trail clears the opening;
            // the already-spawned clouds provide a short, natural overlap.
            double remaining = emergingVentTick
                    ? 1.0 - launchElapsed
                            / (double) MissileSiloConstants.DOOR_CLOSE_DELAY_TICKS
                    : 0.5;
            int count = Math.max(1, (int) Math.ceil(1.0 + remaining * 4.0));
            server.sendParticles(
                    ParticleTypes.CLOUD,
                    pos.getX() + 0.5,
                    pos.getY() + 0.42,
                    pos.getZ() + 0.5,
                    count,
                    0.42,
                    0.06,
                    0.42,
                    0.018);
        }
        int signal =
                MissileSiloBlock.maximumIncomingSignal(
                        server, pos, state.getValue(MissileSiloBlock.FACING));
        silo.processRedstoneSignal(server, signal);
        if (silo.siloState == MissileSiloState.LAUNCHING && --silo.launchingTicksRemaining <= 0) {
            silo.enterState(MissileSiloState.COOLDOWN);
            silo.cooldownTicksRemaining = MissileSiloConstants.PRE_RELOAD_COOLDOWN_TICKS;
            silo.sync();
        } else if (silo.siloState == MissileSiloState.COOLDOWN
                && --silo.cooldownTicksRemaining <= 0) {
            if (!silo.missileStack().isEmpty()) silo.beginReload(server);
            else {
                silo.activeMissileId = null;
                silo.finishTransientState();
                silo.sync();
            }
        } else if (silo.siloState == MissileSiloState.RELOADING
                && --silo.reloadTicksRemaining <= 0) {
            silo.reloadTicksRemaining = 0;
            silo.activeMissileId = null;
            silo.finishTransientState();
            silo.sync();
            if (SharedConstants.IS_RUNNING_IN_IDE)
                WarMod.LOGGER.info(
                        "Silo {} reload complete: payload={}, remaining={}",
                        silo.siloId,
                        MissilePayloadItems.missileType(silo.missileStack())
                                .map(type -> type.serializedName())
                                .orElse("none"),
                        silo.missileStack().getCount());
        }
        silo.processPendingRedstoneLaunch(server);
    }

    private void processRedstoneSignal(final ServerLevel server, final int incomingSignal) {
        int signal = Math.max(0, Math.min(15, incomingSignal));
        boolean changed = this.previousRedstoneSignal != signal;
        this.previousRedstoneSignal = signal;
        if (signal == 0) {
            if (this.redstoneCycleConsumed || this.pendingRedstoneLaunch) changed = true;
            this.redstoneCycleConsumed = false;
            this.pendingRedstoneLaunch = false;
            if (changed) this.sync();
            return;
        }
        if (!this.redstoneCycleConsumed) {
            this.redstoneCycleConsumed = true;
            if (readyForRedstoneLaunch()) {
                attemptRedstoneLaunch(server, false);
            } else if (transientlyBusyForRedstone()) {
                this.pendingRedstoneLaunch = true;
            }
            changed = true;
            if (SharedConstants.IS_RUNNING_IN_IDE)
                WarMod.LOGGER.info(
                        "Silo {} redstone cycle: signal={}, consumed={}, pending={}, state={}",
                        this.siloId,
                        signal,
                        this.redstoneCycleConsumed,
                        this.pendingRedstoneLaunch,
                        this.siloState);
        }
        if (changed) this.sync();
    }

    private void processPendingRedstoneLaunch(final ServerLevel server) {
        if (!this.pendingRedstoneLaunch
                || this.previousRedstoneSignal <= 0
                || !readyForRedstoneLaunch()) return;
        this.pendingRedstoneLaunch = false;
        if (SharedConstants.IS_RUNNING_IN_IDE)
            WarMod.LOGGER.info(
                    "Silo {} redstone cycle: signal={}, consumed={}, pending={}, state={}",
                    this.siloId,
                    this.previousRedstoneSignal,
                    this.redstoneCycleConsumed,
                    false,
                    this.siloState);
        this.sync();
        attemptRedstoneLaunch(server, true);
    }

    private void attemptRedstoneLaunch(final ServerLevel server, final boolean pending) {
        MissileSiloLaunchService.requestLaunch(
                server,
                this,
                MissileSiloLaunchTrigger.REDSTONE,
                this.ownerPlayerId,
                this.ownerDisplayName,
                null);
        if (SharedConstants.IS_RUNNING_IN_IDE)
            WarMod.LOGGER.info(
                    "Silo {} redstone cycle: signal={}, consumed={}, pending={}, state={}",
                    this.siloId,
                    this.previousRedstoneSignal,
                    this.redstoneCycleConsumed,
                    pending,
                    this.siloState);
    }

    private boolean readyForRedstoneLaunch() {
        return this.siloState == MissileSiloState.READY && this.pendingLaunchRequestId == null;
    }

    private boolean transientlyBusyForRedstone() {
        return this.siloState == MissileSiloState.PREPARING
                || this.siloState == MissileSiloState.LAUNCHING
                || this.siloState == MissileSiloState.COOLDOWN
                || this.siloState == MissileSiloState.RELOADING;
    }

    public UUID siloId() {
        return this.siloId;
    }

    public ResourceKey<Level> dimension() {
        return this.dimension;
    }

    public Direction facing() {
        return this.facing;
    }

    public @Nullable UUID ownerPlayerId() {
        return this.ownerPlayerId;
    }

    public String ownerDisplayName() {
        return this.ownerDisplayName;
    }

    public List<DefenceAlly> allies() {
        return this.allies.entrySet().stream()
                .map(entry -> new DefenceAlly(entry.getKey(), entry.getValue()))
                .toList();
    }

    public DefenceOwnershipSnapshot ownership() {
        return new DefenceOwnershipSnapshot(this.ownerPlayerId, this.ownerDisplayName, allies());
    }

    public boolean claimOwnership(final ServerPlayer actor) {
        if (this.ownerPlayerId != null) return false;
        this.ownerPlayerId = actor.getUUID();
        this.ownerDisplayName = actor.getGameProfile().name();
        this.allies.clear();
        this.sync();
        return true;
    }

    public boolean unclaimOwnership(final ServerPlayer actor) {
        if (!actor.getUUID().equals(this.ownerPlayerId)) return false;
        this.ownerPlayerId = null;
        this.ownerDisplayName = "SERVER";
        this.allies.clear();
        this.sync();
        return true;
    }

    public boolean addAlly(final ServerPlayer actor, final UUID playerId, final String playerName) {
        if (!actor.getUUID().equals(this.ownerPlayerId)
                || playerId.equals(this.ownerPlayerId)
                || this.allies.containsKey(playerId)) return false;
        this.allies.put(playerId, playerName);
        this.sync();
        return true;
    }

    public boolean removeAlly(final ServerPlayer actor, final UUID playerId) {
        if (!actor.getUUID().equals(this.ownerPlayerId)
                || this.allies.remove(playerId) == null) return false;
        this.sync();
        return true;
    }

    public @Nullable DefenceAlly allyByName(final String playerName) {
        return allies().stream()
                .filter(ally -> ally.playerName().equalsIgnoreCase(playerName))
                .findFirst()
                .orElse(null);
    }

    public ItemStack missileStack() {
        return this.inventory.getItem(0);
    }

    public @Nullable TargetCoordinates storedTarget() {
        return this.storedTarget;
    }

    public MissileSiloState siloState() {
        return this.siloState;
    }

    public int reloadTicksRemaining() {
        return this.reloadTicksRemaining;
    }

    public int reloadTicksTotal() {
        return this.reloadTicksTotal;
    }

    public long reloadStartGameTime() {
        return this.reloadStartGameTime;
    }

    public int installedGuidanceTier() {
        return MissilePayloadItems.guidanceTier(
                this.reservedMissile.isEmpty() ? this.missileStack() : this.reservedMissile);
    }

    public long animationStartGameTime() {
        return this.animationStartGameTime;
    }

    public int leftGuidanceTier() {
        return this.leftGuidanceTier;
    }

    public int rightGuidanceTier() {
        return this.rightGuidanceTier;
    }

    public String lastError() {
        return this.lastError;
    }

    public @Nullable UUID pendingLaunchRequestId() {
        return this.pendingLaunchRequestId;
    }

    public ItemStack reservedMissile() {
        return this.reservedMissile;
    }

    public @Nullable UUID activeMissileId() {
        return this.activeMissileId;
    }

    public boolean teardownInProgress() {
        return this.teardownInProgress;
    }

    public void setTeardownInProgress(final boolean value) {
        this.teardownInProgress = value;
    }

    public void setStoredTarget(final @Nullable TargetCoordinates target) {
        this.storedTarget = target != null && target.isValid() ? target : null;
        if (!isTransient(this.siloState)) this.recalculateIdleState();
        this.sync();
    }

    public int insert(final ItemStack stack, final int amount) {
        return this.inventory.insert(stack, amount);
    }

    public boolean extractionAllowed() {
        return !this.teardownInProgress
                && this.siloState != MissileSiloState.PREPARING
                && this.siloState != MissileSiloState.LAUNCHING;
    }

    public @Nullable ItemStack reserveOne(final UUID requestId) {
        if (this.pendingLaunchRequestId != null || this.missileStack().isEmpty()) return null;
        this.reservedMissile = this.inventory.removeItem(0, 1);
        this.pendingLaunchRequestId = requestId;
        this.enterState(MissileSiloState.PREPARING);
        this.animationStartGameTime = this.level == null ? 0 : this.level.getGameTime();
        if (this.level instanceof ServerLevel server) {
            server.playSound(
                    null,
                    this.worldPosition,
                    com.andye.warmod.acoustics.ModSoundEvents.MISSILE_ENGINE_IGNITION_NEAR,
                    SoundSource.BLOCKS,
                    0.9F,
                    0.72F);
        }
        this.sync();
        return this.reservedMissile;
    }

    public void restoreReserved() {
        if (!this.reservedMissile.isEmpty()) {
            int moved = this.inventory.insert(this.reservedMissile, 1);
            if (moved == 0) {
                // A detached block entity must retain the reservation until a server world
                // exists to receive the overflow; clearing it here would lose ammunition.
                if (!(this.level instanceof ServerLevel)) return;
                net.minecraft.world.level.block.Block.popResource(
                        this.level, this.worldPosition, this.reservedMissile.copy());
            }
        }
        this.reservedMissile = ItemStack.EMPTY;
        this.pendingLaunchRequestId = null;
        this.finishTransientState();
        this.sync();
    }

    public void launchAccepted(final UUID missileId) {
        this.reservedMissile = ItemStack.EMPTY;
        this.pendingLaunchRequestId = null;
        this.activeMissileId = missileId;
        this.enterState(MissileSiloState.LAUNCHING);
        this.animationStartGameTime = this.level == null ? 0 : this.level.getGameTime();
        this.launchingTicksRemaining = MissileSiloConstants.LAUNCHING_STATE_TICKS;
        this.sync();
    }

    public void fail(final String reason) {
        this.lastError = reason == null ? "launch failed" : reason;
        this.restoreReserved();
    }

    private void enterState(final MissileSiloState state) {
        this.siloState = state;
    }

    private void finishTransientState() {
        this.siloState = MissileSiloState.EMPTY;
        this.recalculateIdleState();
    }

    private void recalculateIdleState() {
        this.siloState =
                this.missileStack().isEmpty()
                        ? MissileSiloState.EMPTY
                        : MissilePayloadItems.isInterceptor(this.missileStack())
                                        || this.storedTarget != null
                                ? MissileSiloState.READY
                                : MissileSiloState.NO_TARGET;
    }

    public void recalculateState() {
        if (!isTransient(this.siloState)) this.recalculateIdleState();
    }

    private static boolean isTransient(final MissileSiloState state) {
        return state == MissileSiloState.PREPARING
                || state == MissileSiloState.LAUNCHING
                || state == MissileSiloState.COOLDOWN
                || state == MissileSiloState.RELOADING
                || state == MissileSiloState.INVALID_STRUCTURE
                || state == MissileSiloState.ERROR;
    }

    private void beginReload(final ServerLevel level) {
        this.enterState(MissileSiloState.RELOADING);
        this.reloadTicksTotal = MissileSiloConstants.RELOAD_ANIMATION_TICKS;
        this.reloadTicksRemaining = this.reloadTicksTotal;
        this.reloadStartGameTime = level.getGameTime();
        this.sync();
        if (SharedConstants.IS_RUNNING_IN_IDE)
            WarMod.LOGGER.info("Silo {} entered reload animation", this.siloId);
    }

    private void inventoryChanged() {
        if (!isTransient(this.siloState)) this.recalculateIdleState();
        this.sync();
    }

    public void sync() {
        this.setChanged();
        if (this.level != null)
            this.level.sendBlockUpdated(
                    this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(final ValueOutput output) {
        super.saveAdditional(output);
        output.store("silo_id", UUIDUtil.CODEC, this.siloId);
        output.store("dimension", Level.RESOURCE_KEY_CODEC, this.dimension);
        output.putString("facing", this.facing.getSerializedName());
        output.storeNullable("owner_id", UUIDUtil.CODEC, this.ownerPlayerId);
        output.putString("owner_name", this.ownerDisplayName);
        output.store("allies", DefenceAlly.CODEC.listOf(), allies());
        output.store("missile", ItemStack.OPTIONAL_CODEC, this.missileStack());
        output.storeNullable("target", TargetCoordinates.CODEC, this.storedTarget);
        output.putString("state", this.siloState.name());
        output.putInt("previous_redstone_signal", this.previousRedstoneSignal);
        output.putBoolean("redstone_cycle_consumed", this.redstoneCycleConsumed);
        output.putBoolean("pending_redstone_launch", this.pendingRedstoneLaunch);
        output.putLong("animation_start", this.animationStartGameTime);
        output.putInt("launching_ticks", this.launchingTicksRemaining);
        output.putInt("cooldown_ticks", this.cooldownTicksRemaining);
        output.putInt("reload_ticks", this.reloadTicksRemaining);
        output.putInt("reload_total", this.reloadTicksTotal);
        output.putLong("reload_start_time", this.reloadStartGameTime);
        output.putInt("guidance_tier", this.installedGuidanceTier);
        output.putInt("guidance_left", this.leftGuidanceTier);
        output.putInt("guidance_right", this.rightGuidanceTier);
        output.storeNullable("pending_request", UUIDUtil.CODEC, this.pendingLaunchRequestId);
        output.store("reserved_missile", ItemStack.OPTIONAL_CODEC, this.reservedMissile);
        output.storeNullable("active_missile", UUIDUtil.CODEC, this.activeMissileId);
        output.putString("last_error", this.lastError);
    }

    @Override
    protected void loadAdditional(final ValueInput input) {
        super.loadAdditional(input);
        this.siloId = input.read("silo_id", UUIDUtil.CODEC).orElseGet(UUID::randomUUID);
        this.dimension = input.read("dimension", Level.RESOURCE_KEY_CODEC).orElse(Level.OVERWORLD);
        this.facing = Direction.byName(input.getStringOr("facing", "north"));
        if (this.facing == null || !this.facing.getAxis().isHorizontal())
            this.facing = Direction.NORTH;
        this.ownerPlayerId = input.read("owner_id", UUIDUtil.CODEC).orElse(null);
        this.ownerDisplayName = input.getStringOr("owner_name", "SERVER");
        this.allies.clear();
        for (DefenceAlly ally : input.read("allies", DefenceAlly.CODEC.listOf()).orElse(List.of())) {
            if (!ally.playerId().equals(this.ownerPlayerId))
                this.allies.putIfAbsent(ally.playerId(), ally.playerName());
        }
        ItemStack loaded = input.read("missile", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        this.inventory.setItem(
                0,
                MissilePayloadItems.isMissile(loaded)
                        ? loaded.copyWithCount(
                                Math.min(MissileSiloConstants.MAX_MISSILES, loaded.getCount()))
                        : ItemStack.EMPTY);
        this.storedTarget =
                input.read("target", TargetCoordinates.CODEC)
                        .filter(TargetCoordinates::isValid)
                        .orElse(null);
        try {
            this.siloState = MissileSiloState.valueOf(input.getStringOr("state", "EMPTY"));
        } catch (IllegalArgumentException ignored) {
            this.siloState = MissileSiloState.ERROR;
        }
        this.previousRedstoneSignal =
                Math.max(
                        0,
                        Math.min(
                                15,
                                input.getIntOr(
                                        "previous_redstone_signal",
                                        input.getBooleanOr("previously_powered", false) ? 15 : 0)));
        this.redstoneCycleConsumed =
                input.getBooleanOr("redstone_cycle_consumed", this.previousRedstoneSignal > 0);
        this.pendingRedstoneLaunch = input.getBooleanOr("pending_redstone_launch", false);
        this.launchingTicksRemaining = Math.max(0, input.getIntOr("launching_ticks", 0));
        this.cooldownTicksRemaining = Math.max(0, input.getIntOr("cooldown_ticks", 0));
        this.reloadTicksTotal =
                Math.max(
                        0,
                        Math.min(
                                MissileSiloConstants.RELOAD_ANIMATION_TICKS,
                                input.getIntOr(
                                        "reload_total",
                                        MissileSiloConstants.RELOAD_ANIMATION_TICKS)));
        this.reloadTicksRemaining =
                Math.max(0, Math.min(this.reloadTicksTotal, input.getIntOr("reload_ticks", 0)));
        this.reloadStartGameTime = Math.max(0L, input.getLongOr("reload_start_time", 0L));
        this.installedGuidanceTier = Math.max(0, Math.min(3, input.getIntOr("guidance_tier", 0)));
        this.leftGuidanceTier = Math.max(0, Math.min(3, input.getIntOr("guidance_left", 0)));
        this.rightGuidanceTier = Math.max(0, Math.min(3, input.getIntOr("guidance_right", 0)));
        this.pendingLaunchRequestId = input.read("pending_request", UUIDUtil.CODEC).orElse(null);
        ItemStack reserved =
                input.read("reserved_missile", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        this.reservedMissile =
                MissilePayloadItems.isMissile(reserved)
                        ? reserved.copyWithCount(1)
                        : ItemStack.EMPTY;
        this.activeMissileId = input.read("active_missile", UUIDUtil.CODEC).orElse(null);
        this.animationStartGameTime = input.getLongOr("animation_start", 0L);
        this.lastError = input.getStringOr("last_error", "");
        if (this.siloState == MissileSiloState.RELOADING
                && (this.missileStack().isEmpty() || this.reloadTicksRemaining <= 0)) {
            this.activeMissileId = null;
            this.finishTransientState();
        }
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(
            final net.minecraft.core.HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    @Override
    public int[] getSlotsForFace(final Direction direction) {
        return SLOT;
    }

    @Override
    public boolean canPlaceItemThroughFace(
            final int slot, final ItemStack stack, final @Nullable Direction direction) {
        return this.canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(
            final int slot, final ItemStack stack, final Direction direction) {
        return slot == 0 && extractionAllowed();
    }

    @Override
    public int getContainerSize() {
        return this.inventory.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        return this.inventory.isEmpty();
    }

    @Override
    public ItemStack getItem(final int slot) {
        return this.inventory.getItem(slot);
    }

    @Override
    public ItemStack removeItem(final int slot, final int count) {
        return extractionAllowed() ? this.inventory.removeItem(slot, count) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(final int slot) {
        return extractionAllowed() ? this.inventory.removeItemNoUpdate(slot) : ItemStack.EMPTY;
    }

    @Override
    public void setItem(final int slot, final ItemStack stack) {
        this.inventory.setItem(slot, stack);
    }

    @Override
    public int getMaxStackSize() {
        return MissileSiloConstants.MAX_MISSILES;
    }

    @Override
    public boolean stillValid(final Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public boolean canPlaceItem(final int slot, final ItemStack stack) {
        return this.inventory.canPlaceItem(slot, stack);
    }

    @Override
    public void clearContent() {
        this.inventory.clearContent();
    }
}
