package com.andye.warmod.radar.station.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

public final class RadarDishMesh {
    private static final int SEGMENTS = 28;
    private static final int RINGS = 8;
    private static final float RADIUS = 1.15F;
    private static final float DEPTH = 0.38F;

    private RadarDishMesh() { }

    public static void renderMount(final PoseStack.Pose pose, final VertexConsumer buffer,
        final int light, final boolean warning) {
        box(pose,buffer,-.34F,-.15F,-.34F,.34F,.02F,.34F,42,49,54,light);
        box(pose,buffer,-.27F,.00F,-.25F,-.15F,.58F,.18F,58,67,72,light);
        box(pose,buffer,.15F,.00F,-.25F,.27F,.58F,.18F,58,67,72,light);
        box(pose,buffer,-.18F,.43F,-.18F,.18F,.60F,.18F,76,86,91,light);
        box(pose,buffer,-.13F,.12F,-.62F,.13F,.34F,-.28F,38,45,50,light);
        box(pose,buffer,-.09F,.17F,-.78F,.09F,.29F,-.62F,
            warning?213:160,warning?52:106,warning?38:34,light);
    }
    // Local dish forward is +Z, up is +Y and right is +X.
    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final int light, final boolean warning) {
        for (int ring = 0; ring < RINGS; ring++) {
            float r1 = RADIUS * ring / RINGS;
            float r2 = RADIUS * (ring + 1) / RINGS;
            float z1 = -DEPTH * square(r1 / RADIUS);
            float z2 = -DEPTH * square(r2 / RADIUS);
            for (int index = 0; index < SEGMENTS; index++) {
                double angle = Math.PI * 2.0 * index / SEGMENTS;
                double next = Math.PI * 2.0 * (index + 1) / SEGMENTS;
                float ax=(float)Math.cos(angle)*r1, ay=(float)Math.sin(angle)*r1;
                float bx=(float)Math.cos(angle)*r2, by=(float)Math.sin(angle)*r2;
                float cx=(float)Math.cos(next)*r2, cy=(float)Math.sin(next)*r2;
                float dx=(float)Math.cos(next)*r1, dy=(float)Math.sin(next)*r1;
                quad(pose,buffer,ax,ay,z1,bx,by,z2,cx,cy,z2,dx,dy,z1,116,128,132,light);
                if (ring == RINGS - 1) {
                    quad(pose,buffer,bx,by,z2,bx,by,z2-.08F,cx,cy,z2-.08F,cx,cy,z2,
                        73,82,87,light);
                }
            }
        }
        renderRearRibs(pose, buffer, light);
        renderFeedAssembly(pose, buffer, light, warning);
    }

    private static void renderRearRibs(final PoseStack.Pose pose,
        final VertexConsumer buffer, final int light) {
        for (int rib = 0; rib < 8; rib++) {
            double angle = Math.PI * 2.0 * rib / 8.0;
            float x=(float)Math.cos(angle)*RADIUS;
            float y=(float)Math.sin(angle)*RADIUS;
            quad(pose,buffer,-.04F,-.04F,-.10F,.04F,.04F,-.10F,
                x,y,-DEPTH-.07F,x*.94F,y*.94F,-DEPTH+.02F,57,64,69,light);
        }
        box(pose,buffer,-.18F,-.18F,-.58F,.18F,.18F,-.10F,48,55,60,light);
        box(pose,buffer,-.26F,-.22F,-.82F,.26F,.22F,-.58F,41,47,52,light);
    }

    private static void renderFeedAssembly(final PoseStack.Pose pose,
        final VertexConsumer buffer, final int light, final boolean warning) {
        float focal = 0.58F;
        for (int arm = 0; arm < 4; arm++) {
            double angle = Math.PI * 2.0 * arm / 4.0;
            float x=(float)Math.cos(angle)*.78F;
            float y=(float)Math.sin(angle)*.78F;
            quad(pose,buffer,x-.025F,y-.025F,-.22F,x+.025F,y+.025F,-.22F,
                .04F,.04F,focal-.10F,-.04F,-.04F,focal-.10F,68,76,80,light);
        }
        box(pose,buffer,-.09F,-.09F,focal-.16F,.09F,.09F,focal+.10F,81,91,95,light);
        box(pose,buffer,-.16F,-.16F,focal+.08F,.16F,.16F,focal+.22F,
            warning?215:181,warning?52:128,warning?39:39,15728880);
    }

    private static float square(final float value) { return value * value; }

    private static void box(final PoseStack.Pose p,final VertexConsumer b,
        final float x1,final float y1,final float z1,final float x2,final float y2,final float z2,
        final int r,final int g,final int bl,final int light) {
        quad(p,b,x1,y1,z1,x2,y1,z1,x2,y2,z1,x1,y2,z1,r,g,bl,light);
        quad(p,b,x2,y1,z2,x1,y1,z2,x1,y2,z2,x2,y2,z2,r,g,bl,light);
        quad(p,b,x1,y1,z2,x1,y1,z1,x1,y2,z1,x1,y2,z2,r,g,bl,light);
        quad(p,b,x2,y1,z1,x2,y1,z2,x2,y2,z2,x2,y2,z1,r,g,bl,light);
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
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p,0,0,1);
    }
}