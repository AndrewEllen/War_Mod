package com.andye.warmod.entity.client;

import com.andye.warmod.warhead.client.render.WarheadRenderPipelines;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Compact client shell shared by artillery rounds and hand-thrown timed charges. */
public final class SimpleWarheadRenderer<T extends Entity> extends EntityRenderer<T, SimpleWarheadRenderState> {
    public SimpleWarheadRenderer(final EntityRendererProvider.Context context) { super(context); shadowRadius = 0.15F; }
    @Override public SimpleWarheadRenderState createRenderState() { return new SimpleWarheadRenderState(); }
    @Override public void extractRenderState(final T entity, final SimpleWarheadRenderState state, final float partialTick) { super.extractRenderState(entity, state, partialTick); state.velocity = entity.getDeltaMovement(); }
    @Override public void submit(final SimpleWarheadRenderState state, final PoseStack poses, final SubmitNodeCollector collector, final CameraRenderState camera) {
        poses.pushPose(); Vector3f velocity = new Vector3f((float)state.velocity.x, (float)state.velocity.y, (float)state.velocity.z); if (velocity.lengthSquared() > 1.0E-6F) poses.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), velocity.normalize()));
        collector.submitCustomGeometry(poses, WarheadRenderPipelines.PROJECTILE, (pose, buffer) -> box(pose, buffer, 0.16F, 0.52F, state.lightCoords)); poses.popPose(); super.submit(state, poses, collector, camera);
    }
    private static void box(final PoseStack.Pose pose, final VertexConsumer buffer, final float radius, final float half, final int light) { quad(pose,buffer,-radius,-half,-radius,radius,-half,-radius,radius,half,-radius,-radius,half,-radius,118,122,116,light); quad(pose,buffer,radius,-half,radius,-radius,-half,radius,-radius,half,radius,radius,half,radius,118,122,116,light); quad(pose,buffer,-radius,-half,radius,-radius,-half,-radius,-radius,half,-radius,-radius,half,radius,102,106,100,light); quad(pose,buffer,radius,-half,-radius,radius,-half,radius,radius,half,radius,radius,half,-radius,102,106,100,light); quad(pose,buffer,-radius,half,-radius,radius,half,-radius,radius,half,radius,-radius,half,radius,170,162,132,light); quad(pose,buffer,-radius,-half,radius,radius,-half,radius,radius,-half,-radius,-radius,-half,-radius,72,74,70,light); }
    private static void quad(final PoseStack.Pose pose, final VertexConsumer buffer, final float ax, final float ay, final float az, final float bx, final float by, final float bz, final float cx, final float cy, final float cz, final float dx, final float dy, final float dz, final int red, final int green, final int blue, final int light) { vertex(pose,buffer,ax,ay,az,0,0,red,green,blue,light); vertex(pose,buffer,bx,by,bz,1,0,red,green,blue,light); vertex(pose,buffer,cx,cy,cz,1,1,red,green,blue,light); vertex(pose,buffer,dx,dy,dz,0,1,red,green,blue,light); }
    private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer, final float x, final float y, final float z, final float u, final float v, final int red, final int green, final int blue, final int light) { buffer.addVertex(pose,x,y,z).setColor(red,green,blue,255).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,0,1,0); }
}
