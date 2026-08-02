package com.andye.warmod.block.entity;

import com.andye.warmod.silo.MissilePayloadItems;
import com.andye.warmod.silo.MissileSiloConstants;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class MissileSiloInventory implements Container {
    private ItemStack stack = ItemStack.EMPTY;
    private final Runnable changed;

    public MissileSiloInventory(final Runnable changed) {
        this.changed = changed;
    }

    @Override public int getContainerSize() { return 1; }
    @Override public boolean isEmpty() { return this.stack.isEmpty(); }
    @Override public ItemStack getItem(final int slot) { return slot == 0 ? this.stack : ItemStack.EMPTY; }

    @Override
    public ItemStack removeItem(final int slot, final int count) {
        if (slot != 0 || count <= 0 || this.stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack removed = this.stack.split(Math.min(count, this.stack.getCount()));
        if (this.stack.isEmpty()) this.stack = ItemStack.EMPTY;
        this.setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(final int slot) {
        if (slot != 0) return ItemStack.EMPTY;
        ItemStack result = this.stack;
        this.stack = ItemStack.EMPTY;
        return result;
    }

    @Override
    public void setItem(final int slot, final ItemStack incoming) {
        if (slot != 0) return;
        if (!incoming.isEmpty() && !MissilePayloadItems.isMissile(incoming)) {
            this.stack = ItemStack.EMPTY;
        } else {
            this.stack = incoming.isEmpty() ? ItemStack.EMPTY
                : incoming.copyWithCount(Math.min(MissileSiloConstants.MAX_MISSILES, incoming.getCount()));
        }
        this.setChanged();
    }

    public int insert(final ItemStack incoming, final int requested) {
        if (incoming.isEmpty() || requested <= 0 || !MissilePayloadItems.compatible(this.stack, incoming)) return 0;
        int room = MissileSiloConstants.MAX_MISSILES - this.stack.getCount();
        int moved = Math.min(Math.min(room, requested), incoming.getCount());
        if (moved <= 0) return 0;
        if (this.stack.isEmpty()) this.stack = incoming.copyWithCount(moved);
        else this.stack.grow(moved);
        this.setChanged();
        return moved;
    }

    @Override public int getMaxStackSize() { return MissileSiloConstants.MAX_MISSILES; }
    @Override public boolean stillValid(final Player player) { return true; }
    @Override public boolean canPlaceItem(final int slot, final ItemStack stack) {
        return slot == 0 && MissilePayloadItems.compatible(this.stack, stack)
            && this.stack.getCount() < MissileSiloConstants.MAX_MISSILES;
    }
    @Override public void clearContent() { this.stack = ItemStack.EMPTY; this.setChanged(); }
    @Override public void setChanged() { this.changed.run(); }
}
