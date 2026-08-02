package com.andye.warmod.rocket.client;

import com.andye.warmod.entity.RocketProjectileEntity;
import com.andye.warmod.warhead.client.render.WarheadRenderPipelines;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class RocketProjectileRenderer extends EntityRenderer<RocketProjectileEntity, RocketProjectileRenderState> {
    public RocketProjectileRenderer(final EntityRendererProvider.Context context) { super(context); this.shadowRadius = 0.0F; }
    @Override public RocketProjectileRenderState createRenderState() { return new RocketProjectileRenderState(); }
    @Override public void extractRenderState(final RocketProjectileEntity entity, final RocketProjectileRenderState state,
        final float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.velocity = entity.getDeltaMovement();
    }
    @Override public void submit(final RocketProjectileRenderState state, final PoseStack poseStack,
        final SubmitNodeCollector collector, final CameraRenderState camera) {
        poseStack.pushPose();
        Vector3f direction = new Vector3f((float)state.velocity.x, (float)state.velocity.y, (float)state.velocity.z);
        if (direction.lengthSquared() > 1.0E-6F) {
            direction.normalize();
            poseStack.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), direction));
        }
        poseStack.scale(0.16F, 0.16F, 0.16F);
        collector.submitCustomGeometry(poseStack, WarheadRenderPipelines.PROJECTILE,
            (pose, buffer) -> RocketProjectileMesh.render(pose, buffer, state.lightCoords));
        collector.submitCustomGeometry(poseStack, WarheadRenderPipelines.HEAVY_SMOKE,
            (pose, buffer) -> RocketTrailRenderer.render(pose, buffer, state));
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
