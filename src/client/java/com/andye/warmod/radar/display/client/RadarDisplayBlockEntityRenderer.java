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

        /*
         * Reset every dynamic field because render-state objects are reused.
         */
        state.structureValid =
            display.valid();

        state.width =
            Math.max(
                1,
                display.width()
            );

        state.height =
            Math.max(
                1,
                display.height()
            );

        state.tileX =
            display.tileX();

        state.tileY =
            display.tileY();

        state.controller =
            display.controller();

        state.displayId =
            display.displayId();

        state.facing =
            display.getBlockState()
                .getValue(
                    RadarDisplayPanelBlock.FACING
                );

        state.online =
            false;

        state.syncing =
            display.link() != null;

        state.stale =
            false;

        state.offlineReason =
            display.link() == null
                ? RadarDisplayOfflineReason.UNLINKED
                : RadarDisplayOfflineReason
                    .STATION_MISSING;

        state.radarCentre =
            Vec3.ZERO;

        state.horizontalRadius =
            1.0;

        state.verticalRadius =
            1.0;

        state.warningRadius =
            0.0;

        state.fireRadius =
            0.0;

        state.redstoneSignal =
            0;

        state.serverNow =
            0.0;

        state.sweepPeriod =
            80;

        state.sweepAngleDegrees =
            0.0;

        state.observations =
            List.of();

        if (
            display.getLevel() == null
            || state.displayId == null
        ) {
            return;
        }

        Identifier dimension =
            display.getLevel()
                .dimension()
                .identifier();

        double clientTime =
            display.getLevel()
                .getGameTime()
                + partialTick;

        ClientRadarDisplayState.View view =
            ClientRadarDisplayState.INSTANCE.view(
                dimension,
                state.controller,
                state.displayId,
                clientTime
            );

        if (view == null) {
            return;
        }

        RadarDisplaySnapshot snapshot =
            view.snapshot();

        state.structureValid =
            snapshot.structureValid();

        state.online =
            snapshot.online();

        state.syncing =
            false;

        state.stale =
            view.stale();

        state.offlineReason =
            snapshot.offlineReason();

        state.width =
            Math.max(
                1,
                snapshot.width()
            );

        state.height =
            Math.max(
                1,
                snapshot.height()
            );

        state.horizontalRadius =
            Math.max(
                1.0,
                snapshot.horizontalRadius()
            );

        state.verticalRadius =
            Math.max(
                1.0,
                snapshot.verticalRadius()
            );

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

        if (
            snapshot.radarCentre()
                != null
        ) {
            state.radarCentre =
                Vec3.atCenterOf(
                    snapshot.radarCentre()
                );
        }

        List<RadarDisplayRenderObservation> observations =
            new ArrayList<>();

        for (
            var observation
                : snapshot.observations()
        ) {
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
        final PoseStack pose,
        final SubmitNodeCollector collector,
        final CameraRenderState camera
    ) {
        if (
            !state.structureValid
            || state.width < 1
            || state.height < 1
            || state.tileX < 0
            || state.tileX >= state.width
            || state.tileY < 0
            || state.tileY >= state.height
        ) {
            return;
        }

        collector.submitCustomGeometry(
            pose,
            RadarDisplayRenderPipelines.screen(),
            (submittedPose, buffer) ->
                RadarDisplayMapRenderer.renderTile(
                    submittedPose,
                    buffer,
                    state
                )
        );
    }

    @Override
    public int getViewDistance() {
        return 1024;
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
