package com.andye.warmod.warhead.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;

/** Multiplies only vertex alpha so CPU and GPU representations can crossfade. */
public final class OpticalEnvelopeVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final float alphaScale;

    private OpticalEnvelopeVertexConsumer(final VertexConsumer delegate,
        final float alphaScale) {
        this.delegate = delegate;
        this.alphaScale = Mth.clamp(alphaScale, 0.0F, 1.0F);
    }

    public static VertexConsumer scale(final VertexConsumer delegate,
        final float alphaScale) {
        if (delegate == null) throw new NullPointerException("delegate");
        return alphaScale >= 0.999F ? delegate
            : new OpticalEnvelopeVertexConsumer(delegate, alphaScale);
    }

    @Override
    public VertexConsumer addVertex(final float x, final float y, final float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(final int red, final int green, final int blue,
        final int alpha) {
        delegate.setColor(red, green, blue,
            Mth.clamp(Math.round(alpha * alphaScale), 0, 255));
        return this;
    }

    @Override
    public VertexConsumer setColor(final int argb) {
        int alpha = Mth.clamp(Math.round((argb >>> 24) * alphaScale), 0, 255);
        delegate.setColor((argb & 0x00FFFFFF) | alpha << 24);
        return this;
    }

    @Override
    public VertexConsumer setUv(final float u, final float v) {
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(final int u, final int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(final int u, final int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setLight(final int packedLight) {
        delegate.setLight(packedLight);
        return this;
    }

    @Override
    public VertexConsumer setOverlay(final int packedOverlay) {
        delegate.setOverlay(packedOverlay);
        return this;
    }

    @Override
    public VertexConsumer setNormal(final float x, final float y, final float z) {
        delegate.setNormal(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(final float width) {
        delegate.setLineWidth(width);
        return this;
    }
}
