package com.andye.warmod.compat.iris;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Converts existing four-vertex visual billboards to Minecraft 26.2 particle render state. */
public final class IrisParticleQuadCollector implements VertexConsumer {
    private static final float MIN_EDGE = 1.0E-5F;
    private static final float MAX_EDGE_RATIO_ERROR = 0.08F;
    private static final float MAX_ORTHOGONAL_ERROR = 0.08F;
    private static final float MAX_CORNER_ERROR = 0.10F;

    private final QuadParticleRenderState particles;
    private final TextureAtlasSprite sprite;
    private final SingleQuadParticle.Layer layer;
    private final float[] x = new float[4];
    private final float[] y = new float[4];
    private final float[] z = new float[4];
    private final float[] u = new float[4];
    private final float[] v = new float[4];
    private final int[] color = new int[4];
    private final int[] light = new int[4];
    private int completedVertices;
    private int currentVertex = -1;
    private boolean currentOpen;
    private boolean supported = true;
    private int particleCount;

    public IrisParticleQuadCollector(final QuadParticleRenderState particles,
        final TextureAtlasSprite sprite) {
        this.particles = particles;
        this.sprite = sprite;
        this.layer = SingleQuadParticle.Layer.bySprite(sprite);
    }

    @Override
    public VertexConsumer addVertex(final float px, final float py, final float pz) {
        finishCurrentVertex();
        if (!supported) return this;
        currentVertex = completedVertices;
        if (currentVertex < 0 || currentVertex >= 4) {
            supported = false;
            currentVertex = -1;
            return this;
        }
        x[currentVertex] = px;
        y[currentVertex] = py;
        z[currentVertex] = pz;
        u[currentVertex] = 0.0F;
        v[currentVertex] = 0.0F;
        color[currentVertex] = 0xFFFFFFFF;
        light[currentVertex] = 0;
        currentOpen = true;
        return this;
    }

    @Override
    public VertexConsumer setColor(final int red, final int green, final int blue, final int alpha) {
        if (currentOpen) {
            color[currentVertex] = ((alpha & 255) << 24)
                | ((red & 255) << 16)
                | ((green & 255) << 8)
                | (blue & 255);
        }
        return this;
    }

    @Override
    public VertexConsumer setColor(final int argb) {
        if (currentOpen) color[currentVertex] = argb;
        return this;
    }

    @Override
    public VertexConsumer setUv(final float textureU, final float textureV) {
        if (currentOpen) {
            u[currentVertex] = textureU;
            v[currentVertex] = textureV;
        }
        return this;
    }

    @Override
    public VertexConsumer setUv1(final int overlayU, final int overlayV) {
        return this;
    }

    @Override
    public VertexConsumer setUv2(final int lightU, final int lightV) {
        if (currentOpen) light[currentVertex] = (lightU & 0xFFFF) | ((lightV & 0xFFFF) << 16);
        return this;
    }

    @Override
    public VertexConsumer setLight(final int packedLight) {
        if (currentOpen) light[currentVertex] = packedLight;
        return this;
    }

    @Override
    public VertexConsumer setOverlay(final int packedOverlay) {
        return this;
    }

    @Override
    public VertexConsumer setNormal(final float normalX, final float normalY, final float normalZ) {
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(final float width) {
        return this;
    }

    public boolean finish() {
        finishCurrentVertex();
        if (completedVertices != 0) supported = false;
        return supported;
    }

    public int particleCount() {
        return particleCount;
    }

    private void finishCurrentVertex() {
        if (!currentOpen) return;
        currentOpen = false;
        completedVertices++;
        currentVertex = -1;
        if (completedVertices == 4) {
            if (supported) supported = emitQuad();
            completedVertices = 0;
        }
    }

    private boolean emitQuad() {
        Vector3f rightEdge = new Vector3f(x[3] - x[0], y[3] - y[0], z[3] - z[0]);
        Vector3f upEdge = new Vector3f(x[1] - x[0], y[1] - y[0], z[1] - z[0]);
        float rightLength = rightEdge.length();
        float upLength = upEdge.length();
        if (!Float.isFinite(rightLength) || !Float.isFinite(upLength)
            || rightLength < MIN_EDGE || upLength < MIN_EDGE) return false;
        float largestEdge = Math.max(rightLength, upLength);
        if (Math.abs(rightLength - upLength) / largestEdge > MAX_EDGE_RATIO_ERROR) return false;

        Vector3f right = rightEdge.div(rightLength);
        Vector3f up = upEdge.div(upLength);
        if (Math.abs(right.dot(up)) > MAX_ORTHOGONAL_ERROR) return false;
        Vector3f expectedCorner = new Vector3f(x[0], y[0], z[0])
            .add(new Vector3f(right).mul(rightLength))
            .add(new Vector3f(up).mul(upLength));
        float cornerError = expectedCorner.distance(new Vector3f(x[2], y[2], z[2]));
        if (!Float.isFinite(cornerError) || cornerError > largestEdge * MAX_CORNER_ERROR) return false;

        Vector3f normal = new Vector3f(right).cross(up);
        float normalLength = normal.length();
        if (!Float.isFinite(normalLength) || normalLength < MIN_EDGE) return false;
        normal.div(normalLength);
        up.set(normal).cross(right).normalize();
        Quaternionf rotation = rotationFromBasis(right, up, normal);

        float centerX = (x[0] + x[1] + x[2] + x[3]) * 0.25F;
        float centerY = (y[0] + y[1] + y[2] + y[3]) * 0.25F;
        float centerZ = (z[0] + z[1] + z[2] + z[3]) * 0.25F;
        float scale = (rightLength + upLength) * 0.25F;
        float rawMinU = Math.min(Math.min(u[0], u[1]), Math.min(u[2], u[3]));
        float rawMaxU = Math.max(Math.max(u[0], u[1]), Math.max(u[2], u[3]));
        float rawMinV = Math.min(Math.min(v[0], v[1]), Math.min(v[2], v[3]));
        float rawMaxV = Math.max(Math.max(v[0], v[1]), Math.max(v[2], v[3]));
        float minU = remap(rawMinU, sprite.getU0(), sprite.getU1());
        float maxU = remap(rawMaxU, sprite.getU0(), sprite.getU1());
        float minV = remap(rawMinV, sprite.getV0(), sprite.getV1());
        float maxV = remap(rawMaxV, sprite.getV0(), sprite.getV1());

        particles.add(layer, centerX, centerY, centerZ,
            rotation.x, rotation.y, rotation.z, rotation.w,
            scale, minU, maxU, minV, maxV, color[0], light[0]);
        particleCount++;
        return true;
    }

    private static float remap(final float value, final float minimum, final float maximum) {
        return minimum + (maximum - minimum) * value;
    }

    private static Quaternionf rotationFromBasis(final Vector3f right,
        final Vector3f up, final Vector3f forward) {
        float m00 = right.x, m01 = up.x, m02 = forward.x;
        float m10 = right.y, m11 = up.y, m12 = forward.y;
        float m20 = right.z, m21 = up.z, m22 = forward.z;
        float qx, qy, qz, qw;
        float trace = m00 + m11 + m22;
        if (trace > 0.0F) {
            float s = (float) Math.sqrt(trace + 1.0F) * 2.0F;
            qw = 0.25F * s;
            qx = (m21 - m12) / s;
            qy = (m02 - m20) / s;
            qz = (m10 - m01) / s;
        } else if (m00 > m11 && m00 > m22) {
            float s = (float) Math.sqrt(1.0F + m00 - m11 - m22) * 2.0F;
            qw = (m21 - m12) / s;
            qx = 0.25F * s;
            qy = (m01 + m10) / s;
            qz = (m02 + m20) / s;
        } else if (m11 > m22) {
            float s = (float) Math.sqrt(1.0F + m11 - m00 - m22) * 2.0F;
            qw = (m02 - m20) / s;
            qx = (m01 + m10) / s;
            qy = 0.25F * s;
            qz = (m12 + m21) / s;
        } else {
            float s = (float) Math.sqrt(1.0F + m22 - m00 - m11) * 2.0F;
            qw = (m10 - m01) / s;
            qx = (m02 + m20) / s;
            qy = (m12 + m21) / s;
            qz = 0.25F * s;
        }
        return new Quaternionf(qx, qy, qz, qw).normalize();
    }
}
