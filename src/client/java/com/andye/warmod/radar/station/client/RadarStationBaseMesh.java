package com.andye.warmod.radar.station.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

public final class RadarStationBaseMesh {
    private RadarStationBaseMesh() { }

    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final int light, final boolean warning) {
        // A single centre renderer spans and visibly connects the complete 3x3 structure.
        box(pose,buffer,-.82F,.18F,-.82F,1.82F,.34F,1.82F,42,49,54,light);
        box(pose,buffer,-.68F,.34F,-.68F,-.46F,1.02F,-.46F,54,62,67,light);
        box(pose,buffer,1.46F,.34F,-.68F,1.68F,1.02F,-.46F,54,62,67,light);
        box(pose,buffer,-.68F,.34F,1.46F,-.46F,1.02F,1.68F,54,62,67,light);
        box(pose,buffer,1.46F,.34F,1.46F,1.68F,1.02F,1.68F,54,62,67,light);
        box(pose,buffer,.16F,.30F,.16F,.84F,.54F,.84F,48,56,61,light);
        box(pose,buffer,.31F,.52F,.31F,.69F,2.28F,.69F,61,70,75,light);
        box(pose,buffer,.20F,2.18F,.20F,.80F,2.38F,.80F,78,89,94,light);
        box(pose,buffer,.08F,.42F,.76F,.92F,1.15F,1.22F,37,44,49,light);
        box(pose,buffer,.15F,.58F,1.215F,.85F,.99F,1.24F,22,28,32,light);
        box(pose,buffer,.22F,.68F,1.24F,.61F,.84F,1.26F,184,129,34,15728880);
        box(pose,buffer,.68F,.69F,1.24F,.76F,.77F,1.26F,
            warning?230:45,warning?46:190,warning?35:82,15728880);
        // Four diagonal braces visually tie the mast into the footprint.
        box(pose,buffer,-.48F,.88F,-.48F,.37F,1.02F,.37F,49,58,63,light);
        box(pose,buffer,.63F,.88F,-.48F,1.48F,1.02F,.37F,49,58,63,light);
        box(pose,buffer,-.48F,.88F,.63F,.37F,1.02F,1.48F,49,58,63,light);
        box(pose,buffer,.63F,.88F,.63F,1.48F,1.02F,1.48F,49,58,63,light);
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