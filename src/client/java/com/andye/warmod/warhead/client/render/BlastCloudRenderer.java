package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadVisualMath;
import com.andye.warmod.warhead.client.WarheadClientVisualProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class BlastCloudRenderer {
	private BlastCloudRenderer(){}
	public static void render(final PoseStack.Pose pose,final VertexConsumer buffer,final double age,final float visualScale,final WarheadClientVisualProfile p,final List<BlastCloudLobe> lobes,final WarheadMesh.Lod lod,final Quaternionf camera){if(age<p.smokeStartTick()||age>=p.cloudDissipationEndTick()||lobes==null)return;int limit=lod==WarheadMesh.Lod.NEAR?p.nearSmokeLobes():lod==WarheadMesh.Lod.MEDIUM?p.mediumSmokeLobes():p.farSmokeLobes();limit=Math.min(limit,lobes.size());double develop=smooth((age-p.smokeStartTick())/(double)Math.max(1,p.cloudRiseEndTick()-p.smokeStartTick()));double rise=smooth((age-p.cloudRiseStartTick())/(double)Math.max(1,p.cloudRiseEndTick()-p.cloudRiseStartTick()));double fade=Math.pow(1-WarheadVisualMath.clamp((age-p.cloudRiseEndTick())/(double)Math.max(1,p.cloudDissipationEndTick()-p.cloudRiseEndTick()),0,1),.72);double scale=p.payloadType()==WarheadPayloadType.NUCLEAR?1.0:Mth.clamp(visualScale,.55F,1.45F);for(int i=0;i<limit;i++){BlastCloudLobe l=lobes.get(i);Vec3 base=l.baseOffset();double horizontal=Math.sqrt(base.x*base.x+base.z*base.z),dx=horizontal<1E-4?Math.cos(l.rotation()):base.x/horizontal,dz=horizontal<1E-4?Math.sin(l.rotation()):base.z/horizontal;double capRoll=l.upperCap()?1+.11*Math.sin(l.phase()+age*.025):1;Vec3 center=new Vec3(base.x*develop*scale*capRoll,(base.y*develop+rise*(l.upperCap()?p.maximumCloudHeight()*.16:p.maximumCloudHeight()*.08)*l.riseFactor())*scale,base.z*develop*scale*capRoll).add(dx*l.outwardDrift()*rise,0,dz*l.outwardDrift()*rise);float radius=(float)(l.baseRadius()*(.45+.55*develop)*scale*(1+.08*Math.sin(l.phase()+age*.035)));float alpha=(float)(l.opacity()*(.65+.35*develop)*fade);billboard(pose,buffer,center,radius,l.rotation()+age*.0025,l.red(),l.green(),l.blue(),alpha,camera);}}
	public static Vec3 center(final BlastCloudLobe l,final double age,final float scale){double rise=smooth((age-35)/165.0);double horizontal=Math.sqrt(l.baseOffset().x*l.baseOffset().x+l.baseOffset().z*l.baseOffset().z),dx=horizontal<1E-4?Math.cos(l.rotation()):l.baseOffset().x/horizontal,dz=horizontal<1E-4?Math.sin(l.rotation()):l.baseOffset().z/horizontal;return l.baseOffset().scale(scale).add(dx*l.outwardDrift()*rise,rise*(l.upperCap()?34:30)*l.riseFactor(),dz*l.outwardDrift()*rise);}
	private static void billboard(final PoseStack.Pose p,final VertexConsumer b,final Vec3 c,final float radius,final double rotation,final int r,final int g,final int bl,final float alpha,final Quaternionf camera){float cos=Mth.cos((float)rotation),sin=Mth.sin((float)rotation),ux=cos*radius,uy=sin*radius,vx=-sin*radius,vy=cos*radius;int a=Mth.clamp((int)(alpha*255),0,255);vertex(p,b,c,-ux-vx,-uy-vy,0,1,r,g,bl,a,camera);vertex(p,b,c,-ux+vx,-uy+vy,0,0,r,g,bl,a,camera);vertex(p,b,c,ux+vx,uy+vy,1,0,r,g,bl,a,camera);vertex(p,b,c,ux-vx,uy-vy,1,1,r,g,bl,a,camera);}
	private static void vertex(final PoseStack.Pose p,final VertexConsumer b,final Vec3 c,final float x,final float y,final float u,final float v,final int r,final int g,final int bl,final int a,final Quaternionf camera){Vector3f o=new Vector3f(x,y,0).rotate(camera),n=new Vector3f(0,0,1).rotate(camera);b.addVertex(p,(float)c.x+o.x,(float)c.y+o.y,(float)c.z+o.z).setColor(r,g,bl,a).setUv(u,v).setOverlay(0).setLight(0xA000A0).setNormal(p,n.x,n.y,n.z);}
	private static double smooth(final double v){double t=WarheadVisualMath.clamp(v,0,1);return t*t*(3-2*t);}
}