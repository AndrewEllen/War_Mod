package com.andye.warmod.rocket.client;

import com.andye.warmod.entity.RocketProjectileEntity;
import com.andye.warmod.client.model.BlockbenchModelRenderType;
import com.andye.warmod.rocket.RocketConstants;
import com.andye.warmod.rocket.RocketPayloadType;
import com.andye.warmod.warhead.client.render.WarheadRenderPipelines;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class RocketProjectileRenderer
    extends EntityRenderer<RocketProjectileEntity, RocketProjectileRenderState> {
    public RocketProjectileRenderer(final EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    @Override
    public RocketProjectileRenderState createRenderState() {
        return new RocketProjectileRenderState();
    }

    @Override
    public void extractRenderState(final RocketProjectileEntity entity,
        final RocketProjectileRenderState state, final float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.velocity = entity.getDeltaMovement();
        state.payloadType = entity.payloadType();
        state.visualSeed = entity.visualSeed();
        state.ageInTicks = entity.age() + partialTick;
        double distance = Math.sqrt(state.distanceToCameraSq);
        state.lod = distance < RocketConstants.NEAR_LOD_BLOCKS
            ? RocketProjectileRenderState.RocketLod.NEAR
            : distance < RocketConstants.MEDIUM_LOD_BLOCKS
                ? RocketProjectileRenderState.RocketLod.MEDIUM
                : RocketProjectileRenderState.RocketLod.FAR;
    }

    @Override
    public void submit(final RocketProjectileRenderState state, final PoseStack poseStack,
        final SubmitNodeCollector collector, final CameraRenderState camera) {
        poseStack.pushPose();
        Vector3f direction = new Vector3f((float)state.velocity.x,
            (float)state.velocity.y, (float)state.velocity.z);
        if (direction.lengthSquared() > 1.0E-6F) {
            poseStack.mulPose(new Quaternionf().rotationTo(
                new Vector3f(0, 1, 0), direction.normalize()));
        }
        collector.submitCustomGeometry(poseStack,
            state.payloadType == RocketPayloadType.HE
                ? BlockbenchModelRenderType.SOLID : WarheadRenderPipelines.PROJECTILE,
            (pose, buffer) -> RocketProjectileMesh.render(pose, buffer, state));
        collector.submitCustomGeometry(poseStack, WarheadRenderPipelines.FIREBALL_HOT,
            (pose, buffer) -> RocketTrailRenderer.renderFlame(pose, buffer, state));
        collector.submitCustomGeometry(poseStack, WarheadRenderPipelines.GROUND_DUST,
            (pose, buffer) -> RocketTrailRenderer.renderSmoke(pose, buffer, state));
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
