package com.andye.warmod.icbm.client.render;

import com.andye.warmod.warhead.WarheadDeliveryMode;
import com.andye.warmod.warhead.WarheadYield;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public final class SpentIcbmStageRenderer {
    private SpentIcbmStageRenderer() { }

    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final WarheadYield yield, final WarheadDeliveryMode deliveryMode,
        final int light, final float alpha) {
        IcbmMissileMesh.renderStage(pose, buffer, yield, deliveryMode, light,
            Math.max(0, Math.min(255, (int) (alpha * 255.0F))));
    }
}
