package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadConstants;
import com.andye.warmod.warhead.WarheadEffectMath;
import com.andye.warmod.warhead.WarheadVisualMath;
import com.andye.warmod.warhead.client.ClientWarheadVisualManager;
import com.andye.warmod.warhead.client.ImpactVisualState;
import com.andye.warmod.warhead.client.TerrainRingSampler;
import com.andye.warmod.warhead.client.TerrainRingSampler.RingKind;
import com.andye.warmod.warhead.client.TerrainRingSampler.RingSample;
import com.andye.warmod.warhead.client.TerrainShockfrontSpoke;
import com.andye.warmod.warhead.client.WarheadVisualState;
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

public final class WarheadWorldRenderer {
	private static final double NEAR_DISTANCE = 192.0;
	private static final double MEDIUM_DISTANCE = 640.0;
	private static final double MAX_DISTANCE = 1536.0;
	private static volatile RenderFrame currentFrame = RenderFrame.EMPTY;
	private static boolean registered;

	private WarheadWorldRenderer() {
	}

	public static void register() {
		if (registered) {
			return;
		}
		LevelExtractionEvents.END_EXTRACTION.register(WarheadWorldRenderer::extract);
		LevelRenderEvents.COLLECT_SUBMITS.register(WarheadWorldRenderer::collectSubmits);
		registered = true;
	}

	private static void extract(final LevelExtractionContext context) {
		ClientLevel level = context.level();
		CameraRenderState camera = context.levelState().cameraRenderState;
		if (level == null || camera == null || camera.pos == null) {
			currentFrame = RenderFrame.EMPTY;
			return;
		}

		Vec3 cameraPosition = camera.pos;
		Quaternionf cameraOrientation = camera.orientation == null ? new Quaternionf() : new Quaternionf(camera.orientation);
		long gameTime = level.getGameTime();
		double partialTick = context.deltaTracker().getGameTimeDeltaPartialTick(true);
		ClientWarheadVisualManager.Snapshot snapshot = ClientWarheadVisualManager.INSTANCE.snapshot(level);
		List<WarheadFrame> warheads = new ArrayList<>(snapshot.warheads().size());
		for (WarheadVisualState state : snapshot.warheads()) {
			if (state.isExpired(gameTime, partialTick)) {
				continue;
			}
			Vec3 position = state.positionAt(gameTime, partialTick);
			Vec3 velocity = state.velocityAt(gameTime, partialTick);
			double distance = cameraPosition.distanceTo(position);
			if (!position.isFinite() || !velocity.isFinite() || !Double.isFinite(distance) || distance > MAX_DISTANCE) {
				continue;
			}
			WarheadMesh.Lod lod = lod(distance);
			double elapsed = state.elapsedTicks(gameTime, partialTick);
			warheads.add(new WarheadFrame(
				position,
				velocity,
				(float) state.progressAt(gameTime, partialTick),
				(float) elapsed,
				(float) Math.max(0.0, state.flightTicks() - elapsed),
				state.flightTicks(),
				state.visualSeed(),
				lod,
				sampledLight(level, position)
			));
		}

		List<ImpactFrame> impacts = new ArrayList<>(snapshot.impacts().size());
		for (ImpactVisualState state : snapshot.impacts()) {
			double age = state.ageTicks(gameTime, partialTick);
			if (state.isExpired(gameTime, partialTick)) {
				continue;
			}
			double distance = cameraPosition.distanceTo(state.impactPosition());
			if (!Double.isFinite(distance) || distance > MAX_DISTANCE) {
				continue;
			}
			WarheadMesh.Lod lod = lod(distance);
			int pressureSegments = lod == WarheadMesh.Lod.NEAR ? 96 : lod == WarheadMesh.Lod.MEDIUM ? 48 : 24;
			double pressureRadius = WarheadVisualMath.pressureSphereRadius(age);
			double dustRadius = WarheadEffectMath.dustRingRadius(age, state.visualScale());
			List<RingSample> pressure = state.terrainSampler().sample(level, pressureRadius, pressureSegments, RingKind.PRESSURE, gameTime);
			List<RingSample> dust = state.terrainSampler().sample(level, dustRadius, pressureSegments, RingKind.DUST, gameTime);
			impacts.add(new ImpactFrame(
				state.impactPosition(),
				age,
				state.visualScale(),
				lod,
				pressure,
				dust,
				state.terrainShockfrontField().snapshotSpokes(),
				state.fireballLobes()
			));
		}
		currentFrame = new RenderFrame(cameraPosition, cameraOrientation, List.copyOf(warheads), List.copyOf(impacts));
	}

	private static void collectSubmits(final LevelRenderContext context) {
		RenderFrame frame = currentFrame;
		if (frame == RenderFrame.EMPTY || (frame.warheads().isEmpty() && frame.impacts().isEmpty())) {
			return;
		}
		PoseStack poseStack = context.poseStack();
		if (poseStack == null) {
			return;
		}

		for (WarheadFrame warhead : frame.warheads()) {
			poseStack.pushPose();
			Vec3 relative = warhead.position().subtract(frame.cameraPosition());
			poseStack.translate(relative.x, relative.y, relative.z);
			poseStack.mulPose(rotationToVelocity(warhead.velocity()));
			context.submitNodeCollector().submitCustomGeometry(
				poseStack,
				WarheadRenderPipelines.PROJECTILE,
				(pose, buffer) -> WarheadMesh.render(pose, buffer, warhead.lod(), warhead.packedLight())
			);
			context.submitNodeCollector().submitCustomGeometry(
				poseStack,
				WarheadRenderPipelines.CONE,
				(pose, buffer) -> ShockConeMesh.render(
					pose,
					buffer,
					warhead.lod(),
					warhead.progress(),
					warhead.elapsedTicks(),
					warhead.remainingTicks(),
					warhead.velocity(),
					warhead.visualSeed(),
					warhead.flightTicks()
				)
			);
			context.submitNodeCollector().submitCustomGeometry(
				poseStack,
				WarheadRenderPipelines.VAPOR_BAND,
				(pose, buffer) -> VaporBandRenderer.render(
					pose,
					buffer,
					warhead.lod(),
					warhead.elapsedTicks(),
					warhead.visualSeed(),
					(float) coneActivation(warhead),
					(float) WarheadVisualMath.coneFade(warhead.remainingTicks())
				)
			);
			poseStack.popPose();
		}

		for (ImpactFrame impact : frame.impacts()) {
			Vec3 relative = impact.position().subtract(frame.cameraPosition());
			poseStack.pushPose();
			poseStack.translate(relative.x, relative.y, relative.z);
			context.submitNodeCollector().submitCustomGeometry(
				poseStack,
				WarheadRenderPipelines.PRESSURE_SHELL,
				(pose, buffer) -> PressureWaveSphereRenderer.render(pose, buffer, impact.ageTicks(), impact.visualScale(), impact.lod())
			);
			context.submitNodeCollector().submitCustomGeometry(
				poseStack,
				WarheadRenderPipelines.SHOCKWAVE,
				(pose, buffer) -> TerrainShockwaveRenderer.renderFrontier(
					pose,
					buffer,
					impact.shockfrontSpokes(),
					impact.position(),
					WarheadVisualMath.groundShockwaveRadius(impact.ageTicks()),
					frontierSpokeCount(impact.lod()),
					groundFrontierWidth(impact.ageTicks(), impact.visualScale()),
					groundFrontierAlpha(impact.ageTicks()),
					208,
					226,
					244
				)
			);
			context.submitNodeCollector().submitCustomGeometry(
				poseStack,
				WarheadRenderPipelines.SHOCKWAVE,
				(pose, buffer) -> TerrainShockwaveRenderer.renderFrontier(
					pose,
					buffer,
					impact.shockfrontSpokes(),
					impact.position(),
					WarheadVisualMath.pressureSphereRadius(impact.ageTicks()),
					frontierSpokeCount(impact.lod()),
					frontierWidth(impact.ageTicks(), impact.visualScale()),
					frontierAlpha(impact.ageTicks()),
					226,
					239,
					251
				)
			);
			context.submitNodeCollector().submitCustomGeometry(
				poseStack,
				WarheadRenderPipelines.SHOCKWAVE,
				(pose, buffer) -> TerrainShockwaveRenderer.render(
					pose,
					buffer,
					impact.pressureSamples(),
					impact.position(),
					pressureWidth(impact.ageTicks(), impact.visualScale()),
					pressureAlpha(impact.ageTicks()) * 0.32F,
					224,
					236,
					248
				)
			);
			context.submitNodeCollector().submitCustomGeometry(
				poseStack,
				WarheadRenderPipelines.SHOCKWAVE,
				(pose, buffer) -> TerrainShockwaveRenderer.render(
					pose,
					buffer,
					impact.dustSamples(),
					impact.position(),
					dustWidth(impact.ageTicks(), impact.visualScale()),
					dustAlpha(impact.ageTicks()),
					130,
					119,
					108
				)
			);
			poseStack.popPose();

			poseStack.pushPose();
			poseStack.translate(relative.x, relative.y, relative.z);
			poseStack.mulPose(new Quaternionf(frame.cameraOrientation()));
			context.submitNodeCollector().submitCustomGeometry(
				poseStack,
				WarheadRenderPipelines.SMOKE_LOBE,
				(pose, buffer) -> RisingBlastCloudRenderer.render(pose, buffer, impact.ageTicks(), impact.visualScale(), impact.fireballLobes(), impact.lod())
			);
			context.submitNodeCollector().submitCustomGeometry(
				poseStack,
				WarheadRenderPipelines.SMOKE_LOBE,
				(pose, buffer) -> AftermathSmokeColumnRenderer.render(pose, buffer, impact.ageTicks(), impact.visualScale(), impact.fireballLobes(), impact.lod())
			);
			context.submitNodeCollector().submitCustomGeometry(
				poseStack,
				WarheadRenderPipelines.FIREBALL_COOL,
				(pose, buffer) -> ImpactFireballRenderer.renderCooling(pose, buffer, impact.ageTicks(), impact.visualScale(), impact.fireballLobes(), impact.lod())
			);
			context.submitNodeCollector().submitCustomGeometry(
				poseStack,
				WarheadRenderPipelines.FIREBALL_HOT,
				(pose, buffer) -> ImpactFireballRenderer.renderHot(pose, buffer, impact.ageTicks(), impact.visualScale(), impact.fireballLobes(), impact.lod())
			);
			poseStack.popPose();
		}
	}

	private static double coneActivation(final WarheadFrame warhead) {
		double speed = WarheadVisualMath.normalizedSpeed(warhead.velocity(), WarheadConstants.TRAJECTORY_SPEED_BLOCKS_PER_TICK * 1.65);
		return WarheadVisualMath.coneActivation(speed) * WarheadVisualMath.coneAttack(warhead.elapsedTicks() - warhead.flightTicks() * 0.20);
	}

	private static int frontierSpokeCount(final WarheadMesh.Lod lod) {
		return lod == WarheadMesh.Lod.NEAR ? 64 : lod == WarheadMesh.Lod.MEDIUM ? 40 : 24;
	}

	private static WarheadMesh.Lod lod(final double distance) {
		if (distance < NEAR_DISTANCE) {
			return WarheadMesh.Lod.NEAR;
		}
		if (distance < MEDIUM_DISTANCE) {
			return WarheadMesh.Lod.MEDIUM;
		}
		return WarheadMesh.Lod.FAR;
	}

	private static int sampledLight(final ClientLevel level, final Vec3 position) {
		BlockPos block = BlockPos.containing(position);
		if (!level.hasChunkAt(block)) {
			return LightCoordsUtil.pack(5, 5);
		}
		int packed = LightCoordsUtil.getLightCoords(level, block);
		return LightCoordsUtil.pack(Math.max(5, LightCoordsUtil.block(packed)), Math.max(5, LightCoordsUtil.sky(packed)));
	}

	private static Quaternionf rotationToVelocity(final Vec3 velocity) {
		Vector3f direction = new Vector3f((float) velocity.x, (float) velocity.y, (float) velocity.z);
		if (!Float.isFinite(direction.x()) || !Float.isFinite(direction.y()) || !Float.isFinite(direction.z()) || direction.lengthSquared() < 1.0E-8F) {
			return new Quaternionf();
		}
		direction.normalize();
		return new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), direction);
	}

	private static float pressureWidth(final double ageTicks, final float scale) {
		double t = WarheadVisualMath.clamp(ageTicks / 24.0, 0.0, 1.0);
		return (float) (WarheadVisualMath.clamp(scale, 0.05F, 2.0F) * (1.0 + 1.4 * t));
	}

	private static float pressureAlpha(final double ageTicks) {
		return (float) (0.68 * Math.pow(1.0 - WarheadVisualMath.clamp(ageTicks / 24.0, 0.0, 1.0), 1.2));
	}

	private static float groundFrontierWidth(final double ageTicks, final float scale) {
		double progress = WarheadVisualMath.clamp(ageTicks / 32.0, 0.0, 1.0);
		return (float) (WarheadVisualMath.clamp(scale, 0.45F, 1.5F) * (1.4 + 2.8 * progress));
	}

	private static float groundFrontierAlpha(final double ageTicks) {
		return (float) (WarheadVisualMath.groundShockwaveAlpha(ageTicks) * 0.78);
	}

	private static float frontierWidth(final double ageTicks, final float scale) {
		return (float) (WarheadVisualMath.clamp(scale, 0.45F, 1.5F) * (0.7 + 0.9 * WarheadVisualMath.clamp(ageTicks / 24.0, 0.0, 1.0)));
	}

	private static float frontierAlpha(final double ageTicks) {
		return (float) (0.64 * Math.pow(1.0 - WarheadVisualMath.clamp(ageTicks / 24.0, 0.0, 1.0), 1.7));
	}

	private static float dustWidth(final double ageTicks, final float scale) {
		double t = WarheadVisualMath.clamp((ageTicks - 5.0) / 47.0, 0.0, 1.0);
		return (float) (WarheadVisualMath.clamp(scale, 0.05F, 2.0F) * (3.0 + 1.8 * t));
	}

	private static float dustAlpha(final double ageTicks) {
		double t = WarheadVisualMath.clamp((ageTicks - 5.0) / 47.0, 0.0, 1.0);
		return (float) (0.42 * Math.pow(1.0 - t, 0.72));
	}

	private record WarheadFrame(
		Vec3 position,
		Vec3 velocity,
		float progress,
		float elapsedTicks,
		float remainingTicks,
		int flightTicks,
		long visualSeed,
		WarheadMesh.Lod lod,
		int packedLight
	) {
	}

	private record ImpactFrame(
		Vec3 position,
		double ageTicks,
		float visualScale,
		WarheadMesh.Lod lod,
		List<RingSample> pressureSamples,
		List<RingSample> dustSamples,
		List<TerrainShockfrontSpoke> shockfrontSpokes,
		List<FireballLobe> fireballLobes
	) {
	}

	private record RenderFrame(
		Vec3 cameraPosition,
		Quaternionf cameraOrientation,
		List<WarheadFrame> warheads,
		List<ImpactFrame> impacts
	) {
		private static final RenderFrame EMPTY = new RenderFrame(Vec3.ZERO, new Quaternionf(), List.of(), List.of());
	}
}