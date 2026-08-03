package com.andye.warmod.radar.display.client;

import com.andye.warmod.radar.display.RadarDisplayOfflineReason;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class RadarDisplayRenderState
    extends BlockEntityRenderState {
    public @Nullable UUID displayId;
    public BlockPos controller = BlockPos.ZERO;
    public Direction facing = Direction.NORTH;

    public int size;
    public int displayRadius;
    public int sweepPeriod = 80;
    public int redstoneSignal;

    public boolean controllerPanel;
    public boolean structureValid;
    public boolean online;
    public boolean syncing;

    public RadarDisplayOfflineReason offlineReason =
        RadarDisplayOfflineReason.UNLINKED;

    public Vec3 radarCentre = Vec3.ZERO;

    public double warningRadius;
    public double fireRadius;
    public double serverNow;
    public double sweepAngleDegrees;

    public List<RadarDisplayRenderObservation> observations =
        List.of();
}
