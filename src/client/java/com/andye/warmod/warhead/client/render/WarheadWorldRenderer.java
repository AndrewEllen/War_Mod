package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadEffectMath;
import com.andye.warmod.warhead.client.ClientWarheadVisualManager;
import com.andye.warmod.warhead.client.ImpactVisualState;
import com.andye.warmod.warhead.client.TerrainRingSampler;
import com.andye.warmod.warhead.client.TerrainRingSampler.RingKind;
import com.andye.warmod.warhead.client.TerrainRingSampler.RingSample;
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
			warheads.add(new WarheadFrame(
				position,
				velocity,
				(float) state.progressAt(gameTime, partialTick),
				(float) Math.max(0.0, state.elapsedTicks(gameTime, partialTick) * -1.0 + state.flightTicks()),
				lod
			));
		}

		List<ImpactFrame> impacts = new ArrayList<>(snapshot.impacts().size());
		int pressureSegments;
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
			pressureSegments = lod == WarheadMesh.Lod.NEAR ? 96 : lod == WarheadMesh.Lod.MEDIUM ? 48 : 24;
			double pressureRadius = WarheadEffectMath.pressureRingRadius(age, state.visualScale());
			double dustRadius = WarheadEffectMath.dustRingRadius(age, state.visualScale());
			List<RingSample> pressure = state.terrainSampler().sample(level, pressureRadius, pressureSegments, RingKind.PRESSURE, gameTime);
			List<RingSample> dust = state.terrainSampler().sample(level, dustRadius, pressureSegments, RingKind.DUST, gameTime);
			impacts.add(new ImpactFrame(
				state.impactPosition(),
				age,
				state.visualSeed(),
				state.visualScale(),
				lod,
				pressure,
				dust
			));
		}
		currentFrame = new RenderFrame(
			cameraPosition,
			cameraOrientation,
			gameTime,
			List.copyOf(warheads),
			List.copyOf(impacts)
		);
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
				(pose, buffer) -> WarheadMesh.render(pose, buffer, warhead.lod())
			);
			context.submitNodeCollector().submitCustomGeometry(
				poseStack,
				WarheadRenderPipelines.CONE,
				(pose, buffer) -> ShockConeMesh.render(
					pose,
					buffer,
					warhead.lod(),
					warhead.progress(),
					warhead.remainingTicks()
				)
			);
			poseStack.popPose();
		}

		for (ImpactFrame impact : frame.impacts()) {
			poseStack.pushPose();
			Vec3 relative = impact.position().subtract(frame.cameraPosition());
			poseStack.translate(relative.x, relative.y, relative.z);
			poseStack.mulPose(new Quaternionf(frame.cameraOrientation()));
			context.submitNodeCollector().submitCustomGeometry(
				poseStack,
				WarheadRenderPipelines.FIREBALL,
				(pose, buffer) -> ImpactFireballRenderer.render(
					pose,
					buffer,
					impact.ageTicks(),
					impact.visualScale(),
					impact.visualSeed(),
					impact.lod()
				)
			);
			poseStack.popPose();

			poseStack.pushPose();
			poseStack.translate(relative.x, relative.y, relative.z);
			context.submitNodeCollector().submitCustomGeometry(
				poseStack,
				WarheadRenderPipelines.SHOCKWAVE,
				(pose, buffer) -> TerrainShockwaveRenderer.render(
					pose,
					buffer,
					impact.pressureSamples(),
					impact.position(),
					pressureWidth(impact.ageTicks(), impact.visualScale()),
					pressureAlpha(impact.ageTicks()),
					224,
					230,
					232
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
					125,
					113,
					103
				)
			);
			poseStack.popPose();
		}
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

	private static Quaternionf rotationToVelocity(final Vec3 velocity) {
		Vector3f direction = new Vector3f((float) velocity.x, (float) velocity.y, (float) velocity.z);
		if (!Float.isFinite(direction.x()) || !Float.isFinite(direction.y()) || !Float.isFinite(direction.z()) || direction.lengthSquared() < 1.0E-8F) {
			return new Quaternionf();
		}
		direction.normalize();
		return new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), direction);
	}

	private static float pressureWidth(final double ageTicks, final float scale) {
		double t = WarheadEffectMath.clamp((ageTicks - 2.0) / 30.0, 0.0, 1.0);
		return (float) (WarheadEffectMath.clamp(scale, 0.05F, 8.0F) * (1.5 + 2.0 * t));
	}

	private static float pressureAlpha(final double ageTicks) {
		double t = WarheadEffectMath.clamp((ageTicks - 2.0) / 30.0, 0.0, 1.0);
		return (float) (0.78 * Math.pow(1.0 - t, 0.72));
	}

	private static float dustWidth(final double ageTicks, final float scale) {
		double t = WarheadEffectMath.clamp((ageTicks - 5.0) / 47.0, 0.0, 1.0);
		return (float) (WarheadEffectMath.clamp(scale, 0.05F, 8.0F) * (4.0 + 2.0 * t));
	}

	private static float dustAlpha(final double ageTicks) {
		double t = WarheadEffectMath.clamp((ageTicks - 5.0) / 47.0, 0.0, 1.0);
		return (float) (0.48 * Math.pow(1.0 - t, 0.64));
	}

	private record WarheadFrame(
		Vec3 position,
		Vec3 velocity,
		float progress,
		float remainingTicks,
		WarheadMesh.Lod lod
	) {
	}

	private record ImpactFrame(
		Vec3 position,
		double ageTicks,
		long visualSeed,
		float visualScale,
		WarheadMesh.Lod lod,
		List<RingSample> pressureSamples,
		List<RingSample> dustSamples
	) {
	}

	private record RenderFrame(
		Vec3 cameraPosition,
		Quaternionf cameraOrientation,
		long gameTime,
		List<WarheadFrame> warheads,
		List<ImpactFrame> impacts
	) {
		private static final RenderFrame EMPTY = new RenderFrame(
			Vec3.ZERO,
			new Quaternionf(),
			Long.MIN_VALUE,
			List.of(),
			List.of()
		);
	}
}