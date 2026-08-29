package com.andye.warmod.silo.client;

import com.andye.warmod.block.MissileSiloState;
import com.andye.warmod.block.entity.MissileSiloBlockEntity;
import com.andye.warmod.client.model.BlockbenchGameplayMeshes;
import com.andye.warmod.client.model.BlockbenchGameplayMeshes.Model;
import com.andye.warmod.client.model.BlockbenchModelRenderType;
import com.andye.warmod.silo.MissilePayloadItems;
import com.andye.warmod.silo.MissileSiloConstants;
import net.minecraft.util.Mth;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class MissileSiloBlockEntityRenderer implements BlockEntityRenderer<MissileSiloBlockEntity, MissileSiloRenderState> {
    public MissileSiloBlockEntityRenderer(final BlockEntityRendererProvider.Context context) { }
    @Override public MissileSiloRenderState createRenderState() { return new MissileSiloRenderState(); }
    @Override public void extractRenderState(final MissileSiloBlockEntity silo, final MissileSiloRenderState state,
        final float partialTicks, final Vec3 cameraPosition,
        final ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(silo, state, partialTicks, cameraPosition, breakProgress);
        state.missileType = MissilePayloadItems.missileType(silo.missileStack()).orElse(null);
        state.availableCount = silo.missileStack().getCount();
        state.siloState = silo.siloState();
        state.facing = silo.facing();
        state.visible = state.availableCount > 0 && state.missileType != null
            && state.siloState != MissileSiloState.PREPARING && state.siloState != MissileSiloState.LAUNCHING && state.siloState != MissileSiloState.COOLDOWN
            && state.siloState != MissileSiloState.INVALID_STRUCTURE;
        state.reloadOffsetY = 0.0;
        state.doorProgress = state.visible || state.siloState == MissileSiloState.PREPARING
            || state.siloState == MissileSiloState.LAUNCHING
            || state.siloState == MissileSiloState.COOLDOWN ? 1.0F : 0.0F;
        if (state.siloState == MissileSiloState.RELOADING && silo.getLevel() != null) {
            double duration = Math.max(1, silo.reloadTicksTotal());
            double elapsed = silo.getLevel().getGameTime() + partialTicks - silo.reloadStartGameTime();
            double progress = Mth.clamp(elapsed / duration, 0.0, 1.0);
            double doorPhase = Mth.clamp(progress / 0.22, 0.0, 1.0);
            state.doorProgress = (float) (doorPhase * doorPhase * (3.0 - 2.0 * doorPhase));
            double risePhase = Mth.clamp((progress - 0.18) / 0.82, 0.0, 1.0);
            double eased = risePhase * risePhase * (3.0 - 2.0 * risePhase);
            state.reloadOffsetY = Mth.lerp(eased, MissileSiloConstants.RELOAD_START_OFFSET_Y,
                MissileSiloConstants.RELOAD_END_OFFSET_Y);
        }
    }
    @Override public void submit(final MissileSiloRenderState state, final PoseStack poseStack,
        final SubmitNodeCollector collector, final CameraRenderState camera) {
        submitDoor(state, poseStack, collector, true);
        submitDoor(state, poseStack, collector, false);
        MissileSiloMissileRenderer.submit(state, poseStack, collector);
    }

    private static void submitDoor(final MissileSiloRenderState state,
        final PoseStack poseStack, final SubmitNodeCollector collector, final boolean left) {
        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        float hingeX = left ? -0.75F : 0.75F;
        poseStack.translate(hingeX, 5.0F / 16.0F, 0.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees((left ? 82.0F : -82.0F)
            * state.doorProgress));
        poseStack.translate(-hingeX, -5.0F / 16.0F, 0.0F);
        Model model = left ? Model.SILO_DOOR_LEFT : Model.SILO_DOOR_RIGHT;
        collector.submitCustomGeometry(poseStack, BlockbenchModelRenderType.SOLID,
            (pose, buffer) -> BlockbenchGameplayMeshes.render(pose, buffer, model,
                1.0F / 16.0F, 0.0F, 0.0F, 0.0F, state.lightCoords));
        poseStack.popPose();
    }

    @Override public int getViewDistance() { return 256; }
    @Override public boolean shouldRenderOffScreen() { return true; }
}
