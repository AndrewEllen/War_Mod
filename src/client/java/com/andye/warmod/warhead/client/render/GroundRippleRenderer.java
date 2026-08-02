package com.andye.warmod.warhead.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public final class GroundRippleRenderer {
	private GroundRippleRenderer() { }
	public static void render(final PoseStack.Pose pose, final VertexConsumer buffer, final GroundRippleRenderState state) {
		GroundRippleMesh.render(pose, buffer, state);
	}
}