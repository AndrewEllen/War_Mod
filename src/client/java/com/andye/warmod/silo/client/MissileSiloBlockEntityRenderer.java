package com.andye.warmod.silo.client;

import com.andye.warmod.block.MissileSiloState;
import com.andye.warmod.block.entity.MissileSiloBlockEntity;
import com.andye.warmod.client.model.BlockbenchGameplayMeshes;
import com.andye.warmod.client.model.BlockbenchGameplayMeshes.Model;
import com.andye.warmod.client.model.BlockbenchModelRenderType;
import com.andye.warmod.silo.MissilePayloadItems;
import com.andye.warmod.silo.MissileSiloConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

public final class MissileSiloBlockEntityRenderer
        implements BlockEntityRenderer<MissileSiloBlockEntity, MissileSiloRenderState> {
    public MissileSiloBlockEntityRenderer(final BlockEntityRendererProvider.Context context) {}

    @Override
    public MissileSiloRenderState createRenderState() {
        return new MissileSiloRenderState();
    }

    @Override
    public void extractRenderState(
            final MissileSiloBlockEntity silo,
            final MissileSiloRenderState state,
            final float partialTicks,
            final Vec3 cameraPosition,
            final ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(
                silo, state, partialTicks, cameraPosition, breakProgress);
        state.siloState = silo.siloState();
        state.facing = silo.facing();
        state.large = silo.getBlockState().getValue(com.andye.warmod.block.MissileSiloBlock.LARGE);
        state.missileType = MissilePayloadItems.missileType(silo.reservedMissile()).orElse(null);
        state.availableCount = silo.reservedMissile().getCount();
        state.visible = state.siloState == MissileSiloState.PREPARING && state.missileType != null;
        state.missileCenterOffsetY = MissileSiloConstants.MISSILE_HIDDEN_CENTER_OFFSET_Y;
        state.doorProgress = 0;
        double elapsed =
                silo.getLevel() == null
                        ? 0
                        : silo.getLevel().getGameTime()
                                + partialTicks
                                - silo.animationStartGameTime();
        if (state.siloState == MissileSiloState.PREPARING) {
            double door = Mth.clamp(elapsed / 12.0, 0, 1);
            state.doorProgress = (float) (door * door * (3 - 2 * door));
            // Keep the reserved model below the throat. The launched entity now
            // supplies the powered emergence after the doors and ignition delay.
            state.missileCenterOffsetY = MissileSiloConstants.MISSILE_HIDDEN_CENTER_OFFSET_Y;
        } else if (state.siloState == MissileSiloState.LAUNCHING) {
            double close = Mth.clamp(
                    (elapsed - MissileSiloConstants.DOOR_CLOSE_DELAY_TICKS)
                            / MissileSiloConstants.DOOR_CLOSE_ANIMATION_TICKS,
                    0,
                    1);
            state.doorProgress = (float) (1 - close * close * (3 - 2 * close));
        }
    }

    @Override
    public void submit(
            final MissileSiloRenderState state,
            final PoseStack poseStack,
            final SubmitNodeCollector collector,
            final CameraRenderState camera) {
        submitDoor(state, poseStack, collector, true);
        submitDoor(state, poseStack, collector, false);
        MissileSiloMissileRenderer.submit(state, poseStack, collector);
    }

    private static void submitDoor(
            final MissileSiloRenderState state,
            final PoseStack poseStack,
            final SubmitNodeCollector collector,
            final boolean left) {
        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        float hingeX = left
                ? (state.large ? -1.25F : -0.75F)
                : (state.large ? 1.25F : 0.75F);
        poseStack.translate(hingeX, 5.0F / 16.0F, 0.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees((left ? 82.0F : -82.0F) * state.doorProgress));
        poseStack.translate(-hingeX, -5.0F / 16.0F, 0.0F);
        Model model = state.large
                ? (left ? Model.SILO_LARGE_DOOR_LEFT : Model.SILO_LARGE_DOOR_RIGHT)
                : (left ? Model.SILO_DOOR_LEFT : Model.SILO_DOOR_RIGHT);
        collector.submitCustomGeometry(
                poseStack,
                BlockbenchModelRenderType.SOLID,
                (pose, buffer) ->
                        BlockbenchGameplayMeshes.render(
                                pose,
                                buffer,
                                model,
                                1.0F / 16.0F,
                                0.0F,
                                0.0F,
                                0.0F,
                                state.lightCoords));
        poseStack.popPose();
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
