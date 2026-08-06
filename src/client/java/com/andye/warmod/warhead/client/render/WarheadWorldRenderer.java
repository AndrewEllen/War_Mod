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

public final class WarheadWorldRenderer {
	private static final int[][] CLUSTER_TWO_OFFSETS = {
		{0, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {-1, 0, 0}
	};
	private static final int[][] CLUSTER_THREE_OFFSETS = {
		{0, 0, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
		{0, 1, 0}, {1, 1, 0}, {0, 1, 1}, {-1, 1, 0}, {0, 2, 0}
	};
	private static final int[][] CLUSTER_FOUR_OFFSETS = {
		{0,0,0},{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{1,0,1},{-1,0,1},{1,0,-1},{-1,0,-1},
		{0,1,0},{1,1,0},{-1,1,0},{0,1,1},{0,1,-1},{1,1,1},{-1,1,-1},{0,2,0},{1,2,0},{0,2,1}
	};
	private static final int[][] CLUSTER_FIVE_OFFSETS = {
		{0,0,0},{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{1,0,1},{-1,0,1},{1,0,-1},{-1,0,-1},
		{2,0,0},{-2,0,0},{0,0,2},{0,0,-2},{0,1,0},{1,1,0},{-1,1,0},{0,1,1},{0,1,-1},
		{1,1,1},{-1,1,1},{1,1,-1},{-1,1,-1},{2,1,0},{-2,1,0},{0,1,2},{0,1,-2},
		{0,2,0},{1,2,0},{-1,2,0},{0,2,1},{0,2,-1},{1,2,1},{-1,2,-1},{0,3,0}
	};
	private static final double NEAR_DISTANCE = 192.0;
	private static final double MEDIUM_DISTANCE = 640.0;
	private static final double MAX_DISTANCE = 1536.0;
	private static volatile RenderFrame currentFrame = RenderFrame.EMPTY;
	private static boolean registered;

	private WarheadWorldRenderer() {
	}

	public static void register() {
		if (registered) return;
		LevelExtractionEvents.END_EXTRACTION.register(WarheadWorldRenderer::extract);
		LevelRenderEvents.COLLECT_SUBMITS.register(WarheadWorldRenderer::collectSubmits);
		registered = true;
		if (SharedConstants.IS_RUNNING_IN_IDE) {
			com.andye.warmod.WarMod.LOGGER.info("Re-entry visual configuration loaded");
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
			warheads.add(new WarheadFrame(
				position,
				velocity,
				(float) state.progressAt(gameTime, partialTick),
				(float) elapsed,
				(float) Math.max(0.0, state.flightTicks() - elapsed),
				state.flightTicks(),
				state.visualSeed(),
				lod(distance),
				sampledLight(level, position)
			));
		}

		List<ImpactFrame> impacts = new ArrayList<>();
		for (ImpactVisualState state : snapshot.impacts()) {
			double age = state.ageTicks(gameTime, partialTick);
			if (state.isExpired(gameTime, partialTick)) continue;
			double distance = cameraPosition.distanceTo(state.impactPosition());
			if (!Double.isFinite(distance) || distance > MAX_DISTANCE) continue;
			WarheadMesh.Lod lod = lod(distance);
			float yieldRadiusScale = WarheadYieldScaling.radiusScale(state.payloadType(), state.visualScale());
			double groundDistance = WarheadVisualMath.groundShockwaveDistance(age, yieldRadiusScale);
			int dustLimit = lod == WarheadMesh.Lod.NEAR ? 2_000 : lod == WarheadMesh.Lod.MEDIUM ? 1_000 : 400;
			List<TerrainShockfrontNode> dustNodes = groundEffects(state.effectProfile())
				? state.terrainShockfrontField().activeDustNodes(groundDistance, frontierSpokeCount(lod), dustLimit, gameTime)
				: List.of();
			for (TerrainShockfrontNode node : dustNodes) {
				if (node.state() == TerrainShockfrontNode.State.READY) {
					state.terrainShockfrontField().markEmitted(node, gameTime);
				}
			}
			impacts.add(new ImpactFrame(
				state.warheadId(),
				state.impactPosition(),
				age,
				state.visualScale(),
				state.visualSeed(),
				state.payloadType(),
				state.effectProfile(),
				ClientWarheadVisualManager.INSTANCE.shouldRenderVolumetrics(state.warheadId()),
				state.profile(),
				lod,
				state.terrainShockfrontField().snapshotSpokes(),
				dustNodes,
				state.fireballLobes(),
				state.blastCloudLobes(),
				gameTime
			));
		}
		List<DebrisFrame> debris = new ArrayList<>();
		for (ClientDebrisBatchManager.RenderSample sample : ClientDebrisBatchManager.INSTANCE.snapshot(
			level, gameTime, partialTick, cameraPosition, MAX_DISTANCE)) {
			BlockPos blockPosition = BlockPos.containing(sample.position());
			MovingBlockRenderState movingBlock = new MovingBlockRenderState();
			movingBlock.randomSeedPos = blockPosition;
			movingBlock.blockPos = blockPosition;
			movingBlock.blockState = sample.state();
			movingBlock.biome = null;
			movingBlock.cardinalLighting = CardinalLighting.DEFAULT;
			movingBlock.lightEngine = LevelLightEngine.EMPTY;
			int trailColour = 0x8A8178;
			if (level.hasChunkAt(blockPosition)) {
				movingBlock.biome = level.getBiome(blockPosition);
				movingBlock.cardinalLighting = level.cardinalLighting();
				movingBlock.lightEngine = level.getLightEngine();
				trailColour = sample.state().getMapColor(level, blockPosition).col & 0xFFFFFF;
			}
			debris.add(new DebrisFrame(sample, movingBlock, trailColour));
		}
		currentFrame = new RenderFrame(cameraPosition, cameraOrientation, List.copyOf(warheads),
			List.copyOf(impacts), List.copyOf(debris));
	}

	private static void collectSubmits(final LevelRenderContext context) {
		RenderFrame frame = currentFrame;
		if (frame == RenderFrame.EMPTY || (frame.warheads().isEmpty() && frame.impacts().isEmpty()
			&& frame.debris().isEmpty())) return;
		PoseStack poseStack = context.poseStack();
		if (poseStack == null) return;
		for (WarheadFrame warhead : frame.warheads()) {
			renderWarhead(context, poseStack, frame.cameraPosition(), warhead);
		}
		for (ImpactFrame impact : frame.impacts()) {
			renderImpact(context, poseStack, frame, impact);
		}
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
		float alphaScale = (float) impact.profile().shockwaveAlphaScale()
			* Mth.clamp(yieldThicknessScale, 0.72F, 1.28F);
		double groundDistance = WarheadVisualMath.groundShockwaveDistance(impact.ageTicks(), yieldRadiusScale);

		poseStack.pushPose();
		poseStack.translate(relative.x, relative.y, relative.z);
		context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.PRESSURE_SHELL,
			(pose, buffer) -> PressureWaveSphereRenderer.render(pose, buffer,
				WarheadVisualMath.airShockwaveRadius(impact.ageTicks(), yieldRadiusScale), impact.ageTicks(),
				thicknessScale, alphaScale, yieldRadiusScale, impact.lod()));
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
					dustAlpha(impact.ageTicks(), alphaScale, yieldRadiusScale), 130, 119, 108));
		}
		poseStack.popPose();

		CloudCluster cloud = impact.payloadType() == WarheadPayloadType.NUCLEAR
			? cloudCluster(frame.impacts(), impact)
			: new CloudCluster(impact.id(), impact.ageTicks(), impact.visualScale(), impact.visualSeed(),
				impact.profile(), List.of(new VoxelImpactCloudRenderer.CloudSource(
					Vec3.ZERO, impact.ageTicks(), impact.visualScale(), impact.visualSeed())));
		boolean renderMergedCloud = impact.payloadType() == WarheadPayloadType.NUCLEAR
			? impact.renderVolumetrics() && cloud.leaderId().equals(impact.id())
			: impact.renderVolumetrics();

		poseStack.pushPose();
		poseStack.translate(relative.x, relative.y, relative.z);
		context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.GROUND_DUST,
			(pose, buffer) -> GroundDustFrontRenderer.render(pose, buffer, impact.dustNodes(), impact.position(),
				impact.gameTime(), impact.lod(),
				(float) impact.profile().shockwaveParticleDensityScale() * yieldThicknessScale,
				frame.cameraOrientation()));
		if (impact.payloadType() != WarheadPayloadType.NUCLEAR) {
			/* OpenMiner-style ground-coupled blast: fire, cooling smoke and dust all emerge from the crater. */
			context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.GROUND_DUST,
				(pose, buffer) -> ConventionalBlastParticleRenderer.renderSurfaceFront(pose, buffer,
					impact.ageTicks(), groundDistance, impact.visualScale(), impact.visualSeed(),
					impact.lod(), frame.cameraOrientation()));
			if (renderMergedCloud) {
				context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.HEAVY_SMOKE,
					(pose, buffer) -> ConventionalBlastParticleRenderer.renderSmoke(pose, buffer,
						impact.ageTicks(), impact.visualScale(), impact.profile(), impact.visualSeed(),
						impact.lod(), frame.cameraOrientation()));
				context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.FIREBALL_COOL,
					(pose, buffer) -> ConventionalBlastParticleRenderer.renderCooling(pose, buffer,
						impact.ageTicks(), impact.visualScale(), impact.profile(), impact.visualSeed(),
						impact.lod(), frame.cameraOrientation()));
				context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.FIREBALL_HOT,
					(pose, buffer) -> ConventionalBlastParticleRenderer.renderHot(pose, buffer,
						impact.ageTicks(), impact.visualScale(), impact.profile(), impact.visualSeed(),
						impact.lod(), frame.cameraOrientation()));
			}
		} else if (renderMergedCloud) {
			/* Nuclear yields alone use one-block voxels, with analytical particles only as edge turbulence. */
			context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.VOXEL_SMOKE,
				(pose, buffer) -> VoxelImpactCloudRenderer.renderSmoke(pose, buffer, cloud.ageTicks(),
					cloud.visualScale(), cloud.profile(), cloud.visualSeed(), impact.lod(), cloud.sources()));
			context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.HEAVY_SMOKE,
				(pose, buffer) -> ProceduralImpactParticleRenderer.renderSmoke(pose, buffer, cloud.ageTicks(),
					cloud.visualScale() * 0.58F, cloud.profile(), cloud.visualSeed(), impact.blastCloudLobes(),
					impact.lod(), frame.cameraOrientation()));
			context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.VOXEL_FIRE_COOL,
				(pose, buffer) -> VoxelImpactCloudRenderer.renderFire(pose, buffer, cloud.ageTicks(),
					cloud.visualScale(), cloud.profile(), cloud.visualSeed(), impact.lod(), false, cloud.sources()));
			context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.VOXEL_FIRE_HOT,
				(pose, buffer) -> VoxelImpactCloudRenderer.renderFire(pose, buffer, cloud.ageTicks(),
					cloud.visualScale(), cloud.profile(), cloud.visualSeed(), impact.lod(), true, cloud.sources()));
			context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.FIREBALL_HOT,
				(pose, buffer) -> ProceduralImpactParticleRenderer.renderHot(pose, buffer, cloud.ageTicks(),
					cloud.visualScale() * 0.52F, cloud.profile(), cloud.visualSeed(), impact.lod(),
					frame.cameraOrientation()));
		}

		if (impact.payloadType() == WarheadPayloadType.NUCLEAR
			&& (renderMergedCloud || cloud.sources().size() == 1)) {
			context.submitNodeCollector().submitCustomGeometry(poseStack, WarheadRenderPipelines.NUCLEAR_FLASH,
				(pose, buffer) -> NuclearFlashRenderer.render(pose, buffer, impact.ageTicks(), frame.cameraOrientation()));
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
			poseStack.mulPose(new Quaternionf().rotationXYZ(
				(float) sample.spin().x * sample.age(),
				(float) sample.spin().y * sample.age(),
				(float) sample.spin().z * sample.age()));
			/* Each sample is an actual block from a connected captured fragment. */
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
			if (sample.onGround() || sample.age() > 55.0F || sample.velocity().lengthSqr() < 0.01) continue;
			Vec3 current = sample.position().subtract(cameraPosition);
			Vec3 velocity = sample.velocity();
			double speed = Math.sqrt(velocity.lengthSqr());
			int red = debris.trailColour() >>> 16 & 255;
			int green = debris.trailColour() >>> 8 & 255;
			int blue = debris.trailColour() & 255;
			if (sample.scale() < 0.55F) {
				addDebrisBillboard(pose, buffer, current, Math.max(0.12F, sample.scale() * 0.62F),
					red, green, blue, 0.78F, right, up, normal);
			}
			for (int puff = 0; puff < 3; puff++) {
				double back = (puff + 1) * (0.45 + Math.min(1.8, speed * 1.4));
				Vec3 center = current.subtract(velocity.normalize().scale(back));
				float radius = (0.24F + puff * 0.11F) * Math.max(0.35F, sample.scale());
				float alpha = (0.28F - puff * 0.065F) * Math.max(0.0F, 1.0F - sample.age() / 55.0F);
				addDebrisBillboard(pose, buffer, center, radius, red, green, blue, alpha, right, up, normal);
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
			.setColor(red, green, blue, alpha).setUv(u, v).setOverlay(0).setLight(0xA000A0)
			.setNormal(pose, normal.x, normal.y, normal.z);
	}


	private static CloudCluster cloudCluster(final List<ImpactFrame> impacts, final ImpactFrame anchor) {
		float anchorRadiusScale = WarheadYieldScaling.radiusScale(anchor.payloadType(), anchor.visualScale());
		double mergeRadius = (anchor.payloadType() == WarheadPayloadType.NUCLEAR ? 82.0 : 24.0)
			* Math.max(0.55, anchorRadiusScale);
		double maximumAgeDelta = anchor.payloadType() == WarheadPayloadType.NUCLEAR ? 180.0 : 72.0;
		ArrayList<ImpactFrame> members = new ArrayList<>();
		ImpactFrame leader = null;
		for (ImpactFrame candidate : impacts) {
			if (candidate.payloadType() != anchor.payloadType()
				|| candidate.effectProfile() != anchor.effectProfile()) continue;
			float candidateScale = WarheadYieldScaling.radiusScale(candidate.payloadType(), candidate.visualScale());
			double candidateMerge = Math.max(mergeRadius,
				(anchor.payloadType() == WarheadPayloadType.NUCLEAR ? 82.0 : 24.0) * candidateScale);
			if (Math.abs(candidate.ageTicks() - anchor.ageTicks()) > maximumAgeDelta
				|| candidate.position().distanceToSqr(anchor.position()) > candidateMerge * candidateMerge) continue;
			members.add(candidate);
			if (candidate.renderVolumetrics()
				&& (leader == null || candidate.ageTicks() > leader.ageTicks())) leader = candidate;
		}
		if (members.isEmpty()) members.add(anchor);
		if (leader == null) leader = anchor;

		double volume = 0.0;
		double maximumScale = 0.01;
		long mergedSeed = 0x434C4F55445F4D35L;
		ArrayList<VoxelImpactCloudRenderer.CloudSource> sources = new ArrayList<>(members.size());
		for (ImpactFrame member : members) {
			double scale = Math.max(0.05, member.visualScale());
			volume += scale * scale * scale;
			maximumScale = Math.max(maximumScale, scale);
			mergedSeed ^= mixCloudSeed(member.visualSeed());
			sources.add(new VoxelImpactCloudRenderer.CloudSource(
				member.position().subtract(leader.position()),
				member.ageTicks(),
				member.visualScale(),
				member.visualSeed()
			));
		}
		float mergedScale = (float) Math.min(maximumScale * 1.90, Math.cbrt(volume));
		return new CloudCluster(
			leader.id(),
			leader.ageTicks(),
			mergedScale,
			mergedSeed,
			leader.profile(),
			List.copyOf(sources)
		);
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

	private record WarheadFrame(Vec3 position, Vec3 velocity, float progress, float elapsedTicks,
		float remainingTicks, int flightTicks, long visualSeed, WarheadMesh.Lod lod, int packedLight) {
	}

	private record ImpactFrame(UUID id, Vec3 position, double ageTicks, float visualScale, long visualSeed,
		WarheadPayloadType payloadType, WarheadEffectProfile effectProfile, boolean renderVolumetrics,
		WarheadClientVisualProfile profile,
		WarheadMesh.Lod lod, List<TerrainShockfrontSpoke> shockfrontSpokes, List<TerrainShockfrontNode> dustNodes,
		List<FireballLobe> fireballLobes, List<BlastCloudLobe> blastCloudLobes, long gameTime) {
	}

	private record CloudCluster(UUID leaderId, double ageTicks, float visualScale, long visualSeed,
		WarheadClientVisualProfile profile, List<VoxelImpactCloudRenderer.CloudSource> sources) {
	}

	private record DebrisFrame(ClientDebrisBatchManager.RenderSample sample, MovingBlockRenderState movingBlock, int trailColour) {
	}

	private record RenderFrame(Vec3 cameraPosition, Quaternionf cameraOrientation,
		List<WarheadFrame> warheads, List<ImpactFrame> impacts, List<DebrisFrame> debris) {
		private static final RenderFrame EMPTY = new RenderFrame(Vec3.ZERO, new Quaternionf(), List.of(), List.of(), List.of());
	}
}
