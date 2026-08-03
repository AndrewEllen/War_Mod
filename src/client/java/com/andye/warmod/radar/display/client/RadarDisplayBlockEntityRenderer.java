package com.andye.warmod.radar.display.client;

import com.andye.warmod.block.RadarDisplayPanelBlock;
import com.andye.warmod.block.entity.RadarDisplayPanelBlockEntity;
import com.andye.warmod.radar.display.RadarDisplayOfflineReason;
import com.andye.warmod.radar.display.RadarDisplaySnapshot;
import com.andye.warmod.radar.station.RadarSweepMath;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class RadarDisplayBlockEntityRenderer
    implements BlockEntityRenderer<
        RadarDisplayPanelBlockEntity,
        RadarDisplayRenderState
    > {
    public RadarDisplayBlockEntityRenderer(
        final BlockEntityRendererProvider.Context context
    ) {
    }

    @Override
    public RadarDisplayRenderState createRenderState() {
        return new RadarDisplayRenderState();
    }

    @Override
    public void extractRenderState(
        final RadarDisplayPanelBlockEntity display,
        final RadarDisplayRenderState state,
        final float partialTick,
        final Vec3 camera,
        final ModelFeatureRenderer.@Nullable CrumblingOverlay overlay
    ) {
        BlockEntityRenderer.super.extractRenderState(
            display,
            state,
            partialTick,
            camera,
            overlay
        );

        state.controllerPanel =
            display.controllerPanel();

        state.structureValid =
            display.valid();

        state.size =
            Math.max(1, display.size());

        state.displayRadius =
            display.radius();

        state.controller =
            display.controller();

        state.displayId =
            display.displayId();

        state.facing =
            display.getBlockState()
                .getValue(RadarDisplayPanelBlock.FACING);

        state.online = false;
        state.syncing = false;
        state.offlineReason =
            display.link() == null
                ? RadarDisplayOfflineReason.UNLINKED
                : RadarDisplayOfflineReason.STATION_MISSING;

        state.observations = List.of();

        if (!state.controllerPanel
            || display.getLevel() == null
            || state.displayId == null) {
            return;
        }

        Identifier dimension =
            display.getLevel()
                .dimension()
                .identifier();

        double clientTime =
            display.getLevel().getGameTime()
            + partialTick;

        ClientRadarDisplayState.View view =
            ClientRadarDisplayState.INSTANCE.view(
                dimension,
                state.controller,
                state.displayId,
                clientTime
            );

        if (view == null) {
            state.syncing = display.link() != null;
            return;
        }

        RadarDisplaySnapshot snapshot =
            view.snapshot();

        state.structureValid =
            snapshot.structureValid();

        state.online =
            snapshot.online();

        state.syncing = false;

        state.offlineReason =
            snapshot.offlineReason();

        state.size =
            Math.max(1, snapshot.size());

        state.displayRadius =
            snapshot.displayRadius();

        state.warningRadius =
            snapshot.warningRadius();

        state.fireRadius =
            snapshot.fireRadius();

        state.redstoneSignal =
            snapshot.redstoneSignal();

        state.serverNow =
            view.serverNow();

        state.sweepPeriod =
            snapshot.sweepPeriodTicks();

        state.sweepAngleDegrees =
            RadarSweepMath.angleDegrees(
                state.serverNow,
                snapshot.phaseOffset()
            );

        if (snapshot.radarCentre() != null) {
            state.radarCentre =
                Vec3.atCenterOf(snapshot.radarCentre());
        }

        List<RadarDisplayRenderObservation> observations =
            new ArrayList<>(
                snapshot.observations().size()
            );

        for (var observation
            : snapshot.observations()) {
            RadarDisplayRenderObservation rendered =
                RadarDisplayRenderObservation.from(
                    observation,
                    state.serverNow,
                    state.sweepPeriod
                );

            if (rendered.alpha() > 0.0F) {
                observations.add(rendered);
            }
        }

        state.observations =
            List.copyOf(observations);
    }

    @Override
    public void submit(
        final RadarDisplayRenderState state,
        final PoseStack poseStack,
        final SubmitNodeCollector collector,
        final CameraRenderState camera
    ) {
        if (!state.controllerPanel
            || state.size < 1) {
            return;
        }

        collector.submitCustomGeometry(
            poseStack,
            RadarDisplayRenderPipelines.SCREEN,
            (pose, buffer) ->
                RadarDisplayMapRenderer.render(
                    pose,
                    buffer,
                    state
                )
        );
    }

    @Override
    public int getViewDistance() {
        return 320;
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
