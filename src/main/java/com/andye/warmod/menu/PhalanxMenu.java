package com.andye.warmod.menu;

import com.andye.warmod.block.entity.PhalanxBlockEntity;
import com.andye.warmod.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class PhalanxMenu extends AbstractContainerMenu {
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
            inventory.player.level()
                .getBlockEntity(data.centre())
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
        this(
            id,
            inventory,
            phalanx,
            phalanx.getBlockPos()
        );
    }

    private PhalanxMenu(
        final int id,
        final Inventory inventory,
        final @Nullable PhalanxBlockEntity phalanx,
        final BlockPos centre
    ) {
        super(
            ModMenus.PHALANX,
            id
        );

        this.centre =
            centre;

        turret =
            phalanx;

        var container =
            phalanx == null
                ? new SimpleContainer(2)
                : phalanx;

        checkContainerSize(
            container,
            2
        );

        addSlot(
            new Slot(
                container,
                0,
                44,
                46
            ) {
                @Override
                public boolean mayPlace(
                    final ItemStack stack
                ) {
                    return stack.is(
                        ModItems.ANTI_AIR_GUN_AMMO
                    );
                }
            }
        );

        addSlot(
            new Slot(
                container,
                1,
                116,
                46
            ) {
                @Override
                public boolean mayPlace(
                    final ItemStack stack
                ) {
                    return stack.is(
                        ModItems.ANTI_AIR_GUN_AMMO
                    );
                }
            }
        );

        addStandardInventorySlots(
            inventory,
            10,
            134
        );
    }

    @Override
    public boolean stillValid(
        final Player player
    ) {
        return turret != null
            && player.level()
                == turret.getLevel()
            && player.distanceToSqr(
                net.minecraft.world.phys.Vec3
                    .atCenterOf(centre)
            ) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(
        final Player player,
        final int index
    ) {
        Slot slot =
            getSlot(index);

        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack =
            slot.getItem();

        ItemStack copy =
            stack.copy();

        if (index < 2) {
            if (
                !moveItemStackTo(
                    stack,
                    2,
                    38,
                    true
                )
            ) {
                return ItemStack.EMPTY;
            }
        } else if (
            stack.is(
                ModItems.ANTI_AIR_GUN_AMMO
            )
        ) {
            if (
                !moveItemStackTo(
                    stack,
                    0,
                    2,
                    false
                )
            ) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(
                ItemStack.EMPTY
            );
        } else {
            slot.setChanged();
        }

        return copy;
    }

    public @Nullable PhalanxBlockEntity turret() {
        return turret;
    }
}
