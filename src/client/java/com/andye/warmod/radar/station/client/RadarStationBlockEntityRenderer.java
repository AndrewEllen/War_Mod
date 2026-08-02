package com.andye.warmod.radar.station.client;

import com.andye.warmod.block.entity.RadarStationBlockEntity;
import com.andye.warmod.radar.station.RadarSweepMath;
import com.andye.warmod.warhead.client.render.WarheadRenderPipelines;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class RadarStationBlockEntityRenderer
    implements BlockEntityRenderer<RadarStationBlockEntity, RadarStationRenderState> {
    public RadarStationBlockEntityRenderer(final BlockEntityRendererProvider.Context context) { }

    @Override
    public RadarStationRenderState createRenderState() {
        return new RadarStationRenderState();
    }

    @Override
    public void extractRenderState(final RadarStationBlockEntity blockEntity,
        final RadarStationRenderState state, final float partialTick, final Vec3 camera,
        final ModelFeatureRenderer.@Nullable CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, camera, overlay);
        long time = blockEntity.getLevel() == null ? 0L : blockEntity.getLevel().getGameTime();
        state.sweepAngle = RadarSweepMath.angleAt(time, partialTick, blockEntity.phaseOffset());
        state.warningActive = blockEntity.warningActive();
    }

    @Override
    public void submit(final RadarStationRenderState state, final PoseStack poseStack,
        final SubmitNodeCollector collector, final CameraRenderState camera) {
        collector.submitCustomGeometry(poseStack, WarheadRenderPipelines.PROJECTILE,
            (pose, buffer) -> RadarStationBaseMesh.render(
                pose, buffer, state.lightCoords, state.warningActive));
        RadarDishRenderer.submit(state, poseStack, collector);
    }

    @Override
    public int getViewDistance() {
        return 192;
    }
}