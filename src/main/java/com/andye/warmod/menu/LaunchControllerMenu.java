package com.andye.warmod.menu;

import com.andye.warmod.block.entity.LaunchControllerBlockEntity;
import com.andye.warmod.item.component.LinkedSilo;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class LaunchControllerMenu extends AbstractContainerMenu {
    private final BlockPos centre;
    private final UUID controllerId;
    private final @Nullable LaunchControllerBlockEntity controller;

    public LaunchControllerMenu(
        final int id,
        final Inventory inventory,
        final ModMenus.LaunchControllerOpeningData data
    ) {
        this(
            id,
            inventory,
            inventory.player.level().getBlockEntity(data.centre())
                    instanceof LaunchControllerBlockEntity value
                ? value
                : null,
            data.centre(),
            data.controllerId()
        );
    }

    public LaunchControllerMenu(
        final int id,
        final Inventory inventory,
        final LaunchControllerBlockEntity controller
    ) {
        this(
            id,
            inventory,
            controller,
            controller.getBlockPos(),
            controller.controllerId()
        );
    }

    private LaunchControllerMenu(
        final int id,
        final Inventory inventory,
        final @Nullable LaunchControllerBlockEntity controller,
        final BlockPos centre,
        final UUID controllerId
    ) {
        super(ModMenus.LAUNCH_CONTROLLER, id);
        this.centre = centre;
        this.controllerId = controllerId;
        this.controller = controller;
    }

    public BlockPos centre() {
        return centre;
    }

    public UUID controllerId() {
        return controllerId;
    }

    public @Nullable LaunchControllerBlockEntity controller() {
        return controller;
    }

    public List<LinkedSilo> linkedSilos() {
        return controller == null ? List.of() : controller.linkedSilos();
    }

    public String lastBatchSummary() {
        return controller == null
            ? "Controller unavailable"
            : controller.lastBatchSummary();
    }

    @Override
    public boolean stillValid(final Player player) {
        return controller != null
            && !controller.isRemoved()
            && player.level() == controller.getLevel()
            && player.level().getBlockEntity(centre) == controller
            && controller.controllerId().equals(controllerId)
            && player.distanceToSqr(Vec3.atCenterOf(centre)) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        return ItemStack.EMPTY;
    }
}
