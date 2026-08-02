package com.andye.warmod.icbm.client.render;

import com.andye.warmod.warhead.client.render.WarheadMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

public final class IcbmMissileMesh {
	private IcbmMissileMesh() { }
	public static void render(final PoseStack.Pose pose, final VertexConsumer buffer, final WarheadMesh.Lod lod, final int light) {
		render(pose, buffer, lod, light, 255);
	}
	public static void render(final PoseStack.Pose pose, final VertexConsumer buffer, final WarheadMesh.Lod lod,
		final int light, final int alpha) {
		int sides = lod == WarheadMesh.Lod.NEAR ? 12 : lod == WarheadMesh.Lod.MEDIUM ? 8 : 6;
		float bottom = -2.6F, bodyTop = 1.7F, nose = 2.6F, radius = 0.52F;
		for (int index = 0; index < sides; index++) {
			float angle = Mth.TWO_PI * index / sides, nextAngle = Mth.TWO_PI * (index + 1) / sides;
			float x = radius * Mth.cos(angle), z = radius * Mth.sin(angle);
			float nextX = radius * Mth.cos(nextAngle), nextZ = radius * Mth.sin(nextAngle);
			quad(pose, buffer, x,bottom,z, x,bodyTop,z, nextX,bodyTop,nextZ, nextX,bottom,nextZ,
				42,46,51,alpha,light,Mth.cos(angle),0,Mth.sin(angle));
			quad(pose, buffer, x,bodyTop,z, 0,nose,0, 0,nose,0, nextX,bodyTop,nextZ,
				62,67,74,alpha,light,Mth.cos(angle),.4F,Mth.sin(angle));
		}
		for (int fin = 0; fin < 4; fin++) {
			float angle = Mth.TWO_PI * fin / 4, x = Mth.cos(angle), z = Mth.sin(angle);
			quad(pose, buffer, x*radius,-2.35F,z*radius, x*1.05F,-2.25F,z*1.05F,
				x*.9F,-1.25F,z*.9F, x*radius,-1.45F,z*radius, 36,40,46,alpha,light,x,0,z);
		}
	}
	private static void quad(final PoseStack.Pose p, final VertexConsumer b,
		final float x1,final float y1,final float z1, final float x2,final float y2,final float z2,
		final float x3,final float y3,final float z3, final float x4,final float y4,final float z4,
		final int red,final int green,final int blue,final int alpha,final int light,
		final float nx,final float ny,final float nz) {
		vertex(p,b,x1,y1,z1,0,1,red,green,blue,alpha,light,nx,ny,nz);
		vertex(p,b,x2,y2,z2,0,0,red,green,blue,alpha,light,nx,ny,nz);
		vertex(p,b,x3,y3,z3,1,0,red,green,blue,alpha,light,nx,ny,nz);
		vertex(p,b,x4,y4,z4,1,1,red,green,blue,alpha,light,nx,ny,nz);
	}
	private static void vertex(final PoseStack.Pose p, final VertexConsumer b, final float x,final float y,final float z,
		final float u,final float v,final int red,final int green,final int blue,final int alpha,final int light,
		final float nx,final float ny,final float nz) {
		b.addVertex(p,x,y,z).setColor(red,green,blue,alpha).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(light).setNormal(p,nx,ny,nz);
	}
}