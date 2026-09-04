package com.andye.warmod.block.entity;

import com.andye.warmod.silo.MissileAssembly;
import com.andye.warmod.block.MissileWorkbenchBlock;
import com.andye.warmod.block.MissileWorkbenchPart;
import com.andye.warmod.block.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
        if (level.getGameTime() % 10 != 0) return;
        ItemStack result =
                MissileAssembly.assemble(bench.getItem(0), bench.getItem(1), bench.getItem(2));
        ItemStack output = bench.getItem(3);
        if (result.isEmpty()
                || (!output.isEmpty()
                        && (!ItemStack.isSameItemSameComponents(output, result)
                                || output.getCount() >= output.getMaxStackSize()))) return;
        // Validate the entire transaction before consuming any component.
        for (int slot = 0; slot < 3; slot++) bench.inventory.removeItem(slot, 1);
        if (output.isEmpty()) bench.inventory.setItem(3, result);
        else {
            output.grow(1);
            bench.setChanged();
        }
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
        return inventory.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return inventory.removeItem(slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return inventory.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        inventory.setItem(slot, stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        inventory.clearContent();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) net.minecraft.world.Containers.dropContents(level, pos, this);
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("companion_migration_checked", companionMigrationChecked);
        for (int i = 0; i < 4; i++) output.store("slot_" + i, ItemStack.OPTIONAL_CODEC, getItem(i));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        companionMigrationChecked = input.getBooleanOr("companion_migration_checked", false);
        for (int i = 0; i < 4; i++)
            inventory.setItem(
                    i, input.read("slot_" + i, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
    }
}
