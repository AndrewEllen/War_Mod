package com.andye.warmod.silo.client;

import com.andye.warmod.block.MissileWorkbenchBlock;
import com.andye.warmod.block.entity.MissileWorkbenchBlockEntity;
import com.andye.warmod.silo.MissileWorkbenchPreview;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Renders the body and payload sitting in the workbench cradle; chips stay internal. */
public final class MissileWorkbenchBlockEntityRenderer
        implements BlockEntityRenderer<MissileWorkbenchBlockEntity, MissileWorkbenchRenderState> {
    private final ItemModelResolver itemModels;

    public MissileWorkbenchBlockEntityRenderer(final BlockEntityRendererProvider.Context context) {
        this.itemModels = context.itemModelResolver();
    }

    @Override
    public MissileWorkbenchRenderState createRenderState() {
        return new MissileWorkbenchRenderState();
    }

    @Override
    public void extractRenderState(
            final MissileWorkbenchBlockEntity bench,
            final MissileWorkbenchRenderState state,
            final float partialTick,
            final Vec3 camera,
            final ModelFeatureRenderer.@Nullable CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(bench, state, partialTick, camera, overlay);
        state.facing = bench.getBlockState().getValue(MissileWorkbenchBlock.FACING);
        itemModels.updateForTopItem(
                state.body,
                bench.getItem(MissileWorkbenchPreview.BODY_SLOT),
                ItemDisplayContext.FIXED,
                bench.getLevel(),
                null,
                0);
        itemModels.updateForTopItem(
                state.payload,
                bench.getItem(MissileWorkbenchPreview.PAYLOAD_SLOT),
                ItemDisplayContext.FIXED,
                bench.getLevel(),
                null,
                1);
    }

    @Override
    public void submit(
            final MissileWorkbenchRenderState state,
            final PoseStack poses,
            final SubmitNodeCollector collector,
            final CameraRenderState camera) {
        // The fixed item transforms retain the authored Y=180 orientation.
        // Rotate native vertical component bounds onto the two-block cradle's
        // local +X axis, joining the body and payload at x≈1.10.
        poses.pushPose();
        poses.translate(0.5, 0.98, 0.5);
        poses.mulPose(Axis.YP.rotationDegrees(yawFor(state.facing)));
        submitItem(state.body, poses, collector, state.lightCoords, 0.62F, 0.50F, 1.75F);
        submitItem(state.payload, poses, collector, state.lightCoords, 1.41F, 0.50F, 1.15F);
        poses.popPose();
    }

    private static void submitItem(
            final net.minecraft.client.renderer.item.ItemStackRenderState item,
            final PoseStack poses,
            final SubmitNodeCollector collector,
            final int light,
            final float x,
            final float z,
            final float scale) {
        if (item.isEmpty()) return;
        poses.pushPose();
        poses.translate(x - 0.5F, 0.0F, z - 0.5F);
        poses.mulPose(Axis.ZN.rotationDegrees(90.0F));
        poses.scale(scale, scale, scale);
        item.submit(poses, collector, light, OverlayTexture.NO_OVERLAY, 0);
        poses.popPose();
    }

    private static float yawFor(final net.minecraft.core.Direction facing) {
        return switch (facing) {
            case EAST -> -90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };
    }

    @Override
    public int getViewDistance() {
        return 64;
    }

    // The controller BE is on the left half while its assembled display can
    // extend over the companion half across a chunk edge.
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
