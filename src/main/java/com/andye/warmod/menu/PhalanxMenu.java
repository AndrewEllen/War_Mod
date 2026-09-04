package com.andye.warmod.menu;

import com.andye.warmod.block.entity.PhalanxBlockEntity;
import com.andye.warmod.item.ModItems;
import com.andye.warmod.phalanx.PhalanxConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class PhalanxMenu extends AbstractContainerMenu {
    private static final int TURRET_SLOT_COUNT =
        PhalanxConstants.AMMO_SLOT_COUNT;

    private final BlockPos centre;
    private final @Nullable PhalanxBlockEntity turret;

    public PhalanxMenu(
        final int id,
        final Inventory inventory,
        final ModMenus.PhalanxOpeningData data
    ) {
        this(
            id,
            inventory,
            inventory.player.level().getBlockEntity(data.centre())
                    instanceof PhalanxBlockEntity phalanx
                ? phalanx
                : null,
            data.centre()
        );
    }

    public PhalanxMenu(
        final int id,
        final Inventory inventory,
        final PhalanxBlockEntity phalanx
    ) {
        this(id, inventory, phalanx, phalanx.getBlockPos());
    }

    private PhalanxMenu(
        final int id,
        final Inventory inventory,
        final @Nullable PhalanxBlockEntity phalanx,
        final BlockPos centre
    ) {
        super(ModMenus.PHALANX, id);

        this.centre = centre;
        turret = phalanx;

        var container = phalanx == null
            ? new SimpleContainer(TURRET_SLOT_COUNT)
            : phalanx;

        checkContainerSize(container, TURRET_SLOT_COUNT);

        for (int slot = 0; slot < TURRET_SLOT_COUNT; slot++) {
            int column = slot % 4;
            int row = slot / 4;

            addSlot(new Slot(
                container,
                slot,
                66 + column * 24,
                44 + row * 22
            ) {
                @Override
                public boolean mayPlace(final ItemStack stack) {
                    return stack.is(ModItems.ANTI_AIR_GUN_AMMO);
                }
            });
        }

        addStandardInventorySlots(inventory, 29, 166);
    }

    @Override
    public boolean stillValid(final Player player) {
        return turret != null
            && player.level() == turret.getLevel()
            && player.distanceToSqr(
                net.minecraft.world.phys.Vec3.atCenterOf(centre)
            ) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(
        final Player player,
        final int index
    ) {
        Slot slot = getSlot(index);

        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int playerEnd = TURRET_SLOT_COUNT + 36;

        if (index < TURRET_SLOT_COUNT) {
            if (!moveItemStackTo(
                stack,
                TURRET_SLOT_COUNT,
                playerEnd,
                true
            )) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModItems.ANTI_AIR_GUN_AMMO)) {
            if (!moveItemStackTo(
                stack,
                0,
                TURRET_SLOT_COUNT,
                false
            )) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return copy;
    }

    public @Nullable PhalanxBlockEntity turret() {
        return turret;
    }

    public BlockPos centre() {
        return centre;
    }

    public java.util.UUID turretId() {
        return turret == null ? new java.util.UUID(0L, 0L) : turret.turretId();
    }
}
