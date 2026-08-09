package com.andye.warmod.entity.client;

import com.andye.warmod.block.ModBlocks;
import com.andye.warmod.entity.TimedWarheadTntEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.lighting.LevelLightEngine;

public final class TimedWarheadTntRenderer
    extends EntityRenderer<TimedWarheadTntEntity, TimedTntRenderState> {
    public TimedWarheadTntRenderer(final EntityRendererProvider.Context context) {
        super(context);
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
        BlockPos pos = entity.blockPosition();
        state.movingBlock.randomSeedPos = pos;
        state.movingBlock.blockPos = pos;
        state.movingBlock.blockState = ModBlocks.timedTnt(
            entity.yield(), entity.cluster()).defaultBlockState();
        state.movingBlock.biome = null;
        state.movingBlock.cardinalLighting = CardinalLighting.DEFAULT;
        state.movingBlock.lightEngine = LevelLightEngine.EMPTY;
        if (entity.level() instanceof ClientLevel level && level.hasChunkAt(pos)) {
            state.movingBlock.biome = level.getBiome(pos);
            state.movingBlock.cardinalLighting = level.cardinalLighting();
            state.movingBlock.lightEngine = level.getLightEngine();
        }
    }

    @Override
    public void submit(final TimedTntRenderState state, final PoseStack poses,
        final SubmitNodeCollector collector, final CameraRenderState camera) {
        poses.pushPose();
        poses.translate(-0.5, 0.0, -0.5);
        collector.submitMovingBlock(poses, state.movingBlock, state.outlineColor);
        poses.popPose();
        super.submit(state, poses, collector, camera);
    }
}