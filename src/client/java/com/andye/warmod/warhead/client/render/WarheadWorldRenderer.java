package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadConstants;
import com.andye.warmod.warhead.WarheadEffectProfile;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadVisualMath;
import com.andye.warmod.warhead.WarheadYieldScaling;
import com.andye.warmod.warhead.client.ClientDebrisBatchManager;
import com.andye.warmod.warhead.client.ClientWarheadVisualManager;
import com.andye.warmod.warhead.client.ImpactVisualState;
import com.andye.warmod.warhead.client.TerrainShockfrontNode;
import com.andye.warmod.warhead.client.TerrainShockfrontSpoke;
import com.andye.warmod.warhead.client.WarheadClientVisualProfile;
import com.andye.warmod.warhead.client.WarheadVisualState;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Active War Mod world render submission path. */
public final class WarheadWorldRenderer {
	private static final double NEAR_DISTANCE = 192.0;
	private static final double MEDIUM_DISTANCE = 640.0;
	private static final double MAX_DISTANCE = 1536.0;
	private static volatile RenderFrame currentFrame = RenderFrame.EMPTY;
	private static boolean registered;
	private static long lastDebugTick = Long.MIN_VALUE;

	private WarheadWorldRenderer() { }

	public static void register() {
		if (registered) return;
		LevelExtractionEvents.END_EXTRACTION.register(WarheadWorldRenderer::extract);
		LevelRenderEvents.COLLECT_SUBMITS.register(WarheadWorldRenderer::collectSubmits);
		registered = true;
		if (SharedConstants.IS_RUNNING_IN_IDE) {
			com.andye.warmod.WarMod.LOGGER.info("Warhead renderer loaded; backend={}", debugSnapshot().activeRenderBackend());
		}
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

		List<WarheadFrame> warheads = new ArrayList<>();
		for (WarheadVisualState state : snapshot.warheads()) {
			if (state.isExpired(gameTime, partialTick)) continue;
			Vec3 position = state.positionAt(gameTime, partialTick);
			Vec3 velocity = state.velocityAt(gameTime, partialTick);
			double distance = cameraPosition.distanceTo(position);
			if (!position.isFinite() || !velocity.isFinite() || !Double.isFinite(distance) || distance > MAX_DISTANCE) continue;
			double elapsed = state.elapsedTicks(gameTime, partialTick);
			warheads.add(new WarheadFrame(position, velocity, (float) state.progressAt(gameTime, partialTick),
				(float) elapsed, (float) Math.max(0.0, state.flightTicks() - elapsed), state.flightTicks(),
				state.visualSeed(), lod(distance), sampledLight(level, position)));
		}

		List<ImpactFrame> impacts = new ArrayList<>();
		for (ImpactVisualState state : snapshot.impacts()) {
			double age = state.ageTicks(gameTime, partialTick);
			if (state.isExpired(gameTime, partialTick)) continue;
			double distance = cameraPosition.distanceTo(state.impactPosition());
			if (!Double.isFinite(distance) || distance > MAX_DISTANCE) continue;
			WarheadMesh.Lod impactLod = lod(distance);
			float yieldRadiusScale = WarheadYieldScaling.radiusScale(state.payloadType(), state.visualScale());
			double groundDistance = WarheadVisualMath.groundShockwaveDistance(age, yieldRadiusScale);
			int dustLimit = impactLod == WarheadMesh.Lod.NEAR ? 2_000 : impactLod == WarheadMesh.Lod.MEDIUM ? 1_000 : 400;
			List<TerrainShockfrontNode> dustNodes = groundEffects(state.effectProfile())
				? state.terrainShockfrontField().activeDustNodes(groundDistance, frontierSpokeCount(impactLod), dustLimit, gameTime)
				: List.of();
			for (TerrainShockfrontNode node : dustNodes) {
				if (node.state() == TerrainShockfrontNode.State.READY) state.terrainShockfrontField().markEmitted(node, gameTime);
			}
			impacts.add(new ImpactFrame(state.warheadId(), state.impactPosition(), age, state.visualScale(),
				state.visualSeed(), state.payloadType(), state.effectProfile(),
				ClientWarheadVisualManager.INSTANCE.shouldRenderVolumetrics(state.warheadId()), state.profile(),
				impactLod, state.terrainShockfrontField().snapshotSpokes(), dustNodes, gameTime));
		}

		List<DebrisFrame> debris = new ArrayList<>();
		for (ClientDebrisBatchManager.RenderSample sample : ClientDebrisBatchManager.INSTANCE.snapshot(
			level, gameTime, partialTick, cameraPosition, MAX_DISTANCE)) {
			BlockPos blockPosition = BlockPos.containing(sample.position());
			MovingBlockRenderState movingBlock = new MovingBlockRenderState();
			movingBlock.randomSeedPos = blockPosition;
			movingBlock.blockPos = blockPosition;
			movingBlock.blockState = sample.state();
			movingBlock.biome = level.hasChunkAt(blockPosition) ? level.getBiome(blockPosition) : null;
			movingBlock.cardinalLighting = CardinalLighting.DEFAULT;
			movingBlock.lightEngine = LevelLightEngine.EMPTY;
			debris.add(new DebrisFrame(sample, movingBlock, 0xD8D8D2));
		}
		currentFrame = new RenderFrame(cameraPosition, cameraOrientation, List.copyOf(warheads),
			List.copyOf(impacts), List.copyOf(debris));

		if (SharedConstants.IS_RUNNING_IN_IDE && gameTime != lastDebugTick && gameTime % 100L == 0L) {
			lastDebugTick = gameTime;
			DebugSnapshot debug = debugSnapshot();
			com.andye.warmod.WarMod.LOGGER.info(
				"Stage8 particles={} spawned/tick={} culled={} debris={} backend={}",
				debug.activeParticles(), debug.spawnedParticlesPerTick(), debug.culledParticles(),
				debug.activeDebrisFragments(), debug.activeRenderBackend());
		}
	}

	private static void collectSubmits(final LevelRenderContext context) {
		RenderFrame frame = currentFrame;
		if (frame == RenderFrame.EMPTY || (frame.warheads().isEmpty() && frame.impacts().isEmpty() && frame.debris().isEmpty())) return;
		PoseStack poseStack = context.poseStack();
		if (poseStack == null) return;
		for (WarheadFrame warhead : frame.warheads()) renderWarhead(context, poseStack, frame.cameraPosition(), warhead);
		for (ImpactFrame impact : frame.impacts()) renderImpact(context, poseStack, frame, impact);
		renderDebris(context, poseStack, frame);
	}

	private static void renderWarhead(final LevelRenderContext context, final PoseStack poseStack,
		final Vec3 camera, final WarheadFrame warhead) {
		poseStack.pushPose();
		Vec3 relative = warhead.position().subtract(camera);
		poseStack.translate(relative.x, relative.y, relative.z);
		poseStack.mulPose(rotationToVelocity(warhead.velocity()));
		context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.PROJECTILE,
			(pose, buffer) -> WarheadMesh.render(pose, buffer, warhead.lod(), warhead.packedLight()));
		context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.CONE,
			(pose, buffer) -> ShockConeMesh.render(pose, buffer, warhead.lod(), warhead.progress(),
				warhead.elapsedTicks(), warhead.remainingTicks(), warhead.velocity(), warhead.visualSeed(), warhead.flightTicks()));
		context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.VAPOR_BAND,
			(pose, buffer) -> VaporBandRenderer.render(pose, buffer, warhead.lod(), warhead.elapsedTicks(),
				warhead.visualSeed(), warhead.progress(), (float) coneActivation(warhead),
				(float) WarheadVisualMath.coneFade(warhead.remainingTicks())));
		context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.REENTRY_PLASMA,
			(pose, buffer) -> ReentryHeatingRenderer.renderBowShock(pose, buffer, warhead.lod(), warhead.progress(),
				warhead.elapsedTicks(), warhead.remainingTicks(), warhead.velocity(), warhead.visualSeed()));
		context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.REENTRY_PLASMA,
			(pose, buffer) -> ReentryHeatingRenderer.renderGlow(pose, buffer, warhead.lod(), warhead.progress(),
				warhead.elapsedTicks(), warhead.remainingTicks(), warhead.velocity(), warhead.visualSeed()));
		context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.REENTRY_PLASMA,
			(pose, buffer) -> ReentryHeatingRenderer.renderFilaments(pose, buffer, warhead.lod(), warhead.progress(),
				warhead.elapsedTicks(), warhead.remainingTicks(), warhead.velocity(), warhead.visualSeed()));
		poseStack.popPose();
	}

	private static void renderImpact(final LevelRenderContext context, final PoseStack poseStack,
		final RenderFrame frame, final ImpactFrame impact) {
		Vec3 relative = impact.position().subtract(frame.cameraPosition());
		float yieldRadiusScale = WarheadYieldScaling.radiusScale(impact.payloadType(), impact.visualScale());
		float yieldThicknessScale = (float) Math.sqrt(yieldRadiusScale);
		float thicknessScale = (float) impact.profile().shockwaveThicknessScale() * yieldThicknessScale;
		float alphaScale = (float) impact.profile().shockwaveAlphaScale() * Mth.clamp(yieldThicknessScale, 0.72F, 1.28F);
		double groundDistance = WarheadVisualMath.groundShockwaveDistance(impact.ageTicks(), yieldRadiusScale);

		poseStack.pushPose();
		poseStack.translate(relative.x, relative.y, relative.z);
		context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.PRESSURE_SHELL,
			(pose, buffer) -> PressureWaveSphereRenderer.render(pose, buffer,
				WarheadVisualMath.airShockwaveRadius(impact.ageTicks(), yieldRadiusScale), impact.ageTicks(),
				thicknessScale, alphaScale, yieldRadiusScale, impact.lod()));
		if (impact.payloadType() == WarheadPayloadType.NUCLEAR) {
			double returnRadius = WarheadVisualMath.nuclearReturnWaveRadius(impact.ageTicks(), yieldRadiusScale);
			if (returnRadius > 0.0) {
				context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.PRESSURE_SHELL,
					(pose, buffer) -> PressureWaveSphereRenderer.renderReturn(pose, buffer, returnRadius,
						impact.ageTicks(), yieldRadiusScale, impact.lod()));
			}
		}
		if (groundEffects(impact.effectProfile())) {
			context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.SHOCKWAVE,
				(pose, buffer) -> TerrainShockwaveRenderer.renderFrontier(pose, buffer, impact.shockfrontSpokes(),
					impact.position(), groundDistance, frontierSpokeCount(impact.lod()),
					groundFrontierWidth(impact.ageTicks(), thicknessScale, yieldRadiusScale),
					groundFrontierAlpha(impact.ageTicks(), alphaScale, yieldRadiusScale), 208, 226, 244));
			context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.SHOCKWAVE,
				(pose, buffer) -> TerrainShockwaveRenderer.renderFrontier(pose, buffer, impact.shockfrontSpokes(),
					impact.position(), Math.max(0.0, groundDistance - 3.0 * thicknessScale),
					frontierSpokeCount(impact.lod()), dustWidth(impact.ageTicks(), thicknessScale, yieldRadiusScale),
					dustAlpha(impact.ageTicks(), alphaScale, yieldRadiusScale), 188, 190, 190));
		}
		poseStack.popPose();

		CloudCluster cloud = impact.payloadType() == WarheadPayloadType.NUCLEAR ? cloudCluster(frame.impacts(), impact) : null;
		boolean renderCloud = impact.renderVolumetrics() && (cloud == null || cloud.leaderId().equals(impact.id()));

		poseStack.pushPose();
		poseStack.translate(relative.x, relative.y, relative.z);
		if (impact.payloadType() == WarheadPayloadType.NUCLEAR) {
			context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.GROUND_DUST,
				(pose, buffer) -> GroundDustFrontRenderer.render(pose, buffer, impact.dustNodes(), impact.position(),
					impact.gameTime(), impact.lod(),
					(float) impact.profile().shockwaveParticleDensityScale() * yieldThicknessScale,
					frame.cameraOrientation()));
			if (renderCloud) {
				context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.HEAVY_SMOKE,
					(pose, buffer) -> NuclearParticleCloudRenderer.renderSmoke(pose, buffer, cloud.ageTicks(),
						cloud.visualScale(), cloud.profile(), cloud.visualSeed(), impact.lod(), cloud.sources(),
						frame.cameraOrientation()));
				context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.FIREBALL_COOL,
					(pose, buffer) -> NuclearParticleCloudRenderer.renderFire(pose, buffer, cloud.ageTicks(),
						cloud.visualScale(), cloud.profile(), cloud.visualSeed(), impact.lod(), false, cloud.sources(),
						frame.cameraOrientation()));
				context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.FIREBALL_HOT,
					(pose, buffer) -> NuclearParticleCloudRenderer.renderFire(pose, buffer, cloud.ageTicks(),
						cloud.visualScale(), cloud.profile(), cloud.visualSeed(), impact.lod(), true, cloud.sources(),
						frame.cameraOrientation()));
			}
			double returnRadius = WarheadVisualMath.nuclearReturnWaveRadius(impact.ageTicks(), yieldRadiusScale);
			if (returnRadius > 0.0) {
				context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.GROUND_DUST,
					(pose, buffer) -> ConventionalBlastParticleRenderer.renderNuclearReturnFront(pose, buffer,
						impact.ageTicks(), returnRadius, yieldRadiusScale, impact.visualSeed(), impact.lod(),
						frame.cameraOrientation()));
			}
			if (renderCloud || cloud.sources().size() == 1) {
				context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.NUCLEAR_FLASH,
					(pose, buffer) -> NuclearFlashRenderer.render(pose, buffer, impact.ageTicks(), frame.cameraOrientation()));
			}
		} else {
			context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.GROUND_DUST,
				(pose, buffer) -> ConventionalBlastParticleRenderer.renderSurfaceFront(pose, buffer,
					impact.ageTicks(), groundDistance, impact.visualScale(), impact.visualSeed(),
					impact.lod(), frame.cameraOrientation()));
			if (renderCloud) {
				context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.HEAVY_SMOKE_CORE,
					(pose, buffer) -> ConventionalBlastParticleRenderer.renderSmokeCore(pose, buffer,
						impact.ageTicks(), impact.visualScale(), impact.profile(), impact.visualSeed(), impact.lod(),
						frame.cameraOrientation()));
				context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.FIREBALL_CORE,
					(pose, buffer) -> ConventionalBlastParticleRenderer.renderFireCore(pose, buffer,
						impact.ageTicks(), impact.visualScale(), impact.profile(), impact.visualSeed(), impact.lod(),
						frame.cameraOrientation()));
				context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.HEAVY_SMOKE,
					(pose, buffer) -> ConventionalBlastParticleRenderer.renderSmoke(pose, buffer,
						impact.ageTicks(), impact.visualScale(), impact.profile(), impact.visualSeed(), impact.lod(),
						frame.cameraOrientation()));
				context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.FIREBALL_COOL,
					(pose, buffer) -> ConventionalBlastParticleRenderer.renderCooling(pose, buffer,
						impact.ageTicks(), impact.visualScale(), impact.profile(), impact.visualSeed(), impact.lod(),
						frame.cameraOrientation()));
				context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.FIREBALL_HOT,
					(pose, buffer) -> ConventionalBlastParticleRenderer.renderHot(pose, buffer,
						impact.ageTicks(), impact.visualScale(), impact.profile(), impact.visualSeed(), impact.lod(),
						frame.cameraOrientation()));
			}
		}
		poseStack.popPose();
	}

	private static void renderDebris(final LevelRenderContext context, final PoseStack poseStack, final RenderFrame frame) {
		if (frame.debris().isEmpty()) return;
		poseStack.pushPose();
		context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.HEAVY_SMOKE,
			(pose, buffer) -> renderDebrisTrails(pose, buffer, frame.debris(), frame.cameraPosition(), frame.cameraOrientation()));
		poseStack.popPose();
		for (DebrisFrame debris : frame.debris()) {
			ClientDebrisBatchManager.RenderSample sample = debris.sample();
			double distanceSquared = frame.cameraPosition().distanceToSqr(sample.position());
			if (sample.scale() < 0.55F && distanceSquared > 64.0 * 64.0) continue;
			Vec3 relative = sample.position().subtract(frame.cameraPosition());
			poseStack.pushPose();
			poseStack.translate(relative.x, relative.y, relative.z);
			poseStack.scale(sample.scale(), sample.scale(), sample.scale());
			poseStack.mulPose(new Quaternionf().rotationXYZ((float) sample.spin().x * sample.age(),
				(float) sample.spin().y * sample.age(), (float) sample.spin().z * sample.age()));
			poseStack.translate(-0.5, -0.5, -0.5);
			context.submitNodeCollector().submitMovingBlock(poseStack, debris.movingBlock(), 0);
			poseStack.popPose();
		}
	}

	private static void renderDebrisTrails(final PoseStack.Pose pose, final com.mojang.blaze3d.vertex.VertexConsumer buffer,
		final List<DebrisFrame> debrisFrames, final Vec3 cameraPosition, final Quaternionf cameraOrientation) {
		Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F).rotate(cameraOrientation);
		Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F).rotate(cameraOrientation);
		Vector3f normal = new Vector3f(0.0F, 0.0F, 1.0F).rotate(cameraOrientation);
		for (DebrisFrame debris : debrisFrames) {
			ClientDebrisBatchManager.RenderSample sample = debris.sample();
			if (sample.partIndex() != 0 || sample.trailPositions().size() < 2) continue;
			List<Vec3> trail = sample.trailPositions();
			int red = debris.trailColour() >>> 16 & 255;
			int green = debris.trailColour() >>> 8 & 255;
			int blue = debris.trailColour() & 255;
			for (int point = 1; point < trail.size(); point++) {
				Vec3 start = trail.get(point - 1);
				Vec3 end = trail.get(point);
				double segmentLength = start.distanceTo(end);
				int subdivisions = Math.max(1, Math.min(5, (int) Math.ceil(segmentLength / 0.35)));
				for (int subdivision = 0; subdivision < subdivisions; subdivision++) {
					double t = (subdivision + 0.5) / subdivisions;
					Vec3 center = start.lerp(end, t).subtract(cameraPosition);
					float head = (point - 1 + (float) t) / Math.max(1.0F, trail.size() - 1.0F);
					float radius = (0.075F + 0.16F * head) * Math.max(0.65F, sample.scale());
					float alpha = (0.08F + 0.34F * head) * (sample.onGround() ? 0.24F : 1.0F);
					addDebrisBillboard(pose, buffer, center, radius, red, green, blue, alpha, right, up, normal);
				}
			}
		}
	}

	private static void addDebrisBillboard(final PoseStack.Pose pose, final com.mojang.blaze3d.vertex.VertexConsumer buffer,
		final Vec3 center, final float radius, final int red, final int green, final int blue, final float alpha,
		final Vector3f right, final Vector3f up, final Vector3f normal) {
		int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
		debrisVertex(pose, buffer, center, -radius, -radius, 0.0F, 1.0F, red, green, blue, a, right, up, normal);
		debrisVertex(pose, buffer, center, -radius, radius, 0.0F, 0.0F, red, green, blue, a, right, up, normal);
		debrisVertex(pose, buffer, center, radius, radius, 1.0F, 0.0F, red, green, blue, a, right, up, normal);
		debrisVertex(pose, buffer, center, radius, -radius, 1.0F, 1.0F, red, green, blue, a, right, up, normal);
	}

	private static void debrisVertex(final PoseStack.Pose pose, final com.mojang.blaze3d.vertex.VertexConsumer buffer,
		final Vec3 center, final float x, final float y, final float u, final float v, final int red, final int green,
		final int blue, final int alpha, final Vector3f right, final Vector3f up, final Vector3f normal) {
		float ox = right.x * x + up.x * y;
		float oy = right.y * x + up.y * y;
		float oz = right.z * x + up.z * y;
		buffer.addVertex(pose, (float) center.x + ox, (float) center.y + oy, (float) center.z + oz)
			.setColor(red, green, blue, alpha).setUv(u, v).setOverlay(0).setLight(0xB000B0)
			.setNormal(pose, normal.x, normal.y, normal.z);
	}

	private static CloudCluster cloudCluster(final List<ImpactFrame> impacts, final ImpactFrame anchor) {
		float anchorRadiusScale = WarheadYieldScaling.radiusScale(anchor.payloadType(), anchor.visualScale());
		double mergeRadius = 82.0 * Math.max(0.55, anchorRadiusScale);
		ArrayList<ImpactFrame> members = new ArrayList<>();
		ImpactFrame leader = null;
		for (ImpactFrame candidate : impacts) {
			if (candidate.payloadType() != WarheadPayloadType.NUCLEAR || candidate.effectProfile() != anchor.effectProfile()) continue;
			float candidateScale = WarheadYieldScaling.radiusScale(candidate.payloadType(), candidate.visualScale());
			double candidateMerge = Math.max(mergeRadius, 82.0 * candidateScale);
			if (Math.abs(candidate.ageTicks() - anchor.ageTicks()) > 180.0
				|| candidate.position().distanceToSqr(anchor.position()) > candidateMerge * candidateMerge) continue;
			members.add(candidate);
			if (candidate.renderVolumetrics() && (leader == null || candidate.ageTicks() > leader.ageTicks())) leader = candidate;
		}
		if (members.isEmpty()) members.add(anchor);
		if (leader == null) leader = anchor;
		double volume = 0.0;
		double maximumScale = 0.01;
		long mergedSeed = 0x434C4F55445F4D35L;
		ArrayList<VoxelImpactCloudRenderer.CloudSource> sources = new ArrayList<>(members.size());
		for (ImpactFrame member : members) {
			double memberScale = Math.max(0.05, member.visualScale());
			volume += memberScale * memberScale * memberScale;
			maximumScale = Math.max(maximumScale, memberScale);
			mergedSeed ^= mixCloudSeed(member.visualSeed());
			sources.add(new VoxelImpactCloudRenderer.CloudSource(member.position().subtract(leader.position()),
				member.ageTicks(), member.visualScale(), member.visualSeed()));
		}
		float mergedScale = (float) Math.min(maximumScale * 1.90, Math.cbrt(volume));
		return new CloudCluster(leader.id(), leader.ageTicks(), mergedScale, mergedSeed, leader.profile(), List.copyOf(sources));
	}

	public static DebugSnapshot debugSnapshot() {
		ConventionalBlastParticleRenderer.DebugSnapshot conventional = ConventionalBlastParticleRenderer.debugSnapshot();
		NuclearParticleCloudRenderer.DebugSnapshot nuclear = NuclearParticleCloudRenderer.debugSnapshot();
		return new DebugSnapshot(conventional.activeParticles() + nuclear.activeParticles(),
			conventional.spawnedParticlesPerTick() + nuclear.spawnedParticlesPerTick(),
			conventional.culledParticles() + nuclear.culledParticles(),
			ClientDebrisBatchManager.INSTANCE.activeFragmentCount(),
			WarheadRenderPipelines.compatibilityRendererActive()
				? "fabric_entity_pipeline_external_renderer" : "war_mod_custom_pipeline");
	}

	private static long mixCloudSeed(long value) {
		value ^= value >>> 30;
		value *= 0xBF58476D1CE4E5B9L;
		value ^= value >>> 27;
		value *= 0x94D049BB133111EBL;
		return value ^ value >>> 31;
	}

	private static boolean groundEffects(final WarheadEffectProfile effect) {
		return effect != WarheadEffectProfile.ANTI_AIR_INTERCEPTION
			&& effect != WarheadEffectProfile.ANTI_AIR_SAFE_SELF_DESTRUCT;
	}

	private static double coneActivation(final WarheadFrame warhead) {
		double speed = WarheadVisualMath.normalizedSpeed(warhead.velocity(),
			WarheadConstants.TRAJECTORY_SPEED_BLOCKS_PER_TICK * 1.65);
		return WarheadVisualMath.coneActivation(speed)
			* WarheadVisualMath.coneAttack(warhead.elapsedTicks() - warhead.flightTicks() * 0.20);
	}

	private static int frontierSpokeCount(final WarheadMesh.Lod lod) {
		return lod == WarheadMesh.Lod.NEAR ? 256 : lod == WarheadMesh.Lod.MEDIUM ? 160 : 96;
	}

	private static WarheadMesh.Lod lod(final double distance) {
		return distance < NEAR_DISTANCE ? WarheadMesh.Lod.NEAR
			: distance < MEDIUM_DISTANCE ? WarheadMesh.Lod.MEDIUM : WarheadMesh.Lod.FAR;
	}

	private static int sampledLight(final ClientLevel level, final Vec3 position) {
		BlockPos block = BlockPos.containing(position);
		if (!level.hasChunkAt(block)) return LightCoordsUtil.pack(5, 5);
		int packed = LightCoordsUtil.getLightCoords(level, block);
		return LightCoordsUtil.pack(Math.max(5, LightCoordsUtil.block(packed)), Math.max(5, LightCoordsUtil.sky(packed)));
	}

	private static Quaternionf rotationToVelocity(final Vec3 velocity) {
		Vector3f direction = new Vector3f((float) velocity.x, (float) velocity.y, (float) velocity.z);
		if (!Float.isFinite(direction.x()) || !Float.isFinite(direction.y()) || !Float.isFinite(direction.z())
			|| direction.lengthSquared() < 1.0E-8F) return new Quaternionf();
		return new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), direction.normalize());
	}

	private static float groundFrontierWidth(final double age, final float scale, final float radiusScale) {
		return (float) WarheadVisualMath.airShockwaveThickness(age, scale, radiusScale);
	}

	private static float groundFrontierAlpha(final double age, final float scale, final float radiusScale) {
		return Mth.clamp((float) (WarheadVisualMath.groundShockwaveAlpha(age, radiusScale) * 0.74 * scale), 0.0F, 1.0F);
	}

	private static float dustWidth(final double age, final float scale, final float radiusScale) {
		return (float) (WarheadVisualMath.airShockwaveThickness(age, scale, radiusScale) * 2.3);
	}

	private static float dustAlpha(final double age, final float scale, final float radiusScale) {
		return Mth.clamp((float) (WarheadVisualMath.groundShockwaveAlpha(age, radiusScale) * 0.58 * scale), 0.0F, 1.0F);
	}

	public record DebugSnapshot(int activeParticles, int spawnedParticlesPerTick, int culledParticles,
		int activeDebrisFragments, String activeRenderBackend) { }

	private record WarheadFrame(Vec3 position, Vec3 velocity, float progress, float elapsedTicks,
		float remainingTicks, int flightTicks, long visualSeed, WarheadMesh.Lod lod, int packedLight) { }

	private record ImpactFrame(UUID id, Vec3 position, double ageTicks, float visualScale, long visualSeed,
		WarheadPayloadType payloadType, WarheadEffectProfile effectProfile, boolean renderVolumetrics,
		WarheadClientVisualProfile profile, WarheadMesh.Lod lod, List<TerrainShockfrontSpoke> shockfrontSpokes,
		List<TerrainShockfrontNode> dustNodes, long gameTime) { }

	private record CloudCluster(UUID leaderId, double ageTicks, float visualScale, long visualSeed,
		WarheadClientVisualProfile profile, List<VoxelImpactCloudRenderer.CloudSource> sources) { }

	private record DebrisFrame(ClientDebrisBatchManager.RenderSample sample,
		MovingBlockRenderState movingBlock, int trailColour) { }

	private record RenderFrame(Vec3 cameraPosition, Quaternionf cameraOrientation,
		List<WarheadFrame> warheads, List<ImpactFrame> impacts, List<DebrisFrame> debris) {
		private static final RenderFrame EMPTY = new RenderFrame(Vec3.ZERO, new Quaternionf(), List.of(), List.of(), List.of());
	}
}
