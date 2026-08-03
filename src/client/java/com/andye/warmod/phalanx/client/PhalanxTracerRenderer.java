package com.andye.warmod.phalanx.client;

import com.andye.warmod.phalanx.PhalanxBulletTrajectory;
import com.andye.warmod.warhead.client.render.WarheadRenderPipelines;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

public final class PhalanxTracerRenderer {
    private record Streak(Vec3 start, Vec3 end, float alpha) { }
    private record Frame(Vec3 camera, List<Streak> streaks) { static final Frame EMPTY = new Frame(Vec3.ZERO, List.of()); }
    private static volatile Frame frame = Frame.EMPTY; private static boolean registered;
    private PhalanxTracerRenderer() { }
    public static void register() { if (registered) return; LevelExtractionEvents.END_EXTRACTION.register(PhalanxTracerRenderer::extract); LevelRenderEvents.COLLECT_SUBMITS.register(PhalanxTracerRenderer::render); registered=true; }
    private static void extract(LevelExtractionContext context) { CameraRenderState camera=context.levelState().cameraRenderState; if(context.level()==null||camera==null||camera.pos==null){frame=Frame.EMPTY;return;} double now=context.level().getGameTime()+context.deltaTracker().getGameTimeDeltaPartialTick(true); ArrayList<Streak> streaks=new ArrayList<>(); for(var tracer:PhalanxTracerManager.snapshot(context.level().getGameTime())) { double age=Math.max(0,now-tracer.startTime()); Vec3 end=PhalanxBulletTrajectory.position(tracer.origin(),tracer.velocity(),age); Vec3 start=PhalanxBulletTrajectory.position(tracer.origin(),tracer.velocity(),Math.max(0,age-.35)); if(start.isFinite()&&end.isFinite()) streaks.add(new Streak(start,end,(float)Math.max(.08,1.0-age/6.0))); } frame=new Frame(camera.pos,List.copyOf(streaks)); }
    private static void render(LevelRenderContext context) { Frame current=frame; if(current==Frame.EMPTY||context.poseStack()==null)return; for(Streak streak:current.streaks()){PoseStack stack=context.poseStack();stack.pushPose();Vec3 offset=streak.start().subtract(current.camera());stack.translate(offset.x,offset.y,offset.z);Vec3 direction=streak.end().subtract(streak.start());context.submitNodeCollector().submitCustomGeometry(stack,WarheadRenderPipelines.NUCLEAR_FLASH,(pose,buffer)->ribbons(pose,buffer,direction,streak.alpha()));stack.popPose();} }
    private static void ribbons(PoseStack.Pose pose, VertexConsumer buffer, Vec3 direction, float alpha) { if(direction.lengthSqr()<1.0E-8)return; Vec3 forward=direction.normalize(); Vec3 sideA=forward.cross(new Vec3(0,1,0)); if(sideA.lengthSqr()<1.0E-8)sideA=new Vec3(1,0,0); sideA=sideA.normalize().scale(.035); Vec3 sideB=forward.cross(sideA).normalize().scale(.035); ribbon(pose,buffer,direction,sideA,alpha); ribbon(pose,buffer,direction,sideB,alpha); }
    private static void ribbon(PoseStack.Pose p,VertexConsumer b,Vec3 end,Vec3 side,float alpha){int lead=(int)(255*alpha),trail=(int)(55*alpha);v(p,b,side,255,248,190,lead);v(p,b,side.scale(-1),255,248,190,lead);v(p,b,end.subtract(side),255,126,25,trail);v(p,b,end.add(side),255,126,25,trail);}
    private static void v(PoseStack.Pose p,VertexConsumer b,Vec3 v,int r,int g,int bl,int a){b.addVertex(p,(float)v.x,(float)v.y,(float)v.z).setColor(r,g,bl,a).setUv(0,0).setOverlay(0).setLight(0xF000F0).setNormal(p,0,1,0);}
}