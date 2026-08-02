package com.andye.warmod.icbm.client.render;

import com.andye.warmod.icbm.IcbmTrajectory;
import com.andye.warmod.icbm.IcbmConstants;
import com.andye.warmod.icbm.client.ClientIcbmVisualManager;
import com.andye.warmod.icbm.client.IcbmTrailSample;
import com.andye.warmod.icbm.client.IcbmVisualState;
import com.andye.warmod.icbm.client.SpentIcbmStageState;
import com.andye.warmod.warhead.client.render.WarheadMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class IcbmWorldRenderer {
	private static volatile Frame frame=Frame.EMPTY;private static boolean registered;private IcbmWorldRenderer(){}
	public static void register(){if(registered)return;LevelExtractionEvents.END_EXTRACTION.register(IcbmWorldRenderer::extract);LevelRenderEvents.COLLECT_SUBMITS.register(IcbmWorldRenderer::render);registered=true;}
	private static void extract(final LevelExtractionContext context){ClientLevel level=context.level();CameraRenderState camera=context.levelState().cameraRenderState;if(level==null||camera==null||camera.pos==null){frame=Frame.EMPTY;return;}Vec3 cameraPos=camera.pos;Quaternionf orientation=camera.orientation==null?new Quaternionf():new Quaternionf(camera.orientation);long time=level.getGameTime();double partial=context.deltaTracker().getGameTimeDeltaPartialTick(true);ClientIcbmVisualManager.Snapshot snapshot=ClientIcbmVisualManager.INSTANCE.snapshot(level);List<MissileFrame> missiles=new ArrayList<>();for(IcbmVisualState state:snapshot.missiles()){Vec3 pos=state.position(time,partial),velocity=state.velocity(time,partial);double distance=cameraPos.distanceTo(pos);if(!pos.isFinite()||!velocity.isFinite()||distance>IcbmConstants.VISUAL_RANGE_BLOCKS)continue;double elapsed=state.elapsed(time,partial);missiles.add(new MissileFrame(pos,velocity,IcbmTrajectory.thrustActive(state.flightPlan(),elapsed),elapsed,state.flightPlan().visualSeed(),lod(distance),light(level,pos),state.trail(time,partial)));}List<StageFrame> stages=new ArrayList<>();for(SpentIcbmStageState state:snapshot.spentStages()){Vec3 pos=state.position(time,partial);double distance=cameraPos.distanceTo(pos);if(!pos.isFinite()||distance>IcbmConstants.VISUAL_RANGE_BLOCKS)continue;stages.add(new StageFrame(pos,state.age(time,partial),state.orientationVelocity(),state.rollDrift(),state.alpha(time,partial),lod(distance),light(level,pos)));}frame=new Frame(cameraPos,orientation,List.copyOf(missiles),List.copyOf(stages));}
	private static void render(final LevelRenderContext context){Frame f=frame;if(f==Frame.EMPTY)return;PoseStack stack=context.poseStack();if(stack==null)return;for(MissileFrame m:f.missiles()){stack.pushPose();Vec3 rel=m.position.subtract(f.camera);stack.translate(rel.x,rel.y,rel.z);stack.mulPose(rotation(m.velocity));context.submitNodeCollector().submitCustomGeometry(stack,IcbmRenderPipelines.MISSILE,(p,b)->IcbmMissileMesh.render(p,b,m.lod,m.light));if(m.thrust)context.submitNodeCollector().submitCustomGeometry(stack,IcbmRenderPipelines.EXHAUST,(p,b)->IcbmExhaustRenderer.render(p,b,m.seed,m.elapsed));stack.popPose();if(!m.trail.isEmpty()){stack.pushPose();stack.translate(-f.camera.x,-f.camera.y,-f.camera.z);context.submitNodeCollector().submitCustomGeometry(stack,IcbmRenderPipelines.SMOKE,(p,b)->IcbmSmokeTrailRenderer.render(p,b,m.trail,m.lod,f.orientation));stack.popPose();}}
		for(StageFrame s:f.stages()){stack.pushPose();Vec3 rel=s.position.subtract(f.camera);stack.translate(rel.x,rel.y,rel.z);stack.mulPose(rotation(s.orientationVelocity));stack.mulPose(new Quaternionf().rotateY((float)(s.age*s.rollDrift)));context.submitNodeCollector().submitCustomGeometry(stack,IcbmRenderPipelines.SPENT_STAGE,(p,b)->SpentIcbmStageRenderer.render(p,b,s.lod,s.light,s.alpha));stack.popPose();}}
	private static WarheadMesh.Lod lod(final double d){return d<192?WarheadMesh.Lod.NEAR:d<640?WarheadMesh.Lod.MEDIUM:WarheadMesh.Lod.FAR;}private static int light(final ClientLevel l,final Vec3 p){BlockPos b=BlockPos.containing(p);if(!l.hasChunkAt(b))return LightCoordsUtil.pack(5,5);int v=LightCoordsUtil.getLightCoords(l,b);return LightCoordsUtil.pack(Math.max(5,LightCoordsUtil.block(v)),Math.max(5,LightCoordsUtil.sky(v)));}private static Quaternionf rotation(final Vec3 v){Vector3f d=new Vector3f((float)v.x,(float)v.y,(float)v.z);return d.lengthSquared()<1E-8?new Quaternionf():new Quaternionf().rotationTo(new Vector3f(0,1,0),d.normalize());}
	private record MissileFrame(Vec3 position,Vec3 velocity,boolean thrust,double elapsed,long seed,WarheadMesh.Lod lod,int light,List<IcbmTrailSample> trail){}private record StageFrame(Vec3 position,double age,Vec3 orientationVelocity,float rollDrift,float alpha,WarheadMesh.Lod lod,int light){}private record Frame(Vec3 camera,Quaternionf orientation,List<MissileFrame> missiles,List<StageFrame> stages){private static final Frame EMPTY=new Frame(Vec3.ZERO,new Quaternionf(),List.of(),List.of());}
}
