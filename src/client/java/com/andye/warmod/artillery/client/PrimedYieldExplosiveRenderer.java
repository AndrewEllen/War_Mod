package com.andye.warmod.artillery.client;

import com.andye.warmod.entity.PrimedYieldExplosiveEntity;
import com.andye.warmod.rocket.client.RocketProjectileRenderState;
import com.andye.warmod.warhead.client.render.WarheadMesh;
import com.andye.warmod.warhead.client.render.WarheadRenderPipelines;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Quaternionf;

public final class PrimedYieldExplosiveRenderer
    extends EntityRenderer<PrimedYieldExplosiveEntity, RocketProjectileRenderState> {
    public PrimedYieldExplosiveRenderer(final EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.18F;
    }

    @Override public RocketProjectileRenderState createRenderState() {
        return new RocketProjectileRenderState();
    }

    @Override
    public void extractRenderState(final PrimedYieldExplosiveEntity entity,
        final RocketProjectileRenderState state, final float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.velocity = entity.getDeltaMovement();
        state.visualSeed = entity.visualSeed();
        state.ageInTicks = entity.tickCount + partialTick;
        state.lod = RocketProjectileRenderState.RocketLod.NEAR;
    }

    @Override
    public void submit(final RocketProjectileRenderState state, final PoseStack poseStack,
        final SubmitNodeCollector collector, final CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.scale(0.58F, 0.58F, 0.58F);
        poseStack.mulPose(new Quaternionf().rotateY(state.ageInTicks * 0.09F)
            .rotateZ(state.ageInTicks * 0.055F));
        collector.submitCustomGeometry(poseStack, WarheadRenderPipelines.PROJECTILE,
            (pose, buffer) -> WarheadMesh.render(pose, buffer, WarheadMesh.Lod.NEAR));
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
