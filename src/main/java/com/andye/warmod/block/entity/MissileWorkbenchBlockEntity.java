package com.andye.warmod.block.entity;

import com.andye.warmod.silo.MissileAssembly;
import com.andye.warmod.silo.MissileWorkbenchPreview;
import com.andye.warmod.block.MissileWorkbenchBlock;
import com.andye.warmod.block.MissileWorkbenchPart;
import com.andye.warmod.block.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jspecify.annotations.Nullable;

public final class MissileWorkbenchBlockEntity extends BlockEntity implements WorldlyContainer {
    /**
     * Legacy one-block benches get exactly one opportunity to grow their missing
     * companion. Persisting the attempt prevents a manually removed half from
     * coming back after later chunk loads.
     */
    private boolean companionMigrationChecked;
    private @Nullable PreviewExtraction pendingPreviewExtraction;
    private long pendingPreviewExtractionTick = Long.MIN_VALUE;
    private final SimpleContainer inventory =
            new SimpleContainer(4) {
                @Override
                public void setChanged() {
                    super.setChanged();
                    MissileWorkbenchBlockEntity.this.setChanged();
                }
            };

    public MissileWorkbenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MISSILE_WORKBENCH, pos, state);
    }

    public static void serverTick(
            Level level, BlockPos pos, BlockState state, MissileWorkbenchBlockEntity bench) {
        bench.migrateLegacyCompanion(level, pos, state);
        if (bench.pendingPreviewExtractionTick < level.getGameTime())
            bench.completePreviewExtraction();
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        // Components are typed, so an automation face is not a meaningful part
        // of the assembly contract. Expose every input on every face and let
        // canPlaceItem decide whether the supplied body, chip, or head fits.
        // The finished missile is deliberately the only extractable slot.
        return new int[] {0, 1, 2, 3};
    }

    private void migrateLegacyCompanion(
            final Level level, final BlockPos pos, final BlockState state) {
        if (companionMigrationChecked || level.isClientSide()
                || !state.is(ModBlocks.MISSILE_WORKBENCH)
                || state.getValue(MissileWorkbenchBlock.PART) != MissileWorkbenchPart.LEFT) return;

        // Mark before changing the world so both a blocked legacy position and
        // a successfully migrated bench remain stable across all later loads.
        companionMigrationChecked = true;
        BlockPos companion = pos.relative(state.getValue(MissileWorkbenchBlock.FACING).getClockWise());
        if (!level.isOutsideBuildHeight(companion)
                && level.getBlockEntity(companion) == null
                && level.getBlockState(companion).canBeReplaced()) {
            level.setBlock(companion, state.setValue(MissileWorkbenchBlock.PART,
                    MissileWorkbenchPart.RIGHT), Block.UPDATE_ALL);
        }
        setChanged();
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot < 3 && canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot == 3;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return MissileAssembly.accepts(slot, stack);
    }

    @Override
    public int getContainerSize() {
        return 4;
    }

    @Override
    public boolean isEmpty() {
        return inventory.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot == MissileWorkbenchPreview.OUTPUT_SLOT
                ? MissileWorkbenchPreview.preview(inventory)
                : inventory.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = slot == MissileWorkbenchPreview.OUTPUT_SLOT
                ? extractOutput(amount)
                : inventory.removeItem(slot, amount);
        if (slot != MissileWorkbenchPreview.OUTPUT_SLOT && !removed.isEmpty())
            completePreviewExtraction();
        if (!removed.isEmpty()) sync();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = slot == MissileWorkbenchPreview.OUTPUT_SLOT
                ? extractOutput(getItem(slot).getCount())
                : inventory.removeItemNoUpdate(slot);
        if (slot != MissileWorkbenchPreview.OUTPUT_SLOT && !removed.isEmpty())
            completePreviewExtraction();
        if (!removed.isEmpty()) sync();
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        // Generic container adapters restore a simulated extraction through
        // setItem. Treat that exact virtual result as a rollback, rather than
        // serialising it as a new legacy output and losing the component set.
        if (slot == MissileWorkbenchPreview.OUTPUT_SLOT
                && !stack.isEmpty()
                && inventory.getItem(MissileWorkbenchPreview.OUTPUT_SLOT).isEmpty()
                && rollbackPreviewExtraction(stack)) return;
        inventory.setItem(slot, stack);
        completePreviewExtraction();
        sync();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        inventory.clearContent();
        completePreviewExtraction();
        sync();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        // Breakage drops the retained input components. It must not silently
        // turn a displayed preview into a missile, because no output was taken.
        if (level != null) net.minecraft.world.Containers.dropContents(level, pos, inventory);
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("companion_migration_checked", companionMigrationChecked);
        for (int i = 0; i < 4; i++) output.store("slot_" + i, ItemStack.OPTIONAL_CODEC, inventory.getItem(i));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        companionMigrationChecked = input.getBooleanOr("companion_migration_checked", false);
        completePreviewExtraction();
        for (int i = 0; i < 4; i++)
            inventory.setItem(
                    i, input.read("slot_" + i, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /**
     * Item pipes remove first and may have to restore a rejected transfer. A
     * virtual output must roll back to its original components, never become a
     * newly stored legacy missile when the destination declines it.
     */
    public boolean rollbackPreviewExtraction(final ItemStack rejectedOutput) {
        PreviewExtraction pending = pendingPreviewExtraction;
        if (pending == null || !ItemStack.isSameItemSameComponents(
                pending.output(), rejectedOutput) || !canRestore(pending)) return false;
        restoreSlot(MissileWorkbenchPreview.BODY_SLOT, pending.body());
        restoreSlot(MissileWorkbenchPreview.CHIP_SLOT, pending.chip());
        restoreSlot(MissileWorkbenchPreview.PAYLOAD_SLOT, pending.payload());
        pendingPreviewExtraction = null;
        pendingPreviewExtractionTick = Long.MIN_VALUE;
        sync();
        return true;
    }

    /** Finalises a successful menu, hopper or pipe take after its caller commits. */
    public void completePreviewExtraction() {
        pendingPreviewExtraction = null;
        pendingPreviewExtractionTick = Long.MIN_VALUE;
    }

    private ItemStack extractOutput(final int amount) {
        if (amount <= 0) return ItemStack.EMPTY;
        if (!inventory.getItem(MissileWorkbenchPreview.OUTPUT_SLOT).isEmpty()) {
            completePreviewExtraction();
            return MissileWorkbenchPreview.extract(inventory, amount);
        }
        ItemStack body = inventory.getItem(MissileWorkbenchPreview.BODY_SLOT).copyWithCount(1);
        ItemStack chip = inventory.getItem(MissileWorkbenchPreview.CHIP_SLOT).copyWithCount(1);
        ItemStack payload = inventory.getItem(MissileWorkbenchPreview.PAYLOAD_SLOT).copyWithCount(1);
        ItemStack output = MissileWorkbenchPreview.extract(inventory, amount);
        pendingPreviewExtraction = output.isEmpty()
                ? null : new PreviewExtraction(body, chip, payload, output.copy());
        pendingPreviewExtractionTick = pendingPreviewExtraction == null || level == null
                ? Long.MIN_VALUE : level.getGameTime();
        return output;
    }

    private boolean canRestore(final PreviewExtraction pending) {
        return canRestoreSlot(MissileWorkbenchPreview.BODY_SLOT, pending.body())
                && canRestoreSlot(MissileWorkbenchPreview.CHIP_SLOT, pending.chip())
                && canRestoreSlot(MissileWorkbenchPreview.PAYLOAD_SLOT, pending.payload());
    }

    private boolean canRestoreSlot(final int slot, final ItemStack restore) {
        ItemStack current = inventory.getItem(slot);
        return current.isEmpty() || (ItemStack.isSameItemSameComponents(current, restore)
                && current.getCount() + restore.getCount() <= current.getMaxStackSize());
    }

    private void restoreSlot(final int slot, final ItemStack restore) {
        ItemStack current = inventory.getItem(slot);
        if (current.isEmpty()) inventory.setItem(slot, restore.copy());
        else {
            ItemStack combined = current.copy();
            combined.grow(restore.getCount());
            inventory.setItem(slot, combined);
        }
    }

    private record PreviewExtraction(
            ItemStack body, ItemStack chip, ItemStack payload, ItemStack output) { }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(
            final net.minecraft.core.HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }
}
