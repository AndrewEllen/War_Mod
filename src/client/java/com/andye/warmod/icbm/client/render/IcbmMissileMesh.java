package com.andye.warmod.icbm.client.render;

import com.andye.warmod.warhead.client.render.WarheadMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

public final class IcbmMissileMesh {private IcbmMissileMesh(){}public static void render(final PoseStack.Pose p,final VertexConsumer b,final WarheadMesh.Lod lod,final int light){int sides=lod==WarheadMesh.Lod.NEAR?12:lod==WarheadMesh.Lod.MEDIUM?8:6;float bottom=-2.6F,bodyTop=1.7F,nose=2.6F,r=.52F;for(int i=0;i<sides;i++){float a=Mth.TWO_PI*i/sides,n=Mth.TWO_PI*(i+1)/sides,x=r*Mth.cos(a),z=r*Mth.sin(a),nx=r*Mth.cos(n),nz=r*Mth.sin(n);quad(p,b,x,bottom,z,x,bodyTop,z,nx,bodyTop,nz,nx,bottom,nz,42,46,51,light,Mth.cos(a),0,Mth.sin(a));quad(p,b,x,bodyTop,z,0,nose,0,0,nose,0,nx,bodyTop,nz,62,67,74,light,Mth.cos(a),.4F,Mth.sin(a));}for(int fin=0;fin<4;fin++){float a=Mth.TWO_PI*fin/4,x=Mth.cos(a),z=Mth.sin(a);quad(p,b,x*r,-2.35F,z*r,x*1.05F,-2.25F,z*1.05F,x*.9F,-1.25F,z*.9F,x*r,-1.45F,z*r,36,40,46,light,x,0,z);} }
	private static void quad(final PoseStack.Pose p,final VertexConsumer b,final float x1,final float y1,final float z1,final float x2,final float y2,final float z2,final float x3,final float y3,final float z3,final float x4,final float y4,final float z4,final int r,final int g,final int bl,final int light,final float nx,final float ny,final float nz){v(p,b,x1,y1,z1,0,1,r,g,bl,light,nx,ny,nz);v(p,b,x2,y2,z2,0,0,r,g,bl,light,nx,ny,nz);v(p,b,x3,y3,z3,1,0,r,g,bl,light,nx,ny,nz);v(p,b,x4,y4,z4,1,1,r,g,bl,light,nx,ny,nz);}private static void v(final PoseStack.Pose p,final VertexConsumer b,final float x,final float y,final float z,final float u,final float vv,final int r,final int g,final int bl,final int light,final float nx,final float ny,final float nz){b.addVertex(p,x,y,z).setColor(r,g,bl,255).setUv(u,vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p,nx,ny,nz);}}
