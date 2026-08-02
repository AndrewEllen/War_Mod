package com.andye.warmod.entity.client;

import com.andye.warmod.entity.WarheadDebrisEntity;
import com.andye.warmod.warhead.WarheadConstants;
import com.andye.warmod.warhead.client.render.WarheadRenderPipelines;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.core.BlockPos;
import org.joml.Quaternionf;

public final class WarheadDebrisRenderer extends EntityRenderer<WarheadDebrisEntity, WarheadDebrisRenderState> {
	public WarheadDebrisRenderer(final EntityRendererProvider.Context context) { super(context); this.shadowRadius = 0.3F; }

	@Override
	public boolean shouldRender(final WarheadDebrisEntity entity, final Frustum frustum,
		final double cameraX, final double cameraY, final double cameraZ) {
		double maximumDistance = WarheadConstants.VISUAL_RANGE_BLOCKS;
		if (entity.distanceToSqr(cameraX, cameraY, cameraZ) > maximumDistance * maximumDistance) return false;
		return !this.affectedByCulling(entity) || frustum.isVisible(this.getBoundingBoxForCulling(entity).inflate(0.5));
	}
	@Override
	public WarheadDebrisRenderState createRenderState() { return new WarheadDebrisRenderState(); }

	@Override
	public void extractRenderState(final WarheadDebrisEntity entity, final WarheadDebrisRenderState state, final float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		BlockPos pos = entity.blockPosition();
		state.movingBlock.randomSeedPos = pos;
		state.movingBlock.blockPos = pos;
		state.movingBlock.blockState = entity.blockState();
		state.movingBlock.biome = null;
		state.movingBlock.cardinalLighting = CardinalLighting.DEFAULT;
		state.movingBlock.lightEngine = LevelLightEngine.EMPTY;
		state.trailColour = 0x8A8178;
		if (entity.level() instanceof ClientLevel level && level.hasChunkAt(pos)) {
			state.movingBlock.biome = level.getBiome(pos);
			state.movingBlock.cardinalLighting = level.cardinalLighting();
			state.movingBlock.lightEngine = level.getLightEngine();
			state.trailColour = entity.blockState().getMapColor(level,pos).col & 0xFFFFFF;
		}
		state.angularVelocity = entity.angularVelocity();
		state.velocity = entity.getDeltaMovement();
		state.debrisAge = entity.age() + partialTick;
		state.visualScale = entity.visualScale();
		state.trailSeed = entity.getId();
		state.onGround = entity.onGround();
	}

	@Override
	public void submit(final WarheadDebrisRenderState state, final PoseStack poseStack, final SubmitNodeCollector collector, final CameraRenderState camera) {
		poseStack.pushPose();
		collector.submitCustomGeometry(poseStack,WarheadRenderPipelines.HEAVY_SMOKE,
			(pose,buffer)->WarheadDebrisTrailRenderer.render(pose,buffer,state));
		poseStack.popPose();
		poseStack.pushPose();
		poseStack.scale(state.visualScale, state.visualScale, state.visualScale);
		poseStack.mulPose(new Quaternionf().rotationXYZ((float) state.angularVelocity.x * state.debrisAge,
			(float) state.angularVelocity.y * state.debrisAge, (float) state.angularVelocity.z * state.debrisAge));
		poseStack.translate(-0.5, -0.5, -0.5);
		collector.submitMovingBlock(poseStack, state.movingBlock, state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}