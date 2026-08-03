package com.andye.warmod.phalanx.client;

import com.andye.warmod.block.entity.PhalanxBlockEntity;
import com.andye.warmod.phalanx.PhalanxGunStatus;
import com.andye.warmod.warhead.client.render.WarheadRenderPipelines;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class PhalanxBlockEntityRenderer implements BlockEntityRenderer<PhalanxBlockEntity, PhalanxRenderState> {
    public PhalanxBlockEntityRenderer(final BlockEntityRendererProvider.Context context) { }
    @Override public PhalanxRenderState createRenderState() { return new PhalanxRenderState(); }
    @Override public void extractRenderState(final PhalanxBlockEntity blockEntity, final PhalanxRenderState state, final float partialTick, final Vec3 camera, final ModelFeatureRenderer.@Nullable CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, camera, overlay);
        double clientTime = blockEntity.getLevel() == null ? 0.0 : blockEntity.getLevel().getGameTime() + partialTick;
        ClientPhalanxStateManager.View network = ClientPhalanxStateManager.INSTANCE.view(blockEntity.turretId(), clientTime);
        if (network != null) { state.yaw=network.yaw(); state.pitch=network.pitch(); state.barrelSpeed=network.barrelSpeed(); state.barrelAngle=network.barrelAngle(); state.bloom=network.bloom(); state.rounds=network.rounds(); state.firing=network.muzzleFlash(); state.enabled=network.enabled(); return; }
        state.yaw=blockEntity.yaw(); state.pitch=blockEntity.pitch(); state.barrelSpeed=blockEntity.barrelSpin(); state.barrelAngle=(float)(clientTime*72.0*blockEntity.barrelSpin())%360.0F; state.bloom=blockEntity.bloom(); state.rounds=blockEntity.rounds(); state.firing=blockEntity.status()==PhalanxGunStatus.FIRING; state.enabled=blockEntity.enabled();
    }
    @Override public void submit(final PhalanxRenderState state, final PoseStack poseStack, final SubmitNodeCollector collector, final CameraRenderState camera) {
        collector.submitCustomGeometry(poseStack, WarheadRenderPipelines.PROJECTILE, (pose, buffer) -> PhalanxTurretMesh.renderStaticBase(pose, buffer, state.lightCoords, state.enabled));
        poseStack.pushPose(); poseStack.translate(0.5, 1.02, 0.5); poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
        collector.submitCustomGeometry(poseStack, WarheadRenderPipelines.PROJECTILE, (pose, buffer) -> PhalanxTurretMesh.renderYawHousing(pose, buffer, state.lightCoords));
        poseStack.translate(0.0, 0.24, 0.0); poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch));
        collector.submitCustomGeometry(poseStack, WarheadRenderPipelines.PROJECTILE, (pose, buffer) -> { PhalanxTurretMesh.renderCradle(pose, buffer, state.lightCoords); PhalanxTurretMesh.renderBarrels(pose, buffer, state.lightCoords, state.barrelAngle); if (state.firing) PhalanxTurretMesh.renderMuzzleFlash(pose, buffer, state.lightCoords); });
        poseStack.popPose();
    }
    @Override public int getViewDistance() { return 256; }
    @Override public boolean shouldRenderOffScreen() { return true; }
}