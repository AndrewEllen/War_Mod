package com.andye.warmod.silo.client;

import com.andye.warmod.block.MissileSiloState;
import com.andye.warmod.block.entity.MissileSiloBlockEntity;
import com.andye.warmod.silo.MissilePayloadItems;
import com.mojang.blaze3d.vertex.PoseStack;
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
        state.payloadType = MissilePayloadItems.payloadType(silo.missileStack()).orElse(null);
        state.availableCount = silo.missileStack().getCount();
        state.siloState = silo.siloState();
        state.facing = silo.facing();
        state.visible = state.availableCount > 0 && state.payloadType != null
            && state.siloState != MissileSiloState.PREPARING && state.siloState != MissileSiloState.LAUNCHING && state.siloState != MissileSiloState.COOLDOWN
            && state.siloState != MissileSiloState.INVALID_STRUCTURE;
    }
    @Override public void submit(final MissileSiloRenderState state, final PoseStack poseStack,
        final SubmitNodeCollector collector, final CameraRenderState camera) {
        MissileSiloMissileRenderer.submit(state, poseStack, collector);
    }
    @Override public int getViewDistance() { return 128; }
}
