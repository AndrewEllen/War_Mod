package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadConstants;
import com.andye.warmod.warhead.WarheadVisualMath;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.client.WarheadClientVisualProfile;
import com.andye.warmod.warhead.client.ClientWarheadVisualManager;
import com.andye.warmod.warhead.client.ImpactVisualState;
import com.andye.warmod.warhead.client.TerrainShockfrontNode;
import com.andye.warmod.warhead.client.TerrainShockfrontSpoke;
import com.andye.warmod.warhead.client.WarheadVisualState;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class WarheadWorldRenderer {
	private static final double NEAR_DISTANCE = 192.0;
	private static final double MEDIUM_DISTANCE = 640.0;
	private static final double MAX_DISTANCE = 1536.0;
	private static volatile RenderFrame currentFrame = RenderFrame.EMPTY;
	private static boolean registered;
	private WarheadWorldRenderer() { }

	public static void register() {
		if (registered) return;
		LevelExtractionEvents.END_EXTRACTION.register(WarheadWorldRenderer::extract);
		LevelRenderEvents.COLLECT_SUBMITS.register(WarheadWorldRenderer::collectSubmits);
		registered = true;
		if(SharedConstants.IS_RUNNING_IN_IDE)com.andye.warmod.WarMod.LOGGER.info("Re-entry visual configuration loaded");
	}

	private static void extract(final LevelExtractionContext context) {
		ClientLevel level = context.level();
		CameraRenderState camera = context.levelState().cameraRenderState;
		if (level == null || camera == null || camera.pos == null) { currentFrame = RenderFrame.EMPTY; return; }
		Vec3 cameraPosition = camera.pos;
		Quaternionf cameraOrientation = camera.orientation == null ? new Quaternionf() : new Quaternionf(camera.orientation);
		long gameTime = level.getGameTime();
		double partialTick = context.deltaTracker().getGameTimeDeltaPartialTick(true);
		ClientWarheadVisualManager.Snapshot snapshot = ClientWarheadVisualManager.INSTANCE.snapshot(level);
		List<WarheadFrame> warheads = new ArrayList<>();
		for (WarheadVisualState state : snapshot.warheads()) {
			if (state.isExpired(gameTime, partialTick)) continue;
			Vec3 position = state.positionAt(gameTime, partialTick), velocity = state.velocityAt(gameTime, partialTick);
			double distance = cameraPosition.distanceTo(position);
			if (!position.isFinite() || !velocity.isFinite() || !Double.isFinite(distance) || distance > MAX_DISTANCE) continue;
			double elapsed = state.elapsedTicks(gameTime, partialTick);
			warheads.add(new WarheadFrame(position, velocity, (float) state.progressAt(gameTime, partialTick), (float) elapsed,
				(float) Math.max(0.0, state.flightTicks() - elapsed), state.flightTicks(), state.visualSeed(), lod(distance), sampledLight(level, position)));
		}

		List<ImpactFrame> impacts = new ArrayList<>();
		for (ImpactVisualState state : snapshot.impacts()) {
			double age = state.ageTicks(gameTime, partialTick);
			if (state.isExpired(gameTime, partialTick)) continue;
			double distance = cameraPosition.distanceTo(state.impactPosition());
			if (!Double.isFinite(distance) || distance > MAX_DISTANCE) continue;
			WarheadMesh.Lod lod = lod(distance);
			double groundDistance = WarheadVisualMath.groundShockwaveDistance(age);
			int dustLimit = lod == WarheadMesh.Lod.NEAR ? 2_000 : lod == WarheadMesh.Lod.MEDIUM ? 1_000 : 400;
			List<TerrainShockfrontNode> dustNodes = state.terrainShockfrontField().activeDustNodes(groundDistance, frontierSpokeCount(lod), dustLimit, gameTime);
			for (TerrainShockfrontNode node : dustNodes) {
				if (node.state() == TerrainShockfrontNode.State.READY) state.terrainShockfrontField().markEmitted(node, gameTime);
			}
			impacts.add(new ImpactFrame(state.impactPosition(), age, state.visualScale(), state.payloadType(), state.profile(), lod,
				state.terrainShockfrontField().snapshotSpokes(), dustNodes, state.fireballLobes(), state.blastCloudLobes(), gameTime));
		}
		currentFrame = new RenderFrame(cameraPosition, cameraOrientation, List.copyOf(warheads), List.copyOf(impacts));
	}

	private static void collectSubmits(final LevelRenderContext context) {
		RenderFrame frame = currentFrame;
		if (frame == RenderFrame.EMPTY || (frame.warheads().isEmpty() && frame.impacts().isEmpty())) return;
		PoseStack poseStack = context.poseStack();
		if (poseStack == null) return;
		for (WarheadFrame warhead : frame.warheads()) renderWarhead(context, poseStack, frame.cameraPosition(), warhead);
		for (ImpactFrame impact : frame.impacts()) renderImpact(context, poseStack, frame, impact);
	}

	private static void renderWarhead(final LevelRenderContext context, final PoseStack poseStack, final Vec3 camera, final WarheadFrame warhead) {
		poseStack.pushPose();
		Vec3 relative = warhead.position().subtract(camera);
		poseStack.translate(relative.x, relative.y, relative.z);
		poseStack.mulPose(rotationToVelocity(warhead.velocity()));
		context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.PROJECTILE,
			(pose, buffer) -> WarheadMesh.render(pose, buffer, warhead.lod(), warhead.packedLight()));
		context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.CONE,
			(pose, buffer) -> ShockConeMesh.render(pose, buffer, warhead.lod(), warhead.progress(), warhead.elapsedTicks(), warhead.remainingTicks(), warhead.velocity(), warhead.visualSeed(), warhead.flightTicks()));
		context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.VAPOR_BAND,
			(pose, buffer) -> VaporBandRenderer.render(pose, buffer, warhead.lod(), warhead.elapsedTicks(), warhead.visualSeed(),warhead.progress(), (float) coneActivation(warhead), (float) WarheadVisualMath.coneFade(warhead.remainingTicks())));
		context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.REENTRY_PLASMA,
			(pose, buffer) -> ReentryHeatingRenderer.renderBowShock(pose,buffer,warhead.lod(),warhead.progress(),warhead.elapsedTicks(),warhead.remainingTicks(),warhead.velocity(),warhead.visualSeed()));
		context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.REENTRY_PLASMA,
			(pose, buffer) -> ReentryHeatingRenderer.renderGlow(pose,buffer,warhead.lod(),warhead.progress(),warhead.elapsedTicks(),warhead.remainingTicks(),warhead.velocity(),warhead.visualSeed()));
		context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.REENTRY_PLASMA,
			(pose, buffer) -> ReentryHeatingRenderer.renderFilaments(pose,buffer,warhead.lod(),warhead.progress(),warhead.elapsedTicks(),warhead.remainingTicks(),warhead.velocity(),warhead.visualSeed()));
		poseStack.popPose();
	}

	private static void renderImpact(final LevelRenderContext context, final PoseStack poseStack, final RenderFrame frame, final ImpactFrame impact) {
		Vec3 relative = impact.position().subtract(frame.cameraPosition());
		float thicknessScale=(float)impact.profile().shockwaveThicknessScale(),alphaScale=(float)impact.profile().shockwaveAlphaScale();
		double groundDistance = WarheadVisualMath.groundShockwaveDistance(impact.ageTicks());
		poseStack.pushPose();
		poseStack.translate(relative.x, relative.y, relative.z);
		context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.PRESSURE_SHELL,
			(pose, buffer) -> PressureWaveSphereRenderer.render(pose, buffer, WarheadVisualMath.airShockwaveRadius(impact.ageTicks()), impact.ageTicks(), thicknessScale, alphaScale, impact.lod()));
		context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.SHOCKWAVE,
			(pose, buffer) -> TerrainShockwaveRenderer.renderFrontier(pose, buffer, impact.shockfrontSpokes(), impact.position(), groundDistance,
				frontierSpokeCount(impact.lod()), groundFrontierWidth(impact.ageTicks(), thicknessScale), groundFrontierAlpha(impact.ageTicks(), alphaScale), 208, 226, 244));
		context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.SHOCKWAVE,
			(pose, buffer) -> TerrainShockwaveRenderer.renderFrontier(pose, buffer, impact.shockfrontSpokes(), impact.position(), Math.max(0.0, groundDistance - 3.0 * thicknessScale),
				frontierSpokeCount(impact.lod()), dustWidth(impact.ageTicks(), thicknessScale), dustAlpha(impact.ageTicks(), alphaScale), 130, 119, 108));
		poseStack.popPose();

		poseStack.pushPose();
		poseStack.translate(relative.x, relative.y, relative.z);
		context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.GROUND_DUST,
			(pose, buffer) -> GroundDustFrontRenderer.render(pose, buffer, impact.dustNodes(), impact.position(), impact.gameTime(), impact.lod(), (float)impact.profile().shockwaveParticleDensityScale(), frame.cameraOrientation()));
		context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.HEAVY_SMOKE,
			(pose, buffer) -> BlastCloudRenderer.render(pose, buffer, impact.ageTicks(), impact.visualScale(), impact.profile(), impact.blastCloudLobes(), impact.lod(), frame.cameraOrientation()));
		context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.FIREBALL_COOL,
			(pose, buffer) -> ImpactFireballRenderer.renderCooling(pose, buffer, impact.ageTicks(), impact.visualScale(), impact.profile(), impact.fireballLobes(), impact.lod(), frame.cameraOrientation()));
		context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.FIREBALL_HOT,
			(pose, buffer) -> ImpactFireballRenderer.renderHot(pose, buffer, impact.ageTicks(), impact.visualScale(), impact.profile(), impact.fireballLobes(), impact.lod(), frame.cameraOrientation()));
		if (impact.payloadType() == WarheadPayloadType.NUCLEAR) {
			context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.NUCLEAR_FLASH,
				(pose, buffer) -> NuclearFlashRenderer.render(pose, buffer, impact.ageTicks(), frame.cameraOrientation()));
		}
		poseStack.popPose();
	}

	private static double coneActivation(final WarheadFrame warhead) {
		double speed = WarheadVisualMath.normalizedSpeed(warhead.velocity(), WarheadConstants.TRAJECTORY_SPEED_BLOCKS_PER_TICK * 1.65);
		return WarheadVisualMath.coneActivation(speed) * WarheadVisualMath.coneAttack(warhead.elapsedTicks() - warhead.flightTicks() * 0.20);
	}
	private static int frontierSpokeCount(final WarheadMesh.Lod lod) { return lod == WarheadMesh.Lod.NEAR ? 256 : lod == WarheadMesh.Lod.MEDIUM ? 160 : 96; }
	private static WarheadMesh.Lod lod(final double distance) { return distance < NEAR_DISTANCE ? WarheadMesh.Lod.NEAR : distance < MEDIUM_DISTANCE ? WarheadMesh.Lod.MEDIUM : WarheadMesh.Lod.FAR; }
	private static int sampledLight(final ClientLevel level, final Vec3 position) {
		BlockPos block = BlockPos.containing(position);
		if (!level.hasChunkAt(block)) return LightCoordsUtil.pack(5, 5);
		int packed = LightCoordsUtil.getLightCoords(level, block);
		return LightCoordsUtil.pack(Math.max(5, LightCoordsUtil.block(packed)), Math.max(5, LightCoordsUtil.sky(packed)));
	}
	private static Quaternionf rotationToVelocity(final Vec3 velocity) {
		Vector3f direction = new Vector3f((float) velocity.x, (float) velocity.y, (float) velocity.z);
		if (!Float.isFinite(direction.x()) || !Float.isFinite(direction.y()) || !Float.isFinite(direction.z()) || direction.lengthSquared() < 1.0E-8F) return new Quaternionf();
		return new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), direction.normalize());
	}
	private static float groundFrontierWidth(final double age, final float scale) { return (float) WarheadVisualMath.airShockwaveThickness(age, scale); }
	private static float groundFrontierAlpha(final double age,final float scale) { return Mth.clamp((float)(WarheadVisualMath.groundShockwaveAlpha(age)*0.74*scale),0.0F,1.0F); }
	private static float dustWidth(final double age, final float scale) { return (float) (WarheadVisualMath.airShockwaveThickness(age, scale) * 2.3); }
	private static float dustAlpha(final double age,final float scale) { return Mth.clamp((float)(WarheadVisualMath.groundShockwaveAlpha(age)*0.58*scale),0.0F,1.0F); }

	private record WarheadFrame(Vec3 position, Vec3 velocity, float progress, float elapsedTicks, float remainingTicks,
		int flightTicks, long visualSeed, WarheadMesh.Lod lod, int packedLight) { }
	private record ImpactFrame(Vec3 position, double ageTicks, float visualScale, WarheadPayloadType payloadType, WarheadClientVisualProfile profile, WarheadMesh.Lod lod,
		List<TerrainShockfrontSpoke> shockfrontSpokes, List<TerrainShockfrontNode> dustNodes,
		List<FireballLobe> fireballLobes, List<BlastCloudLobe> blastCloudLobes, long gameTime) { }
	private record RenderFrame(Vec3 cameraPosition, Quaternionf cameraOrientation, List<WarheadFrame> warheads, List<ImpactFrame> impacts) {
		private static final RenderFrame EMPTY = new RenderFrame(Vec3.ZERO, new Quaternionf(), List.of(), List.of());
	}
}