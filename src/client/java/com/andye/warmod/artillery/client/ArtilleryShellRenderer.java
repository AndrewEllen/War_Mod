package com.andye.warmod.artillery.client;

import com.andye.warmod.entity.ArtilleryShellEntity;
import com.andye.warmod.rocket.client.RocketProjectileRenderState;
import com.andye.warmod.warhead.client.render.WarheadMesh;
import com.andye.warmod.warhead.client.render.WarheadRenderPipelines;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class ArtilleryShellRenderer
    extends EntityRenderer<ArtilleryShellEntity, RocketProjectileRenderState> {
    public ArtilleryShellRenderer(final EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override public RocketProjectileRenderState createRenderState() {
        return new RocketProjectileRenderState();
    }

    @Override
    public void extractRenderState(final ArtilleryShellEntity entity,
        final RocketProjectileRenderState state, final float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.velocity = entity.getDeltaMovement();
        state.ageInTicks = entity.tickCount + partialTick;
        double distance = Math.sqrt(state.distanceToCameraSq);
        state.lod = distance < 192.0 ? RocketProjectileRenderState.RocketLod.NEAR
            : distance < 640.0 ? RocketProjectileRenderState.RocketLod.MEDIUM
            : RocketProjectileRenderState.RocketLod.FAR;
    }

    @Override
    public void submit(final RocketProjectileRenderState state, final PoseStack poseStack,
        final SubmitNodeCollector collector, final CameraRenderState camera) {
        poseStack.pushPose();
        Vec3 velocity = state.velocity;
        Vector3f direction = new Vector3f((float)velocity.x, (float)velocity.y, (float)velocity.z);
        if (direction.lengthSquared() > 1.0E-6F) {
            poseStack.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), direction.normalize()));
        }
        WarheadMesh.Lod lod = switch (state.lod) {
            case NEAR -> WarheadMesh.Lod.NEAR;
            case MEDIUM -> WarheadMesh.Lod.MEDIUM;
            case FAR -> WarheadMesh.Lod.FAR;
        };
        collector.submitCustomGeometry(poseStack, WarheadRenderPipelines.PROJECTILE,
            (pose, buffer) -> WarheadMesh.render(pose, buffer, lod));
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
