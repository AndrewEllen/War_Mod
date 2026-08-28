package com.andye.warmod.artillery.client.render;

import com.andye.warmod.artillery.ArtilleryConstants;
import com.andye.warmod.artillery.ArtilleryTrajectory;
import com.andye.warmod.block.ArtilleryCannonBlock;
import com.andye.warmod.block.entity.ArtilleryCannonBlockEntity;
import com.andye.warmod.client.model.BlockbenchGameplayMeshes;
import com.andye.warmod.client.model.BlockbenchGameplayMeshes.Model;
import com.andye.warmod.client.model.BlockbenchModelRenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Three-block-wide visual carriage with a target-driven traversing and elevating gun.
 * The shared physical muzzle constants keep the shell spawn at the end of the visible tube.
 */
public final class ArtilleryCannonBlockEntityRenderer
    implements BlockEntityRenderer<ArtilleryCannonBlockEntity, ArtilleryCannonRenderState> {
    private static final float MODEL_SCALE = (float)(ArtilleryConstants.BARREL_PIVOT_HEIGHT / 14.0);
    private static final float YAW_PIVOT_Y = 8.0F;
    private static final float PITCH_PIVOT_Y = 14.0F;
    private static final float PITCH_PIVOT_Z = -3.0F;

    public ArtilleryCannonBlockEntityRenderer(final BlockEntityRendererProvider.Context context) { }

    @Override
    public ArtilleryCannonRenderState createRenderState() {
        return new ArtilleryCannonRenderState();
    }

    @Override
    public void extractRenderState(final ArtilleryCannonBlockEntity cannon,
        final ArtilleryCannonRenderState state, final float partialTick, final Vec3 camera,
        final ModelFeatureRenderer.@Nullable CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(cannon, state, partialTick, camera, overlay);
        Direction facing = cannon.getBlockState().getValue(ArtilleryCannonBlock.FACING);
        float placedYaw = switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> -90.0F;
            default -> 0.0F;
        };
        state.baseYawDegrees = placedYaw;
        state.yawDegrees = placedYaw;
        state.elevationDegrees = 12.0F;
        state.hasTarget = cannon.target() != null;
        if (cannon.target() == null) return;

        Vec3 origin = Vec3.atCenterOf(cannon.getBlockPos()).add(0.0,
            ArtilleryConstants.BARREL_PIVOT_HEIGHT, 0.0);
        ArtilleryTrajectory.solveFromCannon(origin, cannon.target().position()).ifPresent(launch -> {
            Vec3 velocity = launch.velocity();
            // The hand-built barrel points along local -Z; positive Axis.Y rotation turns it west.
            state.yawDegrees = (float)-Math.toDegrees(Math.atan2(velocity.x, -velocity.z));
            state.elevationDegrees = (float)Math.toDegrees(
                Math.atan2(velocity.y, velocity.horizontalDistance()));
        });
    }

    @Override
    public void submit(final ArtilleryCannonRenderState state, final PoseStack poses,
        final SubmitNodeCollector collector, final CameraRenderState camera) {
        // The saved Blockbench hierarchy keeps the carriage fixed while the upper
        // groups traverse and elevate around their authored pivots.
        poses.pushPose();
        poses.translate(0.5, 0.0, 0.5);
        poses.mulPose(Axis.YP.rotationDegrees(state.baseYawDegrees));
        collector.submitCustomGeometry(poses, BlockbenchModelRenderType.SOLID,
            (pose, buffer) -> BlockbenchGameplayMeshes.render(pose, buffer,
                Model.ARTILLERY_FIXED, MODEL_SCALE, 0.0F, 0.0F, 0.0F, state.lightCoords));
        poses.popPose();

        poses.pushPose();
        poses.translate(0.5, YAW_PIVOT_Y * MODEL_SCALE, 0.5);
        poses.mulPose(Axis.YP.rotationDegrees(state.yawDegrees));
        collector.submitCustomGeometry(poses, BlockbenchModelRenderType.SOLID,
            (pose, buffer) -> BlockbenchGameplayMeshes.render(pose, buffer,
                Model.ARTILLERY_YAW, MODEL_SCALE, 0.0F, YAW_PIVOT_Y, 0.0F,
                state.lightCoords));

        poses.translate(0.0, (PITCH_PIVOT_Y - YAW_PIVOT_Y) * MODEL_SCALE,
            PITCH_PIVOT_Z * MODEL_SCALE);
        poses.mulPose(Axis.XP.rotationDegrees(state.elevationDegrees));
        collector.submitCustomGeometry(poses, BlockbenchModelRenderType.SOLID,
            (pose, buffer) -> BlockbenchGameplayMeshes.render(pose, buffer,
                Model.ARTILLERY_PITCH, MODEL_SCALE, 0.0F, PITCH_PIVOT_Y,
                PITCH_PIVOT_Z, state.lightCoords));
        poses.popPose();
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
