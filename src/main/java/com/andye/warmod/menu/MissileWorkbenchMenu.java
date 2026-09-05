package com.andye.warmod.menu;

import com.andye.warmod.block.entity.MissileWorkbenchBlockEntity;
import com.andye.warmod.silo.MissileAssembly;
import com.andye.warmod.silo.MissileWorkbenchPreview;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class MissileWorkbenchMenu extends AbstractContainerMenu {
    private final Container inventory;

    public MissileWorkbenchMenu(int id, Inventory player, ModMenus.ArtilleryOpeningData data) {
        this(
                id,
                player,
                player.player.level().getBlockEntity(data.position())
                                instanceof MissileWorkbenchBlockEntity bench
                        ? bench
                        : new SimpleContainer(4));
    }

    public MissileWorkbenchMenu(int id, Inventory player, Container inventory) {
        super(ModMenus.MISSILE_WORKBENCH, id);
        this.inventory = inventory;
        for (int i = 0; i < 4; i++) {
            final int slot = i;
            addSlot(
                    new Slot(inventory, i, new int[] {26, 62, 98, 152}[i], 41) {
                        @Override
                        public boolean mayPlace(ItemStack stack) {
                            return MissileAssembly.accepts(slot, stack);
                        }

                        @Override
                        public void onTake(final Player player, final ItemStack taken) {
                            super.onTake(player, taken);
                            if (slot == MissileWorkbenchPreview.OUTPUT_SLOT
                                    && inventory instanceof MissileWorkbenchBlockEntity bench)
                                bench.completePreviewExtraction();
                        }
                    });
        }
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(player, col + row * 9 + 9, 17 + col * 18, 109 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(player, col, 17 + col * 18, 167));
    }

    @Override
    public boolean stillValid(Player player) {
        return inventory.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        if (index == MissileWorkbenchPreview.OUTPUT_SLOT) {
            // moveItemStackTo may accept only part of a legacy persisted output.
            // Commit exactly what it accepted; a virtual preview has count one.
            ItemStack moving = stack.copy();
            if (!moveItemStackTo(moving, 4, slots.size(), true))
                return ItemStack.EMPTY;
            int moved = stack.getCount() - moving.getCount();
            if (moved <= 0
                    || inventory.removeItem(MissileWorkbenchPreview.OUTPUT_SLOT, moved).isEmpty())
                return ItemStack.EMPTY;
            if (inventory instanceof MissileWorkbenchBlockEntity bench)
                bench.completePreviewExtraction();
            slot.setChanged();
            return copy;
        }
        if (index < MissileWorkbenchPreview.OUTPUT_SLOT) {
            if (!moveItemStackTo(stack, 4, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, 3, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }
}
