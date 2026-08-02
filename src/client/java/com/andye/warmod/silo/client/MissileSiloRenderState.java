package com.andye.warmod.silo.client;

import com.andye.warmod.block.MissileSiloState;
import com.andye.warmod.warhead.WarheadPayloadType;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

public final class MissileSiloRenderState extends BlockEntityRenderState {
    public @Nullable WarheadPayloadType payloadType;
    public int availableCount;
    public MissileSiloState siloState = MissileSiloState.EMPTY;
    public Direction facing = Direction.NORTH;
    public boolean visible;
    public double reloadOffsetY;
}
