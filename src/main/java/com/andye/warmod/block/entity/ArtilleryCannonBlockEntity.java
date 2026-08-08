package com.andye.warmod.block.entity;

import com.andye.warmod.artillery.ArtilleryLaunchService;
import com.andye.warmod.item.YieldWarheadItem;
import com.andye.warmod.item.component.TargetCoordinates;
import java.util.UUID;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class ArtilleryCannonBlockEntity extends BlockEntity implements WorldlyContainer {
    private static final int[] SLOTS = IntStream.range(0, 16).toArray();
    private final ArtilleryInventory inventory = new ArtilleryInventory(this::sync);
    private @Nullable UUID ownerPlayerId;
    private @Nullable TargetCoordinates storedTarget;
    private boolean previouslyPowered;
    private String lastStatus = "No target";
    private double lastAngleDegrees;
    private double lastRangeBlocks;
    private double lastApexY;
    private int lastFlightTicks;

    public ArtilleryCannonBlockEntity(final BlockPos pos, final BlockState state) {
        super(ModBlockEntities.ARTILLERY_CANNON, pos, state);
    }

    public void initialize(final @Nullable Player owner) {
        this.ownerPlayerId = owner == null ? null : owner.getUUID();
        sync();
    }

    public static void serverTick(final Level level, final BlockPos pos,
        final BlockState state, final ArtilleryCannonBlockEntity cannon) {
        if (!(level instanceof ServerLevel server)) return;
        boolean powered = server.hasNeighborSignal(pos);
        if (powered && !cannon.previouslyPowered) cannon.fire(server, null);
        if (powered != cannon.previouslyPowered) {
            cannon.previouslyPowered = powered;
            cannon.sync();
        }
    }

    public ArtilleryLaunchService.FireResult fire(final ServerLevel level,
        final @Nullable Player triggeringPlayer) {
        if (storedTarget == null || !storedTarget.isValid()
            || !storedTarget.dimension().equals(level.dimension())) {
            return fail("No valid same-dimension target");
        }
        int slot = inventory.firstLoadedSlot();
        if (slot < 0) return fail("Artillery has no warheads loaded");
        ItemStack ammunition = inventory.getItem(slot);
        if (!(ammunition.getItem() instanceof YieldWarheadItem warhead)) {
            return fail("Unsupported artillery ammunition");
        }

        Vec3 muzzle = new Vec3(worldPosition.getX() + 0.5,
            worldPosition.getY() + 1.35, worldPosition.getZ() + 0.5);
        UUID owner = triggeringPlayer == null ? ownerPlayerId : triggeringPlayer.getUUID();
        ArtilleryLaunchService.FireResult result = ArtilleryLaunchService.fire(level,
            owner, muzzle, storedTarget.position(), warhead.yield(), warhead.cluster());
        if (!result.accepted()) return fail(result.message());

        inventory.removeItem(slot, 1);
        lastStatus = warhead.cluster() ? "Cluster warhead fired" : "Fired";
        lastAngleDegrees = result.angleDegrees();
        lastRangeBlocks = result.rangeBlocks();
        lastApexY = result.apexY();
        lastFlightTicks = result.flightTicks();
        sync();
        return result;
    }

    private ArtilleryLaunchService.FireResult fail(final String message) {
        lastStatus = message;
        sync();
        return ArtilleryLaunchService.FireResult.failed(message);
    }

    public @Nullable TargetCoordinates storedTarget() { return storedTarget; }
    public void setStoredTarget(final @Nullable TargetCoordinates target) {
        this.storedTarget = target != null && target.isValid() ? target : null;
        this.lastStatus = this.storedTarget == null ? "No target" : "Target programmed";
        sync();
    }
    public String lastStatus() { return lastStatus; }
    public double lastAngleDegrees() { return lastAngleDegrees; }
    public double lastRangeBlocks() { return lastRangeBlocks; }
    public double lastApexY() { return lastApexY; }
    public int lastFlightTicks() { return lastFlightTicks; }
    public int roundsLoaded() { return inventory.countRounds(); }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(final ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("owner_id", UUIDUtil.CODEC, ownerPlayerId);
        output.storeNullable("target", TargetCoordinates.CODEC, storedTarget);
        output.putBoolean("previously_powered", previouslyPowered);
        output.putString("last_status", lastStatus);
        output.putDouble("last_angle", lastAngleDegrees);
        output.putDouble("last_range", lastRangeBlocks);
        output.putDouble("last_apex", lastApexY);
        output.putInt("last_flight_ticks", lastFlightTicks);
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            output.store("ammo_" + slot, ItemStack.OPTIONAL_CODEC, inventory.getItem(slot));
        }
    }

    @Override
    protected void loadAdditional(final ValueInput input) {
        super.loadAdditional(input);
        ownerPlayerId = input.read("owner_id", UUIDUtil.CODEC).orElse(null);
        storedTarget = input.read("target", TargetCoordinates.CODEC)
            .filter(TargetCoordinates::isValid).orElse(null);
        previouslyPowered = input.getBooleanOr("previously_powered", false);
        lastStatus = input.getStringOr("last_status", storedTarget == null ? "No target" : "Ready");
        lastAngleDegrees = input.getDoubleOr("last_angle", 0.0);
        lastRangeBlocks = input.getDoubleOr("last_range", 0.0);
        lastApexY = input.getDoubleOr("last_apex", 0.0);
        lastFlightTicks = Math.max(0, input.getIntOr("last_flight_ticks", 0));
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = input.read("ammo_" + slot, ItemStack.OPTIONAL_CODEC)
                .orElse(ItemStack.EMPTY);
            inventory.setItem(slot, stack);
        }
    }

    @Override public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    @Override public net.minecraft.nbt.CompoundTag getUpdateTag(
        final net.minecraft.core.HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override public int[] getSlotsForFace(final Direction direction) { return SLOTS; }
    @Override public boolean canPlaceItemThroughFace(final int slot, final ItemStack stack,
        final @Nullable Direction direction) { return canPlaceItem(slot, stack); }
    @Override public boolean canTakeItemThroughFace(final int slot, final ItemStack stack,
        final Direction direction) { return true; }
    @Override public int getContainerSize() { return inventory.getContainerSize(); }
    @Override public boolean isEmpty() { return inventory.isEmpty(); }
    @Override public ItemStack getItem(final int slot) { return inventory.getItem(slot); }
    @Override public ItemStack removeItem(final int slot, final int count) { return inventory.removeItem(slot, count); }
    @Override public ItemStack removeItemNoUpdate(final int slot) { return inventory.removeItemNoUpdate(slot); }
    @Override public void setItem(final int slot, final ItemStack stack) { inventory.setItem(slot, stack); }
    @Override public int getMaxStackSize() { return 1; }
    @Override public boolean stillValid(final Player player) { return Container.stillValidBlockEntity(this, player); }
    @Override public boolean canPlaceItem(final int slot, final ItemStack stack) { return inventory.canPlaceItem(slot, stack); }
    @Override public void clearContent() { inventory.clearContent(); }
}
