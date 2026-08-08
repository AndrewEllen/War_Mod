package com.andye.warmod.menu;

import com.andye.warmod.block.entity.ArtilleryCannonBlockEntity;
import com.andye.warmod.item.YieldWarheadItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class ArtilleryCannonMenu extends AbstractContainerMenu {
    private static final int AMMO_SLOTS = 16;
    private static final int PLAYER_START = AMMO_SLOTS;
    private static final int PLAYER_END = PLAYER_START + 36;
    private final BlockPos cannonPos;
    private final @Nullable ArtilleryCannonBlockEntity cannon;

    public ArtilleryCannonMenu(final int id, final Inventory inventory,
        final ModMenus.ArtilleryOpeningData data) {
        this(id, inventory, resolve(inventory, data), data.cannonPos());
    }

    public ArtilleryCannonMenu(final int id, final Inventory inventory,
        final ArtilleryCannonBlockEntity cannon) {
        this(id, inventory, cannon, cannon.getBlockPos());
    }

    private ArtilleryCannonMenu(final int id, final Inventory playerInventory,
        final @Nullable ArtilleryCannonBlockEntity cannon, final BlockPos cannonPos) {
        super(ModMenus.ARTILLERY_CANNON, id);
        this.cannon = cannon;
        this.cannonPos = cannonPos;
        var container = cannon == null ? new SimpleContainer(AMMO_SLOTS) : cannon;
        checkContainerSize(container, AMMO_SLOTS);
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                int slot = row * 4 + col;
                addSlot(new Slot(container, slot, 18 + col * 18, 26 + row * 18) {
                    @Override public boolean mayPlace(final ItemStack stack) {
                        return stack.getItem() instanceof YieldWarheadItem
                            && container.canPlaceItem(slot, stack);
                    }
                    @Override public int getMaxStackSize() { return 1; }
                });
            }
        }
        addStandardInventorySlots(playerInventory, 18, 112);
    }

    private static @Nullable ArtilleryCannonBlockEntity resolve(final Inventory inventory,
        final ModMenus.ArtilleryOpeningData data) {
        return inventory.player.level().getBlockEntity(data.cannonPos())
            instanceof ArtilleryCannonBlockEntity cannon ? cannon : null;
    }

    public BlockPos cannonPos() { return cannonPos; }
    public @Nullable ArtilleryCannonBlockEntity cannon() { return cannon; }

    @Override
    public boolean stillValid(final Player player) {
        if (cannon == null || player.level() != cannon.getLevel()
            || player.distanceToSqr(cannonPos.getX() + 0.5, cannonPos.getY() + 0.5,
                cannonPos.getZ() + 0.5) > 64.0) return false;
        return !(player.level() instanceof ServerLevel)
            || player.level().getBlockEntity(cannonPos) == cannon;
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        Slot slot = getSlot(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        if (index < AMMO_SLOTS) {
            if (!moveItemStackTo(source, PLAYER_START, PLAYER_END, true)) return ItemStack.EMPTY;
        } else {
            if (!(source.getItem() instanceof YieldWarheadItem)
                || !moveItemStackTo(source, 0, AMMO_SLOTS, false)) return ItemStack.EMPTY;
        }
        if (source.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }
}
