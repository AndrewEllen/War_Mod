package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadVisualMath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class NuclearFlashRenderer {
	private NuclearFlashRenderer(){}
	public static void render(final PoseStack.Pose pose,final VertexConsumer buffer,final double age,final Quaternionf camera){if(age<0||age>=18)return;double radius=age<=4?Mth.lerp(age/4.0,8,80):Mth.lerp((age-4)/14.0,80,150);double alpha=Math.pow(1-WarheadVisualMath.clamp(age/18.0,0,1),.45);billboard(pose,buffer,Vec3.ZERO,(float)radius,0,alpha,camera);billboard(pose,buffer,Vec3.ZERO,(float)(radius*.78),Math.PI*.25,alpha*.9,camera);}
	private static void billboard(final PoseStack.Pose p,final VertexConsumer b,final Vec3 c,final float r,final double rotation,final double alpha,final Quaternionf camera){float cos=Mth.cos((float)rotation),sin=Mth.sin((float)rotation),ux=cos*r,uy=sin*r,vx=-sin*r,vy=cos*r;int a=Mth.clamp((int)(alpha*255),0,255);vertex(p,b,c,-ux-vx,-uy-vy,0,1,a,camera);vertex(p,b,c,-ux+vx,-uy+vy,0,0,a,camera);vertex(p,b,c,ux+vx,uy+vy,1,0,a,camera);vertex(p,b,c,ux-vx,uy-vy,1,1,a,camera);}
	private static void vertex(final PoseStack.Pose p,final VertexConsumer b,final Vec3 c,final float x,final float y,final float u,final float v,final int a,final Quaternionf camera){Vector3f o=new Vector3f(x,y,0).rotate(camera),n=new Vector3f(0,0,1).rotate(camera);b.addVertex(p,(float)c.x+o.x,(float)c.y+o.y,(float)c.z+o.z).setColor(255,250,220,a).setUv(u,v).setOverlay(0).setLight(0xF000F0).setNormal(p,n.x,n.y,n.z);}
}
