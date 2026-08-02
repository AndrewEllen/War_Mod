package com.andye.warmod.radar.station.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

public final class RadarStationBaseMesh {
    private RadarStationBaseMesh() { }

    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final int light, final boolean warning) {
        box(pose,buffer,.12F,.34F,.12F,.88F,.52F,.88F,49,56,61,light);
        box(pose,buffer,.30F,.50F,.30F,.70F,1.30F,.70F,57,65,70,light);
        box(pose,buffer,.22F,1.28F,.22F,.78F,1.48F,.78F,77,86,91,light);
        box(pose,buffer,.10F,.42F,.72F,.90F,1.12F,1.16F,39,46,51,light);
        box(pose,buffer,.18F,.58F,1.155F,.82F,.96F,1.18F,24,30,34,light);
        box(pose,buffer,.24F,.66F,1.18F,.62F,.82F,1.20F,184,129,34,15728880);
        box(pose,buffer,.68F,.66F,1.18F,.74F,.72F,1.20F,
            warning?220:55,warning?48:180,warning?38:75,15728880);
        box(pose,buffer,.10F,.46F,-.16F,.34F,.86F,.18F,46,53,58,light);
        box(pose,buffer,.66F,.46F,-.16F,.90F,.86F,.18F,46,53,58,light);
    }

    private static void box(final PoseStack.Pose p, final VertexConsumer b,
        final float x1,final float y1,final float z1,final float x2,final float y2,final float z2,
        final int r,final int g,final int bl,final int light) {
        quad(p,b,x1,y1,z1,x2,y1,z1,x2,y2,z1,x1,y2,z1,r,g,bl,light);
        quad(p,b,x2,y1,z2,x1,y1,z2,x1,y2,z2,x2,y2,z2,r,g,bl,light);
        quad(p,b,x1,y1,z2,x1,y1,z1,x1,y2,z1,x1,y2,z2,r,g,bl,light);
        quad(p,b,x2,y1,z1,x2,y1,z2,x2,y2,z2,x2,y2,z1,r,g,bl,light);
        quad(p,b,x1,y2,z1,x2,y2,z1,x2,y2,z2,x1,y2,z2,r,g,bl,light);
        quad(p,b,x1,y1,z2,x2,y1,z2,x2,y1,z1,x1,y1,z1,r,g,bl,light);
    }

    private static void quad(final PoseStack.Pose p,final VertexConsumer b,
        final float ax,final float ay,final float az,final float bx,final float by,final float bz,
        final float cx,final float cy,final float cz,final float dx,final float dy,final float dz,
        final int r,final int g,final int bl,final int light) {
        vertex(p,b,ax,ay,az,r,g,bl,light);vertex(p,b,bx,by,bz,r,g,bl,light);
        vertex(p,b,cx,cy,cz,r,g,bl,light);vertex(p,b,dx,dy,dz,r,g,bl,light);
    }

    private static void vertex(final PoseStack.Pose p,final VertexConsumer b,
        final float x,final float y,final float z,final int r,final int g,final int bl,final int light) {
        b.addVertex(p,x,y,z).setColor(r,g,bl,255).setUv(0,0)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p,0,1,0);
    }
}