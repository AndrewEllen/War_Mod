package com.andye.warmod.rocket.client;

import com.andye.warmod.client.model.BlockbenchGameplayMeshes;
import com.andye.warmod.client.model.BlockbenchGameplayMeshes.Model;
import com.andye.warmod.icbm.client.render.IcbmLongRangeRenderContext;
import com.andye.warmod.icbm.client.render.IcbmMissileMesh;
import com.andye.warmod.icbm.client.render.IcbmPayloadAppearance;
import com.andye.warmod.rocket.RocketPayloadType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

public final class RocketProjectileMesh {
    private RocketProjectileMesh() { }

    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final RocketProjectileRenderState state) {
        if (state.payloadType != RocketPayloadType.HE) {
            IcbmLongRangeRenderContext.Lod detail = switch (state.lod) {
                case NEAR -> IcbmLongRangeRenderContext.Lod.NEAR;
                case MEDIUM -> IcbmLongRangeRenderContext.Lod.MEDIUM;
                case FAR -> IcbmLongRangeRenderContext.Lod.EXTREME;
            };
            IcbmPayloadAppearance appearance = state.payloadType == RocketPayloadType.NUCLEAR_ICBM
                ? IcbmPayloadAppearance.NUCLEAR : IcbmPayloadAppearance.CONVENTIONAL;
            IcbmMissileMesh.render(pose, buffer, appearance, detail, state.lightCoords);
            return;
        }
        float scale = switch (state.lod) {
            case NEAR -> 0.050F;
            case MEDIUM -> 0.057F;
            case FAR -> 0.070F;
        };
        BlockbenchGameplayMeshes.render(pose, buffer, Model.HE_ROCKET, scale,
            0.0F, 0.0F, 0.0F, state.lightCoords);
    }

    private static void renderFins(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float radius, final float half, final int light) {
        for (int index = 0; index < 4; index++) {
            boolean xAxis = index < 2;
            float sign = index % 2 == 0 ? -1 : 1;
            if (xAxis) box(pose, buffer, sign * radius, -half * 0.9F, -0.025F,
                sign * (radius + 0.13F), -half * 0.42F, 0.025F, 53, 60, 49, light);
            else box(pose, buffer, -0.025F, -half * 0.9F, sign * radius,
                0.025F, -half * 0.42F, sign * (radius + 0.13F), 53, 60, 49, light);
        }
    }

    private static void pyramid(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float radius, final float base, final float tip, final int red, final int green,
        final int blue, final int light) {
        triangle(pose,buffer,-radius,base,-radius,radius,base,-radius,0,tip,0,red,green,blue,light);
        triangle(pose,buffer,radius,base,-radius,radius,base,radius,0,tip,0,red,green,blue,light);
        triangle(pose,buffer,radius,base,radius,-radius,base,radius,0,tip,0,red,green,blue,light);
        triangle(pose,buffer,-radius,base,radius,-radius,base,-radius,0,tip,0,red,green,blue,light);
    }

    private static void box(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float x1, final float y1, final float z1, final float x2, final float y2, final float z2,
        final int red, final int green, final int blue, final int light) {
        quad(pose,buffer,x1,y1,z1,x2,y1,z1,x2,y2,z1,x1,y2,z1,red,green,blue,light);
        quad(pose,buffer,x2,y1,z2,x1,y1,z2,x1,y2,z2,x2,y2,z2,red,green,blue,light);
        quad(pose,buffer,x1,y1,z2,x1,y1,z1,x1,y2,z1,x1,y2,z2,red,green,blue,light);
        quad(pose,buffer,x2,y1,z1,x2,y1,z2,x2,y2,z2,x2,y2,z1,red,green,blue,light);
        quad(pose,buffer,x1,y2,z1,x2,y2,z1,x2,y2,z2,x1,y2,z2,red,green,blue,light);
        quad(pose,buffer,x1,y1,z2,x2,y1,z2,x2,y1,z1,x1,y1,z1,red,green,blue,light);
    }

    private static void triangle(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float ax,final float ay,final float az,final float bx,final float by,final float bz,
        final float cx,final float cy,final float cz,final int red,final int green,final int blue,final int light) {
        vertex(pose,buffer,ax,ay,az,0,0,red,green,blue,light);
        vertex(pose,buffer,bx,by,bz,1,0,red,green,blue,light);
        vertex(pose,buffer,cx,cy,cz,.5F,1,red,green,blue,light);
    }

    private static void quad(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float ax,final float ay,final float az,final float bx,final float by,final float bz,
        final float cx,final float cy,final float cz,final float dx,final float dy,final float dz,
        final int red,final int green,final int blue,final int light) {
        vertex(pose,buffer,ax,ay,az,0,0,red,green,blue,light);
        vertex(pose,buffer,bx,by,bz,1,0,red,green,blue,light);
        vertex(pose,buffer,cx,cy,cz,1,1,red,green,blue,light);
        vertex(pose,buffer,dx,dy,dz,0,1,red,green,blue,light);
    }

    private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float x,final float y,final float z,final float u,final float v,
        final int red,final int green,final int blue,final int light) {
        buffer.addVertex(pose,x,y,z).setColor(red,green,blue,255).setUv(u,v)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,0,1,0);
    }
}
