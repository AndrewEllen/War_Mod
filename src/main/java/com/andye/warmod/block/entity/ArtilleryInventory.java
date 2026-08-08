package com.andye.warmod.block.entity;

import com.andye.warmod.artillery.ArtilleryConstants;
import com.andye.warmod.item.YieldWarheadItem;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Sixteen one-round cells so the cannon capacity is exactly sixteen warheads. */
public final class ArtilleryInventory implements Container {
    private final NonNullList<ItemStack> items = NonNullList.withSize(
        ArtilleryConstants.AMMUNITION_SLOTS, ItemStack.EMPTY);
    private final Runnable changed;

    public ArtilleryInventory(final Runnable changed) { this.changed = changed; }

    @Override public int getContainerSize() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(final int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }
    @Override public ItemStack removeItem(final int slot, final int count) {
        if (slot < 0 || slot >= items.size() || count <= 0) return ItemStack.EMPTY;
        ItemStack stack = items.get(slot);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack removed = stack.split(Math.min(1, count));
        if (stack.isEmpty()) items.set(slot, ItemStack.EMPTY);
        setChanged();
        return removed;
    }
    @Override public ItemStack removeItemNoUpdate(final int slot) {
        if (slot < 0 || slot >= items.size()) return ItemStack.EMPTY;
        ItemStack result = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        return result;
    }
    @Override public void setItem(final int slot, final ItemStack stack) {
        if (slot < 0 || slot >= items.size()) return;
        items.set(slot, stack.getItem() instanceof YieldWarheadItem
            ? stack.copyWithCount(Math.min(1, stack.getCount())) : ItemStack.EMPTY);
        setChanged();
    }
    @Override public int getMaxStackSize() { return 1; }
    @Override public boolean stillValid(final Player player) { return true; }
    @Override public boolean canPlaceItem(final int slot, final ItemStack stack) {
        return slot >= 0 && slot < items.size() && items.get(slot).isEmpty()
            && stack.getItem() instanceof YieldWarheadItem;
    }
    @Override public void clearContent() {
        for (int i = 0; i < items.size(); i++) items.set(i, ItemStack.EMPTY);
        setChanged();
    }
    @Override public void setChanged() { changed.run(); }

    public int firstLoadedSlot() {
        for (int i = 0; i < items.size(); i++) if (!items.get(i).isEmpty()) return i;
        return -1;
    }

    public int countRounds() {
        int count = 0;
        for (ItemStack stack : items) if (!stack.isEmpty()) count++;
        return count;
    }
}
