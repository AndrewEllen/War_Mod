package com.andye.warmod.entity.client;

import com.andye.warmod.block.ModBlocks;
import com.andye.warmod.entity.TimedWarheadTntEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.TntMinecartRenderer;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;

public final class TimedWarheadTntRenderer
    extends EntityRenderer<TimedWarheadTntEntity, TimedTntRenderState> {
    private final BlockModelResolver blockModelResolver;

    public TimedWarheadTntRenderer(final EntityRendererProvider.Context context) {
        super(context);
        blockModelResolver = context.getBlockModelResolver();
        shadowRadius = 0.5F;
    }

    @Override
    public TimedTntRenderState createRenderState() {
        return new TimedTntRenderState();
    }

    @Override
    public void extractRenderState(final TimedWarheadTntEntity entity,
        final TimedTntRenderState state, final float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.fuseRemainingInTicks = entity.fuse() - partialTick + 1.0F;
        blockModelResolver.update(state.blockState, ModBlocks.timedTnt(
            entity.yield(), entity.cluster()).defaultBlockState(),
            TntRenderer.BLOCK_DISPLAY_CONTEXT);
    }

    @Override
    public void submit(final TimedTntRenderState state, final PoseStack poses,
        final SubmitNodeCollector collector, final CameraRenderState camera) {
        poses.pushPose();
        poses.translate(0.0F, 0.5F, 0.0F);
        if (state.fuseRemainingInTicks < 10.0F) {
            float swell = 1.0F + TntRenderer.getSwellAmount(state.fuseRemainingInTicks);
            poses.scale(swell, swell, swell);
        }
        poses.mulPose(Axis.YP.rotationDegrees(-90.0F));
        poses.translate(-0.5F, -0.5F, 0.5F);
        poses.mulPose(Axis.YP.rotationDegrees(90.0F));
        TntMinecartRenderer.submitWhiteSolidBlock(state.blockState, poses, collector,
            state.lightCoords, TntRenderer.isLit(state.fuseRemainingInTicks), state.outlineColor);
        poses.popPose();
        super.submit(state, poses, collector, camera);
    }
}
