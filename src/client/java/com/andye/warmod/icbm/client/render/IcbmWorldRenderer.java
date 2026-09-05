package com.andye.warmod.icbm.client.render;

import com.andye.warmod.WarMod;
import com.andye.warmod.client.model.BlockbenchModelRenderType;
import com.andye.warmod.icbm.IcbmTrajectory;
import com.andye.warmod.icbm.client.ClientIcbmVisualManager;
import com.andye.warmod.icbm.client.IcbmLaunchGroundSmokeManager;
import com.andye.warmod.icbm.client.IcbmTrailSample;
import com.andye.warmod.icbm.client.IcbmVisualState;
import com.andye.warmod.icbm.client.SpentIcbmStageState;
import com.andye.warmod.warhead.WarheadDeliveryMode;
import com.andye.warmod.warhead.WarheadYield;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class IcbmWorldRenderer {
	private static final Set<UUID> LOGGED_LONG_RANGE = new HashSet<>();
	private static volatile Frame frame = Frame.EMPTY;
	private static boolean registered;
	private IcbmWorldRenderer() { }

	public static void register() {
		if (registered) return;
		LevelExtractionEvents.END_EXTRACTION.register(IcbmWorldRenderer::extract);
		LevelRenderEvents.COLLECT_SUBMITS.register(IcbmWorldRenderer::render);
		registered = true;
	}

	private static void extract(final LevelExtractionContext context) {
		ClientLevel level = context.level();
		CameraRenderState camera = context.levelState().cameraRenderState;
		if (level == null || camera == null || camera.pos == null) { frame = Frame.EMPTY; return; }
		Vec3 cameraPos = camera.pos;
		Quaternionf orientation = camera.orientation == null ? new Quaternionf() : new Quaternionf(camera.orientation);
		long time = level.getGameTime();
		double partial = context.deltaTracker().getGameTimeDeltaPartialTick(true);
		ClientIcbmVisualManager.Snapshot snapshot = ClientIcbmVisualManager.INSTANCE.snapshot(level);
		IcbmLaunchGroundSmokeManager.Snapshot cloudSnapshot =
			IcbmLaunchGroundSmokeManager.INSTANCE.snapshot(level);
		List<LaunchCloudFrame> launchClouds = new ArrayList<>();
		for (IcbmLaunchGroundSmokeManager.LaunchCloud cloud : cloudSnapshot.clouds()) {
			BlockPos anchor = BlockPos.containing(cloud.position());
			if (!level.hasChunkAt(anchor) || cameraPos.distanceToSqr(cloud.position()) > camera.depthFar * camera.depthFar)
				continue;
			launchClouds.add(new LaunchCloudFrame(cloud));
		}
		launchClouds.sort(java.util.Comparator.comparingDouble(
			(LaunchCloudFrame cloud) -> cameraPos.distanceToSqr(cloud.cloud().position())).reversed());
		List<MissileFrame> missiles = new ArrayList<>();
		for (IcbmVisualState state : snapshot.missiles()) {
			Vec3 position = state.position(time, partial);
			Vec3 velocity = state.velocity(time, partial);
			if (!position.isFinite() || !velocity.isFinite()) continue;
			IcbmLongRangeRenderContext renderContext = IcbmLongRangeRenderContext.create(cameraPos, position, camera.depthFar);
			logLongRangeOnce(state.flightPlan().missileId(), renderContext.transform());
			double elapsed = state.elapsed(time, partial);
			missiles.add(new MissileFrame(position, velocity, IcbmTrajectory.thrustActive(state.flightPlan(), elapsed),
				elapsed, state.flightPlan().visualSeed(), state.yield(), state.deliveryMode(),
				renderContext, light(level, position), state.trail(time, partial)));
		}
		List<StageFrame> stages = new ArrayList<>();
		for (SpentIcbmStageState state : snapshot.spentStages()) {
			Vec3 position = state.position(time, partial);
			if (!position.isFinite()) continue;
			IcbmLongRangeRenderContext renderContext = IcbmLongRangeRenderContext.create(cameraPos, position, camera.depthFar);
			stages.add(new StageFrame(position, state.age(time, partial), state.orientationVelocity(), state.rollDrift(),
				state.alpha(time, partial), state.yield(), state.deliveryMode(), renderContext, light(level, position)));
		}
		frame = new Frame(cameraPos, orientation, List.copyOf(missiles), List.copyOf(stages),
			List.copyOf(launchClouds), cloudSnapshot.visualTime());
	}

	private static void render(final LevelRenderContext context) {
		Frame current = frame;
		if (current == Frame.EMPTY) return;
		PoseStack stack = context.poseStack();
		if (stack == null) return;
		for (MissileFrame missile : current.missiles()) {
			IcbmLongRangeTransform transform = missile.renderContext().transform();
			stack.pushPose();
			Vec3 relative = transform.renderedCenter().subtract(current.camera());
			stack.translate(relative.x, relative.y, relative.z);
			stack.mulPose(rotation(missile.velocity()));
			float compression = (float) transform.compression();
			stack.scale(compression, compression, compression);
			context.submitNodeCollector().submitCustomGeometry(stack, BlockbenchModelRenderType.SOLID,
				(pose, buffer) -> IcbmMissileMesh.render(pose, buffer, missile.yield(), missile.deliveryMode(),
					missile.renderContext().lod(), missile.light()));
			if (missile.thrust()) {
				context.submitNodeCollector().submitCustomGeometry(stack, IcbmRenderPipelines.EXHAUST_CORE,
					(pose, buffer) -> IcbmExhaustRenderer.renderCore(pose, buffer, missile.seed(),
						missile.elapsed(), missile.renderContext().lod()));
				context.submitNodeCollector().submitCustomGeometry(stack, IcbmRenderPipelines.EXHAUST_FRINGE,
					(pose, buffer) -> IcbmExhaustRenderer.renderFringe(pose, buffer, missile.seed(),
						missile.elapsed(), missile.renderContext().lod()));
			}
			stack.popPose();
			if (!missile.trail().isEmpty()) {
				stack.pushPose();
				stack.translate(-current.camera().x, -current.camera().y, -current.camera().z);
				context.submitNodeCollector().submitCustomGeometry(stack, IcbmRenderPipelines.SMOKE,
					(pose, buffer) -> IcbmSmokeTrailRenderer.render(pose, buffer, missile.trail(),
						missile.renderContext(), current.orientation()));
				stack.popPose();
			}
		}
		if (!current.launchClouds().isEmpty()) {
			QuadParticleRenderState particles = new QuadParticleRenderState();
			for (LaunchCloudFrame cloud : current.launchClouds()) {
				IcbmLaunchGroundSmokeRenderer.append(particles, cloud.cloud(),
					cloud.cloud().elapsed(current.visualTime()), current.camera(), current.orientation());
			}
			// The native translucent-particle phase runs after water terrain and
			// uses the appropriate particle framebuffer in Fabulous rendering.
			context.submitNodeCollector().submitQuadParticleGroup(particles);
		}
		for (StageFrame stage : current.stages()) {
			IcbmLongRangeTransform transform = stage.renderContext().transform();
			stack.pushPose();
			Vec3 relative = transform.renderedCenter().subtract(current.camera());
			stack.translate(relative.x, relative.y, relative.z);
			stack.mulPose(rotation(stage.orientationVelocity()));
			stack.mulPose(new Quaternionf().rotateY((float) (stage.age() * stage.rollDrift())));
			float compression = (float) transform.compression();
			stack.scale(compression, compression, compression);
			context.submitNodeCollector().submitCustomGeometry(stack, BlockbenchModelRenderType.TRANSLUCENT,
				(pose, buffer) -> SpentIcbmStageRenderer.render(pose, buffer,
					stage.yield(), stage.deliveryMode(), stage.light(), stage.alpha()));
			stack.popPose();
		}
	}

	private static void logLongRangeOnce(final UUID id, final IcbmLongRangeTransform transform) {
		if (!transform.compressed() || !SharedConstants.IS_RUNNING_IN_IDE) return;
		synchronized (LOGGED_LONG_RANGE) {
			if (!LOGGED_LONG_RANGE.add(id)) return;
		}
		WarMod.LOGGER.info("ICBM {} long-range render active: actualDistance={}, transformedDistance={}, mode=compressed",
			id, transform.actualDistance(), transform.renderedDistance());
	}

	private static int light(final ClientLevel level, final Vec3 position) {
		BlockPos block = BlockPos.containing(position);
		if (!level.hasChunkAt(block)) return LightCoordsUtil.pack(5, 5);
		int light = LightCoordsUtil.getLightCoords(level, block);
		return LightCoordsUtil.pack(Math.max(5, LightCoordsUtil.block(light)), Math.max(5, LightCoordsUtil.sky(light)));
	}

	private static Quaternionf rotation(final Vec3 velocity) {
		Vector3f direction = new Vector3f((float) velocity.x, (float) velocity.y, (float) velocity.z);
		return direction.lengthSquared() < 1.0E-8F ? new Quaternionf()
			: new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), direction.normalize());
	}

	private record MissileFrame(Vec3 position, Vec3 velocity, boolean thrust, double elapsed, long seed,
		WarheadYield yield, WarheadDeliveryMode deliveryMode,
		IcbmLongRangeRenderContext renderContext, int light,
		List<IcbmTrailSample> trail) { }
	private record StageFrame(Vec3 position, double age, Vec3 orientationVelocity,
		float rollDrift, float alpha, WarheadYield yield, WarheadDeliveryMode deliveryMode,
		IcbmLongRangeRenderContext renderContext, int light) { }
	private record LaunchCloudFrame(IcbmLaunchGroundSmokeManager.LaunchCloud cloud) { }
	private record Frame(Vec3 camera, Quaternionf orientation, List<MissileFrame> missiles,
		List<StageFrame> stages, List<LaunchCloudFrame> launchClouds, double visualTime) {
		private static final Frame EMPTY = new Frame(Vec3.ZERO, new Quaternionf(), List.of(), List.of(),
			List.of(), 0.0);
	}
}
