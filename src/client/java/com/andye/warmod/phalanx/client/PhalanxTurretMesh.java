package com.andye.warmod.phalanx.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

public final class PhalanxTurretMesh {
    private PhalanxTurretMesh() { }
    public static void renderStaticBase(PoseStack.Pose p, VertexConsumer b, int light, boolean enabled) { int glow=enabled?120:55; box(p,b,.08F,.02F,.08F,.92F,.16F,.92F,50,56,59,light); box(p,b,.16F,.16F,.16F,.84F,.68F,.84F,64,71,75,light); box(p,b,.24F,.68F,.24F,.76F,1.04F,.76F,glow,glow+8,glow+10,light); }
    public static void renderYawHousing(PoseStack.Pose p, VertexConsumer b, int light) { box(p,b,-.36F,-.13F,-.34F,.36F,.34F,.34F,82,91,96,light); }
    public static void renderCradle(PoseStack.Pose p, VertexConsumer b, int light) { box(p,b,-.24F,-.15F,-.20F,.24F,.15F,.24F,66,74,78,light); }
    public static void renderBarrels(PoseStack.Pose p, VertexConsumer b, int light, float spinDegrees) { double phase=Math.toRadians(spinDegrees); float radius=.105F; for(int index=0;index<6;index++){double angle=phase+index*Math.PI/3.0;float x=(float)Math.cos(angle)*radius,y=(float)Math.sin(angle)*radius;box(p,b,x-.026F,y-.026F,.10F,x+.026F,y+.026F,1.12F,38,44,47,light);} }
    public static void renderMuzzleFlash(PoseStack.Pose p, VertexConsumer b, int light) { int full=0xF000F0; box(p,b,-.11F,-.11F,1.16F,.11F,.11F,1.39F,255,128,30,full); box(p,b,-.055F,-.055F,1.38F,.055F,.055F,1.57F,255,248,190,full); }
    private static void box(PoseStack.Pose p,VertexConsumer b,float x1,float y1,float z1,float x2,float y2,float z2,int r,int g,int bl,int light){quad(p,b,x1,y1,z1,x2,y1,z1,x2,y2,z1,x1,y2,z1,r,g,bl,light);quad(p,b,x2,y1,z2,x1,y1,z2,x1,y2,z2,x2,y2,z2,r,g,bl,light);quad(p,b,x1,y1,z2,x1,y1,z1,x1,y2,z1,x1,y2,z2,r,g,bl,light);quad(p,b,x2,y1,z1,x2,y1,z2,x2,y2,z2,x2,y2,z1,r,g,bl,light);quad(p,b,x1,y1,z2,x2,y1,z2,x2,y1,z1,x1,y1,z1,r,g,bl,light);quad(p,b,x1,y2,z1,x2,y2,z1,x2,y2,z2,x1,y2,z2,r,g,bl,light);}
    private static void quad(PoseStack.Pose p,VertexConsumer b,float ax,float ay,float az,float bx,float by,float bz,float cx,float cy,float cz,float dx,float dy,float dz,int r,int g,int bl,int light){v(p,b,ax,ay,az,r,g,bl,light);v(p,b,bx,by,bz,r,g,bl,light);v(p,b,cx,cy,cz,r,g,bl,light);v(p,b,dx,dy,dz,r,g,bl,light);}
    private static void v(PoseStack.Pose p,VertexConsumer b,float x,float y,float z,int r,int g,int bl,int light){b.addVertex(p,x,y,z).setColor(r,g,bl,255).setUv(0,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p,0,1,0);}
}