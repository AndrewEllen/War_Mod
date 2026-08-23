package com.andye.warmod.artillery.client.render;

import com.andye.warmod.entity.ArtilleryWarheadEntity;
import com.andye.warmod.warhead.WarheadConstants;
import com.andye.warmod.warhead.WarheadVisualMath;
import com.andye.warmod.warhead.client.render.ReentryHeatingRenderer;
import com.andye.warmod.warhead.client.render.ShockConeMesh;
import com.andye.warmod.warhead.client.render.VaporBandRenderer;
import com.andye.warmod.warhead.client.render.WarheadMesh;
import com.andye.warmod.warhead.client.render.WarheadRenderPipelines;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A local, gravity-flight counterpart to the incoming ICBM warhead renderer. It deliberately
 * submits into the same projectile, cone, vapor, and re-entry pipelines, so Iris and vanilla
 * receive exactly the already-compatible render types instead of a second particle path.
 */
public final class ArtilleryWarheadRenderer extends EntityRenderer<ArtilleryWarheadEntity,
    ArtilleryWarheadRenderState> {
    public ArtilleryWarheadRenderer(final EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.15F;
    }

    @Override
    public ArtilleryWarheadRenderState createRenderState() {
        return new ArtilleryWarheadRenderState();
    }

    @Override
    public void extractRenderState(final ArtilleryWarheadEntity shell,
        final ArtilleryWarheadRenderState state, final float partialTick) {
        super.extractRenderState(shell, state, partialTick);
        state.velocity = shell.getDeltaMovement();
        state.visualSeed = shell.visualSeed();
        state.flightTicks = Math.max(1, shell.flightTicks());
        state.elapsedTicks = Math.min(state.flightTicks,
            Math.max(0.0F, shell.activeTicks() + partialTick));
        state.remainingTicks = Math.max(0.0F, state.flightTicks - state.elapsedTicks);
        state.progress = Math.min(1.0F, state.elapsedTicks / state.flightTicks);
        state.clusterCarrier = shell.clusterCarrier();
        double distance = Math.sqrt(state.distanceToCameraSq);
        state.lod = distance < 192.0 ? WarheadMesh.Lod.NEAR
            : distance < 640.0 ? WarheadMesh.Lod.MEDIUM : WarheadMesh.Lod.FAR;
    }

    @Override
    public void submit(final ArtilleryWarheadRenderState state, final PoseStack poses,
        final SubmitNodeCollector collector, final CameraRenderState camera) {
        poses.pushPose();
        if (state.clusterCarrier) poses.scale(1.32F, 1.32F, 1.32F);
        Vector3f direction = new Vector3f((float) state.velocity.x, (float) state.velocity.y,
            (float) state.velocity.z);
        if (direction.lengthSquared() > 1.0E-6F) {
            poses.mulPose(new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F),
                direction.normalize()));
        }
        collector.submitCustomGeometry(poses, WarheadRenderPipelines.PROJECTILE,
            (pose, buffer) -> WarheadMesh.render(pose, buffer, state.lod, state.lightCoords));
        collector.submitCustomGeometry(poses, WarheadRenderPipelines.CONE,
            (pose, buffer) -> ShockConeMesh.render(pose, buffer, state.lod, state.progress,
                state.elapsedTicks, state.remainingTicks, state.velocity, state.visualSeed,
                state.flightTicks));
        float coneActivation = (float) WarheadVisualMath.coneActivation(
            WarheadVisualMath.normalizedSpeed(state.velocity,
                WarheadConstants.TRAJECTORY_SPEED_BLOCKS_PER_TICK * 1.65));
        collector.submitCustomGeometry(poses, WarheadRenderPipelines.VAPOR_BAND,
            (pose, buffer) -> VaporBandRenderer.render(pose, buffer, state.lod,
                state.elapsedTicks, state.visualSeed, state.progress, coneActivation,
                (float) WarheadVisualMath.coneFade(state.remainingTicks)));
        collector.submitCustomGeometry(poses, WarheadRenderPipelines.REENTRY_PLASMA,
            (pose, buffer) -> ReentryHeatingRenderer.renderBowShock(pose, buffer, state.lod,
                state.progress, state.elapsedTicks, state.remainingTicks, state.velocity,
                state.visualSeed));
        collector.submitCustomGeometry(poses, WarheadRenderPipelines.REENTRY_PLASMA,
            (pose, buffer) -> ReentryHeatingRenderer.renderGlow(pose, buffer, state.lod,
                state.progress, state.elapsedTicks, state.remainingTicks, state.velocity,
                state.visualSeed));
        collector.submitCustomGeometry(poses, WarheadRenderPipelines.REENTRY_PLASMA,
            (pose, buffer) -> ReentryHeatingRenderer.renderFilaments(pose, buffer, state.lod,
                state.progress, state.elapsedTicks, state.remainingTicks, state.velocity,
                state.visualSeed));
        poses.popPose();
        super.submit(state, poses, collector, camera);
    }
}
