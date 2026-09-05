package com.andye.warmod.silo.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.Direction;

/** Immutable-at-submit item model snapshots for the visible assembly cradle. */
public final class MissileWorkbenchRenderState extends BlockEntityRenderState {
    public final ItemStackRenderState body = new ItemStackRenderState();
    public final ItemStackRenderState payload = new ItemStackRenderState();
    public Direction facing = Direction.NORTH;
}
