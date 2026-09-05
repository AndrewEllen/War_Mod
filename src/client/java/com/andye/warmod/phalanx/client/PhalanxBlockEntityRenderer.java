package com.andye.warmod.phalanx.client;

import com.andye.warmod.block.entity.PhalanxBlockEntity;
import com.andye.warmod.client.model.BlockbenchModelRenderType;
import com.andye.warmod.phalanx.PhalanxGunStatus;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class PhalanxBlockEntityRenderer implements BlockEntityRenderer<PhalanxBlockEntity, PhalanxRenderState> {
    private static final double HEAT_UP_TICKS = 70.0;
    private static final double COOL_DOWN_TICKS = 150.0;
    private final Map<UUID, ThermalState> thermalStates = new HashMap<>();
    public PhalanxBlockEntityRenderer(final BlockEntityRendererProvider.Context context) { }
    @Override public PhalanxRenderState createRenderState() { return new PhalanxRenderState(); }
    @Override public void extractRenderState(final PhalanxBlockEntity blockEntity, final PhalanxRenderState state, final float partialTick, final Vec3 camera, final ModelFeatureRenderer.@Nullable CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, camera, overlay);
        double clientTime = blockEntity.getLevel() == null ? 0.0 : blockEntity.getLevel().getGameTime() + partialTick;
        ClientPhalanxStateManager.View network = ClientPhalanxStateManager.INSTANCE.view(blockEntity.turretId(), clientTime);
        if (network != null) {
            state.yaw=network.yaw(); state.pitch=network.pitch(); state.barrelSpeed=network.barrelSpeed(); state.barrelAngle=network.barrelAngle(); state.bloom=network.bloom(); state.rounds=network.rounds(); state.firing=network.muzzleFlash(); state.enabled=network.enabled();
        } else {
            state.yaw=blockEntity.yaw(); state.pitch=blockEntity.pitch(); state.barrelSpeed=blockEntity.barrelSpin(); state.barrelAngle=(float)(clientTime*72.0*blockEntity.barrelSpin())%360.0F; state.bloom=blockEntity.bloom(); state.rounds=blockEntity.rounds(); state.firing=blockEntity.status()==PhalanxGunStatus.FIRING; state.enabled=blockEntity.enabled();
        }
        state.barrelHeat = updateHeat(blockEntity.turretId(), clientTime, state.firing);
    }
    @Override public void submit(final PhalanxRenderState state, final PoseStack poseStack, final SubmitNodeCollector collector, final CameraRenderState camera) {
        collector.submitCustomGeometry(poseStack, BlockbenchModelRenderType.PHALANX_SOLID, (pose, buffer) -> PhalanxTurretMesh.renderStaticBase(pose, buffer, state.lightCoords, state.enabled));
        poseStack.pushPose(); poseStack.translate(0.5, 1.02, 0.5); poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
        collector.submitCustomGeometry(poseStack, BlockbenchModelRenderType.PHALANX_SOLID, (pose, buffer) -> PhalanxTurretMesh.renderYawHousing(pose, buffer, state.lightCoords));
        poseStack.translate(0.0, 0.24, 0.0); poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch));
        collector.submitCustomGeometry(poseStack, BlockbenchModelRenderType.PHALANX_SOLID, (pose, buffer) -> { PhalanxTurretMesh.renderCradle(pose, buffer, state.lightCoords); PhalanxTurretMesh.renderBarrels(pose, buffer, state.lightCoords, state.barrelAngle, state.barrelHeat); if (state.firing) PhalanxTurretMesh.renderMuzzleFlash(pose, buffer, state.lightCoords); });
        poseStack.popPose();
    }
    @Override public int getViewDistance() { return 256; }
    @Override public boolean shouldRenderOffScreen() { return true; }

    private float updateHeat(final UUID turretId, final double now, final boolean firing) {
        ThermalState previous = thermalStates.get(turretId);
        double elapsed = previous == null ? 0.0 : Math.min(4.0, Math.max(0.0, now - previous.time()));
        float previousHeat = previous == null ? 0.0F : previous.heat();
        float heat = firing
                ? Math.min(1.0F, previousHeat + (float)(elapsed / HEAT_UP_TICKS))
                : Math.max(0.0F, previousHeat - (float)(elapsed / COOL_DOWN_TICKS));
        thermalStates.put(turretId, new ThermalState(now, heat));
        return heat;
    }

    private record ThermalState(double time, float heat) { }
}
