package com.andye.warmod.menu;

import com.andye.warmod.artillery.ArtilleryPayloadItems;
import com.andye.warmod.block.entity.ArtilleryCannonBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class ArtilleryCannonMenu extends AbstractContainerMenu {
    private final BlockPos position;
    private final @Nullable ArtilleryCannonBlockEntity cannon;
    public ArtilleryCannonMenu(final int id, final Inventory inventory, final ModMenus.ArtilleryOpeningData data) { this(id, inventory, inventory.player.level().getBlockEntity(data.position()) instanceof ArtilleryCannonBlockEntity value ? value : null, data.position()); }
    public ArtilleryCannonMenu(final int id, final Inventory inventory, final ArtilleryCannonBlockEntity cannon) { this(id, inventory, cannon, cannon.getBlockPos()); }
    private ArtilleryCannonMenu(final int id, final Inventory inventory, final @Nullable ArtilleryCannonBlockEntity cannon, final BlockPos position) {
        super(ModMenus.ARTILLERY_CANNON, id); this.position = position; this.cannon = cannon;
        var container = cannon == null ? new SimpleContainer(1) : cannon;
        addSlot(new Slot(container, 0, 234, 54) { @Override public boolean mayPlace(final ItemStack stack) { return ArtilleryPayloadItems.isWarhead(stack) && container.canPlaceItem(0, stack); } @Override public int getMaxStackSize() { return 16; } });
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column + row * 9 + 9, 87 + column * 18, 218 + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 87 + column * 18, 276));
    }
    public BlockPos position() { return position; } public @Nullable ArtilleryCannonBlockEntity cannon() { return cannon; }
    @Override public boolean stillValid(final Player player) { return cannon != null && cannon.stillValid(player); }
    @Override public ItemStack quickMoveStack(final Player player, final int index) { Slot slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY; ItemStack stack = slot.getItem(), copy = stack.copy(); if (index == 0) { if (!moveItemStackTo(stack, 1, slots.size(), true)) return ItemStack.EMPTY; } else if (!ArtilleryPayloadItems.isWarhead(stack) || !moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY; if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged(); return copy; }
}
