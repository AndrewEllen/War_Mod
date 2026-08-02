package com.andye.warmod.entity.client;

import com.andye.warmod.entity.WarheadDebrisEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import org.joml.Quaternionf;

public final class WarheadDebrisRenderer extends EntityRenderer<WarheadDebrisEntity, WarheadDebrisRenderState> {
	public WarheadDebrisRenderer(final EntityRendererProvider.Context context) { super(context); this.shadowRadius = 0.3F; }

	@Override
	public WarheadDebrisRenderState createRenderState() { return new WarheadDebrisRenderState(); }

	@Override
	public void extractRenderState(final WarheadDebrisEntity entity, final WarheadDebrisRenderState state, final float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		BlockPos pos = entity.blockPosition();
		state.movingBlock.randomSeedPos = pos;
		state.movingBlock.blockPos = pos;
		state.movingBlock.blockState = entity.blockState();
		if (entity.level() instanceof ClientLevel level) {
			state.movingBlock.biome = level.getBiome(pos);
			state.movingBlock.cardinalLighting = level.cardinalLighting();
			state.movingBlock.lightEngine = level.getLightEngine();
		}
		state.angularVelocity = entity.angularVelocity();
		state.debrisAge = entity.age() + partialTick;
	}

	@Override
	public void submit(final WarheadDebrisRenderState state, final PoseStack poseStack, final SubmitNodeCollector collector, final CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.mulPose(new Quaternionf().rotationXYZ((float) state.angularVelocity.x * state.debrisAge,
			(float) state.angularVelocity.y * state.debrisAge, (float) state.angularVelocity.z * state.debrisAge));
		poseStack.translate(-0.5, -0.5, -0.5);
		collector.submitMovingBlock(poseStack, state.movingBlock, state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}